package cz.dcervenka.choretracker.core.common.performance

class ScreenLoadTrace(
    private val monitor: PerformanceMonitor,
    private val name: PerformanceTraceName,
) {
    private var trace: PerformanceTrace? = null
    private var finished = false

    fun start(isLoading: Boolean) {
        if (trace == null && !finished) {
            if (isLoading) trace = monitor.startTrace(name) else finished = true
        }
    }

    fun ready() = finish(PerformanceOutcome.SUCCESS)

    fun cancel() = finish(PerformanceOutcome.CANCELLED)

    private fun finish(outcome: PerformanceOutcome) {
        if (!finished) {
            finished = true
            trace?.stop(outcome)
            trace = null
        }
    }
}
