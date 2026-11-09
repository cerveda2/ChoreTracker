import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import ci_changes


class CheckSelectionTest(unittest.TestCase):
    def test_docs_site_and_store_listing_skip_expensive_checks(self):
        self.assertEqual(ci_changes.checks([
            "README.md", "docs/FIREBASE_SETUP.md", "site/index.html",
            ".github/workflows/pages.yml", "app/src/main/play/en-US/title.txt",
            "core/domain/README.md", "functions/README.md", "docs/diagram.png", ".claude/settings.json", "LICENSE",
        ]), {"android": False, "backend": False, "version": False, "backend_deploy": False, "workflows": True})

    def test_android_sources_and_build_inputs(self):
        for path in ["app/src/main/AndroidManifest.xml", "core/domain/src/Foo.kt",
                     "feature/auth/impl/src/Foo.kt", "build-logic/convention/build.gradle.kts",
                     "gradle/wrapper/gradle-wrapper.jar", "config/detekt/detekt.yml",
                     "settings.gradle.kts", "gradle.properties", ".editorconfig",
                     "new-module/src/main/res/values/strings.xml", "gradle.lockfile"]:
            with self.subTest(path=path):
                self.assertEqual(ci_changes.checks([path]),
                                 {"android": True, "backend": False, "version": False, "backend_deploy": False, "workflows": False})

    def test_backend_sources_rules_indexes_and_tests(self):
        for path in ["functions/src/index.ts", "functions/test/requests.test.js",
                     "functions/package-lock.json", "firestore.rules", "firestore.indexes.json",
                     "firebase.json", ".firebaserc"]:
            with self.subTest(path=path):
                self.assertEqual(ci_changes.checks([path]),
                                 {"android": False, "backend": True, "version": False, "backend_deploy": not path.startswith("functions/test/"), "workflows": False})

    def test_version_bump_runs_android_checks_and_requests_a_release(self):
        self.assertEqual(ci_changes.checks(["version.properties"]),
                         {"android": True, "backend": False, "version": True, "backend_deploy": False, "workflows": False})

    def test_ci_logic_changes_validate_both_stacks(self):
        for path in [".github/workflows/android.yml", ".github/workflows/release.yml",
                     ".github/workflows/deploy-firebase.yml", ".github/scripts/ci_changes.py"]:
            with self.subTest(path=path):
                self.assertEqual(ci_changes.checks([path]),
                                 {"android": True, "backend": True, "version": False, "backend_deploy": False, "workflows": path.endswith(".yml")})

    def test_failed_and_cancelled_jobs_block_deploys(self):
        ci_changes.validation({"android": {"result": "skipped"}, "backend": {"result": "success"}})
        for result in ["failure", "cancelled"]:
            with self.subTest(result=result), self.assertRaises(ValueError):
                ci_changes.validation({"backend": {"result": result}})


class GitChangesTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        previous = os.getcwd()
        os.chdir(self.temporary.name)
        self.addCleanup(os.chdir, previous)
        self.git("init", "--quiet", "--initial-branch=main")
        self.git("config", "user.email", "ci@example.invalid")
        self.git("config", "user.name", "CI tests")
        self.write("functions/src/index.ts", "original")
        self.write("firestore.rules", "original")
        self.write("version.properties", "original")
        self.initial = self.commit()

    def git(self, *args):
        return subprocess.check_output(["git", *args], stderr=subprocess.STDOUT).decode().strip()

    def write(self, path, content):
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        Path(path).write_text(content)

    def commit(self):
        self.git("add", "--all")
        self.git("commit", "--quiet", "-m", "fixture")
        return self.git("rev-parse", "HEAD")

    def test_pr_diff_ignores_new_main_commits_and_includes_deleted_files(self):
        self.git("checkout", "-qb", "pr")
        Path("functions/src/index.ts").unlink()
        head = self.commit()
        self.git("checkout", "main")
        self.write("version.properties", "bump on main")
        base = self.commit()
        paths = ci_changes.event_paths("pull_request", {
            "pull_request": {"base": {"sha": base}, "head": {"sha": head}},
        })
        self.assertEqual(paths, ["functions/src/index.ts"])

    def test_push_diff_covers_all_commits(self):
        self.write("functions/src/index.ts", "change")
        self.commit()
        self.write("firestore.rules", "change")
        head = self.commit()
        paths = ci_changes.event_paths("push", {"before": self.initial, "after": head})
        self.assertEqual(set(paths), {"functions/src/index.ts", "firestore.rules"})

    def test_initial_push_checks_all_files(self):
        paths = ci_changes.event_paths("push", {"before": "0" * 40, "after": self.initial})
        self.assertEqual(ci_changes.checks(paths), {"android": True, "backend": True, "version": True, "backend_deploy": True, "workflows": False})

    def test_renames_include_old_and_new_stack(self):
        self.git("mv", "functions/src/index.ts", "App.kt")
        self.commit()
        self.assertEqual(set(ci_changes.changed_paths(self.initial)),
                         {"functions/src/index.ts", "App.kt"})

    def test_app_ci_and_test_only_release_skips_backend_after_successful_previous_release(self):
        self.git("tag", "v0.2.0")
        self.write("app/src/App.kt", "app change")
        self.write("version.properties", "version bump")
        self.write(".github/workflows/android.yml", "CI change")
        self.write("functions/test/new.test.js", "test change")
        self.commit()
        with patch.object(ci_changes, "previous_release_succeeded", return_value=True) as succeeded:
            self.assertFalse(ci_changes.production_backend_changed())
            succeeded.assert_called_once_with(self.initial)

    def test_backend_merged_before_version_bump_is_deployed(self):
        self.git("tag", "v0.2.0")
        self.write("functions/src/index.ts", "backend change")
        self.commit()
        self.write("version.properties", "version bump")
        self.commit()
        with patch.object(ci_changes, "previous_release_succeeded", return_value=True):
            self.assertTrue(ci_changes.production_backend_changed())

    def test_failed_tagged_release_cannot_hide_unapplied_backend_changes(self):
        self.git("tag", "v0.2.0")
        self.write("functions/src/index.ts", "backend change that failed deployment")
        self.commit()
        self.git("tag", "v0.2.1")
        self.write("version.properties", "next version bump only")
        self.commit()
        with patch.object(ci_changes, "previous_release_succeeded", return_value=False):
            self.assertTrue(ci_changes.production_backend_changed())

    def test_first_release_deploys_backend_without_history_lookup(self):
        with patch.object(ci_changes, "previous_release_succeeded") as succeeded:
            self.assertTrue(ci_changes.production_backend_changed())
            succeeded.assert_not_called()

    def release_fixture(self, tags):
        self.write("version.properties", "version.major=0\nversion.minor=2\nversion.patch=1\n")
        remote = tempfile.TemporaryDirectory()
        self.addCleanup(remote.cleanup)
        self.git("init", "--quiet", "--bare", remote.name)
        self.git("remote", "add", "origin", remote.name)
        for tag in tags:
            self.git("push", "origin", f"HEAD:refs/tags/{tag}")

    def test_first_release_ignores_non_version_tags(self):
        self.release_fixture(["unrelated-tag", "vlatest"])
        self.assertEqual(ci_changes.release_version(), {"tag": "v0.2.1", "release": True})

    def test_new_version_is_compared_numerically(self):
        self.release_fixture(["v0.1.99", "v0.2.0"])
        self.assertTrue(ci_changes.release_version()["release"])

    def test_existing_version_and_superseded_runs_do_not_release(self):
        for tags in [["v0.2.1"], ["v0.2.10"]]:
            with self.subTest(tags=tags), patch.object(ci_changes, "git", return_value=f"sha\trefs/tags/{tags[0]}"):
                self.write("version.properties", "version.major=0\nversion.minor=2\nversion.patch=1\n")
                self.assertFalse(ci_changes.release_version()["release"])

    def test_tag_lookup_failure_cannot_trigger_a_release(self):
        self.write("version.properties", "version.major=0\nversion.minor=2\nversion.patch=1\n")
        with patch.object(ci_changes, "git", side_effect=subprocess.CalledProcessError(128, "git")):
            with self.assertRaises(subprocess.CalledProcessError):
                ci_changes.release_version()

    def test_malformed_version_is_rejected_before_building(self):
        self.write("version.properties", "version.major=0\nversion.minor=two\nversion.patch=1\n")
        with self.assertRaises(ValueError):
            ci_changes.release_version()


class ReleaseHistoryTest(unittest.TestCase):
    def test_successful_play_upload_confirms_both_manual_and_reusable_releases(self):
        cases = [
            ("Publish to Play internal testing", "success", "github-actions", True),
            ("Release / Release pipeline / Publish to Play internal testing", "success", "github-actions", True),
            ("Publish to Play internal testing", "failure", "github-actions", False),
            ("Publish to Play internal testing", "skipped", "github-actions", False),
            ("Publish to Play internal testing", "success", "another-app", False),
            ("CI validation", "success", "github-actions", False),
        ]
        for name, conclusion, app, expected in cases:
            payload = json.dumps({"check_runs": [{"name": name, "conclusion": conclusion, "app": {"slug": app}}]})
            with self.subTest(name=name, conclusion=conclusion, app=app):
                with patch.dict(os.environ, {"GITHUB_REPOSITORY": "fixture/repo"}):
                    with patch.object(subprocess, "check_output", return_value=payload.encode()):
                        self.assertEqual(ci_changes.previous_release_succeeded("sha"), expected)

    def test_missing_or_unavailable_history_is_not_treated_as_a_successful_release(self):
        for response in [b'{"check_runs": []}', b'invalid-json', subprocess.CalledProcessError(1, "gh")]:
            with self.subTest(response=response), patch.dict(os.environ, {"GITHUB_REPOSITORY": "fixture/repo"}):
                kwargs = {"side_effect": response} if isinstance(response, Exception) else {"return_value": response}
                with patch.object(subprocess, "check_output", **kwargs):
                    self.assertFalse(ci_changes.previous_release_succeeded("sha"))


if __name__ == "__main__":
    unittest.main()
