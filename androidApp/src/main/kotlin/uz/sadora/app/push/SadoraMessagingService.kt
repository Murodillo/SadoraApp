package uz.sadora.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM's entry point into the app.
 *
 * `onNewToken` is deliberately not overridden: the service has no session to send a
 * token with, and [PushRegistration] sends the current token on every start that has
 * one — which is the rotated token, by then.
 */
class SadoraMessagingService : FirebaseMessagingService() {

    /** Only reached with the app in the foreground; see [PushNotifications]. */
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        PushNotifications.show(
            context = this,
            title = notification.title,
            body = notification.body,
            notificationId = message.data["notificationId"],
        )
    }
}
