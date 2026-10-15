package uz.sadora.contract

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

/**
 * Everything a home-screen widget or a watch draws, in one answer.
 *
 * One call and not the four the app makes, because a widget's refreshes are rationed by
 * the OS — iOS gives a few dozen a day — and spending four of them on one picture would
 * leave the widget a day behind by evening.
 *
 * It carries the stage *anchor* and not only today's week, so a widget that has not been
 * refreshed since yesterday can still count the new day: a pregnancy week is the due
 * date minus today, a cycle day is yesterday's plus one.
 */
@Serializable
data class WidgetSummary(
    /** Her local date, by her timezone — the day [water] and [medications] belong to. */
    val today: LocalDate,
    val lifeStage: LifeStage,
    val stage: StageBaseline? = null,
    /** Only for the stages that count a cycle; null in pregnancy, postpartum and menopause. */
    val cycle: CycleStatus? = null,
    val water: WaterState,
    val medications: MedicationDay,
    /** What a wearable or the phone measured today; null when nothing is connected. */
    val health: DailyHealth? = null,
    /** Premium unlocks the configurable sizes and the watch; the small stage widget is free. */
    val premium: Boolean,
    /** Draw in neutral words: see [UserProfile.discreet]. */
    val discreet: Boolean,
    /** The app's language, which a widget cannot read from the app itself. */
    val language: Language,
)
