package uz.sadora.app

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
// Declared in a UIKit category, which Kotlin/Native exposes as an extension to import.
import platform.UIKit.registerForRemoteNotifications
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import uz.sadora.app.data.SadoraGraph
import uz.sadora.app.data.SessionState

/**
 * The Kotlin half of push on iOS.
 *
 * Swift owns the Firebase SDK and the APNs callbacks, and hands the FCM token over with
 * [onToken]. Deciding when to ask for permission and whom to register the token for
 * lives here, so iOS follows the same rules as `MainActivity` on Android: the token goes
 * to the backend for whoever is signed in, and the permission is asked once she is past
 * onboarding.
 */
object IosPush {

    private val token = MutableStateFlow<String?>(null)
    private val scope = MainScope()
    private var following = false

    /** Called from Swift whenever Firebase issues or rotates the FCM token. */
    fun onToken(value: String) {
        token.value = value
    }

    internal fun follow(graph: SadoraGraph) {
        if (following) return
        following = true

        val signedIn = graph.session.state.filterIsInstance<SessionState.SignedIn>()
        scope.launch {
            // A new user, or a rotated token, is each a reason to register again; every
            // profile edit emits a new SignedIn and is not.
            combine(signedIn.distinctUntilChangedBy { it.user.id }, token.filterNotNull()) { _, value -> value }
                .collect { graph.repository.registerPushToken(it) }
        }
        scope.launch {
            signedIn.first { !it.needsOnboarding }
            askForNotifications()
        }
    }

    /**
     * Asking again once granted returns at once without a prompt, which is what re-registers
     * with APNs on every start — the device token is only ever handed out after this call.
     */
    private fun askForNotifications() {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            if (granted) {
                dispatch_async(dispatch_get_main_queue()) {
                    UIApplication.sharedApplication.registerForRemoteNotifications()
                }
            }
        }
    }
}
