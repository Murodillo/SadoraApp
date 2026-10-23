package uz.sadora.server.admin.pm

import java.io.File
import kotlin.uuid.Uuid
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.server.audit.AuditActions
import uz.sadora.server.audit.AuditService
import uz.sadora.server.auth.RequestContext
import uz.sadora.server.core.ConflictException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.now
import uz.sadora.server.core.parseUuidOrNull
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.dbQuery
import uz.sadora.server.db.escapeLike
import uz.sadora.server.plugins.AdminPrincipal

/**
 * The team's docs: Markdown pages in a tree, each save a new version.
 *
 * Several people and Claude sessions write the same pages, so a save names the version it
 * was made on and a newer one on the server refuses it — the editor reloads instead of
 * silently erasing the other's words. Every version stays readable, and an old one is
 * brought back by saving it again. Pictures are files beside the database, opened only
 * through this section.
 */
class PmDocService(private val audit: AuditService, private val imageDir: File) {

    suspend fun tree(archived: Boolean = false): List<PmDocNode> = dbQuery {
        PmDocs.select(PmDocs.id, PmDocs.parentId, PmDocs.title, PmDocs.position, PmDocs.version, PmDocs.archivedAt, PmDocs.updatedBy, PmDocs.updatedAt)
            .where { if (archived) PmDocs.archivedAt.isNotNull() else PmDocs.archivedAt.isNull() }
            .orderBy(PmDocs.position to SortOrder.ASC, PmDocs.title to SortOrder.ASC)
            .map {
                PmDocNode(
                    id = it[PmDocs.id].toString(),
                    parentId = it[PmDocs.parentId]?.toString(),
                    title = it[PmDocs.title],
                    position = it[PmDocs.position],
                    version = it[PmDocs.version],
                    archived = it[PmDocs.archivedAt] != null,
                    updatedBy = it[PmDocs.updatedBy]?.toString(),
                    updatedAt = it[PmDocs.updatedAt].toKotlinInstant(),
                )
            }
    }

    suspend fun get(id: Uuid): PmDoc = dbQuery { requireRow(id).toDoc() }

    suspend fun create(request: CreatePmDocRequest, actor: AdminPrincipal): PmDoc {
        val title = cleanTitle(request.title)
        val body = cleanBody(request.body)
        return dbQuery {
            val parent = request.parentId?.let { requireParent(it, null) }
            val id = Uuid.random()
            val at = now().toOffsetDateTime()
            val top = PmDocs.position.max()
            val last = PmDocs.select(top).where { if (parent == null) PmDocs.parentId.isNull() else PmDocs.parentId eq parent }.single()[top]
            PmDocs.insert {
                it[PmDocs.id] = id
                it[parentId] = parent
                it[PmDocs.title] = title
                it[PmDocs.body] = body
                it[position] = (last ?: 0.0) + 1.0
                it[version] = 1
                it[createdBy] = actor.adminId
                it[updatedBy] = actor.adminId
                it[createdAt] = at
                it[updatedAt] = at
            }
            saveVersion(id, 1, title, body, actor)
            requireRow(id).toDoc()
        }
    }

    suspend fun update(id: Uuid, request: UpdatePmDocRequest, actor: AdminPrincipal): PmDoc {
        val title = cleanTitle(request.title)
        val body = cleanBody(request.body)
        return dbQuery {
            val row = requireRow(id)
            val current = row[PmDocs.version]
            if (request.baseVersion != current) {
                throw ConflictException("Hujjatni siz ochgandan keyin boshqa odam o'zgartirdi (v$current). Yangisini oching va qayta yozing.")
            }
            val parent = request.parentId?.let { requireParent(it, id) }
            val unchanged = title == row[PmDocs.title] && body == row[PmDocs.body] && parent == row[PmDocs.parentId] &&
                (request.position == null || request.position == row[PmDocs.position])
            if (unchanged) return@dbQuery row.toDoc()
            val contentChanged = title != row[PmDocs.title] || body != row[PmDocs.body]
            val next = if (contentChanged) current + 1 else current
            PmDocs.update({ PmDocs.id eq id }) {
                it[PmDocs.title] = title
                it[PmDocs.body] = body
                it[parentId] = parent
                request.position?.let { value -> it[position] = value }
                it[version] = next
                it[updatedBy] = actor.adminId
                it[updatedAt] = now().toOffsetDateTime()
            }
            if (contentChanged) saveVersion(id, next, title, body, actor)
            requireRow(id).toDoc()
        }
    }

    suspend fun versions(id: Uuid): List<PmDocVersionInfo> = dbQuery {
        requireRow(id)
        PmDocVersions.selectAll().where { PmDocVersions.docId eq id }
            .orderBy(PmDocVersions.version to SortOrder.DESC)
            .map {
                PmDocVersionInfo(
                    version = it[PmDocVersions.version],
                    title = it[PmDocVersions.title],
                    editedBy = it[PmDocVersions.editedBy]?.toString(),
                    createdAt = it[PmDocVersions.createdAt].toKotlinInstant(),
                    size = it[PmDocVersions.body].length,
                )
            }
    }

    suspend fun version(id: Uuid, version: Int): PmDocVersion = dbQuery {
        val row = PmDocVersions.selectAll().where { (PmDocVersions.docId eq id) and (PmDocVersions.version eq version) }.singleOrNull()
            ?: throw NotFoundException("Versiya topilmadi")
        PmDocVersion(
            version = row[PmDocVersions.version],
            title = row[PmDocVersions.title],
            body = row[PmDocVersions.body],
            editedBy = row[PmDocVersions.editedBy]?.toString(),
            createdAt = row[PmDocVersions.createdAt].toKotlinInstant(),
        )
    }

    /** Archives the page and everything under it; [restore] brings back what was archived with it. */
    suspend fun archive(id: Uuid): PmDoc = dbQuery {
        val row = requireRow(id)
        if (row[PmDocs.archivedAt] == null) {
            val at = now().toOffsetDateTime()
            val ids = subtree(id)
            PmDocs.update({ (PmDocs.id inList ids) and PmDocs.archivedAt.isNull() }) { it[archivedAt] = at }
        }
        requireRow(id).toDoc()
    }

    suspend fun restore(id: Uuid): PmDoc = dbQuery {
        val row = requireRow(id)
        val archivedAt = row[PmDocs.archivedAt]
        if (archivedAt != null) {
            val ids = subtree(id)
            PmDocs.update({ (PmDocs.id eq id) or ((PmDocs.id inList ids) and (PmDocs.archivedAt eq archivedAt)) }) { it[PmDocs.archivedAt] = null }
            // Its parent may still be in the archive; then it comes back at the top.
            val parent = row[PmDocs.parentId]
            if (parent != null && PmDocs.selectAll().where { (PmDocs.id eq parent) and PmDocs.archivedAt.isNotNull() }.count() > 0) {
                PmDocs.update({ PmDocs.id eq id }) { it[parentId] = null }
            }
        }
        requireRow(id).toDoc()
    }

    /** For good, with every page under it. Owner only, and only once archived. */
    suspend fun delete(id: Uuid, actor: AdminPrincipal, context: RequestContext) {
        refuseForGood(actor)
        val title = dbQuery {
            val row = requireRow(id)
            if (row[PmDocs.archivedAt] == null) throw ConflictException("Avval hujjatni arxivlang")
            PmDocs.deleteWhere { PmDocs.id eq id }
            row[PmDocs.title]
        }
        audit.recordPm(actor, AuditActions.PM_DOC_DELETED, "pm_doc", id.toString(), context, mapOf("title" to title.take(120)))
    }

    suspend fun search(text: String): List<PmDocHit> {
        val needle = text.trim().take(80)
        if (needle.length < 2) return emptyList()
        val pattern = "%${needle.lowercase().escapeLike()}%"
        return dbQuery {
            PmDocs.selectAll()
                .where { PmDocs.archivedAt.isNull() and ((PmDocs.title.lowerCase() like pattern) or (PmDocs.body.lowerCase() like pattern)) }
                .orderBy(PmDocs.updatedAt to SortOrder.DESC)
                .limit(20)
                .map { PmDocHit(it[PmDocs.id].toString(), it[PmDocs.title], snippet(it[PmDocs.body], needle)) }
        }
    }

    /** Live pages whose text names [key] — "SAD-12", not "SAD-123" — for the task page. */
    suspend fun mentioning(key: String): List<PmDocHit> {
        val exact = Regex("(?<![A-Za-z0-9-])${Regex.escape(key)}(?![0-9A-Za-z])")
        return dbQuery {
            PmDocs.selectAll()
                .where { PmDocs.archivedAt.isNull() and ((PmDocs.body like "%${key.escapeLike()}%") or (PmDocs.title like "%${key.escapeLike()}%")) }
                .orderBy(PmDocs.updatedAt to SortOrder.DESC)
                .limit(50)
                .filter { exact.containsMatchIn(it[PmDocs.body]) || exact.containsMatchIn(it[PmDocs.title]) }
                .map { PmDocHit(it[PmDocs.id].toString(), it[PmDocs.title], snippet(it[PmDocs.body], key)) }
        }
    }

    // ------------------------------------------------------------ pictures

    /** Keeps a picture if its first bytes say PNG, JPEG, WebP or GIF; a name or a header is not believed. */
    suspend fun saveImage(bytes: ByteArray, actor: AdminPrincipal): PmImageRef {
        if (bytes.size > IMAGE_MAX) throw ValidationException("image", "Rasm eng ko'pi 10 MB bo'lsin")
        val type = sniff(bytes) ?: throw ValidationException("image", "Faqat PNG, JPEG, WebP yoki GIF rasm")
        val id = Uuid.random()
        imageDir.mkdirs()
        File(imageDir, id.toString()).writeBytes(bytes)
        dbQuery {
            PmImages.insert {
                it[PmImages.id] = id
                it[contentType] = type
                it[sizeBytes] = bytes.size
                it[uploadedBy] = actor.adminId
                it[createdAt] = now().toOffsetDateTime()
            }
        }
        return PmImageRef(id.toString(), type, bytes.size)
    }

    /**
     * A restored backup's picture coming back under its old id. Only for a picture the
     * restored rows name and the disk does not have, so nothing can be overwritten.
     */
    suspend fun restoreImage(id: Uuid, bytes: ByteArray) {
        val type = dbQuery { PmImages.selectAll().where { PmImages.id eq id }.singleOrNull()?.get(PmImages.contentType) }
            ?: throw NotFoundException("Rasm topilmadi")
        val file = File(imageDir, id.toString())
        if (file.exists()) throw ConflictException("Bu rasm allaqachon bor")
        if (sniff(bytes) != type) throw ValidationException("image", "Fayl zaxiradagi rasm emas")
        imageDir.mkdirs()
        file.writeBytes(bytes)
    }

    suspend fun image(id: Uuid): Pair<String, ByteArray> {
        val type = dbQuery { PmImages.selectAll().where { PmImages.id eq id }.singleOrNull()?.get(PmImages.contentType) }
            ?: throw NotFoundException("Rasm topilmadi")
        val file = File(imageDir, id.toString())
        if (!file.isFile) throw NotFoundException("Rasm topilmadi")
        return type to file.readBytes()
    }

    // ------------------------------------------------------------ inside a transaction

    private fun JdbcTransaction.requireRow(id: Uuid): ResultRow =
        PmDocs.selectAll().where { PmDocs.id eq id }.singleOrNull() ?: throw NotFoundException("Hujjat topilmadi")

    /** A parent that exists and is not the page itself or anything under it. */
    private fun JdbcTransaction.requireParent(raw: String, self: Uuid?): Uuid {
        val parent = parseUuidOrNull(raw) ?: throw ValidationException("parentId", "Yuqori hujjat topilmadi")
        if (PmDocs.selectAll().where { PmDocs.id eq parent }.empty()) throw ValidationException("parentId", "Yuqori hujjat topilmadi")
        if (self != null && parent in subtree(self)) throw ValidationException("parentId", "Hujjatni o'zining ichiga ko'chirib bo'lmaydi")
        return parent
    }

    /** The page and every page under it, breadth first. */
    private fun subtree(root: Uuid): List<Uuid> {
        val all = PmDocs.select(PmDocs.id, PmDocs.parentId).map { it[PmDocs.id] to it[PmDocs.parentId] }
        val children = all.filter { it.second != null }.groupBy({ it.second!! }, { it.first })
        val found = mutableListOf(root)
        var index = 0
        while (index < found.size) {
            children[found[index]]?.forEach { if (it !in found) found += it }
            index++
        }
        return found
    }

    private fun saveVersion(id: Uuid, version: Int, title: String, body: String, actor: AdminPrincipal) {
        PmDocVersions.insert {
            it[docId] = id
            it[PmDocVersions.version] = version
            it[PmDocVersions.title] = title
            it[PmDocVersions.body] = body
            it[editedBy] = actor.adminId
            it[createdAt] = now().toOffsetDateTime()
        }
    }

    private fun ResultRow.toDoc() = PmDoc(
        id = this[PmDocs.id].toString(),
        parentId = this[PmDocs.parentId]?.toString(),
        title = this[PmDocs.title],
        body = this[PmDocs.body],
        position = this[PmDocs.position],
        version = this[PmDocs.version],
        archived = this[PmDocs.archivedAt] != null,
        createdBy = this[PmDocs.createdBy]?.toString(),
        updatedBy = this[PmDocs.updatedBy]?.toString(),
        createdAt = this[PmDocs.createdAt].toKotlinInstant(),
        updatedAt = this[PmDocs.updatedAt].toKotlinInstant(),
    )

    internal companion object {
        const val TITLE_MAX = 160
        const val BODY_MAX = 500_000
        const val IMAGE_MAX = 10 * 1024 * 1024

        fun cleanTitle(raw: String): String {
            val title = raw.trim()
            if (title.isEmpty() || title.length > TITLE_MAX) throw ValidationException("title", "Sarlavha 1–$TITLE_MAX belgi bo'lsin")
            return title
        }

        fun cleanBody(raw: String): String {
            if (raw.length > BODY_MAX) throw ValidationException("body", "Hujjat juda uzun")
            return raw.trimEnd()
        }

        fun sniff(bytes: ByteArray): String? {
            fun at(index: Int) = bytes.getOrNull(index)?.toInt()?.and(0xFF)
            fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
            return when {
                at(0) == 0x89 && ascii(1, "PNG") -> "image/png"
                at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "image/jpeg"
                ascii(0, "GIF8") -> "image/gif"
                ascii(0, "RIFF") && ascii(8, "WEBP") -> "image/webp"
                else -> null
            }
        }

        /** About 160 characters around the first match, on one line. */
        fun snippet(body: String, needle: String): String {
            val flat = body.replace(Regex("\\s+"), " ")
            val at = flat.lowercase().indexOf(needle.lowercase())
            if (at < 0) return flat.take(160)
            val from = (at - 60).coerceAtLeast(0)
            val to = (at + needle.length + 100).coerceAtMost(flat.length)
            return (if (from > 0) "…" else "") + flat.substring(from, to) + (if (to < flat.length) "…" else "")
        }
    }
}
