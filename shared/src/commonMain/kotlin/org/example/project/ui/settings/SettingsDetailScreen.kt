package org.example.project.ui.settings

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
import androidx.compose.ui.text.input.KeyboardType
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.data.SadoraController
import org.example.project.model.AppLanguage
import org.example.project.model.AppState
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.nav.Route
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.ConsentRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ErrorStrip
import org.example.project.ui.components.OptionRow
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraDialog
import org.example.project.ui.components.SadoraSwitch
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SelectChip

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
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (route) {
            Route.PersonalDetails -> PersonalDetails(state, controller, onClose)
            Route.GoalsSettings -> GoalsSettings(state, controller, onClose)
            Route.LifeStageSettings -> LifeStageSettings(state, controller, onClose)
            Route.Notifications -> NotificationSettings(state, onClose)
            Route.PrivacySecurity -> PrivacySettings(state, controller, onClose, onOpen, onSignedOut)
            Route.LanguageSettings -> LanguageSettings(state, controller, onClose)
            Route.About -> About(onClose)
            else -> About(onClose)
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
) {
    val scope = rememberCoroutineScope()
    controller.error?.let { ErrorStrip(it) }
    SadoraButton(
        if (controller.busy) strings.common.saving else label,
        enabled = !controller.busy,
        onClick = { scope.launch { if (controller.saveProfile()) onSaved() } },
    )
}

@Composable
private fun PersonalDetails(state: AppState, controller: SadoraController, onClose: () -> Unit) {
    val t = strings.settings
    SadoraTopBar(t.personalTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                SadoraTextField(state.name, { state.name = it }, label = t.name)
                SadoraTextField(
                    state.birthDate,
                    { state.birthDate = it },
                    label = t.birthDate,
                    keyboardType = KeyboardType.Number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraTextField(
                        state.heightCm,
                        { state.heightCm = it },
                        label = t.height,
                        suffix = t.centimetres,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraTextField(
                        state.weightKg,
                        { state.weightKg = it },
                        label = t.weight,
                        suffix = t.kilograms,
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item { DisclaimerNote(t.weightNote) }
        item { SaveButton(controller, onClose) }
    }
}

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

@Composable
private fun LifeStageSettings(state: AppState, controller: SadoraController, onClose: () -> Unit) {
    val t = strings.settings
    val stages = strings.stages
    SadoraTopBar(t.lifeStageTitle, onBack = onClose)
    ScreenContent {
        items(LifeStage.entries.size) { index ->
            val stage = LifeStage.entries[index]
            OptionRow(
                title = stages.title(stage),
                subtitle = stages.subtitle(stage),
                leading = stage.glyph,
                selected = state.lifeStage == stage,
                onClick = { state.lifeStage = stage },
            )
        }
        item { DisclaimerNote(t.lifeStageNote) }
        item { SaveButton(controller, onClose) }
    }
}

@Composable
private fun NotificationSettings(state: AppState, onClose: () -> Unit) {
    val t = strings.settings
    SadoraTopBar(t.notificationsTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                ToggleRow(t.medReminder, t.medReminderNote, state.notificationsAllowed) {
                    state.notificationsAllowed = it
                }
                ToggleRow(t.cycleReminder, t.cycleReminderNote, state.notificationsAllowed) {
                    state.notificationsAllowed = it
                }
                ToggleRow(t.waterReminder, t.waterReminderNote, false) {}
                ToggleRow(t.aiSummary, t.aiSummaryNote, state.isPremium) {}
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
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    onSignedOut: () -> Unit,
) {
    val t = strings.settings
    val scope = rememberCoroutineScope()
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
            controller.error?.let { ErrorStrip(it) }
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
                SadoraButton(t.exportData, {}, tone = ButtonTone.Secondary)
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
private fun About(onClose: () -> Unit) {
    val c = Sadora.colors
    SadoraTopBar(strings.settings.aboutTitle, onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                Text("SADORA", style = Sadora.type.h1, color = c.text)
                Text(strings.settings.version("1.0.0"), style = Sadora.type.body, color = c.muted)
                Text(strings.settings.medicalDisclaimer, style = Sadora.type.body, color = c.muted)
            }
        }
    }
}
