package uz.sadora.server.partner

import java.security.SecureRandom
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.AcceptPartnerInviteRequest
import uz.sadora.contract.AccountKind
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.CoinReasons
import uz.sadora.contract.CreatePartnerInviteRequest
import uz.sadora.contract.CreatePartnerWebLinkRequest
import uz.sadora.contract.PartnerMessage
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerMessages
import uz.sadora.contract.PartnerWebLink
import uz.sadora.contract.SendPartnerMessageRequest
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.FollowedPerson
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.Limits
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.PartnerAppointment
import uz.sadora.contract.PartnerCare
import uz.sadora.contract.PartnerCycle
import uz.sadora.contract.PartnerDay
import uz.sadora.contract.PartnerInvite
import uz.sadora.contract.PartnerLink
import uz.sadora.contract.PartnerLinkStatus
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerPregnancy
import uz.sadora.contract.PartnerState
import uz.sadora.contract.PartnerView
import uz.sadora.contract.StageEventKind
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.parseUuid
import uz.sadora.server.core.sha256
import uz.sadora.server.health.AppointmentRepository
import uz.sadora.server.health.HealthRepository
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.StageEventRepository
import uz.sadora.server.notify.DeliveryDecision
import uz.sadora.server.notify.NotificationPolicy
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.rewards.RewardsService
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * Yaqinim: one person she lets see how she is.
 *
 * Three rules shape it, borrowed from the doctor's QR code and made stricter. The code is
 * short so it can be read out over the phone, so it is stored hashed, lives two days, is
 * typed behind a rate limit, and opens nothing by itself: she still has to say yes to the
 * name that typed it. The view is assembled from her records at the moment it is opened,
 * from the parts she ticked and nothing else — a part she did not tick is not filtered on
 * the phone, it is never read. And either of them can end it, at once, without asking.
 *
 * What is never shown, whatever she ticks: her notes and journal, the intimate and
 * contraception records, her weight, the mood questionnaire, the AI chat, the anonymous
 * chat, her consultations. Those have no permission because there is no case for them.
 */
class PartnerService(
    private val links: PartnerRepository,
    private val users: UserRepository,
    private val health: HealthService,
    private val healthRecords: HealthRepository,
    private val appointments: AppointmentRepository,
    private val stageEvents: StageEventRepository,
    private val notifications: NotificationRepository,
    private val audit: AuditService,
    private val publicBaseUrl: String,
    /** Optional only for the tests that build the service by hand. */
    private val rewards: RewardsService? = null,
    private val messages: PartnerMessageRepository = PartnerMessageRepository(),
) {
    private val random = SecureRandom()

    // ---------------------------------------------------------------- both sides

    suspend fun state(userId: Uuid): PartnerState {
        val live = links.liveOf(userId)
        val at = now()
        val invite = live?.takeIf { it.status == PartnerLinkStatus.INVITED && it.isCodeLive(at) }
        val link = live?.takeIf { it.partnerId != null && it.status != PartnerLinkStatus.INVITED }
        return PartnerState(
            invite = invite?.toInvite(code = null),
            link = link?.toLink()?.copy(unread = messages.unreadFor(link.id, userId)),
            following = following(userId),
            webLink = messages.liveWebLinkOf(userId, at),
        )
    }

    private suspend fun following(userId: Uuid): List<FollowedPerson> {
        val rows = links.followingOf(userId)
        val unread = messages.unreadFor(rows.map { it.link.id }, userId)
        return rows.map { row ->
            FollowedPerson(
                linkId = row.link.id.toString(),
                name = row.ownerName,
                relation = row.link.relation,
                status = row.link.status,
                unread = unread[row.link.id] ?: 0,
                acceptsPaymentRequests = row.link.acceptsPaymentRequests,
            )
        }
    }

    // ---------------------------------------------------------------- messages

    /** The last few things the two of them sent, from whichever side asks. */
    suspend fun messages(userId: Uuid, linkId: String): PartnerMessages {
        val link = sharedLink(userId, linkId)
        return PartnerMessages(
            items = messages.recent(link.id, Limits.PARTNER_MESSAGES_SHOWN).map { it.toDto(userId) },
            unread = messages.unreadFor(link.id, userId),
        )
    }

    suspend fun markRead(userId: Uuid, linkId: String): PartnerMessages {
        val link = sharedLink(userId, linkId)
        messages.markRead(link.id, userId, now())
        return messages(userId, linkId)
    }

    /**
     * A heart, a request, a short line. Only on an active link — a paused one is her
     * saying "not now", and that covers this too — and at most a day's worth.
     */
    suspend fun send(userId: Uuid, linkId: String, request: SendPartnerMessageRequest): PartnerMessage {
        val link = sharedLink(userId, linkId)
        if (link.status != PartnerLinkStatus.ACTIVE) throw ConflictException("Ulanish hozir faol emas")
        val text = if (request.kind == PartnerMessageKind.CUSTOM) {
            val trimmed = request.text?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
            if (trimmed.isEmpty()) throw ValidationException("text", "Bo'sh bo'lishi mumkin emas")
            if (trimmed.length > Limits.PARTNER_MESSAGE_MAX) {
                throw ValidationException("text", "Eng ko'pi ${Limits.PARTNER_MESSAGE_MAX} belgi")
            }
            trimmed
        } else {
            null
        }
        val at = now()
        if (messages.sentSince(link.id, userId, at - 1.days) >= Limits.PARTNER_MESSAGES_PER_DAY) {
            throw uz.sadora.server.core.RateLimitedException("Bugun juda ko'p xabar yuborildi")
        }
        val sender = requireActive(userId)
        val saved = messages.add(link.id, userId, request.kind, text, at)

        val recipientId = if (userId == link.ownerId) link.partnerId else link.ownerId
        recipientId?.let { users.findById(it) }?.let { recipient ->
            notify(
                recipient.id,
                "partner_msg:${saved.id}",
                PartnerPhrases.message(sender.firstName(), request.kind, text, recipient.language),
                NotificationCategory.SYSTEM,
            )
        }
        return saved.toDto(userId)
    }

    /** A live link this account is on, from either end. Codes nobody typed are not one. */
    private suspend fun sharedLink(userId: Uuid, linkId: String): PartnerLinkRecord =
        links.byId(parseUuid(linkId))
            ?.takeIf { it.status != PartnerLinkStatus.INVITED && (it.ownerId == userId || it.partnerId == userId) }
            ?: throw NotFoundException("Topilmadi")

    private fun PartnerMessageRecord.toDto(readerId: Uuid) = PartnerMessage(
        id = id.toString(),
        linkId = linkId.toString(),
        fromMe = senderId == readerId,
        kind = kind,
        text = body,
        createdAt = createdAt,
        readAt = readAt,
    )

    // ---------------------------------------------------------------- the web link

    /** A browser link for someone without the app. A new one retires the one before. */
    suspend fun createWebLink(ownerId: Uuid, request: CreatePartnerWebLinkRequest, ip: String?): PartnerWebLink {
        val owner = requireActive(ownerId)
        if (owner.accountKind == AccountKind.PARTNER) {
            throw ForbiddenException(message = "Bu hisobda ulashiladigan ma'lumot yo'q")
        }
        val range = Limits.PARTNER_WEB_TTL_HOURS
        if (request.ttlHours !in range) {
            throw ValidationException("ttlHours", "${range.first}–${range.last} soat oralig'ida")
        }
        val at = now()
        messages.revokeWebLinks(ownerId, at)
        val token = uz.sadora.server.core.randomToken(WEB_TOKEN_BYTES)
        val created = messages.createWebLink(ownerId, sha256(token), request.permissions, at + request.ttlHours.hours, at)
        record(
            ownerId,
            AuditActions.PARTNER_WEB_CREATED,
            Uuid.parse(created.id),
            ip,
            mapOf("ttlHours" to request.ttlHours.toString(), "shown" to request.permissions.shownKeys().joinToString(",")),
        )
        return created.copy(url = "$publicBaseUrl/yv/$token")
    }

    suspend fun revokeWebLink(ownerId: Uuid, ip: String?): PartnerState {
        val live = messages.liveWebLinkOf(ownerId, now())
        if (messages.revokeWebLinks(ownerId, now()) > 0 && live != null) {
            record(ownerId, AuditActions.PARTNER_WEB_REVOKED, Uuid.parse(live.id), ip)
        }
        return state(ownerId)
    }

    /**
     * The page behind a web link, or null for every way it can be unusable — unknown,
     * expired, revoked, an account that is gone — so the page tells nobody which it was.
     */
    suspend fun openWebLink(token: String, language: Language?, ip: String?, userAgent: String?): PartnerWebView? {
        if (token.length !in WEB_TOKEN_LENGTH) return null
        val record = messages.webLinkByTokenHash(sha256(token)) ?: return null
        val at = now()
        if (!record.isLive(at)) return null
        val owner = users.findById(record.ownerId)?.takeIf { it.status == AccountStatus.ACTIVE } ?: return null
        val id = Uuid.parse(record.link.id)
        messages.recordWebView(id, at)
        audit.record(
            AuditEntry(
                actorType = ActorType.SYSTEM,
                actorId = owner.id,
                action = AuditActions.PARTNER_WEB_VIEWED,
                entityType = "partner_web_link",
                entityId = record.link.id,
                ip = ip,
                userAgent = userAgent,
            ),
        )
        val today = at.dayIn(owner.timezone)
        val base = PartnerView(
            linkId = record.link.id,
            status = PartnerLinkStatus.ACTIVE,
            name = owner.firstName(),
            relation = uz.sadora.contract.PartnerRelation.OTHER,
            permissions = record.link.permissions,
            generatedAt = at,
            today = today,
        )
        val shown = language ?: owner.language
        return PartnerWebView(build(base, owner, record.link.permissions, today, shown), shown)
    }

    // ---------------------------------------------------------------- hers

    /**
     * A new code. An earlier code nobody typed is retired by it; a person already linked
     * is not — she ends that first, on purpose, so a new code is never a silent swap.
     */
    suspend fun createInvite(ownerId: Uuid, request: CreatePartnerInviteRequest, ip: String?): PartnerInvite {
        val owner = requireActive(ownerId)
        if (owner.accountKind == AccountKind.PARTNER) {
            throw ForbiddenException(message = "Bu hisobda ulashiladigan ma'lumot yo'q")
        }
        val at = now()
        links.liveOf(ownerId)?.let { live ->
            if (live.status != PartnerLinkStatus.INVITED) {
                throw ConflictException("Sizda yaqin allaqachon ulangan — avval uni uzing")
            }
            links.end(live.id, ENDED_SUPERSEDED, at)
        }

        val expiresAt = at + Limits.PARTNER_INVITE_HOURS.hours
        // A code that happens to be out already fails the unique index: draw again, a few
        // times at most. Thirty-one letters to the eighth power makes a second try rare.
        var failure: Throwable? = null
        repeat(CODE_ATTEMPTS) {
            val code = newCode()
            val created = runCatching { links.createInvite(ownerId, request.relation, sha256(code), expiresAt, at) }
                .onFailure { failure = it }
                .getOrNull() ?: return@repeat
            record(ownerId, AuditActions.PARTNER_INVITED, created.id, ip, mapOf("relation" to request.relation.name.lowercase()))
            return created.toInvite(code)
        }
        throw failure ?: IllegalStateException("no code")
    }

    suspend fun approve(ownerId: Uuid, ip: String?): PartnerState {
        val live = links.liveOf(ownerId)?.takeIf { it.status == PartnerLinkStatus.PENDING }
            ?: throw NotFoundException("So'rov topilmadi")
        if (!links.approve(live.id, now())) throw NotFoundException("So'rov topilmadi")
        record(ownerId, AuditActions.PARTNER_APPROVED, live.id, ip)

        val owner = requireActive(ownerId)
        live.partnerId?.let { partnerId ->
            val partner = users.findById(partnerId)
            if (partner != null) {
                val text = PartnerPhrases.approved(owner.firstName(), partner.language)
                notify(partnerId, "partner_approved:${live.id}", text, NotificationCategory.SYSTEM)
            }
        }
        // Once, ever: the reference makes a second link pay nothing.
        runCatching {
            rewards?.grant(ownerId, CoinReasons.PARTNER_LINKED, language = owner.language, reference = "once")
        }
        return state(ownerId)
    }

    suspend fun savePermissions(ownerId: Uuid, permissions: PartnerPermissions, ip: String?): PartnerState {
        val live = links.liveOf(ownerId) ?: throw NotFoundException("Yaqin topilmadi")
        links.savePermissions(live.id, permissions)
        record(
            ownerId,
            AuditActions.PARTNER_PERMISSIONS,
            live.id,
            ip,
            mapOf("shown" to permissions.shownKeys().joinToString(",")),
        )
        return state(ownerId)
    }

    suspend fun pause(ownerId: Uuid, paused: Boolean, ip: String?): PartnerState {
        val live = links.liveOf(ownerId)
            ?.takeIf { it.status == PartnerLinkStatus.ACTIVE || it.status == PartnerLinkStatus.PAUSED }
            ?: throw NotFoundException("Yaqin topilmadi")
        if (links.setPaused(live.id, paused, now())) {
            record(ownerId, if (paused) AuditActions.PARTNER_PAUSED else AuditActions.PARTNER_RESUMED, live.id, ip)
        }
        return state(ownerId)
    }

    /** Ends whatever she has — a code, a request, a link. Saying it twice is not an error. */
    suspend fun end(ownerId: Uuid, ip: String?): PartnerState {
        links.liveOf(ownerId)?.let { live ->
            if (links.end(live.id, ENDED_BY_OWNER, now())) record(ownerId, AuditActions.PARTNER_ENDED, live.id, ip)
        }
        return state(ownerId)
    }

    // ---------------------------------------------------------------- theirs

    /**
     * The code typed in. Every way it can fail to open anything — unknown, used, expired,
     * an account that is gone — gets the same answer, so the door says nothing about
     * which codes exist.
     */
    suspend fun accept(partnerId: Uuid, request: AcceptPartnerInviteRequest, ip: String?): FollowedPerson {
        val partner = requireActive(partnerId)
        val code = normaliseCode(request.code)
        if (code.length != Limits.PARTNER_CODE_LENGTH) throw ValidationException("code", "Kod noto'g'ri")
        val name = request.name?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
        if (name != null && name.length > Limits.PARTNER_NAME_MAX) {
            throw ValidationException("name", "Eng ko'pi ${Limits.PARTNER_NAME_MAX} belgi")
        }
        val becomesPartner = request.asPartnerAccount && !partner.onboardingCompleted
        if (becomesPartner && name == null && partner.name.isBlank()) {
            throw ValidationException("name", "Bo'sh bo'lishi mumkin emas")
        }

        val at = now()
        val invite = links.inviteByCodeHash(sha256(code))?.takeIf { it.isCodeLive(at) }
            ?: throw NotFoundException(CODE_GONE)
        if (invite.ownerId == partnerId) throw ConflictException("O'zingizning kodingizni kirita olmaysiz")
        val owner = users.findById(invite.ownerId)?.takeIf { it.status == AccountStatus.ACTIVE }
            ?: throw NotFoundException(CODE_GONE)
        if (links.followingOf(partnerId).size >= Limits.PARTNER_MAX_FOLLOWING) {
            throw ConflictException("Ko'pi bilan ${Limits.PARTNER_MAX_FOLLOWING} kishini kuzatish mumkin")
        }
        if (!links.accept(invite.id, partnerId, at)) throw NotFoundException(CODE_GONE)

        when {
            becomesPartner -> users.becomePartnerAccount(partnerId, name)
            name != null && partner.name.isBlank() -> users.setName(partnerId, name)
        }
        record(partnerId, AuditActions.PARTNER_ACCEPTED, invite.id, ip)

        val partnerName = name ?: partner.name.ifBlank { "?" }
        notify(
            invite.ownerId,
            "partner_accepted:${invite.id}",
            PartnerPhrases.accepted(partnerName, owner.language),
            NotificationCategory.SYSTEM,
        )
        return FollowedPerson(
            linkId = invite.id.toString(),
            name = owner.name,
            relation = invite.relation,
            status = PartnerLinkStatus.PENDING,
        )
    }

    /** The follower's own switch for her requests to pay. */
    suspend fun setAcceptsPaymentRequests(partnerId: Uuid, linkId: String, enabled: Boolean): FollowedPerson {
        val link = links.byId(parseUuid(linkId))?.takeIf { it.partnerId == partnerId }
            ?: throw NotFoundException("Topilmadi")
        links.setAcceptsPaymentRequests(link.id, enabled)
        return following(partnerId).first { it.linkId == link.id.toString() }
    }

    suspend fun leave(partnerId: Uuid, linkId: String, ip: String?) {
        val link = links.byId(parseUuid(linkId))?.takeIf { it.partnerId == partnerId }
            ?: throw NotFoundException("Topilmadi")
        if (links.end(link.id, ENDED_BY_PARTNER, now())) record(partnerId, AuditActions.PARTNER_LEFT, link.id, ip)
    }

    /**
     * What they see. Not active — waiting for her, or paused — answers with the status and
     * nothing else; a link that is not theirs is a 404 like any other.
     */
    suspend fun view(viewerId: Uuid, linkId: String): PartnerView {
        val viewer = requireActive(viewerId)
        val link = links.byId(parseUuid(linkId))?.takeIf { it.partnerId == viewerId }
            ?: throw NotFoundException("Topilmadi")
        val owner = users.findById(link.ownerId)?.takeIf { it.status == AccountStatus.ACTIVE }
            ?: throw NotFoundException("Topilmadi")
        val at = now()
        val today = at.dayIn(owner.timezone)
        val base = PartnerView(
            linkId = link.id.toString(),
            status = link.status,
            name = owner.name,
            relation = link.relation,
            permissions = link.permissions,
            generatedAt = at,
            today = today,
        )
        if (link.status != PartnerLinkStatus.ACTIVE) return base
        links.recordView(link.id, at)
        return build(base, owner, link.permissions, today, viewer.language)
    }

    private suspend fun build(
        base: PartnerView,
        owner: UserRecord,
        shown: PartnerPermissions,
        today: LocalDate,
        language: Language,
    ): PartnerView {
        val stage = owner.lifeStage
        val visibleStage = when (stage) {
            LifeStage.CYCLE -> stage.takeIf { shown.cycle }
            LifeStage.TRYING_TO_CONCEIVE -> if (shown.fertile) stage else LifeStage.CYCLE.takeIf { shown.cycle }
            LifeStage.PREGNANCY, LifeStage.POSTPARTUM -> stage.takeIf { shown.pregnancy }
            LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE -> stage.takeIf { shown.cycle || shown.care }
        }

        val cycle = if (stage.predictsCycle && (shown.cycle || shown.fertile)) cycleOf(owner.id, shown, today) else null
        val pregnancy = if (shown.pregnancy && (stage == LifeStage.PREGNANCY || stage == LifeStage.POSTPARTUM)) {
            pregnancyOf(owner.id, stage, today)
        } else {
            null
        }
        val day = if (shown.mood || shown.symptoms) dayOf(owner.id, shown, today, language) else null
        val visits = if (shown.appointments) {
            appointments.list(owner.id)
                .filter { !it.isDone && it.scheduledOn >= today && it.scheduledOn <= today.plus(APPOINTMENT_DAYS, DateTimeUnit.DAY) }
                .sortedWith(compareBy({ it.scheduledOn }, { it.scheduledAt }))
                .take(APPOINTMENT_COUNT)
                .map { PartnerAppointment(it.title, it.scheduledOn, it.scheduledAt, it.place) }
        } else {
            emptyList()
        }
        val care = if (shown.care) careOf(owner, stage, today) else null

        return base.copy(
            stage = visibleStage,
            cycle = cycle,
            pregnancy = pregnancy,
            day = day,
            appointments = visits,
            care = care,
        )
    }

    private suspend fun cycleOf(ownerId: Uuid, shown: PartnerPermissions, today: LocalDate): PartnerCycle {
        val status = health.status(ownerId)
        val prediction = status.prediction
        val period = status.currentPeriod
        // The window is told only while it is ahead or under way: last month's dates read
        // as if they were news, and the next one is not predicted until the period comes.
        val fertile = shown.fertile && prediction.fertileUntil?.let { it >= today } == true
        // Without the window, a fertile day is just the first half of the cycle.
        val phase = status.phase?.let { if (it == CyclePhase.FERTILE && !fertile) CyclePhase.FOLLICULAR else it }
        return PartnerCycle(
            cycleDay = status.cycleDay.takeIf { shown.cycle },
            phase = phase.takeIf { shown.cycle },
            periodNow = shown.cycle && period != null,
            periodDay = period?.let { it.startedOn.daysUntil(today) + 1 }?.takeIf { shown.cycle },
            nextPeriodStart = prediction.nextPeriodStart.takeIf { shown.cycle },
            daysUntilNextPeriod = status.daysUntilNextPeriod.takeIf { shown.cycle },
            averageCycleLength = prediction.averageCycleLength.takeIf { shown.cycle },
            fertileFrom = prediction.fertileFrom.takeIf { fertile },
            fertileUntil = prediction.fertileUntil.takeIf { fertile },
            ovulationOn = prediction.ovulationOn.takeIf { fertile },
            confidence = prediction.confidence,
        )
    }

    private suspend fun pregnancyOf(ownerId: Uuid, stage: LifeStage, today: LocalDate): PartnerPregnancy? {
        val anchor = users.stageBaselineOf(ownerId) ?: return null
        return if (stage == LifeStage.PREGNANCY) {
            val due = anchor.dueDate ?: return null
            val left = today.daysUntil(due)
            PartnerPregnancy(
                week = ((PREGNANCY_DAYS - left) / 7).coerceIn(1, 42),
                dueDate = due,
                daysToGo = left.coerceAtLeast(0),
            )
        } else {
            val born = anchor.birthDate ?: return null
            PartnerPregnancy(childBirthDate = born, babyAgeDays = born.daysUntil(today).coerceAtLeast(0))
        }
    }

    private suspend fun dayOf(ownerId: Uuid, shown: PartnerPermissions, today: LocalDate, language: Language): PartnerDay? {
        val log = health.log(ownerId, today)
        val labels = if (shown.symptoms && log.symptoms.isNotEmpty()) {
            val catalogue = healthRecords.symptomCatalogue(lifeStage = null, language = language).associateBy { it.key }
            log.symptoms.mapNotNull { catalogue[it.key]?.label }
        } else {
            emptyList()
        }
        val day = PartnerDay(
            mood = log.mood.takeIf { shown.mood },
            energy = log.energy.takeIf { shown.mood },
            symptoms = labels,
        )
        return day.takeUnless { it.mood == null && it.energy == null && it.symptoms.isEmpty() }
    }

    private suspend fun careOf(owner: UserRecord, stage: LifeStage, today: LocalDate): PartnerCare? {
        val midnight = today.atStartOfDayIn(TimeZone.of(owner.timezone))
        return when (stage) {
            LifeStage.POSTPARTUM -> {
                val feeds = stageEvents.list(owner.id, StageEventKind.FEEDING, since = midnight, limit = CARE_EVENTS)
                PartnerCare(feedsToday = feeds.size, lastFeedAt = feeds.maxOfOrNull { it.startedAt })
            }
            LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE -> {
                val flushes = stageEvents.list(owner.id, StageEventKind.HOT_FLUSH, since = midnight, limit = CARE_EVENTS)
                PartnerCare(hotFlushesToday = flushes.size)
            }
            else -> null
        }
    }

    // ---------------------------------------------------------------- alerts

    /**
     * Her period started today. Only with the cycle part shown: a push is a part of the
     * view arriving on its own, so it obeys the same ticks.
     */
    suspend fun periodStarted(ownerId: Uuid, day: LocalDate) {
        val link = links.activeOf(ownerId)?.takeIf { it.permissions.cycle } ?: return
        val owner = users.findById(ownerId) ?: return
        if (!owner.lifeStage.predictsCycle) return
        val partner = link.partnerId?.let { users.findById(it) } ?: return
        deliver(
            partner,
            NotificationCategory.CYCLE,
            "partner_period:${link.id}:$day",
            PartnerPhrases.periodStarted(owner.firstName(), partner.language),
        )
    }

    /**
     * "Labour has started": the one push she sends by hand. It goes to an active link
     * whatever she ticked — pressing it is the permission — and at most once an hour.
     */
    suspend fun labourAlert(ownerId: Uuid, ip: String?) {
        val link = links.activeOf(ownerId) ?: throw NotFoundException("Yaqin topilmadi")
        val owner = requireActive(ownerId)
        val partner = link.partnerId?.let { users.findById(it) } ?: throw NotFoundException("Yaqin topilmadi")
        val hour = now().epochSeconds / 3600
        notify(partner.id, "partner_labour:${link.id}:$hour", PartnerPhrases.labour(owner.firstName(), partner.language), NotificationCategory.SYSTEM)
        record(ownerId, AuditActions.PARTNER_LABOUR_ALERT, link.id, ip)
    }

    /**
     * The daily pass: a period two days off, a doctor visit tomorrow. Runs often and is
     * kept honest by the dedupe keys; sends only in the person's daytime, so a server that
     * was down at nine still sends at ten, and nobody is woken at three.
     */
    suspend fun dailyAlerts(at: Instant = now()) {
        links.allActive().forEach { link ->
            runCatching {
                val partner = link.partnerId?.let { users.findById(it) } ?: return@runCatching
                val hour = at.toLocalDateTime(TimeZone.of(partner.timezone)).hour
                if (hour !in ALERT_HOURS) return@runCatching
                val owner = users.findById(link.ownerId)?.takeIf { it.status == AccountStatus.ACTIVE } ?: return@runCatching
                val ownerToday = at.dayIn(owner.timezone)
                val name = owner.firstName()

                if (link.permissions.cycle && owner.lifeStage.predictsCycle) {
                    val status = health.status(owner.id)
                    val next = status.prediction.nextPeriodStart
                    if (status.currentPeriod == null && next != null && status.daysUntilNextPeriod == PERIOD_NOTICE_DAYS) {
                        deliver(
                            partner,
                            NotificationCategory.CYCLE,
                            "partner_period_soon:${link.id}:$next",
                            PartnerPhrases.periodSoon(name, PERIOD_NOTICE_DAYS, partner.language),
                        )
                    }
                }
                if (link.permissions.appointments) {
                    val tomorrow = ownerToday.plus(1, DateTimeUnit.DAY)
                    appointments.list(owner.id)
                        .filter { !it.isDone && it.scheduledOn == tomorrow }
                        .forEach { visit ->
                            val title = visit.scheduledAt?.let { "${visit.title} · $it" } ?: visit.title
                            deliver(
                                partner,
                                NotificationCategory.CYCLE,
                                "partner_appointment:${link.id}:${visit.id}:$tomorrow",
                                PartnerPhrases.appointmentTomorrow(name, title, partner.language),
                            )
                        }
                }
            }
        }
    }

    /** A routine push, through the person's own switches, quiet hours and caps. */
    private suspend fun deliver(to: UserRecord, category: NotificationCategory, dedupeKey: String, text: PartnerPhrases.Text) {
        val at = now()
        val decision = NotificationPolicy.decide(
            category = category,
            localTime = at.toLocalDateTime(TimeZone.of(to.timezone)).time,
            settings = notifications.settingsOf(to.id),
            sentToday = notifications.sentCount(to.id, at - 1.days),
            sentThisWeek = notifications.sentCount(to.id, at - 7.days),
            caps = notifications.caps(),
            // A missing device is found at delivery and recorded there.
            hasDevice = true,
        )
        runCatching {
            notifications.enqueue(
                userId = to.id,
                category = category,
                title = text.title,
                body = text.body,
                scheduledFor = at,
                dedupeKey = dedupeKey,
                status = if (decision is DeliveryDecision.Send) NotificationStatus.QUEUED else NotificationStatus.SUPPRESSED,
                suppressedReason = (decision as? DeliveryDecision.Suppress)?.reason,
                link = LINK,
            )
        }
    }

    // ---------------------------------------------------------------- plumbing

    private suspend fun requireActive(userId: Uuid): UserRecord {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        if (user.status != AccountStatus.ACTIVE) throw ForbiddenException(uz.sadora.contract.ErrorCodes.ACCOUNT_BLOCKED, "Hisob faol emas")
        return user
    }

    private suspend fun notify(to: Uuid, dedupeKey: String, text: PartnerPhrases.Text, category: NotificationCategory) {
        runCatching {
            notifications.enqueue(
                userId = to,
                category = category,
                title = text.title,
                body = text.body,
                scheduledFor = now(),
                dedupeKey = dedupeKey,
                status = NotificationStatus.QUEUED,
                suppressedReason = null,
                link = LINK,
            )
        }
    }

    private suspend fun record(actor: Uuid, action: String, linkId: Uuid, ip: String?, metadata: Map<String, String> = emptyMap()) {
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = actor,
                action = action,
                entityType = "partner_link",
                entityId = linkId.toString(),
                metadata = metadata,
                ip = ip,
            ),
        )
    }

    private fun newCode(): String =
        (1..Limits.PARTNER_CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    private fun PartnerLinkRecord.isCodeLive(at: Instant): Boolean = codeExpiresAt?.let { it > at } == true

    private fun PartnerLinkRecord.toInvite(code: String?) = PartnerInvite(
        code = code?.let(::display),
        url = code?.let { "$publicBaseUrl/y/$it" },
        relation = relation,
        createdAt = createdAt,
        expiresAt = codeExpiresAt ?: createdAt,
    )

    private suspend fun PartnerLinkRecord.toLink(): PartnerLink = PartnerLink(
        id = id.toString(),
        status = status,
        partnerName = partnerId?.let { users.findById(it)?.name }.orEmpty(),
        relation = relation,
        permissions = permissions,
        createdAt = createdAt,
        acceptedAt = acceptedAt,
        approvedAt = approvedAt,
        pausedAt = pausedAt,
        lastViewedAt = lastViewedAt,
        acceptsPaymentRequests = acceptsPaymentRequests,
    )

    private fun UserRecord.firstName(): String = name.trim().substringBefore(' ').ifBlank { "Sadora" }

    companion object {
        /** No 0/O, 1/I/L: a code read out over the phone must not be misheard. */
        const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val LINK = "sadora://yaqinim"
        private const val CODE_GONE = "Kod topilmadi yoki muddati o'tgan"
        private const val CODE_ATTEMPTS = 5
        private const val ENDED_BY_OWNER = "owner"
        private const val ENDED_BY_PARTNER = "partner"
        private const val ENDED_SUPERSEDED = "superseded"
        private const val PREGNANCY_DAYS = 280
        private const val APPOINTMENT_DAYS = 60
        private const val APPOINTMENT_COUNT = 5
        private const val CARE_EVENTS = 200
        private const val WEB_TOKEN_BYTES = 32
        /** 32 random bytes are 43 base64url characters. */
        private val WEB_TOKEN_LENGTH = 40..48
        private const val PERIOD_NOTICE_DAYS = 2
        /** The person's local hours in which a routine alert may go out. */
        private val ALERT_HOURS = 9..20

        /** What she typed, as the server compares it: case, spaces and the dash ignored. */
        fun normaliseCode(raw: String): String = raw.uppercase().filter { it in CODE_ALPHABET }

        /** `K7M2QP4X` is handed over as `K7M2-QP4X`. */
        fun display(code: String): String = code.chunked(4).joinToString("-")
    }
}

/** The browser page's content and the language it is drawn in: asked for, else hers. */
data class PartnerWebView(val view: PartnerView, val language: Language)

/** The parts a permission set shows, for the audit row. */
internal fun PartnerPermissions.shownKeys(): List<String> = buildList {
    if (cycle) add("cycle")
    if (fertile) add("fertile")
    if (mood) add("mood")
    if (symptoms) add("symptoms")
    if (pregnancy) add("pregnancy")
    if (appointments) add("appointments")
    if (care) add("care")
}
