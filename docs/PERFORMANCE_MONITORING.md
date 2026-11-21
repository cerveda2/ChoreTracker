# Performance monitoring

Release builds use Firebase Performance Monitoring without adding Google Analytics. The existing Firebase convention plugin applies the Performance plugin and imports the SDK through the existing Firebase BoM. Debug builds disable both bytecode instrumentation and SDK collection; the manifest's `firebase_performance_collection_deactivated=true` cannot be overridden at runtime. Preview rendering uses a no-op monitor, and custom traces also do nothing when Firebase is unconfigured or collection is disabled.

## Metrics

Firebase supplies automatic app startup/lifecycle timings, supported HTTP/S request metrics, and activity/fragment rendering metrics. This app uses one Compose activity, so automatic slow/frozen-frame metrics describe `MainActivity`, rather than individual Compose tabs. The SDK's rendering thresholds assume 60 Hz.

Custom traces add the following measurements:

| Trace | Measurement |
| --- | --- |
| `startup_routing` | Initial root composition until the startup destination is ready at the next frame. |
| `home_load` | Home composition until its dashboard snapshot is ready at the next frame. |
| `chores_load` | Chores composition until its household state is ready at the next frame. |
| `insights_load` | Insights composition until its statistics snapshot is ready at the next frame. |
| `sync_upload` | Draining a non-empty local sync queue, after acquiring the sync lock. |
| `household_restore` | Fetching and applying a household snapshot. |

Each trace has one fixed `outcome` attribute: `success`, `error`, or `cancelled`. Screen-load traces finish once per composition; recomposition and later data updates do not restart them. A screen that is already loaded when it appears, such as a tab you return to or a recreated activity, records no trace. Leaving or backgrounding a screen before it is ready records cancellation. Filter to successful outcomes when comparing load times.

Sync traces include client processing and Room writes as well as network time. They are not HTTP traces. Firestore uses gRPC, which is outside the SDK's documented automatic HTTP instrumentation support; use the custom sync timings to investigate household transfers. Firebase's automatic HTTP metrics cover supported networking stacks only.

## Collection boundaries

The custom API accepts an enum of fixed trace names, with no arbitrary attributes or user identifiers. Household/member IDs, names, chore text, notes, emails and invite codes are not passed to it. The Firebase adapter handles unavailable monitoring without changing the underlying operation's result or cancellation.

The SDK itself collects diagnostic/device metadata, installation/session identifiers, and IP-derived country. Automatic network traces include URLs without query parameters or payload contents, alongside timing, size and status information. Remote Config is included by the SDK to regulate reporting; this does not implement the separate feature-flag backlog item.

The privacy policy and Play Console answers are updated in this change. Publishing the policy and updating the live Data safety form remain release steps; see [app content](play-store/app-content.md).

## Verification

The common-module tests exercise completion, returned errors, exceptions, coroutine cancellation, suspended operations and screen-load lifecycle behavior with a recording monitor. The affected sync/presentation tests run without sending metrics. Debug and minified release builds validate the instrumentation integration.

For a delivery check, install a configured release build on a test device, open Home/Chores/Insights, log a chore, and refresh the household. Check the production project's Firebase Performance dashboard for the automatic metrics and fixed custom traces. For local troubleshooting, temporarily enable `firebase_performance_logcat_enabled` in the application manifest and inspect `adb logcat -s FirebasePerformance`; keep that diagnostic flag out of the shipped manifest. Console arrival is subject to Firebase batching and sampling, so a local build alone cannot verify delivery.

References: [Android setup](https://firebase.google.com/docs/perf-mon/get-started-android), [disable collection/instrumentation](https://firebase.google.com/docs/perf-mon/disable-sdk?platform=android), [rendering metrics](https://firebase.google.com/docs/perf-mon/screen-traces?platform=android), [HTTP support](https://firebase.google.com/docs/perf-mon/network-traces?platform=android), and [data collection and retention](https://firebase.google.com/support/privacy).
