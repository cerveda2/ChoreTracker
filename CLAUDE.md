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
```

## Key Conventions

- New Compose components go in `core/design`, following `SectionCard`, `PrimaryButton` patterns
- Chore/member management UI lives in **settings feature only**
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

## Code Review Findings Backlog (ordered by priority)

Issues found during codebase review (2026-05-13). Work through one at a time. Items already in Release Readiness Backlog are excluded.

### MEDIUM — Bugs

6. **`HouseholdRole.valueOf` unguarded** — `LocalSyncRepository.kt:232`, `DatabaseMappers.kt:30`. Throws `IllegalArgumentException` on unknown enum value. Fix: `runCatching { HouseholdRole.valueOf(it) }.getOrDefault(HouseholdRole.MEMBER)`.
7. **`selectedChoreId!!` force-unwrap** — `DashboardScreen.kt:315`. `mutableStateOf var` can't be smart-cast; capture to local `val` instead.
8. **`SettingsViewModel` double-subscribes** — `SettingsViewModel.kt:75` and `:152`. Two independent `flatMapLatest` chains on the same household flow → household-restore logic runs twice on init.
10. **`Timestamp?.asInstant()` returns epoch for null** — `FirebaseHouseholdDataSource.kt:324`. Missing `createdAt` in Firestore → `Instant.fromEpochMilliseconds(0)` → chores appear massively overdue.
11. **`observeRecentCompletions(limit = Int.MAX_VALUE)`** — `DashboardViewModel.kt:53`. Loads every completion into dashboard `StateFlow`. Full history screen should subscribe to its own flow.

### MEDIUM — Code Quality

12. **FQN usages instead of imports:**
    - `LogCompletionBottomSheet.kt:38,42,49,54,149` — `SnapshotStateList`, `kotlin.time.Instant`, `Clock.System`
    - `RecentCompletionDetailScreen.kt:42` — `SnapshotStateList`
    - `FirebaseHouseholdDataSource.kt:94,132` — `DocumentReference`
13. **`PreviewAwareAuthRepository` unmanaged `CoroutineScope`** — `PreviewAwareAuthRepository.kt:25`. Should inject `@ApplicationScope CoroutineScope` instead of creating its own.

### LOW — File Organization

17. **`ChoresSettingsScreen.kt` is 685 lines** — `RenameChoreDialog`, `ChoreFrequencyDialog`, `ChoreCategoryDialog` should be extracted to their own files.
18. **`StatsTab` enum inside `StatsScreen.kt`** — should be its own file per convention.

### LOW — UX / Data Layer

19. **Detail screen permanent loading** — `DashboardNavigation.kt:70–71`. If `allCompletions` hasn't loaded when navigating to a completion detail, the screen shows permanent loading with no error state or retry.
20. **`observeRecentCompletions` limit not pushed to SQL** — `CompletionDao` has no `LIMIT` clause; limit is applied in-memory after loading all rows.

---

## Feature Backlog

1. **QR code invite sharing** — On the owner's Manage Household screen, display a QR code for the current invite code. On the join screen, allow scanning the QR code as the primary entry path with manual code input as fallback. Use a lightweight QR-generation library (e.g. `io.github.alexzhirkevich:qrose`) for generation and `androidx.camera` + ML Kit for scanning.
2. **Real-time Firestore sync** — Currently uses one-shot `get()` calls; a member's logged completion doesn't appear on the owner's device until the next explicit sync. Switch the completions collection (and members) to `addSnapshotListener` for push-based delivery. Requires restructuring the remote data source from pull to push — non-trivial but eliminates the need for FCM to trigger refreshes. Known UX issue: owner doesn't see accepted invite until cold start — will be resolved by invite-accepted notification or real-time sync.
3. **Password visibility toggle** — Show/hide button on sign-in and sign-up password fields.
4. **Invite code input auto-uppercase** — Join screen code field should `KeyboardCapitalization.Characters` + auto-uppercase transform so manual entry matches generated codes without user needing to switch case.
5. **Pull-to-refresh on dashboard** — `SwipeRefresh` (or `PullToRefreshBox` M3) to manually trigger `syncPendingOperations` + `restoreHouseholdForUser`.
6. **Invite accepted notification** — FCM push to owner when a member consumes an invite link. Requires Phase 1 Firebase setup (Crashlytics) to be in place first.
7. **Notification settings** — In-app screen to toggle specific notification types (invite accepted, chore reminders, etc.).

---

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
21. **Chore reminders** — WorkManager daily job; push notification when X chores need attention.
