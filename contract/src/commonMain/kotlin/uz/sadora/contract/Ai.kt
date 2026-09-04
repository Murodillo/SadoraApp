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
