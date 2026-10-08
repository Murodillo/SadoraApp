package uz.sadora.server.doctor

import kotlin.uuid.Uuid
import uz.sadora.contract.BadgeBoard
import uz.sadora.contract.BadgeState
import uz.sadora.contract.BadgeUnlock
import uz.sadora.contract.DoctorBadges
import uz.sadora.contract.DoctorStatus
import uz.sadora.server.core.ForbiddenException

/**
 * A verified doctor's badge board, awarding on the way: the counts are taken on read,
 * any tier newly crossed is written and comes back in [BadgeBoard.unseen] until her app
 * says it has played the unlock. No Gul, and nothing to wear — the check mark is what
 * she wears.
 */
class DoctorBadgeService(
    private val doctors: DoctorRepository,
    private val repository: DoctorBadgeRepository = DoctorBadgeRepository(),
) {

    suspend fun board(userId: Uuid): BadgeBoard {
        val doctor = requireApproved(userId)
        val counts = repository.counts(doctor.id, doctor.userId)
        val reached = repository.earned(doctor.id).groupBy { it.badge }
        for ((key, _) in DoctorBadges.catalogue) {
            val tier = DoctorBadges.tierFor(key, counts[key] ?: 0)
            val have = reached[key].orEmpty().maxOfOrNull { it.tier } ?: 0
            for (t in (have + 1)..tier) repository.reach(doctor.id, key, t)
        }
        val earned = repository.earned(doctor.id)
        return BadgeBoard(
            canWear = false,
            badges = DoctorBadges.catalogue.map { (key, thresholds) ->
                val mine = earned.filter { it.badge == key }
                val top = mine.maxByOrNull { it.tier }
                BadgeState(
                    key = key,
                    tier = top?.tier ?: 0,
                    thresholds = thresholds,
                    progress = counts[key] ?: 0,
                    earnedAt = top?.earnedAt,
                )
            },
            unseen = earned.filter { !it.seen }
                .sortedWith(compareBy({ it.earnedAt }, { it.tier }))
                .map {
                    BadgeUnlock(
                        key = it.badge,
                        tier = it.tier,
                        maxTier = DoctorBadges.tiersOf(it.badge).size,
                        earnedAt = it.earnedAt,
                    )
                },
        )
    }

    suspend fun markSeen(userId: Uuid, keys: List<String>) {
        val doctor = requireApproved(userId)
        val known = keys.filter { key -> DoctorBadges.catalogue.any { it.first == key } }
        // Only unknown keys asked for: nothing to mark, rather than "mark everything".
        if (keys.isNotEmpty() && known.isEmpty()) return
        repository.markSeen(doctor.id, known)
    }

    private suspend fun requireApproved(userId: Uuid): DoctorRecord =
        doctors.byUser(userId)?.takeIf { it.status == DoctorStatus.APPROVED }
            ?: throw ForbiddenException(message = "Faqat tasdiqlangan shifokorlar uchun")
}
