package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.doctor.data.ApiFailure
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.Motion
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.SectionHeader
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.SuccessCheck

/**
 * One question on a page of its own: the whole text, the thread under it with doctors'
 * answers first, and her answer field pinned above the keyboard.
 *
 * Laid out like the client's post page, so what she writes here sits where the woman
 * who asked will read it. Once she has answered, the tick is written above the field,
 * her answer slides into the thread, and the question leaves the waiting list — the
 * panel shows it going when she returns.
 */
@Composable
fun QuestionScreen(
    postId: String,
    doctors: DoctorController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.community
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val calls = doctors.threadCalls

    LaunchedEffect(postId) { doctors.openThread(postId) }

    // How many answers went out from this page; each one raises the success line.
    var sentCount by remember { mutableIntStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(sentCount) {
        if (sentCount == 0) return@LaunchedEffect
        celebrating = true
        delay(CelebrateMillis)
        celebrating = false
    }
    val post = doctors.thread?.takeIf { it.id == postId }
    val comments = if (post != null) doctors.threadComments else emptyList()

    Column(modifier.fillMaxSize().background(c.bg)) {
        SadoraTopBar(if (post?.doctor != null) t.postTitle else t.questionTitle, onBack = onClose)

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (post == null) {
                // A failure says so in the strip below; only the wait is drawn here.
                if (calls.error == null) {
                    item { Skeleton(Modifier.fillMaxWidth().height(160.dp), shape = Radius.card) }
                }
            } else {
                item { PostCard(post = post, onOpen = null, foldable = false) }
                item { SectionHeader(t.commentsCount(comments.size)) }
                when {
                    !doctors.threadLoaded && calls.error == null -> item {
                        Skeleton(Modifier.fillMaxWidth().height(64.dp))
                    }
                    comments.isEmpty() && doctors.threadLoaded -> item {
                        Text(t.noComments, style = Sadora.type.body, color = c.muted)
                    }
                    // Her answer arrives among the doctors' answers at the top: it fades in and
                    // the thread under it slides down to make room.
                    else -> items(comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment,
                            Modifier.animateItem(
                                fadeInSpec = tween(Motion.Standard),
                                placementSpec = tween(Motion.Standard, easing = Motion.Emphasized),
                                fadeOutSpec = tween(Motion.Quick),
                            ),
                        )
                    }
                }
            }
        }

        // Why an answer did not go — the daily limit, a restricted account, no network —
        // or why the page could not load, with the way to try again.
        calls.error?.let { failure ->
            // Gone is gone: deleted by her, or hidden by a moderator. Nothing to retry.
            val gone = failure is ApiFailure.NotFound
            ErrorStrip(
                if (gone) t.postMissing else failure.readable(),
                onRetry = if (gone) {
                    null
                } else {
                    {
                        calls.clearError()
                        scope.launch { doctors.openThread(postId) }
                    }
                },
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
        }

        if (post != null) {
            Column(
                Modifier
                    .background(c.surface)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                // The answer went out: the tick is written and the line says so, then both
                // fold away again. Each send plays it afresh.
                AnimatedVisibility(
                    visible = celebrating,
                    enter = fadeIn(tween(Motion.Standard)) + expandVertically(tween(Motion.Standard, easing = Motion.Emphasized)),
                    exit = fadeOut(tween(Motion.Standard)) + shrinkVertically(tween(Motion.Standard, easing = Motion.Emphasized)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        SuccessCheck(size = 28.dp, key = sentCount)
                        Text(t.answerSent, style = Sadora.type.h3, color = c.successText)
                    }
                }
                doctors.doctorName?.let { name ->
                    Text(
                        strings.doctors.writingAs(name),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                    )
                }
                AnswerInput(
                    onSend = { body ->
                        calls.clearError()
                        val sent = doctors.answer(postId, body)
                        if (sent) {
                            sentCount++
                            // Her answer joins the doctors' answers at the top of the thread.
                            scope.launch { list.animateScrollToItem(1) }
                        }
                        sent
                    },
                )
            }
        }
    }
}

/** How long the "sent" line stays under the thread before it folds away. */
private const val CelebrateMillis = 2_400L
