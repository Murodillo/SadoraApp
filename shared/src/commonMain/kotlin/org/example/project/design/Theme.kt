package org.example.project.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** Shorthand accessors so screens read `Sadora.colors.primary`. */
object Sadora {
    val colors: SadoraColors
        @Composable @ReadOnlyComposable get() = LocalSadoraColors.current
    val type: SadoraTypography
        @Composable @ReadOnlyComposable get() = LocalSadoraTypography.current
}

/**
 * Wraps content in the SADORA palette and type scale.
 *
 * Material3 is kept underneath (a few primitives such as ripple and text selection
 * read from it) but every SADORA surface paints from [SadoraColors] directly.
 */
@Composable
fun SadoraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) SadoraDarkColors else SadoraLightColors
    val material = if (darkTheme) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            background = colors.bg,
            surface = colors.surface,
            onSurface = colors.text,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            background = colors.bg,
            surface = colors.surface,
            onSurface = colors.text,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(
        LocalSadoraColors provides colors,
        LocalSadoraTypography provides SadoraTypography(),
        LocalContentColor provides colors.text,
    ) {
        MaterialTheme(colorScheme = material) {
            Box(Modifier.fillMaxSize().background(colors.bg)) { content() }
        }
    }
}
