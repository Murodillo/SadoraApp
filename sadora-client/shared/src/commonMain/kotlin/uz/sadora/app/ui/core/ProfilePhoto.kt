package uz.sadora.app.ui.core

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.app.data.PhotoController
import uz.sadora.app.data.readable
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.ui.components.Avatar
import uz.sadora.app.ui.components.CameraAccess
import uz.sadora.app.ui.components.CameraShutter
import uz.sadora.app.ui.components.CapturedPhoto
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.LiveCamera
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.pressable
import uz.sadora.app.ui.components.rememberOpenAppSettings
import uz.sadora.app.ui.components.rememberPhotoCapture
import uz.sadora.app.resources.*
import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.ArtIcon

// Her own photo: the avatar that changes it, and the sheets it opens. Private by rule —
// it is drawn on her profile and her home header, and a doctor she consults sees it;
// the Chat never does, and every sheet here says so beside the choice.

/**
 * Her avatar on the account card, as a button: the camera mark on its shoulder says it
 * can be changed, and while a photo is going up or coming down it spins.
 */
@Composable
internal fun EditableAvatar(
    state: AppState,
    photos: PhotoController,
    size: Dp,
    onClick: () -> Unit,
    /** The camera badge: changes the photo. Without it the whole avatar does. */
    onCamera: (() -> Unit)? = null,
) {
    val c = Sadora.colors
    val label = if (onCamera != null) strings.frames.title else strings.profile.photoChange
    val photoLabel = strings.profile.photoChange
    Box(Modifier.size(size + 4.dp)) {
        Box(
            Modifier
                .size(size)
                .clip(Radius.chip)
                .noRippleClickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            uz.sadora.app.ui.components.FramedAvatar(state.wornFrame, size = size) { inner ->
                Box(contentAlignment = Alignment.Center) {
                    Avatar(state.name, size = inner, photoUrl = state.avatarUrl)
                    if (photos.saving) {
                        Box(
                            Modifier.size(inner).clip(Radius.chip).background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            SadoraLoader(size = inner * 0.5f)
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .then(
                    if (onCamera != null) {
                        // The badge is small; its hit area is the full touch target around it.
                        Modifier
                            .minimumInteractiveComponentSize()
                            .noRippleClickable(role = Role.Button, onClick = onCamera)
                            .semantics { contentDescription = photoLabel }
                    } else {
                        Modifier
                    },
                )
                .size((size.value * 0.4f).dp)
                .clip(Radius.chip)
                .background(c.surface)
                .padding(2.dp)
                .clip(Radius.chip)
                .background(c.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SadoraIcons.Camera, contentDescription = null, Modifier.size((size.value * 0.22f).dp), tint = c.onPrimary)
        }
    }
}

/**
 * The sheets behind her avatar: the choice (gallery, the front camera, remove), the
 * camera itself, and the picture she chose, round as it will be drawn, with Save.
 *
 * A picture is only ever sent from the last of these, so nothing goes up that she has
 * not looked at first. A picture the server refuses — not an image, too small, too big —
 * stays on screen with the server's reason under it.
 */
@Composable
internal fun ProfilePhotoSheets(
    state: AppState,
    photos: PhotoController,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val t = strings.profile
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<CapturedPhoto?>(null) }
    var camera by remember { mutableStateOf(false) }
    val picker = rememberPhotoCapture { pending = it }

    LaunchedEffect(visible) { if (visible) photos.clearError() }

    SadoraBottomSheet(visible = visible, title = t.photoTitle, onDismiss = onDismiss) {
        PrivacyNote()
        if (picker.available) {
            PhotoOption(Res.drawable.ic3d_gallery, t.photoGallery, t.photoGalleryNote) {
                onDismiss()
                photos.clearError()
                picker.pickFromGallery()
            }
        }
        PhotoOption(Res.drawable.ic3d_camera, t.photoCamera, t.photoCameraNote) {
            onDismiss()
            photos.clearError()
            camera = true
        }
        if (state.avatarUrl != null) {
            PhotoOption(
                Res.drawable.ic3d_profile,
                t.photoRemove,
                if (photos.saving) t.photoRemoving else t.photoRemoveNote,
                enabled = !photos.saving,
            ) {
                scope.launch { if (photos.removeMine()) onDismiss() }
            }
        }
        photos.error?.let { ErrorStrip(it.readable()) }
    }

    SadoraBottomSheet(visible = camera, title = t.photoCamera, onDismiss = { camera = false }) {
        // Composed only while the sheet is up: the camera asks for its permission, and
        // starts, the moment it is drawn.
        if (camera) {
            SelfieCamera(
                onCaptured = {
                    camera = false
                    pending = it
                },
            )
        }
    }

    SadoraBottomSheet(
        visible = pending != null,
        title = t.photoPreviewTitle,
        onDismiss = { if (!photos.saving) pending = null },
    ) {
        pending?.let { photo ->
            PhotoPreview(photo)
            PrivacyNote()
            photos.error?.let { ErrorStrip(it.readable()) }
            SadoraButton(
                if (photos.saving) t.photoSaving else t.photoSave,
                onClick = {
                    scope.launch { if (photos.setMine(photo.base64, photo.mimeType)) pending = null }
                },
                enabled = !photos.saving,
                icon = SadoraIcons.Check,
            )
        }
    }
}

/** "Rasmingizni faqat siz va siz yozgan shifokor ko'radi…", with the lock beside it. */
@Composable
private fun PrivacyNote() {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .padding(Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        ArtIcon(Res.drawable.ic3d_lock, IconSize.lg)
        Text(
            strings.profile.photoPrivacy,
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = c.muted,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PhotoOption(
    art: DrawableResource,
    title: String,
    note: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Sadora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .clip(Radius.cardSmall)
            .background(c.surface2)
            .pressable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ArtTile(art, tint = c.primary, size = 40.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = Sadora.type.h3, color = c.text)
            Text(note, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
        }
    }
}

/**
 * The front camera in a square, which is what the server keeps of any photo: the centre
 * square. Black in both themes, like the scanner's viewfinder.
 */
@Composable
private fun SelfieCamera(onCaptured: (CapturedPhoto) -> Unit) {
    val c = Sadora.colors
    val m = strings.modules
    val shutter = remember { CameraShutter() }
    var access by remember { mutableStateOf(CameraAccess.Starting) }
    val live = access == CameraAccess.Live

    Box(
        Modifier.fillMaxWidth().aspectRatio(1f).clip(Radius.card).background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        LiveCamera(
            shutter = shutter,
            onAccess = { access = it },
            onCaptured = onCaptured,
            modifier = Modifier.fillMaxSize(),
            front = true,
        )
        when (access) {
            // The circle the avatar will be cut from.
            CameraAccess.Live -> Box(
                Modifier.fillMaxSize(0.86f).border(2.dp, Color.White.copy(alpha = 0.85f), Radius.chip),
            )

            CameraAccess.Starting -> SadoraLoader(size = 36.dp)

            CameraAccess.Denied -> Column(
                Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(m.cameraDenied, style = Sadora.type.h3, color = Color.White, textAlign = TextAlign.Center)
                Text(m.cameraDeniedBody, style = Sadora.type.body, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
                SadoraButton(m.cameraOpenSettings, rememberOpenAppSettings(), tone = ButtonTone.Secondary)
            }

            CameraAccess.Missing -> Text(
                m.cameraMissing,
                style = Sadora.type.body,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.lg),
            )
        }
    }
    // Dimmed until frames arrive, as on the scanner: a shutter that looks ready and does
    // nothing is worse than one that plainly is not.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(68.dp)
                .graphicsLayer { alpha = if (live) 1f else 0.4f }
                .clip(Radius.chip)
                .background(c.heroGradient)
                .pressable(enabled = live, pressedScale = 0.92f, role = Role.Button, onClick = shutter::fire),
            contentAlignment = Alignment.Center,
        ) {
            Icon(SadoraIcons.Camera, contentDescription = strings.profile.photoCamera, Modifier.size(26.dp), tint = c.onPrimary)
        }
    }
}

/** The picture she chose, cut round as the avatar will be. */
@Composable
private fun PhotoPreview(photo: CapturedPhoto) {
    val preview by produceState<ImageBitmap?>(null, photo) {
        value = withContext(Dispatchers.Default) { decodeCaptured(photo.base64) }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val bitmap = preview
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(PreviewSize).clip(Radius.chip),
            )
        } else {
            Skeleton(Modifier.size(PreviewSize), shape = Radius.chip)
        }
    }
}

private val PreviewSize = 168.dp

@OptIn(ExperimentalEncodingApi::class, ExperimentalResourceApi::class)
private fun decodeCaptured(base64: String): ImageBitmap? =
    runCatching { Base64.decode(base64).decodeToImageBitmap() }.getOrNull()
