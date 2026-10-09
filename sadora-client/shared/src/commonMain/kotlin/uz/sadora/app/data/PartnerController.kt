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
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.CreatePaymentRequest
import uz.sadora.contract.IncomingPaymentRequest
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequest
import uz.sadora.contract.PaymentRequestKind
import uz.sadora.contract.PaymentRequestStatus
import uz.sadora.contract.PaymentState

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
    /** The store's sheet in a store build: a gift is bought there, never by Payme or Click. */
    val store: StoreBilling? = null,
    /** Stamped on a store purchase so the server can tell whose it is. */
    private val currentUserId: () -> String? = { null },
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

    suspend fun setAcceptsPaymentRequests(linkId: String, enabled: Boolean): Boolean {
        val api = api ?: return false
        val updated = calls.run { api.setAcceptsPaymentRequests(linkId, enabled) } ?: return false
        state = state?.let { s -> s.copy(following = s.following.map { if (it.linkId == linkId) updated else it }) }
        if (!enabled) incoming = incoming.filterNot { it.linkId == linkId }
        return true
    }

    // ---------------------------------------------------------------- her requests to pay

    /** Her request open now, or the one that closed in the last few days. */
    var myRequest by mutableStateOf<PaymentRequest?>(null)
        private set

    /** The browser link of [myRequest], only on the answer that made it; the server keeps a hash. */
    var shareUrl by mutableStateOf<String?>(null)
        private set

    suspend fun loadMyRequest() {
        val api = api ?: return
        val read = calls.run(silent = true) { api.myPaymentRequest() } ?: return
        myRequest = read.current
        if (read.current?.id != shareUrlFor) shareUrl = null
    }

    private var shareUrlFor: String? = null

    suspend fun askToPay(kind: PaymentRequestKind, period: BillingPeriod? = null, doctorId: String? = null, note: String? = null): PaymentRequest? {
        val api = api ?: return null
        val created = calls.run { api.askToPay(CreatePaymentRequest(kind, period, doctorId, note?.takeIf { it.isNotBlank() })) } ?: return null
        myRequest = created
        shareUrl = created.shareUrl
        shareUrlFor = created.id
        analytics.event(AnalyticsEvents.PAYMENT_REQUEST_SENT, mapOf("kind" to kind.name.lowercase()))
        return created
    }

    suspend fun cancelMyRequest(): Boolean {
        val api = api ?: return false
        val id = myRequest?.id ?: return false
        myRequest = calls.run { api.cancelPaymentRequest(id) } ?: return false
        shareUrl = null
        return true
    }

    /** The link to share: the one in hand, or a fresh one (which retires the old). */
    suspend fun requestShareUrl(): String? {
        shareUrl?.let { return it }
        val api = api ?: return null
        val id = myRequest?.id ?: return null
        val rotated = calls.run { api.sharePaymentRequest(id) } ?: return null
        shareUrl = rotated.shareUrl
        shareUrlFor = rotated.id
        return rotated.shareUrl
    }

    /** True while her request is open: a second one would be refused. */
    val hasOpenRequest: Boolean
        get() = myRequest?.status == PaymentRequestStatus.OPEN

    // ---------------------------------------------------------------- requests sent to this account

    var incoming by mutableStateOf<List<IncomingPaymentRequest>>(emptyList())
        private set

    /** The store's localized prices for the gift plans, by product id. */
    var giftPrices by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    /** The request being paid, while its provider page is open. */
    var paying by mutableStateOf<CheckoutSession?>(null)
        private set

    /** A store purchase that clears later — cash at a kiosk. */
    var storePending by mutableStateOf(false)
        private set

    suspend fun loadIncoming() {
        val api = api ?: return
        val read = calls.run(silent = true) { api.incomingPaymentRequests() } ?: return
        incoming = read
        val store = store ?: return
        val ids = read.flatMap { it.plans }.mapNotNull(::storeProductId).distinct()
        if (ids.isNotEmpty() && !giftPrices.keys.containsAll(ids)) {
            giftPrices = runCatching { store.giftPrices(ids) }.getOrDefault(emptyMap())
        }
    }

    fun storeProductId(plan: BillingPlan): String? = when (store?.provider) {
        PaymentProvider.GOOGLE_PLAY -> plan.googlePlayProductId
        PaymentProvider.APP_STORE -> plan.appStoreProductId
        else -> null
    }

    suspend fun declineRequest(id: String): Boolean {
        val api = api ?: return false
        calls.run { api.declinePaymentRequest(id) } ?: return false
        incoming = incoming.filterNot { it.id == id }
        return true
    }

    /** Payme or Click: the checkout link to open. */
    suspend fun startRequestCheckout(id: String, provider: PaymentProvider, planId: String?): CheckoutSession? {
        val api = api ?: return null
        val session = calls.run { api.payPaymentRequest(id, provider, planId) } ?: return null
        paying = session
        return session
    }

    /** Waits for the provider's callback, the way the paywall does. True once paid. */
    suspend fun awaitRequestPayment(): Boolean {
        val api = api ?: return false
        val session = paying ?: return false
        repeat(PollAttempts) {
            kotlinx.coroutines.delay(PollIntervalMillis)
            val status = api.paymentStatus(session.transactionId).valueOrNull ?: return@repeat
            when (status.state) {
                PaymentState.PAID -> {
                    paying = null
                    loadIncoming()
                    analytics.event(AnalyticsEvents.PAYMENT_REQUEST_PAID, mapOf("via" to session.provider.name.lowercase()))
                    return true
                }
                PaymentState.CANCELLED, PaymentState.FAILED -> {
                    paying = null
                    return false
                }
                PaymentState.PENDING -> Unit
            }
        }
        paying = null
        return false
    }

    fun cancelRequestCheckout() {
        paying = null
    }

    /**
     * A gift plan through the store's own sheet: bought for this account, verified by the
     * server for her, and only then consumed.
     */
    suspend fun payInStore(id: String, plan: BillingPlan): Boolean {
        val api = api ?: return false
        val store = store ?: return false
        val productId = storeProductId(plan) ?: return false
        val accountId = currentUserId() ?: return false
        storePending = false
        return when (val outcome = store.purchaseGift(productId, accountId)) {
            is StoreOutcome.Purchased -> {
                val receipt = outcome.receipt
                calls.run { api.payPaymentRequestInStore(id, store.provider, receipt.productId, receipt.token) } ?: return false
                runCatching { store.finishGift(receipt) }
                incoming = incoming.filterNot { it.id == id }
                analytics.event(AnalyticsEvents.PAYMENT_REQUEST_PAID, mapOf("via" to store.provider.name.lowercase()))
                true
            }
            StoreOutcome.Pending -> {
                storePending = true
                false
            }
            StoreOutcome.Cancelled -> false
            is StoreOutcome.Failed -> false
        }
    }

    private fun apply(next: PartnerState) {
        state = next
        // The code on screen is good only while the server still holds that invite.
        if (next.invite == null || next.invite?.createdAt != freshInvite?.createdAt) freshInvite = null
        if (next.webLink?.id != freshWebLink?.id) freshWebLink = null
    }
}
