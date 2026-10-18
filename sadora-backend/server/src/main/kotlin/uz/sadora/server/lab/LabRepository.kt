package uz.sadora.server.lab

import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import uz.sadora.contract.LabPhoto
import uz.sadora.server.core.now
import uz.sadora.server.core.toKotlinInstant
import uz.sadora.server.core.toOffsetDateTime
import uz.sadora.server.db.LabReportPhotos
import uz.sadora.server.db.LabReports
import uz.sadora.server.db.LabValues
import uz.sadora.server.db.dbQuery

data class LabReportRecord(
    val id: Uuid,
    val userId: Uuid,
    val takenOn: LocalDate,
    val place: String?,
    val note: String?,
    val scanned: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
    val values: List<LabValueRecord>,
    val photos: List<LabPhoto>,
)

data class LabValueRecord(
    val markerCode: String?,
    val name: String,
    val value: Double?,
    val valueText: String?,
    val unit: String?,
    val refLow: Double?,
    val refHigh: Double?,
    val refText: String?,
    val id: Uuid = Uuid.random(),
)

/** A page as stored: checked and measured by the service before it gets here. */
data class LabPhotoRecord(val mimeType: String, val width: Int, val height: Int, val bytes: ByteArray)

/** The sheet fields that are not rows or pages. */
data class LabSheetFields(val takenOn: LocalDate, val place: String?, val note: String?)

/** A known marker's number on one sheet: one point of a chart. */
data class LabTrendRow(val reportId: Uuid, val takenOn: LocalDate, val row: LabValueRecord)

/**
 * Lab sheets, scoped to their owner in every query a user can reach.
 *
 * The rows of a sheet are replaced as a whole on edit rather than patched one by one:
 * the edit screen is the whole sheet, and a sheet is a few dozen short rows.
 */
class LabRepository {

    /** Hers, newest sheet first; the id breaks ties so pages never repeat or skip. */
    suspend fun page(userId: Uuid, limit: Int, offset: Int): Pair<List<LabReportRecord>, Long> = dbQuery {
        val total = LabReports.selectAll().where { LabReports.userId eq userId }.count()
        val reports = LabReports.selectAll()
            .where { LabReports.userId eq userId }
            .orderBy(LabReports.takenOn to SortOrder.DESC, LabReports.id to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .toList()
        withRows(reports) to total
    }

    /** Hers from [from] on, newest first, at most [limit]: the doctor's summary. */
    suspend fun since(userId: Uuid, from: LocalDate, limit: Int): List<LabReportRecord> = dbQuery {
        val reports = LabReports.selectAll()
            .where { (LabReports.userId eq userId) and (LabReports.takenOn greaterEq from) }
            .orderBy(LabReports.takenOn to SortOrder.DESC, LabReports.id to SortOrder.DESC)
            .limit(limit)
            .toList()
        withRows(reports)
    }

    suspend fun byId(userId: Uuid, id: Uuid): LabReportRecord? = byIds(listOf(id))[id]?.takeIf { it.userId == userId }

    /** Any owner's: for the lines in a thread and the shared page, which check the owner themselves. */
    suspend fun byIds(ids: Collection<Uuid>): Map<Uuid, LabReportRecord> = dbQuery {
        if (ids.isEmpty()) return@dbQuery emptyMap()
        val reports = LabReports.selectAll().where { LabReports.id inList ids }.toList()
        withRows(reports).associateBy { it.id }
    }

    suspend fun insert(
        userId: Uuid,
        fields: LabSheetFields,
        scanned: Boolean,
        rows: List<LabValueRecord>,
        photos: List<LabPhotoRecord>,
    ): Uuid = dbQuery {
        val id = Uuid.random()
        val timestamp = now().toOffsetDateTime()
        LabReports.insert {
            it[LabReports.id] = id
            it[LabReports.userId] = userId
            it[takenOn] = fields.takenOn
            it[place] = fields.place
            it[note] = fields.note
            it[LabReports.scanned] = scanned
            it[createdAt] = timestamp
            it[updatedAt] = timestamp
        }
        insertRows(id, rows)
        photos.forEachIndexed { index, photo ->
            LabReportPhotos.insert {
                it[reportId] = id
                it[page] = index
                it[mimeType] = photo.mimeType
                it[width] = photo.width
                it[height] = photo.height
                it[content] = photo.bytes
                it[createdAt] = timestamp
            }
        }
        id
    }

    /** Replaces the details and every row; false when the sheet is not hers. */
    suspend fun update(userId: Uuid, id: Uuid, fields: LabSheetFields, rows: List<LabValueRecord>): Boolean = dbQuery {
        val changed = LabReports.update({ (LabReports.id eq id) and (LabReports.userId eq userId) }) {
            it[takenOn] = fields.takenOn
            it[place] = fields.place
            it[note] = fields.note
            it[updatedAt] = now().toOffsetDateTime()
        } > 0
        if (changed) {
            LabValues.deleteWhere { LabValues.reportId eq id }
            insertRows(id, rows)
        }
        changed
    }

    /** The rows and the pages go with it (ON DELETE CASCADE); a chat card keeps its plain text. */
    suspend fun delete(userId: Uuid, id: Uuid): Boolean = dbQuery {
        LabReports.deleteWhere { (LabReports.id eq id) and (LabReports.userId eq userId) } > 0
    }

    suspend fun photo(reportId: Uuid, page: Int): LabPhotoRecord? = dbQuery {
        LabReportPhotos.selectAll()
            .where { (LabReportPhotos.reportId eq reportId) and (LabReportPhotos.page eq page) }
            .singleOrNull()
            ?.let {
                LabPhotoRecord(it[LabReportPhotos.mimeType], it[LabReportPhotos.width], it[LabReportPhotos.height], it[LabReportPhotos.content])
            }
    }

    /** Every number she has for a known marker, oldest sheet first. */
    suspend fun trendRows(userId: Uuid): List<LabTrendRow> = dbQuery {
        (LabValues innerJoin LabReports)
            .selectAll()
            .where {
                (LabReports.userId eq userId) and LabValues.markerCode.isNotNull() and LabValues.value.isNotNull()
            }
            .orderBy(LabReports.takenOn to SortOrder.ASC, LabReports.createdAt to SortOrder.ASC, LabValues.position to SortOrder.ASC)
            .map { LabTrendRow(it[LabReports.id], it[LabReports.takenOn], it.toValue()) }
    }

    // ---------------------------------------------------------------- inside a transaction

    private fun insertRows(reportId: Uuid, rows: List<LabValueRecord>) {
        rows.forEachIndexed { index, row ->
            LabValues.insert {
                it[id] = Uuid.random()
                it[LabValues.reportId] = reportId
                it[position] = index
                it[markerCode] = row.markerCode
                it[name] = row.name
                it[value] = row.value
                it[valueText] = row.valueText
                it[unit] = row.unit
                it[refLow] = row.refLow
                it[refHigh] = row.refHigh
                it[refText] = row.refText
            }
        }
    }

    /** The rows and page shapes of [reports], read in two queries rather than two per sheet. */
    private fun withRows(reports: List<ResultRow>): List<LabReportRecord> {
        if (reports.isEmpty()) return emptyList()
        val ids = reports.map { it[LabReports.id] }
        val rows = LabValues.selectAll()
            .where { LabValues.reportId inList ids }
            .orderBy(LabValues.position to SortOrder.ASC)
            .groupBy({ it[LabValues.reportId] }, { it.toValue() })
        val photos = LabReportPhotos
            .select(LabReportPhotos.reportId, LabReportPhotos.page, LabReportPhotos.mimeType, LabReportPhotos.width, LabReportPhotos.height)
            .where { LabReportPhotos.reportId inList ids }
            .orderBy(LabReportPhotos.page to SortOrder.ASC)
            .groupBy(
                { it[LabReportPhotos.reportId] },
                { LabPhoto(it[LabReportPhotos.width], it[LabReportPhotos.height], it[LabReportPhotos.mimeType]) },
            )
        return reports.map { report ->
            val id = report[LabReports.id]
            LabReportRecord(
                id = id,
                userId = report[LabReports.userId],
                takenOn = report[LabReports.takenOn],
                place = report[LabReports.place],
                note = report[LabReports.note],
                scanned = report[LabReports.scanned],
                createdAt = report[LabReports.createdAt].toKotlinInstant(),
                updatedAt = report[LabReports.updatedAt].toKotlinInstant(),
                values = rows[id].orEmpty(),
                photos = photos[id].orEmpty(),
            )
        }
    }

    private fun ResultRow.toValue() = LabValueRecord(
        id = this[LabValues.id],
        markerCode = this[LabValues.markerCode],
        name = this[LabValues.name],
        value = this[LabValues.value],
        valueText = this[LabValues.valueText],
        unit = this[LabValues.unit],
        refLow = this[LabValues.refLow],
        refHigh = this[LabValues.refHigh],
        refText = this[LabValues.refText],
    )
}
