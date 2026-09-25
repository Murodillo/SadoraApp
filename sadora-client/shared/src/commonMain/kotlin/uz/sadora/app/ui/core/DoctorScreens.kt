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
 * her own words, and what she has written. No message button yet — paid consultations
 * are the next stage, and a button that goes nowhere is worse than none.
 */
@Composable
fun DoctorProfileScreen(
    doctorId: String,
    state: AppState,
    doctors: DoctorController,
    onOpenPost: (CommunityPost) -> Unit,
    onOpenMenu: (CommunityPost) -> Unit,
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
