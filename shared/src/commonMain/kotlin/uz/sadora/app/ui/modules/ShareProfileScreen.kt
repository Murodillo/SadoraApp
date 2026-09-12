package uz.sadora.app.ui.modules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.data.ShareController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.IconTile
import uz.sadora.app.ui.components.QrCode
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SelectChip
import uz.sadora.app.ui.components.appearFromBelow
import uz.sadora.app.ui.components.rememberShareAction
import uz.sadora.contract.Limits
import uz.sadora.contract.ProfileShare

/** How long a link may live, as the chips offer it. Hours; the last two are days. */
private val TtlOptions = listOf(1, 6, 24, 24 * 3, 24 * 7)

/**
 * "Shifokorga ko'rsatish" — the QR code and what it opens.
 *
 * Two states. Before a code exists the screen explains, in one paragraph, what a
 * doctor will see and how long it lasts, and offers one button. Once it exists the
 * code is the screen: big, white, with the expiry under it and the two ways to hand
 * the link over beside it. Everything about privacy is said on the screen rather than
 * behind a link, because this is the one place her data leaves the app.
 */
@Composable
fun ShareProfileScreen(
    share: ShareController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.share
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val shareAction = rememberShareAction()
    var ttl by remember { mutableStateOf(Limits.SHARE_DEFAULT_HOURS) }
    var confirmRevoke by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { share.refresh() }

    val current = share.current
    val now = Clock.System.now()
    val live = current?.takeIf { it.isActive(now) }
    val url = live?.url

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)

        ScreenContent {
            share.error?.let { failure ->
                item { ErrorStrip(failure.readable(), onRetry = share::clearError) }
            }

            if (url != null) {
                item {
                    Box(Modifier.appearFromBelow(0)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            QrCode(url, contentDescription = t.showToDoctor)
                            Text(t.showToDoctor, style = Sadora.type.h3, color = c.text, textAlign = TextAlign.Center)
                            ShareStatusLine(live)
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SadoraButton(
                            t.shareLink,
                            { shareAction(t.shareMessage(url)) },
                            icon = SadoraIcons.Share,
                            modifier = Modifier.weight(1f),
                        )
                        SadoraButton(
                            t.revoke,
                            { confirmRevoke = true },
                            tone = ButtonTone.Outline,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                item {
                    Box(Modifier.appearFromBelow(0)) {
                        SadoraCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                            ) {
                                IconTile(SadoraIcons.Shield, tint = c.primary, size = 46.dp)
                                Text(t.subtitle, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
                            }
                            Text(t.intro, style = Sadora.type.body, color = c.muted)
                            if (live != null) ShareStatusLine(live)
                        }
                    }
                }
                item {
                    SadoraCard {
                        CardLabel(t.validFor)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                            TtlOptions.forEach { hours ->
                                SelectChip(
                                    label = if (hours >= 24) t.days(hours / 24) else t.hours(hours),
                                    selected = ttl == hours,
                                    onClick = { ttl = hours },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        SadoraButton(
                            if (share.busy) t.creating else if (live != null) t.regenerate else t.create,
                            enabled = !share.busy && !share.isOffline,
                            onClick = {
                                scope.launch {
                                    if (share.create(ttl) == null) onToast(t.failed)
                                }
                            },
                        )
                        if (share.isOffline) {
                            Text(t.offline, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            item {
                SadoraCard {
                    CardLabel(t.includesTitle)
                    t.includes.forEach { line -> Bullet(line, "✓", c.success) }
                    CardLabel(t.excludesTitle, modifier = Modifier.padding(top = Spacing.xxs))
                    t.excludes.forEach { line -> Bullet(line, "–", c.muted2) }
                }
            }

            item { DisclaimerNote(t.privacyNote) }
        }
    }

    SadoraDialog(
        visible = confirmRevoke,
        title = t.revoke,
        body = t.privacyNote,
        confirmText = t.revoke,
        cancelText = strings.common.cancel,
        onConfirm = {
            confirmRevoke = false
            scope.launch { if (share.revoke()) onToast(t.revoked) }
        },
        onDismiss = { confirmRevoke = false },
    )
}

/** Expiry, views, and the last open — the record of where the link has been. */
@Composable
private fun ShareStatusLine(share: ProfileShare?) {
    share ?: return
    val t = strings.share
    val c = Sadora.colors
    val dates = strings.dates
    val zone = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val expiry = share.expiresAt.toLocalDateTime(zone)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (share.isActive(now)) {
                SadoraBadge(t.expiresAt("${dates.dayMonth(expiry.date)} ${Fmt.time(expiry)}"), BadgeTone.Success)
            } else {
                SadoraBadge(t.expired, BadgeTone.Neutral)
            }
        }
        Text(
            if (share.viewCount == 0) t.neverViewed else {
                t.viewedTimes(share.viewCount) + (share.lastViewedAt?.let { " · " + t.lastViewed(dates.ago(it, now)) }.orEmpty())
            },
            style = Sadora.type.caption,
            color = c.muted,
        )
    }
}

@Composable
private fun Bullet(text: String, mark: String, tint: androidx.compose.ui.graphics.Color) {
    val c = Sadora.colors
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
        Text(mark, style = Sadora.type.body.copy(fontWeight = FontWeight.Bold), color = tint)
        Text(text, style = Sadora.type.body, color = c.text)
    }
}
