package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * Health tables, kept in their own file rather than mixed into [Tables].
 *
 * The separation is the point: `AdminService` imports the account tables and nothing
 * here, so an admin endpoint cannot reach a cycle log even by accident. Section 17 of
 * the TZ requires that, and a file boundary makes it visible in review.
 */

object CyclePeriods : Table("cycle_periods") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val startedOn = date("started_on")
    val endedOn = date("ended_on").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object DailyLogs : Table("daily_logs") {
    val userId = uuid("user_id").references(Users.id)
    val logDate = date("log_date")
    val flow = text("flow").nullable()
    val mood = text("mood").nullable()
    val energy = integer("energy").nullable()
    val note = text("note").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId, logDate)
}

object SymptomDefinitions : Table("symptom_definitions") {
    val key = text("key")
    val label = text("label")
    val category = text("category")
    val sortOrder = integer("sort_order")
    val active = bool("active")

    override val primaryKey = PrimaryKey(key)
}

object SymptomLifeStages : Table("symptom_life_stages") {
    val symptomKey = text("symptom_key").references(SymptomDefinitions.key)
    val lifeStage = text("life_stage")

    override val primaryKey = PrimaryKey(symptomKey, lifeStage)
}

object DailySymptoms : Table("daily_symptoms") {
    val userId = uuid("user_id")
    val logDate = date("log_date")
    val symptomKey = text("symptom_key").references(SymptomDefinitions.key)
    val severity = text("severity")

    override val primaryKey = PrimaryKey(userId, logDate, symptomKey)
}
