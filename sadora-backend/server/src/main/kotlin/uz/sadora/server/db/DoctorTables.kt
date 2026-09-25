package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** A verified doctor, or an application to become one. `id` is the public id. */
object DoctorProfiles : Table("doctor_profiles") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val fullName = text("full_name")
    val specialty = text("specialty")
    val workplace = text("workplace")
    val experienceYears = integer("experience_years")
    val licenseNumber = text("license_number")
    val bio = text("bio").nullable()
    val status = text("status")
    val reviewNote = text("review_note").nullable()
    val reviewedBy = uuid("reviewed_by").nullable()
    val reviewedAt = timestampWithTimeZone("reviewed_at").nullable()
    val verifiedAt = timestampWithTimeZone("verified_at").nullable()
    val submittedAt = timestampWithTimeZone("submitted_at")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/** The diploma and licence photos an admin reviews. Never served to the app. */
object DoctorDocuments : Table("doctor_documents") {
    val id = uuid("id")
    val doctorId = uuid("doctor_id").references(DoctorProfiles.id)
    val kind = text("kind")
    val mimeType = text("mime_type")
    val content = binary("content")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
