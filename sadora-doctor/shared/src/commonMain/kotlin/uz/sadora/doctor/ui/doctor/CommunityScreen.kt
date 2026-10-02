package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.LoadMoreRow
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SelectChip
import uz.sadora.doctor.ui.components.Skeleton

/**
 * The fourth tab: the community feed the women's app calls Chat, as a doctor reads it —
 * every post, or only the doctors'. A post opens on its own page, where she can answer
 * under her name; the plus writes a post of her own.
 */
@Composable
fun CommunityScreen(
    doctors: DoctorController,
    onOpenPost: (String) -> Unit,
    onNewPost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tabs
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { doctors.loadFeed() }
    val posts = doctors.feed
    val doctorsOnly = doctors.feedDoctorsOnly

    Column(modifier) {
        SadoraTopBar(
            t.communityTitle,
            trailing = { CircleIconButton(SadoraIcons.Plus, contentDescription = strings.community.newPost, onClick = onNewPost) },
        )
        ScreenContent(animateItems = true) {
            item(key = "filters") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SelectChip(t.filterAll, selected = !doctorsOnly, onClick = { scope.launch { doctors.loadFeed(doctorsOnly = false) } })
                    SelectChip(t.filterDoctors, selected = doctorsOnly, onClick = { scope.launch { doctors.loadFeed(doctorsOnly = true) } })
                }
            }
            doctors.feedCalls.error?.let { failure ->
                item(key = "error") { ErrorStrip(failure.readable(), onRetry = { scope.launch { doctors.loadFeed() } }) }
            }
            if (!doctors.feedLoaded && doctors.feedCalls.error == null) {
                items(3, key = { "skeleton-$it" }) { Skeleton(Modifier.fillMaxWidth().height(120.dp), shape = Radius.card) }
            }
            if (doctors.feedLoaded && posts.isEmpty()) {
                item(key = "empty") {
                    EmptyState(title = t.feedEmpty, body = t.feedEmptyBody, actionText = strings.community.newPost, onAction = onNewPost)
                }
            }
            items(posts.size, key = { posts[it].id }) { index ->
                val post = posts[index]
                PostCard(post = post, onOpen = { onOpenPost(post.id) })
            }
            if (doctors.feedHasMore) {
                item(key = "more") { LoadMoreRow(doctors.feedOffset, onLoadMore = { doctors.loadMoreFeed() }) }
            }
        }
    }
}
