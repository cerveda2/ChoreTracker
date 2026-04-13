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
| AGP | 9.1.0 |
| Gradle | 9.3.1 |
| Kotlin | 2.3.20 |
| Compose BOM | 2026.03.01 |
| Room | 2.8.4 |
| Hilt | 2.59.2 |

## Firebase

Firebase is optional. Without configuration the auth screen shows a banner and lets you continue in preview mode with local-only data.

Full setup: [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

## CI

GitHub Actions workflow runs `assembleDebug` and unit tests on push.
