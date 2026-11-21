package cz.dcervenka.choretracker.core.sync.scheduler

import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.AuthRepository
import cz.dcervenka.choretracker.core.database.dao.PendingSyncOperationDao
import cz.dcervenka.choretracker.core.model.auth.AppUser
import cz.dcervenka.choretracker.core.model.auth.AuthState
import cz.dcervenka.choretracker.core.sync.worker.SyncWorker
import cz.dcervenka.choretracker.core.test.mock.sampleAuthenticatedState
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val UNIQUE_WORK_NAME = "pending_sync_work"

class PendingSyncSchedulerTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var workManager: WorkManager

    @MockK
    lateinit var authRepository: AuthRepository

    @MockK
    lateinit var pendingSyncOperationDao: PendingSyncOperationDao

    private val authState = MutableStateFlow<AuthState>(sampleAuthenticatedState())
    private val pendingCount = MutableStateFlow(0)

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { authRepository.authState } returns authState
        every { pendingSyncOperationDao.observePendingCount() } returns pendingCount
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } returns mockk(relaxed = true)
        every { workManager.cancelUniqueWork(any()) } returns mockk(relaxed = true)
    }

    private fun createScheduler() = PendingSyncScheduler(
        workManager = workManager,
        authRepository = authRepository,
        pendingSyncOperationDao = pendingSyncOperationDao,
        scope = CoroutineScope(coroutineRule.dispatcher),
    )

    @Test
    fun `enqueues network-constrained sync work with KEEP once changes are waiting`() =
        runTest(coroutineRule.dispatcher) {
            createScheduler()
            pendingCount.value = 2
            advanceUntilIdle()

            val request = slot<OneTimeWorkRequest>()
            verify(exactly = 1) {
                workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, capture(request))
            }
            assertThat(request.captured.workSpec.workerClassName).isEqualTo(SyncWorker::class.java.name)
            assertThat(request.captured.workSpec.constraints.requiredNetworkType).isEqualTo(NetworkType.CONNECTED)
        }

    @Test
    fun `asks again with KEEP on every change to a waiting queue and cancels when it drains`() =
        runTest(coroutineRule.dispatcher) {
            pendingCount.value = 1
            createScheduler()
            advanceUntilIdle()

            pendingCount.value = 3
            advanceUntilIdle()
            pendingCount.value = 0
            advanceUntilIdle()

            verify(exactly = 2) {
                workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
            }
            verify(exactly = 1) { workManager.cancelUniqueWork(UNIQUE_WORK_NAME) }
        }

    @Test
    fun `leaves scheduled work alone until the session is restored`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Initializing
        pendingCount.value = 1
        createScheduler()
        advanceUntilIdle()

        verify(exactly = 0) { workManager.cancelUniqueWork(any()) }
        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }

        authState.value = sampleAuthenticatedState()
        advanceUntilIdle()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `cancels instead of scheduling when signed out`() = runTest(coroutineRule.dispatcher) {
        pendingCount.value = 1
        createScheduler()
        advanceUntilIdle()

        authState.value = AuthState.SignedOut
        advanceUntilIdle()

        verify(exactly = 1) { workManager.cancelUniqueWork(UNIQUE_WORK_NAME) }
    }

    @Test
    fun `never schedules for a preview user`() = runTest(coroutineRule.dispatcher) {
        authState.value = AuthState.Authenticated(
            AppUser(id = "preview-user", email = null, displayName = "Preview User", isPreview = true),
        )
        pendingCount.value = 1
        createScheduler()
        advanceUntilIdle()

        verify(exactly = 0) { workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>()) }
    }
}
