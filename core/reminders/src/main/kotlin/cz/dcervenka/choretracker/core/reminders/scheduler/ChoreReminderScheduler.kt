package cz.dcervenka.choretracker.core.reminders.scheduler

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.reminders.di.ReminderScope
import cz.dcervenka.choretracker.core.reminders.worker.ChoreReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

private const val UNIQUE_WORK_NAME = "chore_reminder_work"

@Singleton
class ChoreReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    @ReminderScope private val scope: CoroutineScope,
) {
    // Guards against the cold-start race: when WorkManager itself starts the app process to run
    // the due work, this class's init subscription fires its first reschedule concurrently with
    // that same work already being enqueued/running. REPLACE there would cancel the work that
    // just woke the process, so the very first reschedule of this process only uses KEEP (enqueue
    // if nothing is already scheduled). A real settings change - or the worker's own
    // rescheduleNow() after it finishes - always forces REPLACE so it takes effect immediately.
    @Volatile
    private var hasScheduledSinceProcessStart = false

    init {
        reminderSettingsRepository.observeSettings()
            .distinctUntilChanged()
            .onEach { settings ->
                val policy = if (hasScheduledSinceProcessStart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
                reschedule(settings, policy)
            }
            .catch { error -> Timber.e(error, "ChoreReminderScheduler: settings subscription failed") }
            .launchIn(scope)
    }

    suspend fun rescheduleNow() = reschedule(reminderSettingsRepository.getSettings(), ExistingWorkPolicy.REPLACE)

    private fun reschedule(settings: ReminderSettings, policy: ExistingWorkPolicy) {
        hasScheduledSinceProcessStart = true
        if (!settings.enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val delayMillis = computeInitialDelay(settings.hour, settings.minute).inWholeMilliseconds
        val request = OneTimeWorkRequestBuilder<ChoreReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    // Zone-aware (unlike java.time.LocalDateTime/Duration.between with no attached zone): on a
    // DST transition day, converting the target local hour:minute back to an Instant via
    // toInstant(timeZone) correctly yields a 23h or 25h gap instead of assuming every day is 24h.
    private fun computeInitialDelay(hour: Int, minute: Int): Duration {
        val timeZone = TimeZone.currentSystemDefault()
        val now: Instant = Clock.System.now()
        val today = now.toLocalDateTime(timeZone).date
        var next = LocalDateTime(today, LocalTime(hour, minute)).toInstant(timeZone)
        if (next <= now) {
            next = LocalDateTime(today.plus(DatePeriod(days = 1)), LocalTime(hour, minute)).toInstant(timeZone)
        }
        return next - now
    }
}
