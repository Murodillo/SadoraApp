package uz.sadora.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.app.data.TelegramController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.resources.*
import uz.sadora.app.ui.components.ArtTile
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraDialog
import uz.sadora.app.ui.components.SadoraLoader
import uz.sadora.app.ui.components.SadoraSwitch
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent

/**
 * "Telegram bot": what it does, the button that opens Telegram with a one-time code, and once
 * linked, the detail switch and a way out. After Telegram opens, the screen checks back every
 * few seconds, so coming back to the app shows it linked without a pull.
 */
@Composable
fun TelegramScreen(
    telegram: TelegramController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.telegram
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var waiting by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { telegram.refresh() }
    LaunchedEffect(waiting) {
        if (!waiting) return@LaunchedEffect
        repeat(LINK_CHECKS) {
            delay(3000)
            telegram.refresh()
            if (telegram.status?.linked == true) {
                waiting = false
                onToast(t.connectedToast)
                return@LaunchedEffect
            }
        }
        waiting = false
    }
    val status = telegram.status

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.title, onBack = onClose)
            ScreenContent {
                telegram.error?.let { failure -> item { ErrorStrip(failure.readable(), onRetry = telegram::clearError) } }
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            ArtTile(Res.drawable.ic3d_message, tint = c.primary, size = 52.dp)
                            Text(t.intro, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
                        }
                        t.features.forEach { Text(it, style = Sadora.type.body, color = c.muted) }
                    }
                }
                when {
                    status == null -> item { Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) { SadoraLoader(size = 40.dp) } }
                    !status.available -> item { Text(t.unavailable, style = Sadora.type.body, color = c.muted) }
                    !status.linked -> item {
                        SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                            if (waiting) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SadoraLoader(size = 36.dp) }
                            }
                            SadoraButton(
                                t.connect,
                                enabled = !telegram.busy,
                                onClick = {
                                    scope.launch {
                                        telegram.link()?.let { url ->
                                            uri.openUri(url)
                                            waiting = true
                                        }
                                    }
                                },
                            )
                            Text(t.connectNote, style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = c.muted)
                        }
                    }
                    else -> {
                        item {
                            SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                                Text(t.linked(status.username), style = Sadora.type.h3, color = c.text)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(t.detailedTitle, style = Sadora.type.body, color = c.text)
                                        Text(t.detailedNote, style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = c.muted)
                                    }
                                    SadoraSwitch(status.detailed, { on -> scope.launch { telegram.setDetailed(on) } })
                                }
                            }
                        }
                        item { SadoraButton(t.disconnect, onClick = { confirmUnlink = true }, tone = ButtonTone.Secondary) }
                    }
                }
            }
        }

        SadoraDialog(
            visible = confirmUnlink,
            title = t.disconnectTitle,
            body = t.disconnectBody,
            confirmText = t.disconnect,
            onConfirm = {
                confirmUnlink = false
                scope.launch { if (telegram.unlink()) onToast(t.disconnected) }
            },
            onDismiss = { confirmUnlink = false },
        )
    }
}

/** Forty checks three seconds apart: two minutes to press "Start" in Telegram. */
private const val LINK_CHECKS = 40
