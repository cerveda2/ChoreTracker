# CI and releases

## What runs now

`android.yml` selects jobs from changed files on PRs and pushes to main. The workflow always runs; irrelevant jobs report as skipped so required checks still complete.

| Event | Validation | Delivery |
| --- | --- | --- |
| Android-only PR | Wrapper once, Detekt + lint, full JVM/Android unit tests, debug + unsigned R8 builds | Debug APK artifact |
| Backend-only PR | Functions build/typecheck + Auth/Firestore/Functions emulator tests | None |
| Mixed PR | Both stacks | Debug APK artifact |
| Main merge | Affected stacks, checked again on the merged commit | Backend source/configuration changes deploy to dev after validation |
| Version merge to main | Android checks; the signed AAB/APK build replaces the extra unsigned main R8 build | Conditional production backend deployment, then Play internal testing |
| Documentation/store listing/site | CI routing tests; workflow edits also run actionlint | Pages publishes site/Pages workflow changes on main |

CI/deployment logic changes validate both stacks. Backend test-only changes run emulator tests without deploying. Routing tests run on every event. `test` includes plain JVM modules as well as Android variants. Debug APKs and reports are retained for seven days.

`CI validation` accepts intentional skips and fails for failed/cancelled dependencies. The `main-protection` ruleset requires this check from GitHub Actions before merging; it covers all applicable Android and backend jobs, including the R8 build and emulator tests. Automatic deployments and releases also depend on it. Main validation remains because PRs can currently merge without being up to date with main. **CI setup** selects the affected stacks, tests the routing, lints workflow changes and validates the Gradle wrapper when Android checks apply.

PR updates cancel superseded runs. Main commits validate independently. [GitHub's `queue: max`](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency) preserves pending release/deployment runs without cancelling an active one. Actionlint 1.7.12 rejects this documented key, so only that exact diagnostic is ignored.

## Deployments and releases

Dev deploys `firestore,functions --force` after a successful main run that changes backend source/configuration. PRs use demo-project emulators. For a cloud preview, manually run **Deploy Firebase** with `dev` on the branch; manual deployments run emulator tests first. Dev is shared scratch state: queued main runs and manual previews can replace each other's backend.

For production, the version job compares backend files (`functions/`, `firestore.rules`, `firestore.indexes.json`, `firebase.json`, `.firebaserc`) with the previous reachable version tag. Functions tests and Markdown are excluded from deployment eligibility. It trusts that baseline only when the tagged commit has a successful **Publish to Play internal testing** check from GitHub Actions. Otherwise it deploys conservatively, covering first releases, failed tagged releases, and unavailable history. An app-only release after a confirmed successful release skips Firebase entirely.

Eligible deployments run `firebase deploy --only firestore,functions`; only dev adds `--force`. Deployment jobs use a read-only repository token. Manual production deployments must target main and bypass the release change filter, so they can also reconcile console/CLI edits. Play waits for a successful production deployment, or an intentional no-change skip; a failed deployment blocks publishing.

Bump `version.major/minor/patch` in `version.properties` in a PR to release. After main validation, `release.yml` makes one serialized call to `_release.yml` covering version checks, signing, the version tag, draft GitHub Release, Firebase, and Play. Both automatic and manual entry points use this same job-level lock. Per-project Firebase locks also serialize manual and automatic backend deployments.

The signed AAB, APK and Crashlytics mapping stay in the draft release; Play uploads that exact AAB. Signing disables configuration-cache use and cache writes, and the keystore is removed afterward. Closed-track promotion and store listings remain manual.

A manual **Release** run must target main and requires green `CI validation` for the same commit. Existing/superseded version tags skip building. Retry failures after tagging with **Re-run failed jobs** on the original run; failures before tagging can also use **Run workflow** on main.

Signing uses the repository's `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, and `RELEASE_KEY_PASSWORD` secrets. `FIREBASE_SERVICE_ACCOUNT` is environment-specific (`dev`/`prod`); `PLAY_SERVICE_ACCOUNT_JSON` belongs to `prod`. Reusable callers forward these secrets by name, even environment-only ones, since GitHub only guarantees that form for environment secrets. They are declared required so omitted forwarding fails workflow validation. See [GitHub's environment-secret rules](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows#using-inputs-and-secrets-in-a-reusable-workflow).
