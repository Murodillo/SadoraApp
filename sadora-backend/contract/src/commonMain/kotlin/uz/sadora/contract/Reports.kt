package uz.sadora.contract

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The doctor's report as a PDF: three or six months of what she recorded, the sections she
 * chose, in the language she chose for the doctor who will read it.
 *
 * Made on the server so it looks the same from either phone. One a month is free, Premium
 * has no limit; the doctor's own download from the QR page costs her nothing.
 */
@Serializable
enum class ReportSection {
    @SerialName("cycle") CYCLE,
    @SerialName("symptoms") SYMPTOMS,
    @SerialName("medications") MEDICATIONS,
    @SerialName("labs") LABS,
    @SerialName("conditions") CONDITIONS,
    @SerialName("mood") MOOD,
    @SerialName("pregnancy") PREGNANCY,
    @SerialName("pelvic_floor") PELVIC_FLOOR,
}

@Serializable
data class PdfReportRequest(
    val months: Int = 3,
    val sections: List<ReportSection> = ReportSection.entries,
    val language: Language = Language.UZ,
)

/** What the report screen needs before she presses the button. */
@Serializable
data class ReportOptions(
    /** Her `doctor_report_pdf` allowance now: what is left this month on a free account. */
    val allowance: FeatureEntitlement? = null,
)

/** A file in a message — so far only the report. Fetched by the message id. */
@Serializable
data class MessageDocument(
    val name: String,
    val mimeType: String = "application/pdf",
    val sizeBytes: Int,
)
