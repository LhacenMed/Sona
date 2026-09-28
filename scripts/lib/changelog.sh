#!/usr/bin/env bash
# lib/changelog.sh — release notes, gathered from commit messages.
# Source this file; do not execute directly.
#
# A release's notes are every commit since the release before it, oldest first,
# under Keep a Changelog's headings. Each commit speaks for itself one of two
# ways:
#
#   - Its changelog lines, when it has them: git trailers closing its message,
#     one per change, as CONTRIBUTING.md describes - written for users, so they
#     win whenever they are there.
#
#       Added: A sleep timer in the player's menu.
#       Fixed: The queue no longer jumps when a track is removed.
#
#   - Otherwise its Conventional Commits subject, under the heading its type
#     stands for: "feat(player): add a sleep timer" is Added, "Add a sleep timer."
#     A type users never notice - build, ci, docs, chore, test, style - says
#     nothing, and neither does a subject that is not conventional, such as the
#     pipeline's own "release: v1.6.0".

# The headings, in the order the notes list them.
CHANGELOG_SECTIONS=(Added Changed Fixed Removed)

# Prints the heading a Conventional Commits type stands for, or nothing for a
# type users never notice.
_changelog_section_of_type() {
    case "$1" in
        feat)                 echo Added ;;
        fix)                  echo Fixed ;;
        perf|refactor|revert) echo Changed ;;
    esac
}

# Prints a subject's summary as a changelog entry: "add a sleep timer" becomes
# "Add a sleep timer.", read as the sentences written changelog lines are.
_changelog_entry_of_summary() {
    local entry="${1^}"
    [[ "$entry" =~ [.!?]$ ]] || entry="${entry}."
    echo "$entry"
}

# changelog::since <from_tag> [to_rev]
# Prints the notes of the commits after <from_tag> up to [to_rev] (HEAD), as
# markdown - "### Added" and its bullets, and so on - or nothing when none of
# them has anything to say. An empty <from_tag> takes the whole history.
changelog::since() {
    local from="$1" to="${2:-HEAD}"
    local range="$to"
    [[ -n "$from" ]] && range="${from}..${to}"

    # One entry list per heading, filled commit by commit.
    local -A entries=()
    local section
    for section in "${CHANGELOG_SECTIONS[@]}"; do entries[$section]=""; done

    # One record per commit - its subject, then its changelog lines, one per line -
    # read in a single pass over the range.
    local keys trailer_format record subject trailers line key value
    keys="$(printf 'key=%s,' "${CHANGELOG_SECTIONS[@]}")"
    trailer_format="%(trailers:${keys}unfold)"
    while IFS= read -r -d $'\x1e' record; do
        record="${record#$'\n'}"
        [[ -n "$record" ]] || continue
        subject="${record%%$'\x1f'*}"
        trailers="${record#*$'\x1f'}"

        if [[ -n "${trailers//[[:space:]]/}" ]]; then
            while IFS= read -r line; do
                [[ "$line" =~ ^([A-Za-z]+):[[:space:]]*(.+)$ ]] || continue
                key="${BASH_REMATCH[1],,}"
                value="${BASH_REMATCH[2]}"
                for section in "${CHANGELOG_SECTIONS[@]}"; do
                    [[ "$key" == "${section,,}" ]] && entries[$section]+="- ${value}"$'\n'
                done
            done <<< "$trailers"
        elif [[ "$subject" =~ ^([a-z]+)(\([a-z0-9-]+\))?!?:[[:space:]]+(.+)$ ]]; then
            section="$(_changelog_section_of_type "${BASH_REMATCH[1]}")"
            [[ -n "$section" ]] || continue
            entries[$section]+="- $(_changelog_entry_of_summary "${BASH_REMATCH[3]}")"$'\n'
        fi
    done < <(git log --reverse --no-merges --format="%s%x1f${trailer_format}%x1e" "$range")

    local printed=0
    for section in "${CHANGELOG_SECTIONS[@]}"; do
        [[ -n "${entries[$section]}" ]] || continue
        (( printed )) && echo ""
        echo "### ${section}"
        echo ""
        printf '%s' "${entries[$section]}"
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
# up to [to_rev] (HEAD) close - a "Fixes #12", "Closes #12" or "Resolves #12"
# line, as CONTRIBUTING.md writes them - or were merged as - the "(#12)" GitHub
# ends a squash-merged subject with. Only a line of its own counts, so a
# message that mentions "Fixes #12" in passing tells no issue anything.
changelog::referenced_issues() {
    local from="$1" to="${2:-HEAD}"
    local range="$to"
    [[ -n "$from" ]] && range="${from}..${to}"
    git log --format='%B' "$range" \
        | { grep -oiE '(^[[:space:]]*(close[sd]?|fix(e[sd])?|resolve[sd]?):? +#[0-9]+)|(\(#[0-9]+\)$)' || true; } \
        | grep -oE '[0-9]+' \
        | sort -un
}
