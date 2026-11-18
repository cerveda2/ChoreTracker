package cz.dcervenka.choretracker.core.performance

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.perf.FirebasePerformance
import cz.dcervenka.choretracker.core.common.performance.NoOpPerformanceMonitor
import cz.dcervenka.choretracker.core.common.performance.PerformanceMonitor
import cz.dcervenka.choretracker.core.common.performance.PerformanceTrace
import cz.dcervenka.choretracker.core.common.performance.PerformanceTraceName
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebasePerformanceMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PerformanceMonitor {

    override fun startTrace(name: PerformanceTraceName): PerformanceTrace = runCatching {
        if (FirebaseApp.getApps(context).isEmpty()) {
            return@runCatching NoOpPerformanceMonitor.startTrace(name)
        }
        val performance = FirebasePerformance.getInstance()
        if (!performance.isPerformanceCollectionEnabled) {
            return@runCatching NoOpPerformanceMonitor.startTrace(name)
        }
        val trace = performance.newTrace(name.value)
        trace.start()
        val stopped = AtomicBoolean(false)
        PerformanceTrace { outcome ->
            if (stopped.compareAndSet(false, true)) {
                runCatching {
                    trace.putAttribute("outcome", outcome.value)
                    trace.stop()
                }.onFailure { error -> Timber.d(error, "Could not finish performance trace") }
            }
        }
    }.getOrElse { error ->
        Timber.d(error, "Performance monitoring unavailable")
        NoOpPerformanceMonitor.startTrace(name)
    }
}
