package cz.dcervenka.choretracker.core.notifications.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

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
                "Household invites",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifies you when someone joins your household"
            }
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
            created = true
        }
    }
}
