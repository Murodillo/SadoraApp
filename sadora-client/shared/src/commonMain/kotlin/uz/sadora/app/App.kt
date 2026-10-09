package uz.sadora.app

import uz.sadora.app.ui.components.systemReducesMotion
import uz.sadora.app.ui.components.BadgeUnlockOverlay
import uz.sadora.app.ui.components.PetBubbleOverlay
import uz.sadora.app.ui.components.Companion
import uz.sadora.app.ui.components.LocalCompanion
import uz.sadora.app.ui.components.BadgeDetailSheet
import uz.sadora.app.ui.components.LocalReduceMotion
import uz.sadora.app.ui.components.LocalPhotoSource
import uz.sadora.app.ui.components.PhotoSource
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.contract.DoctorProfile
import uz.sadora.app.data.AnalyticsEvents
import uz.sadora.app.data.CommunitySyncBridge
import uz.sadora.app.data.HealthSync
import uz.sadora.app.data.PetSyncTap
import uz.sadora.app.data.SadoraGraph
import uz.sadora.app.data.SessionState
import uz.sadora.app.ui.modules.HealthGate
import uz.sadora.app.ui.modules.WearablePromptSheet
import uz.sadora.app.ui.modules.wearableQuestionDue
import uz.sadora.app.ui.modules.wearableSnoozedUntil
import uz.sadora.app.data.applyServerProfile
import uz.sadora.app.design.SadoraTheme
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.ProvideStrings
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.CommunityFilter
import uz.sadora.app.model.CommunityPost
import uz.sadora.app.model.CommunitySort
import uz.sadora.app.nav.AppLink
import uz.sadora.app.nav.AppLinks
import uz.sadora.app.nav.AppPhase
import uz.sadora.app.nav.MindSection
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
import uz.sadora.app.ui.components.ToastTone
import uz.sadora.app.ui.components.StreakCelebration
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.core.AiChatScreen
import uz.sadora.app.ui.core.AiFreePreviewScreen
import uz.sadora.app.ui.core.AliasProfileScreen
import uz.sadora.app.ui.core.ConsultationConsentSheetContent
import uz.sadora.app.ui.core.ConsultationPaySheetContent
import uz.sadora.app.ui.core.ProfilePhotoSheets
import uz.sadora.app.ui.core.DoctorDirectoryScreen
import uz.sadora.app.ui.core.DoctorProfileScreen
import uz.sadora.app.ui.core.CommunityFiltersSheetContent
import uz.sadora.app.ui.core.CommunityRulesSheetContent
import uz.sadora.app.ui.core.ConversationMenuSheetContent
import uz.sadora.app.ui.core.ConversationScreen
import uz.sadora.app.ui.core.ConversationsScreen
import uz.sadora.app.ui.core.EditBioSheetContent
import uz.sadora.app.ui.core.PostDetailScreen
import uz.sadora.app.ui.core.ComposePostSheetContent
import uz.sadora.app.ui.core.NutritionScreen
import uz.sadora.app.ui.core.PostMenuSheetContent
import uz.sadora.app.ui.core.PremiumScreen
import uz.sadora.app.ui.core.ProfileScreen
import uz.sadora.app.ui.core.SecretChatScreen
import uz.sadora.app.ui.core.TodayScreen
import uz.sadora.app.ui.journey.CycleCalendarScreen
import uz.sadora.app.ui.journey.ContractionTimerScreen
import uz.sadora.app.ui.journey.CycleDayScreen
import uz.sadora.app.ui.journey.FeedingScreen
import uz.sadora.app.ui.journey.HotFlushScreen
import uz.sadora.app.ui.journey.JourneyScreen
import uz.sadora.app.ui.journey.KickCounterScreen
import uz.sadora.app.ui.journey.MoodScreenScreen
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
import uz.sadora.app.ui.modules.MindNutritionScreen
import uz.sadora.app.ui.modules.MindScreen
import uz.sadora.app.ui.modules.PaywallScreen
import uz.sadora.app.ui.modules.ReferralScreen
import uz.sadora.app.ui.modules.RewardsScreen
import uz.sadora.app.ui.modules.ShareProfileScreen
import uz.sadora.app.ui.partner.PartnerJoinFlow
import uz.sadora.app.ui.partner.PartnerShell
import uz.sadora.app.ui.partner.PartnerViewScreen
import uz.sadora.app.ui.partner.YaqinimScreen
import uz.sadora.app.ui.modules.ShopScreen
import uz.sadora.app.ui.modules.SleepScreen
import uz.sadora.app.ui.onboarding.LegalDocument
import uz.sadora.app.ui.onboarding.LegalScreen
import uz.sadora.app.ui.onboarding.OnboardingFlow
import uz.sadora.app.ui.onboarding.art
import uz.sadora.app.ui.onboarding.SignInScreen
import uz.sadora.app.ui.onboarding.SplashScreen
import uz.sadora.app.ui.settings.NotificationInboxScreen
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
    // One store per signed-in session, not per process. It used to be one for good, and
    // signing out cleared only the wearable numbers: the next account on the same phone
    // opened onto the last one's name, meals, journal, anonymous alias and message list,
    // and kept them for as long as its own loads took — or for the whole session, offline.
    // The controllers are keyed on the store, so their caches go with it.
    var store by remember { mutableStateOf(AppState()) }
    val state = store
    val navigator = remember { Navigator() }
    val controllers = remember(graph, state) { AppControllers.from(graph, state) }

    var wasInside by remember { mutableStateOf(false) }
    LaunchedEffect(navigator.phase) {
        val inside = navigator.phase == AppPhase.Main || navigator.phase == AppPhase.Partner
        if (wasInside && !inside) {
            navigator.select(Tab.Today)
            // What belongs to the phone rather than to her account is carried over.
            store = AppState().also {
                it.language = state.language
                it.darkTheme = state.darkTheme
                it.appVersion = state.appVersion
            }
        }
        wasInside = inside
    }

    LaunchedEffect(graph) { state.appVersion = graph?.appVersion }

    // The session can end while she is inside: a refresh the server refuses, an account
    // blocked by an operator. The store then cleared itself and nothing else moved — every
    // call failed quietly and the shell kept showing her data. Now the app leaves the
    // shell the moment the session is gone; the phase change above resets the store.
    val sessionState = graph?.session?.state?.collectAsState()?.value
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.SignedOut && (navigator.phase == AppPhase.Main || navigator.phase == AppPhase.Partner)) {
            navigator.goTo(AppPhase.SignIn)
        }
    }

    // Analytics follows her consent, and nothing else: off until the box is ticked,
    // off again the moment it is unticked.
    LaunchedEffect(state.consentAnalytics) { controllers.analytics.setEnabled(state.consentAnalytics) }

    // An invite link is only ever a prefill: the code is settled by the server when the
    // account is created, and an existing account ignores it entirely. A wearable
    // return is handled by the shell, where the devices screen can be reached.
    val link = AppLinks.pending
    LaunchedEffect(link, navigator.phase) {
        if (link is AppLink.Invite && state.pendingInviteCode.isNullOrBlank()) {
            state.pendingInviteCode = link.code
            AppLinks.consume()
        }
        // A Yaqinim code before anyone is signed in: the join screen, with the code in it.
        // Inside the app the shell takes it; on the splash it waits for the session.
        val outside = navigator.phase == AppPhase.Onboarding || navigator.phase == AppPhase.SignIn ||
            navigator.phase == AppPhase.PartnerJoin
        if (link is AppLink.Partner && outside) {
            AppLinks.consume()
            link.code?.let { code ->
                state.pendingPartnerCode = code
                navigator.goTo(AppPhase.PartnerJoin)
            }
        }
    }

    SadoraTheme(darkTheme = state.darkTheme) {
        // One language for the whole tree: a change to it recomposes every screen at
        // once, which is what changing language is.
        ProvideStrings(state.language) {
            // The phone's "less motion" setting, read once for every endless animation.
            // Every face drawn from a photoUrl loads through this session's photo controller.
            val photoSource = remember(controllers) { PhotoSource { controllers.photos.bytes(it) } }
            CompositionLocalProvider(
                LocalReduceMotion provides systemReducesMotion(),
                LocalPhotoSource provides photoSource,
                // Her companion, for the places outside the AI chat that show it. None without the pet.
                LocalCompanion provides controllers.pet.let { if (it.active) Companion(it.pet, it.cheers, it.lastWin, it::cheerShown) else null },
            ) {
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
                            onPartnerInvite = { navigator.goTo(AppPhase.PartnerJoin) },
                            onPartnerAccount = { navigator.goTo(AppPhase.Partner) },
                        )

                        AppPhase.PartnerJoin -> PartnerJoinFlow(
                            state = state,
                            controllers = controllers,
                            onJoined = navigator::goTo,
                            onExit = { navigator.goTo(AppPhase.Onboarding) },
                            onSignInInstead = { navigator.goTo(AppPhase.SignIn) },
                        )

                        AppPhase.Partner -> PartnerShell(
                            state = state,
                            controllers = controllers,
                            onSignedOut = {
                                state.clearDeviceData()
                                navigator.goTo(AppPhase.SignIn)
                            },
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

                        // The phone's health store is asked for, in full, before the app opens.
                        AppPhase.Main -> HealthGate(controllers.wearables) {
                            MainShell(state, navigator, controllers)
                        }
                    }
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
            onResolved(
                when {
                    resolved.needsOnboarding -> AppPhase.Onboarding
                    resolved.user.accountKind == uz.sadora.contract.AccountKind.PARTNER -> AppPhase.Partner
                    else -> AppPhase.Main
                },
            )
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
    var menuFor by mutableStateOf<CommunityPost?>(null)
    var showCompose by mutableStateOf(false)
    var showCommunityRules by mutableStateOf(false)
    var showCommunityFilters by mutableStateOf(false)
    var showEditBio by mutableStateOf(false)
    var showConversationMenu by mutableStateOf(false)
    /** The doctor she is about to open a consultation with: the consent sheet is up. */
    var consultWith by mutableStateOf<DoctorProfile?>(null)
    /** The doctor, by id, whose consultation she is paying for: the pay sheet is up. */
    var payFor by mutableStateOf<String?>(null)
    var showSymptomSheet by mutableStateOf(false)
    /** The badge opened on the board: its sheet covers the tab bar like the others. */
    var openBadge by mutableStateOf<uz.sadora.contract.BadgeState?>(null)
    /** Her profile photo sheet: at shell level so it covers the tab bar, like every sheet here. */
    var showPhotoSheet by mutableStateOf(false)

    /** The day the symptom sheet writes; null is today. Set by the calendar's day page. */
    var symptomSheetDate by mutableStateOf<kotlinx.datetime.LocalDate?>(null)
    var toast by mutableStateOf<String?>(null)
    /** The toast on screen reports a failure: drawn with the warning mark, not the check. */
    var toastIsError by mutableStateOf(false)
    var lastWaterAdded by mutableStateOf(0)

    val anyOpen: Boolean
        get() = showWaterSheet || showSymptomSheet || menuFor != null || showCompose || showCommunityRules || showCommunityFilters ||
            showEditBio || showConversationMenu || consultWith != null || payFor != null || showPhotoSheet

    /** Closes the topmost sheet. False when none was open. */
    fun closeTop(): Boolean = when {
        showWaterSheet -> { showWaterSheet = false; true }
        showSymptomSheet -> { showSymptomSheet = false; true }
        menuFor != null -> { menuFor = null; true }
        showCompose -> { showCompose = false; true }
        showCommunityRules -> { showCommunityRules = false; true }
        showCommunityFilters -> { showCommunityFilters = false; true }
        showEditBio -> { showEditBio = false; true }
        showConversationMenu -> { showConversationMenu = false; true }
        consultWith != null -> { consultWith = null; true }
        payFor != null -> { payFor = null; true }
        showPhotoSheet -> { showPhotoSheet = false; true }
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
        // The pet hears the same edits on their way up, after they are passed on.
        state.sync = PetSyncTap(HealthSync(health, scope), controllers.pet, state, scope)
        health.afterCycleLog = { controllers.pet.fire(scope, uz.sadora.contract.PetTrigger.CYCLE_LOGGED) }
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
        // Badges after the check-in, which may itself have crossed a streak tier.
        rewards.loadBadges(force = true)
        // Which pet she has, and whether her plan lets it speak.
        controllers.pet.load()
        // The frame she wears, for her photo and her alias before the chat is opened.
        controllers.frames.load()
        // Whether she writes in the chat as a doctor; the composer names her either way.
        controllers.doctors.loadAccount()
        // Whether someone sees her — the labour button needs to know — and whom she follows.
        controllers.partner.refresh()
    }

    // "Do you wear a smart watch or band?" — no longer part of sign-up. Asked a few days
    // in, once she has seen what the sleep tab is, and a few seconds after the app opens
    // so it never lands on the first frame. A device already connected answers it.
    var askWearable by remember { mutableStateOf(false) }
    LaunchedEffect(health.loaded) {
        if (!health.loaded) return@LaunchedEffect
        delay(WearableAskDelayMillis)
        val userId = controllers.account.currentUserId ?: return@LaunchedEffect
        val wearables = controllers.wearables
        if (state.hasWearable == null) wearables.load()
        askWearable = wearableQuestionDue(
            prompts = controllers.prompts,
            userId = userId,
            hasWearable = state.hasWearable,
            memberSince = state.memberSince,
            deviceConnected = wearables.deviceEnabled || wearables.connected.isNotEmpty(),
            today = state.today,
        )
    }

    // The pet's first word of the day — a feature she has not tried — a few seconds in,
    // after the opening's own moments. A free account instead sees it asleep now and then.
    // The legendary pet's one-off visit, on the day it is due, takes the slot instead.
    LaunchedEffect(health.loaded) {
        if (!health.loaded) return@LaunchedEffect
        delay(PetOpenDelayMillis)
        val pet = controllers.pet
        if (pet.maybeOffer()) return@LaunchedEffect
        if (pet.active) {
            pet.after(uz.sadora.contract.PetTrigger.APP_OPEN)
        } else {
            controllers.account.currentUserId?.let { pet.maybeTease(it, state.today) }
        }
    }

    // The phone's health store, read each time the app comes to the front. The sync rests
    // a quarter hour between runs by itself, so switching apps back and forth reads
    // nothing; a run is left to finish on pause, since the upload is already under way.
    // A store purchase made while the server could not be reached — or on another phone —
    // is sent for verification each time the app comes forward. Play refunds a purchase
    // nobody acknowledges within three days, so this is not optional.
    LifecycleResumeEffect(controllers.billing) {
        val job = scope.launch {
            if (controllers.account.currentUserId != null) {
                controllers.billing.reconcileStore { controllers.account.refreshEntitlements() }
                controllers.pet.reconcileStore()
                controllers.frames.reconcileStore()
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    LifecycleResumeEffect(controllers.wearables) {
        scope.launch {
            val wearables = controllers.wearables
            wearables.refreshDevice()
            val outcome = wearables.syncDevice(force = false) ?: return@launch
            if (outcome.failure != null) return@launch
            if (outcome.changedMetrics) health.refreshWearables()
            if (outcome.periodsAdded > 0) health.refreshCycle()
        }
        onPauseOrDispose { }
    }

    // The unread count behind the chat's envelope and the dot on its tab: read each time
    // the app comes forward, and every half minute while she is on the Chat tab, where a
    // doctor's reply is most likely to be waited for. Elsewhere the count waits for the
    // next return to the front, rather than polling from every tab.
    LifecycleResumeEffect(community) {
        val job = scope.launch { community.refreshIdentity() }
        onPauseOrDispose { job.cancel() }
    }
    LifecycleResumeEffect(community, navigator.tab) {
        val job = if (navigator.tab != Tab.SecretChat) null else scope.launch {
            while (true) {
                delay(UnreadRefreshMillis)
                community.refreshIdentity()
            }
        }
        onPauseOrDispose { job?.cancel() }
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
    // The round trip runs in the shell's scope, not this effect's: consuming the link
    // changes the key, and the effect it restarts would cancel the code on its way to
    // the server — the grant was given and never saved.
    val link = AppLinks.pending
    LaunchedEffect(link) {
        if (link is AppLink.WearableReturn) {
            AppLinks.consume()
            if (navigator.current != Route.DataSources) navigator.push(Route.DataSources)
            scope.launch { controllers.wearables.onReturned(link.provider, link.ok, link.code, link.state) }
        }
        // A tapped push about a consultation: the doctor wrote, the window ended, money is
        // coming back. The thread says which; it is opened over whatever tab she was on.
        // Yaqinim: a shared code opens her screen with the code typed in; a push about
        // her person (a request, a yes) opens the same screen, read again.
        if (link is AppLink.Partner) {
            AppLinks.consume()
            overlays.closeTop()
            link.code?.let { state.pendingPartnerCode = it }
            if (navigator.current != Route.Yaqinim) navigator.push(Route.Yaqinim)
            scope.launch { controllers.partner.refresh() }
        }
        if (link is AppLink.Conversation) {
            AppLinks.consume()
            overlays.closeTop()
            val open = navigator.current
            if (open is Route.Conversation && open.id == link.id) {
                scope.launch { controllers.messages.refreshThread(link.id) }
            } else {
                navigator.push(Route.Conversation(link.id, controllers.messages.conversations.firstOrNull { it.id == link.id }?.alias.orEmpty()))
            }
        }
    }

    // A saved stage change: the symptom catalogue, the prediction and the calendar are
    // all scoped by the stage the server holds, and none of them was read again — a
    // switch to menopause kept offering cramps and no hot flushes until a restart.
    LaunchedEffect(state.stageRevision) {
        if (state.stageRevision > 0) health.loadAll()
    }

    // The app brought forward on a later day than it last read. The load above runs once,
    // so a phone left with the app open overnight kept yesterday: the cycle day, the
    // water, the streak — the calendar ringed yesterday as today.
    LifecycleResumeEffect(health) {
        if (health.loaded && uz.sadora.app.model.deviceToday() != state.today) {
            scope.launch {
                health.loadAll()
                rewards.checkIn()
            }
        }
        onPauseOrDispose { }
    }

    // Premium swaps the bar's last slot. Bought while standing on the Premium tab, that
    // tab is gone from under her; Today is the one place that is always there.
    val tabs = Tab.bar(state.isPremium)
    LaunchedEffect(tabs) {
        if (navigator.tab !in tabs) navigator.retarget(Tab.Today)
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

    val toast: (String) -> Unit = {
        overlays.toastIsError = false
        overlays.toast = it
    }
    val fullScreen = route?.isFullScreen == true
    // Her account and everything opened from it read as one place of their own, away
    // from the tabs: no bar there either, and the content keeps clear of the system bar.
    val profileStack = !fullScreen && navigator.inProfile
    val barHidden = fullScreen || profileStack

    Box(Modifier.fillMaxSize()) {
        // The bar floats over the page rather than below it, so what scrolls under it
        // shows past the pill's rounded ends; the screens' bottom padding clears it.
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if (profileStack) Modifier.navigationBarsPadding() else Modifier)) {
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
                            pushed, state, navigator, controllers, overlays, toast,
                            // In the shell's scope: the thread's own ends with it, and the
                            // count is refreshed exactly as she leaves.
                            onThreadRead = { scope.launch { community.refreshIdentity() } },
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
                                tab, state, navigator, controllers, overlays,
                                onAddWater = { overlays.showWaterSheet = true },
                                onQuickWater = ::addWater,
                            )
                        }
                    }
                }
            }

            // The tab bar stays put while a module screen is open on top of a tab, and
            // steps aside for the screens that take the whole display and for Profile.
            if (!barHidden) {
                SadoraBottomNav(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    tabs = tabs,
                    selected = navigator.tab,
                    onSelect = navigator::select,
                    journeyLabel = strings.tabs.journey(state.lifeStage),
                    journeyArt = state.lifeStage.art(),
                    mindLabel = if (state.isPremium) strings.tabs.mind else strings.tabs.mindAndNutrition,
                    chatUnread = state.communityUnread,
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
                tone = if (overlays.toastIsError) ToastTone.Error else ToastTone.Success,
                actionText = if (overlays.lastWaterAdded > 0) waterStrings.undo else null,
                onAction = {
                    state.addWater(-overlays.lastWaterAdded)
                    overlays.lastWaterAdded = 0
                    overlays.toast = null
                },
                onTimeout = {
                    overlays.toast = null
                    overlays.toastIsError = false
                    overlays.lastWaterAdded = 0
                },
            )
        }

        // The once-a-day celebration, above everything including the sheets: it is the
        // first thing that happens on the first open of a day, and it takes itself away.
        StreakCelebration(
            result = rewards.celebration,
            onDismiss = {
                rewards.celebrationShown()
                controllers.pet.fire(scope, uz.sadora.contract.PetTrigger.STREAK_KEPT)
            },
        )

        BadgeDetailSheet(
            overlays.openBadge,
            onDismiss = { overlays.openBadge = null },
            worn = rewards.badges?.worn,
            onWear = { key -> scope.launch { rewards.wear(key) } },
            canWear = rewards.badges?.canWear ?: state.isPremium,
            onUpgrade = {
                overlays.openBadge = null
                controllers.analytics.event(AnalyticsEvents.PAYWALL_OPENED, mapOf("from" to "badge_wear"))
                navigator.push(Route.Paywall)
            },
        )

        // After the streak card and the badges: one thing at a time on opening.
        WearablePromptSheet(
            visible = askWearable && rewards.celebration == null && rewards.unlocks.isEmpty(),
            onYes = {
                askWearable = false
                scope.launch { controllers.account.answerWearable(true) }
                navigator.push(Route.DataSources)
            },
            onNo = {
                askWearable = false
                scope.launch { controllers.account.answerWearable(false) }
            },
            onLater = {
                askWearable = false
                val userId = controllers.account.currentUserId
                if (userId != null) {
                    scope.launch { controllers.prompts.setWearableAskAfter(userId, wearableSnoozedUntil(state.today)) }
                }
            },
        )

        // A badge tier just reached — after the streak card, never on top of it.
        BadgeUnlockOverlay(
            unlock = if (rewards.celebration == null) rewards.unlocks.firstOrNull() else null,
            remaining = (rewards.unlocks.size - 1).coerceAtLeast(0),
            onNext = { shown ->
                scope.launch {
                    rewards.unlockShown(shown)
                    if (rewards.unlocks.isEmpty()) controllers.pet.after(uz.sadora.contract.PetTrigger.BADGE_EARNED)
                }
            },
            onWearFrame = { shown ->
                scope.launch {
                    controllers.frames.wear(shown.frame)
                    rewards.unlockShown(shown)
                    if (rewards.unlocks.isEmpty()) controllers.pet.after(uz.sadora.contract.PetTrigger.BADGE_EARNED)
                }
            },
            onSkipAll = {
                scope.launch {
                    rewards.unlocksSkipped()
                    controllers.pet.after(uz.sadora.contract.PetTrigger.BADGE_EARNED)
                }
            },
        )

        // The pet, in the corner above the tab bar — only once nothing else of the
        // opening's is on screen, and never over a full-screen page or an open sheet.
        val petFree = rewards.celebration == null && rewards.unlocks.isEmpty() && !askWearable &&
            !overlays.anyOpen && !fullScreen
        PetBubbleOverlay(
            bubble = controllers.pet.bubble.takeIf { petFree },
            onAction = { action ->
                controllers.pet.dismiss()
                when (action) {
                    uz.sadora.contract.PetAction.FOOD_SCANNER -> navigator.push(Route.FoodScanCamera)
                    uz.sadora.contract.PetAction.MIND_JOURNAL -> navigator.push(Route.MindJournal)
                    uz.sadora.contract.PetAction.WATER -> overlays.showWaterSheet = true
                    uz.sadora.contract.PetAction.PARTNER -> navigator.push(Route.Yaqinim)
                    uz.sadora.contract.PetAction.BADGES -> navigator.push(Route.Badges)
                    uz.sadora.contract.PetAction.DOCTOR_SHARE -> navigator.push(Route.ShareProfile)
                    uz.sadora.contract.PetAction.AI_CHAT -> navigator.push(state.aiRoute())
                    uz.sadora.contract.PetAction.LEARN -> navigator.push(Route.Knowledge)
                    uz.sadora.contract.PetAction.MEDICATIONS -> navigator.push(Route.Medications)
                }
            },
            onWake = {
                controllers.pet.dismiss()
                controllers.analytics.event(AnalyticsEvents.PAYWALL_OPENED, mapOf("from" to "pet"))
                navigator.push(Route.Paywall)
            },
            onDismiss = controllers.pet::dismiss,
            onOffer = {
                controllers.pet.dismiss()
                navigator.push(Route.PetPicker)
            },
            // The layer covers the whole shell, tab bar included; this places the pet in it.
            modifier = Modifier
                .navigationBarsPadding()
                .padding(end = Spacing.sm, bottom = if (barHidden) Spacing.lg else 84.dp),
        )

        SymptomSheet(
            visible = overlays.showSymptomSheet,
            state = state,
            health = health,
            onDismiss = { overlays.showSymptomSheet = false },
            date = overlays.symptomSheetDate ?: state.today,
        )

        ProfilePhotoSheets(state, controllers.photos, visible = overlays.showPhotoSheet, onDismiss = { overlays.showPhotoSheet = false })

        // Kept mounted through the exit animation so the sheet does not blank as it closes.
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
                    community = community,
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
                community = community,
                onPosted = {
                    overlays.showCompose = false
                    overlays.toast = communityStrings.postSent
                    // Whatever the feed was showing, the post she just wrote is now the
                    // first thing on it — not hidden behind "saved" or an activity sort.
                    state.communityFilter = CommunityFilter.Feed
                    state.communitySort = CommunitySort.Newest
                },
            )
        }

        SadoraBottomSheet(
            visible = overlays.showCommunityRules,
            title = communityStrings.rulesTitle,
            onDismiss = { overlays.showCommunityRules = false },
        ) {
            CommunityRulesSheetContent(state, onDone = { overlays.showCommunityRules = false })
        }

        SadoraBottomSheet(
            visible = overlays.showCommunityFilters,
            title = communityStrings.filtersTitle,
            onDismiss = { overlays.showCommunityFilters = false },
        ) {
            CommunityFiltersSheetContent(state, onDone = { overlays.showCommunityFilters = false })
        }

        SadoraBottomSheet(
            visible = overlays.showEditBio,
            title = communityStrings.editBio,
            onDismiss = { overlays.showEditBio = false },
        ) {
            EditBioSheetContent(
                state = state,
                community = community,
                onSaved = {
                    overlays.showEditBio = false
                    overlays.toast = communityStrings.profileSaved
                },
            )
        }

        SadoraBottomSheet(
            visible = overlays.showConversationMenu,
            title = communityStrings.conversationMenu,
            onDismiss = { overlays.showConversationMenu = false },
        ) {
            ConversationMenuSheetContent(
                messages = controllers.messages,
                community = community,
                onOpenProfile = { navigator.push(Route.AliasProfile(it)) },
                onOpenDoctor = { navigator.openDoctor(it) },
                onDone = { message ->
                    overlays.showConversationMenu = false
                    message?.let { overlays.toast = it }
                },
            )
        }

        SadoraBottomSheet(
            visible = overlays.consultWith != null,
            title = strings.doctors.consentTitle,
            onDismiss = { overlays.consultWith = null },
        ) {
            overlays.consultWith?.let { doctor ->
                ConsultationConsentSheetContent(
                    profile = doctor,
                    messages = controllers.messages,
                    onStarted = { thread ->
                        overlays.consultWith = null
                        navigator.push(Route.Conversation(thread.id, thread.alias))
                    },
                    onNeedsPayment = {
                        overlays.consultWith = null
                        overlays.payFor = doctor.id
                    },
                    onCancel = { overlays.consultWith = null },
                )
            }
        }

        SadoraBottomSheet(
            visible = overlays.payFor != null,
            title = strings.doctors.payTitle,
            onDismiss = { overlays.payFor = null },
        ) {
            val paidToast = strings.doctors.paidToast
            overlays.payFor?.let { doctorId ->
                ConsultationPaySheetContent(
                    doctorId = doctorId,
                    doctors = controllers.doctors,
                    partner = controllers.partner,
                    onPaid = {
                        overlays.payFor = null
                        overlays.toast = paidToast
                        // In the shell's scope: the sheet, and its scope, close with this.
                        scope.launch { openPaidConsultation(doctorId, navigator, controllers) }
                    },
                    onCancel = { overlays.payFor = null },
                )
            }
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
    overlays: ShellOverlays,
    onAddWater: () -> Unit,
    onQuickWater: (Int) -> Unit,
) {
    // Badges are counted on read, so each return to a tab — from logging a meal, from a
    // breathing session — is when a tier she just crossed gets its moment. Throttled in
    // the controller, so flicking between tabs does not hammer the server.
    LaunchedEffect(tab) { controllers.rewards.loadBadges() }

    when (tab) {
        Tab.Today -> {
            // A new line on every entry to the tab — that is the feature, so it is asked
            // for here rather than once per session.
            LaunchedEffect(Unit) { controllers.ai.loadGreeting() }
            TodayScreen(
                state = state,
                onOpen = navigator::push,
                // Today's shortcuts to Ong mean the mind half. A free account keeps the
                // food diary in the same tab, and the segment she last left it on is
                // sticky — "Nafas" used to open the diary.
                onSelectTab = { target ->
                    if (target == Tab.Mind) navigator.mindSection = MindSection.Mind
                    navigator.select(target)
                },
                onAddWater = onAddWater,
                onQuickWater = onQuickWater,
                greeting = controllers.ai.greeting,
                health = controllers.health,
                insights = controllers.insights,
                learn = controllers.learn,
                doctors = controllers.doctors,
                isLoading = !controllers.health.loaded,
            )
        }

        // Free accounts keep the food diary inside this tab; Premium gives it the fifth slot.
        Tab.Mind -> if (state.isPremium) {
            MindScreen(
                state = state,
                insights = controllers.insights,
                onClose = null,
                onOpenAi = { navigator.push(state.aiRoute()) },
                onOpenJournal = { navigator.push(Route.MindJournal) },
            )
        } else {
            MindNutritionScreen(
                section = navigator.mindSection,
                onSection = { navigator.mindSection = it },
                state = state,
                insights = controllers.insights,
                onOpenAi = { navigator.push(state.aiRoute()) },
                onOpenJournal = { navigator.push(Route.MindJournal) },
                onOpen = navigator::push,
                onAddWater = onAddWater,
                onQuickWater = onQuickWater,
            )
        }

        Tab.SecretChat -> SecretChatScreen(
            state = state,
            community = controllers.community,
            onOpenPost = { navigator.push(Route.Post(it.id)) },
            onOpenProfile = { navigator.push(Route.AliasProfile(it)) },
            onOpenMessages = { navigator.push(Route.Messages) },
            onOpenDoctor = { navigator.push(Route.Doctor(it)) },
            onOpenDoctors = { navigator.push(Route.Doctors) },
            onOpenMenu = { overlays.menuFor = it },
            onCompose = { overlays.showCompose = true },
            onOpenRules = { overlays.showCommunityRules = true },
            onOpenFilters = { overlays.showCommunityFilters = true },
        )

        Tab.Journey -> JourneyScreen(state = state, health = controllers.health, tools = controllers.stageEvents, onOpen = navigator::push)

        Tab.Nutrition -> NutritionScreen(
            state = state,
            onOpen = navigator::push,
            onAddWater = onAddWater,
            onQuickWater = onQuickWater,
        )

        Tab.Premium -> PremiumScreen(
            state = state,
            controller = controllers.account,
            billing = controllers.billing,
            rewards = controllers.rewards,
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
    onThreadRead: () -> Unit,
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
        is Route.CycleDay -> CycleDayScreen(
            state = state,
            health = health,
            date = route.date,
            onOpenSymptomSheet = { day ->
                overlays.symptomSheetDate = day
                overlays.showSymptomSheet = true
            },
            onClose = close,
            sheetOpen = overlays.showSymptomSheet,
        )

        // Pregnancy
        Route.PregnancyAppointments -> PregnancyAppointmentsScreen(health, close)
        Route.PregnancyCheckIn -> PregnancyCheckInScreen(state, health, close)
        Route.KickCounter -> KickCounterScreen(controllers.stageEvents, close)
        Route.Contractions -> ContractionTimerScreen(controllers.stageEvents, close, partner = controllers.partner, onToast = toast)
        Route.Feeding -> FeedingScreen(controllers.stageEvents, close)
        Route.MoodScreen -> MoodScreenScreen(controllers.stageEvents, close, onAskDoctor = { navigator.push(Route.Doctors) })
        Route.HotFlushes -> HotFlushScreen(controllers.stageEvents, close, onToast = toast)

        // Stage detail
        Route.StageSymptoms -> StageSymptomsScreen(state, health, close)
        Route.StageSleepMood -> StageSleepMoodScreen(state, health, insights, onOpen = { navigator.push(it) }, onClose = close)

        // AI — the chat follows the app theme like every other screen.
        Route.AiChat -> AiChatScreen(state, controllers.ai, controllers.pet, close)
        Route.AiPreview -> AiFreePreviewScreen(pet = controllers.pet.pet, onUpgrade = upgrade, onDismiss = close)

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
        Route.Medications -> MedicationsScreen(state, health, close, navigator::push)
        Route.AddMedication -> AddMedicationScreen(health, close)
        is Route.EditMedication -> AddMedicationScreen(health, close, editingId = route.id)
        Route.MedicationHistory -> MedicationHistoryScreen(health, close)
        Route.Sleep -> SleepScreen(state, health, insights, close, onToast = toast)
        Route.Insights -> InsightsScreen(state, insights, close, upgrade)
        Route.Knowledge -> KnowledgeScreen(state, controllers.learn, close, navigator::push)
        is Route.Article -> ArticleScreen(route.slug, controllers.learn, close, upgrade)
        Route.DataSources -> DataSourcesScreen(
            controllers.wearables,
            health,
            close,
            onToast = toast,
            onErrorToast = {
                overlays.toastIsError = true
                overlays.toast = it
            },
        )

        // Gul.
        Route.Rewards -> RewardsScreen(state, controllers.rewards, close, navigator::push)
        Route.Badges -> uz.sadora.app.ui.modules.BadgesScreen(
            controllers.rewards,
            close,
            onOpenBadge = { overlays.openBadge = it },
            onUpgrade = upgrade,
        )
        Route.PetPicker -> uz.sadora.app.ui.modules.PetPickerScreen(controllers.pet, close, onUpgrade = upgrade, partner = controllers.partner)
        Route.Frames -> uz.sadora.app.ui.modules.FramesScreen(
            state = state,
            frames = controllers.frames,
            onClose = close,
            onEditPhoto = { overlays.showPhotoSheet = true },
            onOpenBadges = { navigator.push(Route.Badges) },
            partner = controllers.partner,
        )
        Route.Shop -> ShopScreen(
            state = state,
            rewards = controllers.rewards,
            onClose = close,
            onOpenFrames = { navigator.push(Route.Frames) },
            // Premium bought with coins changes the tier, and half the app reads it.
            onPremiumGranted = {
                controllers.analytics.event(AnalyticsEvents.PREMIUM_GRANTED, mapOf("via" to "coins"))
                scope.launch { controllers.account.refreshEntitlements() }
            },
        )
        Route.Referral -> ReferralScreen(controllers.rewards, close)
        Route.HomeLayout -> HomeLayoutScreen(state, controllers.rewards, close)
        // Kept for a link into it; the tab is where it lives now.
        Route.SecretChat -> SecretChatScreen(
            state = state,
            community = controllers.community,
            onOpenPost = { navigator.push(Route.Post(it.id)) },
            onOpenProfile = { navigator.push(Route.AliasProfile(it)) },
            onOpenMessages = { navigator.push(Route.Messages) },
            onOpenDoctor = { navigator.push(Route.Doctor(it)) },
            onOpenDoctors = { navigator.push(Route.Doctors) },
            onOpenMenu = { overlays.menuFor = it },
            onCompose = { overlays.showCompose = true },
            onOpenRules = { overlays.showCommunityRules = true },
            onOpenFilters = { overlays.showCommunityFilters = true },
            onClose = close,
        )
        is Route.Post -> PostDetailScreen(
            postId = route.id,
            state = state,
            community = controllers.community,
            onOpenMenu = { overlays.menuFor = it },
            onOpenProfile = { navigator.push(Route.AliasProfile(it)) },
            onOpenDoctor = { navigator.push(Route.Doctor(it)) },
            onClose = close,
        )
        is Route.Doctor -> DoctorProfileScreen(
            doctorId = route.id,
            state = state,
            doctors = controllers.doctors,
            messages = controllers.messages,
            onOpenPost = { navigator.push(Route.Post(it.id)) },
            onOpenMenu = { overlays.menuFor = it },
            onMessage = { overlays.consultWith = it },
            onPay = { overlays.payFor = it.id },
            onOpenConversation = { id, name -> navigator.push(Route.Conversation(id, name)) },
            onClose = close,
        )
        Route.Doctors -> DoctorDirectoryScreen(
            doctors = controllers.doctors,
            onOpenDoctor = { navigator.push(Route.Doctor(it)) },
            onClose = close,
        )
        is Route.AliasProfile -> AliasProfileScreen(
            alias = route.alias,
            state = state,
            community = controllers.community,
            onOpenPost = { navigator.push(Route.Post(it.id)) },
            onOpenMenu = { overlays.menuFor = it },
            onMessage = { alias ->
                // An existing thread with her is reused; the list knows which. A consultation
                // carries a doctor's real name, never an alias, but it is excluded all the same.
                val existing = controllers.messages.conversations.firstOrNull { it.alias == alias && !it.isConsultation }
                navigator.push(Route.Conversation(existing?.id, alias))
            },
            onEditBio = { overlays.showEditBio = true },
            onClose = close,
            onToast = toast,
        )
        Route.Messages -> ConversationsScreen(
            messages = controllers.messages,
            onOpen = { navigator.push(Route.Conversation(it.id, it.alias)) },
            onClose = close,
        )
        is Route.Conversation -> ConversationScreen(
            conversationId = route.id,
            alias = route.alias,
            messages = controllers.messages,
            language = state.language.code,
            onOpenProfile = { navigator.push(Route.AliasProfile(it)) },
            onOpenDoctor = { navigator.openDoctor(it) },
            onOpenMenu = { overlays.showConversationMenu = true },
            onPay = { overlays.payFor = it },
            onRead = onThreadRead,
            onClose = close,
            health = controllers.health,
            onToast = toast,
        )

        // Her account, behind the avatar in the home header.
        Route.Profile -> ProfileScreen(
            state = state,
            controller = controllers.account,
            health = health,
            photos = controllers.photos,
            partner = controllers.partner,
            badges = controllers.rewards.badges,
            pet = controllers.pet.pet,
            legendaryPet = controllers.pet.owned.firstOrNull { it.legendary },
            onEditPhoto = { overlays.showPhotoSheet = true },
            onOpen = {
                when (it) {
                    Route.Paywall -> upgrade()
                    // A tab, not a screen over Profile: selecting it also clears the stack.
                    Route.SecretChat -> navigator.select(Tab.SecretChat)
                    else -> navigator.push(it)
                }
            },
            onSignedOut = {
                state.clearDeviceData()
                navigator.goTo(AppPhase.SignIn)
            },
            onClose = close,
        )
        Route.ShareProfile -> ShareProfileScreen(controllers.share, close, onToast = toast)
        Route.Yaqinim -> YaqinimScreen(
            state = state,
            partner = controllers.partner,
            onOpenPerson = { navigator.push(Route.PartnerView(it)) },
            onClose = close,
            onToast = toast,
        )
        is Route.PartnerView -> PartnerViewScreen(route.linkId, controllers.partner, close, onToast = toast)
        Route.NotificationInbox -> NotificationInboxScreen(state, controllers.notifications, close, navigator::push)

        // The same documents onboarding shows, reachable again from settings.
        Route.Terms -> LegalScreen(LegalDocument.Terms, close)
        Route.PrivacyPolicy -> LegalScreen(LegalDocument.Privacy, close)
        Route.Paywall -> PaywallScreen(state, controllers.account, controllers.billing, close, partner = controllers.partner)

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
            lastPeriod = controllers.health.cycle?.lastPeriodStart,
        )
    }
}

/** How often the unread count is re-read while she is on the Chat tab. */
private const val UnreadRefreshMillis = 30_000L

/** How long after opening the app the smart-device question waits before it appears. */
private const val WearableAskDelayMillis = 4_000L

/** Later than the wearable question, so the two never arrive together. */
private const val PetOpenDelayMillis = 6_000L

/**
 * After a consultation is paid: her page is read again for the thread's id — the server
 * made the thread at checkout and opened its window on the callback — and the thread is
 * opened, or re-read when she paid from inside it.
 */
private suspend fun openPaidConsultation(doctorId: String, navigator: Navigator, controllers: AppControllers) {
    val doctors = controllers.doctors
    doctors.loadProfile(doctorId)
    val profile = doctors.profile?.takeIf { it.id == doctorId } ?: return
    val conversationId = profile.conversationId ?: return
    controllers.messages.load()
    val open = navigator.current
    if (open is Route.Conversation && open.id == conversationId) {
        controllers.messages.refreshThread(conversationId)
    } else {
        navigator.push(Route.Conversation(conversationId, profile.fullName))
    }
}
