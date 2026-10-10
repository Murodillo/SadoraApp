package uz.sadora.server.doctor

import kotlin.io.encoding.Base64
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable
import uz.sadora.contract.AccountStatus
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorApplicationRequest
import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorSpecialty
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.Limits
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.Page
import uz.sadora.contract.UpdateDoctorProfileRequest
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.Photos
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.plugins.AdminPrincipal
import uz.sadora.server.user.UserRepository

// ---------------------------------------------------------------- admin views
//
// Unlike the moderation views these carry the account and its phone: a doctor applied
// under her real name precisely so an admin could check it, and a licence is verified
// by calling the person who holds it.

@Serializable
data class AdminDoctorRow(
    val id: String,
    val userId: String,
    val fullName: String,
    val specialty: DoctorSpecialty,
    val workplace: String,
    val experienceYears: Int,
    val status: DoctorStatus,
    val documentCount: Int,
    val submittedAt: Instant,
    val reviewedAt: Instant? = null,
    /** Her photo, through the admin route; null when she has none. */
    val photoUrl: String? = null,
)

@Serializable
data class AdminDoctorDocument(
    val id: String,
    val kind: DoctorDocumentKind,
    val mimeType: String,
    val sizeBytes: Int,
    val createdAt: Instant,
)

@Serializable
data class AdminDoctorDetail(
    val id: String,
    val userId: String,
    val phone: String? = null,
    val accountName: String,
    val fullName: String,
    val specialty: DoctorSpecialty,
    val workplace: String,
    val experienceYears: Int,
    val licenseNumber: String,
    val bio: String? = null,
    val status: DoctorStatus,
    val reviewNote: String? = null,
    val submittedAt: Instant,
    val reviewedAt: Instant? = null,
    val verifiedAt: Instant? = null,
    val documents: List<AdminDoctorDocument> = emptyList(),
    val acceptsConsultations: Boolean = true,
    /** Counts only: how busy her consultations are, never what was said in them. */
    val consultations: DoctorConsultationStats? = null,
    val photoUrl: String? = null,
)

@Serializable
data class DoctorConsultationStats(
    val total: Long = 0,
    val open: Long = 0,
    val messagesFromDoctor: Long = 0,
    val messagesFromPatients: Long = 0,
    val lastMessageAt: Instant? = null,
)

@Serializable
data class DoctorCountsView(
    val pending: Long = 0,
    val approved: Long = 0,
    val rejected: Long = 0,
    val suspended: Long = 0,
)

@Serializable
data class DoctorReviewRequest(
    /** `approve`, `reject`, `suspend` or `reinstate`. */
    val action: String,
    /** Required to reject or suspend; the doctor reads it. */
    val note: String? = null,
)

/**
 * The doctor role: applying, being reviewed, and editing what an approved doctor may.
 *
 * What a doctor writes in the room goes through [uz.sadora.server.community.CommunityService]
 * like everyone else's; this service only decides who is a doctor.
 */
class DoctorService(
    private val doctors: DoctorRepository,
    private val users: UserRepository,
    private val audit: AuditService,
    private val notifications: NotificationRepository,
    /** Where her consultation counts come from; null in tests without messages. */
    private val messaging: uz.sadora.server.community.MessagingRepository? = null,
) {

    // ---------------------------------------------------------------- her own account

    suspend fun account(userId: Uuid): DoctorAccount {
        requireActive(userId)
        val record = doctors.byUser(userId) ?: return DoctorAccount(DoctorStatus.NONE)
        val documents = doctors.documentCounts(listOf(record.id))[record.id] ?: 0
        return record.toAccount(documents)
    }

    /**
     * A first application, or another try after a rejection.
     *
     * Pending waits for its answer; approved has nothing to apply for; suspended is an
     * admin's decision and is lifted by one, not by sending the form again.
     */
    suspend fun apply(userId: Uuid, request: DoctorApplicationRequest): DoctorAccount {
        requireActive(userId)
        when (doctors.byUser(userId)?.status) {
            DoctorStatus.PENDING -> throw ConflictException("Arizangiz ko'rib chiqilmoqda")
            DoctorStatus.APPROVED -> throw ConflictException("Siz allaqachon tasdiqlangan shifokorsiz")
            DoctorStatus.SUSPENDED -> throw ForbiddenException(message = "Hisobingiz to'xtatilgan — Sadora bilan bog'laning")
            else -> Unit
        }
        val application = validate(request)
        val documents = decode(request)
        val record = doctors.submit(userId, application, documents)
        audit.record(
            AuditEntry(
                actorType = ActorType.USER,
                actorId = userId,
                action = AuditActions.DOCTOR_APPLIED,
                entityType = "doctor_profile",
                entityId = record.id.toString(),
            ),
        )
        return record.toAccount(documents.size)
    }

    suspend fun updateProfile(userId: Uuid, request: UpdateDoctorProfileRequest): DoctorAccount {
        requireActive(userId)
        val record = doctors.byUser(userId)?.takeIf { it.status == DoctorStatus.APPROVED }
            ?: throw ForbiddenException(message = "Faqat tasdiqlangan shifokorlar uchun")
        val workplace = request.workplace?.trim()
        if (workplace != null) checkWorkplace(workplace)
        val bio = request.bio?.trim()
        if (bio != null && bio.length > Limits.DOCTOR_BIO_MAX) {
            throw ValidationException("bio", "O'zingiz haqingizda eng ko'pi ${Limits.DOCTOR_BIO_MAX} belgi bo'lsin")
        }
        doctors.updateProfile(
            record.id,
            workplace,
            bio?.takeIf { it.isNotEmpty() },
            keepBio = request.bio == null,
            acceptsConsultations = request.acceptsConsultations,
        )
        return account(userId)
    }

    // ---------------------------------------------------------------- admin

    suspend fun list(status: DoctorStatus?, limit: Int, offset: Long): Page<AdminDoctorRow> {
        val (rows, total) = doctors.list(status, limit, offset)
        val documents = doctors.documentCounts(rows.map { it.id })
        return Page(
            rows.map {
                AdminDoctorRow(
                    id = it.id.toString(),
                    userId = it.userId.toString(),
                    fullName = it.fullName,
                    specialty = it.specialty,
                    workplace = it.workplace,
                    experienceYears = it.experienceYears,
                    status = it.status,
                    documentCount = documents[it.id] ?: 0,
                    submittedAt = it.submittedAt,
                    reviewedAt = it.reviewedAt,
                    photoUrl = it.photoUpdatedAt?.let { at -> Photos.adminDoctorUrl(it.id, at) },
                )
            },
            total,
            limit,
            offset.toInt(),
        )
    }

    suspend fun counts(): DoctorCountsView {
        val counts = doctors.countByStatus()
        return DoctorCountsView(
            pending = counts[DoctorStatus.PENDING] ?: 0,
            approved = counts[DoctorStatus.APPROVED] ?: 0,
            rejected = counts[DoctorStatus.REJECTED] ?: 0,
            suspended = counts[DoctorStatus.SUSPENDED] ?: 0,
        )
    }

    suspend fun detail(id: Uuid): AdminDoctorDetail {
        val record = doctors.byId(id) ?: throw NotFoundException("Shifokor topilmadi")
        val user = users.findById(record.userId)
        return AdminDoctorDetail(
            id = record.id.toString(),
            userId = record.userId.toString(),
            phone = user?.phone,
            accountName = user?.name.orEmpty(),
            fullName = record.fullName,
            specialty = record.specialty,
            workplace = record.workplace,
            experienceYears = record.experienceYears,
            licenseNumber = record.licenseNumber,
            bio = record.bio,
            status = record.status,
            reviewNote = record.reviewNote,
            submittedAt = record.submittedAt,
            reviewedAt = record.reviewedAt,
            verifiedAt = record.verifiedAt,
            documents = doctors.documentsOf(record.id).map {
                AdminDoctorDocument(it.id.toString(), it.kind, it.mimeType, it.sizeBytes, it.createdAt)
            },
            acceptsConsultations = record.acceptsConsultations,
            consultations = messaging?.consultationStats(record.id, record.userId),
            photoUrl = record.photoUpdatedAt?.let { Photos.adminDoctorUrl(record.id, it) },
        )
    }

    suspend fun document(id: Uuid, documentId: Uuid): DoctorDocument =
        doctors.document(id, documentId) ?: throw NotFoundException("Hujjat topilmadi")

    /**
     * An admin's decision. Each action is allowed from one state only, so a double
     * click or a stale page cannot approve what was just rejected.
     */
    suspend fun review(id: Uuid, request: DoctorReviewRequest, admin: AdminPrincipal, context: RequestContext) {
        val record = doctors.byId(id) ?: throw NotFoundException("Shifokor topilmadi")
        val note = request.note?.trim()?.takeIf { it.isNotEmpty() }
        if (note != null && note.length > Limits.DOCTOR_REVIEW_NOTE_MAX) {
            throw ValidationException("note", "Izoh eng ko'pi ${Limits.DOCTOR_REVIEW_NOTE_MAX} belgi bo'lsin")
        }
        val (from, to, action) = when (request.action) {
            ACTION_APPROVE -> Triple(setOf(DoctorStatus.PENDING), DoctorStatus.APPROVED, AuditActions.DOCTOR_APPROVED)
            ACTION_REJECT -> Triple(setOf(DoctorStatus.PENDING), DoctorStatus.REJECTED, AuditActions.DOCTOR_REJECTED)
            ACTION_SUSPEND -> Triple(setOf(DoctorStatus.APPROVED), DoctorStatus.SUSPENDED, AuditActions.DOCTOR_SUSPENDED)
            ACTION_REINSTATE -> Triple(setOf(DoctorStatus.SUSPENDED), DoctorStatus.APPROVED, AuditActions.DOCTOR_REINSTATED)
            else -> throw ValidationException("action", "approve, reject, suspend yoki reinstate")
        }
        if (record.status !in from) {
            throw ConflictException("Ariza holati «${record.status.uzWord()}» — bu amal bajarilmaydi")
        }
        if ((to == DoctorStatus.REJECTED || to == DoctorStatus.SUSPENDED) && note == null) {
            throw ValidationException("note", "Sababini yozing — shifokor uni o'qiydi")
        }
        doctors.setStatus(record.id, to, note, admin.adminId)
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = action,
                entityType = "doctor_profile",
                entityId = record.id.toString(),
                reason = note,
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        notifyDecision(record, to, note)
    }

    private suspend fun notifyDecision(record: DoctorRecord, status: DoctorStatus, note: String?) {
        val settings = notifications.settingsOf(record.userId)
        if (!settings.enabled || !settings.isCategoryEnabled(NotificationCategory.SYSTEM)) return
        val language = users.findById(record.userId)?.language ?: uz.sadora.contract.Language.UZ
        val words = DoctorPhrases.decision(status, note, language) ?: return
        notifications.enqueue(
            userId = record.userId,
            category = NotificationCategory.SYSTEM,
            title = words.title,
            body = words.body.take(PUSH_PREVIEW),
            scheduledFor = now(),
            dedupeKey = "doctor_review:${record.id}:${status.name.lowercase()}:${now().toEpochMilliseconds()}",
            status = NotificationStatus.QUEUED,
            suppressedReason = null,
        )
    }

    // ---------------------------------------------------------------- checks

    private suspend fun requireActive(userId: Uuid) {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        if (user.status != AccountStatus.ACTIVE) throw ForbiddenException(ErrorCodes.ACCOUNT_BLOCKED, "Hisob faol emas")
    }

    private fun validate(request: DoctorApplicationRequest): DoctorApplication {
        val name = request.fullName.trim().replace(Regex("\\s+"), " ")
        if (name.length < Limits.DOCTOR_NAME_MIN) {
            throw ValidationException("fullName", "Ism-familiya kamida ${Limits.DOCTOR_NAME_MIN} ta belgi bo'lsin")
        }
        if (name.length > Limits.DOCTOR_NAME_MAX) {
            throw ValidationException("fullName", "Ism-familiya eng ko'pi ${Limits.DOCTOR_NAME_MAX} belgi bo'lsin")
        }
        val workplace = request.workplace.trim()
        checkWorkplace(workplace)
        val license = request.licenseNumber.trim()
        if (license.isEmpty()) throw ValidationException("licenseNumber", "Litsenziya raqamini yozing")
        if (license.length > Limits.DOCTOR_LICENSE_MAX) {
            throw ValidationException("licenseNumber", "Litsenziya raqami eng ko'pi ${Limits.DOCTOR_LICENSE_MAX} belgi bo'lsin")
        }
        if (request.experienceYears !in Limits.DOCTOR_EXPERIENCE_YEARS) {
            throw ValidationException("experienceYears", "0 dan 70 gacha")
        }
        val bio = request.bio?.trim()?.takeIf { it.isNotEmpty() }
        if (bio != null && bio.length > Limits.DOCTOR_BIO_MAX) {
            throw ValidationException("bio", "O'zingiz haqingizda eng ko'pi ${Limits.DOCTOR_BIO_MAX} belgi bo'lsin")
        }
        return DoctorApplication(name, request.specialty, workplace, request.experienceYears, license, bio)
    }

    private fun decode(request: DoctorApplicationRequest): List<DoctorDocument> {
        if (request.documents.isEmpty()) throw ValidationException("documents", "Kamida bitta hujjat rasmini yuklang")
        if (request.documents.size > Limits.DOCTOR_DOCUMENTS_MAX) {
            throw ValidationException("documents", "Eng ko'pi ${Limits.DOCTOR_DOCUMENTS_MAX} ta hujjat")
        }
        return request.documents.map { upload ->
            if (upload.mimeType !in ALLOWED_MIME) throw ValidationException("documents", "Faqat JPEG, PNG yoki WEBP")
            val text = upload.imageBase64.trim()
            if (text.isEmpty()) throw ValidationException("documents", "Rasm bo'sh")
            if (text.length > MAX_IMAGE_CHARS) throw ValidationException("documents", "Rasm juda katta — kichikroq qilib yuboring")
            val bytes = runCatching { Base64.decode(text) }.getOrElse {
                throw ValidationException("documents", "Rasmni o'qib bo'lmadi")
            }
            DoctorDocument(upload.kind, upload.mimeType, bytes)
        }
    }

    private fun checkWorkplace(workplace: String) {
        if (workplace.isEmpty()) throw ValidationException("workplace", "Ish joyini yozing")
        if (workplace.length > Limits.DOCTOR_WORKPLACE_MAX) {
            throw ValidationException("workplace", "Ish joyi eng ko'pi ${Limits.DOCTOR_WORKPLACE_MAX} belgi bo'lsin")
        }
    }

    /** The application's state as the staff panel reads it, in Uzbek. */
    private fun DoctorStatus.uzWord(): String = when (this) {
        DoctorStatus.NONE -> "ariza yo'q"
        DoctorStatus.PENDING -> "ko'rib chiqilmoqda"
        DoctorStatus.APPROVED -> "tasdiqlangan"
        DoctorStatus.REJECTED -> "qaytarilgan"
        DoctorStatus.SUSPENDED -> "to'xtatilgan"
    }

    private fun DoctorRecord.toAccount(documents: Int) = DoctorAccount(
        status = status,
        profileId = id.toString().takeIf { status == DoctorStatus.APPROVED },
        fullName = fullName,
        specialty = specialty,
        workplace = workplace,
        experienceYears = experienceYears,
        licenseNumber = licenseNumber,
        bio = bio,
        documentCount = documents,
        reviewNote = reviewNote,
        submittedAt = submittedAt,
        reviewedAt = reviewedAt,
        acceptsConsultations = acceptsConsultations,
        photoUrl = photoUpdatedAt?.let { Photos.doctorUrl(id, it) },
    )

    companion object {
        const val ACTION_APPROVE = "approve"
        const val ACTION_REJECT = "reject"
        const val ACTION_SUSPEND = "suspend"
        const val ACTION_REINSTATE = "reinstate"
        /** The same ceiling as a food scan: a phone photo, resized, base64. */
        const val MAX_IMAGE_CHARS = 5_600_000
        val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")
        private const val PUSH_PREVIEW = 160
    }
}
