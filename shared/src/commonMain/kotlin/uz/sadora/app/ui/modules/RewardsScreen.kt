package uz.sadora.app.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import uz.sadora.app.data.RewardsController
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.AppState
import uz.sadora.app.model.Fmt
import uz.sadora.app.nav.Route
import uz.sadora.app.ui.components.AnimatedNumber
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.NurMark
import uz.sadora.app.ui.components.ProgressRing
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDivider
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.Skeleton
import uz.sadora.app.ui.components.StatTile
import uz.sadora.app.ui.components.TileRow
import uz.sadora.contract.CoinEntry
import uz.sadora.contract.EarnRate
import uz.sadora.contract.StreakStatus

/**
 * The Nur wallet: what she has, how the streak is going, and every coin that moved.
 *
 * The history is the important half. A currency whose balance cannot be explained is a
 * number the app is asking her to trust, and this screen is the answer to "where did
 * that come from" — which is also why the earn rates are listed rather than hidden in a
 * help page.
 */
@Composable
fun RewardsScreen(
    state: AppState,
    rewards: RewardsController,
    onClose: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.rewards
    val c = Sadora.colors

    LaunchedEffect(Unit) { rewards.loadSummary() }

    val summary = rewards.summary

    Column(modifier) {
        SadoraTopBar(t.walletTitle, onBack = onClose)

        if (summary == null && rewards.busy) {
            WalletSkeleton()
            return@Column
        }

        ScreenContent {
            item {
                BalanceCard(
                    balance = summary?.coins?.balance ?: state.coins,
                    earned = summary?.coins?.earned ?: 0,
                    spent = summary?.coins?.spent ?: 0,
                )
            }

            item {
                StreakCard(summary?.streak ?: StreakStatus(current = state.streakDays))
            }

            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SadoraButton(
                        t.openShop,
                        { onOpen(Route.Shop) },
                        icon = SadoraIcons.Bloom,
                        modifier = Modifier.weight(1f),
                    )
                    SadoraButton(
                        t.inviteFriends,
                        { onOpen(Route.Referral) },
                        tone = ButtonTone.Outline,
                        icon = SadoraIcons.Share,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (summary != null && summary.earnRates.isNotEmpty()) {
                item { EarnRatesCard(summary.earnRates) }
            }

            item {
                SadoraCard {
                    Text(t.history, style = Sadora.type.h3, color = c.text)
                    val history = summary?.history.orEmpty()
                    if (history.isEmpty()) {
                        Text(t.historyEmpty, style = Sadora.type.body, color = c.muted)
                    } else {
                        history.forEachIndexed { index, entry ->
                            if (index > 0) SadoraDivider()
                            HistoryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

/** The balance, counting up, with what went in and out either side of it. */
@Composable
private fun BalanceCard(balance: Int, earned: Int, spent: Int) {
    val t = strings.rewards
    val c = Sadora.colors
    SadoraCard {
        CardLabel(t.balance)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            NurMark(size = 34.dp)
            AnimatedNumber(
                value = balance,
                style = Sadora.type.h1,
                color = c.text,
                format = { Fmt.int(it) },
            )
            Text(
                t.coinName.lowercase(),
                style = Sadora.type.body,
                color = c.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        TileRow {
            StatTile(t.earned, Fmt.int(earned), Modifier.weight(1f), tint = c.success)
            StatTile(t.spent, Fmt.int(spent), Modifier.weight(1f), tint = c.muted2)
        }
    }
}

/** The streak, with the ring showing how far along the next milestone is. */
@Composable
private fun StreakCard(streak: StreakStatus) {
    val t = strings.rewards
    val c = Sadora.colors
    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProgressRing(
                progress = streak.milestoneProgress,
                size = 84.dp,
                strokeWidth = 8.dp,
                color = c.secondary,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedNumber(streak.current, Sadora.type.h2, c.text)
                    Text(
                        strings.common.daysWord,
                        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted,
                    )
                }
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(t.currentStreak, style = Sadora.type.body, color = c.muted)
                Text(
                    if (streak.current <= 1) t.streakStarted else t.streakDays(streak.current),
                    style = Sadora.type.h3,
                    color = c.text,
                )
                Text(
                    streak.nextMilestone
                        ?.let { next -> t.daysToMilestone((next - streak.current).coerceAtLeast(1), next) }
                        ?: t.streakBeyondMilestones,
                    style = Sadora.type.body,
                    color = c.muted,
                )
                Text(
                    "${t.longestStreak}: ${t.days(streak.longest)}",
                    style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                    color = c.muted2,
                )
            }
        }
    }
}

/**
 * What each action pays, read from the server.
 *
 * Listed rather than described in prose so the economy is checkable: an operator who
 * changes a rate changes this list, and nothing in the app has to be rebuilt to agree
 * with it.
 */
@Composable
private fun EarnRatesCard(rates: List<EarnRate>) {
    val t = strings.rewards
    val c = Sadora.colors
    SadoraCard {
        Text(t.howToEarn, style = Sadora.type.h3, color = c.text)
        rates.forEach { rate ->
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(t.earnReason(rate.reason), style = Sadora.type.body, color = c.text)
                    rate.dailyCap?.takeIf { it > 1 }?.let { cap ->
                        Text(
                            t.perDay(cap),
                            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
                            color = c.muted2,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NurMark(size = 14.dp)
                    Text(
                        "+${Fmt.int(rate.amount)}",
                        style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                        color = c.textAccent,
                    )
                }
            }
        }
    }
}

/** One row of the ledger: what it was, when, and how much it moved. */
@Composable
private fun HistoryRow(entry: CoinEntry) {
    val c = Sadora.colors
    val gained = entry.amount >= 0
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(entry.title, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
        Text(
            (if (gained) "+" else "−") + Fmt.int(kotlin.math.abs(entry.amount)),
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            // Not colour alone: the sign is on the number, so the row still reads for
            // someone who cannot tell the two greens apart.
            color = if (gained) c.textAccent else c.muted,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun WalletSkeleton() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Skeleton(Modifier.fillMaxWidth().height(150.dp))
        Skeleton(Modifier.fillMaxWidth().height(128.dp))
        Skeleton(Modifier.fillMaxWidth().height(52.dp))
        Skeleton(Modifier.fillMaxWidth().height(200.dp))
    }
}

/**
 * The Today widget: streak on the left, balance on the right, one tap to the wallet.
 *
 * Deliberately small. The reward scheme is a layer on top of a health app, and it does
 * not get the same weight on the home screen as the day itself.
 */
@Composable
fun StreakWidget(
    state: AppState,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.rewards
    val c = Sadora.colors
    SadoraCard(modifier, onClick = { onOpen(Route.Rewards) }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(
                    progress = if (state.streakDays > 0) 1f else 0f,
                    size = 56.dp,
                    strokeWidth = 6.dp,
                    color = c.secondary,
                ) {
                    AnimatedNumber(state.streakDays, Sadora.type.h3, c.text)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (state.streakDays <= 1) t.streakStarted else t.streakDays(state.streakDays),
                    style = Sadora.type.h3,
                    color = c.text,
                    maxLines = 1,
                )
                Text(t.streakSubtitle, style = Sadora.type.body, color = c.muted, maxLines = 1)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                NurMark(size = 20.dp)
                AnimatedNumber(
                    value = state.coins,
                    style = Sadora.type.h3,
                    color = c.text,
                    format = { Fmt.int(it) },
                )
            }
        }
    }
}
