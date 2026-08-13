package cz.dcervenka.choretracker.core.reminders.scheduler

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.model.settings.ReminderSettings
import cz.dcervenka.choretracker.core.reminders.di.ReminderScope
import cz.dcervenka.choretracker.core.reminders.worker.ChoreReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val UNIQUE_WORK_NAME = "chore_reminder_work"

@Singleton
class ChoreReminderScheduler @Inject constructor(
    private val workManager: WorkManager,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    @ReminderScope private val scope: CoroutineScope,
) {
    init {
        reminderSettingsRepository.observeSettings()
            .distinctUntilChanged()
            .onEach { settings -> reschedule(settings) }
            .launchIn(scope)
    }

    suspend fun rescheduleNow() = reschedule(reminderSettingsRepository.getSettings())

    private fun reschedule(settings: ReminderSettings) {
        if (!settings.enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<ChoreReminderWorker>()
            .setInitialDelay(computeInitialDelay(settings.hour, settings.minute).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun computeInitialDelay(hour: Int, minute: Int): Duration {
        val now = LocalDateTime.now()
        var next = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }
}
