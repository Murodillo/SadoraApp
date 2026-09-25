package uz.sadora.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.app.data.HealthController
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CameraAccess
import uz.sadora.app.ui.components.CameraShutter
import uz.sadora.app.ui.components.CapturedPhoto
import uz.sadora.app.ui.components.EmptyState
import uz.sadora.app.ui.components.LiveCamera
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.pressable
import uz.sadora.app.ui.components.rememberOpenAppSettings
import uz.sadora.app.ui.components.rememberPhotoCapture
import uz.sadora.contract.FoodScanResult

/**
 * "Ovqat skaneri" — a live viewfinder, the wait, and the result, in one screen.
 *
 * It is one screen because it is one action: the three routes it replaced were stitched
 * together by a timer, which is why the middle one could show a ticking list of steps
 * that had not happened and the last one could show a dish nobody had photographed.
 * The photo goes to the model, and what comes back is what is shown — including
 * "that is not food", which is a real answer a camera has to be able to give.
 *
 * Scanning is never the only way in: manual entry sits in the same row as the shutter,
 * and it is what every failure offers.
 */
@Composable
fun FoodScannerScreen(
    state: AppState,
    health: HealthController,
    onManualEntry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf<ScanStage>(ScanStage.Framing) }
    // Both roads — the page's own camera and the gallery — join here, before the upload.
    // Which photo the page is waiting on. Cancelling moves it on, so an answer to a
    // photo she gave up on cannot land over the next one she takes.
    var attempt by remember { mutableIntStateOf(0) }
    val read: (CapturedPhoto) -> Unit = { photo ->
        val mine = ++attempt
        stage = ScanStage.Reading
        scope.launch {
            val result = health.scanFood(photo.base64, photo.mimeType)
            if (mine != attempt) return@launch
            stage = when {
                result == null -> ScanStage.Failed
                !result.isFood -> ScanStage.NotFood(result.message)
                else -> ScanStage.Done(result)
            }
        }
    }
    val picker = rememberPhotoCapture(read)

    when (val current = stage) {
        ScanStage.Framing -> Viewfinder(
            onCaptured = read,
            gallery = picker::pickFromGallery,
            onManualEntry = onManualEntry,
            onClose = onClose,
            modifier = modifier,
        )

        ScanStage.Reading -> ReadingPhoto(
            onCancel = {
                attempt++
                stage = ScanStage.Framing
            },
            modifier = modifier,
        )

        ScanStage.Failed -> ScanProblem(
            title = t.scanFailed,
            body = t.scanFailedBody,
            onRetry = { stage = ScanStage.Framing },
            onManualEntry = onManualEntry,
            onClose = onClose,
            modifier = modifier,
        )

        is ScanStage.NotFood -> ScanProblem(
            title = t.notFood,
            body = current.message ?: t.scanFailedBody,
            onRetry = { stage = ScanStage.Framing },
            onManualEntry = onManualEntry,
            onClose = onClose,
            modifier = modifier,
        )

        is ScanStage.Done -> FoodScanScreen(
            scan = current.result,
            state = state,
            onClose = onClose,
            modifier = modifier,
        )
    }
}

/** Where the flow is. The result is carried rather than fetched again. */
private sealed interface ScanStage {
    data object Framing : ScanStage
    data object Reading : ScanStage
    data object Failed : ScanStage
    data class NotFood(val message: String?) : ScanStage
    data class Done(val result: FoodScanResult) : ScanStage
}

@Composable
private fun Viewfinder(
    onCaptured: (CapturedPhoto) -> Unit,
    gallery: () -> Unit,
    onManualEntry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.modules
    val shutter = remember { CameraShutter() }
    var access by remember { mutableStateOf(CameraAccess.Starting) }
    val live = access == CameraAccess.Live

    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar(t.scannerTitle, onBack = onClose, centered = true)

        Column(
            Modifier.weight(1f).padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                t.scannerFrameHint,
                style = Sadora.type.h2,
                color = c.text,
                textAlign = TextAlign.Center,
            )

            // The camera itself, in the page. Black in both themes: it is a viewfinder,
            // and the frame before the first image should look like one.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(Radius.card)
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                LiveCamera(
                    shutter = shutter,
                    onAccess = { access = it },
                    onCaptured = onCaptured,
                    modifier = Modifier.fillMaxSize(),
                )
                when (access) {
                    CameraAccess.Live -> Box(
                        Modifier
                            .fillMaxWidth(0.78f)
                            .aspectRatio(1f)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), Radius.card),
                    )

                    CameraAccess.Starting -> SadoraLoader(size = 36.dp)

                    CameraAccess.Denied -> CameraNotice(
                        title = t.cameraDenied,
                        body = t.cameraDeniedBody,
                        actionText = t.cameraOpenSettings,
                        onAction = rememberOpenAppSettings(),
                    )

                    CameraAccess.Missing -> CameraNotice(title = null, body = t.cameraMissing)
                }
            }

            Text(t.scannerLightHint, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
        }

        Column(
            Modifier.padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CaptureSideAction(SadoraIcons.Document, t.scannerGallery, onClick = gallery)

                // Dimmed until frames arrive: a shutter that looks ready and does nothing
                // is the button this page used to have.
                Box(
                    Modifier
                        .size(76.dp)
                        .graphicsLayer { alpha = if (live) 1f else 0.4f }
                        .clip(Radius.chip)
                        .background(c.heroGradient)
                        .pressable(enabled = live, pressedScale = 0.92f, role = Role.Button, onClick = shutter::fire),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        SadoraIcons.Camera,
                        contentDescription = t.scannerShutter,
                        Modifier.size(28.dp),
                        tint = c.onPrimary,
                    )
                }

                CaptureSideAction(SadoraIcons.Pencil, t.scannerManual, onClick = onManualEntry)
            }
        }
    }
}

/** What the viewfinder says when there is no picture to show in it. */
@Composable
private fun CameraNotice(
    title: String?,
    body: String,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (title != null) {
            Text(title, style = Sadora.type.h3, color = Color.White, textAlign = TextAlign.Center)
        }
        Text(body, style = Sadora.type.body, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        if (actionText != null) {
            SadoraButton(actionText, onAction, tone = ButtonTone.Secondary)
        }
    }
}

@Composable
private fun CaptureSideAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = Sadora.colors
    Column(
        Modifier.noRippleClickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(Radius.chip)
                .background(c.surface2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, Modifier.size(IconSize.md), tint = c.text)
        }
        Text(label, style = Sadora.type.body, color = c.muted)
    }
}

/**
 * The wait.
 *
 * It says what is happening and how long it usually takes, and nothing else: the
 * previous version ticked off "Rasm sifati tekshirildi" and "Taom aniqlandi" on a
 * 900-millisecond timer, which described a pipeline that did not exist.
 */
@Composable
private fun ReadingPhoto(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val t = strings.modules

    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar("", onBack = onCancel)

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SadoraLoader(size = 44.dp)
            Text(t.analysing, style = Sadora.type.h3, color = c.text)
            Text(t.analysingWait, style = Sadora.type.body, color = c.muted)
        }

        Box(Modifier.padding(Spacing.screen)) {
            SadoraButton(strings.common.cancel, onCancel, tone = ButtonTone.Ghost)
        }
    }
}

/** A failure, or a photograph of something that is not food. Both offer the same ways on. */
@Composable
private fun ScanProblem(
    title: String,
    body: String,
    onRetry: () -> Unit,
    onManualEntry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    Column(modifier.fillMaxSize().navigationBarsPadding()) {
        SadoraTopBar(t.scannerTitle, onBack = onClose, centered = true)

        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
        ) {
            EmptyState(
                title = title,
                body = body,
                actionText = strings.common.retry,
                onAction = onRetry,
                glyph = "📷",
            )
            SadoraButton(t.scannerManual, onManualEntry, tone = ButtonTone.Secondary)
        }
    }
}
