package cz.dcervenka.choretracker.core.notifications.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import cz.dcervenka.choretracker.core.notifications.R

// Must match the "com.google.firebase.messaging.default_notification_channel_id" meta-data
// value in core/notifications/src/main/AndroidManifest.xml.
internal const val INVITE_ACCEPTED_CHANNEL_ID = "household_invites"

object NotificationChannels {
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
                INVITE_ACCEPTED_CHANNEL_ID,
                context.getString(R.string.invite_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.invite_notification_channel_description)
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
            created = true
        }
    }
}
