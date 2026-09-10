# YTDLnis — Metadata Tagging & Lyrics System (research for Sona reimplementation)

Source app: `C:\Users\lhacenmed\AndroidStudioProjects\YTDLnis` (package `com.deniscerri.ytdl`).
This is a yt-dlp based downloader with a "music mode" for audio downloads that identifies the
song, resolves rich metadata + lyrics from online catalogues, embeds them into the audio file's
tags via jaudiotagger, and renames the file to "Artist - Title.ext". There is NO local lyrics/tag
database — everything is resolved live over HTTP and written straight into the audio file (or
kept transiently in a Parcelable data class attached to the download item / Room entity).

---

## 1. Tag fetching & editing

### 1.1 Overview / architecture

- Metadata is **not** scraped from the downloaded file or from YouTube alone. It is resolved by
  parsing the video title/uploader into an "artist + song" guess, then querying **online music
  catalogues** (Deezer and iTunes — no MusicBrainz, no Spotify) for the real tags, then merging
  the extended tag fields from per-catalogue "detail" endpoints, and finally **writing** those
  tags into the audio file using **jaudiotagger**.
- Data model: `com.deniscerri.ytdl.database.models.MusicMetadata` (Parcelable data class) —
  title, artist, album, year, albumArtist, genre, label, trackNumber, trackTotal, discNumber,
  isrc, lyrics, coverUrl, source (`MusicSource(provider, trackId, albumId)`). Same file:
  `app/src/main/java/com/deniscerri/ytdl/database/models/MusicMetadata.kt`.
  - `isUsable` = title and artist both non-blank (gate before tagging).
  - `displayName()` = "Artist - Title" (used for both UI and the renamed filename).
  - `details()` = "Album • Year" (UI subtitle).
- This `MusicMetadata` is stored on `AudioPreferences.musicMetadata` (part of the download item /
  Room entity), so it survives from the "download card" UI through to the background worker that
  actually performs the download and tagging.

### 1.2 Catalog/provider layer (fetching tag data)

Package: `app/src/main/java/com/deniscerri/ytdl/util/extractors/music/`

- **`MusicProvider` interface** (`MusicProvider.kt`): contract for a tag catalogue.
  ```kotlin
  interface MusicProvider {
      val id: String
      val name: String
      suspend fun search(query: String, limit: Int): List<MusicMetadata>?   // null = unreachable
      suspend fun details(metadata: MusicMetadata): MusicMetadata = metadata // fill extended tags
  }
  ```
  Also hosts small Gson `JsonObject` extension helpers used by all providers: `str(key)`,
  `obj(key)`, `arr(key)`, and `firstNonBlank(vararg)` — all null/type-safe, degrading to "" on any
  parse issue (loose typing tolerance, since public APIs are inconsistent).

- **`DeezerProvider`** (`DeezerProvider.kt`) — primary catalogue, no API key required.
  - `id = "deezer"`, `name = "Deezer"`.
  - `search`: `GET https://api.deezer.com/search?q=<urlencoded query>&limit=<n>` → JSON array
    field `data`; maps each result to `MusicMetadata(title, artist(from artist.name),
    album(from album.title), isrc, coverUrl = album.cover_xl, source = MusicSource("deezer",
    trackId=id, albumId=album.id))`. Filters by `isUsable`.
  - `details`: runs two calls **in parallel** via `coroutineScope { async {...} }`:
    `GET /track/{trackId}` and `GET /album/{albumId}`, then merges: year (release_date, first 4
    chars) from track or album, albumArtist from album.artist.name, genre from
    album.genres.data[0].name, label from album.label, trackNumber from track.track_position,
    trackTotal from album.nb_tracks, discNumber from track.disk_number.

- **`ItunesProvider`** (`ItunesProvider.kt`) — secondary catalogue, always queried alongside
  Deezer (not merely a fallback), no API key required.
  - `id = "itunes"`, `name = "iTunes"`.
  - `search`: `GET https://itunes.apple.com/search?term=<q>&entity=song&limit=<n>` → JSON field
    `results`. Maps trackName/artistName/collectionName/releaseDate(year)/collectionArtistName
    (falls back to artistName)/primaryGenreName/trackNumber/trackCount/discNumber. Cover: takes
    `artworkUrl100` and replaces the "100x100bb" substring with "1200x1200bb" to get a full-size
    cover URL (no separate details call needed — this provider returns everything in one shot,
    so `details()` uses the interface default no-op).

- **`MusicMetadataUtil`** (`MusicMetadataUtil.kt`) — the orchestrator/facade. Key pieces:
  - `providers = listOf(DeezerProvider, ItunesProvider)` (priority order — used only to break
    ties, both are always queried concurrently via `async`/`awaitAll`).
  - Title-parsing heuristics (regexes) to turn a YouTube video title like
    `"Dhurata Dora ft. Soolking - Zemër (Official Video)"` into an artist/title search query:
    strips "feat./ft." from the artist field, strips bracketed noise ("Official Video", "Lyrics",
    "HD", "Remaster", "Slowed + Reverb", etc. — big regex `BRACKET_NOISE`), strips "- Topic"
    suffix from auto-generated channel names, splits on `-`/`–`/`—`/`|` for artist/title, handles
    multiple artists joined by `,`/`&`/`x`.
  - `buildSearchQueries(rawTitle, uploader)`: returns ordered `MusicQuery` candidates (handles
    both "Artist - Title" and reversed "Title - Artist" title conventions).
  - `search(artist, title, providerId?, limit=8)`: manual search entry point (used by the
    in-app manual search dialog); `providerId` narrows to one catalogue or null = all.
  - `searchFromVideo(videoTitle, uploader, limit=8)`: automatic lookup used for a fresh download;
    tries query candidates in order, stopping once a candidate scores ≥ `MusicMatcher.CONFIDENT`
    (0.75); enriches with `(feat. X)` extracted from the raw title if missing from result.
  - `resolveForVideo(videoTitle, uploader)`: convenience — best match, fully completed
    (tags + lyrics) — used by the download worker for a "quick download" started before the user
    ever saw a music card.
  - `complete(metadata, withLyrics=true, lyricsSourceId=null)`: **the "resolve everything" step**.
    Runs `details(metadata)` (extended tags) and `LyricsUtil.fetch(...)` **concurrently** via
    `async`, and merges lyrics onto the completed metadata. This is the single call-site that
    turns a search candidate into a "song" ready to tag. Lyrics already present are never
    re-fetched (keeps user edits).
  - `lookup(query, providerId, limit)`: fires `search` on chosen providers concurrently, returns
    `null` only if literally every provider was unreachable (network down); an empty list is a
    valid "no matches" answer. Results are passed to `MusicMatcher.rank(...)`.
  - `details(metadata)`: dispatches to the provider matching `metadata.source.provider` id.
  - `buildFileName(metadata, extension)`: sanitizes `displayName()` for the filesystem
    (`[\\/:*?"<>|]` → `_`) → `"Artist - Title.ext"`.

- **`MusicMatcher`** (`MusicMatcher.kt`) — fuzzy scoring/ranking so that Deezer+iTunes results
  (which use each catalogue's own relevance ranking) are re-ranked against what was actually
  searched for.
  - `MusicQuery(artist, title, version)` — version = detected rendition keyword ("remix", "live",
    "acoustic", "cover", "instrumental", "karaoke", "unplugged", "demo", "reprise", "orchestral",
    "piano", "nightcore", "slowed"/"sped up" etc.) extracted from trailing text after the base
    title.
  - `normalize()` strips diacritics (NFD unicode normalize + strip combining marks), lowercases,
    replaces non-alphanumerics with spaces.
  - `score(query, candidate)`: weighted title similarity (0.6) + artist similarity (0.4) via a
    Levenshtein-ratio `similarity()` function (with substring containment treated as 0.9 near-
    match), minus a version-mismatch penalty (0.30–0.45 depending on kind of mismatch).
  - `rank(query, byProvider, limit)`: flattens per-provider lists, adds a small per-provider tie-
    break bias (`PROVIDER_STEP = 0.02` weighted by declared provider priority), sorts by score,
    dedupes by `identity()` (normalized artist + base title + version), takes `limit`.
  - `CONFIDENT = 0.75` threshold used to stop trying further query variants early.

### 1.3 Tag writing (embedding metadata into the audio file)

File: `app/src/main/java/com/deniscerri/ytdl/util/MusicTagUtil.kt`

- **Library used: `net.jthink:jaudiotagger:3.0.1`** (Gradle: `app/build.gradle` line ~268:
  `implementation "net.jthink:jaudiotagger:3.0.1"`). This is the classic JAudiotagger fork (not
  TagLib, not mp3agic, not ffmpeg metadata muxing).
- `TAGGABLE` extensions jaudiotagger can write: `mp3, m4a, m4b, mp4, flac, ogg, wav, aif, aiff,
  wma`. Anything else is only renamed, not tagged.
- Suppresses jaudiotagger's verbose JUL logger: `Logger.getLogger("org.jaudiotagger").level =
  Level.OFF` in an `init {}` block.
- Entry points:
  - `applyToDirectory(dir: File, metadata: MusicMetadata)` — walks a directory (used on the temp
    cache dir **before** the file is moved to its final SAF/user-chosen location — tagging never
    has to deal with SAF `DocumentFile` streams, only plain `java.io.File`).
  - `applyToPaths(paths: List<String>, metadata: MusicMetadata): List<String>` — tags files
    already at their destination path, returns updated (possibly renamed) paths.
  - Both delegate to private `apply(files, metadata)`, which:
    1. Bails out entirely if `metadata.isUsable` is false or there are no files.
    2. Resolves the cover art once (`resolveCover`): if `MusicCoverUtil.isLocal(coverUrl)`
       (path starts with `/`) uses the local file directly; otherwise downloads the cover bytes
       via `MusicHttp.bytes(url)` into a `File.createTempFile("cover_", ".jpg")`. Tracks whether
       the cover file is "temporary" (a downloaded copy that should be deleted after use, vs. a
       user-picked file that must be preserved).
    3. For each file with a taggable extension: `runCatching { writeTags(file, metadata,
       cover?.file) }`, logging (not throwing) on failure — tagging failure never aborts the
       download/rename.
    4. Renames every file (taggable or not) to the clean "Artist - Title.ext" name via
       `renameToCleanName` (no-op if target already matches or already exists).
    5. Deletes the temp cover file if it was a downloaded temporary copy.
  - **`writeTags(file, metadata, cover)`** — the actual jaudiotagger calls:
    ```kotlin
    val audioFile = AudioFileIO.read(file)
    val tag = audioFile.tagOrCreateDefault
    tag.setField(FieldKey.TITLE, metadata.title)
    tag.setField(FieldKey.ARTIST, metadata.artist)
    tag.write(FieldKey.ALBUM_ARTIST, metadata.albumArtist.ifBlank { metadata.artist })
    tag.write(FieldKey.ALBUM, metadata.album)
    tag.write(FieldKey.YEAR, metadata.year)
    tag.write(FieldKey.GENRE, metadata.genre)
    tag.write(FieldKey.RECORD_LABEL, metadata.label)
    tag.write(FieldKey.TRACK, metadata.trackNumber)
    tag.write(FieldKey.TRACK_TOTAL, metadata.trackTotal)
    tag.write(FieldKey.DISC_NO, metadata.discNumber)
    tag.write(FieldKey.ISRC, metadata.isrc)
    tag.write(FieldKey.LYRICS, metadata.lyrics)      // <-- lyrics embedded as a standard tag field (USLT for mp3/ID3, ©lyr for m4a, etc. — whatever jaudiotagger maps FieldKey.LYRICS to per format). No separate .lrc sidecar file, no SYLT/synced-lyrics tag support.
    cover?.let {
        tag.deleteArtworkField()
        tag.setField(ArtworkFactory.createArtworkFromFile(it))
    }
    audioFile.tag = tag
    AudioFileIO.write(audioFile)
    ```
  - Private extension `Tag.write(key, value)`: skips blank values (never wipes a field the
    downloader might already have set) and wraps `setField` in `runCatching` so an unsupported
    key for a given container (e.g. some field on OGG/WAV) just logs a warning instead of
    crashing the whole tagging pass. Title/Artist use raw `setField` (not the safe wrapper) since
    those are guaranteed present (`isUsable` gate).
  - Note: **timed/synced lyrics (LRC) are written into the plain `FieldKey.LYRICS` tag as raw
    text**, including the `[mm:ss.xx]` timestamp markers if the fetched lyrics happen to be
    synced — jaudiotagger has no explicit SYLT frame usage here; it's all just the standard
    lyrics/comment field holding whatever string `LyricsUtil` returned (timed or plain).

### 1.4 UI flow for editing tags

- **`MusicMetadataCard`** (`app/src/main/java/com/deniscerri/ytdl/ui/downloadcard/MusicMetadataCard.kt`)
  — binds the "music mode" section of the audio download card.
  - Holds a mutable `current: MusicMetadata` plus a `matches: List<MusicMetadata>` (alternative
    catalogue results) and `selectedMatch` index.
  - `fields: List<Field>` — one entry per editable text field (title, artist, album, year,
    albumArtist, genre, label, trackNumber, trackTotal, discNumber, isrc). Each `Field` wraps an
    `EditText`, has a `read`/`write` accessor pair into `current`, and a `TextWatcher` that
    pushes user edits back into `current` and calls `onMetadataChanged(current.copy(), byUser=true)`
    on every keystroke (guarded by a `binding` flag so programmatic `setText` doesn't loop back
    as a "user edit").
  - Lyrics field is a read-only summary line (line count / "N timed lines") that opens
    `LyricsDialog` on click.
  - Cover thumbnail (Picasso-loaded) opens a cover picker (`onCoverClicked`) when tapped.
  - "Other matches" chip opens a `MaterialAlertDialogBuilder` single-choice list to switch between
    ranked candidates (`onMatchSelected(index)`).
  - "Manual search" chip opens a dialog (`dialog_music_search` layout) with artist/song text
    inputs, a catalogue dropdown (populated from `MusicMetadataUtil.catalogues` — id/name pairs
    exposed by the providers list), a "fetch lyrics" switch, and a lyrics-source dropdown
    (`LyricsUtil.sources`). Confirms into `onSearchRequested(MusicSearch(...))`.
  - "More/fewer details" chip toggles visibility of the extended-tag fields section.
- **`MusicSearch`** data class (`MusicSearch.kt`): `artist, song, catalogueId?, withLyrics=true,
  lyricsSourceId?` — represents one manual search request from the dialog.
- **`MusicViewModel`** (`app/src/main/java/com/deniscerri/ytdl/database/viewmodel/MusicViewModel.kt`)
  — owns the search/lookup state machine per download card (`ViewModel`, scoped to the hosting
  activity so both the "sheet" toggle and the card fields agree).
  - `SearchState` sealed class: `Idle, Waiting (no video title yet), Loading, Found(matches,
    selected), NotFound, Failed (no catalogue reachable)`.
  - `syncWithVideo(title, uploader, url)`: debounced (600ms `TYPING_DELAY`) auto-search triggered
    whenever the fetched/typed video title changes, unless the user has already "pinned" a result
    (searched/picked/edited manually — `pinned` flag set by `pin()`).
  - `search(request: MusicSearch)`: manual lookup, pins state so auto-sync stops overwriting it.
  - `select(index)`: switch to another ranked match, triggers `loadDetails(index)` to complete tags+lyrics for it (lazy — extended tags/lyrics are only fetched for the match actually shown, not all candidates).
  - `loadDetails(index)`: calls `MusicMetadataUtil.complete(match, withLyrics, lyricsSourceId)` and merges the completed result back in-place via `replaceMatch`, guarded by a `generation` counter so a stale completion from an old search never clobbers a newer one.
  - `retry()`: re-runs `lastSearch` after a `Failed` state (e.g. user tapped retry after no network).
- **Download worker integration** — `app/src/main/java/com/deniscerri/ytdl/work/download/DownloadWorker.kt`:
  - `resolveMusicTags(item, dataUpdate)` (around line 499): if the UI card already resolved a
    usable `MusicMetadata` but lyrics are still blank (e.g. download started before lyrics
    finished fetching), calls `MusicMetadataUtil.complete(card)` to finish it off. If there was no
    card at all (quick/instant download path) but music mode was enabled, awaits video-info fetch
    then calls `MusicMetadataUtil.resolveForVideo(item.title, item.author)`. Stores result back
    onto `item.audioPreferences.musicMetadata`.
  - After the actual yt-dlp download finishes (~line 294-322): `val musicMetadata =
    musicTags.await()`; then either `MusicTagUtil.applyToDirectory(tempFileDir, musicMetadata)`
    (normal path, before move-to-destination) or `MusicTagUtil.applyToPaths(finalPaths,
    musicMetadata)` (no-cache path, tags files already scanned at their final location). Tagging
    happens strictly **after** the download completes and **before** (or exactly at) the file
    move/rename — never touches the file mid-download.

### 1.5 Cover art resolution
File: `app/src/main/java/com/deniscerri/ytdl/util/MusicCoverUtil.kt` and
`CoverSearchUtil.kt`/`BingImageSource.kt`/`CatalogueImageSource.kt` (not fully read, but
relevant surface):
- `MusicCoverUtil.store(context, uri)`: copies a user-picked `content://` image into
  `filesDir/music_covers/cover_<timestamp>.jpg` (SAF grants aren't durable enough to keep
  referencing directly); prunes covers older than 7 days.
- `isLocal(cover)`: `cover.startsWith("/")` — distinguishes a locally stored path from a remote
  catalogue URL.
- `load(cover, ImageView)`: uses **Picasso** (`com.squareup.picasso.Picasso`) to load either
  `File(cover)` (local) or the URL string (remote) with a placeholder drawable.
- `preview(context, cover, maxSize)` / `notificationIcon(...)`: manual bytes→`BitmapFactory`
  decode with `inSampleSize` computed to downsample large covers, used for full-size previews and
  notification large-icons (reads bytes directly rather than through Picasso's cache, since these
  are one-shot reads of potentially transient SAF documents).
- `CoverSearchUtil.search(query)`: runs a Bing image search (`BingImageSource`) and a catalogue
  artwork search (`CatalogueImageSource`) concurrently, catalogue results ranked first, deduped by
  URL, capped at 40 results — this is the "pick a different cover manually" flow, separate from
  the automatic per-song cover URL already returned by Deezer/iTunes search results.

---

## 2. Lyrics fetching

### 2.1 Sources — **LRCLIB primary, NetEase Cloud Music secondary**. No Musixmatch, no Genius.

File: `app/src/main/java/com/deniscerri/ytdl/util/extractors/music/LyricsProvider.kt` — tiny
interface:
```kotlin
interface LyricsProvider {
    val id: String
    val name: String
    suspend fun fetch(artist: String, title: String): String?   // null = source has nothing
}
```

**`LrclibProvider`** (`LrclibProvider.kt`) — id `"lrclib"`, name `"LRCLIB"`. Base URL
`https://lrclib.net/api`. No API key, no rate limit encountered in code (no key headers sent).
- Tries the **exact-match endpoint** first: `GET /get?track_name=<title>&artist_name=<artist>`
  → JSON object with `syncedLyrics` and `plainLyrics` fields; timed (`syncedLyrics`) preferred
  over plain, via `.lyrics() = timed() ?: plain()`.
- If that 404s/fails, falls back to the **search endpoint**:
  `GET /search?track_name=<title>&artist_name=<artist>` → JSON **array** of candidate objects;
  first tries `firstNotNullOfOrNull { it.timed() }` across all results (prefers ANY result with
  synced lyrics over an exact-but-plain one), then `firstNotNullOfOrNull { it.plain() }` as final
  fallback.
- Result format: **LRC-style synced lyrics** (`[mm:ss.xx]` timestamp-prefixed lines) when
  available, else plain unsynced text block.

**`NeteaseProvider`** (`NeteaseProvider.kt`) — id `"netease"`, name `"NetEase"`. Base URL
`https://music.163.com/api`. Two-step: search then read.
- `GET /search/get?s=<title artist>&type=1&limit=5` → `result.songs[]`, takes first song's `id`.
- `GET /song/lyric?id=<id>&lv=1&kv=1&tv=-1` → `lrc.lyric` string (already LRC-timed format,
  `lv=1` requests the "lyric version" with timestamps).
- Used as a secondary source mainly for non-English tracks LRCLIB doesn't index.

**`LyricsUtil`** (`LyricsUtil.kt`) — orchestrator, sequential (not parallel) fetch:
```kotlin
private val providers = listOf(LrclibProvider, NeteaseProvider)
val sources: List<Pair<String, String>> = providers.map { it.id to it.name }  // for UI picker

suspend fun fetch(artist: String, title: String, sourceId: String? = null): String? =
    withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        providers
            .filter { sourceId == null || it.id == sourceId }
            .firstNotNullOfOrNull { source ->
                runCatching { source.fetch(artist, title) }.getOrNull()?.ifBlank { null }
            }
    }
```
- Sequential by design (unlike tag providers, which are parallel): "a lyrics fetch has nothing to
  rank" — first source with a non-blank answer wins, so the common case costs one request to
  LRCLIB only.
- `isTimed(lyrics)`: `Regex("""\[\d{1,2}:\d{2}""").containsMatchIn(lyrics)` — the sole heuristic
  for "is this LRC-synced or plain text".
- `lineCount(lyrics)`: counts non-blank lines, used for the UI summary ("42 timed lines" / "42
  lines").

### 2.2 How fetched lyrics are stored/embedded

- **No sidecar `.lrc` file** is ever written. No dedicated database table for lyrics either.
- Lyrics live purely as a `String` field (`MusicMetadata.lyrics`) that flows: fetched by
  `LyricsUtil.fetch()` → merged into `MusicMetadata` inside `MusicMetadataUtil.complete()` →
  persisted on `AudioPreferences.musicMetadata` (part of the Room-backed download item) → at
  tag-write time, embedded directly into the standard lyrics tag field via jaudiotagger:
  `tag.write(FieldKey.LYRICS, metadata.lyrics)` (see §1.3). This maps to `USLT` for ID3/MP3,
  `©lyr` for MP4/M4A, the Vorbis comment `LYRICS`/`UNSYNCEDLYRICS` field for FLAC/OGG, etc.,
  whatever jaudiotagger's `FieldKey.LYRICS` resolves to per format — there is **no distinct
  timed/SYLT frame handling**; synced LRC text (with `[mm:ss]` tags) is stored as-is inside that
  same plain lyrics field as a multi-line string.
- Manual editing: `LyricsDialog` (`app/src/main/java/com/deniscerri/ytdl/ui/downloadcard/LyricsDialog.kt`)
  — a `BottomSheetDialog` with a single multi-line `EditText` (full-height sheet since lyrics are
  the one field too long for the card's normal single-line inputs). Shows a summary line via
  `LyricsUtil.isTimed`/`lineCount`. On save, calls back `onSaved(lyrics: String)` which
  `MusicMetadataCard.setLyrics()` uses to overwrite `current.lyrics` and notify
  `onMetadataChanged(current.copy(), byUser = true)`. Editing is "on a copy" — nothing is
  committed until Save is tapped.
- Lyrics already present on a `MusicMetadata` (e.g. user-typed, or already resolved) are **never
  re-fetched** — `complete()` checks `metadata.lyrics.isNotBlank()` before calling `LyricsUtil.fetch`.

---

## 3. Networking stack

### 3.1 HTTP client — **raw OkHttp**, no Retrofit, no Ktor.

Single shared client object: `app/src/main/java/com/deniscerri/ytdl/util/extractors/music/MusicHttp.kt`
```kotlin
object MusicHttp {
    private const val TIMEOUT_SECONDS = 8L
    private const val USER_AGENT = "Mozilla/5.0"
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    fun json(url: String): JsonObject?       // GET + JsonParser.parseString(...).asJsonObject, null on any failure
    fun jsonArray(url: String): JsonArray?   // same but .asJsonArray, for list-shaped endpoints
    fun bytes(url: String): ByteArray?       // GET, returns response body bytes (used for covers)
    fun html(url: String, headers: Map<String,String> = emptyMap()): String?  // raw text, for HTML-scraped sources (Bing image search)
    // private get()/call() build a Request with the "User-Agent: Mozilla/5.0" header
    // plus any caller-supplied headers, execute synchronously (blocking call, always invoked
    // from Dispatchers.IO by callers), and swallow all exceptions into null with a Log.w.
}
```
- No interceptors, no caching layer, no retry/backoff logic — a single blocking `execute()` per
  request, 8s connect+read timeout, everything wrapped in `runCatching` at the call site so a
  failed request degrades to "this source has nothing" rather than throwing.
- All provider classes (Deezer, iTunes, LRCLIB, NetEase, cover sources) build their query URLs by
  hand (plain string concatenation with `MusicHttp.encode()` for the query param) and call
  `MusicHttp.json(...)` / `.jsonArray(...)` / `.bytes(...)` — there are **no Retrofit service
  interfaces, no DTO/Moshi/Gson `@SerializedName` model classes**. Responses are parsed ad hoc as
  `com.google.gson.JsonObject`/`JsonArray` via `JsonParser.parseString(...)`, then read
  through small null-safe extension helpers (`str/obj/arr` in `MusicProvider.kt`) that default to
  empty string / empty array on any missing/null/wrong-typed field rather than throwing or using
  a strongly-typed DTO. This is a deliberately "always degrade to blank field" loose parsing
  strategy rather than a typed contract.
- Error handling philosophy throughout: a network/parse failure never throws up the call stack;
  it becomes `null` (source unreachable — worth retrying) or an empty result (source has nothing
  — not worth retrying), and callers (`MusicMetadataUtil.lookup`, `LyricsUtil.fetch`) treat "all
  sources returned null" as the only real "failure" state surfaced to the UI
  (`SearchState.Failed` with a retry action); a single source being down just causes fallthrough
  to the next one or an empty/partial result.

### 3.2 Relevant Gradle dependencies (from `app/build.gradle`)
```
implementation 'com.google.code.gson:gson:2.13.2'
implementation "com.squareup.okhttp3:okhttp:5.3.2"
implementation "net.jthink:jaudiotagger:3.0.1"
```
(Picasso is also used, for image loading — `com.squareup.picasso.Picasso`, version not confirmed
from a single grep but referenced in `MusicCoverUtil.kt`; check `app/build.gradle` for exact
Picasso version if needed.)

---

## 4. Summary for Sona reimplementation

To clone this system in Sona (`com.lhacenmed.sona`):
1. **Tag catalogues**: hit Deezer (`api.deezer.com/search`, `/track/{id}`, `/album/{id}` — no
   key) and iTunes Search API (`itunes.apple.com/search?entity=song` — no key) concurrently,
   rank/dedupe results with a Levenshtein+weighted similarity scorer, let the user pick among
   ranked alternatives, complete extended tags only for the selected candidate.
2. **Lyrics**: hit LRCLIB (`lrclib.net/api/get` then `/search` fallback, prefers
   `syncedLyrics` over `plainLyrics`) then NetEase (`music.163.com/api/search/get` +
   `/song/lyric?lv=1`) sequentially as a fallback; detect "timed" via a `[mm:ss` regex.
3. **Tag writing**: use `net.jthink:jaudiotagger:3.0.1` (`AudioFileIO.read/write`,
   `tag.setField(FieldKey.X, value)`, `ArtworkFactory.createArtworkFromFile` for cover art);
   store LRC-timed lyrics as plain text in the standard lyrics field (no SYLT frame handling);
   run tagging as a post-download step on a plain `java.io.File`, before any SAF move; rename to
   "Artist - Title.ext" after tagging, skip blank fields so nothing already written gets wiped.
   Consider whether Sona wants to go further and use a real SYLT/synced-lyrics tag, or keep
   YTDLnis's simpler "one string field" approach — YTDLnis does NOT do proper synced-lyrics tag
   frames, it just dumps LRC-formatted text into the plain lyrics field.
4. **Networking**: plain OkHttp (no Retrofit/Ktor), Gson for ad hoc JSON parsing with
   null-safe accessor helpers instead of typed DTOs, 8s timeouts, no retry logic, all failures
   swallowed to null/empty at the source level so one dead provider never blocks the others.
5. **No local persistence of lyrics/tags** — everything is resolved live per download and
   embedded directly into the file; only the resolved `MusicMetadata` Parcelable rides along with
   the in-flight download item.
