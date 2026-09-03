package org.example.project.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
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
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing

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
    cancelText: String = "Bekor",
    destructive: Boolean = true,
) {
    val c = Sadora.colors
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
                        .navigationBarsPadding()
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
    /** Set only when a specific emoji says more than the default outline — "💊". */
    glyph: String? = null,
) {
    val c = Sadora.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (glyph != null) {
            Text(glyph, style = Sadora.type.display, color = c.muted2)
        } else {
            Icon(
                SadoraIcons.Empty,
                contentDescription = null,
                Modifier.size(40.dp),
                tint = c.muted2,
            )
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
                SadoraButton(actionText, onAction, fillWidth = false)
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
        Text("⚠", style = Sadora.type.h3, color = c.danger)
        Text(text, style = Sadora.type.body, color = c.danger, modifier = Modifier.weight(1f))
        if (onRetry != null) {
            Text(
                "Qayta urinish",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.danger,
                modifier = Modifier.noRippleClickable(onClick = onRetry),
            )
        }
    }
}
