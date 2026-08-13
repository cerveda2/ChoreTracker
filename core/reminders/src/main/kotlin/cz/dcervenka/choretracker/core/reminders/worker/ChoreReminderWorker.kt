package cz.dcervenka.choretracker.core.reminders.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cz.dcervenka.choretracker.core.data.contract.ReminderSettingsRepository
import cz.dcervenka.choretracker.core.domain.usecase.CheckStaleChoresUseCase
import cz.dcervenka.choretracker.core.model.stats.ChoreStaleness
import cz.dcervenka.choretracker.core.reminders.R
import cz.dcervenka.choretracker.core.reminders.notification.CHORE_REMINDER_CHANNEL_ID
import cz.dcervenka.choretracker.core.reminders.scheduler.ChoreReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

private const val REMINDER_NOTIFICATION_ID = 2001

@HiltWorker
class ChoreReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checkStaleChoresUseCase: CheckStaleChoresUseCase,
    private val reminderSettingsRepository: ReminderSettingsRepository,
    private val scheduler: ChoreReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            if (reminderSettingsRepository.getSettings().enabled) {
                val staleChores = checkStaleChoresUseCase()
                if (staleChores.isNotEmpty()) {
                    postGroupedNotification(staleChores)
                }
            }
            return Result.success()
        } finally {
            // Always chain tomorrow's run, whether this one succeeded, failed, or was skipped.
            scheduler.rescheduleNow()
        }
    }

    private fun postGroupedNotification(staleChores: List<ChoreStaleness>) {
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            Timber.d("postGroupedNotification: POST_NOTIFICATIONS not granted, skipping")
            return
        }

        val title = applicationContext.resources.getQuantityString(
            R.plurals.reminder_notification_title,
            staleChores.size,
            staleChores.size,
        )
        val style = NotificationCompat.InboxStyle()
        staleChores.forEach { style.addLine(it.choreName) }

        val builder = NotificationCompat.Builder(applicationContext, CHORE_REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_reminder_notification)
            .setContentTitle(title)
            .setStyle(style)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(buildContentIntent())

        NotificationManagerCompat.from(applicationContext).notify(REMINDER_NOTIFICATION_ID, builder.build())
    }

    private fun buildContentIntent(): PendingIntent? {
        val launchIntent = applicationContext.packageManager
            .getLaunchIntentForPackage(applicationContext.packageName)
            ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            REMINDER_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
