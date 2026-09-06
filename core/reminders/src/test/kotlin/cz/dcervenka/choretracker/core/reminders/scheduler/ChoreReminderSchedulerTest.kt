package cz.dcervenka.choretracker.core.reminders.scheduler

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.test.clock.FixedClock
import cz.dcervenka.choretracker.core.test.rule.TestCoroutineRule
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
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

    // Fixed at 23:30 local time so the default 09:00 reminder time has already passed today,
    // exercising computeInitialDelay's midnight-rollover branch. Derived through
    // TimeZone.currentSystemDefault() rather than a hardcoded offset, so this holds regardless
    // of the machine running the test.
    private val timeZone = TimeZone.currentSystemDefault()
    private val fixedInstant = LocalDateTime(LocalDate(2026, 3, 15), LocalTime(23, 30)).toInstant(timeZone)
    private val clock = FixedClock(fixedInstant)

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

    private fun createScheduler() = ChoreReminderScheduler(workManager, reminderSettingsRepository, scope, clock)

    // Mirrors ChoreReminderScheduler's own private computeInitialDelay formula so the test can
    // assert an exact expected value without exposing that internal.
    private fun expectedInitialDelayMillis(hour: Int, minute: Int): Long {
        val today = fixedInstant.toLocalDateTime(timeZone).date
        var next = LocalDateTime(today, LocalTime(hour, minute)).toInstant(timeZone)
        if (next <= fixedInstant) {
            next = LocalDateTime(today.plus(DatePeriod(days = 1)), LocalTime(hour, minute)).toInstant(timeZone)
        }
        return (next - fixedInstant).inWholeMilliseconds
    }

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

    @Test
    fun `rolls the delay over to the next day when the target time has already passed today`() =
        runTest(coroutineRule.dispatcher) {
            val requestSlot = slot<OneTimeWorkRequest>()
            every {
                workManager.enqueueUniqueWork(any(), any(), capture(requestSlot))
            } returns mockk(relaxed = true)

            createScheduler()
            advanceUntilIdle()

            val expectedDelayMillis = expectedInitialDelayMillis(hour = 9, minute = 0)
            assertThat(requestSlot.captured.workSpec.initialDelay).isEqualTo(expectedDelayMillis)
        }
}
