package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

/**
 * Profile shares, as V21 created them.
 *
 * Only the token's hash is here. The summary a share opens is built from the health
 * tables at read time and never written down, which is why this file has one object.
 */
object ProfileShares : Table("profile_shares") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val tokenHash = text("token_hash")
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val viewCount = integer("view_count")
    val lastViewedAt = timestampWithTimeZone("last_viewed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** Yaqinim links, as V36 created them. Like a share, no health data lives here. */
object PartnerLinks : Table("partner_links") {
    val id = uuid("id")
    val ownerId = uuid("owner_id").references(Users.id)
    val partnerId = uuid("partner_id").references(Users.id).nullable()
    val status = text("status")
    val relation = text("relation")
    val codeHash = text("code_hash").nullable()
    val codeExpiresAt = timestampWithTimeZone("code_expires_at").nullable()
    val permCycle = bool("perm_cycle")
    val permFertile = bool("perm_fertile")
    val permMood = bool("perm_mood")
    val permSymptoms = bool("perm_symptoms")
    val permPregnancy = bool("perm_pregnancy")
    val permAppointments = bool("perm_appointments")
    val permCare = bool("perm_care")
    val createdAt = timestampWithTimeZone("created_at")
    val acceptedAt = timestampWithTimeZone("accepted_at").nullable()
    val approvedAt = timestampWithTimeZone("approved_at").nullable()
    val pausedAt = timestampWithTimeZone("paused_at").nullable()
    val endedAt = timestampWithTimeZone("ended_at").nullable()
    val endedBy = text("ended_by").nullable()
    val lastViewedAt = timestampWithTimeZone("last_viewed_at").nullable()
    val viewCount = integer("view_count")

    override val primaryKey = PrimaryKey(id)
}

object PartnerMessagesTable : Table("partner_messages") {
    val id = uuid("id")
    val linkId = uuid("link_id").references(PartnerLinks.id)
    val senderId = uuid("sender_id").references(Users.id)
    val kind = text("kind")
    val body = text("body").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val readAt = timestampWithTimeZone("read_at").nullable()

    override val primaryKey = PrimaryKey(id)
}

private val permissionsJson = kotlinx.serialization.json.Json { encodeDefaults = true; ignoreUnknownKeys = true }

object PartnerWebLinks : Table("partner_web_links") {
    val id = uuid("id")
    val ownerId = uuid("owner_id").references(Users.id)
    val tokenHash = text("token_hash")
    val permissions = jsonb<uz.sadora.contract.PartnerPermissions>("permissions", permissionsJson)
    val createdAt = timestampWithTimeZone("created_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
    val viewCount = integer("view_count")
    val lastViewedAt = timestampWithTimeZone("last_viewed_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
