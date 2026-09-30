package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.DoctorAccount
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.IconTile
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SectionHeader
import uz.sadora.doctor.ui.components.Skeleton

/**
 * The first tab: her day at a glance. Who she is signed in as, four numbers — questions
 * waiting, messages unread, what she has answered and written — the way to a patient's
 * record, and the questions no doctor has answered yet, which are her work list.
 */
@Composable
fun DoctorHomeScreen(
    account: DoctorAccount,
    doctors: DoctorController,
    onOpenQuestion: (String) -> Unit,
    onScan: () -> Unit,
    onMessages: () -> Unit,
    onProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tabs
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    // Everything the numbers come from, refreshed on each visit to the tab.
    LaunchedEffect(Unit) {
        launch { doctors.loadAccount() }
        launch { doctors.loadQuestions() }
        launch { doctors.loadConversations() }
        account.profileId?.let { launch { doctors.loadProfile(it) } }
    }

    // A question she has just answered is drawn once more, marked, and then let go.
    val rows = doctors.questionRows
    val anyAnswered = rows.any { it.answered }
    LaunchedEffect(anyAnswered) {
        if (anyAnswered) {
            delay(AnsweredLingerMillis)
            doctors.settleAnswered()
        }
    }

    val hour = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).hour }
    val profile = doctors.profile?.takeIf { it.id == account.profileId }

    Column(modifier) {
        SadoraTopBar(
            t.greeting(hour),
            subtitle = t.homeSubtitle,
            trailing = { CircleIconButton(SadoraIcons.Settings, contentDescription = strings.settings.title, onClick = onOpenSettings) },
        )
        ScreenContent(animateItems = true) {
            doctors.error?.let { failure ->
                item(key = "error") {
                    ErrorStrip(failure.readable(), onRetry = { scope.launch { doctors.loadQuestions() } })
                }
            }
            item(key = "me") { WhoCard(account) }
            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(
                            value = if (doctors.questionsLoaded) doctors.questions.size.toString() else null,
                            label = t.statWaiting,
                            icon = SadoraIcons.Document,
                            tint = c.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = if (doctors.conversationsLoaded) doctors.unreadMessages.toString() else null,
                            label = t.statUnread,
                            icon = SadoraIcons.Message,
                            tint = c.primary,
                            modifier = Modifier.weight(1f),
                            onClick = onMessages,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(
                            value = profile?.answerCount?.toString(),
                            label = d.statAnswers,
                            icon = SadoraIcons.Check,
                            tint = c.success,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = profile?.postCount?.toString(),
                            label = d.statPosts,
                            icon = SadoraIcons.Chats,
                            tint = c.accent,
                            modifier = Modifier.weight(1f),
                            onClick = onProfile,
                        )
                    }
                }
            }
            item(key = "scan") { ScanCard(onScan) }
            item(key = "questions-title") { SectionHeader(d.questionsTitle) }
            item(key = "questions-hint") { Text(d.questionsHint, style = Sadora.type.body, color = c.muted) }
            if (!doctors.questionsLoaded && doctors.error == null) {
                item(key = "questions-loading") { Skeleton(Modifier.fillMaxWidth().height(120.dp), shape = Radius.card) }
            }
            if (doctors.questionsLoaded && rows.isEmpty()) {
                item(key = "questions-empty") {
                    EmptyState(title = d.questionsEmpty, body = d.questionsEmptyBody, actionText = null, onAction = {})
                }
            }
            items(rows.size, key = { rows[it].post.id }) { index ->
                val row = rows[index]
                PostCard(post = row.post, onOpen = { onOpenQuestion(row.post.id) }, answered = row.answered)
            }
        }
    }
}

/** How long a just-answered question stays on the list, marked, before it leaves. */
private const val AnsweredLingerMillis = 1_400L

/** Her name, check mark and specialty: the account this phone is working as. */
@Composable
private fun WhoCard(account: DoctorAccount) {
    val d = strings.doctors
    val c = Sadora.colors
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DoctorAvatar(account.fullName.orEmpty(), size = 52.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(account.fullName.orEmpty(), style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f, fill = false))
                    VerifiedMark()
                }
                account.specialty?.let { Text(d.specialty(it), style = Sadora.type.body, color = c.textAccent) }
                account.workplace?.let { Text(it, style = Sadora.type.body, color = c.muted, maxLines = 1) }
            }
        }
    }
}

/** One number with what it counts. Null is still loading, drawn as a shimmer rather than a 0. */
@Composable
private fun StatTile(
    value: String?,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xs) {
        IconTile(icon, tint = tint, size = 36.dp, iconSize = IconSize.md)
        if (value != null) {
            Text(value, style = Sadora.type.h1, color = c.text)
        } else {
            Skeleton(Modifier.size(width = 36.dp, height = 28.dp))
        }
        Text(label, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, minLines = 2, maxLines = 2)
    }
}

/** The way into the Scan tab, for the moment a patient is in the room. */
@Composable
private fun ScanCard(onScan: () -> Unit) {
    val t = strings.tabs
    val c = Sadora.colors
    SadoraCard(onClick = onScan) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(
                Modifier.size(52.dp).clip(Radius.chip).background(c.primary),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(SadoraIcons.Scan, contentDescription = null, Modifier.size(IconSize.lg), tint = c.onPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(t.scanPatient, style = Sadora.type.h3, color = c.text)
                Text(t.scanPatientBody, style = Sadora.type.body, color = c.muted)
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}
