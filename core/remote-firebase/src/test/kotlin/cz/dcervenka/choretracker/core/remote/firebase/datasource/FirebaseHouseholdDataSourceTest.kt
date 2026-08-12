package cz.dcervenka.choretracker.core.remote.firebase.datasource

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import org.junit.Test

class FirebaseHouseholdDataSourceTest {

    // rethrowCancellation

    @Test
    fun `rethrowCancellation rethrows CancellationException instead of wrapping it`() {
        val cancellation = CancellationException("scope torn down")
        val failureResult = Result.failure<Unit>(cancellation)

        var thrown: Throwable? = null
        try {
            failureResult.rethrowCancellation()
        } catch (e: CancellationException) {
            thrown = e
        }

        assertThat(thrown).isSameInstanceAs(cancellation)
    }

    @Test
    fun `rethrowCancellation leaves a regular failure wrapped as a Result failure`() {
        val error = IllegalStateException("network error")
        val failureResult = Result.failure<Unit>(error)

        val result = failureResult.rethrowCancellation()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isSameInstanceAs(error)
    }

    @Test
    fun `rethrowCancellation leaves a success untouched`() {
        val successResult = Result.success("value")

        val result = successResult.rethrowCancellation()

        assertThat(result.getOrNull()).isEqualTo("value")
    }
}
