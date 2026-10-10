package uz.sadora.server.consultation

import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import uz.sadora.contract.CheckoutSession
import uz.sadora.contract.ConsultationPatient
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.ConsultationSession
import uz.sadora.contract.DoctorAvailability
import uz.sadora.contract.DoctorEarnings
import uz.sadora.contract.DoctorHours
import uz.sadora.contract.DoctorListItem
import uz.sadora.contract.DoctorPayoutView
import uz.sadora.contract.DoctorProfile
import uz.sadora.contract.DoctorReview
import uz.sadora.contract.DoctorSettings
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStats
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.EarningLine
import uz.sadora.contract.Limits
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.Page
import uz.sadora.contract.PatientHistory
import uz.sadora.contract.PatientNote
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.QuickReply
import uz.sadora.contract.RateConsultationRequest
import uz.sadora.contract.SavePatientNoteRequest
import uz.sadora.contract.SaveQuickReplyRequest
import uz.sadora.contract.TopicCount
import uz.sadora.contract.UpdateDoctorSettingsRequest
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.billing.BillingService
import uz.sadora.server.billing.TransactionRecord
import uz.sadora.server.community.CommunityRepository
import uz.sadora.server.community.ConversationRecord
import uz.sadora.server.community.MessagingRepository
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.Photos
import uz.sadora.server.core.ValidationException
import uz.sadora.server.doctor.DoctorRecord
import uz.sadora.server.doctor.DoctorRepository
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.notify.TARGET_CLIENT
import uz.sadora.server.notify.TARGET_DOCTOR
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.user.UserRepository

/**
 * The doctor's working day and the money around a consultation.
 *
 * A consultation's thread lives in messaging; each 24-hour window of it is a session
 * here. A free doctor's window opens the moment the patient asks (messaging does that);
 * a paid one opens when the provider says the money arrived, through [onPaid]. A paid
 * window that ends without one line from the doctor is owed back: it is marked
 * `refund_due` and an operator returns it in the provider's cabinet, since neither
 * Payme nor Click refunds through the API we use.
 *
 * Her hours and the "busy" switch only inform: a patient is told when she is likely to
 * answer, and may still write. A price is taken as it stood when the checkout started.
 */
class ConsultationService(
    private val repository: ConsultationRepository,
    private val messages: MessagingRepository,
    private val doctors: DoctorRepository,
    private val users: UserRepository,
    private val identities: CommunityRepository,
    private val billing: BillingService,
    private val notifications: NotificationRepository,
    private val audit: AuditService,
    private val clock: Clock = Clock.System,
) {

    // ---------------------------------------------------------------- the doctor's settings

    suspend fun settings(userId: Uuid): DoctorSettings {
        val doctor = requireApprovedDoctor(userId)
        val work = repository.work(doctor.id) ?: throw NotFoundException("Shifokor topilmadi")
        return DoctorSettings(
            priceMinor = work.priceMinor,
            busy = work.busy,
            hours = work.hours,
            timezone = work.timezone,
            acceptsConsultations = doctor.acceptsConsultations,
            commissionPercent = repository.commissionPercent(),
        )
    }

    suspend fun updateSettings(userId: Uuid, request: UpdateDoctorSettingsRequest): DoctorSettings {
        val doctor = requireApprovedDoctor(userId)
        request.priceMinor?.let { price ->
            if (price != 0L && (price < MIN_PRICE_MINOR || price > MAX_PRICE_MINOR)) {
                throw ValidationException(
                    "priceMinor",
                    "Narx 0 (bepul) yoki ${MIN_PRICE_MINOR / 100}–${MAX_PRICE_MINOR / 100} so'm oralig'ida bo'lsin",
                )
            }
        }
        request.hours?.let(::validateHours)
        repository.updateWork(doctor.id, request.priceMinor, request.busy, request.hours?.sortedBy { it.weekday })
        return settings(userId)
    }

    private fun validateHours(hours: List<DoctorHours>) {
        if (hours.map { it.weekday }.toSet().size != hours.size) throw ValidationException("hours", "Har kun bir marta")
        hours.forEach { day ->
            if (day.weekday !in 1..7) throw ValidationException("hours", "Hafta kuni 1–7")
            if (day.startMinute !in 0..1439 || day.endMinute !in 1..1440 || day.endMinute <= day.startMinute) {
                throw ValidationException("hours", "Boshlanish tugashdan oldin bo'lsin")
            }
        }
    }

    /**
     * Whether she answers now. With no hours set, her hours are "always" and only the
     * switch speaks; with hours, she is online inside them and not busy.
     */
    fun availability(work: DoctorWorkRecord, accepts: Boolean, at: Instant = clock.now()): DoctorAvailability {
        val zone = zoneOf(work.timezone)
        val local = at.toLocalDateTime(zone)
        val minute = local.hour * 60 + local.minute
        val today = work.hours.firstOrNull { it.weekday == local.dayOfWeek.isoDayNumber }
        val inHours = work.hours.isEmpty() || (today != null && minute >= today.startMinute && minute < today.endMinute)
        val online = accepts && !work.busy && inHours
        val next = if (online || !accepts || work.hours.isEmpty()) {
            null
        } else {
            (0..7).firstNotNullOfOrNull { offset ->
                val date = local.date.plus(DatePeriod(days = offset))
                val day = work.hours.firstOrNull { it.weekday == date.dayOfWeek.isoDayNumber } ?: return@firstNotNullOfOrNull null
                if (offset == 0 && minute >= day.startMinute) return@firstNotNullOfOrNull null
                LocalDateTime(date, LocalTime(day.startMinute / 60, day.startMinute % 60)).toInstant(zone)
            }
        }
        return DoctorAvailability(online, work.busy, next, work.hours, work.timezone)
    }

    // ---------------------------------------------------------------- public pages

    /** Her price, rating, hours and the ways to pay, on the page a patient reads. */
    suspend fun decorate(viewer: Uuid, profile: DoctorProfile): DoctorProfile {
        val id = Uuid.parse(profile.id)
        val doctor = doctors.byId(id) ?: return profile
        val work = repository.work(id) ?: return profile
        val rating = repository.ratings(listOf(id))[id]
        return profile.copy(
            priceMinor = work.priceMinor,
            rating = rating?.first?.roundTo1(),
            ratingCount = rating?.second ?: 0,
            availability = availability(work, doctor.acceptsConsultations),
            paymentProviders = if (work.priceMinor > 0 && !profile.isMe) billing.consultationProviders(viewer) else emptyList(),
        )
    }

    /**
     * The directory: each doctor with her price, rating, hours and reply habits, in the
     * "recommended" order. The apps re-sort for rating, price or speed; this order is
     * what a patient sees first, so it is the one decided here.
     */
    suspend fun decorate(list: List<DoctorListItem>): List<DoctorListItem> {
        val ids = list.map { Uuid.parse(it.id) }
        val works = repository.works(ids)
        val ratings = repository.ratings(ids)
        val records = doctors.byIds(ids)
        // Every doctor's reply habits in one grouped read; it used to be one read of all
        // her sessions per doctor, on every open of the directory.
        val at = clock.now()
        val totals = repository.totals(ids, at, at - 7.days, at - 30.days)
        val scored = list.map { item ->
            val id = Uuid.parse(item.id)
            val work = works[id]
            val figures = totals[id] ?: DoctorTotals()
            val answered = figures.total - figures.unanswered
            val decorated = item.copy(
                priceMinor = work?.priceMinor ?: 0,
                rating = ratings[id]?.first?.roundTo1(),
                ratingCount = ratings[id]?.second ?: 0,
                onlineNow = work != null && availability(work, records[id]?.acceptsConsultations == true).onlineNow,
                avgFirstReplyMinutes = figures.avgFirstReplyMinutes,
                consultationsTotal = figures.total,
                fastReply = answered >= Limits.DOCTOR_RATING_MIN &&
                    (figures.avgFirstReplyMinutes ?: Int.MAX_VALUE) <= Limits.DOCTOR_FAST_REPLY_MINUTES,
            )
            decorated to recommendScore(decorated, ratings[id], figures)
        }
        // The id settles a full tie, so the directory read in pages cuts the same order each time.
        return scored.sortedWith(
            compareByDescending<Pair<DoctorListItem, Double>> { it.second }
                .thenByDescending { it.first.answerCount }
                .thenBy { it.first.id },
        ).map { it.first }
    }

    /**
     * The "recommended" order. The rating is a Bayesian average — pulled towards
     * [PRIOR_RATING] by [PRIOR_WEIGHT] imaginary ratings — so 4.9 from fifty outranks
     * 5.0 from two. Then small nudges: online now, a fast reply habit, and a penalty for
     * the share of windows she left unanswered.
     */
    private fun recommendScore(item: DoctorListItem, rating: Pair<Double, Int>?, figures: DoctorTotals): Double {
        val (average, count) = rating ?: (0.0 to 0)
        val bayes = (PRIOR_RATING * PRIOR_WEIGHT + average * count) / (PRIOR_WEIGHT + count)
        val unansweredShare = if (figures.total == 0) 0.0 else figures.unanswered.toDouble() / figures.total
        return bayes +
            (if (item.onlineNow) 0.3 else 0.0) +
            (if (item.fastReply) 0.2 else 0.0) -
            unansweredShare
    }

    /** Her reviews, newest first, [limit] from [offset]; no paging asked is the latest [MAX_REVIEWS]. */
    suspend fun reviews(doctorId: Uuid, limit: Int = MAX_REVIEWS, offset: Long = 0): List<DoctorReview> =
        repository.reviewsOf(doctorId, limit.coerceIn(1, MAX_REVIEWS), offset)
            .map { DoctorReview(it.rating!!, it.review, it.ratedAt ?: it.createdAt) }

    // ---------------------------------------------------------------- paying

    /**
     * A checkout for a window with a paid doctor. The thread is made (without a window)
     * so the payment has somewhere to land; a checkout she abandoned is reused while
     * its price still stands, so tapping "pay" twice does not make two.
     */
    suspend fun checkout(userId: Uuid, doctorId: Uuid, provider: PaymentProvider, origin: String): CheckoutSession {
        val (session, _) = pendingSessionFor(userId, doctorId)
        return billing.consultationCheckout(userId, session.id, session.priceMinor, provider, origin)
    }

    /** The checkout's pending window, and the doctor's name — for her to ask someone else to pay it. */
    suspend fun pendingSessionFor(userId: Uuid, doctorId: Uuid): Pair<SessionRecord, String> {
        val doctor = doctors.byId(doctorId)?.takeIf { it.status == DoctorStatus.APPROVED }
            ?: throw NotFoundException("Shifokor topilmadi")
        if (doctor.userId == userId) throw ValidationException("doctorId", "O'zingizga yozib bo'lmaydi")
        if (!doctor.acceptsConsultations || identities.blockedEitherWay(userId, doctor.userId)) {
            throw ForbiddenException(message = "Shifokor hozir konsultatsiya qabul qilmayapti")
        }
        val work = repository.work(doctor.id) ?: throw NotFoundException("Shifokor topilmadi")
        if (work.priceMinor == 0L) throw ValidationException("provider", "Bu shifokor bilan konsultatsiya bepul")
        val thread = messages.ensureConsultation(userId, doctor.userId, doctor.id)
        if (thread.isOpen(clock.now())) throw ConflictException("Konsultatsiya allaqachon ochiq")
        val session = repository.pendingSession(thread.id)?.takeIf { it.priceMinor == work.priceMinor }
            ?: repository.createSession(
                conversationId = thread.id,
                doctorId = doctor.id,
                patientId = userId,
                priceMinor = work.priceMinor,
                commissionPercent = repository.commissionPercent(),
                payment = ConsultationPayment.PENDING,
                openedAt = null,
                expiresAt = null,
            )
        return session to doctor.fullName
    }

    /** A window still waiting for its money, at its price — what a request's payer may pay. */
    suspend fun payableSession(sessionId: Uuid): SessionRecord? =
        repository.session(sessionId)?.takeIf { it.payment == ConsultationPayment.PENDING }

    suspend fun doctorName(doctorId: Uuid): String? = doctors.byId(doctorId)?.fullName

    /** The provider says the money arrived: the window opens, and the doctor hears of it. */
    suspend fun onPaid(sessionId: Uuid, transaction: TransactionRecord) {
        val session = repository.session(sessionId) ?: return
        if (session.payment != ConsultationPayment.PENDING || transaction.amountMinor != session.priceMinor) return
        val at = clock.now()
        if (!repository.markPaid(sessionId, at, at + Limits.CONSULTATION_HOURS.hours)) return
        val doctor = doctors.byId(session.doctorId) ?: return
        messages.openConsultation(session.patientId, doctor.userId, doctor.id, Limits.CONSULTATION_HOURS.hours)
        val patientName = users.findById(session.patientId)?.name?.takeIf { it.isNotBlank() }
        push(
            userId = doctor.userId,
            text = { language ->
                ConsultationPhrases.paid(patientName ?: ConsultationPhrases.patientFallback(language), Limits.CONSULTATION_HOURS, language)
            },
            dedupeKey = "consultation_paid:$sessionId",
            target = TARGET_DOCTOR,
            conversationId = session.conversationId,
        )
    }

    // ---------------------------------------------------------------- ending, rating

    /**
     * Closes the windows whose time is up. A paid one without a word from the doctor
     * becomes `refund_due`; an answered one asks the patient for a rating.
     */
    suspend fun expireDue(): Int {
        val due = repository.expiredOpen(clock.now(), EXPIRE_BATCH)
        due.forEach { session ->
            val closed = repository.close(session.id, session.expiresAt ?: clock.now(), ConsultationRepository.REASON_EXPIRED, null)
                ?: return@forEach
            afterClose(closed)
        }
        return due.size
    }

    /** What the patient hears when a window ends: money coming back, or a rating to give. */
    suspend fun afterClose(session: SessionRecord) {
        when {
            session.payment == ConsultationPayment.REFUND_DUE -> push(
                userId = session.patientId,
                text = ConsultationPhrases::refundDue,
                dedupeKey = "consultation_refund:${session.id}",
                target = TARGET_CLIENT,
                conversationId = session.conversationId,
            )
            session.firstReplyAt != null && session.rating == null -> push(
                userId = session.patientId,
                text = { language -> ConsultationPhrases.rate(session.summary != null, language) },
                dedupeKey = "consultation_rate:${session.id}",
                target = TARGET_CLIENT,
                conversationId = session.conversationId,
            )
        }
    }

    suspend fun rate(userId: Uuid, conversationId: Uuid, request: RateConsultationRequest) {
        val thread = messages.conversationById(conversationId)?.takeIf { it.has(userId) && it.isConsultation }
            ?: throw NotFoundException("Suhbat topilmadi")
        val doctor = thread.doctorId?.let { doctors.byId(it) } ?: throw NotFoundException("Shifokor topilmadi")
        if (doctor.userId == userId) throw ForbiddenException(message = "Konsultatsiyani bemor baholaydi")
        if (request.rating !in 1..5) throw ValidationException("rating", "1 dan 5 gacha")
        val review = request.review?.trim()?.takeIf { it.isNotEmpty() }
        if (review != null && review.length > REVIEW_MAX) throw ValidationException("review", "Sharh eng ko'pi $REVIEW_MAX belgi bo'lsin")
        val session = repository.currentSession(thread.id) ?: throw ValidationException("rating", "Baholanadigan konsultatsiya yo'q")
        if (session.firstReplyAt == null) throw ValidationException("rating", "Shifokor hali javob bermagan")
        if (!repository.rate(session.id, request.rating, review)) throw ConflictException("Siz bu konsultatsiyaga baho qo'ygansiz")
    }

    // ---------------------------------------------------------------- quick replies

    suspend fun quickReplies(userId: Uuid): List<QuickReply> =
        repository.quickReplies(requireApprovedDoctor(userId).id).map { it.toDto() }

    suspend fun addQuickReply(userId: Uuid, request: SaveQuickReplyRequest): QuickReply {
        val doctor = requireApprovedDoctor(userId)
        val (title, body) = validateReply(request)
        if (repository.countQuickReplies(doctor.id) >= MAX_QUICK_REPLIES) {
            throw ValidationException("title", "Eng ko'pi $MAX_QUICK_REPLIES ta tayyor javob")
        }
        return repository.addQuickReply(doctor.id, title, body, request.position).toDto()
    }

    suspend fun updateQuickReply(userId: Uuid, id: Uuid, request: SaveQuickReplyRequest): QuickReply {
        val doctor = requireApprovedDoctor(userId)
        val (title, body) = validateReply(request)
        if (!repository.updateQuickReply(doctor.id, id, title, body, request.position)) throw NotFoundException("Topilmadi")
        return QuickReply(id.toString(), title, body, request.position)
    }

    suspend fun deleteQuickReply(userId: Uuid, id: Uuid) {
        val doctor = requireApprovedDoctor(userId)
        if (!repository.deleteQuickReply(doctor.id, id)) throw NotFoundException("Topilmadi")
    }

    private fun validateReply(request: SaveQuickReplyRequest): Pair<String, String> {
        val title = request.title.trim()
        val body = request.body.trim()
        if (title.isEmpty() || title.length > 60) throw ValidationException("title", "Sarlavha 1–60 belgi")
        if (body.isEmpty() || body.length > Limits.MESSAGE_MAX) throw ValidationException("body", "Matn 1–${Limits.MESSAGE_MAX} belgi")
        return title to body
    }

    // ---------------------------------------------------------------- one patient

    suspend fun note(userId: Uuid, conversationId: Uuid): PatientNote {
        val (doctor, thread) = requireOwnConsultation(userId, conversationId)
        val note = repository.note(doctor.id, thread.other(userId))
        return PatientNote(note?.first.orEmpty(), note?.second)
    }

    suspend fun saveNote(userId: Uuid, conversationId: Uuid, request: SavePatientNoteRequest): PatientNote {
        val (doctor, thread) = requireOwnConsultation(userId, conversationId)
        val body = request.body.trim()
        if (body.length > NOTE_MAX) throw ValidationException("body", "Izoh eng ko'pi $NOTE_MAX belgi bo'lsin")
        val at = repository.saveNote(doctor.id, thread.other(userId), body)
        return PatientNote(body, at.takeIf { body.isNotEmpty() })
    }

    /** Every window she has had with this patient, with what was attached in each. */
    suspend fun history(userId: Uuid, conversationId: Uuid): PatientHistory {
        val (_, thread) = requireOwnConsultation(userId, conversationId)
        val patientId = thread.other(userId)
        val sessions = repository.sessionsOf(listOf(thread.id))[thread.id].orEmpty()
            .filter { it.openedAt != null }
            .sortedBy { it.openedAt }
        val records = repository.recordMessages(thread.id, patientId)
        val patient = users.findById(patientId)
        return PatientHistory(
            patient = patient?.let {
                ConsultationPatient(it.name.ifBlank { "Bemor" }, null, it.lifeStage, Photos.conversationUrlFor(thread.id, it.avatarUrl))
            },
            sessions = sessions.map { session ->
                val end = session.closedAt ?: session.expiresAt
                ConsultationSession(
                    id = session.id.toString(),
                    openedAt = session.openedAt,
                    expiresAt = session.expiresAt,
                    closedAt = session.closedAt,
                    closedReason = session.closedReason,
                    priceMinor = session.priceMinor,
                    payment = session.payment,
                    firstReplyAt = session.firstReplyAt,
                    summary = session.summary,
                    rating = session.rating,
                    review = session.review,
                    recordMessageIds = records
                        .filter { (_, at) -> at >= session.openedAt!! && (end == null || at <= end) }
                        .map { it.first.toString() },
                )
            },
        )
    }

    // ---------------------------------------------------------------- her numbers

    suspend fun stats(userId: Uuid): DoctorStats {
        val doctor = requireApprovedDoctor(userId)
        // Counted by the database: a busy doctor's Home used to load every session she
        // ever held to add them up.
        val at = clock.now()
        val figures = repository.totals(listOf(doctor.id), at, at - 7.days, at - 30.days)[doctor.id] ?: DoctorTotals()
        val topics = repository.answeredTopics(doctor.id)
        return DoctorStats(
            consultationsWeek = figures.week,
            consultationsMonth = figures.month,
            consultationsTotal = figures.total,
            openNow = figures.openNow,
            avgFirstReplyMinutes = figures.avgFirstReplyMinutes,
            unansweredTotal = figures.unanswered,
            rating = figures.rating?.roundTo1(),
            ratingCount = figures.ratingCount,
            topTopics = topics.entries.sortedByDescending { it.value }.take(TOP_TOPICS).map { TopicCount(it.key, it.value) },
            answersTotal = topics.values.sum(),
        )
    }

    suspend fun earnings(userId: Uuid): DoctorEarnings = earningsOf(requireApprovedDoctor(userId).id)

    suspend fun earningLines(userId: Uuid, limit: Int, offset: Long): Page<EarningLine> =
        linesOf(requireApprovedDoctor(userId).id, limit, offset)

    suspend fun payouts(userId: Uuid, limit: Int, offset: Long): Page<DoctorPayoutView> =
        payoutsOf(requireApprovedDoctor(userId).id, limit, offset)

    /**
     * The totals, summed by the database over every session and payout, and the first
     * page of each list. A doctor with years of consultations costs the same as a new one.
     */
    private suspend fun earningsOf(doctorId: Uuid): DoctorEarnings {
        val at = clock.now()
        val totals = repository.totals(listOf(doctorId), at, at - 7.days, at - 30.days)[doctorId] ?: DoctorTotals()
        val paidOut = repository.payoutTotals(listOf(doctorId))[doctorId] ?: 0
        val lines = linesOf(doctorId, DoctorEarnings.PAGE, 0)
        val payouts = payoutsOf(doctorId, DoctorEarnings.PAGE, 0)
        return moneyOf(totals, paidOut).copy(
            lines = lines.items,
            payouts = payouts.items,
            linesTotal = lines.total,
            payoutsTotal = payouts.total,
        )
    }

    private suspend fun linesOf(doctorId: Uuid, limit: Int, offset: Long): Page<EarningLine> {
        val (sessions, total) = repository.moneySessions(doctorId, limit, offset)
        val names = repository.patientNames(sessions.map { it.patientId })
        val lines = sessions.map { s ->
            val earns = s.payment == ConsultationPayment.PAID
            EarningLine(
                sessionId = s.id.toString(),
                patientName = names[s.patientId]?.ifBlank { null } ?: "Bemor",
                openedAt = s.openedAt,
                priceMinor = s.priceMinor,
                commissionMinor = if (earns) s.commissionMinor else 0,
                netMinor = if (earns) s.priceMinor - s.commissionMinor else 0,
                payment = s.payment,
            )
        }
        return Page(lines, total, limit, offset.toInt())
    }

    private suspend fun payoutsOf(doctorId: Uuid, limit: Int, offset: Long): Page<DoctorPayoutView> {
        val (payouts, total) = repository.payouts(doctorId, limit, offset)
        return Page(payouts.map { DoctorPayoutView(it.id.toString(), it.amountMinor, it.note, it.paidAt) }, total, limit, offset.toInt())
    }

    private fun moneyOf(totals: DoctorTotals, paidOut: Long): DoctorEarnings {
        val net = totals.grossMinor - totals.commissionMinor
        return DoctorEarnings(
            grossMinor = totals.grossMinor,
            commissionMinor = totals.commissionMinor,
            netMinor = net,
            paidOutMinor = paidOut,
            balanceMinor = net - paidOut,
            refundDueMinor = totals.refundDueMinor,
        )
    }

    // ---------------------------------------------------------------- operators

    suspend fun adminConsultations(payment: ConsultationPayment?, limit: Int, offset: Long): AdminConsultationPage {
        val (rows, total) = repository.paidSessions(payment, limit, offset)
        val doctorsById = doctors.byIds(rows.map { it.doctorId })
        val transactions = repository.paidTransactions(rows.map { it.id })
        val counts = repository.countByPayment()
        return AdminConsultationPage(
            page = Page(
                rows.map { s ->
                    val patient = users.findById(s.patientId)
                    val tx = transactions[s.id]
                    AdminConsultationRow(
                        id = s.id.toString(),
                        doctorId = s.doctorId.toString(),
                        doctorName = doctorsById[s.doctorId]?.fullName.orEmpty(),
                        patientId = s.patientId.toString(),
                        patientPhone = patient?.phone,
                        priceMinor = s.priceMinor,
                        commissionMinor = s.commissionMinor,
                        payment = s.payment,
                        provider = tx?.provider,
                        transactionId = tx?.id?.toString(),
                        providerTransactionId = tx?.externalId,
                        openedAt = s.openedAt,
                        closedAt = s.closedAt,
                        closedReason = s.closedReason,
                        firstReplyAt = s.firstReplyAt,
                        rating = s.rating,
                        refundedAt = s.refundedAt,
                        createdAt = s.createdAt,
                    )
                },
                total,
                limit,
                offset.toInt(),
            ),
            paid = counts[ConsultationPayment.PAID] ?: 0,
            refundDue = counts[ConsultationPayment.REFUND_DUE] ?: 0,
            refunded = counts[ConsultationPayment.REFUNDED] ?: 0,
            commissionPercent = repository.commissionPercent(),
        )
    }

    suspend fun markRefunded(sessionId: Uuid, admin: AdminPrincipal, context: RequestContext) {
        val session = repository.session(sessionId) ?: throw NotFoundException("Konsultatsiya topilmadi")
        if (!repository.markRefunded(sessionId, admin.adminId)) {
            throw ConflictException("To'lov holati «${session.payment.uzWord()}» — qaytarildi deb belgilab bo'lmaydi")
        }
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = AuditActions.CONSULTATION_REFUNDED,
                entityType = "consultation_session",
                entityId = sessionId.toString(),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
    }

    suspend fun commission(): CommissionView = CommissionView(repository.commissionPercent())

    suspend fun setCommission(percent: Int, admin: AdminPrincipal, context: RequestContext): CommissionView {
        if (percent !in 0..100) throw ValidationException("percent", "0 dan 100 gacha")
        val before = repository.commissionPercent()
        repository.setCommissionPercent(percent, admin.adminId)
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = AuditActions.COMMISSION_CHANGED,
                entityType = "app_setting",
                entityId = ConsultationRepository.COMMISSION_KEY,
                reason = "$before → $percent",
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        return CommissionView(percent)
    }

    /**
     * A page of the approved and suspended doctors, the busiest this month first, with
     * how each answers and what she is owed. A handful of reads per page, not per doctor.
     */
    suspend fun quality(limit: Int, offset: Long): Page<AdminDoctorQuality> {
        val at = clock.now()
        val (ids, total) = repository.qualityPage(at - 30.days, limit, offset)
        val records = doctors.byIds(ids)
        val works = repository.works(ids)
        val paidOut = repository.payoutTotals(ids)
        val totals = repository.totals(ids, at, at - 7.days, at - 30.days)
        val rows = ids.mapNotNull { records[it] }.map { doctor ->
            val figures = totals[doctor.id] ?: DoctorTotals()
            val money = moneyOf(figures, paidOut[doctor.id] ?: 0)
            val work = works[doctor.id]
            AdminDoctorQuality(
                doctorId = doctor.id.toString(),
                fullName = doctor.fullName,
                specialty = doctor.specialty,
                status = doctor.status,
                priceMinor = work?.priceMinor ?: 0,
                busy = work?.busy ?: false,
                onlineNow = work != null && availability(work, doctor.acceptsConsultations).onlineNow,
                consultationsTotal = figures.total,
                consultationsMonth = figures.month,
                openNow = figures.openNow,
                avgFirstReplyMinutes = figures.avgFirstReplyMinutes,
                unansweredTotal = figures.unanswered,
                rating = figures.rating?.roundTo1(),
                ratingCount = figures.ratingCount,
                grossMinor = money.grossMinor,
                netMinor = money.netMinor,
                paidOutMinor = money.paidOutMinor,
                balanceMinor = money.balanceMinor,
                refundDueMinor = money.refundDueMinor,
                photoUrl = doctor.photoUpdatedAt?.let { Photos.adminDoctorUrl(doctor.id, it) },
            )
        }
        return Page(rows, total, limit, offset.toInt())
    }

    suspend fun adminEarnings(doctorId: Uuid): DoctorEarnings {
        doctors.byId(doctorId) ?: throw NotFoundException("Shifokor topilmadi")
        return earningsOf(doctorId)
    }

    suspend fun adminEarningLines(doctorId: Uuid, limit: Int, offset: Long): Page<EarningLine> {
        doctors.byId(doctorId) ?: throw NotFoundException("Shifokor topilmadi")
        return linesOf(doctorId, limit, offset)
    }

    suspend fun adminPayouts(doctorId: Uuid, limit: Int, offset: Long): Page<DoctorPayoutView> {
        doctors.byId(doctorId) ?: throw NotFoundException("Shifokor topilmadi")
        return payoutsOf(doctorId, limit, offset)
    }

    suspend fun addPayout(doctorId: Uuid, request: CreatePayoutRequest, admin: AdminPrincipal, context: RequestContext): DoctorEarnings {
        doctors.byId(doctorId) ?: throw NotFoundException("Shifokor topilmadi")
        if (request.amountMinor <= 0) throw ValidationException("amountMinor", "Summa musbat bo'lsin")
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > 500) throw ValidationException("note", "Izoh eng ko'pi 500 belgi bo'lsin")
        val payout = repository.addPayout(doctorId, request.amountMinor, note, admin.adminId)
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = AuditActions.DOCTOR_PAYOUT_RECORDED,
                entityType = "doctor_profile",
                entityId = doctorId.toString(),
                reason = "${payout.amountMinor / 100} so'm" + (note?.let { " — $it" } ?: ""),
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        return earningsOf(doctorId)
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun requireApprovedDoctor(userId: Uuid): DoctorRecord =
        doctors.byUser(userId)?.takeIf { it.status == DoctorStatus.APPROVED }
            ?: throw ForbiddenException(message = "Faqat tasdiqlangan shifokorlar uchun")

    /** A consultation she holds as the doctor; anything else reads as not there. */
    private suspend fun requireOwnConsultation(userId: Uuid, conversationId: Uuid): Pair<DoctorRecord, ConversationRecord> {
        val doctor = requireApprovedDoctor(userId)
        val thread = messages.conversationById(conversationId)
        if (thread == null || !thread.has(userId) || thread.doctorId != doctor.id) throw NotFoundException("Suhbat topilmadi")
        return doctor to thread
    }

    /** A push in the language of the person it reaches. */
    private suspend fun push(
        userId: Uuid,
        text: (uz.sadora.contract.Language) -> ConsultationPhrases.Text,
        dedupeKey: String,
        target: String,
        conversationId: Uuid,
    ) {
        val settings = notifications.settingsOf(userId)
        if (!settings.enabled || !settings.isCategoryEnabled(NotificationCategory.SYSTEM)) return
        val words = text(users.findById(userId)?.language ?: uz.sadora.contract.Language.UZ)
        notifications.enqueue(
            userId = userId,
            category = NotificationCategory.SYSTEM,
            title = words.title,
            body = words.body,
            scheduledFor = clock.now(),
            dedupeKey = dedupeKey,
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
            targetApp = target,
            link = conversationLink(conversationId),
        )
    }

    /** The payment's state as the staff panel reads it, in Uzbek. */
    private fun ConsultationPayment.uzWord(): String = when (this) {
        ConsultationPayment.FREE -> "bepul"
        ConsultationPayment.PENDING -> "to'lanmagan"
        ConsultationPayment.PAID -> "to'langan"
        ConsultationPayment.REFUND_DUE -> "qaytarilishi kerak"
        ConsultationPayment.REFUNDED -> "qaytarilgan"
    }

    private fun QuickReplyRecord.toDto() = QuickReply(id.toString(), title, body, position)

    companion object {
        /** 1 000 so'm: Payme's and Click's smallest payment. */
        const val MIN_PRICE_MINOR = 100_000L

        /** 2 000 000 so'm. */
        const val MAX_PRICE_MINOR = 200_000_000L
        const val MAX_QUICK_REPLIES = 30
        const val NOTE_MAX = 4000
        const val REVIEW_MAX = 1000
        const val MAX_REVIEWS = 50
        const val TOP_TOPICS = 5
        const val EXPIRE_BATCH = 200

        /** The quality table's page, and the most one request may ask for. */
        const val QUALITY_PAGE = 50
        const val MAX_QUALITY_PAGE = 200

        /** The most earnings lines or payouts one request may ask for. */
        const val MAX_EARNINGS_PAGE = 200

        /** What a doctor with no ratings is assumed to be, for ordering only. */
        const val PRIOR_RATING = 4.5
        const val PRIOR_WEIGHT = 5

        fun conversationLink(conversationId: Uuid) = "sadora://conversation/$conversationId"

        private fun zoneOf(id: String): TimeZone = runCatching { TimeZone.of(id) }.getOrElse { TimeZone.of("Asia/Tashkent") }

        private fun Double.roundTo1(): Double = kotlin.math.round(this * 10) / 10
    }
}

// ---------------------------------------------------------------- operator views

@Serializable
data class AdminConsultationRow(
    val id: String,
    val doctorId: String,
    val doctorName: String,
    val patientId: String,
    val patientPhone: String? = null,
    val priceMinor: Long,
    val commissionMinor: Long,
    val payment: ConsultationPayment,
    val provider: PaymentProvider? = null,
    /** Ours, and the provider's own id — what the provider's cabinet searches by. */
    val transactionId: String? = null,
    val providerTransactionId: String? = null,
    val openedAt: Instant? = null,
    val closedAt: Instant? = null,
    val closedReason: String? = null,
    val firstReplyAt: Instant? = null,
    val rating: Int? = null,
    val refundedAt: Instant? = null,
    val createdAt: Instant,
)

@Serializable
data class AdminConsultationPage(
    val page: Page<AdminConsultationRow>,
    val paid: Long,
    val refundDue: Long,
    val refunded: Long,
    val commissionPercent: Int,
)

@Serializable
data class CommissionView(val percent: Int)

@Serializable
data class SetCommissionRequest(val percent: Int)

@Serializable
data class CreatePayoutRequest(val amountMinor: Long, val note: String? = null)

@Serializable
data class AdminDoctorQuality(
    val doctorId: String,
    val fullName: String,
    val specialty: DoctorSpecialty,
    val status: DoctorStatus,
    val priceMinor: Long,
    val busy: Boolean,
    val onlineNow: Boolean,
    val consultationsTotal: Int,
    val consultationsMonth: Int,
    val openNow: Int,
    val avgFirstReplyMinutes: Int?,
    val unansweredTotal: Int,
    val rating: Double?,
    val ratingCount: Int,
    val grossMinor: Long,
    val netMinor: Long,
    val paidOutMinor: Long,
    val balanceMinor: Long,
    val refundDueMinor: Long,
    val photoUrl: String? = null,
)
