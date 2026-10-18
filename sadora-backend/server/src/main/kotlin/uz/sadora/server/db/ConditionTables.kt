package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** The conditions she marked (V58). Health data: kept out of the admin's imports. */
object UserConditions : Table("user_conditions") {
    val userId = uuid("user_id").references(Users.id)
    val condition = text("condition")
    val status = text("status")
    val diagnosedOn = date("diagnosed_on").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId, condition)
}

/** One day of the pain diary. Areas and kinds are comma-separated wire words. */
object PainLogs : Table("pain_logs") {
    val userId = uuid("user_id").references(Users.id)
    val day = date("day")
    val level = integer("level")
    val areas = text("areas")
    val kinds = text("kinds")
    val painkiller = bool("painkiller")
    val painkillerName = text("painkiller_name").nullable()
    val missedActivities = bool("missed_activities")
    val note = text("note").nullable()
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId, day)
}
