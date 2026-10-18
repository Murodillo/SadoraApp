package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lab results: one sheet from one visit to a laboratory, and the rows printed on it.
 *
 * A sheet is entered by hand or read from photos of it ("Tahlilni skanerlash"); either
 * way she checks every row before it is saved, and the photos are kept with it so her
 * doctor can see the original. Whether a value is low or high is decided by the range
 * the laboratory printed beside it — never by a range of ours, because ranges differ
 * between laboratories, methods and weeks of pregnancy. A row without a range has no flag.
 */
@Serializable
data class LabReport(
    val id: String,
    /** The day the blood was taken, as printed on the sheet. */
    val takenOn: LocalDate,
    /** The laboratory, as she or the sheet names it. */
    val place: String? = null,
    val note: String? = null,
    /** True when the rows came from a photo rather than from her typing. */
    val scanned: Boolean = false,
    val values: List<LabValue> = emptyList(),
    /** The pages photographed, in order; fetched as `…/photos/{index}`. */
    val photos: List<LabPhoto> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val flaggedCount: Int get() = values.count { it.flag == LabFlag.LOW || it.flag == LabFlag.HIGH }
}

/** One page's shape, so a thumbnail can be sized before the picture arrives. */
@Serializable
data class LabPhoto(
    val width: Int,
    val height: Int,
    val mimeType: String = "image/jpeg",
)

/** Where a value sits against the laboratory's own range. */
@Serializable
enum class LabFlag {
    @SerialName("low") LOW,
    @SerialName("normal") NORMAL,
    @SerialName("high") HIGH,
}

/**
 * One row of a sheet.
 *
 * [value] is the number when there is one; [valueText] is what was printed when it is
 * not a number ("manfiy", "1:160"). [markerCode] is set when the row is one of the
 * markers the app knows ([LabMarker]): those get a common name, a short explanation and
 * a chart across sheets. Anything else is kept as printed.
 */
@Serializable
data class LabValue(
    val id: String,
    val markerCode: String? = null,
    /** The marker's common name in her language when known, otherwise as printed. */
    val name: String,
    val value: Double? = null,
    val valueText: String? = null,
    val unit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    /** The range as printed when it is not two numbers. */
    val refText: String? = null,
    val flag: LabFlag? = null,
    /** What the marker is, in two sentences, for a known [markerCode]. */
    val about: String? = null,
)

/** A row as she saves it, or as a scan proposes it. The flag is worked out from the range. */
@Serializable
data class SaveLabValue(
    val markerCode: String? = null,
    val name: String,
    val value: Double? = null,
    val valueText: String? = null,
    val unit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val refText: String? = null,
)

/**
 * A sheet, created or edited. [photos] count only when it is created: a page belongs
 * to the sheet it was read from, so editing changes the rows and the details, never
 * the pages.
 */
@Serializable
data class SaveLabReportRequest(
    val takenOn: LocalDate,
    val place: String? = null,
    val note: String? = null,
    val values: List<SaveLabValue>,
    val photos: List<LabPhotoUpload> = emptyList(),
    val scanned: Boolean = false,
)

/** A page, resized by the phone and sent base64 like a message photo. */
@Serializable
data class LabPhotoUpload(
    val imageBase64: String,
    /** "image/jpeg" or "image/png". */
    val mimeType: String = "image/jpeg",
)

/** Up to [Limits.LAB_PHOTOS_MAX] pages of one sheet, read in one go. Nothing is saved. */
@Serializable
data class LabScanRequest(val photos: List<LabPhotoUpload>)

/**
 * What the model read off the pages, for her to check.
 *
 * [isLabReport] is its own answer because a photo of a prescription, a receipt or a
 * cat is common, and should say so rather than produce rows. [message] is the sentence
 * shown when it is false, or when the pages were too blurred to read.
 */
@Serializable
data class LabScanResult(
    val isLabReport: Boolean,
    val takenOn: LocalDate? = null,
    val place: String? = null,
    val values: List<SaveLabValue> = emptyList(),
    val message: String? = null,
)

/** A marker the app knows: the picker for typing a row in, and the explanations. */
@Serializable
data class LabMarker(
    val code: String,
    val name: String,
    /** The unit most laboratories here print it in; a suggestion, not a rule. */
    val unit: String,
    val about: String,
    /** The group it is listed under: "blood", "iron", "thyroid", "hormones", "metabolic". */
    val group: String,
)

/** One known marker across her sheets, oldest first: one chart. */
@Serializable
data class LabTrend(
    val markerCode: String,
    val name: String,
    val about: String,
    val points: List<LabTrendPoint>,
) {
    val latest: LabTrendPoint? get() = points.lastOrNull()
}

@Serializable
data class LabTrendPoint(
    val reportId: String,
    val takenOn: LocalDate,
    val value: Double,
    val unit: String? = null,
    val refLow: Double? = null,
    val refHigh: Double? = null,
    val flag: LabFlag? = null,
)

/** The section's front page: one trend per known marker she has a number for. */
@Serializable
data class LabOverview(
    val trends: List<LabTrend> = emptyList(),
    /** False when no model is configured: the app then offers typing only. */
    val scanAvailable: Boolean = false,
    /**
     * Her `lab_scan` allowance as it stands now — what the screen says is left this month,
     * from the limits the operator set rather than a number written into the app.
     */
    val scanAllowance: FeatureEntitlement? = null,
)

object LabRanges {
    /**
     * Low, normal or high against the printed range. One bound is enough — "< 5.0" is a
     * range with only a top — and no bound at all is no flag, not "normal".
     */
    fun flagOf(value: Double?, low: Double?, high: Double?): LabFlag? {
        if (value == null || (low == null && high == null)) return null
        if (low != null && high != null && low > high) return null
        return when {
            low != null && value < low -> LabFlag.LOW
            high != null && value > high -> LabFlag.HIGH
            else -> LabFlag.NORMAL
        }
    }
}
