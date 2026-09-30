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
    val acceptsConsultations = bool("accepts_consultations")
    val consultationPriceMinor = long("consultation_price_minor")
    val busy = bool("busy")
    val timezone = text("timezone")

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

/** Each 24-hour window of a consultation: its price, payment, first reply, summary, rating. */
object ConsultationSessions : Table("consultation_sessions") {
    val id = uuid("id")
    val conversationId = uuid("conversation_id")
    val doctorId = uuid("doctor_id").references(DoctorProfiles.id)
    val patientId = uuid("patient_id").references(Users.id)
    val openedAt = timestampWithTimeZone("opened_at").nullable()
    val expiresAt = timestampWithTimeZone("expires_at").nullable()
    val closedAt = timestampWithTimeZone("closed_at").nullable()
    val closedReason = text("closed_reason").nullable()
    val priceMinor = long("price_minor")
    val commissionPercent = integer("commission_percent")
    val paymentState = text("payment_state")
    val firstReplyAt = timestampWithTimeZone("first_reply_at").nullable()
    val summary = text("summary").nullable()
    val summaryAt = timestampWithTimeZone("summary_at").nullable()
    val rating = short("rating").nullable()
    val review = text("review").nullable()
    val ratedAt = timestampWithTimeZone("rated_at").nullable()
    val refundedAt = timestampWithTimeZone("refunded_at").nullable()
    val refundedBy = uuid("refunded_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object DoctorHoursTable : Table("doctor_hours") {
    val doctorId = uuid("doctor_id").references(DoctorProfiles.id)
    val weekday = integer("weekday")
    val startMinute = integer("start_minute")
    val endMinute = integer("end_minute")

    override val primaryKey = PrimaryKey(doctorId, weekday)
}

object DoctorQuickReplies : Table("doctor_quick_replies") {
    val id = uuid("id")
    val doctorId = uuid("doctor_id").references(DoctorProfiles.id)
    val title = text("title")
    val body = text("body")
    val position = integer("position")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object DoctorPatientNotes : Table("doctor_patient_notes") {
    val doctorId = uuid("doctor_id").references(DoctorProfiles.id)
    val patientId = uuid("patient_id").references(Users.id)
    val body = text("body")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(doctorId, patientId)
}

object DoctorPayouts : Table("doctor_payouts") {
    val id = uuid("id")
    val doctorId = uuid("doctor_id").references(DoctorProfiles.id)
    val amountMinor = long("amount_minor")
    val note = text("note").nullable()
    val paidAt = timestampWithTimeZone("paid_at")
    val createdBy = uuid("created_by")

    override val primaryKey = PrimaryKey(id)
}

/** Numbers an operator changes without a deploy. */
object AppSettings : Table("app_settings") {
    val key = text("key")
    val value = text("value")
    val updatedAt = timestampWithTimeZone("updated_at")
    val updatedBy = uuid("updated_by").nullable()

    override val primaryKey = PrimaryKey(key)
}
