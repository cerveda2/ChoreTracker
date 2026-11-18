# ChoreTracker

ChoreTracker is an offline-first Android chore tracker for couples and housemates. Log what you did in a tap, see who's carrying the load, and keep it fair. Changes are saved on the device first and shared with the household through Firebase whenever you're online. The app is available in English and Czech.

## Features

### Home
- **Balance**: each member's share of completions over the last 30 days.
- **Asked of you**: chores another member asked you to do, with Done and "Can't today".
- **Overdue** and **Due soon**: chores past or close to their schedule, with who did them last and whose turn it is. Mark one done in a tap; logging on someone else's turn asks first. When it's another member's turn, **Remind {name}** nudges them.
- **Waiting on others**: chores you asked someone to do, which you can cancel. A decline shows for a day.
- **Recent activity**, plus the full completion history filtered by member or chore. Completions can be edited or deleted.
- **Log**: pick a chore (suggested ones first), who did it, when, and an optional note.
- Sync status chip, a banner when notifications are off, and pull to refresh.

### Chores
- Search, filter by Overdue, Due soon or Paused, and mark a chore done from the list.
- Add or edit a chore: name (with suggestions), category, and schedule (none, daily, weekly, every N days, chosen days of the week, or a day of the month). Pause or delete it.
- **Take turns**: rotate a chore between members. Whoever has done it least since the rotation started is up next.
- **Remind me when it's due**: turn the daily reminder off for one chore, just for you.
- **Ask**: ask another member to do one or more chores, with an optional note.

### Insights
- Week, Month, 6 months or All.
- Share of work per member, the last 6 months, a split by category, and counts per chore with whose turn is next.
- Completion history for each chore.

### Settings
- **Household**: rename it, manage invite codes (copy, share, QR code, history), transfer ownership, or delete the household.
- **Members**: add members before they join and send each one an invite, make or remove admins, remove members.
- **Roles**: the owner and admins manage the household and members and can edit any chore or completion. Members add chores and edit their own. Only the owner changes roles, transfers ownership or deletes the household.
- **Account**: display name, leave household, sign out, delete account.
- **Preferences**: notifications, language, theme (system, light, dark, wallpaper colors), and the app version.

### Notifications
- A daily reminder at a time you choose: one notification listing chores that need attention and chores asked of you. Turn-based chores are included only on your turn. The household syncs first, so chores done on another phone aren't reported.
- Push notifications when someone accepts your invite or asks you to do chores. Each can be turned off.

### Accounts and sync
- Firebase Auth with email and password. Without Firebase configuration, preview mode runs with local-only data.
- Create a household, or join one with an invite code typed in or scanned as a QR code. Signing in restores your household from Firestore.
- Room is the source of truth: changes queue locally and upload in the background, and Firestore listeners keep members, chores, completions, invites and requests live on every device.
- A removed member's device drops the household's data on its next sync.

## Architecture

Clean Architecture with an MVI presentation layer (`UiState`/`UiIntent`/`UiEffect` in `core/common`):

```
app                       Navigation root, bottom tabs (Home, Chores, Insights, Settings)
├── feature/auth          Sign in, sign up, preview mode
├── feature/onboarding    Create or join a household, QR code scanner
├── feature/dashboard     Home, logging, completion history, requests
├── feature/chores        Chore list, editor, turns, Ask sheet
├── feature/stats         Insights and per-chore history
├── feature/settings      Household, members, roles, account, preferences
├── core/model            Domain models (Household, Chore, Completion, ChoreRequest, etc.)
├── core/common           AppResult, MVI contracts, utilities
├── core/domain           Use cases, HouseholdStatisticsCalculator
├── core/data-contract    Repository interfaces
├── core/data             OfflineFirst repository implementations, DataStore settings
├── core/database-room    Room entities, DAOs, migrations, database
├── core/remote-contract  Remote data source interfaces
├── core/remote-firebase  Firebase Auth, Firestore and Cloud Functions implementation
├── core/sync             LocalSyncRepository, upload queue, real-time listeners
├── core/notifications    FCM token registration and push notifications
├── core/reminders        Daily chore reminder (WorkManager)
├── core/design           Material 3 theme, Compose components, strings
├── core/formatters       Date/time locale formatting
├── core/test             Test utilities, sample data generators
├── functions             Cloud Functions (TypeScript) and emulator tests
└── build-logic           Convention plugins (Android, Compose, Hilt, Room)
```

Feature code lives in each feature's `impl` module (`feature/<name>/impl`).

- **DI:** Hilt, including `HiltWorkerFactory` for WorkManager.
- **State:** `StateFlow<UiState>` collected with `collectAsStateWithLifecycle()`.
- **Navigation:** Navigation Compose with four bottom tabs.
- **Database:** Room (schema version 13) with a pending sync queue and soft-deleted chores. Every schema bump ships a migration, tested from the oldest shipped schema.
- **Remote:** Firebase Auth, Firestore, Cloud Functions, Cloud Messaging and Crashlytics, with emulator support in debug builds.
- **Testing:** JUnit 4, MockK, Turbine, Truth and Robolectric. `functions/test/` runs the Cloud Functions and `firestore.rules` against the Firebase emulators.

## Build

Provide local Firebase client files as described in [Firebase setup](docs/FIREBASE_SETUP.md).
For a fresh checkout without real Firebase configuration, generate demo clients first:

```bash
python3 .github/scripts/firebase_config.py --allow-demo
./gradlew assembleDebug
```

| Tool | Version |
|------|---------|
| AGP | 9.5.0 |
| Gradle | 9.8.0 |
| Kotlin | 2.4.20 |
| Compose BOM | 2026.09.00 |
| Room | 2.8.5 |
| Hilt | 2.60.1 |
| minSdk / targetSdk | 28 / 37 |

## Firebase

Firebase is optional. Without configuration the auth screen shows a banner and lets you continue in preview mode with local-only data.

Full setup: [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

### Deploying rules, indexes, and Cloud Functions

Debug builds (`cz.dcervenka.choretracker.debug`) use the dev project `choretracker-dev-1faf5`; release builds use production, `choretracker-fb576`. `.firebaserc` defines the aliases `dev` and `prod`, and `default` points at dev.

Automatic deployments, manual branch previews, and release deployment rules are described in the [CI and release policy](docs/CI.md#deployments-and-releases).

Production gets backend changes together with an app release, and old and new app versions run side by side until everyone updates, so rules and functions must keep working with the previous release too.

To try a rules, index, or function change by hand, the commands below target the dev project (the `default` alias), so no `--project` flag is needed. The Firebase CLI never targets production without an explicit `--project prod`.

```bash
npm install -g firebase-tools   # once, if you don't already have it
firebase login

# Rules and indexes together
firebase deploy --only firestore

# Cloud Functions (installs dependencies; firebase.json's predeploy hook builds the TypeScript)
npm --prefix functions ci
firebase deploy --only functions
```

Prefer the [Firestore Emulator Suite](docs/FIREBASE_SETUP.md#6-emulator-first-development) first for anything rules-related — it doesn't touch any cloud project.

## CI

GitHub Actions checks affected Android/backend code on PRs and main. See the [run matrix and required-check setup](docs/CI.md#what-runs-now).

## Releasing

Bump `version.major`/`minor`/`patch` in `version.properties` in a PR. The [release policy](docs/CI.md#deployments-and-releases) covers signing, Play internal testing, backend deployment, manual runs, and retries.
