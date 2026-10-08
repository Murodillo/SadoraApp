package uz.sadora.server.doctor

import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.DoctorBadges
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.DoctorBadgeTiers
import uz.sadora.server.db.dbQuery
import uz.sadora.server.rewards.BadgeRepository.EarnedTier

/**
 * The counts behind a doctor's badges, and the tiers she has reached.
 *
 * Like a woman's board, the counts are read live from the tables that already hold the
 * work — her answers, consultations, notes — and nothing here reads money: a badge for
 * what she earned would reward the price, not the care.
 */
class DoctorBadgeRepository {

    /** Her count for every badge in [DoctorBadges.catalogue], in one round trip. */
    suspend fun counts(doctorId: Uuid, userId: Uuid): Map<String, Int> = dbQuery {
        // Parsed Uuids, so interpolating them cannot inject anything.
        val d = "'$doctorId'::uuid"
        val u = "'$userId'::uuid"
        val sql = """
            SELECT
              (SELECT CASE WHEN verified_at IS NOT NULL THEN 1 ELSE 0 END FROM doctor_profiles WHERE id = $d),
              (SELECT CASE WHEN photo_updated_at IS NOT NULL THEN 1 ELSE 0 END FROM doctor_profiles WHERE id = $d),
              (SELECT count(*) FROM community_comments WHERE doctor_id = $d AND status = 'visible'),
              (SELECT count(*) FROM community_posts WHERE doctor_id = $d AND status = 'visible'),
              (SELECT count(*) FROM consultation_sessions WHERE doctor_id = $d AND opened_at IS NOT NULL),
              (SELECT count(DISTINCT patient_id) FROM consultation_sessions WHERE doctor_id = $d AND opened_at IS NOT NULL),
              (SELECT count(*) FROM consultation_sessions WHERE doctor_id = $d AND opened_at IS NOT NULL
                 AND first_reply_at IS NOT NULL
                 AND first_reply_at - opened_at <= interval '${DoctorBadges.FAST_REPLY_MINUTES} minutes'),
              (SELECT count(*) FROM community_messages m JOIN community_conversations c ON c.id = m.conversation_id
                 WHERE c.doctor_id = $d AND m.sender_id = $u AND m.status = 'visible'),
              (SELECT count(*) FROM consultation_sessions WHERE doctor_id = $d AND rating IS NOT NULL),
              (SELECT count(*) FROM consultation_sessions WHERE doctor_id = $d AND rating = 5),
              (SELECT count(DISTINCT entity_id) FROM audit_log
                 WHERE actor_id = $u AND action = '${AuditActions.CONSULTATION_RECORD_VIEWED}'),
              (SELECT count(*) FROM doctor_patient_notes WHERE doctor_id = $d),
              (SELECT count(*) FROM doctor_quick_replies WHERE doctor_id = $d),
              (SELECT count(*) FROM community_post_likes l JOIN community_posts p ON p.id = l.post_id
                 WHERE p.doctor_id = $d AND l.user_id <> $u),
              (SELECT coalesce(floor(extract(epoch FROM now() - verified_at) / 86400), 0)::int
                 FROM doctor_profiles WHERE id = $d)
        """.trimIndent()
        val keys = listOf(
            DoctorBadges.VERIFIED, DoctorBadges.PHOTO, DoctorBadges.ANSWERS, DoctorBadges.POSTS,
            DoctorBadges.CONSULTS, DoctorBadges.PATIENTS, DoctorBadges.FAST_REPLY, DoctorBadges.MESSAGES,
            DoctorBadges.RATED, DoctorBadges.FIVE_STARS, DoctorBadges.RECORDS, DoctorBadges.NOTES,
            DoctorBadges.QUICK_REPLIES, DoctorBadges.THANKED, DoctorBadges.TENURE,
        )
        val result = HashMap<String, Int>()
        exec(sql) { rows ->
            if (rows.next()) keys.forEachIndexed { i, key -> result[key] = rows.getInt(i + 1) }
        }
        result
    }

    suspend fun earned(doctorId: Uuid): List<EarnedTier> = dbQuery {
        DoctorBadgeTiers.selectAll()
            .where { DoctorBadgeTiers.doctorId eq doctorId }
            .map {
                EarnedTier(
                    badge = it[DoctorBadgeTiers.badge],
                    tier = it[DoctorBadgeTiers.tier],
                    earnedAt = it[DoctorBadgeTiers.earnedAt].toKotlinInstant(),
                    seen = it[DoctorBadgeTiers.seenAt] != null,
                )
            }
    }

    /** Writes a reached tier; false when a racing read already wrote it. */
    suspend fun reach(doctorId: Uuid, badge: String, tier: Int): Boolean = dbQuery {
        DoctorBadgeTiers.insertIgnore {
            it[DoctorBadgeTiers.doctorId] = doctorId
            it[DoctorBadgeTiers.badge] = badge
            it[DoctorBadgeTiers.tier] = tier
            it[earnedAt] = now().toOffsetDateTime()
            it[seenAt] = null
        }.insertedCount > 0
    }

    /** Marks unseen tiers as celebrated; an empty [badges] means all of them. */
    suspend fun markSeen(doctorId: Uuid, badges: List<String>): Int = dbQuery {
        DoctorBadgeTiers.update({
            (DoctorBadgeTiers.doctorId eq doctorId) and DoctorBadgeTiers.seenAt.isNull() and
                (if (badges.isEmpty()) org.jetbrains.exposed.v1.core.Op.TRUE else DoctorBadgeTiers.badge inList badges)
        }) {
            it[seenAt] = now().toOffsetDateTime()
        }
    }
}
