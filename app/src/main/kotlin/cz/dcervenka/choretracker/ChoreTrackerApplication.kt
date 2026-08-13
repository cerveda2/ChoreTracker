package cz.dcervenka.choretracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import cz.dcervenka.choretracker.core.notifications.repository.FcmTokenRegistrar
import cz.dcervenka.choretracker.core.notifications.service.NotificationChannels
import cz.dcervenka.choretracker.core.reminders.notification.ReminderNotificationChannels
import cz.dcervenka.choretracker.core.reminders.scheduler.ChoreReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class ChoreTrackerApplication : Application(), Configuration.Provider {

    // Hilt member-injects this before onCreate()'s body runs, which is what actually starts the
    // "invite accepted" push feature - see core/notifications. To fully remove the feature,
    // delete this field, the NotificationChannels.ensureCreated() call below, and both imports.
    @Inject
    lateinit var fcmTokenRegistrar: FcmTokenRegistrar

    // Same pattern as fcmTokenRegistrar above - force-starts the reactive scheduling loop in
    // core/reminders. To fully remove chore reminders, delete this field, hiltWorkerFactory below
    // (and the Configuration.Provider override), the ReminderNotificationChannels.ensureCreated()
    // call, the WorkManager <provider> override in AndroidManifest.xml, and core/reminders itself.
    @Inject
    lateinit var choreReminderScheduler: ChoreReminderScheduler

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(hiltWorkerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // TODO - enable crashlytics and analytics only for release builds
        /*val isRelease = !BuildConfig.DEBUG
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isRelease)
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isRelease)*/
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
        NotificationChannels.ensureCreated(this)
        ReminderNotificationChannels.ensureCreated(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
