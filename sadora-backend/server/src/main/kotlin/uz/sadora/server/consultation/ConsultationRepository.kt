package uz.sadora.server.consultation

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.DoctorHours
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.MessageKind
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentState
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.AppSettings
import uz.sadora.server.db.CommunityComments
import uz.sadora.server.db.CommunityMessages
import uz.sadora.server.db.CommunityPosts
import uz.sadora.server.db.ConsultationSessions
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.db.DoctorHoursTable
import uz.sadora.server.db.DoctorPatientNotes
import uz.sadora.server.db.DoctorPayouts
import uz.sadora.server.db.DoctorProfiles
import uz.sadora.server.db.DoctorQuickReplies
import uz.sadora.server.db.PaymentTransactions
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class SessionRecord(
    val id: Uuid,
    val conversationId: Uuid,
    val doctorId: Uuid,
    val patientId: Uuid,
    val openedAt: Instant?,
    val expiresAt: Instant?,
    val closedAt: Instant?,
    val closedReason: String?,
    val priceMinor: Long,
    val commissionPercent: Int,
    val payment: ConsultationPayment,
    val firstReplyAt: Instant?,
    val summary: String?,
    val rating: Int?,
    val review: String?,
    val ratedAt: Instant?,
    val refundedAt: Instant?,
    val createdAt: Instant,
) {
    /** The window is running: opened, not closed, not past its end. */
    fun isOpen(at: Instant): Boolean = openedAt != null && closedAt == null && expiresAt != null && expiresAt > at

    /** What Sadora keeps of this one, rounded down to the tiyin. */
    val commissionMinor: Long get() = priceMinor * commissionPercent / 100
}

data class PaidTransaction(val id: Uuid, val provider: PaymentProvider, val externalId: String?)

data class QuickReplyRecord(val id: Uuid, val title: String, val body: String, val position: Int)

data class PayoutRecord(val id: Uuid, val doctorId: Uuid, val amountMinor: Long, val note: String?, val paidAt: Instant)

/** A doctor's sessions summed up: how she answers, and the money on them. */
data class DoctorTotals(
    val total: Int = 0,
    val week: Int = 0,
    val month: Int = 0,
    val openNow: Int = 0,
    val avgFirstReplyMinutes: Int? = null,
    val unanswered: Int = 0,
    val rating: Double? = null,
    val ratingCount: Int = 0,
    val grossMinor: Long = 0,
    val commissionMinor: Long = 0,
    val refundDueMinor: Long = 0,
    /** Sessions with money on them: paid, owed back or returned — her earnings' lines. */
    val moneyLines: Long = 0,
)

/** A doctor's own consultation settings as they are stored. */
data class DoctorWorkRecord(
    val priceMinor: Long,
    val busy: Boolean,
    val timezone: String,
    val hours: List<DoctorHours>,
)

/**
 * Consultation sessions, and the doctor's working day around them: hours, quick
 * replies, private notes, payouts, and the one operator setting they need.
 */
class ConsultationRepository {

    // ---------------------------------------------------------------- sessions

    suspend fun createSession(
        conversationId: Uuid,
        doctorId: Uuid,
        patientId: Uuid,
        priceMinor: Long,
        commissionPercent: Int,
        payment: ConsultationPayment,
        openedAt: Instant?,
        expiresAt: Instant?,
    ): SessionRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        ConsultationSessions.insert {
            it[ConsultationSessions.id] = id
            it[ConsultationSessions.conversationId] = conversationId
            it[ConsultationSessions.doctorId] = doctorId
            it[ConsultationSessions.patientId] = patientId
            it[ConsultationSessions.priceMinor] = priceMinor
            it[ConsultationSessions.commissionPercent] = commissionPercent
            it[paymentState] = payment.dbValue()
            it[ConsultationSessions.openedAt] = openedAt?.toOffsetDateTime()
            it[ConsultationSessions.expiresAt] = expiresAt?.toOffsetDateTime()
            it[createdAt] = timestamp.toOffsetDateTime()
        }
        ConsultationSessions.selectAll().where { ConsultationSessions.id eq id }.single().toSession()
    }

    suspend fun session(id: Uuid): SessionRecord? = dbQuery {
        ConsultationSessions.selectAll().where { ConsultationSessions.id eq id }.singleOrNull()?.toSession()
    }

    /** Every session of these conversations, newest first — one query for a list of threads. */
    suspend fun sessionsOf(conversationIds: Collection<Uuid>): Map<Uuid, List<SessionRecord>> = dbQuery {
        if (conversationIds.isEmpty()) return@dbQuery emptyMap()
        ConsultationSessions.selectAll()
            .where { ConsultationSessions.conversationId inList conversationIds.distinct() }
            .orderBy(ConsultationSessions.createdAt to SortOrder.DESC)
            .map { it.toSession() }
            .groupBy { it.conversationId }
    }

    /** The latest window that was actually opened — free, or paid for. */
    suspend fun currentSession(conversationId: Uuid): SessionRecord? = dbQuery {
        ConsultationSessions.selectAll()
            .where { (ConsultationSessions.conversationId eq conversationId) and ConsultationSessions.openedAt.isNotNull() }
            .orderBy(ConsultationSessions.openedAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toSession()
    }

    /** A checkout she started and has not finished, to be reused rather than doubled. */
    suspend fun pendingSession(conversationId: Uuid): SessionRecord? = dbQuery {
        ConsultationSessions.selectAll()
            .where {
                (ConsultationSessions.conversationId eq conversationId) and
                    (ConsultationSessions.paymentState eq ConsultationPayment.PENDING.dbValue())
            }
            .orderBy(ConsultationSessions.createdAt to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.toSession()
    }

    /**
     * Pending → paid, and the window opens. The state guard is the claim: a provider's
     * retry, or two callbacks racing, finds nothing pending and changes nothing.
     */
    suspend fun markPaid(id: Uuid, openedAt: Instant, expiresAt: Instant): Boolean = dbQuery {
        ConsultationSessions.update({
            (ConsultationSessions.id eq id) and (ConsultationSessions.paymentState eq ConsultationPayment.PENDING.dbValue())
        }) {
            it[paymentState] = ConsultationPayment.PAID.dbValue()
            it[ConsultationSessions.openedAt] = openedAt.toOffsetDateTime()
            it[ConsultationSessions.expiresAt] = expiresAt.toOffsetDateTime()
        } > 0
    }

    suspend fun markFirstReply(id: Uuid, at: Instant): Unit = dbQuery {
        ConsultationSessions.update({ (ConsultationSessions.id eq id) and ConsultationSessions.firstReplyAt.isNull() }) {
            it[firstReplyAt] = at.toOffsetDateTime()
        }
    }

    /**
     * Ends a window. A paid one that ends without a word from the doctor is owed back:
     * the reason says `refund` and the payment `refund_due`, whoever ended it.
     */
    suspend fun close(id: Uuid, at: Instant, reason: String, summary: String?): SessionRecord? = dbQuery {
        val session = ConsultationSessions.selectAll().where { ConsultationSessions.id eq id }.singleOrNull()?.toSession()
            ?: return@dbQuery null
        if (session.closedAt != null) return@dbQuery session
        val unanswered = session.firstReplyAt == null && session.payment == ConsultationPayment.PAID
        ConsultationSessions.update({ ConsultationSessions.id eq id }) {
            it[closedAt] = at.toOffsetDateTime()
            it[closedReason] = if (unanswered) REASON_REFUND else reason
            if (unanswered) it[paymentState] = ConsultationPayment.REFUND_DUE.dbValue()
            if (summary != null) {
                it[ConsultationSessions.summary] = summary
                it[summaryAt] = at.toOffsetDateTime()
            }
        }
        ConsultationSessions.selectAll().where { ConsultationSessions.id eq id }.single().toSession()
    }

    /** Windows whose time is up and that nobody closed: the job's work list. */
    suspend fun expiredOpen(at: Instant, limit: Int): List<SessionRecord> = dbQuery {
        ConsultationSessions.selectAll()
            .where {
                ConsultationSessions.openedAt.isNotNull() and ConsultationSessions.closedAt.isNull() and
                    (ConsultationSessions.expiresAt lessEq at.toOffsetDateTime())
            }
            .limit(limit)
            .map { it.toSession() }
    }

    /** Her advice, written after the window already ended. */
    suspend fun setSummary(id: Uuid, summary: String, at: Instant): Unit = dbQuery {
        ConsultationSessions.update({ ConsultationSessions.id eq id }) {
            it[ConsultationSessions.summary] = summary
            it[summaryAt] = at.toOffsetDateTime()
        }
    }

    suspend fun rate(id: Uuid, rating: Int, review: String?): Boolean = dbQuery {
        ConsultationSessions.update({ (ConsultationSessions.id eq id) and ConsultationSessions.rating.isNull() }) {
            it[ConsultationSessions.rating] = rating.toShort()
            it[ConsultationSessions.review] = review
            it[ratedAt] = now().toOffsetDateTime()
        } > 0
    }

    suspend fun markRefunded(id: Uuid, by: Uuid): Boolean = dbQuery {
        ConsultationSessions.update({
            (ConsultationSessions.id eq id) and
                (ConsultationSessions.paymentState inList listOf(ConsultationPayment.PAID.dbValue(), ConsultationPayment.REFUND_DUE.dbValue()))
        }) {
            it[paymentState] = ConsultationPayment.REFUNDED.dbValue()
            it[refundedAt] = now().toOffsetDateTime()
            it[refundedBy] = by
        } > 0
    }

    /** Every session with money on it, for the operators; [payment] narrows to one state. */
    suspend fun paidSessions(payment: ConsultationPayment?, limit: Int, offset: Long): Pair<List<SessionRecord>, Long> = dbQuery {
        var query = ConsultationSessions.selectAll()
            .where { ConsultationSessions.paymentState inList MONEY_STATES }
        payment?.let { query = query.andWhere { ConsultationSessions.paymentState eq it.dbValue() } }
        val total = query.count()
        query.orderBy(ConsultationSessions.createdAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toSession() } to total
    }

    /**
     * A page of one doctor's sessions with money on them, the latest opened first — the
     * lines under her earnings. The id breaks ties, so an offset never repeats or skips one.
     */
    suspend fun moneySessions(doctorId: Uuid, limit: Int, offset: Long): Pair<List<SessionRecord>, Long> = dbQuery {
        val query = ConsultationSessions.selectAll()
            .where { (ConsultationSessions.doctorId eq doctorId) and (ConsultationSessions.paymentState inList MONEY_STATES) }
        val total = query.count()
        query.orderBy(
            ConsultationSessions.openedAt to SortOrder.DESC_NULLS_LAST,
            ConsultationSessions.createdAt to SortOrder.DESC,
            ConsultationSessions.id to SortOrder.DESC,
        )
            .limit(limit)
            .offset(offset)
            .map { it.toSession() } to total
    }

    /** The patients' names for a page of lines, in one read. */
    suspend fun patientNames(userIds: Collection<Uuid>): Map<Uuid, String> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyMap()
        Users.select(Users.id, Users.name)
            .where { Users.id inList userIds.distinct() }
            .associate { it[Users.id] to it[Users.name] }
    }

    /**
     * Each doctor's numbers, added up by the database — one row per doctor however many
     * sessions she has. The same rules as the service's own count over a list: a window
     * counts once opened; a reply time is whole minutes, cut down; Sadora's share is
     * rounded down per session, so the sum matches the lines.
     */
    suspend fun totals(doctorIds: Collection<Uuid>, at: Instant, weekSince: Instant, monthSince: Instant): Map<Uuid, DoctorTotals> =
        dbQuery {
            if (doctorIds.isEmpty()) return@dbQuery emptyMap()
            // Spliced in, not bound: every value is a parsed UUID or an instant formatted
            // here, so nothing a caller typed reaches the statement.
            val ids = doctorIds.distinct().joinToString(",") { "'$it'" }
            val opened = "opened_at IS NOT NULL"
            val paid = "payment_state = '${ConsultationPayment.PAID.dbValue()}'"
            val sql = """
                SELECT doctor_id,
                    count(*) FILTER (WHERE $opened),
                    count(*) FILTER (WHERE opened_at >= '$weekSince'::timestamptz),
                    count(*) FILTER (WHERE opened_at >= '$monthSince'::timestamptz),
                    count(*) FILTER (WHERE $opened AND closed_at IS NULL AND expires_at > '$at'::timestamptz),
                    avg(trunc(extract(epoch FROM first_reply_at - opened_at) / 60)) FILTER (WHERE $opened AND first_reply_at IS NOT NULL),
                    count(*) FILTER (WHERE $opened AND closed_at IS NOT NULL AND first_reply_at IS NULL),
                    avg(rating) FILTER (WHERE $opened AND rating IS NOT NULL),
                    count(rating) FILTER (WHERE $opened),
                    coalesce(sum(price_minor) FILTER (WHERE $paid), 0),
                    coalesce(sum(price_minor * commission_percent / 100) FILTER (WHERE $paid), 0),
                    coalesce(sum(price_minor) FILTER (WHERE payment_state = '${ConsultationPayment.REFUND_DUE.dbValue()}'), 0),
                    count(*) FILTER (WHERE payment_state IN (${MONEY_STATES.joinToString(",") { "'$it'" }}))
                FROM consultation_sessions
                WHERE doctor_id IN ($ids)
                GROUP BY doctor_id
            """.trimIndent()
            val result = HashMap<Uuid, DoctorTotals>()
            exec(sql) { rows ->
                while (rows.next()) {
                    result[Uuid.parse(rows.getString(1))] = DoctorTotals(
                        total = rows.getInt(2),
                        week = rows.getInt(3),
                        month = rows.getInt(4),
                        openNow = rows.getInt(5),
                        avgFirstReplyMinutes = rows.getBigDecimal(6)?.toDouble()?.toInt(),
                        unanswered = rows.getInt(7),
                        rating = rows.getBigDecimal(8)?.toDouble(),
                        ratingCount = rows.getInt(9),
                        grossMinor = rows.getLong(10),
                        commissionMinor = rows.getLong(11),
                        refundDueMinor = rows.getLong(12),
                        moneyLines = rows.getLong(13),
                    )
                }
            }
            result
        }

    /**
     * A page of the staff's quality table: approved and suspended doctors, the busiest
     * this month first, then the latest to apply. Ordered here, not after loading, so a
     * page is the same slice whatever the offset.
     */
    suspend fun qualityPage(monthSince: Instant, limit: Int, offset: Long): Pair<List<Uuid>, Long> = dbQuery {
        val shown = listOf(DoctorStatus.APPROVED, DoctorStatus.SUSPENDED).map { it.dbValue() }
        val total = DoctorProfiles.selectAll().where { DoctorProfiles.status inList shown }.count()
        val statuses = shown.joinToString(",") { "'$it'" }
        val sql = """
            SELECT d.id
            FROM doctor_profiles d
            LEFT JOIN consultation_sessions s ON s.doctor_id = d.id AND s.opened_at >= '$monthSince'::timestamptz
            WHERE d.status IN ($statuses)
            GROUP BY d.id, d.submitted_at
            ORDER BY count(s.id) DESC, d.submitted_at DESC, d.id
            LIMIT $limit OFFSET $offset
        """.trimIndent()
        val ids = ArrayList<Uuid>()
        exec(sql) { rows -> while (rows.next()) ids += Uuid.parse(rows.getString(1)) }
        ids to total
    }

    suspend fun countByPayment(): Map<ConsultationPayment, Long> = dbQuery {
        val counter = ConsultationSessions.id.count()
        ConsultationSessions.select(ConsultationSessions.paymentState, counter)
            .groupBy(ConsultationSessions.paymentState)
            .associate { enumFromDb(it[ConsultationSessions.paymentState], ConsultationPayment.FREE) to it[counter] }
    }

    /** Rated sessions of a doctor, newest first. */
    suspend fun reviewsOf(doctorId: Uuid, limit: Int, offset: Long = 0): List<SessionRecord> = dbQuery {
        ConsultationSessions.selectAll()
            .where { (ConsultationSessions.doctorId eq doctorId) and ConsultationSessions.rating.isNotNull() }
            // The id breaks a tie in the time, so pages read by offset neither repeat nor skip one.
            .orderBy(ConsultationSessions.ratedAt to SortOrder.DESC, ConsultationSessions.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toSession() }
    }

    /** Average rating and count per doctor. */
    suspend fun ratings(doctorIds: Collection<Uuid>): Map<Uuid, Pair<Double, Int>> = dbQuery {
        if (doctorIds.isEmpty()) return@dbQuery emptyMap()
        ConsultationSessions.select(ConsultationSessions.doctorId, ConsultationSessions.rating)
            .where { (ConsultationSessions.doctorId inList doctorIds.distinct()) and ConsultationSessions.rating.isNotNull() }
            .groupBy({ it[ConsultationSessions.doctorId] }, { it[ConsultationSessions.rating]!!.toInt() })
            .mapValues { (_, values) -> values.average() to values.size }
    }

    /** The records a patient attached in a conversation, with when. */
    suspend fun recordMessages(conversationId: Uuid, patientId: Uuid): List<Pair<Uuid, Instant>> = dbQuery {
        CommunityMessages.select(CommunityMessages.id, CommunityMessages.createdAt)
            .where {
                (CommunityMessages.conversationId eq conversationId) and
                    (CommunityMessages.senderId eq patientId) and
                    (CommunityMessages.kind eq MessageKind.RECORD.dbValue()) and
                    (CommunityMessages.status eq ContentStatus.VISIBLE.dbValue())
            }
            .orderBy(CommunityMessages.createdAt to SortOrder.ASC)
            .map { it[CommunityMessages.id] to it[CommunityMessages.createdAt].toKotlinInstant() }
    }

    /** The topics of the room's posts she has answered, counted. */
    suspend fun answeredTopics(doctorId: Uuid): Map<CommunityTopic, Int> = dbQuery {
        val postIds = CommunityComments.select(CommunityComments.postId)
            .where { (CommunityComments.doctorId eq doctorId) and (CommunityComments.status eq ContentStatus.VISIBLE.dbValue()) }
            .map { it[CommunityComments.postId] }
            .distinct()
        if (postIds.isEmpty()) return@dbQuery emptyMap()
        CommunityPosts.select(CommunityPosts.topic)
            .where { CommunityPosts.id inList postIds }
            .mapNotNull { enumFromDb<CommunityTopic>(it[CommunityPosts.topic]) }
            .groupingBy { it }
            .eachCount()
    }

    // ---------------------------------------------------------------- the doctor's day

    suspend fun work(doctorId: Uuid): DoctorWorkRecord? = dbQuery {
        val row = DoctorProfiles.select(DoctorProfiles.consultationPriceMinor, DoctorProfiles.busy, DoctorProfiles.timezone)
            .where { DoctorProfiles.id eq doctorId }
            .singleOrNull() ?: return@dbQuery null
        DoctorWorkRecord(
            priceMinor = row[DoctorProfiles.consultationPriceMinor],
            busy = row[DoctorProfiles.busy],
            timezone = row[DoctorProfiles.timezone],
            hours = hoursOf(doctorId),
        )
    }

    suspend fun works(doctorIds: Collection<Uuid>): Map<Uuid, DoctorWorkRecord> = dbQuery {
        if (doctorIds.isEmpty()) return@dbQuery emptyMap()
        val hours = DoctorHoursTable.selectAll()
            .where { DoctorHoursTable.doctorId inList doctorIds.distinct() }
            .groupBy({ it[DoctorHoursTable.doctorId] }, { it.toHours() })
        DoctorProfiles.select(DoctorProfiles.id, DoctorProfiles.consultationPriceMinor, DoctorProfiles.busy, DoctorProfiles.timezone)
            .where { DoctorProfiles.id inList doctorIds.distinct() }
            .associate {
                val id = it[DoctorProfiles.id]
                id to DoctorWorkRecord(
                    it[DoctorProfiles.consultationPriceMinor],
                    it[DoctorProfiles.busy],
                    it[DoctorProfiles.timezone],
                    hours[id].orEmpty().sortedBy { h -> h.weekday },
                )
            }
    }

    private fun hoursOf(doctorId: Uuid): List<DoctorHours> =
        DoctorHoursTable.selectAll()
            .where { DoctorHoursTable.doctorId eq doctorId }
            .orderBy(DoctorHoursTable.weekday to SortOrder.ASC)
            .map { it.toHours() }

    /** Writes what is set; [hours] replaces the whole week in the same transaction. */
    suspend fun updateWork(doctorId: Uuid, priceMinor: Long?, busy: Boolean?, hours: List<DoctorHours>?): Unit = dbQuery {
        if (priceMinor != null || busy != null) {
            DoctorProfiles.update({ DoctorProfiles.id eq doctorId }) {
                priceMinor?.let { value -> it[consultationPriceMinor] = value }
                busy?.let { value -> it[DoctorProfiles.busy] = value }
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
        if (hours != null) {
            DoctorHoursTable.deleteWhere { DoctorHoursTable.doctorId eq doctorId }
            hours.forEach { day ->
                DoctorHoursTable.insert {
                    it[DoctorHoursTable.doctorId] = doctorId
                    it[weekday] = day.weekday
                    it[startMinute] = day.startMinute
                    it[endMinute] = day.endMinute
                }
            }
        }
    }

    // ---------------------------------------------------------------- quick replies

    suspend fun quickReplies(doctorId: Uuid): List<QuickReplyRecord> = dbQuery {
        DoctorQuickReplies.selectAll()
            .where { DoctorQuickReplies.doctorId eq doctorId }
            .orderBy(DoctorQuickReplies.position to SortOrder.ASC, DoctorQuickReplies.createdAt to SortOrder.ASC)
            .map { QuickReplyRecord(it[DoctorQuickReplies.id], it[DoctorQuickReplies.title], it[DoctorQuickReplies.body], it[DoctorQuickReplies.position]) }
    }

    suspend fun countQuickReplies(doctorId: Uuid): Long = dbQuery {
        DoctorQuickReplies.selectAll().where { DoctorQuickReplies.doctorId eq doctorId }.count()
    }

    suspend fun addQuickReply(doctorId: Uuid, title: String, body: String, position: Int): QuickReplyRecord = dbQuery {
        val id = Uuid.random()
        DoctorQuickReplies.insert {
            it[DoctorQuickReplies.id] = id
            it[DoctorQuickReplies.doctorId] = doctorId
            it[DoctorQuickReplies.title] = title
            it[DoctorQuickReplies.body] = body
            it[DoctorQuickReplies.position] = position
            it[createdAt] = now().toOffsetDateTime()
        }
        QuickReplyRecord(id, title, body, position)
    }

    suspend fun updateQuickReply(doctorId: Uuid, id: Uuid, title: String, body: String, position: Int): Boolean = dbQuery {
        DoctorQuickReplies.update({ (DoctorQuickReplies.id eq id) and (DoctorQuickReplies.doctorId eq doctorId) }) {
            it[DoctorQuickReplies.title] = title
            it[DoctorQuickReplies.body] = body
            it[DoctorQuickReplies.position] = position
        } > 0
    }

    suspend fun deleteQuickReply(doctorId: Uuid, id: Uuid): Boolean = dbQuery {
        DoctorQuickReplies.deleteWhere { (DoctorQuickReplies.id eq id) and (DoctorQuickReplies.doctorId eq doctorId) } > 0
    }

    // ---------------------------------------------------------------- notes

    suspend fun note(doctorId: Uuid, patientId: Uuid): Pair<String, Instant>? = dbQuery {
        DoctorPatientNotes.selectAll()
            .where { (DoctorPatientNotes.doctorId eq doctorId) and (DoctorPatientNotes.patientId eq patientId) }
            .singleOrNull()
            ?.let { it[DoctorPatientNotes.body] to it[DoctorPatientNotes.updatedAt].toKotlinInstant() }
    }

    /** An empty note is no note: the row goes rather than keeping an empty string. */
    suspend fun saveNote(doctorId: Uuid, patientId: Uuid, body: String): Instant = dbQuery {
        val timestamp = now()
        if (body.isEmpty()) {
            DoctorPatientNotes.deleteWhere { (DoctorPatientNotes.doctorId eq doctorId) and (DoctorPatientNotes.patientId eq patientId) }
        } else {
            DoctorPatientNotes.upsert {
                it[DoctorPatientNotes.doctorId] = doctorId
                it[DoctorPatientNotes.patientId] = patientId
                it[DoctorPatientNotes.body] = body
                it[updatedAt] = timestamp.toOffsetDateTime()
            }
        }
        timestamp
    }

    // ---------------------------------------------------------------- money

    suspend fun commissionPercent(): Int = dbQuery {
        AppSettings.select(AppSettings.value)
            .where { AppSettings.key eq COMMISSION_KEY }
            .singleOrNull()
            ?.get(AppSettings.value)
            ?.toIntOrNull()
            ?: DEFAULT_COMMISSION
    }

    suspend fun setCommissionPercent(percent: Int, by: Uuid): Unit = dbQuery {
        AppSettings.upsert {
            it[key] = COMMISSION_KEY
            it[value] = percent.toString()
            it[updatedAt] = now().toOffsetDateTime()
            it[updatedBy] = by
        }
    }

    /** A page of a doctor's payouts, the latest first, with how many there are in all. */
    suspend fun payouts(doctorId: Uuid, limit: Int, offset: Long): Pair<List<PayoutRecord>, Long> = dbQuery {
        val query = DoctorPayouts.selectAll().where { DoctorPayouts.doctorId eq doctorId }
        val total = query.count()
        query.orderBy(DoctorPayouts.paidAt to SortOrder.DESC, DoctorPayouts.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset)
            .map { it.toPayout() } to total
    }

    suspend fun payoutTotals(doctorIds: Collection<Uuid>): Map<Uuid, Long> = dbQuery {
        if (doctorIds.isEmpty()) return@dbQuery emptyMap()
        DoctorPayouts.select(DoctorPayouts.doctorId, DoctorPayouts.amountMinor)
            .where { DoctorPayouts.doctorId inList doctorIds.distinct() }
            .groupBy({ it[DoctorPayouts.doctorId] }, { it[DoctorPayouts.amountMinor] })
            .mapValues { (_, amounts) -> amounts.sum() }
    }

    suspend fun addPayout(doctorId: Uuid, amountMinor: Long, note: String?, by: Uuid): PayoutRecord = dbQuery {
        val id = Uuid.random()
        val timestamp = now()
        DoctorPayouts.insert {
            it[DoctorPayouts.id] = id
            it[DoctorPayouts.doctorId] = doctorId
            it[DoctorPayouts.amountMinor] = amountMinor
            it[DoctorPayouts.note] = note
            it[paidAt] = timestamp.toOffsetDateTime()
            it[createdBy] = by
        }
        PayoutRecord(id, doctorId, amountMinor, note, timestamp)
    }

    /** The paid transaction behind each session, for an operator refunding it in the provider's cabinet. */
    suspend fun paidTransactions(sessionIds: Collection<Uuid>): Map<Uuid, PaidTransaction> = dbQuery {
        if (sessionIds.isEmpty()) return@dbQuery emptyMap()
        PaymentTransactions.selectAll()
            .where {
                (PaymentTransactions.consultationSessionId inList sessionIds.distinct()) and
                    (PaymentTransactions.state eq PaymentState.PAID.dbValue())
            }
            .mapNotNull { row ->
                row[PaymentTransactions.consultationSessionId]?.let { session ->
                    session to PaidTransaction(
                        id = row[PaymentTransactions.id],
                        provider = enumFromDb(row[PaymentTransactions.provider], PaymentProvider.PAYME),
                        externalId = row[PaymentTransactions.externalId],
                    )
                }
            }
            .toMap()
    }

    // ---------------------------------------------------------------- mapping

    private fun ResultRow.toHours() = DoctorHours(
        weekday = this[DoctorHoursTable.weekday],
        startMinute = this[DoctorHoursTable.startMinute],
        endMinute = this[DoctorHoursTable.endMinute],
    )

    private fun ResultRow.toPayout() = PayoutRecord(
        id = this[DoctorPayouts.id],
        doctorId = this[DoctorPayouts.doctorId],
        amountMinor = this[DoctorPayouts.amountMinor],
        note = this[DoctorPayouts.note],
        paidAt = this[DoctorPayouts.paidAt].toKotlinInstant(),
    )

    private fun ResultRow.toSession() = SessionRecord(
        id = this[ConsultationSessions.id],
        conversationId = this[ConsultationSessions.conversationId],
        doctorId = this[ConsultationSessions.doctorId],
        patientId = this[ConsultationSessions.patientId],
        openedAt = this[ConsultationSessions.openedAt]?.toKotlinInstant(),
        expiresAt = this[ConsultationSessions.expiresAt]?.toKotlinInstant(),
        closedAt = this[ConsultationSessions.closedAt]?.toKotlinInstant(),
        closedReason = this[ConsultationSessions.closedReason],
        priceMinor = this[ConsultationSessions.priceMinor],
        commissionPercent = this[ConsultationSessions.commissionPercent],
        payment = enumFromDb(this[ConsultationSessions.paymentState], ConsultationPayment.FREE),
        firstReplyAt = this[ConsultationSessions.firstReplyAt]?.toKotlinInstant(),
        summary = this[ConsultationSessions.summary],
        rating = this[ConsultationSessions.rating]?.toInt(),
        review = this[ConsultationSessions.review],
        ratedAt = this[ConsultationSessions.ratedAt]?.toKotlinInstant(),
        refundedAt = this[ConsultationSessions.refundedAt]?.toKotlinInstant(),
        createdAt = this[ConsultationSessions.createdAt].toKotlinInstant(),
    )

    companion object {
        const val REASON_DOCTOR = "doctor"
        const val REASON_EXPIRED = "expired"
        const val REASON_REFUND = "refund"
        const val COMMISSION_KEY = "consultation_commission_percent"
        const val DEFAULT_COMMISSION = 20
        val MONEY_STATES = listOf(
            ConsultationPayment.PAID,
            ConsultationPayment.REFUND_DUE,
            ConsultationPayment.REFUNDED,
        ).map { it.dbValue() }
    }
}
