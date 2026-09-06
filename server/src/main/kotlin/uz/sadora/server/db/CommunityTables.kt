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

/** The two states content can be in. Stored lowercase, like every other enum. */
enum class ContentStatus { VISIBLE, HIDDEN }
