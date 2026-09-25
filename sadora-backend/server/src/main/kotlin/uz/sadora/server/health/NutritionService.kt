package uz.sadora.server.health

import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import uz.sadora.contract.AddWaterRequest
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.FoodItem
import uz.sadora.contract.FoodScanRequest
import uz.sadora.contract.FoodScanResult
import uz.sadora.contract.LogMealRequest
import uz.sadora.contract.Meal
import uz.sadora.contract.NutritionDay
import uz.sadora.contract.NutritionGoals
import uz.sadora.contract.NutritionTotals
import uz.sadora.contract.UpdateNutritionGoalsRequest
import uz.sadora.contract.WaterState
import uz.sadora.contract.Limits
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.contract.CoinReasons
import uz.sadora.server.core.RewardHooks
import uz.sadora.server.core.now

/**
 * Food, water and the day's targets.
 *
 * The totals are summed from the logged meals rather than kept as a counter, so deleting
 * a meal cannot leave the day's figure wrong — the app's undo is a delete, and a stored
 * running total would drift the first time one was missed.
 */
class NutritionService(
    private val nutrition: NutritionRepository,
    private val access: HealthAccess,
    /** Null when no model is configured; the scan endpoint then says so rather than guessing. */
    private val vision: uz.sadora.server.ai.FoodVision? = null,
    private val visionConfig: uz.sadora.server.config.AiConfig? = null,
    private val usage: uz.sadora.server.ai.AiUsageRecorder? = null,
    /**
     * The reward scheme, as one call it can ignore.
     *
     * Defaulted to the no-op so nothing in this service's tests has to know the scheme
     * exists, and so a coin that cannot be written never costs a log entry.
     */
    private val rewards: RewardHooks = RewardHooks.None,
) {

    /** True when a photo could be read at all — the app asks before offering the camera. */
    val scannerAvailable: Boolean get() = vision != null && visionConfig?.apiKey != null

    /**
     * Reads a photo of a meal.
     *
     * Gated like every other paid AI call: consent, then entitlement, then the daily
     * limit the operator sets. It writes nothing — the result comes back to the screen,
     * she corrects the portion, and only then is a meal logged. That separation is what
     * keeps an estimate out of the diary until she has agreed to it.
     */
    suspend fun scan(userId: Uuid, request: FoodScanRequest): FoodScanResult {
        val user = access.requireWritable(userId, FeatureKeys.FOOD_SCAN)

        // The request is checked before the model is looked for: a malformed request is
        // malformed whether or not there is something to send it to, and answering 503
        // to an empty image tells the app to retry something that cannot work.
        val image = request.imageBase64.trim()
        if (image.isEmpty()) throw ValidationException("imageBase64", "Rasm bo'sh")
        // Base64 is about a third larger than the bytes it carries, so the cap is on what
        // arrives rather than on what the phone thinks it sent.
        if (image.length > MAX_IMAGE_CHARS) {
            throw ValidationException("imageBase64", "Rasm juda katta — kichikroq qilib yuboring")
        }
        if (request.mimeType !in ALLOWED_MIME) {
            throw ValidationException("mimeType", "Faqat JPEG yoki PNG")
        }

        val model = vision ?: throw uz.sadora.server.core.UpstreamUnavailableException(
            "Skaner hozircha ishlamayapti. Taomni qo'lda qo'shishingiz mumkin.",
        )

        // The use is spent before the model is called, as the chat does: a scan the model
        // answered is a scan that cost money, and the daily and monthly limits on this
        // feature were never counted down — a Premium account could scan without end.
        access.consume(userId, FeatureKeys.FOOD_SCAN, user.timezone)

        val started = kotlin.time.TimeSource.Monotonic.markNow()
        return try {
            val answer = model.recognise(image, request.mimeType, user.language)
            usage?.record(
                uz.sadora.server.ai.AiUsageEntry(
                    userId = userId,
                    source = uz.sadora.server.ai.AiSource.MODEL,
                    model = answer.model,
                    feature = FeatureKeys.FOOD_SCAN,
                    promptTokens = answer.promptTokens,
                    completionTokens = answer.completionTokens,
                    costMicros = visionConfig?.costMicros(answer.promptTokens, answer.completionTokens) ?: 0,
                    latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                    outcome = "ok",
                ),
            )
            answer.result
        } catch (failure: uz.sadora.server.ai.ModelUnavailableException) {
            usage?.record(
                uz.sadora.server.ai.AiUsageEntry(
                    userId = userId,
                    source = uz.sadora.server.ai.AiSource.FALLBACK,
                    model = model.name,
                    feature = FeatureKeys.FOOD_SCAN,
                    latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                    outcome = "error",
                    errorCode = failure.code,
                ),
            )
            // There is no rule engine that can look at a photograph, so this one really
            // does fail — and says so, rather than returning a plausible dish.
            throw uz.sadora.server.core.UpstreamUnavailableException(
                "Rasmni o'qib bo'lmadi. Qaytadan urinib ko'ring yoki qo'lda kiriting.",
            )
        }
    }

    suspend fun day(userId: Uuid, date: LocalDate?): NutritionDay {
        val user = access.requireUser(userId)
        val target = date ?: now().dayIn(user.timezone)
        val meals = nutrition.mealsOn(userId, target)
        return NutritionDay(
            date = target,
            meals = meals,
            totals = meals.total(),
            goals = nutrition.goalsOf(userId),
            waterMl = nutrition.waterOn(userId, target),
        )
    }

    suspend fun addMeal(userId: Uuid, request: LogMealRequest): Meal {
        val user = access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        if (request.description.isBlank()) {
            throw ValidationException("description", "Bo'sh bo'lishi mumkin emas")
        }
        if (request.description.length > MAX_DESCRIPTION) {
            throw ValidationException("description", "Eng ko'pi $MAX_DESCRIPTION belgi")
        }
        if (request.date > now().dayIn(user.timezone)) {
            throw ValidationException("date", "Kelajakdagi kun uchun ovqat qo'shib bo'lmaydi")
        }
        validateAmount("kcal", request.kcal, MAX_KCAL)
        validateAmount("proteinG", request.proteinG, MAX_MACRO)
        validateAmount("fatG", request.fatG, MAX_MACRO)
        validateAmount("carbsG", request.carbsG, MAX_MACRO)

        val id = nutrition.addMeal(userId, request)
        // Referenced by the meal's own id, so the daily cap counts meals rather than
        // taps: editing the same meal twice is not two meals.
        rewards.logged(userId, CoinReasons.MEAL_LOGGED, id.toString())
        return nutrition.mealsOn(userId, request.date).firstOrNull { it.id == id.toString() }
            ?: throw NotFoundException("Ovqat topilmadi")
    }

    suspend fun deleteMeal(userId: Uuid, id: Uuid) {
        access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        if (!nutrition.deleteMeal(userId, id)) throw NotFoundException("Ovqat topilmadi")
    }

    /** A negative [AddWaterRequest.ml] is the undo path, so it is allowed here. */
    suspend fun addWater(userId: Uuid, request: AddWaterRequest): WaterState {
        val user = access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        if (request.ml == 0) throw ValidationException("ml", "Nol bo'lishi mumkin emas")
        if (kotlin.math.abs(request.ml) > MAX_WATER_STEP) {
            throw ValidationException("ml", "Bir marta eng ko'pi $MAX_WATER_STEP ml")
        }
        val today = now().dayIn(user.timezone)
        val total = nutrition.addWater(userId, today, request.ml)
        val goal = nutrition.goalsOf(userId).waterGoalMl
        // The coin is for reaching her own goal, once a day — not for each glass, or
        // the reward would be for tapping rather than for drinking.
        if (goal > 0 && total >= goal) rewards.logged(userId, CoinReasons.WATER_GOAL)
        return WaterState(today, total, goal)
    }

    suspend fun goals(userId: Uuid): NutritionGoals {
        access.requireUser(userId)
        return nutrition.goalsOf(userId)
    }

    suspend fun updateGoals(userId: Uuid, request: UpdateNutritionGoalsRequest): NutritionGoals {
        access.requireWritable(userId, FeatureKeys.NUTRITION_LOG)
        val current = nutrition.goalsOf(userId)
        val updated = NutritionGoals(
            calorieGoal = request.calorieGoal ?: current.calorieGoal,
            proteinGoalG = request.proteinGoalG ?: current.proteinGoalG,
            fatGoalG = request.fatGoalG ?: current.fatGoalG,
            carbsGoalG = request.carbsGoalG ?: current.carbsGoalG,
            waterGoalMl = request.waterGoalMl ?: current.waterGoalMl,
        )
        // Bounds, not advice: the product does not compute a target from her body, it
        // only refuses figures that cannot be meant seriously.
        validateRange("calorieGoal", updated.calorieGoal, 800, 6000)
        validateRange("proteinGoalG", updated.proteinGoalG, 10, 400)
        validateRange("fatGoalG", updated.fatGoalG, 10, 300)
        validateRange("carbsGoalG", updated.carbsGoalG, 10, 800)
        validateRange("waterGoalMl", updated.waterGoalMl, 500, 6000)

        nutrition.saveGoals(userId, updated)
        return updated
    }

    suspend fun searchFoods(userId: Uuid, query: String?): List<FoodItem> {
        access.requireUser(userId)
        return nutrition.searchFoods(query, FOOD_SEARCH_LIMIT)
    }

    private fun List<Meal>.total() = NutritionTotals(
        kcal = sumOf { it.kcal },
        proteinG = sumOf { it.proteinG },
        fatG = sumOf { it.fatG },
        carbsG = sumOf { it.carbsG },
    )

    private fun validateAmount(field: String, value: Int, max: Int) {
        if (value < 0) throw ValidationException(field, "Manfiy bo'lishi mumkin emas")
        if (value > max) throw ValidationException(field, "Eng ko'pi $max")
    }

    private fun validateRange(field: String, value: Int, min: Int, max: Int) {
        if (value !in min..max) throw ValidationException(field, "$min–$max oralig'ida bo'lishi kerak")
    }

    private companion object {
        const val MAX_DESCRIPTION = Limits.MEAL_DESCRIPTION_MAX
        const val MAX_KCAL = 10_000
        const val MAX_MACRO = 1_000
        const val MAX_WATER_STEP = Limits.WATER_STEP_MAX_ML
        const val FOOD_SEARCH_LIMIT = 50

        /** Roughly 4 MB of image once base64 is undone. */
        const val MAX_IMAGE_CHARS = 5_600_000
        val ALLOWED_MIME = setOf("image/jpeg", "image/png", "image/webp")
    }
}
