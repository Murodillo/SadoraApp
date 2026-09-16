package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import uz.sadora.app.data.CommunityController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.SectionHeader
import uz.sadora.app.ui.components.rememberShareAction

/**
 * One post on a page of its own: the whole text, the same actions as in the feed, the
 * comments under it, and the comment field pinned above the keyboard.
 *
 * The post is read back from the store by id rather than carried in, so a like or a
 * comment made here changes the card the feed will show on the way back.
 */
@Composable
fun PostDetailScreen(
    postId: String,
    state: AppState,
    community: CommunityController,
    onOpenMenu: (CommunityPost) -> Unit,
    onOpenProfile: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.community
    val share = rememberShareAction()
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val post = state.communityPosts.firstOrNull { it.id == postId }

    // The feed carries only a count; the comments themselves come when the page opens.
    LaunchedEffect(postId) { community.loadComments(postId) }

    Column(modifier.fillMaxSize().background(c.bg)) {
        SadoraTopBar(t.postTitle, onBack = onClose)

        if (post == null) {
            // Deleted, or reported away, while the page was open.
            Text(
                t.postDeleted,
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.padding(Spacing.screen),
            )
            return@Column
        }

        val comments = state.commentsOf(post)

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                PostCard(
                    post = post,
                    liked = post.id in state.likedPosts,
                    saved = post.id in state.savedPosts,
                    likes = state.likeCount(post),
                    comments = state.commentCountOf(post),
                    onLike = { state.toggleLike(post.id) },
                    onSave = { state.toggleSaved(post.id) },
                    onOpen = {},
                    onOpenAuthor = { onOpenProfile(post.alias) },
                    onShare = { share("${post.body}\n\n" + t.shareSuffix) },
                    onMore = { onOpenMenu(post) },
                    foldable = false,
                )
            }
            item { SectionHeader(t.commentsCount(comments.size)) }
            if (comments.isEmpty()) {
                item { Text(t.noComments, style = Sadora.type.body, color = c.muted) }
            } else {
                items(comments) { comment -> CommentRow(comment, onOpenAuthor = { onOpenProfile(comment.alias) }) }
            }
        }

        CommentInput(
            onSend = { body ->
                state.addComment(post.id, body)
                // Her comment is appended; the list follows it down.
                scope.launch { list.animateScrollToItem(comments.size + 2) }
            },
            modifier = Modifier
                .background(c.surface)
                .padding(horizontal = Spacing.screen, vertical = Spacing.xs)
                .navigationBarsPadding()
                .imePadding(),
        )
    }
}
