package org.example.project.ui.journey

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.Mood
import org.example.project.model.SampleData
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.SegmentedControl
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.SettingsRow
import org.example.project.ui.components.noRippleClickable

private data class PregnancyEvent(
    val title: String,
    val date: String,
    val time: String?,
    val place: String?,
    val reminder: String?,
)

private val pregnancyEvents = listOf(
    PregnancyEvent("Skrining UTT", "27-avgust", "10:30", "Respublika markazi, 3-xona", "1 kun oldin"),
    PregnancyEvent("Qon tahlili", "4-sentabr", "08:00", "nahorda", null),
    PregnancyEvent("Shifokor ko'rigi", "18-sentabr", "11:00", null, null),
    PregnancyEvent("Glyukoza testi", "Oktabr", null, "sana belgilanmagan", null),
)

/**
 * "Homiladorlik · Tadbirlar".
 *
 * The app does not prescribe a screening schedule — the user fills this list
 * themselves, and the footnote says so plainly.
 */
@Composable
fun PregnancyAppointmentsScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    var filter by remember { mutableStateOf(0) }

    Column(modifier) {
        SadoraTopBar(
            "Tadbirlar",
            onBack = onClose,
            trailing = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.surface2)
                        .noRippleClickable {},
                    contentAlignment = Alignment.Center,
                ) {
                    Text("＋", style = Sadora.type.h2, color = c.text)
                }
            },
        )

        ScreenContent {
            item {
                SegmentedControl(
                    options = listOf("Yaqin", "O'tgan", "Barchasi"),
                    selectedIndex = filter,
                    onSelect = { filter = it },
                )
            }

            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("KEYINGI", style = Sadora.type.caption, color = c.muted)
                        Text("8 KUNDAN KEYIN", style = Sadora.type.caption, color = c.textAccent)
                    }
                    EventRow(pregnancyEvents.first(), highlighted = true)
                }
            }

            items(pregnancyEvents.size - 1) { index ->
                SadoraCard(padding = Spacing.sm) {
                    EventRow(pregnancyEvents[index + 1], highlighted = false)
                }
            }

            item {
                DisclaimerNote(
                    "Tadbirlar ro'yxatini o'zingiz to'ldirasiz. SADORA tekshiruv " +
                        "jadvalini tayinlamaydi.",
                )
            }

            item {
                SadoraCard(padding = Spacing.xs) {
                    SettingsRow(
                        "📎",
                        "Hujjatlar",
                        value = "2 fayl · faqat qurilmangizda",
                    ) {}
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: PregnancyEvent, highlighted: Boolean) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(
            Modifier
                .clip(Radius.field)
                .background(if (highlighted) c.primary.copy(alpha = 0.16f) else c.surface2)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                event.date.substringBefore('-'),
                style = Sadora.type.h2,
                color = if (highlighted) c.textAccent else c.text,
            )
            Text(
                event.date.substringAfter('-').take(3).uppercase(),
                style = Sadora.type.caption,
                color = c.muted,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(event.title, style = Sadora.type.h3, color = c.text)
            val detail = listOfNotNull(event.time, event.place).joinToString(" · ")
            if (detail.isNotEmpty()) {
                Text(detail, style = Sadora.type.body, color = c.muted)
            }
            if (event.reminder != null) {
                SadoraBadge("Eslatma ${event.reminder}", BadgeTone.Neutral, leading = "🔔")
            }
        }
        Text("✎", style = Sadora.type.h3, color = c.muted2)
    }
}

/**
 * "Homiladorlik · o'zini his qilish" — the daily check-in.
 *
 * Foetal movement is the one place the app escalates: a marked drop gets an explicit
 * "see a doctor without delay" warning rather than a reassuring interpretation.
 */
@Composable
fun PregnancyCheckInScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val symptoms = remember { mutableStateListOf<String>() }
    var movement by remember { mutableStateOf("Odatdagidek") }
    var note by remember { mutableStateOf("") }

    Column(modifier) {
        SadoraTopBar("", onBack = onClose)

        ScreenContent {
            item {
                Text(
                    "O'zingizni qanday his qilyapsiz?",
                    style = Sadora.type.h1,
                    color = c.text,
                )
            }

            item {
                SadoraCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Mood.entries.forEach { mood ->
                            val label = if (mood == Mood.Bad) "Qiyin" else mood.label
                            MoodCell(
                                emoji = mood.emoji,
                                label = label,
                                selected = state.mood == mood,
                                onClick = { state.mood = mood },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bugungi simptomlar")
                    ChipFlowRow {
                        listOf(
                            "Belda og'riq",
                            "Ko'ngil aynishi",
                            "Shish",
                            "Nafas qisishi",
                            "Uyqusizlik",
                        ).forEach { symptom ->
                            SelectChip(
                                label = symptom,
                                selected = symptom in symptoms,
                                onClick = {
                                    if (!symptoms.remove(symptom)) symptoms.add(symptom)
                                },
                            )
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel("Bolaning harakati")
                    ChipFlowRow {
                        listOf("Odatdagidek", "Kamroq", "Ko'proq").forEach { option ->
                            SelectChip(
                                label = option,
                                selected = movement == option,
                                onClick = { movement = option },
                            )
                        }
                    }
                    // The one escalation in the whole app.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radius.cardSmall)
                            .background(c.danger.copy(alpha = 0.12f))
                            .padding(Spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Text("⚠", style = Sadora.type.h3, color = c.danger)
                        Text(
                            "Harakat sezilarli kamaysa yoki umuman sezilmasa, " +
                                "kechiktirmasdan shifokorga murojaat qiling.",
                            style = Sadora.type.body,
                            color = c.danger,
                        )
                    }
                }
            }

            item {
                SadoraTextField(
                    note,
                    { note = it },
                    label = "Izoh — faqat siz ko'rasiz",
                    placeholder = "Yozib qo'ying…",
                )
            }

            item { SadoraButton("Saqlash", onClose) }
        }
    }
}

@Composable
internal fun MoodCell(
    emoji: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    Column(
        modifier
            .clip(Radius.field)
            .background(
                if (selected) c.primary.copy(alpha = if (c.isDark) 0.2f else 0.1f) else c.surface2,
            )
            .noRippleClickable(onClick = onClick)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(emoji, style = Sadora.type.h2)
        Text(
            label,
            style = Sadora.type.caption.copy(
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
            color = if (selected) c.textAccent else c.muted,
            maxLines = 1,
            softWrap = false,
        )
    }
}
