package uz.sadora.server.community

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import uz.sadora.contract.CommunityBadge

/**
 * The badge rules, in one place and nowhere else.
 *
 * Every badge is a reading of her activity at the moment it is asked for. Nothing is
 * awarded and stored, so a deleted post takes its share of "writer" with it and there
 * is no table that could fall out of step with the posts.
 */
object CommunityBadges {
    const val NEWCOMER_DAYS = 7
    const val VETERAN_DAYS = 90
    const val EARLY_ALIASES = 500
    const val WRITER_POSTS = 5
    const val HELPER_COMMENTS = 20
    const val LOVED_LIKES = 50

    fun of(stats: ActivityStats, now: Instant): List<CommunityBadge> = buildList {
        val age = now - stats.memberSince
        if (age < NEWCOMER_DAYS.days) add(CommunityBadge.NEWCOMER)
        if (stats.early) add(CommunityBadge.EARLY)
        if (stats.posts >= WRITER_POSTS) add(CommunityBadge.WRITER)
        if (stats.comments >= HELPER_COMMENTS) add(CommunityBadge.HELPER)
        if (stats.likesReceived >= LOVED_LIKES) add(CommunityBadge.LOVED)
        if (age >= VETERAN_DAYS.days) add(CommunityBadge.VETERAN)
    }
}
