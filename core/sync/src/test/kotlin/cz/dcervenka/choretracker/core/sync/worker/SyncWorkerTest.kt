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
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.database.entity.PendingSyncOperationEntity
import cz.dcervenka.choretracker.core.model.error.AppError
import cz.dcervenka.choretracker.core.test.clock.FixedClock
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.parse("2026-11-21T12:00:00Z")

// Robolectric's newest supported SDK is behind targetSdk; see ChoreReminderWorkerTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncWorkerTest {

    private val syncRepository: SyncRepository = mockk()
    private val pendingSyncOperationDao: PendingSyncOperationDao = mockk()

    @Before
    fun setUp() {
        coEvery { pendingSyncOperationDao.getAll() } returns emptyList()
    }

    private fun buildWorker(runAttemptCount: Int = 0): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext<Context>())
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = SyncWorker(
                        context = appContext,
                        params = workerParameters,
                        syncRepository = syncRepository,
                        pendingSyncOperationDao = pendingSyncOperationDao,
                        clock = FixedClock(NOW),
                    )
                },
            )
            .build()

    private fun operation(createdAt: Instant) = PendingSyncOperationEntity(
        id = "op-1",
        entityType = "completion",
        entityId = "completion-1",
        operationType = "upsert",
        payload = "",
        createdAt = createdAt,
    )

    @Test
    fun `succeeds once the pending changes are uploaded`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)

        assertThat(buildWorker().doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `succeeds when only changes from before the run are still queued`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(operation(createdAt = NOW - 1.minutes))

        assertThat(buildWorker().doWork()).isEqualTo(ListenableWorker.Result.success())
    }

    @Test
    fun `retries when a change queued during the run is still waiting`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Success(Unit)
        coEvery { pendingSyncOperationDao.getAll() } returns listOf(operation(createdAt = NOW))

        assertThat(buildWorker().doWork()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun `retries when the upload fails`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Error(AppError.NETWORK)

        assertThat(buildWorker().doWork()).isEqualTo(ListenableWorker.Result.retry())
    }

    @Test
    fun `gives up after its last run fails`() = runTest {
        coEvery { syncRepository.syncPendingOperations() } returns AppResult.Error(AppError.PERMISSION_DENIED)

        assertThat(buildWorker(runAttemptCount = 8).doWork()).isEqualTo(ListenableWorker.Result.retry())
        assertThat(buildWorker(runAttemptCount = 9).doWork()).isEqualTo(ListenableWorker.Result.failure())
    }
}
