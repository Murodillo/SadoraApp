package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Things a stage asks her to note as they happen, rather than once a day: a feed, a
 * count of the baby's kicks, a contraction, a hot flush, a mood questionnaire.
 *
 * One shape for all of them because they are all the same kind of fact — something
 * happened at a time, perhaps for a while, perhaps with a number — and one list of them
 * is what a doctor reads. What each field means depends on [kind]:
 *
 * | kind          | durationSeconds        | value                 | detail             |
 * |---------------|------------------------|-----------------------|--------------------|
 * | FEEDING       | at the breast          | millilitres, bottle   | [FeedingSide]      |
 * | KICK_COUNT    | time to count them     | kicks counted         | —                  |
 * | CONTRACTION   | how long it lasted     | —                     | —                  |
 * | HOT_FLUSH     | —                      | intensity 1–3         | [HotFlushTrigger]  |
 * | MOOD_SCREEN   | —                      | EPDS score, 0–30      | the ten answers    |
 */
@Serializable
enum class StageEventKind {
    @SerialName("feeding") FEEDING,
    @SerialName("kick_count") KICK_COUNT,
    @SerialName("contraction") CONTRACTION,
    @SerialName("hot_flush") HOT_FLUSH,
    @SerialName("mood_screen") MOOD_SCREEN,
}

@Serializable
enum class FeedingSide {
    @SerialName("left") LEFT,
    @SerialName("right") RIGHT,
    @SerialName("bottle") BOTTLE,
    @SerialName("pump") PUMP,
    ;

    val isBreast: Boolean get() = this == LEFT || this == RIGHT
}

/** What she thinks set a hot flush off — the triggers worth noticing a pattern in. */
@Serializable
enum class HotFlushTrigger {
    @SerialName("heat") HEAT,
    @SerialName("hot_drink") HOT_DRINK,
    @SerialName("spicy_food") SPICY_FOOD,
    @SerialName("caffeine") CAFFEINE,
    @SerialName("alcohol") ALCOHOL,
    @SerialName("stress") STRESS,
    @SerialName("night") NIGHT,
}

@Serializable
data class StageEvent(
    val id: String,
    val kind: StageEventKind,
    val startedAt: Instant,
    val durationSeconds: Int? = null,
    val value: Int? = null,
    val detail: String? = null,
    val createdAt: Instant,
)

/**
 * A new event. For [StageEventKind.MOOD_SCREEN] only [answers] is read: the score is the
 * server's to work out, so a result stored is a result the questionnaire gives.
 */
@Serializable
data class LogStageEventRequest(
    val kind: StageEventKind,
    val startedAt: Instant,
    val durationSeconds: Int? = null,
    val value: Int? = null,
    val detail: String? = null,
    val answers: List<Int>? = null,
)

/**
 * The Edinburgh Postnatal Depression Scale: ten questions about the past seven days,
 * each answered with one of four options, scored 0–3.
 *
 * Cox, J.L., Holden, J.M. and Sagovsky, R. (1987) Detection of postnatal depression.
 * Development of the 10-item Edinburgh Postnatal Depression Scale. British Journal of
 * Psychiatry 150: 782–786.
 *
 * Shared so the app and the server score it the same way. An answer is the index of the
 * option as the questionnaire lists it; items 1, 2 and 4 score that index, the others
 * are listed worst-first and score it reversed.
 */
object Epds {
    const val ITEMS = 10
    const val OPTIONS = 4
    const val MAX_SCORE = 30

    /** Zero-based items scored in the order listed; the rest are reversed. */
    private val forward = setOf(0, 1, 3)

    /** The self-harm question. Any answer but "never" is acted on, whatever the total. */
    const val SELF_HARM_ITEM = 9

    /** At or above: depression is likely and a specialist should see her. */
    const val LIKELY = 13

    /** At or above (below [LIKELY]): possible; worth raising and repeating in two weeks. */
    const val POSSIBLE = 10

    fun isComplete(answers: List<Int>): Boolean =
        answers.size == ITEMS && answers.all { it in 0 until OPTIONS }

    fun itemScore(item: Int, answer: Int): Int = if (item in forward) answer else OPTIONS - 1 - answer

    fun score(answers: List<Int>): Int = answers.withIndex().sumOf { (item, answer) -> itemScore(item, answer) }

    fun selfHarm(answers: List<Int>): Boolean =
        answers.getOrNull(SELF_HARM_ITEM)?.let { itemScore(SELF_HARM_ITEM, it) > 0 } ?: false
}
