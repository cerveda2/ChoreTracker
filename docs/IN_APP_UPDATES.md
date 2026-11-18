# In-app updates

Release builds use Google Play's flexible update flow. The app checks when a signed-in user returns to a main tab, after the notification permission prompt has settled. Debug builds skip the feature.

Google Play asks for consent before downloading. Declining or cancelling leaves the app usable, and the same version is offered at most once per app session. The offered version survives activity recreation through saved state. An immediate-only update is skipped.

Downloads continue while the app is in use. Once an update is downloaded, a dismissible snackbar offers **Restart**. Installation starts only when the user taps it. Returning to a main tab checks for updates that finished while the app was closed or in the background. A failed install keeps the restart action available for another attempt; an unavailable Play service does not interrupt normal use.

The implementation lives in `core/updates`; `ChoreTrackerRoot` hosts its prompt and snackbar. It has no dependency on household data or Firebase.

## Automated checks

```bash
./gradlew :core:updates:testDebugUnitTest :core:updates:detekt :core:updates:lintDebug
```

The tests use Google's `FakeAppUpdateManager` with Robolectric. They cover optional consent, dismissal across recreation, newer versions, active downloads, downloaded-update recovery, installation retry, cancelled and failed downloads, unavailable Play services, and listener cleanup. The fake never contacts Play or installs an update.

## Play smoke test

End-to-end delivery needs two versions distributed through Play; a local debug APK cannot exercise it. Follow [Google's in-app update testing guide](https://developer.android.com/guide/playcore/in-app-updates/test):

1. Install a release build containing this feature through an internal app sharing URL.
2. Upload another build with the same application ID and signing key and a higher version code to internal app sharing.
3. Open the newer build's sharing link on the device, then leave its Play page without installing it.
4. Open the older app and enter a main tab. Verify that declining the update does not cause another prompt when switching away and back.
5. Relaunch, accept the update, and verify that chore logging remains usable during download.
6. When **Restart** appears, dismiss it once, background the app, and return. Confirm the action is offered again and installs the newer build when selected.

Use an account that has acquired the app through Google Play. Test both English and Czech and repeat the downloaded-update case after process recreation. This Play smoke test remains a release-time check; automated tests verify the app's behavior with the official fake.
