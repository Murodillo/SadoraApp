package org.example.project.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.CommunityPost
import org.example.project.model.CommunityTopic
import org.example.project.model.ReportReason
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.pressable

/**
 * The composer, raised by the shell over the tab bar.
 *
 * It opens on the room she was reading — "Hammasi" is a filter, not a room, so it
 * falls back to the first real one — and says which alias the post will carry, since
 * that is the whole reassurance the room offers.
 */
@Composable
fun ComposePostSheetContent(
    state: AppState,
    onPosted: () -> Unit,
) {
    val c = Sadora.colors
    val rooms = remember { CommunityTopic.entries.filter { it != CommunityTopic.All } }
    var topic by remember {
        mutableStateOf(state.communityTopic.takeIf { it != CommunityTopic.All } ?: rooms.first())
    }
    var body by remember { mutableStateOf("") }
    val canPost = body.trim().length >= MinPostLength && body.length <= MaxPostLength

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            state.communityAlias?.let { "Post \"$it\" nomidan chiqadi — ismingiz ko'rinmaydi." }
                ?: "Post taxallus ostida chiqadi — ismingiz ko'rinmaydi.",
            style = Sadora.type.body,
            color = c.muted,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            rooms.forEach { room ->
                SelectChip(label = room.label, selected = topic == room, onClick = { topic = room })
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(Radius.cardSmall)
                .background(c.surface2)
                .padding(Spacing.sm)
                .heightIn(min = 120.dp),
        ) {
            if (body.isEmpty()) {
                Text("Nima haqida so'ramoqchisiz?", style = Sadora.type.body, color = c.muted2)
            }
            BasicTextField(
                value = body,
                onValueChange = { if (it.length <= MaxPostLength) body = it },
                textStyle = Sadora.type.body.copy(color = c.text),
                cursorBrush = SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${body.length} / $MaxPostLength", style = Sadora.type.body, color = c.muted2)
        }

        SadoraButton(
            "Yuborish",
            onClick = {
                state.createPost(topic, body)
                onPosted()
            },
            enabled = canPost,
        )
    }
}

/**
 * What can be done to a post besides reacting to it: report it, or — for her own —
 * take it down. The reasons are the closed list the moderation queue sorts by.
 */
@Composable
fun PostMenuSheetContent(
    state: AppState,
    post: CommunityPost,
    onDone: (message: String?) -> Unit,
) {
    val c = Sadora.colors
    var reason by remember { mutableStateOf<ReportReason?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (post.isMine) {
            Text("Bu sizning postingiz.", style = Sadora.type.body, color = c.muted)
            SadoraButton(
                "Postni o'chirish",
                onClick = {
                    state.deletePost(post.id)
                    onDone("Post o'chirildi")
                },
                tone = ButtonTone.Destructive,
            )
            return@Column
        }

        Text("Shikoyat sababi", style = Sadora.type.h3, color = c.text)
        Text(
            "Shikoyat moderatorga boradi. Kim yuborgani ko'rinmaydi.",
            style = Sadora.type.body,
            color = c.muted,
        )
        ReportReason.entries.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(Radius.cardSmall)
                    .background(if (reason == option) c.primary.copy(alpha = 0.12f) else c.surface2)
                    .pressable { reason = option }
                    .padding(horizontal = Spacing.sm, vertical = 12.dp),
            ) {
                Text(
                    option.label,
                    style = Sadora.type.body.copy(fontWeight = if (reason == option) FontWeight.SemiBold else FontWeight.Normal),
                    color = c.text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SadoraButton(
            "Shikoyat yuborish",
            onClick = {
                reason?.let { state.reportPost(post.id, it, null) }
                onDone("Shikoyat yuborildi")
            },
            enabled = reason != null,
        )
    }
}

private const val MinPostLength = 2
private const val MaxPostLength = 2000
