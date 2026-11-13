# Play Console: App content answers

Answers for Play Console → **Policy and programs → App content**, matching the app as of v0.3.0.
Keep this in sync with the privacy policy (`site/privacy/`) whenever data collection changes.

## Privacy policy

`https://cerveda2.github.io/ChoreTracker/privacy/`

## Ads

**No**, the app doesn't contain ads.

## App access

**All or some functionality is restricted**: the app needs a sign-in. Add one set of instructions:

- Name: `Reviewer account`
- Username / password: a dedicated account you create in the **production** app for Google's
  reviewers. Give it a household with a few chores so every screen has content.
- Any other information: "Sign in with the email and password above. A demo without an account is
  also available from the sign-in screen via 'Continue in preview mode'."

## Advertising ID

**No.** Google Analytics was removed in v0.1.2, so the release manifest has no `AD_ID` permission.

## Content rating

Category: **All other app types** (a utility). Answer **No** to everything except:

- **Users can interact or exchange content**: **Yes**. Members of one household see each
  other's chores, completions and notes. It's invite-only, with no public sharing or chat.

There are no purchases, no location sharing, and no unrestricted internet access.

## Target audience and content

- Target age: **18 and over**
- Appeals to children: **No**

## News, government, financial features, health

All **No** / **None**.

## Data safety

### Data collection and security

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes**. Firebase uses HTTPS. |
| Which of the following methods of account creation does your app support? | **Username and password**. Email and password through Firebase Authentication. |
| Delete account URL | `https://cerveda2.github.io/ChoreTracker/delete-account/` |
| Do you provide a way for users to request that some or all of their data is deleted, without requiring them to delete their account? | **No**. Deletion is per account, in the app or by email. |

### Data types

Every type below is **Collected**, **not Shared**, **not processed ephemerally**, and **Required**,
except **Other in-app messages**, which is **Optional**: users can use the app without sending chore requests.
Data that only goes to Google as a service provider (Firebase) doesn't count as "shared" under
Play's definition.

| Category → data type | Purposes | What it is in this app |
|---|---|---|
| Personal info → **Name** | App functionality, Account management | The display name, shown to household members. |
| Personal info → **Email address** | App functionality, Account management | The sign-in email, also stored on the member record so household members can see it. |
| Personal info → **User IDs** | App functionality, Account management | The Firebase Authentication user ID and household member IDs. |
| App activity → **Other user-generated content** | App functionality | Household name, chores, completions (who, when and optional completion notes), invite codes. |
| Messages → **Other in-app messages** | App functionality | Chore requests addressed to another household member, including the requested chores and optional request notes. Collection is optional. |
| App info and performance → **Crash logs** | Analytics | Crashlytics stack traces. |
| App info and performance → **Diagnostics** | Analytics | The device model, Android version and app state sent with a crash report. |
| Device or other IDs → **Device or other IDs** | App functionality, Analytics | Firebase installation ID, Crashlytics installation UUID, and the push notification (FCM) token. |

### Notification data flow

Invite pushes can include the joining member's display name and go to the eligible invite creator
(otherwise the owner). Request pushes carry the sender's display name, chore names, optional note,
household ID and request type through Firebase Cloud Messaging. Both are app functionality, not
developer communications or marketing. Android may show this content on the lock screen.

The invite/request toggles update `users/{uid}.pushTypes`; they retain `fcmToken`, even when both
types are off. Changes take effect for new pushes after the preference syncs. Sign-out attempts to
clear the token before authentication ends; account deletion removes the user record. Daily
reminders and their time/mute preferences stay on the device, although the worker refreshes the
shared household data before choosing reminders.

The Messages classification follows Play's broad definition of [other in-app messages](https://support.google.com/googleplay/android-developer/answer/10787469).
Update the live Data safety form to include this optional category when this documentation is adopted.

### Not collected

Location, financial info, health and fitness, email/SMS/MMS messages, photos and videos, audio, files and docs,
calendar, contacts, app interactions, in-app search history, installed apps, other actions, web
browsing.

- **Photos and videos:** the camera is used only to scan invite QR codes. Frames are processed on
  the device and never leave it, so this isn't "collected".
- **Location:** Firebase Authentication and Cloud Functions see the caller's IP address to run the
  request and to prevent abuse, but the app doesn't derive or store a location from it.
