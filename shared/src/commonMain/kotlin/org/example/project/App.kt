package org.example.project

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.example.project.data.HealthController
import org.example.project.data.HealthSync
import org.example.project.data.SadoraController
import org.example.project.data.SadoraGraph
import org.example.project.data.SessionState
import org.example.project.data.applyServerProfile
import org.example.project.design.SadoraDarkSurface
import org.example.project.design.SadoraTheme
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.model.CommunityPost
import org.example.project.nav.AppPhase
import org.example.project.nav.Navigator
import org.example.project.nav.Route
import org.example.project.nav.Tab
import org.example.project.nav.aiRoute
import org.example.project.nav.isFullScreen
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.Motion
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.SadoraBottomNav
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraToast
import org.example.project.ui.components.SystemBackHandler
import org.example.project.ui.core.AiChatScreen
import org.example.project.ui.core.AiFreePreviewScreen
import org.example.project.ui.core.CommentsSheetContent
import org.example.project.ui.core.NutritionScreen
import org.example.project.ui.core.ProfileScreen
import org.example.project.ui.core.SecretChatScreen
import org.example.project.ui.core.TodayScreen
import org.example.project.ui.journey.CycleCalendarScreen
import org.example.project.ui.journey.CycleDayScreen
import org.example.project.ui.journey.JourneyScreen
import org.example.project.ui.journey.PregnancyAppointmentsScreen
import org.example.project.ui.journey.PregnancyCheckInScreen
import org.example.project.ui.journey.StageSleepMoodScreen
import org.example.project.ui.journey.StageSymptomsScreen
import org.example.project.ui.journey.SymptomSheet
import org.example.project.ui.modules.AddMedicationScreen
import org.example.project.ui.modules.ArticleScreen
import org.example.project.ui.modules.BalanceScreen
import org.example.project.ui.modules.DataSourcesScreen
import org.example.project.ui.modules.FoodScanAnalyzingScreen
import org.example.project.ui.modules.FoodScanCameraScreen
import org.example.project.ui.modules.FoodScanScreen
import org.example.project.ui.modules.FoodSearchScreen
import org.example.project.ui.modules.InsightsScreen
import org.example.project.ui.modules.KnowledgeScreen
import org.example.project.ui.modules.MedicationHistoryScreen
import org.example.project.ui.modules.MedicationsScreen
import org.example.project.ui.modules.MindJournalScreen
import org.example.project.ui.modules.MindScreen
import org.example.project.ui.modules.PaywallScreen
import org.example.project.ui.modules.SleepScreen
import org.example.project.ui.onboarding.LegalDocument
import org.example.project.ui.onboarding.LegalScreen
import org.example.project.ui.onboarding.OnboardingFlow
import org.example.project.ui.onboarding.SignInScreen
import org.example.project.ui.onboarding.SplashScreen
import org.example.project.ui.settings.SettingsDetailScreen

/**
 * SADORA — root composable.
 *
 * Owns the single [AppState] store and the [Navigator], and switches between the
 * pre-login phases and the five-tab shell.
 */
@Composable
@Preview
fun App(graph: SadoraGraph? = null) {
    val state = remember { AppState() }
    val navigator = remember { Navigator() }
    // One controller for the whole app; with no graph it runs everything locally.
    val controller = remember(graph, state) { SadoraController(graph?.repository, state) }

    // The health tabs get their own controller; it mirrors what it loads onto [state],
    // so the screens keep reading the store they already read.
    val health = remember(graph, state) {
        graph?.healthController(state) ?: HealthController(null, null, null, null)
    }

    SadoraTheme(darkTheme = state.darkTheme) {
        AnimatedContent(
            targetState = navigator.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
        ) { phase ->
            when (phase) {
                AppPhase.Splash -> SplashGate(
                    graph = graph,
                    state = state,
                    onResolved = navigator::goTo,
                )

                AppPhase.Onboarding -> OnboardingFlow(
                    state = state,
                    controller = controller,
                    onFinished = { navigator.goTo(AppPhase.Main) },
                    onSignInInstead = { navigator.goTo(AppPhase.SignIn) },
                )

                AppPhase.SignIn -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    SignInScreen(
                        state = state,
                        controller = controller,
                        onSignedIn = { navigator.goTo(it) },
                        onRegisterInstead = { navigator.goTo(AppPhase.Onboarding) },
                    )
                }

                AppPhase.Main -> MainShell(state, navigator, controller, health)
            }
        }
    }
}

/**
 * The splash, holding until two things are true: the animation has had its moment,
 * and the stored session has been resolved.
 *
 * Waiting for both is what stops the app flashing the sign-in screen at a user who is
 * already signed in — resolving a session takes a network round trip, and routing on the
 * animation alone would show sign-in and then yank it away.
 *
 * With no graph — previews and the `@Preview` entry point — it behaves as before and
 * goes straight to onboarding.
 */
@Composable
private fun SplashGate(
    graph: SadoraGraph?,
    state: AppState,
    onResolved: (AppPhase) -> Unit,
) {
    var animationDone by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<SessionState>(SessionState.Unknown) }

    LaunchedEffect(graph) {
        session = graph?.repository?.resume() ?: SessionState.SignedOut
    }

    LaunchedEffect(animationDone, session) {
        val resolved = session
        if (!animationDone || resolved is SessionState.Unknown) return@LaunchedEffect
        if (resolved is SessionState.SignedIn) {
            state.applyServerProfile(resolved.user, resolved.entitlements)
            // Onboarding is a gate, not a screen: a half-registered account goes back
            // into the flow rather than into an app with no profile behind it.
            onResolved(if (resolved.needsOnboarding) AppPhase.Onboarding else AppPhase.Main)
        } else {
            onResolved(AppPhase.Onboarding)
        }
    }

    SplashScreen(onReady = { animationDone = true })
}

/**
 * The tab shell: content, bottom navigation, and the overlays (water sheet, toast)
 * that can be raised from any tab.
 */
@Composable
private fun MainShell(
    state: AppState,
    navigator: Navigator,
    controller: SadoraController,
    health: HealthController,
) {
    val scope = rememberCoroutineScope()

    // One load on entering the shell. Failures are silent — a tab that could not reach
    // the server shows its empty state rather than a banner over the whole app.
    LaunchedEffect(health) {
        // Every screen already edits the store; the sink is what carries those edits on
        // to the server, so none of them had to learn about it.
        state.sync = HealthSync(health, scope)
        // Anything the onboarding calendar collected goes up before the first read, so
        // Today opens on a prediction built from her own cycles rather than on the
        // baseline's assumed one.
        health.flushOnboardingPeriods()
        health.loadAll()
    }

    var showWaterSheet by remember { mutableStateOf(false) }
    // Owned here rather than by the chat screen so the sheet covers the tab bar.
    var commentsFor by remember { mutableStateOf<CommunityPost?>(null) }
    var showSymptomSheet by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastWaterAdded by remember { mutableStateOf(0) }

    // The system back button closes an open sheet, then pops the pushed screen, then
    // returns to Today; only from Today with nothing open does it leave the app.
    SystemBackHandler(
        enabled = showWaterSheet || showSymptomSheet || commentsFor != null ||
            navigator.canGoBack || navigator.tab != Tab.Today,
    ) {
        when {
            showWaterSheet -> showWaterSheet = false
            showSymptomSheet -> showSymptomSheet = false
            commentsFor != null -> commentsFor = null
            navigator.canGoBack -> navigator.pop()
            else -> navigator.select(Tab.Today)
        }
    }

    fun addWater(ml: Int) {
        state.addWater(ml)
        lastWaterAdded = ml
        toast = "$ml ml qo'shildi"
        showWaterSheet = false
    }

    val route = navigator.current
    val fullScreen = route?.isFullScreen == true

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                // A pushed screen slides in over the tab it was opened from and slides
                // back out when it is popped, so the stack reads as having a direction.
                // Switching tabs is a cross-fade instead: tabs are siblings, and sliding
                // between them would imply one sits behind another.
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val opening = targetState != null
                        val slide = tween<IntOffset>(Motion.Standard, easing = Motion.Emphasized)
                        if (opening) {
                            (slideInHorizontally(slide) { it / 4 } + fadeIn(tween(Motion.Standard)))
                                .togetherWith(fadeOut(tween(Motion.Quick)))
                        } else {
                            fadeIn(tween(Motion.Standard)).togetherWith(
                                slideOutHorizontally(slide) { it / 4 } + fadeOut(tween(Motion.Standard)),
                            )
                        } using SizeTransform(clip = false)
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "route",
                ) { pushed ->
                    if (pushed != null) {
                        PushedScreen(
                            pushed,
                            state,
                            navigator,
                            controller,
                            onSymptomSheet = { showSymptomSheet = true },
                            onOpenComments = { commentsFor = it },
                        )
                    } else {
                        AnimatedContent(
                            targetState = navigator.tab,
                            transitionSpec = {
                                (fadeIn(tween(Motion.Standard)) + scaleIn(tween(Motion.Standard, easing = Motion.Emphasized), initialScale = 0.97f))
                                    .togetherWith(fadeOut(tween(Motion.Quick))) using SizeTransform(clip = false)
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "tab",
                        ) { tab ->
                            RootTab(
                                tab,
                                state,
                                navigator,
                                controller,
                                onAddWater = { showWaterSheet = true },
                            )
                        }
                    }
                }
            }

            // The tab bar stays put while a module screen is open on top of a tab, and
            // steps aside only for the screens that take the whole display.
            if (!fullScreen) {
                SadoraBottomNav(
                    selected = navigator.tab,
                    onSelect = navigator::select,
                    journeyLabel = state.lifeStage.tabLabel,
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        ) {
            SadoraToast(
                message = toast,
                actionText = "Qaytarish",
                onAction = {
                    state.addWater(-lastWaterAdded)
                    toast = null
                },
                onTimeout = { toast = null },
            )
        }

        SymptomSheet(
            visible = showSymptomSheet,
            state = state,
            onDismiss = { showSymptomSheet = false },
        )

        // Kept mounted through the exit animation so the sheet does not blank as it closes.
        val lastComments = remember { mutableStateOf<CommunityPost?>(null) }
        commentsFor?.let { lastComments.value = it }
        SadoraBottomSheet(
            visible = commentsFor != null,
            title = "Izohlar",
            onDismiss = { commentsFor = null },
        ) {
            lastComments.value?.let { CommentsSheetContent(state, it) }
        }

        SadoraBottomSheet(
            visible = showWaterSheet,
            title = "Suv qo'shish",
            onDismiss = { showWaterSheet = false },
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                listOf(150, 250, 500).forEach { ml ->
                    PillButton(
                        "+$ml",
                        { addWater(ml) },
                        tone = ButtonTone.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun RootTab(
    tab: Tab,
    state: AppState,
    navigator: Navigator,
    controller: SadoraController,
    onAddWater: () -> Unit,
) {
    when (tab) {
        Tab.Today -> TodayScreen(
            state = state,
            onOpen = navigator::push,
            onSelectTab = navigator::select,
            onAddWater = onAddWater,
        )

        Tab.Mind -> MindScreen(
            state = state,
            onClose = null,
            onOpenAi = { navigator.push(state.aiRoute()) },
            onOpenJournal = { navigator.push(Route.MindJournal) },
        )

        Tab.Journey -> JourneyScreen(state = state, onOpen = navigator::push)

        Tab.Nutrition -> NutritionScreen(
            state = state,
            onOpen = navigator::push,
            onAddWater = onAddWater,
        )

        Tab.Profile -> ProfileScreen(
            state = state,
            controller = controller,
            onOpen = navigator::push,
            onSignedOut = { navigator.goTo(AppPhase.SignIn) },
        )
    }
}

@Composable
private fun PushedScreen(
    route: Route,
    state: AppState,
    navigator: Navigator,
    controller: SadoraController,
    onSymptomSheet: () -> Unit,
    onOpenComments: (CommunityPost) -> Unit,
) {
    val close = navigator::pop
    val upgrade = { navigator.push(Route.Paywall) }

    when (route) {
        // Cycle
        Route.CycleCalendar -> CycleCalendarScreen(state, navigator::push, close)
        is Route.CycleDay -> CycleDayScreen(state, route.date, onSymptomSheet, close)

        // Pregnancy
        Route.PregnancyAppointments -> PregnancyAppointmentsScreen(close)
        Route.PregnancyCheckIn -> PregnancyCheckInScreen(state, close)

        // Stage detail
        Route.StageSymptoms -> StageSymptomsScreen(state, close)
        Route.StageSleepMood -> StageSleepMoodScreen(state, close)

        // AI — the chat is drawn on the deck's navy whatever the app theme is.
        Route.AiChat -> SadoraDarkSurface { AiChatScreen(state, close) }
        Route.AiPreview -> AiFreePreviewScreen(onUpgrade = upgrade, onDismiss = close)

        // Nutrition: camera -> analysing -> result is one linear flow, so each step
        // replaces the last rather than stacking on it.
        Route.FoodSearch -> FoodSearchScreen(state, close)
        Route.FoodScanCamera -> FoodScanCameraScreen(
            state = state,
            onCapture = { navigator.replaceTop(Route.FoodScanAnalyzing) },
            onManualEntry = { navigator.replaceTop(Route.FoodSearch) },
            onClose = close,
        )
        Route.FoodScanAnalyzing -> FoodScanAnalyzingScreen(
            onDone = { navigator.replaceTop(Route.FoodScan) },
            onCancel = close,
        )
        Route.FoodScan -> FoodScanScreen(state, close)
        Route.Balance -> BalanceScreen(state, close)

        Route.MindJournal -> MindJournalScreen(close)
        Route.Medications -> MedicationsScreen(state, close, navigator::push)
        Route.AddMedication -> AddMedicationScreen(state, close)
        Route.MedicationHistory -> MedicationHistoryScreen(close)
        Route.Sleep -> SleepScreen(state, close)
        Route.Insights -> InsightsScreen(state, close, upgrade)
        Route.Knowledge -> KnowledgeScreen(state, close, navigator::push)
        is Route.Article -> ArticleScreen(route.title, close)
        Route.DataSources -> DataSourcesScreen(close)
        Route.SecretChat -> SecretChatScreen(state, onOpenComments = onOpenComments, onClose = close)

        // The same documents onboarding shows, reachable again from settings.
        Route.Terms -> LegalScreen(LegalDocument.Terms, close)
        Route.PrivacyPolicy -> LegalScreen(LegalDocument.Privacy, close)
        Route.Paywall -> PaywallScreen(state, controller, close)

        // Settings detail screens reuse the existing surfaces they configure.
        Route.PersonalDetails,
        Route.GoalsSettings,
        Route.LifeStageSettings,
        Route.Notifications,
        Route.PrivacySecurity,
        Route.About,
        -> SettingsDetailScreen(
            route = route,
            state = state,
            controller = controller,
            onClose = close,
            onOpen = navigator::push,
            onSignedOut = { navigator.goTo(AppPhase.SignIn) },
        )
    }
}
