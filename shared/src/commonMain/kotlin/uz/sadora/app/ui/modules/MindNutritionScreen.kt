package uz.sadora.app.ui.modules

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import uz.sadora.app.data.InsightsController
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.nav.MindSection
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.Motion
import uz.sadora.app.ui.components.SegmentedControl
import uz.sadora.app.ui.core.NutritionScreen

/**
 * The Mind tab of a free account: mind and the food diary behind one switch.
 *
 * The bar has five slots and the last one has to sell Premium until she has it, so
 * the two everyday screens share a slot. Each keeps its own top bar and content; only
 * the switch under the bar is added, and Premium takes it away again by giving the
 * food diary a tab of its own.
 */
@Composable
fun MindNutritionScreen(
    section: MindSection,
    onSection: (MindSection) -> Unit,
    state: AppState,
    insights: InsightsController,
    onOpenAi: () -> Unit,
    onOpenJournal: () -> Unit,
    onOpen: (Route) -> Unit,
    onAddWater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tabs
    val switch: @Composable () -> Unit = {
        SegmentedControl(
            options = listOf(t.mind, t.nutrition),
            selectedIndex = section.ordinal,
            onSelect = { onSection(MindSection.entries[it]) },
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.xxs),
        )
    }

    AnimatedContent(
        targetState = section,
        transitionSpec = { fadeIn(tween(Motion.Standard)).togetherWith(fadeOut(tween(Motion.Quick))) },
        modifier = modifier.fillMaxSize(),
        label = "mind-section",
    ) { shown ->
        when (shown) {
            MindSection.Mind -> MindScreen(
                state = state,
                insights = insights,
                onClose = null,
                onOpenAi = onOpenAi,
                onOpenJournal = onOpenJournal,
                underBar = switch,
            )

            MindSection.Nutrition -> NutritionScreen(
                state = state,
                onOpen = onOpen,
                onAddWater = onAddWater,
                underBar = switch,
            )
        }
    }
}
