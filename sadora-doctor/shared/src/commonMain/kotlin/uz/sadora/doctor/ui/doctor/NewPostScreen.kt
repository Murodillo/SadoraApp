package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.Limits
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ChipFlowRow
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SelectChip

/**
 * A post of her own, the client's composer on a page. It says which name it will go out
 * under — the server signs an approved doctor's post with her name and check mark, which
 * is the whole point of writing here rather than in the client app.
 */
@Composable
fun NewPostScreen(
    doctors: DoctorController,
    onPosted: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.community
    val scope = rememberCoroutineScope()
    val calls = doctors.composeCalls
    var topic by remember { mutableStateOf(CommunityTopic.CYCLE) }
    var body by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val length = body.trim().length
    val canPost = length >= Limits.POST_MIN && body.length <= Limits.POST_MAX && !sending

    // A failure left over from an earlier attempt is not this page's failure.
    LaunchedEffect(Unit) { calls.clearError() }

    Column(modifier) {
        SadoraTopBar(t.newPostTitle, onBack = onClose)
        ScreenContent(stagger = false) {
            doctors.doctorName?.let { name ->
                item { Text(strings.doctors.writingAs(name), style = Sadora.type.body, color = c.muted) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(t.topicLabel.uppercase(), style = Sadora.type.caption, color = c.muted)
                    ChipFlowRow {
                        CommunityTopic.entries.forEach { room ->
                            SelectChip(label = t.topic(room), selected = topic == room, onClick = { topic = room })
                        }
                    }
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.cardSmall)
                        .background(c.surface2)
                        .padding(Spacing.sm)
                        .heightIn(min = 160.dp),
                ) {
                    if (body.isEmpty()) {
                        Text(t.postHint, style = Sadora.type.body, color = c.muted2)
                    }
                    BasicTextField(
                        value = body,
                        // Trimmed to fit, not refused: a paste one character over used to do nothing.
                        onValueChange = { body = it.take(Limits.POST_MAX) },
                        textStyle = Sadora.type.body.copy(color = c.text),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    FieldNote(
                        if (body.isNotEmpty() && length < Limits.POST_MIN) t.postTooShort(Limits.POST_MIN) else "",
                        warn = true,
                    )
                    FieldNote("${body.length} / ${Limits.POST_MAX}")
                }
            }
            item { Text(strings.doctors.disclaimer, style = Sadora.type.body, color = c.muted2) }
            // The daily limit, a restricted account, no network: the reason, with her text
            // still in the field.
            calls.error?.let { failure -> item { ErrorStrip(failure.readable()) } }
            item {
                SadoraButton(
                    if (sending) strings.auth.sending else t.publish,
                    enabled = canPost,
                    onClick = {
                        sending = true
                        scope.launch {
                            val sent = doctors.createPost(topic, body.trim())
                            sending = false
                            if (sent) onPosted()
                        }
                    },
                )
            }
        }
    }
}
