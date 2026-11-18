package cz.dcervenka.choretracker.core.common.performance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenLoadTraceTest {

    private val monitor = RecordingPerformanceMonitor()
    private val trace = ScreenLoadTrace(monitor, PerformanceTraceName.HOME_LOAD)

    @Test
    fun `ready content is reported once even when the screen later closes`() {
        trace.start()
        trace.ready()
        trace.ready()
        trace.cancel()

        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.SUCCESS)
    }

    @Test
    fun `leaving during loading records cancellation instead of a completed load`() {
        trace.start()
        trace.cancel()
        trace.ready()

        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.CANCELLED)
    }

    @Test
    fun `recomposition does not start another load trace`() {
        trace.start()
        trace.start()

        assertThat(monitor.traces).hasSize(1)
        assertThat(monitor.traces.single().name).isEqualTo(PerformanceTraceName.HOME_LOAD)
        assertThat(monitor.traces.single().outcomes).isEmpty()
    }

    @Test
    fun `a finished load is not restarted by later data updates`() {
        trace.start()
        trace.ready()
        trace.start()

        assertThat(monitor.traces).hasSize(1)
        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.SUCCESS)
    }
}
