package uz.sadora.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import uz.sadora.app.data.SadoraGraph
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.contract.AppStatus
import uz.sadora.contract.AppUpdate

/**
 * Asks the server, on every start and every return to the app, whether this build may run
 * and whether the service is open — before sign-in, so an outdated app on the sign-in screen
 * is stopped too.
 *
 * Fails open: no answer (offline, an older server) means the app carries on as it is. Only a
 * clear "required" or "maintenance" replaces the app, and the app's own state lives above
 * this gate, so it is all still there when the gate opens again.
 */
@Composable
fun AppStatusGate(graph: SadoraGraph?, content: @Composable () -> Unit) {
    if (graph == null) {
        content()
        return
    }
    var status by remember { mutableStateOf<AppStatus?>(null) }
    var suggestionSeen by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun check() {
        checking = true
        graph.api.appStatus(graph.device.platform, graph.appBuild).valueOrNull?.let { status = it }
        checking = false
    }

    LifecycleResumeEffect(graph) {
        val job = scope.launch { check() }
        onPauseOrDispose { job.cancel() }
    }

    val current = status
    when {
        current?.update == AppUpdate.REQUIRED -> UpdateRequired(current.storeUrl)
        current?.maintenance == true -> Maintenance(current.message, checking) { scope.launch { check() } }
        else -> {
            content()
            val t = strings.appStatus
            val uri = LocalUriHandler.current
            val url = current?.storeUrl
            SadoraDialog(
                visible = current?.update == AppUpdate.SUGGESTED && !suggestionSeen && url != null,
                title = t.suggestedTitle,
                body = t.suggestedBody,
                confirmText = t.updateButton,
                cancelText = t.later,
                destructive = false,
                onConfirm = {
                    suggestionSeen = true
                    url?.let { runCatching { uri.openUri(it) } }
                },
                onDismiss = { suggestionSeen = true },
            )
        }
    }
}

@Composable
private fun UpdateRequired(storeUrl: String?) {
    val t = strings.appStatus
    val uri = LocalUriHandler.current
    Blocker(Res.drawable.ic3d_bell, t.updateTitle, t.updateBody) {
        if (storeUrl != null) {
            SadoraButton(t.updateButton, onClick = { runCatching { uri.openUri(storeUrl) } })
        } else {
            Text(t.updateNoStore, style = Sadora.type.body, color = Sadora.colors.muted, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Maintenance(message: String?, checking: Boolean, onRetry: () -> Unit) {
    val t = strings.appStatus
    Blocker(Res.drawable.ic3d_shield, t.maintenanceTitle, message ?: t.maintenanceBody) {
        SadoraButton(t.retry, onClick = onRetry, tone = ButtonTone.Secondary, enabled = !checking)
    }
}

/** The whole screen, the app nowhere behind it: an art tile, a title, a line, one action. */
@Composable
private fun Blocker(art: DrawableResource, title: String, body: String, action: @Composable () -> Unit) {
    val c = Sadora.colors
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(horizontal = Spacing.screen, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ArtTile(art, size = 72.dp, artSize = 54.dp)
            Text(title, style = Sadora.type.h1, color = c.text, textAlign = TextAlign.Center)
            Text(body, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
            action()
        }
    }
}
