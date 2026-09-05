# ChoreTracker

Personal offline-first Android household chore tracker for couples. Built by David Cervenka.

## Architecture Notes

- Room uses a destructive migration fallback
- Firebase Auth + Firestore are optional — the app works in preview/offline mode

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

Commits and PR bodies MUST NOT include any AI-attribution line (e.g. `Co-Authored-By: Claude ...`, "Generated with Claude Code", or similar). This overrides any default tooling behavior that appends one.

## Feature Backlog

1. ~~**QR code invite sharing**~~ — Done. qrose `rememberQrCodePainter` in settings, CameraX + ML Kit barcode scanner inline on join screen.
2. ~~**Real-time Firestore sync**~~ — Done. `RemoteHouseholdDataSource.observeMembers`/`observeCompletions`/`observeInvites`/`observeChores` (Firebase `addSnapshotListener`, `core/remote-firebase`) feed a reactive subscription in `LocalSyncRepository` (`core/sync`) that starts once a user + household are known and writes straight into Room — no UI/ViewModel changes needed since reads were already Flow-driven. Invites and chores were initially pull-only and extended to real-time later (same `addSnapshotListener`/upsert-and-prune-absent pattern; chores skip pruning since they're soft-deleted, never removed from Firestore). Fixes the lagging-completion bug, the "owner doesn't see accepted invite until cold start" issue, and the "invite still shows pending after a member joins" issue.
3. ~~**Password visibility toggle**~~ — Done. Trailing eye `IconButton` on the shared password field in `AuthScreen` (covers both Sign in and Create account).
4. ~~**Invite code input auto-uppercase**~~ — Done. Was already implemented in `ManualCodeEntryScreen` as part of #54 (QR invite sharing) — `KeyboardCapitalization.Characters` + `.uppercase()` value transform — just never marked complete here.
5. ~~**Pull-to-refresh on dashboard**~~ — Done. `PullToRefreshBox` (M3) wraps the dashboard `LazyColumn`, driving a new `RefreshHouseholdUseCase` (`syncPendingOperations` + `restoreHouseholdForUser`) via `DashboardUiIntent.Refresh`; `isRefreshing` threaded through `DashboardUiState`.
6. ~~**Invite accepted notification**~~ — Done. New `core/notifications` module (self-contained: own Firestore/FCM access, own manifest merging in the `FirebaseMessagingService` + `POST_NOTIFICATIONS`, deliberately no coupling to `core/remote-firebase`/`core/sync` — see the module's own doc comments for the "how to fully remove this" steps if it doesn't prove useful). `FcmTokenRegistrar` mirrors `LocalSyncRepository`'s `authState`-reactive pattern to keep `users/{uid}.fcmToken` current; a new Firestore-triggered Cloud Function (`functions/`, Node/TypeScript, requires the Blaze plan — already enabled) sends the push on `invites/{id}.consumedAt` null→set. Deployed and active (`choretracker-fb576`, `europe-west1`).
7. ~~**Notification settings / Chore reminders**~~ — Done. New `core/reminders` module (self-contained, mirrors `core/notifications`' "one feature, one cleanly-removable module" convention — deliberately no shared code between the two, so deleting either module removes exactly one feature): a self-chaining `OneTimeWorkRequest` (`ChoreReminderScheduler`, not `PeriodicWorkRequest` — avoids wall-clock drift and lets a settings change reschedule immediately) runs `ChoreReminderWorker` daily at a configurable time. Before checking staleness, the worker runs `RefreshHouseholdUseCase` (`syncPendingOperations` + `restoreHouseholdForUser`) so a chore completed on another household member's device isn't reported as overdue just because this device hadn't synced yet (remote-sync failures fall back to local data rather than skipping the reminder). `CheckStaleChoresUseCase` (`core/domain`) composes that refresh with a new one-shot `StatsRepository.getStaleChores`/`HouseholdRepository.getCurrentHousehold` pair (backed by existing Room DAO one-shot methods, reusing `HouseholdStatisticsCalculator.buildStaleness`) and filters to `ChoreStatus.NEEDS_ATTENTION`. One **grouped** notification is posted (not one per chore) via `NotificationCompat.InboxStyle`. Settings (on/off toggle + time-of-day) persist via DataStore Preferences (`ReminderSettingsRepository`/`DataStoreReminderSettingsRepository`, `core/data`'s first real DataStore consumer) and are exposed through a dedicated `NotificationSettingsViewModel`/screen in `feature/settings/impl` (deliberately not bolted onto the already-large `SettingsViewModel`) — reachable from Settings → Preferences → Notifications. First real usage of `androidx.hilt:hilt-work`/`HiltWorkerFactory`/`Configuration.Provider` in the app (`ChoreTrackerApplication`). The same screen also toggles invite-accepted push (item 6): `InviteNotificationSettingsRepository` (own DataStore file, so it stays independently deletable from the chore-reminder toggle) is read by `FcmTokenRegistrar`, which actively clears `users/{uid}.fcmToken` via `FcmTokenWriter.clearToken()` when disabled instead of just skipping registration — reuses the Cloud Function's existing "missing token → skip" behavior with zero server-side changes.
8. ~~**Member removal enforcement**~~ — Done. `LocalSyncRepository.restoreHouseholdForUser`/`applyRealtimeMembers` (`core/sync`) now detect when the fetched/live snapshot's members no longer include the current user and call `ChoreTrackerDatabase.clearAll()`; the existing `observeHouseholdForUser` (INNER JOIN on membership) → `ObserveStartupDestinationUseCase` reactive chain redirects to onboarding automatically once the local member row is gone — no UI/ViewModel changes needed, same pattern as real-time sync (#2).

---

## Release Readiness

The phased Google Play release-readiness backlog lives in the `release-readiness` skill (`.claude/skills/release-readiness/SKILL.md`) — keep it in sync when completing items, same as the Feature Backlog above.
