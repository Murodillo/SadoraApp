package uz.sadora.server.payrequest

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.AccountKind
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.BillingPeriod
import uz.sadora.contract.BillingPlan
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.CreatePaymentRequest
import uz.sadora.contract.IncomingPaymentRequest
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PayPaymentRequest
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequest
import uz.sadora.contract.PaymentRequestKind
import uz.sadora.contract.PaymentRequestLimits
import uz.sadora.contract.PaymentRequestStatus
import uz.sadora.contract.PaymentRequestStorePurchase
import uz.sadora.contract.PaymentState
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.billing.BillingRepository
import uz.sadora.server.billing.BillingService
import uz.sadora.server.billing.GiftService
import uz.sadora.server.billing.ReceiptRejectedException
import uz.sadora.server.billing.StoreVerifier
import uz.sadora.server.billing.TransactionRecord
import uz.sadora.server.consultation.ConsultationService
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.parseUuid
import uz.sadora.server.core.randomToken
import uz.sadora.server.core.sha256
import uz.sadora.server.notify.NotificationPolicy
import uz.sadora.server.pet.wireKey
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.partner.PartnerRepository
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * "Ask Yaqinim to pay".
 *
 * She makes a request; the person who follows her in the app is told by push, and anyone
 * else can be sent its browser link. Whoever pays, the money is recorded as hers to
 * receive — a gift plan's days or a consultation window — through the same activation a
 * payment of her own takes, so nothing here grants anything by itself. Store payments come
 * in as receipts and are checked with the store, bought for the payer's own account.
 */
class PaymentRequestService(
    private val repository: PaymentRequestRepository,
    private val billing: BillingService,
    private val billingRepository: BillingRepository,
    private val gifts: GiftService,
    private val verifier: StoreVerifier,
    private val consultations: ConsultationService,
    private val links: PartnerRepository,
    private val users: UserRepository,
    private val notifications: NotificationRepository,
    private val audit: AuditService,
    private val publicBaseUrl: String,
) {
    /** The legendary pet's sale, for a PET request. Set once at wiring. */
    var petShop: uz.sadora.server.pet.PetShopService? = null

    // ---------------------------------------------------------------- her side

    suspend fun create(ownerId: Uuid, request: CreatePaymentRequest): PaymentRequest {
        val owner = requireActive(ownerId)
        if (owner.accountKind == AccountKind.PARTNER) throw ForbiddenException(message = "Bu hisobdan so'rov yuborib bo'lmaydi")
        val note = request.note?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > PaymentRequestLimits.NOTE_MAX) {
            throw ValidationException("note", "Eng ko'pi ${PaymentRequestLimits.NOTE_MAX} belgi")
        }
        val at = now()
        expireIfDue(repository.openOf(ownerId), at)
        if (repository.openOf(ownerId) != null) throw ConflictException("Sizda ochiq so'rov bor")

        var planId: String? = null
        var sessionId: Uuid? = null
        var doctorId: Uuid? = null
        var pet: String? = null
        val amount: Long
        when (request.kind) {
            PaymentRequestKind.PREMIUM -> {
                val period = request.period ?: throw ValidationException("period", "Ko'rsatilishi shart")
                val plan = giftPlan(period)
                planId = plan.id
                amount = plan.priceMinor
            }
            PaymentRequestKind.CONSULTATION -> {
                doctorId = request.doctorId?.let { parseUuid(it, "doctorId") }
                    ?: throw ValidationException("doctorId", "Ko'rsatilishi shart")
                val (session, _) = consultations.pendingSessionFor(ownerId, doctorId)
                sessionId = session.id
                amount = session.priceMinor
            }
            PaymentRequestKind.PET -> {
                val wanted = request.pet ?: throw ValidationException("pet", "Ko'rsatilishi shart")
                val product = shop().forSale(ownerId, wanted)
                pet = product.pet.wireKey
                amount = product.priceMinor
            }
        }

        val link = links.activeOf(ownerId)?.takeIf { it.partnerId != null && it.acceptsPaymentRequests }
        val token = randomToken(TOKEN_BYTES)
        val created = try {
            repository.create(
                ownerId = ownerId,
                kind = request.kind,
                planId = planId,
                consultationSessionId = sessionId,
                doctorId = doctorId,
                amountMinor = amount,
                note = note,
                partnerLinkId = link?.id,
                webTokenHash = sha256(token),
                at = at,
                expiresAt = at + PaymentRequestLimits.OPEN_DAYS.days,
                pet = pet,
            )
        } catch (e: Exception) {
            // Two taps at once: the unique index lets one through.
            if (repository.openOf(ownerId) != null) throw ConflictException("Sizda ochiq so'rov bor")
            throw e
        }
        record(ownerId, "payment_request.created", created.id, mapOf("kind" to request.kind.name.lowercase()))

        link?.partnerId?.let { users.findById(it) }?.let { partner ->
            val text = PaymentRequestPhrases.asked(owner.firstName(), created.kind, periodOf(created), partner.language)
            pushRespectingQuiet(partner, "payreq:${created.id}", text)
        }
        return created.toDto(shareUrl = shareUrl(token))
    }

    /** Her newest request, while it is open or recently closed; null otherwise. */
    suspend fun current(ownerId: Uuid): PaymentRequest? {
        val at = now()
        val latest = repository.latestOf(ownerId) ?: return null
        val record = if (expireIfDue(latest, at)) repository.byId(latest.id) ?: return null else latest
        if (record.status != PaymentRequestStatus.OPEN && (record.closedAt ?: at) < at - SHOW_CLOSED_FOR) return null
        return record.toDto()
    }

    suspend fun cancel(ownerId: Uuid, id: String): PaymentRequest {
        val record = own(ownerId, id)
        if (repository.close(record.id, PaymentRequestStatus.CANCELLED, now())) {
            record(ownerId, "payment_request.cancelled", record.id)
        }
        return (repository.byId(record.id) ?: record).toDto()
    }

    /** A fresh browser link; the one before it stops working. */
    suspend fun shareLink(ownerId: Uuid, id: String): PaymentRequest {
        val record = own(ownerId, id)
        if (record.status != PaymentRequestStatus.OPEN) throw ConflictException("So'rov yopilgan")
        val token = randomToken(TOKEN_BYTES)
        repository.rotateToken(record.id, sha256(token))
        return record.toDto(shareUrl = shareUrl(token))
    }

    // ---------------------------------------------------------------- the payer's side

    /** Open requests from the people this account follows. */
    suspend fun incoming(payerId: Uuid): List<IncomingPaymentRequest> {
        val following = links.followingOf(payerId)
            .filter { it.link.status == PartnerLinkStatus.ACTIVE && it.link.acceptsPaymentRequests }
        if (following.isEmpty()) return emptyList()
        val names = following.associate { it.link.id to it.ownerName }
        val at = now()
        val providers = payerProviders(payerId)
        return repository.openOnLinks(names.keys)
            .filterNot { expireIfDue(it, at) }
            .map { record ->
                IncomingPaymentRequest(
                    id = record.id.toString(),
                    linkId = record.partnerLinkId.toString(),
                    fromName = names[record.partnerLinkId].orEmpty().trim().substringBefore(' '),
                    kind = record.kind,
                    period = periodOf(record),
                    doctorName = record.doctorId?.let { consultations.doctorName(it) },
                    pet = petOf(record),
                    amountMinor = record.amountMinor,
                    note = record.note,
                    createdAt = record.createdAt,
                    expiresAt = record.expiresAt,
                    plans = if (record.kind == PaymentRequestKind.PREMIUM) billingRepository.giftPlans() else emptyList(),
                    petProduct = petShop?.product(record.pet)?.let {
                        uz.sadora.contract.PetProduct(it.pet, record.amountMinor, it.currency, it.appStoreProductId, it.googlePlayProductId, providers)
                    },
                    providers = providers,
                )
            }
    }

    /** "Not now". She reads it as closed, nothing more. */
    suspend fun decline(payerId: Uuid, id: String) {
        val record = asked(payerId, id)
        if (repository.close(record.id, PaymentRequestStatus.DECLINED, now())) {
            record(payerId, "payment_request.declined", record.id)
        }
    }

    /** Payme or Click, from the app of the person she asked. */
    suspend fun pay(payerId: Uuid, id: String, request: PayPaymentRequest, origin: String): CheckoutSession =
        checkout(asked(payerId, id), payerId, request, origin)

    /** A gift plan, or the legendary pet, bought in the payer's own store account. */
    suspend fun payInStore(payerId: Uuid, id: String, purchase: PaymentRequestStorePurchase): PaymentRequest {
        val record = asked(payerId, id, requireOpen = false)
        if (record.kind == PaymentRequestKind.CONSULTATION) throw ValidationException("kind", "Konsultatsiya store orqali to'lanmaydi")
        if (purchase.provider != PaymentProvider.APP_STORE && purchase.provider != PaymentProvider.GOOGLE_PLAY) {
            throw ValidationException("provider", "Bu provayder store emas")
        }
        fun matches(appStore: String?, googlePlay: String?) = when (purchase.provider) {
            PaymentProvider.APP_STORE -> appStore == purchase.productId
            else -> googlePlay == purchase.productId
        }
        val plan = if (record.kind == PaymentRequestKind.PREMIUM) {
            billingRepository.giftPlans().firstOrNull { matches(it.appStoreProductId, it.googlePlayProductId) }
                ?: throw ValidationException("productId", "Bunday mahsulot yo'q")
        } else {
            null
        }
        val pet = if (record.kind == PaymentRequestKind.PET) {
            shop().product(record.pet)?.takeIf { matches(it.appStoreProductId, it.googlePlayProductId) }
                ?: throw ValidationException("productId", "Bunday mahsulot yo'q")
        } else {
            null
        }

        val verified = try {
            verifier.verifyOneTime(purchase.provider, purchase.productId, purchase.token)
        } catch (rejected: ReceiptRejectedException) {
            throw ValidationException("token", rejected.message ?: "Chek tasdiqlanmadi")
        }
        // Bought for the payer's own account: one receipt cannot be posted from another.
        if (verified.accountId != payerId.toString()) throw ValidationException("token", "Bu xarid boshqa hisobga tegishli")

        val existing = billingRepository.byExternalId(purchase.provider, verified.transactionId)
        if (existing != null && existing.paymentRequestId != record.id) throw ConflictException("Bu chek allaqachon qayd etilgan")
        // A receipt for a request that closed meanwhile still bought her the days; a new
        // purchase is only taken while the request is open.
        if (existing == null && record.status != PaymentRequestStatus.OPEN) throw ConflictException("So'rov yopilgan")
        val transaction = existing ?: billingRepository.createTransaction(
            userId = record.ownerId,
            planId = plan?.id,
            provider = purchase.provider,
            amountMinor = plan?.priceMinor ?: record.amountMinor,
            currency = plan?.currency ?: "UZS",
            payerId = payerId,
            paymentRequestId = record.id,
            pet = pet?.pet?.wireKey,
        ).also { created ->
            if (!billingRepository.attachExternalId(created.id, verified.transactionId, null)) {
                throw ConflictException("Bu chek allaqachon qayd etilgan")
            }
        }
        billing.activate(billingRepository.transaction(transaction.id) ?: transaction)
        return (repository.byId(record.id) ?: record).toDto()
    }

    // ---------------------------------------------------------------- the browser link

    /** What the browser page shows; null when the link is not a live one. */
    suspend fun webView(token: String): WebRequest? {
        if (token.length !in TOKEN_LENGTH) return null
        val record = repository.byTokenHash(sha256(token)) ?: return null
        expireIfDue(record, now())
        val fresh = repository.byId(record.id) ?: return null
        val owner = users.findById(fresh.ownerId)?.takeIf { it.status == AccountStatus.ACTIVE } ?: return null
        return WebRequest(
            request = fresh.toDto(),
            ownerName = owner.firstName(),
            language = owner.language,
            plans = if (fresh.kind == PaymentRequestKind.PREMIUM) billingRepository.giftPlans() else emptyList(),
            providers = billing.consultationProviders(owner.id),
        )
    }

    /** A browser payer's checkout: no account, so the payment names no payer. */
    suspend fun webCheckout(token: String, provider: PaymentProvider, planId: String?, origin: String): CheckoutSession {
        if (token.length !in TOKEN_LENGTH) throw NotFoundException("Topilmadi")
        val record = repository.byTokenHash(sha256(token)) ?: throw NotFoundException("Topilmadi")
        if (expireIfDue(record, now()) || record.status != PaymentRequestStatus.OPEN) throw ConflictException("So'rov yopilgan")
        return checkout(record, null, PayPaymentRequest(provider, planId), origin)
    }

    // ---------------------------------------------------------------- what payment does

    /** The money arrived: the request is paid and she is told who gave it. */
    suspend fun onPaid(transaction: TransactionRecord) {
        val requestId = transaction.paymentRequestId ?: return
        val record = repository.byId(requestId) ?: return
        if (!repository.markPaid(record.id, transaction.payerId, transaction.id, now())) return
        val owner = users.findById(record.ownerId) ?: return
        val payer = transaction.payerId?.let { users.findById(it) }?.firstName()
        val period = transaction.planId?.let { billingRepository.plan(it) }?.period ?: periodOf(record)
        val text = PaymentRequestPhrases.paid(payer, record.kind, period, owner.language)
        enqueue(owner.id, "payreq_paid:${record.id}", text, now())
    }

    /** A gift's money went back: its days go too. */
    suspend fun refundGift(transactionId: Uuid, adminId: Uuid?): Boolean {
        val transaction = billingRepository.transaction(transactionId) ?: throw NotFoundException("To'lov topilmadi")
        val planId = transaction.planId
        if (transaction.state != PaymentState.PAID || planId == null || !billingRepository.isGiftPlan(planId)) {
            throw ValidationException("id", "Faqat to'langan sovg'ani qaytarish mumkin")
        }
        if (!billingRepository.markRefunded(transaction.id)) return false
        gifts.revoke(transaction.id)
        audit.record(
            AuditEntry(
                actorType = if (adminId != null) ActorType.ADMIN else ActorType.SYSTEM,
                actorId = adminId,
                action = "payment.gift_refunded",
                entityType = "payment_transaction",
                entityId = transaction.id.toString(),
            ),
        )
        users.findById(transaction.userId)?.let { owner ->
            enqueue(owner.id, "gift_refunded:${transaction.id}", PaymentRequestPhrases.refunded(owner.language), now())
        }
        return true
    }

    /** The quarter-hourly pass: requests past their week close, and the unanswered get one reminder. */
    suspend fun tick() {
        val at = now()
        repository.dueToExpire(at, BATCH).forEach { repository.close(it.id, PaymentRequestStatus.EXPIRED, at) }
        repository.dueForReminder(at - PaymentRequestLimits.REMIND_AFTER_HOURS.hours, BATCH).forEach { record ->
            val link = record.partnerLinkId?.let { links.byId(it) }
                ?.takeIf { it.status == PartnerLinkStatus.ACTIVE && it.acceptsPaymentRequests }
            val partner = link?.partnerId?.let { users.findById(it) }
            if (!repository.markReminded(record.id, at)) return@forEach
            val owner = users.findById(record.ownerId) ?: return@forEach
            if (partner != null) {
                pushRespectingQuiet(partner, "payreq_remind:${record.id}", PaymentRequestPhrases.reminder(owner.firstName(), partner.language))
            }
        }
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun checkout(record: PaymentRequestRecord, payerId: Uuid?, request: PayPaymentRequest, origin: String): CheckoutSession {
        if (record.status != PaymentRequestStatus.OPEN) throw ConflictException("So'rov yopilgan")
        return when (record.kind) {
            PaymentRequestKind.PREMIUM -> {
                val planId = request.planId ?: record.planId
                val plan = billingRepository.giftPlans().firstOrNull { it.id == planId }
                    ?: throw ValidationException("planId", "Bunday tarif yo'q")
                billing.requestCheckout(
                    beneficiaryId = record.ownerId,
                    payerId = payerId,
                    requestId = record.id,
                    planId = plan.id,
                    consultationSessionId = null,
                    amountMinor = plan.priceMinor,
                    provider = request.provider,
                    origin = origin,
                )
            }
            PaymentRequestKind.CONSULTATION -> {
                val session = record.consultationSessionId?.let { consultations.payableSession(it) }
                    ?.takeIf { it.priceMinor == record.amountMinor }
                    ?: run {
                        repository.close(record.id, PaymentRequestStatus.EXPIRED, now())
                        throw ConflictException("Konsultatsiya narxi o'zgargan yoki allaqachon to'langan")
                    }
                billing.requestCheckout(
                    beneficiaryId = record.ownerId,
                    payerId = payerId,
                    requestId = record.id,
                    planId = null,
                    consultationSessionId = session.id,
                    amountMinor = session.priceMinor,
                    provider = request.provider,
                    origin = origin,
                )
            }
            PaymentRequestKind.PET -> {
                // She bought it herself meanwhile: a second payment would buy nothing.
                closeIfOwned(record)
                billing.requestCheckout(
                beneficiaryId = record.ownerId,
                payerId = payerId,
                requestId = record.id,
                planId = null,
                consultationSessionId = null,
                amountMinor = record.amountMinor,
                provider = request.provider,
                origin = origin,
                pet = record.pet,
            )
            }
        }
    }

    /**
     * Humo became hers by some other payment: an open request for it would only take a
     * second payment for nothing, so it closes. [except] is the request that payment answered.
     */
    suspend fun closeOpenPetRequests(ownerId: Uuid, petKey: String, except: Uuid?) {
        val open = repository.openOf(ownerId) ?: return
        if (open.kind != PaymentRequestKind.PET || open.pet != petKey || open.id == except) return
        repository.close(open.id, PaymentRequestStatus.CANCELLED, now())
    }

    private suspend fun closeIfOwned(record: PaymentRequestRecord) {
        if (!shop().owns(record.ownerId, record.pet)) return
        repository.close(record.id, PaymentRequestStatus.CANCELLED, now())
        throw ConflictException("So'rov yopilgan")
    }

    /** Payme and Click where they are offered, and the stores when in-app purchase is on. */
    private suspend fun payerProviders(payerId: Uuid): List<PaymentProvider> {
        val stores = billing.catalogue(payerId).providers.filter { it == PaymentProvider.APP_STORE || it == PaymentProvider.GOOGLE_PLAY }
        return billing.consultationProviders(payerId) + stores
    }

    private suspend fun giftPlan(period: BillingPeriod): BillingPlan =
        billingRepository.giftPlans().firstOrNull { it.period == period }
            ?: throw ValidationException("period", "Bunday tarif yo'q")

    private fun shop(): uz.sadora.server.pet.PetShopService =
        petShop ?: throw uz.sadora.server.core.FeatureDisabledException(uz.sadora.server.pet.PetShopService.SALE_FLAG)

    private fun petOf(record: PaymentRequestRecord): uz.sadora.contract.PetKind? =
        record.pet?.let { key -> uz.sadora.contract.PetKind.entries.firstOrNull { it.wireKey == key } }

    private suspend fun periodOf(record: PaymentRequestRecord): BillingPeriod? =
        record.planId?.let { billingRepository.plan(it) }?.period

    /** Closes a request whose week is up; true when it is (now) closed that way. */
    private suspend fun expireIfDue(record: PaymentRequestRecord?, at: Instant): Boolean {
        if (record == null || record.status != PaymentRequestStatus.OPEN || record.expiresAt > at) return false
        repository.close(record.id, PaymentRequestStatus.EXPIRED, at)
        return true
    }

    private suspend fun own(ownerId: Uuid, id: String): PaymentRequestRecord =
        repository.byId(parseUuid(id, "id"))?.takeIf { it.ownerId == ownerId } ?: throw NotFoundException("Topilmadi")

    /** A request sent to this account along a live link; anything else reads as not there. */
    private suspend fun asked(payerId: Uuid, id: String, requireOpen: Boolean = true): PaymentRequestRecord {
        requireActive(payerId)
        val record = repository.byId(parseUuid(id, "id")) ?: throw NotFoundException("Topilmadi")
        val link = record.partnerLinkId?.let { links.byId(it) }
            ?.takeIf { it.partnerId == payerId && it.status == PartnerLinkStatus.ACTIVE }
            ?: throw NotFoundException("Topilmadi")
        if (!link.acceptsPaymentRequests) throw NotFoundException("Topilmadi")
        if (requireOpen && (expireIfDue(record, now()) || record.status != PaymentRequestStatus.OPEN)) {
            throw ConflictException("So'rov yopilgan")
        }
        return record
    }

    /**
     * A request to pay is never capped away, but it waits out the person's quiet hours:
     * money is not a thing to wake anyone for.
     */
    private suspend fun pushRespectingQuiet(to: UserRecord, dedupeKey: String, text: PaymentRequestPhrases.Text) {
        val settings = notifications.settingsOf(to.id)
        if (!settings.enabled || !settings.isCategoryEnabled(NotificationCategory.PARTNER)) return
        val at = now()
        val zone = runCatching { TimeZone.of(to.timezone) }.getOrElse { TimeZone.of("Asia/Tashkent") }
        val local = at.toLocalDateTime(zone)
        val until = settings.quietUntil
        val sendAt = if (until != null && NotificationPolicy.isQuiet(local.time, settings.quietFrom, until)) {
            val today = local.date.atTime(until)
            val next = if (today > local) today else local.date.plus(1, DateTimeUnit.DAY).atTime(until)
            next.toInstant(zone)
        } else {
            at
        }
        enqueue(to.id, dedupeKey, text, sendAt)
    }

    private suspend fun enqueue(to: Uuid, dedupeKey: String, text: PaymentRequestPhrases.Text, at: Instant) {
        runCatching {
            notifications.enqueue(
                userId = to,
                category = NotificationCategory.PARTNER,
                title = text.title,
                body = text.body,
                scheduledFor = at,
                dedupeKey = dedupeKey,
                status = NotificationStatus.QUEUED,
                suppressedReason = null,
                link = LINK,
            )
        }
    }

    private suspend fun requireActive(userId: Uuid): UserRecord {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        if (user.status != AccountStatus.ACTIVE) throw ForbiddenException(uz.sadora.contract.ErrorCodes.ACCOUNT_BLOCKED, "Hisob faol emas")
        return user
    }

    private suspend fun record(actor: Uuid, action: String, id: Uuid, metadata: Map<String, String> = emptyMap()) {
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = actor,
                action = action,
                entityType = "payment_request",
                entityId = id.toString(),
                metadata = metadata,
            ),
        )
    }

    private suspend fun PaymentRequestRecord.toDto(shareUrl: String? = null) = PaymentRequest(
        id = id.toString(),
        kind = kind,
        status = status,
        period = periodOf(this),
        doctorName = doctorId?.let { consultations.doctorName(it) },
        pet = petOf(this),
        amountMinor = amountMinor,
        note = note,
        sentToPartner = partnerLinkId != null,
        shareUrl = shareUrl,
        createdAt = createdAt,
        expiresAt = expiresAt,
        closedAt = closedAt,
    )

    private fun shareUrl(token: String) = "$publicBaseUrl/pr/$token"

    private fun UserRecord.firstName(): String = name.trim().substringBefore(' ').ifBlank { "Sadora" }

    /** The browser page's content. */
    data class WebRequest(
        val request: PaymentRequest,
        val ownerName: String,
        val language: uz.sadora.contract.Language,
        val plans: List<BillingPlan>,
        val providers: List<PaymentProvider>,
    )

    companion object {
        const val LINK = "sadora://yaqinim"
        private const val TOKEN_BYTES = 32
        private val TOKEN_LENGTH = 40..48
        private const val BATCH = 200
        /** How long a closed request still shows on her screen, so she sees how it ended. */
        private val SHOW_CLOSED_FOR = 3.days
    }
}
