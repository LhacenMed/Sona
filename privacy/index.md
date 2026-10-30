---
title: Privacy Policy
---

# Privacy Policy

_Last updated: 2026-10-30_

Sona does not collect, sell or share personal data. It has no accounts of its own, no analytics, no advertising and no crash reporting. This document lists everything the app stores, and every time it uses the network.

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

Sona reads your music files but never modifies or deletes them. The only files it writes outside its own storage are playlists you export, to the location you choose, backups (below), and music and videos a restore puts back.

## Backups

Backups are made only when you ask for one, or on the schedule you turn on in Settings › Backup & restore. A backup holds your playlists, favorites, play statistics, resume positions, settings, and the covers, background image and font you chose. It lists your music and video files by their path and a fingerprint of their contents (SHA-256), so a restore can tell which ones a device is missing.

| Where | What is kept |
| --- | --- |
| On your device, in `Android/media/com.lhacenmed.sona/Backup` | Your last five backups. Android deletes this folder when Sona is uninstalled. |
| In your Google Drive, only if you connect it | Your backups, and one copy of each of your music and video files, in a folder Sona creates |

You can encrypt backups with a passphrase (off by default). Encrypted backups are sealed with AES-256-GCM using a key made from your passphrase; your passphrase is never stored or sent anywhere, and without it an encrypted backup cannot be opened, by Sona or by anyone else.

### Google Drive

Connecting Google Drive is optional. When you connect it, Sona asks Google only for permission to see, create and delete the files Sona itself creates in your Drive (the `drive.file` scope). Sona cannot see any other file in your Drive, and does not ask for your name, email address or profile. Google keeps the sign-in; Sona stores no password or token of its own.

Sona uses this access only to upload, list, download and delete its own backups and files, at your request or on your backup schedule. It sends this data only to your own Google Drive, never to the developer or anyone else, and does not use it for advertising, analytics or any other purpose. You can disconnect Drive in Sona, or remove Sona's access at any time from your Google Account's security settings.

Sona's use and transfer of information received from Google APIs adheres to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements.

## Network use

Sona uses the network **only for app updates**, and for **Google Drive** once you connect it:

| When | Request | To |
| --- | --- | --- |
| At launch and when the connection returns, if a release build is online | Download the version manifest (`version.json`) | `raw.githubusercontent.com` |
| When you tap **Check now** in Settings › Updates | Download the version manifest | `raw.githubusercontent.com` |
| When you tap **Update** in the update dialog | Download the new APK | `github.com`, which redirects to GitHub's download servers |
| When you back up or restore with Google Drive connected | Upload, list, download or delete Sona's own files | `googleapis.com`, your Google Drive |

The update requests send no personal data. Like any web request, they reveal your IP address and a standard HTTP user agent to GitHub, which handles them under the [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement). Google Drive requests carry your backups to your own Drive, under the [Google Privacy Policy](https://policies.google.com/privacy).

Nothing else in the app uses the network.

## Sharing

Tracks are shared only when you choose Share, and only with the app you pick in Android's share sheet.

## Permissions

| Permission | Used for |
| --- | --- |
| Music and audio (`READ_MEDIA_AUDIO`; `READ_EXTERNAL_STORAGE` on Android 12 and older) | Reading your music files |
| Notifications | Playback controls and update download progress |
| Internet, network state | The update and Google Drive requests above |
| All files access | Editing tags and deleting tracks you choose, and putting restored music and videos back where they were |
| Install unknown apps | Installing a downloaded update; Android asks for your approval |
| Foreground service, wake lock | Playing, downloading, backing up and restoring while the app is in the background |
| Modify audio settings | The equalizer |

## Changes

Changes to this policy are made in this file and are visible in the repository's history.

## Contact

Questions: open an [issue](https://github.com/LhacenMed/Sona/issues).
