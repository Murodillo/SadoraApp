package org.example.project.data

import kotlin.time.Clock
import kotlin.time.Instant
import org.example.project.model.CommunityComment
import org.example.project.model.CommunityPost
import org.example.project.model.CommunityTopic
import org.example.project.model.Fmt
import uz.sadora.contract.CommunityComment as WireComment
import uz.sadora.contract.CommunityPost as WirePost
import uz.sadora.contract.CommunityTopic as WireTopic

/**
 * Wire posts onto the store's posts.
 *
 * The store counts her own like on top of [CommunityPost.likes], so the server's total
 * — which already includes hers — has it taken back off here. Doing it in one place is
 * what keeps a like from being counted twice the moment the feed refreshes.
 */
fun WirePost.toAppPost(now: Instant = Clock.System.now()): CommunityPost = CommunityPost(
    id = id,
    alias = alias,
    tint = tint,
    topic = topic.toAppTopic(),
    ago = Fmt.ago(createdAt, now),
    body = body,
    likes = likeCount - (if (liked) 1 else 0),
    comments = emptyList(),
    commentCount = commentCount,
    isMine = isMine,
)

fun WireComment.toAppComment(now: Instant = Clock.System.now()): CommunityComment = CommunityComment(
    alias = alias,
    tint = tint,
    ago = Fmt.ago(createdAt, now),
    body = body,
    isMine = isMine,
)

fun WireTopic.toAppTopic(): CommunityTopic = when (this) {
    WireTopic.CYCLE -> CommunityTopic.Cycle
    WireTopic.PREGNANCY -> CommunityTopic.Pregnancy
    WireTopic.WELLBEING -> CommunityTopic.Wellbeing
    WireTopic.BODY -> CommunityTopic.Body
}

/** [CommunityTopic.All] is a filter, not a room, so it has no wire form. */
fun CommunityTopic.toWireTopic(): WireTopic? = when (this) {
    CommunityTopic.All -> null
    CommunityTopic.Cycle -> WireTopic.CYCLE
    CommunityTopic.Pregnancy -> WireTopic.PREGNANCY
    CommunityTopic.Wellbeing -> WireTopic.WELLBEING
    CommunityTopic.Body -> WireTopic.BODY
}
