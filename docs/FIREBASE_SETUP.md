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
3. Leave Google sign-in for later unless you also want to configure Credential Manager and SHA certificates.

## 4. Create Firestore

1. Open `Build > Firestore Database`.
2. Create a database in Native mode.
3. Pick a region close to your users.

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

Start with strict rules:

- authenticated users only
- users can read and write only households where they are active members
- `users/{uid}` readable and writable only by the matching user
- invite redemption constrained to the household invite documents you expose

## 6. Emulator-first development

Use the Firebase Emulator Suite for local development once the real sync implementation is added:

- Authentication emulator
- Firestore emulator

That will let you test auth, onboarding, invite join, and sync flows without touching production data.

## 7. Verify in app

After the file is added and auth is enabled:

1. Launch the app.
2. Use the Auth screen with email/password.
3. Create a household.
4. Confirm the app no longer shows the Firebase configuration warning.

## Official references

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/auth/android/start
- https://firebase.google.com/docs/firestore/quickstart
- https://firebase.google.com/docs/firestore/manage-data/enable-offline
