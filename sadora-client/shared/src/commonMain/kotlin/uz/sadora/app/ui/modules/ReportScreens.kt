package uz.sadora.app.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.app.data.ReportController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppLanguage
import uz.sadora.app.model.deviceToday
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.rememberFileShareAction
import uz.sadora.contract.Language
import uz.sadora.contract.Limits
import uz.sadora.contract.PdfReportRequest
import uz.sadora.contract.ReportSection

/** What she picked for the report: the same choice on its own screen and in a consultation. */
@Stable
class ReportChoice(language: AppLanguage) {
    var months by mutableStateOf(3)
    var sections by mutableStateOf(ReportSection.entries.toSet())
    var language by mutableStateOf(
        when (language) {
            AppLanguage.Uz -> Language.UZ
            AppLanguage.Ru -> Language.RU
            AppLanguage.En -> Language.EN
        },
    )

    val ready: Boolean get() = sections.isNotEmpty()

    fun toggle(section: ReportSection) {
        sections = if (section in sections) sections - section else sections + section
    }

    fun request() = PdfReportRequest(
        months = months,
        sections = ReportSection.entries.filter { it in sections },
        language = language,
    )
}

/**
 * "Shifokor uchun hisobot": she picks, the server makes the PDF, the share sheet takes it —
 * to Telegram, to print, or to a doctor who is not in the app.
 */
@Composable
fun DoctorReportScreen(
    reports: ReportController,
    language: AppLanguage,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.reports
    val scope = rememberCoroutineScope()
    val share = rememberFileShareAction()
    val choice = remember { ReportChoice(language) }
    LaunchedEffect(Unit) { reports.refresh() }

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)
        ScreenContent {
            item { Text(t.intro, style = Sadora.type.body, color = Sadora.colors.muted) }
            reports.error?.let { failure -> item { ErrorStrip(failure.readable(), onRetry = reports::clearError) } }
            item { ReportForm(choice) }
            item {
                ReportAction(
                    reports = reports,
                    choice = choice,
                    label = t.make,
                    onUpgrade = onUpgrade,
                    onClick = {
                        scope.launch {
                            reports.pdf(choice.request())?.let { bytes ->
                                share(bytes, "SADORA_${t.title.fileWord()}_${deviceToday()}.pdf", "application/pdf")
                            }
                        }
                    },
                )
            }
            item { DisclaimerNote(t.note) }
        }
    }
}

/** Period, sections and language, as chips. */
@Composable
fun ReportForm(choice: ReportChoice) {
    val t = strings.reports
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        CardLabel(t.period)
        ChipFlowRow {
            Limits.REPORT_MONTHS.sorted().forEach { m ->
                SelectChip(t.months(m), selected = choice.months == m, onClick = { choice.months = m })
            }
        }
        CardLabel(t.sections, modifier = Modifier.padding(top = Spacing.xxs))
        ChipFlowRow {
            ReportSection.entries.forEach { section ->
                SelectChip(t.section(section), selected = section in choice.sections, onClick = { choice.toggle(section) })
            }
        }
        if (!choice.ready) Text(t.pickSection, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = Sadora.colors.warning)
        CardLabel(t.language, modifier = Modifier.padding(top = Spacing.xxs))
        ChipFlowRow {
            Language.entries.forEach { language ->
                SelectChip(language.native(), selected = choice.language == language, onClick = { choice.language = language })
            }
        }
    }
}

/**
 * The button, what is left of her month under it, and in its place the way to Premium
 * once the free one is spent.
 */
@Composable
fun ReportAction(
    reports: ReportController,
    choice: ReportChoice,
    label: String,
    onUpgrade: () -> Unit,
    onClick: () -> Unit,
    busy: Boolean = reports.busy,
    locked: Boolean = reports.locked,
) {
    val t = strings.reports
    val c = Sadora.colors
    if (locked) {
        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
            Text(t.lockedTitle, style = Sadora.type.h3, color = c.text)
            Text(t.lockedBody, style = Sadora.type.body, color = c.muted)
            SadoraButton(t.upgrade, onClick = onUpgrade)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs), horizontalAlignment = Alignment.CenterHorizontally) {
        if (busy) {
            Box(Modifier.fillMaxWidth().padding(Spacing.xs), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) }
        } else {
            SadoraButton(label, onClick = onClick, enabled = choice.ready)
        }
        val allowance = reports.allowance
        val line = when {
            allowance == null -> null
            allowance.remainingThisMonth == null -> t.unlimited
            else -> t.allowance(allowance.remainingThisMonth ?: 0)
        }
        line?.let { Text(it, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted) }
    }
}

private fun Language.native(): String = when (this) {
    Language.UZ -> AppLanguage.Uz.native
    Language.RU -> AppLanguage.Ru.native
    Language.EN -> AppLanguage.En.native
}

/** The title as one ASCII word for the file's name: "Shifokor_uchun_hisobot". */
private fun String.fileWord(): String =
    filter { it.isLetterOrDigit() && it.code < 128 || it == ' ' }.trim().replace(' ', '_').ifEmpty { "report" }
