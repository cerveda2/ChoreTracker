package cz.dcervenka.choretracker

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import cz.dcervenka.choretracker.core.notifications.repository.FcmTokenRegistrar
import cz.dcervenka.choretracker.core.notifications.service.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ChoreTrackerApplication : Application() {

    // Hilt member-injects this before onCreate()'s body runs, which is what actually starts the
    // "invite accepted" push feature - see core/notifications. To fully remove the feature,
    // delete this field, the NotificationChannels.ensureCreated() call below, and both imports.
    @Inject
    lateinit var fcmTokenRegistrar: FcmTokenRegistrar

    override fun onCreate() {
        super.onCreate()
        // TODO - enable crashlytics and analytics only for release builds
        /*val isRelease = !BuildConfig.DEBUG
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isRelease)
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isRelease)*/
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
        NotificationChannels.ensureCreated(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
