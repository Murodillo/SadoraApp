package uz.sadora.doctor.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.contract.UzbekPhone
import uz.sadora.doctor.data.AuthController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.MinTouchTarget
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.AppLanguage
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.LanguageSheet
import uz.sadora.doctor.ui.components.DisclaimerNote
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.OtpInput
import uz.sadora.doctor.ui.components.PhoneMask
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraLogoReveal
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SystemBackHandler
import uz.sadora.doctor.ui.components.noRippleClickable
import uz.sadora.doctor.ui.components.phoneError
import uz.sadora.doctor.ui.components.pressable

/** The line under the wordmark wherever the doctor app shows its logo. */
internal const val DoctorTagline = "DOCTOR"

/**
 * Signing in: the number, then the six-digit code — the client app's sign-in, page for
 * page, against the same accounts. The phone page opens with the logo writing itself;
 * the code page is the client's code question, full progress bar and keypad included;
 * and the two slide into each other the way the client's onboarding pages do.
 *
 * The language can be changed before anything else, since the SMS goes out in it.
 */
@Composable
fun SignInScreen(
    auth: AuthController,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var languageOpen by remember { mutableStateOf(false) }
    val onCodeStep = auth.challenge != null

    // Back on the code page returns to the number, as the chevron does.
    SystemBackHandler(enabled = onCodeStep && !languageOpen, onBack = auth::changeNumber)

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = onCodeStep,
            // Forward pushes the new page in from the right, back reverses it. The fade is
            // offset from the slide so the two pages are never both at full opacity.
            transitionSpec = {
                val direction = if (targetState) 1 else -1
                val slide = tween<IntOffset>(380, easing = FastOutSlowInEasing)
                (slideInHorizontally(slide) { width -> direction * width } + fadeIn(tween(240, delayMillis = 80)))
                    .togetherWith(slideOutHorizontally(slide) { width -> -direction * width } + fadeOut(tween(200)))
            },
            label = "sign-in-step",
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) { codeStep ->
            if (!codeStep) {
                PhoneStep(
                    auth = auth,
                    onLanguage = { languageOpen = true },
                    onSubmit = {
                        focus.clearFocus()
                        scope.launch { auth.requestCode(language.wire) }
                    },
                )
            } else {
                CodeStep(
                    auth = auth,
                    onVerify = { scope.launch { if (auth.verify()) onSignedIn() } },
                    onResend = { scope.launch { auth.requestCode(language.wire) } },
                )
            }
        }

        LanguageSheet(
            visible = languageOpen,
            current = language,
            onSelect = {
                onLanguage(it)
                languageOpen = false
            },
            onDismiss = { languageOpen = false },
        )
    }
}

/** The client's sign-in page: the logo writing itself, the headline, the number. */
@Composable
private fun PhoneStep(auth: AuthController, onLanguage: () -> Unit, onSubmit: () -> Unit) {
    val c = Sadora.colors
    val t = strings.auth
    val focus = LocalFocusManager.current
    val error = auth.error?.readable()

    Column(
        Modifier
            .fillMaxSize()
            // The content lifts above the keyboard, and a tap on the empty space around
            // the field dismisses it.
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
            Spacer(Modifier.weight(1f))
            PillButton(strings.languageName, onClick = onLanguage)
        }
        // Signing in opens with the logo writing itself, the same reveal the splash plays:
        // whoever is coming back sees the app introduce itself again.
        SadoraLogoReveal(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            size = 104.dp,
            taglineText = DoctorTagline,
        )
        Column(
            Modifier.align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(t.title, style = Sadora.type.h1, color = c.text, textAlign = TextAlign.Center)
            Text(t.subtitle, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
        }

        SadoraTextField(
            value = auth.phone,
            // The field holds nine digits at most; the mask is drawn, not stored.
            onValueChange = auth::updatePhone,
            label = t.phoneLabel,
            leading = "+998",
            placeholder = "90 123 45 67",
            error = phoneError(auth.phone),
            visualTransformation = PhoneMask,
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done,
            keyboardActions = KeyboardActions(onDone = { if (auth.phoneReady && !auth.busy) onSubmit() else focus.clearFocus() }),
        )

        error?.let { ErrorStrip(it) }

        SadoraButton(
            if (auth.busy) t.sending else t.sendCode,
            onClick = onSubmit,
            enabled = auth.phoneReady && !auth.busy,
        )
        DisclaimerNote(t.phoneNote, icon = "🔒")
        Spacer(Modifier.height(Spacing.xl))
    }
}

/**
 * The client's code question, with the progress bar already full: the boxes, the keypad
 * in the page rather than the system keyboard, and the button that slides up once six
 * digits are in.
 */
@Composable
private fun CodeStep(auth: AuthController, onVerify: () -> Unit, onResend: () -> Unit) {
    val c = Sadora.colors
    val t = strings.auth
    val challenge = auth.challenge ?: return
    val error = auth.error?.readable()
    val entry = rememberPageEntry()

    // A new challenge — the first one or a resend — starts its own countdown.
    var secondsLeft by remember(challenge) { mutableStateOf(challenge.resendAfterSeconds) }
    LaunchedEffect(challenge, secondsLeft) {
        if (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    QuestionScaffold(
        title = t.codeTitle,
        subtitle = t.codeSubtitle(UzbekPhone.format(auth.phone)),
        progress = 1f,
        onBack = auth::changeNumber,
        entry = entry,
        // The keypad needs the height; on a tall phone the brand pushed its last row
        // under the confirm button.
        brand = false,
        footer = {
            AnswerFooter(visible = auth.codeReady) {
                SadoraButton(
                    if (auth.busy) t.checking else t.confirm,
                    onVerify,
                    enabled = !auth.busy,
                )
            }
            Text(
                if (secondsLeft > 0) t.resendIn(secondsLeft) else t.resend,
                style = Sadora.type.body,
                color = if (secondsLeft > 0) c.muted2 else c.textAccent,
                modifier = Modifier
                    .defaultMinSize(minHeight = MinTouchTarget)
                    .noRippleClickable(enabled = secondsLeft == 0 && !auth.busy, role = Role.Button, onClick = onResend)
                    .padding(top = Spacing.sm),
            )
        },
    ) {
        Reveal(entry.value, from = 0.24f) {
            OtpInput(code = auth.code, length = AuthController.CODE_LENGTH, isError = error != null)
        }
        if (challenge.devCode != null) {
            Reveal(entry.value, from = 0.28f) {
                Text(t.devCodeFilled, style = Sadora.type.body, color = c.muted2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
        if (error != null) {
            Reveal(entry.value, from = 0.32f) { ErrorStrip(error) }
        }
        Reveal(entry.value, from = 0.36f) {
            DisclaimerNote(t.codeSecrecy, icon = "🔒")
        }
        Reveal(entry.value, from = 0.44f) {
            NumberPad(
                onDigit = { auth.updateCode(auth.code + it) },
                onDelete = { auth.updateCode(auth.code.dropLast(1)) },
            )
        }
    }
}

/**
 * The code's own keypad, as in the client app: part of the page rather than the system
 * keyboard, which keeps the boxes and the digits on screen together on a short phone.
 */
@Composable
private fun NumberPad(onDigit: (String) -> Unit, onDelete: () -> Unit) {
    val c = Sadora.colors
    val t = strings.auth
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
                            // A floor, not a fixed height, so a large system font grows the key.
                            .heightIn(min = 52.dp)
                            .clip(Radius.field)
                            .background(if (key.isEmpty()) Color.Transparent else c.surface)
                            // The key dips under the finger, like every other press in the app.
                            .pressable(enabled = key.isNotEmpty(), pressedScale = 0.94f, role = Role.Button) {
                                if (key == "⌫") onDelete() else onDigit(key)
                            }
                            .then(
                                if (key == "⌫") Modifier.clearAndSetSemantics { contentDescription = t.deleteDigit } else Modifier,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, style = Sadora.type.h2, color = c.text)
                    }
                }
            }
        }
    }
}
