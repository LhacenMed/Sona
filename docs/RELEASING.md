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
| **Release console** | [`release.sh`](../scripts/release.sh) | One command per kind of release - `stable`, `artifact`, `hotfix` - that works out the version and the notes, shows them, and on a yes dispatches the pipeline and follows it. |
| **Artifact release** | [`artifact.yml`](../.github/workflows/artifact.yml) | Dispatched by `release.sh artifact`, or on a push to `dev` whose commit the owner authored carries `Release: <stage> [bump]`: publishes a pre-release of `dev`'s head. A push without a marker stops in seconds. |
| **Stable release** | [`release.yml`](../.github/workflows/release.yml), dispatched by `release.sh stable` and `release.sh hotfix` | Merges `dev` - or a `hotfix/*` branch - into `main`, publishes a stable release, writes `CHANGELOG.md`, `version.json` and the version in `app/build.gradle.kts`, then brings `dev` up to date with it. |
| Build and upload | [`upload-release`](../.github/actions/upload-release/action.yml) | The steps both release workflows share: build and sign the APKs at a version, and upload them to a draft release. |
| Versions | [`version.sh`](../scripts/lib/version.sh) | Works out the next version from the release tags. |
| Notes | [`changelog.sh`](../scripts/lib/changelog.sh) | Gathers every commit's notes - its changelog trailers, or else its subject - and the issues they close. |

The two release workflows share one concurrency group, so they never run at once: each always sees the tags the other made.

## The flow

```
contributor ──PR──▶ dev ◀── maintainer's own commits
                     │
                     ├─ every push ─────────────▶ Checks: debug APK
                     │
                     ├─ ./scripts/release.sh artifact beta ─▶ Artifact release: v1.6.0-beta.1 (pre-release)
                     │
                     └─ ./scripts/release.sh ──────────────▶ Stable release: v1.6.0
                                                              main ← dev, CHANGELOG.md, version.json
```

1. **Contributions.** Contributors open pull requests against `dev`. Checks builds them and checks their commits; the build's APK can be downloaded from the run to try the change. Merge them with **Create a merge commit** or **Rebase and merge** - both keep each commit and its trailers. Squash merging is turned off: it joins the commits' messages into one, where git no longer finds the trailers.
2. **Your own work.** Commit to `dev` as usual. Each commit's subject is its line in the notes; add [changelog trailers](../CONTRIBUTING.md#changelog-trailers) where users deserve a better sentence than the subject.
3. **An artifact.** When `dev` is worth trying:
   ```bash
   ./scripts/release.sh artifact beta
   ```
   It shows the version and the notes; say yes, and a few minutes later the pre-release is on GitHub and apps on the Artifact channel offer it. The issues its commits fix are told it can be tried. A commit pushed with a `Release: beta` trailer does the same.
4. **Stable.** When the artifacts have been tried:
   ```bash
   ./scripts/release.sh
   ```
   It releases the version the artifacts lead to - or the next patch, with none out - with the notes of every commit since the last stable release. Say yes; the pipeline does the rest, and the issues are told the fix is out. `--edit` opens the notes to polish first, and `--bump minor` or `major` goes further.

`release.sh` needs `gh` (logged in) and `jq`, and nothing else - it builds nothing locally, and closing it does not stop the release. `-y` releases without asking; `--help` lists the rest.

## Hotfixes

A stable release from `dev` ships everything on `dev`. When a released version needs a fix now, and `dev` holds work not ready to ship, the fix is released on its own. Commit it to `dev` as usual, then:

```bash
./scripts/release.sh hotfix <commit>...
```

It makes `hotfix/<version>` from `main`, cherry-picks the commits onto it, shows the version and notes, and on a yes pushes the branch and releases it. A fix that does not apply to `main` as it is gets a branch of its own instead - `git switch -c hotfix/queue-crash origin/main`, fix it there, push it, and run `./scripts/release.sh hotfix` on it.

A hotfix is always the last stable release's patch - `1.6.0` becomes `1.6.1`, even while `dev`'s artifacts are at `1.7.0-beta.2`. Checks builds the branch as it is pushed. The pipeline then:

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

**Stable** releases take a bump - `release.sh --bump <kind>`, or by default `release` while artifacts are out and `patch` otherwise:

| Bump | From `1.5.0`, artifacts at `1.6.0-rc.2` | From `1.6.0`, no artifacts since |
| --- | --- | --- |
| `release` | `1.6.0` - the default when there are artifacts | refused |
| `patch` | refused - below `1.6.0-rc.2` | `1.6.1` |
| `minor` | `1.6.0` | `1.7.0` |
| `major` | `2.0.0` | `2.0.0` |
| `hotfix` - from a `hotfix/*` branch only | `1.5.1` | `1.6.1` |

Version codes follow from the version (see `Version.toVersionCode()` in `app/build.gradle.kts`): every release outranks every earlier one, artifacts included, and each ABI's APK adds 1 to 4 to it, the universal one nothing.

## Release notes

Notes are gathered from every commit since the release before, oldest first, under Keep a Changelog's headings. Each commit gives either:

- **its changelog trailers** - `Added`, `Changed`, `Fixed`, `Removed` - when it has them: sentences written for users, so they win;
- **or its subject**, under the heading its type stands for: `feat` is Added, `fix` is Fixed, `perf`, `refactor` and `revert` are Changed - `feat(player): add a sleep timer` reads "Add a sleep timer.". The types users never notice - `build`, `ci`, `docs`, `chore`, `test`, `style` - give nothing, and neither does a subject that is not a Conventional Commits one.

The range:

- **An artifact**: since the latest release of either kind. They are its GitHub release notes as they are.
- **A stable release or hotfix**: since the last stable one, so every artifact's changes together. They are its `CHANGELOG.md` section, and its release notes unless `--notes`, `--notes-file` or `--edit` gives others - those reach the app's update sheet and the GitHub release, while `CHANGELOG.md` keeps the commits' own.

## Every case

| Case | What to do |
| --- | --- |
| A pull request is ready | Wait for Checks, then merge with a merge commit or a rebase. |
| A contributor's commit carries `Release:` | Checks fails the pull request. Ask them to remove it; if it is merged anyway, the artifact workflow ignores it - only the owner's markers count. |
| A contributor's change has no trailers | Its subject is its line in the notes. For a better sentence, add one when merging, in a commit of your own: `git commit --allow-empty -m "docs: note #123's change" -m "Fixed: …"`. |
| Several markers in one push | The newest of yours decides, and the whole push is released as its last commit. |
| A marker on a commit already released | Nothing is released twice. |
| Try `dev` without releasing it | Download the debug APK from Checks' run on that commit. It installs as Sona Debug, next to the real app. |
| A stable release with nothing users notice | `./scripts/release.sh --bump patch`; the notes read "Bug fixes and improvements." unless you write them. |
| A released version needs a fix, and `dev` is not ready | `./scripts/release.sh hotfix <commit>...` - see [Hotfixes](#hotfixes). |
| A hotfix's pull request into `dev` is open | The hotfix changed lines `dev` changed too. Resolve the conflicts in it and merge it with a merge commit. |
| A commit's changelog lines are rejected | A non-trailer line - usually `Fixes #12` - shares their paragraph, so git would not read them. Move it above them. |

## When something goes wrong

- **A workflow fails before publishing** - a build error, a failed upload: nothing is public. Fix it and run the same `release.sh` command again. A draft left behind is removed by the next run.
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
