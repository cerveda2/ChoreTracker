"""Select CI checks and release work from Git changes."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess

BACKEND_FILES = {"firestore.rules", "firestore.indexes.json", "firebase.json", ".firebaserc"}


def git(*args):
    return subprocess.check_output(["git", *args]).decode("utf-8", "surrogateescape").strip("\n")


def changed_paths(base, head="HEAD", merge_base=False):
    revisions = [f"{base}...{head}"] if merge_base else [base, head]
    return git("diff", "--no-renames", "--name-only", "-z", *revisions).strip("\0").split("\0")


def event_paths(event_name, event):
    if event_name == "pull_request":
        pr = event["pull_request"]
        return changed_paths(pr["base"]["sha"], pr["head"]["sha"], merge_base=True)
    if event_name != "push":
        raise ValueError(f"Unsupported CI event: {event_name}")
    before = event["before"]
    if not before.strip("0"):
        return git("ls-files", "-z").strip("\0").split("\0")
    # A force push can make the old tip unreachable from the checked-out branches.
    if subprocess.run(["git", "cat-file", "-e", before], capture_output=True).returncode:
        subprocess.run(["git", "fetch", "--no-tags", "origin", before], check=True)
    return changed_paths(before, event["after"])


def backend_source_changed(paths):
    return any(path in BACKEND_FILES or (
        path.startswith("functions/") and not path.startswith("functions/test/")
        and not path.endswith(".md")
    ) for path in paths)


def checks(paths):
    android = backend = version = False
    for path in paths:
        if not path or path.endswith(".md"):
            continue
        if path.startswith(".github/"):
            if path != ".github/workflows/pages.yml":
                android = backend = True
        elif path.startswith("functions/") or path in BACKEND_FILES:
            backend = True
        elif path.startswith(("app/src/main/play/", "docs/", "site/", ".claude/", ".agents/")) or path in {
            "LICENSE", "LICENSE.txt", "NOTICE", ".gitignore",
        }:
            continue
        else:
            # Unknown paths validate Android too, so adding a module cannot silently bypass CI.
            android = True
        version |= path == "version.properties"
    return {"android": android, "backend": backend, "version": version,
            "backend_deploy": backend_source_changed(paths),
            "workflows": any(path.startswith(".github/workflows/") and path.endswith((".yml", ".yaml"))
                             for path in paths)}


def previous_release_succeeded(commit):
    try:
        runs = json.loads(subprocess.check_output([
            "gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/commits/{commit}/check-runs?per_page=100",
        ], stderr=subprocess.PIPE))
    except (subprocess.CalledProcessError, json.JSONDecodeError):
        return False
    return any(run.get("app", {}).get("slug") == "github-actions"
               and run.get("name", "").endswith("Publish to Play internal testing")
               and run.get("conclusion") == "success" for run in runs.get("check_runs", []))


def production_backend_changed():
    previous = subprocess.run([
        "git", "describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", "HEAD",
    ], capture_output=True, text=True)
    previous_tag = previous.stdout.strip()
    if previous.returncode != 0 or not previous_release_succeeded(git("rev-parse", f"{previous_tag}^{{commit}}")):
        # Tags precede deployment. A failed/unknown previous release must not hide unapplied changes.
        print("::notice::No confirmed successful previous release; deploying the backend conservatively")
        return True
    return backend_source_changed(changed_paths(previous_tag))


def validation(results):
    failed = [name for name, job in results.items() if job["result"] not in {"success", "skipped"}]
    if failed:
        raise ValueError(f"CI did not pass: {', '.join(failed)}")


def release_version():
    properties = dict(line.split("=", 1) for line in Path("version.properties").read_text().splitlines()
                      if "=" in line and not line.lstrip().startswith("#"))
    parts = [properties[f"version.{part}"].strip() for part in ("major", "minor", "patch")]
    if not all(part.isascii() and part.isdecimal() for part in parts):
        raise ValueError("Version components must be non-negative integers")
    version = tuple(map(int, parts))
    tag = "v" + ".".join(map(str, version))
    released = []
    # Let a network failure fail CI, rather than misidentifying it as a missing tag.
    for line in git("ls-remote", "--tags", "--refs", "origin", "refs/tags/v*").splitlines():
        match = re.fullmatch(r"refs/tags/v(\d+)\.(\d+)\.(\d+)", line.split()[1])
        if match:
            released.append(tuple(map(int, match.groups())))
    should_release = not released or version > max(released)
    if not should_release:
        print(f"::notice::{tag} is already tagged or superseded by a newer version; skipping")
    return {"tag": tag, "release": should_release}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["checks", "release-backend", "validate", "version"])
    args = parser.parse_args()
    if args.command == "checks":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        output = checks(event_paths(os.environ["GITHUB_EVENT_NAME"], event))
    elif args.command == "release-backend":
        output = {"changed": production_backend_changed()}
    elif args.command == "version":
        output = release_version()
    else:
        validation(json.loads(os.environ["NEEDS_JSON"]))
        print("All applicable checks passed")
        return
    lines = "".join(f"{key}={str(value).lower() if isinstance(value, bool) else value}\n"
                    for key, value in output.items())
    print(lines, end="")
    with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
        stream.write(lines)


if __name__ == "__main__":
    main()
