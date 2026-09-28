#!/usr/bin/env bash
#
# release.sh — Sona's release console: one command for each kind of release.
#
#   ./scripts/release.sh                              a stable release of dev
#   ./scripts/release.sh stable [--bump <kind>]       the same, a bump chosen
#   ./scripts/release.sh artifact <alpha|beta|rc>     a pre-release of dev
#   ./scripts/release.sh hotfix <commit>...           those commits alone, on
#                                                     what was last released
#
# Everything is worked out for you: the version from the release tags
# (scripts/lib/version.sh), and the notes from the commits since the release
# before (scripts/lib/changelog.sh) - their changelog lines where they wrote
# them, their subjects where they did not. You see both, and say yes.
#
# Nothing is built, signed or published here. Each release runs on GitHub
# Actions - stable and hotfix in release.yml, artifacts in artifact.yml - so
# once one is dispatched this script only follows it, and closing the terminal
# stops nothing. See docs/RELEASING.md.
#
# Requirements: gh (authenticated) and jq. No keystore, no Android SDK.

set -euo pipefail

usage() {
    cat <<'USAGE'
Usage:
  ./scripts/release.sh [stable] [options]              release dev as stable
  ./scripts/release.sh artifact <alpha|beta|rc> [...]  release dev as a pre-release
  ./scripts/release.sh hotfix [<commit>...] [options]  release a fix on its own

  --bump <kind>            stable: release (the default while artifacts are out),
                           patch (the default otherwise), minor or major.
                           artifact: patch (the default), minor or major - the
                           bump from the last stable release, where no artifact
                           leads further already
  --notes <text>           stable, hotfix: the notes, instead of the commits'
  --notes-file <path|->    stable, hotfix: the notes from a file, or - for stdin
  --edit                   stable, hotfix: edit the commits' notes before releasing
  -y, --yes                release without asking
  -h, --help               show this help

hotfix: given commits - from dev, usually - makes hotfix/<version> from main,
cherry-picks them onto it, pushes it and releases it. Given none, it releases
the hotfix/* branch you are on.

Markdown notes: --notes-file (or - with a quoted heredoc) is always safe; in a
shell, quote --notes with '...' (bash) or @'...'@ (PowerShell), never "...".
USAGE
}

# ── Parse the command and its options ──────────────────────────────────────────
COMMAND="stable"
case "${1:-}" in
    stable|artifact|hotfix) COMMAND="$1"; shift ;;
esac

STAGE="" COMMITS=() FLAG_BUMP="" FLAG_NOTES="" FLAG_NOTES_FILE="" FLAG_EDIT=0 FLAG_YES=0

# `set -u` turns a flag left without its value into "$2: unbound variable", which
# says nothing about which flag was left dangling. This names it.
need_value() {
    [[ $# -ge 2 ]] || { echo "✗ ${1} needs a value."; exit 1; }
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --bump)            need_value "$@"; FLAG_BUMP="${2,,}"; shift 2 ;;
        --bump=*)          FLAG_BUMP="${1#*=}"; FLAG_BUMP="${FLAG_BUMP,,}"; shift ;;
        --notes)           need_value "$@"; FLAG_NOTES="$2"; shift 2 ;;
        --notes=*)         FLAG_NOTES="${1#*=}"; shift ;;
        --notes-file)      need_value "$@"; FLAG_NOTES_FILE="$2"; shift 2 ;;
        --notes-file=*)    FLAG_NOTES_FILE="${1#*=}"; shift ;;
        --edit)            FLAG_EDIT=1; shift ;;
        -y|--yes)          FLAG_YES=1; shift ;;
        -h|--help)         usage; exit 0 ;;
        -*)                echo "✗ Unknown option: $1"; usage; exit 1 ;;
        *)
            case "$COMMAND" in
                artifact) [[ -z "$STAGE" ]] || { echo "✗ One stage only: ${STAGE} or $1."; exit 1; }
                          STAGE="${1,,}" ;;
                hotfix)   COMMITS+=("$1") ;;
                *)        echo "✗ Unexpected argument: $1"; usage; exit 1 ;;
            esac
            shift ;;
    esac
done

if [[ "$COMMAND" == "artifact" ]]; then
    [[ -n "$STAGE" ]] || { echo "✗ Which stage? ./scripts/release.sh artifact <alpha|beta|rc>"; exit 1; }
    case "$STAGE" in alpha|beta|rc) ;; *) echo "✗ The stage is alpha, beta or rc - not ${STAGE}."; exit 1 ;; esac
    case "${FLAG_BUMP:-patch}" in patch|minor|major) ;; *) echo "✗ An artifact's --bump is patch, minor or major."; exit 1 ;; esac
    [[ -z "$FLAG_NOTES$FLAG_NOTES_FILE" && "$FLAG_EDIT" -eq 0 ]] \
        || { echo "✗ An artifact's notes are always its commits'."; exit 1; }
fi
if [[ "$COMMAND" == "hotfix" && -n "$FLAG_BUMP" ]]; then
    echo "✗ A hotfix takes no --bump: it is always the last stable release's next patch."; exit 1
fi
if (( $(( ${#FLAG_NOTES} > 0 )) + $(( ${#FLAG_NOTES_FILE} > 0 )) + FLAG_EDIT > 1 )); then
    echo "✗ Pass one of --notes, --notes-file and --edit."; exit 1
fi

# Prompts read the terminal itself rather than stdin, because stdin may be carrying
# the notes (--notes-file -) and would otherwise be at end-of-file by the time the
# question is asked. Only when the session looks interactive, though: somewhere with
# no terminal behind it (CI, a pipe) this falls back to stdin, so an unattended run
# ends at end-of-file rather than waiting for a person forever.
ask() {
    local __dest="$1" __prompt="$2"
    if [[ -t 1 && -r /dev/tty ]]; then read -rp "$__prompt" "$__dest" < /dev/tty
    else                               read -rp "$__prompt" "$__dest"
    fi
}

# confirm <question> - true for yes; Enter alone is yes. --yes answers it.
confirm() {
    (( FLAG_YES )) && { echo "  Proceeding (--yes)."; return 0; }
    local answer
    ask answer "  $1 [Y/n]: "
    [[ -z "$answer" || "${answer,,}" == "y" || "${answer,,}" == "yes" ]]
}

# ── Bootstrap ───────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

source "${SCRIPT_DIR}/lib/version.sh"
source "${SCRIPT_DIR}/lib/changelog.sh"
source "${SCRIPT_DIR}/lib/git.sh"

for cmd in gh jq; do
    command -v "$cmd" >/dev/null || { echo "✗ Required tool not found: ${cmd}"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "✗ gh not authenticated — run: gh auth login"; exit 1; }

# The pipelines build from origin, so local-only work would silently be left out.
git::ensure_clean
git fetch origin --tags --quiet

LATEST="$(version::latest)"
LAST_STABLE="$(version::latest stable)"
PENDING_ARTIFACT=0
if [[ -n "$LATEST" ]]; then
    version::parse "$LATEST"
    [[ -n "$V_STAGE" ]] && PENDING_ARTIFACT=1
fi

# ── Notes ───────────────────────────────────────────────────────────────────────
# A file, or "-" for stdin — the one way of passing markdown that no shell can
# reinterpret on the way in.
read_notes_file() {
    local raw
    if [[ "$1" == "-" ]]; then
        raw="$(cat)"
    else
        [[ -f "$1" ]] || { echo "✗ --notes-file not found: ${1}" >&2; exit 1; }
        raw="$(cat "$1")"
    fi
    # A leading byte-order mark is dropped. PowerShell writes one when it pipes, and
    # Windows editors write one when they save; it shows as nothing in the terminal,
    # but it would sit in front of the first "##" and stop it being a heading.
    printf '%s' "${raw#$'﻿'}"
}

# edit_notes <drafted> <tag> - opens the drafted notes in the editor, and prints
# what is left above the cut line once it is saved.
edit_notes() {
    local drafted="$1" tag="$2" cut="[cut]" dir file
    dir="$(mktemp -d)"
    file="${dir}/RELEASE_NOTES.md"
    cat > "$file" <<TEMPLATE
${drafted}

${cut}
Release notes for ${tag}.

Above the ${cut} line are the notes the commits gave. Edit them into the notes
the app's update sheet and the GitHub release page show; CHANGELOG.md keeps
them as the commits gave them. Markdown is supported: ## headings, - bullets
and **bold** all render.

Everything from ${cut} down is ignored, and saving with no notes aborts the
release.
TEMPLATE
    eval "$(git var GIT_EDITOR) \"\$file\"" < /dev/tty > /dev/tty
    # The help text lives below the cut rather than behind a comment prefix,
    # because that prefix would be "#", and "#" is a markdown heading. Blank lines
    # are held back until content follows them, so none pad either end.
    awk -v cut="$cut" '
        { line = $0; gsub(/^[ 	]+|[ 	]+$/, "", line) }
        line == cut { exit }
        line == "" { if (started) pending++; next }
        { while (pending-- > 0) print ""; pending = 0; started = 1; print }
    ' "$file"
    rm -rf "$dir"
}

# resolve_notes <drafted> <tag> - prints the notes to release with: the ones
# given, the drafted ones edited, or the drafted ones as they are - which, left
# empty, are the pipeline's to fill.
resolve_notes() {
    local drafted="$1" tag="$2"
    if [[ -n "$FLAG_NOTES_FILE" ]]; then
        read_notes_file "$FLAG_NOTES_FILE"
    elif [[ -n "$FLAG_NOTES" ]]; then
        printf '%s' "$FLAG_NOTES"
    elif (( FLAG_EDIT )); then
        local edited
        edited="$(edit_notes "$drafted" "$tag")"
        [[ -n "$edited" ]] || { echo "  Aborted — no release notes written." >&2; exit 1; }
        printf '%s' "$edited"
    fi
}

# preview <from> <to> <tag> <detail> <notes>
preview() {
    echo ""
    echo "  ┌── $3 ─────────────────────────────────────────"
    echo "  │  ${1:-none}  →  $2"
    [[ -n "$4" ]] && echo "  │  $4"
    echo "  ├── Notes ─────────────────────────────────────"
    sed 's/^/  │  /' <<< "${5:-Bug fixes and improvements.}"
    echo "  └──────────────────────────────────────────────"
    echo ""
}

# ── Stable: dev, merged into main ───────────────────────────────────────────────
release_stable() {
    git::ensure_pushed dev
    local bump="${FLAG_BUMP:-$( ((PENDING_ARTIFACT)) && echo release || echo patch)}"
    case "$bump" in
        release|patch|minor|major) ;;
        *) echo "✗ A stable release's --bump is release, patch, minor or major."; exit 1 ;;
    esac
    local name tag drafted notes
    name="$(version::next_stable "$bump")"
    tag="v${name}"
    git::release_published "$tag" && { echo "✗ ${tag} is already published."; exit 1; }
    drafted="$(changelog::since "${LAST_STABLE:+v${LAST_STABLE}}" origin/dev)"
    notes="$(resolve_notes "$drafted" "$tag")"

    preview "$LAST_STABLE" "$name" "Stable release ${tag}" "dev → main  (${bump})" "${notes:-$drafted}"
    confirm "Release ${tag}?" || { echo "  Aborted."; exit 0; }

    local inputs
    inputs="$(jq -n --arg bump "$bump" --arg notes "$notes" \
        '{bump: $bump, notes: $notes, source_branch: "dev"}')"
    echo "▶ Releasing ${tag}…"
    git::run_workflow release.yml dev "$inputs"
}

# ── Artifact: dev as it stands, pre-released ────────────────────────────────────
release_artifact() {
    git::ensure_pushed dev
    local name tag drafted
    name="$(version::next_artifact "$STAGE" "${FLAG_BUMP:-patch}")"
    tag="v${name}"
    [[ -z "$(git tag --points-at origin/dev --list 'v*')" ]] \
        || { echo "✗ dev's head is released already."; exit 1; }
    drafted="$(changelog::since "${LATEST:+v${LATEST}}" origin/dev)"

    preview "$LATEST" "$name" "Artifact release ${tag}" "dev, pre-released" "$drafted"
    confirm "Release ${tag}?" || { echo "  Aborted."; exit 0; }

    local inputs
    inputs="$(jq -n --arg stage "$STAGE" --arg bump "${FLAG_BUMP:-patch}" '{stage: $stage, bump: $bump}')"
    echo "▶ Releasing ${tag}…"
    git::run_workflow artifact.yml dev "$inputs"
}

# ── Hotfix: a fix released on its own, beside dev ──────────────────────────────
release_hotfix() {
    local name tag branch origin_branch="" drafted notes
    name="$(version::next_stable hotfix)"
    tag="v${name}"
    git::release_published "$tag" && { echo "✗ ${tag} is already published."; exit 1; }

    if (( ${#COMMITS[@]} )); then
        # Made here from what was last released, the commits cherry-picked onto it,
        # and pushed only once the release is confirmed - an abort leaves nothing.
        origin_branch="$(git::current_branch)"
        branch="hotfix/${name}"
        git rev-parse --verify --quiet "refs/heads/${branch}" >/dev/null \
            && { echo "✗ ${branch} exists already - release it from there, or delete it."; exit 1; }
        git switch --quiet -c "$branch" "origin/main"
        if ! git cherry-pick -x "${COMMITS[@]}"; then
            git cherry-pick --abort 2>/dev/null || true
            git switch --quiet "$origin_branch"
            git branch --quiet -D "$branch"
            echo "✗ The commits do not apply to what was last released - make ${branch} from main and fix it there."
            exit 1
        fi
        drafted="$(changelog::since "${LAST_STABLE:+v${LAST_STABLE}}" HEAD)"
    else
        branch="$(git::current_branch)"
        [[ "$branch" == hotfix/* ]] \
            || { echo "✗ Name the commits to release - ./scripts/release.sh hotfix <commit>... - or run this on a hotfix/* branch."; exit 1; }
        git::ensure_pushed "$branch"
        drafted="$(changelog::since "${LAST_STABLE:+v${LAST_STABLE}}" "origin/${branch}")"
    fi

    # Leaves the branch made here, going back to where the command was run - its
    # commits, if pushed, are on origin, and the pipeline deletes it there.
    leave_made_branch() {
        [[ -n "$origin_branch" ]] || return 0
        git switch --quiet "$origin_branch"
        git branch --quiet -D "$branch"
    }

    notes="$(resolve_notes "$drafted" "$tag")"
    preview "$LAST_STABLE" "$name" "Hotfix ${tag}" "${branch} → main, then dev" "${notes:-$drafted}"
    confirm "Release ${tag}?" || { leave_made_branch; echo "  Aborted."; exit 0; }

    [[ -z "$origin_branch" ]] || git push --quiet -u origin "$branch"
    leave_made_branch

    local inputs
    inputs="$(jq -n --arg notes "$notes" --arg branch "$branch" \
        '{bump: "hotfix", notes: $notes, source_branch: $branch}')"
    echo "▶ Releasing ${tag}…"
    git::run_workflow release.yml "$branch" "$inputs"
}

echo ""
echo "  Last stable     : ${LAST_STABLE:-none}"
echo "  Latest artifact : $( ((PENDING_ARTIFACT)) && echo "$LATEST" || echo none)"

case "$COMMAND" in
    stable)   release_stable ;;
    artifact) release_artifact ;;
    hotfix)   release_hotfix ;;
esac
