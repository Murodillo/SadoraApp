package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * The secret chat's tables.
 *
 * Their own file for the same reason [HealthTables] has one: the admin code that
 * moderates the feed is allowed to read [CommunityPosts] and [CommunityComments], but
 * [CommunityIdentities] is the only join from a post back to an account and the admin
 * side must never import it. Keeping the boundary visible in the import list is what
 * makes it reviewable.
 */

object CommunityIdentities : Table("community_identities") {
    val userId = uuid("user_id").references(Users.id)
    val alias = text("alias")
    val tint = integer("tint")
    val createdAt = timestampWithTimeZone("created_at")
    /** A line under the alias, 160 characters at most. */
    val bio = text("bio").nullable()
    /** Whether strangers may open a conversation with her. */
    val dmOpen = bool("dm_open")

    override val primaryKey = PrimaryKey(userId)
}

object CommunityPosts : Table("community_posts") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val topic = text("topic")
    val body = text("body")
    val status = text("status")
    val hiddenReason = text("hidden_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    /** Set when written as a verified doctor; see [DoctorProfiles]. */
    val doctorId = uuid("doctor_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CommunityComments : Table("community_comments") {
    val id = uuid("id")
    val postId = uuid("post_id").references(CommunityPosts.id)
    val userId = uuid("user_id").references(Users.id)
    val body = text("body")
    val status = text("status")
    val hiddenReason = text("hidden_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val doctorId = uuid("doctor_id").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CommunityPostLikes : Table("community_post_likes") {
    val postId = uuid("post_id").references(CommunityPosts.id)
    val userId = uuid("user_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(postId, userId)
}

object CommunityPostSaves : Table("community_post_saves") {
    val postId = uuid("post_id").references(CommunityPosts.id)
    val userId = uuid("user_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(postId, userId)
}

object CommunityReports : Table("community_reports") {
    val id = uuid("id")
    val reporterId = uuid("reporter_id").references(Users.id)
    val postId = uuid("post_id").references(CommunityPosts.id).nullable()
    val commentId = uuid("comment_id").references(CommunityComments.id).nullable()
    val messageId = uuid("message_id").references(CommunityMessages.id).nullable()
    val reason = text("reason")
    val note = text("note").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val resolvedAt = timestampWithTimeZone("resolved_at").nullable()
    val resolvedBy = uuid("resolved_by").nullable()
    val resolution = text("resolution").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CommunityRestrictions : Table("community_restrictions") {
    val userId = uuid("user_id").references(Users.id)
    val reason = text("reason")
    val until = timestampWithTimeZone("until").nullable()
    val createdBy = uuid("created_by").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(userId)
}

/** One private thread between two accounts, ids in a fixed order so a pair is one row. */
object CommunityConversations : Table("community_conversations") {
    val id = uuid("id")
    val userA = uuid("user_a").references(Users.id)
    val userB = uuid("user_b").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at")
    val lastMessageAt = timestampWithTimeZone("last_message_at")
    val aReadAt = timestampWithTimeZone("a_read_at").nullable()
    val bReadAt = timestampWithTimeZone("b_read_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CommunityMessages : Table("community_messages") {
    val id = uuid("id")
    val conversationId = uuid("conversation_id").references(CommunityConversations.id)
    val senderId = uuid("sender_id").references(Users.id)
    val body = text("body")
    val status = text("status")
    val hiddenReason = text("hidden_reason").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}

object CommunityBlocks : Table("community_blocks") {
    val blockerId = uuid("blocker_id").references(Users.id)
    val blockedId = uuid("blocked_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(blockerId, blockedId)
}

/** The two states content can be in. Stored lowercase, like every other enum. */
enum class ContentStatus { VISIBLE, HIDDEN }
