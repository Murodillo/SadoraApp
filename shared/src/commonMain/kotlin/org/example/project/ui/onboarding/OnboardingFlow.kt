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
import org.example.project.model.AppState

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
    onFinished: () -> Unit,
    onSignInInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(OnboardingStep.Intro1) }
    val steps = remember { OnboardingStep.entries }

    fun advance() {
        val next = steps.getOrNull(step.ordinal + 1)
        if (next == null) onFinished() else step = next
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
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                        onSignInInstead = onSignInInstead,
                    )

                    OnboardingStep.Otp -> OtpStep(
                        state = state,
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
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
                        step = current.indicator,
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Ready -> ReadyStep(
                        state = state,
                        onEnter = onFinished,
                    )
                }
            }
        }
    }
}
