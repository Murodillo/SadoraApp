package uz.sadora.app.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.sadora.app.data.api.PartnerApi
import uz.sadora.contract.FollowedPerson
import uz.sadora.contract.PartnerInvite
import uz.sadora.contract.PartnerMessage
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerWebLink
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerRelation
import uz.sadora.contract.PartnerState
import uz.sadora.contract.PartnerView

/**
 * Yaqinim, both sides of it.
 *
 * Her side is [state]: the code she has out, the person who typed it, and what that
 * person may see. The code itself exists only on the response that made it — the server
 * keeps a hash — so [freshInvite] is the one copy, held for as long as the app runs.
 *
 * The follower's side is [state]'s `following` list and the [views] read for each.
 */
class PartnerController(
    private val api: PartnerApi?,
    private val analytics: Analytics = Analytics.None,
) {
    private val calls = ApiCallState()

    val busy: Boolean get() = calls.busy
    val error: ApiFailure? get() = calls.error
    val isOffline: Boolean get() = api == null

    fun clearError() = calls.clearError()

    var state by mutableStateOf<PartnerState?>(null)
        private set

    /** The invite this app just made, with its code. Null once used, ended or replaced. */
    var freshInvite by mutableStateOf<PartnerInvite?>(null)
        private set

    /** The last view read per link, so reopening shows something while it refreshes. */
    val views = mutableStateMapOf<String, PartnerView>()

    /** True when someone sees her right now — the labour button only makes sense then. */
    val hasActiveLink: Boolean
        get() = state?.link?.status == PartnerLinkStatus.ACTIVE

    val following: List<FollowedPerson>
        get() = state?.following.orEmpty()

    suspend fun refresh(silent: Boolean = true) {
        val api = api ?: return
        calls.run(silent) { api.state() }?.let(::apply)
    }

    // ---------------------------------------------------------------- hers

    suspend fun invite(relation: PartnerRelation): PartnerInvite? {
        val api = api ?: return null
        val created = calls.run { api.invite(relation) } ?: return null
        freshInvite = created
        state = (state ?: PartnerState()).copy(invite = created.copy(code = null, url = null))
        analytics.event(AnalyticsEvents.PARTNER_INVITED, mapOf("relation" to relation.name.lowercase()))
        return created
    }

    suspend fun approve(): Boolean {
        val api = api ?: return false
        val next = calls.run { api.approve() } ?: return false
        apply(next)
        analytics.event(AnalyticsEvents.PARTNER_LINKED)
        return true
    }

    suspend fun savePermissions(permissions: PartnerPermissions): Boolean {
        val api = api ?: return false
        // Drawn at once; the server's answer replaces it, and a failure puts it back.
        val before = state
        state = before?.copy(link = before.link?.copy(permissions = permissions))
        val next = calls.run { api.savePermissions(permissions) }
        if (next == null) {
            state = before
            return false
        }
        apply(next)
        return true
    }

    suspend fun pause(paused: Boolean): Boolean {
        val api = api ?: return false
        return calls.run { api.pause(paused) }?.also(::apply) != null
    }

    /** Ends whatever she has out: a code, a request or a link. */
    suspend fun end(): Boolean {
        val api = api ?: return false
        return calls.run { api.end() }?.also(::apply) != null
    }

    // ---------------------------------------------------------------- messages

    /** The last few messages per link, newest first. */
    val messages = mutableStateMapOf<String, List<PartnerMessage>>()

    /**
     * Reads a link's messages, and marks the other one's as read when there were any:
     * opening the screen is reading them.
     */
    suspend fun loadMessages(linkId: String) {
        val api = api ?: return
        val read = calls.run(silent = true) { api.messages(linkId) } ?: return
        messages[linkId] = read.items
        if (read.unread > 0) {
            calls.run(silent = true) { api.markRead(linkId) }?.let { messages[linkId] = it.items }
            clearUnread(linkId)
        }
    }

    suspend fun send(linkId: String, kind: PartnerMessageKind, text: String? = null): Boolean {
        val api = api ?: return false
        val sent = calls.run { api.send(linkId, kind, text) } ?: return false
        messages[linkId] = listOf(sent) + messages[linkId].orEmpty()
        analytics.event(AnalyticsEvents.PARTNER_MESSAGE, mapOf("kind" to kind.name.lowercase()))
        return true
    }

    private fun clearUnread(linkId: String) {
        val s = state ?: return
        state = s.copy(
            link = s.link?.let { if (it.id == linkId) it.copy(unread = 0) else it },
            following = s.following.map { if (it.linkId == linkId) it.copy(unread = 0) else it },
        )
    }

    // ---------------------------------------------------------------- the web link

    /** The web link this app just made, with its URL. Null once replaced or taken back. */
    var freshWebLink by mutableStateOf<PartnerWebLink?>(null)
        private set

    suspend fun createWebLink(ttlHours: Int, permissions: PartnerPermissions): PartnerWebLink? {
        val api = api ?: return null
        val created = calls.run { api.createWebLink(ttlHours, permissions) } ?: return null
        freshWebLink = created
        state = (state ?: PartnerState()).copy(webLink = created.copy(url = null))
        analytics.event(AnalyticsEvents.PARTNER_WEB_LINK, mapOf("ttl_hours" to ttlHours.toString()))
        return created
    }

    suspend fun revokeWebLink(): Boolean {
        val api = api ?: return false
        val next = calls.run { api.revokeWebLink() } ?: return false
        apply(next)
        return true
    }

    suspend fun labourAlert(): Boolean {
        val api = api ?: return false
        return calls.run { api.labourAlert() } != null
    }

    // ---------------------------------------------------------------- theirs

    suspend fun accept(code: String, name: String? = null, asPartnerAccount: Boolean = false): FollowedPerson? {
        val api = api ?: return null
        val followed = calls.run { api.accept(code, name, asPartnerAccount) } ?: return null
        val current = state ?: PartnerState()
        state = current.copy(following = listOf(followed) + current.following.filterNot { it.linkId == followed.linkId })
        analytics.event(AnalyticsEvents.PARTNER_ACCEPTED)
        return followed
    }

    suspend fun loadView(linkId: String, silent: Boolean = false): PartnerView? {
        val api = api ?: return null
        val view = calls.run(silent) { api.view(linkId) } ?: return null
        views[linkId] = view
        // The list says what the view says: a link she approved since is active here too.
        state = state?.let { s ->
            s.copy(following = s.following.map { if (it.linkId == linkId) it.copy(status = view.status, name = view.name) else it })
        }
        return view
    }

    suspend fun leave(linkId: String): Boolean {
        val api = api ?: return false
        calls.run { api.leave(linkId) } ?: return false
        views.remove(linkId)
        state = state?.let { s -> s.copy(following = s.following.filterNot { it.linkId == linkId }) }
        return true
    }

    private fun apply(next: PartnerState) {
        state = next
        // The code on screen is good only while the server still holds that invite.
        if (next.invite == null || next.invite?.createdAt != freshInvite?.createdAt) freshInvite = null
        if (next.webLink?.id != freshWebLink?.id) freshWebLink = null
    }
}
