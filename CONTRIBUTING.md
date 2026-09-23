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
./gradlew :app:assembleDebug
```

You do not need a signing key. Debug builds use the default debug key when `keystore.properties` is absent.

## Branches

| Branch | Role |
| --- | --- |
| `dev` | Where work happens. **All pull requests target `dev`.** |
| `main` | What was last released. Only the release pipeline writes to it. |

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

- **type**: `feat`, `fix`, `perf`, `refactor`, `build`, `docs`, `chore`
- **scope**: the area changed, usually the module: `library`, `player`, `playback`, `scanner`, `settings`, `designsystem`, `update`, `ui`
- **summary**: imperative, lower case, no trailing period, under ~72 characters

Examples from this repository:

```
feat(player): show what collection the queue is playing from
fix(scanner): use case-insensitive matching for artist and album names
perf(sort): compute each name's sort key once, not once per comparison
```

Add a body when the reason for the change is not obvious from the summary.

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

Releases are made by the maintainer with `./scripts/release.sh`, which runs the release workflow on GitHub Actions. Contributors do not need to bump versions or edit `version.json`; both are written by the pipeline.

Before a release, the maintainer adds its entry to [CHANGELOG.md](CHANGELOG.md).
