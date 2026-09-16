package uz.sadora.app.ui.core

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.data.CommunityController
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityComment
import uz.sadora.app.model.CommunityFilter
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.CommunitySort
import uz.sadora.app.model.CommunityTopic
import uz.sadora.app.ui.components.BadgeRow
import uz.sadora.app.ui.components.RoundIconButton
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.SegmentedControl
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.appearFromBelow
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.rememberShareAction
import kotlin.time.Clock
import uz.sadora.app.i18n.strings
import uz.sadora.app.data.readable
import uz.sadora.app.ui.components.acceptText
import uz.sadora.contract.Limits

/** The tints an alias avatar can take, so the feed is not five identical circles. */
@Composable
private fun avatarTints(): List<Color> {
    val c = Sadora.colors
    val t = strings.community
    return listOf(c.primary, c.secondary, c.accent, c.success)
}

/**
 * The secret chat: an anonymous feed, one room per topic.
 *
 * Every post is written under an alias and nothing here reaches back to an account —
 * that is the whole reason the space exists, and it is why the screen never shows a
 * real name, not even the reader's own. What it does show, in the header, is her
 * alias: the name her posts carry, so she is never surprised by it.
 *
 * A root tab now, so there is no back button; the profile's row into it just selects
 * the tab.
 */
@Composable
fun SecretChatScreen(
    state: AppState,
    community: CommunityController,
    /** Opens the post's own page: the whole text and the comments. */
    onOpenPost: (CommunityPost) -> Unit,
    /** An alias's page — an author's from a card, her own from the header. */
    onOpenProfile: (String) -> Unit,
    onOpenMessages: () -> Unit,
    /**
     * Raises the post menu.
     *
     * The sheet is owned by the shell rather than by this screen so that it covers the
     * tab bar; a sheet opened from inside the content area is drawn underneath it. The
     * composer and the rules are raised the same way for the same reason.
     */
    onOpenMenu: (CommunityPost) -> Unit,
    onCompose: () -> Unit,
    onOpenRules: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.community
    val share = rememberShareAction()
    val posts = state.visiblePosts()
    val filters = CommunityFilter.entries

    // The server's feed replaces the samples on open; the samples are what a build
    // with no backend keeps showing.
    LaunchedEffect(community) { community.load() }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(
                title = t.title,
                onBack = onClose,
                subtitle = state.communityAlias?.let(t::anonymousAs) ?: t.anonymous,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        RoundIconButton(SadoraIcons.Info, onClick = onOpenRules, filled = false, contentDescription = t.rulesTitle)
                        UnreadIconButton(count = state.communityUnread, onClick = onOpenMessages, contentDescription = t.messagesTitle)
                        RoundIconButton(SadoraIcons.Pencil, onClick = onCompose, contentDescription = t.compose)
                    }
                },
            )
            // Her alias is the door to her own page: the bio, the badges, the door switch.
            state.communityAlias?.let { alias ->
                Row(
                    Modifier
                        .padding(horizontal = Spacing.screen)
                        .clip(Radius.chip)
                        .noRippleClickable { onOpenProfile(alias) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    BadgeRow(state.communityBadges, max = 3)
                    Text(t.viewProfile, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.textAccent)
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                CommunityTopic.entries.forEach { topic ->
                    SelectChip(
                        label = t.topic(topic),
                        selected = state.communityTopic == topic,
                        onClick = { state.communityTopic = topic },
                    )
                }
            }

            // Whose posts, and in what order. The sort is one chip that flips: two
            // states do not need a control of their own.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                SegmentedControl(
                    options = filters.map(t::filter),
                    selectedIndex = filters.indexOf(state.communityFilter),
                    onSelect = { state.communityFilter = filters[it] },
                    modifier = Modifier.weight(1f),
                )
                SelectChip(
                    label = t.sort(CommunitySort.Active),
                    selected = state.communitySort == CommunitySort.Active,
                    onClick = {
                        state.communitySort =
                            if (state.communitySort == CommunitySort.Active) CommunitySort.Newest else CommunitySort.Active
                    },
                )
            }

            if (community.error != null) {
                Text(
                    community.error?.readable().orEmpty(),
                    style = Sadora.type.body,
                    color = c.danger,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                )
            }

            if (posts.isEmpty() && !community.loaded && community.busy) {
                // The first load: the shape of a feed rather than a blank, so the screen
                // does not flash "nothing here" at someone whose feed is on its way.
                FeedSkeleton()
            } else if (posts.isEmpty()) {
                EmptyFeed(filter = state.communityFilter, onCompose = onCompose)
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
                    itemsIndexed(posts, key = { _, post -> post.id }) { index, post ->
                        Box(Modifier.appearFromBelow(index.coerceAtMost(6))) {
                            PostCard(
                                post = post,
                                liked = post.id in state.likedPosts,
                                saved = post.id in state.savedPosts,
                                likes = state.likeCount(post),
                                comments = state.commentCountOf(post),
                                onLike = { state.toggleLike(post.id) },
                                onSave = { state.toggleSaved(post.id) },
                                onOpen = { onOpenPost(post) },
                                onOpenAuthor = { onOpenProfile(post.alias) },
                                onShare = { share("${post.body}\n\n" + t.shareSuffix) },
                                onMore = { onOpenMenu(post) },
                            )
                        }
                    }
                }
            }
        }

    }
}

/** Three post-shaped placeholders while the first feed is on its way. */
@Composable
private fun FeedSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        repeat(3) {
            SadoraCard {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(Modifier.size(36.dp), shape = Radius.chip)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skeleton(Modifier.height(14.dp).fillMaxWidth(0.4f))
                        Skeleton(Modifier.height(12.dp).fillMaxWidth(0.25f))
                    }
                }
                Skeleton(Modifier.height(14.dp).fillMaxWidth())
                Skeleton(Modifier.height(14.dp).fillMaxWidth(0.7f))
            }
        }
    }
}

@Composable
private fun EmptyFeed(filter: CommunityFilter, onCompose: () -> Unit) {
    val c = Sadora.colors
    val t = strings.community
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
                when (filter) {
                    CommunityFilter.Feed -> SadoraIcons.Lock
                    CommunityFilter.Saved -> SadoraIcons.Bookmark
                    CommunityFilter.Mine -> SadoraIcons.Pencil
                },
                contentDescription = null,
                Modifier.size(28.dp),
                tint = c.secondary,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            when (filter) {
                CommunityFilter.Feed -> t.nothingHere
                CommunityFilter.Saved -> t.nothingSaved
                CommunityFilter.Mine -> t.nothingMine
            },
            style = Sadora.type.h3,
            color = c.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xxs))
        Text(
            when (filter) {
                CommunityFilter.Feed -> t.nothingHereBody
                CommunityFilter.Saved -> t.nothingSavedBody
                CommunityFilter.Mine -> t.nothingMineBody
            },
            style = Sadora.type.body,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        if (filter != CommunityFilter.Saved) {
            Spacer(Modifier.height(Spacing.md))
            SadoraButton(t.write, onClick = onCompose, fillWidth = false)
        }
    }
}

// ---------------------------------------------------------------- post

/**
 * One post in the feed, the way a LinkedIn card reads: the head, the text cut at a
 * few lines with "…ko'proq" inline, and the actions. The card itself opens the post's
 * page; only the "more" link expands in place.
 */
@Composable
internal fun PostCard(
    post: CommunityPost,
    liked: Boolean,
    saved: Boolean,
    likes: Int,
    comments: Int,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onOpen: () -> Unit,
    onOpenAuthor: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    /** False on the post's own page, where the whole text is the point. */
    foldable: Boolean = true,
) {
    val c = Sadora.colors
    val t = strings.community
    SadoraCard(onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // The name and the avatar open the author's page; the rest of the card, the post.
            Box(Modifier.clip(Radius.chip).noRippleClickable(onClick = onOpenAuthor)) {
                AliasAvatar(post.alias, post.tint)
            }
            Column(Modifier.weight(1f).noRippleClickable(onClick = onOpenAuthor)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        if (post.isMine) "${post.alias} · ${t.you}" else post.alias,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                    )
                    BadgeRow(post.badges, max = 2, compact = true)
                }
                Text(
                    "${t.topic(post.topic)} · " + strings.dates.ago(post.createdAt, Clock.System.now()),
                    style = Sadora.type.body,
                    color = c.muted2,
                )
            }
            Icon(
                SadoraIcons.More,
                contentDescription = t.more,
                Modifier
                    .size(MinTouchTarget)
                    .clip(Radius.chip)
                    .noRippleClickable(onClick = onMore)
                    .padding(12.dp),
                tint = c.muted2,
            )
        }
        // A long post is cut at the fold so the feed stays a feed, with the link on the
        // same line as the cut. Tapping it expands in place; tapping the text opens the page.
        var expanded by remember(post.id) { mutableStateOf(false) }
        val folded = foldable && !expanded && post.body.length > FoldLength
        if (folded) {
            Text(
                buildAnnotatedString {
                    append(post.body.take(FoldLength).trimEnd())
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "more",
                            styles = TextLinkStyles(SpanStyle(color = c.textAccent, fontWeight = FontWeight.Medium)),
                        ) { expanded = true },
                    ) { append(t.readMore) }
                },
                style = Sadora.type.body,
                color = c.text,
            )
        } else {
            Text(post.body, style = Sadora.type.body, color = c.text)
        }
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
                onClick = onOpen,
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

/** Characters a post shows before it is folded. About six lines on a phone. */
private const val FoldLength = 280

@Composable
internal fun AliasAvatar(alias: String, tint: Int, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val c = Sadora.colors
    val t = strings.community
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
    val t = strings.community
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

/** One comment on the post page: the alias with its badges, its age, and the text. */
@Composable
internal fun CommentRow(comment: CommunityComment, onOpenAuthor: () -> Unit) {
    val c = Sadora.colors
    val t = strings.community
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.clip(Radius.chip).noRippleClickable(onClick = onOpenAuthor)) {
            AliasAvatar(comment.alias, comment.tint, size = 30.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                Modifier.noRippleClickable(onClick = onOpenAuthor),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    (if (comment.isMine) t.youParenthesised(comment.alias) else comment.alias) +
                        " · " + strings.dates.ago(comment.createdAt, Clock.System.now()),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted2,
                )
                BadgeRow(comment.badges, max = 1, compact = true)
            }
            Text(comment.body, style = Sadora.type.body, color = c.text)
        }
    }
}

/** The messages button on the chat header, with the unread count on its shoulder. */
@Composable
internal fun UnreadIconButton(count: Int, onClick: () -> Unit, contentDescription: String) {
    val c = Sadora.colors
    Box {
        RoundIconButton(SadoraIcons.Message, onClick = onClick, filled = false, contentDescription = contentDescription)
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(Radius.chip)
                    .background(c.secondary)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (count > 99) "99+" else "$count",
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Bold),
                    color = c.onPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The comment field with its send button, which appears only once there is something to send. */
@Composable
internal fun CommentInput(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = strings.community.commentHint,
    maxLength: Int = Limits.COMMENT_MAX,
) {
    val c = Sadora.colors
    val t = strings.community
    var draft by remember { mutableStateOf("") }
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        SadoraTextField(
            value = draft,
            onValueChange = { draft = acceptText(it, maxLength) },
            placeholder = placeholder,
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
                    contentDescription = t.send,
                    Modifier.size(IconSize.md),
                    tint = c.onPrimary,
                )
            }
        }
    }
}
