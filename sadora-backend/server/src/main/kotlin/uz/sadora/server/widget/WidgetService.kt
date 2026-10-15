package uz.sadora.server.widget

import kotlin.uuid.Uuid
import uz.sadora.contract.SubscriptionTier
import uz.sadora.contract.WaterState
import uz.sadora.contract.WidgetSummary
import uz.sadora.server.health.HealthService
import uz.sadora.server.health.MedicationService
import uz.sadora.server.health.NutritionService
import uz.sadora.server.user.UserService
import uz.sadora.server.wearable.WearableService

/**
 * The one answer a home-screen widget and a watch draw from.
 *
 * Composed from the services the app's own screens read, so a widget can never show a
 * number the app would not: the same cycle prediction, the same dose schedule, the same
 * water total. Nothing here computes anything of its own.
 */
class WidgetService(
    private val users: UserService,
    private val health: HealthService,
    private val nutrition: NutritionService,
    private val medications: MedicationService,
    private val wearables: WearableService,
) {
    suspend fun summary(userId: Uuid): WidgetSummary {
        val profile = users.profile(userId)
        val cycle = if (profile.lifeStage.predictsCycle) health.status(userId) else null
        val day = nutrition.day(userId, null)
        return WidgetSummary(
            today = day.date,
            lifeStage = profile.lifeStage,
            stage = profile.stage,
            cycle = cycle,
            water = WaterState(day.date, day.waterMl, day.goals.waterGoalMl),
            medications = medications.day(userId, day.date),
            // Health data sits behind its own consent; a widget without it draws the rest.
            health = runCatching { wearables.today(userId) }.getOrNull()?.takeIf { it.metrics.isNotEmpty() },
            premium = users.entitlements(userId).tier == SubscriptionTier.PREMIUM,
            discreet = profile.discreet,
            language = profile.language,
        )
    }
}
