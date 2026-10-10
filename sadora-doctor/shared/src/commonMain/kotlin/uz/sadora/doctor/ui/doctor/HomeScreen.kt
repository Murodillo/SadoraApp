package uz.sadora.doctor.ui.doctor

import uz.sadora.doctor.resources.ic3d_star
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.resources.ic3d_bell
import uz.sadora.doctor.resources.ic3d_calendar
import uz.sadora.doctor.resources.ic3d_chats
import uz.sadora.doctor.resources.ic3d_gem
import uz.sadora.doctor.resources.ic3d_message
import uz.sadora.doctor.resources.ic3d_notebook
import uz.sadora.doctor.resources.ic3d_qr
import uz.sadora.doctor.ui.components.ArtTile
import uz.sadora.doctor.ui.components.BadgeStrip
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.DoctorAccount
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ChipFlowRow
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.LoadMoreRow
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SectionHeader
import uz.sadora.doctor.ui.components.Skeleton

/**
 * The first tab: her day at a glance. Who she is signed in as, the busy switch, four
 * numbers — questions waiting, messages unread, what she has answered and written — her
 * consultations in figures, her balance, her price and hours, the way to a patient's
 * record, and the questions no doctor has answered yet, which are her work list.
 */
@Composable
fun DoctorHomeScreen(
    account: DoctorAccount,
    doctors: DoctorController,
    work: WorkController,
    onOpenQuestion: (String) -> Unit,
    onScan: () -> Unit,
    onMessages: () -> Unit,
    onProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenWork: () -> Unit,
    onOpenEarnings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Her badges page, from the strip that shows once she has one. */
    onOpenBadges: () -> Unit = {},
    onToast: (String) -> Unit = {},
) {
    val t = strings.tabs
    val d = strings.doctors
    val w = strings.work
    val c = Sadora.colors
    val scope = rememberCoroutineScope()

    // Everything the numbers come from, refreshed on each visit to the tab.
    LaunchedEffect(Unit) {
        launch { doctors.loadAccount() }
        launch { doctors.loadQuestions() }
        launch { doctors.loadConversations() }
        account.profileId?.let { launch { doctors.loadProfile(it) } }
        launch { work.loadSettings() }
        launch { work.loadStats() }
        launch { work.loadEarnings() }
    }
    var switching by remember { mutableStateOf(false) }

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
            // Asked for at the top until she has one: a face is what a patient looks for first.
            if (account.photoUrl == null) {
                item(key = "photo") {
                    val saved = strings.photo.saved
                    AskForPhotoCard(account, doctors, onSaved = { onToast(saved) })
                }
            }
            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(
                            value = if (doctors.questionsLoaded) {
                                doctors.questions.size.toString() + if (doctors.questionsHasMore) "+" else ""
                            } else {
                                null
                            },
                            label = t.statWaiting,
                            art = Res.drawable.ic3d_bell,
                            tint = c.secondary,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = if (doctors.conversationsLoaded) doctors.unreadMessages.toString() else null,
                            label = t.statUnread,
                            art = Res.drawable.ic3d_message,
                            tint = c.primary,
                            modifier = Modifier.weight(1f),
                            onClick = onMessages,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(
                            value = profile?.answerCount?.toString(),
                            label = d.statAnswers,
                            art = Res.drawable.ic3d_chats,
                            tint = c.success,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            value = profile?.postCount?.toString(),
                            label = d.statPosts,
                            art = Res.drawable.ic3d_notebook,
                            tint = c.accent,
                            modifier = Modifier.weight(1f),
                            onClick = onProfile,
                        )
                    }
                }
            }
            item(key = "busy") {
                val settings = work.settings
                if (settings == null) {
                    Skeleton(Modifier.fillMaxWidth().height(76.dp), shape = Radius.card)
                } else {
                    SwitchCard(
                        title = w.busyTitle,
                        body = w.busyBody.takeIf { settings.busy },
                        on = settings.busy,
                        enabled = !switching,
                        tint = c.warning,
                        onToggle = { wanted ->
                            switching = true
                            scope.launch {
                                work.setBusy(wanted)
                                switching = false
                            }
                        },
                    )
                }
            }
            // Her latest medals, once she has one; the page itself is on her profile too.
            work.badges?.takeIf { board -> board.badges.any { it.tier > 0 } }?.let { board ->
                item(key = "badges") { BadgeStrip(board, onOpen = onOpenBadges) }
            }
            item(key = "consultations") { ConsultationStatsCard(work) }
            item(key = "earnings") { EarningsCard(work, onOpenEarnings) }
            item(key = "work") {
                val settings = work.settings
                NavCard(
                    Res.drawable.ic3d_calendar,
                    title = w.settingsTitle,
                    subtitle = settings?.let {
                        priceText(it.priceMinor) + " · " + (if (it.hours.isEmpty()) w.noHours else w.workDays(it.hours.size))
                    },
                    onClick = onOpenWork,
                )
            }
            item(key = "scan") { ScanCard(onScan) }
            // Her own card sits with the settings rather than first: under "who is waiting
            // for you" the top of the screen belongs to the patients' numbers.
            item(key = "me") { WhoCard(account, onClick = onProfile) }
            item(key = "questions-title") { SectionHeader(d.questionsTitle) }
            item(key = "questions-hint") { Text(d.questionsHint, style = Sadora.type.body, color = c.muted) }
            if (!doctors.questionsLoaded && doctors.error == null) {
                item(key = "questions-loading") { Skeleton(Modifier.fillMaxWidth().height(120.dp), shape = Radius.card) }
            }
            if (doctors.questionsLoaded && rows.isEmpty()) {
                item(key = "questions-empty") {
                    EmptyState(title = d.questionsEmpty, body = d.questionsEmptyBody, actionText = null, onAction = {}, art = Res.drawable.ic3d_star)
                }
            }
            items(rows.size, key = { rows[it].post.id }) { index ->
                val row = rows[index]
                PostCard(post = row.post, onOpen = { onOpenQuestion(row.post.id) }, answered = row.answered)
            }
            if (doctors.questionsHasMore) {
                // Keyed on the server offset, not the rows: a page that only repeated rows
                // still moves it, so the next one is asked for.
                item(key = "questions-more") { LoadMoreRow(doctors.questionsOffset, onLoadMore = { doctors.loadMoreQuestions() }) }
            }
        }
    }
}

/** How long a just-answered question stays on the list, marked, before it leaves. */
private const val AnsweredLingerMillis = 1_400L

/** Her photo, name, check mark and specialty: the account this phone is working as. */
@Composable
private fun WhoCard(account: DoctorAccount, onClick: () -> Unit) {
    val d = strings.doctors
    val c = Sadora.colors
    SadoraCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DoctorAvatar(account.fullName.orEmpty(), size = 52.dp, photoUrl = account.photoUrl)
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
    art: DrawableResource,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    SadoraCard(modifier = modifier, padding = Spacing.sm, onClick = onClick, verticalGap = Spacing.xs) {
        ArtTile(art, tint = tint, size = 40.dp, artSize = 30.dp)
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
            ArtTile(Res.drawable.ic3d_qr, tint = c.accent, size = 52.dp, artSize = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(t.scanPatient, style = Sadora.type.h3, color = c.text)
                Text(t.scanPatientBody, style = Sadora.type.body, color = c.muted)
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}

/**
 * Her consultations in figures: this week, this month and in all, how many are open,
 * how fast she first answers, how many ended without a word from her, her rating, and
 * the topics she answers most in the room. A shimmer until the numbers arrive.
 */
@Composable
private fun ConsultationStatsCard(work: WorkController) {
    val w = strings.work
    val c = Sadora.colors
    val stats = work.stats
    SadoraCard {
        Text(w.statsTitle, style = Sadora.type.h3, color = c.text)
        if (stats == null) {
            Skeleton(Modifier.fillMaxWidth().height(96.dp))
            return@SadoraCard
        }
        Row(Modifier.fillMaxWidth()) {
            Figure(stats.consultationsWeek.toString(), w.statWeek, Modifier.weight(1f))
            Figure(stats.consultationsMonth.toString(), w.statMonth, Modifier.weight(1f))
            Figure(stats.consultationsTotal.toString(), w.statTotal, Modifier.weight(1f))
        }
        FigureLine(w.openNow, stats.openNow.toString())
        FigureLine(w.avgReply, stats.avgFirstReplyMinutes?.let(w::duration) ?: w.noValue)
        FigureLine(w.unanswered, stats.unansweredTotal.toString(), tint = if (stats.unansweredTotal > 0) c.dangerText else null)
        FigureLine(
            w.rating,
            stats.rating?.let { "★ " + w.ratingValue(it.toString(), stats.ratingCount) } ?: w.noRating,
        )
        if (stats.topTopics.isNotEmpty()) {
            Text(w.topTopics, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
            ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                stats.topTopics.forEach { TintChip("${strings.community.topic(it.topic)} · ${it.count}", c.textAccent) }
            }
        }
    }
}

@Composable
private fun Figure(value: String, label: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Sadora.type.h1, color = c.text)
        Text(label, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FigureLine(label: String, value: String, tint: Color? = null) {
    val c = Sadora.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = Sadora.type.body, color = c.muted, modifier = Modifier.weight(1f))
        Text(value, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = tint ?: c.text)
    }
}

/** The balance still to be paid to her, and the way into the whole account of it. */
@Composable
private fun EarningsCard(work: WorkController, onOpen: () -> Unit) {
    val w = strings.work
    val c = Sadora.colors
    val earnings = work.earnings
    SadoraCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            ArtTile(Res.drawable.ic3d_gem, tint = c.success, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.earningsTitle, style = Sadora.type.h3, color = c.text)
                Text(w.balance, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
            }
            if (earnings != null) {
                Text(somText(earnings.balanceMinor), style = Sadora.type.h3, color = c.textAccent)
            } else {
                Skeleton(Modifier.size(width = 72.dp, height = 20.dp))
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}
