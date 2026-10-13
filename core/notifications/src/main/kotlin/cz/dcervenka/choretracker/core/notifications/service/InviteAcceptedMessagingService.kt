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

        // The push carries titleLocKey/bodyLocKey rather than literal text (see onInviteAccepted in
        // functions/src/index.ts), so title/body are null here - resolve the same resources the
        // system uses for the backgrounded case.
        val joiningName = notification.bodyLocalizationArgs?.firstOrNull()
        val body = if (joiningName != null) {
            getString(R.string.invite_accepted_notification_body, joiningName)
        } else {
            getString(R.string.invite_accepted_notification_body_anonymous)
        }
        val builder = NotificationCompat.Builder(this, INVITE_ACCEPTED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.invite_accepted_notification_title))
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
