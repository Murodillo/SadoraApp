package uz.sadora.app.ui.core

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.InsightsController
import uz.sadora.app.data.LearnController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.MedStatus
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.EmojiTile
import uz.sadora.app.ui.components.IconTile
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraProgressBar
import uz.sadora.app.ui.modules.sentence
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.TrendMetric

/**
 * The four widgets that were previously only whole screens: sleep, medications,
 * insights and knowledge.
 *
 * Each is a summary with one tap into the screen behind it, and each is off by default:
 * a home screen that shipped with eleven cards would be a wall, and the arrangement
 * screen is where she turns on the ones she actually wants. That is the whole point of
 * the layout being hers.
 *
 * None of them invents anything. A widget with no data says it has none and links to
 * the place where data comes from, rather than drawing an empty chart.
 */

/** Last night, from whatever the wearable layer has. */
@Composable
fun SleepWidget(
    state: AppState,
    health: HealthController,
    insights: InsightsController,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val c = Sadora.colors
    val common = strings.common
    val minutes = health.wearableToday?.value(HealthMetric.SLEEP_DURATION)?.toInt() ?: state.sleepMinutes
    // Loaded here for the same reason as in the Insights widget: the week's average was
    // blank until she had happened to open Ong or Uyqu in the same session.
    LaunchedEffect(Unit) { insights.load(7) }
    val week = insights.summary(7)?.trend(TrendMetric.SLEEP_MINUTES)

    SadoraCard(modifier, onClick = { onOpen(Route.Sleep) }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconTile(SadoraIcons.Moon, tint = c.primary, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.today.sleep, style = Sadora.type.body, color = c.muted)
                Text(
                    state.sleepLabel(minutes, common::hoursMinutes),
                    style = Sadora.type.h2,
                    color = c.text,
                )
            }
            // The week's average is the only comparison worth making on a card this
            // small: last night against her own recent nights, not against a norm.
            week?.average?.let { average ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        t.sleepWeek,
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                    )
                    Text(
                        t.average(state.sleepLabel(average.toInt(), common::hoursMinutes)),
                        style = Sadora.type.body,
                        color = c.muted,
                    )
                }
            }
        }
        SadoraProgressBar(
            progress = ((minutes ?: 0) / uz.sadora.app.model.DailySleepGoalMinutes.toFloat()).coerceIn(0f, 1f),
            height = 6.dp,
        )
    }
}

/** Today's doses — how many are done, and the next one still waiting. */
@Composable
fun MedicationsWidget(
    state: AppState,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val c = Sadora.colors
    val pending = state.medications.firstOrNull { it.status == MedStatus.Pending }

    SadoraCard(modifier, onClick = { onOpen(Route.Medications) }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconTile(SadoraIcons.Pill, tint = c.secondary, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.profile.medications, style = Sadora.type.body, color = c.muted)
                Text(
                    when {
                        state.dosesDue == 0 -> t.medsEmpty
                        pending == null -> t.allDoneToday
                        else -> pending.name
                    },
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.dosesDue > 0) {
                SadoraBadge(
                    "${state.dosesTaken} / ${state.dosesDue}",
                    tone = if (pending == null) BadgeTone.Success else BadgeTone.Neutral,
                )
            }
        }

        if (pending != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                EmojiTile(pending.emoji, tint = c.secondary, size = 30.dp)
                Text(pending.time, style = Sadora.type.body, color = c.muted)
                Text(
                    strings.modules.doseCaption(pending.note, pending.foodRelation),
                    style = Sadora.type.body,
                    color = c.muted2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The most recent finding from Tahlillar.
 *
 * Only ever a finding the server produced, worded the way the Insights screen words it
 * — "often seen together", never "caused by". A card that had to shorten a finding into
 * a claim would be the one place in the app where the causation rule broke.
 */
@Composable
fun InsightsWidget(
    insights: InsightsController,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.modules
    val c = Sadora.colors
    // The widget is the only thing on Today that reads Tahlillar, so it fetches its own
    // window rather than relying on the Insights screen having been opened first — a
    // card that said "nothing yet" only because nobody had visited that screen would be
    // lying about her data.
    // A free account is refused the long window, so the week is asked for when the
    // month does not come: the fallback below was reading a summary nobody had loaded.
    LaunchedEffect(Unit) {
        insights.load(INSIGHT_WINDOW_DAYS)
        if (insights.summary(INSIGHT_WINDOW_DAYS) == null) insights.load(7)
    }
    val summary = insights.summary(INSIGHT_WINDOW_DAYS) ?: insights.summary(7)
    val finding = summary?.findings?.firstOrNull()

    SadoraCard(modifier, onClick = { onOpen(Route.Insights) }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconTile(SadoraIcons.Chart, tint = c.accent, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.profile.insights, style = Sadora.type.body, color = c.muted)
                Text(
                    finding?.sentence(t) ?: t.insightsEmptyTitle,
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 3,
                )
            }
        }
        finding?.let {
            CardLabel(t.basedOnDays(it.daysConsidered))
        }
    }
}

/**
 * One piece from the library.
 *
 * The first published article for her stage, which is what the Bilim screen would show
 * her at the top anyway — the widget is a shortcut to reading, not a second editorial
 * decision made on a card.
 */
@Composable
fun KnowledgeWidget(
    learn: LearnController,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    // Same reason as the insights widget: it loads the library itself, so the card is
    // empty only when the library is.
    LaunchedEffect(Unit) { learn.loadFeed() }
    val article = learn.feed?.articles?.firstOrNull()

    SadoraCard(
        modifier,
        onClick = { onOpen(article?.let { Route.Article(it.slug) } ?: Route.Knowledge) },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            IconTile(SadoraIcons.Book, tint = c.primary, size = 44.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(strings.profile.knowledge, style = Sadora.type.body, color = c.muted)
                Text(
                    article?.title ?: strings.modules.libraryEmpty,
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                article?.let {
                    Text(
                        "${it.categoryLabel} · ${strings.modules.readMinutes(it.readMinutes)}",
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                    )
                }
            }
            if (article?.premium == true) SadoraBadge("PREMIUM", tone = BadgeTone.Premium)
        }
    }
}

/**
 * The window the insights widget reads.
 *
 * Thirty days rather than seven: a finding needs enough days behind it to be worth
 * stating, and the Insights screen's own default is the same.
 */
private const val INSIGHT_WINDOW_DAYS = 30
