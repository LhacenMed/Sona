# Releasing Sona

How a change travels from a commit to a user's phone, and what the maintainer does at each step. Contributors need only [CONTRIBUTING.md](../CONTRIBUTING.md); this page is for whoever makes releases.

## Contents

- [The pieces](#the-pieces)
- [The flow](#the-flow)
- [Hotfixes](#hotfixes)
- [Versions](#versions)
- [Release notes](#release-notes)
- [Every case](#every-case)
- [When something goes wrong](#when-something-goes-wrong)
- [Repository setup](#repository-setup)

## The pieces

| Piece | Where | What it does |
| --- | --- | --- |
| **Checks** | [`ci.yml`](../.github/workflows/ci.yml) | On every pull request to `dev`: checks the commit messages ([`check-commits.sh`](../scripts/check-commits.sh)). On every pull request, and every push to `dev` or a `hotfix/*` branch: builds a debug APK and keeps it on the run's page for 14 days. |
| Commit hook | [`hooks/commit-msg`](../scripts/hooks/commit-msg) | The same message check, run as each commit is made - `git config core.hooksPath scripts/hooks` once per clone. Your own `Release:` markers pass it. |
| **Artifact release** | [`artifact.yml`](../.github/workflows/artifact.yml) | On every push to `dev`: if a commit the owner authored carries `Release: <stage> [bump]`, publishes a pre-release of that commit. Otherwise stops in seconds. |
| **Stable release** | [`release.yml`](../.github/workflows/release.yml), started by [`release.sh`](../scripts/release.sh) | Merges `dev` - or a `hotfix/*` branch - into `main`, publishes a stable release, writes `CHANGELOG.md`, `version.json` and the version in `app/build.gradle.kts`, then brings `dev` up to date with it. |
| Build and upload | [`upload-release`](../.github/actions/upload-release/action.yml) | The steps both release workflows share: build and sign the APKs at a version, and upload them to a draft release. |
| Versions | [`version.sh`](../scripts/lib/version.sh) | Works out the next version from the release tags. |
| Notes | [`changelog.sh`](../scripts/lib/changelog.sh) | Gathers the commits' changelog trailers, and the issues they close. |

The two release workflows share one concurrency group, so they never run at once: each always sees the tags the other made.

## The flow

```
contributor ──PR──▶ dev ◀── maintainer's own commits
                     │
                     ├─ every push ─────────────▶ Checks: debug APK
                     │
                     ├─ push with "Release: beta" ─▶ Artifact release: v1.6.0-beta.1 (pre-release)
                     │
                     └─ ./scripts/release.sh ────▶ Stable release: v1.6.0
                                                    main ← dev, CHANGELOG.md, version.json
```

1. **Contributions.** Contributors open pull requests against `dev`. Checks builds them and checks their commits; the build's APK can be downloaded from the run to try the change. Merge them with **Create a merge commit** or **Rebase and merge** - both keep each commit and its trailers. Squash merging is turned off: it joins the commits' messages into one, where git no longer finds the trailers.
2. **Your own work.** Commit to `dev` as usual, with [changelog trailers](../CONTRIBUTING.md#changelog-trailers) on what users will notice.
3. **An artifact.** When `dev` is worth trying, make its newest commit carry the marker, or push an empty one:
   ```bash
   git commit --allow-empty -m "chore: release an artifact" -m "Release: beta"
   git push
   ```
   A few minutes later the pre-release is on GitHub, and apps on the Artifact channel offer it. The issues its commits fix are told it can be tried.
4. **Stable.** When the artifacts have been tried, from any branch:
   ```bash
   ./scripts/release.sh
   ```
   Choose `release`, and edit the notes: they open drafted from every trailer since the last stable release. The pipeline does the rest, and the issues are told the fix is out.

`release.sh` needs `gh` (logged in) and `jq`, and nothing else - it builds nothing locally, and closing it does not stop the release.

## Hotfixes

A stable release from `dev` ships everything on `dev`. When a released version needs a fix now, and `dev` holds work not ready to ship, the fix is released on its own from a `hotfix/*` branch made from `main`:

```bash
git switch -c hotfix/queue-crash origin/main
# fix it, and commit with its "Fixed:" trailer - or cherry-pick a fix already on dev
git push -u origin hotfix/queue-crash
./scripts/release.sh
```

On a `hotfix/*` branch `release.sh` asks nothing about the version: it is always a `hotfix`, the last stable release's patch - `1.6.0` becomes `1.6.1`, even while `dev`'s artifacts are at `1.7.0-beta.2`. Checks builds the branch as it is pushed, to try the fix first. The pipeline then:

1. checks the branch is made from the latest `main`, and holds none of `dev`'s unreleased commits - a fix cherry-picked from `dev` is fine, `dev` merged in is not;
2. merges it into `main` and publishes the release, its notes gathered from the branch's commits alone;
3. merges the release into `dev`, so `dev` has the fix too - or, where the fix and `dev` changed the same lines, opens a pull request from `main` into `dev` to resolve them in;
4. deletes the branch.

Stable users are offered `1.6.1`; Artifact users keep their newer `1.7.0-beta.2`. The next artifact carries on at `1.7.0-beta.3`, and the next stable release's notes start after `1.6.1`.

## Versions

Every version is worked out from the `v*` release tags - nothing is stored in a file for the pipelines to race over. Builds are handed the result as `-Psona.version=<version>`; a local build is versioned as the last stable release, written into `app/build.gradle.kts` by each stable release.

**Artifacts** are `x.y.z-<stage>.<n>`, stage one of `alpha` < `beta` < `rc`:

| Latest release | Marker | Artifact |
| --- | --- | --- |
| `1.5.0` | `Release: beta` | `1.5.1-beta.1` - patch unless a bump is given |
| `1.5.0` | `Release: alpha minor` | `1.6.0-alpha.1` |
| `1.6.0-alpha.1` | `Release: alpha` | `1.6.0-alpha.2` - the same stage counts on |
| `1.6.0-alpha.2` | `Release: beta` | `1.6.0-beta.1` - a later stage starts again |
| `1.6.0-beta.1` | `Release: alpha` | refused - it would be a lower version than one already out |
| `1.6.0-beta.1` | `Release: beta major` | `2.0.0-beta.1` - bumps count from the last stable release, `1.5.0` |

**Stable** releases take a bump in `release.sh`:

| Bump | From `1.5.0`, artifacts at `1.6.0-rc.2` | From `1.6.0`, no artifacts since |
| --- | --- | --- |
| `release` | `1.6.0` - the default when there are artifacts | refused |
| `patch` | refused - below `1.6.0-rc.2` | `1.6.1` |
| `minor` | `1.6.0` | `1.7.0` |
| `major` | `2.0.0` | `2.0.0` |
| `hotfix` - from a `hotfix/*` branch only | `1.5.1` | `1.6.1` |

Version codes follow from the version (see `Version.toVersionCode()` in `app/build.gradle.kts`): every release outranks every earlier one, artifacts included, and each ABI's APK adds 1 to 4 to it, the universal one nothing.

## Release notes

Notes are gathered from the `Added`, `Changed`, `Fixed` and `Removed` trailers of the commits since the release before, oldest first, under those headings:

- **An artifact**: since the latest release of either kind. They are its GitHub release notes as they are.
- **A stable release**: since the last stable one, so every artifact's changes together. They become its `CHANGELOG.md` section as they are, and `release.sh` opens them for you to edit into the notes the app's update sheet and the GitHub release show. Passed with `--yes` and no notes, they are used as they are.

A commit without trailers adds nothing. Commits made before trailers existed can be covered by a later commit's trailers, as `441c3c6` covered 1.6.0's.

## Every case

| Case | What to do |
| --- | --- |
| A pull request is ready | Wait for Checks, then merge with a merge commit or a rebase. |
| A contributor's commit carries `Release:` | Checks fails the pull request. Ask them to remove it; if it is merged anyway, the artifact workflow ignores it - only the owner's markers count. |
| A contributor's change has no trailers | Add them when merging, in a commit of your own: `git commit --allow-empty -m "docs: note #123's change" -m "Fixed: …"`. |
| Several markers in one push | The newest of yours decides, and the whole push is released as its last commit. |
| A marker on a commit already released | Nothing is released twice. |
| Try `dev` without releasing it | Download the debug APK from Checks' run on that commit. It installs as Sona Debug, next to the real app. |
| A stable release with nothing new | Choose a `patch` bump; the notes read "Bug fixes and improvements." unless you write them. |
| A released version needs a fix, and `dev` is not ready | Release it from a `hotfix/*` branch - see [Hotfixes](#hotfixes). |
| A hotfix's pull request into `dev` is open | The hotfix changed lines `dev` changed too. Resolve the conflicts in it and merge it with a merge commit. |
| A commit's changelog lines are rejected | A non-trailer line - usually `Fixes #12` - shares their paragraph, so git would not read them. Move it above them. |

## When something goes wrong

- **A workflow fails before publishing** - a build error, a failed upload: nothing is public. Fix it and push again (artifact), or run `release.sh` again (stable). A draft left behind is removed by the next run.
- **Bringing `dev` up to date fails** after a stable release: the release is out and `main` is right. Bring `dev` level with `git switch dev && git merge origin/main && git push`.
- **Stable fails after pushing `main`**, before publishing: the release commit and the uploaded draft are both there, so finish the last two steps by hand rather than running it again, which would find nothing left to commit:
  ```bash
  gh release edit v1.6.0 --draft=false --prerelease=false   # tags main's release commit
  git fetch origin && git switch dev && git merge origin/main && git push   # brings dev up to date
  ```
- **A merge conflict** from `dev` into `main` stops the stable release. Resolve it locally on `main`, push, and run `release.sh` again.
- **A release is wrong once published**: publish a newer one. Deleting a published release and its tag makes its version free again for the pipelines, but apps that installed it keep it.

## Repository setup

What the pipelines rely on, set once:

- **Secrets**: `KEYSTORE_BASE64` (the release keystore, base64-encoded), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. The same key must sign every release, or apps cannot update from one to the next.
- **Merging**: squash merging off; merge commits and rebase on.
- **Ruleset "Protect main and dev"**: neither branch can be deleted or force-pushed. Checks are not required at the branch level: GitHub would then refuse the stable release's own push to `dev`, which carries no checks. Look at a pull request's checks before merging it.
- **Actions**: allowed, with read and write permissions granted per workflow in the files themselves.
