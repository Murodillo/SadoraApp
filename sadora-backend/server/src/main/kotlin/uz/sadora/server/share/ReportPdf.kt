package uz.sadora.server.share

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder
import java.io.ByteArrayOutputStream
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.Epds
import uz.sadora.contract.LabFlag
import uz.sadora.contract.Language
import uz.sadora.contract.ReportSection
import uz.sadora.server.lab.LabWords

/**
 * The doctor's report: the same document as the QR page, laid out for paper.
 *
 * Built as strict XHTML — openhtmltopdf reads XML, not tag soup — and set in the bundled
 * Noto Sans, so the Cyrillic of a Russian report and the apostrophes of an Uzbek one print
 * the same on a laptop and in the production container. The words are the QR page's own
 * ([PageText]); only the few that belong to paper are here.
 */
object ReportPdf {

    fun render(summary: DoctorSummary, sections: Set<ReportSection>, from: LocalDate, to: LocalDate, language: Language): ByteArray {
        val html = html(summary, sections, from, to, language)
        val out = ByteArrayOutputStream()
        PdfRendererBuilder().apply {
            useFastMode()
            FONTS.forEach { (resource, weight) ->
                useFont({ ReportPdf::class.java.getResourceAsStream(resource) }, FAMILY, weight, BaseRendererBuilder.FontStyle.NORMAL, true)
            }
            withHtmlContent(html, null)
            toStream(out)
            run()
        }
        return out.toByteArray()
    }

    /** The file name she sees in the share sheet and the doctor sees in the chat. */
    fun fileName(summary: DoctorSummary, to: LocalDate): String {
        val name = summary.person.name.filter { it.isLetterOrDigit() }.take(30).ifEmpty { "SADORA" }
        return "SADORA_${name}_$to.pdf"
    }

    internal fun html(summary: DoctorSummary, sections: Set<ReportSection>, from: LocalDate, to: LocalDate, language: Language): String {
        val t = PageText.of(language)
        val w = Words(language)
        val p = summary.person
        return buildString {
            append("<html><head><meta charset=\"utf-8\"/><title>").append(x(w.title)).append("</title><style>").append(CSS).append("</style></head><body>")

            // ---- who, and which months
            append("<div class=\"brand\">SADORA</div>")
            append("<h1>").append(x(w.title)).append("</h1>")
            append("<p class=\"sub\">").append(x(w.period(date(from), date(to)))).append("</p>")
            append("<table class=\"facts\"><tr>")
            cell(w.patient, p.name)
            cell(t.birthDate, p.birthDate?.let(::date)?.let { d -> p.age?.let { "$d (${t.years(it)})" } ?: d })
            cell(w.stage, t.stage(p.lifeStage))
            append("</tr><tr>")
            cell(t.height, p.heightCm?.let { "$it ${t.cm}" })
            cell(t.weight, p.weightKg?.let { "$it ${t.kg}" })
            cell(t.generatedAt, at(summary.generatedAt))
            append("</tr></table>")
            append("<p class=\"note\">").append(x(t.disclaimer)).append("</p>")

            // ---- the cycle
            val cycle = summary.cycle
            if (ReportSection.CYCLE in sections && cycle != null) {
                section(t.cycleTitle) {
                    val history = cycle.history
                    val prediction = history.prediction
                    append("<table class=\"facts\"><tr>")
                    cell(t.averageCycle, history.averageCycleLength?.let { t.days(it) })
                    cell(t.averagePeriod, history.averagePeriodLength?.let { t.days(it) })
                    cell(
                        t.range,
                        if (history.shortestCycle != null && history.longestCycle != null) "${history.shortestCycle}–${history.longestCycle} ${t.daysWord}" else null,
                    )
                    cell(
                        t.nextPeriod,
                        if (prediction.nextPeriodEarliest != null && prediction.nextPeriodLatest != null) {
                            "${date(prediction.nextPeriodEarliest!!)} – ${date(prediction.nextPeriodLatest!!)} (${t.estimated})"
                        } else {
                            prediction.nextPeriodStart?.let { "${date(it)} (${t.estimated})" }
                        },
                    )
                    append("</tr></table>")
                    val inWindow = history.cycles.filter { it.startedOn >= from }
                    if (inWindow.isEmpty()) {
                        append("<p class=\"muted\">").append(x(t.noCycles)).append("</p>")
                    } else {
                        append("<table class=\"grid\"><thead><tr><th>${x(t.cycleStart)}</th><th>${x(t.cycleLength)}</th><th>${x(t.periodLength)}</th></tr></thead><tbody>")
                        inWindow.forEach { c ->
                            val long = c.cycleLength > 35
                            append("<tr${if (long) " class=\"warn\"" else ""}><td>${date(c.startedOn)}</td><td>${x(t.days(c.cycleLength))}</td><td>${c.periodLength?.let { x(t.days(it)) } ?: "—"}</td></tr>")
                        }
                        append("</tbody></table>")
                    }
                }
            }

            // ---- pregnancy and the stage tools that belong to it
            val records = summary.stageRecords
            if (ReportSection.PREGNANCY in sections && (summary.pregnancy != null || records?.kickCounts?.isNotEmpty() == true || records?.contractions != null || records?.feeding != null)) {
                section(t.pregnancyTitle) {
                    summary.pregnancy?.let { g ->
                        append("<table class=\"facts\"><tr>")
                        cell(t.week, g.week?.toString())
                        cell(t.dueDate, g.dueDate?.let(::date))
                        cell(t.childBirth, g.childBirthDate?.let(::date))
                        append("</tr></table>")
                        if (g.lessMovementDays.isNotEmpty()) {
                            append("<p class=\"warn\">${x(t.lessMovement)}: ${g.lessMovementDays.joinToString(", ") { date(it) }}</p>")
                        }
                    }
                    records?.kickCounts?.takeIf { it.isNotEmpty() }?.let { kicks ->
                        append("<h3>${x(t.kicksTitle)}</h3><table class=\"grid\"><thead><tr><th>${x(t.date)}</th><th>${x(t.kicks)}</th><th>${x(t.duration)}</th></tr></thead><tbody>")
                        kicks.forEach { k ->
                            append("<tr${if (k.isSlow) " class=\"warn\"" else ""}><td>${at(k.at)}</td><td>${k.kicks}</td><td>${minutesSeconds(k.durationSeconds)}</td></tr>")
                        }
                        append("</tbody></table>")
                    }
                    records?.contractions?.let { c ->
                        append("<h3>${x(t.contractionsTitle)}</h3><table class=\"facts\"><tr>")
                        cell(t.count, c.count.toString())
                        cell(t.averageDuration, c.averageDurationSeconds?.let(::minutesSeconds))
                        cell(t.averageInterval, c.averageIntervalSeconds?.let(::minutesSeconds))
                        append("</tr></table>")
                    }
                    records?.feeding?.let { f ->
                        append("<h3>${x(t.feedingTitle)}</h3><table class=\"facts\"><tr>")
                        cell(t.feedsPerDay, one(f.feeds.toDouble() / f.windowDays))
                        cell(t.breastFeeds, f.breastFeeds.toString() + (f.averageBreastMinutes?.let { " · ${t.averageMinutes(it)}" } ?: ""))
                        cell(t.bottleFeeds, if (f.bottleFeeds > 0) "${f.bottleFeeds} · ${f.bottleMl} ml" else null)
                        append("</tr></table>")
                    }
                }
            }

            // ---- symptoms, and hot flushes with them
            if (ReportSection.SYMPTOMS in sections && (summary.symptomCounts.isNotEmpty() || records?.hotFlushes != null)) {
                section(t.symptomsTitle) {
                    if (summary.symptomCounts.isNotEmpty()) {
                        append("<table class=\"grid\"><thead><tr><th>${x(t.symptoms)}</th><th>${x(w.days)}</th></tr></thead><tbody>")
                        summary.symptomCounts.take(20).forEach { s -> append("<tr><td>${x(s.label)}</td><td>${s.days}</td></tr>") }
                        append("</tbody></table>")
                    }
                    records?.hotFlushes?.let { h ->
                        append("<h3>${x(t.hotFlushTitle)}</h3><table class=\"facts\"><tr>")
                        cell(t.count, h.count.toString())
                        cell(t.perDay, one(h.count.toDouble() / h.windowDays))
                        cell(t.strong, h.strong.toString())
                        append("</tr></table>")
                    }
                }
            }

            // ---- conditions and the pain diary
            val conditions = summary.conditions
            if (ReportSection.CONDITIONS in sections && conditions != null) {
                section(t.conditionsTitle) {
                    append("<table class=\"grid\"><tbody>")
                    conditions.conditions.forEach { c ->
                        val name = uz.sadora.server.condition.ConditionWords.info(c.condition, language).name
                        append("<tr><td>${x(name)}</td><td>${x(t.conditionStatus(c.status))}${c.diagnosedOn?.let { " · " + date(it) } ?: ""}</td></tr>")
                    }
                    append("</tbody></table>")
                    conditions.pain?.let { pain ->
                        append("<h3>${x(t.painTitle(pain.days))}</h3><table class=\"facts\"><tr>")
                        cell(t.painDays, "${pain.painDays} / ${pain.daysLogged}")
                        cell(t.painAverage, pain.averageLevel?.let { one(it) + " / 10" })
                        cell(t.painMax, "${pain.maxLevel} / 10")
                        append("</tr><tr>")
                        cell(t.painkillerDays, pain.painkillerDays.toString())
                        cell(t.missedDays, pain.missedDays.toString())
                        cell(null, null)
                        append("</tr></table>")
                        if (pain.byKind.isNotEmpty()) {
                            append("<p>${x(t.painKinds)}: ${pain.byKind.entries.sortedByDescending { it.value }.joinToString(", ") { (k, n) -> x(t.painKind(k)) + " — " + n }}</p>")
                        }
                        if (pain.byArea.isNotEmpty()) {
                            append("<p>${x(t.painAreas)}: ${pain.byArea.entries.sortedByDescending { it.value }.joinToString(", ") { (a, n) -> x(t.painArea(a)) + " — " + n }}</p>")
                        }
                    }
                }
            }

            // ---- pelvic floor
            val pf = summary.pelvicFloor
            if (ReportSection.PELVIC_FLOOR in sections && pf != null) {
                section(t.pelvicTitle) {
                    append("<table class=\"facts\"><tr>")
                    cell(t.kegelProgram(pf.program), "${t.kegelLevel} ${pf.level}")
                    cell(t.kegelSessions, pf.sessions.toString())
                    cell(t.kegelDays, pf.activeDays.toString())
                    cell(null, null)
                    append("</tr></table>")
                    if (pf.checks.isNotEmpty()) {
                        append("<h3>${x(t.leakChecks)}</h3>")
                        append("<table class=\"grid\"><thead><tr><th>${x(t.date)}</th><th>${x(t.leakFrequency)}</th><th>${x(t.leakAmount)}</th><th>${x(t.leakWhen)}</th><th>${x(t.leakBother)}</th></tr></thead><tbody>")
                        pf.checks.forEach { c ->
                            append("<tr><td>${date(c.takenOn)}</td><td>${x(t.leakFrequencyWord(c.frequency))}</td><td>${x(t.leakAmountWord(c.amount))}</td>")
                            append("<td>${x(c.situations.joinToString(", ") { t.leakSituation(it) }.ifEmpty { "—" })}</td><td>${c.bother} / 10</td></tr>")
                        }
                        append("</tbody></table><p class=\"muted\">${x(t.leakNote)}</p>")
                    }
                }
            }

            // ---- medications
            if (ReportSection.MEDICATIONS in sections && summary.medications.isNotEmpty()) {
                section(t.medsTitle) {
                    append("<table class=\"grid\"><thead><tr><th>${x(t.medName)}</th><th>${x(t.dose)}</th><th>${x(t.schedule)}</th><th>${x(t.period)}</th><th>${x(t.adherence)}</th></tr></thead><tbody>")
                    summary.medications.forEach { m ->
                        val dose = listOfNotNull(m.dosage, m.unit).joinToString(" ")
                        val period = date(m.startedOn) + (m.endedOn?.let { " – " + date(it) } ?: "")
                        val adherence = m.adherencePercent?.let { "$it% (${m.takenCount}/${m.takenCount + m.skippedCount})" } ?: "—"
                        append("<tr><td>${x(m.name)}</td><td>${x(dose)}</td><td>${x(m.schedule.describe() + " · " + t.foodRelation(m.foodRelation))}</td><td>$period</td><td class=\"nowrap\">$adherence</td></tr>")
                    }
                    append("</tbody></table>")
                }
            }

            // ---- lab results
            if (ReportSection.LABS in sections && summary.labs.isNotEmpty()) {
                section(t.labsTitle) {
                    append("<p class=\"muted\">").append(x(t.labsNote)).append("</p>")
                    summary.labs.filter { it.takenOn >= from }.ifEmpty { summary.labs.take(1) }.forEach { report ->
                        append("<h3>${x(listOfNotNull(date(report.takenOn), report.place).joinToString(" · "))}</h3>")
                        append("<table class=\"grid\"><thead><tr><th>${x(t.labMarker)}</th><th>${x(t.labResult)}</th><th>${x(t.labRange)}</th><th></th></tr></thead><tbody>")
                        report.values.forEach { v ->
                            val flagged = v.flag == LabFlag.LOW || v.flag == LabFlag.HIGH
                            append("<tr${if (flagged) " class=\"warn\"" else ""}><td>${x(v.name)}</td><td>${x(LabWords.result(v))}</td>")
                            append("<td>${x(LabWords.rangeText(v).orEmpty())}</td><td>${x(v.flag?.let { LabWords.flag(it, language) }.orEmpty())}</td></tr>")
                        }
                        append("</tbody></table>")
                    }
                }
            }

            // ---- mood
            val mind = summary.mind
            if (ReportSection.MOOD in sections && (mind?.daysLogged ?: 0) + (records?.moodScreens?.size ?: 0) > 0) {
                section(t.mindTitle) {
                    mind?.takeIf { it.daysLogged > 0 }?.let { m ->
                        append("<table class=\"facts\"><tr>")
                        cell(t.daysLogged, "${m.daysLogged} / ${m.windowDays}")
                        cell(t.averageMood, m.averageMood?.let { one(it) + " / 5" })
                        cell(t.averageEnergy, m.averageEnergy?.let { one(it) + " / 5" })
                        cell(t.averageStress, m.averageStress?.let { one(it) + " / 5" })
                        append("</tr></table>")
                    }
                    records?.moodScreens?.takeIf { it.isNotEmpty() }?.let { screens ->
                        append("<h3>${x(t.epdsTitle)}</h3>")
                        if (screens.any { it.selfHarm }) append("<p class=\"warn\">${x(t.epdsSelfHarm)}</p>")
                        append("<table class=\"grid\"><thead><tr><th>${x(t.date)}</th><th>${x(t.score)}</th><th></th></tr></thead><tbody>")
                        screens.forEach { s ->
                            val band = when {
                                s.score >= Epds.LIKELY -> t.epdsLikely
                                s.score >= Epds.POSSIBLE -> t.epdsPossible
                                else -> t.epdsLow
                            }
                            val warn = s.score >= Epds.LIKELY || s.selfHarm
                            append("<tr${if (warn) " class=\"warn\"" else ""}><td>${date(s.takenOn)}</td><td>${s.score} / 30</td><td>${x(band)}</td></tr>")
                        }
                        append("</tbody></table><p class=\"muted\">${x(t.epdsSource)}</p>")
                    }
                }
            }

            append("<p class=\"foot\">").append(x(w.footer)).append("</p>")
            append("</body></html>")
        }
    }

    // ---------------------------------------------------------------- pieces

    private inline fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        append("<div class=\"section\"><h2>").append(x(title)).append("</h2>")
        body()
        append("</div>")
    }

    /** One label and its value in a facts row; an empty cell keeps the columns even. */
    private fun StringBuilder.cell(label: String?, value: String?) {
        append("<td>")
        if (label != null && value != null) append("<span class=\"label\">").append(x(label)).append("</span><br/><b>").append(x(value)).append("</b>")
        append("</td>")
    }

    /** XML text: the five characters XML will not take as they are. */
    private fun x(text: String): String = buildString(text.length) {
        text.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\t') append(ch)
            }
        }
    }

    private fun date(d: LocalDate): String = "%02d.%02d.%d".format(d.day, d.month.ordinal + 1, d.year)

    private fun at(instant: kotlin.time.Instant): String {
        val local = instant.toLocalDateTime(TimeZone.of(uz.sadora.server.core.DEFAULT_TIMEZONE))
        return date(local.date) + " %02d:%02d".format(local.hour, local.minute)
    }

    private fun one(value: Double): String = "%.1f".format(value)

    private fun minutesSeconds(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    /** The words that belong to paper rather than to the page. */
    private class Words(private val language: Language) {
        private fun pick(uz: String, ru: String, en: String) = when (language) {
            Language.UZ -> uz
            Language.RU -> ru
            Language.EN -> en
        }
        val title get() = pick("Shifokor uchun hisobot", "Отчёт для врача", "Report for the doctor")
        val patient get() = pick("Bemor", "Пациентка", "Patient")
        val stage get() = pick("Bosqich", "Этап", "Stage")
        val days get() = pick("Kunlar", "Дней", "Days")
        val footer get() = pick(
            "Ma'lumotlarni bemorning o'zi SADORA ilovasida yuritadi. SADORA — ayollar salomatligi ilovasi.",
            "Данные ведёт сама пациентка в приложении SADORA. SADORA — приложение о женском здоровье.",
            "The patient keeps these records herself in the SADORA app. SADORA — a women's health app.",
        )
        fun period(from: String, to: String) = pick("Davr: $from – $to", "Период: $from – $to", "Period: $from – $to")
    }

    private const val FAMILY = "Noto Sans"
    private val FONTS = listOf("/fonts/NotoSans-Regular.ttf" to 400, "/fonts/NotoSans-Bold.ttf" to 700)

    private val CSS = """
        @page { size: A4; margin: 16mm 14mm 18mm 14mm;
          @bottom-center { content: "SADORA · " counter(page) " / " counter(pages); font-family: 'Noto Sans'; font-size: 8pt; color: #8a86a0; } }
        body { font-family: 'Noto Sans'; font-size: 9.5pt; color: #1a1630; line-height: 1.4; }
        .brand { font-weight: 700; letter-spacing: 3px; color: #6247e0; font-size: 9pt; }
        h1 { font-size: 17pt; margin: 2mm 0 0 0; }
        .sub { color: #6f6a8a; margin: 0 0 3mm 0; }
        h2 { font-size: 12pt; color: #6247e0; border-bottom: 1px solid #e3def7; padding-bottom: 1mm; margin: 5mm 0 2mm 0; }
        h2, h3 { page-break-after: avoid; -fs-page-break-min-height: 28mm; }
        .nowrap { white-space: nowrap; }
        h3 { font-size: 10pt; margin: 3mm 0 1mm 0; }
        .section { page-break-inside: auto; }
        table { width: 100%; border-collapse: collapse; margin: 1mm 0 2mm 0; }
        table.facts td { width: 25%; vertical-align: top; padding: 1mm 2mm 1mm 0; }
        .label { color: #6f6a8a; font-size: 8pt; }
        table.grid th { text-align: left; font-size: 8pt; color: #6f6a8a; border-bottom: 1px solid #e3def7; padding: 1mm 1.5mm; }
        table.grid td { border-bottom: 1px solid #f0edfa; padding: 1mm 1.5mm; vertical-align: top; }
        tr { page-break-inside: avoid; }
        tr.warn td, p.warn { color: #9a6200; }
        .note { background: #f4f1ff; padding: 2mm 3mm; font-size: 8.5pt; color: #4b4566; }
        .muted { color: #6f6a8a; font-size: 8.5pt; }
        .foot { margin-top: 6mm; font-size: 8pt; color: #8a86a0; }
    """.trimIndent()
}
