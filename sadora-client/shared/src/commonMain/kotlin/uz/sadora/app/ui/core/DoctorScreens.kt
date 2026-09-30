package uz.sadora.app.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import uz.sadora.app.data.DoctorController
import uz.sadora.app.data.MessagesController
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.model.Conversation
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SectionHeader
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.rememberShareAction
import uz.sadora.contract.DoctorProfile

// A verified doctor's public page, as a reader opens it from the chat. Applying, the
// panel and answering live in the doctor's own app, sadora-doctor.

/**
 * A verified doctor as readers see her: name, specialty, where she works, how long,
 * her own words, and what she has written — and, when she takes consultations, the
 * way to write to her.
 *
 * The button reads what will happen: "Suhbatni ochish" while a consultation with her is
 * open, "Shifokorga yozish" otherwise. The second never opens anything by itself; it
 * raises the consent sheet first, because a consultation is not anonymous and she has
 * to know that before the first word, not after.
 */
@Composable
fun DoctorProfileScreen(
    doctorId: String,
    state: AppState,
    doctors: DoctorController,
    messages: MessagesController,
    onOpenPost: (CommunityPost) -> Unit,
    onOpenMenu: (CommunityPost) -> Unit,
    /** Raises the consent sheet; the shell owns it so it covers the tab bar. */
    onMessage: (DoctorProfile) -> Unit,
    onOpenConversation: (id: String, name: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val d = strings.doctors
    val t = strings.community
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val share = rememberShareAction()

    LaunchedEffect(doctorId) { doctors.loadProfile(doctorId) }
    val profile = doctors.profile?.takeIf { it.id == doctorId }

    Column(modifier) {
        SadoraTopBar(d.profileTitle, onBack = onClose)
        ScreenContent {
            doctors.error?.let { failure ->
                item { ErrorStrip(failure.readable(), onRetry = { scope.launch { doctors.loadProfile(doctorId) } }) }
            }
            if (profile == null) {
                if (doctors.error == null) item { DoctorSkeleton() }
                return@ScreenContent
            }
            item { DoctorHeader(profile) }
            if (!profile.isMe) {
                item {
                    ConsultationAction(
                        profile = profile,
                        messages = messages,
                        onMessage = { onMessage(profile) },
                        onOpenConversation = { onOpenConversation(it, profile.fullName) },
                    )
                }
            }
            item {
                SadoraCard(padding = Spacing.sm) {
                    Row(Modifier.fillMaxWidth()) {
                        DoctorStat(Fmt.int(profile.postCount), d.statPosts, Modifier.weight(1f))
                        DoctorStat(Fmt.int(profile.answerCount), d.statAnswers, Modifier.weight(1f))
                        DoctorStat(profile.experienceYears.toString(), d.statExperience, Modifier.weight(1f))
                    }
                }
            }
            item { Text(d.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2) }
            item { SectionHeader(d.herPosts) }
            val posts = doctors.profilePosts
            if (posts.isEmpty()) {
                item { Text(d.noPosts, style = Sadora.type.body, color = c.muted) }
            } else {
                items(posts.size, key = { posts[it].id }) { index ->
                    val post = posts[index]
                    PostCard(
                        post = post,
                        liked = post.id in state.likedPosts,
                        saved = post.id in state.savedPosts,
                        likes = state.likeCount(post),
                        comments = state.commentCountOf(post),
                        onLike = { state.toggleLike(post.id) },
                        onSave = { state.toggleSaved(post.id) },
                        onOpen = { onOpenPost(post) },
                        onOpenAuthor = {},
                        onShare = { share("${post.body}\n\n" + t.shareSuffix) },
                        onMore = { onOpenMenu(post) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DoctorHeader(profile: DoctorProfile) {
    val d = strings.doctors
    val c = Sadora.colors
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        DoctorAvatar(profile.fullName, size = 84.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(profile.fullName, style = Sadora.type.h2, color = c.text, textAlign = TextAlign.Center)
            VerifiedMark(size = 20.dp)
        }
        Text(d.specialty(profile.specialty), style = Sadora.type.h3, color = c.textAccent)
        Text(profile.workplace, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
        Text(d.verifiedSince(monthYear(profile.verifiedSince)), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
        profile.bio?.let {
            Text(
                it,
                style = Sadora.type.body,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Spacing.sm),
            )
        }
    }
}

/**
 * The way into a consultation with her. Whether hers is still open is read from the
 * thread list — the page knows only that one exists — so the list is fetched once if
 * the thread is not in it yet.
 */
@Composable
private fun ConsultationAction(
    profile: DoctorProfile,
    messages: MessagesController,
    onMessage: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val conversationId = profile.conversationId
    // By id when the page names it; by doctor when it was opened a moment ago from this
    // page and the page has not been re-read since.
    val existing = conversationId?.let { id -> messages.conversations.firstOrNull { it.id == id } }
        ?: messages.conversations.firstOrNull { it.doctor?.id == profile.id }
    LaunchedEffect(conversationId) {
        if (conversationId != null && messages.conversations.none { it.id == conversationId }) messages.load()
    }
    val openId = existing?.takeIf { it.consultation?.open == true }?.id
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        when {
            openId != null ->
                SadoraButton(d.openConsultation, onClick = { onOpenConversation(openId) }, icon = SadoraIcons.Message)
            profile.canMessage -> {
                SadoraButton(d.messageDoctor, onClick = onMessage, icon = SadoraIcons.Message)
                Text(
                    d.messageDoctorNote,
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> Text(
                d.cannotMessage,
                style = Sadora.type.body,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // A closed consultation is still her history with this doctor; it stays readable.
        val historyId = existing?.id ?: conversationId
        if (openId == null && historyId != null) {
            SadoraButton(d.viewHistory, onClick = { onOpenConversation(historyId) }, tone = ButtonTone.Secondary)
        }
    }
}

/**
 * What she agrees to before a consultation opens: free for now, a day long, her real
 * name and age seen by the doctor, an answer that is not a diagnosis, 103 for an
 * emergency. Confirming opens the consultation — or opens hers again — and the thread.
 */
@Composable
fun ConsultationConsentSheetContent(
    profile: DoctorProfile,
    messages: MessagesController,
    onStarted: (Conversation) -> Unit,
    onCancel: () -> Unit,
) {
    val d = strings.doctors
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    LaunchedEffect(profile.id) { messages.clearError() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        DoctorAvatar(profile.fullName, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.fullName, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f, fill = false))
                VerifiedMark(size = 16.dp)
            }
            Text(d.specialty(profile.specialty), style = Sadora.type.body, color = c.textAccent)
        }
    }
    val icons = listOf(SadoraIcons.Clock, SadoraIcons.Profile, SadoraIcons.Info, SadoraIcons.Shield)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        d.consentPoints.forEachIndexed { index, point ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(32.dp).clip(Radius.chip).background(c.primary.copy(alpha = if (c.isDark) 0.24f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icons[index % icons.size], contentDescription = null, Modifier.size(IconSize.sm), tint = c.primary)
                }
                Text(point, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f).padding(top = 4.dp))
            }
        }
    }
    messages.error?.let { ErrorStrip(it.readable()) }
    SadoraButton(
        d.consentConfirm,
        enabled = !working,
        onClick = {
            working = true
            scope.launch {
                messages.startConsultation(profile.id)?.let(onStarted)
                working = false
            }
        },
    )
    SadoraButton(strings.common.cancel, onClick = onCancel, tone = ButtonTone.Secondary)
}

@Composable
private fun DoctorStat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Sadora.type.h2, color = c.text)
        Text(label, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun DoctorSkeleton() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Skeleton(Modifier.size(84.dp), shape = Radius.chip)
        Skeleton(Modifier.size(width = 180.dp, height = 22.dp))
        Skeleton(Modifier.size(width = 120.dp, height = 16.dp))
        Skeleton(Modifier.fillMaxWidth().size(height = 72.dp, width = 0.dp))
    }
}

@Composable
private fun monthYear(at: Instant): String {
    val date = at.toLocalDateTime(TimeZone.currentSystemDefault()).date
    return strings.dates.monthYear(date.year, date.month.ordinal + 1)
}
