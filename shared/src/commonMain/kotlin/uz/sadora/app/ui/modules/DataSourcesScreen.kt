package uz.sadora.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlin.time.Clock
import kotlinx.coroutines.launch
import uz.sadora.app.data.HealthController
import uz.sadora.app.data.WearableController
import uz.sadora.app.data.health.DeviceSyncOutcome
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.BadgeTone
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.ProviderLogo
import uz.sadora.app.ui.components.SadoraBadge
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.rememberHealthAccessRequest
import uz.sadora.contract.ConnectionStatus
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.ProviderInfo
import uz.sadora.contract.ProviderKind
import uz.sadora.contract.ProviderUnavailable

/**
 * "Qurilmalar" — every provider as a tile: its logo and its name, nothing else.
 *
 * Hers come first, marked with a dot, then what can be connected, then what is planned,
 * greyed. A provider this phone can never read (Apple Health on Android) is left out.
 * Tapping an open tile connects it; tapping one of hers opens a sheet with sync and
 * disconnect. Connecting a cloud provider leaves for the browser and comes back through
 * a link, and the return says whether it worked.
 */
@Composable
fun DataSourcesScreen(
    wearables: WearableController,
    health: HealthController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
    onErrorToast: (String) -> Unit = onToast,
) {
    val t = strings.devices
    // Read here, not inside the coroutines below: the language lives in composition.
    val errors = strings.errors
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var confirmDisconnect by remember { mutableStateOf<HealthProvider?>(null) }
    var opened by remember { mutableStateOf<HealthProvider?>(null) }

    LaunchedEffect(Unit) {
        wearables.load()
        health.loadSources()
    }

    // Back from Health Connect's own screen or from the Play Store: the phone's side may
    // have changed while the app was behind them.
    LifecycleResumeEffect(Unit) {
        val job = scope.launch { wearables.refreshDevice() }
        onPauseOrDispose { job.cancel() }
    }

    // What a sync of the phone's store changed, carried on to the screens that show it.
    val afterDeviceSync: suspend (DeviceSyncOutcome) -> Unit = { outcome ->
        if (outcome.changedMetrics) health.refreshWearables()
        health.loadSources()
        if (outcome.periodsAdded > 0) {
            health.refreshCycle()
            onToast(t.periodsImported(outcome.periodsAdded))
        }
    }

    val requestDeviceAccess = rememberHealthAccessRequest(wearables.devicePlatform) { granted ->
        scope.launch {
            val outcome = wearables.onDeviceAccess(granted)
            val failure = outcome?.failure
            when {
                !granted -> onErrorToast(t.accessDenied)
                failure != null -> onErrorToast(failure.readable(errors))
                else -> {
                    wearables.devicePlatform.provider?.let { onToast(t.deviceConnected(t.provider(it))) }
                    outcome?.let { afterDeviceSync(it) }
                }
            }
        }
    }

    // The return from the provider's page, once: a toast, then the list reloads itself.
    LaunchedEffect(wearables.returned) {
        when (wearables.returned) {
            true -> onToast(t.returnedOk)
            false -> onErrorToast(t.returnedError)
            null -> Unit
        }
        if (wearables.returned != null) {
            wearables.clearReturned()
            health.refreshWearables()
        }
    }

    fun connect(info: ProviderInfo) {
        when {
            info.kind == ProviderKind.ON_DEVICE && wearables.deviceNeedsInstall -> wearables.devicePlatform.openStore()
            info.kind == ProviderKind.ON_DEVICE -> requestDeviceAccess()
            else -> scope.launch { wearables.startConnect(info.provider)?.let(uriHandler::openUri) }
        }
    }

    // A provider no phone of this kind can read is not an option, so it is not drawn.
    val tiles = (wearables.connected + wearables.available + wearables.planned).filterNot {
        it.unavailableReason == ProviderUnavailable.IOS_ONLY || it.unavailableReason == ProviderUnavailable.ANDROID_ONLY
    }

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)

        ScreenContent {
            wearables.error?.let { failure ->
                item { ErrorStrip(failure.readable(), onRetry = wearables::clearError) }
            }

            tiles.chunked(2).forEach { pair ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        pair.forEach { info ->
                            ProviderTile(
                                info = info,
                                connecting = wearables.connectStarted == info.provider || wearables.deviceSyncing && info.kind == ProviderKind.ON_DEVICE,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when {
                                        info.connection != null -> opened = info.provider
                                        info.available -> if (!wearables.busy) connect(info)
                                        else -> onToast(t.unavailable(info.unavailableReason.orEmpty()))
                                    }
                                },
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }

    val sheetInfo = opened?.let { p -> wearables.connected.firstOrNull { it.provider == p } }
    SadoraBottomSheet(
        visible = sheetInfo != null,
        title = sheetInfo?.let { t.provider(it.provider) }.orEmpty(),
        onDismiss = { opened = null },
    ) {
        val info = sheetInfo ?: return@SadoraBottomSheet
        ConnectedActions(
            info = info,
            busy = wearables.busy || wearables.deviceSyncing,
            onSync = {
                scope.launch {
                    if (info.kind == ProviderKind.ON_DEVICE) {
                        val outcome = wearables.syncDevice(force = true)
                        val failure = outcome?.failure
                        if (failure != null) {
                            onErrorToast(failure.readable(errors))
                        } else {
                            onToast(t.synced)
                            outcome?.let { afterDeviceSync(it) }
                        }
                    } else {
                        wearables.syncNow(info.provider)?.let { result ->
                            onToast(t.synced)
                            if (result.accepted + result.updated > 0) health.refreshWearables()
                        }
                    }
                }
            },
            onReconnect = {
                opened = null
                connect(info)
            },
            onDisconnect = {
                opened = null
                confirmDisconnect = info.provider
            },
        )
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

/** Logo and name. A dot in the corner for one of hers: green when it reads, amber when it needs her. */
@Composable
private fun ProviderTile(
    info: ProviderInfo,
    connecting: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val t = strings.devices
    val c = Sadora.colors
    val status = info.connection?.status
    Box(modifier) {
        SadoraCard(
            modifier = Modifier.alpha(if (info.available || status != null) 1f else 0.45f),
            onClick = onClick,
            verticalGap = Spacing.xs,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ProviderLogo(info.provider, size = 56.dp)
                Text(
                    if (connecting) t.connecting else t.provider(info.provider),
                    style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = c.text,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (status != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (status == ConnectionStatus.ACTIVE) c.success else c.warning),
            )
        }
    }
}

/** The sheet behind one of hers: status, last sync, and the two actions. */
@Composable
private fun ConnectedActions(
    info: ProviderInfo,
    busy: Boolean,
    onSync: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val t = strings.devices
    val c = Sadora.colors
    val dates = strings.dates
    val connection = info.connection ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ProviderLogo(info.provider, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(
                connection.lastSyncAt?.let { t.lastSync(dates.ago(it, Clock.System.now())) } ?: t.neverSynced,
                style = Sadora.type.body,
                color = c.muted,
            )
        }
        when (connection.status) {
            ConnectionStatus.ACTIVE -> SadoraBadge(t.statusActive, BadgeTone.Connected)
            ConnectionStatus.EXPIRED -> SadoraBadge(t.statusExpired, BadgeTone.Neutral)
            ConnectionStatus.ERROR -> SadoraBadge(t.statusError, BadgeTone.Neutral)
        }
    }
    if (info.provider == HealthProvider.APPLE_HEALTH) {
        Text(t.appleHealthManage, style = Sadora.type.caption, color = c.muted)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // Off while a request is running: the label said "syncing" but the pill still
        // fired, so every extra tap sent another sync or disconnect.
        if (connection.status == ConnectionStatus.EXPIRED) {
            PillButton(t.reconnect, onReconnect, tone = ButtonTone.Primary, modifier = Modifier.weight(1f), enabled = !busy)
        } else {
            PillButton(if (busy) t.syncing else t.syncNow, onSync, tone = ButtonTone.Primary, modifier = Modifier.weight(1f), enabled = !busy)
        }
        PillButton(t.disconnect, onDisconnect, modifier = Modifier.weight(1f), enabled = !busy)
    }
}
