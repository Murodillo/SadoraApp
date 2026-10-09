package uz.sadora.app.ui.modules

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import uz.sadora.app.data.WearableController
import uz.sadora.app.data.health.HealthAvailability
import uz.sadora.app.design.IconSize
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.SadoraIcons
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraProgressBar
import uz.sadora.app.ui.components.rememberHealthAccessRequest
import uz.sadora.contract.HealthProvider
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.ArtIcon

/** Where the gate before the app stands. */
private enum class GateStep {
    /** Reading the store's state — drawn as nothing, so an open gate is never seen. */
    Checking,

    /** The ask: what is read, and the button into the store's own sheet. */
    Ask,

    /** Some types allowed, not all: the count, and the way into the store's settings. */
    Partial,

    /** Health Connect is missing or too old: the Play listing. */
    Install,

    /** Everything allowed; her history on its way up. */
    Importing,

    /** The upload stopped; try again, or go in and let the next launch finish it. */
    Failed,

    /** Nothing more to ask: the app itself. */
    Open,
}

/**
 * The page before the app: every type the phone's health store holds, asked for, and her
 * whole history brought up once.
 *
 * It stands in front of [content] for as long as a type is still refused — the choice
 * was made to make it required — and is checked again each time she comes back from the
 * store's settings. A phone with no store at all goes straight in; so does one whose
 * upload failed, when she chooses to, because the data is already allowed and the next
 * launch picks the import up again.
 */
@Composable
fun HealthGate(wearables: WearableController, content: @Composable () -> Unit) {
    val t = strings.healthGate
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(GateStep.Checking) }
    var counts by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val platform = wearables.devicePlatform
    val store = if (platform.provider == HealthProvider.APPLE_HEALTH) "Apple Health" else "Health Connect"

    suspend fun import() {
        step = GateStep.Importing
        step = if (wearables.importEverything()) GateStep.Open else GateStep.Failed
    }

    /** Reads the store again and moves to whatever it now says. */
    suspend fun recheck() {
        if (!wearables.gateNeeded()) {
            step = GateStep.Open
            return
        }
        counts = platform.grantedCount()
        step = when {
            wearables.deviceAvailability != HealthAvailability.AVAILABLE -> GateStep.Install
            wearables.deviceFullAccess -> GateStep.Importing
            wearables.deviceAccess -> GateStep.Partial
            else -> GateStep.Ask
        }
        if (step == GateStep.Importing) import()
    }

    LaunchedEffect(Unit) { recheck() }

    // Back from the store's settings or the Play Store: what she changed there counts.
    LifecycleResumeEffect(step) {
        val job = if (step == GateStep.Partial || step == GateStep.Install) scope.launch { recheck() } else null
        onPauseOrDispose { job?.cancel() }
    }

    val ask = rememberHealthAccessRequest(platform) { scope.launch { recheck() } }

    if (step == GateStep.Open) {
        content()
        return
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "health-gate",
        ) { current ->
            if (current == GateStep.Checking) {
                Box(Modifier.fillMaxSize())
                return@AnimatedContent
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(Spacing.md))
                ArtTile(Res.drawable.ic3d_health, size = 72.dp, artSize = 54.dp)
                when (current) {
                    GateStep.Install -> {
                        Heading(t.install(store), t.installBody(store))
                        SadoraButton(t.installButton, onClick = platform::openStore)
                    }
                    GateStep.Importing -> {
                        Heading(t.importing, t.importingBody)
                        val progress = wearables.deviceSyncProgress ?: 0f
                        SadoraProgressBar(progress, gradient = true, height = 10.dp)
                        Text(t.importPercent((progress * 100).toInt()), style = Sadora.type.h3, color = c.textAccent)
                    }
                    GateStep.Failed -> {
                        Heading(t.importing, t.importFailed)
                        SadoraButton(t.retry, onClick = { scope.launch { import() } })
                        SadoraButton(t.later, onClick = { step = GateStep.Open }, tone = ButtonTone.Secondary)
                    }
                    else -> {
                        Heading(t.title(store), t.body(store))
                        SadoraCard {
                            t.kinds.forEach { kind ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
                                    Icon(SadoraIcons.Check, contentDescription = null, Modifier.size(IconSize.md), tint = c.primary)
                                    Text(kind, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.Top) {
                            ArtIcon(Res.drawable.ic3d_lock, IconSize.md)
                            Text(t.privacy, style = Sadora.type.body, color = c.muted2, modifier = Modifier.weight(1f))
                        }
                        if (current == GateStep.Partial) {
                            val (granted, total) = counts ?: (0 to 0)
                            SadoraCard {
                                Text(t.partial(granted, total), style = Sadora.type.h3, color = c.dangerText)
                                Text(t.partialBody, style = Sadora.type.body, color = c.muted)
                            }
                            SadoraButton(t.openSettings, onClick = platform::openPermissionSettings)
                            // The sheet again: Health Connect shows it until the second refusal.
                            SadoraButton(t.allow, onClick = ask, tone = ButtonTone.Secondary)
                        } else {
                            SadoraButton(t.allow, onClick = ask)
                        }
                    }
                }
                Spacer(Modifier.fillMaxWidth().height(Spacing.md))
            }
        }
    }
}

@Composable
private fun Heading(title: String, body: String) {
    val c = Sadora.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(title, style = Sadora.type.h1, color = c.text, textAlign = TextAlign.Center)
        Text(body, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
    }
}
