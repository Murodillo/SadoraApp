package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a doctor practises. A closed list so the feed can say it in three languages and
 * the admin panel can filter by it; [OTHER] covers the rest.
 */
@Serializable
enum class DoctorSpecialty {
    @SerialName("gynecologist") GYNECOLOGIST,
    @SerialName("obstetrician") OBSTETRICIAN,
    @SerialName("reproductologist") REPRODUCTOLOGIST,
    @SerialName("endocrinologist") ENDOCRINOLOGIST,
    @SerialName("mammologist") MAMMOLOGIST,
    @SerialName("psychologist") PSYCHOLOGIST,
    @SerialName("nutritionist") NUTRITIONIST,
    @SerialName("pediatrician") PEDIATRICIAN,
    @SerialName("general") GENERAL,
    @SerialName("other") OTHER,
}

/**
 * Where an account stands as a doctor.
 *
 * [NONE] is every account that never applied. [PENDING] waits for an admin; [REJECTED]
 * came back with a note and may apply again; [SUSPENDED] was approved once and has been
 * taken off the feed by an admin — her doctor posts disappear with it.
 */
@Serializable
enum class DoctorStatus {
    @SerialName("none") NONE,
    @SerialName("pending") PENDING,
    @SerialName("approved") APPROVED,
    @SerialName("rejected") REJECTED,
    @SerialName("suspended") SUSPENDED,
}

@Serializable
enum class DoctorDocumentKind {
    @SerialName("diploma") DIPLOMA,
    @SerialName("license") LICENSE,
    @SerialName("other") OTHER,
}

/**
 * The byline of a post or comment a verified doctor wrote.
 *
 * A doctor writes under her real name — that is what the check mark vouches for — so
 * unlike an alias this carries an id: the doctor's own public id, not the account's.
 */
@Serializable
data class DoctorAuthor(
    val id: String,
    val fullName: String,
    val specialty: DoctorSpecialty,
)

/** One page of proof, photographed by the phone and sent base64 like a food scan. */
@Serializable
data class DoctorDocumentUpload(
    val kind: DoctorDocumentKind,
    val imageBase64: String,
    val mimeType: String = "image/jpeg",
)

/**
 * An application, or a resubmission after a rejection.
 *
 * The documents replace whatever was sent before; an admin reviews the whole set again.
 */
@Serializable
data class DoctorApplicationRequest(
    val fullName: String,
    val specialty: DoctorSpecialty,
    val workplace: String,
    val experienceYears: Int,
    val licenseNumber: String,
    val bio: String? = null,
    val documents: List<DoctorDocumentUpload>,
)

/** What an approved doctor may change without a new review. Null leaves a field as it is. */
@Serializable
data class UpdateDoctorProfileRequest(
    val workplace: String? = null,
    val bio: String? = null,
)

/**
 * The caller's own doctor account: the status, and what she sent.
 *
 * [reviewNote] is the admin's reason on a rejection or a suspension. [profileId] is set
 * once approved — the id her posts carry and her public page is addressed by.
 */
@Serializable
data class DoctorAccount(
    val status: DoctorStatus,
    val profileId: String? = null,
    val fullName: String? = null,
    val specialty: DoctorSpecialty? = null,
    val workplace: String? = null,
    val experienceYears: Int? = null,
    val licenseNumber: String? = null,
    val bio: String? = null,
    val documentCount: Int = 0,
    val reviewNote: String? = null,
    val submittedAt: Instant? = null,
    val reviewedAt: Instant? = null,
)

/** A verified doctor's public page: who she is, and what she has written in the room. */
@Serializable
data class DoctorProfile(
    val id: String,
    val fullName: String,
    val specialty: DoctorSpecialty,
    val workplace: String,
    val experienceYears: Int,
    val bio: String? = null,
    val verifiedSince: Instant,
    val postCount: Int = 0,
    val answerCount: Int = 0,
    val isMe: Boolean = false,
    /** Her recent posts, newest first. */
    val posts: List<CommunityPost> = emptyList(),
)

/** A doctor as the directory lists her. */
@Serializable
data class DoctorListItem(
    val id: String,
    val fullName: String,
    val specialty: DoctorSpecialty,
    val workplace: String,
    val experienceYears: Int,
    val answerCount: Int = 0,
)
