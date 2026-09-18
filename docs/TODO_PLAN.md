# Sona — Development To-Do Plan

Ordered from most to least critical. Every entry describes **what** is broken or needed — not how to build it.

---

## Priority 1 — Critical: Data Integrity & Crash Risk

- [ ] **1.1 Fix large-selection sharing before it fails**
  When sharing more than 10 tracks, the current flow risks failure because it pushes many file references through the share mechanism at once.
  - Sharing 1–10 tracks: keep current behavior unchanged.
  - Sharing more than 10 tracks: the selection must be compressed into a single archive and shared as one file instead of many.
  - Shared files (either mode) must be exposed through secure content references, not raw file paths.
  - Memory usage while building the archive must stay flat regardless of how many tracks are included — it must not require holding all track data in memory at once.
  - The user must see progress feedback (percentage + count) while the archive is being prepared.
  - The estimated final archive size should be shown before the process starts.
  - The user must be able to cancel archive preparation while it's in progress.
  - The system share sheet must only open once the archive is fully ready, never before.
  - ⚠️ **Conflict to resolve:** the pasted spec suggests a much higher, "tunable" cutoff (e.g. ~50 items, or based on total size) for switching to archive mode, while the direct instruction says the switch happens above 10 tracks. Needs confirmation before implementation.

- [ ] **1.2 Fix duplicated items in Artists collections**
  Artist collections sometimes show duplicated entries — the same category of bug previously seen (and fixed) in Genre collections.
  - Apply the same category of fix to Artists.
  - Audit every other collection type (Albums, Genres, Playlists, Folders, etc.) to confirm none of them show the same duplication issue.

---

## Priority 2 — Critical: Core Playback & List Interaction Bugs

- [ ] **2.1 Fix queue-list scroll conflicting with bottom-sheet drag**
  The queue list sits inside a bottom sheet. Scrolling to the end of the list currently overlaps/conflicts with the gesture used to drag the sheet closed.

- [ ] **2.2 Stabilize skip-next / skip-previous controls**
  The player's skip-next and skip-previous behavior is currently unstable/unreliable and needs to be made consistent.

- [ ] **2.3 Fix lists hidden behind the mini player**
  When a track is playing, the mini player overlaps the bottom of content lists. There's no reserved blank space at the bottom, so the last item(s) can't be reached or seen.

- [ ] **2.4 Fix "more options" in the player opening the wrong sheet**
  The player's "more options" control should open the standard track-actions sheet, but currently doesn't trigger it correctly.

- [ ] **2.5 Fix limited trigger area for revealing the queue sheet**
  Swiping up should reveal the queue bottom sheet from anywhere on the player screen. Currently it only works when the gesture starts specifically on the sheet's top handle.

---

## Priority 3 — Add-to-Collection Flow Fixes

*Affects the "Add Tracks" and "Add to Collections" activities.*

- [ ] **3.1 Fix back-navigation during search + selection**
  With an active search and selected tracks, pressing back should first close the search; only a subsequent back press should clear the current selection. Currently this sequencing is wrong.

- [ ] **3.2 Remove forced selection mode on entry**
  - These activities should open into a normal (non-selection) list, like any other list in the app.
  - Selection/contextual mode should only begin via long-press on a track, matching standard behavior elsewhere.
  - Since items are shown normally, they should keep their usual bottom-sheet action menus and other standard interactions.

- [ ] **3.3 Show a confirmation dialog with total count**
  Pressing the confirm/add button should show a confirmation dialog stating the total number of tracks about to be added.

- [ ] **3.4 Remove redundant counter from the confirmation dialog**
  The dialog currently repeats a "Total: …" line in its body even though the count already appears in the title — remove the duplicate.

- [ ] **3.5 Keep confirmation dialogs consistent app-wide**
  Beyond this dialog, all confirmation dialogs across the app should be simple, visually consistent, and well designed.

---

## Priority 4 — Visual, Theming & Floating-Window Bugs

- [ ] **4.1 Fix white bottom section in floating-window mode**
  Running the app in Android's floating/freeform window mode leaves the bottom portion of the window white/blank for an unknown reason.

- [ ] **4.2 Fix status bar not following light/dark theme**
  The system status bar doesn't update its appearance when the app switches between light and dark mode.

- [ ] **4.3 Remove ripple on the player's seek-slider thumb**
  The draggable thumb on the wavy timeline slider currently shows a ripple effect on touch; it should not.

- [ ] **4.4 Recolor the favorite button to follow the app theme**
  The favorite/like button is a fixed pink color and should instead follow the app's theme color.

- [ ] **4.5 Change the "more options" icon**
  It should be a horizontal ellipsis (•••) rather than its current style.

- [ ] **4.6 Reposition the drag handle in queue items**
  When the queue is unlocked (reordering enabled) and items are draggable, the drag handle should sit on the left side of the item, positioned relative to the more-options button.

- [ ] **4.7 Verify the player works correctly in floating-window mode**
  Beyond the specific white-section bug above, test and confirm the app — and the player specifically — behaves correctly while running in floating/freeform window mode.

---

## Priority 5 — Player UI Consolidation & Polish

- [ ] **5.1 Add and consolidate the equalizer entry point**
  - Add an equalizer button to the cinematic player, in the queue bottom-sheet header, next to the existing lyrics and sleep-timer controls.
  - This becomes the single default way to reach the equalizer.
  - All other existing equalizer entry points should be removed.

- [ ] **5.2 Remove the duplicate share button from the player**
  Sharing is already available from the track's action/options sheet; the player's separate share button should be removed.

- [ ] **5.3 Rebuild the swipeable cover-art section to match Auxio exactly**
  The player's swipeable album-cover area should match Auxio's version exactly — showing only the artwork, with no extra color styling applied. (Auxio's version is XML-based; rebuild in Compose if feasible.)

- [ ] **5.4 Apply Material "expressive" animated styling to player buttons**
  Player control buttons (and the player UI generally) should animate: extending in size and changing corner radius on press.

---

## Priority 6 — Browsing & List Navigation Features

- [ ] **6.1 Add a shuffle FAB**
- [ ] **6.2 Add a scroll-to-top FAB**
- [ ] **6.3 Add fast-play / swipe action for playlist items** — scoped only to the Playlists activity.
- [ ] **6.4 Add an Auxio-style fast-scroll list control**
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

---

## Priority 7 — Track & Playlist Interaction Features

- [ ] **7.1 Add swipe actions on track items across all collections**
  Applies to Playlists, Artists, Albums, Genres, Folders, and any other collection listing:
  - Swipe left → queue the track to play next.
  - Swipe right → add the track to a playlist.

- [ ] **7.2 Build a playlist cover & name editor**
  Let the user set a custom playlist cover instead of the default stacked-covers image, choosing from:
  - The cover art of the top- or bottom-sorted track (per current sort order).
  - Any specific track's cover art, from within the playlist or elsewhere in the library.
  - A custom image from the device's media/gallery library.
  - Also allow editing the playlist's name from the same place.

---

## Priority 8 — Collection Detail Screen Feature

- [ ] **8.1 Add a collapsible header to collection detail screens**
  Applies to Albums, Artists, Playlists, Genres, and similar detail screens, placed above the item list, matching Auxio's exact style:
  - Shows cover art, collection type, name, track count, and total duration.
  - Includes two action buttons: Play and Shuffle.
  - On scroll, the header collapses into the top app bar, and the Play/Shuffle actions move into the top app bar's action area.

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

**Total: 41 items** across bug fixes, flow fixes, visual fixes, player polish, and new features.
