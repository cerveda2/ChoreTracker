package cz.dcervenka.choretracker.core.common.performance

import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.model.error.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PerformanceMonitorTest {

    private val monitor = RecordingPerformanceMonitor()

    @Test
    fun `successful operations keep their result`() = runTest {
        val expected = AppResult.Success("result")

        val actual = monitor.measure(PerformanceTraceName.SYNC_UPLOAD) { expected }

        assertThat(actual).isSameInstanceAs(expected)
        assertThat(monitor.traces.single().name).isEqualTo(PerformanceTraceName.SYNC_UPLOAD)
        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.SUCCESS)
    }

    @Test
    fun `returned failures keep their error and cause`() = runTest {
        val expected = AppResult.Error(AppError.UNKNOWN, IllegalStateException("cause"))

        val actual = monitor.measure(PerformanceTraceName.HOUSEHOLD_RESTORE) { expected }

        assertThat(actual).isSameInstanceAs(expected)
        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.ERROR)
    }

    @Test
    fun `unexpected exceptions still finish the trace and propagate`() = runTest {
        val expected = IllegalStateException("failure")

        val failure = runCatching {
            monitor.measure<Unit>(PerformanceTraceName.SYNC_UPLOAD) { throw expected }
        }.exceptionOrNull()

        assertThat(failure).isSameInstanceAs(expected)
        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.ERROR)
    }

    @Test
    fun `cancellation stays cancellation and finishes the trace`() = runTest {
        val expected = CancellationException("cancelled")

        val failure = runCatching {
            monitor.measure<Unit>(PerformanceTraceName.HOUSEHOLD_RESTORE) { throw expected }
        }.exceptionOrNull()

        assertThat(failure).isSameInstanceAs(expected)
        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.CANCELLED)
    }

    @Test
    fun `a suspended operation is measured until it actually finishes`() = runTest {
        val ready = CompletableDeferred<Unit>()
        launch {
            monitor.measure(PerformanceTraceName.HOUSEHOLD_RESTORE) {
                ready.await()
                AppResult.Success(Unit)
            }
        }
        runCurrent()

        assertThat(monitor.traces.single().outcomes).isEmpty()

        ready.complete(Unit)
        advanceUntilIdle()
        assertThat(monitor.traces.single().outcomes).containsExactly(PerformanceOutcome.SUCCESS)
    }
}
