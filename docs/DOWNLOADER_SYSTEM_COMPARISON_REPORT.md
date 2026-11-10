# Downloader systems: Sona, Nezzel, Seal, YTDLnis

How four Android yt-dlp apps are built, why Nezzel's YouTube lookup is so fast, and the design proposed for Sona.

Sources: the code of each project as checked out on 2026-11-10 (Sona `dev` at `c2a7695`; Nezzel, Seal and YTDLnis
local checkouts). Line counts and file references are from those checkouts. No timings were measured; where a claim
is about speed, the reason is read from the code, and the size of the gain has to be measured on a device.

---

## 1. Summary

- **Nezzel is fast on YouTube because, most of the time, it does not run yt-dlp to read the link.** It reads YouTube
  in-process with NewPipe Extractor, inside the app's already running JVM, over a handful of HTTPS calls, and turns
  the answer into JSON shaped like yt-dlp's own. yt-dlp is only the fallback. Starting Python and loading yt-dlp's
  extractors, which every Sona lookup pays, is skipped entirely.
- **The same JSON is reused for the download.** yt-dlp is given it with `--load-info-json`, so it does not read the
  page a second time, and a single-file MP4 is fetched by Nezzel's own HTTP downloader without starting Python at all.
  Sona currently reads every link twice, once to show it and once to download it.
- **The separate engines do make the common case efficient.** The design choice that makes it work is the shared
  contract (yt-dlp-shaped info JSON), not the number of engines. Its cost is a slower miss: up to 11 s of NewPipe
  before yt-dlp starts.
- **Is it maintainable? Partly.** The seams are good (an interface per source, a fallback on any failure), but
  `YoutubedlEngine.kt` is a 1,212-line class doing inspection, fallback, caching, transfer, re-verification and updates,
  and ten hand-written site scrapers repeat work yt-dlp's maintainers already do upstream.
- **Proposed for Sona:** one contract (info JSON), two sources (NewPipe for YouTube, yt-dlp for everything), an
  extraction cache, two transfers (direct HTTP and yt-dlp), and the existing Room queue and foreground service. Sona
  being a music app makes this unusually worthwhile: YouTube audio could be looked up and downloaded **before the
  downloader runtime is even installed**, with TagLib (already in the app) writing the tags.

---

## 2. Why Nezzel's YouTube lookup is fast: the exact code

All paths below are under `Nezzel/engine/src/main/kotlin/dev/ahmed/savlino/engine/`.

### 2.1 The path a YouTube link takes

```text
LinkViewModel.startInspection()                    app/.../ui/LinkViewModel.kt:763
  ├─ inspect() starts at once; the disk preview cache is read in parallel and never delays it (:802-803)
  └─ YoutubedlEngine.inspect(source)               YoutubedlEngine.kt:249
       ├─ joined(): one extraction per link; a second caller waits for the first (:273)
       ├─ recentHit(): memory cache hit, no network (:296)
       └─ extract()                                 (:313)
            ├─ link failed fast before? → yt-dlp straight away (:318)
            ├─ fastInfoJson() → NpeFastSource.infoJson()   10 s budget + 1 s grace (:359)
            │     └─ StreamInfo.getInfo(ServiceList.YouTube, url)   NewPipe, in-process
            │     └─ NpeInfoJson.build(info)        yt-dlp-shaped JSON
            ├─ JSON accepted → cached, returned (provenance NPE) (:342-347)
            ├─ no network at all → fail now, do not start yt-dlp on a dead network (:331-333)
            └─ anything else → yt-dlp --dump-json with a fresh budget (:350)
```

### 2.2 The reasons, one by one

| # | What Nezzel does | Where | Why it is faster |
| --- | --- | --- | --- |
| 1 | Reads YouTube **in-process** with NewPipe Extractor (`v0.26.5`) | `youtube/YoutubeFastSource.kt:42` | No process is started. yt-dlp's path starts a Python interpreter, loads `libpython`, and imports yt-dlp from its zipapp, with its whole extractor table, on every call. In-process, the JVM is already warm. |
| 2 | Uses YouTube's **InnerTube API** directly | `youtube/NpeDownloader.kt:99-101` only lets requests through to `youtube.com`, `youtubei.googleapis.com`, `googlevideo.com` | NewPipe asks InnerTube for the player response. Mobile clients usually answer with stream URLs that need no deciphering, so the player JavaScript is often not fetched or run. yt-dlp's default path reads the watch page and the player script, and solves the `n` challenge in a separate JS runtime (in Sona: QuickJS, plus the EJS solver fetched with `--remote-components ejs:github`). |
| 3 | Keeps **only progressive HTTPS streams** | `youtube/NpeInfoJson.kt:95-96` | No DASH/HLS manifests to fetch and parse. The JSON is small: one entry per itag with its URL, size and codecs. |
| 4 | Gives up quickly and **remembers giving up** | `YoutubedlEngine.kt:45` (`NPE_BUDGET_MS = 10_000`), `:1205` (1 s grace), `:318` and `:1206` (16 links remembered) | A link NewPipe could not read goes straight to yt-dlp next time, so the slow path is never paid twice. |
| 5 | Tells **"no network" apart from "NewPipe failed"** | `NpeDownloader.kt:39-51` (`offline()`), `FastResult.Unreachable` | If no request got any response, yt-dlp would wait on the same dead network, so it is not started at all. |
| 6 | **Caches** what it read, in memory | `RecentInspections.kt` (8 links, 15 min, never past the signed URL's `expire` minus 5 min) | Reopening the sheet for the same link uses no network. |
| 7 | **Shares** one extraction among callers | `YoutubedlEngine.kt:273` `joined()` | Two screens asking about the same link start one extraction, not two. |
| 8 | The JSON **is yt-dlp's format**: `format_id` is the itag, plus `url`, `http_headers`, `http_chunk_size` | `NpeInfoJson.kt:98-107` | At download time the stored JSON, trimmed to the chosen formats, is handed to yt-dlp with `--load-info-json`, so yt-dlp downloads **without reading YouTube again** (`YtdlpInfoCache`, `YoutubedlEngine.kt:465-541`). |
| 9 | A **native HTTP downloader** for single-file video | `download/DirectDownloader.kt` (Range resume, ETag/Last-Modified check, 8 MB checkpoints, atomic rename) | When the chosen format is one MP4 file, no Python is started for the download either (`resumeEngine()`, `:557`). |
| 10 | The yt-dlp fallback is **kept warm** | `YtdlpRuntime.kt:536` `materialize()`, `:275-285` warm-up stamp | The zipapp is unpacked to a directory, so Python imports plain files and keeps its `__pycache__`. That makes the fallback start faster too. |

Safety measures that do not slow it down: requests are limited to YouTube's hosts, redirects are refused, every
response is capped at 16 MB (`NpeDownloader.kt:57`), no cookies are sent, and the NewPipe work runs outside the
operation's coroutine scope because Rhino's JavaScript may ignore interruption (`YoutubedlEngine.kt:366-367`).

### 2.3 How the JSON is checked before it is trusted

`NpeInfoJson.build()` returns `null`, which means "use yt-dlp", when:

- it is not a plain video (a live, a premiere still to come), or a stream getter failed (`:17-18`);
- there is no video-only stream or no audio stream (`:59`);
- two format ids collide, or an id would be read by yt-dlp as a selector such as `best` or `mp4` (`:63`).

`NpeInfoJson.accepts()` then requires at least one audio row that is not a dubbed or descriptive track (`:76-88`).
A wrong NewPipe answer can therefore only cost time, never a wrong file.

### 2.4 Other sites

NewPipe is used **for YouTube only** (`YtdlpCommand.isYouTube`, `YoutubedlEngine.kt:315`). Other sites take two kinds
of route, chosen in `MediaSourceInspector.kt:9` (`MediaSourceRoute`):

- **Native readers** for X, Reddit, Instagram, Threads, TikTok, Bluesky, Pinterest, Facebook, LinkedIn and Telegram
  (folders `x/`, `reddit/`, and so on: 19 to 434 lines each), all over one guarded `MediaHttpClient` (890 lines).
  They read a public JSON or page, such as X's syndication JSON in `x/XReader.kt`. For a photo post they return
  the images. For a video they return **yt-dlp-shaped JSON** that goes into the same cache through
  `RecentInspections.putPrefetched()` (provenance `READER`), so these sites get the same fast lookup and
  `--load-info-json` download.
- **LEGACY** (everything else): plain `yt-dlp --dump-json`.

So the speed carries over to the sites with a reader. Every other site is read at yt-dlp's speed.

---

## 3. Do the separate engines make it efficient, scalable and maintainable?

### 3.1 Efficiency: yes, for the common case

- Hit (most YouTube videos): one process start avoided at lookup and another at download, and one full extraction
  avoided at download.
- Miss: up to **11 s** of NewPipe, then yt-dlp from the beginning. A miss is remembered, so a link pays this once.
- **One NewPipe read at a time, process-wide** (`fastSourceInFlight`, `YoutubedlEngine.kt:69`, `:363`). A second
  inspection running at the same time, for example in a playlist batch, is sent to yt-dlp. That is the right choice
  for a phone, since Rhino uses a lot of CPU, but it means the fast path does not get faster with more parallelism.

### 3.2 Scalability: the contract scales, the hand-written readers do not

What scales is the **contract**: every source produces yt-dlp-shaped info JSON, and one download path consumes it.
Adding a source does not touch the queue, the transfer, the post-processing or the publishing. This is the idea
worth taking.

What does not scale is **a scraper per site**. Every reader must follow its site's changes. yt-dlp's maintainers
already do that for over a thousand sites. Nezzel carries about 3,300 lines of site readers, plus the 890-line HTTP
client and about 2,100 lines of image handling, for ten sites.

### 3.3 Maintainability: good seams, heavy centre

| Good | Costly |
| --- | --- |
| `YoutubeFastSource` is an interface, so it is fakeable in tests (`NpeFastSourceTest`) | `YoutubedlEngine.kt`, 1,212 lines: inspection, NewPipe orchestration, two caches, transfer selection, rebuild/verify on expiry, updates, process gating |
| Any NewPipe failure, even a `LinkageError`, falls back. A broken extractor never breaks the app | Two extractors whose output must agree (format ids, `accepts()`, `FormatRecipe.equivalent()`) |
| A typed error taxonomy (27 `DownloadError` codes, each with retry/keep/resume rules) | Very defensive code (epochs, tokens, grace windows) that is correct but hard to change safely |
| `ARGV_VERSION` / resume recipe invalidates partial files when the command changes | NewPipe must be bumped whenever YouTube changes, just like yt-dlp, but by hand through a release |

---

## 4. The four projects at core level

### 4.1 Structure

| | Sona | Nezzel | Seal | YTDLnis |
| --- | --- | --- | --- | --- |
| Modules | `core/downloader`, `feature/download`, plus core db/datastore/model | `core` (domain), `engine`, `app` | `app`, `color` | `app` only |
| Central class | `YtDlp` (190 lines) | `YoutubedlEngine` (1,212) | `Downloader` (~700) + `DownloadUtil` (~870) | `YTDLPUtil` (~1,700) + `RuntimeManager` |
| Readers | yt-dlp | NewPipe (YouTube) → native readers (10 sites) → yt-dlp | yt-dlp | yt-dlp (NewPipe for search only) |
| Contract between reader and download | none (reads twice) | yt-dlp info JSON | none (reads twice) | yt-dlp info JSON, cached with expiry |

### 4.2 Runtime: Python, FFmpeg, yt-dlp

| | Sona | Nezzel | Seal | YTDLnis |
| --- | --- | --- | --- | --- |
| How it gets there | **Downloaded on demand**, per ABI, only the needed entries read by HTTP Range from Maven AARs | Bundled in the APK, extracted on first run | Bundled (`youtubedl-android`) | Bundled, plus optional packages from GitHub releases |
| What it includes | Python, FFmpeg, QuickJS | Python, FFmpeg | Python, FFmpeg, aria2c | Python, FFmpeg, aria2c, Node, Deno, QuickJS |
| yt-dlp update | `--update-to stable` every 7 days, **inside `info()`** | Two-stage: staged, swapped atomically, self-tested, rolled back if needed | `UpdateUtil` (stable/nightly) | GitHub (stable/nightly/master) |
| Warm-up | none | zipapp materialized to a directory, warm-up stamp | none | none |

### 4.3 Queue and execution

| | Sona | Nezzel | Seal | YTDLnis |
| --- | --- | --- | --- | --- |
| Stored in | Room | SQLite `JobStore` (schema v9) + batches | in memory (history in Room) | Room |
| Runs in | foreground service (`dataSync`) | WorkManager, expedited, network constraint | foreground service | WorkManager foreground worker |
| At once | 1 | 2 (fewer when the device runs hot) | 1 | 1-10 (setting) |
| Resume | no | yes (native: Range; yt-dlp: kept partials, recipe-checked) | no | yt-dlp `.part` only |
| Stale stream URLs | not handled | 24 h confirmation, re-verify the format, `FormatExpired` | not handled | on 403/410/JSON errors, drop the cached JSON and re-extract once (`DownloadWorker.kt:269-285`) |
| Saving | yt-dlp writes to the folder, MediaScanner | staging dir, then MediaStore or SAF, atomic, recovered on start | MediaScanner, SAF move for SD | cache dir, then moved, MediaScanner |

### 4.4 What each does best

| Project | Worth taking | Not worth taking |
| --- | --- | --- |
| **Sona** | Runtime downloaded on demand (small APK). `SONA_*` print protocol (clean events). Heatmap and chapters in the file's tags. Small, readable classes | Two extractions per download. Update run on the lookup path |
| **Nezzel** | NewPipe fast path. Info JSON as the contract. Extraction cache bounded by URL expiry. In-flight dedupe. Direct HTTP transfer with resume. Typed errors. Materialized zipapp | The god class. Ten site scrapers. Keystore-encrypted URLs and a one-way link key (privacy work beyond what Sona needs) |
| **Seal** | Simple, one engine. Music metadata enrichment (Deezer/iTunes). Format grid UI | Global singleton state machine. MMKV for settings |
| **YTDLnis** | Concurrency setting. Info JSON cache with expiry checks and a one-shot stale retry. Scheduling | A 1,700-line command builder. Six runtimes managed as packages |

---

## 5. Where Sona loses time today

Read from `core/downloader/.../YtDlp.kt`:

1. **Every call starts Python** (`run()`, `:105-133`): lookup, download and version check each start a new process.
2. **Two full extractions per download.** `info()` (`:63`) reads the link to show it, keeps only the title, uploader,
   duration, thumbnail and count, and throws the formats away. `download()` (`:77`) then reads the link again.
3. **The update can block the lookup.** `info()` and `download()` both call `updateIfDue()` first (`:64`, `:78`).
   Once a week, the user's first lookup waits for a yt-dlp self-update.
4. **YouTube's player script and the `n` challenge** are handled with QuickJS in a separate process, with the EJS
   solver fetched from GitHub the first time (`--remote-components ejs:github`, `:143`).
5. **Nothing is cached.** Typing, clearing and pasting the same link reads it again.

None of this is wrong, and it is all simple. It is just the slow way to read YouTube.

---

## 6. Proposed design for Sona

### 6.1 Rules

1. **One contract:** yt-dlp-shaped info JSON. Every source produces it. Both transfers read it.
2. **Fast sources are optional:** any failure falls back to yt-dlp. A fast source can cost time, never correctness.
3. **Add a fast source only where measurement shows it pays off.** Start with YouTube alone. yt-dlp keeps covering the
   other sites until a measured problem makes a reader worth it.
4. **No class over ~300 lines** in the downloader. Orchestration, caching, sources and transfers stay separate (the
   lesson from `YoutubedlEngine.kt`).
5. **The data is live:** every schema change is an explicit additive Room migration.

### 6.2 Layers

```text
feature/download
  DownloadViewModel ─ DownloadScreen ─ QuickDownloadActivity      (exist)
  DownloadQueue ─ DownloadService                                  (exist: Room + foreground service)
        │
core/downloader
  ┌──────────────────────────────────────────────────────────────┐
  │ MediaResolver                     (new, orchestration only)  │
  │   cache hit? → ExtractionCache                               │
  │   in flight? → join it                                       │
  │   YouTube and not failed before? → NewPipeSource (budget)    │
  │   otherwise / miss → YtDlpSource                             │
  └──────────────┬───────────────────────────────────────────────┘
                 │ Extraction(infoJson, MediaInfo with formats, expiresAt, provenance)
     ┌───────────┴───────────┐
  NewPipeSource           YtDlpSource           (both implement MediaSource)
  (new, in-process)       (YtDlp.info, now keeping the JSON)

  ExtractionCache        (new) memory LRU, bounded by the signed URL's expiry; plus one file per queued download
  TransferPlanner        (new) picks a transfer from the choices
     ├─ DirectTransfer   (new) OkHttp, Range chunks, resume sidecar, single-file streams
     └─ YtDlpTransfer    (YtDlp.download with --load-info-json; merge, captions, SponsorBlock, conversion)
  DownloaderRuntime      (exists) + zipapp materialization, update moved off the lookup path
  EmbeddedExtras         (exists) heatmap/chapters tags, written after either transfer
```

### 6.3 The contracts

```kotlin
/** Something that can read a link into yt-dlp-shaped info JSON. */
interface MediaSource {
    /** Whether this source may try [url] at all, without network. */
    fun accepts(url: String): Boolean

    /** Null when this source cannot read [url]: the next source is tried. Throws only for "no network". */
    suspend fun extract(url: String): Extraction?
}

data class Extraction(
    val infoJson: String,             // yt-dlp's own format, so --load-info-json takes it as is
    val info: MediaInfo,              // title, uploader, duration, thumbnail, playlist count, formats
    val expiresAt: Long?,             // earliest `expire=` in its stream URLs, minus a margin
    val source: Provenance,           // NEWPIPE or YTDLP: which one read it
)

/** How a queued download gets its bytes. */
interface Transfer {
    suspend fun run(download: Download, extraction: Extraction, outputDir: File, onEvent: (DownloadEvent) -> Unit)
}
```

`DownloadEvent` (`Item`, `Progress`, `Saved`) stays as it is, so `DownloadService` does not change.

### 6.4 Choosing the transfer

| The choices need | Transfer | Runtime needed? |
| --- | --- | --- |
| Audio, original format (m4a/opus), no SponsorBlock | **Direct** | **No**. TagLib writes the tags and cover |
| Video at a quality YouTube serves as one progressive file | Direct | No |
| Video at higher qualities (separate video and audio streams) | yt-dlp + FFmpeg merge | Yes |
| Audio converted (MP3), captions, SponsorBlock, chapters cut | yt-dlp | Yes |
| Any site other than YouTube | yt-dlp | Yes |

For Sona this is the main gain. The most common music download, "audio from a YouTube link", would need neither
Python nor FFmpeg. The 'Downloader' panel would only appear for what really needs it. Direct transfers to
`googlevideo.com` should request byte ranges in chunks (NewPipe's JSON already sets `http_chunk_size` to 10 MB).
YouTube is known to throttle long single-range reads, and Nezzel's single-request `DirectDownloader` only uses Range
to resume. This must be checked on a real download before it ships.

### 6.5 Caching and stale links

- **Memory:** last 8 links, 15 min, never past `expiresAt` (Nezzel's `RecentInspections`).
- **Per download:** when a download is queued, its `Extraction.infoJson` is written to
  `noBackupFilesDir/downloader/info/<downloadId>.json` and deleted when the download ends. The path follows from the
  id, so no column is needed.
- **Stale:** if the file is past `expiresAt`, or a transfer gets 403/410, delete it and extract again **once**
  (YTDLnis, `DownloadWorker.kt:269-285`). If the chosen quality is gone, fail with a clear message rather than
  silently downloading another (Nezzel's `FormatExpired`).

### 6.6 Runtime changes

- Move `updateIfDue()` out of `info()` and `download()`. Run it when the service starts with an empty queue, or from
  a periodic job. A lookup never waits for an update.
- Materialize the yt-dlp zipapp into a directory after each install or update (Nezzel `YtdlpRuntime.kt:536`).
  Measure the cold start before and after. Keep it only if it helps.

### 6.7 Queue and service

- Keep Room + `DownloadService`. They work, and the design above changes neither the queue's shape nor the events.
- **Concurrency:** let 2 run at once, as Nezzel does. Direct transfers are cheap. yt-dlp ones are limited to 1.
- **Android 15+:** a `dataSync` foreground service gets about 6 hours per 24 hours. A long queue can reach that. If it
  does in practice, move execution to a WorkManager foreground worker (Nezzel, YTDLnis). The queue in Room stays the
  same.
- **Schema (additive migration):** `formatId TEXT` (the chosen format, so a retry downloads the same thing),
  `bytesDone INTEGER` (resume and progress after a restart), `transfer TEXT` (`direct`/`ytdlp`, so a partial file is
  only resumed by the transfer that wrote it, like Nezzel's resume recipe).

### 6.8 Errors

Replace the free-text `error` with a small typed set mapped from both sources' failures. Keep the text for details:

`NoNetwork` · `Unavailable` (private, removed, region) · `LoginRequired` · `FormatGone` · `NoSpace` ·
`RuntimeMissing` · `Cancelled` · `Failed`

Each one knows whether Retry can help. Nezzel's 27 are more than Sona needs.

### 6.9 What not to take

- Site scrapers beyond YouTube (Nezzel): yt-dlp maintains these upstream.
- Encrypted URL storage (Nezzel), Node/Deno/aria2c runtimes (YTDLnis), MMKV (Seal), local MP3 encoder (Nezzel).
- A god class: if `MediaResolver` passes ~300 lines, it is doing someone else's job.

### 6.10 Risks

| Risk | Answer |
| --- | --- |
| NewPipe breaks when YouTube changes | Fallback to yt-dlp is automatic. Bump the dependency with each NewPipe release |
| APK grows (NewPipe Extractor, Rhino, jsoup, nanojson) | Measure `assembleRelease` before and after. Sona already downloads its runtime, so a few MB here is the one size cost |
| License | Sona, Nezzel and NewPipe Extractor are all GPLv3: compatible |
| Two extractors disagree on a format | Format ids are itags in both. Store the chosen `formatId`, and on retry fail with `FormatGone` instead of guessing |
| Direct downloads throttled | Chunked ranges (6.4). Fall back to yt-dlp on a stall |

### 6.11 Order of work

Each step ships alone, and each one is useful without the next.

1. **Keep the first extraction.** `YtDlp.info()` keeps the JSON. Download with `--load-info-json`, plus the stale
   retry. Move the update off the lookup path. *(Removes one full extraction per download. No new dependency.)*
2. **`MediaSource` + `MediaResolver` + `ExtractionCache`**, with `YtDlpSource` only. *(The seams, tested with fakes.)*
3. **`NewPipeSource`** for YouTube videos, with a budget, failure memo and in-flight join. *(The fast lookup. Works
   before the runtime is installed.)*
4. **`DirectTransfer`** for single-file audio and progressive video, with TagLib tags and `EmbeddedExtras`. *(YouTube
   audio with no runtime at all.)*
5. **Migration + concurrency + typed errors.**
6. **Playlists:** NewPipe or `--flat-playlist` pages, each entry queued as its own download.
