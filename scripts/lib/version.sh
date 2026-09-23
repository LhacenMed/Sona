#!/usr/bin/env bash
# lib/version.sh — version read/bump helpers for build.gradle.kts.
# Source this file; do not execute directly.

# ── Regex patterns (match the sealed class blocks in build.gradle.kts) ─────────
_MAJOR_RE='versionMajor[[:space:]]*=[[:space:]]*([0-9]+)'
_MINOR_RE='versionMinor[[:space:]]*=[[:space:]]*([0-9]+)'
_PATCH_RE='versionPatch[[:space:]]*=[[:space:]]*([0-9]+)'
_BUILD_RE='versionBuild[[:space:]]*=[[:space:]]*([0-9]+)'

# version::read <gradle_file>
# Sets: V_TYPE, V_MAJOR, V_MINOR, V_PATCH, V_BUILD
version::read() {
    local file="$1"
    local block
    # grep -A is far more robust than an awk range pattern against CRLF/BOM
    # quirks that can otherwise hang or silently mismatch on Windows-edited files.
    block="$(grep -A 5 'val currentVersion' "$file")"

    if [[ -z "$block" ]]; then
        echo "✗ version::read — could not locate 'val currentVersion' block in ${file}" >&2
        exit 1
    fi

    if   echo "$block" | grep -q 'Version\.Alpha';             then V_TYPE="Alpha"
    elif echo "$block" | grep -q 'Version\.Beta';              then V_TYPE="Beta"
    elif echo "$block" | grep -q 'Version\.ReleaseCandidate';  then V_TYPE="ReleaseCandidate"
    else                                                             V_TYPE="Stable"
    fi

    V_MAJOR="$(echo "$block" | grep -oE "$_MAJOR_RE" | grep -oE '[0-9]+$')"
    V_MINOR="$(echo "$block" | grep -oE "$_MINOR_RE" | grep -oE '[0-9]+$')"
    V_PATCH="$(echo "$block" | grep -oE "$_PATCH_RE" | grep -oE '[0-9]+$')"
    # versionBuild is absent for Stable versions — grep legitimately finds no match
    # and exits 1, which would otherwise trip set -e/pipefail. The `|| true` lets
    # that be a normal "not present" case instead of a fatal error.
    V_BUILD="$(echo "$block" | grep -oE "$_BUILD_RE" | grep -oE '[0-9]+$' || true)"
    V_BUILD="${V_BUILD:-0}"

    # Fail loudly instead of silently proceeding with empty version numbers.
    if [[ -z "$V_MAJOR" || -z "$V_MINOR" || -z "$V_PATCH" ]]; then
        echo "✗ version::read — failed to parse major/minor/patch from ${file}" >&2
        echo "  Parsed block was:" >&2
        echo "$block" >&2
        exit 1
    fi
}

# version::name <type> <major> <minor> <patch> [build]
# Prints the human-readable version string (mirrors Kotlin toVersionName()).
version::name() {
    local type="$1" major="$2" minor="$3" patch="$4" build="${5:-0}"
    case "$type" in
        Alpha)            echo "${major}.${minor}.${patch}-alpha.${build}" ;;
        Beta)             echo "${major}.${minor}.${patch}-beta.${build}"  ;;
        ReleaseCandidate) echo "${major}.${minor}.${patch}-rc.${build}"    ;;
        *)                echo "${major}.${minor}.${patch}"                ;;
    esac
}

# version::bump <type> <bump_kind>
# bump_kind: major | minor | patch | build (build increments versionBuild for pre-release)
# Sets: V_MAJOR, V_MINOR, V_PATCH, V_BUILD (in place)
version::bump() {
    local type="$1" kind="$2"
    case "$kind" in
        major) V_MAJOR=$(( V_MAJOR + 1 )); V_MINOR=0; V_PATCH=0; V_BUILD=0 ;;
        minor) V_MINOR=$(( V_MINOR + 1 )); V_PATCH=0; V_BUILD=0             ;;
        patch) V_PATCH=$(( V_PATCH + 1 )); V_BUILD=0                        ;;
        build) V_BUILD=$(( V_BUILD + 1 ))                                   ;;
        *)     echo "✗ version::bump — unknown bump kind: ${kind}" >&2; exit 1 ;;
    esac
}

# version::write <gradle_file> <new_type> <major> <minor> <patch> [build]
# Rewrites the `val currentVersion` block in-place.
# Uses printf (not bash string \n interpolation) to build real newlines reliably,
# then passes the block to perl via an environment variable — never via shell
# string interpolation into the perl program text, which avoids any quoting/
# escaping hazard from the version values reaching the regex engine.
version::write() {
    local file="$1" type="$2" major="$3" minor="$4" patch="$5" build="${6:-0}"

    local new_block
    if [[ "$type" == "Stable" ]]; then
        new_block="$(printf 'val currentVersion: Version = Version.Stable(\n    versionMajor = %s,\n    versionMinor = %s,\n    versionPatch = %s,\n)' "$major" "$minor" "$patch")"
    else
        new_block="$(printf 'val currentVersion: Version = Version.%s(\n    versionMajor = %s,\n    versionMinor = %s,\n    versionPatch = %s,\n    versionBuild = %s,\n)' "$type" "$major" "$minor" "$patch" "$build")"
    fi

    NEW_BLOCK="$new_block" perl -i -0777 -pe \
        's/val currentVersion: Version = Version\.\w+\(.*?\)\n?/$ENV{NEW_BLOCK}\n/s' \
        "$file" \
        || { echo "✗ version::write — perl substitution failed on ${file}" >&2; exit 1; }
}