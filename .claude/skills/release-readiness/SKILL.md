---
name: release-readiness
description: Phased Google Play release-readiness backlog for ChoreTracker — analytics, R8/signing hardening, i18n, store listing, CI/CD release automation, post-launch polish. Consult and update when doing release-prep work.
---

# Release Readiness Backlog (ordered by priority)

Items needed before (and for) Google Play publication. Some are useful well before launch. Keep this file in sync when completing items (mark done with strikethrough), same as the Feature Backlog in CLAUDE.md.

### Phase 1 — Useful Now (add during active development)

1. ~~**Crashlytics + Analytics**~~ — Done. `FirebaseConventionPlugin` wires both SDKs; collection disabled in DEBUG builds.
2. **Analytics events** — Instrument key user actions with `FirebaseAnalytics.logEvent()`: sign-in/sign-up, household create/join, chore logged, chore added/edited/deleted, member invited/removed. Gives visibility into real usage before launch.
3. **Release build hardening** — Enable `isMinifyEnabled = true`, `isShrinkResources = true`, `isDebuggable = false` for release. Add R8 keep rules for Room, Firebase, Hilt, Kotlin Serialization. Test release build regularly.
4. **ProGuard/R8 rules** — Current `proguard-rules.pro` is empty template. Need keep rules for: Room entities, Firebase model classes, Kotlin serialization, Hilt generated code. Test with `./gradlew assembleRelease` and verify no runtime crashes.
5. **Signing config** — Generate upload keystore (`.jks`), configure `signingConfigs` in `app/build.gradle.kts`, store passwords in `local.properties` (gitignored). Google Play requires consistent signing.
6. **Version bumping strategy** — Current `version.properties` (0.1.0) works. Define: patch = bugfix, minor = feature, major = breaking. Consider a Gradle task or script to bump + tag.

### Phase 2 — Pre-Launch Essentials

1. **Localization (i18n)** — Extract all hardcoded strings from `core/design` strings.xml into proper keys. Add `values-cs/` (Czech) as primary second language. Use Android Studio translation editor or Crowdin. All user-facing strings must go through `stringResource()`.
2. **Privacy policy + Terms** — Required by Google Play. Host on a simple webpage (GitHub Pages works). Link from app settings screen and Play Store listing.
3. **App icon + adaptive icon** — Production-quality launcher icon with foreground/background layers for adaptive icon support. Current icon may be template.
4. **Google Play Store listing assets** — Screenshots (phone + tablet if supported), feature graphic (1024x500), short/full descriptions, categorization.
5. **`google-services.json` security** — Currently committed to git. Move to gitignored location, provide via CI secret or local-only path. Not a hard blocker (Firebase keys are meant to be in APK) but best practice.

### Phase 3 — CI/CD & Release Automation

1. **Release CI workflow** — GitHub Actions workflow: on tag push, build signed release APK/AAB, run tests, upload artifact. Extend existing `android.yml`.
2. **Fastlane or Gradle Play Publisher** — Automate AAB upload to Google Play internal/beta/production tracks. Manage changelogs, screenshots, metadata in repo.
3. **Auto version bump on release** — Tag-triggered version increment. Could read `version.properties`, bump, commit, push.

### Phase 4 — Post-Launch Polish

1. **In-app update (Play Core)** — Prompt users to update when new version available. `com.google.android.play:app-update`.
2. **Firebase Performance Monitoring** — Track screen load times, network latency, slow frames. Useful once real users exist.
3. **Remote Config** — Feature flags for gradual rollouts. Useful at scale.
4. **ANR & vitals monitoring** — Play Console vitals + Crashlytics ANR tracking. Comes mostly free with Crashlytics.
5. **Accessibility audit** — Content descriptions, touch targets (48dp min), screen reader support, contrast ratios. Required for good Play Store rating.
6. **Tablet / foldable support** — Adaptive layouts for larger screens. Google Play flags apps that don't handle tablets well.
7. **Data export / account deletion** — Google Play policy requires account deletion option if app has accounts. Add "Delete my account" in settings.
8. ~~**Chore reminders**~~ — Done. See Feature Backlog item 7 in CLAUDE.md (implemented together as one feature).
