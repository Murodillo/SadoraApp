package uz.sadora.app.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uz.sadora.app.design.Sadora
import uz.sadora.app.i18n.strings
import uz.sadora.app.design.Spacing
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.SystemBackHandler

/** The two documents a user has to be able to read before agreeing to anything. */
enum class LegalDocument { Terms, Privacy }

/**
 * A full legal document, scrollable, with a back affordance.
 *
 * The text lives in the app rather than behind a link on purpose: onboarding asks
 * for consent before an account exists, and a user should never have to leave the
 * flow — or have a network connection — to read what she is agreeing to.
 */
@Composable
fun LegalScreen(
    document: LegalDocument,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val t = strings.settings
    val legal = t.legal
    val title = when (document) {
        LegalDocument.Terms -> legal.termsTitle
        LegalDocument.Privacy -> legal.privacyTitle
    }
    val sections = when (document) {
        LegalDocument.Terms -> legal.terms
        LegalDocument.Privacy -> legal.privacy
    }

    SystemBackHandler(enabled = true, onBack = onClose)

    Column(modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        SadoraTopBar(title, onBack = onClose)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${t.legalEffectiveDate}: ${legal.effectiveDate}",
                    style = Sadora.type.caption,
                    color = c.muted2,
                )
                // Only outside Uzbek: the translation is a courtesy, and which text
                // actually binds her has to be on the same screen as the text.
                legal.translationNotice?.let {
                    Text(it, style = Sadora.type.caption, color = c.muted2)
                }
            }
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.heading, style = Sadora.type.h3, color = c.text)
                    section.body.forEach {
                        Text(it, style = Sadora.type.body, color = c.muted)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * Slides a legal document up over whatever raised it.
 *
 * A sheet rather than a pushed route because the consent screen is not on the
 * navigator's stack — onboarding is a phase — and because coming back to a consent
 * screen with the boxes exactly as they were left is the whole point.
 */
@Composable
fun LegalOverlay(
    document: LegalDocument?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = document != null,
        enter = slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { it },
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        // Held so the document keeps rendering through the exit animation instead of
        // blanking the moment the state clears.
        val shown = remember { mutableStateOf(document) }
        document?.let { shown.value = it }
        shown.value?.let { LegalScreen(it, onClose) }
    }
}
