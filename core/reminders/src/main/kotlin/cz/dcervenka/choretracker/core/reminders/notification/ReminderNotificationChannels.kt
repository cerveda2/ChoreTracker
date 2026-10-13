package cz.dcervenka.choretracker.core.reminders.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import cz.dcervenka.choretracker.core.reminders.R

internal const val CHORE_REMINDER_CHANNEL_ID = "chore_reminders"

object ReminderNotificationChannels {
    @Volatile
    private var created = false

    fun ensureCreated(context: Context) {
        if (created || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            created = true
            return
        }
        synchronized(this) {
            if (created) return
            val channel = NotificationChannel(
                CHORE_REMINDER_CHANNEL_ID,
                context.getString(R.string.reminder_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.reminder_notification_channel_description)
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
            created = true
        }
    }
}
