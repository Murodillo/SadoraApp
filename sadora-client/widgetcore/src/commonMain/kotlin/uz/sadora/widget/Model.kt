package uz.sadora.widget

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.WidgetSummary

/** The OS's widget sizes; the lock-screen and watch shapes draw from SMALL's content. */
@Serializable
enum class WidgetSize { SMALL, MEDIUM, LARGE }

/** What a widget can show besides the stage, which every widget shows. */
@Serializable
enum class WidgetBlock { WATER, DOSES, HEALTH }

/**
 * One widget's settings, as she chose them when she placed it. Kept per widget by the OS
 * (an App Intent on iOS, the widget's id on Android), not on the server.
 */
@Serializable
data class WidgetConfig(
    val size: WidgetSize,
    val blocks: List<WidgetBlock> = emptyList(),
    /** What one tap of the water button adds. */
    val waterStepMl: Int = DEFAULT_WATER_STEP,
    val metrics: List<HealthMetric> = DEFAULT_METRICS,
    /** A lock-screen widget or a watch face: in public view, so discreet by default. */
    val lockScreen: Boolean = false,
    /** She turned the lock screen's discretion off for this widget. */
    val revealOnLockScreen: Boolean = false,
) {
    companion object {
        const val DEFAULT_WATER_STEP = 250
        val WATER_STEPS = listOf(150, 250, 330, 500)
        val DEFAULT_METRICS = listOf(HealthMetric.STEPS, HealthMetric.SLEEP_DURATION)

        /** The wearable numbers a widget offers; the rest are too rare to be worth a slot. */
        val METRIC_CHOICES = listOf(
            HealthMetric.STEPS,
            HealthMetric.SLEEP_DURATION,
            HealthMetric.RESTING_HEART_RATE,
            HealthMetric.HRV,
            HealthMetric.RECOVERY,
            HealthMetric.SPO2,
        )

        /** A widget nobody has set up: medium, with water and the next dose. Last, as it reads the above. */
        val Default = WidgetConfig(WidgetSize.MEDIUM, listOf(WidgetBlock.WATER, WidgetBlock.DOSES))
    }
}

/**
 * A tap made on a widget or a watch that the server has not confirmed yet.
 *
 * Drawn as if it had happened — the glass is counted the moment she taps — and sent when
 * a connection allows. Water carries its own id so a resend counts once; a dose is a
 * status, which sending twice cannot double.
 */
@Serializable
sealed interface PendingAction {
    @Serializable
    @SerialName("water")
    data class Water(val ml: Int, val clientId: String, val day: LocalDate) : PendingAction

    @Serializable
    @SerialName("dose")
    data class Dose(
        val medicationId: String,
        val dueOn: LocalDate,
        val dueAt: LocalTime,
        val status: DoseStatus,
    ) : PendingAction
}

/** What every widget, the watch and the app's own snapshot agree on. */
@Serializable
data class WidgetSnapshot(
    val summary: WidgetSummary,
    val savedAt: Instant,
    val pending: List<PendingAction> = emptyList(),
)
