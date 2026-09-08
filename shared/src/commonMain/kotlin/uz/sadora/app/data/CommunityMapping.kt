package uz.sadora.app.data

import uz.sadora.app.model.CommunityComment
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.CommunityTopic
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
fun WirePost.toAppPost(): CommunityPost = CommunityPost(
    id = id,
    alias = alias,
    tint = tint,
    topic = topic.toAppTopic(),
    createdAt = createdAt,
    body = body,
    likes = likeCount - (if (liked) 1 else 0),
    comments = emptyList(),
    commentCount = commentCount,
    isMine = isMine,
)

fun WireComment.toAppComment(): CommunityComment = CommunityComment(
    alias = alias,
    tint = tint,
    createdAt = createdAt,
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
