package uz.sadora.server.photo

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.DoctorStatus
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationStatus
import uz.sadora.contract.PhotoUpload
import uz.sadora.contract.PhotoView
import uz.sadora.server.audit.ActorType
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditEntry
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.community.MessagingRepository
import uz.sadora.server.core.ForbiddenException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.Photos
import uz.sadora.server.core.ProfilePhoto
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.DoctorPhotos
import uz.sadora.server.db.DoctorProfiles
import uz.sadora.server.db.UserPhotos
import uz.sadora.server.db.Users
import uz.sadora.server.db.dbQuery
import uz.sadora.server.doctor.DoctorRepository
import uz.sadora.server.notify.NotificationRepository
import uz.sadora.server.notify.TARGET_DOCTOR
import uz.sadora.server.plugins.AdminPrincipal

/**
 * Profile photos: a woman's, and a doctor's.
 *
 * Hers is private. She sees it in her own app, and a doctor she consults sees it through
 * that consultation — the same reach as her name and age. It is never served to the
 * room, where she is an alias; nothing that lists aliases carries it.
 *
 * A doctor's is public like her name, and served to any signed-in reader once she is
 * approved; before that, to her alone and to the staff who review her.
 */
class PhotoService(
    private val doctors: DoctorRepository,
    private val messages: MessagingRepository,
    private val notifications: NotificationRepository,
    private val audit: AuditService,
    /** For the language of the doctor a removal notice reaches; Uzbek without it. */
    private val users: uz.sadora.server.user.UserRepository? = null,
) {

    // ---------------------------------------------------------------- hers

    suspend fun setMine(userId: Uuid, upload: PhotoUpload): PhotoView {
        val photo = Photos.process(upload)
        val at = now()
        val url = Photos.ownUrl(at)
        dbQuery {
            UserPhotos.upsert { it.fill(userId, photo, at) }
            Users.update({ Users.id eq userId }) { it[avatarUrl] = url }
        }
        return PhotoView(url)
    }

    suspend fun removeMine(userId: Uuid) = dbQuery {
        UserPhotos.deleteWhere { UserPhotos.userId eq userId }
        Users.update({ Users.id eq userId }) { it[avatarUrl] = null }
    }

    suspend fun mine(userId: Uuid): ByteArray = userPhoto(userId) ?: throw NotFoundException("Rasm yo'q")

    /**
     * The other side's photo in a consultation: the patient's for her doctor, the
     * doctor's for her patient. An alias thread has none — there the two are aliases.
     */
    suspend fun inConversation(viewer: Uuid, conversationId: Uuid): ByteArray {
        val thread = messages.conversationById(conversationId)
        if (thread == null || !thread.has(viewer) || !thread.isConsultation) throw NotFoundException("Rasm yo'q")
        val doctor = thread.doctorId?.let { doctors.byId(it) } ?: throw NotFoundException("Rasm yo'q")
        val bytes = if (doctor.userId == viewer) userPhoto(thread.other(viewer)) else doctorPhoto(doctor.id)
        return bytes ?: throw NotFoundException("Rasm yo'q")
    }

    // ---------------------------------------------------------------- a doctor's

    /** Any doctor profile may set one, pending included: the application asks for it. */
    suspend fun setDoctor(userId: Uuid, upload: PhotoUpload): PhotoView {
        val doctor = doctors.byUser(userId) ?: throw ForbiddenException(message = "Avval shifokor arizasini yuboring")
        val photo = Photos.process(upload)
        val at = now()
        dbQuery {
            DoctorPhotos.upsert {
                it[doctorId] = doctor.id
                it[content] = photo.bytes
                it[sizePx] = photo.sizePx
                it[updatedAt] = at.toOffsetDateTime()
            }
            DoctorProfiles.update({ DoctorProfiles.id eq doctor.id }) { it[photoUpdatedAt] = at.toOffsetDateTime() }
        }
        return PhotoView(Photos.doctorUrl(doctor.id, at))
    }

    suspend fun removeDoctor(userId: Uuid) {
        val doctor = doctors.byUser(userId) ?: throw ForbiddenException(message = "Shifokor profili yo'q")
        clearDoctor(doctor.id)
    }

    /** Hers to any signed-in reader once she is approved; before that, to her alone. */
    suspend fun doctor(viewer: Uuid, doctorId: Uuid): ByteArray {
        val doctor = doctors.byId(doctorId) ?: throw NotFoundException("Rasm yo'q")
        if (doctor.status != DoctorStatus.APPROVED && doctor.userId != viewer) throw NotFoundException("Rasm yo'q")
        return doctorPhoto(doctor.id) ?: throw NotFoundException("Rasm yo'q")
    }

    // ---------------------------------------------------------------- staff

    suspend fun adminDoctor(doctorId: Uuid): ByteArray = doctorPhoto(doctorId) ?: throw NotFoundException("Rasm yo'q")

    /** Takes down a photo that is not a portrait of her, and tells her why in the app. */
    suspend fun adminRemoveDoctor(doctorId: Uuid, reason: String?, admin: AdminPrincipal, context: RequestContext) {
        val doctor = doctors.byId(doctorId) ?: throw NotFoundException("Shifokor topilmadi")
        if (doctor.photoUpdatedAt == null) throw NotFoundException("Rasm yo'q")
        clearDoctor(doctorId)
        audit.record(
            AuditEntry(
                actorType = ActorType.ADMIN,
                actorId = admin.adminId,
                actorLabel = admin.role.name.lowercase(),
                action = AuditActions.DOCTOR_PHOTO_REMOVED,
                entityType = "doctor_profile",
                entityId = doctorId.toString(),
                reason = reason,
                ip = context.ip,
                userAgent = context.userAgent,
            ),
        )
        val settings = notifications.settingsOf(doctor.userId)
        if (settings.enabled && settings.isCategoryEnabled(NotificationCategory.SYSTEM)) {
            val language = users?.findById(doctor.userId)?.language ?: uz.sadora.contract.Language.UZ
            val words = uz.sadora.server.doctor.DoctorPhrases.photoRemoved(reason, language)
            notifications.enqueue(
                userId = doctor.userId,
                category = NotificationCategory.SYSTEM,
                title = words.title,
                body = words.body,
                scheduledFor = now(),
                dedupeKey = "doctor_photo_removed:$doctorId:${now().toEpochMilliseconds()}",
                status = NotificationStatus.QUEUED,
                suppressedReason = null,
                targetApp = TARGET_DOCTOR,
            )
        }
    }

    // ---------------------------------------------------------------- storage

    private suspend fun clearDoctor(doctorId: Uuid) = dbQuery {
        DoctorPhotos.deleteWhere { DoctorPhotos.doctorId eq doctorId }
        DoctorProfiles.update({ DoctorProfiles.id eq doctorId }) { it[photoUpdatedAt] = null }
    }

    private suspend fun userPhoto(userId: Uuid): ByteArray? = dbQuery {
        UserPhotos.selectAll().where { UserPhotos.userId eq userId }.singleOrNull()?.get(UserPhotos.content)
    }

    private suspend fun doctorPhoto(doctorId: Uuid): ByteArray? = dbQuery {
        DoctorPhotos.selectAll().where { DoctorPhotos.doctorId eq doctorId }.singleOrNull()?.get(DoctorPhotos.content)
    }

    private fun org.jetbrains.exposed.v1.core.statements.UpdateBuilder<*>.fill(userId: Uuid, photo: ProfilePhoto, at: Instant) {
        this[UserPhotos.userId] = userId
        this[UserPhotos.content] = photo.bytes
        this[UserPhotos.sizePx] = photo.sizePx
        this[UserPhotos.updatedAt] = at.toOffsetDateTime()
    }
}
