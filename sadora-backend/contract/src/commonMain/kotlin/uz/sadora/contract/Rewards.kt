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

    /** Her Yaqinim said yes and sees her for the first time. Paid once, to her. */
    const val PARTNER_LINKED = "partner_linked"

    /** A badge reached a new tier. Paid once per badge and tier; the higher the tier, the more. */
    const val BADGE_EARNED = "badge_earned"

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
        PARTNER_LINKED,
        BADGE_EARNED,
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

// ---------------------------------------------------------------- badges

/**
 * The badges, and the thresholds behind each tier.
 *
 * Like Gul, a badge is earned for *doing* — logging, reading, showing up — and never for
 * a number her body produced: there is no "perfect cycle" or "ideal weight" badge, and
 * there will not be one. The catalogue lives here so the server that counts and the app
 * that draws agree on the same thresholds; the words are the app's, in her language.
 *
 * Most badges have three tiers (bronze, silver, gold). A badge with one threshold is a
 * single moment — the first day, her person joining — and is drawn as gold.
 */
object Badges {
    const val FIRST_STEP = "first_step"
    const val STREAK = "streak"
    const val LOYAL = "loyal"
    const val CYCLE = "cycle"
    const val DAILY_LOG = "daily_log"
    const val WATER = "water"
    const val MEDS = "meds"
    const val MEALS = "meals"
    const val JOURNAL = "journal"
    const val MIND = "mind"
    const val READER = "reader"
    const val FRIENDS = "friends"
    const val PARTNER = "partner"
    const val DOCTOR = "doctor"
    const val COMMUNITY = "community"
    const val SYMPTOMS = "symptoms"
    const val MOOD = "mood"
    const val CALM_MINUTES = "calm_minutes"
    const val SCANNER = "scanner"
    const val DEVICES = "devices"
    const val HYDRO = "hydro"
    const val CURIOUS = "curious"
    const val SHARE = "share"
    const val HELPER = "helper"
    const val LOVED = "loved"
    const val SHOPPER = "shopper"
    const val GARDENER = "gardener"

    /** Every badge with its tier thresholds, in the order the board shows them. */
    val catalogue: List<Pair<String, List<Int>>> = listOf(
        FIRST_STEP to listOf(1),
        STREAK to listOf(7, 30, 100),
        LOYAL to listOf(30, 100, 365),
        CYCLE to listOf(1, 3, 12),
        DAILY_LOG to listOf(7, 30, 100),
        WATER to listOf(7, 30, 100),
        MEDS to listOf(10, 50, 200),
        MEALS to listOf(10, 50, 200),
        JOURNAL to listOf(3, 15, 50),
        MIND to listOf(3, 15, 50),
        READER to listOf(5, 25, 75),
        FRIENDS to listOf(1, 3, 10),
        PARTNER to listOf(1),
        DOCTOR to listOf(1, 3, 10),
        COMMUNITY to listOf(1, 10, 50),
        SYMPTOMS to listOf(5, 30, 100),
        MOOD to listOf(7, 30, 100),
        CALM_MINUTES to listOf(30, 300, 1000),
        SCANNER to listOf(1, 10, 50),
        DEVICES to listOf(1),
        HYDRO to listOf(10, 50, 200),
        CURIOUS to listOf(5, 25, 100),
        SHARE to listOf(1, 3, 10),
        HELPER to listOf(5, 25, 100),
        LOVED to listOf(10, 50, 200),
        SHOPPER to listOf(1, 3, 10),
        GARDENER to listOf(500, 2500, 10000),
    )

    fun tiersOf(key: String): List<Int> = catalogue.firstOrNull { it.first == key }?.second.orEmpty()

    /** The tier a count has reached: 0 below the first threshold. */
    fun tierFor(key: String, count: Int): Int = tiersOf(key).count { count >= it }

    /** How many Gul a tier pays, as a multiple of the rule's amount: 1, 2, 4. */
    fun coinMultiplier(tier: Int, tierCount: Int): Int =
        if (tierCount == 1) 2 else when (tier) { 1 -> 1; 2 -> 2; else -> 4 }
}

/**
 * One badge on her board.
 *
 * [tier] is 0 while it is still locked. [progress] is the raw count behind it, so the app
 * can say "12 / 30" without knowing how the server counted.
 */
@Serializable
data class BadgeState(
    val key: String,
    val tier: Int = 0,
    val thresholds: List<Int> = emptyList(),
    val progress: Int = 0,
    /** When the current tier was reached. */
    val earnedAt: Instant? = null,
) {
    val maxTier: Int get() = thresholds.size
    val complete: Boolean get() = tier >= maxTier && maxTier > 0

    /** The count the next tier needs, or null once the last one is reached. */
    val nextThreshold: Int? get() = thresholds.getOrNull(tier)

    /** 0..1 toward [nextThreshold], from the tier below it. */
    val nextProgress: Float
        get() {
            val next = nextThreshold ?: return 1f
            val previous = thresholds.getOrNull(tier - 1) ?: 0
            return ((progress - previous).toFloat() / (next - previous).coerceAtLeast(1)).coerceIn(0f, 1f)
        }
}

/** A tier she reached and has not been shown yet — what the unlock animation plays. */
@Serializable
data class BadgeUnlock(
    val key: String,
    val tier: Int,
    val maxTier: Int,
    val coins: Int = 0,
    val earnedAt: Instant,
    /** The avatar frame this tier gives ([AvatarFrames] key), when it gives one. */
    val frame: String? = null,
)

/**
 * The whole board, in one read.
 *
 * Reading it is also what awards: counts are taken now, any tier newly crossed is
 * written (and paid) in the same call, and it comes back in [unseen] until the app
 * says it has played the animation for it.
 */
@Serializable
data class BadgeBoard(
    val badges: List<BadgeState> = emptyList(),
    val unseen: List<BadgeUnlock> = emptyList(),
    /** The badge she wears beside her name, or null when she wears none (or cannot now). */
    val worn: String? = null,
    /** Whether she may wear one: Premium. False keeps the button behind the paywall. */
    val canWear: Boolean = false,
) {
    val earnedCount: Int get() = badges.sumOf { it.tier }
    val totalCount: Int get() = badges.sumOf { it.maxTier }
}

/**
 * The one badge she chose to wear: beside her name on her profile, and after her alias
 * on every post and comment she writes. [tier] is the highest she has reached of it, so
 * the medal others see grows with her without her choosing it again.
 */
@Serializable
data class WornBadge(
    val key: String,
    val tier: Int,
    val maxTier: Int,
)

/** Wear a badge she has earned; a null [key] takes it off. */
@Serializable
data class WearBadgeRequest(val key: String? = null)

/** "These have been celebrated." Empty means every unseen unlock. */
@Serializable
data class MarkBadgesSeenRequest(val keys: List<String> = emptyList())

// ---------------------------------------------------------------- doctor badges

/**
 * A verified doctor's badges, read in the doctor app and her web panel.
 *
 * The same rule as hers: a badge is for what the doctor *does* — answering, writing,
 * seeing patients, replying fast — never for money earned. The board and its tiers are
 * the same shapes as a woman's ([BadgeBoard], [BadgeState], [BadgeUnlock]); only the
 * catalogue differs, and there is no Gul and no wearing.
 *
 * "Rated" counts the reviews she was given, whatever the stars: a badge for five stars
 * alone would reward a number a patient chose, so [FIVE_STARS] stays a gentle second
 * ladder beside it rather than the only one.
 */
object DoctorBadges {
    const val VERIFIED = "verified"
    const val PHOTO = "photo"
    const val ANSWERS = "answers"
    const val POSTS = "posts"
    const val CONSULTS = "consults"
    const val PATIENTS = "patients"
    const val FAST_REPLY = "fast_reply"
    const val MESSAGES = "messages"
    const val RATED = "rated"
    const val FIVE_STARS = "five_stars"
    const val RECORDS = "records"
    const val NOTES = "notes"
    const val QUICK_REPLIES = "quick_replies"
    const val THANKED = "thanked"
    const val TENURE = "tenure"

    /** A first reply within this many minutes of the window opening counts as fast. */
    const val FAST_REPLY_MINUTES = 60

    /** Every badge with its tier thresholds, in the order the board shows them. */
    val catalogue: List<Pair<String, List<Int>>> = listOf(
        VERIFIED to listOf(1),
        PHOTO to listOf(1),
        ANSWERS to listOf(5, 25, 100),
        POSTS to listOf(1, 10, 50),
        CONSULTS to listOf(1, 10, 50),
        PATIENTS to listOf(5, 25, 100),
        FAST_REPLY to listOf(5, 25, 100),
        MESSAGES to listOf(50, 250, 1000),
        RATED to listOf(5, 25, 100),
        FIVE_STARS to listOf(5, 25, 100),
        RECORDS to listOf(1, 10, 50),
        NOTES to listOf(5, 25, 100),
        QUICK_REPLIES to listOf(1, 5, 15),
        THANKED to listOf(10, 50, 200),
        TENURE to listOf(30, 180, 365),
    )

    fun tiersOf(key: String): List<Int> = catalogue.firstOrNull { it.first == key }?.second.orEmpty()

    /** The tier a count has reached: 0 below the first threshold. */
    fun tierFor(key: String, count: Int): Int = tiersOf(key).count { count >= it }
}

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
