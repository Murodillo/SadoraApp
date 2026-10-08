package uz.sadora.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.semantics.Role
import uz.sadora.app.design.MinTouchTarget
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.resources.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import uz.sadora.contract.PetPose

/**
 * Centre modal — destructive confirmations such as "Hisobni o'chirish?".
 *
 * The confirm action is [ButtonTone.Destructive]; cancel is always the calmer
 * secondary so the dangerous option is never the visual default.
 */
@Composable
fun SadoraDialog(
    visible: Boolean,
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    cancelText: String = strings.common.cancel,
    destructive: Boolean = true,
) {
    val c = Sadora.colors
    // System back closes the dialog, as it closes a sheet. Without this it popped the
    // screen underneath and took the open confirmation with it.
    SystemBackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(Spacing.xl)
                    .clip(Radius.card)
                    .background(c.surface)
                    // Swallows the tap so it never reaches the scrim behind, which
                    // would dismiss the dialog the user is reading.
                    .noRippleClickable {}
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(title, style = Sadora.type.h2, color = c.text)
                Text(body, style = Sadora.type.body, color = c.muted)
                Row(
                    Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SadoraButton(
                        cancelText,
                        onDismiss,
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraButton(
                        confirmText,
                        onConfirm,
                        tone = if (destructive) ButtonTone.Destructive else ButtonTone.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Bottom sheet — quick logging such as "Suv qo'shish". */
@Composable
fun SadoraBottomSheet(
    visible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    // Back closes the sheet. Without this it reached the shell's handler, which switched
    // to Today — throwing away a breathing session mid-count and whatever was typed.
    SystemBackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .noRippleClickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(Radius.sheet)
                        .background(c.surface)
                        // Same as the dialog: the sheet body must not dismiss itself.
                        .noRippleClickable {}
                        // A sheet with a text field rises above the keyboard and scrolls,
                        // so the button under the field is never left beneath it.
                        .imePadding()
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 40.dp, height = 4.dp)
                            .clip(Radius.chip)
                            .background(c.line),
                    )
                    Text(title, style = Sadora.type.h2, color = c.text)
                    content()
                }
            }
        }
    }
}

enum class ToastTone { Success, Error }

/**
 * Transient confirmation with an optional undo, e.g. "250 ml qo'shildi · Qaytarish".
 */
@Composable
fun SadoraToast(
    message: String?,
    modifier: Modifier = Modifier,
    tone: ToastTone = ToastTone.Success,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    onTimeout: () -> Unit = {},
) {
    val c = Sadora.colors
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        val text = message ?: return@AnimatedVisibility
        LaunchedEffect(text) {
            delay(2600)
            onTimeout()
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .clip(Radius.cardSmall)
                .background(if (tone == ToastTone.Success) c.surface else c.danger.copy(alpha = 0.15f))
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                if (tone == ToastTone.Success) "✓" else "⚠",
                style = Sadora.type.h3,
                color = if (tone == ToastTone.Success) c.success else c.danger,
            )
            Text(text, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
            if (actionText != null) {
                Text(
                    actionText,
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textAccent,
                    modifier = Modifier.noRippleClickable { onAction?.invoke() },
                )
            }
        }
    }
}

/**
 * Empty state — "Hali ma'lumot yo'q" with the action that fills it.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    actionText: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    /** Set only when a specific emoji says more than the default picture — "💊". */
    glyph: String? = null,
    /** The colour icon that says what is missing; preferred over [glyph]. */
    art: org.jetbrains.compose.resources.DrawableResource? = null,
    /**
     * Something went wrong rather than nothing being there yet: her companion, when she
     * has one, is asleep instead of waiting, and the action wakes it.
     */
    failed: Boolean = false,
    /** Off where the screen is not hers — the partner's view has no pet of its own. */
    withCompanion: Boolean = true,
    /** How her companion waits here when the screen calls for more than standing — asleep on Uyqu. */
    companionPose: PetPose = PetPose.IDLE,
) {
    val c = Sadora.colors
    val companion = LocalCompanion.current?.takeIf { withCompanion }
    var woken by remember { mutableStateOf(false) }
    // Awake for the retry; if it fails again the screen is still here and it dozes off.
    LaunchedEffect(woken) {
        if (woken) {
            kotlinx.coroutines.delay(2_500)
            woken = false
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (companion != null) {
            // The companion stands where the picture was; a specific picture stays, small,
            // at its feet, because it says what is missing.
            Box(contentAlignment = Alignment.BottomEnd) {
                val pose = when {
                    !failed -> companionPose
                    woken -> PetPose.HAPPY
                    else -> PetPose.SLEEP
                }
                PetImage(companion.pet, pose, 112.dp)
                if (art != null) ArtIcon(art, 36.dp)
            }
        } else if (art != null) {
            ArtIcon(art, 64.dp)
        } else if (glyph != null) {
            Text(glyph, style = Sadora.type.display, color = c.muted2)
        } else {
            ArtIcon(Res.drawable.ic3d_empty, 64.dp)
        }
        Text(title, style = Sadora.type.h3, color = c.text)
        Text(
            body,
            style = Sadora.type.body,
            color = c.muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionText != null) {
            Box(Modifier.padding(top = Spacing.xs)) {
                SadoraButton(
                    actionText,
                    onClick = {
                        woken = true
                        onAction()
                    },
                    fillWidth = false,
                )
            }
        }
    }
}

/** Inline error strip — "Saqlanmadi — qayta urinib ko'ring". */
@Composable
fun ErrorStrip(text: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.cardSmall)
            .background(c.danger.copy(alpha = 0.12f))
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // A failure she can retry is usually the network: her companion naps through it.
        val companion = LocalCompanion.current
        if (onRetry != null && companion != null) {
            PetImage(companion.pet, PetPose.SLEEP, 36.dp)
        } else {
            Text("⚠", style = Sadora.type.h3, color = c.danger)
        }
        Text(text, style = Sadora.type.body, color = c.danger, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text(
                strings.common.retry,
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.danger,
                modifier = Modifier
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .noRippleClickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = Spacing.xs),
            )
        }
    }
}
