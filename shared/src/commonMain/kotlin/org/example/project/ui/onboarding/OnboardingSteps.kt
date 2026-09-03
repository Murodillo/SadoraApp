package org.example.project.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.data.AuthDestination
import org.example.project.data.SadoraController
import org.example.project.design.Radius
import org.example.project.design.Sadora
import org.example.project.design.SadoraIcons
import org.example.project.design.Spacing
import org.example.project.model.AppLanguage
import org.example.project.model.AppState
import org.example.project.model.Goal
import org.example.project.model.LifeStage
import org.example.project.nav.AppPhase
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.ChipFlowRow
import org.example.project.ui.components.ConsentRow
import org.example.project.ui.components.DisclaimerNote
import org.example.project.ui.components.ImagePlaceholder
import org.example.project.ui.components.OptionRow
import org.example.project.ui.components.OtpInput
import org.example.project.ui.components.SadoraButton
import org.example.project.ui.components.SadoraCheckbox
import org.example.project.ui.components.SadoraTextField
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.SelectChip
import org.example.project.ui.components.TabSwitch
import org.example.project.ui.components.noRippleClickable
import uz.sadora.contract.AuthProvider
import uz.sadora.contract.OtpChallenge

/** Shared layout for a numbered onboarding step: header, body, pinned footer. */
@Composable
private fun StepScaffold(
    title: String,
    subtitle: String,
    step: String?,
    onBack: (() -> Unit)?,
    footer: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = Sadora.colors
    // The activity draws edge to edge, so the status and navigation bars sit over the
    // content unless the scaffold makes room. Without this the footer button lands
    // underneath the navigation bar and onboarding cannot be finished at all on a device
    // with the three-button layout.
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        SadoraTopBar(title = "", onBack = onBack, step = step)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, style = Sadora.type.h1, color = c.text)
                Text(subtitle, style = Sadora.type.body, color = c.muted)
            }
            content()
            Spacer(Modifier.height(Spacing.xs))
        }
        Column(
            Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            footer()
        }
    }
}


/** Numeric keypad for the OTP step. */
@Composable
internal fun NumberPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val c = Sadora.colors
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(Radius.field)
                            .background(if (key.isEmpty()) androidx.compose.ui.graphics.Color.Transparent else c.surface)
                            .noRippleClickable(enabled = key.isNotEmpty()) {
                                if (key == "⌫") onDelete() else onDigit(key)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = Sadora.type.h2, color = c.text)
                    }
                }
            }
        }
    }
}


// ---------------------------------------------------------------- ready

@Composable
fun ReadyStep(state: AppState, controller: SadoraController, onEnter: () -> Unit) {
    val c = Sadora.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(72.dp)
                .clip(Radius.chip)
                .background(c.success.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("✓", style = Sadora.type.display, color = c.success)
        }
        val greeting = state.name.trim()
            .let { if (it.isEmpty()) "Hammasi tayyor" else "Hammasi tayyor, $it" }
        Text(greeting, style = Sadora.type.h1, color = c.text)
        Text(
            "Bugun ekranini siklingiz, maqsadlaringiz va ulangan qurilmalaringiz asosida sozladik.",
            style = Sadora.type.body,
            color = c.muted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SummaryLine("${state.lifeStage.title} yoqildi")
            if (state.healthDataAllowed) SummaryLine("Apple Health ulandi")
            SummaryLine("${state.goals.size} maqsad belgilandi")
        }
        Spacer(Modifier.weight(1f))
        controller.error?.let { org.example.project.ui.components.ErrorStrip(it) }
        SadoraButton(
            if (controller.busy) "Saqlanmoqda…" else "SADORA'ga kirish",
            onEnter,
            enabled = !controller.busy,
        )
        Spacer(Modifier.height(Spacing.md))
    }
}

@Composable
private fun SummaryLine(text: String) {
    val c = Sadora.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text("✓", style = Sadora.type.h3, color = c.success)
        Text(text, style = Sadora.type.h3, color = c.text)
    }
}

// ---------------------------------------------------------------- sign in

@Composable
fun SignInScreen(
    state: AppState,
    controller: SadoraController,
    onSignedIn: (AppPhase) -> Unit,
    onRegisterInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current

    // Signing in is the same phone-and-code exchange as signing up: verifying a code for
    // a number the backend already knows returns that account. There is no password to
    // hold, and nothing for a user who signed up by phone to have forgotten.
    var challenge by remember { mutableStateOf<OtpChallenge?>(null) }
    var awaitingCode by remember { mutableStateOf(false) }
    var code by remember(challenge) { mutableStateOf(challenge?.devCode.orEmpty()) }
    var secondsLeft by remember(challenge) { mutableStateOf(challenge?.resendAfterSeconds ?: 42) }

    LaunchedEffect(secondsLeft, awaitingCode) {
        if (awaitingCode && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    fun finish(destination: AuthDestination) {
        onSignedIn(
            if (destination == AuthDestination.Main) AppPhase.Main else AppPhase.Onboarding,
        )
    }

    fun sendCode() {
        scope.launch {
            controller.requestOtp(state.phone)?.let {
                challenge = it
                awaitingCode = true
            }
        }
    }

    if (awaitingCode) {
        // The same page the sign-up flow uses, with the progress bar already full —
        // one code entry to keep correct rather than two that drift apart.
        OtpQuestion(
            phone = state.phone,
            code = code,
            onCode = {
                code = it
                controller.clearError()
            },
            busy = controller.busy,
            error = controller.error,
            secondsLeft = secondsLeft,
            progress = 1f,
            onBack = {
                controller.clearError()
                awaitingCode = false
            },
            onVerify = {
                val challengeId = challenge?.challengeId
                scope.launch {
                    if (challengeId == null) {
                        finish(AuthDestination.Onboarding)
                    } else {
                        controller.verifyOtp(challengeId, code)?.let(::finish)
                    }
                }
            },
            onResend = ::sendCode,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            // Same two fixes as the onboarding questions: the content lifts above the
            // keyboard, and a tap on the empty space around the field dismisses it.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(Brush.linearGradient(listOf(c.secondary, c.primary))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                SadoraIcons.Sparkle,
                contentDescription = null,
                Modifier.size(28.dp),
                tint = c.onPrimary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Xush kelibsiz", style = Sadora.type.h1, color = c.text)
            Text("Raqamingizga kod yuboramiz", style = Sadora.type.body, color = c.muted)
        }

        SadoraTextField(
            value = state.phone,
            onValueChange = {
                state.phone = it
                controller.clearError()
            },
            label = "Telefon raqami",
            leading = "+998",
            placeholder = "90 123 45 67",
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        )

        controller.error?.let { org.example.project.ui.components.ErrorStrip(it) }

        SadoraButton(
            if (controller.busy) "Yuborilmoqda…" else "Kodni yuborish",
            onClick = ::sendCode,
            // Nine digits is a complete Uzbek number; the server normalises the spacing.
            enabled = state.phone.count { it.isDigit() } >= 9 && !controller.busy,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
            Text("  yoki  ", style = Sadora.type.body, color = c.muted)
            Box(Modifier.weight(1f).height(1.dp).background(c.line))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SadoraButton(
                "Apple",
                tone = ButtonTone.Secondary,
                leading = "",
                enabled = !controller.busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        controller.signInWithSocial(AuthProvider.APPLE, "")?.let(::finish)
                    }
                },
            )
            SadoraButton(
                "Google",
                tone = ButtonTone.Secondary,
                leading = "G",
                enabled = !controller.busy,
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch {
                        controller.signInWithSocial(AuthProvider.GOOGLE, "")?.let(::finish)
                    }
                },
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hisobingiz yo'qmi? ", style = Sadora.type.body, color = c.muted)
            Text(
                "Ro'yxatdan o'tish",
                style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.noRippleClickable(onClick = onRegisterInstead),
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}
