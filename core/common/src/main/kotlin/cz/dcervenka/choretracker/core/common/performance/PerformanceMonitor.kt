package cz.dcervenka.choretracker.core.common.performance

import cz.dcervenka.choretracker.core.common.AppResult
import kotlinx.coroutines.CancellationException

enum class PerformanceTraceName(val value: String) {
    STARTUP_ROUTING("startup_routing"),
    HOME_LOAD("home_load"),
    CHORES_LOAD("chores_load"),
    INSIGHTS_LOAD("insights_load"),
    SYNC_UPLOAD("sync_upload"),
    HOUSEHOLD_RESTORE("household_restore"),
}

enum class PerformanceOutcome(val value: String) {
    SUCCESS("success"),
    ERROR("error"),
    CANCELLED("cancelled"),
}

fun interface PerformanceTrace {
    fun stop(outcome: PerformanceOutcome)
}

fun interface PerformanceMonitor {
    fun startTrace(name: PerformanceTraceName): PerformanceTrace
}

object NoOpPerformanceMonitor : PerformanceMonitor {
    private val trace = PerformanceTrace { }

    override fun startTrace(name: PerformanceTraceName): PerformanceTrace = trace
}

suspend fun <T> PerformanceMonitor.measure(
    name: PerformanceTraceName,
    block: suspend () -> AppResult<T>,
): AppResult<T> {
    val trace = startTrace(name)
    var outcome = PerformanceOutcome.ERROR
    return try {
        block().also { result ->
            outcome = if (result is AppResult.Success) PerformanceOutcome.SUCCESS else PerformanceOutcome.ERROR
        }
    } catch (error: CancellationException) {
        outcome = PerformanceOutcome.CANCELLED
        throw error
    } finally {
        trace.stop(outcome)
    }
}
