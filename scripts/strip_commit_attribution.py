"""Strip Claude co-author attribution from every commit and tag message.

Removes `Co-Authored-By: ... <noreply@anthropic.com>`, `Claude-Session:`,
"Generated with [Claude Code]" and bare claude.ai session-link lines in one
git-filter-repo pass over all local refs (branches, remote-tracking branches,
tags), so commits shared between branches stay shared under one new hash.
Messages without attribution are left byte-for-byte untouched.

--dry-run lists each attributed commit, the lines that would be removed from
it and every ref that would be rewritten; nothing is rewritten or pushed.

--push fetches every remote first, then force-pushes each remote branch and
tag whose commit was rewritten, leased against the value just fetched, so
anything pushed to a remote in the meantime is never overwritten. Combined
with --dry-run it only fetches, so the preview covers what the remotes hold.

Usage, from anywhere in the repository with no uncommitted tracked changes:
    python scripts/strip_commit_attribution.py [--dry-run] [--push]

Requires git-filter-repo (pip install git-filter-repo).
"""

import argparse
import io
import os
import re
import subprocess
import sys
from pathlib import Path

ATTRIBUTION_LINE = re.compile(
    rb"^(?:co-authored-by:[^\n]*<noreply@anthropic\.com>"
    rb"|claude-session:[^\n]*"
    rb"|[^\n]*generated with \[claude code\][^\n]*"
    rb"|https://claude\.ai/code/session_\S*"
    rb")[ \t\r]*(?:\n|\Z)",
    re.IGNORECASE | re.MULTILINE,
)

# git-filter-repo runs callbacks in its own interpreter; importing this module
# there (via PYTHONPATH) keeps strip_attribution() the single source of truth.
MESSAGE_CALLBACK = f"from {Path(__file__).stem} import strip_attribution; return strip_attribution(message)"

PUSHABLE_REF_PREFIXES = ("refs/heads/", "refs/tags/")


def strip_attribution(message: bytes) -> bytes:
    stripped = ATTRIBUTION_LINE.sub(b"", message)
    return message if stripped == message else stripped.rstrip() + b"\n"


def git(*args: str) -> bytes:
    return subprocess.run(["git", *args], check=True, stdout=subprocess.PIPE).stdout


def git_lines(*args: str) -> list[str]:
    return git(*args).decode().splitlines()


def ensure_clean_worktree() -> None:
    if git("status", "--porcelain", "--untracked-files=no"):
        sys.exit("Commit or stash tracked changes first: the rewrite resets the working tree.")


def find_attributed_commits() -> dict[str, bytes]:
    """Maps each commit (in any ref) whose message carries attribution to that message."""
    entries = git("log", "--all", "--format=%H%x01%B%x00").split(b"\x00")
    commits = (entry.lstrip(b"\n").split(b"\x01", 1) for entry in entries if b"\x01" in entry)
    return {sha.decode(): message for sha, message in commits if strip_attribution(message) != message}


def ref_snapshot() -> dict[str, str]:
    # Symbolic refs (e.g. refs/remotes/origin/HEAD) print as blank lines and are skipped.
    lines = git_lines("for-each-ref", "--format=%(if)%(symref)%(then)%(else)%(objectname) %(refname)%(end)")
    return {ref: sha for sha, ref in (line.split(" ", 1) for line in lines if line)}


def print_preview(attributed_commits: dict[str, bytes]) -> None:
    for sha, message in attributed_commits.items():
        subject = message.split(b"\n", 1)[0].decode(errors="replace")
        print(f"{sha[:7]} {subject}")
        for line in ATTRIBUTION_LINE.findall(message):
            print(f"    - {line.decode(errors='replace').rstrip()}")
    print("Refs that would be rewritten (refs/remotes/* are what --push would force-push):")
    for ref in ref_snapshot():
        if not attributed_commits.keys().isdisjoint(git_lines("rev-list", ref)):
            print(f"  {ref}")


def rewrite_history(git_dir: Path) -> dict[str, str]:
    """Rewrites every local ref in place; returns {old commit: new commit} for each rewritten commit."""
    metadata_dir = git_dir / "filter-repo"
    # A leftover marker from an earlier run would merge this run's maps into that run's.
    (metadata_dir / "already_ran").unlink(missing_ok=True)
    python_path = os.pathsep.join(filter(None, [str(Path(__file__).parent), os.environ.get("PYTHONPATH")]))
    # --partial rewrites all refs where they stand; without it, filter-repo folds refs/remotes/origin/*
    # into local branches (dropping any that diverged), removes the origin remote and expires reflogs.
    # --force skips its fresh-clone check; the working tree is checked by ensure_clean_worktree().
    subprocess.run(
        ["git", "filter-repo", "--force", "--partial", "--message-callback", MESSAGE_CALLBACK],
        check=True,
        env={**os.environ, "PYTHONPATH": python_path, "PYTHONDONTWRITEBYTECODE": "1"},
    )
    rows = (line.split() for line in (metadata_dir / "commit-map").read_text().splitlines()[1:])
    return {old: new for old, new in rows if old != new}


def remote_refs(remote: str) -> dict[str, tuple[str, str]]:
    """Maps each ref on the remote to (its value, the commit it points at), peeling annotated tags."""
    values, peeled = {}, {}
    for line in git_lines("ls-remote", remote):
        sha, ref = line.split("\t")
        if ref.endswith("^{}"):
            peeled[ref.removesuffix("^{}")] = sha
        else:
            values[ref] = sha
    return {ref: (value, peeled.get(ref, value)) for ref, value in values.items()}


def push_rewritten_refs(rewritten: dict[str, str]) -> None:
    for remote in git_lines("remote"):
        leases, refspecs, read_only_refs = [], [], []
        for ref, (value, commit) in remote_refs(remote).items():
            if commit not in rewritten:
                continue
            if not ref.startswith(PUSHABLE_REF_PREFIXES):
                if ref.startswith("refs/"):
                    read_only_refs.append(ref)
                continue
            # A branch moves to its commit's rewrite; a tag to the local tag filter-repo rewrote.
            source = rewritten[commit] if ref.startswith("refs/heads/") else ref
            leases.append(f"--force-with-lease={ref}:{value}")
            refspecs.append(f"{source}:{ref}")
        if refspecs:
            subprocess.run(["git", "push", "--atomic", remote, *leases, *refspecs], check=True)
        for ref in read_only_refs:
            print(f"{remote}: {ref} still points at the old history (read-only, e.g. a GitHub pull request ref).")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="list what would be stripped and which refs would be rewritten; rewrites and pushes nothing "
        "(with --push it fetches first, so the preview covers what the remotes hold)",
    )
    parser.add_argument(
        "--push",
        action="store_true",
        help="fetch all remotes first, then force-push (leased) every remote branch and tag that was rewritten",
    )
    args = parser.parse_args()
    # Keep this script's output in order with the git subprocesses writing to the same console.
    if isinstance(sys.stdout, io.TextIOWrapper):
        sys.stdout.reconfigure(line_buffering=True)

    os.chdir(git("rev-parse", "--show-toplevel").decode().strip())
    if not args.dry_run:
        ensure_clean_worktree()
    if args.push:
        git("fetch", "--all", "--prune")

    attributed_commits = find_attributed_commits()
    if not attributed_commits:
        print("No attributed commits found; history is already clean.")
        return
    if args.dry_run:
        print_preview(attributed_commits)
        return
    print(f"Stripping attribution from {len(attributed_commits)} commits...")

    before = ref_snapshot()
    rewritten = rewrite_history(Path(git("rev-parse", "--git-dir").decode().strip()))
    after = ref_snapshot()
    print("Rewritten refs:")
    for ref in sorted(ref for ref, sha in before.items() if after.get(ref) != sha):
        print(f"  {ref}  {before[ref][:7]} -> {after[ref][:7]}")

    if args.push:
        push_rewritten_refs(rewritten)
    else:
        print("Only local refs were rewritten; rerun with --push to publish.")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as error:
        sys.exit(f"Failed: {' '.join(map(str, error.cmd))}")
