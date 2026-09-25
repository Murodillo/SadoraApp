package uz.sadora.contract

import kotlinx.serialization.Serializable

@Serializable
data class AiChatRequest(
    val question: String,
)

/**
 * One answer from SADORA AI.
 *
 * [basedOn] names the data the answer drew on — "Sikl 14-kun, uyqu 6s 40d" — and is
 * empty when she has not consented to AI insights, in which case the answer is general
 * and says so. [remainingToday] and [remainingThisMonth] are null for an unmetered
 * allowance; the app shows the counter from here rather than counting on its own.
 */
@Serializable
data class AiChatReply(
    val answer: String,
    val basedOn: String = "",
    val remainingToday: Int? = null,
    val remainingThisMonth: Int? = null,
)

/** What the chat screen shows before the first question: how many are left. */
@Serializable
data class AiChatQuota(
    val enabled: Boolean,
    val dailyLimit: Int? = null,
    val monthlyLimit: Int? = null,
    val usedToday: Int = 0,
    val usedThisMonth: Int = 0,
) {
    val remainingToday: Int?
        get() = dailyLimit?.let { (it - usedToday).coerceAtLeast(0) }

    val remainingThisMonth: Int?
        get() = monthlyLimit?.let { (it - usedThisMonth).coerceAtLeast(0) }
}

/**
 * The line under "Salom, Malika!" on the home screen.
 *
 * Written for this moment: the hour, how long her streak is, where she is in her cycle,
 * how she slept. It changes on every open, which is the whole point — a greeting that
 * says the same thing twice is wallpaper, and she stops reading it on the third day.
 *
 * Cost is bounded the only way a per-open model call can be: the server asks for several
 * lines at once and hands them out one at a time until the context behind them changes.
 * [source] says which of the two wrote it — `model` or `rules` — so the admin page can
 * see how often the model is actually reached.
 */
@Serializable
data class AiGreeting(
    val line: String,
    val source: String = "rules",
)
