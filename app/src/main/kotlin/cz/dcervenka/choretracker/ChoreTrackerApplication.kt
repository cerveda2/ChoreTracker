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
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import timber.log.Timber
import javax.inject.Inject

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkerFactoryEntryPoint {
    fun hiltWorkerFactory(): HiltWorkerFactory
}

@HiltAndroidApp
class ChoreTrackerApplication : Application(), Configuration.Provider {

    // Hilt member-injects this before onCreate()'s body runs, which is what actually starts the
    // "invite accepted" push feature - see core/notifications. To fully remove the feature,
    // delete this field, the NotificationChannels.ensureCreated() call below, and both imports.
    @Inject
    lateinit var fcmTokenRegistrar: FcmTokenRegistrar

    // dagger.Lazy, not an eagerly-injected field like fcmTokenRegistrar above: constructing
    // ChoreReminderScheduler resolves a WorkManager (see RemindersModule), which triggers
    // WorkManager's on-demand init. get() is called explicitly at the end of onCreate() below
    // so reminder scheduling starts only after the notification channel it posts into exists.
    // To fully remove chore reminders, delete this field, the
    // ReminderNotificationChannels.ensureCreated() call, the WorkManager <provider> override in
    // AndroidManifest.xml, and core/reminders itself.
    @Inject
    lateinit var choreReminderScheduler: Lazy<ChoreReminderScheduler>

    // Resolved via EntryPoint rather than a member-injected lateinit field: WorkManager's
    // on-demand init can read workManagerConfiguration at any point - including before Hilt
    // finishes field-injecting this class - so it must not depend on Hilt's field-injection
    // order. A lateinit var here previously crashed with "hiltWorkerFactory has not been
    // initialized" whenever some other field's construction (e.g. choreReminderScheduler,
    // before it became Lazy) triggered WorkManager init ahead of its turn in declaration order.
    // EntryPointAccessors resolves straight from the already-ready SingletonComponent instead,
    // so no future field ordering can reintroduce that crash.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(
                EntryPointAccessors.fromApplication(this, WorkerFactoryEntryPoint::class.java).hiltWorkerFactory(),
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        val isRelease = !BuildConfig.DEBUG
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = isRelease
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isRelease)
        NotificationChannels.ensureCreated(this)
        ReminderNotificationChannels.ensureCreated(this)
        choreReminderScheduler.get()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
