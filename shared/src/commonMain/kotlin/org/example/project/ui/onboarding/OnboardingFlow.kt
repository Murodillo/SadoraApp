package org.example.project.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import org.example.project.data.AuthDestination
import org.example.project.data.SadoraController
import org.example.project.model.AppState
import uz.sadora.contract.OtpChallenge

/** The ordered onboarding steps. Step numbering in the UI is "n/9". */
enum class OnboardingStep(val indicator: String?) {
    Intro1(null),
    Intro2(null),
    Intro3(null),
    Language("1/9"),
    SignUp("2/9"),
    Otp("3/9"),
    Personal("4/9"),
    Goals("5/9"),
    LifeStagePick("6/9"),
    Permissions("7/9"),
    Privacy("8/9"),
    Ready(null),
}

/**
 * Drives the onboarding sequence.
 *
 * The flow is conditional by design: steps that do not apply to the chosen life
 * stage are skipped rather than shown disabled.
 */
@Composable
fun OnboardingFlow(
    state: AppState,
    controller: SadoraController,
    onFinished: () -> Unit,
    onSignInInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(OnboardingStep.Intro1) }
    val steps = remember { OnboardingStep.entries }
    val scope = rememberCoroutineScope()
    // Carried from the sign-up step to the OTP step.
    var challenge by remember { mutableStateOf<OtpChallenge?>(null) }

    fun advance() {
        val next = steps.getOrNull(step.ordinal + 1)
        if (next == null) onFinished() else step = next
    }

    /**
     * Submits the flow, and only then leaves it.
     *
     * Staying put on failure is deliberate: onboarding is sent as one request, so a
     * user who advances past a failed submit would land in an app whose server has no
     * profile for her.
     */
    fun submitAndFinish() {
        scope.launch {
            if (controller.completeOnboarding()) onFinished()
        }
    }

    fun back() {
        val previous = steps.getOrNull(step.ordinal - 1)
        if (previous != null) step = previous
    }

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
        ) { current ->
            Column(Modifier.fillMaxSize()) {
                when (current) {
                    OnboardingStep.Intro1 -> IntroSlide(
                        index = 0,
                        onNext = ::advance,
                        onSkip = { step = OnboardingStep.Language },
                    )

                    OnboardingStep.Intro2 -> IntroSlide(
                        index = 1,
                        onNext = ::advance,
                        onSkip = { step = OnboardingStep.Language },
                    )

                    OnboardingStep.Intro3 -> IntroSlide(
                        index = 2,
                        onNext = ::advance,
                        onSkip = onSignInInstead,
                    )

                    OnboardingStep.Language -> LanguageStep(
                        state = state,
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.SignUp -> SignUpStep(
                        state = state,
                        controller = controller,
                        step = current.indicator,
                        onBack = ::back,
                        onChallenge = {
                            challenge = it
                            advance()
                        },
                        // Apple, Google and e-mail sign-up skip the OTP screen; where
                        // they land depends on whether the account already has a profile.
                        onAuthenticated = { destination ->
                            if (destination == AuthDestination.Main) {
                                onFinished()
                            } else {
                                step = OnboardingStep.Personal
                            }
                        },
                        onSignInInstead = onSignInInstead,
                    )

                    OnboardingStep.Otp -> OtpStep(
                        state = state,
                        controller = controller,
                        challenge = challenge,
                        step = current.indicator,
                        onBack = ::back,
                        onVerified = { destination ->
                            if (destination == AuthDestination.Main) onFinished() else advance()
                        },
                    )

                    OnboardingStep.Personal -> PersonalStep(
                        state = state,
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Goals -> GoalsStep(
                        state = state,
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.LifeStagePick -> LifeStageStep(
                        state = state,
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Permissions -> PermissionsStep(
                        state = state,
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Privacy -> PrivacyStep(
                        state = state,
                        controller = controller,
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Ready -> ReadyStep(
                        state = state,
                        controller = controller,
                        onEnter = ::submitAndFinish,
                    )
                }
            }
        }
    }
}
