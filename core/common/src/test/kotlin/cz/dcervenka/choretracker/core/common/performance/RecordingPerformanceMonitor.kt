package cz.dcervenka.choretracker.core.common.performance

internal class RecordingPerformanceMonitor : PerformanceMonitor {
    val traces = mutableListOf<RecordedTrace>()

    override fun startTrace(name: PerformanceTraceName): PerformanceTrace =
        RecordedTrace(name).also(traces::add)
}

internal class RecordedTrace(val name: PerformanceTraceName) : PerformanceTrace {
    val outcomes = mutableListOf<PerformanceOutcome>()

    override fun stop(outcome: PerformanceOutcome) {
        outcomes += outcome
    }
}
