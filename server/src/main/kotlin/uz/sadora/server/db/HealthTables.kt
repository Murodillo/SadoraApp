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

object Medications : Table("medications") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val name = text("name")
    val emoji = text("emoji").nullable()
    val dosage = text("dosage").nullable()
    val unit = text("unit").nullable()
    val foodRelation = text("food_relation")
    val note = text("note").nullable()
    val scheduleKind = text("schedule_kind")
    /** Comma-separated `HH:MM`, one entry per dose in the day. */
    val times = text("times")
    /** Comma-separated ISO weekday numbers, for the weekday schedule. */
    val weekdays = text("weekdays")
    val intervalDays = integer("interval_days").nullable()
    val remindersEnabled = bool("reminders_enabled")
    val startedOn = date("started_on")
    val endedOn = date("ended_on").nullable()
    val stockUnits = integer("stock_units").nullable()
    val active = bool("active")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object MedicationIntakes : Table("medication_intakes") {
    val medicationId = uuid("medication_id").references(Medications.id)
    val userId = uuid("user_id").references(Users.id)
    val dueOn = date("due_on")
    val dueAt = time("due_at")
    val status = text("status")
    val recordedAt = timestampWithTimeZone("recorded_at")

    override val primaryKey = PrimaryKey(medicationId, dueOn, dueAt)
}

object HealthSamples : Table("health_samples") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val provider = text("provider")
    val externalId = text("external_id")
    val metric = text("metric")
    val value = double("value")
    val unit = text("unit")
    val startedAt = timestampWithTimeZone("started_at")
    val endedAt = timestampWithTimeZone("ended_at").nullable()
    /** The calendar day in the user's timezone, fixed at ingest. */
    val localDate = date("local_date")
    val sourceDevice = text("source_device").nullable()
    val recordedAt = timestampWithTimeZone("recorded_at")

    override val primaryKey = PrimaryKey(id)
}

object DailyHealthMetrics : Table("daily_health_metrics") {
    val userId = uuid("user_id").references(Users.id)
    val localDate = date("local_date")
    val metric = text("metric")
    val value = double("value")
    val unit = text("unit")
    val sampleCount = integer("sample_count")
    val providers = text("providers")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(userId, localDate, metric)
}

object ProviderMetricMappings : Table("provider_metric_mappings") {
    val provider = text("provider")
    val providerMetric = text("provider_metric")
    val metric = text("metric")
    val providerUnit = text("provider_unit").nullable()
    val scale = double("scale")
    val active = bool("active")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(provider, providerMetric)
}
