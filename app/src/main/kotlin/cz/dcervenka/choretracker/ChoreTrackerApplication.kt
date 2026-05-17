package cz.dcervenka.choretracker

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class ChoreTrackerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // TODO - enable crashlytics and analytics only for release builds
        /*val isRelease = !BuildConfig.DEBUG
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(isRelease)
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(isRelease)*/
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(true)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
