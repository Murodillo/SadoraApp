package uz.sadora.server.rewards

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.Badges
import uz.sadora.contract.CoinReasons
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.WornBadge
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.UserBadges
import uz.sadora.server.db.UserWornBadge
import uz.sadora.server.db.dbQuery

/**
 * The counts behind each badge, and the tiers already reached.
 *
 * The counts are read live from the tables that hold what she did. Most are a `count(*)`
 * of rows — that a meal was logged, that a dose was confirmed. The few sums add up
 * effort, never a body: minutes she spent breathing, water she logged, Gul she earned.
 * Nothing here reads a weight, a cycle length or a sleep score, and nothing will — a
 * badge for a number her body produced would be a prize for a body. Water and articles
 * come from the coin ledger, which already records "the goal she set was reached" and
 * "this article was read to the end" once per day and per slug.
 */
class BadgeRepository {

    /** One user's count for every badge in [Badges.catalogue], in one round trip. */
    suspend fun counts(userId: Uuid): Map<String, Int> = dbQuery {
        // The id is a parsed Uuid, so interpolating it cannot inject anything.
        val u = "'$userId'::uuid"
        val sql = """
            SELECT
              (SELECT coalesce(max(total_days), 0) FROM user_streaks WHERE user_id = $u),
              (SELECT coalesce(max(longest_days), 0) FROM user_streaks WHERE user_id = $u),
              (SELECT count(*) FROM cycle_periods WHERE user_id = $u),
              (SELECT count(*) FROM daily_logs d WHERE d.user_id = $u AND (
                   d.flow IS NOT NULL OR d.mood IS NOT NULL OR d.energy IS NOT NULL OR d.stress IS NOT NULL
                   OR EXISTS (SELECT 1 FROM daily_symptoms s WHERE s.user_id = d.user_id AND s.log_date = d.log_date))),
              (SELECT count(DISTINCT earned_on) FROM coin_ledger WHERE user_id = $u AND reason = '${CoinReasons.WATER_GOAL}'),
              (SELECT count(*) FROM medication_intakes WHERE user_id = $u AND status = 'taken'),
              (SELECT count(*) FROM meals WHERE user_id = $u),
              (SELECT count(*) FROM journal_entries WHERE user_id = $u),
              (SELECT count(*) FROM mind_practices WHERE user_id = $u),
              (SELECT count(*) FROM coin_ledger WHERE user_id = $u AND reason = '${CoinReasons.ARTICLE_READ}'),
              (SELECT count(*) FROM referral_claims WHERE inviter_user_id = $u),
              (SELECT count(*) FROM partner_links WHERE owner_id = $u AND accepted_at IS NOT NULL),
              (SELECT count(*) FROM consultation_sessions WHERE patient_id = $u AND opened_at IS NOT NULL)
                + (SELECT count(*) FROM appointments WHERE user_id = $u),
              (SELECT count(*) FROM community_posts WHERE user_id = $u AND status = 'visible'),
              (SELECT count(DISTINCT log_date) FROM daily_symptoms WHERE user_id = $u),
              (SELECT count(*) FROM daily_logs WHERE user_id = $u
                 AND (mood IS NOT NULL OR energy IS NOT NULL OR stress IS NOT NULL)),
              (SELECT coalesce(sum(duration_seconds), 0) / 60 FROM mind_practices WHERE user_id = $u),
              (SELECT coalesce(sum(used), 0) FROM feature_usage_daily WHERE user_id = $u AND feature_key = '${FeatureKeys.FOOD_SCAN}'),
              (SELECT count(*) FROM wearable_connections WHERE user_id = $u)
                + (SELECT CASE WHEN EXISTS (SELECT 1 FROM health_samples WHERE user_id = $u) THEN 1 ELSE 0 END),
              (SELECT coalesce(sum(water_ml), 0) / 1000 FROM daily_logs WHERE user_id = $u),
              (SELECT count(*) FROM ai_usage_log WHERE user_id = $u AND feature = '${FeatureKeys.AI_CHAT}' AND outcome = 'ok'),
              (SELECT count(*) FROM profile_shares WHERE user_id = $u),
              (SELECT count(*) FROM community_comments WHERE user_id = $u AND status = 'visible'),
              (SELECT count(*) FROM community_post_likes l JOIN community_posts p ON p.id = l.post_id
                 WHERE p.user_id = $u AND l.user_id <> $u),
              (SELECT count(*) FROM shop_redemptions WHERE user_id = $u AND status <> 'cancelled'),
              (SELECT coalesce(sum(amount), 0) FROM coin_ledger
                 WHERE user_id = $u AND amount > 0 AND reason <> '${CoinReasons.ADMIN_ADJUSTMENT}')
        """.trimIndent()
        val result = HashMap<String, Int>()
        exec(sql) { rows ->
            if (rows.next()) {
                result[Badges.FIRST_STEP] = rows.getInt(1)
                result[Badges.LOYAL] = rows.getInt(1)
                result[Badges.STREAK] = rows.getInt(2)
                result[Badges.CYCLE] = rows.getInt(3)
                result[Badges.DAILY_LOG] = rows.getInt(4)
                result[Badges.WATER] = rows.getInt(5)
                result[Badges.MEDS] = rows.getInt(6)
                result[Badges.MEALS] = rows.getInt(7)
                result[Badges.JOURNAL] = rows.getInt(8)
                result[Badges.MIND] = rows.getInt(9)
                result[Badges.READER] = rows.getInt(10)
                result[Badges.FRIENDS] = rows.getInt(11)
                result[Badges.PARTNER] = rows.getInt(12)
                result[Badges.DOCTOR] = rows.getInt(13)
                result[Badges.COMMUNITY] = rows.getInt(14)
                result[Badges.SYMPTOMS] = rows.getInt(15)
                result[Badges.MOOD] = rows.getInt(16)
                result[Badges.CALM_MINUTES] = rows.getInt(17)
                result[Badges.SCANNER] = rows.getInt(18)
                result[Badges.DEVICES] = rows.getInt(19)
                result[Badges.HYDRO] = rows.getInt(20)
                result[Badges.CURIOUS] = rows.getInt(21)
                result[Badges.SHARE] = rows.getInt(22)
                result[Badges.HELPER] = rows.getInt(23)
                result[Badges.LOVED] = rows.getInt(24)
                result[Badges.SHOPPER] = rows.getInt(25)
                result[Badges.GARDENER] = rows.getLong(26).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }
        result
    }

    data class EarnedTier(val badge: String, val tier: Int, val earnedAt: Instant, val seen: Boolean)

    suspend fun earned(userId: Uuid): List<EarnedTier> = dbQuery {
        UserBadges.selectAll()
            .where { UserBadges.userId eq userId }
            .map {
                EarnedTier(
                    badge = it[UserBadges.badge],
                    tier = it[UserBadges.tier],
                    earnedAt = it[UserBadges.earnedAt].toKotlinInstant(),
                    seen = it[UserBadges.seenAt] != null,
                )
            }
    }

    /**
     * Writes a reached tier. Returns false when it was already there — two reads of the
     * board racing each other cannot both celebrate (or both pay) the same tier.
     */
    suspend fun reach(userId: Uuid, badge: String, tier: Int, seen: Boolean): Boolean = dbQuery {
        val at = now().toOffsetDateTime()
        UserBadges.insertIgnore {
            it[UserBadges.userId] = userId
            it[UserBadges.badge] = badge
            it[UserBadges.tier] = tier
            it[earnedAt] = at
            it[seenAt] = if (seen) at else null
        }.insertedCount > 0
    }

    /**
     * The badge each of [userIds] wears, at the highest tier she has of it. A worn key
     * with no earned tier (never written that way, but cheap to guard) is left out.
     */
    suspend fun wornFor(userIds: Collection<Uuid>): Map<Uuid, WornBadge> {
        val ids = userIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return dbQuery {
            // Parsed Uuids, so the literal list cannot carry anything but ids.
            val list = ids.joinToString(",") { "'$it'::uuid" }
            val sql = """
                SELECT w.user_id, w.badge, max(b.tier)
                FROM user_worn_badge w
                JOIN user_badges b ON b.user_id = w.user_id AND b.badge = w.badge
                WHERE w.user_id IN ($list)
                GROUP BY w.user_id, w.badge
            """.trimIndent()
            val result = HashMap<Uuid, WornBadge>()
            exec(sql) { rows ->
                while (rows.next()) {
                    val key = rows.getString(2)
                    val max = Badges.tiersOf(key).size
                    if (max == 0) continue
                    result[Uuid.parse(rows.getString(1))] = WornBadge(key, rows.getInt(3).coerceAtMost(max), max)
                }
            }
            result
        }
    }

    suspend fun worn(userId: Uuid): String? = dbQuery {
        UserWornBadge.selectAll().where { UserWornBadge.userId eq userId }
            .firstOrNull()?.get(UserWornBadge.badge)
    }

    /** Puts a badge on, or takes it off with a null [badge]. */
    suspend fun wear(userId: Uuid, badge: String?) = dbQuery {
        if (badge == null) {
            UserWornBadge.deleteWhere { UserWornBadge.userId eq userId }
        } else {
            UserWornBadge.upsert {
                it[UserWornBadge.userId] = userId
                it[UserWornBadge.badge] = badge
                it[updatedAt] = now().toOffsetDateTime()
            }
        }
    }

    /** Marks unseen tiers as celebrated; an empty [badges] means all of them. */
    suspend fun markSeen(userId: Uuid, badges: List<String>): Int = dbQuery {
        UserBadges.update({
            (UserBadges.userId eq userId) and UserBadges.seenAt.isNull() and
                (if (badges.isEmpty()) org.jetbrains.exposed.v1.core.Op.TRUE else UserBadges.badge inList badges)
        }) {
            it[seenAt] = now().toOffsetDateTime()
        }
    }
}
