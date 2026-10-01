# Changelog

All notable changes to Sona are listed here, newest first.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each release's full notes are on its [GitHub release page](https://github.com/LhacenMed/Sona/releases).

## [Unreleased]

## [1.6.3] - 2026-10-01

### Added

- A synced lyrics editor - time each line as it is sung, nudge or type its time, and bring lyrics in from the web, the clipboard or a file.
- Find missing lyrics - the playing, next and previous tracks get lyrics from the web written into their files when they have none.

### Changed

- Lyrics are read from and saved into each file, so they are the same wherever the file plays.

### Fixed

- Tapping outside a dialog closes it.
- Pressing an option in a dialog highlights the whole width of the dialog.

### Removed

- The lyrics cache and queue lyrics preloading, with their settings.

## [1.6.2] - 2026-10-01

### Added

- Add multiple new online lyrics sources.
- Add video library support to music player.
- Show all videos as a flat list in the Videos tab.
- Pick excluded folders from library, not the system picker.
- Quick play - the library button and the launcher shortcut play one chosen source, shuffled or in order, and a long press on the button plays another.
- A Custom lyrics sync offset, typed in exact milliseconds.
- A spinner covers the screen while tags are saved, and the editor closes once they are.

### Changed

- Use Android's cached video thumbnails instead of decoding frames.
- Remove sticky header pinning from section components.
- Every setting's chooser lists the option it starts at first, as Default.
- Settings that only apply while another is set some way stay on screen, faded, instead of disappearing.
- Repeat, shuffle and favorite are offered to every media control - the notification, the system's media controls, a car or a watch.
- The lyrics menu looks and behaves like every other options sheet.
- Saving tags is much faster, and a chosen cover downloads while you edit.

### Fixed

- On a detail screen with only a few tracks, dragging anywhere below them opens and collapses the header.
- Each track shows its own cover, and a cover changed in a file shows at once.
- Artists whose tracks sit on another artist's album are listed under their own name.
- Album years match their tracks' tags and follow every change to them.
- Saving the tags of the playing track no longer stops playback.
- The notification and lock screen show a track's new tags as soon as they are saved.
- Tags written into MP4 and M4A files named as another format now reach the file, and a file damaged that way is repaired on its next save.

### Removed

- The player button colours setting.

## [1.6.1] - 2026-09-28

### Added

- Edit a track's tags from its menu: its best match from Deezer and iTunes applied in one press, every tag editable, a cover from the matches or the gallery, and lyrics from five sources - each showing whether it was synced to a recording as long as the track.
- Lists stretch past their ends and spring back.
- Rescanning the library shows its progress in a notification, and says when it is done.

### Changed

- A track counts towards Most played once 80% of it has been played, rather than after 10 seconds, and skipping ahead does not count. Plays started from a headset, the notification or another app count too.
- Recent lists a track once it actually plays, not when the queue is restored as the app opens.
- Every section folds away at a press on its heading, smoothly, and a heading held at the top is underlined while its rows scroll beneath it.
- A newer version is prompted for on whatever screen is open, not only the library.
- Japanese lyrics romanization downloads its 13 MB dictionary once, from Settings › Lyrics, instead of it coming with every install - Sona is now a 5 MB download instead of 20 MB. Anyone who had it on turns it on again there.
- Sona reads in English throughout, including the few labels its components used to show in the phone's language.

### Fixed

- A new release is found as soon as it is out, and a check that failed offline is made again the moment the connection is back.
- Messages no longer queue up one after another; a new one replaces the last.
- Sona no longer asks for access to music when it already has access to all files.

## [1.6.0] - 2026-09-27

### Added

- Swipe a playlist, or Most played, to the right to play it and to the left to shuffle it.
- A banner says when the connection is lost, and again when it is back.
- Pick which build of an update to download - the one made for your phone's processor, or the universal one.
- The download dialog shows how much has arrived, of how much, and how fast.
- Show updates on launch, in Settings › Updates, to stop the update sheet opening with the app.
- Long sections - an album's or a genre's, a release in the changelog, About's contributors - can be collapsed, and their headings stay in view while you scroll them.
- About shows which build of Sona is installed.

### Changed

- The Beta update channel is now Artifact: the alpha, beta and release candidate builds of the next version.
- About and Updates look like the rest of Settings, and licenses, the changelog and recent commits have screens of their own.
- Contributors, releases and recent commits open on what was loaded last, offline too, and refresh whenever you are back online.
- The top bar tints as a list scrolls under it.
- Downloaded updates are checked before they are installed, so a damaged download is caught.

### Fixed

- The fast scroll thumb no longer jumps on screens with several sections, such as a genre's artists and tracks.
- Contributors' pictures load.
- Debug builds are no longer offered updates.

## [1.5.0] - 2026-09-26

### Added

- Theme: System, Light or Dark, and a Black theme while dark.
- Dynamic colors, which follow the wallpaper recoloured by the playing cover. Turned off, Wallpaper colors, Cover colors and a Color palette can each be chosen instead.
- A Color palette screen with 68 presets, and a Theme creator for your own palette from four seed colors. Themes can be imported and exported as files.
- Font: Default (Poppins), System, Inter, or any .ttf you pick.
- Player background styles: Follow theme, Gradient, Blur, Coloring, Blur gradient, Glow, Glow animated, and Custom, your own image with blur, contrast and brightness.
- Mini player background styles: Follow theme, Gradient and Glow.
- Player button colors, Hide player thumbnail, and Swipe to change song, which covers the mini player too.
- An Updates screen with your version and the latest one, the changelog, and the development branch's recent commits.
- Update channels: Stable, or Beta for pre-releases as well.
- Update notifications: a check every few hours, and a notification whose Download starts the update.
- An About screen with links, open-source licenses, credits and contributors.
- Storage shows the library database and image cache sizes, rescans the library, and clears the image cache.

### Changed

- The default font is Poppins. System keeps the previous look.
- Lyrics background style moves from Lyrics to Appearance, and the lyrics follow the player's custom image while it has one.
- A new version is announced with a sheet showing its release notes, and downloads with a progress dialog.
- Updates are found from GitHub releases, and download the APK made for your device instead of the universal one.

### Removed

- The Update alerts switch. A new version is announced once per session.

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
