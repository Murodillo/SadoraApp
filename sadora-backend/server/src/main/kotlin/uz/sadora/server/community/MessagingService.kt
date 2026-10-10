package uz.sadora.server.community

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.Consultation
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.ConsultationPatient
import uz.sadora.contract.Conversation
import uz.sadora.contract.ConversationThread
import uz.sadora.contract.DirectMessage
import uz.sadora.contract.DoctorAuthor
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Language
import uz.sadora.contract.Limits
import uz.sadora.contract.MessageImage
import uz.sadora.contract.MessageImageUpload
import uz.sadora.contract.MessageKind
import uz.sadora.contract.MessagePage
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.SendPrescriptionRequest
import uz.sadora.contract.StartConsultationRequest
import uz.sadora.contract.StartConversationRequest
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.cache.Cache
import uz.sadora.server.consultation.ConsultationRepository
import uz.sadora.server.consultation.SessionRecord
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ConsultationPaymentRequiredException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.Photos
import uz.sadora.server.core.RateLimitedException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.doctor.DoctorRecord
import uz.sadora.server.doctor.DoctorRepository
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.prescription.PrescriptionRecord
import uz.sadora.server.prescription.PrescriptionRepository
import uz.sadora.server.prescription.PrescriptionRules
import uz.sadora.server.notify.TARGET_CLIENT
import uz.sadora.server.notify.TARGET_DOCTOR
import uz.sadora.server.user.UserRecord
import uz.sadora.server.user.UserRepository

/**
 * Private messages: between two aliases, and between a patient and a verified doctor.
 *
 * Between aliases, the same gates as the feed — the room must be open to her, and a
 * silenced author cannot write — plus three of its own. Nobody messages herself. A
 * closed door (`dmOpen`) or a block on either side refuses the thread with the one word
 * "not available", so a blocked sender learns nothing about which it was. And there is a
 * per-day ceiling, because a private channel is where spam goes when the feed is
 * moderated.
 *
 * A consultation is the same thread with a doctor profile on it. The patient opens it
 * from the doctor's page, for [Limits.CONSULTATION_HOURS]; it takes messages from both
 * sides until then or until the doctor closes it, and the patient may open it again.
 * Here names are real: the doctor writes as herself, and she sees the patient's name and
 * age — the app says so before the first line. The patient may attach her health
 * record, which is assembled when the doctor opens it and only while the window is open.
 *
 * The push a message earns names the sender and nothing else; the phone that receives
 * it is looked up by account, inside the outbox, and never appears here.
 */
class MessagingService(
    private val messages: MessagingRepository,
    private val community: CommunityService,
    private val identities: CommunityRepository,
    private val notifications: NotificationRepository,
    /** The doctors behind consultations; null in tests without them. */
    private val doctors: DoctorRepository? = null,
    /** The patient's name and age, for her doctor. */
    private val users: UserRepository? = null,
    /** The record a patient attaches: the same document her QR code opens. */
    private val records: (suspend (Uuid, Language) -> DoctorSummary)? = null,
    /** Where "yozmoqda…" lives for a few seconds; without it nobody is ever typing. */
    private val cache: Cache? = null,
    private val audit: AuditService? = null,
    /** Each window's session: price, payment, first reply, summary, rating. Null in older tests. */
    private val consultations: ConsultationRepository? = null,
    private val clock: Clock = Clock.System,
    /** The structured copy behind `prescription` lines. Null in tests without them. */
    private val prescriptions: PrescriptionRepository? = null,
    /** The avatar frame each alias wears; null in tests, which then show none. */
    private val wornFrames: (suspend (Collection<Uuid>) -> Map<Uuid, String>)? = null,
) {
    /** What happens after a window is closed — the patient's push. Set at wiring. */
    var onSessionClosed: (suspend (SessionRecord) -> Unit)? = null

    // ---------------------------------------------------------------- reading

    /**
     * Her threads, most recently written first, [limit] at a time. The next page is the
     * threads last written before [before] — the `lastMessageAt` of the last one she has.
     */
    suspend fun conversations(
        userId: Uuid,
        scope: ConversationScope = ConversationScope.ALL,
        limit: Int = MAX_THREADS,
        before: Instant? = null,
    ): List<Conversation> {
        community.openIdentity(userId)
        val myDoctor = doctors?.byUser(userId)
        val threads = messages.conversationsOf(userId, limit.coerceIn(1, MAX_THREADS), scope, myDoctor?.id, before)
        if (threads.isEmpty()) return emptyList()
        val view = viewFor(userId, threads)
        val last = messages.lastMessages(threads.map { it.id })
        val unread = messages.unreadCounts(threads, userId)
        return threads.map { thread ->
            view.dto(
                thread,
                last = last[thread.id],
                unread = unread[thread.id] ?: 0,
                blocked = identities.blockedEitherWay(userId, thread.other(userId)),
            )
        }
    }

    /** Opens the thread and marks it read: what is on screen has been seen. */
    suspend fun thread(userId: Uuid, conversationId: Uuid, limit: Int = MAX_MESSAGES): ConversationThread {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val other = thread.other(userId)
        val (lines, hasMore) = messages.messagesOf(conversationId, limit.coerceIn(1, MAX_MESSAGES))
        messages.markRead(thread, userId)
        val otherReadAt = thread.readAt(other)
        return ConversationThread(
            conversation = viewFor(userId, listOf(thread)).dto(
                thread,
                last = lines.lastOrNull(),
                unread = 0,
                blocked = identities.blockedEitherWay(userId, other),
            ),
            messages = dtos(userId, lines, otherReadAt),
            otherTyping = cache?.get(typingKey(conversationId, other)) != null,
            otherReadAt = otherReadAt,
            hasMore = hasMore,
        )
    }

    /**
     * The lines written before [beforeId], scrolling up a long thread. Reads only: what
     * is on screen was marked read when the thread was opened.
     */
    suspend fun olderMessages(userId: Uuid, conversationId: Uuid, beforeId: Uuid, limit: Int): MessagePage {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val anchor = messages.messageById(beforeId)?.takeIf { it.conversationId == conversationId }
            ?: throw NotFoundException("Xabar topilmadi")
        val (lines, hasMore) = messages.messagesOf(conversationId, limit.coerceIn(1, MAX_MESSAGES), before = anchor)
        val otherReadAt = thread.readAt(thread.other(userId))
        return MessagePage(dtos(userId, lines, otherReadAt), hasMore)
    }

    // ---------------------------------------------------------------- starting

    /** Finds or opens the thread with [StartConversationRequest.alias] and sends the first line. */
    suspend fun start(userId: Uuid, request: StartConversationRequest): ConversationThread {
        val me = community.openIdentity(userId)
        val target = identities.identityByAlias(request.alias.trim()) ?: throw NotFoundException("Taxallus topilmadi")
        if (target.userId == me.userId) throw ValidationException("alias", "O'zingizga xabar yozib bo'lmaydi")
        if (!target.dmOpen || identities.blockedEitherWay(userId, target.userId)) {
            throw ForbiddenException(message = UNAVAILABLE)
        }
        // The line is checked before the thread exists: an empty or over-long first
        // message used to open an empty conversation on the other side's list anyway.
        val body = validateText(request.body)
        val thread = messages.openConversation(userId, target.userId)
        write(userId, thread, body, MessageKind.TEXT, null)
        return thread(userId, thread.id)
    }

    /**
     * Opens a consultation with a doctor, or opens it again once its window has closed.
     * A consultation still open is returned as it is: a second tap on the doctor's page
     * must not quietly give her another day.
     */
    suspend fun startConsultation(userId: Uuid, doctorId: Uuid, request: StartConsultationRequest): ConversationThread {
        community.openIdentity(userId)
        val doctor = doctors?.byId(doctorId)?.takeIf { it.status == DoctorStatus.APPROVED }
            ?: throw NotFoundException("Shifokor topilmadi")
        if (doctor.userId == userId) throw ValidationException("doctorId", "O'zingizga yozib bo'lmaydi")
        if (!doctor.acceptsConsultations || identities.blockedEitherWay(userId, doctor.userId)) {
            throw ForbiddenException(message = DOCTOR_UNAVAILABLE)
        }
        val firstLine = request.body?.trim()?.takeIf { it.isNotEmpty() }?.let(::validateText)
        val existing = messages.consultationBetween(userId, doctor.userId, doctor.id)
        val thread = if (existing != null && existing.isOpen(clock.now())) {
            existing
        } else {
            // A paid doctor's window opens on payment, not here: the app is told the
            // price and goes to the checkout.
            val price = consultations?.work(doctor.id)?.priceMinor ?: 0L
            if (price > 0) throw ConsultationPaymentRequiredException(price)
            messages.openConsultation(userId, doctor.userId, doctor.id, Limits.CONSULTATION_HOURS.hours).also { opened ->
                consultations?.createSession(
                    conversationId = opened.id,
                    doctorId = doctor.id,
                    patientId = userId,
                    priceMinor = 0,
                    commissionPercent = 0,
                    payment = ConsultationPayment.FREE,
                    openedAt = opened.openedAt,
                    expiresAt = opened.expiresAt,
                )
            }
        }
        if (firstLine != null) write(userId, thread, firstLine, MessageKind.TEXT, null)
        return thread(userId, thread.id)
    }

    /**
     * The doctor ends a consultation before its window runs out, or — once it has run
     * out — adds the advice she did not get to write. [summary] is what the patient
     * keeps as "Shifokor tavsiyasi".
     */
    suspend fun close(userId: Uuid, conversationId: Uuid, summary: String? = null): ConversationThread {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val doctor = thread.doctorId?.let { doctors?.byId(it) }
        if (doctor == null || doctor.userId != userId) {
            throw ForbiddenException(message = "Konsultatsiyani faqat shifokor yopadi")
        }
        val advice = summary?.trim()?.takeIf { it.isNotEmpty() }
        if (advice != null && advice.length > SUMMARY_MAX) throw ValidationException("summary", "Tavsiya eng ko'pi $SUMMARY_MAX belgi bo'lsin")
        val at = clock.now()
        val session = consultations?.currentSession(thread.id)
        if (thread.isOpen(at)) {
            messages.closeConsultation(thread.id)
            if (session != null) {
                consultations?.close(session.id, at, ConsultationRepository.REASON_DOCTOR, advice)?.let { onSessionClosed?.invoke(it) }
            }
        } else if (session != null && advice != null && session.summary == null) {
            consultations?.setSummary(session.id, advice, at)
        }
        return thread(userId, conversationId)
    }

    // ---------------------------------------------------------------- writing

    suspend fun send(userId: Uuid, conversationId: Uuid, request: SendMessageRequest): DirectMessage {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val other = thread.other(userId)
        if (identities.blockedEitherWay(userId, other)) throw ForbiddenException(message = UNAVAILABLE)
        if (!thread.isOpen(clock.now())) throw ForbiddenException(message = CONSULTATION_CLOSED)
        if (thread.isConsultation) {
            // A suspended doctor's consultations stop taking messages with her page.
            val doctor = thread.doctorId?.let { doctors?.byId(it) }
            if (doctor == null || doctor.status != DoctorStatus.APPROVED) throw ForbiddenException(message = DOCTOR_UNAVAILABLE)
        }

        val (kind, body, image) = when {
            request.attachRecord -> {
                val doctor = thread.doctorId?.let { doctors?.byId(it) }
                if (doctor == null || doctor.userId == userId) {
                    throw ValidationException("attachRecord", "Bemor kartasini faqat bemorning o'zi shifokoriga biriktiradi")
                }
                Triple(MessageKind.RECORD, "", null)
            }
            request.image != null -> Triple(MessageKind.IMAGE, validateCaption(request.body), decodeImage(request.image!!))
            else -> Triple(MessageKind.TEXT, validateText(request.body), null)
        }
        val message = write(userId, thread, body, kind, image)
        // The doctor's first line in a window is what "answered" and the reply time mean.
        if (thread.isConsultation && thread.doctorId != null && doctors?.byId(thread.doctorId)?.userId == userId) {
            consultations?.currentSession(thread.id)
                ?.takeIf { it.firstReplyAt == null }
                ?.let { consultations.markFirstReply(it.id, message.createdAt) }
        }
        // What was written ends the "yozmoqda…" it came from.
        cache?.delete(typingKey(conversationId, userId))
        return message.toDto(userId, thread.readAt(other))
    }

    /**
     * The doctor sends a prescription into the consultation. Only the consultation's
     * doctor, only while its window is open; the line's body is the prescription as
     * plain text, and the structured copy is stored beside it.
     */
    suspend fun sendPrescription(userId: Uuid, conversationId: Uuid, request: SendPrescriptionRequest): DirectMessage {
        val store = prescriptions ?: throw NotFoundException("Retsept topilmadi")
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val other = thread.other(userId)
        val doctor = thread.doctorId?.let { doctors?.byId(it) }
        if (doctor == null || doctor.userId != userId) throw ForbiddenException(message = "Retseptni faqat shifokor yozadi")
        if (doctor.status != DoctorStatus.APPROVED) throw ForbiddenException(message = DOCTOR_UNAVAILABLE)
        if (identities.blockedEitherWay(userId, other)) throw ForbiddenException(message = UNAVAILABLE)
        if (!thread.isOpen(clock.now())) throw ForbiddenException(message = CONSULTATION_CLOSED)

        val clean = PrescriptionRules.clean(request)
        val message = write(userId, thread, PrescriptionRules.plainText(clean.items, clean.note), MessageKind.PRESCRIPTION, null)
        store.insert(
            PrescriptionRecord(
                id = Uuid.random(),
                messageId = message.id,
                conversationId = thread.id,
                doctorId = doctor.id,
                patientId = other,
                items = clean.items,
                note = clean.note,
                createdAt = message.createdAt,
                cancelledAt = null,
                cancelReason = null,
                addedAt = null,
            ),
        )
        consultations?.currentSession(thread.id)
            ?.takeIf { it.firstReplyAt == null }
            ?.let { consultations.markFirstReply(it.id, message.createdAt) }
        cache?.delete(typingKey(conversationId, userId))
        return dtos(userId, listOf(message), thread.readAt(other)).single()
    }

    /** "yozmoqda…": remembered for a few seconds, and only while she may write. */
    suspend fun typing(userId: Uuid, conversationId: Uuid) {
        val thread = requireParticipant(userId, conversationId)
        if (!thread.isOpen(clock.now())) return
        cache?.set(typingKey(conversationId, userId), "1", Limits.TYPING_SECONDS.seconds)
    }

    private suspend fun write(
        userId: Uuid,
        thread: ConversationRecord,
        body: String,
        kind: MessageKind,
        image: MessageImageRecord?,
    ): MessageRecord {
        community.requireCanWrite(userId)
        if (messages.messagesSince(userId, now() - 24.hours) >= MAX_MESSAGES_PER_DAY) {
            throw RateLimitedException("Bir kunda $MAX_MESSAGES_PER_DAY tadan ko'p xabar yozib bo'lmaydi")
        }
        val message = messages.insertMessage(thread.id, userId, body, kind, image)
        notify(thread, recipient = thread.other(userId), sender = userId, message = message)
        return message
    }

    private fun validateText(rawBody: String): String {
        val body = rawBody.trim()
        if (body.isEmpty()) throw ValidationException("body", "Xabarni yozing")
        if (body.length > Limits.MESSAGE_MAX) throw ValidationException("body", "Xabar eng ko'pi ${Limits.MESSAGE_MAX} belgi bo'lsin")
        return body
    }

    private fun validateCaption(rawBody: String): String {
        val body = rawBody.trim()
        if (body.length > Limits.MESSAGE_MAX) throw ValidationException("body", "Xabar eng ko'pi ${Limits.MESSAGE_MAX} belgi bo'lsin")
        return body
    }

    /**
     * A photo is checked for what it claims to be: the size is read from the picture
     * itself, and bytes that are not a JPEG or PNG are refused rather than stored.
     */
    private fun decodeImage(upload: MessageImageUpload): MessageImageRecord {
        if (upload.mimeType !in IMAGE_MIME) throw ValidationException("image", "Faqat JPEG yoki PNG rasm")
        val text = upload.imageBase64.trim()
        if (text.isEmpty()) throw ValidationException("image", "Rasm bo'sh")
        if (text.length > Limits.MESSAGE_IMAGE_MAX_BYTES / 3 * 4 + 4) {
            throw ValidationException("image", "Rasm juda katta — kichikroq qilib yuboring")
        }
        val bytes = runCatching { Base64.decode(text) }.getOrElse { throw ValidationException("image", "Rasmni o'qib bo'lmadi") }
        val picture = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: throw ValidationException("image", "Rasmni o'qib bo'lmadi")
        return MessageImageRecord(upload.mimeType, picture.width, picture.height, bytes)
    }

    // ---------------------------------------------------------------- attachments

    /** A photo in a thread, for the two people in it. */
    suspend fun image(userId: Uuid, conversationId: Uuid, messageId: Uuid): MessageImageRecord {
        val thread = requireParticipant(userId, conversationId)
        val message = messages.messageById(messageId)
            ?.takeIf { it.conversationId == thread.id && it.status == ContentStatus.VISIBLE && it.kind == MessageKind.IMAGE }
            ?: throw NotFoundException("Rasm topilmadi")
        return messages.imageOf(message.id) ?: throw NotFoundException("Rasm topilmadi")
    }

    /**
     * The record a patient attached, assembled now. Her doctor reads it while the
     * consultation is open — once it closes the door closes with it — and she can always
     * read her own. Every opening by the doctor is audited, as a QR scan is.
     */
    suspend fun record(userId: Uuid, conversationId: Uuid, messageId: Uuid, language: Language?): DoctorSummary {
        val thread = requireParticipant(userId, conversationId)
        val message = messages.messageById(messageId)
            ?.takeIf { it.conversationId == thread.id && it.status == ContentStatus.VISIBLE && it.kind == MessageKind.RECORD }
            ?: throw NotFoundException("Bemor kartasi topilmadi")
        val patient = message.senderId
        val readingOwn = patient == userId
        if (!readingOwn && !thread.isOpen(clock.now())) {
            throw ForbiddenException(message = "Konsultatsiya yopilgan — bemor kartasi endi ko'rinmaydi")
        }
        val provider = records ?: throw NotFoundException("Bemor kartasi topilmadi")
        val owner = users?.findById(patient)
        if (owner == null || owner.status != AccountStatus.ACTIVE) throw NotFoundException("Bemor kartasi topilmadi")
        if (!readingOwn) {
            audit?.record(
                AuditEntry(
                    actorType = ActorType.USER,
                    actorId = userId,
                    action = AuditActions.CONSULTATION_RECORD_VIEWED,
                    entityType = "community_message",
                    entityId = message.id.toString(),
                ),
            )
        }
        return provider(patient, language ?: owner.language)
    }

    // ---------------------------------------------------------------- reports

    /**
     * Files a report on the other side's latest message. Reports are what a moderator
     * reads, so the line reported is the one she can see, not the whole thread.
     */
    suspend fun report(userId: Uuid, conversationId: Uuid, request: ReportRequest) {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val target = messages.latestFrom(conversationId, thread.other(userId))
            ?: throw ValidationException("conversationId", "Shikoyat qiladigan xabar yo'q")
        val note = request.note?.trim()?.take(Limits.REPORT_NOTE_MAX)
        if (!identities.addReport(userId, null, null, request.reason, note, messageId = target.id)) {
            throw ConflictException("Bu xabar allaqachon shikoyat qilingan")
        }
    }

    // ---------------------------------------------------------------- push

    private suspend fun notify(thread: ConversationRecord, recipient: Uuid, sender: Uuid, message: MessageRecord) {
        // Her notification switch applies to a message like to a reminder: off means
        // nothing is queued, rather than a push the scheduler would deliver anyway.
        val settings = notifications.settingsOf(recipient)
        if (!settings.enabled || !settings.isCategoryEnabled(NotificationCategory.SYSTEM)) return
        val doctor = thread.doctorId?.let { doctors?.byId(it) }
        val language = users?.findById(recipient)?.language ?: uz.sadora.contract.Language.UZ
        val name = when {
            doctor != null && doctor.userId == sender -> "${doctor.fullName} ✓"
            doctor != null -> users?.findById(sender)?.name?.takeIf { it.isNotBlank() }
                ?: uz.sadora.server.consultation.ConsultationPhrases.patientFallback(language)
            else -> identities.identitiesFor(listOf(sender))[sender]?.alias ?: CommunityService.FALLBACK_ALIAS
        }
        // Who wrote, never what, nor whether it was a photo, a record or a prescription:
        // a push is read on the lock screen by whoever holds the phone.
        val words = uz.sadora.server.consultation.ConsultationPhrases.message(name, language)
        notifications.enqueue(
            userId = recipient,
            category = NotificationCategory.SYSTEM,
            title = words.title,
            body = words.body,
            scheduledFor = message.createdAt,
            dedupeKey = "dm:${message.id}",
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
            // A patient's line rings the doctor app; everything else the women's app.
            targetApp = if (doctor != null && doctor.userId == recipient) TARGET_DOCTOR else TARGET_CLIENT,
            link = "sadora://conversation/${thread.id}",
        )
    }

    // ---------------------------------------------------------------- views

    private suspend fun requireParticipant(userId: Uuid, conversationId: Uuid): ConversationRecord {
        val thread = messages.conversationById(conversationId)
        // Not hers reads the same as not there: a thread id is not a thing to probe.
        if (thread == null || !thread.has(userId)) throw NotFoundException("Suhbat topilmadi")
        return thread
    }

    /** Everything a list of threads needs about the people in them, read once for the page. */
    private suspend fun viewFor(viewer: Uuid, threads: List<ConversationRecord>): ThreadView {
        val others = threads.map { it.other(viewer) }.distinct()
        val doctorIds = threads.mapNotNull { it.doctorId }.distinct()
        val doctorsById = if (doctorIds.isEmpty()) emptyMap() else doctors?.byIds(doctorIds).orEmpty()
        // Patients are looked up only where the viewer is their doctor.
        val patientIds = threads.filter { t -> doctorsById[t.doctorId]?.userId == viewer }.map { it.other(viewer) }
        val patients = patientIds.distinct().mapNotNull { id -> users?.findById(id)?.let { id to it } }.toMap()
        val consultationIds = threads.filter { it.isConsultation }.map { it.id }
        return ThreadView(
            viewer = viewer,
            sessions = consultations?.sessionsOf(consultationIds).orEmpty(),
            prices = consultations?.works(doctorIds)?.mapValues { it.value.priceMinor }.orEmpty(),
            identities = identities.identitiesFor(others),
            activity = identities.activityFor(others),
            // Only aliases wear a frame: never the two sides of a consultation.
            frames = runCatching { wornFrames?.invoke(threads.filter { !it.isConsultation }.map { it.other(viewer) }) }
                .getOrNull().orEmpty(),
            doctors = doctorsById,
            patients = patients,
            now = clock.now(),
        )
    }

    private inner class ThreadView(
        val viewer: Uuid,
        /** Each consultation's sessions, newest first. */
        val sessions: Map<Uuid, List<SessionRecord>>,
        /** Each doctor's price now, by doctor profile. */
        val prices: Map<Uuid, Long>,
        val identities: Map<Uuid, IdentityRecord>,
        val activity: Map<Uuid, ActivityStats>,
        val frames: Map<Uuid, String>,
        val doctors: Map<Uuid, DoctorRecord>,
        val patients: Map<Uuid, UserRecord>,
        val now: Instant,
    ) {
        fun dto(thread: ConversationRecord, last: MessageRecord?, unread: Int, blocked: Boolean): Conversation {
            val other = thread.other(viewer)
            val identity = identities[other]
            val doctor = thread.doctorId?.let { doctors[it] }
            val consultation = if (thread.isConsultation) {
                val all = sessions[thread.id].orEmpty()
                val current = all.filter { it.openedAt != null }.maxByOrNull { it.openedAt!! }
                val isPatient = doctor != null && doctor.userId != viewer
                Consultation(
                    openedAt = thread.openedAt ?: thread.createdAt,
                    expiresAt = thread.expiresAt ?: thread.createdAt,
                    closedAt = thread.closedAt,
                    open = thread.isOpen(now),
                    sessionId = current?.id?.toString(),
                    priceMinor = current?.priceMinor ?: 0,
                    payment = current?.payment ?: ConsultationPayment.FREE,
                    summary = all.filter { it.summary != null }.maxByOrNull { it.openedAt ?: it.createdAt }?.summary,
                    canRate = isPatient && current?.firstReplyAt != null && current.rating == null,
                    rating = current?.rating,
                    answered = current?.firstReplyAt != null,
                    doctorPriceMinor = thread.doctorId?.let { prices[it] } ?: 0,
                )
            } else {
                null
            }
            val base = Conversation(
                id = thread.id.toString(),
                alias = identity?.alias ?: CommunityService.FALLBACK_ALIAS,
                tint = identity?.tint ?: 0,
                badges = activity[other]?.let { CommunityBadges.of(it, now) }.orEmpty(),
                lastMessage = last?.preview(),
                lastMessageAt = last?.createdAt ?: thread.lastMessageAt,
                unread = unread,
                blocked = blocked,
                consultation = consultation,
                lastMessageKind = last?.kind ?: MessageKind.TEXT,
                lastMessageRead = last != null && last.senderId == viewer &&
                    thread.readAt(other)?.let { it >= last.createdAt } == true,
                frame = if (thread.isConsultation) null else frames[other],
            )
            return when {
                doctor == null -> base
                // The viewer is the doctor: the patient, by her real name.
                doctor.userId == viewer -> {
                    val patient = patients[other]
                    base.copy(
                        alias = patient?.name?.takeIf { it.isNotBlank() } ?: PATIENT_FALLBACK,
                        badges = emptyList(),
                        patient = patient?.let {
                            ConsultationPatient(
                                it.name.ifBlank { PATIENT_FALLBACK },
                                it.birthDate?.let(::ageOf),
                                it.lifeStage,
                                Photos.conversationUrlFor(thread.id, it.avatarUrl),
                            )
                        },
                    )
                }
                // The viewer is the patient: the doctor, as she signs everything.
                else -> base.copy(
                    alias = doctor.fullName,
                    badges = emptyList(),
                    doctor = DoctorAuthor(
                        doctor.id.toString(),
                        doctor.fullName,
                        doctor.specialty,
                        doctor.photoUpdatedAt?.let { Photos.doctorUrl(doctor.id, it) },
                    ),
                )
            }
        }

        private fun ageOf(birthDate: LocalDate): Int {
            val today = now.toLocalDateTime(TimeZone.UTC).date
            var age = today.year - birthDate.year
            if (today.month.ordinal < birthDate.month.ordinal ||
                (today.month == birthDate.month && today.day < birthDate.day)
            ) {
                age--
            }
            return age.coerceAtLeast(0)
        }
    }

    private fun MessageRecord.preview(): String = when (kind) {
        MessageKind.TEXT -> body.take(PREVIEW_LENGTH)
        // The apps word these themselves from lastMessageKind; a caption rides along.
        MessageKind.IMAGE -> body.take(PREVIEW_LENGTH)
        MessageKind.RECORD -> ""
        MessageKind.PRESCRIPTION -> ""
    }

    /**
     * A page of lines as the apps read them. The prescriptions behind `prescription`
     * lines are read once for the page, with their doctor.
     */
    private suspend fun dtos(viewer: Uuid, lines: List<MessageRecord>, otherReadAt: Instant?): List<DirectMessage> {
        val ids = lines.filter { it.kind == MessageKind.PRESCRIPTION }.map { it.id }
        val byMessage = if (ids.isEmpty()) emptyMap() else prescriptions?.byMessages(ids).orEmpty()
        val doctorsById = byMessage.values.map { it.doctorId }.distinct()
            .takeIf { it.isNotEmpty() }?.let { doctors?.byIds(it) }.orEmpty()
        return lines.map { line ->
            val prescription = byMessage[line.id]?.let { record ->
                doctorsById[record.doctorId]?.let { PrescriptionRules.dto(record, it, patientName = null) }
            }
            line.toDto(viewer, otherReadAt).copy(prescription = prescription)
        }
    }

    private fun MessageRecord.toDto(viewer: Uuid, otherReadAt: Instant?) = DirectMessage(
        id = id.toString(),
        body = body,
        createdAt = createdAt,
        isMine = senderId == viewer,
        kind = kind,
        image = if (kind == MessageKind.IMAGE && imageWidth != null && imageHeight != null) {
            MessageImage(imageWidth, imageHeight, imageMime ?: "image/jpeg")
        } else {
            null
        },
        read = senderId == viewer && otherReadAt != null && otherReadAt >= createdAt,
    )

    private fun typingKey(conversationId: Uuid, userId: Uuid) = "typing:$conversationId:$userId"

    companion object {
        const val MAX_THREADS = 100
        const val MAX_MESSAGES = 200
        const val MAX_MESSAGES_PER_DAY = 200
        const val PREVIEW_LENGTH = 120
        const val UNAVAILABLE = "Bu taxallusga xabar yozib bo'lmaydi"
        const val DOCTOR_UNAVAILABLE = "Shifokor hozir konsultatsiya qabul qilmayapti"
        const val CONSULTATION_CLOSED = "Konsultatsiya yopilgan — yangisini oching"
        const val PATIENT_FALLBACK = "Bemor"
        const val SUMMARY_MAX = 2000
        val IMAGE_MIME = setOf("image/jpeg", "image/png")
    }
}
