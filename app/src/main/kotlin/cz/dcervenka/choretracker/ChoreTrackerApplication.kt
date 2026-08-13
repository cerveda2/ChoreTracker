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
import dagger.Lazy
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

    // dagger.Lazy, not an eagerly-injected field like fcmTokenRegistrar above: constructing
    // ChoreReminderScheduler resolves a WorkManager (see RemindersModule), which triggers
    // WorkManager's on-demand init - that reads workManagerConfiguration below, which needs
    // hiltWorkerFactory already set. Eager field injection races Hilt's member-injection order
    // (fields are injected in declaration order) against hiltWorkerFactory's own injection and
    // crashes on startup with "lateinit property hiltWorkerFactory has not been initialized".
    // get() is called explicitly in onCreate() below, once every field on this class is
    // guaranteed already injected. To fully remove chore reminders, delete this field,
    // hiltWorkerFactory below (and the Configuration.Provider override), the
    // ReminderNotificationChannels.ensureCreated() call, the WorkManager <provider> override in
    // AndroidManifest.xml, and core/reminders itself.
    @Inject
    lateinit var choreReminderScheduler: Lazy<ChoreReminderScheduler>

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
        choreReminderScheduler.get()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
