package uz.sadora.doctor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.decodeToImageBitmap
import uz.sadora.contract.BadgeState
import uz.sadora.contract.DoctorAccount
import uz.sadora.doctor.data.AuthController
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.DoctorGraph
import uz.sadora.doctor.data.PanelState
import uz.sadora.doctor.data.RemoteImages
import uz.sadora.doctor.data.photoNudgeDue
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.data.SessionState
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.SadoraTheme
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.AppLanguage
import uz.sadora.doctor.i18n.ProvideStrings
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.nav.AppPhase
import uz.sadora.doctor.nav.Navigator
import uz.sadora.doctor.nav.PushLinks
import uz.sadora.doctor.nav.Route
import uz.sadora.doctor.nav.Tab
import uz.sadora.doctor.ui.SettingsSheet
import uz.sadora.doctor.ui.SplashScreen
import uz.sadora.doctor.ui.auth.SignInScreen
import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.resources.ic3d_chats
import uz.sadora.doctor.resources.ic3d_home
import uz.sadora.doctor.resources.ic3d_message
import uz.sadora.doctor.resources.ic3d_profile
import uz.sadora.doctor.resources.ic3d_qr
import uz.sadora.doctor.ui.components.BadgeDetailSheet
import uz.sadora.doctor.ui.components.BadgeUnlockOverlay
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.LocalReduceMotion
import uz.sadora.doctor.ui.components.LocalRemoteImages
import uz.sadora.doctor.ui.components.Motion
import uz.sadora.doctor.ui.components.NavItemSpec
import uz.sadora.doctor.ui.components.SadoraBottomNav
import uz.sadora.doctor.ui.components.SadoraDialog
import uz.sadora.doctor.ui.components.SadoraToast
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SystemBackHandler
import uz.sadora.doctor.ui.components.systemReducesMotion
import uz.sadora.doctor.ui.doctor.BadgesScreen
import uz.sadora.doctor.ui.doctor.CommunityScreen
import uz.sadora.doctor.ui.doctor.ConversationScreen
import uz.sadora.doctor.ui.doctor.DoctorApplyScreen
import uz.sadora.doctor.ui.doctor.DoctorHomeScreen
import uz.sadora.doctor.ui.doctor.DoctorPanelScreen
import uz.sadora.doctor.ui.doctor.DoctorProfileScreen
import uz.sadora.doctor.ui.doctor.EarningsScreen
import uz.sadora.doctor.ui.doctor.EditDoctorCard
import uz.sadora.doctor.ui.doctor.MessagesScreen
import uz.sadora.doctor.ui.doctor.NewPostScreen
import uz.sadora.doctor.ui.doctor.PatientRecordScreen
import uz.sadora.doctor.ui.doctor.PatientScreen
import uz.sadora.doctor.ui.doctor.PhotoNudgeSheet
import uz.sadora.doctor.ui.doctor.QuestionScreen
import uz.sadora.doctor.ui.doctor.QuickRepliesScreen
import uz.sadora.doctor.ui.doctor.RecordSource
import uz.sadora.doctor.ui.doctor.ScanScreen
import uz.sadora.doctor.ui.doctor.WorkSettingsScreen

/**
 * Sadora Doctor — the root composable.
 *
 * Owns the [Navigator] and the two controllers, resolves the stored session behind the
 * splash, and switches between signing in and the panel. Everything the platform has to
 * say arrives through [graph]; a null graph is a preview with no backend.
 */
@Composable
@Preview
fun App(graph: DoctorGraph? = null) {
    val navigator = remember { Navigator() }
    val auth = remember(graph) { graph?.authController() ?: AuthController(null) }
    val doctors = remember(graph) { graph?.doctorController() ?: DoctorController(null, null) }
    val work = remember(graph) { graph?.workController() ?: WorkController(null) }
    // Every avatar's photo, fetched with her token and kept by URL for the whole launch.
    val photos = remember(graph) {
        graph?.let { g -> RemoteImages<ImageBitmap>(fetch = g::photo, decode = { it.decodeToImageBitmap() }) }
    }
    val scope = rememberCoroutineScope()

    var language by remember { mutableStateOf(AppLanguage.fromCode(graph?.prefs?.readLanguage())) }
    val chooseLanguage: (AppLanguage) -> Unit = {
        language = it
        graph?.prefs?.writeLanguage(it.code)
    }
    var toast by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }


    // The session can end while she is inside: a refresh the server refuses, an account
    // blocked by an operator. The app leaves the panel the moment it does, and forgets
    // what the panel held, so the next account on this phone starts clean.
    val session = graph?.session?.state?.collectAsState()?.value
    LaunchedEffect(session) {
        if (session is SessionState.SignedOut && navigator.phase == AppPhase.Main) {
            photos?.clear()
            doctors.reset()
            work.reset()
            auth.reset()
            navigator.goTo(AppPhase.SignIn)
        }
    }
    val phone = (session as? SessionState.SignedIn)?.user?.phone

    SadoraTheme(darkTheme = isSystemInDarkTheme()) {
        // One language for the whole tree: a change recomposes every screen at once.
        ProvideStrings(language) {
            // The phone's "less motion" setting, read once for every endless animation, and
            // the photo loader every avatar reads.
            CompositionLocalProvider(
                LocalReduceMotion provides systemReducesMotion(),
                LocalRemoteImages provides photos,
            ) {
                val words = strings
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = navigator.phase,
                        transitionSpec = { fadeIn(tween(Motion.Standard)) togetherWith fadeOut(tween(Motion.Standard)) },
                        modifier = Modifier.fillMaxSize(),
                    ) { phase ->
                        when (phase) {
                            AppPhase.Splash -> SplashGate(graph = graph, onResolved = navigator::goTo)
                            AppPhase.SignIn -> SignInScreen(
                                auth = auth,
                                language = language,
                                onLanguage = chooseLanguage,
                                onSignedIn = {
                                    // The sign-in page keeps its state while it fades out;
                                    // it is cleared when the session ends.
                                    doctors.reset()
                                    work.reset()
                                    navigator.goTo(AppPhase.Main)
                                },
                            )
                            AppPhase.Main -> MainContent(
                                navigator = navigator,
                                doctors = doctors,
                                work = work,
                                onOpenSettings = { settingsOpen = true },
                                onToast = { toast = it },
                            )
                        }
                    }

                    // Above the tab bar while it is there, rather than on top of it.
                    val barShown = navigator.phase == AppPhase.Main &&
                        doctors.panelState is PanelState.Approved &&
                        !navigator.canGoBack
                    SadoraToast(
                        message = toast,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = if (barShown) ToastAboveBar else Spacing.md),
                        onTimeout = { toast = null },
                    )

                    SettingsSheet(
                        visible = settingsOpen,
                        phone = phone,
                        language = language,
                        onLanguage = chooseLanguage,
                        onSignOut = {
                            settingsOpen = false
                            confirmSignOut = true
                        },
                        onDismiss = { settingsOpen = false },
                    )

                    SadoraDialog(
                        visible = confirmSignOut,
                        title = words.settings.signOutTitle,
                        body = words.settings.signOutBody,
                        confirmText = words.settings.signOut,
                        onConfirm = {
                            confirmSignOut = false
                            scope.launch {
                                auth.signOut()
                                photos?.clear()
                                doctors.reset()
                                work.reset()
                                navigator.goTo(AppPhase.SignIn)
                            }
                        },
                        onDismiss = { confirmSignOut = false },
                    )
                }
            }
        }
    }
}

/**
 * The signed-in app. Before she is approved, the panel is the whole of it; once she is,
 * five tabs — Home, Messages, Scan, Chat, Profile — each a root with screens pushed over
 * it. The system Back pops the stack, then returns to Home; only from Home with nothing
 * open does it fall through and leave the app.
 */
@Composable
private fun MainContent(
    navigator: Navigator,
    doctors: DoctorController,
    work: WorkController,
    onOpenSettings: () -> Unit,
    onToast: (String) -> Unit,
) {
    val words = strings
    val c = Sadora.colors
    // Each screen's saveable state — a tab's scroll above all — outlives a screen pushed
    // over it, so coming back from a question lands where she left.
    val saved = rememberSaveableStateHolder()

    val approved = doctors.panelState as? PanelState.Approved
    val tabbed = approved != null
    // The unread count feeds the Messages dot from any tab, not only once she opens it.
    LaunchedEffect(tabbed) {
        while (tabbed) {
            doctors.loadConversations(silent = true)
            kotlinx.coroutines.delay(UnreadPollMillis)
        }
    }

    // A tapped push waits here until the tabs are there: a cold start resolves the
    // session and her account first, and the conversation then opens over Messages.
    val pushed = PushLinks.pendingConversation
    LaunchedEffect(pushed, tabbed) {
        if (pushed != null && tabbed) PushLinks.take()?.let(navigator::openConversation)
    }

    // Her badge board, read as she moves between tabs (throttled in the controller): a
    // tier crossed by an answer or a consultation plays as she lands on the next tab.
    var openBadge by remember { mutableStateOf<BadgeState?>(null) }
    LaunchedEffect(tabbed, navigator.tab, navigator.depth) {
        if (tabbed && !navigator.canGoBack) work.loadBadges()
    }

    SystemBackHandler(enabled = navigator.canGoBack || (tabbed && navigator.tab != Tab.Home)) {
        if (navigator.canGoBack) navigator.pop() else navigator.select(Tab.Home)
    }

    // Once per launch, an approved doctor with no photo is asked for one — a moment after
    // the app opens, and not over a conversation a push has just opened.
    var photoNudge by remember { mutableStateOf(false) }
    val nudgeDue = photoNudgeDue(approved?.account, doctors.photoNudgeAsked) && !navigator.canGoBack
    LaunchedEffect(nudgeDue) {
        if (!nudgeDue) return@LaunchedEffect
        kotlinx.coroutines.delay(PhotoNudgeDelayMillis)
        doctors.photoNudgeAsked = true
        photoNudge = true
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The client's route transition: a pushed screen slides in over the one it was
                // opened from and slides back out when it is popped. Switching tabs is a
                // cross-fade instead: tabs are siblings, and a slide would put one behind another.
                AnimatedContent(
                    // The tab is part of the key only at the root: under a pushed screen it
                    // cannot change, and a tab switch should cross-fade, not snap.
                    targetState = navigator.screen to navigator.tab,
                    transitionSpec = {
                        val opening = targetState.first.depth > initialState.first.depth
                        val closing = targetState.first.depth < initialState.first.depth
                        val slide = tween<IntOffset>(Motion.Standard, easing = Motion.Emphasized)
                        when {
                            opening -> (slideInHorizontally(slide) { it / 4 } + fadeIn(tween(Motion.Standard)))
                                .togetherWith(fadeOut(tween(Motion.Quick)))
                            closing -> fadeIn(tween(Motion.Standard)).togetherWith(
                                slideOutHorizontally(slide) { it / 4 } + fadeOut(tween(Motion.Standard)),
                            ).apply {
                                // The page leaving stays on top while it slides away.
                                targetContentZIndex = -1f
                            }
                            else -> (fadeIn(tween(Motion.Standard)) + scaleIn(tween(Motion.Standard, easing = Motion.Emphasized), initialScale = 0.97f))
                                .togetherWith(fadeOut(tween(Motion.Quick)))
                        } using SizeTransform(clip = false)
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "route",
                ) { (screen, tab) ->
                    val root = screen.route == Route.Panel
                    saved.SaveableStateProvider(if (root && tabbed) "tab:$tab" else "${screen.depth}:${screen.route}") {
                        when (val route = screen.route) {
                            Route.Panel -> if (approved != null) {
                                TabRoot(tab, approved.account, navigator, doctors, work, onOpenSettings, onToast)
                            } else {
                                DoctorPanelScreen(
                                    doctors = doctors,
                                    onApply = { navigator.push(Route.Apply) },
                                    onOpenPage = { navigator.push(Route.MyPage(it)) },
                                    onNewPost = { navigator.push(Route.NewPost) },
                                    onOpenSettings = onOpenSettings,
                                )
                            }
                            Route.Apply -> DoctorApplyScreen(
                                doctors = doctors,
                                onSubmitted = {
                                    navigator.pop()
                                    onToast(words.doctors.submitted)
                                },
                                onClose = navigator::pop,
                            )
                            is Route.Question -> QuestionScreen(
                                postId = route.postId,
                                doctors = doctors,
                                onClose = navigator::pop,
                            )
                            is Route.MyPage -> DoctorProfileScreen(
                                doctorId = route.profileId,
                                doctors = doctors,
                                onOpenPost = { navigator.push(Route.Question(it)) },
                                onNewPost = { navigator.push(Route.NewPost) },
                                onClose = navigator::pop,
                            )
                            Route.NewPost -> NewPostScreen(
                                doctors = doctors,
                                onPosted = {
                                    navigator.pop()
                                    onToast(words.community.published)
                                },
                                onClose = navigator::pop,
                            )
                            is Route.Conversation -> ConversationScreen(
                                id = route.id,
                                doctors = doctors,
                                work = work,
                                onClose = navigator::pop,
                                onOpenRecord = { navigator.push(Route.AttachedRecord(route.id, it)) },
                                onOpenPatient = { navigator.push(Route.Patient(route.id)) },
                                onManageReplies = { navigator.push(Route.QuickReplies) },
                                onToast = onToast,
                            )
                            is Route.PatientRecord -> PatientRecordScreen(
                                source = RecordSource.Share(route.token),
                                doctors = doctors,
                                onClose = navigator::pop,
                            )
                            is Route.AttachedRecord -> PatientRecordScreen(
                                source = RecordSource.Attached(route.conversationId, route.messageId),
                                doctors = doctors,
                                onClose = navigator::pop,
                            )
                            Route.WorkSettings -> WorkSettingsScreen(
                                work = work,
                                onClose = navigator::pop,
                                onSaved = {
                                    navigator.pop()
                                    onToast(words.doctors.saved)
                                },
                            )
                            Route.Earnings -> EarningsScreen(work = work, onClose = navigator::pop)
                            Route.QuickReplies -> QuickRepliesScreen(work = work, onClose = navigator::pop)
                            Route.Badges -> BadgesScreen(
                                work = work,
                                onClose = navigator::pop,
                                onOpenBadge = { openBadge = it },
                            )
                            is Route.Patient -> PatientScreen(
                                conversationId = route.conversationId,
                                work = work,
                                doctors = doctors,
                                onClose = navigator::pop,
                                onOpenRecord = { navigator.push(Route.AttachedRecord(route.conversationId, it)) },
                                onToast = onToast,
                            )
                        }
                    }
                }
            }

            // The bar belongs to the roots; a pushed screen takes the whole display, its own
            // composer included. Without the bar the system navigation bar gets the page
            // colour behind it instead of the content scrolling through it.
            if (tabbed && !navigator.canGoBack) {
                SadoraBottomNav(
                    items = listOf(
                        NavItemSpec(Tab.Home, words.tabs.home, Res.drawable.ic3d_home, badge = doctors.questionsLoaded && doctors.questions.isNotEmpty()),
                        NavItemSpec(Tab.Messages, words.tabs.messages, Res.drawable.ic3d_message, badge = doctors.unreadMessages > 0),
                        NavItemSpec(Tab.Scan, words.tabs.scan, Res.drawable.ic3d_qr),
                        NavItemSpec(Tab.Community, words.tabs.community, Res.drawable.ic3d_chats),
                        NavItemSpec(Tab.Profile, words.tabs.profile, Res.drawable.ic3d_profile),
                    ),
                    selected = navigator.tab,
                    onSelect = navigator::select,
                )
            } else {
                Box(Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(c.bg))
            }
        }

        PhotoNudgeSheet(
            visible = photoNudge,
            account = approved?.account,
            doctors = doctors,
            onSaved = { onToast(words.photo.saved) },
            onDismiss = { photoNudge = false },
        )

        BadgeDetailSheet(openBadge, onDismiss = { openBadge = null })

        // A tier just reached — never over the photo sheet, which asks something of her.
        val scope = rememberCoroutineScope()
        BadgeUnlockOverlay(
            unlock = if (tabbed && !photoNudge) work.unlocks.firstOrNull() else null,
            remaining = (work.unlocks.size - 1).coerceAtLeast(0),
            onNext = { shown -> scope.launch { work.unlockShown(shown) } },
            onSkipAll = { scope.launch { work.unlocksSkipped() } },
        )
    }
}

/** How long after the tabs appear the one-time photo sheet rises. */
private const val PhotoNudgeDelayMillis = 1_200L

/** How often the Messages dot is refreshed from any tab. */
private const val UnreadPollMillis = 30_000L

/** The tab bar's height and its margin, plus the usual gap. */
private val ToastAboveBar = 90.dp

/** One of the five tabs, as its root screen. */
@Composable
private fun TabRoot(
    tab: Tab,
    account: DoctorAccount,
    navigator: Navigator,
    doctors: DoctorController,
    work: WorkController,
    onOpenSettings: () -> Unit,
    onToast: (String) -> Unit,
) {
    val openQuestion: (String) -> Unit = { navigator.push(Route.Question(it)) }
    val newPost: () -> Unit = { navigator.push(Route.NewPost) }
    val savedText = strings.doctors.saved
    when (tab) {
        Tab.Home -> DoctorHomeScreen(
            account = account,
            doctors = doctors,
            work = work,
            onOpenQuestion = openQuestion,
            onScan = { navigator.select(Tab.Scan) },
            onMessages = { navigator.select(Tab.Messages) },
            onProfile = { navigator.select(Tab.Profile) },
            onOpenSettings = onOpenSettings,
            onOpenWork = { navigator.push(Route.WorkSettings) },
            onOpenEarnings = { navigator.push(Route.Earnings) },
            onOpenBadges = { navigator.push(Route.Badges) },
            onToast = onToast,
        )
        Tab.Messages -> MessagesScreen(
            doctors = doctors,
            onOpen = { navigator.push(Route.Conversation(it)) },
        )
        Tab.Scan -> ScanScreen(
            doctors = doctors,
            onOpenPatient = { navigator.push(Route.PatientRecord(it)) },
            onToast = onToast,
        )
        Tab.Community -> CommunityScreen(
            doctors = doctors,
            onOpenPost = openQuestion,
            onNewPost = newPost,
        )
        Tab.Profile -> {
            val profileId = account.profileId
            if (profileId != null) {
                DoctorProfileScreen(
                    doctorId = profileId,
                    doctors = doctors,
                    onOpenPost = openQuestion,
                    onNewPost = newPost,
                    onClose = null,
                    onOpenSettings = onOpenSettings,
                    account = account,
                    onSaved = { onToast(savedText) },
                    onOpenWork = { navigator.push(Route.WorkSettings) },
                    onOpenReplies = { navigator.push(Route.QuickReplies) },
                    onOpenBadges = { navigator.push(Route.Badges) },
                    badges = work.badges,
                    onToast = onToast,
                )
            } else {
                // Approved but without a page yet — a moment the server should never show.
                Column {
                    SadoraTopBar(
                        strings.doctors.profileTitle,
                        trailing = { CircleIconButton(SadoraIcons.Settings, contentDescription = strings.settings.title, onClick = onOpenSettings) },
                    )
                    ScreenContent {
                        item { EditDoctorCard(account, doctors, onSaved = { onToast(savedText) }) }
                    }
                }
            }
        }
    }
}

/**
 * The splash, holding until two things are true: the logo has written itself, and the
 * stored session has been resolved — the client app's gate. Waiting for both is what
 * stops the sign-in page flashing past a doctor who is already signed in.
 */
@Composable
private fun SplashGate(graph: DoctorGraph?, onResolved: (AppPhase) -> Unit) {
    var animationDone by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<SessionState>(SessionState.Unknown) }

    LaunchedEffect(graph) {
        session = graph?.repository?.resume() ?: SessionState.SignedOut
    }
    LaunchedEffect(animationDone, session) {
        val resolved = session
        if (!animationDone || resolved is SessionState.Unknown) return@LaunchedEffect
        onResolved(if (resolved is SessionState.SignedIn) AppPhase.Main else AppPhase.SignIn)
    }

    SplashScreen(onReady = { animationDone = true })
}
