package uz.sadora.server.admin.pm

import java.io.File
import java.util.zip.GZIPOutputStream
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.db.dbQuery
import uz.sadora.server.plugins.AdminPrincipal

/**
 * The whole "Loyiha" section as one JSON file of rows, plus its pictures as they are.
 *
 * The rows are read with Postgres's own `json_agg` and come back with
 * `json_populate_recordset`, so the file follows the schema without a hand-written field
 * list. Pictures are not inlined: a wiki full of screenshots would make one string the size
 * of all of them. The daily job copies each picture file beside the snapshots once (they
 * never change), and tools/pm `backup` fetches them one by one. The server keeps 14 daily
 * snapshots under MEDIA_DIR/pm-backups. Tokens are left out: they are keys, and new ones
 * are cheap.
 *
 * A file comes back only into an empty section, which is what a lost database looks
 * like; it never merges into, or overwrites, work already there. Pictures follow through
 * [PmDocService.restoreImage]. Operators of the old database who do not exist in the new
 * one become "unknown" on the rows they touched.
 */
class PmBackupService(
    private val audit: AuditService,
    private val imageDir: File,
    private val backupDir: File,
) {
    private val logger = LoggerFactory.getLogger(PmBackupService::class.java)

    suspend fun snapshot(): String {
        val tables = dbQuery {
            TABLES.associateWith { table ->
                var rows = "[]"
                exec("SELECT coalesce(json_agg(t), '[]'::json)::text FROM $table t") { result -> if (result.next()) rows = result.getString(1) }
                Json.parseToJsonElement(rows)
            }
        }
        val snapshot = buildJsonObject {
            put("format", FORMAT)
            put("takenAt", now().toString())
            put("tables", JsonObject(tables))
        }
        return snapshot.toString()
    }

    suspend fun restore(raw: String, actor: AdminPrincipal, context: RequestContext) {
        val snapshot = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: throw ValidationException("backup", "Fayl zaxira emas")
        if (snapshot["format"]?.jsonPrimitive?.content != FORMAT.toString()) throw ValidationException("backup", "Zaxira formati tanish emas")
        val tables = snapshot["tables"]?.jsonObject ?: throw ValidationException("backup", "Zaxirada jadvallar yo'q")

        dbQuery {
            val occupied = listOf("pm_tasks", "pm_docs", "pm_epics", "pm_releases").filter { count(it) > 0 }
            if (occupied.isNotEmpty()) throw ConflictException("Loyiha bo'sh emas — zaxira faqat bo'sh bazaga tiklanadi")
            TABLES.forEach { table -> load(table, tables[table]?.toString() ?: "[]") }
            // Numbering carries on after the highest restored key; an empty section starts at SAD-1.
            exec(
                "SELECT CASE WHEN max(number) IS NULL THEN setval('pm_task_number_seq', 1, false) " +
                    "ELSE setval('pm_task_number_seq', max(number), true) END FROM pm_tasks",
            )
        }
        audit.recordPm(actor, AuditActions.PM_RESTORED, "pm", snapshot["takenAt"]?.jsonPrimitive?.content ?: "?", context)
    }

    /** Writes today's file unless it is there, keeps the newest [KEEP], and copies pictures not yet copied. */
    suspend fun writeDaily() {
        val day = now().toLocalDateTime(TimeZone.of("Asia/Tashkent")).date
        backupDir.mkdirs()
        val target = File(backupDir, "pm-backup-$day.json.gz")
        if (!target.exists()) {
            val partial = File(backupDir, "${target.name}.part")
            GZIPOutputStream(partial.outputStream()).bufferedWriter().use { it.write(snapshot()) }
            partial.renameTo(target)
            logger.info("Loyiha backup written: {}", target.name)
        }
        backupDir.listFiles { file -> file.name.startsWith("pm-backup-") && file.name.endsWith(".json.gz") }
            ?.sortedByDescending { it.name }
            ?.drop(KEEP)
            ?.forEach { it.delete() }
        val copies = File(backupDir, "images").apply { mkdirs() }
        imageDir.listFiles()?.filter { it.isFile }?.forEach { file ->
            val copy = File(copies, file.name)
            if (!copy.exists()) file.copyTo(copy)
        }
    }

    private fun JdbcTransaction.count(table: String): Long {
        var count = 0L
        exec("SELECT count(*) FROM $table") { result -> if (result.next()) count = result.getLong(1) }
        return count
    }

    /** Through a scratch table, so references to operators this database lacks can be emptied first. */
    private fun JdbcTransaction.load(table: String, rows: String) {
        exec("CREATE TEMP TABLE pm_restore ON COMMIT DROP AS SELECT * FROM json_populate_recordset(NULL::$table, ?::json)", listOf(TextColumnType() to rows))
        ADMIN_COLUMNS[table].orEmpty().forEach { column ->
            exec("UPDATE pm_restore SET $column = NULL WHERE $column IS NOT NULL AND $column NOT IN (SELECT id FROM admin_users)")
        }
        exec("INSERT INTO $table SELECT * FROM pm_restore")
        exec("DROP TABLE pm_restore")
    }

    internal companion object {
        const val FORMAT = 1
        const val KEEP = 14

        /** Parents before children. */
        val TABLES = listOf(
            "pm_epics", "pm_releases", "pm_tasks", "pm_comments", "pm_task_events", "pm_task_commits",
            "pm_docs", "pm_doc_versions", "pm_images",
        )

        val ADMIN_COLUMNS = mapOf(
            "pm_epics" to listOf("created_by"),
            "pm_releases" to listOf("created_by"),
            "pm_tasks" to listOf("assignee_id", "reporter_id"),
            "pm_comments" to listOf("author_id"),
            "pm_task_events" to listOf("actor_id"),
            "pm_docs" to listOf("created_by", "updated_by"),
            "pm_doc_versions" to listOf("edited_by"),
            "pm_images" to listOf("uploaded_by"),
        )
    }
}

/** Writes the day's backup, checking every hour. */
class PmBackupJob(private val backups: PmBackupService) {
    private val logger = LoggerFactory.getLogger(PmBackupJob::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        job = scope.launch {
            while (isActive) {
                runCatching { backups.writeDaily() }.onFailure { logger.error("Loyiha backup failed", it) }
                delay(1.hours)
            }
        }
    }

    fun stop() {
        job?.cancel()
        scope.cancel()
    }
}
