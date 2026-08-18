package cz.dcervenka.choretracker.core.reminders.scheduler

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val UNIQUE_WORK_NAME = "chore_reminder_work"

class ChoreReminderSchedulerTest {

    @get:Rule
    val coroutineRule = TestCoroutineRule(startPaused = true)

    @MockK
    lateinit var workManager: WorkManager

    @MockK
    lateinit var reminderSettingsRepository: ReminderSettingsRepository

    private val settingsFlow = MutableStateFlow(ReminderSettings(enabled = true))
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        every { reminderSettingsRepository.observeSettings() } returns settingsFlow
        coEvery { reminderSettingsRepository.getSettings() } answers { settingsFlow.value }
        every {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        } returns mockk(relaxed = true)
        every { workManager.cancelUniqueWork(any()) } returns mockk(relaxed = true)
        scope = CoroutineScope(coroutineRule.dispatcher)
    }

    private fun createScheduler() = ChoreReminderScheduler(workManager, reminderSettingsRepository, scope)

    @Test
    fun `enqueues unique work with KEEP on init so it doesn't clobber already-scheduled work`() =
        runTest(coroutineRule.dispatcher) {
            createScheduler()
            advanceUntilIdle()

            verify(exactly = 1) {
                workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
            }
            verify(exactly = 0) {
                workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>())
            }
        }

    @Test
    fun `cancels unique work when settings flip to disabled`() = runTest(coroutineRule.dispatcher) {
        createScheduler()
        advanceUntilIdle()

        settingsFlow.value = ReminderSettings(enabled = false)
        advanceUntilIdle()

        verify(exactly = 1) { workManager.cancelUniqueWork(UNIQUE_WORK_NAME) }
    }

    @Test
    fun `rescheduleNow reads current settings and re-enqueues with REPLACE`() = runTest(coroutineRule.dispatcher) {
        val scheduler = createScheduler()
        advanceUntilIdle()

        scheduler.rescheduleNow()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
        }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `reschedules to the new time with REPLACE when settings change while still enabled`() =
        runTest(coroutineRule.dispatcher) {
            createScheduler()
            advanceUntilIdle()

            settingsFlow.value = ReminderSettings(enabled = true, hour = 20, minute = 30)
            advanceUntilIdle()

            verify(exactly = 1) {
                workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, any<OneTimeWorkRequest>())
            }
            verify(exactly = 1) {
                workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, any<OneTimeWorkRequest>())
            }
        }
}
