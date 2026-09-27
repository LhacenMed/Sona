#!/usr/bin/env bash
#
# check-commits.sh — checks commit messages, as CONTRIBUTING.md describes them.
#
#   ./scripts/check-commits.sh <base> <head>           a pull request's commits (CI)
#   ./scripts/check-commits.sh --message-file <file>   a message being committed
#                                                     (scripts/hooks/commit-msg)
#
# A message must:
#   - have a Conventional Commits subject: "<type>(<scope>): <summary>"
#   - have its changelog lines read as trailers: Added, Changed, Fixed and
#     Removed count only as the message's last paragraph, with nothing else in
#     it - a "Fixes #12" among them hides them all from the release notes
# A pull request's commits must also carry no "Release:" trailer - an artifact
# release is the maintainer's to make, and would otherwise be made the moment
# the pull request is merged. And a trailer that is none of the changelog's
# headings is pointed out, as the likeliest reason a change goes missing.
#
# Writes GitHub annotations when run in Actions, and fails when a rule is broken.

set -euo pipefail

TYPES='feat|fix|perf|refactor|style|build|ci|docs|chore|test|revert'
CHANGELOG_KEYS='added|changed|fixed|removed'
# The trailer keys the changelog reads, those git tools add, and the release marker.
KNOWN_TRAILERS="^(${CHANGELOG_KEYS}|release|co-authored-by|signed-off-by|reviewed-by)$"

failures=0

# report <error|warning> <what> <message>
report() {
    local level="$1" what="$2" message="$3"
    if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
        echo "::${level} title=${what}::${message}"
    else
        echo "$( [[ "$level" == error ]] && echo "✗" || echo "!") ${what}: ${message}"
    fi
    [[ "$level" == error ]] && failures=$(( failures + 1 ))
    return 0
}

# check_message <what> <message> <allow_release>
check_message() {
    local what="$1" message="$2" allow_release="$3"
    local subject
    subject="$(head -n 1 <<< "$message")"

    # Messages git and the release pipeline write themselves are left as they are.
    [[ "$subject" =~ ^(Merge\ |Revert\ \"|fixup!\ |squash!\ |amend!\ |release:\ v[0-9]) ]] && return 0

    if [[ ! "$subject" =~ ^(${TYPES})(\([a-z0-9-]+\))?!?:\ .+ ]]; then
        report error "$what" "\"${subject}\" is not a Conventional Commits subject - <type>(<scope>): <summary>, the type one of ${TYPES//|/, }."
    fi

    local trailers key read_lines written_lines
    trailers="$(git interpret-trailers --parse <<< "$message")"
    while IFS= read -r key; do
        [[ -n "$key" ]] || continue
        if [[ "${key,,}" == "release" && "$allow_release" != "true" ]]; then
            report error "$what" "carries a Release: trailer. Artifact releases are made by the maintainer - remove it."
        elif [[ ! "${key,,}" =~ $KNOWN_TRAILERS ]]; then
            report warning "$what" "has a \"${key}:\" trailer, which the changelog does not read - it reads Added, Changed, Fixed and Removed."
        fi
    done < <(sed 's/:.*//' <<< "$trailers")

    read_lines="$(grep -ciE "^(${CHANGELOG_KEYS}):" <<< "$trailers" || true)"
    written_lines="$(grep -ciE "^(${CHANGELOG_KEYS}):" <<< "$message" || true)"
    if (( written_lines > read_lines )); then
        report error "$what" "has changelog lines git does not read as trailers, so they would be left out of the release notes. Make them the message's last paragraph, with nothing else in it - a \"Fixes #12\" goes above them."
    fi
}

if [[ "${1:-}" == "--message-file" ]]; then
    check_message "Commit message" "$(git stripspace --strip-comments < "$2")" true
else
    BASE="$1" HEAD="$2"
    while read -r sha; do
        check_message "Commit ${sha:0:7}" "$(git log -1 --format=%B "$sha")" false
    done < <(git rev-list --no-merges "${BASE}..${HEAD}")
fi

if (( failures )); then
    echo "✗ ${failures} problem(s) in the commit message(s) - see CONTRIBUTING.md#commit-messages."
    exit 1
fi
echo "✓ Commit message(s) follow CONTRIBUTING.md."
