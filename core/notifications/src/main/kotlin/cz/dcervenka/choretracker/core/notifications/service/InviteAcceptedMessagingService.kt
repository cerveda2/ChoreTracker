package cz.dcervenka.choretracker.core.notifications.service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import cz.dcervenka.choretracker.core.notifications.R
import cz.dcervenka.choretracker.core.notifications.repository.FcmTokenRegistrar
import cz.dcervenka.choretracker.core.notifications.repository.PUSH_TYPE_REQUEST
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class InviteAcceptedMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var tokenRegistrar: FcmTokenRegistrar

    override fun onRegistered(installationId: String) {
        super.onRegistered(installationId)
        Timber.d("InviteAcceptedMessagingService: onRegistered")
        tokenRegistrar.onIdRegistered(installationId)
    }

    // Only reached while the app is in the foreground - FCM auto-displays "notification"-payload
    // pushes itself (using the manifest meta-data channel/icon) when the app is backgrounded or
    // killed, without ever calling this method.
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val notification = message.notification ?: return
        val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        // The push carries titleLocKey/bodyLocKey rather than literal text (see functions/src/index.ts),
        // so title/body are null here - resolve the same resources the system uses for the
        // backgrounded case. Pushes without a type are invite-accepted ones, which predate it.
        val builder = if (message.data["type"] == PUSH_TYPE_REQUEST) {
            requestNotification(notification)
        } else {
            inviteAcceptedNotification(notification)
        }
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun inviteAcceptedNotification(notification: RemoteMessage.Notification): NotificationCompat.Builder {
        val joiningName = notification.bodyLocalizationArgs?.firstOrNull()
        val body = if (joiningName != null) {
            getString(R.string.invite_accepted_notification_body, joiningName)
        } else {
            getString(R.string.invite_accepted_notification_body_anonymous)
        }
        return NotificationCompat.Builder(this, INVITE_ACCEPTED_CHANNEL_ID)
            .setContentTitle(getString(R.string.invite_accepted_notification_title))
            .setContentText(body)
    }

    private fun requestNotification(notification: RemoteMessage.Notification): NotificationCompat.Builder {
        val sender = notification.titleLocalizationArgs?.firstOrNull().orEmpty()
        val bodyArgs = notification.bodyLocalizationArgs.orEmpty()
        val chores = bodyArgs.firstOrNull().orEmpty()
        val note = bodyArgs.getOrNull(1)
        val body = if (note != null) {
            getString(R.string.chore_request_notification_body_note, chores, note)
        } else {
            getString(R.string.chore_request_notification_body, chores)
        }
        return NotificationCompat.Builder(this, CHORE_REQUESTS_CHANNEL_ID)
            .setContentTitle(getString(R.string.chore_request_notification_title, sender))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
    }
}
