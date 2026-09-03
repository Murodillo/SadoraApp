package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.FoodItem
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.Meal
import uz.sadora.contract.MealSlot
import uz.sadora.contract.NutritionGoals
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.DailyLogs
import uz.sadora.server.db.FoodItems
import uz.sadora.server.db.Meals
import uz.sadora.server.db.NutritionGoalsTable
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

class NutritionRepository {

    // ---------------------------------------------------------------- meals

    suspend fun mealsOn(userId: Uuid, date: LocalDate): List<Meal> = dbQuery {
        Meals.selectAll()
            .where { (Meals.userId eq userId) and (Meals.logDate eq date) }
            .orderBy(Meals.eatenAt to SortOrder.ASC, Meals.createdAt to SortOrder.ASC)
            .map { row ->
                Meal(
                    id = row[Meals.id].toString(),
                    date = row[Meals.logDate],
                    slot = enumFromDb(row[Meals.slot], MealSlot.SNACK),
                    eatenAt = row[Meals.eatenAt],
                    description = row[Meals.description],
                    kcal = row[Meals.kcal],
                    proteinG = row[Meals.proteinG],
                    fatG = row[Meals.fatG],
                    carbsG = row[Meals.carbsG],
                    createdAt = row[Meals.createdAt].toKotlinInstant(),
                )
            }
    }

    suspend fun addMeal(userId: Uuid, request: LogMealRequest): Uuid = dbQuery {
        val id = Uuid.random()
        Meals.insert {
            it[Meals.id] = id
            it[Meals.userId] = userId
            it[logDate] = request.date
            it[slot] = request.slot.dbValue()
            it[eatenAt] = request.eatenAt
            it[description] = request.description.trim()
            it[kcal] = request.kcal
            it[proteinG] = request.proteinG
            it[fatG] = request.fatG
            it[carbsG] = request.carbsG
            it[createdAt] = now().toOffsetDateTime()
        }
        id
    }

    suspend fun deleteMeal(userId: Uuid, id: Uuid): Boolean = dbQuery {
        Meals.deleteWhere { (Meals.id eq id) and (Meals.userId eq userId) } > 0
    }

    // ---------------------------------------------------------------- water

    suspend fun waterOn(userId: Uuid, date: LocalDate): Int = dbQuery {
        DailyLogs.selectAll()
            .where { (DailyLogs.userId eq userId) and (DailyLogs.logDate eq date) }
            .singleOrNull()
            ?.get(DailyLogs.waterMl)
            ?: 0
    }

    /**
     * Adds (or, with a negative amount, takes back) water and returns the new total.
     *
     * Clamped at zero so an undo tapped twice cannot leave a negative figure on the
     * screen. The upsert touches only the water column, so it cannot wipe a mood or a
     * symptom recorded on the same day.
     */
    suspend fun addWater(userId: Uuid, date: LocalDate, deltaMl: Int): Int = dbQuery {
        val current = DailyLogs.selectAll()
            .where { (DailyLogs.userId eq userId) and (DailyLogs.logDate eq date) }
            .singleOrNull()
            ?.get(DailyLogs.waterMl)
            ?: 0
        val updated = (current + deltaMl).coerceIn(0, MAX_WATER_ML)
        val timestamp = now().toOffsetDateTime()

        DailyLogs.upsert(
            DailyLogs.userId,
            DailyLogs.logDate,
            onUpdate = {
                it[DailyLogs.waterMl] = updated
                it[DailyLogs.updatedAt] = timestamp
            },
        ) {
            it[DailyLogs.userId] = userId
            it[logDate] = date
            it[waterMl] = updated
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
        }
        updated
    }

    // ---------------------------------------------------------------- goals

    suspend fun goalsOf(userId: Uuid): NutritionGoals = dbQuery {
        NutritionGoalsTable.selectAll()
            .where { NutritionGoalsTable.userId eq userId }
            .singleOrNull()
            ?.let {
                NutritionGoals(
                    calorieGoal = it[NutritionGoalsTable.calorieGoal],
                    proteinGoalG = it[NutritionGoalsTable.proteinGoalG],
                    fatGoalG = it[NutritionGoalsTable.fatGoalG],
                    carbsGoalG = it[NutritionGoalsTable.carbsGoalG],
                    waterGoalMl = it[NutritionGoalsTable.waterGoalMl],
                )
            }
            ?: NutritionGoals()
    }

    suspend fun saveGoals(userId: Uuid, goals: NutritionGoals): Unit = dbQuery {
        NutritionGoalsTable.upsert(NutritionGoalsTable.userId) {
            it[NutritionGoalsTable.userId] = userId
            it[calorieGoal] = goals.calorieGoal
            it[proteinGoalG] = goals.proteinGoalG
            it[fatGoalG] = goals.fatGoalG
            it[carbsGoalG] = goals.carbsGoalG
            it[waterGoalMl] = goals.waterGoalMl
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    // ---------------------------------------------------------------- catalogue

    /** Matches anywhere in the name, so "somsa" finds both kinds. */
    suspend fun searchFoods(query: String?, limit: Int): List<FoodItem> = dbQuery {
        var statement = FoodItems.selectAll().where { FoodItems.active eq true }
        query?.trim()?.takeIf { it.isNotEmpty() }?.let { term ->
            statement = statement.andWhere { FoodItems.name.lowerCase() like "%${term.lowercase()}%" }
        }
        statement
            .orderBy(FoodItems.name to SortOrder.ASC)
            .limit(limit)
            .map { row ->
                FoodItem(
                    key = row[FoodItems.key],
                    name = row[FoodItems.name],
                    kcal = row[FoodItems.kcal],
                    proteinG = row[FoodItems.proteinG],
                    fatG = row[FoodItems.fatG],
                    carbsG = row[FoodItems.carbsG],
                    perPiece = row[FoodItems.perPiece],
                )
            }
    }

    private companion object {
        /** Ten litres is far past any real intake; anything beyond it is a typo. */
        const val MAX_WATER_ML = 10_000
    }
}
