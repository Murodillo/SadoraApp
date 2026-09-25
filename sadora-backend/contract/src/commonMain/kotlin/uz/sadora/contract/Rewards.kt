package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gul — the app's own currency, and the streak that earns most of it.
 *
 * Two rules shape everything in this file. Gul is earned for *using* the app, never for
 * a health number: a good night's sleep pays the same as a bad one, because paying for
 * outcomes would put a price on her body. And the server owns every balance — the app
 * displays what it is told and never adds up its own, or a reinstall would mint coins.
 */

/** What an award was for. The amounts behind these keys are the operator's to set. */
object CoinReasons {
    /** Opening the app on a day she had not opened it yet. */
    const val DAILY_OPEN = "daily_open"

    /** Reaching a streak milestone — 3, 7, 30, 100 days. The length is the reference. */
    const val STREAK_MILESTONE = "streak_milestone"

    /** The day's check-in: mood, energy, stress. */
    const val CHECK_IN = "check_in"

    /** Hitting the water goal. Once a day, and only the goal she set herself. */
    const val WATER_GOAL = "water_goal"

    /** Confirming a dose. Capped per day so a long course does not become a coin farm. */
    const val DOSE_TAKEN = "dose_taken"

    /** Logging a meal, however it was logged — search or scanner. */
    const val MEAL_LOGGED = "meal_logged"

    /** Writing a journal entry. */
    const val JOURNAL_ENTRY = "journal_entry"

    /** Finishing a breathing or meditation session. */
    const val PRACTICE = "practice"

    /** Reading an article to the end. */
    const val ARTICLE_READ = "article_read"

    /** A friend signed up with her code. Paid to the inviter. */
    const val REFERRAL_JOINED = "referral_joined"

    /** The welcome the invited account gets for arriving with a code. */
    const val REFERRAL_WELCOME = "referral_welcome"

    /** Spending, written as a negative amount. */
    const val REDEMPTION = "redemption"

    /** An operator's manual correction, from the user card. */
    const val ADMIN_ADJUSTMENT = "admin_adjustment"

    /** Every reason an award can carry, in the order the admin table lists them. */
    val earnable: List<String> = listOf(
        DAILY_OPEN,
        STREAK_MILESTONE,
        CHECK_IN,
        WATER_GOAL,
        DOSE_TAKEN,
        MEAL_LOGGED,
        JOURNAL_ENTRY,
        PRACTICE,
        ARTICLE_READ,
        REFERRAL_JOINED,
        REFERRAL_WELCOME,
    )
}

/** The streak lengths that pay a bonus, smallest first. */
val StreakMilestones: List<Int> = listOf(3, 7, 14, 30, 60, 100, 365)

/**
 * How the app opening was counted.
 *
 * [current] is the run of consecutive days including today once [openedToday] is true.
 * A day missed resets it to one rather than to zero: she opened the app today, and a
 * streak that reads "0" on the day she came back would be punishing her for returning.
 */
@Serializable
data class StreakStatus(
    val current: Int = 0,
    val longest: Int = 0,
    val lastOpenOn: LocalDate? = null,
    val openedToday: Boolean = false,
    val totalDays: Int = 0,
) {
    /** The next milestone she is working toward, or null past the last one. */
    val nextMilestone: Int? get() = StreakMilestones.firstOrNull { it > current }

    /** How far along she is toward [nextMilestone], 0..1. */
    val milestoneProgress: Float
        get() {
            val next = nextMilestone ?: return 1f
            val previous = StreakMilestones.lastOrNull { it <= current } ?: 0
            val span = (next - previous).coerceAtLeast(1)
            return ((current - previous).toFloat() / span).coerceIn(0f, 1f)
        }
}

/**
 * The wallet.
 *
 * [balance] is [earned] minus [spent] as the ledger has it — computed by the server on
 * every read rather than kept in a column, so a balance can never drift away from the
 * rows that explain it.
 */
@Serializable
data class CoinBalance(
    val balance: Int = 0,
    val earned: Int = 0,
    val spent: Int = 0,
)

/** One row of the wallet's history, already worded by the server for her language. */
@Serializable
data class CoinEntry(
    val id: String,
    val amount: Int,
    val reason: String,
    val title: String,
    val createdAt: Instant,
)

/** One award, as the check-in animation lists them. */
@Serializable
data class CoinAward(
    val reason: String,
    val amount: Int,
    val title: String,
    /** The streak length behind a milestone award; null for every other reason. */
    val milestone: Int? = null,
)

/**
 * The answer to "the app just opened".
 *
 * [celebrate] is the server's call, not the app's: it is true exactly when this open
 * started a new day of the streak, which is the one moment the overlay should appear.
 * Reopening the app ten minutes later returns the same numbers with [celebrate] false.
 */
@Serializable
data class DailyCheckInResult(
    val streak: StreakStatus,
    val coins: CoinBalance,
    val awards: List<CoinAward> = emptyList(),
    val celebrate: Boolean = false,
    /** Set when this open landed on a milestone, so the overlay can say which. */
    val milestone: Int? = null,
)

/** Everything the wallet screen shows, in one read. */
@Serializable
data class RewardsSummary(
    val streak: StreakStatus,
    val coins: CoinBalance,
    val history: List<CoinEntry> = emptyList(),
    val referral: ReferralStatus? = null,
    /** What each action pays, so the "how to earn" list is never out of date. */
    val earnRates: List<EarnRate> = emptyList(),
)

/** One line of the "how Gul is earned" list. */
@Serializable
data class EarnRate(
    val reason: String,
    val amount: Int,
    val dailyCap: Int? = null,
)

// ---------------------------------------------------------------- referral

/**
 * Her invite code and what it has brought in.
 *
 * [link] is built by the server so the domain lives in one place; the app shares the
 * string it is given rather than assembling a URL of its own.
 */
@Serializable
data class ReferralStatus(
    val code: String,
    val link: String,
    val invited: Int = 0,
    val coinsEarned: Int = 0,
    /** What the next accepted invite pays her, and what it pays the person invited. */
    val rewardPerJoin: Int = 0,
    val welcomeReward: Int = 0,
)

/** Sent once, by the invited account, when it finishes onboarding. */
@Serializable
data class ClaimReferralRequest(val code: String)

@Serializable
data class ClaimReferralResult(
    val accepted: Boolean,
    val coins: CoinBalance,
    val awarded: Int = 0,
)

// ---------------------------------------------------------------- admin

/** A coin rule as the panel edits it. */
@Serializable
data class CoinRule(
    val reason: String,
    val amount: Int,
    /** How many times a day it can pay. Null is unlimited; only counted awards use it. */
    val dailyCap: Int? = null,
    val enabled: Boolean = true,
    val description: String,
)

@Serializable
data class SaveCoinRuleRequest(
    val amount: Int,
    val dailyCap: Int? = null,
    val enabled: Boolean = true,
)

/** What the panel adds or takes away by hand, always with a reason on the audit row. */
@Serializable
data class AdjustCoinsRequest(
    val amount: Int,
    val note: String,
)

/** The operator's view of one account's rewards. */
@Serializable
data class AdminRewardsCard(
    val streak: StreakStatus,
    val coins: CoinBalance,
    val referral: ReferralStatus? = null,
    val history: List<CoinEntry> = emptyList(),
)

/** How the whole scheme is doing, for the rewards page's header. */
@Serializable
data class RewardsOverview(
    val coinsOutstanding: Long = 0,
    val coinsEarnedTotal: Long = 0,
    val coinsSpentTotal: Long = 0,
    val redemptionsIssued: Long = 0,
    val activeStreaks: Long = 0,
    val longestStreak: Int = 0,
    val referralsAccepted: Long = 0,
)

// ---------------------------------------------------------------- app icon

/**
 * How warm the launcher icon is, decided by the streak and applied by the platform.
 *
 * Deliberately three steps and not a gradient: the icon is a 48-pixel square on a home
 * screen, and a difference she cannot see is not feedback. [COLD] is the resting state
 * of an app that has not been opened in a while, never a punishment for one missed day.
 */
@Serializable
enum class AppIconMood {
    @SerialName("warm") WARM,
    @SerialName("calm") CALM,
    @SerialName("cold") COLD;

    companion object {
        /**
         * The mood a streak earns.
         *
         * A live streak of a week or more is warm; anything still running is calm; only
         * a streak that has actually lapsed goes cold.
         */
        fun forStreak(current: Int, daysSinceLastOpen: Int): AppIconMood = when {
            daysSinceLastOpen >= 3 -> COLD
            current >= 7 -> WARM
            current >= 1 -> CALM
            else -> COLD
        }
    }
}
