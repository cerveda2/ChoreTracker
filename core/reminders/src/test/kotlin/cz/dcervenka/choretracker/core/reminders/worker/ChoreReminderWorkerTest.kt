package cz.dcervenka.choretracker.core.reminders.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.domain.usecase.CheckStaleChoresUseCase
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.model.stats.ChoreStatus
import cz.dcervenka.choretracker.core.reminders.scheduler.ChoreReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.16.1's newest supported SDK is 36; targetSdk is 37, ahead of what
// Robolectric ships shadows for yet. Pin the test SDK explicitly until Robolectric catches up.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChoreReminderWorkerTest {

    private lateinit var context: Context
    private lateinit var checkStaleChoresUseCase: CheckStaleChoresUseCase
    private lateinit var reminderSettingsRepository: ReminderSettingsRepository
    private lateinit var scheduler: ChoreReminderScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        checkStaleChoresUseCase = mockk()
        reminderSettingsRepository = mockk()
        scheduler = mockk(relaxed = true)
    }

    private fun buildWorker(): ChoreReminderWorker =
        TestListenableWorkerBuilder<ChoreReminderWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = ChoreReminderWorker(
                        appContext,
                        workerParameters,
                        checkStaleChoresUseCase,
                        reminderSettingsRepository,
                        scheduler,
                    )
                },
            )
            .build()

    @Test
    fun `skips the staleness check when reminders are disabled`() = runTest {
        coEvery { reminderSettingsRepository.getSettings() } returns ReminderSettings(enabled = false)

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 0) { checkStaleChoresUseCase() }
        coVerify(exactly = 1) { scheduler.rescheduleNow() }
    }

    @Test
    fun `succeeds without a stale-chore list when nothing needs attention`() = runTest {
        coEvery { reminderSettingsRepository.getSettings() } returns ReminderSettings(enabled = true)
        coEvery { checkStaleChoresUseCase() } returns emptyList()

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 1) { scheduler.rescheduleNow() }
    }

    @Test
    fun `succeeds and reschedules when there are stale chores to notify about`() = runTest {
        coEvery { reminderSettingsRepository.getSettings() } returns ReminderSettings(enabled = true)
        coEvery { checkStaleChoresUseCase() } returns listOf(
            ChoreStaleness(
                choreId = "chore-1",
                choreName = "Kitchen",
                lastCompletedDate = null,
                daysSinceLastCompletion = 10,
                frequencyDays = 7,
                status = ChoreStatus.NEEDS_ATTENTION,
            ),
        )

        val result = buildWorker().doWork()

        assertThat(result).isEqualTo(ListenableWorker.Result.success())
        coVerify(exactly = 1) { scheduler.rescheduleNow() }
    }

    @Test
    fun `always reschedules tomorrow's run even when the staleness check throws`() = runTest {
        coEvery { reminderSettingsRepository.getSettings() } returns ReminderSettings(enabled = true)
        coEvery { checkStaleChoresUseCase() } throws IllegalStateException("boom")

        runCatching { buildWorker().doWork() }

        coVerify(exactly = 1) { scheduler.rescheduleNow() }
    }
}
