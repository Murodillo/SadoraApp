package uz.sadora.server.rewards

import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import uz.sadora.contract.AdminRewardsCard
import uz.sadora.contract.ClaimReferralResult
import uz.sadora.contract.CoinAward
import uz.sadora.contract.CoinBalance
import uz.sadora.contract.CoinEntry
import uz.sadora.contract.CoinReasons
import uz.sadora.contract.CoinRule
import uz.sadora.contract.DailyCheckInResult
import uz.sadora.contract.EarnRate
import uz.sadora.contract.Language
import uz.sadora.contract.ReferralStatus
import uz.sadora.contract.RewardsSummary
import uz.sadora.contract.StreakStatus
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.RewardHooks
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.user.UserRepository

/**
 * The reward scheme: the streak, the coins it earns, and the invite code that earns more.
 *
 * Three product decisions are enforced here rather than left to callers.
 *
 * **Nothing pays for a health outcome.** Every reason in [CoinReasons] is an action she
 * took in the app — opening it, logging something, reading something. Sleeping eight
 * hours pays exactly what sleeping four pays, because a currency that rewarded the
 * former would be paying her to enter numbers she wished were true.
 *
 * **The server owns the balance.** A phone that reinstalls, or one whose clock is a day
 * ahead, changes nothing: the calendar day comes from her timezone on the server and
 * every award is unique in the index.
 *
 * **The celebration fires once.** [checkIn] is called on every launch, and returns
 * `celebrate = true` only on the launch that started a new day of the streak.
 */
class RewardsService(
    private val repository: RewardsRepository,
    private val users: UserRepository,
    private val referralLinkBase: String,
) : RewardHooks {

    // ---------------------------------------------------------------- daily open

    /**
     * "The app just opened."
     *
     * Idempotent within a day: the second call returns the same numbers with nothing
     * awarded and nothing to celebrate.
     */
    suspend fun checkIn(userId: Uuid): DailyCheckInResult {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val today = now().dayIn(user.timezone)
        val outcome = repository.recordOpen(userId, today)

        if (!outcome.startedNewDay) {
            return DailyCheckInResult(
                streak = outcome.status,
                coins = repository.balance(userId),
                celebrate = false,
            )
        }

        val awards = buildList {
            grant(userId, CoinReasons.DAILY_OPEN, today, user.language)?.let(::add)
            outcome.milestone?.let { milestone ->
                grant(
                    userId = userId,
                    reason = CoinReasons.STREAK_MILESTONE,
                    day = today,
                    language = user.language,
                    reference = milestone.toString(),
                    milestone = milestone,
                    // A longer run is worth more: the rule's amount is the unit, and a
                    // hundred-day streak paying the same as a three-day one would make
                    // the milestone meaningless the second time she reached one.
                    multiplier = milestoneMultiplier(milestone),
                )?.let(::add)
            }
        }

        return DailyCheckInResult(
            streak = outcome.status,
            coins = repository.balance(userId),
            awards = awards,
            // Worth an animation only when something happened: a new day of the streak.
            celebrate = true,
            milestone = outcome.milestone,
        )
    }

    /**
     * Awards a reason if its rule allows it, and reports what was actually paid.
     *
     * Returns null when the rule is off, pays nothing, has already paid its cap today,
     * or when the row was refused by the unique index — which is the same answer from
     * the caller's point of view: nothing to show her.
     */
    suspend fun grant(
        userId: Uuid,
        reason: String,
        day: LocalDate? = null,
        language: Language = Language.UZ,
        reference: String? = null,
        milestone: Int? = null,
        multiplier: Int = 1,
    ): CoinAward? {
        val rule = repository.rule(reason)?.takeIf { it.enabled && it.amount > 0 } ?: return null
        val today = day ?: now().dayIn(users.findById(userId)?.timezone ?: "Asia/Tashkent")

        // A cap above one is counted; a cap of one (or none) is held by the index alone.
        val cap = rule.dailyCap
        val entryReference = when {
            reference != null -> reference
            cap != null && cap > 1 -> {
                val used = repository.countToday(userId, reason, today)
                if (used >= cap) return null
                "$today#${used + 1}"
            }
            else -> null
        }

        val amount = rule.amount * multiplier
        val written = repository.award(
            userId = userId,
            reason = reason,
            amount = amount,
            earnedOn = today,
            reference = entryReference,
        )
        if (!written) return null
        return CoinAward(
            reason = reason,
            amount = amount,
            title = RewardPhrases.title(reason, language, milestone),
            milestone = milestone,
        )
    }

    /**
     * Awards for something logged, called from the health services.
     *
     * Deliberately swallows its own failures: a coin that could not be written must
     * never stop a dose from being recorded. The log is the product; the coin is a
     * decoration on top of it.
     */
    override suspend fun logged(userId: Uuid, reason: String, reference: String?) {
        runCatching {
            val user = users.findById(userId) ?: return
            grant(
                userId = userId,
                reason = reason,
                day = now().dayIn(user.timezone),
                language = user.language,
                reference = reference,
            )
        }
    }

    // ---------------------------------------------------------------- wallet

    suspend fun summary(userId: Uuid, historyLimit: Int = 30): RewardsSummary {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val today = now().dayIn(user.timezone)
        val rules = repository.rules().filter { it.enabled && it.amount > 0 }
        return RewardsSummary(
            streak = repository.streak(userId, today),
            coins = repository.balance(userId),
            history = history(userId, user.language, historyLimit),
            referral = referral(userId, rules),
            earnRates = CoinReasons.earnable
                .mapNotNull { reason -> rules.firstOrNull { it.reason == reason } }
                .map { EarnRate(it.reason, it.amount, it.dailyCap) },
        )
    }

    suspend fun balance(userId: Uuid): CoinBalance = repository.balance(userId)

    suspend fun streak(userId: Uuid): StreakStatus {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        return repository.streak(userId, now().dayIn(user.timezone))
    }

    private suspend fun history(userId: Uuid, language: Language, limit: Int): List<CoinEntry> =
        repository.history(userId, limit).map { row ->
            val milestone = row.reference?.toIntOrNull()
                ?.takeIf { row.reason == CoinReasons.STREAK_MILESTONE }
            row.toEntry(RewardPhrases.title(row.reason, language, milestone))
        }

    // ---------------------------------------------------------------- referral

    /**
     * Her invite code, generated on first read.
     *
     * Not at sign-up: most accounts never open this screen, and a code taken out of the
     * space for each of them makes collisions likelier for the ones that do.
     */
    suspend fun referral(userId: Uuid, rules: List<CoinRule>? = null): ReferralStatus {
        val code = repository.referralCodeOf(userId) ?: generateCode(userId)
        val loaded = rules ?: repository.rules()
        return ReferralStatus(
            code = code,
            link = "$referralLinkBase/$code",
            invited = repository.invitedCount(userId),
            coinsEarned = repository.referralCoins(userId),
            rewardPerJoin = loaded.firstOrNull { it.reason == CoinReasons.REFERRAL_JOINED }
                ?.takeIf { it.enabled }?.amount ?: 0,
            welcomeReward = loaded.firstOrNull { it.reason == CoinReasons.REFERRAL_WELCOME }
                ?.takeIf { it.enabled }?.amount ?: 0,
        )
    }

    /**
     * The invited account presents the code it arrived with.
     *
     * Called once, from onboarding. Everything that could go wrong — an unknown code,
     * her own code, a second attempt — answers `accepted = false` rather than failing:
     * a typo in an optional field must not block a sign-up.
     */
    suspend fun claimReferral(invitedUserId: Uuid, rawCode: String): ClaimReferralResult {
        val code = normalise(rawCode)
        if (code.isEmpty()) return ClaimReferralResult(false, repository.balance(invitedUserId))

        val inviter = repository.ownerOfCode(code)
            ?: return ClaimReferralResult(false, repository.balance(invitedUserId))
        if (inviter == invitedUserId) {
            return ClaimReferralResult(false, repository.balance(invitedUserId))
        }

        if (!repository.recordClaim(invitedUserId, inviter, code)) {
            return ClaimReferralResult(false, repository.balance(invitedUserId))
        }

        val invitedUser = users.findById(invitedUserId)
        val inviterUser = users.findById(inviter)
        val today = now().dayIn(invitedUser?.timezone ?: "Asia/Tashkent")

        // The inviter is paid per arrival, so the reference is the account that arrived.
        grant(
            userId = inviter,
            reason = CoinReasons.REFERRAL_JOINED,
            day = now().dayIn(inviterUser?.timezone ?: "Asia/Tashkent"),
            language = inviterUser?.language ?: Language.UZ,
            reference = invitedUserId.toString(),
        )
        val welcome = grant(
            userId = invitedUserId,
            reason = CoinReasons.REFERRAL_WELCOME,
            day = today,
            language = invitedUser?.language ?: Language.UZ,
            reference = code,
        )

        return ClaimReferralResult(
            accepted = true,
            coins = repository.balance(invitedUserId),
            awarded = welcome?.amount ?: 0,
        )
    }

    /**
     * A code that is easy to read out over the phone.
     *
     * No vowels, so it cannot spell a word by accident, and no `0`/`O` or `1`/`I`, which
     * is the pair people get wrong when copying one off a screen.
     */
    private suspend fun generateCode(userId: Uuid): String {
        repeat(GENERATE_ATTEMPTS) {
            val code = (1..CODE_LENGTH).map { CODE_ALPHABET.random(Random) }.joinToString("")
            if (repository.claimCode(userId, code)) return code
            // Lost the race, or the code was taken: the row may now exist for this user.
            repository.referralCodeOf(userId)?.let { return it }
        }
        throw ValidationException("code", "Taklif kodini yaratib bo'lmadi, qayta urinib ko'ring")
    }

    // ---------------------------------------------------------------- admin

    suspend fun rules(): List<CoinRule> = repository.rules()

    suspend fun saveRule(reason: String, amount: Int, dailyCap: Int?, enabled: Boolean): CoinRule {
        if (amount < 0) throw ValidationException("amount", "Manfiy bo'lishi mumkin emas")
        if (dailyCap != null && dailyCap <= 0) {
            throw ValidationException("dailyCap", "Noldan katta bo'lishi kerak")
        }
        return repository.saveRule(reason, amount, dailyCap, enabled)
            ?: throw NotFoundException("Bunday qoida yo'q: $reason")
    }

    /** An operator's manual correction. The note is required and lands on the row. */
    suspend fun adjust(userId: Uuid, amount: Int, note: String): CoinBalance {
        if (amount == 0) throw ValidationException("amount", "Nol bo'lishi mumkin emas")
        if (note.isBlank()) throw ValidationException("note", "Sabab yozilishi shart")
        val balance = repository.balance(userId)
        if (amount < 0 && balance.balance + amount < 0) {
            throw ValidationException("amount", "Balansdan ko'p ayirib bo'lmaydi")
        }
        repository.award(
            userId = userId,
            reason = CoinReasons.ADMIN_ADJUSTMENT,
            amount = amount,
            reference = Uuid.random().toString(),
            note = note.trim(),
        )
        return repository.balance(userId)
    }

    suspend fun adminCard(userId: Uuid): AdminRewardsCard {
        val user = users.findById(userId) ?: throw NotFoundException("Foydalanuvchi topilmadi")
        val today = now().dayIn(user.timezone)
        return AdminRewardsCard(
            streak = repository.streak(userId, today),
            coins = repository.balance(userId),
            referral = repository.referralCodeOf(userId)?.let { referral(userId) },
            history = history(userId, user.language, 20),
        )
    }

    private companion object {
        const val CODE_LENGTH = 6
        const val GENERATE_ATTEMPTS = 8
        const val CODE_ALPHABET = "BCDFGHJKLMNPQRSTVWXYZ23456789"

        /** Longer runs pay more, in whole multiples of the rule's amount. */
        fun milestoneMultiplier(milestone: Int): Int = when {
            milestone >= 365 -> 20
            milestone >= 100 -> 8
            milestone >= 60 -> 5
            milestone >= 30 -> 3
            milestone >= 14 -> 2
            else -> 1
        }

        fun normalise(code: String): String = code.trim().uppercase().filter { it.isLetterOrDigit() }
    }
}
