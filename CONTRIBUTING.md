# Contributing to Sona

Thanks for helping. This document covers how to report problems, propose changes, and get a pull request merged. By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Contents

- [Contributing to Sona](#contributing-to-sona)
  - [Contents](#contents)
  - [Reporting a bug](#reporting-a-bug)
  - [Requesting a feature](#requesting-a-feature)
  - [Setting up](#setting-up)
  - [Branches](#branches)
  - [Making a change](#making-a-change)
  - [Code guidelines](#code-guidelines)
  - [Commit messages](#commit-messages)
    - [Changelog trailers](#changelog-trailers)
  - [Pull requests](#pull-requests)
  - [Using code from other projects](#using-code-from-other-projects)
  - [Releases](#releases)

## Reporting a bug

1. Update to the [latest release](https://github.com/LhacenMed/Sona/releases/latest) and check the bug still happens.
2. Search [existing issues](https://github.com/LhacenMed/Sona/issues?q=is%3Aissue) (open and closed) to avoid duplicates.
3. Open a [bug report](https://github.com/LhacenMed/Sona/issues/new?template=bug_report.yml) and fill in every field. The form asks for:
   - **App version**, from Settings › Updates › Installed version.
   - **Device and Android version.**
   - **Steps to reproduce**, starting from opening the app.
   - **Expected and actual behaviour.**
   - **Logs**, for crashes. With USB debugging on: `adb logcat -d > sona-log.txt` right after the crash, then attach the file.

A bug that can be reproduced gets fixed. One that cannot usually stays open until someone can.

**Security problems are not bugs to report publicly.** Follow [SECURITY.md](SECURITY.md).

## Requesting a feature

Open a [feature request](https://github.com/LhacenMed/Sona/issues/new?template=feature_request.yml). Describe the **problem** you want solved before the solution you have in mind; there is often more than one way to solve it.

Sona is an offline player for local files. Requests for streaming, accounts, cloud sync or online services are out of scope.

## Setting up

Prerequisites and build commands are in the README: [Build from source](README.md#build-from-source).

Short version:

```bash
git clone https://github.com/LhacenMed/Sona.git
cd Sona
git checkout dev
git config core.hooksPath scripts/hooks
./gradlew :app:assembleDebug
```

You do not need a signing key. Debug builds use the default debug key when `keystore.properties` is absent.

`git config core.hooksPath scripts/hooks` turns on the repository's commit hook: each commit message is checked as you commit it, the same way CI checks a pull request's - see [Commit messages](#commit-messages).

## Branches

| Branch | Role |
| --- | --- |
| `dev` | Where work happens. **All pull requests target `dev`.** |
| `main` | What was last released. Only the release pipeline writes to it. |
| `hotfix/*` | The maintainer's: a fix released on its own, made from `main` - see [docs/RELEASING.md](docs/RELEASING.md#hotfixes). |

Create your branch from an up-to-date `dev`:

```bash
git checkout dev
git pull
git checkout -b fix/queue-scroll
```

Name branches `<type>/<short-description>`, using the same types as [commit messages](#commit-messages).

## Making a change

1. **For anything bigger than a small fix, open an issue first** and describe the approach. It avoids work that cannot be merged.
2. Keep one pull request to one change. A bug fix and an unrelated cleanup are two pull requests.
3. Change only what the change needs. No drive-by reformatting, renaming or refactoring of unrelated code.
4. **Verify it:**
   - `./gradlew :app:assembleDebug` must succeed with no new warnings in the files you touched.
   - Run the app on a device or emulator and exercise the change, including the empty, loading and error states it can reach.
   - For UI changes, check light and dark theme.

Sona has no automated test suite yet, so manual verification is required. Say in the pull request what you tested and on which device.

## Code guidelines

**Match the code around you.** The codebase has a consistent style; a new file should read as if the same person wrote it.

**Where code goes:**

| Kind of code | Module |
| --- | --- |
| A new screen or user-facing feature | the matching `:feature:*` module, or a new one |
| A UI component used by more than one feature | `:core:designsystem` |
| A user setting | a `Setting` in `:core:datastore`, registered in `SettingsLoader` |
| Library data (tracks, albums, playlists) | `:core:data` (`LibraryRepository`), backed by `:core:database` |
| A new screen reached by navigation | a `Screen` implementation, opened with `LocalNavigator.current.go(...)` |

Feature modules do not depend on each other unless they already do. If two features need the same thing, it belongs in a `:core:*` module.

**Kotlin and Compose:**

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Name things for what they mean to the user or the domain, not for how they are implemented.
- Comments explain **why**, not what. Public and non-obvious declarations get KDoc.
- No unused code, parameters or imports. Remove what your change makes unused.
- No speculative options or configuration nobody asked for.
- Do not handle errors that cannot happen. Do handle the ones that can: I/O, network, a missing file.
- Keep layouts stable: a screen should not jump when data arrives, when a list is empty, or when an action becomes available.
- Pass frequently changing values to rows as lambdas (see `TrackRow`), so one change does not recompose a whole list.
- Anything that touches disk, database or network runs off the main thread.

**Dialogs:** confirmations use `SonaConfirmationDialog`; dialog buttons use `SonaActionButtonGroup`.

## Commit messages

Sona uses [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary>
```

- **type**: `feat`, `fix`, `perf`, `refactor`, `style`, `build`, `ci`, `docs`, `chore`, `test`, `revert`
- **scope**: the area changed, usually the module: `library`, `player`, `playback`, `scanner`, `settings`, `designsystem`, `update`, `ui`
- **summary**: imperative, lower case, no trailing period, under ~72 characters

Examples from this repository:

```
feat(player): show what collection the queue is playing from
fix(scanner): use case-insensitive matching for artist and album names
perf(sort): compute each name's sort key once, not once per comparison
```

Add a body when the reason for the change is not obvious from the summary.

### Changelog trailers

Sona's release notes are written by its commits. A commit that changes something a user will notice says so in [git trailers](https://git-scm.com/docs/git-interpret-trailers): `Key: value` lines closing the message, as its last paragraph, one per change, under [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)'s headings:

```
fix(player): keep the queue in place when a track is removed

Removing a track re-sorted the queue from the top.

Fixes #123

Fixed: The queue no longer jumps to the top when a track is removed.
```

- **Keys**: `Added`, `Changed`, `Fixed`, `Removed`. Write the value for users, not for developers: what they will see, in a sentence.
- **Several changes**: one trailer each. A long one continues on the next line, indented.
- **Nothing a user would notice** - a refactor, a build change: no trailers. The commit is left out of the notes.
- **Last paragraph, trailers only**: git reads the trailers only if nothing else shares their paragraph. A line like `Fixes #123` among them hides them all, so the change is missing from the notes.
- **Issues**: `Fixes #123` (or `Closes`, `Resolves`) in the body, above the trailers. When a release ships the commit, the issue is told.

Every release's notes gather these trailers from all the commits since the release before, so check yours read well on their own. The commit hook checks each message as you commit, and CI checks every pull request's: the subject's format, changelog lines git would not read, and a trailer the changelog does not read at all.

## Pull requests

1. Push your branch and open a pull request **against `dev`**.
2. Fill in the pull request template: what changed, why, how you tested it, and screenshots or a recording for UI changes.
3. Keep the branch up to date with `dev`. Rebase rather than merging `dev` into your branch.
4. Respond to review comments by pushing new commits to the same branch.

A pull request is merged when it builds, does what it says, has been tested, and fits the guidelines above.

## Using code from other projects

Sona is licensed under **GPL-3.0**. Code you contribute is released under the same license.

You may bring in code from another project only if its license is compatible with GPL-3.0: for example GPL-3.0, Apache-2.0, MIT or BSD. Code under GPL-2.0-only, proprietary or unclear licenses cannot be used.

When you port code:

1. Keep the original copyright notice if the file had one.
2. Say where it came from in a comment, as existing ports do: `Ported from Auxio's ...`.
3. Add the project to the Credits table in the [README](README.md#credits) if it is not there yet.

## Releases

Releases are made by the maintainer, on GitHub Actions. Contributors never bump versions or edit `version.json` or `CHANGELOG.md`: the pipelines write them all, from the [changelog trailers](#changelog-trailers).

Every pull request, and every push to `dev`, is built by CI, and its debug APK is kept on the run's page to try.

| Release | Versions | Made by | Update channel |
| --- | --- | --- | --- |
| **Artifact** | `1.6.0-alpha.1`, `1.6.0-beta.2`, `1.6.0-rc.1` - early builds of the next version | a commit on `dev` marked `Release:` | Artifact |
| **Stable** | `1.6.0` | `./scripts/release.sh` | Stable and Artifact |

**Artifact.** The maintainer adds a `Release: <alpha|beta|rc> [major|minor|patch]` trailer to a commit, or pushes an empty one (`git commit --allow-empty -m "chore: release an artifact" -m "Release: beta"`). When it reaches `dev`, [`artifact.yml`](.github/workflows/artifact.yml) publishes a pre-release of that commit, its notes gathered from the commits since the last release. Only a marker on a commit the repository's owner authored counts; pull requests carrying one are turned away by CI.

Versions are worked out from the release tags ([`scripts/lib/version.sh`](scripts/lib/version.sh)): the bump, `patch` unless given, is from the last stable release; the same stage counts on (`beta.1`, `beta.2`), a later one starts again (`alpha.3`, then `beta.1`), and an earlier one is refused.

**Stable.** `./scripts/release.sh` runs [`release.yml`](.github/workflows/release.yml): it merges `dev` into `main`, and releases either the version the artifacts lead to (`release`) or a `patch`, `minor` or `major` bump. The notes open in an editor, already drafted from the trailers since the last stable release; `CHANGELOG.md` gets those trailers as its new section.

The maintainer's side of it all, every case included, is in [docs/RELEASING.md](docs/RELEASING.md).
