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
| The Japanese romanization dictionary, once you download it | Show Japanese lyrics in Latin script |
| A downloaded update APK, while an update is pending | Install the update; deleted once installed |

All of it is deleted when you uninstall Sona. You can clear it earlier in Android's app settings (Storage › Clear data).

**Android backup:** Sona allows Android's system backup. If backup is enabled on your device, Android may include this app data in your device backup, for example Google's. That backup is handled by Android and your backup provider, not by Sona. You can turn it off in Android's settings.

Sona changes your music files only when you ask it to - editing their tags, or deleting tracks you choose - and, while **Find missing lyrics** is on, to write lyrics it found into a file that had none. Outside its own storage it otherwise writes only playlists you export, to the location you choose, backups (below), and music and videos a restore puts back.

## Backups

Backups are made only when you ask for one, or on the schedule you turn on in Settings › Backup & restore. A backup holds your playlists, favorites, play statistics, resume positions, settings, and the covers, background image and font you chose. It lists your music and video files by their path and a fingerprint of their contents (SHA-256), so a restore can tell which ones a device is missing.

| Where | What is kept |
| --- | --- |
| On your device, in `Android/media/com.lhacenmed.sona/Backup` | Your last five backups. Android deletes this folder when Sona is uninstalled. |
| In your Google Drive, only if you connect it | Your backups, and one copy of each music and video file from the folders you choose, in a folder Sona creates |

You can encrypt backups with a passphrase (off by default). Encrypted backups are sealed with AES-256-GCM using a key made from your passphrase; your passphrase is never stored or sent anywhere, and without it an encrypted backup cannot be opened, by Sona or by anyone else.

### Google Drive

Connecting Google Drive is optional. When you connect it, Sona asks Google only for permission to see, create and delete the files Sona itself creates in your Drive (the `drive.file` scope). Sona cannot see any other file in your Drive. It also asks for your account's email address, only to show in Settings which account is connected; it stays on your device. Sona does not ask for your name or profile. Google keeps the sign-in; Sona stores no password or token of its own.

Sona uses this access only to upload, list, download and delete its own backups and files, at your request or on your backup schedule. It sends this data only to your own Google Drive, never to the developer or anyone else, and does not use it for advertising, analytics or any other purpose. You can disconnect Drive in Sona, or remove Sona's access at any time from your Google Account's security settings.

Sona's use and transfer of information received from Google APIs adheres to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements.

## Network use

Sona has no server of its own. It connects only to the services below, each for one feature, and sends none of them anything that identifies you. The only content it sends is a track's title, artist, album and length, when it looks up that track's lyrics or tags.

| Feature | When | What is sent | To |
| --- | --- | --- | --- |
| Update check | At launch and when the connection returns, if a release build is online; when you tap **Check now** in Settings › Updates | Nothing | `raw.githubusercontent.com` |
| Update download | When you tap **Update** in the update dialog | Nothing | `github.com`, which redirects to GitHub's download servers |
| Changelog, commits and avatars | When you open Settings › Updates or About | Nothing | `api.github.com`, `github.com` |
| **Find missing lyrics** (off by default, in Settings › Lyrics) | While music plays and the device is online, for the playing, next and previous tracks whose files have no lyrics | Title, artist, album, length | The lyrics services below |
| Lyrics search in the tag editor | When you search for a track's lyrics | Title, artist, album, length | The lyrics services below |
| Tag lookup in the tag editor | When you look up a track's tags, or auto-tag tracks | Title, artist, album | `api.deezer.com`, `itunes.apple.com`, and their cover image servers |
| Japanese romanization | When you choose to download its dictionary (13 MB) in Settings › Lyrics | Nothing | `repo1.maven.org` |
| Google Drive backup | When you back up or restore with Google Drive connected | Your backups and files | `googleapis.com`, your own Google Drive |

The lyrics services are LRCLIB (`lrclib.net`), Musixmatch (`apic-desktop.musixmatch.com`), NetEase Cloud Music (`music.163.com`), KuGou (`mobileservice.kugou.com`, `lyrics.kugou.com`), Genius (`genius.com`), SimpMusic (`api-lyrics.simpmusic.org`), BetterLyrics and Unison (`lyrics-api.boidu.dev`, `unison.boidu.dev`), and YouLyPlus (`lyricsplus.binimum.org`, `lyricsplus.prjktla.my.id`, `lyricsplus.prjktla.workers.dev`, `lyricsplus.atomix.one`, `lyricsplus-seven.vercel.app`). Turn off **Find missing lyrics** and Sona contacts them only when you search in the tag editor.

Like any web request, each of these reveals your IP address and a standard HTTP user agent to the service it goes to, which handles it under its own privacy policy - GitHub's under the [GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement), Google Drive's under the [Google Privacy Policy](https://policies.google.com/privacy).

Nothing else in the app uses the network.

## Sharing

Tracks are shared only when you choose Share, and only with the app you pick in Android's share sheet.

## Permissions

| Permission | Used for |
| --- | --- |
| Music and audio (`READ_MEDIA_AUDIO`; `READ_EXTERNAL_STORAGE` on Android 12 and older) | Reading your music files |
| Notifications | Playback controls and update download progress |
| Internet, network state | The requests listed under Network use |
| All files access | Editing tags and deleting tracks you choose, writing found lyrics into files, and putting restored music and videos back where they were |
| Install unknown apps | Installing a downloaded update; Android asks for your approval |
| Foreground service, wake lock | Playing, downloading, backing up and restoring while the app is in the background |
| Modify audio settings | The equalizer |

## Contact

Questions: open an [issue](https://github.com/LhacenMed/Sona/issues).
