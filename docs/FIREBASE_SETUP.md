# Firebase Setup

The app uses Firebase Authentication, Firestore sync, Cloud Functions, Cloud Messaging and
Crashlytics. Android client configuration stays local or is supplied by CI.

## 1. Create a Firebase project

ChoreTracker has separate development and production projects:

| Build | Firebase project | Android package | Local file |
| --- | --- | --- | --- |
| Debug | `choretracker-dev-1faf5` | `cz.dcervenka.choretracker.debug` | `app/src/debug/google-services.json` |
| Release | `choretracker-fb576` | `cz.dcervenka.choretracker` | `app/src/release/google-services.json` |

For a fork, create your own Firebase projects and register the matching Android packages.

## 2. Add `google-services.json`

Download each app's `google-services.json` from its Firebase project settings and put it in the
matching variant path above. Both paths are gitignored. The Google Services Gradle plugin selects
the matching build-type file; the app module's root `app/google-services.json` is no longer used.

When migrating an existing checkout, save copies of both tracked files outside the checkout before
pulling the change that removes them. Restore the debug copy to its original path and the old
`app/google-services.json` production copy to `app/src/release/google-services.json` afterward.

The Gradle plugin requires configuration for every variant being built, even when you intend to
use the app's preview mode. For a fresh checkout without real configuration, generate synthetic
demo clients for both variants:

```bash
python3 .github/scripts/firebase_config.py --allow-demo
./gradlew assembleDebug
```

This uses `demo-choretracker-ci` when the configuration environment variables are unset. It builds
without accessing a real project's data; use the app's preview flow or the Auth/Firestore emulators
for runtime checks. The script refuses to overwrite existing local files. Preserve or remove those
copies deliberately before regenerating them.

Firebase client identifiers are public and remain in the APK. Moving the JSON out of source control
keeps project configuration separate from source code; access control comes from Authentication,
Firestore rules and Firebase/Google Cloud permissions. Service-account credentials are separate
from these Android client files.

### CI provisioning

The repository's `FIREBASE_ANDROID_DEV_JSON` and `FIREBASE_ANDROID_PROD_JSON` Actions secrets contain
the respective complete client JSON files. The Android validation jobs provision both variant files
before Gradle runs. Fork and Dependabot PRs without secrets use the synthetic demo configuration.
A supplied configuration must match the expected project and package; malformed inputs fail instead
of falling back to demo.

Signed releases forward `FIREBASE_ANDROID_PROD_JSON` through `android.yml` → `release.yml` →
`_release.yml` (and from the manual Release entry point). The build writes only the release file
and fails before signing when the secret is missing or targets a different project/package. It
never enables the demo fallback. See [CI and releases](CI.md).

## 3. Enable authentication

In the Firebase console:

1. Open `Build > Authentication`.
2. Enable `Email/Password`.
3. Leave `Email link (passwordless sign-in)` disabled for now. The app currently implements classic email + password only.
4. Leave Google sign-in for later unless you also want to configure Credential Manager and SHA certificates.

## 4. Create Firestore

1. Open `Build > Firestore Database`.
2. Create a database in Native mode.
3. For Europe-based usage, prefer `eur3` unless you explicitly want a cheaper single-region deployment.
4. Start in `production mode`.

Recommended top-level structure:

```text
users/{uid}
households/{householdId}
households/{householdId}/members/{memberId}
households/{householdId}/chores/{choreId}
households/{householdId}/completions/{completionId}
households/{householdId}/invites/{inviteId}
```

The app also stores chore requests in `households/{householdId}/requests`. Dashboard and statistics
are computed locally from Room; Firestore stores the underlying household data.

## 5. Security rules

The repo includes [`firestore.rules`](../firestore.rules). Use the documented CI deployment flow
to publish rules and functions together.

Start with strict rules:

- authenticated users only
- users can read and write only households where they are active members
- `users/{uid}` readable and writable only by the matching user
- invite redemption constrained to the household invite documents you expose

## 6. Emulator-first development

Use the Firebase Emulator Suite for local development:

- Authentication emulator
- Firestore emulator

This project already supports emulator hosts through Gradle properties in [`gradle.properties`](../gradle.properties):

```properties
choretracker.firebase.useEmulators=false
choretracker.firebase.authEmulatorHost=10.0.2.2
choretracker.firebase.authEmulatorPort=9099
choretracker.firebase.firestoreEmulatorHost=10.0.2.2
choretracker.firebase.firestoreEmulatorPort=8080
```

To use local emulators, set:

```properties
choretracker.firebase.useEmulators=true
```

Notes:

- `10.0.2.2` is correct for the Android emulator.
- For a physical device, replace the host with your machine's LAN IP and add it to [`network_security_config.xml`](../app/src/debug/res/xml/network_security_config.xml), which only permits cleartext for `10.0.2.2`, `localhost` and `127.0.0.1`.
- Firebase Auth and Firestore emulator support is configured in `core:remote-firebase`.

## 7. Verify in app

After the file is added and auth is enabled:

1. Launch the app.
2. Use the Auth screen with email/password.
3. Create a household.
4. Confirm the app no longer shows the Firebase configuration warning.

## 8. Optional: SHA fingerprints

You do not need SHA fingerprints for the current v1 email/password flow.

Add SHA-1 and SHA-256 later if you enable:

- Google sign-in
- phone auth
- App Check providers that require them

If you decide to add Google sign-in later, add both debug and release fingerprints.

## Official references

- [Android setup](https://firebase.google.com/docs/android/setup)
- [Build-type configuration files](https://firebase.google.com/docs/android/google-services-plugin-and-file)
- [Firebase client configuration and API keys](https://firebase.google.com/support/guides/security-checklist)
- [Authentication](https://firebase.google.com/docs/auth/android/start)
- [Firestore](https://firebase.google.com/docs/firestore/quickstart)
