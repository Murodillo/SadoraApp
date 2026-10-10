package uz.sadora.app.ui.core

import uz.sadora.app.ui.components.noRippleToggleable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.draw.alpha
import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.app.ui.components.SelectableArt
import uz.sadora.app.resources.ic3d_bookmark
import uz.sadora.app.resources.ic3d_share
import uz.sadora.app.resources.ic3d_chats
import uz.sadora.app.resources.ic3d_heart
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import uz.sadora.app.ui.components.LoadMoreRow
import uz.sadora.app.ui.components.EmptyState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.boundsInWindow
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
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
import androidx.compose.ui.text.style.TextOverflow
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
import uz.sadora.app.ui.components.BadgeRow
import uz.sadora.app.ui.components.WornBadgeMark
import uz.sadora.app.ui.components.RoundIconButton
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.EntranceGated
import uz.sadora.app.ui.components.appearFromBelow
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.rememberShareAction
import kotlin.time.Clock
import uz.sadora.app.i18n.strings
import uz.sadora.app.data.readable
import uz.sadora.app.ui.components.acceptText
import uz.sadora.contract.Limits
import uz.sadora.app.resources.*

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
    /** A verified doctor's page, by her doctor id. */
    onOpenDoctor: (String) -> Unit,
    /** The directory of every verified doctor, behind the header's stethoscope. */
    onOpenDoctors: () -> Unit,
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
    /** The topic, whose posts, the order — once two rows of chips, now one sheet. */
    onOpenFilters: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.community
    val share = rememberShareAction()
    val posts = state.visiblePosts()

    // The server's feed replaces the samples on open; the samples are what a build
    // with no backend keeps showing.
    LaunchedEffect(community) { community.load() }
    // What she read here is reported as she leaves, not after the batch's wait.
    DisposableEffect(state) { onDispose { state.flushPostViews() } }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(
                title = t.title,
                onBack = onClose,
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        // Three, not five: the rules and the doctors' directory are named
                        // links under the bar, where nobody has to guess what a glyph means.
                        FilterIconButton(active = state.communityFiltered, onClick = onOpenFilters, contentDescription = t.filtersTitle)
                        UnreadIconButton(count = state.communityUnread, onClick = onOpenMessages, contentDescription = t.messagesTitle)
                        RoundIconButton(SadoraIcons.Pencil, onClick = onCompose, contentDescription = t.compose)
                    }
                },
            )
            // Under the bar rather than in it: beside the buttons the line had a third
            // of the width and broke into four.
            // A verified doctor is not anonymous here, and the header says so.
            Text(
                state.doctorName?.let(strings.doctors::writingAs)
                    ?: state.communityAlias?.let(t::anonymousAs) ?: t.anonymous,
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.padding(horizontal = Spacing.screen).padding(bottom = Spacing.xxs),
            )
            val link = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified)
            Row(
                Modifier.padding(horizontal = Spacing.screen),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                // Her alias is the door to her own page: the bio, the badges, the door switch.
                state.communityAlias?.let { alias ->
                    Row(
                        Modifier
                            .clip(Radius.chip)
                            .noRippleClickable { onOpenProfile(alias) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        WornBadgeMark(state.wornBadge, size = 24.dp)
                        BadgeRow(state.communityBadges, max = 3)
                        Text(t.viewProfile, style = link, color = c.textAccent)
                    }
                    Text("·", style = link, color = c.muted)
                }
                Text(
                    t.doctorsLink,
                    style = link,
                    color = c.textAccent,
                    modifier = Modifier.clip(Radius.chip).noRippleClickable(role = Role.Button, onClick = onOpenDoctors).padding(vertical = 2.dp),
                )
                Text("·", style = link, color = c.muted)
                Text(
                    t.rulesLink,
                    style = link,
                    color = c.textAccent,
                    modifier = Modifier.clip(Radius.chip).noRippleClickable(role = Role.Button, onClick = onOpenRules).padding(vertical = 2.dp),
                )
            }

            // Over a feed that is showing; with no feed the error is the state below.
            if (community.error != null && (community.loaded || posts.isNotEmpty())) {
                Text(
                    community.error?.readable().orEmpty(),
                    style = Sadora.type.body,
                    color = c.dangerText,
                    modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xs),
                )
            }

            if (posts.isEmpty() && !community.loaded && community.busy) {
                // The first load: the shape of a feed rather than a blank, so the screen
                // does not flash "nothing here" at someone whose feed is on its way.
                FeedSkeleton()
            } else if (posts.isEmpty() && !community.loaded && community.error != null) {
                // Not an empty feed — a feed that did not arrive. The way on is to ask
                // again, not to "write the first post" into a community of thousands.
                val retryScope = rememberCoroutineScope()
                EmptyState(
                    title = community.error?.readable().orEmpty(),
                    body = "",
                    actionText = strings.common.retry,
                    failed = true,
                    onAction = { retryScope.launch { community.load() } },
                    art = Res.drawable.ic3d_globe,
                )
            } else if (posts.isEmpty()) {
                EmptyFeed(filter = state.communityFilter, doctorsOnly = state.communityDoctorsOnly, onCompose = onCompose)
            } else {
                // A fling through the feed draws posts in place; only the first screenful rises.
                val feedState = rememberLazyListState()
                EntranceGated(feedState) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        state = feedState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = Spacing.screen,
                            end = Spacing.screen,
                            top = Spacing.xs,
                            // Room for the tab bar floating over the list.
                            bottom = 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
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
                                    onOpenAuthor = { post.doctor?.let { onOpenDoctor(it.id) } ?: onOpenProfile(post.alias) },
                                    onShare = { share("${post.body}\n\n" + t.shareSuffix) },
                                    onMore = { onOpenMenu(post) },
                                    onSeen = { state.postSeen(post) },
                                )
                            }
                        }
                        // Her own and her saved posts are filtered from what is loaded; only
                        // the feed itself pages on, or a short filtered list would read the
                        // whole room through one page at a time.
                        if (community.feedHasMore && state.communityFilter == CommunityFilter.Feed) {
                            item(key = "more") {
                                LoadMoreRow(community.feedLoadedCount, onLoadMore = { community.loadMoreFeed() })
                            }
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
private fun EmptyFeed(filter: CommunityFilter, doctorsOnly: Boolean, onCompose: () -> Unit) {
    val c = Sadora.colors
    val t = strings.community
    if (doctorsOnly && filter == CommunityFilter.Feed) {
        EmptyState(title = strings.doctors.nothingYet, body = strings.doctors.nothingYetBody, actionText = null, onAction = {}, art = Res.drawable.ic3d_doctor)
        return
    }
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
                    CommunityFilter.Feed -> SadoraIcons.Chats
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
    /** Null on the post's own page, where there is nowhere further to open it to. */
    onOpen: (() -> Unit)?,
    onOpenAuthor: () -> Unit,
    onShare: () -> Unit,
    onMore: () -> Unit,
    /** The card has been on screen long enough to count as read; see [whenSeen]. */
    onSeen: () -> Unit,
    /** False on the post's own page, where the whole text is the point. */
    foldable: Boolean = true,
) {
    val c = Sadora.colors
    val t = strings.community
    val doctor = post.doctor
    SadoraCard(modifier = Modifier.whenSeen(post.id, onSeen), onClick = onOpen) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // The name and the avatar open the author's page; the rest of the card, the post.
            Box(Modifier.clip(Radius.chip).noRippleClickable(onClick = onOpenAuthor)) {
                if (doctor != null) DoctorAvatar(post.alias, photoUrl = doctor.photoUrl) else AliasAvatar(post.alias, post.tint, frame = post.frame)
            }
            Column(Modifier.weight(1f).noRippleClickable(onClick = onOpenAuthor)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        if (post.isMine) "${post.alias} · ${t.you}" else post.alias,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // The badge she chose to wear stands right after her alias.
                    if (doctor == null) WornBadgeMark(post.worn, size = 26.dp)
                    if (doctor != null) VerifiedMark() else BadgeRow(post.badges, max = 2, compact = true)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // A doctor's specialty leads: it is why her post is worth reading.
                        (doctor?.let { strings.doctors.specialty(it.specialty) + " · " } ?: "") +
                            "${t.topic(post.topic)} · " + strings.dates.ago(post.createdAt, Clock.System.now()),
                        style = Sadora.type.body,
                        color = c.muted2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // Beside the time and not among the actions: it is a fact about the
                    // post, and down there it would look like something to press. A post
                    // nobody has reached yet says nothing rather than "0".
                    if (post.viewCount > 0) PostViews(post.viewCount)
                }
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
        // A question a doctor has answered says so on the card: that answer is often
        // the reason to open it.
        if (doctor == null && post.doctorAnswers > 0) {
            DoctorAnsweredChip(post.doctorAnswers, Modifier.noRippleClickable(onClick = onOpen ?: {}))
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PostAction(
                art = Res.drawable.ic3d_heart,
                name = t.like,
                label = likes.toString(),
                active = liked,
                activeTint = c.primary,
                onClick = onLike,
            )
            PostAction(
                art = Res.drawable.ic3d_chats,
                name = t.comments,
                label = comments.toString(),
                onClick = onOpen ?: {},
            )
            PostAction(art = Res.drawable.ic3d_share, name = t.sharePost, onClick = onShare)
            Spacer(Modifier.weight(1f))
            PostAction(
                art = Res.drawable.ic3d_bookmark,
                name = t.save,
                active = saved,
                activeTint = c.secondary,
                onClick = onSave,
            )
        }
    }
}

/** "· (eye) 240" after the post's age, read out as one phrase. */
@Composable
private fun PostViews(count: Int) {
    val c = Sadora.colors
    val t = strings.community
    Row(
        Modifier.clearAndSetSemantics { contentDescription = t.viewsSpoken(count) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(" · ", style = Sadora.type.body, color = c.muted2)
        Icon(SadoraIcons.Eye, contentDescription = null, Modifier.size(15.dp), tint = c.muted2)
        Text(" " + t.viewsShort(count), style = Sadora.type.body, color = c.muted2, maxLines = 1)
    }
}

/**
 * Calls [onSeen] once the card has been on screen long enough to have been read: half
 * of it — or, for a card taller than the screen, half the screen — for [SeenAfter].
 * A card flicked past never gets there. It may call again when the card comes back;
 * counting it once is the caller's business.
 */
@Composable
private fun Modifier.whenSeen(key: Any, onSeen: () -> Unit): Modifier {
    var showing by remember(key) { mutableStateOf(false) }
    val seen by rememberUpdatedState(onSeen)
    LaunchedEffect(key, showing) {
        if (showing) {
            delay(SeenAfter)
            seen()
        }
    }
    return onGloballyPositioned { card ->
        val height = card.size.height
        val enough = minOf(height, card.findRootCoordinates().size.height) / 2f
        showing = height > 0 && card.boundsInWindow().height >= enough
    }
}

private val SeenAfter = 1.seconds

/** Characters a post shows before it is folded. About six lines on a phone. */
private const val FoldLength = 280

@Composable
internal fun AliasAvatar(
    alias: String,
    tint: Int,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    /** The frame the alias wears; null draws the plain circle at full [size]. */
    frame: String? = null,
) {
    val colour = avatarTints()[tint % avatarTints().size]
    uz.sadora.app.ui.components.FramedAvatar(frame, size) { inner ->
        Box(
            Modifier.size(inner).clip(Radius.chip).background(colour.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                alias.take(1).uppercase(),
                style = Sadora.type.h3,
                color = colour,
            )
        }
    }
}

/**
 * One action under a post, as a clay icon like the tab bar's.
 *
 * Idle, the icon is drained of most of its colour; a like or a save brings it back in
 * full with the tab bar's hop — only on the transition, so a feed of five posts never
 * looks like it is fidgeting. Comment and share are never "on", so they stay drained
 * and the coloured ones in a row are exactly what she has done to the post.
 */
@Composable
private fun PostAction(
    art: DrawableResource,
    onClick: () -> Unit,
    /** What the action is, for a screen reader: the row draws only an icon and a count. */
    name: String,
    label: String? = null,
    /** Null for an action that is never "on" (comment, share); like and save are toggles. */
    active: Boolean? = null,
    activeTint: Color = Sadora.colors.primary,
) {
    val c = Sadora.colors
    val on = active == true
    val tint by animateColorAsState(if (on) activeTint else c.muted, tween(220), label = "action")
    val description = if (label != null) "$name, $label" else name
    Row(
        Modifier
            .clip(Radius.chip)
            .defaultMinSize(minWidth = MinTouchTarget, minHeight = MinTouchTarget)
            .then(
                if (active != null) {
                    Modifier.noRippleToggleable(active, Role.Checkbox, focusShape = Radius.chip) { onClick() }
                } else {
                    Modifier.noRippleClickable(focusShape = Radius.chip, onClick = onClick)
                },
            )
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SelectableArt(art, 26.dp, on, dimWhenIdle = true)
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
internal fun CommentRow(comment: CommunityComment, onOpenAuthor: () -> Unit, onLike: () -> Unit) {
    if (comment.doctor != null) {
        DoctorCommentRow(comment, onOpenAuthor, onLike)
        return
    }
    val c = Sadora.colors
    val t = strings.community
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Box(Modifier.clip(Radius.chip).noRippleClickable(onClick = onOpenAuthor)) {
            AliasAvatar(comment.alias, comment.tint, size = 30.dp, frame = comment.frame)
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
                WornBadgeMark(comment.worn, size = 20.dp)
                BadgeRow(comment.badges, max = 1, compact = true)
            }
            Text(comment.body, style = Sadora.type.body, color = c.text)
        }
        CommentLike(comment, onLike)
    }
}

/**
 * The heart at the end of a comment: the post's own, smaller, with the count under it
 * once there is one. Her comment that has not reached the server yet has no id to like,
 * so the heart waits, drawn but not a button.
 */
@Composable
private fun CommentLike(comment: CommunityComment, onLike: () -> Unit) {
    val c = Sadora.colors
    val sent = comment.id.isNotEmpty()
    val tint by animateColorAsState(if (comment.liked) c.primary else c.muted, tween(220), label = "commentLike")
    val name = strings.community.like
    val description = if (comment.likeCount > 0) "$name, ${comment.likeCount}" else name
    Column(
        Modifier
            .clip(Radius.chip)
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .then(
                if (sent) {
                    Modifier.noRippleToggleable(comment.liked, Role.Checkbox, focusShape = Radius.chip) { onLike() }
                } else {
                    Modifier.alpha(0.4f)
                },
            )
            .clearAndSetSemantics {
                contentDescription = description
                if (!sent) disabled()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SelectableArt(Res.drawable.ic3d_heart, 20.dp, comment.liked, dimWhenIdle = true)
        if (comment.likeCount > 0) {
            Text(
                comment.likeCount.toString(),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.Medium),
                color = tint,
            )
        }
    }
}

/**
 * A verified doctor's answer: set apart on a tinted card, labelled, with her name,
 * her specialty and the check mark, and the reminder that it is not a diagnosis.
 */
@Composable
private fun DoctorCommentRow(comment: CommunityComment, onOpenDoctor: () -> Unit, onLike: () -> Unit) {
    val c = Sadora.colors
    val d = strings.doctors
    val doctor = comment.doctor ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.primary.copy(alpha = if (c.isDark) 0.16f else 0.07f))
            .border(1.dp, c.primary.copy(alpha = 0.35f), Radius.cardSmall)
            .padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            d.doctorAnswer.uppercase(),
            style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold),
            color = c.textAccent,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Box(Modifier.clip(Radius.chip).noRippleClickable(onClick = onOpenDoctor)) {
                DoctorAvatar(comment.alias, size = 30.dp, photoUrl = doctor.photoUrl)
            }
            Column(Modifier.weight(1f).noRippleClickable(onClick = onOpenDoctor)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    Text(
                        comment.alias,
                        style = Sadora.type.h3,
                        color = c.text,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    VerifiedMark(size = 14.dp)
                }
                Text(
                    d.specialty(doctor.specialty) + " · " + strings.dates.ago(comment.createdAt, Clock.System.now()),
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted2,
                )
            }
            CommentLike(comment, onLike)
        }
        Text(comment.body, style = Sadora.type.body, color = c.text)
        Text(d.disclaimer, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    }
}

/**
 * The filter button on the chat header. A dot on its shoulder while any filter is off
 * its default, so a feed that looks thin says why without the chips on screen.
 */
@Composable
private fun FilterIconButton(active: Boolean, onClick: () -> Unit, contentDescription: String) {
    val c = Sadora.colors
    val stateText = if (active) strings.community.filtersOn else null
    Box {
        RoundIconButton(
            SadoraIcons.Filter,
            onClick = onClick,
            filled = false,
            contentDescription = contentDescription,
            // The dot was the only sign a filter was on; now the button says it too.
            modifier = Modifier.semantics { stateText?.let { stateDescription = it } },
        )
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
                    .size(10.dp)
                    .clip(Radius.chip)
                    .background(c.surface)
                    .padding(2.dp)
                    .clip(Radius.chip)
                    .background(c.secondary),
            )
        }
    }
}

/** The messages button on the chat header, with the unread count on its shoulder. */
@Composable
internal fun UnreadIconButton(count: Int, onClick: () -> Unit, contentDescription: String) {
    val c = Sadora.colors
    // The count on the shoulder is folded into the button's name, so it is read with it.
    val described = if (count > 0) "$contentDescription, ${strings.community.unreadCount(count)}" else contentDescription
    Box {
        RoundIconButton(SadoraIcons.Message, onClick = onClick, filled = false, contentDescription = described)
        if (count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .clip(Radius.chip)
                    .background(c.secondary)
                    .padding(horizontal = 5.dp)
                    .clearAndSetSemantics {},
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
    /** False when it did not go: the text then stays in the field instead of being lost. */
    onSend: suspend (String) -> Boolean,
    modifier: Modifier = Modifier,
    placeholder: String = strings.community.commentHint,
    maxLength: Int = Limits.COMMENT_MAX,
) {
    val c = Sadora.colors
    val t = strings.community
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendScope = rememberCoroutineScope()
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
                    .noRippleClickable(enabled = !sending, role = Role.Button) {
                        val text = draft
                        sending = true
                        sendScope.launch {
                            // Cleared only once it is sent. It used to be cleared first,
                            // and a message that failed offline took its text with it.
                            if (onSend(text) && draft == text) draft = ""
                            sending = false
                        }
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
