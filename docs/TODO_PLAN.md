# Sona — Development To-Do Plan

Ordered from most to least critical. Every entry describes **what** is broken or needed — not how to build it.

---

## Priority 1 — Critical: Data Integrity & Crash Risk

- [x] **1.1 Make large-selection sharing zero-copy and predictable**
  Sharing many tracks must never copy audio into app storage. A track is shared as its own content reference, so a selection costs the same whether it holds three tracks or three hundred and the share sheet opens immediately.
  - Shared files are exposed through secure content references, not raw file paths.
  - The intent's parcel budget is counted before the intent is sent, rather than catching the overflow afterwards — an oversized intent usually fails on the far side of the transaction, where there is nothing to catch.
  - A selection that loses manually scanned tracks (which have no address another app may open) says so, rather than quietly sharing fewer than were picked.
  - **Resolved:** the archive was dropped. Compressing audio saves ~0–2% — zipping is pure repackaging — so it cost a full copy of the selection (~800&nbsp;MB for 100 tracks) to buy nothing but one URI instead of many. That also removed the 10-vs-50 threshold question, the progress feedback, the size estimate, the cancellation and the deferred share sheet, none of which are needed when nothing is built.

- [x] **1.2 Fix duplicated items in Artists collections**
  Artists and albums were identified by MediaStore's row id, while the filesystem walk that picks up files MediaStore has not indexed yet derives an id from the name instead. A newly downloaded track therefore arrived under two different artist and album rows — the walk's, then MediaStore's once it had indexed the file — and both were listed until the scan's final pass swept the first away.
  - Artist and album ids are now derived from the name, the way a genre's already was, so both arrivals land on the same row and there is nothing to sweep.
  - The manual walk and the MediaStore query now name artists and albums through one rule, so a tag's stray whitespace is not a second artist.
  - `SCANNER_SCHEMA_VERSION` bumped, so an existing install actually rescans instead of skipping on an unchanged MediaStore signature.
  - Names are compared whatever their case, as Auxio's `MusicGraph` clusters them (`rawName.lowercase()`): "5 Seconds of Summer" and "5 Seconds Of Summer" are one artist. Ids are derived from the lowercased name, and every track, album and artist row takes the spelling most tracks use (ties broken alphabetically, so each scan picks the same) - Auxio melds a cluster into its most popular vertex. Albums are keyed on artist and title alike; files the storage walk finds join the row their name already has. `SCANNER_SCHEMA_VERSION` 6.
  - **Audited:** genres were already name-identified (which is why they never showed this). Folders are derived per distinct `tracks.folderPath`, so they cannot duplicate. Playlists are trimmed on insert under a unique name index. Albums had the same bug as artists and are fixed with them.

- [x] **1.3 Study Fossify Music's fast-load-with-background-fetch behavior**
  Inspect the open-source Fossify Music project to extract its exact behavior/logic for showing already-saved library data instantly on app launch while resource and metadata tags, and any remaining track fetching, continue updating in the background. Use this as the reference behavior for how Sona should mount with previously saved data.
  - **Audited — already in place, and ahead of the reference.** Fossify paints whatever Room holds from the last run, fires the scan first, publishes MediaStore's results before the slow storage walk (`complete=false`), then the walk's (`complete=true`). Sona does each of those: `SonaApplication` starts the library's Room read before the first activity, the launch screen is held until it lands, `requestScan` is fire-and-forget on the application scope, and the scan's stage 1 publishes MediaStore before the walk.
  - Where Sona goes further: the scan is skipped outright when MediaStore's version and generation are unchanged, and when it runs it writes a diff, so an unchanged relaunch writes nothing and repaints nothing. Fossify rewrites every row on every launch; its fragments re-read on callbacks rather than observing, which is the only reason that does not flicker there.
  - Nothing ported. The research is in `docs/research/research_fossify_fast_load_and_permission_ux.md`.

- [x] **1.4 Study and adopt Budget's live folder-watching mechanism for the library**
  If access to inspect it is possible, examine the "Budget" project's WhatsApp-status-saver feature, which listens for live changes in a folder, and clone that mechanism literally. Merge it into Sona's own system so the app listens for live changes in any folder that is not excluded from scanning, rather than only picking up changes on a manual/triggered rescan. The resulting data tracking, saving, and restoring system must remain clean, maintainable, solid, stable, consistent, scalable, fast, and efficient.
  - **Adopted in shape, not in source.** Budget watches two known, flat folders with `FileObserver` (inotify). inotify is not recursive: covering every folder of the library would take a watch per directory on the whole device, re-registered for each new one, against a per-app watch limit. Budget's per-file `Added`/`Removed` patches don't fit either: Sona's albums, artists and genres are derived from the tracks, so a change has to be re-derived and diffed, not patched.
  - `MediaStoreChangeObserver` keeps Budget's structure — a cold `callbackFlow` that registers on collection and unregisters in `awaitClose`, debounced 500&nbsp;ms like Budget's recheck — but observes MediaStore's audio collection. One registration covers every indexed folder, which is exactly the set the scanner reads. Files written through shared storage are indexed by the system on Android 11+, so that is every file a download lands.
  - A change is a *refresh*: MediaStore re-read and diffed, without walking storage again. What the last walk found is carried from the database (minus files since indexed or deleted). A refresh costs one MediaStore query and, for a change outside the library (an excluded folder), writes nothing. Launch and an explicit rescan still walk.
  - Watching starts with the first scan request — only ever made with the permission granted — and lasts the process.
  - Scan requests are queued instead of dropped: one made mid-scan used to vanish (excluding a folder during the launch scan did nothing until the next launch); now it runs once the current scan ends, and any made meanwhile merge into it as the widest asked for.

---

## Priority 2 — Critical: Core Playback & List Interaction Bugs

- [x] **2.1 Fix queue-list scroll conflicting with bottom-sheet drag**
  The sheet was driven by two independent gesture owners at once: a raw `detectVerticalDragGestures` covering the whole sheet, and the list's `preUpPostDownNestedScrollConnection`. Over the list both were live, so which one took a swipe came down to a touch-slop race and to whether the list happened to be able to scroll in that direction at that instant — and each kept its own `VelocityTracker` and called `performFling` separately, so the owner could change mid-gesture.
  - `BottomSheet` now takes `isContentDraggable`. The collapsed bar is always draggable; the expanded content only when it holds nothing that scrolls.
  - The queue sheet passes `false`: the list owns every gesture over it and hands the sheet only what it cannot scroll, and the sheet is dragged by its header handle.
  - Present in ArchiveTune too (unfixed upstream), so this is a deliberate divergence from the clone rather than a port.
  - The stutter under a reorder drag was a second, separate cause: `PlaybackUiState` carries `positionMs`, which the controller polls every 500ms, and `PlayerViewModel.uiState` passed it straight through. Every tick produced a new `PlayerUiState` instance, and under strong skipping an unstable parameter is compared by identity — so `Queue` could never skip and rebuilt every visible row twice a second, including mid-drag. The position is now dropped before the state is built (`steadyPlaybackState`), which nothing shows: the seek bar and the lyrics poll the player directly through `currentPositionMs()`.

- [x] **2.2 Stabilize skip-next / skip-previous controls**
  The player's skip-next and skip-previous behavior is currently unstable/unreliable and needs to be made consistent.
  - The artwork was a three-page window - previous, current, next - rebuilt on every track change. A swipe skipped the moment the scroll offset touched zero, mid-fling, then the window re-keyed under the finger while `animateScrollToItem` pulled it back to the middle: a fast swipe stopped dead, and consecutive swipes raced each other.
  - Replaced with Auxio's playback pager, cloned: a `ViewPager2` hosted in Compose (`swiper/QueueCoverPager`), the whole queue as its pages, `CarouselTransformer` (the M3-carousel mask, gap and parallax), `UserAwarePagerCallback`, and Auxio's `dampen`, `recycler` and `smoothScrollByPageTo` - copied as is. A page is a queue slot, so a swipe asks for that slot outright (`onPlayQueueItem`), never for "next" or "previous".
  - The player's own moves come back as Auxio's do: a one-step move with the queue unchanged scrolls smoothly, anything else lands on its slot at once. Every move goes the same way, whatever caused it - the skip buttons, the notification, a track ending - because the pager follows the player's reported slot rather than being told to animate.
  - And, as Auxio's `updatePager` does, only once the frame the track change redraws has been committed (a frame-commit callback on Q+, two animation frames before): a skip redraws the whole player around the cover, and a slide started inside that frame spent its 300&nbsp;ms there and landed as a jump - which is why the skip buttons first showed no animation.
  - The theme follows the cover once it has landed. Every track change reseeded the whole app's colour scheme from its artwork and animated it for 350&nbsp;ms, recomposing everything that reads a theme colour - the player around the cover included - on every frame, right on top of the slide. `SonaThemeSeed` still extracts the colour at once, off the main thread, but applies it only after `CoverSlideDurationMillis` plus the slide's own start-up wait; a skip within that window cancels it, so skipping through several recolours once.
  - One deliberate divergence: Auxio's player moves in the same call that asks it to; Sona's `MediaController` reports back asynchronously. So the pager is turned to the player's track only at rest, and never back to a track a swipe has already left - otherwise the late report scrolls it back and `smoothScrollByPageTo`'s `stopScroll()` kills the swipe in progress.
  - A page is Auxio's `MaskableFrameLayout` around a `ComposeView` drawing `SonaCoverImage`, so covers and their placeholder are the library's. Double-tap seek on either half is kept.
  - The two private fields `dampen`/`recycler` reflect into are kept through R8 by `feature/player/consumer-rules.pro` - Auxio sidesteps this with `-dontobfuscate`.

- [x] **2.3 Fix lists hidden behind the mini player**
  When a track is playing, the mini player overlaps the bottom of content lists. There's no reserved blank space at the bottom, so the last item(s) can't be reached or seen.
  - Nothing reserved any bottom space: the player was drawn *beside* each activity's content, so no screen could know it was there. Untitled screens didn't even clear the navigation bar.
  - `PlayerOverlay.Content` now wraps the activity's content, and the player host provides `LocalBottomContentPadding` (`core:designsystem`) — exactly the mini player's top edge: navigation bar + spacing + height, the same value as the sheet's collapsed bound. It is ArchiveTune's `LocalPlayerAwareWindowInsets`, narrowed to the one side Sona needs.
  - Reserved persistently, whether or not a track is loaded, so a list's end never jumps under the player as it comes and goes.
  - Every scrolling container reads it once, at its shared root: `LibraryList` (every tab, detail screen and picker), the playlists tab, `SettingsList` (every settings screen), tab visibility, excluded folders (whose bottom add button is lifted with it) and the equalizer. Rows still scroll behind the bar; only the end is held clear. A reorder drag starts auto-scrolling above the mini player, not under it.
  - Titled hosted screens no longer inset the bottom in `HostActivity`'s Scaffold, which would have counted the navigation bar twice.

- [x] **2.4 Fix "more options" in the player opening the wrong sheet**
  The player opened a two-item sheet of its own (`PlayerMenuSheet`: go to album, go to artist), which is now deleted. The player and every queue row open the library's `OptionsSheet` instead.
  - `feature:player` does not depend on `feature:library`. The player takes a `trackOptionsSheet` slot, and `SonaPlayerOverlay` in `:app` fills it with `OptionsSheet(OptionsTarget.ForTrack(track))` — the same seam that already supplies `onGoToAlbum`/`onGoToArtist`.

- [x] **2.5 Fix limited trigger area for revealing the queue sheet**
  Swiping up should reveal the queue bottom sheet from anywhere on the player screen. Currently it only works when the gesture starts specifically on the sheet's top handle.
  - The whole open player was already covered by one drag detector - the player sheet's own. A swipe up there went to the player, which is already at full height, so it moved nothing; only the queue's bar had a detector for the queue.
  - `BottomSheet` takes `swipeUpSheet`, and the player passes its queue. The same detector decides which sheet a drag moves from its first movement: up while the player is expanded raises the queue, anything else moves the player as before (a swipe down still closes it). The choice holds until release, and the release flings the sheet that was dragged.
  - One detector, one velocity tracker, one owner per gesture - the rule 2.1 set - rather than a second detector over the player racing the first. Taps, the seek bar and the artwork's sideways swipe are untouched: none of them claims a vertical drag.
  - Present in ArchiveTune too (the queue opens only from its bar), so this is a deliberate divergence from the clone.

- [x] **2.6 Fix mini player disappearing after returning to the app**
  Sometimes the mini player disappears from an activity even though a track is still playing. This happens after leaving the app and coming back to it after a while, and currently requires closing and reopening the app to make the mini player reappear.
  - **Cause:** after a while in the background the system kills the process. The media notification stays, and its play button starts a new process and resumes the saved queue. Opening the app restores the activity, and its saved sheet anchor puts the mini player back. For a moment, though, the new process knows of no track: the controller hasn't connected, the saved queue hasn't been read back and the library hasn't loaded. The host took that as "no track" and started dismissing the sheet. The track arrived while the sheet was still sliding away, and the host checked where the sheet *was* (`isDismissed`: not yet) rather than where it was *going*, so it never brought the sheet back. The sheet then stayed dismissed for the rest of that process.
  - The host now reads the target (`isDismissedOrDismissing`, alongside `isExpandedOrExpanding`), as Auxio's `tryShowSheets`/`tryHideAllSheets` read `targetState`. So a track that arrives mid-slide always brings the sheet back.
  - "Not known yet" is no longer read as "none". `PlaybackUiState.isReady` turns true once the controller has connected and any saved queue is back. `PlayerUiState.isResolved` also waits for the library. Until both are loaded the sheet stays where the restored activity left it, so there's no dismiss-and-return flicker.
  - The mini player is still dismissed only when the queue is emptied: by swiping it down (stop and clear) or by the player dropping the queue. Auxio behaves the same way. Its bar hides only when the song becomes null, and dragging can't hide it (`isHideableWhenDragging() = false`).

---

## Priority 3 — Add-to-Collection Flow Fixes

*Affects the "Add Tracks" and "Add to Collections" activities.*

- [x] **3.1 Fix back-navigation during search + selection**
  With an active search and selected tracks, pressing back should first close the search; only a subsequent back press should clear the current selection. Currently this sequencing is wrong.
  - **Resolved:** while a search is open the bar keeps its field (with Add and Select all beside it) instead of the selection bar, so the bar's own back handling closes the search first; the picker's extra handler that dropped the picks first is gone.

- [x] **3.2 Remove forced selection mode on entry**
  - These activities should open into a normal (non-selection) list, like any other list in the app.
  - Selection/contextual mode should only begin via long-press on a track, matching standard behavior elsewhere.
  - Since items are shown normally, they should keep their usual bottom-sheet action menus and other standard interactions.
  - **Resolved:** rows are the library's own - a tap plays a track or opens a collection, the overflow opens its options sheet, and rows show what is playing. A selection brings the library's selection bar, with Add beside Select all.

- [x] **3.3 Show a confirmation dialog with total count**
  Pressing the confirm/add button should show a confirmation dialog stating the total number of tracks about to be added.
  - **Resolved:** the count is what will really be added - each track once, none the playlist already holds - so it never overstates. The screen now closes after adding; before, its back press only cleared the selection.

- [x] **3.4 Remove redundant counter from the confirmation dialog**
  The dialog currently repeats a "Total: …" line in its body even though the count already appears in the title — remove the duplicate.

- [x] **3.5 Keep confirmation dialogs consistent app-wide**
  Beyond this dialog, all confirmation dialogs across the app should be simple, visually consistent, and well designed.
  - **Resolved:** one `SonaConfirmationDialog` in the design system backs every confirmation - excluding folders, deleting playlists, adding or removing a playlist's tracks, clearing the lyrics cache - each titled with what it changes, held with a progress bar until done, then confirmed by a toast.

---

## Priority 4 — Visual, Theming & Floating-Window Bugs

- [x] **4.1 Fix white bottom section in floating-window mode**
  Running the app in Android's floating/freeform window mode leaves the bottom portion of the window white/blank for an unknown reason.
  - Likely fixed with 4.2, to confirm in a floating window: no activity was edge-to-edge of its own accord, so the navigation bar's area was left to the window theme to fill - a colour and an idea of light or dark of its own - rather than drawn by the app. `SonaActivity` now draws every screen edge to edge from its first frame, and the bars are styled from the Compose theme.

- [x] **4.2 Fix status bar not following light/dark theme**
  The system status bar doesn't update its appearance when the app switches between light and dark mode.
  - Nothing set how the system bars look: the app draws its screens behind them, but their icons were left to the window theme's own light or dark, a second source of truth beside the Compose theme the screens are drawn in. `SonaActivity` calls `enableEdgeToEdge()` before any content, and `SonaTheme` re-applies it with `SystemBarStyle.auto { darkTheme }` whenever its `darkTheme` changes - dark icons over a light theme, light over a dark one, the platform's translucent scrim over 3-button navigation - so the bars follow exactly what every screen is drawn in (Now in Android's arrangement).

- [x] **4.3 Remove ripple on the player's seek-slider thumb**
  The draggable thumb on the wavy timeline slider currently shows a ripple effect on touch; it should not.

- [x] **4.4 Recolor the favorite button to follow the app theme**
  The favorite/like button is a fixed pink color and should instead follow the app's theme color.
  - The Default player marked a favourite with `colorScheme.error`; it now uses `primary`, as the queue's favourite button already did - the icon and its card's tint alike.

- [x] **4.5 Change the "more options" icon**
  Every row now draws its overflow button through `SonaListRow`, whose glyph is the horizontal ellipsis - so the queue's rows changed with the library's rather than separately.

- [x] **4.6 Reposition the drag handle in queue items**
  The queue's rows are `SonaTrackRow`s now, which take the handle from `LocalDragHandle` and draw it to the left of the overflow button - the same place, on the same keylines, as a reorderable library list.
  - Reordering is reached by long-pressing a row, and left by system back or the close button beside the header's favourite button. The lock toggle, the selection and its floating toolbar (select all, delete) are gone: the queue has one mode, and a row is played, dragged or swiped away.

- [ ] **4.7 Verify the player works correctly in floating-window mode**
  Beyond the specific white-section bug above, test and confirm the app — and the player specifically — behaves correctly while running in floating/freeform window mode.

- [x] **4.8 Fix disabled dropdown menu items not shown as disabled**
  Dropdown menu items that are disabled are correctly non-selectable, but they aren't visually grayed out — they look identical to enabled items, which is misleading.

---

## Priority 5 — Player UI Consolidation & Polish

- [x] **5.1 Add and consolidate the equalizer entry point**
  - Add an equalizer button to the cinematic player, in the queue bottom-sheet header, next to the existing lyrics and sleep-timer controls.
  - This becomes the single default way to reach the equalizer.
  - All other existing equalizer entry points should be removed.

- [x] **5.2 Remove the duplicate share button from the player**
  Sharing is already available from the track's action/options sheet; the player's separate share button should be removed.

- [x] **5.3 Rebuild the swipeable cover-art section to match Auxio exactly**
  The player's swipeable album-cover area should match Auxio's version exactly — showing only the artwork, with no extra color styling applied. (Auxio's version is XML-based; rebuild in Compose if feasible.)

- [ ] **5.4 Apply Material "expressive" animated styling to player buttons**
  Player control buttons (and the player UI generally) should animate: extending in size and changing corner radius on press.

---

## Priority 6 — Browsing & List Navigation Features

- [x] **6.1 Add a shuffle FAB**
  - **Resolved, as one shuffle system** built from Auxio, ArchiveTune and Fossify Music. The library shows Auxio's shuffle button on every tab. It hides with Auxio's rules: when the library is empty, while the search is open, while a fast scroller thumb is dragged, and while the player rises over it. Lists keep room at their end so the last row scrolls clear of it. The launcher's "Shuffle all" shortcut uses the same `PlaybackController.shuffleAll()`, which waits for the library and the restored queue so a cold start neither loses nor overwrites it.
  - Turning shuffle on mid-queue keeps the order the queue was dealt when it was set, so off-and-on brings back the same order, as Sona always did. **Reshuffle each time** (off by default) deals a new order from the playing track instead, for every source (player, notification, other controller), as all three references do.
  - `QueueShuffleOrder.startingFrom` is the one place an order is dealt, so a smarter pick later replaces one function.
  - `ShuffleSettings` holds every shuffle option, in the playback settings file where the on/off state always lived, and Playback › Shuffle exposes them: **Keep shuffle** (Auxio's `keepShuffle`; ArchiveTune's "permanent shuffle"), **Reshuffle each time**, **Remember shuffle order** and **Shuffle all button**.
  - The shuffled order is saved with the queue (`queue_items.shufflePosition`, `MIGRATION_10_11`, Auxio's `QueueShuffledMappingItem`) and comes back when the app is reopened or a media button wakes the service, whether or not shuffle is on, so the kept order survives a restart too. Both restore paths now read `loadSavedQueue`. The saved order is armed on the empty player *before* the queue is set, because media3 runs custom commands at once but queues player commands, so an order sent after the queue could arrive first.
- [ ] **6.2 Add a scroll-to-top FAB**
- [ ] **6.3 Add fast-play / swipe action for playlist items** — scoped only to the Playlists activity.
- [x] **6.4 Add an Auxio-style fast-scroll list control**
  A draggable scroll indicator/thumb for sorted lists that shows the current section/order position while dragging, with adjustable sensitivity near the screen edges — matching Auxio's exact behavior.
- [ ] **6.5 Add pull-to-refresh to trigger a library rescan**
  For lists inside collection tabs, pulling down should trigger a rescan.
- [ ] **6.6 Collapse the main activity's top section on scroll**
  Matching Auxio's exact behavior.
- [ ] **6.7 Fetch and display real artist profile images**
  Replace the current stacked-cover thumbnail on artist list items with each artist's fetched profile image.
- [ ] **6.8 Integrate a Khamah-style iOS swipe gesture**
  Bring in the same swipe gesture mechanics (same math/algorithm) used in the referenced app "Khamah," specifically the interaction used in its "Wird session reader" when opening a new session.
  *Note: the original request doesn't specify which screen in Sona this should apply to — needs clarification.*

- [ ] **6.9 Expand the favorites system beyond tracks**
  Currently only tracks can be liked/favorited. Extend favoriting to Artists, Albums, Genres, and Folders collections as well, alongside the existing liked-tracks feature.
  - Add top tabs to the favorites area, similar to the tabs already used in the main activity, so each favorited collection type has its own tab.
  - *Under consideration:* a grid-style listing for favorited Artists/Albums, similar to the grid layout used in the Samsung Music app — flagged as an idea to explore, not a firm requirement.

---

## Priority 7 — Track & Playlist Interaction Features

- [x] **7.1 Add swipe actions on track items across all collections**
  Applies to Playlists, Artists, Albums, Genres, Folders, and any other collection listing:
  - Swipe left → queue the track to play next.
  - Swipe right → add the track to a playlist.

- [x] **7.2 Build a playlist cover & name editor**
  Let the user set a custom playlist cover instead of the default stacked-covers image, choosing from:
  - The cover art of the top- or bottom-sorted track (per current sort order).
  - Any specific track's cover art, from within the playlist or elsewhere in the library.
  - A custom image from the device's media/gallery library.
  - Also allow editing the playlist's name from the same place.
  - **Resolved:** `EditPlaylistScreen` edits the name and the cover as a draft, previewed live and saved together in one transaction; back discards both. `PlaylistCover` is stacked (the default), the first or last track, one track, or an image - and a playlist's `coverArtUris` is its resolved cover, so every place a playlist is drawn shows the choice without knowing about it. One rule (`PlaylistCover.coverArtUris`) resolves it for the list and for the editor's preview alike.
  - First and last track follow the playlist's current sort live, as the Favorites card already did: only playlists with that cover have their sorted tracks read. A chosen track is read in the playlists query itself, and falls back to the stack while it is not in the library.
  - An image comes from the system photo picker and is copied into app storage only on Save, sampled down to 1024&nbsp;px, so an abandoned edit leaves nothing behind; the copy it replaces, and a deleted playlist's, are removed.
  - Favorites takes a cover but keeps its name, and its shortcut card shows the cover it is given. Schema 10 (`MIGRATION_9_10`); the column default keeps Favorites' raw-SQL seed working on a fresh install.

---

## Priority 8 — Collection Detail Screen Feature

- [x] **8.1 Add a collapsible header to collection detail screens**
  Applies to Albums, Artists, Playlists, Genres, and similar detail screens, placed above the item list, matching Auxio's exact style:
  - Shows cover art, collection type, name, track count, and total duration.
  - Includes two action buttons: Play and Shuffle.
  - On scroll, the header collapses into the top app bar, and the Play/Shuffle actions move into the top app bar's action area.
  - **Done** as Auxio's `fragment_detail` (its tall-phone `layout-h480dp`), rebuilt as one list: `DetailScaffold` (`core:designsystem`) makes the header the list's first item and pins the bar over it. The collapse is worked out from the list's scroll, with Auxio's `onOffsetChanged` numbers - parallax 0.85, the header shrinking by 0.12 and fading over the first half, the title and the round Play (tonal) and Shuffle (filled) buttons fading and rising 8&nbsp;dp into the bar over the second half, the buttons laid out only once they start to show. It settles open or collapsed on release (`exitUntilCollapsed|snap`), and the bar lifts to `surfaceContainer` once the list scrolls under it. Everything is read while drawing, so scrolling recomposes nothing. The header is laid out as Auxio's `AppBarLayout` is, outside the list: it collapses by taking the scroll before the list does (a nested scroll connection, `exitUntilCollapsed`), so it collapses the same over one row as over a thousand, and the list sits below it at the height of the screen under the collapsed bar - a collapse only moves things. The header can be dragged itself, carrying on into the list once collapsed (`ContinuousAppBarLayoutBehavior`). The settle runs in a job of its own that any touch cancels, so interrupting it never stops the next one. Searching holds the header collapsed with its results from the top, and closing the search puts the header and the list back exactly where they were.
  - `SonaTopAppBar` takes a `TopBarCollapse` for this; selection and search modes are unchanged. The header is `DetailHeader`: a 256&nbsp;dp cover (28&nbsp;dp corners, round for an artist), type, name, subhead, info, and Play/Shuffle in an expressive button group.
  - The list is in sections, as Auxio's `DetailGenerator` builds it: an artist lists its Albums (newest first) and those it Appears on, then Tracks; a genre, its Artists, then Tracks; an album groups its tracks by disc once there is more than one. Section headings are Auxio's `item_header`, with the sort button on the Tracks heading, and dividers between sections. An artist's albums show their year and its tracks their album.
  - Search moved into the ⋮ menu beside the collection's actions; searching lays the results straight under the bar. Sona reads no release types, so an artist's own albums are one section rather than Auxio's albums, EPs, singles and so on. Playlist dragging now shares one drag system with the library lists (`rememberReorderableRows`).

---

## Priority 9 — Major New Systems & Integrations

- [ ] **9.1 Complete remaining settings/preferences functionality**
- [ ] **9.2 Add app icon quick-action shortcuts** (long-press shortcuts from the device's home screen/app list, outside the app itself)
- [ ] **9.3 Add home screen widgets**, styled after the referenced app "ArchiveTune"
- [ ] **9.4 Integrate a synced lyrics editor with a lyrics-provider fetching system**, based on the referenced app "ArchiveTune"
- [ ] **9.5 Integrate a metadata/tag editor system**, based on the referenced system "AutomaTag"
- [ ] **9.6 Add a Videos tab** listing all video files on the device, playable as audio only, based on the referenced app "MiMusic"
- [ ] **9.7 Add a video-to-audio converter**, based on the referenced tool "MP3 Video Converter"

---

**Total: 46 items** across bug fixes, flow fixes, visual fixes, player polish, and new features.
