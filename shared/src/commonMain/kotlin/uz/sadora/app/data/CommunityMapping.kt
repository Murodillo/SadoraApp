package uz.sadora.app.data

import uz.sadora.app.model.AliasProfile
import uz.sadora.app.model.CommunityBadge
import uz.sadora.app.model.CommunityComment
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.model.Conversation
import uz.sadora.app.model.DirectMessage
import uz.sadora.contract.CommunityBadge as WireBadge
import uz.sadora.contract.CommunityComment as WireComment
import uz.sadora.contract.CommunityPost as WirePost
import uz.sadora.contract.CommunityProfile as WireProfile
import uz.sadora.contract.CommunityTopic as WireTopic
import uz.sadora.contract.Conversation as WireConversation
import uz.sadora.contract.DirectMessage as WireMessage

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
    badges = badges.map { it.toAppBadge() },
)

fun WireComment.toAppComment(): CommunityComment = CommunityComment(
    alias = alias,
    tint = tint,
    createdAt = createdAt,
    body = body,
    isMine = isMine,
    badges = badges.map { it.toAppBadge() },
)

fun WireBadge.toAppBadge(): CommunityBadge = when (this) {
    WireBadge.NEWCOMER -> CommunityBadge.Newcomer
    WireBadge.EARLY -> CommunityBadge.Early
    WireBadge.WRITER -> CommunityBadge.Writer
    WireBadge.HELPER -> CommunityBadge.Helper
    WireBadge.LOVED -> CommunityBadge.Loved
    WireBadge.VETERAN -> CommunityBadge.Veteran
}

fun WireProfile.toAppProfile(): AliasProfile = AliasProfile(
    alias = alias,
    tint = tint,
    bio = bio,
    badges = badges.map { it.toAppBadge() },
    postCount = postCount,
    commentCount = commentCount,
    likesReceived = likesReceived,
    memberSince = memberSince,
    isMe = isMe,
    canMessage = canMessage,
    blocked = blocked,
    posts = posts.map { it.toAppPost() },
)

fun WireConversation.toAppConversation(): Conversation = Conversation(
    id = id,
    alias = alias,
    tint = tint,
    badges = badges.map { it.toAppBadge() },
    lastMessage = lastMessage,
    lastMessageAt = lastMessageAt,
    unread = unread,
    blocked = blocked,
)

fun WireMessage.toAppMessage(): DirectMessage = DirectMessage(id, body, createdAt, isMine)

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
