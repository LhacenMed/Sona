# Research: Fossify Music Player ("Music") backend — for cloning into Sona

Source project: `C:\Users\lhacenmed\AndroidStudioProjects\Music`
Package: `org.fossify.musicplayer` (this is a fork of **Fossify Music Player** — NOT Auxio, despite one comment referencing "ported from Auxio's getAttrColorCompat"). Kotlin, min SDK 26, target/compile SDK 36, AGP 9.3.1, Kotlin 2.3.10.

Key module layout (`app/src/main/kotlin/org/fossify/musicplayer/`):
- `data/` — MediaScanner.kt, AudioHelper.kt, MusicDatabase.kt, MusicDatabaseMigrations.kt, RoomHelper.kt, TagHelper.kt, PlaylistIO.kt, `dao/*.kt`
- `models/` — Track, Album, Artist, Genre, Folder, Playlist, PlayStats, QueueItem (all Room `@Entity` except Folder)
- `helpers/` — Config.kt (SharedPreferences wrapper via Fossify Commons `BaseConfig`), Constants.kt
- `update/` — AppUpdate.kt, UpdateChecker.kt, UpdateDownloader.kt, UpdateInstaller.kt, UpdateService.kt, UpdateState.kt
- `activities/MainActivity.kt` — wires permission request → scan → update check

---

## 1. Media scanning system

File: `data/MediaScanner.kt` (singleton, `MediaScanner.getInstance(app: Application)`).

### Strategy: MediaStore first, then manual filesystem fallback
`scan(progress, callback)` (annotated `@Synchronized`, guarded by an internal `scanning` boolean so only one scan runs at a time) runs on a background thread (`ensureBackgroundThread`) and does, in order:
1. `scanMediaStore()` — always.
2. If `isQPlus()` (Android 10+): invoke callback with `complete=false` (partial UI update), then `scanFilesManually()` — a raw filesystem walk to catch files MediaStore hasn't indexed yet (e.g. pushed via `adb push`).
3. `cleanupDatabase()` — removes DB rows for tracks/albums/artists/genres no longer present.
4. invoke callback with `complete=true`.

Only one flow runs even if `scan()` is called re-entrantly while already scanning (early return after `maybeShowScanProgress()`).

### MediaStore querying (`scanMediaStore` → `getTracksSync/getArtistsSync/getAlbumsSync/getGenresSync/assignGenreToTracks`)
Uses `content://` queries via `context.queryCursor(uri, projection, selection, args) { cursor -> ... }` (a Fossify Commons extension wrapping `ContentResolver.query`).

- **Tracks**: `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` with projection: `_ID, DURATION, DATA, TITLE, ARTIST, ALBUM, ALBUM_ID, ARTIST_ID, TRACK, YEAR, DATE_ADDED`, plus conditionally `BUCKET_DISPLAY_NAME` (Q+) and `GENRE, GENRE_ID, DISC_NUMBER` (R+ / API 30+).
  - Track number encoding quirk: MediaStore packs disc+track into one `TRACK` column pre-R (e.g. `1007` = disc 1, track 7); code does `if (trackId >= 1000) { discNumber = trackId/1000; trackId %= 1000 }` when no explicit `DISC_NUMBER` exists.
  - Folder name: Q+ uses `BUCKET_DISPLAY_NAME`; pre-Q calls `context.getFriendlyFolder(path)` (extensions/Context.kt:323) which maps to "Internal storage"/"SD card" strings or the parent dir name.
  - Album art: constructed as `ContentUris.withAppendedId(artworkUri, albumId)` where `artworkUri = "content://media/external/audio/albumart".toUri()` (helpers/Constants.kt:28) — no bitmap decoding at scan time, just a content URI stored as a string in `coverArt`.
  - Tracks with an empty title are skipped.
- **Artists**: `Audio.Artists.EXTERNAL_CONTENT_URI`, projection `_ID, ARTIST, NUMBER_OF_TRACKS, NUMBER_OF_ALBUMS`. Only kept if `albumCnt > 0 && trackCnt > 0`. Note: the code assigns `NUMBER_OF_TRACKS` to `albumCnt` and `NUMBER_OF_ALBUMS` to `trackCnt` — swapped in this codebase's variable names (an apparent bug/quirk carried over — the numbers get recalculated correctly afterward from tracks anyway, see below).
- **Albums**: `Audio.Albums.EXTERNAL_CONTENT_URI`, projection `_ID, ARTIST, FIRST_YEAR, ALBUM, NUMBER_OF_SONGS` (+`ARTIST_ID` on Q+; pre-Q artistId is resolved by matching artist name string against already-fetched artists list).
- **Genres**: `Audio.Genres.EXTERNAL_CONTENT_URI`, projection `_ID, NAME`.
- **Genre-track association**: on API ≤30 (`!isRPlus()`), `Audio.Media.GENRE_ID` doesn't exist, so genre membership is queried separately via `content://media/external/audio/genres/all/members` (`GENRE_CONTENT_URI` constant) using `Audio.Genres.Members.GENRE_ID/AUDIO_ID`, building a `Map<genreId, List<trackId>>` and back-filling `track.genreId`. On R+, `GENRE`/`GENRE_ID` columns come directly from the Media table.

### Post-processing / normalization (still inside `scanMediaStore`)
- Excludes tracks whose parent folder is in `config.excludedFolders`, and tracks whose `albumId`/`artistId` don't match a fetched album/artist (orphans).
- Recomputes `trackCnt` for each album from the actually-kept tracks (overriding MediaStore's own counts) and sets `album.dateAdded` from the first track's `dateAdded`.
- Recomputes `artist.trackCnt`/`albumCnt` from kept tracks/albums (this is what "fixes" the swapped variable naming above), and picks `artist.albumArt` from the first album with non-empty cover art.
- Recomputes `genre.trackCnt` and `genre.albumArt` similarly.
- Drops albums/artists/genres left with 0 tracks.
- Calls `updateAllDatabases()` to persist everything found so far (see Section 2).

### Manual filesystem scan (`scanFilesManually` → `findTracksManually`/`findAudioFiles`)
Only runs on Q+ (`isQPlus()`), as a supplement/fallback:
- Roots: `context.internalStoragePath` and `context.sdCardPath` (Fossify Commons extensions resolving to physical storage roots).
- `findAudioFiles(file, destination, excludedPaths)` recursively walks directories using plain `java.io.File` (`file.listFiles()`), skipping:
  - hidden files/dirs (`file.isHidden`),
  - any path in `excludedPaths` (paths already found via MediaStore, plus `config.excludedFolders`) or whose **parent** is excluded,
  - any directory containing a `.nomedia` file (`file.containsNoMedia()`, a Fossify Commons extension) — standard Android convention for excluding a folder from media scanning.
  - Files are added if `path.isAudioFast()` (Fossify Commons extension — presumably an extension-based check, not content sniffing).
- For each found path not already known from MediaStore, metadata is extracted using **`android.media.MediaMetadataRetriever`** (NOT jaudiotagger for scanning — jaudiotagger is only used for tag *editing*, see below):
  - `setDataSource(path)`, falling back to `setDataSource(FileInputStream(path).fd)` if the direct path fails (e.g. some URI/permission edge cases).
  - Extracts `METADATA_KEY_TITLE` (falls back to filename), `ARTIST` (falls back to `ALBUMARTIST`, then `UNKNOWN_STRING`), `DURATION`, `ALBUM` (falls back to folder name), `CD_TRACK_NUMBER`, `DISC_NUMBER`, `YEAR`, `GENRE`.
  - `dateAdded` comes from `File(path).lastModified()` (this is the closest thing to a file-modification-based signal in the whole scanner).
  - Track gets `flags = FLAG_MANUAL_CACHE` (bit `1`) to mark it as not MediaStore-backed; `mediaStoreId` is synthesized as `track.hashCode().toLong()` since there's no real MediaStore row.
  - Progress notifications are throttled to every 100ms via `maybeShowScanProgress()`, shown through `NotificationHelper.createMediaScannerNotification` (delayed 1500ms before first showing, to avoid flicker on fast scans).
- After the manual walk, calls `context.rescanPaths(pathsToRescan)` (`maybeRescanPaths`) — a Fossify Commons extension that likely calls `MediaScannerConnection.scanFile()` to nudge the system MediaStore into indexing these files for next time.
- Manually-found tracks/artists/albums/genres are grouped via `splitIntoArtists/Albums/Genres` (simple `groupBy` on artist/album/genre string, since there's no MediaStore ID to key on), with synthetic IDs from `hashCode()`.

### Format support
No explicit whitelist of extensions in the scanner itself — relies on:
- `path.isAudioFast()` (Fossify Commons, presumably extension-string based, e.g. mp3/m4a/flac/ogg/wav/wma/opus) for the manual filesystem walk.
- `MediaMetadataRetriever` for manual-scan metadata (supports whatever Android's built-in decoder framework understands: MP3, AAC/M4A, FLAC, OGG/Vorbis, WAV, AMR, MIDI to a degree — codec-dependent, no format is explicitly excluded except at the *tag-editing* layer).
- Tag *editing* (not scanning) via `TagHelper` explicitly excludes `wma` and `wav` (flaky in jaudiotagger) — see Section 1b below.

### Permissions
- `getPermissionToRequest()` (helpers/Constants.kt:149): `PERMISSION_READ_MEDIA_AUDIO` (maps to `android.permission.READ_MEDIA_AUDIO`) if `isTiramisuPlus()` (API 33+), else `PERMISSION_WRITE_STORAGE` (legacy `WRITE_EXTERNAL_STORAGE`).
- Requested in `MainActivity.onCreate()` via `handlePermission(getPermissionToRequest()) { granted -> if (granted) initActivity() else { toast + finish() } }` — a hard requirement; the app refuses to run without it.
- Manifest declares: `READ_MEDIA_AUDIO`, `WRITE_EXTERNAL_STORAGE` (maxSdkVersion=32), `POST_NOTIFICATIONS` (for scan/update notifications), plus `android:requestLegacyExternalStorage="true"` (soft opt-out of scoped storage on API 29, ignored on 30+).
- **No `MANAGE_EXTERNAL_STORAGE`** (all-files-access) is requested anywhere — the manual filesystem walk relies on legacy storage permission/READ_MEDIA_AUDIO scope, which on modern Android limits raw `File` access largely to what MediaStore itself would already know about; this is consistent with the comment that the manual scan exists mainly to catch freshly-`adb push`ed files before MediaStore re-indexes them, not to bypass scoped storage entirely.

### Folder exclusion
- `config.excludedFolders: MutableSet<String>` (SharedPreferences string set, key `EXCLUDED_FOLDERS`), managed via `Config.addExcludedFolder/addExcludedFolders/removeExcludedFolder`, normalized by trimming trailing `/`.
- Applied twice: (a) filtering MediaStore results by `track.path.getParentPath() in excludedFolders`, and (b) filtering the manual filesystem walk by checking both the exact path and its parent against `excludedPaths` (which is `pathsToIgnore + config.excludedFolders` — see `findTracksManually`).
- UI: `fragments/ExcludedFoldersFragment.kt` + `adapters/ExcludedFoldersAdapter.kt` (not read in depth, but referenced).
- `AudioHelper.getAllFolders()` also filters excluded folders out of the folder listing at read time as a second safety net.

---

## 2. Storage system

**Room** (SQLite via AndroidX Room, version `2.8.4`), single database class `data/MusicDatabase.kt`:

```kotlin
@Database(
    entities = [Track::class, Playlist::class, QueueItem::class, Artist::class, Album::class, Genre::class, PlayStats::class],
    version = 18
)
abstract class MusicDatabase : RoomDatabase()
```
- DB file name: `"songs.db"`.
- Singleton via double-checked locking `getInstance(context)`; `destroyInstance()` for tests/teardown.
- Custom single-thread `queryExecutor` (`Executors.newSingleThreadExecutor()`) passed via `.setQueryExecutor(...)` — serializes all Room queries onto one background thread (not Room's default thread pool), presumably to avoid write races given how much manual SQL/cross-table sync happens (see `syncMetadataFromCanonicalTracks`).
- 17 sequential `Migration` objects registered (`MIGRATION_1_2` ... `migration17To18(...)`), i.e. this schema has evolved incrementally since v1 — full migration history lives in `data/MusicDatabaseMigrations.kt` (not fully read, but confirms real hand-written migrations rather than `fallbackToDestructiveMigration`).
- `.addCallback(ManagedPlaylistsCallback(favoritesTitle, historyTitle, mostPlayedTitle))` — a Room `RoomDatabase.Callback` that seeds the built-in playlists (Favorites/History/Most Played) on creation/open.

### Entities / schema (all in `models/`)

**Track** (`@Entity(tableName = "tracks", indices = [Index(["media_store_id","playlist_id"], unique = true)])`):
```
id: Long (PK, autoGenerate)
media_store_id: Long        // MediaStore _ID, or hashCode()-derived for manual-scan/unknown tracks
title, artist, path, album, genre: String
duration: Int (seconds)
cover_art: String            // content:// URI string, not a blob
playlist_id: Int             // 0 = canonical row; >0 = row is this track's membership in playlist N
track_id: Int?               // position within album
disc_number: Int?
folder_name: String
album_id, artist_id, genre_id: Long
year: Int
date_added: Int              // epoch seconds
order_in_playlist: Int
flags: Int = 0               // bit 1 = FLAG_MANUAL_CACHE, bit 2 = FLAG_IS_CURRENT (transient/queue-only)
date_added_to_playlist: Int = 0
```
Important design point: **a track is denormalized/duplicated into one row per playlist it belongs to** (playlist_id=0 is the canonical/library row; membership in "All tracks", Favorites, History, Most Played, or a user playlist is a *separate row* with the same media_store_id but a different playlist_id, and its own `date_added_to_playlist`/`order_in_playlist`). This is why `SongsDao.syncMetadataFromCanonicalTracks()` exists — a hand-written UPDATE that copies mutable fields (title, artist, album, path, duration, cover_art, genre, genre_id, disc_number, year, folder_name, album_id, artist_id) from each canonical (playlist_id=0) row onto every other row sharing its media_store_id, run after every scan (`AudioHelper.syncPlaylistTrackMetadata()`, called from `MediaScanner.updateAllDatabases()`) so retagged/renamed files don't go stale in playlists.

`Track.playCount` is `@Ignore` (not persisted on the entity) — actual play counts live in the separate `PlayStats` table and are joined in at query time (`AudioHelper.withPlayCounts()`).

**Album** (`@Entity("albums")`): `id, artist, title, cover_art, year, track_cnt, artist_id, date_added`.

**Artist** (`@Entity("artists")`): `id, title, album_cnt, track_cnt, album_art`.

**Genre** (`@Entity("genres")`): `id, title, track_cnt, album_art`.

**Folder**: plain Kotlin data class (`title, trackCount, path`) — **not a Room entity**, it's a runtime-only aggregation computed by `AudioHelper.getAllFolders()` via grouping the tracks table by `folder_name` (no dedicated folders table).

**Playlist** (`@Entity("playlists")`): `id (autoGenerate), title`, plus `@Ignore trackCount`. Four fixed, app-managed playlist IDs are hardcoded constants (helpers/Constants.kt): `ALL_TRACKS_PLAYLIST_ID=1`, `FAVORITES_PLAYLIST_ID=2`, `HISTORY_PLAYLIST_ID=3`, `MOST_PLAYED_PLAYLIST_ID=4`. `Playlist.isManaged` (= isAllTracks || isFavorites || isHistory || isMostPlayed) blocks rename/delete for those four; `hasFixedOrder`/`isReorderable` further special-case History/MostPlayed (always newest/most-played first, not user-orderable).

**PlayStats** (`@Entity("play_stats")`): `media_store_id (PK), play_count`. A listen only counts once it plays past `PLAY_COUNT_THRESHOLD_MS = 10_000` ms (see `PlayHistoryRecorder`, not fully read but referenced via `AudioHelper.recordPlayCounted`/`recordPlayStarted`).

**QueueItem** (`@Entity("queue_items", primaryKeys=["track_id"])`): `track_id, track_order, is_current, last_position` — this is literally the playback-queue persistence (see Section 3).

### DAOs (`data/dao/*.kt`)
One interface per entity (`SongsDao`, `AlbumsDao`, `ArtistsDao`, `GenresDao`, `PlaylistsDao`, `PlayStatsDao`, `QueueItemsDao`), plain `@Insert(onConflict=REPLACE)` / `@Query` methods, no Flow/LiveData observed in what was read (reads appear to be one-shot, triggered manually after scans via EventBus + callback, not reactive DB observers).

### Writing scan results (`AudioHelper` in `data/AudioHelper.kt`, wraps the DAOs)
`MediaScanner.updateAllDatabases()`:
```kotlin
context.audioHelper.apply {
    insertTracks(newTracks)      // insertAll with OnConflictStrategy.REPLACE
    insertAlbums(newAlbums)
    insertArtists(newArtists)
    insertGenres(newGenres)
    syncPlaylistTrackMetadata()  // propagate canonical row → playlist copies
}
updateAllTracksPlaylist()        // add newly-scanned, non-excluded tracks to the "All tracks" playlist,
                                  // skipping any media_store_id the user explicitly removed
                                  // (config.tracksRemovedFromAllTracksPlaylist)
```
`cleanupDatabase()` (after both MediaStore + manual scans) computes the diff between what's now known (`newTracks`/`newAlbums`/etc, kept in memory across the whole scan run) and what's in the DB, deleting rows for tracks/albums/artists/genres that vanished. Artists get an update-in-place path too: if track/album counts changed, it deletes and reinserts.

### Tag reading/writing library
- **Reading** (during scan): `android.media.MediaMetadataRetriever` — a **platform API**, not a third-party library. No jaudiotagger/TagLib usage for scanning.
- **Writing/editing** (user-initiated tag edit dialog, `dialogs/EditDialog.kt` + `data/TagHelper.kt`): **jaudiotagger** (`net.jthink:jaudiotagger:3.0.1`).
  - `TagHelper.writeTag(track, newArtist, newTitle, newAlbum)`: copies the file out of MediaStore via `contentResolver.openInputStream(uri)` into a temp file (`activity.getTempFile("music", filename)`), edits tags with jaudiotagger (`AudioFileIO.read(temp)`, `tag.setField(FieldKey.TITLE/ARTIST/ALBUM)`, `audioFile.commit()`), writes the temp file's bytes back through `contentResolver.openOutputStream(uri, "w")`, deletes the temp file, then also updates MediaStore's own ContentValues (`TITLE/ARTIST/ALBUM`) directly via `contentResolver.update()` so MediaStore's cache reflects the edit immediately (avoiding a full rescan for a metadata edit).
  - Excludes `wma`/`wav` from editing (`EXCLUDED_EXTENSIONS`, jaudiotagger considered flaky on those).
  - Container-specific tag types selected in `createTag(extension)`: `.ogg`→`VorbisCommentTag`, `.m4a`→`Mp4Tag`, `.flac`→`FlacTag`, else→`ID3v24Tag`.
  - `TagOptionSingleton.getInstance().isAndroid = true` set once in `init{}` — required jaudiotagger Android compatibility flag.

### Playlist import/export
`data/PlaylistIO.kt`:
- `M3uExporter`: writes an M3U file by hand (`#EXTM3U` header, `#EXTINF:<duration>,<artist> - <title>` + path per track) using Fossify Commons' `writeLn`.
- `M3uImporter`: parses `.m3u`/`.m3u8` via **`com.github.bjoernpetersen:m3u-parser:1.4.0`** (`net.bjoernpetersen.m3u.M3uParser`), then matches each `M3uEntry` against the existing tracks table by path or title, and calls `audioHelper.addTracksToPlaylist(playlistId, matchedTracks)`.

### Cover art
No caching/blob storage of images at all — `coverArt` is always a `content://media/external/audio/albumart/<id>` URI string (or empty), resolved lazily by the image-loading layer (e.g. Glide/Coil, not explored here) at render time; the manual-scan path leaves `coverArt = ""` since there's no MediaStore-backed album id to build a URI from.

---

## 3. Restoring / caching system (avoiding full rescans, incremental behavior)

There is **no dedicated incremental/delta scanning engine, no WorkManager periodic worker, and no ContentObserver on MediaStore** anywhere in this codebase (verified by grep — zero hits for `WorkManager`/`ContentObserver`/`registerContentObserver`). The "caching" strategy is simpler and consists of these layers:

1. **Room DB as the persistent cache.** The `tracks`/`albums`/`artists`/`genres` tables in `songs.db` are the durable local cache; the UI reads from Room (via `AudioHelper.getAllTracks()` etc.), not by querying MediaStore live on every screen open. A full `MediaScanner.scan()` re-fetches everything from MediaStore and diffs it (see below) but that's inexpensive because MediaStore itself is already indexed — the app doesn't re-parse audio files on every launch, it just re-queries the already-fast MediaStore ContentProvider.
2. **Scan triggers, not a background scheduler:**
   - `MainActivity.initActivity()` (called immediately after the storage permission is granted) calls `mediaScanner.scan()` unconditionally on every app open — this is the sole "on launch" resync point, run in the background so the DB-backed fragments can show instantly and update once the scan diff lands.
   - `refreshAllFragments(showProgress = config.appRunCount == 1)` — shows a *visible* progress bar+notification only on the very first run ever (`config.appRunCount == 1`, a Fossify Commons counter incremented by `appLaunched()`); subsequent launches scan silently.
   - Manual "Rescan" menu item (`R.id.rescan_media` in `MainActivity.setupOptionsMenu()`) calls `refreshAllFragments(showProgress = true)` on demand.
   - No periodic background rescanning; the app only rescans when it's actually in the foreground being opened/interacted with.
3. **Full-scan-but-diffed cleanup**, not timestamp-based incremental detection. `MediaScanner` doesn't compare `last_modified`/`date_added` against a stored "last scan time" to skip unchanged files — every scan re-queries MediaStore fully (cheap, since it's a ContentProvider index) and re-walks the filesystem manually only when not already known via MediaStore (`pathsToIgnore = trackPaths` filter in `findTracksManually`, so previously-seen-by-MediaStore paths are never re-parsed with `MediaMetadataRetriever`). `cleanupDatabase()` is the only "invalidation" logic: it deletes DB rows whose `media_store_id`/`path` are no longer present in the freshly gathered `newTracks`/`newAlbums`/etc — i.e., it's a **replace-and-reconcile** model, not incremental deltas.
4. **`date_added`/`lastModified` usage is per-file metadata, not a scan watermark.** `File.lastModified()` is only used to populate a track's own `date_added` field for manual-scan tracks (Section 1) — there is no persisted "last full scan timestamp" or per-folder mtime cache checked before deciding whether to rescan a folder.
5. **Explicit user removals are remembered across rescans.** `config.tracksRemovedFromAllTracksPlaylist: MutableSet<String>` (media_store_id strings) prevents a track the user explicitly removed from the "All tracks" playlist from being silently re-added on the next scan (`updateAllTracksPlaylist()` filters on this set) — this is the one piece of real "don't undo what I did" persistence around rescans.
6. **Queue restoration across launches** is handled separately from library scanning, via `QueueItem` rows: `AudioHelper.getQueuedTracksLazily(callback)` reads `queueDAO.getCurrent()` + `queueDAO.getAll()` and immediately calls back with just the current track (fast first paint) before resolving the full queue — this is how playback state (what was playing, at what position) survives an app restart, independent of the media scan. `resetQueue(items, currentTrackId, startPosition)` / `clearQueue()` manage this table; an empty queue (`clearQueue()`) means "nothing playing" on next launch.
7. **Play history / most-played "restoring"**: `PlayStats` (per-track play_count) and the History/MostPlayed managed playlists persist listening behavior indefinitely in Room; there's no expiry/pruning logic observed for these (only cleaned up if the underlying track itself disappears via `AudioHelper.deleteTrack` → `playStatsDAO.deletePlayStats`).
8. **No explicit backup/restore-specific logic** was found (no `BackupAgent`, no special handling of Android Auto Backup triggering a rescan) beyond the standard `android:allowBackup="true"` manifest flag — Room's `songs.db` would be included in Android's default auto-backup unless excluded via a backup rules XML (none was found in the manifest reference, meaning the DB likely round-trips through Android backup as-is, and any staleness after a long absence is simply corrected by the next foreground `scan()` call as described above, not by dedicated "just restored" detection).

**Summary for Sona's design:** the "caching" model here is best characterized as *stateless full-rescan-with-diff-on-every-launch*, leaning entirely on MediaStore's own indexing speed rather than building bespoke incremental-scan bookkeeping. If Sona wants faster/smarter incremental scanning (e.g. only touching folders whose mtime changed since a stored watermark), that would be new work, not something to port from this app.

---

## 4. In-app update system

Files: `update/AppUpdate.kt`, `update/UpdateChecker.kt`, `update/UpdateDownloader.kt`, `update/UpdateInstaller.kt`, `update/UpdateService.kt`, `update/UpdateState.kt`, `dialogs/AppUpdateDialog.kt`.

This is a **fully custom GitHub-raw-file update mechanism** — not the Play Store's in-app update API, not F-Droid's repo mechanism.

### Manifest source of truth
`UpdateChecker.MANIFEST_URL = "https://raw.githubusercontent.com/LhacenMed/Music-Player/main/version.json"` — a static JSON file at the root of the GitHub repo (fetched as a raw file, not via GitHub's Releases API). Expected shape (`AppUpdate.fromJson`):
```json
{ "versionCode": 123, "versionName": "1.2.3", "apkUrl": "https://.../release.apk", "notes": "..." }
```

### Flow
1. **Check** (`UpdateChecker.checkAsync`, called from `MainActivity.checkForAppUpdate()` on every launch unless `BuildConfig.DEBUG` or `!config.checkForUpdates` [a user-toggleable Settings preference, default true]):
   - Opens an `HttpURLConnection` (plain `java.net` — no Retrofit/OkHttp) to the manifest URL with a 15s connect/read timeout, reads the body as text, parses via `org.json.JSONObject`.
   - Returns the parsed `AppUpdate` only `if (it.versionCode > BuildConfig.VERSION_CODE)`, else null — comparison is purely local versionCode vs remote versionCode.
   - Drives a shared, synchronously-readable state machine: `UpdateService.state: UpdateState` (sealed class: `Idle, Checking, Available(update), Downloading(update, percent), Downloaded(update, apkFile), Error(update, message)`), broadcast via `EventBus` (`Events.UpdateStateChanged`) so any open `AppUpdateDialog` re-renders.
   - Guards against re-checking while a download is already `Downloading`/`Downloaded` (returns that state's update instead of resetting it).
2. **Prompt**: `AppUpdateDialog` shown from `MainActivity` when `checkAsync` returns non-null. Shows version name + notes; the primary button either starts the download or triggers install, depending on `UpdateService.state`.
3. **Download** (`update/UpdateService.kt`, a **foreground `Service`** with `foregroundServiceType="dataSync"`, started via `ContextCompat.startForegroundService` from the dialog so the download survives dialog dismissal/backgrounding):
   - `UpdateDownloader.download()` opens the APK URL with manual redirect-following (`openWithRedirects`, up to 5 hops) because `HttpURLConnection` doesn't auto-follow redirects across host changes — needed because **GitHub Release asset URLs 302-redirect to a different CDN host** (this confirms `apkUrl` is expected to point at a GitHub Releases asset even though the manifest itself is fetched from raw.githubusercontent.com, not the Releases API).
   - Streams to a stable path: `File(context.cacheDir, "updates").mkdirs()/update.apk` (always overwritten, one slot).
   - Reports progress via a percent callback, throttled to only post when the whole-percent value changes; updates a persistent notification (`NotificationHelper.createUpdateNotification`) and `UpdateService.state = Downloading(update, percent)`.
   - On success: state → `Downloaded(update, apkFile)`, notification switches to "tap to install", service stops foreground+self (`ServiceCompat.stopForeground(STOP_FOREGROUND_DETACH)`).
   - On failure: deletes the partial file, state → `Error(update, message)`, cancels notification.
4. **Install** (`UpdateInstaller.kt`):
   - `canInstallPackages(context)`: true if `SDK_INT < O` or `packageManager.canRequestPackageInstalls()`.
   - If not permitted: shows a toast and opens `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` for this package (`requestInstallPermissionIntent`) so the user can grant "install unknown apps" specifically for this app.
   - Otherwise: builds an install `Intent(ACTION_VIEW)` with `FileProvider.getUriForFile(context, "$packageName.provider", apkFile)`, MIME `application/vnd.android.package-archive`, flags `FLAG_GRANT_READ_URI_PERMISSION | FLAG_ACTIVITY_NEW_TASK`, and starts it — handing off to the system package installer UI.

### Permissions/manifest wiring for updates
- `<uses-permission android:name="android.permission.INTERNET" />` — fetch manifest + APK.
- `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` — required for `canRequestPackageInstalls()`/unknown-sources install flow.
- `<service android:name=".update.UpdateService" android:exported="false" android:foregroundServiceType="dataSync" />`.
- `FileProvider` declared once (`androidx.core.content.FileProvider`, authority `${applicationId}.provider`, `grantUriPermissions="true"`, paths from `@xml/provider_paths`) and reused for both the update APK and (presumably) other file-sharing needs (playlist export, tag-edit temp files).
- `POST_NOTIFICATIONS` permission (declared for the media-scan notification) is also what backs the update download's foreground notification.

### Notes for Sona
- This is a **from-scratch bespoke updater**, not GitHub Releases API (`api.github.com/repos/.../releases/latest`) — it reads a hand-maintained `version.json` at the repo root via the raw-content CDN, which means whoever ships Sona updates this way would need a CI step that regenerates `version.json` with the new versionCode/versionName/apkUrl/notes on each release, and separately upload the APK to a GitHub Release (or any URL) that `apkUrl` points to.
- No signature/checksum verification of the downloaded APK is performed before installing — relies entirely on Android's package installer verifying the APK signature matches the installed app's signing key.
- Debug builds never check for updates (`BuildConfig.DEBUG` short-circuit) — worth replicating so devs aren't nagged.
