package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.contract.ConsultationPayment
import uz.sadora.contract.DoctorPayoutView
import uz.sadora.contract.EarningLine
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.EmptyState
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.LoadMoreRow
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SectionHeader
import uz.sadora.doctor.ui.components.Skeleton

/**
 * What her paid consultations have brought in: the totals — gross, Sadora's share, hers,
 * what has been paid out and the balance still owed her, and what is to go back to
 * patients — then every paid consultation and every payout, newest first, read a page
 * at a time as she scrolls.
 */
@Composable
fun EarningsScreen(
    work: WorkController,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = strings.work
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val calls = work.earningsCalls

    LaunchedEffect(Unit) {
        calls.clearError()
        work.loadEarnings(silent = false)
    }
    val earnings = work.earnings

    Column(modifier) {
        SadoraTopBar(w.earningsTitle, onBack = onClose)
        ScreenContent {
            calls.error?.let { failure ->
                item(key = "error") { ErrorStrip(failure.readable(), onRetry = { scope.launch { work.loadEarnings(silent = false) } }) }
            }
            if (earnings == null) {
                if (calls.error == null) item(key = "skeleton") { Skeleton(Modifier.fillMaxWidth().height(220.dp), shape = Radius.card) }
                return@ScreenContent
            }
            item(key = "totals") {
                SadoraCard {
                    Text(w.balance, style = Sadora.type.body, color = c.muted)
                    Text(somText(earnings.balanceMinor), style = Sadora.type.display, color = c.textAccent)
                    MoneyLine(w.gross, earnings.grossMinor)
                    MoneyLine(w.commissionLine, earnings.commissionMinor)
                    MoneyLine(w.net, earnings.netMinor, strong = true)
                    MoneyLine(w.paidOut, earnings.paidOutMinor)
                    if (earnings.refundDueMinor > 0) MoneyLine(w.refundDue, earnings.refundDueMinor, tint = c.danger)
                    Text(w.earningsNote, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                }
            }

            // Both lists in the server's order, the latest first, a page at a time: the end of
            // each, once drawn, asks for its next page.
            item(key = "lines-title") { SectionHeader(w.linesTitle) }
            val lines = earnings.lines
            if (lines.isEmpty()) {
                item(key = "lines-empty") { EmptyState(title = w.linesEmpty, body = w.linesEmptyBody, actionText = null, onAction = {}) }
            } else {
                items(lines.size, key = { "line-" + lines[it].sessionId }) { EarningRow(lines[it]) }
            }
            if (work.linesHaveMore) {
                item(key = "lines-more") { LoadMoreRow(work.linesRead, onLoadMore = { work.loadMoreLines() }) }
            }

            item(key = "payouts-title") { SectionHeader(w.payoutsTitle) }
            val payouts = earnings.payouts
            if (payouts.isEmpty()) {
                item(key = "payouts-empty") { Text(w.payoutsEmpty, style = Sadora.type.body, color = c.muted) }
            } else {
                items(payouts.size, key = { "payout-" + payouts[it].id }) { PayoutRow(payouts[it]) }
            }
            if (work.payoutsHaveMore) {
                item(key = "payouts-more") { LoadMoreRow(work.payoutsRead, onLoadMore = { work.loadMorePayouts() }) }
            }
        }
    }
}

/** One paid consultation: who, when, how it stands, and what of it is hers. */
@Composable
private fun EarningRow(line: EarningLine) {
    val w = strings.work
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(line.patientName, style = Sadora.type.h3, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(somText(line.netMinor), style = Sadora.type.h3, color = if (line.payment == ConsultationPayment.PAID) c.text else c.muted2)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            line.openedAt?.let {
                Text(dayMonthTime(it), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2, modifier = Modifier.weight(1f))
            } ?: Row(Modifier.weight(1f)) {}
            PaymentChip(line.payment)
        }
        Text(
            "${somText(line.priceMinor)} − ${somText(line.commissionMinor)}",
            style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
            color = c.muted,
        )
    }
}

@Composable
private fun PayoutRow(payout: DoctorPayoutView) {
    val c = Sadora.colors
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xxs) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(dayMonthOf(payout.paidAt), style = Sadora.type.body, color = c.muted, modifier = Modifier.weight(1f))
            Text(somText(payout.amountMinor), style = Sadora.type.h3, color = c.successText)
        }
        payout.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = Sadora.type.body, color = c.text) }
    }
}

/** How a consultation's money stands, in its colour: paid green, owed back red. */
@Composable
internal fun PaymentChip(payment: ConsultationPayment, modifier: Modifier = Modifier) {
    val c = Sadora.colors
    val tint = when (payment) {
        ConsultationPayment.PAID -> c.successText
        ConsultationPayment.REFUND_DUE -> c.danger
        ConsultationPayment.PENDING -> c.warning
        ConsultationPayment.FREE, ConsultationPayment.REFUNDED -> c.muted2
    }
    TintChip(strings.work.payment(payment), tint, modifier)
}
