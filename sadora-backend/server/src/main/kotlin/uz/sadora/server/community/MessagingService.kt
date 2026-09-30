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
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.ReportRequest
import uz.sadora.contract.SendMessageRequest
import uz.sadora.contract.StartConsultationRequest
import uz.sadora.contract.StartConversationRequest
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.cache.Cache
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.RateLimitedException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.db.ContentStatus
import uz.sadora.server.doctor.DoctorRecord
import uz.sadora.server.doctor.DoctorRepository
import uz.sadora.server.notify.NotificationRepository
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
    private val clock: Clock = Clock.System,
) {

    // ---------------------------------------------------------------- reading

    suspend fun conversations(userId: Uuid, scope: ConversationScope = ConversationScope.ALL): List<Conversation> {
        community.openIdentity(userId)
        val myDoctor = doctors?.byUser(userId)
        val threads = messages.conversationsOf(userId, MAX_THREADS, scope, myDoctor?.id)
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
    suspend fun thread(userId: Uuid, conversationId: Uuid): ConversationThread {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val other = thread.other(userId)
        val lines = messages.messagesOf(conversationId, MAX_MESSAGES)
        messages.markRead(thread, userId)
        val otherReadAt = thread.readAt(other)
        return ConversationThread(
            conversation = viewFor(userId, listOf(thread)).dto(
                thread,
                last = lines.lastOrNull(),
                unread = 0,
                blocked = identities.blockedEitherWay(userId, other),
            ),
            messages = lines.map { it.toDto(userId, otherReadAt) },
            otherTyping = cache?.get(typingKey(conversationId, other)) != null,
            otherReadAt = otherReadAt,
        )
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
            messages.openConsultation(userId, doctor.userId, doctor.id, Limits.CONSULTATION_HOURS.hours)
        }
        if (firstLine != null) write(userId, thread, firstLine, MessageKind.TEXT, null)
        return thread(userId, thread.id)
    }

    /** The doctor ends a consultation before its window runs out. */
    suspend fun close(userId: Uuid, conversationId: Uuid): ConversationThread {
        community.openIdentity(userId)
        val thread = requireParticipant(userId, conversationId)
        val doctor = thread.doctorId?.let { doctors?.byId(it) }
        if (doctor == null || doctor.userId != userId) {
            throw ForbiddenException(message = "Konsultatsiyani faqat shifokor yopadi")
        }
        if (thread.isOpen(clock.now())) messages.closeConsultation(thread.id)
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
                    throw ValidationException("attachRecord", "Tibbiy kartani faqat bemor shifokoriga biriktiradi")
                }
                Triple(MessageKind.RECORD, "", null)
            }
            request.image != null -> Triple(MessageKind.IMAGE, validateCaption(request.body), decodeImage(request.image!!))
            else -> Triple(MessageKind.TEXT, validateText(request.body), null)
        }
        val message = write(userId, thread, body, kind, image)
        // What was written ends the "yozmoqda…" it came from.
        cache?.delete(typingKey(conversationId, userId))
        return message.toDto(userId, thread.readAt(other))
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
        if (body.isEmpty()) throw ValidationException("body", "Xabar bo'sh bo'lishi mumkin emas")
        if (body.length > Limits.MESSAGE_MAX) throw ValidationException("body", "Eng ko'pi ${Limits.MESSAGE_MAX} belgi")
        return body
    }

    private fun validateCaption(rawBody: String): String {
        val body = rawBody.trim()
        if (body.length > Limits.MESSAGE_MAX) throw ValidationException("body", "Eng ko'pi ${Limits.MESSAGE_MAX} belgi")
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
            ?: throw NotFoundException("Tibbiy karta topilmadi")
        val patient = message.senderId
        val readingOwn = patient == userId
        if (!readingOwn && !thread.isOpen(clock.now())) {
            throw ForbiddenException(message = "Konsultatsiya yopilgan — karta endi ko'rinmaydi")
        }
        val provider = records ?: throw NotFoundException("Tibbiy karta topilmadi")
        val owner = users?.findById(patient)
        if (owner == null || owner.status != AccountStatus.ACTIVE) throw NotFoundException("Tibbiy karta topilmadi")
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
        val name = when {
            doctor != null && doctor.userId == sender -> "${doctor.fullName} ✓"
            doctor != null -> users?.findById(sender)?.name?.takeIf { it.isNotBlank() } ?: PATIENT_FALLBACK
            else -> identities.identitiesFor(listOf(sender))[sender]?.alias ?: CommunityService.FALLBACK_ALIAS
        }
        val preview = when (message.kind) {
            MessageKind.TEXT -> message.body
            MessageKind.IMAGE -> "📷 Rasm" + message.body.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()
            MessageKind.RECORD -> "📋 Tibbiy karta biriktirildi"
        }
        notifications.enqueue(
            userId = recipient,
            category = NotificationCategory.SYSTEM,
            title = "$name: yangi xabar",
            body = preview.take(PUSH_PREVIEW),
            scheduledFor = message.createdAt,
            dedupeKey = "dm:${message.id}",
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
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
        return ThreadView(
            viewer = viewer,
            identities = identities.identitiesFor(others),
            activity = identities.activityFor(others),
            doctors = doctorsById,
            patients = patients,
            now = clock.now(),
        )
    }

    private inner class ThreadView(
        val viewer: Uuid,
        val identities: Map<Uuid, IdentityRecord>,
        val activity: Map<Uuid, ActivityStats>,
        val doctors: Map<Uuid, DoctorRecord>,
        val patients: Map<Uuid, UserRecord>,
        val now: Instant,
    ) {
        fun dto(thread: ConversationRecord, last: MessageRecord?, unread: Int, blocked: Boolean): Conversation {
            val other = thread.other(viewer)
            val identity = identities[other]
            val doctor = thread.doctorId?.let { doctors[it] }
            val consultation = if (thread.isConsultation) {
                Consultation(
                    openedAt = thread.openedAt ?: thread.createdAt,
                    expiresAt = thread.expiresAt ?: thread.createdAt,
                    closedAt = thread.closedAt,
                    open = thread.isOpen(now),
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
                            ConsultationPatient(it.name.ifBlank { PATIENT_FALLBACK }, it.birthDate?.let(::ageOf), it.lifeStage)
                        },
                    )
                }
                // The viewer is the patient: the doctor, as she signs everything.
                else -> base.copy(
                    alias = doctor.fullName,
                    badges = emptyList(),
                    doctor = DoctorAuthor(doctor.id.toString(), doctor.fullName, doctor.specialty),
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
        const val PUSH_PREVIEW = 80
        const val UNAVAILABLE = "Bu taxallusga xabar yozib bo'lmaydi"
        const val DOCTOR_UNAVAILABLE = "Shifokor hozir konsultatsiya qabul qilmayapti"
        const val CONSULTATION_CLOSED = "Konsultatsiya yopilgan — yangisini oching"
        const val PATIENT_FALLBACK = "Bemor"
        val IMAGE_MIME = setOf("image/jpeg", "image/png")
    }
}
