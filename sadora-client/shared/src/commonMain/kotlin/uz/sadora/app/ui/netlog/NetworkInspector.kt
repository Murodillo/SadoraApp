package uz.sadora.app.ui.netlog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import uz.sadora.app.data.netlog.NetworkCall
import uz.sadora.app.data.netlog.NetworkLog
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.ui.components.SystemBackHandler
import kotlin.math.roundToInt
import kotlin.time.Instant

/*
 * The network inspector — Chucker's idea, in Compose, for both platforms. Only staging and
 * development builds have a [NetworkLog]; with none, [NetworkInspectorHost] is just its
 * content. The screens are a tool for the team, so they are in English and drawn plainly
 * from the theme's tokens.
 */

/**
 * [content], with a small draggable button above it that opens the inspector. The button
 * can be hidden from the inspector until the app is restarted.
 */
@Composable
fun NetworkInspectorHost(log: NetworkLog?, content: @Composable () -> Unit) {
    if (log == null) {
        content()
        return
    }
    var open by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (!hidden && !open) InspectorButton(log) { open = true }
        if (open) {
            NetworkInspectorScreen(
                log = log,
                onClose = { open = false },
                onHideButton = { hidden = true; open = false },
            )
        }
    }
}

@Composable
private fun InspectorButton(log: NetworkLog, onOpen: () -> Unit) {
    val c = Sadora.colors
    val calls by log.calls.collectAsState()
    val failed = calls.firstOrNull()?.let { it.error != null || (it.status ?: 0) >= 500 } == true
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
        val density = LocalDensity.current
        val size = 44.dp
        val maxX = with(density) { (maxWidth - size).toPx() }
        val maxY = with(density) { (maxHeight - size).toPx() }
        var x by remember { mutableStateOf(maxX) }
        var y by remember { mutableStateOf(maxY * 0.62f) }
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .size(size)
                .clip(Radius.chip)
                .background(if (failed) c.danger else c.text.copy(alpha = 0.78f))
                .pointerInput(maxX, maxY) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        x = (x + drag.x).coerceIn(0f, maxX)
                        y = (y + drag.y).coerceIn(0f, maxY)
                    }
                }
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (calls.size > 99) "99+" else "${calls.size}",
                style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold),
                color = c.bg,
            )
        }
    }
}

@Composable
private fun NetworkInspectorScreen(log: NetworkLog, onClose: () -> Unit, onHideButton: () -> Unit) {
    val c = Sadora.colors
    var selected by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    val calls by log.calls.collectAsState()
    val current = selected?.let { id -> calls.firstOrNull { it.id == id } }

    SystemBackHandler { if (selected != null) selected = null else onClose() }
    Column(Modifier.fillMaxSize().background(c.bg).safeDrawingPadding()) {
        if (current == null) {
            TopBar(
                title = "Network · ${calls.size}",
                onBack = onClose,
                actions = listOf("Clear" to log::clear, "Hide button" to onHideButton),
            )
            SearchField(query) { query = it }
            val shown = calls.filter { query.isBlank() || query.trim().lowercase() in "${it.method} ${it.url} ${it.status ?: ""}".lowercase() }
            if (shown.isEmpty()) {
                Text(
                    text = if (calls.isEmpty()) "No requests yet." else "Nothing matches.",
                    style = Sadora.type.body,
                    color = c.muted,
                    modifier = Modifier.padding(Spacing.screen),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { call -> CallRow(call) { selected = call.id } }
            }
        } else {
            CallDetail(current) { selected = null }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit, actions: List<Pair<String, () -> Unit>>) {
    val c = Sadora.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "‹ Back",
            style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = c.textAccent,
            modifier = Modifier.clip(Radius.chip).clickable(onClick = onBack).padding(Spacing.xs),
        )
        Text(
            text = title,
            style = Sadora.type.body.copy(fontWeight = FontWeight.Bold),
            color = c.text,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions.forEach { (label, action) ->
            Text(
                text = label,
                style = Sadora.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = c.textAccent,
                modifier = Modifier.clip(Radius.chip).clickable(onClick = action).padding(Spacing.xs),
            )
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Sadora.colors
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xxs)
            .clip(Radius.field)
            .border(1.dp, c.line, Radius.field)
            .background(c.surface)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        if (value.isEmpty()) Text("Filter by path, method or status", style = Sadora.type.body, color = c.muted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Sadora.type.body.copy(color = c.text),
            cursorBrush = SolidColor(c.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CallRow(call: NetworkCall, onClick: () -> Unit) {
    val c = Sadora.colors
    val (path, host) = splitUrl(call.url)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(call)
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${call.method} $path",
                style = Sadora.type.body.copy(fontFamily = FontFamily.Monospace),
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(clock(call.startedAt), host, call.durationMs?.let { "$it ms" }, call.error).joinToString(" · "),
                style = Sadora.type.caption,
                color = if (call.error != null) c.dangerText else c.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(start = Spacing.md).height(1.dp).background(c.line))
}

@Composable
private fun StatusBadge(call: NetworkCall) {
    val c = Sadora.colors
    val status = call.status ?: 0
    val (label, color) = when {
        call.error != null -> "ERR" to c.danger
        call.inFlight -> "…" to c.muted
        status >= 500 -> "$status" to c.danger
        status >= 400 -> "$status" to c.warning
        status >= 300 -> "$status" to c.accent
        else -> "$status" to c.success
    }
    Box(
        Modifier.width(48.dp).clip(RoundedCornerShape(Radius.sm)).background(color.copy(alpha = 0.16f)).padding(vertical = Spacing.xxs),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace), color = color)
    }
}

@Suppress("DEPRECATION") // the suspending Clipboard needs a platform ClipEntry; this one is common code
@Composable
private fun CallDetail(call: NetworkCall, onBack: () -> Unit) {
    val c = Sadora.colors
    val clipboard = LocalClipboardManager.current
    var copied by remember(call.id) { mutableStateOf<String?>(null) }
    fun copy(what: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        copied = what
    }
    TopBar(
        title = copied?.let { "Copied $it" } ?: "${call.method} ${call.status ?: call.error ?: "…"}",
        onBack = onBack,
        actions = listOf(
            "cURL" to { copy("cURL", curl(call)) },
            "Copy all" to { copy("call", fullText(call)) },
        ),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Section("Overview") {
            Mono(
                listOfNotNull(
                    "${call.method} ${call.url}",
                    "Status: ${call.status ?: "—"}",
                    call.error?.let { "Error: $it" },
                    "Started: ${clock(call.startedAt)}",
                    "Duration: ${call.durationMs?.let { "$it ms" } ?: "in flight"}",
                ).joinToString("\n"),
            )
        }
        Section("Request headers") { Mono(headerText(call.requestHeaders).ifEmpty { "—" }) }
        Section("Request body", onCopy = call.requestBody?.let { { copy("request body", it) } }) { Mono(call.requestBody ?: "—") }
        Section("Response headers") { Mono(headerText(call.responseHeaders).ifEmpty { "—" }) }
        Section("Response body", onCopy = call.responseBody?.let { { copy("response body", it) } }) { Mono(call.responseBody ?: "—") }
        Text(
            text = "Tokens, cookies and sign-in codes are masked. Nothing here is saved to the phone.",
            style = Sadora.type.caption,
            color = c.muted,
        )
        Spacer(Modifier.height(Spacing.lg))
    }
}

@Composable
private fun Section(title: String, onCopy: (() -> Unit)? = null, body: @Composable () -> Unit) {
    val c = Sadora.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.uppercase(), style = Sadora.type.caption.copy(fontWeight = FontWeight.Bold), color = c.muted, modifier = Modifier.weight(1f))
            if (onCopy != null) {
                Text(
                    "Copy",
                    style = Sadora.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textAccent,
                    modifier = Modifier.clip(Radius.chip).clickable(onClick = onCopy).padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(Radius.cardSmall)
                .background(c.surface)
                .border(1.dp, c.line, Radius.cardSmall)
                .horizontalScroll(rememberScrollState())
                .padding(Spacing.sm),
        ) { body() }
    }
}

@Composable
private fun Mono(text: String) {
    Text(text, style = Sadora.type.caption.copy(fontFamily = FontFamily.Monospace), color = Sadora.colors.text)
}

private fun headerText(headers: List<Pair<String, String>>) = headers.joinToString("\n") { (k, v) -> "$k: $v" }

private fun splitUrl(url: String): Pair<String, String> {
    val rest = url.substringAfter("://", url)
    val host = rest.substringBefore('/')
    val path = "/" + rest.substringAfter('/', "")
    return path to host
}

private fun clock(epochMs: Long): String {
    val t = Instant.fromEpochMilliseconds(epochMs).toString() // 2026-10-21T12:03:04.567Z
    return t.substringAfter('T').take(12) + " UTC"
}

/** The call as a command a developer can paste into a terminal; masked values stay masked. */
internal fun curl(call: NetworkCall): String = buildString {
    append("curl -X ").append(call.method).append(" '").append(call.url.replace("'", "'\\''")).append("'")
    call.requestHeaders.forEach { (k, v) -> append(" \\\n  -H '").append("$k: $v".replace("'", "'\\''")).append("'") }
    call.requestBody?.takeIf { !it.startsWith("<") }?.let { append(" \\\n  --data '").append(it.replace("'", "'\\''")).append("'") }
}

private fun fullText(call: NetworkCall): String = buildString {
    appendLine("${call.method} ${call.url}")
    appendLine("Status: ${call.status ?: "—"}${call.error?.let { " · $it" } ?: ""} · ${call.durationMs ?: "?"} ms · ${clock(call.startedAt)}")
    appendLine()
    appendLine("> Request headers"); appendLine(headerText(call.requestHeaders))
    call.requestBody?.let { appendLine(); appendLine("> Request body"); appendLine(it) }
    appendLine()
    appendLine("< Response headers"); appendLine(headerText(call.responseHeaders))
    call.responseBody?.let { appendLine(); appendLine("< Response body"); appendLine(it) }
}

