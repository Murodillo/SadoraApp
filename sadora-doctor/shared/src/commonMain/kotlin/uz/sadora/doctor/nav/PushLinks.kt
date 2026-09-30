package uz.sadora.doctor.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.doctor.data.conversationIdFromLink

/**
 * The conversation a tapped push asked for, held until the app can open it.
 *
 * The platform offers the push's `link` the moment the app is started or brought
 * forward by it — often before the session is resolved or the panel knows she is
 * approved. The app takes it once the tabs are there, and not before, so a tap that
 * starts the app cold still lands in the conversation rather than on Home.
 */
object PushLinks {
    var pendingConversation by mutableStateOf<String?>(null)
        private set

    /** A link the app does not open is ignored rather than kept. */
    fun offer(link: String?) {
        conversationIdFromLink(link)?.let { pendingConversation = it }
    }

    /** The conversation to open, once; null when there is none. */
    fun take(): String? = pendingConversation.also { pendingConversation = null }
}
