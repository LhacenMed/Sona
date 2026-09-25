# Changelog

All notable changes to Sona are listed here, newest first.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each release's full notes are on its [GitHub release page](https://github.com/LhacenMed/Sona/releases).

## [Unreleased]

## [1.4.0] - 2026-09-25

### Added

- Shuffle all can play one collection instead of every track: Favorites, or any playlist, artist, album, genre or folder. The launcher's Shuffle all shortcut plays the same.
- Long press the shuffle button for a menu of All tracks, Favorites, the collection you chose and Other. Picking one makes it the button's source and shuffles it at once.
- Settings › Playback › Shuffle all plays, choosing All tracks or opening a searchable picker of your playlists, artists, albums, genres or folders. Other in the shuffle menu opens the settings scrolled to it, and highlights it.
- A scroll to top button on every list: the library tabs, Playlists, every album, artist, genre, folder and playlist screen, the pickers, the settings and the equalizer. On a detail screen it opens the header again as it goes.

### Changed

- The shuffle button is larger, and springs as it shows, hides and opens.
- The floating buttons stand just above the mini player, or above the navigation bar without one, and move with it as it slides in and out.
- The floating buttons step aside together while the player is raised, while a list is fast scrolled, and as a list reaches its end.

## [1.3.0] - 2026-09-25

### Added

- Swipe actions on library rows: in an album's, artist's or playlist's screen, swipe a track, album or artist one way to play it next and the other way to add it to the queue.
- Queue rows swipe too: one way to play next, the other to remove, with an undo.
- A swipe ticks as it reaches the point where letting go acts, and again if pulled back behind it.
- Disable animations, Force high refresh rate and Haptics now work. Animations start off on low-memory devices, and the refresh rate row shows the display's fastest rate.
- Most played is pinned at the top of Playlists once something has been played enough to count.
- The Playlists shortcut card shows the cover of the playlist listed first.

### Changed

- The queue has no separate reorder mode: every row can be dragged by its handle at any time, shuffled or not, and an undone removal goes back exactly where it was.
- Round mode now shapes the whole app, not only covers: turned off, every corner is square.
- Playlists lists only the playlists you made. Favorites and Recent keep their own shortcut cards.
- Creating a playlist is one New playlist button: start empty, from a folder or from a playlist file, then name it. An empty Playlists screen offers to create one.
- New defaults, for settings not yet changed: tracks and a playlist's tracks sort newest added first, playlists by last changed, covers are cropped square, the fast scroller's touch area is Narrow, the seek bar is circular, and Rewind before skip back and Stop after current track are off.

### Fixed

- A slightly slanted sideways swipe on the cover or mini player no longer drags the player sheet, and a slanted scroll no longer swipes a list row.
- A drag the list, sheet or cover has taken is no longer snatched part-way by a row or the sheet, and a claimed drag no longer moves what is underneath it.
- Swiping past the first or last cover and back now turns to the neighbouring cover.
- Long pressing the artist under the player's title gives the same haptic as the title.
- Removing a track from a shuffled queue no longer flashes the queue in its unshuffled order.
- Importing a playlist file that matches no tracks no longer leaves an empty playlist behind.

## [1.2.0] - 2026-09-25

### Added

- Shuffle all button on the library tabs, and a "Shuffle all" launcher shortcut.
- Shuffle settings under Playback › Shuffle: Keep shuffle, Reshuffle each time, Remember shuffle order and Shuffle all button.
- A shuffled queue comes back in the same order after the app is closed.
- Drag to select, as in a file browser: long press a row and drag across the list to select a range, scrolling at its edges; long press a second row to select everything between the two.
- Delete from device, for a track, an album, artist, genre or folder, and a selection. With all-files access granted it deletes without Android asking each time; without it, Android's own request asks.
- Remove from playlist in the menu of a track opened inside a playlist.
- Settings › Permissions, listing every permission Sona uses, what it is for and whether it is allowed, with a tap to grant or change it.

### Changed

- A long press now selects a row rather than toggling it, so it can start a drag.
- Sorting by date added groups tracks and albums by month rather than by year, and the fast scroller's popup shows the month above the year.
- Sona asks for music and notification access together, in one dialog, when it opens.
- Every dialog shares one layout: the title and buttons stay in place while long content scrolls, and dialogs keep a sensible width on tablets and in landscape.
- One picker for adding tracks to a playlist and for choosing where an imported playlist goes.
- Buttons, icon buttons and connected button groups all tighten to the same corners while pressed, and the shuffle all button follows them.
- New icons for Import and Export.

### Fixed

- The shuffle all button steps aside when a list's last row reaches it, instead of lists keeping empty room for it at their end.
- Opening or closing search on a detail screen slides its header out of the way and back to where it was, and no longer flashes Play and Shuffle in the bar.
- A quick double tap, or opening a screen that is already showing, no longer stacks a second copy of it.

## [1.1.0] - 2026-09-23

### Added

- Playlist editor for a playlist's name and cover: stacked track covers, its first or last track, any track in the library, or an image from the photo picker. Favorites can take a cover too.
- Fast scroller on every library list, with a popup naming the current section as the sort defines it and a haptic tick as it changes.
- Fast scroll touch area setting (Narrow, Standard or Wide) under Behavior › Display.
- Rubber-band swipe on the mini player: it follows the finger to the skip point, ticks, then resists; towards a side with no track it resists from the start.
- Rubber-band stretch on the player's cover when swiping past the first or last track, instead of not moving at all.

### Changed

- The mini player now floats with an even gap above the end of every list.

### Fixed

- A mini player swipe can be undone before release on the first and last track, and pulling out and snapping back no longer skips.

## [1.0.0] - 2026-09-23

First stable release.

### Added

- Library tabs for tracks, artists, albums, genres and folders, with search, per-list sorting, intelligent sorting, hideable tabs and excluded folders.
- Detail screens with collapsing headers for albums, artists, genres, folders and playlists.
- Live library updates when files are added or removed.
- Multi-select across tabs, and an options sheet on every row.
- Sharing of any number of tracks without copying files.
- Playlists: create, rename, delete, create from a folder, custom drag order, M3U import and export, and adding whole collections with a confirmation count.
- Favorites, Recent and Most played.
- Mini and full player with swipeable covers, a reorderable queue, and the queue's source collection in the header.
- Repeat all, repeat one, stop after current track, shuffle, rewind before skip-back, sleep timer and equalizer.
- Queue and position restore, and resume from a media button.
- Media notification with shuffle, repeat and favorite controls.
- Selectable player and seek bar styles.
- Synced lyrics from embedded LRC, TTML and QRC, with word-by-word highlighting, romanization and preloading.
- Artwork-based dynamic colours, light and dark themes, composed collection covers, animated launch screen and edge-to-edge layout.
- In-app updates from GitHub Releases, with an option to check only on request.

[Unreleased]: https://github.com/LhacenMed/Sona/compare/v1.4.0...HEAD
[1.4.0]: https://github.com/LhacenMed/Sona/compare/v1.3.0...v1.4.0
[1.3.0]: https://github.com/LhacenMed/Sona/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/LhacenMed/Sona/compare/v1.1.1...v1.2.0
[1.1.0]: https://github.com/LhacenMed/Sona/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/LhacenMed/Sona/releases/tag/v1.0.0
