package uz.sadora.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import kotlinx.coroutines.launch
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.WearableController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.ChipFlowRow
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.IconTile
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.appearFromBelow
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.contract.ConnectionStatus
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.ProviderInfo
import uz.sadora.contract.ProviderStatus

/**
 * "Qurilmalar" — the providers, the ones she has, and what each one is for.
 *
 * Three groups, in the order that matters to her: what is connected (with its status,
 * a sync button and a way out), what can be connected today, and what is planned. A
 * planned provider is drawn rather than hidden, greyed with a reason, so the list is the
 * same on every phone and nobody wonders where their watch went.
 *
 * Connecting a cloud provider leaves for the browser and comes back through a link;
 * the controller remembers that we went, and the return says whether it worked.
 */
@Composable
fun DataSourcesScreen(
    wearables: WearableController,
    health: HealthController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.devices
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var confirmDisconnect by remember { mutableStateOf<HealthProvider?>(null) }
    var expanded by remember { mutableStateOf<HealthProvider?>(null) }

    LaunchedEffect(Unit) {
        wearables.load()
        health.loadSources()
    }

    // The return from the provider's page, once: a toast, then the list reloads itself.
    LaunchedEffect(wearables.returned) {
        when (wearables.returned) {
            true -> onToast(t.returnedOk)
            false -> onToast(t.returnedError)
            null -> Unit
        }
        if (wearables.returned != null) {
            wearables.clearReturned()
            health.refreshWearables()
        }
    }

    val samplesBy = health.sources.associateBy { it.provider }

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose, subtitle = t.subtitle)

        ScreenContent {
            wearables.error?.let { failure ->
                item { ErrorStrip(failure.readable(), onRetry = wearables::clearError) }
            }

            if (wearables.connected.isNotEmpty()) {
                item { SectionLabel(t.connectedSection) }
                itemsIndexed(wearables.connected) { index, info ->
                    Box(Modifier.appearFromBelow(index)) {
                        ConnectedCard(
                            info = info,
                            samples = samplesBy[info.provider],
                            busy = wearables.busy,
                            onSync = {
                                scope.launch {
                                    wearables.syncNow(info.provider)?.let { result ->
                                        onToast(t.synced)
                                        if (result.accepted + result.updated > 0) health.refreshWearables()
                                    }
                                }
                            },
                            onDisconnect = { confirmDisconnect = info.provider },
                            onReconnect = {
                                scope.launch {
                                    wearables.startConnect(info.provider)?.let(uriHandler::openUri)
                                }
                            },
                        )
                    }
                }
            }

            if (wearables.available.isNotEmpty()) {
                item { SectionLabel(t.availableSection) }
                items(wearables.available.size) { index ->
                    val info = wearables.available[index]
                    Box(Modifier.appearFromBelow(index + wearables.connected.size)) {
                        ProviderCard(
                            info = info,
                            expanded = expanded == info.provider,
                            onToggle = { expanded = if (expanded == info.provider) null else info.provider },
                            action = {
                                SadoraButton(
                                    if (wearables.connectStarted == info.provider) t.connecting else t.connect,
                                    enabled = !wearables.busy,
                                    onClick = {
                                        scope.launch {
                                            wearables.startConnect(info.provider)?.let(uriHandler::openUri)
                                        }
                                    },
                                )
                                Text(t.openBrowserNote, style = Sadora.type.caption, color = c.muted)
                                if (info.provider == HealthProvider.WHOOP) {
                                    Text(t.noStepsNote, style = Sadora.type.caption, color = c.muted)
                                }
                            },
                        )
                    }
                }
            }

            if (wearables.planned.isNotEmpty()) {
                item { SectionLabel(t.plannedSection) }
                items(wearables.planned.size) { index ->
                    val info = wearables.planned[index]
                    ProviderCard(
                        info = info,
                        expanded = expanded == info.provider,
                        onToggle = { expanded = if (expanded == info.provider) null else info.provider },
                        action = null,
                    )
                }
            }

            if (wearables.providers.isEmpty() && !wearables.busy) {
                item {
                    SadoraCard {
                        Text(strings.modules.sourcesEmpty, style = Sadora.type.h3, color = c.text)
                        Text(strings.modules.sourcesEmptyBody, style = Sadora.type.body, color = c.muted)
                    }
                }
            }

            item {
                SadoraCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        IconTile(SadoraIcons.Pencil, tint = c.muted, size = 40.dp, iconSize = 18.dp)
                        Column(Modifier.weight(1f)) {
                            Text(t.manualTitle, style = Sadora.type.h3, color = c.text)
                            Text(t.manualBody, style = Sadora.type.body, color = c.muted)
                        }
                    }
                }
            }

            item { DisclaimerNote(t.note) }
        }
    }

    val pending = confirmDisconnect
    SadoraDialog(
        visible = pending != null,
        title = t.disconnectConfirmTitle,
        body = t.disconnectConfirmBody,
        confirmText = t.disconnect,
        cancelText = strings.common.cancel,
        onConfirm = {
            confirmDisconnect = null
            pending?.let { provider -> scope.launch { wearables.disconnect(provider) } }
        },
        onDismiss = { confirmDisconnect = null },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = Sadora.type.h3, color = Sadora.colors.text, modifier = Modifier.padding(start = Spacing.xxs, top = Spacing.xxs))
}

/** A provider she has: status, last sync, sample count, and the two actions. */
@Composable
private fun ConnectedCard(
    info: ProviderInfo,
    samples: ProviderStatus?,
    busy: Boolean,
    onSync: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
) {
    val t = strings.devices
    val m = strings.modules
    val c = Sadora.colors
    val dates = strings.dates
    val connection = info.connection ?: return
    val status = connection.status
    SadoraCard {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProviderGlyph(info.provider, active = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.provider(info.provider), style = Sadora.type.h3, color = c.text)
                Text(
                    connection.lastSyncAt?.let { t.lastSync(dates.ago(it, Clock.System.now())) } ?: t.neverSynced,
                    style = Sadora.type.body,
                    color = c.muted,
                )
            }
            when (status) {
                ConnectionStatus.ACTIVE -> SadoraBadge(t.statusActive, BadgeTone.Connected)
                ConnectionStatus.EXPIRED -> SadoraBadge(t.statusExpired, BadgeTone.Neutral)
                ConnectionStatus.ERROR -> SadoraBadge(t.statusError, BadgeTone.Neutral)
            }
        }
        samples?.let {
            Text(
                listOfNotNull(
                    m.samples(Fmt.int(it.sampleCount.toInt())),
                    it.lastSampleAt?.let { at -> m.lastSample(dates.ago(at, Clock.System.now())) },
                ).joinToString(" · "),
                style = Sadora.type.caption,
                color = c.muted2,
            )
        }
        if (info.metrics.isNotEmpty()) {
            ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                info.metrics.take(8).forEach { metric -> SadoraBadge(m.metric(metric), BadgeTone.Neutral) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (status == ConnectionStatus.EXPIRED) {
                PillButton(t.reconnect, onReconnect, tone = ButtonTone.Primary, modifier = Modifier.weight(1f))
            } else {
                PillButton(if (busy) t.syncing else t.syncNow, onSync, tone = ButtonTone.Primary, modifier = Modifier.weight(1f))
            }
            PillButton(t.disconnect, onDisconnect, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * A provider she does not have: the name, the tagline, and — when opened — what it
 * brings and where the app uses it. The action slot is the connect button for an
 * available provider and nothing for a planned one.
 */
@Composable
private fun ProviderCard(
    info: ProviderInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    action: (@Composable () -> Unit)?,
) {
    val t = strings.devices
    val m = strings.modules
    val c = Sadora.colors
    SadoraCard(onClick = onToggle) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ProviderGlyph(info.provider, active = info.available)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(t.provider(info.provider), style = Sadora.type.h3, color = if (info.available) c.text else c.muted)
                Text(t.providerTagline(info.provider), style = Sadora.type.body, color = c.muted)
            }
            if (!info.available) {
                SadoraBadge(t.unavailable(info.unavailableReason.orEmpty()), BadgeTone.Neutral)
            } else {
                Text(if (expanded) "–" else "+", style = Sadora.type.h3, color = c.muted)
            }
        }
        if (expanded || action != null && info.available) {
            CardLabel(t.givesTitle)
            ChipFlowRow(horizontalGap = Spacing.xxs, verticalGap = Spacing.xxs) {
                info.metrics.forEach { metric -> SadoraBadge(m.metric(metric), BadgeTone.Neutral) }
            }
            CardLabel(t.usedInTitle)
            t.usedIn(info.provider).forEach { place ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("•", style = Sadora.type.body, color = c.primary)
                    Text(place, style = Sadora.type.body, color = c.text)
                }
            }
            action?.invoke()
        }
    }
}

/** A round tile with the provider's initial — no vendor logos, so nothing looks endorsed. */
@Composable
private fun ProviderGlyph(provider: HealthProvider, active: Boolean) {
    val c = Sadora.colors
    val t = strings.devices
    Box(
        Modifier
            .size(44.dp)
            .clip(Radius.chip)
            .background(if (active) c.primary.copy(alpha = if (c.isDark) 0.24f else 0.12f) else c.surface2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            t.provider(provider).take(1),
            style = Sadora.type.h3.copy(fontWeight = FontWeight.Bold),
            color = if (active) c.textAccent else c.muted2,
        )
    }
}
