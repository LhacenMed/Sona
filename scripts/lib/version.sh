#!/usr/bin/env bash
# lib/version.sh — Sona's version rules, over release tags.
# Source this file; do not execute directly.
#
# The repository's `v*` tags are the one record of what has been released, so
# every version is worked out from them - never from a file a push could race.
# Builds are handed the result as `-Psona.version=<name>`.
#
# A version is  major.minor.patch  for a stable release, or
#               major.minor.patch-<stage>.<build>  for an artifact, stage being
#               alpha < beta < rc - the stages of the version it leads to.

# Makes git order 1.6.0-alpha.1 < 1.6.0-beta.1 < 1.6.0-rc.1 < 1.6.0, as semver does.
_VERSION_SORT=(-c versionsort.suffix=-alpha -c versionsort.suffix=-beta -c versionsort.suffix=-rc)

# version::latest [stable]
# Prints the newest release version (without its "v"), or the newest stable one
# with "stable". Prints nothing when there is none.
version::latest() {
    local pattern='v[0-9]*'
    local tags
    tags="$(git "${_VERSION_SORT[@]}" tag --list "$pattern" --sort=-v:refname)"
    if [[ "${1:-}" == "stable" ]]; then
        tags="$(grep -v -- '-' <<< "$tags" || true)"
    fi
    head -n 1 <<< "$tags" | sed 's/^v//'
}

# version::parse <name>
# Sets: V_MAJOR, V_MINOR, V_PATCH, V_STAGE ("" when stable), V_BUILD (0 when stable)
version::parse() {
    local name="$1"
    if [[ ! "$name" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)(-(alpha|beta|rc)\.([0-9]+))?$ ]]; then
        echo "✗ version::parse — not a release version: ${name}" >&2
        exit 1
    fi
    V_MAJOR="${BASH_REMATCH[1]}"
    V_MINOR="${BASH_REMATCH[2]}"
    V_PATCH="${BASH_REMATCH[3]}"
    V_STAGE="${BASH_REMATCH[5]}"
    V_BUILD="${BASH_REMATCH[6]:-0}"
}

# version::format -> prints the name of the parsed version in V_*.
version::format() {
    if [[ -n "$V_STAGE" ]]; then
        echo "${V_MAJOR}.${V_MINOR}.${V_PATCH}-${V_STAGE}.${V_BUILD}"
    else
        echo "${V_MAJOR}.${V_MINOR}.${V_PATCH}"
    fi
}

# Prints a stage's rank, so stages compare as numbers.
_version_stage_rank() {
    case "$1" in
        alpha) echo 1 ;;
        beta)  echo 2 ;;
        rc)    echo 3 ;;
        *)     echo "✗ Unknown stage: $1 (alpha, beta or rc)" >&2; exit 1 ;;
    esac
}

# Prints <stable> bumped by <kind>: major, minor or patch.
_version_bumped() {
    local stable="$1" kind="$2"
    version::parse "$stable"
    case "$kind" in
        major) echo "$(( V_MAJOR + 1 )).0.0" ;;
        minor) echo "${V_MAJOR}.$(( V_MINOR + 1 )).0" ;;
        patch) echo "${V_MAJOR}.${V_MINOR}.$(( V_PATCH + 1 ))" ;;
        *)     echo "✗ Unknown bump: ${kind} (major, minor or patch)" >&2; exit 1 ;;
    esac
}

# Prints the larger of two x.y.z versions.
_version_max() {
    printf '%s\n%s\n' "$1" "$2" | sort -t. -k1,1n -k2,2n -k3,3n | tail -n 1
}

# version::next_artifact <stage> [bump]
# Prints the next artifact version, from the tags:
#   - The version it leads to is the last stable one bumped by [bump] - patch when
#     none is given - or the one the newest artifacts already lead to, whichever
#     is greater. A new one starts at build 1.
#   - Towards the same version, the same stage counts on, a later stage starts
#     again at 1, and an earlier stage is refused: it would be a lower version
#     than one already released.
version::next_artifact() {
    local stage="$1" bump="${2:-patch}"
    _version_stage_rank "$stage" > /dev/null
    local stable latest target
    stable="$(version::latest stable)"
    stable="${stable:-0.0.0}"
    latest="$(version::latest)"
    target="$(_version_bumped "$stable" "$bump")"

    local build=1
    if [[ -n "$latest" ]]; then
        version::parse "$latest"
        if [[ -n "$V_STAGE" ]]; then
            local leading="${V_MAJOR}.${V_MINOR}.${V_PATCH}"
            local chosen
            chosen="$(_version_max "$leading" "$target")"
            if [[ "$chosen" == "$leading" ]]; then
                target="$leading"
                local rank latest_rank
                rank="$(_version_stage_rank "$stage")"
                latest_rank="$(_version_stage_rank "$V_STAGE")"
                if (( rank < latest_rank )); then
                    echo "✗ ${stage} would come before ${latest} - an artifact cannot go back a stage." >&2
                    exit 1
                fi
                (( rank == latest_rank )) && build=$(( V_BUILD + 1 ))
            fi
        fi
    fi
    echo "${target}-${stage}.${build}"
}

# version::next_stable <bump>
# Prints the next stable version, from the tags: "release" makes the version the
# newest artifacts lead to stable; major, minor and patch bump the last stable one;
# "hotfix" is a patch on the last stable one, released from beside dev - so it
# may come below artifacts already out, which keep leading on to their version.
version::next_stable() {
    local bump="$1"
    local latest stable
    latest="$(version::latest)"
    stable="$(version::latest stable)"
    stable="${stable:-0.0.0}"
    local latest_stage=""
    if [[ -n "$latest" ]]; then
        version::parse "$latest"
        latest_stage="$V_STAGE"
    fi
    if [[ "$bump" == "release" ]]; then
        if [[ -z "$latest_stage" ]]; then
            echo "✗ There is no artifact to release - bump major, minor or patch instead." >&2
            exit 1
        fi
        echo "${V_MAJOR}.${V_MINOR}.${V_PATCH}"
        return
    fi
    if [[ "$bump" == "hotfix" ]]; then
        _version_bumped "$stable" patch
        return
    fi
    local next
    next="$(_version_bumped "$stable" "$bump")"
    # Never below what the artifacts already reached.
    if [[ -n "$latest_stage" ]]; then
        version::parse "$latest"
        if [[ "$(_version_max "${V_MAJOR}.${V_MINOR}.${V_PATCH}" "$next")" != "$next" ]]; then
            echo "✗ ${next} is below ${latest}, already released - use a larger bump, or release." >&2
            exit 1
        fi
    fi
    echo "$next"
}

# version::write_stable <gradle_file> <name>
# Rewrites the `val lastStableVersion` block: what a local build is versioned as.
version::write_stable() {
    local file="$1" name="$2"
    version::parse "$name"
    local block
    block="$(printf 'val lastStableVersion: Version = Version.Stable(\n    versionMajor = %s,\n    versionMinor = %s,\n    versionPatch = %s,\n)' "$V_MAJOR" "$V_MINOR" "$V_PATCH")"
    NEW_BLOCK="$block" perl -i -0777 -pe \
        's/val lastStableVersion: Version = Version\.Stable\(.*?\)\n?/$ENV{NEW_BLOCK}\n/s' \
        "$file" \
        || { echo "✗ version::write_stable — perl substitution failed on ${file}" >&2; exit 1; }
    local written
    written="$(grep -A 4 'val lastStableVersion' "$file" | tr -d ' \r\n')"
    [[ "$written" == "vallastStableVersion:Version=Version.Stable(versionMajor=${V_MAJOR},versionMinor=${V_MINOR},versionPatch=${V_PATCH},)" ]] \
        || { echo "✗ version::write_stable — ${file} was not updated" >&2; exit 1; }
}
