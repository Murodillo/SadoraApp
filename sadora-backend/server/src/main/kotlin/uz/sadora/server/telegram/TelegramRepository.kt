package uz.sadora.server.telegram

import kotlin.time.Instant
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import uz.sadora.server.core.now
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.TelegramLinkCodes
import uz.sadora.server.db.TelegramLinks
import uz.sadora.server.db.TelegramOffset
import uz.sadora.server.db.dbQuery

data class TelegramLinkRow(val userId: Uuid, val chatId: Long, val username: String?, val detailed: Boolean)

/** Her chat, the link codes, and the update offset. */
class TelegramRepository {

    suspend fun linkOf(userId: Uuid): TelegramLinkRow? = dbQuery {
        TelegramLinks.selectAll().where { TelegramLinks.userId eq userId }.singleOrNull()?.toRow()
    }

    suspend fun linkByChat(chatId: Long): TelegramLinkRow? = dbQuery {
        TelegramLinks.selectAll().where { TelegramLinks.chatId eq chatId }.singleOrNull()?.toRow()
    }

    /** Links [chatId] to [userId], taking it from any account it was linked to before. */
    suspend fun link(userId: Uuid, chatId: Long, username: String?) = dbQuery {
        TelegramLinks.deleteWhere { (TelegramLinks.chatId eq chatId) and (TelegramLinks.userId neq userId) }
        TelegramLinks.upsert(onUpdateExclude = listOf(TelegramLinks.detailed)) {
            it[TelegramLinks.userId] = userId
            it[TelegramLinks.chatId] = chatId
            it[TelegramLinks.username] = username
            it[detailed] = false
            it[linkedAt] = now().toOffsetDateTime()
        }
    }

    suspend fun unlink(userId: Uuid): Boolean = dbQuery {
        TelegramLinks.deleteWhere { TelegramLinks.userId eq userId } > 0
    }

    suspend fun setDetailed(userId: Uuid, detailed: Boolean): Boolean = dbQuery {
        TelegramLinks.update({ TelegramLinks.userId eq userId }) { it[TelegramLinks.detailed] = detailed } > 0
    }

    suspend fun linkedUsers(): List<TelegramLinkRow> = dbQuery {
        TelegramLinks.selectAll().map { it.toRow() }
    }

    suspend fun addCode(code: String, userId: Uuid, expiresAt: Instant) = dbQuery {
        TelegramLinkCodes.insert {
            it[TelegramLinkCodes.code] = code
            it[TelegramLinkCodes.userId] = userId
            it[TelegramLinkCodes.expiresAt] = expiresAt.toOffsetDateTime()
        }
    }

    /** The account behind a code still valid and unused; marks it used. */
    suspend fun redeem(code: String): Uuid? = dbQuery {
        val at = now().toOffsetDateTime()
        val row = TelegramLinkCodes.selectAll()
            .where { (TelegramLinkCodes.code eq code) and TelegramLinkCodes.usedAt.isNull() and (TelegramLinkCodes.expiresAt greater at) }
            .singleOrNull() ?: return@dbQuery null
        TelegramLinkCodes.update({ TelegramLinkCodes.code eq code }) { it[usedAt] = at }
        row[TelegramLinkCodes.userId]
    }

    suspend fun offset(): Long = dbQuery {
        TelegramOffset.selectAll().singleOrNull()?.get(TelegramOffset.nextUpdate) ?: 0L
    }

    suspend fun saveOffset(next: Long) = dbQuery {
        TelegramOffset.upsert {
            it[id] = 1
            it[nextUpdate] = next
        }
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toRow() = TelegramLinkRow(
        userId = this[TelegramLinks.userId],
        chatId = this[TelegramLinks.chatId],
        username = this[TelegramLinks.username],
        detailed = this[TelegramLinks.detailed],
    )
}
