package uz.sadora.doctor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.ui.auth.DoctorTagline
import uz.sadora.doctor.ui.components.BloomField
import uz.sadora.doctor.ui.components.LogoRevealMillis
import uz.sadora.doctor.ui.components.SadoraLoader
import uz.sadora.doctor.ui.components.SadoraLogoReveal

/** The deck's splash ground: a lavender wash, #F7E7FF at the top to #EDE9FF below. */
private val SplashWash = listOf(Color(0xFFF7E7FF), Color(0xFFEDE9FF))

/**
 * The client app's splash: the mark, the wordmark and the line under it on the lavender
 * wash, with the bloom field gathering at the foot of the screen — here the line says
 * DOCTOR.
 *
 * [onReady] fires once the reveal has played out; the caller holds here until the stored
 * session has resolved too, so a signed-in doctor never sees the sign-in page flash past.
 */
@Composable
fun SplashScreen(onReady: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sadora.colors

    // Hold until the reveal has finished rather than for a round number: the app should
    // never cut its own logo off mid-word.
    LaunchedEffect(Unit) {
        delay(LogoRevealMillis + 350L)
        onReady()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(if (c.isDark) Brush.verticalGradient(listOf(c.bg, c.surface)) else Brush.verticalGradient(SplashWash)),
    ) {
        BloomField(Modifier.fillMaxSize(), fieldAlpha = 0.85f)

        Column(
            Modifier.fillMaxSize().padding(horizontal = Spacing.xl),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SadoraLogoReveal(size = 148.dp, taglineText = DoctorTagline)

            // A slow session would otherwise leave the finished logo sitting still. The
            // loader arrives a beat after the hand-off would normally have happened, so it
            // is seen only when the app really is waiting.
            var waiting by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(LogoRevealMillis + 700L)
                waiting = true
            }
            Spacer(Modifier.height(Spacing.lg))
            AnimatedVisibility(waiting, enter = fadeIn(tween(400))) {
                SadoraLoader(size = 40.dp)
            }
        }
    }
}
