package uz.sadora.app.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.app.AppControllers
import uz.sadora.app.data.AuthDestination
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.nav.AppPhase
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.onboarding.AnswerFooter
import uz.sadora.app.ui.onboarding.LegalDocument
import uz.sadora.app.ui.onboarding.LegalOverlay
import uz.sadora.app.ui.onboarding.OtpQuestion
import uz.sadora.app.ui.onboarding.PhoneQuestion
import uz.sadora.app.ui.onboarding.QuestionScaffold
import uz.sadora.app.ui.onboarding.Reveal
import uz.sadora.app.ui.onboarding.rememberPageEntry
import uz.sadora.contract.OtpChallenge

private enum class JoinStep { Code, Phone, Otp }

/**
 * Signing up with a Yaqinim code: the code and a name, the phone, the SMS code — and
 * nothing about anyone's health. The account the server makes from it follows her and
 * tracks nothing of its own.
 *
 * A number that already has an account simply signs in, and the code is taken on that
 * account: a mother who tracks her own menopause can follow her daughter without a
 * second app.
 */
@Composable
fun PartnerJoinFlow(
    state: AppState,
    controllers: AppControllers,
    onJoined: (AppPhase) -> Unit,
    onExit: () -> Unit,
    onSignInInstead: () -> Unit,
) {
    val account = controllers.account
    val partner = controllers.partner
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(JoinStep.Code) }
    var code by remember { mutableStateOf(formatCode(state.pendingPartnerCode.orEmpty())) }
    var name by remember { mutableStateOf("") }
    var challenge by remember { mutableStateOf<OtpChallenge?>(null) }
    var otp by remember(challenge) { mutableStateOf(challenge?.devCode.orEmpty()) }
    var secondsLeft by remember(challenge) { mutableStateOf(challenge?.resendAfterSeconds ?: 42) }
    var legal by remember { mutableStateOf<LegalDocument?>(null) }
    // Where the account belongs once the code is taken.
    var destination by remember { mutableStateOf<AuthDestination?>(null) }

    LaunchedEffect(secondsLeft, step) {
        if (step == JoinStep.Otp && secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    /** The code goes to the server once the phone is proven; a refusal returns to the code. */
    fun acceptAndFinish(signedInAs: AuthDestination) {
        destination = signedInAs
        scope.launch {
            val asPartner = signedInAs == AuthDestination.Onboarding || signedInAs == AuthDestination.Partner
            val followed = partner.accept(normaliseCode(code), name.trim(), asPartnerAccount = asPartner)
            if (followed == null) {
                step = JoinStep.Code
                return@launch
            }
            state.pendingPartnerCode = null
            if (signedInAs == AuthDestination.Main) {
                onJoined(AppPhase.Main)
            } else {
                // A fresh account has no profile to read back yet; the two facts the
                // follower app needs are known here.
                state.isPartnerAccount = true
                if (name.isNotBlank()) state.name = name.trim()
                onJoined(AppPhase.Partner)
            }
        }
    }

    fun back() {
        account.clearError()
        partner.clearError()
        when (step) {
            JoinStep.Code -> onExit()
            JoinStep.Phone -> step = JoinStep.Code
            JoinStep.Otp -> step = JoinStep.Phone
        }
    }

    SystemBackHandler(enabled = legal == null) { back() }

    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        when (step) {
            JoinStep.Code -> CodeStep(
                code = code,
                onCode = {
                    code = formatCode(it)
                    partner.clearError()
                },
                name = name,
                onName = { name = it.take(uz.sadora.contract.Limits.PARTNER_NAME_MAX) },
                error = partner.error?.partnerReadable(),
                busy = partner.busy,
                onBack = ::back,
                onOpenLegal = { legal = it },
                onNext = {
                    // A session already proved the number (a code refused after sign-in):
                    // try the corrected code at once rather than sending a second SMS.
                    val signedIn = destination
                    if (signedIn != null && account.isSignedIn) acceptAndFinish(signedIn) else step = JoinStep.Phone
                },
            )

            JoinStep.Phone -> PhoneQuestion(
                state = state,
                busy = account.busy,
                error = account.error?.readable(),
                progress = 0.66f,
                onBack = ::back,
                onSubmit = {
                    scope.launch {
                        account.requestOtp(state.phone)?.let {
                            challenge = it
                            step = JoinStep.Otp
                        }
                    }
                },
                onSignInInstead = onSignInInstead,
            )

            JoinStep.Otp -> OtpQuestion(
                phone = state.phone,
                code = otp,
                onCode = {
                    otp = it
                    account.clearError()
                },
                busy = account.busy || partner.busy,
                error = account.error?.readable(),
                secondsLeft = secondsLeft,
                progress = 1f,
                onBack = ::back,
                onVerify = {
                    val challengeId = challenge?.challengeId ?: return@OtpQuestion
                    scope.launch { account.verifyOtp(challengeId, otp)?.let(::acceptAndFinish) }
                },
                onResend = {
                    scope.launch { account.requestOtp(state.phone)?.let { challenge = it } }
                },
            )
        }

        LegalOverlay(document = legal, onClose = { legal = null })
    }
}

@Composable
private fun CodeStep(
    code: String,
    onCode: (String) -> Unit,
    name: String,
    onName: (String) -> Unit,
    error: String?,
    busy: Boolean,
    onBack: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onNext: () -> Unit,
) {
    val t = strings.partner
    val c = Sadora.colors
    val entry = rememberPageEntry()
    val focus = LocalFocusManager.current
    val ready = normaliseCode(code).length == CodeLength && name.isNotBlank()

    QuestionScaffold(
        title = t.joinTitle,
        subtitle = t.joinSubtitle,
        progress = 0.33f,
        onBack = onBack,
        onSkip = null,
        entry = entry,
        footer = {
            AnswerFooter(visible = true) {
                SadoraButton(strings.onboarding.continueLabel, onNext, enabled = ready && !busy)
            }
        },
    ) {
        Spacer(Modifier.height(Spacing.md))
        Reveal(entry.value, from = 0.28f) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SadoraTextField(
                    value = code,
                    onValueChange = onCode,
                    label = t.codeLabel,
                    placeholder = t.codeHint,
                    leadingIcon = SadoraIcons.Heart,
                )
                SadoraTextField(
                    value = name,
                    onValueChange = onName,
                    label = t.yourNameLabel,
                    placeholder = t.yourNameHint,
                    leadingIcon = SadoraIcons.Profile,
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                )
                Text(t.yourNameNote, style = Sadora.type.body, color = c.muted)
            }
        }
        if (error != null) {
            Reveal(entry.value, from = 0.34f) { ErrorStrip(error) }
        }
        Reveal(entry.value, from = 0.40f) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(t.joinTermsLead, style = Sadora.type.body, color = c.muted)
                Text(
                    t.termsLink,
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textAccent,
                    modifier = Modifier.noRippleClickable { onOpenLegal(LegalDocument.Terms) },
                )
                Text(
                    t.privacyLink,
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textAccent,
                    modifier = Modifier.noRippleClickable { onOpenLegal(LegalDocument.Privacy) },
                )
            }
        }
    }
}
