#!/usr/bin/env bash
# lib/git.sh — git helpers for the release console.
# Source this file; do not execute directly.
#
# The branch mechanics (sync / merge / commit / fast-forward) now live in
# .github/workflows/release.yml, where they run against a fresh checkout.
# What remains here is what the local console still needs: identify the repo,
# refuse to dispatch a release that would miss local work, and dispatch and
# follow the pipelines.

# git::repo_slug -> prints "owner/repo" from the remote URL (supports https and ssh)
git::repo_slug() {
    git remote get-url origin \
        | sed -E 's|.*github\.com[:/]||; s|\.git$||'
}

# git::current_branch -> prints current branch name
git::current_branch() {
    git rev-parse --abbrev-ref HEAD
}

# git::ensure_clean
# Aborts if there are uncommitted changes (staged or unstaged).
git::ensure_clean() {
    if ! git diff --quiet || ! git diff --cached --quiet; then
        echo "✗ Working tree has uncommitted changes — stash or commit them first."
        exit 1
    fi
}

# git::ensure_pushed <branch>
# Aborts if the branch has commits that origin does not. The pipeline builds
# from origin, so unpushed commits would be silently absent from the release.
git::ensure_pushed() {
    local branch="$1"
    git fetch origin "$branch" --quiet 2>/dev/null || {
        echo "✗ Branch ${branch} does not exist on origin — push it first."
        exit 1
    }
    local ahead
    ahead="$(git rev-list --count "origin/${branch}..${branch}")"
    if [[ "$ahead" -gt 0 ]]; then
        echo "✗ ${branch} is ${ahead} commit(s) ahead of origin — push them first."
        exit 1
    fi
}

# git::release_published <tag>
# True only for a published release. A leftover draft from a failed run is
# reclaimed by the pipeline and must not block a retry.
git::release_published() {
    [[ "$(gh release view "$1" --json isDraft --jq .isDraft 2>/dev/null || echo absent)" == "false" ]]
}

# git::run_workflow <workflow> <ref> <inputs_json>
# Dispatches <workflow> on <ref> with <inputs_json>, then streams its run.
# `gh workflow run` does not return the run it starts, so the newest run is
# remembered first and the next one to appear is taken as it. Ctrl-C only
# detaches: the run goes on, on GitHub.
git::run_workflow() {
    local workflow="$1" ref="$2" inputs="$3"
    local previous run_id=""
    previous="$(gh run list --workflow "$workflow" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
    gh workflow run "$workflow" --ref "$ref" --json <<< "$inputs"

    for _ in $(seq 1 20); do
        sleep 2
        run_id="$(gh run list --workflow "$workflow" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
        [[ "$run_id" != "$previous" && "$run_id" != "0" ]] && break
        run_id=""
    done

    local repo
    repo="$(git::repo_slug)"
    if [[ -z "$run_id" ]]; then
        echo "  Dispatched, but its run did not appear in time."
        echo "  Follow it at: https://github.com/${repo}/actions/workflows/${workflow}"
        return 0
    fi
    echo ""
    echo "  Running on GitHub : https://github.com/${repo}/actions/runs/${run_id}"
    echo "  Streaming below - Ctrl-C only detaches; the release finishes on GitHub either way."
    echo ""
    gh run watch "$run_id" --exit-status || true
}
