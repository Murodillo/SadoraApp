package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.time
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
    val stress = integer("stress").nullable()
    val waterMl = integer("water_ml")
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

object JournalEntries : Table("journal_entries") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val entryDate = date("entry_date")
    val body = text("body")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object MindPractices : Table("mind_practices") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val kind = text("kind")
    val durationSeconds = integer("duration_seconds")
    val completedAt = timestampWithTimeZone("completed_at")

    override val primaryKey = PrimaryKey(id)
}

object Meals : Table("meals") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val logDate = date("log_date")
    val slot = text("slot")
    val eatenAt = time("eaten_at").nullable()
    val description = text("description")
    val kcal = integer("kcal")
    val proteinG = integer("protein_g")
    val fatG = integer("fat_g")
    val carbsG = integer("carbs_g")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object NutritionGoalsTable : Table("nutrition_goals") {
    val userId = uuid("user_id").references(Users.id)
    val calorieGoal = integer("calorie_goal")
    val proteinGoalG = integer("protein_goal_g")
    val fatGoalG = integer("fat_goal_g")
    val carbsGoalG = integer("carbs_goal_g")
    val waterGoalMl = integer("water_goal_ml")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

object FoodItems : Table("food_items") {
    val key = text("key")
    val name = text("name")
    val kcal = integer("kcal")
    val proteinG = integer("protein_g")
    val fatG = integer("fat_g")
    val carbsG = integer("carbs_g")
    val perPiece = bool("per_piece")
    val active = bool("active")

    override val primaryKey = PrimaryKey(key)
}
