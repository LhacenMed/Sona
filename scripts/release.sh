#!/usr/bin/env bash
#
# release.sh — Stable release console for Sona.
#
# Picks the next stable version interactively, then hands the actual work to
# .github/workflows/release.yml. Nothing is built, signed or pushed locally:
# once the run is dispatched this script is a viewer, and closing the terminal
# (or losing power) has no effect on the release.
#
# Artifact releases are not made here: a commit marked `Release: <stage>`,
# pushed to dev, makes one - see .github/workflows/artifact.yml.
#
# Run from dev to release what dev holds, or from a hotfix/* branch made from
# main to release that fix alone - see docs/RELEASING.md. On ANY device.
#   1. Work out the current versions from origin's release tags
#   2. Prompt for the bump and the notes - drafted from the commits' changelog
#   3. Preview + confirm, then dispatch the cloud pipeline
#   4. Stream the run (Ctrl-C is safe — it detaches, it does not cancel)
#
# Usage:
#   ./scripts/release.sh
#   ./scripts/release.sh --bump release --yes
#   ./scripts/release.sh --bump minor --notes 'Fixed the thing.' --yes
#
# Any flag left unset falls back to its interactive prompt. Passing --yes skips
# the confirmation prompt - and, with no notes given, the notes are the ones
# gathered from the commits.
#
# Release notes are markdown, so they carry characters the shell wants for
# itself — ` runs a command, $ expands a variable, # starts a comment. The
# script receives whatever the shell hands it, so how the notes are quoted at
# the prompt decides whether they arrive intact:
#
#   bash / zsh          '...'        single quotes, literal, newlines included
#   PowerShell          @'...'@      single-quoted here-string
#   any shell, always   --notes-file <path>  or  --notes-file -  (stdin)
#
# Double quotes are NOT safe in either shell: bash runs `backticks` and eats
# $words, PowerShell treats ` as its escape character. Use --notes-file, or
# feed the notes on stdin with a quoted heredoc, when in doubt:
#
#   ./scripts/release.sh --bump minor --yes --notes-file - <<'EOF'
#   ## What's new
#   - Fixed the `player` crash
#   EOF
#
# Requirements: gh (authenticated) and jq. No keystore, no Android SDK.

set -euo pipefail

usage() {
    cat <<'USAGE'
Usage: ./scripts/release.sh [options]

  --bump <release|patch|minor|major>  Version bump - release makes the latest
                                      artifacts' version stable. A hotfix/*
                                      branch is always released as a hotfix
  --notes <text>                      Release notes
  --notes-file <path|->               Notes from a file, or - for stdin
  -y, --yes                           Skip the confirmation prompt
  -h, --help                          Show this help

Any option left unset falls back to its interactive prompt.

Quoting markdown notes: use '...' in bash/zsh and @'...'@ in PowerShell.
Double quotes let the shell run `backticks` and expand $words before the
script ever sees them. --notes-file (or - for stdin) is always safe.
USAGE
}

# ── Parse flags ─────────────────────────────────────────────────────────────────
FLAG_BUMP="" FLAG_NOTES="" FLAG_NOTES_FILE="" FLAG_YES=0

# `set -u` turns a flag left without its value into "$2: unbound variable", which
# says nothing about which flag was left dangling. This names it.
need_value() {
    [[ $# -ge 2 ]] || { echo "✗ ${1} needs a value."; exit 1; }
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --bump)            need_value "$@"; FLAG_BUMP="$2"; shift 2 ;;
        --bump=*)          FLAG_BUMP="${1#*=}"; shift ;;
        --notes)           need_value "$@"; FLAG_NOTES="$2"; shift 2 ;;
        --notes=*)         FLAG_NOTES="${1#*=}"; shift ;;
        --notes-file)      need_value "$@"; FLAG_NOTES_FILE="$2"; shift 2 ;;
        --notes-file=*)    FLAG_NOTES_FILE="${1#*=}"; shift ;;
        -y|--yes)          FLAG_YES=1; shift ;;
        -h|--help)         usage; exit 0 ;;
        *) echo "✗ Unknown option: $1"; usage; exit 1 ;;
    esac
done

if [[ -n "$FLAG_NOTES" && -n "$FLAG_NOTES_FILE" ]]; then
    echo "✗ Pass either --notes or --notes-file, not both."; exit 1
fi

# Prompts read the terminal itself rather than stdin, because stdin may be carrying
# the notes (--notes-file -) and would otherwise be at end-of-file by the time the
# first question is asked — every prompt would take the empty answer and the run
# would abort without saying why. Only when the session looks interactive, though:
# somewhere with no terminal behind it (CI, a pipe) this falls back to stdin, so an
# unattended run still ends at end-of-file rather than waiting for a person forever.
ask() {
    local __dest="$1" __prompt="$2"
    if [[ -t 1 && -r /dev/tty ]]; then read -rp "$__prompt" "$__dest" < /dev/tty
    else                               read -rp "$__prompt" "$__dest"
    fi
}

# ── Bootstrap ───────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

source "${SCRIPT_DIR}/lib/version.sh"
source "${SCRIPT_DIR}/lib/changelog.sh"
source "${SCRIPT_DIR}/lib/git.sh"

MAIN_BRANCH="main"
WORKFLOW="release.yml"

# ── Preconditions ───────────────────────────────────────────────────────────────
for cmd in gh jq; do
    command -v "$cmd" >/dev/null || { echo "✗ Required tool not found: ${cmd}"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "✗ gh not authenticated — run: gh auth login"; exit 1; }

REPO="$(git::repo_slug)"
SOURCE_BRANCH="$(git::current_branch)"
case "$SOURCE_BRANCH" in
    dev) ;;
    hotfix/*)
        [[ -z "$FLAG_BUMP" || "${FLAG_BUMP,,}" == "hotfix" ]] \
            || { echo "✗ ${SOURCE_BRANCH} is a hotfix - it takes no --bump."; exit 1; }
        FLAG_BUMP="hotfix" ;;
    *) echo "✗ Release from dev, or from a hotfix/* branch made from main - not ${SOURCE_BRANCH}."; exit 1 ;;
esac

# The pipeline builds from origin, so local-only work would silently be left out.
git::ensure_clean
git::ensure_pushed "$SOURCE_BRANCH"

# ── Step 1: Current versions, from origin's release tags ────────────────────────
git fetch origin --tags --quiet
NOTES_DIR="$(mktemp -d)"
NOTES_FILE="${NOTES_DIR}/RELEASE_NOTES.md"
trap 'rm -rf "$NOTES_DIR"' EXIT

LATEST="$(version::latest)"
LAST_STABLE="$(version::latest stable)"
PENDING_ARTIFACT=0
if [[ -n "$LATEST" ]]; then
    version::parse "$LATEST"
    [[ -n "$V_STAGE" ]] && PENDING_ARTIFACT=1
fi

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│         Sona Stable Release Pipeline         │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Last stable     : ${LAST_STABLE:-none}"
echo "  Latest artifact : $( ((PENDING_ARTIFACT)) && echo "$LATEST" || echo none)"
echo "  Source branch   : ${SOURCE_BRANCH}"
echo "  Target branch   : ${MAIN_BRANCH}"
echo "  Runs on         : GitHub Actions"
echo ""

# ── Step 2: Choose the bump ─────────────────────────────────────────────────────
if [[ -n "$FLAG_BUMP" ]]; then
    BUMP_KIND="${FLAG_BUMP,,}"
    case "$BUMP_KIND" in
        release|patch|minor|major|hotfix) ;;
        *) echo "✗ --bump must be one of: release, patch, minor, major"; exit 1 ;;
    esac
else
    echo "  Version bump:"
    if ((PENDING_ARTIFACT)); then
        echo "    1) release — make ${LATEST%%-*} stable"
    fi
    echo "    2) patch   — bug fixes          (x.y.Z)"
    echo "    3) minor   — new features       (x.Y.0)"
    echo "    4) major   — breaking changes   (X.0.0)"
    echo ""
    DEFAULT_CHOICE=2; ((PENDING_ARTIFACT)) && DEFAULT_CHOICE=1
    ask BUMP_CHOICE "  Select [default ${DEFAULT_CHOICE}]: "
    case "${BUMP_CHOICE:-$DEFAULT_CHOICE}" in
        1) ((PENDING_ARTIFACT)) || { echo "✗ There is no artifact to release"; exit 1; }
           BUMP_KIND="release" ;;
        2) BUMP_KIND="patch" ;;
        3) BUMP_KIND="minor" ;;
        4) BUMP_KIND="major" ;;
        *) echo "✗ Invalid choice"; exit 1 ;;
    esac
fi

NEW_NAME="$(version::next_stable "$BUMP_KIND")"
TAG="v${NEW_NAME}"

# ── Step 3: Release notes ────────────────────────────────────────────────────────
# A file, or "-" for stdin — the one way of passing markdown that no shell can
# reinterpret on the way in.
read_notes_file() {
    local raw
    if [[ "$1" == "-" ]]; then
        raw="$(cat)"
    else
        [[ -f "$1" ]] || { echo "✗ ${2} not found: ${1}" >&2; exit 1; }
        raw="$(cat "$1")"
    fi
    # A leading byte-order mark is dropped. PowerShell writes one when it pipes, and
    # Windows editors write one when they save; it shows as nothing in the terminal,
    # but it would sit in front of the first "##" and stop it being a heading.
    printf '%s' "${raw#$'﻿'}"
}

# What the commits since the last stable release say, as the pipeline gathers it.
CHANGES="$(changelog::since "${LAST_STABLE:+v${LAST_STABLE}}" "origin/${SOURCE_BRANCH}")"

if [[ -n "$FLAG_NOTES_FILE" ]]; then
    NOTES="$(read_notes_file "$FLAG_NOTES_FILE" --notes-file)"
elif [[ -n "$FLAG_NOTES" ]]; then
    NOTES="$FLAG_NOTES"
elif [[ "$FLAG_YES" -eq 1 ]]; then
    # Left to the pipeline, which gathers the same notes.
    NOTES=""
else
    CUT_MARKER="[cut]"

    # Everything above the cut. The help text lives below that cut rather than
    # behind a comment prefix, because the comment prefix would have to be "#" and
    # "#" is a markdown heading.
    notes_section() {
        awk -v cut="$CUT_MARKER" '
            { line = $0; gsub(/^[ 	]+|[ 	]+$/, "", line) }
            line == cut { exit }
            # Blank lines are held back until real content follows them, so the
            # padding the template leaves at either end never reaches the notes.
            line == "" { if (started) pending++; next }
            { while (pending-- > 0) print ""; pending = 0; started = 1; print }
        ' "$NOTES_FILE"
    }

    cat > "$NOTES_FILE" <<TEMPLATE
${CHANGES}

${CUT_MARKER}
Release notes for ${TAG}.

Above the ${CUT_MARKER} line is the changelog the commits since ${LAST_STABLE:-the first
commit} gave - their Added, Changed, Fixed and Removed trailers. Edit it into
the notes the app's update sheet and the GitHub release page show; CHANGELOG.md
keeps the changelog as the commits gave it. Markdown is supported: ## headings,
- bullets and **bold** all render.

Everything from ${CUT_MARKER} down is ignored, and saving with no notes aborts
the release.
TEMPLATE

    eval "$(git var GIT_EDITOR) \"\$NOTES_FILE\""

    NOTES="$(notes_section)"
    [[ -n "$NOTES" ]] || { echo "  Aborted — no release notes written."; exit 0; }
fi

# ── Step 4: Preview + confirm ────────────────────────────────────────────────────
echo ""
echo "  ┌── Release Preview ────────────────────────────┐"
echo "  │  ${LAST_STABLE:-none}  →  ${NEW_NAME}"
echo "  │  Tag    : ${TAG}$( [[ "$BUMP_KIND" == hotfix ]] && echo "  (hotfix from ${SOURCE_BRANCH})")"
echo "  │  Repo   : ${REPO}"
echo "  ├── Notes ─────────────────────────────────────┤"
sed 's/^/  │  /' <<< "${NOTES:-${CHANGES:-(Bug fixes and improvements.)}}"
echo "  └───────────────────────────────────────────────┘"
echo ""

if git::release_published "$TAG"; then
    echo "✗ Release ${TAG} is already published — bump to a different version."; exit 1
fi

if [[ "$FLAG_YES" -eq 1 ]]; then
    echo "  Proceeding (--yes)."
else
    ask CONFIRM "  Proceed? [y/N]: "
    [[ "${CONFIRM,,}" == "y" ]] || { echo "  Aborted."; exit 0; }
fi

# ── Step 5: Dispatch the cloud pipeline ──────────────────────────────────────────
# Remember the newest run id first, so we can identify the one we just created —
# `gh workflow run` does not return it.
echo ""
echo "▶ Dispatching ${WORKFLOW}…"
PREV_RUN="$(gh run list --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"

# --ref picks which branch's *workflow definition* runs, not which code is built:
# the pipeline always checks out main and merges the source branch itself. Using
# the source branch keeps "the workflow I can see on my branch is the workflow
# that runs" true — with --ref main, pipeline edits made on dev could never take
# effect until a release had already shipped them.
jq -n \
    --arg bump          "$BUMP_KIND" \
    --arg notes         "$NOTES" \
    --arg source_branch "$SOURCE_BRANCH" \
    '{bump: $bump, notes: $notes, source_branch: $source_branch}' \
  | gh workflow run "$WORKFLOW" --ref "$SOURCE_BRANCH" --json

RUN_ID=""
for _ in $(seq 1 20); do
    sleep 2
    RUN_ID="$(gh run list --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
    [[ "$RUN_ID" != "$PREV_RUN" && "$RUN_ID" != "0" ]] && break
    RUN_ID=""
done

if [[ -z "$RUN_ID" ]]; then
    echo "  Dispatched, but the run id did not appear in time."
    echo "  Follow it at: https://github.com/${REPO}/actions/workflows/${WORKFLOW}"
    exit 0
fi

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│        ✓ Release running in the cloud        │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Version : ${NEW_NAME}"
echo "  Run     : https://github.com/${REPO}/actions/runs/${RUN_ID}"
echo ""
echo "  Streaming below — Ctrl-C only detaches this terminal,"
echo "  the release finishes on GitHub either way."
echo ""

gh run watch "$RUN_ID" --exit-status || true
