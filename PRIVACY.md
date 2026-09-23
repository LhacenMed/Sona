# Privacy Policy

_Last updated: 2026-09-23_

Sona does not collect, sell or share personal data. It has no accounts, no analytics, no advertising and no crash reporting. This document lists everything the app stores, and every time it uses the network.

## Data stored on your device

Sona keeps the following **only on your device**, in its private app storage:

| Data | Purpose |
| --- | --- |
| Library index: track, album, artist, genre and folder information read from your music files | Show and search your library quickly |
| Playlists and Favorites | Your playlists |
| Play statistics: play counts and when tracks were last played | The Recent and Most played lists |
| Playback state: the queue, current track and position | Resume where you left off |
| Settings | Your preferences |
| Lyrics cache | Avoid re-reading lyrics from files |
| A downloaded update APK, while an update is pending | Install the update; deleted once installed |

All of it is deleted when you uninstall Sona. You can clear it earlier in Android's app settings (Storage › Clear data).

**Android backup:** Sona allows Android's system backup. If backup is enabled on your device, Android may include this app data in your device backup, for example Google's. That backup is handled by Android and your backup provider, not by Sona. You can turn it off in Android's settings.

Sona reads your music files but never modifies or deletes them. The only files it writes outside its own storage are playlists you export, to the location you choose.

## Network use

Sona uses the network **only for app updates**:

| When | Request | To |
| --- | --- | --- |
| At launch and when the connection returns, if a release build is online | Download the version manifest (`version.json`) | `raw.githubusercontent.com` |
| When you tap **Check now** in Settings › Updates | Download the version manifest | `raw.githubusercontent.com` |
| When you tap **Update** in the update dialog | Download the new APK | `github.com`, which redirects to GitHub's download servers |

These requests send no personal data. Like any web request, they reveal your IP address and a standard HTTP user agent to GitHub, which handles them under the [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

Nothing else in the app uses the network.

## Sharing

Tracks are shared only when you choose Share, and only with the app you pick in Android's share sheet.

## Permissions

| Permission | Used for |
| --- | --- |
| Music and audio (`READ_MEDIA_AUDIO`; `READ_EXTERNAL_STORAGE` on Android 12 and older) | Reading your music files |
| Notifications | Playback controls and update download progress |
| Internet, network state | The update requests above |
| Install unknown apps | Installing a downloaded update; Android asks for your approval |
| Foreground service, wake lock | Playing and downloading while the app is in the background |
| Modify audio settings | The equalizer |

## Changes

Changes to this policy are made in this file and are visible in the repository's history.

## Contact

Questions: open an [issue](https://github.com/LhacenMed/Sona/issues).
