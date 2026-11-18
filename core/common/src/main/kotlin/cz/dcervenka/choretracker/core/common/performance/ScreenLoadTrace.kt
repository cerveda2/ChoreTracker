package cz.dcervenka.choretracker.core.common.performance

class ScreenLoadTrace(
    private val monitor: PerformanceMonitor,
    private val name: PerformanceTraceName,
) {
    private var trace: PerformanceTrace? = null
    private var finished = false

    fun start() {
        if (trace == null && !finished) {
            trace = monitor.startTrace(name)
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
