package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.contract.MessageDocument
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.rememberOpenDocument

/**
 * The PDF report a patient sent: its name and size, and a button that downloads it and
 * hands it to the phone's viewer. Nothing is kept: each open fetches it again.
 */
@Composable
internal fun ReportMessageCard(
    document: MessageDocument,
    conversationId: String,
    messageId: String,
    doctors: DoctorController,
    modifier: Modifier = Modifier,
) {
    val r = strings.reports
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val openDocument = rememberOpenDocument()
    var opening by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    Column(
        modifier
            .widthIn(max = 320.dp)
            .clip(Radius.card)
            .background(c.surface)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Box(
                Modifier.size(40.dp).clip(Radius.chip).background(c.primary.copy(alpha = if (c.isDark) 0.22f else 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(SadoraIcons.Document, contentDescription = null, Modifier.size(IconSize.md), tint = c.primary)
            }
            Column(Modifier.weight(1f)) {
                Text(r.title, style = Sadora.type.h3, color = c.text)
                Text(
                    listOf(document.name, r.size(document.sizeBytes)).joinToString(" · "),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (failed) {
            Text(r.failed, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.dangerText)
        }
        PillButton(
            if (opening) r.opening else r.open,
            enabled = !opening,
            onClick = {
                opening = true
                failed = false
                scope.launch {
                    val bytes = doctors.document(conversationId, messageId)
                    failed = bytes == null || !openDocument(bytes, document.name, document.mimeType)
                    opening = false
                }
            },
        )
    }
}
