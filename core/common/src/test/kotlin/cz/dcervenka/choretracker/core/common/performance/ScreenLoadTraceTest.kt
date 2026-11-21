package cz.dcervenka.choretracker.core.common.performance

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScreenLoadTraceTest {

    private val monitor = RecordingPerformanceMonitor()
    private val trace = ScreenLoadTrace(monitor, PerformanceTraceName.HOME_LOAD)

    @Test
    fun `ready content is reported once even when the screen later closes`() {
        trace.start(isLoading = true)
        trace.ready()
        trace.ready()
        trace.cancel()

        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.SUCCESS)
    }

    @Test
    fun `leaving during loading records cancellation instead of a completed load`() {
        trace.start(isLoading = true)
        trace.cancel()
        trace.ready()

        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.CANCELLED)
    }

    @Test
    fun `recomposition does not start another load trace`() {
        trace.start(isLoading = true)
        trace.start(isLoading = true)

        assertThat(monitor.traces).hasSize(1)
        assertThat(monitor.traces.single().name).isEqualTo(PerformanceTraceName.HOME_LOAD)
        assertThat(monitor.traces.single().outcomes).isEmpty()
    }

    @Test
    fun `a finished load is not restarted by later data updates`() {
        trace.start(isLoading = true)
        trace.ready()
        trace.start(isLoading = true)

        assertThat(monitor.traces).hasSize(1)
        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.SUCCESS)
    }

    @Test
    fun `a screen that is already loaded when it appears records no trace`() {
        trace.start(isLoading = false)
        trace.ready()
        trace.start(isLoading = true)
        trace.cancel()

        assertThat(monitor.traces).isEmpty()
    }
}
