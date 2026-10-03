package uz.sadora.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.data.NotificationsController
import uz.sadora.app.data.SadoraController
import uz.sadora.app.data.ShareController
import uz.sadora.app.ui.components.rememberShareAction
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.NotificationCategory
import kotlinx.serialization.json.Json
import uz.sadora.app.model.AppLanguage
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Goal
import uz.sadora.app.model.LifeStage
import uz.sadora.app.model.deviceToday
import uz.sadora.app.data.recountStageWeeks
import uz.sadora.app.ui.onboarding.CalendarPicker
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.ConsentRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.OptionRow
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraSwitch
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.data.readable
import uz.sadora.app.ui.components.acceptDigits
import uz.sadora.app.ui.components.acceptText
import uz.sadora.app.ui.components.numberError
import uz.sadora.app.ui.components.requiredTextError
import uz.sadora.app.ui.components.typedDateError
import uz.sadora.contract.Limits

/**
 * The settings detail screens reachable from Profile.
 *
 * They edit the same [AppState] the onboarding steps wrote to, so changing life
 * stage here reshapes the Journey tab exactly as it would during onboarding.
 */
@Composable
fun SettingsDetailScreen(
    route: Route,
    state: AppState,
    controller: SadoraController,
    notifications: NotificationsController,
    share: ShareController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    onSignedOut: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (route) {
            Route.PersonalDetails -> PersonalDetails(state, controller, onClose)
            Route.GoalsSettings -> GoalsSettings(state, controller, onClose)
            Route.LifeStageSettings -> LifeStageSettings(state, controller, onClose)
            Route.Notifications -> NotificationSettings(state, notifications, onClose)
            Route.PrivacySecurity -> PrivacySettings(state, controller, share, onClose, onOpen, onSignedOut, onToast)
            Route.LanguageSettings -> LanguageSettings(state, controller, onClose)
            Route.About -> About(state, onClose)
            // Every settings route is listed above; an unknown one is a programming
            // error worth seeing, not a screen to fall back to quietly.
            else -> error("Not a settings route: $route")
        }
    }
}

/**
 * Saves through the controller and closes only when the save lands.
 *
 * Closing regardless would leave the screen showing an edit the server never took.
 */
@Composable
private fun SaveButton(
    controller: SadoraController,
    onSaved: () -> Unit,
    label: String = strings.common.save,
    enabled: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    controller.error?.let { ErrorStrip(it.readable()) }
    SadoraButton(
        if (controller.busy) strings.common.saving else label,
        enabled = enabled && !controller.busy,
        onClick = { scope.launch { if (controller.saveProfile()) onSaved() } },
    )
}

@Composable
private fun PersonalDetails(state: AppState, controller: SadoraController, onClose: () -> Unit) {
    val t = strings.settings
    val nameError = requiredTextError(state.name, Limits.NAME_MAX)
    val birthError = typedDateError(state.birthDate, allowFuture = false)
    val heightError = numberError(state.heightCm, Limits.HEIGHT_CM)
    val weightError = numberError(state.weightKg, Limits.WEIGHT_KG)
    // Save is refused for exactly what the server would refuse, so the round trip that
    // loses the field the reason belongs to never happens.
    val valid = state.name.isNotBlank() &&
        listOf(nameError, birthError, heightError, weightError).all { it == null }

    SadoraTopBar(t.personalTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                SadoraTextField(
                    state.name,
                    { state.name = acceptText(it, Limits.NAME_MAX) },
                    label = t.name,
                    error = nameError,
                )
                SadoraTextField(
                    state.birthDate,
                    { state.birthDate = acceptText(it, DateFieldMax) },
                    label = t.birthDate,
                    placeholder = "14.03.1994",
                    error = birthError,
                    keyboardType = KeyboardType.Number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraTextField(
                        state.heightCm,
                        { state.heightCm = acceptDigits(it, 3) },
                        label = t.height,
                        suffix = t.centimetres,
                        error = heightError,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraTextField(
                        state.weightKg,
                        { state.weightKg = acceptDigits(it, 3) },
                        label = t.weight,
                        suffix = t.kilograms,
                        error = weightError,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item { DisclaimerNote(t.weightNote) }
        item { SaveButton(controller, onClose, enabled = valid) }
    }
}

/** `27.08.2026` and its lenient variants; nothing longer is a date. */
private const val DateFieldMax = 10

@Composable
private fun GoalsSettings(state: AppState, controller: SadoraController, onClose: () -> Unit) {
    val t = strings.settings
    SadoraTopBar(t.goalsTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                CardLabel(t.goalsChosen(state.goals.size))
                ChipFlowRow {
                    Goal.entries.forEach { goal ->
                        SelectChip(
                            label = strings.common.goal(goal),
                            selected = goal in state.goals,
                            onClick = { state.toggleGoal(goal) },
                        )
                    }
                }
            }
        }
        item { SaveButton(controller, onClose) }
    }
}

/**
 * Her stage, and for pregnancy and postpartum the date the weeks are counted from.
 *
 * The choice is held here until Save. Tapping a stage used to rebuild the Journey tab at
 * once, before there was a date to count from — so a switch to pregnancy showed week 24
 * to everyone, and there was no way to give the due date afterwards.
 */
@Composable
private fun LifeStageSettings(state: AppState, controller: SadoraController, onClose: () -> Unit) {
    val t = strings.settings
    val stages = strings.stages
    val o = strings.onboarding
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val today = remember { deviceToday() }

    var chosen by remember { mutableStateOf(state.lifeStage) }
    val dueRange = today..today.plus(10, DateTimeUnit.MONTH)
    val bornRange = today.minus(24, DateTimeUnit.MONTH)..today
    // A date is offered back only while it still fits the question. A due date left over
    // from an earlier pregnancy was taken silently — Save was already enabled, the
    // calendar opened on this month and showed nothing chosen, and a new pregnancy began
    // at whatever week the old date gave.
    var due by remember { mutableStateOf(state.dueDate?.takeIf { it in dueRange }) }
    var born by remember { mutableStateOf((state.childBirthDate ?: state.babyBirthDate)?.takeIf { it in bornRange }) }
    val ready = when (chosen) {
        LifeStage.Pregnancy -> due != null
        LifeStage.Postpartum -> born != null
        else -> true
    }

    SadoraTopBar(t.lifeStageTitle, onBack = onClose)
    ScreenContent {
        items(LifeStage.entries.size) { index ->
            val stage = LifeStage.entries[index]
            OptionRow(
                title = stages.title(stage),
                subtitle = stages.subtitle(stage),
                leading = stage.glyph,
                selected = chosen == stage,
                onClick = { chosen = stage },
            )
        }
        if (chosen == LifeStage.Pregnancy) {
            item {
                SadoraCard {
                    CardLabel(o.dueDateTitle)
                    Text(o.dueDateSubtitle, style = Sadora.type.body, color = c.muted)
                    CalendarPicker(
                        isSelected = { it == due },
                        onSelect = { due = it },
                        today = today,
                        monthsBack = 0,
                        monthsForward = 9,
                        range = dueRange,
                    )
                    due?.let { ChosenDate(strings.dates.dayMonthYear(it)) }
                }
            }
        }
        if (chosen == LifeStage.Postpartum) {
            item {
                SadoraCard {
                    CardLabel(o.birthDateTitle)
                    Text(o.birthDateSubtitle, style = Sadora.type.body, color = c.muted)
                    CalendarPicker(
                        isSelected = { it == born },
                        onSelect = { born = it },
                        today = today,
                        monthsBack = 12,
                        range = bornRange,
                    )
                    born?.let { ChosenDate(strings.dates.dayMonthYear(it)) }
                }
            }
        }
        item { DisclaimerNote(t.lifeStageNote) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                controller.error?.let { ErrorStrip(it.readable()) }
                SadoraButton(
                    if (controller.busy) strings.common.saving else strings.common.save,
                    enabled = ready && !controller.busy,
                    onClick = {
                        val previous = Triple(state.lifeStage, state.dueDate, state.childBirthDate)
                        state.lifeStage = chosen
                        when (chosen) {
                            LifeStage.Pregnancy -> state.dueDate = due
                            LifeStage.Postpartum -> state.childBirthDate = born
                            else -> Unit
                        }
                        scope.launch {
                            if (controller.saveProfile(withStageDate = true)) {
                                state.recountStageWeeks()
                                state.stageRevision++
                                onClose()
                            } else {
                                // Refused: the app goes back to the stage the server still has.
                                state.lifeStage = previous.first
                                state.dueDate = previous.second
                                state.childBirthDate = previous.third
                            }
                        }
                    },
                )
            }
        }
    }
}

/** "Tanlangan sana: 15-yanvar 2027" — the picker may be on another month than the choice. */
@Composable
private fun ChosenDate(date: String) {
    Text(strings.settings.chosenDate(date), style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = Sadora.colors.text)
}

@Composable
private fun NotificationSettings(state: AppState, notifications: NotificationsController, onClose: () -> Unit) {
    val t = strings.settings
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { notifications.load() }
    SadoraTopBar(t.notificationSettingsTitle, onBack = onClose)
    ScreenContent {
        item {
            notifications.error?.let { ErrorStrip(it.readable()) }
            SadoraCard {
                // Each switch is one category on the server — the same settings the
                // scheduler reads before it sends — and saves the moment it moves.
                //
                // A period reminder only means something while there is a period to
                // predict; offered to someone pregnant it reads as the app not knowing her.
                listOfNotNull(
                    Triple(t.medReminder, t.medReminderNote, NotificationCategory.MED_REMINDER),
                    Triple(t.cycleReminder, t.cycleReminderNote, NotificationCategory.CYCLE)
                        .takeIf { state.lifeStage.predictsCycle },
                    Triple(t.waterReminder, t.waterReminderNote, NotificationCategory.WATER),
                    Triple(t.aiSummary, t.aiSummaryNote, NotificationCategory.INSIGHT),
                ).forEach { (title, note, category) ->
                    ToggleRow(title, note, notifications.isEnabled(category)) { enabled ->
                        scope.launch { notifications.setCategory(category, enabled) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Sadora.type.h3, color = c.text)
            Text(subtitle, style = Sadora.type.body, color = c.muted)
        }
        SadoraSwitch(checked, onCheckedChange)
    }
}

@Composable
private fun PrivacySettings(
    state: AppState,
    controller: SadoraController,
    share: ShareController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    onSignedOut: () -> Unit,
    onToast: (String) -> Unit,
) {
    val t = strings.settings
    val scope = rememberCoroutineScope()
    val shareAction = rememberShareAction()
    var confirmDelete by remember { mutableStateOf(false) }

    // Show what the server actually has, not what this device last set.
    LaunchedEffect(Unit) { controller.loadConsents() }

    SadoraTopBar(t.privacyTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                ConsentRow(
                    t.consentHealth,
                    t.consentHealthNote,
                    state.consentStoreHealth,
                    { state.consentStoreHealth = it },
                    required = true,
                )
                ConsentRow(
                    t.consentAi,
                    t.consentAiNote,
                    state.consentAiInsights,
                    { state.consentAiInsights = it },
                )
                ConsentRow(
                    t.consentAnalytics,
                    t.consentAnalyticsNote,
                    state.consentAnalytics,
                    { state.consentAnalytics = it },
                )
            }
        }
        item {
            controller.error?.let { ErrorStrip(it.readable()) }
            SadoraButton(
                if (controller.busy) strings.common.saving else t.saveConsents,
                enabled = !controller.busy,
                onClick = { scope.launch { controller.saveConsents() } },
            )
        }

        item {
            SadoraCard {
                CardLabel(t.legalDocuments)
                SadoraButton(t.terms, { onOpen(Route.Terms) }, tone = ButtonTone.Secondary)
                SadoraButton(
                    t.privacyPolicy,
                    { onOpen(Route.PrivacyPolicy) },
                    tone = ButtonTone.Secondary,
                )
            }
        }

        item {
            SadoraCard {
                CardLabel(t.yourData)
                // Her export is the same document the doctor page renders, as JSON,
                // handed to the system share sheet so she chooses where it goes.
                SadoraButton(
                    t.exportData,
                    onClick = {
                        scope.launch {
                            val export = share.loadExport(state.language.code.lowercase())
                            if (export == null) {
                                onToast(t.exportFailed)
                            } else {
                                shareAction(exportJson.encodeToString(DoctorSummary.serializer(), export))
                                onToast(t.exportReady)
                            }
                        }
                    },
                    tone = ButtonTone.Secondary,
                    enabled = !share.busy,
                )
                SadoraButton(
                    t.deleteAccount,
                    { confirmDelete = true },
                    tone = ButtonTone.Destructive,
                )
            }
        }
    }

    SadoraDialog(
        visible = confirmDelete,
        title = t.deleteAccountConfirm,
        body = t.deleteAccountBody,
        confirmText = strings.common.delete,
        onConfirm = {
            confirmDelete = false
            scope.launch {
                // The server clears the session on success, so the app must leave too.
                if (controller.deleteAccount(reason = null)) onSignedOut()
            }
        },
        onDismiss = { confirmDelete = false },
    )
}

/**
 * The language picker.
 *
 * The choice takes effect the moment it is tapped — every screen behind this one is
 * already redrawn by the time she goes back — and only then is it sent to the server,
 * where it decides the language of an email or a push. A save that fails says so and
 * keeps her choice: the app is already in that language, and undoing it under her
 * would be the surprising thing.
 */
@Composable
private fun LanguageSettings(state: AppState, controller: SadoraController, onClose: () -> Unit) {
    val c = Sadora.colors
    val t = strings.settings
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }

    SadoraTopBar(t.languageTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                Text(t.languageNote, style = Sadora.type.body, color = c.muted)
            }
        }
        item {
            if (failed) ErrorStrip(t.languageSaveFailed)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                AppLanguage.entries.forEach { language ->
                    OptionRow(
                        title = language.native,
                        subtitle = language.english,
                        leading = language.code,
                        selected = state.language == language,
                        onClick = {
                            state.language = language
                            scope.launch { failed = !controller.saveProfile() }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun About(state: AppState, onClose: () -> Unit) {
    val c = Sadora.colors
    SadoraTopBar(strings.settings.aboutTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                Text("SADORA", style = Sadora.type.h1, color = c.text)
                // The build she is running, from the platform, never a string typed here.
                Text(strings.settings.version(state.appVersion ?: "—"), style = Sadora.type.body, color = c.muted)
                Text(strings.settings.medicalDisclaimer, style = Sadora.type.body, color = c.muted)
            }
        }
    }
}

/** Readable rather than compact: the export is for her, or for someone she hands it to. */
private val exportJson = Json { prettyPrint = true; encodeDefaults = false; explicitNulls = false }
