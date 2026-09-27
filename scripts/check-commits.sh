#!/usr/bin/env bash
#
# check-commits.sh — checks a pull request's commit messages, as CONTRIBUTING.md
# describes them, before they reach dev.
#
#   ./scripts/check-commits.sh <base> <head>
#
# Each commit after <base> up to <head>, merges aside, must:
#   - have a Conventional Commits subject: "<type>(<scope>): <summary>"
#   - carry no "Release:" trailer - an artifact release is the maintainer's to
#     make, and would otherwise be made the moment the pull request is merged
# and a trailer that is none of the changelog's headings is pointed out, as the
# likeliest reason a change goes missing from the notes.
#
# Writes GitHub annotations when run in Actions, and fails when a rule is broken.

set -euo pipefail

BASE="$1" HEAD="$2"
TYPES='feat|fix|perf|refactor|style|build|ci|docs|chore|test|revert'
# The trailer keys the changelog reads, and those git tools add themselves.
KNOWN_TRAILERS='^(added|changed|fixed|removed|co-authored-by|signed-off-by|reviewed-by)$'

failures=0
while read -r sha; do
    subject="$(git log -1 --format=%s "$sha")"
    short="${sha:0:7}"

    if [[ ! "$subject" =~ ^(${TYPES})(\([a-z0-9-]+\))?!?:\ .+ ]]; then
        echo "::error title=Commit ${short}::\"${subject}\" is not a Conventional Commits subject - <type>(<scope>): <summary>, the type one of ${TYPES//|/, }."
        failures=$(( failures + 1 ))
    fi

    while IFS= read -r key; do
        [[ -n "$key" ]] || continue
        if [[ "${key,,}" == "release" ]]; then
            echo "::error title=Commit ${short}::carries a Release: trailer. Artifact releases are made by the maintainer - remove it."
            failures=$(( failures + 1 ))
        elif [[ ! "${key,,}" =~ $KNOWN_TRAILERS ]]; then
            echo "::warning title=Commit ${short}::has a \"${key}:\" trailer, which the changelog does not read - it reads Added, Changed, Fixed and Removed."
        fi
    done < <(git log -1 --format='%(trailers:only,unfold,key_value_separator=: )' "$sha" | sed 's/: .*//')
done < <(git rev-list --no-merges "${BASE}..${HEAD}")

if (( failures )); then
    echo "✗ ${failures} problem(s) in the commit messages - see CONTRIBUTING.md#commit-messages."
    exit 1
fi
echo "✓ Commit messages follow CONTRIBUTING.md."
