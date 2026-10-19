package uz.sadora.server.admin

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.notInSubQuery
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.DEFAULT_TIMEZONE
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.core.resolveTimeZone
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.Devices
import uz.sadora.server.db.NotificationOutbox
import uz.sadora.server.db.Subscriptions
import uz.sadora.server.db.Users
import uz.sadora.server.db.UserNotificationSettings
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.notify.NotificationPolicy
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.notify.TARGET_CLIENT
import uz.sadora.server.plugins.AdminPrincipal

// ---------------------------------------------------------------- shapes

@Serializable
data class CampaignText(val title: String, val body: String)

/** Who a campaign is for. An empty list means "any". */
@Serializable
data class CampaignSegment(
    val lifeStages: List<LifeStage> = emptyList(),
    val languages: List<Language> = emptyList(),
    /** `all`, `free` or `premium`. */
    val tier: String = "all",
    /** `self` (her own account) and/or `partner` (Yaqinim's). */
    val accountKinds: List<String> = listOf("self"),
)

@Serializable
data class CreateCampaignRequest(
    val name: String,
    /** Keyed `uz`, `ru`, `en`; Uzbek is required, the others fall back to it. */
    val texts: Map<String, CampaignText>,
    val segment: CampaignSegment = CampaignSegment(),
    /** Null sends it at the next pass, within a minute. */
    val scheduledFor: Instant? = null,
)

@Serializable
data class CampaignTestRequest(val texts: Map<String, CampaignText>, val phone: String)

@Serializable
data class CampaignTestResult(val sent: Boolean, val language: Language? = null, val reason: String? = null)

/** How many accounts a segment reaches now, and why the rest would not get it. */
@Serializable
data class CampaignAudience(val matched: Int, val reachable: Int, val skipped: Map<String, Int>)

@Serializable
data class CampaignView(
    val id: String,
    val name: String,
    val texts: Map<String, CampaignText>,
    val segment: CampaignSegment,
    val status: String,
    val scheduledFor: Instant,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val audience: Int? = null,
    /** Outbox rows by status: queued, sent, failed, suppressed. */
    val delivery: Map<String, Int> = emptyMap(),
    /** Accounts left out before queueing, by reason. */
    val skipped: Map<String, Int> = emptyMap(),
)

private val campaignJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

object PushCampaigns : org.jetbrains.exposed.v1.core.Table("push_campaigns") {
    val id = uuid("id")
    val name = text("name")
    val texts = jsonb<Map<String, CampaignText>>("texts", campaignJson)
    val segment = jsonb<CampaignSegment>("segment", campaignJson)
    val status = text("status")
    val scheduledFor = timestampWithTimeZone("scheduled_for")
    val createdBy = uuid("created_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    val audience = integer("audience").nullable()
    val queued = integer("queued").nullable()
    val skipped = jsonb<Map<String, Int>>("skipped", campaignJson).nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Why an account in the segment was not queued. Stable keys; the panel words them. */
object CampaignSkips {
    const val NOTIFICATIONS_OFF = "notifications_off"
    const val NEWS_OFF = "news_off"
    const val DISCREET = "discreet"
    const val NO_DEVICE = "no_device"
    const val DAILY_CAP = "daily_cap"
    const val WEEKLY_CAP = "weekly_cap"
}

/** One account as the campaign pass sees it. */
data class CampaignRecipient(
    val id: Uuid,
    val language: Language,
    val timezone: String,
    val discreet: Boolean,
    val hasDevice: Boolean,
    val enabled: Boolean,
    val news: Boolean,
    val quietFrom: LocalTime?,
    val quietUntil: LocalTime?,
    val sentToday: Int,
    val sentThisWeek: Int,
)

/** Queue now, queue for later (her quiet hours end), or leave out and why. */
sealed interface CampaignDecision {
    data class Queue(val at: Instant) : CampaignDecision
    data class Skip(val reason: String) : CampaignDecision
}

// ---------------------------------------------------------------- service

/**
 * The team's push campaigns: written once in up to three languages, sent to a segment.
 *
 * Every row goes through the ordinary outbox, so delivery, retries and the inbox are the
 * ones every reminder uses. Before a row is queued the account gets the same consideration
 * a reminder does — her master switch, her quiet hours, the frequency caps — plus two of a
 * campaign's own: her "news" switch, and discreet mode, whose neutral "you have a reminder"
 * would turn an offer into a puzzle, so a discreet account is left out entirely.
 */
class CampaignService(
    private val notifications: NotificationRepository,
    private val audit: AuditService,
) {
    private val logger = LoggerFactory.getLogger(CampaignService::class.java)

    suspend fun list(): List<CampaignView> {
        val rows = dbQuery {
            PushCampaigns.selectAll().orderBy(PushCampaigns.createdAt to SortOrder.DESC).limit(100).toList()
        }
        return rows.map { row -> row.toView(delivery(row[PushCampaigns.id])) }
    }

    suspend fun audience(segment: CampaignSegment): CampaignAudience {
        val validated = validateSegment(segment)
        var matched = 0
        var reachable = 0
        val skipped = mutableMapOf<String, Int>()
        val at = now()
        val caps = notifications.caps()
        forEachRecipientPage(validated) { page ->
            page.forEach { recipient ->
                matched++
                when (val decision = decide(recipient, at, caps)) {
                    is CampaignDecision.Queue -> reachable++
                    is CampaignDecision.Skip -> skipped.merge(decision.reason, 1, Int::plus)
                }
            }
        }
        return CampaignAudience(matched, reachable, skipped)
    }

    suspend fun create(request: CreateCampaignRequest, admin: AdminPrincipal, context: RequestContext): CampaignView {
        val name = request.name.trim()
        if (name.isEmpty() || name.length > 120) throw ValidationException("name", "Nomi 1–120 belgi bo'lsin")
        val texts = validateTexts(request.texts)
        val segment = validateSegment(request.segment)
        val at = request.scheduledFor ?: now()
        if (at > now() + 60.days) throw ValidationException("scheduledFor", "Eng ko'pi 60 kun oldinga rejalashtirish mumkin")
        val id = Uuid.random()
        dbQuery {
            PushCampaigns.insert {
                it[PushCampaigns.id] = id
                it[PushCampaigns.name] = name
                it[PushCampaigns.texts] = texts
                it[PushCampaigns.segment] = segment
                it[status] = STATUS_SCHEDULED
                it[scheduledFor] = at.toOffsetDateTime()
                it[createdBy] = admin.adminId
                it[createdAt] = now().toOffsetDateTime()
            }
        }
        record(admin, AuditActions.CAMPAIGN_CREATED, id, context, mapOf("scheduled_for" to at.toString()))
        return require(id)
    }

    suspend fun cancel(id: Uuid, admin: AdminPrincipal, context: RequestContext): CampaignView {
        val changed = dbQuery {
            PushCampaigns.update({ (PushCampaigns.id eq id) and (PushCampaigns.status eq STATUS_SCHEDULED) }) {
                it[status] = STATUS_CANCELLED
                it[finishedAt] = now().toOffsetDateTime()
            }
        }
        if (changed == 0) {
            require(id)
            throw ConflictException("Kampaniya allaqachon yuborilmoqda yoki yuborilgan")
        }
        record(admin, AuditActions.CAMPAIGN_CANCELLED, id, context)
        return require(id)
    }

    /**
     * One push to one phone, now: how it looks on a real lock screen before it goes to
     * thousands. Needs only an installed app on that account; caps and quiet hours are for
     * the audience, not for the colleague holding the test phone.
     */
    suspend fun test(request: CampaignTestRequest, admin: AdminPrincipal, context: RequestContext): CampaignTestResult {
        val texts = validateTexts(request.texts)
        val digits = request.phone.filter { it.isDigit() }
        val phone = when {
            digits.length == 9 -> "+998$digits"
            digits.length == 12 && digits.startsWith("998") -> "+$digits"
            else -> throw ValidationException("phone", "Telefon raqami noto'g'ri")
        }
        val user = dbQuery {
            Users.selectAll().where { Users.phone eq phone }.singleOrNull()
        } ?: return CampaignTestResult(sent = false, reason = "no_account")
        val userId = user[Users.id]
        val language = enumValues<Language>().firstOrNull { it.dbValue() == user[Users.language] } ?: Language.UZ
        if (!hasDevice(userId)) return CampaignTestResult(sent = false, language = language, reason = CampaignSkips.NO_DEVICE)
        val text = textFor(texts, language)
        notifications.enqueue(
            userId = userId,
            category = NotificationCategory.SYSTEM,
            title = text.title,
            body = text.body,
            scheduledFor = now(),
            dedupeKey = "campaign-test:${Uuid.random()}",
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
        )
        record(admin, AuditActions.CAMPAIGN_TESTED, null, context)
        return CampaignTestResult(sent = true, language = language)
    }

    /**
     * Every campaign whose time has come, once. The status moves to `sending` with a
     * conditional update before anything is queued, so two passes cannot both take it; the
     * outbox's dedupe key covers a pass that died half-way and is run again by hand.
     */
    suspend fun runDue(at: Instant = now()): Int {
        val due = dbQuery {
            PushCampaigns.select(PushCampaigns.id)
                .where { (PushCampaigns.status eq STATUS_SCHEDULED) and (PushCampaigns.scheduledFor lessEq at.toOffsetDateTime()) }
                .map { it[PushCampaigns.id] }
        }
        var ran = 0
        for (id in due) {
            val taken = dbQuery {
                PushCampaigns.update({ (PushCampaigns.id eq id) and (PushCampaigns.status eq STATUS_SCHEDULED) }) {
                    it[status] = STATUS_SENDING
                    it[startedAt] = now().toOffsetDateTime()
                }
            }
            if (taken == 0) continue
            runCatching { send(id, at) }.onFailure { logger.error("Campaign {} failed mid-way", id, it) }
            ran++
        }
        return ran
    }

    private suspend fun send(id: Uuid, at: Instant) {
        val row = dbQuery { PushCampaigns.selectAll().where { PushCampaigns.id eq id }.single() }
        val texts = row[PushCampaigns.texts]
        val segment = row[PushCampaigns.segment]
        val caps = notifications.caps()
        var audience = 0
        var queued = 0
        val skipped = mutableMapOf<String, Int>()
        forEachRecipientPage(segment) { page ->
            page.forEach { recipient ->
                audience++
                when (val decision = decide(recipient, at, caps)) {
                    is CampaignDecision.Skip -> skipped.merge(decision.reason, 1, Int::plus)
                    is CampaignDecision.Queue -> {
                        val text = textFor(texts, recipient.language)
                        val fresh = notifications.enqueue(
                            userId = recipient.id,
                            category = NotificationCategory.SYSTEM,
                            title = text.title,
                            body = text.body,
                            scheduledFor = decision.at,
                            dedupeKey = "campaign:$id:${recipient.id}",
                            status = NotificationStatus.QUEUED,
                            suppressedReason = null,
                        )
                        if (fresh) queued++
                    }
                }
            }
        }
        dbQuery {
            PushCampaigns.update({ PushCampaigns.id eq id }) {
                it[status] = STATUS_SENT
                it[finishedAt] = now().toOffsetDateTime()
                it[PushCampaigns.audience] = audience
                it[PushCampaigns.queued] = queued
                it[PushCampaigns.skipped] = skipped
            }
        }
        logger.info("Campaign {}: {} in segment, {} queued, skipped {}", id, audience, queued, skipped)
    }

    /** The segment's accounts a page at a time, with what [decide] needs read in a few queries per page. */
    private suspend fun forEachRecipientPage(segment: CampaignSegment, block: suspend (List<CampaignRecipient>) -> Unit) {
        var offset = 0L
        val weekAgo = now() - 7.days
        val dayStart = now().dayIn(DEFAULT_TIMEZONE).atTime(0, 0).toInstant(resolveTimeZone(DEFAULT_TIMEZONE))
        while (true) {
            val page = dbQuery {
                val users = Users.selectAll()
                    .where { segmentFilter(segment) }
                    .orderBy(Users.id to SortOrder.ASC)
                    .limit(PAGE)
                    .offset(offset)
                    .toList()
                if (users.isEmpty()) return@dbQuery emptyList()
                val ids = users.map { it[Users.id] }
                val settings = UserNotificationSettings.selectAll()
                    .where { UserNotificationSettings.userId inList ids }
                    .associateBy { it[UserNotificationSettings.userId] }
                val withDevice = Devices.select(Devices.userId)
                    .where { (Devices.userId inList ids) and Devices.pushToken.isNotNull() and (Devices.app eq TARGET_CLIENT) }
                    .map { it[Devices.userId] }
                    .toSet()
                val counter = NotificationOutbox.id.count()
                fun sentSince(since: Instant): Map<Uuid, Int> = NotificationOutbox.select(NotificationOutbox.userId, counter)
                    .where {
                        (NotificationOutbox.userId inList ids) and
                            (NotificationOutbox.status eq NotificationStatus.SENT.dbValue()) and
                            (NotificationOutbox.scheduledFor greaterEq since.toOffsetDateTime())
                    }
                    .groupBy(NotificationOutbox.userId)
                    .associate { it[NotificationOutbox.userId] to it[counter].toInt() }
                val today = sentSince(dayStart)
                val week = sentSince(weekAgo)
                users.map { user ->
                    val id = user[Users.id]
                    val own = settings[id]
                    CampaignRecipient(
                        id = id,
                        language = enumValues<Language>().firstOrNull { it.dbValue() == user[Users.language] } ?: Language.UZ,
                        timezone = user[Users.timezone],
                        discreet = user[Users.discreetMode],
                        hasDevice = id in withDevice,
                        enabled = own?.get(UserNotificationSettings.enabled) ?: true,
                        news = own?.get(UserNotificationSettings.news) ?: true,
                        quietFrom = own?.get(UserNotificationSettings.quietFrom),
                        quietUntil = own?.get(UserNotificationSettings.quietUntil),
                        sentToday = today[id] ?: 0,
                        sentThisWeek = week[id] ?: 0,
                    )
                }
            }
            if (page.isEmpty()) return
            block(page)
            if (page.size < PAGE) return
            offset += PAGE
        }
    }

    private fun segmentFilter(segment: CampaignSegment): Op<Boolean> {
        var filter: Op<Boolean> = (Users.status eq "active") and (Users.onboardingCompleted eq true) and
            (Users.accountKind inList segment.accountKinds)
        if (segment.lifeStages.isNotEmpty()) filter = filter and (Users.lifeStage inList segment.lifeStages.map { it.dbValue() })
        if (segment.languages.isNotEmpty()) filter = filter and (Users.language inList segment.languages.map { it.dbValue() })
        when (segment.tier) {
            "premium" -> filter = filter and (Users.id inSubQuery activeSubscribers())
            "free" -> filter = filter and (Users.id notInSubQuery activeSubscribers())
        }
        return filter
    }

    private fun activeSubscribers() = Subscriptions.select(Subscriptions.userId).where { Subscriptions.status eq "active" }

    private suspend fun hasDevice(userId: Uuid): Boolean = dbQuery {
        Devices.selectAll()
            .where { (Devices.userId eq userId) and Devices.pushToken.isNotNull() and (Devices.app eq TARGET_CLIENT) }
            .count() > 0
    }

    private suspend fun delivery(id: Uuid): Map<String, Int> = dbQuery {
        val counter = NotificationOutbox.id.count()
        NotificationOutbox.select(NotificationOutbox.status, counter)
            .where { NotificationOutbox.dedupeKey like "campaign:$id:%" }
            .groupBy(NotificationOutbox.status)
            .associate { it[NotificationOutbox.status] to it[counter].toInt() }
    }

    private suspend fun require(id: Uuid): CampaignView {
        val row = dbQuery { PushCampaigns.selectAll().where { PushCampaigns.id eq id }.singleOrNull() }
            ?: throw NotFoundException("Kampaniya topilmadi")
        return row.toView(delivery(id))
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toView(delivery: Map<String, Int>) = CampaignView(
        id = this[PushCampaigns.id].toString(),
        name = this[PushCampaigns.name],
        texts = this[PushCampaigns.texts],
        segment = this[PushCampaigns.segment],
        status = this[PushCampaigns.status],
        scheduledFor = this[PushCampaigns.scheduledFor].toKotlinInstant(),
        createdAt = this[PushCampaigns.createdAt].toKotlinInstant(),
        startedAt = this[PushCampaigns.startedAt]?.toKotlinInstant(),
        finishedAt = this[PushCampaigns.finishedAt]?.toKotlinInstant(),
        audience = this[PushCampaigns.audience],
        delivery = delivery,
        skipped = this[PushCampaigns.skipped] ?: emptyMap(),
    )

    private suspend fun record(admin: AdminPrincipal, action: String, id: Uuid?, context: RequestContext, metadata: Map<String, String> = emptyMap()) {
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = action,
                entityType = "push_campaign",
                entityId = id?.toString(),
                metadata = metadata,
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    internal companion object {
        const val STATUS_SCHEDULED = "scheduled"
        const val STATUS_SENDING = "sending"
        const val STATUS_SENT = "sent"
        const val STATUS_CANCELLED = "cancelled"
        const val PAGE = 500
        const val TITLE_MAX = 65
        const val BODY_MAX = 240
        private val TIERS = setOf("all", "free", "premium")
        private val KINDS = setOf("self", "partner")

        fun validateTexts(texts: Map<String, CampaignText>): Map<String, CampaignText> {
            val cleaned = texts
                .filterKeys { it in setOf("uz", "ru", "en") }
                .mapValues { (_, text) -> CampaignText(text.title.trim(), text.body.trim()) }
                .filterValues { it.title.isNotEmpty() || it.body.isNotEmpty() }
            val uz = cleaned["uz"]
            if (uz == null || uz.title.isEmpty() || uz.body.isEmpty()) {
                throw ValidationException("texts.uz", "O'zbekcha sarlavha va matn kerak")
            }
            cleaned.forEach { (language, text) ->
                if (text.title.isEmpty() || text.body.isEmpty()) throw ValidationException("texts.$language", "Sarlavha va matn birga yozilsin")
                if (text.title.length > TITLE_MAX) throw ValidationException("texts.$language.title", "Sarlavha eng ko'pi $TITLE_MAX belgi")
                if (text.body.length > BODY_MAX) throw ValidationException("texts.$language.body", "Matn eng ko'pi $BODY_MAX belgi")
            }
            return cleaned
        }

        fun validateSegment(segment: CampaignSegment): CampaignSegment {
            if (segment.tier !in TIERS) throw ValidationException("segment.tier", "Tarif noto'g'ri")
            val kinds = segment.accountKinds.distinct()
            if (kinds.isEmpty() || kinds.any { it !in KINDS }) throw ValidationException("segment.accountKinds", "Hisob turi noto'g'ri")
            return segment.copy(lifeStages = segment.lifeStages.distinct(), languages = segment.languages.distinct(), accountKinds = kinds)
        }

        /** Her language's words, else Uzbek, which validation guarantees. */
        fun textFor(texts: Map<String, CampaignText>, language: Language): CampaignText =
            texts[language.dbValue()] ?: texts.getValue("uz")

        /**
         * The campaign's version of the reminder policy, pure so every branch can be tested.
         * Quiet hours postpone rather than drop: an offer at 07:00 instead of 23:00 is still
         * an offer, a dropped one is not.
         */
        fun decide(recipient: CampaignRecipient, at: Instant, caps: uz.sadora.contract.FrequencyCaps): CampaignDecision {
            if (!recipient.enabled) return CampaignDecision.Skip(CampaignSkips.NOTIFICATIONS_OFF)
            if (!recipient.news) return CampaignDecision.Skip(CampaignSkips.NEWS_OFF)
            if (recipient.discreet) return CampaignDecision.Skip(CampaignSkips.DISCREET)
            if (!recipient.hasDevice) return CampaignDecision.Skip(CampaignSkips.NO_DEVICE)
            if (recipient.sentToday >= caps.maxPerDay) return CampaignDecision.Skip(CampaignSkips.DAILY_CAP)
            if (recipient.sentThisWeek >= caps.maxPerWeek) return CampaignDecision.Skip(CampaignSkips.WEEKLY_CAP)
            val zone = resolveTimeZone(recipient.timezone)
            val local = at.toLocalDateTime(zone)
            val until = recipient.quietUntil
            if (until != null && NotificationPolicy.isQuiet(local.time, recipient.quietFrom, until)) {
                val todayEnd = local.date.atTime(until).toInstant(zone)
                val end = if (todayEnd > at) todayEnd else local.date.plus(1, kotlinx.datetime.DateTimeUnit.DAY).atTime(until).toInstant(zone)
                return CampaignDecision.Queue(end)
            }
            return CampaignDecision.Queue(at)
        }
    }
}

/** Looks for due campaigns once a minute. */
class CampaignJob(private val campaigns: CampaignService) {
    private val logger = LoggerFactory.getLogger(CampaignJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            while (isActive) {
                runCatching { campaigns.runDue() }.onFailure { logger.error("Campaign pass failed", it) }
                delay(1.minutes)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }
}
