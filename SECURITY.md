# Security Policy

## Supported versions

Only the **latest release** receives security fixes. Fixes ship as a new release, which installed copies are offered through the in-app updater.

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Report it privately through GitHub: go to the repository's **[Security › Advisories](https://github.com/LhacenMed/Sona/security/advisories/new)** page and choose **Report a vulnerability**. Only you and the maintainer can see the report.

Include:

- The Sona version (Settings › Updates › Installed version) and your Android version.
- What an attacker can do, and what they need first (another app installed on the device, network access, a crafted file…).
- Steps or a proof of concept to reproduce it.

You will get a reply in the report thread. Once a fix is released, the advisory is published and you are credited unless you ask not to be.

## Scope

In scope, meaning problems in Sona's own code or configuration:

- **The update mechanism**: fetching `version.json`, downloading the APK, or handing it to the installer.
- **Exported components**: the playback service and media button receiver, which other apps can reach by design.
- **File access**: the file provider that hands update APKs to the installer, the links created when sharing tracks, and how media files are read.
- **Handling of untrusted input**: audio tags, embedded lyrics, and imported M3U playlists.

Out of scope:

- Problems that need a rooted or already compromised device.
- Vulnerabilities in Android itself or in third-party libraries, unless Sona uses them in an unsafe way. Report those upstream.
- Someone installing an APK that did not come from this repository's releases.

## How updates are protected

- The version manifest and APKs are downloaded over HTTPS from GitHub (`raw.githubusercontent.com` and `github.com`).
- Android installs an update only if it is signed with the same key as the installed app. A tampered or re-signed APK is rejected by the system installer.
- The signing key is never stored in the repository. The release workflow reads it from encrypted GitHub Actions secrets.
