package org.example.project

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.example.project.design.SadoraTheme
import org.example.project.design.Spacing
import org.example.project.model.AppState
import org.example.project.nav.AppPhase
import org.example.project.nav.Navigator
import org.example.project.nav.Route
import org.example.project.nav.Tab
import org.example.project.ui.components.ButtonTone
import org.example.project.ui.components.PillButton
import org.example.project.ui.components.SadoraBottomNav
import org.example.project.ui.components.SadoraBottomSheet
import org.example.project.ui.components.SadoraToast
import org.example.project.ui.core.AiChatScreen
import org.example.project.ui.core.AiFreePreviewScreen
import org.example.project.ui.core.AiScreen
import org.example.project.ui.core.NutritionScreen
import org.example.project.ui.core.ProfileScreen
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
import org.example.project.ui.modules.MedicationsScreen
import org.example.project.ui.modules.MedicationHistoryScreen
import org.example.project.ui.modules.MindJournalScreen
import org.example.project.ui.modules.MindScreen
import org.example.project.ui.modules.PaywallScreen
import org.example.project.ui.modules.SleepScreen
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
fun App() {
    val state = remember { AppState() }
    val navigator = remember { Navigator() }

    SadoraTheme(darkTheme = state.darkTheme) {
        AnimatedContent(
            targetState = navigator.phase,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.fillMaxSize(),
        ) { phase ->
            when (phase) {
                AppPhase.Splash -> SplashScreen(
                    onReady = { navigator.goTo(AppPhase.Onboarding) },
                )

                AppPhase.Onboarding -> OnboardingFlow(
                    state = state,
                    onFinished = { navigator.goTo(AppPhase.Main) },
                    onSignInInstead = { navigator.goTo(AppPhase.SignIn) },
                )

                AppPhase.SignIn -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                    SignInScreen(
                        state = state,
                        onSignedIn = { navigator.goTo(AppPhase.Main) },
                        onRegisterInstead = { navigator.goTo(AppPhase.Onboarding) },
                    )
                }

                AppPhase.Main -> MainShell(state, navigator)
            }
        }
    }
}

/**
 * The tab shell: content, bottom navigation, and the overlays (water sheet, toast)
 * that can be raised from any tab.
 */
@Composable
private fun MainShell(state: AppState, navigator: Navigator) {
    var showWaterSheet by remember { mutableStateOf(false) }
    var showSymptomSheet by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var lastWaterAdded by remember { mutableStateOf(0) }

    fun addWater(ml: Int) {
        state.addWater(ml)
        lastWaterAdded = ml
        toast = "$ml ml qo'shildi"
        showWaterSheet = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                val route = navigator.current
                if (route != null) {
                    PushedScreen(route, state, navigator, onSymptomSheet = { showSymptomSheet = true })
                } else {
                    RootTab(navigator.tab, state, navigator, onAddWater = { showWaterSheet = true })
                }
            }

            // The tab bar stays put while a module screen is open on top of a tab.
            SadoraBottomNav(
                selected = navigator.tab,
                onSelect = navigator::select,
                journeyLabel = state.lifeStage.tabLabel,
            )
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

        SadoraBottomSheet(
            visible = showWaterSheet,
            title = "Suv qo'shish",
            onDismiss = { showWaterSheet = false },
        ) {
            androidx.compose.foundation.layout.Row(
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
    onAddWater: () -> Unit,
) {
    when (tab) {
        Tab.Today -> TodayScreen(
            state = state,
            onOpen = navigator::push,
            onAddWater = onAddWater,
        )

        Tab.Journey -> JourneyScreen(state = state, onOpen = navigator::push)

        Tab.Ai -> if (state.isPremium) {
            AiScreen(
                state = state,
                onOpenChat = { navigator.push(Route.AiChat) },
                onUpgrade = { navigator.push(Route.Paywall) },
            )
        } else {
            AiFreePreviewScreen(
                onUpgrade = { navigator.push(Route.Paywall) },
                onDismiss = { navigator.select(Tab.Today) },
            )
        }

        Tab.Nutrition -> NutritionScreen(
            state = state,
            onOpen = navigator::push,
            onAddWater = onAddWater,
        )

        Tab.Profile -> ProfileScreen(state = state, onOpen = navigator::push)
    }
}

@Composable
private fun PushedScreen(
    route: Route,
    state: AppState,
    navigator: Navigator,
    onSymptomSheet: () -> Unit,
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

        // AI
        Route.AiChat -> AiChatScreen(state, close)

        // Nutrition
        Route.FoodSearch -> FoodSearchScreen(state, "Kechki ovqat", close)
        Route.FoodScanCamera -> FoodScanCameraScreen(
            state = state,
            onCapture = { navigator.replaceTop(Route.FoodScan) },
            onManualEntry = { navigator.replaceTop(Route.FoodSearch) },
            onClose = close,
        )
        Route.FoodScan -> FoodScanScreen(state, close)
        Route.Balance -> BalanceScreen(state, close)

        Route.Mind -> MindScreen(state, close, upgrade, onOpenJournal = { navigator.push(Route.MindJournal) })
        Route.MindJournal -> MindJournalScreen(close)
        Route.Medications -> MedicationsScreen(state, close, navigator::push)
        Route.AddMedication -> AddMedicationScreen(state, close)
        Route.MedicationHistory -> MedicationHistoryScreen(close)
        Route.Sleep -> SleepScreen(state, close)
        Route.Insights -> InsightsScreen(state, close, upgrade)
        Route.Knowledge -> KnowledgeScreen(state, close, navigator::push)
        is Route.Article -> ArticleScreen(route.title, close)
        Route.DataSources -> DataSourcesScreen(close)
        Route.Paywall -> PaywallScreen(state, close)

        // Settings detail screens reuse the existing surfaces they configure.
        Route.PersonalDetails,
        Route.GoalsSettings,
        Route.LifeStageSettings,
        Route.Notifications,
        Route.PrivacySecurity,
        Route.About,
        -> SettingsDetailScreen(route, state, close)
    }
}
