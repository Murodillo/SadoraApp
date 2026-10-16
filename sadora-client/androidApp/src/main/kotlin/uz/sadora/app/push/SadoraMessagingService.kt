package uz.sadora.app.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import uz.sadora.app.widget.SadoraWidget
import uz.sadora.widget.WidgetHost

/**
 * FCM's entry point into the app.
 *
 * `onNewToken` is deliberately not overridden: the service has no session to send a
 * token with, and [PushRegistration] sends the current token on every start that has
 * one — which is the rotated token, by then.
 */
class SadoraMessagingService : FirebaseMessagingService() {

    /**
     * A notification FCM would draw reaches here only with the app in the foreground; see
     * [PushNotifications]. Two kinds always reach here, as data alone: a widget's "redraw
     * now", and a medication reminder, which the app draws so it can carry its buttons.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == WIDGET_REFRESH) {
            // Off the main thread already; FCM allows about ten seconds here.
            runBlocking {
                val host = WidgetHost.get(this@SadoraMessagingService)
                withTimeoutOrNull(REFRESH_BUDGET_MS) { host.sync.refresh() }
                SadoraWidget.redrawAll(this@SadoraMessagingService)
            }
            return
        }
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]
        if (title == null && body == null) return
        PushNotifications.show(
            context = this,
            title = title,
            body = body,
            notificationId = message.data["notificationId"],
            link = message.data[PushNotifications.EXTRA_LINK],
            category = message.data[PushNotifications.EXTRA_CATEGORY],
        )
    }

    private companion object {
        /** The server's `WidgetRefresher.TYPE`. */
        const val WIDGET_REFRESH = "widget_refresh"
        const val REFRESH_BUDGET_MS = 8_000L
    }
}
