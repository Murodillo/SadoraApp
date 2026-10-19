package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** The people SOS alerts (V63), in the order she listed them. */
object SosContacts : Table("sos_contacts") {
    val userId = uuid("user_id").references(Users.id)
    val position = integer("position")
    val name = text("name")
    val phone = text("phone")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId, position)
}

object SosAlerts : Table("sos_alerts") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val smsSent = integer("sms_sent")
    val smsFailed = integer("sms_failed")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
