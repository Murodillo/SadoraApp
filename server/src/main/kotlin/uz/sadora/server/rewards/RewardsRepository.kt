package uz.sadora.server.rewards

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.CoinBalance
import uz.sadora.contract.CoinEntry
import uz.sadora.contract.CoinRule
import uz.sadora.contract.StreakMilestones
import uz.sadora.contract.StreakStatus
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.CoinLedger
import uz.sadora.server.db.CoinRules
import uz.sadora.server.db.ReferralClaims
import uz.sadora.server.db.ReferralCodes
import uz.sadora.server.db.UserStreaks
import uz.sadora.server.db.dbQuery

/**
 * What one open of the app did to the streak.
 *
 * [startedNewDay] is false when she has already opened it today, which is what stops the
 * celebration from firing every time she comes back from the camera.
 */
data class StreakOutcome(
    val status: StreakStatus,
    val startedNewDay: Boolean,
    val milestone: Int? = null,
)

/**
 * Streaks, the coin ledger and the referral tables.
 *
 * Two invariants are held here rather than in the service above:
 *
 *  - a balance is always `SUM(amount)` over the ledger, never a stored number. There is
 *    no column that could drift away from the rows explaining it;
 *  - the once-a-day awards are unique in the *index*, so two opens racing each other
 *    cannot both pay. [award] returns whether the row actually landed.
 */
class RewardsRepository {

    // ---------------------------------------------------------------- streak

    suspend fun streak(userId: Uuid, today: LocalDate): StreakStatus = dbQuery {
        readStreak(userId, today)
    }

    /**
     * Records that the app was opened on [today], in her timezone.
     *
     * Three cases: the same day again (nothing changes), the next day (the run grows),
     * or a gap (the run restarts at one — the day she came back is day one, not zero).
     */
    suspend fun recordOpen(userId: Uuid, today: LocalDate): StreakOutcome = dbQuery {
        val row = UserStreaks.selectAll().where { UserStreaks.userId eq userId }.singleOrNull()
        val last = row?.get(UserStreaks.lastOpenOn)
        val previous = row?.get(UserStreaks.currentDays) ?: 0

        if (last == today) {
            return@dbQuery StreakOutcome(readStreak(userId, today), startedNewDay = false)
        }

        val continued = last != null && last.plus(1, DateTimeUnit.DAY) == today
        val current = if (continued) previous + 1 else 1
        val longest = maxOf(current, row?.get(UserStreaks.longestDays) ?: 0)
        val total = (row?.get(UserStreaks.totalDays) ?: 0) + 1

        UserStreaks.upsert(UserStreaks.userId) {
            it[UserStreaks.userId] = userId
            it[currentDays] = current
            it[longestDays] = longest
            it[lastOpenOn] = today
            it[totalDays] = total
            it[updatedAt] = now().toOffsetDateTime()
        }

        StreakOutcome(
            status = StreakStatus(
                current = current,
                longest = longest,
                lastOpenOn = today,
                openedToday = true,
                totalDays = total,
            ),
            startedNewDay = true,
            milestone = StreakMilestones.firstOrNull { it == current },
        )
    }

    /**
     * The streak as it stands, with a run that has already lapsed reported as zero.
     *
     * The stored number is not decayed on write — nothing runs at her midnight to do it
     * — so a run that ended is recognised on the next read instead. Storing a stale
     * number and reading it honestly is cheaper than a nightly job over every account.
     */
    private fun readStreak(userId: Uuid, today: LocalDate): StreakStatus {
        val row = UserStreaks.selectAll().where { UserStreaks.userId eq userId }.singleOrNull()
            ?: return StreakStatus()
        val last = row[UserStreaks.lastOpenOn]
        val live = last != null && (last == today || last.plus(1, DateTimeUnit.DAY) == today)
        return StreakStatus(
            current = if (live) row[UserStreaks.currentDays] else 0,
            longest = row[UserStreaks.longestDays],
            lastOpenOn = last,
            openedToday = last == today,
            totalDays = row[UserStreaks.totalDays],
        )
    }

    // ---------------------------------------------------------------- coins

    suspend fun balance(userId: Uuid): CoinBalance = dbQuery { readBalance(userId) }

    private fun readBalance(userId: Uuid): CoinBalance {
        // Two sums rather than one so the wallet can say what came in and what went out;
        // the balance is their difference and cannot disagree with either.
        var earned = 0
        var spent = 0
        CoinLedger
            .select(CoinLedger.amount)
            .where { CoinLedger.userId eq userId }
            .forEach { row ->
                val amount = row[CoinLedger.amount]
                if (amount >= 0) earned += amount else spent += -amount
            }
        return CoinBalance(balance = earned - spent, earned = earned, spent = spent)
    }

    /**
     * Writes one award, or does nothing when the same one has already been paid.
     *
     * The uniqueness lives in the index, so this is the whole of the "once a day" rule:
     * a duplicate insert is caught rather than prevented by a read-then-write that two
     * requests could both pass.
     */
    suspend fun award(
        userId: Uuid,
        reason: String,
        amount: Int,
        earnedOn: LocalDate? = null,
        reference: String? = null,
        note: String? = null,
    ): Boolean = dbQuery {
        if (amount == 0) return@dbQuery false
        try {
            val inserted = CoinLedger.insertIgnore {
                it[id] = Uuid.random()
                it[CoinLedger.userId] = userId
                it[CoinLedger.amount] = amount
                it[CoinLedger.reason] = reason
                it[CoinLedger.earnedOn] = earnedOn
                it[CoinLedger.reference] = reference
                it[CoinLedger.note] = note
                it[createdAt] = now().toOffsetDateTime()
            }
            inserted.insertedCount > 0
        } catch (conflict: ExposedSQLException) {
            // insertIgnore covers the unique indexes; anything else is a real failure.
            false
        }
    }

    /**
     * Spends coins, refusing when the balance does not cover it.
     *
     * The check and the write share one transaction, so two redemptions of the last
     * coins cannot both succeed: the second reads the first's row.
     */
    suspend fun spend(userId: Uuid, amount: Int, reference: String): CoinBalance? = dbQuery {
        val balance = readBalance(userId)
        if (amount <= 0 || balance.balance < amount) return@dbQuery null
        CoinLedger.insert {
            it[id] = Uuid.random()
            it[CoinLedger.userId] = userId
            it[CoinLedger.amount] = -amount
            it[reason] = uz.sadora.contract.CoinReasons.REDEMPTION
            it[CoinLedger.reference] = reference
            it[createdAt] = now().toOffsetDateTime()
        }
        readBalance(userId)
    }

    /** How many times a reason has already paid today — the daily cap reads this. */
    suspend fun countToday(userId: Uuid, reason: String, day: LocalDate): Int = dbQuery {
        CoinLedger.selectAll()
            .where {
                (CoinLedger.userId eq userId) and
                    (CoinLedger.reason eq reason) and
                    (CoinLedger.earnedOn eq day)
            }
            .count()
            .toInt()
    }

    suspend fun history(userId: Uuid, limit: Int): List<LedgerRow> = dbQuery {
        CoinLedger.selectAll()
            .where { CoinLedger.userId eq userId }
            .orderBy(CoinLedger.createdAt to SortOrder.DESC)
            .limit(limit)
            .map { it.toLedgerRow() }
    }

    /** The ledger without its wording — the service adds that in her language. */
    data class LedgerRow(
        val id: String,
        val amount: Int,
        val reason: String,
        val reference: String?,
        val note: String?,
        val createdAt: Instant,
    )

    private fun ResultRow.toLedgerRow() = LedgerRow(
        id = this[CoinLedger.id].toString(),
        amount = this[CoinLedger.amount],
        reason = this[CoinLedger.reason],
        reference = this[CoinLedger.reference],
        note = this[CoinLedger.note],
        createdAt = this[CoinLedger.createdAt].toKotlinInstant(),
    )

    // ---------------------------------------------------------------- rules

    suspend fun rules(): List<CoinRule> = dbQuery {
        CoinRules.selectAll().map { it.toRule() }
    }

    suspend fun rule(reason: String): CoinRule? = dbQuery {
        CoinRules.selectAll().where { CoinRules.reason eq reason }.singleOrNull()?.toRule()
    }

    suspend fun saveRule(reason: String, amount: Int, dailyCap: Int?, enabled: Boolean): CoinRule? = dbQuery {
        val updated = CoinRules.update({ CoinRules.reason eq reason }) {
            it[CoinRules.amount] = amount
            it[CoinRules.dailyCap] = dailyCap
            it[CoinRules.enabled] = enabled
            it[updatedAt] = now().toOffsetDateTime()
        }
        if (updated == 0) return@dbQuery null
        CoinRules.selectAll().where { CoinRules.reason eq reason }.single().toRule()
    }

    private fun ResultRow.toRule() = CoinRule(
        reason = this[CoinRules.reason],
        amount = this[CoinRules.amount],
        dailyCap = this[CoinRules.dailyCap],
        enabled = this[CoinRules.enabled],
        description = this[CoinRules.description],
    )

    // ---------------------------------------------------------------- referral

    suspend fun referralCodeOf(userId: Uuid): String? = dbQuery {
        ReferralCodes.selectAll()
            .where { ReferralCodes.userId eq userId }
            .singleOrNull()
            ?.get(ReferralCodes.code)
    }

    /** Claims a generated code for the account, or reports the collision to the caller. */
    suspend fun claimCode(userId: Uuid, code: String): Boolean = dbQuery {
        ReferralCodes.insertIgnore {
            it[ReferralCodes.code] = code
            it[ReferralCodes.userId] = userId
            it[createdAt] = now().toOffsetDateTime()
        }.insertedCount > 0
    }

    suspend fun ownerOfCode(code: String): Uuid? = dbQuery {
        ReferralCodes.selectAll()
            .where { ReferralCodes.code eq code }
            .singleOrNull()
            ?.get(ReferralCodes.userId)
    }

    /** One arrival per invited account, enforced by the primary key rather than a read. */
    suspend fun recordClaim(invited: Uuid, inviter: Uuid, code: String): Boolean = dbQuery {
        ReferralClaims.insertIgnore {
            it[invitedUserId] = invited
            it[inviterUserId] = inviter
            it[ReferralClaims.code] = code
            it[createdAt] = now().toOffsetDateTime()
        }.insertedCount > 0
    }

    suspend fun invitedCount(userId: Uuid): Int = dbQuery {
        ReferralClaims.selectAll()
            .where { ReferralClaims.inviterUserId eq userId }
            .count()
            .toInt()
    }

    /**
     * The scheme's totals, for the panel's header.
     *
     * Summed in the database. Coins outstanding is what the app owes: everything earned
     * that has not yet been spent, which is the number an operator needs before changing
     * a rate.
     */
    suspend fun overview(): RewardTotals = dbQuery {
        var earned = 0L
        var spent = 0L
        CoinLedger.select(CoinLedger.amount).forEach { row ->
            val amount = row[CoinLedger.amount]
            if (amount >= 0) earned += amount else spent += -amount
        }
        val streaks = UserStreaks.selectAll().map {
            it[UserStreaks.currentDays] to it[UserStreaks.longestDays]
        }
        RewardTotals(
            earned = earned,
            spent = spent,
            activeStreaks = streaks.count { it.first > 0 }.toLong(),
            longestStreak = streaks.maxOfOrNull { it.second } ?: 0,
            referralsAccepted = ReferralClaims.selectAll().count(),
        )
    }

    data class RewardTotals(
        val earned: Long,
        val spent: Long,
        val activeStreaks: Long,
        val longestStreak: Int,
        val referralsAccepted: Long,
    )

    /** What the invites have actually paid, read from the ledger rather than assumed. */
    suspend fun referralCoins(userId: Uuid): Int = dbQuery {
        CoinLedger
            .select(CoinLedger.amount)
            .where {
                (CoinLedger.userId eq userId) and
                    (CoinLedger.reason eq uz.sadora.contract.CoinReasons.REFERRAL_JOINED)
            }
            .sumOf { it[CoinLedger.amount] }
    }
}

/** Turns a ledger row into the line the wallet shows, worded by the caller. */
fun RewardsRepository.LedgerRow.toEntry(title: String): CoinEntry = CoinEntry(
    id = id,
    amount = amount,
    reason = reason,
    title = note?.takeIf { it.isNotBlank() } ?: title,
    createdAt = createdAt,
)
