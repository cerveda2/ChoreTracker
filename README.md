# ChoreTracker

ChoreTracker is an Android-first household chores app built with a local-first architecture that is ready to evolve toward Kotlin Multiplatform later. The current codebase already separates share-ready domain logic from Android-only UI, Room, WorkManager, and Firebase integration points.

## What is implemented

- Multi-module Gradle setup with convention plugins in `build-logic`.
- Shared MVI contracts in `core:common`, with the auth flow already wired to reducer-style intent handling.
- Warm Utility design system in `core:design`.
- Pure Kotlin shared-ready modules for `core:model`, `core:common`, `core:data-contract`, and `core:domain`.
- Room-backed local storage for households, members, chores, invites, completions, participants, and pending sync operations.
- Pure Kotlin statistics calculator in `core:domain`, covered by unit tests for monthly breakdowns, shared completions, and stale chore states.
- Repository layer that supports:
  - Firebase Auth when configured.
  - Preview mode when Firebase is not configured yet.
  - Local household creation and joining.
  - Chore creation and activation toggles.
  - Completion logging with multiple participants.
  - Dashboard and statistics aggregation from Room.
- Compose app shell with auth, onboarding, dashboard, household, stats, and settings flows.
- Local Git repository initialized on `main`.
- GitHub Actions workflow for `assembleDebug` plus unit tests.

## Module map

- `app`: entry point, root navigation, Hilt application wiring.
- `build-logic`: convention plugins for Android, Compose, Hilt, Room, and pure Kotlin modules.
- `core:model`: share-ready product models.
- `core:common`: result primitives and common utilities.
- `core:data-contract`: repository interfaces.
- `core:domain`: use cases for dashboard and stats.
- `core:design`: theme, tokens, and visual identity.
- `core:database-room`: Room database, entities, DAOs, and providers.
- `core:remote-contract`: provider-agnostic remote contracts.
- `core:remote-firebase`: Firebase-backed remote data sources.
- `core:data`: offline-first repository implementations.
- `core:sync`: sync repository placeholder for WorkManager-backed sync.
- `feature:*`: API and implementation modules for each screen area.

## Build

```bash
GRADLE_USER_HOME=$PWD/.gradle-user-home ./gradlew assembleDebug
```

The app currently builds successfully with:

- AGP `9.1.0`
- Gradle `9.3.1`
- Kotlin `2.3.20`

## Firebase

Firebase is optional for the current developer flow because the app supports preview mode. When Firebase is not configured, the auth screen will show a banner and let you continue locally.

Full setup steps live in [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md).

## Suggested next commits

1. Add real Firestore sync and outbox replay in `core:remote-firebase`, `core:data`, and `core:sync`.
2. Add unit tests for stats aggregation and repository behavior.
3. Add Firebase Emulator Suite integration tests.
4. Add settings persistence for dynamic color and user preferences.
5. Refine charts and data visualizations on the stats screen.
