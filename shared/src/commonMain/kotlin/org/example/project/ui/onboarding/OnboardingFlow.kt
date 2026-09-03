package org.example.project.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.example.project.data.AuthDestination
import org.example.project.data.SadoraController
import org.example.project.model.AppState
import org.example.project.model.LifeStage
import org.example.project.ui.components.SystemBackHandler
import uz.sadora.contract.OtpChallenge

/**
 * The onboarding sequence, in order.
 *
 * Everything from [Name] to [Phone] is a question and carries the progress bar; the
 * screens on either side of that range are gates and pauses, which is why progress
 * reads off [questionSteps] rather than the ordinal.
 */
enum class OnboardingStep {
    /** The consent gate. It comes before everything else. */
    Consent,
    Name,
    BirthYear,
    Focus,
    Stage,
    Referral,
    Reassurance,

    // Cycle block — only for the stages that predict one. The two lengths come before
    // the calendar so it knows how many days one period covers, and can fill them in
    // from a single tap instead of asking for every day.
    CycleLength,
    PeriodLength,
    LastPeriod,
    CycleRegularity,

    // Stage-specific dates.
    DueDate,
    BirthDate,

    // Sensitive block. The notice can skip past both questions behind it.
    SensitiveNotice,
    BirthControl,
    Conception,

    Symptoms,
    Feeling,
    Body,
    Permissions,
    Phone,
    Otp,
    Analysing,
    Ready,
}

/** The steps the progress bar measures — the questions, not the gates and pauses. */
private val questionSteps = listOf(
    OnboardingStep.Name,
    OnboardingStep.BirthYear,
    OnboardingStep.Focus,
    OnboardingStep.Stage,
    OnboardingStep.Referral,
    OnboardingStep.CycleLength,
    OnboardingStep.PeriodLength,
    OnboardingStep.LastPeriod,
    OnboardingStep.CycleRegularity,
    OnboardingStep.DueDate,
    OnboardingStep.BirthDate,
    OnboardingStep.BirthControl,
    OnboardingStep.Conception,
    OnboardingStep.Symptoms,
    OnboardingStep.Feeling,
    OnboardingStep.Body,
    OnboardingStep.Permissions,
    OnboardingStep.Phone,
)

/** Steps the back gesture must not leave, because moving off them would loop or undo. */
private val noWayBack = setOf(OnboardingStep.Analysing, OnboardingStep.Ready)

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
    var step by remember { mutableStateOf(OnboardingStep.Consent) }
    val scope = rememberCoroutineScope()
    // Carried from the phone step to the OTP step.
    var challenge by remember { mutableStateOf<OtpChallenge?>(null) }
    var code by remember(challenge) { mutableStateOf(challenge?.devCode.orEmpty()) }
    var secondsLeft by remember(challenge) { mutableStateOf(challenge?.resendAfterSeconds ?: 42) }
    // The terms or privacy document, raised over the flow while it is being read.
    var legal by remember { mutableStateOf<LegalDocument?>(null) }

    LaunchedEffect(secondsLeft) {
        if (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    val order = remember { OnboardingStep.entries }

    /**
     * True for steps this profile should never be shown.
     *
     * Read by [advance], [back] and the progress bar alike, so a skipped step is
     * invisible to all three rather than being stepped over in one and counted in
     * another.
     */
    fun skipped(candidate: OnboardingStep): Boolean = when (candidate) {
        OnboardingStep.LastPeriod,
        OnboardingStep.CycleLength,
        OnboardingStep.PeriodLength,
        OnboardingStep.CycleRegularity,
        OnboardingStep.SensitiveNotice,
        OnboardingStep.BirthControl,
        -> !state.lifeStage.predictsCycle

        OnboardingStep.Conception -> state.lifeStage != LifeStage.TryingToConceive
        OnboardingStep.DueDate -> state.lifeStage != LifeStage.Pregnancy
        OnboardingStep.BirthDate -> state.lifeStage != LifeStage.Postpartum
        else -> false
    }

    fun advance() {
        var next = order.getOrNull(step.ordinal + 1)
        while (next != null && skipped(next)) next = order.getOrNull(next.ordinal + 1)
        if (next == null) onFinished() else step = next
    }

    /** Jumps past a run of steps, honouring the same skip rules as [advance]. */
    fun jumpTo(target: OnboardingStep) {
        var candidate: OnboardingStep? = target
        while (candidate != null && skipped(candidate)) {
            candidate = order.getOrNull(candidate.ordinal + 1)
        }
        step = candidate ?: return onFinished()
    }

    fun back() {
        var previous = order.getOrNull(step.ordinal - 1)
        while (previous != null && skipped(previous)) {
            previous = order.getOrNull(previous.ordinal - 1)
        }
        if (previous != null) step = previous
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

    /**
     * How far along the progress bar sits.
     *
     * Skipped steps are dropped from the count first, so a profile that never sees
     * the cycle questions still crosses the bar evenly instead of jumping a gap.
     */
    fun progressAt(current: OnboardingStep): Float {
        val live = questionSteps.filterNot { skipped(it) }
        val index = live.indexOf(current)
        return if (index < 0) 1f else (index + 1f) / live.size
    }

    // The system back button steps backwards through the flow rather than leaving it.
    // The legal overlay handles its own back press, so this stands down while it is up.
    SystemBackHandler(
        enabled = legal == null && step.ordinal > 0 && step !in noWayBack,
        onBack = ::back,
    )

    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = step,
            // Forward pushes the new page in from the right, back reverses it. The
            // fade is offset from the slide so the two pages are never both at full
            // opacity, which is what stops the crossover reading as a double image.
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val direction = if (forward) 1 else -1
                val slide = tween<IntOffset>(380, easing = FastOutSlowInEasing)
                (
                    slideInHorizontally(slide) { width -> direction * width } +
                        fadeIn(tween(240, delayMillis = 80))
                    ) togetherWith (
                    slideOutHorizontally(slide) { width -> -direction * width } +
                        fadeOut(tween(200))
                    )
            },
            label = "onboarding-step",
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) { current ->
            Column(Modifier.fillMaxSize()) {
                when (current) {
                    OnboardingStep.Consent -> ConsentGateScreen(
                        state = state,
                        onContinue = ::advance,
                        onOpenLegal = { legal = it },
                    )

                    OnboardingStep.Name -> NameQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.BirthYear -> BirthYearQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.Focus -> FocusQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Stage -> LifeStageQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Referral -> ReferralQuestion(
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onAnswered = ::advance,
                    )

                    OnboardingStep.Reassurance -> ReassuranceScreen(onContinue = ::advance)

                    OnboardingStep.CycleLength -> CycleLengthQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.PeriodLength -> PeriodLengthQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.LastPeriod -> LastPeriodQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.CycleRegularity -> CycleRegularityQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.DueDate -> DueDateQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.BirthDate -> BirthDateQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.SensitiveNotice -> SensitiveNoticeScreen(
                        onContinue = ::advance,
                        // Declining once skips the whole block rather than making her
                        // decline each question inside it.
                        onSkipBlock = { jumpTo(OnboardingStep.Symptoms) },
                    )

                    OnboardingStep.BirthControl -> BirthControlQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.Conception -> ConceptionWindowQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.Symptoms -> SymptomsQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.Feeling -> FeelingQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.Body -> BodyQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSkip = ::advance,
                        onNext = ::advance,
                    )

                    OnboardingStep.Permissions -> PermissionsQuestion(
                        state = state,
                        progress = progressAt(current),
                        onBack = ::back,
                        onNext = ::advance,
                    )

                    OnboardingStep.Phone -> PhoneQuestion(
                        state = state,
                        busy = controller.busy,
                        error = controller.error,
                        progress = progressAt(current),
                        onBack = ::back,
                        onSubmit = {
                            scope.launch {
                                controller.requestOtp(state.phone)?.let {
                                    challenge = it
                                    advance()
                                }
                            }
                        },
                        onSignInInstead = onSignInInstead,
                    )

                    OnboardingStep.Otp -> OtpQuestion(
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
                        onBack = ::back,
                        onVerify = {
                            val challengeId = challenge?.challengeId
                            scope.launch {
                                // With no backend behind the app there is nothing to
                                // verify against, so the flow carries on rather than
                                // dead-ending in a preview.
                                val destination = if (challengeId == null) {
                                    AuthDestination.Onboarding
                                } else {
                                    controller.verifyOtp(challengeId, code)
                                }
                                when (destination) {
                                    null -> Unit
                                    AuthDestination.Main -> onFinished()
                                    else -> advance()
                                }
                            }
                        },
                        onResend = {
                            scope.launch {
                                controller.requestOtp(state.phone)?.let { challenge = it }
                            }
                        },
                    )

                    OnboardingStep.Analysing -> AnalysingScreen(onDone = ::advance)

                    OnboardingStep.Ready -> ReadyStep(
                        state = state,
                        controller = controller,
                        onEnter = ::submitAndFinish,
                    )
                }
            }
        }

        LegalOverlay(document = legal, onClose = { legal = null })
    }
}
