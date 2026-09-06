package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/**
 * What AI answers cost.
 *
 * Its own file so the absence is visible: there is no question column and no answer
 * column, and there is nowhere in this table to put one. The chat is not stored, and a
 * cost log is the obvious place for that promise to erode by accident.
 */
object AiUsageLog : Table("ai_usage_log") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id).nullable()
    val feature = text("feature")
    /** `answerSource`, because Exposed's ColumnSet already has a `source`. */
    val answerSource = text("source")
    val model = text("model").nullable()
    val promptTokens = integer("prompt_tokens").nullable()
    val completionTokens = integer("completion_tokens").nullable()
    val costMicros = long("cost_micros")
    val latencyMs = integer("latency_ms").nullable()
    val outcome = text("outcome")
    val errorCode = text("error_code").nullable()
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(id)
}
