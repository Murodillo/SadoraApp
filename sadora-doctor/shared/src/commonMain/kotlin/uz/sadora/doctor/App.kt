package uz.sadora.doctor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.launch
import uz.sadora.doctor.data.AuthController
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.DoctorGraph
import uz.sadora.doctor.data.SessionState
import uz.sadora.doctor.design.SadoraTheme
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.AppLanguage
import uz.sadora.doctor.i18n.ProvideStrings
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.nav.AppPhase
import uz.sadora.doctor.nav.Navigator
import uz.sadora.doctor.nav.Route
import uz.sadora.doctor.ui.SettingsSheet
import uz.sadora.doctor.ui.SplashScreen
import uz.sadora.doctor.ui.auth.SignInScreen
import uz.sadora.doctor.ui.components.LocalReduceMotion
import uz.sadora.doctor.ui.components.Motion
import uz.sadora.doctor.ui.components.SadoraDialog
import uz.sadora.doctor.ui.components.SadoraToast
import uz.sadora.doctor.ui.components.SystemBackHandler
import uz.sadora.doctor.ui.components.systemReducesMotion
import uz.sadora.doctor.ui.doctor.DoctorApplyScreen
import uz.sadora.doctor.ui.doctor.DoctorPanelScreen
import uz.sadora.doctor.ui.doctor.DoctorProfileScreen
import uz.sadora.doctor.ui.doctor.NewPostScreen
import uz.sadora.doctor.ui.doctor.QuestionScreen
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import uz.sadora.doctor.design.Sadora

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
            doctors.reset()
            auth.reset()
            navigator.goTo(AppPhase.SignIn)
        }
    }
    val phone = (session as? SessionState.SignedIn)?.user?.phone

    SadoraTheme(darkTheme = isSystemInDarkTheme()) {
        // One language for the whole tree: a change recomposes every screen at once.
        ProvideStrings(language) {
            // The phone's "less motion" setting, read once for every endless animation.
            CompositionLocalProvider(LocalReduceMotion provides systemReducesMotion()) {
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
                                    navigator.goTo(AppPhase.Main)
                                },
                            )
                            AppPhase.Main -> MainContent(
                                navigator = navigator,
                                doctors = doctors,
                                onOpenSettings = { settingsOpen = true },
                                onToast = { toast = it },
                            )
                        }
                    }

                    SadoraToast(
                        message = toast,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = Spacing.md),
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
                                doctors.reset()
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
 * The signed-in app: the panel at the root and whatever is pushed over it. The system
 * Back pops the stack; with nothing left to pop it falls through and leaves the app.
 */
@Composable
private fun MainContent(
    navigator: Navigator,
    doctors: DoctorController,
    onOpenSettings: () -> Unit,
    onToast: (String) -> Unit,
) {
    val words = strings
    val c = Sadora.colors
    // Each screen's saveable state — the panel's scroll above all — outlives a screen
    // pushed over it, so coming back from a question lands where she left, in time to
    // see the answered one leave the list.
    val saved = rememberSaveableStateHolder()
    SystemBackHandler(enabled = navigator.canGoBack, onBack = navigator::pop)
    Box(Modifier.fillMaxSize()) {
    // The client's route transition: a pushed screen slides in over the one it was opened
    // from and slides back out when it is popped, so the stack reads as having a direction.
    AnimatedContent(
        targetState = navigator.screen,
        transitionSpec = {
            val opening = targetState.depth > initialState.depth
            val slide = tween<IntOffset>(Motion.Standard, easing = Motion.Emphasized)
            if (opening) {
                (slideInHorizontally(slide) { it / 4 } + fadeIn(tween(Motion.Standard)))
                    .togetherWith(fadeOut(tween(Motion.Quick)))
            } else {
                fadeIn(tween(Motion.Standard)).togetherWith(
                    slideOutHorizontally(slide) { it / 4 } + fadeOut(tween(Motion.Standard)),
                ).apply {
                    // The page leaving stays on top while it slides away.
                    targetContentZIndex = -1f
                }
            } using SizeTransform(clip = false)
        },
        modifier = Modifier.fillMaxSize(),
        label = "route",
    ) { screen ->
        saved.SaveableStateProvider("${screen.depth}:${screen.route}") {
        when (val route = screen.route) {
            Route.Panel -> DoctorPanelScreen(
                doctors = doctors,
                onApply = { navigator.push(Route.Apply) },
                onOpenPage = { navigator.push(Route.MyPage(it)) },
                onOpenQuestion = { navigator.push(Route.Question(it)) },
                onNewPost = { navigator.push(Route.NewPost) },
                onOpenSettings = onOpenSettings,
                onToast = onToast,
            )
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
        }
        }
    }
    // The client has its tab bar here; this app has none, so the system navigation bar
    // gets the page colour behind it instead of the content scrolling through it.
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(c.bg),
    )
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
