package cz.dcervenka.choretracker.core.sync.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.common.AppResult
import cz.dcervenka.choretracker.core.data.contract.SyncRepository
import cz.dcervenka.choretracker.core.model.error.AppError
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric's newest supported SDK is behind targetSdk; see ChoreReminderWorkerTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncWorkerTest {

    private val syncRepository: SyncRepository = mockk()

    private fun buildWorker(): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext<Context>())
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = SyncWorker(appContext, workerParameters, syncRepository)
                },
            )
            .build()

    @Test
    fun `succeeds once the pending changes are uploaded`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)

        assertThat(buildWorker().doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `retries when the upload fails`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Error(AppError.NETWORK)

        assertThat(buildWorker().doWork()).isEqualTo(ListenableWorker.Result.retry())
    }
}
