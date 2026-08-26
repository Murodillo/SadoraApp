package org.example.project.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppLanguage
import org.example.project.model.AppState
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.model.SampleData
import org.example.project.nav.Route
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.ConsentRow
import org.example.project.ui.components.DisclaimerNote
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
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        when (route) {
            Route.PersonalDetails -> PersonalDetails(state, onClose)
            Route.GoalsSettings -> GoalsSettings(state, onClose)
            Route.LifeStageSettings -> LifeStageSettings(state, onClose)
            Route.Notifications -> NotificationSettings(state, onClose)
            Route.PrivacySecurity -> PrivacySettings(state, onClose)
            Route.About -> About(onClose)
            else -> About(onClose)
        }
    }
}

@Composable
private fun PersonalDetails(state: AppState, onClose: () -> Unit) {
    val c = Sadora.colors
    SadoraTopBar("Shaxsiy ma'lumotlar", onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                SadoraTextField(state.name, { state.name = it }, label = "Ism")
                SadoraTextField(
                    state.birthDate,
                    { state.birthDate = it },
                    label = "Tug'ilgan sana",
                    keyboardType = KeyboardType.Number,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SadoraTextField(
                        state.heightCm,
                        { state.heightCm = it },
                        label = "Bo'y",
                        suffix = "sm",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraTextField(
                        state.weightKg,
                        { state.weightKg = it },
                        label = "Vazn",
                        suffix = "kg",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            DisclaimerNote("Vazn ixtiyoriy va hech qachon boshqalarga ko'rsatilmaydi.")
        }
        item { SadoraButton("Saqlash", onClose) }
    }
}

@Composable
private fun GoalsSettings(state: AppState, onClose: () -> Unit) {
    SadoraTopBar("Maqsadlar", onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                CardLabel("${state.goals.size} tanlandi")
                ChipFlowRow {
                    Goal.entries.forEach { goal ->
                        SelectChip(
                            label = goal.label,
                            selected = goal in state.goals,
                            onClick = { state.toggleGoal(goal) },
                        )
                    }
                }
            }
        }
        item { SadoraButton("Saqlash", onClose) }
    }
}

@Composable
private fun LifeStageSettings(state: AppState, onClose: () -> Unit) {
    SadoraTopBar("Hayot bosqichi", onBack = onClose)
    ScreenContent {
        items(LifeStage.entries.size) { index ->
            val stage = LifeStage.entries[index]
            OptionRow(
                title = stage.title,
                subtitle = stage.subtitle,
                leading = stage.glyph,
                selected = state.lifeStage == stage,
                onClick = { state.lifeStage = stage },
            )
        }
        item {
            DisclaimerNote(
                "Bosqichni o'zgartirsangiz \"Yo'l\" bo'limi va tegishli ekranlar " +
                    "butunlay yangilanadi. Yozilgan ma'lumotlaringiz saqlanadi.",
            )
        }
        item { SadoraButton("Saqlash", onClose) }
    }
}

@Composable
private fun NotificationSettings(state: AppState, onClose: () -> Unit) {
    val c = Sadora.colors
    SadoraTopBar("Bildirishnomalar", onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                ToggleRow(
                    "Dori eslatmalari",
                    "Qabul vaqtidan 10 daqiqa oldin",
                    state.notificationsAllowed,
                ) { state.notificationsAllowed = it }
                ToggleRow(
                    "Hayz eslatmasi",
                    "Taxminiy sana yaqinlashganda",
                    state.notificationsAllowed,
                ) { state.notificationsAllowed = it }
                ToggleRow("Suv eslatmasi", "Kuniga uch marta", false) {}
                ToggleRow("Kunlik AI xulosasi", "Ertalab 08:00", state.isPremium) {}
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
private fun PrivacySettings(state: AppState, onClose: () -> Unit) {
    val c = Sadora.colors
    var confirmDelete by remember { mutableStateOf(false) }

    SadoraTopBar("Maxfiylik va xavfsizlik", onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                ConsentRow(
                    "Salomatlik ma'lumotlarini saqlash",
                    "Ilova ishlashi uchun zarur. Ma'lumot shifrlangan holda saqlanadi.",
                    state.consentStoreHealth,
                    { state.consentStoreHealth = it },
                    required = true,
                )
                ConsentRow(
                    "AI xulosalar uchun ishlatish",
                    "Shaxsiy xulosa va tavsiyalar tayyorlash uchun.",
                    state.consentAiInsights,
                    { state.consentAiInsights = it },
                )
                ConsentRow(
                    "Anonim analitika",
                    "Ixtiyoriy. Ilovani yaxshilashga yordam beradi.",
                    state.consentAnalytics,
                    { state.consentAnalytics = it },
                )
            }
        }
        item {
            SadoraCard {
                CardLabel("Ma'lumotlaringiz")
                SadoraButton("Ma'lumotlarni eksport qilish", {}, tone = ButtonTone.Secondary)
                SadoraButton(
                    "Hisobni o'chirish",
                    { confirmDelete = true },
                    tone = ButtonTone.Destructive,
                )
            }
        }
    }

    SadoraDialog(
        visible = confirmDelete,
        title = "Hisobni o'chirish?",
        body = "Ma'lumotlaringiz butunlay o'chiriladi. Avval eksport qilishni tavsiya qilamiz.",
        confirmText = "O'chirish",
        onConfirm = { confirmDelete = false },
        onDismiss = { confirmDelete = false },
    )
}

@Composable
private fun About(onClose: () -> Unit) {
    val c = Sadora.colors
    SadoraTopBar("SADORA haqida", onBack = onClose)
    ScreenContent {
        item {
            SadoraCard {
                Text("SADORA", style = Sadora.type.h1, color = c.text)
                Text("Versiya 1.0.0", style = Sadora.type.body, color = c.muted)
                Text(SampleData.medicalDisclaimer, style = Sadora.type.body, color = c.muted)
            }
        }
        item {
            SadoraCard {
                CardLabel("Til")
                AppLanguage.entries.forEach { language ->
                    Text(
                        "${language.code} · ${language.native}",
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }
        }
    }
}
