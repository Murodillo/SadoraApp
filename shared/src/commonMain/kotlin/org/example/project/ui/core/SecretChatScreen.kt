package org.example.project.ui.core

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.example.project.design.IconSize
import org.example.project.design.MinTouchTarget
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.CommunityComment
import org.example.project.model.CommunityFilter
import org.example.project.model.CommunityPost
import org.example.project.model.CommunityTopic
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraCard
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.noRippleClickable
import org.example.project.ui.components.rememberShareAction

/** The tints an alias avatar can take, so the feed is not five identical circles. */
@Composable
private fun avatarTints(): List<Color> {
    val c = Sadora.colors
    return listOf(c.primary, c.secondary, c.accent, c.success)
}

/**
 * The secret chat: an anonymous feed, one room per topic.
 *
 * Every post is written under an alias and nothing here reaches back to an account —
 * that is the whole reason the space exists, and it is why the screen never shows a
 * real name, not even the reader's own.
 */
@Composable
fun SecretChatScreen(
    state: AppState,
    /**
     * Raises the comments sheet.
     *
     * The sheet is owned by the shell rather than by this screen so that it covers the
     * tab bar; a sheet opened from inside the content area is drawn underneath it.
     */
    onOpenComments: (CommunityPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val share = rememberShareAction()
    val posts = state.visiblePosts()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(
                title = "Maxfiy chat",
                trailing = {
                    SavedToggle(
                        active = state.communityFilter == CommunityFilter.Saved,
                        count = state.savedPosts.size,
                        onClick = {
                            state.communityFilter =
                                if (state.communityFilter == CommunityFilter.Saved) {
                                    CommunityFilter.Feed
                                } else {
                                    CommunityFilter.Saved
                                }
                        },
                    )
                },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CommunityTopic.entries.forEach { topic ->
                    SelectChip(
                        label = topic.label,
                        selected = state.communityTopic == topic,
                        onClick = { state.communityTopic = topic },
                    )
                }
            }

            if (posts.isEmpty()) {
                EmptyFeed(saved = state.communityFilter == CommunityFilter.Saved)
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        top = Spacing.xs,
                        // Room for the raised centre button on the tab bar.
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(posts, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            liked = post.id in state.likedPosts,
                            saved = post.id in state.savedPosts,
                            likes = state.likeCount(post),
                            comments = state.commentsOf(post).size,
                            onLike = { state.toggleLike(post.id) },
                            onSave = { state.toggleSaved(post.id) },
                            onComment = { onOpenComments(post) },
                            onShare = { share("${post.body}\n\nSADORA — Maxfiy chat") },
                        )
                    }
                }
            }
        }

    }
}

/**
 * The comments for one post, raised by the shell.
 *
 * Kept here rather than in the shell's own file because everything it draws — the
 * aliases, the tints, the input — belongs to this screen.
 */
@Composable
fun CommentsSheetContent(state: AppState, post: CommunityPost) {
    CommentsSheet(
        comments = state.commentsOf(post),
        onSend = { state.addComment(post.id, it) },
    )
}

@Composable
private fun SavedToggle(active: Boolean, count: Int, onClick: () -> Unit) {
    val c = Sadora.colors
    val tint by animateColorAsState(if (active) c.primary else c.muted, tween(220), label = "saved")
    Row(
        Modifier
            .clip(Radius.chip)
            .background(if (active) c.primary.copy(alpha = 0.12f) else Color.Transparent)
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleClickable(onClick = onClick)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(SadoraIcons.Bookmark, contentDescription = "Saqlangan", Modifier.size(IconSize.md), tint = tint)
        if (count > 0) Text("$count", style = Sadora.type.body, color = tint)
    }
}

@Composable
private fun EmptyFeed(saved: Boolean) {
    val c = Sadora.colors
    Column(
        Modifier.fillMaxSize().padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(Radius.chip).background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (saved) SadoraIcons.Bookmark else SadoraIcons.Lock,
                contentDescription = null,
                Modifier.size(28.dp),
                tint = c.secondary,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            if (saved) "Saqlangan post yo'q" else "Bu bo'limda hozircha post yo'q",
            style = Sadora.type.h3,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            if (saved) {
                "Yoqqan postni belgilab qo'ying — u shu yerda turadi."
            } else {
                "Boshqa bo'limlarni ko'ring yoki keyinroq qaytib keling."
            },
            style = Sadora.type.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
    }
}

// ---------------------------------------------------------------- post

@Composable
private fun PostCard(
    post: CommunityPost,
    liked: Boolean,
    saved: Boolean,
    likes: Int,
    comments: Int,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
) {
    val c = Sadora.colors
    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            AliasAvatar(post.alias, post.tint)
            Column(Modifier.weight(1f)) {
                Text(post.alias, style = Sadora.type.h3, color = c.text)
                Text(
                    "${post.topic.label} · ${post.ago}",
                    style = Sadora.type.body,
                    color = c.muted2,
                )
            }
        }
        Text(post.body, style = Sadora.type.body, color = c.text)
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostAction(
                icon = SadoraIcons.Heart,
                label = likes.toString(),
                active = liked,
                activeTint = c.primary,
                onClick = onLike,
            )
            PostAction(
                icon = SadoraIcons.Message,
                label = comments.toString(),
                onClick = onComment,
            )
            PostAction(icon = SadoraIcons.Share, onClick = onShare)
            Spacer(Modifier.weight(1f))
            PostAction(
                icon = SadoraIcons.Bookmark,
                active = saved,
                activeTint = c.secondary,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun AliasAvatar(alias: String, tint: Int, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val c = Sadora.colors
    val colour = avatarTints()[tint % avatarTints().size]
    Box(
        Modifier.size(size).clip(Radius.chip).background(colour.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            alias.take(1).uppercase(),
            style = Sadora.type.h3,
            color = colour,
        )
    }
}

/**
 * One action under a post.
 *
 * The icon springs when it turns on — small, and only on the transition, so a feed of
 * five posts never looks like it is fidgeting.
 */
@Composable
private fun PostAction(
    icon: ImageVector,
    onClick: () -> Unit,
    label: String? = null,
    active: Boolean = false,
    activeTint: Color = Sadora.colors.primary,
) {
    val c = Sadora.colors
    val tint by animateColorAsState(if (active) activeTint else c.muted, tween(220), label = "action")
    val scale by animateFloatAsState(
        targetValue = if (active) 1.12f else 1f,
        animationSpec = spring(dampingRatio = 0.45f),
        label = "action-scale",
    )
    Row(
        Modifier
            .clip(Radius.chip)
            .defaultMinSize(minHeight = MinTouchTarget)
            .noRippleClickable(onClick = onClick)
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            Modifier.size(IconSize.lg).graphicsLayer { scaleX = scale; scaleY = scale },
            tint = tint,
        )
        if (label != null) {
            Text(
                label,
                style = Sadora.type.body.copy(fontWeight = FontWeight.Medium),
                color = tint,
            )
        }
    }
}

// ---------------------------------------------------------------- comments

@Composable
private fun CommentsSheet(comments: List<CommunityComment>, onSend: (String) -> Unit) {
    val c = Sadora.colors
    var draft by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (comments.isEmpty()) {
            Text(
                "Hali izoh yo'q. Birinchi bo'lib javob bering.",
                style = Sadora.type.body,
                color = c.muted,
            )
        } else {
            comments.forEach { comment ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AliasAvatar(comment.alias, comment.tint, size = 30.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "${comment.alias} · ${comment.ago}",
                            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                            color = c.muted2,
                        )
                        Text(comment.body, style = Sadora.type.body, color = c.text)
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            SadoraTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "Izoh yozing",
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = draft.isNotBlank(),
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                Box(
                    Modifier
                        .size(MinTouchTarget)
                        .clip(Radius.chip)
                        .background(c.primary)
                        .noRippleClickable {
                            onSend(draft)
                            draft = ""
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SadoraIcons.ArrowUp,
                        contentDescription = "Yuborish",
                        Modifier.size(IconSize.md),
                        tint = c.onPrimary,
                    )
                }
            }
        }
    }
}
