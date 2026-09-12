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
import uz.sadora.app.data.AnalyticsEvents
import uz.sadora.app.data.CommunitySyncBridge
import uz.sadora.app.data.HealthSync
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
import uz.sadora.app.nav.AppLink
import uz.sadora.app.nav.AppLinks
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
import uz.sadora.app.ui.core.NutritionScreen
import uz.sadora.app.ui.core.PostMenuSheetContent
import uz.sadora.app.ui.core.PremiumScreen
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
import uz.sadora.app.ui.modules.ShareProfileScreen
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
 * Owns the single [AppState] store, the [Navigator] and the [AppControllers], and
 * switches between the pre-login phases and the five-tab shell. Everything the platform
 * has to say arrives through the graph and through [AppLinks]; nothing here reaches
 * for a singleton of its own.
 */
@Composable
@Preview
fun App(graph: SadoraGraph? = null) {
    val state = remember { AppState() }
    val navigator = remember { Navigator() }
    val controllers = remember(graph, state) { AppControllers.from(graph, state) }

    LaunchedEffect(graph) { state.appVersion = graph?.appVersion }

    // Analytics follows her consent, and nothing else: off until the box is ticked,
    // off again the moment it is unticked.
    LaunchedEffect(state.consentAnalytics) { controllers.analytics.setEnabled(state.consentAnalytics) }

    // An invite link is only ever a prefill: the code is settled by the server when the
    // account is created, and an existing account ignores it entirely. A wearable
    // return is handled by the shell, where the devices screen can be reached.
    val link = AppLinks.pending
    LaunchedEffect(link) {
        if (link is AppLink.Invite && state.pendingInviteCode.isNullOrBlank()) {
            state.pendingInviteCode = link.code
            AppLinks.consume()
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
                        controller = controllers.account,
                        onFinished = {
                            controllers.analytics.event(AnalyticsEvents.ONBOARDING_COMPLETED)
                            navigator.goTo(AppPhase.Main)
                        },
                        onSignInInstead = { navigator.goTo(AppPhase.SignIn) },
                    )

                    AppPhase.SignIn -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        SignInScreen(
                            state = state,
                            controller = controllers.account,
                            onSignedIn = {
                                controllers.analytics.event(AnalyticsEvents.SIGNED_IN)
                                navigator.goTo(it)
                            },
                            onRegisterInstead = { navigator.goTo(AppPhase.Onboarding) },
                        )
                    }

                    AppPhase.Main -> MainShell(state, navigator, controllers)
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
 * The sheets the shell can raise over any tab, and the one toast.
 *
 * Owned by the shell rather than by a screen so they cover the tab bar; screens get
 * callbacks that open them and never see the state.
 */
private class ShellOverlays {
    var showWaterSheet by mutableStateOf(false)
    var commentsFor by mutableStateOf<CommunityPost?>(null)
    var menuFor by mutableStateOf<CommunityPost?>(null)
    var showCompose by mutableStateOf(false)
    var showSymptomSheet by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var lastWaterAdded by mutableStateOf(0)

    val anyOpen: Boolean
        get() = showWaterSheet || showSymptomSheet || commentsFor != null || menuFor != null || showCompose

    /** Closes the topmost sheet. False when none was open. */
    fun closeTop(): Boolean = when {
        showWaterSheet -> { showWaterSheet = false; true }
        showSymptomSheet -> { showSymptomSheet = false; true }
        commentsFor != null -> { commentsFor = null; true }
        menuFor != null -> { menuFor = null; true }
        showCompose -> { showCompose = false; true }
        else -> false
    }
}

/**
 * The tab shell: content, bottom navigation, and the overlays (water sheet, toast)
 * that can be raised from any tab.
 */
@Composable
private fun MainShell(
    state: AppState,
    navigator: Navigator,
    controllers: AppControllers,
) {
    val scope = rememberCoroutineScope()
    val waterStrings = strings.nutrition
    val communityStrings = strings.community
    val overlays = remember { ShellOverlays() }
    val health = controllers.health
    val community = controllers.community
    val rewards = controllers.rewards

    // One load on entering the shell. Failures are silent — a tab that could not reach
    // the server shows its empty state rather than a banner over the whole app.
    LaunchedEffect(health) {
        // Every screen already edits the store; the sink is what carries those edits on
        // to the server, so none of them had to learn about it.
        state.sync = HealthSync(health, scope)
        state.communitySync = CommunitySyncBridge(community, scope)
        // The sections the server can close, before any of them is opened.
        controllers.account.refreshFlags()
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

    // The streak celebration is the one event worth an analytics row on its own:
    // "how many people come back on day seven" is the question the scheme exists for.
    LaunchedEffect(rewards.celebration) {
        val result = rewards.celebration ?: return@LaunchedEffect
        controllers.analytics.event(
            AnalyticsEvents.STREAK_DAY,
            mapOf("days" to result.streak.current.toString(), "milestone" to (result.milestone != null).toString()),
        )
    }

    // The browser sent her back from a wearable provider's consent page. The devices
    // screen shows the outcome, so it is opened if she is not already on it.
    val link = AppLinks.pending
    LaunchedEffect(link) {
        if (link is AppLink.WearableReturn) {
            AppLinks.consume()
            controllers.wearables.onReturned(link.provider, link.ok)
            if (navigator.current != Route.DataSources) navigator.push(Route.DataSources)
        }
    }

    // A screen view per tab or route change, named by the route class — stable across
    // releases and languages, and carrying nothing about what is on the screen.
    val route = navigator.current
    LaunchedEffect(navigator.tab, route) {
        controllers.analytics.screen(route?.let { it::class.simpleName ?: "route" } ?: navigator.tab.name)
    }

    // The system back button closes an open sheet, then pops the pushed screen, then
    // returns to Today; only from Today with nothing open does it leave the app.
    SystemBackHandler(enabled = overlays.anyOpen || navigator.canGoBack || navigator.tab != Tab.Today) {
        when {
            overlays.closeTop() -> Unit
            navigator.canGoBack -> navigator.pop()
            else -> navigator.select(Tab.Today)
        }
    }

    fun addWater(ml: Int) {
        state.addWater(ml)
        overlays.lastWaterAdded = ml
        overlays.toast = waterStrings.waterAdded(ml)
        overlays.showWaterSheet = false
        controllers.analytics.event(AnalyticsEvents.WATER_ADDED)
    }

    val toast: (String) -> Unit = { overlays.toast = it }
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
                        PushedScreen(pushed, state, navigator, controllers, overlays, toast)
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
                            RootTab(tab, state, navigator, controllers, onAddWater = { overlays.showWaterSheet = true })
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
                message = overlays.toast,
                actionText = if (overlays.lastWaterAdded > 0) waterStrings.undo else null,
                onAction = {
                    state.addWater(-overlays.lastWaterAdded)
                    overlays.lastWaterAdded = 0
                    overlays.toast = null
                },
                onTimeout = {
                    overlays.toast = null
                    overlays.lastWaterAdded = 0
                },
            )
        }

        // The once-a-day celebration, above everything including the sheets: it is the
        // first thing that happens on the first open of a day, and it takes itself away.
        StreakCelebration(
            result = rewards.celebration,
            onDismiss = rewards::celebrationShown,
        )

        SymptomSheet(
            visible = overlays.showSymptomSheet,
            state = state,
            health = health,
            onDismiss = { overlays.showSymptomSheet = false },
        )

        // Kept mounted through the exit animation so the sheet does not blank as it closes.
        val lastComments = remember { mutableStateOf<CommunityPost?>(null) }
        overlays.commentsFor?.let { lastComments.value = it }
        // The comments come from the server when the sheet opens, not with the feed.
        LaunchedEffect(overlays.commentsFor?.id) { overlays.commentsFor?.let { community.loadComments(it.id) } }
        SadoraBottomSheet(
            visible = overlays.commentsFor != null,
            title = communityStrings.comments,
            onDismiss = { overlays.commentsFor = null },
        ) {
            lastComments.value?.let { post ->
                // Read back from the store so the loaded comments replace the stale copy.
                val current = state.communityPosts.firstOrNull { it.id == post.id } ?: post
                CommentsSheetContent(state, current)
            }
        }

        val lastMenu = remember { mutableStateOf<CommunityPost?>(null) }
        overlays.menuFor?.let { lastMenu.value = it }
        SadoraBottomSheet(
            visible = overlays.menuFor != null,
            title = if (lastMenu.value?.isMine == true) communityStrings.yourOwnPost else communityStrings.reportPost,
            onDismiss = { overlays.menuFor = null },
        ) {
            lastMenu.value?.let { post ->
                PostMenuSheetContent(
                    state = state,
                    post = post,
                    onDone = { message ->
                        overlays.menuFor = null
                        message?.let { overlays.toast = it }
                    },
                )
            }
        }

        SadoraBottomSheet(
            visible = overlays.showCompose,
            title = communityStrings.newPost,
            onDismiss = { overlays.showCompose = false },
        ) {
            ComposePostSheetContent(
                state = state,
                onPosted = {
                    overlays.showCompose = false
                    overlays.toast = communityStrings.postSent
                },
            )
        }

        SadoraBottomSheet(
            visible = overlays.showWaterSheet,
            title = waterStrings.addWaterTitle,
            onDismiss = { overlays.showWaterSheet = false },
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
    controllers: AppControllers,
    onAddWater: () -> Unit,
) {
    when (tab) {
        Tab.Today -> {
            // A new line on every entry to the tab — that is the feature, so it is asked
            // for here rather than once per session.
            LaunchedEffect(Unit) { controllers.ai.loadGreeting() }
            TodayScreen(
                state = state,
                onOpen = navigator::push,
                onSelectTab = navigator::select,
                onAddWater = onAddWater,
                greeting = controllers.ai.greeting,
                health = controllers.health,
                insights = controllers.insights,
                learn = controllers.learn,
            )
        }

        Tab.Mind -> MindScreen(
            state = state,
            insights = controllers.insights,
            onClose = null,
            onOpenAi = { navigator.push(state.aiRoute()) },
            onOpenJournal = { navigator.push(Route.MindJournal) },
        )

        Tab.Journey -> JourneyScreen(state = state, health = controllers.health, onOpen = navigator::push)

        Tab.Nutrition -> NutritionScreen(
            state = state,
            onOpen = navigator::push,
            onAddWater = onAddWater,
        )

        Tab.Premium -> PremiumScreen(
            state = state,
            controller = controllers.account,
            onOpen = { route ->
                if (route == Route.Paywall) controllers.analytics.event(AnalyticsEvents.PAYWALL_OPENED, mapOf("from" to "premium_tab"))
                navigator.push(route)
            },
        )
    }
}

@Composable
private fun PushedScreen(
    route: Route,
    state: AppState,
    navigator: Navigator,
    controllers: AppControllers,
    overlays: ShellOverlays,
    toast: (String) -> Unit,
) {
    val close = navigator::pop
    val upgrade = {
        controllers.analytics.event(AnalyticsEvents.PAYWALL_OPENED, mapOf("from" to (route::class.simpleName ?: "route")))
        navigator.push(Route.Paywall)
    }
    val scope = rememberCoroutineScope()
    val health = controllers.health
    val insights = controllers.insights

    when (route) {
        // Cycle
        Route.CycleCalendar -> CycleCalendarScreen(state, health, navigator::push, close)
        is Route.CycleDay -> CycleDayScreen(state, health, route.date, { overlays.showSymptomSheet = true }, close)

        // Pregnancy
        Route.PregnancyAppointments -> PregnancyAppointmentsScreen(health, close)
        Route.PregnancyCheckIn -> PregnancyCheckInScreen(state, health, close)

        // Stage detail
        Route.StageSymptoms -> StageSymptomsScreen(state, health, close)
        Route.StageSleepMood -> StageSleepMoodScreen(state, health, insights, onOpen = { navigator.push(it) }, onClose = close)

        // AI — the chat is drawn on the deck's navy whatever the app theme is.
        Route.AiChat -> SadoraDarkSurface { AiChatScreen(state, controllers.ai, close) }
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
        Route.Sleep -> SleepScreen(state, health, insights, close, onToast = toast)
        Route.Insights -> InsightsScreen(state, insights, close, upgrade)
        Route.Knowledge -> KnowledgeScreen(state, controllers.learn, close, navigator::push)
        is Route.Article -> ArticleScreen(route.slug, controllers.learn, close, upgrade)
        Route.DataSources -> DataSourcesScreen(controllers.wearables, health, close, onToast = toast)

        // Gul.
        Route.Rewards -> RewardsScreen(state, controllers.rewards, close, navigator::push)
        Route.Shop -> ShopScreen(
            state = state,
            rewards = controllers.rewards,
            onClose = close,
            // Premium bought with coins changes the tier, and half the app reads it.
            onPremiumGranted = {
                controllers.analytics.event(AnalyticsEvents.PREMIUM_GRANTED, mapOf("via" to "coins"))
                scope.launch { controllers.account.refreshEntitlements() }
            },
        )
        Route.Referral -> ReferralScreen(controllers.rewards, close)
        Route.HomeLayout -> HomeLayoutScreen(state, controllers.rewards, close)
        Route.SecretChat -> SecretChatScreen(
            state = state,
            community = controllers.community,
            onOpenComments = { overlays.commentsFor = it },
            onOpenMenu = { overlays.menuFor = it },
            onCompose = { overlays.showCompose = true },
            onClose = close,
        )

        // Her account, behind the avatar in the home header.
        Route.Profile -> ProfileScreen(
            state = state,
            controller = controllers.account,
            health = health,
            onOpen = { if (it == Route.Paywall) upgrade() else navigator.push(it) },
            onSignedOut = {
                state.clearDeviceData()
                navigator.goTo(AppPhase.SignIn)
            },
            onClose = close,
        )
        Route.ShareProfile -> ShareProfileScreen(controllers.share, close, onToast = toast)

        // The same documents onboarding shows, reachable again from settings.
        Route.Terms -> LegalScreen(LegalDocument.Terms, close)
        Route.PrivacyPolicy -> LegalScreen(LegalDocument.Privacy, close)
        Route.Paywall -> PaywallScreen(state, controllers.account, controllers.billing, close)

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
            controller = controllers.account,
            notifications = controllers.notifications,
            share = controllers.share,
            onClose = close,
            onOpen = navigator::push,
            onSignedOut = { navigator.goTo(AppPhase.SignIn) },
            onToast = toast,
        )
    }
}
