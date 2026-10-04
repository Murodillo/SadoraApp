package uz.sadora.server.notify

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.contract.FrequencyCaps
import uz.sadora.contract.NotificationCategory
import uz.sadora.contract.NotificationMessage
import uz.sadora.contract.NotificationSettings
import uz.sadora.contract.NotificationStatus
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.NotificationCaps
import uz.sadora.server.db.NotificationOutbox
import uz.sadora.server.db.NotificationTemplates
import uz.sadora.server.db.UserNotificationSettings
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.dbValue
import uz.sadora.server.db.enumFromDb

data class TemplateRecord(
    val key: String,
    val language: String,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val active: Boolean,
)

class NotificationRepository {

    // ---------------------------------------------------------------- settings

    suspend fun settingsOf(userId: Uuid): NotificationSettings = dbQuery {
        UserNotificationSettings.selectAll()
            .where { UserNotificationSettings.userId eq userId }
            .singleOrNull()
            ?.let {
                NotificationSettings(
                    enabled = it[UserNotificationSettings.enabled],
                    categories = it[UserNotificationSettings.categories].parseCategories(),
                    quietFrom = it[UserNotificationSettings.quietFrom],
                    quietUntil = it[UserNotificationSettings.quietUntil],
                )
            }
            ?: NotificationSettings()
    }

    suspend fun saveSettings(userId: Uuid, settings: NotificationSettings): Unit = dbQuery {
        UserNotificationSettings.upsert(UserNotificationSettings.userId) {
            it[UserNotificationSettings.userId] = userId
            it[enabled] = settings.enabled
            it[categories] = settings.categories.entries.joinToString(",") { entry ->
                "${entry.key.dbValue()}:${entry.value}"
            }
            it[quietFrom] = settings.quietFrom
            it[quietUntil] = settings.quietUntil
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    // ---------------------------------------------------------------- caps

    suspend fun caps(): FrequencyCaps = dbQuery {
        NotificationCaps.selectAll().singleOrNull()?.let {
            FrequencyCaps(it[NotificationCaps.maxPerDay], it[NotificationCaps.maxPerWeek])
        } ?: FrequencyCaps()
    }

    suspend fun saveCaps(caps: FrequencyCaps): Unit = dbQuery {
        NotificationCaps.update({ NotificationCaps.id eq 1 }) {
            it[maxPerDay] = caps.maxPerDay
            it[maxPerWeek] = caps.maxPerWeek
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    // ---------------------------------------------------------------- templates

    suspend fun templates(): List<TemplateRecord> = dbQuery {
        NotificationTemplates.selectAll()
            .orderBy(NotificationTemplates.key to SortOrder.ASC, NotificationTemplates.language to SortOrder.ASC)
            .map { it.toTemplate() }
    }

    /** Falls back to Uzbek, then to any language, so a missing translation still sends. */
    suspend fun template(key: String, language: String): TemplateRecord? = dbQuery {
        val rows = NotificationTemplates.selectAll()
            .where { (NotificationTemplates.key eq key) and (NotificationTemplates.active eq true) }
            .map { it.toTemplate() }
        rows.firstOrNull { it.language == language }
            ?: rows.firstOrNull { it.language == "uz" }
            ?: rows.firstOrNull()
    }

    suspend fun saveTemplate(record: TemplateRecord): Unit = dbQuery {
        NotificationTemplates.upsert(NotificationTemplates.key, NotificationTemplates.language) {
            it[key] = record.key
            it[language] = record.language
            it[category] = record.category.dbValue()
            it[title] = record.title
            it[body] = record.body
            it[active] = record.active
            it[updatedAt] = now().toOffsetDateTime()
        }
    }

    // ---------------------------------------------------------------- outbox

    /**
     * Queues a notification, ignoring a duplicate.
     *
     * The unique dedupe key is what makes the scheduler safe to run every minute and
     * safe to restart mid-tick: re-queuing the same reminder is a no-op rather than a
     * second buzz. Returns false when the row already existed.
     */
    suspend fun enqueue(
        userId: Uuid,
        category: NotificationCategory,
        title: String,
        body: String,
        scheduledFor: Instant,
        dedupeKey: String,
        status: NotificationStatus,
        suppressedReason: String?,
        /** `client` or `doctor`: whose devices ring. */
        targetApp: String = TARGET_CLIENT,
        /** Where a tap lands, e.g. `sadora://conversation/<id>`. */
        link: String? = null,
    ): Boolean = dbQuery {
        NotificationOutbox.insertIgnore {
            it[NotificationOutbox.targetApp] = targetApp
            it[NotificationOutbox.link] = link
            it[id] = Uuid.random()
            it[NotificationOutbox.userId] = userId
            it[NotificationOutbox.category] = category.dbValue()
            it[NotificationOutbox.title] = title
            it[NotificationOutbox.body] = body
            it[NotificationOutbox.scheduledFor] = scheduledFor.toOffsetDateTime()
            it[NotificationOutbox.status] = status.dbValue()
            it[NotificationOutbox.suppressedReason] = suppressedReason
            it[NotificationOutbox.dedupeKey] = dedupeKey
            it[createdAt] = now().toOffsetDateTime()
        }.insertedCount > 0
    }

    suspend fun due(limit: Int): List<OutboxRecord> = dbQuery {
        NotificationOutbox.selectAll()
            .where {
                (NotificationOutbox.status eq NotificationStatus.QUEUED.dbValue()) and
                    (NotificationOutbox.scheduledFor lessEq now().toOffsetDateTime())
            }
            .orderBy(NotificationOutbox.scheduledFor to SortOrder.ASC)
            .limit(limit)
            .map { it.toOutbox() }
    }

    /** [due], for one account: what the immediate pass after a message reads. */
    suspend fun dueFor(userId: Uuid, limit: Int): List<OutboxRecord> = dbQuery {
        NotificationOutbox.selectAll()
            .where {
                (NotificationOutbox.userId eq userId) and
                    (NotificationOutbox.status eq NotificationStatus.QUEUED.dbValue()) and
                    (NotificationOutbox.scheduledFor lessEq now().toOffsetDateTime())
            }
            .orderBy(NotificationOutbox.scheduledFor to SortOrder.ASC)
            .limit(limit)
            .map { it.toOutbox() }
    }

    suspend fun markSent(id: Uuid): Unit = dbQuery {
        NotificationOutbox.update({ NotificationOutbox.id eq id }) {
            it[status] = NotificationStatus.SENT.dbValue()
            it[sentAt] = now().toOffsetDateTime()
        }
    }

    suspend fun markFailed(id: Uuid, reason: String): Unit = dbQuery {
        NotificationOutbox.update({ NotificationOutbox.id eq id }) {
            it[status] = NotificationStatus.FAILED.dbValue()
            it[suppressedReason] = reason.take(200)
        }
    }

    /** Sent notifications only — suppressed ones never reached her, so they do not count. */
    suspend fun sentCount(userId: Uuid, since: Instant): Int = dbQuery {
        NotificationOutbox.selectAll()
            .where {
                (NotificationOutbox.userId eq userId) and
                    (NotificationOutbox.status eq NotificationStatus.SENT.dbValue()) and
                    (NotificationOutbox.scheduledFor greaterEq since.toOffsetDateTime())
            }
            .count()
            .toInt()
    }

    /**
     * Her notifications, newest first: the latest [limit], or the [limit] queued before
     * her notification [beforeId]. Null when [beforeId] is not one of hers.
     */
    suspend fun history(userId: Uuid, limit: Int, beforeId: Uuid? = null): List<NotificationMessage>? = dbQuery {
        var query = NotificationOutbox.selectAll().where { NotificationOutbox.userId eq userId }
        if (beforeId != null) {
            val at = NotificationOutbox.select(NotificationOutbox.createdAt)
                .where { (NotificationOutbox.id eq beforeId) and (NotificationOutbox.userId eq userId) }
                .singleOrNull()
                ?.get(NotificationOutbox.createdAt)
                ?: return@dbQuery null
            // A batch queued together shares a timestamp; the id breaks the tie the same way the order does.
            query = query.andWhere {
                (NotificationOutbox.createdAt less at) or
                    ((NotificationOutbox.createdAt eq at) and (NotificationOutbox.id less beforeId))
            }
        }
        query.orderBy(NotificationOutbox.createdAt to SortOrder.DESC, NotificationOutbox.id to SortOrder.DESC)
            .limit(limit)
            .map { it.toOutbox().toMessage() }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toTemplate() = TemplateRecord(
        key = this[NotificationTemplates.key],
        language = this[NotificationTemplates.language],
        category = enumFromDb(this[NotificationTemplates.category], NotificationCategory.SYSTEM),
        title = this[NotificationTemplates.title],
        body = this[NotificationTemplates.body],
        active = this[NotificationTemplates.active],
    )

    private fun org.jetbrains.exposed.v1.core.ResultRow.toOutbox() = OutboxRecord(
        id = this[NotificationOutbox.id],
        userId = this[NotificationOutbox.userId],
        category = enumFromDb(this[NotificationOutbox.category], NotificationCategory.SYSTEM),
        title = this[NotificationOutbox.title],
        body = this[NotificationOutbox.body],
        scheduledFor = this[NotificationOutbox.scheduledFor].toKotlinInstant(),
        status = enumFromDb(this[NotificationOutbox.status], NotificationStatus.QUEUED),
        sentAt = this[NotificationOutbox.sentAt]?.toKotlinInstant(),
        suppressedReason = this[NotificationOutbox.suppressedReason],
        targetApp = this[NotificationOutbox.targetApp],
        link = this[NotificationOutbox.link],
    )

    private fun String.parseCategories(): Map<NotificationCategory, Boolean> =
        split(',').mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size != 2) return@mapNotNull null
            val category = enumFromDb<NotificationCategory>(parts[0].trim()) ?: return@mapNotNull null
            category to parts[1].trim().toBooleanStrictOrNull().let { it ?: return@mapNotNull null }
        }.toMap()
}

data class OutboxRecord(
    val id: Uuid,
    val userId: Uuid,
    val category: NotificationCategory,
    val title: String,
    val body: String,
    val scheduledFor: Instant,
    val status: NotificationStatus,
    val sentAt: Instant?,
    val suppressedReason: String?,
    /** `client` or `doctor`: the app whose devices ring. */
    val targetApp: String = TARGET_CLIENT,
    /** Where a tap lands; null opens the app. */
    val link: String? = null,
) {
    fun toMessage() = NotificationMessage(
        id = id.toString(),
        category = category,
        title = title,
        body = body,
        scheduledFor = scheduledFor,
        status = status,
        sentAt = sentAt,
        suppressedReason = suppressedReason,
    )
}

/** The women's app: every notification unless it is a doctor's work. */
const val TARGET_CLIENT = "client"

/** Sadora Doctor: what a doctor receives as a doctor. */
const val TARGET_DOCTOR = "doctor"
