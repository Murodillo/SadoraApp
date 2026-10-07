package uz.sadora.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The companion that pops up after she does something — a tip, a cheer, a feature she
 * has not tried yet. Premium only ([FeatureKeys.AI_PET]); a free account sees it now and
 * then asleep, as a door to the paywall, and it never says anything useful there.
 */
@Serializable
enum class PetKind {
    /** The default: a lotus spirit, the same flower as the Sadora mark. */
    @SerialName("nilufar") NILUFAR,
    @SerialName("momiq") MOMIQ,
    @SerialName("laylo") LAYLO,
    @SerialName("anorxon") ANORXON,
    @SerialName("ohu") OHU;

    companion object {
        val DEFAULT = NILUFAR
    }
}

/** The four drawings each pet has. */
@Serializable
enum class PetPose {
    @SerialName("idle") IDLE,
    @SerialName("happy") HAPPY,
    @SerialName("think") THINK,
    @SerialName("sleep") SLEEP,
}

/**
 * What she just did. The app sends one of these after the action lands; the server
 * decides whether the pet says anything at all (cooldowns live there, so two devices do
 * not double the chatter) and what.
 *
 * There is deliberately no trigger on a screen that carries a clinical alert — a high
 * EPDS score, a foetal-movement warning, heavy bleeding — so a cartoon never answers one.
 */
@Serializable
enum class PetTrigger {
    @SerialName("app_open") APP_OPEN,
    @SerialName("cycle_logged") CYCLE_LOGGED,
    @SerialName("meal_logged") MEAL_LOGGED,
    @SerialName("food_scanned") FOOD_SCANNED,
    @SerialName("water_goal") WATER_GOAL,
    @SerialName("mood_low") MOOD_LOW,
    @SerialName("mood_good") MOOD_GOOD,
    @SerialName("med_taken") MED_TAKEN,
    @SerialName("badge_earned") BADGE_EARNED,
    @SerialName("streak_kept") STREAK_KEPT,
}

/** Where the bubble's button takes her; the app maps each to a screen. */
@Serializable
enum class PetAction {
    @SerialName("food_scanner") FOOD_SCANNER,
    @SerialName("mind_journal") MIND_JOURNAL,
    @SerialName("water") WATER,
    @SerialName("partner") PARTNER,
    @SerialName("badges") BADGES,
    @SerialName("doctor_share") DOCTOR_SHARE,
    @SerialName("ai_chat") AI_CHAT,
    @SerialName("learn") LEARN,
    @SerialName("medications") MEDICATIONS,
}

@Serializable
data class PetState(
    val pet: PetKind = PetKind.DEFAULT,
    /** True when her plan includes the pet; false shows only the sleeping teaser. */
    val active: Boolean = false,
)

@Serializable
data class ChoosePetRequest(val pet: PetKind)

@Serializable
data class PetNudgeRequest(
    val trigger: PetTrigger,
    /**
     * A short label for what she did, when there is one worth mentioning — the dish a
     * scan recognised. Never a number from her body; the server trims it to a line.
     */
    val detail: String? = null,
)

@Serializable
data class PetNudge(
    val pet: PetKind,
    val pose: PetPose,
    val text: String,
    val action: PetAction? = null,
    /** "model" or "rules", for the cost log's sake as much as for debugging. */
    val source: String = "rules",
)

/** [nudge] is null when the pet stays quiet this time — a cooldown, or nothing to say. */
@Serializable
data class PetNudgeAnswer(val nudge: PetNudge? = null)
