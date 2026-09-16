package uz.sadora.app.ui.core

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.CommunityController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AliasProfile
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityBadge
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.BadgeChip
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SectionHeader
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.icon
import uz.sadora.app.ui.components.rememberShareAction
import uz.sadora.app.ui.components.tint

/**
 * An alias's page: the avatar, the bio, the badges with what each one means, three
 * counts, and her recent posts.
 *
 * The same screen serves her own alias — then the message button becomes "edit bio"
 * and there is no block. Everything is read from the server on open, so a bio she just
 * saved or a block she just lifted is what the page shows.
 */
@Composable
fun AliasProfileScreen(
    alias: String,
    state: AppState,
    community: CommunityController,
    onOpenPost: (CommunityPost) -> Unit,
    onOpenMenu: (CommunityPost) -> Unit,
    onMessage: (String) -> Unit,
    onEditBio: () -> Unit,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.community
    val c = Sadora.colors
    val errors = strings.errors
    val scope = rememberCoroutineScope()
    val share = rememberShareAction()
    var confirmBlock by remember { mutableStateOf(false) }

    LaunchedEffect(alias) { community.loadProfile(alias) }

    val profile = community.profile?.takeIf { it.alias == alias }

    Column(modifier) {
        SadoraTopBar(
            if (profile?.isMe == true) t.myProfileTitle else t.profileTitle,
            onBack = onClose,
        )

        ScreenContent {
            community.error?.let { failure ->
                item { ErrorStrip(failure.readable(errors), onRetry = community::clearError) }
            }

            if (profile == null) {
                item { ProfileSkeleton() }
                return@ScreenContent
            }

            item { ProfileHeader(profile) }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    when {
                        profile.isMe -> SadoraButton(t.editBio, onClick = onEditBio, modifier = Modifier.weight(1f))
                        profile.blocked -> PillButton(
                            t.unblock,
                            onClick = {
                                scope.launch {
                                    if (community.setBlocked(alias, blocked = false)) onToast(t.unblocked)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        profile.canMessage -> {
                            SadoraButton(t.messageButton, onClick = { onMessage(alias) }, modifier = Modifier.weight(1f))
                            PillButton(t.block, onClick = { confirmBlock = true })
                        }
                        else -> {
                            Text(
                                t.messagesClosed,
                                style = Sadora.type.body,
                                color = c.muted,
                                modifier = Modifier.weight(1f).padding(vertical = Spacing.xs),
                            )
                            PillButton(t.block, onClick = { confirmBlock = true })
                        }
                    }
                }
            }

            item { StatsRow(profile) }

            item {
                SadoraCard {
                    Text(t.badgesTitle, style = Sadora.type.h3, color = c.text)
                    if (profile.badges.isEmpty()) {
                        Text(t.noBadges, style = Sadora.type.body, color = c.muted)
                    } else {
                        profile.badges.forEach { badge -> BadgeLine(badge) }
                    }
                }
            }

            item { SectionHeader(t.herPosts) }
            if (profile.posts.isEmpty()) {
                item { Text(t.noPostsYet, style = Sadora.type.body, color = c.muted) }
            } else {
                items(profile.posts.size, key = { profile.posts[it].id }) { index ->
                    val post = profile.posts[index]
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

    SadoraDialog(
        visible = confirmBlock,
        title = t.blockConfirmTitle,
        body = t.blockConfirmBody,
        confirmText = t.block,
        cancelText = strings.common.cancel,
        onConfirm = {
            confirmBlock = false
            scope.launch { if (community.setBlocked(alias, blocked = true)) onToast(t.blocked) }
        },
        onDismiss = { confirmBlock = false },
    )
}

@Composable
private fun ProfileHeader(profile: AliasProfile) {
    val t = strings.community
    val c = Sadora.colors
    val since = profile.memberSince.toLocalDateTime(TimeZone.currentSystemDefault()).date
    Column(
        Modifier.fillMaxWidth().padding(top = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        AliasAvatar(profile.alias, profile.tint, size = 84.dp)
        Text(profile.alias, style = Sadora.type.h2, color = c.text, textAlign = TextAlign.Center)
        Text(
            t.memberSince(strings.dates.monthYear(since.year, since.month.ordinal + 1)),
            style = Sadora.type.caption,
            color = c.muted2,
        )
        if (profile.badges.isNotEmpty()) {
            ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                profile.badges.forEach { BadgeChip(it) }
            }
        }
        Text(
            profile.bio ?: t.noBio,
            style = Sadora.type.body,
            color = if (profile.bio == null) c.muted2 else c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
    }
}

/** Posts, comments, likes received: what she has put into the room. */
@Composable
private fun StatsRow(profile: AliasProfile) {
    val t = strings.community
    SadoraCard(padding = Spacing.sm) {
        Row(Modifier.fillMaxWidth()) {
            Stat(Fmt.int(profile.postCount), t.statPosts, Modifier.weight(1f))
            Stat(Fmt.int(profile.commentCount), t.statComments, Modifier.weight(1f))
            Stat(Fmt.int(profile.likesReceived), t.statLikes, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Sadora.type.h2, color = c.text)
        Text(label, style = Sadora.type.caption, color = c.muted)
    }
}

/** A badge with the sentence that earns it — the profile is where the rules are read. */
@Composable
private fun BadgeLine(badge: CommunityBadge) {
    val t = strings.community
    val c = Sadora.colors
    val tint = badge.tint()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box(
            Modifier.size(36.dp).clip(Radius.chip).background(tint.copy(alpha = if (c.isDark) 0.22f else 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(badge.icon(), contentDescription = null, Modifier.size(18.dp), tint = tint)
        }
        Column(Modifier.weight(1f)) {
            Text(t.badge(badge), style = Sadora.type.h3, color = c.text)
            Text(t.badgeHint(badge), style = Sadora.type.body, color = c.muted)
        }
    }
}

@Composable
private fun ProfileSkeleton() {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Skeleton(Modifier.size(84.dp), shape = Radius.chip)
        Skeleton(Modifier.size(width = 160.dp, height = 22.dp))
        Skeleton(Modifier.fillMaxWidth(0.8f).size(height = 16.dp, width = 0.dp))
        Skeleton(Modifier.fillMaxWidth().size(height = 72.dp, width = 0.dp))
    }
}
