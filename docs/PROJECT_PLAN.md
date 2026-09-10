# Sona — Project Plan & Session Handoff

**Package:** `com.lhacenmed.sona` · **Root:** `C:\Users\lhacenmed\AndroidStudioProjects\Sona`
**Last updated:** 2026-09-09 · **Status:** Phases 1–3 complete and building; Phases 4–6 not started.

> **Read this first if you are a new session.** This document is the single source of truth for
> what exists, what is left, and — critically — the toolchain landmines already discovered the hard
> way. Sections 4 (Toolchain) and 8 (Working agreements) will save you hours; do not skip them.

---

## 1. What Sona is

A local music player for Android built by **cloning proven implementations** from five reference
apps that already sit on this machine, rather than designing subsystems from scratch. Compose-only
UI, multi-module Gradle, offline-first (Room as the durable cache).

### Reference projects — which app to clone what from

| Subsystem | Clone from | Local path |
|---|---|---|
| Media scanning, storage, cold-start/caching, in-app updater, player UI/notification/persistence | **Fossify Music Player** | `C:\Users\lhacenmed\AndroidStudioProjects\Music` |
| Audio behaviour settings, ReplayGain, content settings (separators, intelligent sort, hide collaborators), image/cover settings | **Auxio** | `C:\Users\lhacenmed\AndroidStudioProjects\Auxio` |
| Dynamic artwork-driven Material theming | **ArchiveTune** | `C:\Users\lhacenmed\AndroidStudioProjects\ArchiveTune` |
| Online tag fetching, lyrics fetching, tag writing | **YTDLnis** | `C:\Users\lhacenmed\AndroidStudioProjects\YTDLnis` |
| Navigation / reusable host-activity pattern | **Khatmah** | `C:\Users\lhacenmed\AndroidStudioProjects\Khatmah` |

### Pre-extracted research (use these before re-reading reference source)

`docs/research/` holds ~210 KB of **verbatim extracted code** from the reference apps, produced by
dedicated research agents. **Read these before spawning new research agents** — the expensive work
is already done.

| File | Covers |
|---|---|
| `research_music_app.md` | Fossify scanning, Room schema, cache/restore model, updater |
| `research_fossify_player_notification_persistence.md` | Fossify player UI, media notification, queue persistence |
| `research_fossify_fast_load_and_permission_ux.md` | Cold-start order, persist ordering, permission-vs-empty UI logic |
| `research_auxio_settings.md` | Audio settings, **ReplayGain (full code)**, separators, intelligent sort, cover modes |
| `research_archivetune_theme.md` | Palette extraction → materialkolor scheme → animation |
| `research_ytdlnis_tags_lyrics.md` | Deezer/iTunes tag fetch, LRCLIB/NetEase lyrics, jaudiotagger writing |
| `research_khatmah_navigation.md` | Host activity + sealed-route navigation |

---

## 2. Current status

| Phase | Scope | State |
|---|---|---|
| 1 | Foundation: modules, scanner, Room, basic playback, track list | ✅ Complete, builds clean |
| 2 | Navigation shell, all library tabs, search, sorting, settings | ✅ Complete, builds clean |
| 3 | Full player, playback behaviour, persistence, notification, dynamic theming | ✅ Complete, builds clean |
| — | Performance & UX remediation (tabs redesign, load speed, colour transitions) | ✅ Complete, builds clean |
| — | Library data-layer rebuild (`:core:data`, diff sync, stable ids, launch speed) | ✅ Complete, builds clean |
| 4 | Metadata, lyrics, tagging, **ReplayGain**, content/image settings | ❌ Not started |
| 5 | Playlists + listening stats | ❌ Not started |
| 6 | Equalizer, language, in-app updater, personalize, settings consolidation | ❌ Not started |

**Verification bar used throughout:** `./gradlew clean assembleDebug` must succeed.
**The app has never been run on a device or emulator** — all verification to date is compile-only.
This is a deliberate, user-approved standard, but it means runtime behaviour is unproven.

---

## 3. Architecture

### Module graph

```
:app                     Application, MainActivity, SonaApp, AppThemeViewModel
core/
  :core:model            Pure Kotlin/JVM domain models (no Android deps)
  :core:common           Hilt dispatcher qualifiers; natural-sort comparator
  :core:database         Room: SonaDatabase (v4), entities, DAOs, mappers, stable id derivation
  :core:data             LibraryRepository (the app's single library view) + LibraryWriter (diff sync)
  :core:datastore        DataStore Preferences: Library/Playback/Theme settings
  :core:designsystem     SonaTheme, dynamic colour extraction, SonaTabRow
  :core:navigation       Screen contract, AppNavigator, HostActivity
feature/
  :feature:scanner       MediaScanner, MediaStoreQuerier, ManualFileWalker, permission
  :feature:playback      PlaybackService, PlaybackController, ForwardingPlayer
  :feature:library       All tabs, detail screens, search, tab pager
  :feature:player        Expandable player, mini bar
  :feature:settings      Settings home, excluded folders, tab visibility
```

Dependency direction: `app → feature → core`. `core` never depends on `feature`.
`:feature:library` depends on `:feature:scanner` (scan state) and `:feature:playback` (playback).

### Key architectural decisions (and why)

**Navigation — `Screen` interface, not a sealed class.** Khatmah uses a sealed `Dest` class listing
every destination in one file. That cannot span Gradle modules, so Sona uses an open
`Screen : Serializable` interface with an abstract `@Composable fun Content()`. Each feature module
declares its own `Screen` objects; `HostActivity` (in `:core:navigation`) renders any of them.
Adding a screen = write the composable + one `Screen` object. No manifest edit, no graph edit.

**Reactive Room `Flow`s, not one-shot reads.** Fossify re-reads the DB on explicit callbacks. Sona
observes `Flow`s. This is better, but it means Sona *needs* things Fossify doesn't: `@Transaction`
around multi-table writes (otherwise observers see partial batches and flicker). Do not "match
Fossify" by removing transactions.

**One `LibraryRepository`, shared from the application scope.** The library is process-wide
state: the same rows feed five tabs, search, the player, the notification and the dynamic theme.
It is queried, mapped and sorted **once**, on `Dispatchers.Default`, and shared `Eagerly` from an
`@ApplicationScope` `CoroutineScope`. Before this, nine ViewModels each called
`trackDao.observeAll()` and mapped/filtered/sorted the whole table inside
`stateIn(viewModelScope, …)` — whose transform runs on `Dispatchers.Main.immediate`. Every write
to `tracks` therefore fanned out into up to nine full-library re-sorts **on the main thread**.
Do not reintroduce a DAO call in a ViewModel; add a query to the repository instead.

**`LibraryContent` = `Loading | Ready(items)`.** "Empty" and "not loaded" mean opposite things
to a person, and a bare `List` cannot tell them apart. `Loading` renders a placeholder list (same
row height as the real one, so nothing reflows); only `Ready` with no items shows an
empty-library message, still split three ways (no permission / scanning / genuinely empty). The
previous `List<T>?` convention rendered *nothing* while loading — that blank window is what the
launch used to show.

**One `LibraryViewModel` for the whole pager.** The five tabs no longer have five ViewModels;
they share one, whose lists are already computed. Opening a tab is a composition and nothing else.

**Lists never highlight from `PlaybackUiState`.** It carries a position that ticks twice a
second, so collecting it in a list recomposes every row twice a second. ViewModels expose a
`distinctUntilChanged` `currentTrackId` instead, and rows take `isPlaying` as a **lambda** so
only the two affected rows recompose.

**Every Lazy list has a stable key + contentType**, funnelled through `LibraryList` so it cannot
be forgotten. Search namespaces its keys (`"album-$id"`) because four id spaces share one list.

**Theme animates one seed colour, not each role.** `SonaTheme` animates the seed and derives the
whole `ColorScheme` from it. The earlier implementation animated ~34 roles individually, which
recomposed the entire tree every frame *and* left the ~12 newer "fixed" roles un-animated, so they
snapped while the rest crossfaded. Never reintroduce per-role animation.

**`SonaTabRow` takes `selectedPosition: () -> Float`, a lambda.** Reading pager offset in the
parent's composition recomposes the pager every frame of a swipe. The lambda scopes the read to the
indicator (layout phase) and each label. Tabs are equal-width so indicator position is exact
arithmetic — no measurement pass, nothing to drift.

### Frozen contracts

```kotlin
// :feature:playback
data class PlaybackUiState(
    val isPlaying: Boolean = false, val currentTrackId: Long? = null,
    val positionMs: Long = 0L, val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false, val repeatMode: RepeatMode = RepeatMode.OFF,
    val queue: List<Long> = emptyList(),
)
class PlaybackController {            // @Singleton
    fun playTracks(tracks: List<Track>, startIndex: Int); fun togglePlayPause()
    fun seekTo(positionMs: Long); fun skipToNext(); fun skipToPrevious()
    fun setShuffleEnabled(enabled: Boolean); fun setRepeatMode(mode: RepeatMode)
    val playbackState: StateFlow<PlaybackUiState>
}

// :feature:scanner
class MediaScanner {                  // @Singleton — isScanning is shared state
    fun requestScan(force: Boolean = false)                 // fire-and-forget, on the app scope
    suspend fun scan(excludedFolders: Set<String> = emptySet(), force: Boolean = false): SyncStats
    val isScanning: StateFlow<Boolean>
}

// :core:data
class LibraryRepository {             // @Singleton — the only place that reads library DAOs
    val tracks/albums/artists/genres/folders: StateFlow<LibraryContent<T>>  // Eagerly, app scope
    val tracksById: StateFlow<Map<Long, Track>>
    val isReady: StateFlow<Boolean>                         // the splash-screen gate
    fun album/artist/genre(id); fun album/artist/genre/folderTracks(id)     // scoped queries
    fun searchTracks/Albums/Artists/Genres(query, limit)     // SQL LIKE … LIMIT, not in-memory
}
class LibraryWriter {                // @Singleton
    suspend fun sync(tracks, albums, artists, genres, deleteMissing = true): SyncStats
}
fun scannerRequiredPermission(): String            // single source of truth
fun Context.hasScannerPermission(): Boolean

// :core:navigation
interface Screen : Serializable {
    val titleRes: Int? get() = null
    fun title(context: Context): String?
    @Composable fun Content()
}
```

### Scanner pipeline (Fossify's ordering, made idempotent)

0. **Skip check.** `scanSignatureOf()` fingerprints `MediaStore.getVersion()` +
   `MediaStore.getGeneration()` (API 30+) + the excluded-folder set + a scanner schema version.
   If it matches the last completed scan *and* `trackDao.count() > 0`, the scan does not run.
1. Query MediaStore (fast, already indexed).
2. Filter excluded folders + orphans; recompute album/artist/genre counts **via `groupBy`** (was
   O(entities × tracks) — a confirmed major bottleneck, now O(tracks)).
3. **Sync stage 1** — `deleteMissing = false`. First paint happens here on a first run.
4. API 29+ only: manual filesystem walk for files MediaStore missed → merge.
5. **Sync stage 2** — the authoritative pass, and the only one allowed to delete.

Both syncs go through `LibraryWriter`, which **diffs** the result against what is stored and
writes only genuine changes, inside one `withTransaction`. When there is no difference it opens
**no transaction at all**, so nothing is invalidated and nothing repaints. Favourites are carried
forward from the stored row rather than re-overlaid by path.

**Track ids are derived from the file path** (`stableIdOf`), not auto-generated. With
`autoGenerate = true` every scanned track arrived as `id = 0`, so `REPLACE` resolved the unique
`path` index by deleting the row and reinserting it with a fresh rowid — every launch rewrote the
whole table with new primary keys, which invalidated every observing Flow and orphaned the
persisted playback queue. Do not reintroduce `autoGenerate` on `TrackEntity`.

---

## 4. Toolchain landmines (AGP 9 / Kotlin 2.2 / Compose)

**These cost real time to rediscover. Every one was hit and fixed in this project.**

1. **AGP 9 has built-in Kotlin support.** Do **not** apply `org.jetbrains.kotlin.android` — it
   double-registers the `kotlin` extension and fails with *"Cannot add extension with name 'kotlin'"*.
   Apply only `com.android.library`/`com.android.application` (+ `kotlin.plugin.compose` where Compose is used).
2. **Hilt must be ≥ 2.59** for AGP 9 (`Android BaseExtension not found` otherwise). Using **2.60.1**.
3. **KSP must be ≥ 2.3.6** for AGP 9's built-in Kotlin, and KSP is now decoupled from the Kotlin
   version string. Using plain **`2.3.10`** (not `2.2.10-2.0.x`).
4. **`android.kotlinOptions { jvmTarget }` is gone.** Use a top-level
   `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` block.
5. **Pure Kotlin/JVM modules** (`:core:model`) need `kotlin.jvm` declared `apply false` at the root,
   or it conflicts with the Kotlin plugin AGP already put on the classpath.
6. **Hilt 2.60.1 assisted injection changed:** `@HiltViewModel(assistedFactory = X.Factory::class)`
   is now required — auto-detecting the nested `@AssistedFactory` no longer works.
7. **`import androidx.compose.foundation.layout.weight` shadows** the implicit `ColumnScope`/`RowScope`
   receiver extension and resolves to an internal `RowColumnParentData` property. **Delete the import** —
   `weight()` resolves automatically inside a `Column`/`Row`.
8. **`coil3:coil-compose` pulls in JetBrains Compose Multiplatform**, which forces a `kotlin-stdlib`
   newer than the compiler can read. Root `build.gradle.kts` has a project-wide
   `resolutionStrategy.force` on `kotlin-stdlib` — keep it.
9. `TopAppBar`, `ModalBottomSheet`, `PrimaryScrollableTabRow` need `@OptIn(ExperimentalMaterial3Api::class)`.
10. Material3 no longer exposes a public `ColorScheme(...)` constructor — use `.copy(...)`.

### Build

```bash
./gradlew clean assembleDebug        # the verification bar
./gradlew assembleRelease -Psplits   # per-ABI + universal APKs
```

Build config (SDK 36, minSdk 26, JVM 17) is standard; note the user has since added a version
scheme, release signing via `keystore.properties`, ABI splits, custom APK naming
(`sona-<version>-<variant>-<abi>.apk`), and `checkReleaseBuilds = false`. **Treat those as
intentional; do not revert them.**

---

## 5. Remaining work

### Phase 4 — Metadata, lyrics, tagging *(largest remaining phase)*

**New modules:** `:core:network` (OkHttp + Gson), `:feature:tagging`.
**Primary source:** `docs/research/research_ytdlnis_tags_lyrics.md` (has verbatim code).

- **`:core:network`** — shared OkHttp client + Gson helpers. YTDLnis deliberately uses raw OkHttp
  with ad-hoc `JsonObject` parsing and 8 s timeouts, no Retrofit, all failures degrading to null.
- **Tag fetching** — Deezer (`api.deezer.com/search`, `/track/{id}`, `/album/{id}`) and iTunes
  (`itunes.apple.com/search?entity=song`) queried **concurrently**, no API keys. Re-rank with a
  Levenshtein-weighted `MusicMatcher` (title 0.6 / artist 0.4, version-mismatch penalty,
  `CONFIDENT = 0.75`).
- **Lyrics fetching** — LRCLIB (`/get` exact, then `/search`; prefer `syncedLyrics`) then NetEase as
  fallback, **sequentially**. Detect synced via `Regex("""\[\d{1,2}:\d{2}""")`.
- **Tag writing** — `net.jthink:jaudiotagger:3.0.1`. `TagOptionSingleton.getInstance().isAndroid = true`.
  Container-specific tags: `.ogg`→Vorbis, `.m4a`→Mp4, `.flac`→Flac, else ID3v24. Exclude `wma`/`wav`.
- **Manual tag editor screen** (user chose this explicitly in Phase 1 planning) — prefillable from a
  fetched match.
- **Lyrics sync editor (LRC timing UI)** — ⚠️ **no reference implementation exists**; YTDLnis only
  fetches/embeds. This must be designed from scratch. Flag it to the user before building.
- **ReplayGain — deferred here from Phase 3 for a real reason.** Auxio reads gain from a media3
  `Metadata` object its own indexer produces; Sona's scanner (Fossify-derived) uses
  `MediaMetadataRetriever` + MediaStore cursors, which cannot expose `REPLAYGAIN_*`/`R128_*` tags.
  Gain reading needs jaudiotagger — hence bundling it with this phase. Full `ReplayGainAudioProcessor`
  code (raw 16-bit PCM gain, not player volume) is in `research_auxio_settings.md` §2.
  Requires adding gain fields to `Track` + a Room migration.
- **Content settings** — multi-value separators, hide collaborators (Auxio filters artists by
  `explicitAlbums.isNotEmpty()`).
- **Image settings** — cover quality modes map to concrete `(maxDimension, JPEG quality)` pairs:
  SAVE_SPACE (500, 70), BALANCED (750, 85), HIGH_QUALITY (1000, 100), AS_IS (no re-encode), OFF.
  Force-square is a display-time Coil transformation.

### Phase 5 — Playlists + listening stats

- Extend `:core:database`: `Playlist`, `PlaylistTrack` junction, `PlayStats`. (`QueueItem` already exists.)
- Playlist CRUD; **M3U import/export** (Fossify uses `com.github.bjoernpetersen:m3u-parser:1.4.0`);
  create-playlist-from-folder; add folder/files to playlist.
- Play-time tracking — Fossify counts a play past a **10 s threshold**; most-played aggregation.
- **Note:** favourites are currently a plain `Track.isFavorite` boolean column (deliberately simpler
  than Fossify's playlist-backed favourites). Decide whether to migrate onto the playlist system or
  keep the column. Keeping it is fine and simpler.

### Phase 6 — Equalizer, language, updater, polish

- `:feature:equalizer` — platform `Equalizer`/`LoudnessEnhancer` AudioEffect (independent of ReplayGain).
- Per-app locale via `AppCompatDelegate.setApplicationLocales`.
- `:feature:update` — near-verbatim from Fossify: `version.json` on `raw.githubusercontent.com`,
  manual redirect-following download (GitHub release assets 302 across hosts), foreground service,
  `FileProvider` install, `REQUEST_INSTALL_PACKAGES`. Debug builds skip the check. Details in
  `research_music_app.md` §4. **Requires a CI step that regenerates `version.json` per release.**
- Remember-shuffle (Auxio's `ShuffleMode.IMPLICIT` resolution) — distinct from the restart-persistence
  already implemented.
- Settings consolidation (see gaps below).

---

## 6. Known gaps and loose ends

Ordered roughly by how much they'd bite.

1. **Settings screens exist for only 2 of 3 settings stores.** `PlaybackSettings` (remember-pause,
   pause-on-repeat, rewind-before-skip, headset-autoplay) and `ThemeSettings` (dynamic-theme toggle,
   custom colour) are fully implemented in `:core:datastore` and consumed at runtime, but have **no UI**.
   Settings home only links Excluded Folders and Manage Tabs. This is low-effort, high-visibility work.
2. **Player "Go to album/artist" callbacks are no-ops.** `SonaApp.kt` passes empty lambdas to
   `PlayerScreen(onGoToAlbum = {}, onGoToArtist = {})`. The detail screens exist
   (`AlbumDetailScreen(albumId)`, `ArtistDetailScreen(artistId)`) — just wire them via `LocalNavigator`.
3. ~~Detail screens didn't get the loading-state fix.~~ **Done.** They now share
   `TrackListDetailViewModel` + `TrackListDetail`, so they inherit `LibraryContent.Loading` and
   query only their own rows instead of filtering the whole table in memory.
4. **Player lyrics section is an honest placeholder** ("Lyrics not available") pending Phase 4 data.
   Designed so real content slots in without restructuring.
5. ~~`SearchViewModel` has an unresolved `FlowPreview` warning.~~ **Done** — opted in explicitly.
6. **Destructive Room migration is still on.** `DatabaseModule` uses
   `fallbackToDestructiveMigration(dropAllTables = true)` — correct while pre-release, but **must be
   replaced with real migrations before shipping**, or users lose favourites/queue/playlists on upgrade.
7. **No tests anywhere.** No unit, instrumentation, or screenshot tests exist.
8. **Never run on a device.** Everything is compile-verified only.
9. `MediaStoreQuerier` looks up cursor column indices per row rather than caching them. Investigated
   and **deliberately not fixed** — Fossify does the same and it was not the bottleneck (the
   O(n×m) recompute was). Revisit only if profiling implicates it.

---

## 7. Fixes already applied (don't redo or regress these)

- Scanner persists MediaStore results **before** the manual filesystem walk (was the cause of slow
  first paint).
- `recompute{Albums,Artists,Genres}` use `groupBy` — was O(entities × tracks).
- Multi-table writes wrapped in `withTransaction`.
- `MediaScanner` is `@Singleton` (its `isScanning` is shared state).
- Favourites preserved across rescans via `getFavoritePaths()` overlay.
- Permission unified on `scannerRequiredPermission()` — previously `MainActivity` requested
  `WRITE_EXTERNAL_STORAGE` while the scanner checked `READ_EXTERNAL_STORAGE`. Manifest now declares
  `READ_EXTERNAL_STORAGE` (maxSdk 32).
- Empty states distinguish permission / scanning / genuinely-empty instead of assuming "empty = no permission".
- **Launch/relaunch performance overhaul** (see §3): stable path-derived track ids; diff-based
  `LibraryWriter`; MediaStore-signature scan skip; a single `LibraryRepository` shared off the
  main thread; `SortKey` (names tokenized once per item rather than once per *comparison* — the
  comparator was re-running ICU transliteration O(n log n) times); indices + scoped queries for
  the detail screens; SQL-side search and folder aggregation; splash screen held until the
  library is in memory.
- Bottom `NavigationBar` replaced by top `SonaTabRow` + `HorizontalPager`; player overlay no longer
  needs reserved bottom space.

---

## 8. Working agreements with the user

- **Clone, don't invent.** The reference apps already implement these features. Extract and port the
  real code; design from scratch only where no reference exists (and say so).
- **Where a reference is View-based** (Fossify's player UI), clone the *behaviour* faithfully and
  reimplement in Compose — Sona is Compose-only, non-negotiable.
- **Use subagents for heavy work**, with precise prompts naming exact files to read. Freeze contracts
  first so parallel agents don't conflict; never let two agents edit the same files.
- Surface assumptions and ambiguities explicitly rather than silently picking; push back when a
  simpler approach exists.
- Surgical changes only — every changed line traces to the request. Remove imports your own change
  orphaned; mention pre-existing dead code without deleting it.
- **Verification bar:** `./gradlew clean assembleDebug`. Do not run on a device unless asked.
- Subagents have twice hit session rate limits mid-task. **Check for partially written files before
  relaunching** — one agent had completed all file writes and only failed on its summary.
