# ChoreTracker

ChoreTracker is an Android household chore tracker built with a local-first, offline-first architecture. It helps couples or housemates log daily chores, track who does what, and keep things fair with contribution statistics and staleness alerts.

## Features

### Dashboard
- **Member contribution cards** showing total, last-30-days, and current-month counts per person.
- **Quick Log** — tap a chore button to log a completion. Select participants, add an optional note, and save. Chores sorted by frequency for fast access.
- **Recent completions** feed with chore name, date, participants, and notes.
- **Needs Attention** section highlighting stale chores (7+ days since last completion).
- **Sync status banner** with retry for offline-queued operations.

### Statistics
- **Per-chore comparison** — counts by member, total, and leader label.
- **Monthly breakdown** — last 6 months of activity per member.
- **Chore staleness** — OK / Soon / Needs attention / Never done status per chore.

### Settings
- Household name editing and invite code management.
- Add/remove household members.
- Add, toggle active/inactive, and soft-delete chores.
- Account management and sign out.

### Auth & Onboarding
- Firebase Auth (email/password) with preview mode when Firebase is not configured.
- Household creation or join-via-invite-code onboarding flow.
- Household restore from Firestore on first login.

### Sync
- Offline-first: all mutations queue as pending sync operations in Room.
- Sync pushes household snapshots to Firestore and clears the queue on success.
- Sync state (last synced, pending count, errors) is observable from the dashboard.

## Architecture

Clean Architecture with MVVM presentation layer:

```
app
├── feature/auth          Auth screens (sign-in, sign-up, preview mode)
├── feature/onboarding    Household create/join flow
├── feature/dashboard     Main screen, chore logging, completions
├── feature/stats         Statistics and comparisons
├── feature/household     Household info display
├── feature/settings      Management of household, members, chores, account
├── core/model            Domain models (Household, Chore, Completion, etc.)
├── core/common           AppResult, MVI contracts, utilities
├── core/domain           Use cases, HouseholdStatisticsCalculator
├── core/data-contract    Repository interfaces
├── core/data             OfflineFirst repository implementations
├── core/database-room    Room entities, DAOs, database
├── core/remote-contract  Remote data source interfaces
├── core/remote-firebase  Firebase Auth + Firestore implementation
├── core/sync             LocalSyncRepository, pending operation resolution
├── core/design           Material 3 theme, Compose components, strings
├── core/formatters       Date/time locale formatting
├── core/test             Test utilities, sample data generators
└── build-logic           Convention plugins (Android, Compose, Hilt, Room)
```

- **DI:** Hilt with `@HiltViewModel` and `@InstallIn(SingletonComponent)`.
- **State:** `StateFlow<UiState>` collected with `collectAsStateWithLifecycle()`.
- **Navigation:** Jetpack Compose Navigation with bottom tabs (Dashboard, Stats, Settings).
- **Database:** Room with 8 entities, soft-delete support, pending sync queue.
- **Remote:** Firebase Auth + Firestore with emulator support via BuildConfig.
- **Testing:** JUnit, MockK, Turbine for Flow testing, Robolectric.

## Build

```bash
./gradlew assembleDebug
```

| Tool | Version |
|------|---------|
| AGP | 9.4.0 |
| Gradle | 9.7.1 |
| Kotlin | 2.4.10 |
| Compose BOM | 2026.08.00 |
| Room | 2.8.4 |
| Hilt | 2.60.1 |

## Firebase

Firebase is optional. Without configuration the auth screen shows a banner and lets you continue in preview mode with local-only data.

Full setup: [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

### Deploying rules, indexes, and Cloud Functions

CI deploys `firestore.rules`, `firestore.indexes.json`, and `functions/` automatically on every push to `main` — merging a PR *is* the deploy, there's no separate release step or maintenance window to pick.

To try a rules, index, or function change against the real project before merging (the project id is already set in `.firebaserc`, so no `--project` flag is needed):

```bash
npm install -g firebase-tools   # once, if you don't already have it
firebase login

# Rules and indexes together
firebase deploy --only firestore

# Cloud Functions (installs dependencies; firebase.json's predeploy hook builds the TypeScript)
npm --prefix functions ci
firebase deploy --only functions
```

Manually deploying affects the real `choretracker-fb576` project this app runs against day to day. Prefer the [Firestore Emulator Suite](docs/FIREBASE_SETUP.md#6-emulator-first-development) first for anything rules-related — it never touches production data.

## CI

GitHub Actions runs Detekt, Android lint, the full test suite (`./gradlew test`), `assembleDebug`, and a Cloud Functions typecheck (`npm run build` in `functions/`) on every push and pull request. On push to `main`, it also deploys `firestore.rules`, `firestore.indexes.json`, and `functions/` to the live Firebase project — see [Deploying rules, indexes, and Cloud Functions](#deploying-rules-indexes-and-cloud-functions) above.
