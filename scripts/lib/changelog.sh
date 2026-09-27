#!/usr/bin/env bash
# lib/changelog.sh — release notes, gathered from commit messages.
# Source this file; do not execute directly.
#
# A commit says what it changes for the user in git trailers - the "Key: value"
# lines closing its message, as CONTRIBUTING.md describes - one per change,
# under Keep a Changelog's headings:
#
#   Added: A sleep timer in the player's menu.
#   Fixed: The queue no longer jumps when a track is removed.
#
# A release's notes are every such line since the release before it, oldest
# first, under the heading each was given. Commits without any say nothing.

# The headings, in the order the notes list them.
CHANGELOG_SECTIONS=(Added Changed Fixed Removed)

# changelog::since <from_tag> [to_rev]
# Prints the notes of the commits after <from_tag> up to [to_rev] (HEAD), as
# markdown - "### Added" and its bullets, and so on - or nothing when none of
# them has anything to say. An empty <from_tag> takes the whole history.
changelog::since() {
    local from="$1" to="${2:-HEAD}"
    local range="$to"
    [[ -n "$from" ]] && range="${from}..${to}"

    local section entries printed=0
    for section in "${CHANGELOG_SECTIONS[@]}"; do
        entries="$(git log --reverse --no-merges \
            --format="%(trailers:key=${section},valueonly,unfold)" "$range" \
            | sed '/^[[:space:]]*$/d')"
        [[ -n "$entries" ]] || continue
        (( printed )) && echo ""
        echo "### ${section}"
        echo ""
        sed 's/^/- /' <<< "$entries"
        printed=1
    done
}

# changelog::add_release <changelog_file> <version> <date> <notes>
# Adds a release's section to CHANGELOG.md, newest first: under [Unreleased],
# above the release before it.
changelog::add_release() {
    local file="$1" version="$2" date="$3" notes="$4"
    local section
    section="$(printf '## [%s] - %s\n\n%s\n' "$version" "$date" "$notes")"
    SECTION="$section" perl -i -0777 -pe \
        's/^(## \[Unreleased\]\n)\n?/$1\n$ENV{SECTION}\n\n/m' \
        "$file" \
        || { echo "✗ changelog::add_release — could not write ${file}" >&2; exit 1; }
    grep -q "^## \[${version}\] - ${date}$" "$file" \
        || { echo "✗ changelog::add_release — ${file} has no [Unreleased] heading to add under" >&2; exit 1; }
}

# changelog::referenced_issues <from_tag> [to_rev]
# Prints, once each, the issues and pull requests the commits after <from_tag>
# up to [to_rev] (HEAD) close - "Fixes #12", "Closes #12", "Resolves #12" - or
# were merged as - the "(#12)" GitHub ends a squash-merged subject with.
changelog::referenced_issues() {
    local from="$1" to="${2:-HEAD}"
    local range="$to"
    [[ -n "$from" ]] && range="${from}..${to}"
    git log --format='%B' "$range" \
        | { grep -oiE '((close[sd]?|fix(e[sd])?|resolve[sd]?):? +#[0-9]+)|(\(#[0-9]+\)$)' || true; } \
        | grep -oE '[0-9]+' \
        | sort -un
}
