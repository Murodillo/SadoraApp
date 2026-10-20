package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.contract.Limits
import uz.sadora.contract.MediaKind
import uz.sadora.contract.MessageMedia
import uz.sadora.doctor.data.ApiFailure
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.OutgoingAlbum
import uz.sadora.doctor.data.OutgoingState
import uz.sadora.doctor.data.PickedMedia
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.MediaStrings
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.SystemBackHandler
import uz.sadora.doctor.ui.components.VideoPlayer
import uz.sadora.doctor.ui.components.acceptText
import uz.sadora.doctor.ui.components.noRippleClickable
import uz.sadora.doctor.ui.components.rememberMediaSaver

// Photos and videos in a consultation, ported from the client app's thread: the album in a
// bubble, her own album on its way up, what she picked before it goes, and the viewer.

// ---------------------------------------------------------------- rules, kept testable

/**
 * How an album's tiles sit, as rows of indices: one tile alone; two side by side; three
 * as one wide over two; four and more as two by two, the last carrying "+N".
 */
internal fun albumRows(count: Int): List<List<Int>> = when {
    count <= 0 -> emptyList()
    count == 1 -> listOf(listOf(0))
    count == 2 -> listOf(listOf(0, 1))
    count == 3 -> listOf(listOf(0), listOf(1, 2))
    else -> listOf(listOf(0, 1), listOf(2, 3))
}

/** "1:05" for a video's length. */
internal fun formatDuration(ms: Long?): String {
    val seconds = ((ms ?: 0) + 500) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

/**
 * The consultation list's line for a photos-and-videos message: "Video · 0:42",
 * "3 ta rasm · izoh". No emoji, unlike the client app's: the row already draws the
 * clay icon in front of every line that is not plain text.
 */
internal fun mediaLastLine(t: MediaStrings, first: MessageMedia?, count: Int, videos: Int, caption: String): String {
    val what = when {
        first == null -> t.photo
        count <= 1 && first.kind == MediaKind.VIDEO -> "${t.video} · ${formatDuration(first.durationMs)}"
        count <= 1 -> t.photo
        videos >= count -> t.count(0, count)
        else -> t.count(count - videos, videos)
    }
    return if (caption.isBlank()) what else "$what · $caption"
}

// ---------------------------------------------------------------- bubbles

/** As wide as an older photo's bubble draws it, so an album and a photo line up in the thread. */
private val AlbumWidth = 260.dp
private val TileGap = 3.dp

/**
 * The photos and videos of a line, as one grid in the bubble. A lone item keeps its own
 * shape, held to a range a bubble can draw; several are cut square into rows.
 */
@Composable
internal fun MediaAlbum(items: List<MessageMedia>, doctors: DoctorController, onOpen: (Int) -> Unit) {
    val t = strings.media
    AlbumGrid(
        count = items.size,
        singleAspect = items.firstOrNull()?.let { aspectOf(it.width, it.height) } ?: 1f,
    ) { index, modifier ->
        val item = items[index]
        val bitmap by produceState<ImageBitmap?>(null, item.id) {
            if (item.hasThumbnail) value = doctors.mediaThumb(item.id)?.let { decode(it) }
        }
        val label = if (item.kind == MediaKind.VIDEO) {
            val seconds = ((item.durationMs ?: 0) + 500) / 1000
            t.videoLabel((seconds / 60).toInt(), (seconds % 60).toInt())
        } else {
            t.photoLabel(index + 1, items.size)
        }
        MediaTile(
            bitmap = bitmap,
            loading = bitmap == null && item.hasThumbnail,
            video = item.takeIf { it.kind == MediaKind.VIDEO },
            overflow = if (index == 3 && items.size > 4) items.size - 4 else 0,
            modifier = modifier
                .semantics { contentDescription = label }
                .noRippleClickable(role = Role.Button) { onOpen(index) },
        )
    }
}

/**
 * Her album on its way: the pictures she picked, under a ring that fills as they go up and
 * a cross that stops them; or, once it failed, why, with "Qayta yuborish" and "O'chirish".
 * Her side of the thread, in her bubble's colour, like the line it turns into.
 */
@Composable
internal fun OutgoingAlbumBubble(album: OutgoingAlbum, onRetry: () -> Unit, onDiscard: () -> Unit) {
    val c = Sadora.colors
    val t = strings.media
    val failed = album.state as? OutgoingState.Failed
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Column(
            Modifier
                .widthIn(max = 290.dp)
                .clip(Radius.card)
                .background(c.primaryFill)
                .padding(Spacing.xxs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Box {
                AlbumGrid(count = album.items.size, singleAspect = 1f) { index, modifier ->
                    val item = album.items[index]
                    val preview = item.preview
                    val bitmap by produceState<ImageBitmap?>(null, preview) { value = preview?.let { decode(it) } }
                    Box(modifier) {
                        MediaTile(
                            bitmap = bitmap,
                            loading = bitmap == null,
                            video = null,
                            overflow = if (index == 3 && album.items.size > 4) album.items.size - 4 else 0,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // The ring sits over the middle of the album, so a video's mark goes in its corner.
                        if (item.picked.kind == MediaKind.VIDEO) {
                            Icon(SadoraIcons.Play, contentDescription = null, Modifier.align(Alignment.BottomStart).padding(Spacing.xs).size(18.dp), tint = Color.White)
                        }
                    }
                }
                if (failed == null) {
                    val cancel = strings.common.cancel
                    val progress = album.progress
                    Box(
                        Modifier.align(Alignment.Center).size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            progress = { progress.coerceIn(0.02f, 1f) },
                            modifier = Modifier.size(44.dp).semantics { contentDescription = t.sending((progress * 100).toInt()) },
                            color = Color.White,
                            strokeWidth = 3.dp,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .semantics { contentDescription = cancel }
                                .noRippleClickable(role = Role.Button, onClick = onDiscard),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(SadoraIcons.Plus, contentDescription = null, Modifier.size(IconSize.sm).graphicsLayer { rotationZ = 45f }, tint = Color.White)
                        }
                    }
                }
            }
            if (album.caption.isNotBlank()) {
                Text(album.caption, style = Sadora.type.body, color = c.onPrimary, modifier = Modifier.padding(horizontal = Spacing.xs).width(AlbumWidth))
            }
            Text(
                when {
                    failed != null -> t.failed
                    album.progress < 0.01f -> t.preparing
                    else -> t.sending((album.progress * 100).toInt())
                },
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.onPrimary,
                modifier = Modifier.align(Alignment.End).padding(horizontal = Spacing.xs),
            )
        }
        if (failed != null) {
            Text(
                failureText(t, failed),
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = c.dangerText,
                modifier = Modifier.padding(top = Spacing.xxs, end = Spacing.xs),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.padding(top = Spacing.xxs)) {
                PillButton(t.discard, onClick = onDiscard)
                PillButton(t.retry, onClick = onRetry, tone = ButtonTone.Primary)
            }
        }
    }
}

@Composable
private fun failureText(t: MediaStrings, failed: OutgoingState.Failed): String {
    failed.problem?.let { return t.problem(it) }
    return when (val failure = failed.failure) {
        is ApiFailure.LimitReached -> t.limitReached
        is ApiFailure.FeatureDisabled -> t.featureOff
        null -> t.interrupted
        // Validation is the server's own reason — "Video 3 daqiqadan uzun bo'lmasin".
        else -> failure.readable(strings.errors)
    }
}

/** [count] tiles laid out by [albumRows]; [tile] draws one into the modifier it is given. */
@Composable
private fun AlbumGrid(count: Int, singleAspect: Float, tile: @Composable (Int, Modifier) -> Unit) {
    Column(Modifier.width(AlbumWidth).clip(Radius.cardSmall), verticalArrangement = Arrangement.spacedBy(TileGap)) {
        albumRows(count).forEachIndexed { row, indices ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                indices.forEach { index ->
                    val aspect = when {
                        count == 1 -> 1f / singleAspect
                        count == 3 && row == 0 -> 16f / 10f
                        else -> 1f
                    }
                    tile(index, Modifier.weight(1f).aspectRatio(aspect))
                }
            }
        }
    }
}

@Composable
private fun MediaTile(bitmap: ImageBitmap?, loading: Boolean, video: MessageMedia?, overflow: Int, modifier: Modifier) {
    val c = Sadora.colors
    Box(modifier.background(c.surface2), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            loading -> Skeleton(Modifier.fillMaxSize(), shape = RoundedCornerShape(0.dp))
            // A video that came without a poster: the surface, dimmed so the white play mark reads on it.
            else -> Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        if (video != null) {
            PlayGlyph()
            Text(
                "${formatDuration(video.durationMs)} · ${strings.media.size(video.sizeBytes)}",
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(Radius.chip)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (overflow > 0) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Text("+$overflow", style = Sadora.type.h2, color = Color.White)
            }
        }
    }
}

@Composable
private fun BoxScope.PlayGlyph(size: Dp = 44.dp) {
    Box(
        Modifier.align(Alignment.Center).size(size).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(SadoraIcons.Play, contentDescription = null, Modifier.size(size / 2), tint = Color.White)
    }
}

// ---------------------------------------------------------------- before sending

/**
 * What she picked, before it goes: the pictures in a row, each with a cross, a note for
 * the patient, and the button. Videos over three minutes and anything past [max] are
 * named here, not after an upload the server would refuse.
 */
@Composable
internal fun MediaSendContent(
    picked: List<PickedMedia>,
    max: Int,
    onRemove: (PickedMedia) -> Unit,
    onSend: (String) -> Unit,
) {
    val c = Sadora.colors
    val t = strings.media
    var caption by remember { mutableStateOf("") }
    val tooLong = picked.any { (it.durationMs ?: 0) > Limits.MEDIA_VIDEO_MAX_MS }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        picked.forEach { item ->
            val bitmap by produceState<ImageBitmap?>(null, item) { value = item.preview()?.let { decode(it) } }
            Box(Modifier.size(96.dp).clip(Radius.cardSmall).background(c.surface2), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image != null) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Skeleton(Modifier.fillMaxSize())
                }
                if (item.kind == MediaKind.VIDEO) {
                    PlayGlyph(28.dp)
                    Text(
                        formatDuration(item.durationMs),
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spacing.xxs)
                            .background(Color.Black.copy(alpha = 0.5f), Radius.chip)
                            .padding(horizontal = Spacing.xxs),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xxs)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .semantics { contentDescription = t.remove }
                        .noRippleClickable(role = Role.Button) { onRemove(item) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SadoraIcons.Plus, contentDescription = null, Modifier.size(14.dp).graphicsLayer { rotationZ = 45f }, tint = Color.White)
                }
            }
        }
    }
    when {
        picked.size > max -> Text(t.tooMany(max), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.dangerText)
        tooLong -> Text(t.videoTooLong, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.dangerText)
    }
    SadoraTextField(
        value = caption,
        onValueChange = { caption = acceptText(it, Limits.MESSAGE_MAX) },
        placeholder = t.captionHint,
        singleLine = false,
    )
    SadoraButton(
        t.sendTitle(picked.size),
        onClick = { onSend(caption) },
        enabled = picked.isNotEmpty() && picked.size <= max && !tooLong,
        icon = SadoraIcons.ArrowUp,
    )
}

// ---------------------------------------------------------------- the viewer

/**
 * An album opened over the thread: swiped through, a photo pinched to look closer, a video
 * played with the platform's own controls. Pulled down, back, or the cross closes it; the
 * top-right button saves the item on screen to her phone, which is the only way anything
 * of the patient's leaves the app.
 */
@Composable
internal fun MediaViewer(
    items: List<MessageMedia>,
    start: Int,
    caption: String,
    doctors: DoctorController,
    onToast: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val t = strings.media
    val scope = rememberCoroutineScope()
    val saver = rememberMediaSaver()
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val pull = remember { Animatable(0f) }
    val dismissAt = with(LocalDensity.current) { 140.dp.toPx() }
    var zoomed by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    SystemBackHandler(enabled = true, onBack = onDismiss)

    Box(
        Modifier
            .fillMaxSize()
            // Opaque at rest; it thins as she pulls, so the thread shows where she is going back to.
            .background(Color.Black.copy(alpha = (1f - abs(pull.value) / (dismissAt * 3)).coerceIn(0.4f, 1f)))
            .draggable(
                enabled = !zoomed,
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta -> scope.launch { pull.snapTo(pull.value + delta) } },
                onDragStopped = {
                    if (abs(pull.value) > dismissAt) onDismiss() else scope.launch { pull.animateTo(0f) }
                },
            ),
    ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !zoomed,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = pull.value },
        ) { page ->
            val item = items[page]
            when (item.kind) {
                MediaKind.PHOTO -> ViewerPhoto(item, doctors, onZoomed = { zoomed = it })
                MediaKind.VIDEO -> ViewerVideo(item, doctors, playing = pager.currentPage == page, onFailed = { onToast(t.videoFailed) })
            }
        }
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CircleIconButton(
                SadoraIcons.Plus,
                contentDescription = strings.common.close,
                onClick = onDismiss,
                modifier = Modifier.graphicsLayer { rotationZ = 45f },
            )
            if (items.size > 1) {
                Text("${pager.currentPage + 1} / ${items.size}", style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            }
            CircleIconButton(SadoraIcons.Share, contentDescription = t.save) {
                // One save at a time: the button has no disabled look, so a second tap is simply ignored.
                if (saving) return@CircleIconButton
                val item = items.getOrNull(pager.currentPage) ?: return@CircleIconButton
                saving = true
                scope.launch {
                    val done = when (item.kind) {
                        MediaKind.PHOTO -> doctors.mediaPhoto(item.id)?.let { saver.savePhoto(it) } ?: false
                        MediaKind.VIDEO -> doctors.mediaUrl(item.id)?.let { saver.saveVideo(it) } ?: false
                    }
                    saving = false
                    onToast(if (done) t.saved else t.saveFailed)
                }
            }
        }
        if (caption.isNotBlank()) {
            Text(
                caption,
                style = Sadora.type.body,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .navigationBarsPadding()
                    .padding(Spacing.lg),
            )
        }
    }
}

/** The thumbnail at once, the photo itself as soon as it arrives; pinched, it stops the swipe. */
@Composable
private fun ViewerPhoto(item: MessageMedia, doctors: DoctorController, onZoomed: (Boolean) -> Unit) {
    val thumb by produceState<ImageBitmap?>(null, item.id) { value = doctors.mediaThumb(item.id)?.let { decode(it) } }
    val full by produceState<ImageBitmap?>(null, item.id) { value = doctors.mediaPhoto(item.id)?.let { decode(it) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _, zoom, move, _ ->
        scale = (scale * zoom).coerceIn(1f, 4f)
        pan = if (scale == 1f) Offset.Zero else pan + move
        onZoomed(scale > 1f)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shown = full ?: thumb
        if (shown != null) {
            Image(
                shown,
                contentDescription = strings.media.photo,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    // One finger pans only a zoomed photo; otherwise it swipes the album or pulls the viewer shut.
                    .transformable(transform, canPan = { scale > 1f })
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = pan.x
                        translationY = pan.y
                    },
            )
        }
        if (full == null) Spinner()
    }
}

/**
 * A video waits behind its poster until she taps it; only then is a signed address asked
 * for and the file streamed — nothing downloads on its own over mobile data.
 */
@Composable
private fun ViewerVideo(item: MessageMedia, doctors: DoctorController, playing: Boolean, onFailed: () -> Unit) {
    val poster by produceState<ImageBitmap?>(null, item.id) {
        if (item.hasThumbnail) value = doctors.mediaThumb(item.id)?.let { decode(it) }
    }
    var url by remember(item.id) { mutableStateOf<String?>(null) }
    var asked by remember(item.id) { mutableStateOf(false) }
    // Swiped away, the player goes, and its address with it: one lasts minutes.
    LaunchedEffect(playing) {
        if (!playing) {
            url = null
            asked = false
        }
    }
    LaunchedEffect(asked) {
        if (asked && url == null) {
            url = doctors.mediaUrl(item.id)
            if (url == null) {
                asked = false
                onFailed()
            }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val address = url
        if (address != null && playing) {
            VideoPlayer(address, Modifier.fillMaxSize(), onError = onFailed)
        } else {
            poster?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
            val label = strings.media.play
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .semantics { contentDescription = label }
                    .noRippleClickable(role = Role.Button) { asked = true },
                contentAlignment = Alignment.Center,
            ) {
                if (asked) {
                    Spinner()
                } else {
                    Icon(SadoraIcons.Play, contentDescription = null, Modifier.size(36.dp), tint = Color.White)
                }
            }
            Text(
                "${formatDuration(item.durationMs)} · ${strings.media.size(item.sizeBytes)}",
                style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).padding(top = 120.dp),
            )
        }
    }
}

/** A white spinner on the dark viewer, heard as "Yuklanmoqda…". */
@Composable
private fun Spinner() {
    val label = strings.media.loading
    CircularProgressIndicator(
        Modifier.size(32.dp).semantics { contentDescription = label },
        color = Color.White,
        strokeWidth = 2.dp,
    )
}

/** Height over width, held to a range a bubble can draw: no ribbons, no towers. */
private fun aspectOf(width: Int, height: Int): Float =
    if (width <= 0 || height <= 0) 1f else (height.toFloat() / width).coerceIn(0.4f, 1.6f)

private suspend fun decode(bytes: ByteArray): ImageBitmap? =
    withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }

