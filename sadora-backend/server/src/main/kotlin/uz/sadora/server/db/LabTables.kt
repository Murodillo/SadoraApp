package uz.sadora.server.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestampWithTimeZone

/** One laboratory sheet (V56). Health data: like [HealthTables] it stays out of the admin's imports. */
object LabReports : Table("lab_reports") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val takenOn = date("taken_on")
    val place = text("place").nullable()
    val note = text("note").nullable()
    val scanned = bool("scanned")
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")

    override val primaryKey = PrimaryKey(id)
}

/** A printed line of a sheet; the flag is worked out from its range when read. */
object LabValues : Table("lab_values") {
    val id = uuid("id")
    val reportId = uuid("report_id").references(LabReports.id)
    val position = integer("position")
    val markerCode = text("marker_code").nullable()
    val name = text("name")
    val value = double("value").nullable()
    val valueText = text("value_text").nullable()
    val unit = text("unit").nullable()
    val refLow = double("ref_low").nullable()
    val refHigh = double("ref_high").nullable()
    val refText = text("ref_text").nullable()

    override val primaryKey = PrimaryKey(id)
}

/** A photographed page, read only when someone opens it. */
object LabReportPhotos : Table("lab_report_photos") {
    val reportId = uuid("report_id").references(LabReports.id)
    val page = integer("page")
    val mimeType = text("mime_type")
    val width = integer("width")
    val height = integer("height")
    val content = binary("content")
    val createdAt = timestampWithTimeZone("created_at")

    override val primaryKey = PrimaryKey(reportId, page)
}
