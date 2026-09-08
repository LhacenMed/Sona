# Fossify Music Player — fast cold-start & permission-flash research

Checkout root: `C:\Users\lhacenmed\AndroidStudioProjects\Music`
Package: `org.fossify.musicplayer`

---

## 1. Cold-start data restoration order

File: `app/src/main/kotlin/org/fossify/musicplayer/activities/MainActivity.kt`

### `onCreate` (verbatim, lines 49-79)

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(binding.root)
    appLaunched(BuildConfig.APPLICATION_ID)
    setupOptionsMenu()
    refreshMenuItems()
    // Each tab's list pads itself from the insets the playback sheet hands down, which already
    // carry the navigation bar and the keyboard, so the column around them must stay unpadded
    // or the two would stack. Only the sleep timer strip, which floats over the lists rather
    // than scrolling with them, still has to be moved clear of the navigation bar.
    setupEdgeToEdge(moveBottomSystem = listOf(binding.sleepTimerHolder))
    storeStateVariables()
    setupTabs()
    setupCollapsingAppBar()
    setupLibraryShortcuts()
    setupPlaybackSheet()

    handlePermission(getPermissionToRequest()) {
        if (it) {
            initActivity()
        } else {
            toast(org.fossify.commons.R.string.no_storage_permissions)
            finish()
        }
    }

    volumeControlStream = AudioManager.STREAM_MUSIC
    checkWhatsNewDialog()
    checkAppOnSDCard()
    checkForAppUpdate()
}
```

**Key finding: the permission check gates everything.** `initFragments()` (which creates the `ViewPagerAdapter` and therefore instantiates the tab fragments) is called only inside `initActivity()`, which itself only runs from the `handlePermission(...) { if (it) initActivity() }` callback. So no fragment exists, and no Room read happens, until the permission callback fires with `granted == true`. There is no separate "bind UI to already-persisted Room data first, then check permission" path — permission-check comes strictly before any UI/data binding in this codebase.

Note `getPermissionToRequest()` (`app/src/main/kotlin/org/fossify/musicplayer/helpers/Constants.kt:149`):
```kotlin
fun getPermissionToRequest() = if (isTiramisuPlus()) PERMISSION_READ_MEDIA_AUDIO else PERMISSION_WRITE_STORAGE
```
`handlePermission` is a Fossify Commons `BaseSimpleActivity` extension (not vendored in this checkout — see caveat at the end of section 3). Commons' `handlePermission` conventionally checks `hasPermission()` synchronously first and invokes the callback immediately (same frame) if already granted, only showing the system permission dialog if not. Because the source isn't in this checkout, I can't quote it, but the call-site behavior in `MainActivity` matches: if permission is already granted, `initActivity()` should run essentially synchronously off `onCreate`, with no dialog and no visible gap. If the clone's `handlePermission`-equivalent instead does an async/deferred check (e.g. round-trips through a coroutine, `ActivityResultContracts` callback that posts to a later frame, or defaults to "not granted" until a callback resolves), that would explain both the flash of "permission not granted" and the slower first paint — the UI would sit ungated for a frame or more before the callback resolves true.

### `initActivity()` (lines 189-198)

```kotlin
private fun initActivity() {
    bus = EventBus.getDefault()
    bus!!.register(this)
    // trigger a scan first so that the fragments will accurately reflect the scanning state
    mediaScanner.scan()
    initFragments()
    binding.sleepTimerStop.setOnClickListener { stopSleepTimer() }

    refreshAllFragments()
}
```

Order here is explicit and commented: **`mediaScanner.scan()` is called with no callback FIRST** (fire-and-forget — sets `scanning = true` synchronously via the `@Synchronized fun scan()`, see MediaScanner.kt line 63, before spawning the background thread), **then** `initFragments()` builds the `ViewPagerAdapter` (which triggers each fragment's own `setupFragment()` from its `bind`/adapter creation lifecycle), **then** `refreshAllFragments()` is called, which is the thing that actually re-reads Room and updates the placeholder/scanning text (`refreshAllFragments` -> `mediaScanner.scan(progress=..., callback=...)` -> callback runs `getAllFragments().forEach { it.setupFragment(this) }`).

So: fragments are instantiated and immediately query Room (`context.audioHelper.getAllTracks()` etc. — see section 1 continuation below) on a background thread via `ensureBackgroundThread`, independent of whether the scan has produced anything yet. Room already contains whatever was persisted on a previous run, so **the first paint shows the previously-persisted library instantly**, while the scan (which was kicked off first, and runs concurrently on its own background thread) updates the same tables and pings `setupFragment()` again via the `refreshAllFragments` -> `scan(callback)` mechanism once MediaStore is read (`complete=false`) and again when the manual walk finishes (`complete=true`).

### `refreshAllFragments` (lines 200-225)

```kotlin
private fun refreshAllFragments(showProgress: Boolean = config.appRunCount == 1) {
    if (showProgress) {
        binding.loadingProgressBar.show()
    }

    handleNotificationPermission { granted ->
        mediaScanner.scan(progress = showProgress && granted) { complete ->
            runOnUiThread {
                getAllFragments().forEach {
                    it.setupFragment(this)
                }

                if (complete) {
                    binding.loadingProgressBar.hide()
                    withPlayer {
                        if (currentMediaItem == null) {
                            maybePreparePlayer()
                        } else {
                            sendCommand(CustomCommands.RELOAD_CONTENT)
                        }
                    }
                }
            }
        }
    }
}
```

This confirms the `complete=false` / `complete=true` two-stage callback (see section 2) drives `setupFragment()` re-reads at both points, on the UI thread.

### Is there a loading/scanning state distinct from "no permission"?

There is **no dedicated "no permission" empty state at all** in the fragments. `TracksFragment.setupFragment` (lines 30-77) and `AlbumsFragment.setupFragment`/`gotAlbums` (lines 36-79) both only distinguish "scanning" vs. "no items found":

```kotlin
// TracksFragment.kt
override fun setupFragment(activity: BaseSimpleActivity) {
    ensureBackgroundThread {
        tracks = context.audioHelper.getAllTracks()
        ...
        activity.runOnUiThread {
            val scanning = activity.mediaScanner.isScanning()
            binding.tracksPlaceholder.text = if (scanning) {
                context.getString(R.string.loading_files)
            } else {
                context.getString(org.fossify.commons.R.string.no_items_found)
            }
            binding.tracksPlaceholder.beVisibleIf(tracks.isEmpty())
            ...
        }
    }
}
```

```kotlin
// AlbumsFragment.kt
private fun gotAlbums(activity: BaseSimpleActivity, cachedAlbums: ArrayList<Album>) {
    albums = cachedAlbums
    activity.runOnUiThread {
        val scanning = activity.mediaScanner.isScanning()
        binding.albumsPlaceholder.text = if (scanning) {
            context.getString(R.string.loading_files)
        } else {
            context.getString(org.fossify.commons.R.string.no_items_found)
        }
        binding.albumsPlaceholder.beVisibleIf(albums.isEmpty())
        ...
    }
}
```

The conditional is purely: `mediaScanner.isScanning()` (a real boolean flag, `MediaScanner.isScanning()` returning the private `scanning` var) picks the placeholder *text* between "Loading files…" (`R.string.loading_files`) and "No items found" (commons' `R.string.no_items_found`); the placeholder's *visibility* is decided independently by `tracks.isEmpty()` / `albums.isEmpty()`. **There is no branch anywhere in these fragments that reads a permission-granted boolean or shows a "permission not granted" string.** Since fragments are only ever constructed after `handlePermission` has already resolved `true` (see `onCreate` above), Fossify structurally cannot show a permission-related message from inside the tab UI — a "permission not granted" flash in the clone must come from the clone's own code path, not from mirroring this logic.

Layout, e.g. `app/src/main/res/layout/fragment_tracks.xml`:
```xml
<org.fossify.commons.views.MyTextView
    android:id="@+id/tracks_placeholder"
    ...
    android:text="@string/loading_files"
    ... />
```
The placeholder's *default XML text* is `@string/loading_files` — so even before the first `setupFragment()` callback lands, if the placeholder were ever visible (e.g. list not yet populated) it would statically read "Loading files…", never a permission string. Same pattern applies to `fragment_albums.xml`, `fragment_artists.xml`, `fragment_genres.xml`, `fragment_folders.xml` (all found via grep on `no_items_found`/placeholder ids, not all individually opened, but they follow the identical `MyViewPagerFragment` subclass pattern seen in `TracksFragment`/`AlbumsFragment`).

**Conclusion for section 1:** Fossify does NOT bind UI to persisted Room data *before* the permission check — the permission check is strictly first and gates fragment construction entirely. What makes Fossify feel instant is (a) `handlePermission` resolving synchronously/immediately when permission is already granted (no visible gate at all in the already-granted case, which is the common case after first run), and (b) once fragments exist, each fragment's `setupFragment()` reads directly from Room (already populated from the previous run) on a background thread and paints immediately, fully decoupled from whether the concurrently-running scan has produced anything yet. The "permission not granted" flash in the clone is most likely caused by the clone's UI briefly rendering before its own permission-check callback resolves, or by the clone showing a real "permission" UI state that Fossify's fragments never have in the first place.

---

## 2. MediaScanner persist-order

File: `app/src/main/kotlin/org/fossify/musicplayer/data/MediaScanner.kt`

### `scan()` (verbatim, lines 53-89)

```kotlin
@Synchronized
fun scan(progress: Boolean = false, callback: ((complete: Boolean) -> Unit)? = null) {
    onScanComplete = callback
    showProgress = progress
    maybeShowScanProgress()

    if (scanning) {
        return
    }

    scanning = true
    ensureBackgroundThread {
        try {
            scanMediaStore()
            if (isQPlus()) {
                onScanComplete?.invoke(false)
                scanFilesManually()
            }

            cleanupDatabase()
            onScanComplete?.invoke(true)
        } catch (ignored: Exception) {
        } finally {
            if (showProgress && newTracks.isEmpty()) {
                context.toast(org.fossify.commons.R.string.no_items_found)
            }

            newTracks.clear()
            newAlbums.clear()
            newArtists.clear()
            newGenres.clear()
            mediaStorePaths.clear()
            scanning = false
            hideScanProgress()
        }
    }
}
```

### `scanMediaStore()` (verbatim, lines 97-151)

```kotlin
private fun scanMediaStore() {
    newTracks += getTracksSync()
    newArtists += getArtistsSync()
    newAlbums += getAlbumsSync(newArtists)
    newGenres += getGenresSync()
    mediaStorePaths += newTracks.map { it.path }
    assignGenreToTracks()

    // ignore tracks from excluded folders and tracks with no albums, artists
    val albumIds = newAlbums.map { it.id }
    val artistIds = newArtists.map { it.id }
    val excludedFolders = config.excludedFolders
    val tracksToExclude = mutableSetOf<Track>()
    for (track in newTracks) {
        if (track.path.getParentPath() in excludedFolders) {
            tracksToExclude.add(track)
            continue
        }

        if (track.albumId !in albumIds || track.artistId !in artistIds) {
            tracksToExclude.add(track)
        }
    }

    newTracks.removeAll(tracksToExclude)

    // update album, track count if any tracks were excluded
    for (album in newAlbums) {
        val tracksInAlbum = newTracks.filter { it.albumId == album.id }
        album.trackCnt = tracksInAlbum.size
        if (album.trackCnt > 0) {
            album.dateAdded = tracksInAlbum.first().dateAdded
        }
    }

    for (artist in newArtists) {
        artist.trackCnt = newTracks.filter { it.artistId == artist.id }.size
        val albumsByArtist = newAlbums.filter { it.artistId == artist.id }
        artist.albumCnt = albumsByArtist.size
        artist.albumArt = albumsByArtist.firstOrNull { it.coverArt.isNotEmpty() }?.coverArt.orEmpty()
    }

    for (genre in newGenres) {
        val genreTracks = newTracks.filter { it.genreId == genre.id }
        genre.trackCnt = genreTracks.size
        genre.albumArt = genreTracks.firstOrNull { it.coverArt.isNotEmpty() }?.coverArt.orEmpty()
    }

    // remove invalid albums, artists
    newAlbums.removeAll { it.trackCnt == 0 }
    newArtists.removeAll { it.trackCnt == 0 || it.albumCnt == 0 }
    newGenres.removeAll { it.trackCnt == 0 }

    updateAllDatabases()
}
```

**Confirmed: `scanMediaStore()` calls `updateAllDatabases()` (which persists to Room via `AudioHelper.insertTracks/insertAlbums/insertArtists/insertGenres`) as its own last line, entirely BEFORE `scanFilesManually()` is even invoked.** The order in `scan()` is: `scanMediaStore()` (persists MediaStore results to Room internally) → `onScanComplete?.invoke(false)` (partial callback, fires only on Q+) → `scanFilesManually()` (slow manual filesystem walk via `MediaMetadataRetriever`, persists again via its own `updateAllDatabases()` call at line 176 if it finds anything) → `cleanupDatabase()` → `onScanComplete?.invoke(true)` (final callback).

### The `complete=false` partial callback

```kotlin
scanMediaStore()
if (isQPlus()) {
    onScanComplete?.invoke(false)
    scanFilesManually()
}
```

Yes — this is real and exactly as described in the hypothesis. On Android 10+ (`isQPlus()`), after the fast `scanMediaStore()` finishes (and has already written the MediaStore-derived tracks/albums/artists/genres to Room), the callback fires once with `complete=false`. In `MainActivity.refreshAllFragments`, that callback body is:
```kotlin
mediaScanner.scan(progress = showProgress && granted) { complete ->
    runOnUiThread {
        getAllFragments().forEach {
            it.setupFragment(this)
        }
        if (complete) { ... }
    }
}
```
So `getAllFragments().forEach { it.setupFragment(this) }` runs on **both** the `complete=false` and `complete=true` invocations — meaning fragments re-read Room and repaint once right after the fast MediaStore scan (typically sub-second), and then again after the much slower manual filesystem walk finishes. This is what produces a fast first real paint of scanned data distinct from the "already-persisted from last run" first paint described in section 1.

On pre-Q devices (`!isQPlus()`), the `if (isQPlus())` block is skipped entirely, so `scanFilesManually()` never runs and only the single final `onScanComplete?.invoke(true)` fires — this is irrelevant to any modern device but worth noting for fidelity.

---

## 3. MediaStore cursor-reading performance

### `context.queryCursor` extension — NOT present in this checkout

I searched the full checkout (`grep -r "fun queryCursor"` and `grep -r "queryCursor"`) and confirmed: `queryCursor` is called from `MediaScanner.kt` and `RoomHelper.kt` but its **definition lives in the Fossify Commons library, which is consumed here only as a compiled remote Gradle dependency** (`app/build.gradle.kts` line 150: `implementation(libs.fossify.commons)`). There is no vendored/local commons module in this repo, and I checked the Gradle dependency cache (`~/.gradle/caches/modules-2/files-2.1/org.fossify` and the AGP resource-transform cache under `~/.gradle/caches/9.6.1/transforms/*/transformed/org.fossify.commons*`) — only compiled `.aar`/`.class`/resource artifacts exist, **no `-sources.jar`**. I cannot quote `queryCursor`'s actual body or confirm from source whether it caches `cursor.getColumnIndex(...)` once per column. This must be stated explicitly rather than guessed, per the task instructions — if the clone's own `queryCursor`-equivalent looks up column indices per-row, that would still be a real perf bug worth fixing, but it can't be attributed to something visibly wrong here since Fossify's own call sites don't touch column indices directly (see below), and the extension body legitimately isn't available in this checkout.

### Per-row field extraction in `MediaScanner.kt`

Fossify's own per-row code (inside the `queryCursor(...) { cursor -> ... }` lambda) never calls `cursor.getColumnIndex`/`getColumnIndexOrThrow` directly at all — it always goes through further Commons cursor extensions (`cursor.getLongValue(COLUMN)`, `cursor.getStringValue(COLUMN)`, `cursor.getIntValue(COLUMN)`, `cursor.getIntValueOrNull(COLUMN)`), e.g. in `getTracksSync()` (lines 242-294):

```kotlin
context.queryCursor(uri, projection.toTypedArray(), showErrors = true) { cursor ->
    val id = cursor.getLongValue(Audio.Media._ID)
    val title = cursor.getStringValue(Audio.Media.TITLE)
    val duration = cursor.getIntValue(Audio.Media.DURATION) / 1000
    var trackId = cursor.getStringValue(Audio.Media.TRACK)?.firstNumber()
        ?: cursor.getIntValueOrNull(Audio.Media.TRACK)
    val path = cursor.getStringValue(Audio.Media.DATA).orEmpty()
    val artist = cursor.getStringValue(Audio.Media.ARTIST) ?: MediaStore.UNKNOWN_STRING
    ...
}
```

These `getXValue(columnName: String)` helpers are themselves Commons extension functions (also not vendored here — same caveat as `queryCursor` above) that almost certainly call `cursor.getColumnIndexOrThrow(columnName)` internally, once per call, **by column name string, on every row** (since the call site passes the string constant `Audio.Media.TITLE` etc. each time, not a pre-resolved integer index). I cannot see the extension body to confirm whether it internally caches anything (e.g. via a per-cursor column-index map), but structurally, since `queryCursor`'s lambda receives only the raw `Cursor` — not any pre-computed index map passed alongside it — **there is no application-level column-index caching anywhere in `MediaScanner.kt`/`RoomHelper.kt` itself**: whatever caching (if any) exists must happen inside Commons' `getStringValue`/`getLongValue`/`getIntValue` implementations, which are outside this checkout and cannot be quoted or verified here. This is a genuine gap in what this checkout can prove — flag it as such rather than asserting caching exists or doesn't.

`getArtistsSync()` (299-321), `getAlbumsSync()` (323-360), and `getGenresSync()` (362-377) all follow the identical pattern — column values are pulled by name via `cursor.getXValue(COLUMN_NAME)` inside the `queryCursor` row lambda, never via a locally-cached `Int` index.

---

## 4. Database write batching

File: `app/src/main/kotlin/org/fossify/musicplayer/data/AudioHelper.kt`

### Relevant methods (verbatim)

```kotlin
fun insertTracks(tracks: List<Track>) {
    context.tracksDAO.insertAll(tracks)
}
...
fun insertArtists(artists: List<Artist>) {
    context.artistDAO.insertAll(artists)
}
...
fun insertAlbums(albums: List<Album>) {
    context.albumsDAO.insertAll(albums)
}
...
fun insertGenres(genres: List<Genre>) {
    genres.forEach {
        context.genresDAO.insert(it)
    }
}
```

Note `insertGenres` is NOT even a batch call — it loops and calls the single-item `genresDAO.insert(it)` once per genre (no `insertAll` exists on `GenresDao`).

### Caller: `MediaScanner.updateAllDatabases()` (verbatim, lines 180-191)

```kotlin
private fun updateAllDatabases() {
    context.audioHelper.apply {
        insertTracks(newTracks)
        insertAlbums(newAlbums)
        insertArtists(newArtists)
        insertGenres(newGenres)
        // The insert above only refreshed each track's canonical row; carry the same fields
        // into its copies in every other playlist before cleanupDatabase() reads them back.
        syncPlaylistTrackMetadata()
    }
    updateAllTracksPlaylist()
}
```

### DAO signatures (verbatim, from `app/src/main/kotlin/org/fossify/musicplayer/data/dao/*.kt`)

`SongsDao.kt`:
```kotlin
@Dao
interface SongsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(track: Track)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tracks: List<Track>)
    ...
    @Query("UPDATE tracks SET title = (...) WHERE playlist_id != 0 AND EXISTS (...)")
    fun syncMetadataFromCanonicalTracks()
}
```

`AlbumsDao.kt`:
```kotlin
@Dao
interface AlbumsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(album: Album): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(albums: List<Album>)
    ...
}
```

`ArtistsDao.kt`:
```kotlin
@Dao
interface ArtistsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(artist: Artist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(artists: List<Artist>)
    ...
}
```

`GenresDao.kt`:
```kotlin
@Dao
interface GenresDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(genre: Genre): Long
    ...
    // no insertAll
}
```

**I grepped the entire `app/src/main/kotlin` tree for `@Transaction` and found zero matches — no DAO method or AudioHelper/MediaScanner method in the whole app uses Room's `@Transaction` annotation.** None of these DAO methods are `suspend` either; they are plain blocking calls invoked sequentially from a background thread (`ensureBackgroundThread` in `MediaScanner.scan()`), not wrapped in any single atomic transaction spanning tracks+albums+artists+genres.

**Implication:** each `@Insert(onConflict = REPLACE)` with a `List<T>` argument does compile to a single Room-generated SQL loop inside one implicit small transaction per DAO call (Room always wraps a single `@Insert(List<T>)` call in its own transaction), so `insertTracks(newTracks)` alone is atomic, and `insertAlbums(newAlbums)` alone is atomic, etc. — but the four calls (`insertTracks` → `insertAlbums` → `insertArtists` → `insertGenres` → `syncPlaylistTrackMetadata`) are FOUR-PLUS separate transactions, not one. A Room `Flow`/`LiveData` observer on, say, the tracks table could see tracks committed before albums/artists are committed, i.e. a genuinely partially-updated multi-table view is possible for the brief window between these calls. However, Fossify's own fragments do NOT observe Room reactively (no `Flow`/`LiveData` — `TracksFragment`/`AlbumsFragment` do one-shot blocking reads via `ensureBackgroundThread { context.audioHelper.getAllTracks() }` triggered explicitly by the `mediaScanner.scan(callback)` two-stage callback described in section 2), so this lack of a spanning `@Transaction` does not itself cause UI flicker in Fossify — the UI only re-reads at the two callback points (`complete=false`, `complete=true`), never mid-write. If the clone instead observes Room via `Flow`/reactive queries directly, that per-table non-atomicity could indeed cause visible flicker there even though it's invisible in Fossify's own poll-on-callback model.

---

## Summary of what to check in the clone

1. **Permission gate**: Fossify's `onCreate` calls `handlePermission(...)` before touching any fragment/adapter; fragments are constructed only inside the `granted == true` branch. If Commons' `handlePermission` resolves synchronously when already-granted, there should be no visible gap or flash at all. If the clone's equivalent check is async (coroutine/callback that resolves on a later frame) or defaults its "granted" state to `false` until resolved, that alone would produce the observed "permission not granted" flash even with real permission already granted.
2. **No permission-state UI exists in Fossify's fragments** — only `scanning ? "loading_files" : "no_items_found"` gates placeholder text, and `list.isEmpty()` gates its visibility. If the clone has fragment/adapter code that branches on a permission boolean to decide what empty-state text to show, that's a Fossify-inconsistent addition and is likely itself the source of the flash — remove it and drive the same "scanning vs. empty" binary Fossify uses.
3. **Persist order confirmed**: `scanMediaStore()` (fast, MediaStore-only) persists to Room via `updateAllDatabases()` before `scanFilesManually()` (slow manual walk) even starts, and a `complete=false` callback fires in between on Q+ devices, causing an intermediate UI repaint. Ensure the clone's manual walk truly runs only after the MediaStore phase's persistence commits, and that the clone exposes/uses an equivalent partial-completion signal.
4. **Column-index caching**: cannot be confirmed either way from this checkout — Commons' `queryCursor`/`getStringValue`/`getLongValue`/etc. extension bodies are not vendored here (only compiled AAR artifacts are present in the Gradle cache, no sources jar). Fossify's own code (`MediaScanner.kt`, `RoomHelper.kt`) always accesses columns by name per row (`cursor.getStringValue(Audio.Media.TITLE)` etc.), never manages a locally cached index itself.
5. **No `@Transaction` anywhere in this app** — multi-table writes in `updateAllDatabases()` are four-plus sequential, separately-committed calls, not one atomic transaction. This is invisible in Fossify because its fragments never observe Room reactively; it would only matter to a clone that uses reactive (`Flow`/`LiveData`) Room queries directly.
