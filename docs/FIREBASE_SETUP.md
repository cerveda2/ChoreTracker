# Firebase Setup

The project already contains the Firebase-facing abstraction layer. To enable real Firebase auth and later Firestore sync, complete the steps below.

## 1. Create a Firebase project

1. Open the Firebase console.
2. Create a new project.
3. Add an Android app with package name `cz.dcervenka.choretracker`.

## 2. Add `google-services.json`

1. Download `google-services.json` from the Firebase console.
2. Place it at:

```text
app/google-services.json
```

The app currently builds without this file because preview mode is supported. Adding this file enables real Firebase configuration at runtime.

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

The current app computes dashboard and statistics locally from Room. Firestore should store raw operational data, not precomputed aggregates.

## 5. Security rules

The repo includes a starter [`firestore.rules`](../firestore.rules). Publish those rules from the Firebase console or with the Firebase CLI.

Start with strict rules:

- authenticated users only
- users can read and write only households where they are active members
- `users/{uid}` readable and writable only by the matching user
- invite redemption constrained to the household invite documents you expose

## 6. Emulator-first development

Use the Firebase Emulator Suite for local development once the real sync implementation is added:

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
- For a physical device, replace the host with your machine's LAN IP.
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

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/auth/android/start
- https://firebase.google.com/docs/firestore/quickstart
- https://firebase.google.com/docs/firestore/manage-data/enable-offline
