package org.example.project.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.i18n.strings
import org.example.project.model.AppState
import org.example.project.model.JournalNote
import org.example.project.ui.components.BadgeTone
import org.example.project.ui.components.CardLabel
import org.example.project.ui.components.EmptyState
import org.example.project.ui.components.SadoraBadge
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.ScreenContent
import org.example.project.ui.components.noRippleClickable
import org.example.project.ui.components.acceptText
import uz.sadora.contract.Limits

/**
 * "Ong · kundalik va nafas".
 *
 * The privacy label is shown on the surface itself, not buried in settings — the
 * journal is the most sensitive thing the app stores.
 *
 * Entries come from the store, which the Mind summary fills; writing one puts it on
 * screen immediately and sends it on. The breathing practice here is the same one the
 * Mind tab runs, not a second copy of it: the screen is titled "kundalik va praktika"
 * and both halves have to actually work.
 */
@Composable
fun MindJournalScreen(
    state: AppState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules
    val dates = strings.dates
    var draft by remember { mutableStateOf("") }
    var practising by remember { mutableStateOf(false) }
    // Confirmed before removing: the journal is the one place in the app where an
    // accidental tap destroys something she cannot get back.
    var pendingDelete by remember { mutableStateOf<JournalNote?>(null) }

    Column(modifier) {
        SadoraTopBar(t.journalTitle, onBack = onClose)

        ScreenContent {
            item { PracticeCard(breathingPractice(), onStart = { practising = true }) }

            item {
                CardLabel(
                    t.journalLabel,
                    trailing = { SadoraBadge(t.journalPrivate, BadgeTone.Neutral, leading = "🔒") },
                )
            }

            item {
                SadoraCard {
                    SadoraTextField(
                        draft,
                        { draft = acceptText(it, Limits.JOURNAL_MAX) },
                        placeholder = t.journalPrompt,
                        singleLine = false,
                    )
                    SadoraButton(
                        strings.common.save,
                        onClick = {
                            state.addJournalNote(draft)
                            draft = ""
                        },
                        enabled = draft.isNotBlank(),
                    )
                }
            }

            if (state.journal.isEmpty()) {
                item {
                    EmptyState(
                        title = t.journalEmpty,
                        body = t.journalEmptyBody,
                        actionText = null,
                        onAction = {},
                        glyph = "📝",
                    )
                }
            }

            items(state.journal.size) { index ->
                val note = state.journal[index]
                JournalCard(
                    note = note,
                    label = dates.relativeDay(note.date, state.today),
                    onDelete = { pendingDelete = note },
                )
            }
        }
    }

    val practice = breathingPractice()
    PracticeSheet(
        practice = if (practising) practice else null,
        onFinish = { seconds ->
            state.logPractice(practice.kind, seconds)
            practising = false
        },
        onDismiss = { practising = false },
    )

    // Kept mounted through the exit animation so the sheet does not blank as it closes.
    val lastPending = remember { mutableStateOf<JournalNote?>(null) }
    pendingDelete?.let { lastPending.value = it }
    SadoraBottomSheet(
        visible = pendingDelete != null,
        title = t.journalDeleteTitle,
        onDismiss = { pendingDelete = null },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                t.journalDeleteBody,
                style = Sadora.type.body,
                color = c.muted,
            )
            lastPending.value?.let { note ->
                Text(note.body, style = Sadora.type.body, color = c.text, maxLines = 3)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SadoraButton(
                    strings.common.cancel,
                    onClick = { pendingDelete = null },
                    tone = org.example.project.ui.components.ButtonTone.Secondary,
                    modifier = Modifier.weight(1f),
                )
                SadoraButton(
                    strings.common.delete,
                    onClick = {
                        pendingDelete?.let { state.deleteJournalNote(it) }
                        pendingDelete = null
                    },
                    tone = org.example.project.ui.components.ButtonTone.Destructive,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One entry: when it was written, what it says, and a way to remove it. */
@Composable
private fun JournalCard(note: JournalNote, label: String, onDelete: () -> Unit) {
    val c = Sadora.colors
    val t = strings.modules
    SadoraCard(padding = Spacing.sm) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                label,
                style = Sadora.type.h3,
                color = c.text,
                modifier = Modifier.weight(1f),
            )
            Text(note.time, style = Sadora.type.body, color = c.muted2)
            Box(
                Modifier
                    .size(28.dp)
                    .noRippleClickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SadoraIcons.More,
                    contentDescription = t.journalDeleteAction,
                    Modifier.size(IconSize.sm),
                    tint = c.muted2,
                )
            }
        }
        Text(note.body, style = Sadora.type.body, color = c.muted)
    }
}
