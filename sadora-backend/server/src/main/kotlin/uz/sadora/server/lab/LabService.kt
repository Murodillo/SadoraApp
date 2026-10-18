package uz.sadora.server.lab

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.LabFlag
import uz.sadora.contract.LabMarker
import uz.sadora.contract.LabOverview
import uz.sadora.contract.LabPhotoUpload
import uz.sadora.contract.LabRanges
import uz.sadora.contract.LabReport
import uz.sadora.contract.LabScanRequest
import uz.sadora.contract.LabScanResult
import uz.sadora.contract.LabTrend
import uz.sadora.contract.LabTrendPoint
import uz.sadora.contract.LabValue
import uz.sadora.contract.Language
import uz.sadora.contract.Limits
import uz.sadora.contract.Page
import uz.sadora.contract.SaveLabReportRequest
import uz.sadora.contract.SaveLabValue
import uz.sadora.server.ai.AiSource
import uz.sadora.server.ai.AiUsageEntry
import uz.sadora.server.ai.AiUsageRecorder
import uz.sadora.server.ai.LabVision
import uz.sadora.server.ai.LabVisionSheet
import uz.sadora.server.ai.ModelUnavailableException
import uz.sadora.server.config.AiConfig
import uz.sadora.server.core.ConsentRequiredException
import uz.sadora.server.core.NotFoundException
import uz.sadora.server.core.UpstreamUnavailableException
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.health.HealthAccess

/**
 * Her lab sheets: typed in, or read from photos and then checked by her.
 *
 * Typing a sheet in and every chart are free; reading photos is the metered part,
 * because it is a model call. Reading saves nothing — like the food scanner, the rows
 * come back to the screen, she corrects what the model misread, and only her "Saqlash"
 * writes the sheet, with the pages, which are sent again then. That keeps a misread
 * number out of her record until she has looked at it, at the price of a second upload.
 */
class LabService(
    private val labs: LabRepository,
    private val access: HealthAccess,
    /** Null when no model is configured; reading then says so and typing still works. */
    private val vision: LabVision? = null,
    private val visionConfig: AiConfig? = null,
    private val usage: AiUsageRecorder? = null,
) {

    val scanAvailable: Boolean get() = vision != null && visionConfig?.apiKey != null

    // ---------------------------------------------------------------- reading hers

    suspend fun overview(userId: Uuid): LabOverview {
        val user = access.requireUser(userId)
        return LabOverview(
            trends = trends(userId, user.language),
            scanAvailable = scanAvailable,
            scanAllowance = if (scanAvailable) access.feature(userId, FeatureKeys.LAB_SCAN, user.timezone) else null,
        )
    }

    suspend fun list(userId: Uuid, limit: Int, offset: Int): Page<LabReport> {
        val user = access.requireUser(userId)
        val size = limit.coerceIn(1, LIST_MAX)
        val (records, total) = labs.page(userId, size, offset.coerceAtLeast(0))
        return Page(records.map { dto(it, user.language) }, total, size, offset.coerceAtLeast(0))
    }

    suspend fun get(userId: Uuid, id: Uuid): LabReport {
        val user = access.requireUser(userId)
        return dto(requireOwn(userId, id), user.language)
    }

    suspend fun photo(userId: Uuid, id: Uuid, page: Int): LabPhotoRecord {
        requireOwn(userId, id)
        return labs.photo(id, page) ?: throw NotFoundException("Rasm topilmadi")
    }

    suspend fun markers(userId: Uuid): List<LabMarker> {
        val user = access.requireUser(userId)
        return LabMarkers.all.map { it.dto(user.language) }
    }

    // ---------------------------------------------------------------- writing

    suspend fun create(userId: Uuid, request: SaveLabReportRequest): LabReport {
        val user = requireConsent(userId)
        val fields = cleanFields(request, now().dayIn(user.timezone))
        val rows = cleanRows(request.values)
        if (request.photos.size > Limits.LAB_PHOTOS_MAX) {
            throw ValidationException("photos", "Eng ko'pi ${Limits.LAB_PHOTOS_MAX} ta sahifa")
        }
        val photos = request.photos.map(::decodePhoto)
        val id = labs.insert(userId, fields, scanned = request.scanned && photos.isNotEmpty(), rows, photos)
        return dto(requireOwn(userId, id), user.language)
    }

    suspend fun update(userId: Uuid, id: Uuid, request: SaveLabReportRequest): LabReport {
        val user = requireConsent(userId)
        val fields = cleanFields(request, now().dayIn(user.timezone))
        val rows = cleanRows(request.values)
        if (!labs.update(userId, id, fields, rows)) throw NotFoundException("Tahlil topilmadi")
        return dto(requireOwn(userId, id), user.language)
    }

    suspend fun delete(userId: Uuid, id: Uuid) {
        if (!labs.delete(userId, id)) throw NotFoundException("Tahlil topilmadi")
    }

    // ---------------------------------------------------------------- the scanner

    /**
     * Reads the pages. Consent, then the allowance, then the request is checked, and
     * only then is a use spent and the model called — a malformed request costs her
     * nothing, and a model that answered is a use whether or not she saves the result.
     */
    suspend fun scan(userId: Uuid, request: LabScanRequest): LabScanResult {
        val user = access.requireWritable(userId, FeatureKeys.LAB_SCAN)
        if (request.photos.isEmpty()) throw ValidationException("photos", "Kamida bitta sahifani suratga oling")
        if (request.photos.size > Limits.LAB_PHOTOS_MAX) {
            throw ValidationException("photos", "Eng ko'pi ${Limits.LAB_PHOTOS_MAX} ta sahifa")
        }
        request.photos.forEach(::decodePhoto)

        val model = vision?.takeIf { scanAvailable }
            ?: throw UpstreamUnavailableException("Skaner hozircha ishlamayapti. Natijalarni qo'lda kiritishingiz mumkin.")

        access.consume(userId, FeatureKeys.LAB_SCAN, user.timezone)

        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val answer = try {
            withTimeout(SCAN_TIMEOUT) { model.read(request.photos, user.language, LabMarkers.all.map { it.code }) }
        } catch (failure: Exception) {
            if (failure !is ModelUnavailableException && failure !is TimeoutCancellationException) throw failure
            usage?.record(
                AiUsageEntry(
                    userId = userId,
                    source = AiSource.FALLBACK,
                    model = model.name,
                    feature = FeatureKeys.LAB_SCAN,
                    latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                    outcome = "error",
                    errorCode = (failure as? ModelUnavailableException)?.code ?: "timeout",
                ),
            )
            throw UpstreamUnavailableException("Tahlilni o'qib bo'lmadi. Qaytadan urinib ko'ring yoki qo'lda kiriting.")
        }
        usage?.record(
            AiUsageEntry(
                userId = userId,
                source = AiSource.MODEL,
                model = answer.model,
                feature = FeatureKeys.LAB_SCAN,
                promptTokens = answer.promptTokens,
                completionTokens = answer.completionTokens,
                costMicros = visionConfig?.costMicros(answer.promptTokens, answer.completionTokens) ?: 0,
                latencyMs = started.elapsedNow().inWholeMilliseconds.toInt(),
                outcome = "ok",
            ),
        )
        return LabScanCleaner.clean(answer.sheet, now().dayIn(user.timezone))
    }

    // ---------------------------------------------------------------- for others

    /** Her sheets for a doctor's summary: the last year, newest first. */
    suspend fun forSummary(userId: Uuid, today: LocalDate, language: Language): List<LabReport> =
        labs.since(userId, today.minus(Limits.LAB_SHARE_DAYS - 1, DateTimeUnit.DAY), Limits.LAB_SHARE_REPORTS_MAX)
            .map { dto(it, language) }

    /** Hers, for the shared page and the summary's photos; null when it is not hers. */
    suspend fun ownedBy(userId: Uuid, id: Uuid): LabReportRecord? = labs.byId(userId, id)

    suspend fun byIds(ids: Collection<Uuid>): Map<Uuid, LabReportRecord> = labs.byIds(ids)

    suspend fun photoOf(reportId: Uuid, page: Int): LabPhotoRecord? = labs.photo(reportId, page)

    fun dto(record: LabReportRecord, language: Language) = LabReport(
        id = record.id.toString(),
        takenOn = record.takenOn,
        place = record.place,
        note = record.note,
        scanned = record.scanned,
        values = record.values.map { it.dto(language) },
        photos = record.photos,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
    )

    // ---------------------------------------------------------------- inside

    private suspend fun trends(userId: Uuid, language: Language): List<LabTrend> {
        val order = LabMarkers.all.withIndex().associate { (index, marker) -> marker.code to index }
        return labs.trendRows(userId)
            .groupBy { it.row.markerCode!! }
            .mapNotNull { (code, rows) ->
                val marker = LabMarkers.byCode(code) ?: return@mapNotNull null
                LabTrend(
                    markerCode = code,
                    name = marker.names.of(language),
                    about = marker.about.of(language),
                    points = rows.map { point ->
                        LabTrendPoint(
                            reportId = point.reportId.toString(),
                            takenOn = point.takenOn,
                            value = point.row.value!!,
                            unit = point.row.unit,
                            refLow = point.row.refLow,
                            refHigh = point.row.refHigh,
                            flag = LabRanges.flagOf(point.row.value, point.row.refLow, point.row.refHigh),
                        )
                    },
                )
            }
            // The newest result first, so what changed lately is on top; the catalogue order breaks ties.
            .sortedWith(compareByDescending<LabTrend> { it.latest?.takenOn }.thenBy { order[it.markerCode] ?: Int.MAX_VALUE })
    }

    private suspend fun requireOwn(userId: Uuid, id: Uuid): LabReportRecord =
        labs.byId(userId, id) ?: throw NotFoundException("Tahlil topilmadi")

    /** Typing a sheet in is free, but it is still health data: the storage consent applies. */
    private suspend fun requireConsent(userId: Uuid) = access.requireUser(userId).also {
        if (!access.hasStorageConsent(userId)) throw ConsentRequiredException("store_health")
    }

    private fun cleanFields(request: SaveLabReportRequest, today: LocalDate): LabSheetFields {
        if (request.takenOn > today.plus(1, DateTimeUnit.DAY) || request.takenOn < EARLIEST) {
            throw ValidationException("takenOn", "Tahlil sanasi noto'g'ri")
        }
        val place = request.place?.trim()?.takeIf(String::isNotEmpty)
        if (place != null && place.length > Limits.LAB_PLACE_MAX) {
            throw ValidationException("place", "Laboratoriya eng ko'pi ${Limits.LAB_PLACE_MAX} belgi bo'lsin")
        }
        val note = request.note?.trim()?.takeIf(String::isNotEmpty)
        if (note != null && note.length > Limits.LAB_NOTE_MAX) {
            throw ValidationException("note", "Izoh eng ko'pi ${Limits.LAB_NOTE_MAX} belgi bo'lsin")
        }
        return LabSheetFields(request.takenOn, place, note)
    }

    private fun cleanRows(values: List<SaveLabValue>): List<LabValueRecord> {
        if (values.isEmpty()) throw ValidationException("values", "Kamida bitta natijani kiriting")
        if (values.size > Limits.LAB_VALUES_MAX) {
            throw ValidationException("values", "Eng ko'pi ${Limits.LAB_VALUES_MAX} ta natija")
        }
        return values.mapIndexed { index, row -> cleanRow(row, index) }
    }

    private fun cleanRow(row: SaveLabValue, field: Int): LabValueRecord {
        val name = row.name.trim()
        if (name.isEmpty()) throw ValidationException(rowField(field, "name"), "Ko'rsatkich nomini yozing")
        if (name.length > Limits.LAB_NAME_MAX) {
            throw ValidationException(rowField(field, "name"), "Ko'rsatkich nomi eng ko'pi ${Limits.LAB_NAME_MAX} belgi bo'lsin")
        }
        val value = row.value?.takeIf { it.isFinite() }
        if (row.value != null && value == null) throw ValidationException(rowField(field, "value"), "Natija son bo'lishi kerak")
        val valueText = row.valueText?.trim()?.takeIf(String::isNotEmpty)
        if (valueText != null && valueText.length > Limits.LAB_VALUE_TEXT_MAX) {
            throw ValidationException(rowField(field, "valueText"), "Natija eng ko'pi ${Limits.LAB_VALUE_TEXT_MAX} belgi bo'lsin")
        }
        if (value == null && valueText == null) throw ValidationException(rowField(field, "value"), "Natijani yozing")
        val unit = row.unit?.trim()?.takeIf(String::isNotEmpty)
        if (unit != null && unit.length > Limits.LAB_UNIT_MAX) {
            throw ValidationException(rowField(field, "unit"), "Birlik eng ko'pi ${Limits.LAB_UNIT_MAX} belgi bo'lsin")
        }
        val low = row.refLow?.takeIf { it.isFinite() }
        val high = row.refHigh?.takeIf { it.isFinite() }
        if (low != null && high != null && low > high) {
            throw ValidationException(rowField(field, "refLow"), "Normaning quyi chegarasi yuqorisidan katta bo'lmasin")
        }
        val refText = row.refText?.trim()?.takeIf(String::isNotEmpty)
        if (refText != null && refText.length > Limits.LAB_REF_TEXT_MAX) {
            throw ValidationException(rowField(field, "refText"), "Norma eng ko'pi ${Limits.LAB_REF_TEXT_MAX} belgi bo'lsin")
        }
        return LabValueRecord(
            markerCode = LabMarkers.resolve(row.markerCode, name)?.code,
            name = name,
            value = value,
            valueText = valueText,
            unit = unit,
            refLow = low,
            refHigh = high,
            refText = refText,
        )
    }

    /** A page is checked for what it claims to be: the size read from the picture itself. */
    private fun decodePhoto(upload: LabPhotoUpload): LabPhotoRecord {
        if (upload.mimeType !in IMAGE_MIME) throw ValidationException("photos", "Faqat JPEG yoki PNG rasm")
        val text = upload.imageBase64.trim()
        if (text.isEmpty()) throw ValidationException("photos", "Rasm bo'sh")
        if (text.length > Limits.LAB_PHOTO_MAX_BYTES / 3 * 4 + 4) {
            throw ValidationException("photos", "Rasm juda katta — kichikroq qilib yuboring")
        }
        val bytes = runCatching { Base64.decode(text) }.getOrElse { throw ValidationException("photos", "Rasmni o'qib bo'lmadi") }
        val picture = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()
            ?: throw ValidationException("photos", "Rasmni o'qib bo'lmadi")
        return LabPhotoRecord(upload.mimeType, picture.width, picture.height, bytes)
    }

    /** `values[3].unit`: which box of which row, for the form to mark. */
    private fun rowField(index: Int, name: String) = "values[$index].$name"

    companion object {
        const val LIST_MAX = 50
        const val LIST_DEFAULT = 20
        const val MESSAGE_MAX = 200
        val SCAN_TIMEOUT = 60.seconds
        val EARLIEST = LocalDate(1950, 1, 1)
        val IMAGE_MIME = setOf("image/jpeg", "image/png")
    }
}

/** The scanner's answer, cleaned; apart from the service so it can be tested without a database. */
internal object LabScanCleaner {
    /**
     * What the model wrote, made safe to show: rows without a result dropped, text cut to
     * the limits the save will apply, a code kept only when the catalogue knows it, and a
     * date in the future — a misread year — dropped rather than proposed.
     */
    fun clean(sheet: LabVisionSheet, today: LocalDate): LabScanResult {
        val rows = sheet.values.asSequence()
            .mapNotNull { raw ->
                val name = raw.name.trim().take(Limits.LAB_NAME_MAX)
                val value = raw.value?.takeIf { it.isFinite() }
                val valueText = raw.valueText?.trim()?.take(Limits.LAB_VALUE_TEXT_MAX)?.takeIf(String::isNotEmpty)
                if (name.isEmpty() || (value == null && valueText == null)) return@mapNotNull null
                var low = raw.refLow?.takeIf { it.isFinite() }
                var high = raw.refHigh?.takeIf { it.isFinite() }
                if (low != null && high != null && low > high) { low = null; high = null }
                SaveLabValue(
                    markerCode = LabMarkers.resolve(raw.code, name)?.code,
                    name = name,
                    value = value,
                    valueText = if (value != null) null else valueText,
                    unit = raw.unit?.trim()?.take(Limits.LAB_UNIT_MAX)?.takeIf(String::isNotEmpty),
                    refLow = low,
                    refHigh = high,
                    refText = raw.refText?.trim()?.take(Limits.LAB_REF_TEXT_MAX)?.takeIf(String::isNotEmpty),
                )
            }
            .take(Limits.LAB_VALUES_MAX)
            .toList()
        val takenOn = sheet.takenOn?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }
            ?.takeIf { it <= today && it >= LabService.EARLIEST }
        val isLab = sheet.isLabReport && rows.isNotEmpty()
        return LabScanResult(
            isLabReport = isLab,
            takenOn = if (isLab) takenOn else null,
            place = if (isLab) sheet.place?.trim()?.take(Limits.LAB_PLACE_MAX)?.takeIf(String::isNotEmpty) else null,
            values = if (isLab) rows else emptyList(),
            message = sheet.message?.trim()?.take(LabService.MESSAGE_MAX)?.takeIf(String::isNotEmpty),
        )
    }
}

/** One row as the apps read it: the known marker's common name and explanation, and the flag. */
fun LabValueRecord.dto(language: Language): LabValue {
    val marker = LabMarkers.byCode(markerCode)
    return LabValue(
        id = id.toString(),
        markerCode = marker?.code,
        name = marker?.names?.of(language) ?: name,
        value = value,
        valueText = valueText,
        unit = unit,
        refLow = refLow,
        refHigh = refHigh,
        refText = refText,
        flag = LabRanges.flagOf(value, refLow, refHigh),
        about = marker?.about?.of(language),
    )
}

/** Shared wording for the chat line's plain text and the doctor's page. */
object LabWords {
    fun title(language: Language) = when (language) {
        Language.UZ -> "Tahlil natijalari"
        Language.RU -> "Результаты анализов"
        Language.EN -> "Lab results"
    }

    fun flag(flag: LabFlag, language: Language) = when (flag) {
        LabFlag.LOW -> when (language) { Language.UZ -> "past"; Language.RU -> "ниже нормы"; Language.EN -> "low" }
        LabFlag.HIGH -> when (language) { Language.UZ -> "yuqori"; Language.RU -> "выше нормы"; Language.EN -> "high" }
        LabFlag.NORMAL -> when (language) { Language.UZ -> "normada"; Language.RU -> "в норме"; Language.EN -> "in range" }
    }

    fun range(language: Language) = when (language) {
        Language.UZ -> "norma"
        Language.RU -> "норма"
        Language.EN -> "range"
    }

    fun number(value: Double): String =
        java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()

    /** "120–150", "< 5", "> 30", or the printed text. */
    fun rangeText(value: LabValue): String? = when {
        value.refLow != null && value.refHigh != null -> "${number(value.refLow!!)}–${number(value.refHigh!!)}"
        value.refHigh != null -> "< ${number(value.refHigh!!)}"
        value.refLow != null -> "> ${number(value.refLow!!)}"
        else -> value.refText
    }

    fun result(value: LabValue): String =
        listOfNotNull(value.value?.let(::number) ?: value.valueText, value.unit).joinToString(" ")

    /**
     * The sheet as a few lines of text: what a chat line's body carries, so an app that
     * does not know the `lab` kind — or a sheet deleted since — still reads as the results.
     */
    fun plainText(report: LabReport, language: Language, limit: Int = Limits.MESSAGE_MAX): String {
        val date = "%02d.%02d.%d".format(report.takenOn.day, report.takenOn.month.ordinal + 1, report.takenOn.year)
        val head = listOfNotNull("🧪 ${title(language)}", date, report.place).joinToString(" · ")
        val lines = report.values.map { value ->
            val extras = listOfNotNull(
                value.flag?.let { flag(it, language) },
                rangeText(value)?.let { "${range(language)} $it" },
            )
            "• ${value.name}: ${result(value)}" + if (extras.isEmpty()) "" else " (${extras.joinToString("; ")})"
        }
        val text = StringBuilder(head)
        for ((index, line) in lines.withIndex()) {
            val more = "\n… +${lines.size - index}"
            if (text.length + 1 + line.length > limit - more.length) {
                text.append(more)
                break
            }
            text.append('\n').append(line)
        }
        return text.toString()
    }
}
