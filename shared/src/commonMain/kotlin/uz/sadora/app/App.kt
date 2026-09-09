package uz.sadora.app

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
import kotlinx.coroutines.launch
import uz.sadora.app.data.AiController
import uz.sadora.app.data.CommunityController
import uz.sadora.app.data.CommunitySyncBridge
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.HealthSync
import uz.sadora.app.data.BillingController
import uz.sadora.app.data.InsightsController
import uz.sadora.app.data.LearnController
import uz.sadora.app.data.RewardsController
import uz.sadora.app.data.SadoraController
import uz.sadora.app.data.SadoraGraph
import uz.sadora.app.data.SessionState
import uz.sadora.app.data.applyServerProfile
import uz.sadora.app.design.SadoraDarkSurface
import uz.sadora.app.design.SadoraTheme
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.ProvideStrings
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.nav.AppPhase
import uz.sadora.app.nav.Navigator
import uz.sadora.app.nav.Route
import uz.sadora.app.nav.Tab
import uz.sadora.app.nav.aiRoute
import uz.sadora.app.nav.isFullScreen
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.Motion
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.SadoraBottomNav
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraToast
import uz.sadora.app.ui.components.StreakCelebration
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.core.AiChatScreen
import uz.sadora.app.ui.core.AiFreePreviewScreen
import uz.sadora.app.ui.core.CommentsSheetContent
import uz.sadora.app.ui.core.ComposePostSheetContent
import uz.sadora.app.ui.core.PostMenuSheetContent
import uz.sadora.app.ui.core.NutritionScreen
import uz.sadora.app.ui.core.ProfileScreen
import uz.sadora.app.ui.core.SecretChatScreen
import uz.sadora.app.ui.core.TodayScreen
import uz.sadora.app.ui.journey.CycleCalendarScreen
import uz.sadora.app.ui.journey.CycleDayScreen
import uz.sadora.app.ui.journey.JourneyScreen
import uz.sadora.app.ui.journey.PregnancyAppointmentsScreen
import uz.sadora.app.ui.journey.PregnancyCheckInScreen
import uz.sadora.app.ui.journey.StageSleepMoodScreen
import uz.sadora.app.ui.journey.StageSymptomsScreen
import uz.sadora.app.ui.journey.SymptomSheet
import uz.sadora.app.ui.modules.AddMedicationScreen
import uz.sadora.app.ui.modules.ArticleScreen
import uz.sadora.app.ui.modules.BalanceScreen
import uz.sadora.app.ui.modules.DataSourcesScreen
import uz.sadora.app.ui.modules.FoodScannerScreen
import uz.sadora.app.ui.modules.FoodSearchScreen
import uz.sadora.app.ui.modules.InsightsScreen
import uz.sadora.app.ui.modules.KnowledgeScreen
import uz.sadora.app.ui.modules.MedicationHistoryScreen
import uz.sadora.app.ui.modules.MedicationsScreen
import uz.sadora.app.ui.modules.MindJournalScreen
import uz.sadora.app.ui.modules.MindScreen
import uz.sadora.app.ui.modules.PaywallScreen
import uz.sadora.app.ui.modules.ReferralScreen
import uz.sadora.app.ui.modules.RewardsScreen
import uz.sadora.app.ui.modules.ShopScreen
import uz.sadora.app.ui.modules.SleepScreen
import uz.sadora.app.ui.onboarding.LegalDocument
import uz.sadora.app.ui.onboarding.LegalScreen
import uz.sadora.app.ui.onboarding.OnboardingFlow
import uz.sadora.app.ui.onboarding.SignInScreen
import uz.sadora.app.ui.onboarding.SplashScreen
import uz.sadora.app.ui.settings.HomeLayoutScreen
import uz.sadora.app.ui.settings.SettingsDetailScreen

/**
 * SADORA — root composable.
 *
 * Owns the single [AppState] store and the [Navigator], and switches between the
 * pre-login phases and the five-tab shell.
 */
@Composable
@Preview
fun App(
    graph: SadoraGraph? = null,
    /**
     * The invite code the app was opened with, from a shared link.
     *
     * Passed in rather than read here because only the platform entry point sees the
     * launch intent. It is only ever a prefill: the code is settled by the server when
     * the account is created, and an existing account ignores it entirely.
     */
    inviteCode: String? = null,
) {
    val state = remember { AppState() }
    val navigator = remember { Navigator() }
    // One controller for the whole app; with no graph it runs everything locally.
    val controller = remember(graph, state) { SadoraController(graph?.repository, state) }

    // The health tabs get their own controller; it mirrors what it loads onto [state],
    // so the screens keep reading the store they already read.
    val health = remember(graph, state) {
        graph?.healthController(state) ?: HealthController(null, null, null, null)
    }
    val community = remember(graph, state) { graph?.communityController(state) ?: CommunityController(null, state) }
    val ai = remember(graph, state) { graph?.aiController(state) ?: AiController(null, state) }
    val insights = remember(graph) { graph?.insightsController() ?: InsightsController(null) }
    val learn = remember(graph) { graph?.learnController() ?: LearnController(null) }
    val billing = remember(graph) { graph?.billingController() ?: BillingController(null) }
    val rewards = remember(graph, state) { graph?.rewardsController(state) ?: RewardsController(null, state) }

    // Only ever fills a blank: a code she has already typed is hers, not the link's.
    LaunchedEffect(inviteCode) {
        if (state.pendingInviteCode.isNullOrBlank()) {
            state.pendingInviteCode = inviteCode?.takeIf { it.isNotBlank() }
        }
    }

    SadoraTheme(darkTheme = state.darkTheme) {
        // One language for the whole tree: a change to it recomposes every screen at
        // once, which is what changing language is.
        ProvideStrings(state.language) {
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

                    AppPhase.Main -> MainShell(state, navigator, controller, health, community, ai, insights, learn, billing, rewards)
                }
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
    community: CommunityController,
    ai: AiController,
    insights: InsightsController,
    learn: LearnController,
    billing: BillingController,
    rewards: RewardsController,
) {
    val scope = rememberCoroutineScope()
    val waterStrings = strings.nutrition
    val communityStrings = strings.community

    // One load on entering the shell. Failures are silent — a tab that could not reach
    // the server shows its empty state rather than a banner over the whole app.
    LaunchedEffect(health) {
        // Every screen already edits the store; the sink is what carries those edits on
        // to the server, so none of them had to learn about it.
        state.sync = HealthSync(health, scope)
        state.communitySync = CommunitySyncBridge(community, scope)
        // The sections the server can close, before any of them is opened.
        controller.refreshFlags()
        // Anything the onboarding calendar collected goes up before the first read, so
        // Today opens on a prediction built from her own cycles rather than on the
        // baseline's assumed one.
        health.flushOnboardingPeriods()
        health.loadAll()
        // "The app just opened." The server decides whether that means anything today,
        // and the layout comes back with it so Today draws her arrangement, not the
        // default, on the first frame after the load.
        rewards.checkIn()
        rewards.loadHomeLayout()

        // A yes to "do you wear a watch?" ends the flow on the connect screen rather
        // than on Today. Consumed here so it happens exactly once, after sign-up — not
        // on every launch of every account that owns one.
        if (state.pendingDeviceConnect) {
            state.pendingDeviceConnect = false
            navigator.push(Route.DataSources)
        }
    }

    var showWaterSheet by remember { mutableStateOf(false) }
    // Owned here rather than by the chat screen so the sheets cover the tab bar.
    var commentsFor by remember { mutableStateOf<CommunityPost?>(null) }
    var menuFor by remember { mutableStateOf<CommunityPost?>(null) }
    var showCompose by remember { mutableStateOf(false) }
    var showSymptomSheet by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastWaterAdded by remember { mutableStateOf(0) }

    // The system back button closes an open sheet, then pops the pushed screen, then
    // returns to Today; only from Today with nothing open does it leave the app.
    SystemBackHandler(
        enabled = showWaterSheet || showSymptomSheet || commentsFor != null || menuFor != null ||
            showCompose || navigator.canGoBack || navigator.tab != Tab.Today,
    ) {
        when {
            showWaterSheet -> showWaterSheet = false
            showSymptomSheet -> showSymptomSheet = false
            commentsFor != null -> commentsFor = null
            menuFor != null -> menuFor = null
            showCompose -> showCompose = false
            navigator.canGoBack -> navigator.pop()
            else -> navigator.select(Tab.Today)
        }
    }

    fun addWater(ml: Int) {
        state.addWater(ml)
        lastWaterAdded = ml
        toast = waterStrings.waterAdded(ml)
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
                            health = health,
                            community = community,
                            ai = ai,
                            insights = insights,
                            learn = learn,
                            billing = billing,
                            rewards = rewards,
                            onSymptomSheet = { showSymptomSheet = true },
                            onOpenComments = { commentsFor = it },
                            onOpenPostMenu = { menuFor = it },
                            onCompose = { showCompose = true },
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
                                insights = insights,
                                health = health,
                                ai = ai,
                                learn = learn,
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
                    journeyLabel = strings.tabs.journey(state.lifeStage),
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
                actionText = waterStrings.undo,
                onAction = {
                    state.addWater(-lastWaterAdded)
                    toast = null
                },
                onTimeout = { toast = null },
            )
        }

        // The once-a-day celebration, above everything including the sheets: it is the
        // first thing that happens on the first open of a day, and it takes itself away.
        StreakCelebration(
            result = rewards.celebration,
            onDismiss = rewards::celebrationShown,
        )

        SymptomSheet(
            visible = showSymptomSheet,
            state = state,
            health = health,
            onDismiss = { showSymptomSheet = false },
        )

        // Kept mounted through the exit animation so the sheet does not blank as it closes.
        val lastComments = remember { mutableStateOf<CommunityPost?>(null) }
        commentsFor?.let { lastComments.value = it }
        // The comments come from the server when the sheet opens, not with the feed.
        LaunchedEffect(commentsFor?.id) { commentsFor?.let { community.loadComments(it.id) } }
        SadoraBottomSheet(
            visible = commentsFor != null,
            title = communityStrings.comments,
            onDismiss = { commentsFor = null },
        ) {
            lastComments.value?.let { post ->
                // Read back from the store so the loaded comments replace the stale copy.
                val current = state.communityPosts.firstOrNull { it.id == post.id } ?: post
                CommentsSheetContent(state, current)
            }
        }

        val lastMenu = remember { mutableStateOf<CommunityPost?>(null) }
        menuFor?.let { lastMenu.value = it }
        SadoraBottomSheet(
            visible = menuFor != null,
            title = if (lastMenu.value?.isMine == true) communityStrings.yourOwnPost else communityStrings.reportPost,
            onDismiss = { menuFor = null },
        ) {
            lastMenu.value?.let { post ->
                PostMenuSheetContent(
                    state = state,
                    post = post,
                    onDone = { message ->
                        menuFor = null
                        message?.let { toast = it }
                    },
                )
            }
        }

        SadoraBottomSheet(
            visible = showCompose,
            title = communityStrings.newPost,
            onDismiss = { showCompose = false },
        ) {
            ComposePostSheetContent(
                state = state,
                onPosted = {
                    showCompose = false
                    toast = communityStrings.postSent
                },
            )
        }

        SadoraBottomSheet(
            visible = showWaterSheet,
            title = waterStrings.addWaterTitle,
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
    insights: InsightsController,
    health: HealthController,
    ai: AiController,
    learn: LearnController,
) {
    when (tab) {
        Tab.Today -> {
            // A new line on every entry to the tab — that is the feature, so it is asked
            // for here rather than once per session.
            LaunchedEffect(Unit) { ai.loadGreeting() }
            TodayScreen(
                state = state,
                onOpen = navigator::push,
                onSelectTab = navigator::select,
                onAddWater = onAddWater,
                greeting = ai.greeting,
                health = health,
                insights = insights,
                learn = learn,
            )
        }

        Tab.Mind -> MindScreen(
            state = state,
            insights = insights,
            onClose = null,
            onOpenAi = { navigator.push(state.aiRoute()) },
            onOpenJournal = { navigator.push(Route.MindJournal) },
        )

        Tab.Journey -> JourneyScreen(state = state, health = health, onOpen = navigator::push)

        Tab.Nutrition -> NutritionScreen(
            state = state,
            onOpen = navigator::push,
            onAddWater = onAddWater,
        )

        Tab.Profile -> ProfileScreen(
            state = state,
            controller = controller,
            health = health,
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
    health: HealthController,
    community: CommunityController,
    ai: AiController,
    insights: InsightsController,
    learn: LearnController,
    billing: BillingController,
    rewards: RewardsController,
    onSymptomSheet: () -> Unit,
    onOpenComments: (CommunityPost) -> Unit,
    onOpenPostMenu: (CommunityPost) -> Unit,
    onCompose: () -> Unit,
) {
    val close = navigator::pop
    val upgrade = { navigator.push(Route.Paywall) }
    val scope = rememberCoroutineScope()

    when (route) {
        // Cycle
        Route.CycleCalendar -> CycleCalendarScreen(state, health, navigator::push, close)
        is Route.CycleDay -> CycleDayScreen(state, health, route.date, onSymptomSheet, close)

        // Pregnancy
        Route.PregnancyAppointments -> PregnancyAppointmentsScreen(health, close)
        Route.PregnancyCheckIn -> PregnancyCheckInScreen(state, health, close)

        // Stage detail
        Route.StageSymptoms -> StageSymptomsScreen(state, health, close)
        Route.StageSleepMood -> StageSleepMoodScreen(state, health, insights, onOpen = { navigator.push(it) }, onClose = close)

        // AI — the chat is drawn on the deck's navy whatever the app theme is.
        Route.AiChat -> SadoraDarkSurface { AiChatScreen(state, ai, close) }
        Route.AiPreview -> AiFreePreviewScreen(onUpgrade = upgrade, onDismiss = close)

        // Nutrition: camera -> analysing -> result is one linear flow, so each step
        // replaces the last rather than stacking on it.
        Route.FoodSearch -> FoodSearchScreen(state, health, close)
        Route.FoodScanCamera -> FoodScannerScreen(
            state = state,
            health = health,
            onManualEntry = { navigator.replaceTop(Route.FoodSearch) },
            onClose = close,
        )
        Route.Balance -> BalanceScreen(state, close)

        Route.MindJournal -> MindJournalScreen(state, close)
        Route.Medications -> MedicationsScreen(state, close, navigator::push)
        Route.AddMedication -> AddMedicationScreen(health, close)
        Route.MedicationHistory -> MedicationHistoryScreen(health, close)
        Route.Sleep -> SleepScreen(state, health, insights, close)
        Route.Insights -> InsightsScreen(state, insights, close, upgrade)
        Route.Knowledge -> KnowledgeScreen(state, learn, close, navigator::push)
        is Route.Article -> ArticleScreen(route.slug, learn, close, upgrade)
        Route.DataSources -> DataSourcesScreen(health, close)

        // Nur.
        Route.Rewards -> RewardsScreen(state, rewards, close, navigator::push)
        Route.Shop -> ShopScreen(
            state = state,
            rewards = rewards,
            onClose = close,
            // Premium bought with coins changes the tier, and half the app reads it.
            onPremiumGranted = { scope.launch { controller.refreshEntitlements() } },
        )
        Route.Referral -> ReferralScreen(rewards, close)
        Route.HomeLayout -> HomeLayoutScreen(state, rewards, close)
        Route.SecretChat -> SecretChatScreen(
            state = state,
            community = community,
            onOpenComments = onOpenComments,
            onOpenMenu = onOpenPostMenu,
            onCompose = onCompose,
            onClose = close,
        )

        // The same documents onboarding shows, reachable again from settings.
        Route.Terms -> LegalScreen(LegalDocument.Terms, close)
        Route.PrivacyPolicy -> LegalScreen(LegalDocument.Privacy, close)
        Route.Paywall -> PaywallScreen(state, controller, billing, close)

        // Settings detail screens reuse the existing surfaces they configure.
        Route.PersonalDetails,
        Route.GoalsSettings,
        Route.LifeStageSettings,
        Route.Notifications,
        Route.PrivacySecurity,
        Route.LanguageSettings,
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
