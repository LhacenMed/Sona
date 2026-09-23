#!/usr/bin/env bash
#
# release.sh — release console for Sona.
#
# Picks the next version interactively, then hands the actual work to
# .github/workflows/release.yml. Nothing is built, signed or pushed locally:
# once the run is dispatched this script is a viewer, and closing the terminal
# (or losing power) has no effect on the release.
#
# Run from ANY branch, on ANY device.
#   1. Read the current version from origin/main — the authoritative copy
#   2. Prompt for release type, bump kind and notes
#   3. Preview + confirm, then dispatch the cloud pipeline
#   4. Stream the run (Ctrl-C is safe — it detaches, it does not cancel)
#
# Usage:
#   ./scripts/release.sh
#   ./scripts/release.sh --type beta --bump build --notes 'Fixed the thing.' --yes
#
# Any flag left unset falls back to its interactive prompt, so partial flags
# (e.g. just --type) still ask for the rest. Passing --yes skips the
# confirmation prompt.
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
#   ./scripts/release.sh --type beta --bump build --yes --notes-file - <<'EOF'
#   ## What's new
#   - Fixed the `player` crash
#   EOF
#
# Requirements: gh (authenticated) and jq. No keystore, no Android SDK.

set -euo pipefail

usage() {
    cat <<'USAGE'
Usage: ./scripts/release.sh [options]

  --type <stable|alpha|beta|rc>       Release type (default prompt: stable)
  --bump <patch|minor|major|build>    Version bump kind (default prompt: patch)
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
FLAG_TYPE="" FLAG_BUMP="" FLAG_NOTES="" FLAG_NOTES_FILE="" FLAG_YES=0

# `set -u` turns a flag left without its value into "$2: unbound variable", which
# says nothing about which flag was left dangling. This names it.
need_value() {
    [[ $# -ge 2 ]] || { echo "✗ ${1} needs a value."; exit 1; }
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --type)            need_value "$@"; FLAG_TYPE="$2"; shift 2 ;;
        --type=*)          FLAG_TYPE="${1#*=}"; shift ;;
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
source "${SCRIPT_DIR}/lib/git.sh"

GRADLE_FILE="app/build.gradle.kts"
MAIN_BRANCH="main"
WORKFLOW="release.yml"

# ── Preconditions ───────────────────────────────────────────────────────────────
for cmd in gh jq; do
    command -v "$cmd" >/dev/null || { echo "✗ Required tool not found: ${cmd}"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "✗ gh not authenticated — run: gh auth login"; exit 1; }

REPO="$(git::repo_slug)"
SOURCE_BRANCH="$(git::current_branch)"

# The pipeline builds from origin, so local-only work would silently be left out.
git::ensure_clean
git::ensure_pushed "$SOURCE_BRANCH"

# ── Step 1: Read current version from origin/main ───────────────────────────────
git fetch origin "$MAIN_BRANCH" --quiet
MAIN_GRADLE="$(mktemp)"
NOTES_DIR="$(mktemp -d)"
NOTES_FILE="${NOTES_DIR}/RELEASE_NOTES.md"
trap 'rm -f "$MAIN_GRADLE"; rm -rf "$NOTES_DIR"' EXIT
git show "origin/${MAIN_BRANCH}:${GRADLE_FILE}" > "$MAIN_GRADLE"

version::read "$MAIN_GRADLE"
CURRENT_NAME="$(version::name "$V_TYPE" "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD")"

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│            Sona Release Pipeline            │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Current version : ${CURRENT_NAME}  (${V_TYPE})"
echo "  Source branch   : ${SOURCE_BRANCH}"
echo "  Target branch   : ${MAIN_BRANCH}"
echo "  Runs on         : GitHub Actions"
echo ""

# ── Step 2: Choose version type ─────────────────────────────────────────────────
if [[ -n "$FLAG_TYPE" ]]; then
    case "${FLAG_TYPE,,}" in
        stable)                       NEW_TYPE="Stable" ;;
        alpha)                        NEW_TYPE="Alpha" ;;
        beta)                         NEW_TYPE="Beta" ;;
        rc|release-candidate)         NEW_TYPE="ReleaseCandidate" ;;
        *) echo "✗ --type must be one of: stable, alpha, beta, rc"; exit 1 ;;
    esac
else
    echo "  Release type:"
    echo "    1) Stable          (e.g. 1.2.3)"
    echo "    2) Alpha           (e.g. 1.2.3-alpha.1)"
    echo "    3) Beta            (e.g. 1.2.3-beta.1)"
    echo "    4) Release Candidate  (e.g. 1.2.3-rc.1)"
    echo ""
    ask TYPE_CHOICE "  Select [1-4, default 1]: "
    TYPE_CHOICE="${TYPE_CHOICE:-1}"

    case "$TYPE_CHOICE" in
        1) NEW_TYPE="Stable" ;;
        2) NEW_TYPE="Alpha" ;;
        3) NEW_TYPE="Beta" ;;
        4) NEW_TYPE="ReleaseCandidate" ;;
        *) echo "✗ Invalid choice"; exit 1 ;;
    esac
fi

# ── Step 3: Choose bump kind ────────────────────────────────────────────────────
if [[ -n "$FLAG_BUMP" ]]; then
    BUMP_KIND="${FLAG_BUMP,,}"
    case "$BUMP_KIND" in
        patch|minor|major) ;;
        build)
            if [[ "$NEW_TYPE" == "Stable" ]]; then
                echo "✗ --bump build is only for pre-release types"; exit 1
            fi
            ;;
        *) echo "✗ --bump must be one of: patch, minor, major, build"; exit 1 ;;
    esac
else
    echo ""
    echo "  Version bump:"
    echo "    1) patch  — bug fixes          (x.y.Z)"
    echo "    2) minor  — new features       (x.Y.0)"
    echo "    3) major  — breaking changes   (X.0.0)"

    if [[ "$NEW_TYPE" != "Stable" ]]; then
        echo "    4) build  — pre-release iteration (same x.y.z, +build)"
    fi

    echo ""
    BUMP_EXTRA=""; [[ "$NEW_TYPE" != "Stable" ]] && BUMP_EXTRA=" or 4"
    ask BUMP_CHOICE "  Select [1-3${BUMP_EXTRA}, default 1]: "
    BUMP_CHOICE="${BUMP_CHOICE:-1}"

    case "$BUMP_CHOICE" in
        1) BUMP_KIND="patch" ;;
        2) BUMP_KIND="minor" ;;
        3) BUMP_KIND="major" ;;
        4)
            if [[ "$NEW_TYPE" == "Stable" ]]; then
                echo "✗ Build increment is only for pre-release types"; exit 1
            fi
            BUMP_KIND="build"
            ;;
        *) echo "✗ Invalid choice"; exit 1 ;;
    esac
fi

version::bump "$NEW_TYPE" "$BUMP_KIND"
NEW_NAME="$(version::name "$NEW_TYPE" "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD")"
TAG="v${NEW_NAME}"

# ── Step 4: Release notes ────────────────────────────────────────────────────────
# Written in an editor rather than at the prompt: notes are markdown and run to
# several lines.
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

if [[ -n "$FLAG_NOTES" || -n "$FLAG_NOTES_FILE" ]]; then
    if [[ -n "$FLAG_NOTES_FILE" ]]; then
        NOTES="$(read_notes_file "$FLAG_NOTES_FILE" --notes-file)"
    else
        NOTES="$FLAG_NOTES"
    fi

    [[ -n "$NOTES" ]] || { echo "✗ Release notes are required — pass --notes or --notes-file."; exit 1; }
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


${CUT_MARKER}
Release notes for ${TAG}.

Write the notes above the ${CUT_MARKER} line. Markdown is supported: ## headings,
- bullets and **bold** all render in the app's update dialog and on the GitHub
release page.

Everything from ${CUT_MARKER} down is ignored, and saving with no notes aborts
the release.
TEMPLATE

    eval "$(git var GIT_EDITOR) \"\$NOTES_FILE\""

    NOTES="$(notes_section)"
    [[ -n "$NOTES" ]] || { echo "  Aborted — no release notes written."; exit 0; }
fi

# ── Step 5: Preview + confirm ────────────────────────────────────────────────────
echo ""
echo "  ┌── Release Preview ────────────────────────────┐"
echo "  │  ${CURRENT_NAME}  →  ${NEW_NAME}"
echo "  │  Tag    : ${TAG}"
echo "  │  Type   : ${NEW_TYPE}"
echo "  │  Repo   : ${REPO}"
echo "  ├── Notes ─────────────────────────────────────┤"
sed 's/^/  │  /' <<< "$NOTES"
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

# ── Step 6: Dispatch the cloud pipeline ──────────────────────────────────────────
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
    --arg release_type  "$NEW_TYPE" \
    --arg bump          "$BUMP_KIND" \
    --arg notes         "$NOTES" \
    --arg source_branch "$SOURCE_BRANCH" \
    '{release_type: $release_type, bump: $bump, notes: $notes,
      source_branch: $source_branch}' \
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
