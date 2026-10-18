package uz.sadora.server.share

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.Language
import uz.sadora.contract.Limits
import uz.sadora.contract.ReportOptions
import uz.sadora.contract.PdfReportRequest
import uz.sadora.contract.ReportSection
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.health.HealthAccess

/** A finished PDF and the name it travels under. */
class ReportFile(val name: String, val bytes: ByteArray)

/**
 * The doctor's PDF report, for her: one a month on a free account, any number with Premium.
 *
 * The allowance is checked before the work and spent only when the file exists — a report
 * that failed to render is not one she used. The doctor's download from the QR page goes
 * through [ShareService.openPdf] and costs her nothing.
 */
class ReportService(
    private val shares: ShareService,
    private val access: HealthAccess,
) {

    suspend fun options(userId: Uuid): ReportOptions {
        val user = access.requireUser(userId)
        return ReportOptions(allowance = access.feature(userId, FeatureKeys.DOCTOR_REPORT_PDF, user.timezone))
    }

    suspend fun pdf(userId: Uuid, request: PdfReportRequest): ReportFile {
        if (request.months !in Limits.REPORT_MONTHS) throw ValidationException("months", "Noto'g'ri format")
        if (request.sections.isEmpty()) throw ValidationException("sections", "Kamida bitta bo'limni tanlang")
        val user = access.requireFeature(userId, FeatureKeys.DOCTOR_REPORT_PDF)
        val today = now().dayIn(user.timezone)
        val from = today.minus(request.months, DateTimeUnit.MONTH)
        val days = from.daysUntil(today) + 1
        val summary = shares.summaryFor(userId, request.language, windowDays = days)
        val bytes = ReportPdf.render(summary, request.sections.toSet(), from, today, request.language)
        access.consume(userId, FeatureKeys.DOCTOR_REPORT_PDF, user.timezone)
        return ReportFile(ReportPdf.fileName(summary, today), bytes)
    }

    companion object {
        val ALL: Set<ReportSection> = ReportSection.entries.toSet()
        fun language(raw: String?): Language? = Language.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
}

/** The chat line a report travels with: what an app that does not know the kind shows. */
object ReportWords {
    fun line(months: Int, language: Language): String = when (language) {
        Language.UZ -> "📄 Shifokor uchun hisobot · $months oy (PDF)"
        Language.RU -> "📄 Отчёт для врача · $months мес. (PDF)"
        Language.EN -> "📄 Report for the doctor · $months months (PDF)"
    }
}
