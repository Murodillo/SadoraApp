package uz.sadora.server.billing

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import uz.sadora.contract.BillingCatalogue
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.CheckoutRequest
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.contract.PaymentStatus
import uz.sadora.contract.SubscriptionSource
import uz.sadora.server.config.BillingConfig
import uz.sadora.server.config.Environment
import uz.sadora.server.core.FeatureDisabledException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.entitlement.SubscriptionRepository
import uz.sadora.server.flags.FeatureFlagService
import uz.sadora.server.flags.FlagContext
import uz.sadora.server.user.UserRepository

/**
 * Buying Premium, and paying for a doctor's consultation.
 *
 * Checkout only ever creates a pending row and a link. Nothing here grants anything: a
 * subscription appears when a provider says the money arrived, through
 * [activate], and that call is reached only from a verified callback. The app cannot ask
 * for Premium and the client cannot claim to have paid.
 */
class BillingService(
    private val repository: BillingRepository,
    private val subscriptions: SubscriptionRepository,
    private val entitlements: EntitlementService,
    private val users: UserRepository,
    private val flags: FeatureFlagService,
    private val config: BillingConfig,
    private val environment: Environment,
) {
    /**
     * What a paid consultation's money opens. Set once at wiring: the consultation
     * service needs billing to take the money, and billing needs it to open the window.
     */
    var consultationPaid: (suspend (sessionId: Uuid, transaction: TransactionRecord) -> Unit)? = null

    suspend fun catalogue(userId: Uuid): BillingCatalogue {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val context = FlagContext(
            userId = userId,
            environment = environment,
            language = user.language,
            lifeStage = user.lifeStage,
        )
        // A provider with no credentials is not offered, whatever the flag says: a
        // checkout button that cannot produce a link is worse than no button.
        val providers = buildList {
            if (config.payme.isConfigured && flags.isEnabled(PAYME_FLAG, context)) add(PaymentProvider.PAYME)
            if (config.click.isConfigured && flags.isEnabled(CLICK_FLAG, context)) add(PaymentProvider.CLICK)
            if (flags.isEnabled(STORE_FLAG, context)) {
                add(PaymentProvider.APP_STORE)
                add(PaymentProvider.GOOGLE_PLAY)
            }
        }
        return BillingCatalogue(plans = repository.plans(), providers = providers)
    }

    suspend fun checkout(userId: Uuid, request: CheckoutRequest): CheckoutSession {
        val plan = repository.plan(request.planId)
            ?: throw ValidationException("planId", "Bunday tarif yo'q")
        val catalogue = catalogue(userId)
        if (request.provider !in catalogue.providers) {
            throw FeatureDisabledException(request.provider.name.lowercase())
        }

        val transaction = repository.createTransaction(
            userId = userId,
            planId = plan.id,
            provider = request.provider,
            amountMinor = plan.priceMinor,
            currency = plan.currency,
        )

        val url = when (request.provider) {
            PaymentProvider.PAYME -> paymeUrl(transaction.id, plan)
            PaymentProvider.CLICK -> clickUrl(transaction.id, plan)
            // A store purchase happens inside the platform's own sheet; there is no URL
            // to send her to, and the receipt comes back to /store/verify afterwards.
            PaymentProvider.APP_STORE, PaymentProvider.GOOGLE_PLAY ->
                throw ValidationException("provider", "Store xaridi ilova ichida bo'ladi")
        }

        return CheckoutSession(
            transactionId = transaction.id.toString(),
            provider = request.provider,
            url = url,
            amountMinor = plan.priceMinor,
            currency = plan.currency,
        )
    }

    suspend fun status(userId: Uuid, transactionId: Uuid): PaymentStatus {
        val transaction = repository.transaction(transactionId)
            ?: throw NotFoundException("To'lov topilmadi")
        // Reading someone else's payment is a not-found, not a forbidden: whether a
        // transaction id exists is not this caller's business either way.
        if (transaction.userId != userId) throw NotFoundException("To'lov topilmadi")

        return PaymentStatus(
            transactionId = transaction.id.toString(),
            state = transaction.state,
            provider = transaction.provider,
            planId = transaction.planId,
            amountMinor = transaction.amountMinor,
            paidAt = transaction.paidAt,
            consultationSessionId = transaction.consultationSessionId?.toString(),
            subscription = if (transaction.state == PaymentState.PAID && transaction.planId != null) {
                entitlements.subscriptionStatus(userId)
            } else {
                null
            },
        )
    }

    /**
     * Turns a paid transaction into a subscription.
     *
     * Idempotent on purpose: every provider retries, and the second delivery of the same
     * "paid" must find the work already done rather than granting a second month. The
     * transaction's own state is the guard.
     */
    suspend fun activate(transaction: TransactionRecord): Uuid? {
        // Paid and granted: nothing to do. Paid but not granted is the trace of a process
        // that died between the claim and the grant; the provider's retry — which lands
        // here — finishes the job instead of answering "done" from a row that is not.
        if (transaction.state == PaymentState.PAID && transaction.subscriptionId != null) {
            return transaction.subscriptionId
        }

        // A consultation's payment opens its window rather than granting a plan. The
        // opening is idempotent on the session's own state, so a provider's retry of a
        // payment already opened changes nothing.
        transaction.consultationSessionId?.let { sessionId ->
            if (transaction.state != PaymentState.PAID && !repository.claimPaid(transaction.id)) return null
            consultationPaid?.invoke(sessionId, transaction)
            return null
        }

        val plan = transaction.planId?.let { repository.plan(it) } ?: return null
        // The claim, not the state read above, decides who grants: a second delivery
        // racing this one loses here and returns what the winner recorded.
        if (transaction.state != PaymentState.PAID && !repository.claimPaid(transaction.id)) {
            return repository.transaction(transaction.id)?.subscriptionId
        }

        val subscriptionId = try {
            val current = entitlements.subscriptionStatus(transaction.userId)
            // Renewing before the old one lapses extends it rather than throwing the rest
            // away; anything expired starts from now.
            val startsFrom = current.expiresAt?.takeIf { it > now() } ?: now()
            subscriptions.grant(
                userId = transaction.userId,
                source = transaction.provider.asSubscriptionSource(),
                expiresAt = startsFrom + plan.period.duration(),
                productId = plan.id,
                externalId = transaction.externalId,
                reason = "payment ${transaction.id}",
            )
        } catch (e: Throwable) {
            repository.releasePaid(transaction.id, transaction.state)
            throw e
        }
        repository.attachSubscription(transaction.id, subscriptionId)
        return subscriptionId
    }

    // ---------------------------------------------------------------- consultations

    /**
     * The providers a consultation can be paid with. Outside production, with no provider
     * configured, both are offered and the link goes to [DEV_PAY_PATH] — a page on this
     * server that pays at once — so the whole flow can be tried without merchant keys.
     */
    suspend fun consultationProviders(userId: Uuid): List<PaymentProvider> {
        val live = catalogue(userId).providers.filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }
        if (live.isNotEmpty() || environment == Environment.PROD) return live
        return listOf(PaymentProvider.PAYME, PaymentProvider.CLICK)
    }

    /** A pending payment for a consultation window, and the link that pays it. [origin] is the caller's own host. */
    suspend fun consultationCheckout(
        userId: Uuid,
        sessionId: Uuid,
        amountMinor: Long,
        provider: PaymentProvider,
        origin: String,
    ): CheckoutSession {
        if (provider !in consultationProviders(userId)) throw FeatureDisabledException(provider.name.lowercase())
        val transaction = repository.createTransaction(
            userId = userId,
            planId = null,
            provider = provider,
            amountMinor = amountMinor,
            currency = "UZS",
            consultationSessionId = sessionId,
        )
        val live = catalogue(userId).providers.contains(provider)
        val url = when {
            !live -> "${origin.trimEnd('/')}$DEV_PAY_PATH/${transaction.id}"
            provider == PaymentProvider.PAYME -> paymeUrl(transaction.id, amountMinor)
            else -> clickUrl(transaction.id, amountMinor)
        }
        return CheckoutSession(
            transactionId = transaction.id.toString(),
            provider = provider,
            url = url,
            amountMinor = amountMinor,
            currency = "UZS",
        )
    }

    /**
     * The development page's payment: refused in production, and only for a consultation
     * still pending. Everything after it is the same path a real provider's callback takes.
     */
    suspend fun devPay(transactionId: Uuid): Boolean {
        if (environment == Environment.PROD) return false
        val transaction = repository.transaction(transactionId) ?: return false
        if (transaction.consultationSessionId == null) return false
        activate(transaction)
        return true
    }

    // ---------------------------------------------------------------- checkout links

    /**
     * Payme's hosted checkout takes its parameters base64-encoded in the path, in the
     * `m=<merchant>;ac.<field>=<value>;a=<amount>` form their documentation specifies.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private fun paymeUrl(transactionId: Uuid, plan: BillingPlan): String = paymeUrl(transactionId, plan.priceMinor)

    @OptIn(ExperimentalEncodingApi::class)
    private fun paymeUrl(transactionId: Uuid, amountMinor: Long): String {
        val merchant = config.payme.merchantId
            ?: throw FeatureDisabledException(PAYME_FLAG)
        val params = "m=$merchant;ac.${config.payme.accountField}=$transactionId;a=$amountMinor"
        return "${config.payme.checkoutUrl}/${Base64.encode(params.encodeToByteArray())}"
    }

    /** Click's checkout is plain query parameters, amount in so'm rather than tiyin. */
    private fun clickUrl(transactionId: Uuid, plan: BillingPlan): String = clickUrl(transactionId, plan.priceMinor)

    private fun clickUrl(transactionId: Uuid, amountMinor: Long): String {
        val serviceId = config.click.serviceId ?: throw FeatureDisabledException(CLICK_FLAG)
        val merchantId = config.click.merchantId ?: throw FeatureDisabledException(CLICK_FLAG)
        val amount = amountMinor.toSum()
        return "${config.click.checkoutUrl}?service_id=$serviceId" +
            "&merchant_id=$merchantId" +
            "&amount=$amount" +
            "&transaction_param=$transactionId"
    }

    companion object {
        const val PAYME_FLAG = "payme_checkout"
        const val CLICK_FLAG = "click_checkout"
        const val STORE_FLAG = "store_iap"
        const val DEV_PAY_PATH = "/v1/billing/dev-pay"
    }
}

/** A month is 30 days and a year is 365: predictable, and never short of what was sold. */
internal fun BillingPeriod.duration() = when (this) {
    BillingPeriod.MONTH -> 30.days
    BillingPeriod.YEAR -> 365.days
}

internal fun PaymentProvider.asSubscriptionSource(): SubscriptionSource = when (this) {
    PaymentProvider.PAYME -> SubscriptionSource.PAYME
    PaymentProvider.CLICK -> SubscriptionSource.CLICK
    PaymentProvider.APP_STORE -> SubscriptionSource.APP_STORE
    PaymentProvider.GOOGLE_PLAY -> SubscriptionSource.GOOGLE_PLAY
}

/** Tiyin to so'm. Click quotes whole so'm, and 39 900 so'm is 3 990 000 tiyin. */
internal fun Long.toSum(): String {
    val whole = this / 100
    val fraction = this % 100
    return if (fraction == 0L) whole.toString() else "$whole.${fraction.toString().padStart(2, '0')}"
}
