# ChoreTracker

Personal offline-first Android household chore tracker for couples. Built by David Cervenka.

## Architecture

- **Language**: Kotlin, Jetpack Compose, Coroutines + Flow
- **DI**: Hilt
- **DB**: Room (destructive migration fallback)
- **Remote**: Firebase Auth + Firestore (optional — app works in preview/offline mode)
- **Pattern**: Clean Architecture, MVVM with StateFlow

## Module Structure

```
app/                          Main entry, navigation, root scaffold
build-logic/                  Convention plugins, version catalog
core/
  common/                     AppResult, UiState, UiIntent contracts
  model/                      Domain models (Chore, Household, Completion, stats)
  domain/                     Use cases (28+), HouseholdStatisticsCalculator
  data-contract/              Repository interfaces
  data/                       OfflineFirst repositories, mappers
  database-room/              Room entities (8), DAOs (8), ChoreTrackerDatabase
  remote-contract/            Remote data source interfaces
  remote-firebase/            Firebase Auth + Firestore implementations
  sync/                       LocalSyncRepository, pending operation queue
  design/                     Material 3 theme, reusable components, strings, preview data
  formatters/                 Locale-aware date/time formatting
  test/                       Test utilities, sample data
feature/
  auth/impl/                  Sign in/up, preview mode
  onboarding/impl/            Create/join household
  dashboard/impl/             Main screen: contributions, quick-log, recent, needs attention
  stats/impl/                 Per-chore comparison, monthly breakdown, staleness
  settings/impl/              Household, members, chores management
  household/impl/             Read-only household info display
```

## Key Conventions

- New Compose components go in `core/design`, following `SectionCard`, `PrimaryButton` patterns
- Chore/member management UI lives in **settings feature only** (household is read-only)
- New data attributes must update: Room entity, DAO, mapper, repository, use case, Firestore schema, `firestore.rules`
- One feature per commit, scoped changes only
- Do NOT add comments, docstrings, or annotations to unchanged code
- Firestore security rules: `firestore.rules` in project root

## Git Conventions

### Branches
```
feature/feature_name      New features
chore/chore_name          Refactoring, cleanup, dependencies
bugfix/bugfix_name        Bug fixes
```

### Commit Messages
```
Feat: Feature name
Chore: Chore name
Bug: Bug name
```

Always create feature branches from `main`. PRs go into `main`.

## Build & Test

```bash
./gradlew assembleDebug                    # Build debug APK
./gradlew testDebugUnitTest                # Run unit tests
./gradlew :core:domain:test                # Test domain layer only
./gradlew :feature:dashboard:impl:test     # Test specific feature
```

## Improvement Backlog (ordered by priority)

1. Bottom sheet for logging (replace dialog with ModalBottomSheet)
2. One-tap quick log (skip dialog for single-user, "log for me" shortcut)
3. Undo snackbar (Snackbar with undo action after logging completion)
4. Empty states (illustrations for empty lists instead of plain text)
5. Animated transitions (shared element transitions, completion animations)
6. Graphs/charts/visual representation (balance bars, donut charts on stats)
7. Chore categories with icons (group by Kitchen/Bathroom/Laundry/etc.)
8. Quick log improvements (swipe-to-log, better chore ordering)
9. History/calendar view (heatmap or dot calendar for completion history)
10. Undo support (extend undo to edits and deletes, not just logging)
11. Onboarding with categories (preset chore templates during setup)
12. Widgets (Glance widget showing needs-attention chores)
13. Chore rotation (auto-suggest next person based on fairness)
14. Streak/completion tracking (streaks per chore, weekly awards)
15. Photo attachments (optional before/after photos on completions)
16. Drag to reorder (reorder quick-log chores, pin favorites)
17. Notifications (daily reminder, push when chore needs attention via WorkManager)
18. Haptic feedback (subtle haptics on quick-log and confirmation)

## Release Readiness Backlog (ordered by priority)

Items needed before (and for) Google Play publication. Some are useful well before launch.

### Phase 1 — Useful Now (add during active development)

1. **Crashlytics + Analytics** — Add `firebase-crashlytics` + `firebase-analytics` to BOM, apply Gradle plugin, init in Application. Catches crashes in dev/testing before users hit them. Convention plugin recommended.
2. **Release build hardening** — Enable `isMinifyEnabled = true`, `isShrinkResources = true`, `isDebuggable = false` for release. Add R8 keep rules for Room, Firebase, Hilt, Kotlin Serialization. Test release build regularly.
3. **ProGuard/R8 rules** — Current `proguard-rules.pro` is empty template. Need keep rules for: Room entities, Firebase model classes, Kotlin serialization, Hilt generated code. Test with `./gradlew assembleRelease` and verify no runtime crashes.
4. **Signing config** — Generate upload keystore (`.jks`), configure `signingConfigs` in `app/build.gradle.kts`, store passwords in `local.properties` (gitignored). Google Play requires consistent signing.
5. **Version bumping strategy** — Current `version.properties` (0.1.0) works. Define: patch = bugfix, minor = feature, major = breaking. Consider a Gradle task or script to bump + tag.

### Phase 2 — Pre-Launch Essentials

6. **Localization (i18n)** — Extract all hardcoded strings from `core/design` strings.xml into proper keys. Add `values-cs/` (Czech) as primary second language. Use Android Studio translation editor or Crowdin. All user-facing strings must go through `stringResource()`.
7. **Privacy policy + Terms** — Required by Google Play. Host on a simple webpage (GitHub Pages works). Link from app settings screen and Play Store listing.
8. **App icon + adaptive icon** — Production-quality launcher icon with foreground/background layers for adaptive icon support. Current icon may be template.
9. **Google Play Store listing assets** — Screenshots (phone + tablet if supported), feature graphic (1024x500), short/full descriptions, categorization.
10. **`google-services.json` security** — Currently committed to git. Move to gitignored location, provide via CI secret or local-only path. Not a hard blocker (Firebase keys are meant to be in APK) but best practice.

### Phase 3 — CI/CD & Release Automation

11. **Release CI workflow** — GitHub Actions workflow: on tag push, build signed release APK/AAB, run tests, upload artifact. Extend existing `android.yml`.
12. **Fastlane or Gradle Play Publisher** — Automate AAB upload to Google Play internal/beta/production tracks. Manage changelogs, screenshots, metadata in repo.
13. **Auto version bump on release** — Tag-triggered version increment. Could read `version.properties`, bump, commit, push.

### Phase 4 — Post-Launch Polish

14. **In-app update (Play Core)** — Prompt users to update when new version available. `com.google.android.play:app-update`.
15. **Firebase Performance Monitoring** — Track screen load times, network latency, slow frames. Useful once real users exist.
16. **Remote Config** — Feature flags for gradual rollouts. Useful at scale.
17. **ANR & vitals monitoring** — Play Console vitals + Crashlytics ANR tracking. Comes mostly free with Crashlytics.
18. **Accessibility audit** — Content descriptions, touch targets (48dp min), screen reader support, contrast ratios. Required for good Play Store rating.
19. **Tablet / foldable support** — Adaptive layouts for larger screens. Google Play flags apps that don't handle tablets well.
20. **Data export / account deletion** — Google Play policy requires account deletion option if app has accounts. Add "Delete my account" in settings.
