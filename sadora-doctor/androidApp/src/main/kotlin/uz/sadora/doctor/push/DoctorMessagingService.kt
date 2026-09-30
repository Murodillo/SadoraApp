package uz.sadora.doctor.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM's entry point into the doctor app.
 *
 * `onNewToken` is deliberately not overridden, as in the women's app: the service has
 * no session to send a token with, and [PushRegistration] sends the current token on
 * every start with someone signed in — which is the rotated token, by then.
 */
class DoctorMessagingService : FirebaseMessagingService() {

    /**
     * Only reached with the app in the foreground for a notification message, or for a
     * data-only one at any time; the title and text come from whichever the server sent.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification
        PushNotifications.show(
            context = this,
            title = notification?.title ?: message.data["title"],
            body = notification?.body ?: message.data["body"],
            link = message.data[PushNotifications.EXTRA_LINK],
            notificationId = message.data["notificationId"],
        )
    }
}
