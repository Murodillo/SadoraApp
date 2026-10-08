package uz.sadora.server.db

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb
import uz.sadora.contract.PrescriptionItem

private val itemsJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

/** A doctor's prescription, one per `prescription` message (V43). Fixed once written. */
object Prescriptions : Table("prescriptions") {
    val id = uuid("id")
    val messageId = uuid("message_id").references(CommunityMessages.id)
    val conversationId = uuid("conversation_id").references(CommunityConversations.id)
    val doctorId = uuid("doctor_id").references(DoctorProfiles.id)
    val patientId = uuid("patient_id").references(Users.id)
    val items = jsonb<List<PrescriptionItem>>("items", itemsJson)
    val note = text("note").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val cancelledAt = timestampWithTimeZone("cancelled_at").nullable()
    val cancelReason = text("cancel_reason").nullable()
    val addedAt = timestampWithTimeZone("added_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
