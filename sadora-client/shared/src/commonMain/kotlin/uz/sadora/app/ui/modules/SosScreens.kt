package uz.sadora.app.ui.modules

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.sadora.app.data.SosController
import uz.sadora.app.data.readable
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.CardLabel
import uz.sadora.app.ui.components.DisclaimerNote
import uz.sadora.app.ui.components.ErrorStrip
import uz.sadora.app.ui.components.GeoPoint
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTextField
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.SystemBackHandler
import uz.sadora.app.ui.components.noRippleClickable
import uz.sadora.app.ui.components.rememberOneShotLocation
import uz.sadora.app.ui.components.rememberPulse
import uz.sadora.app.ui.components.Pulse
import uz.sadora.contract.Limits
import uz.sadora.contract.SosAlertRequest
import uz.sadora.contract.SosAlertResult
import uz.sadora.contract.SosContact

private val Red = Color(0xFFD7263D)

/**
 * SOS. Calling 103 is the first and biggest thing on the screen, and it never waits for the
 * server. The alert counts down five seconds she can cancel, looks for her position while it
 * counts, and goes with or without one.
 */
@Composable
fun SosScreen(
    sos: SosController,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.sos
    val c = Sadora.colors
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val locate = rememberOneShotLocation()
    val pulse = rememberPulse()
    LaunchedEffect(Unit) { sos.refresh() }
    val state = sos.state
    var counting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SosAlertResult?>(null) }
    var sendFailed by remember { mutableStateOf(false) }

    val recipients = buildList {
        state?.partnerName?.let { add("$it (${t.partnerTag})") }
        state?.contacts?.forEach { add(it.name) }
    }

    Box(modifier.fillMaxSize().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            SadoraTopBar(t.title, onBack = onClose)
            ScreenContent {
                item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        Text(t.emergencyTitle, style = Sadora.type.h3, color = c.text)
                        BigRedButton(t.call103) { uri.openUri("tel:103") }
                        SadoraButton(t.call112, onClick = { uri.openUri("tel:112") }, tone = ButtonTone.Secondary)
                    }
                }
                // Closed from the admin panel: the calls stay, the alert and the contacts go.
                if (state?.enabled != false) item {
                    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
                        Text(t.alertTitle, style = Sadora.type.h3, color = c.text)
                        Text(t.alertBody, style = Sadora.type.body, color = c.muted)
                        Text(if (recipients.isEmpty()) t.noRecipients else t.recipients(recipients), style = Sadora.type.body, color = c.text)
                        SadoraButton(t.send, onClick = { counting = true }, enabled = recipients.isNotEmpty())
                    }
                }
                sos.error?.let { failure -> item { ErrorStrip(failure.readable(), onRetry = sos::clearError) } }
                if (state?.enabled != false) item { ContactsCard(sos, state?.contacts.orEmpty(), onToast) }
                item { DisclaimerNote(t.disclaimer) }
            }
        }

        if (counting) {
            Countdown(
                onCancel = { counting = false },
                onDone = { point ->
                    counting = false
                    scope.launch {
                        val sent = sos.alert(SosAlertRequest(point?.latitude, point?.longitude, point?.accuracyMeters))
                        if (sent == null || sent.smsSent == 0 && !sent.partnerNotified) sendFailed = true else result = sent
                    }
                },
                locate = locate,
                tick = { pulse(Pulse.Squeeze) },
            )
        }

        SadoraBottomSheet(visible = result != null || sendFailed, title = if (sendFailed) t.failedAll else t.sentTitle, onDismiss = {
            result = null
            sendFailed = false
        }) {
            result?.let { Text(t.sentBody(it.smsSent, it.partnerNotified, it.withLocation), style = Sadora.type.body, color = c.text) }
            BigRedButton(t.call103) { uri.openUri("tel:103") }
        }
    }
}

@Composable
private fun BigRedButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Red)
            .noRippleClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, style = Sadora.type.h3.copy(fontWeight = FontWeight.Bold))
    }
}

/** Five, four, three…: a buzz each second, the position looked for meanwhile, "Bekor qilish" always in reach. */
@Composable
private fun Countdown(
    onCancel: () -> Unit,
    onDone: (GeoPoint?) -> Unit,
    locate: ((GeoPoint?) -> Unit) -> Unit,
    tick: () -> Unit,
) {
    val t = strings.sos
    var left by remember { mutableIntStateOf(COUNTDOWN) }
    var point by remember { mutableStateOf<GeoPoint?>(null) }
    var located by remember { mutableStateOf(false) }
    SystemBackHandler(onBack = onCancel)
    LaunchedEffect(Unit) {
        locate { found ->
            point = found
            located = true
        }
        while (left > 0) {
            tick()
            delay(1000)
            left -= 1
        }
        // A last few seconds for a fix that is nearly there; the alert never waits longer.
        repeat(EXTRA_WAIT) { if (!located) delay(1000) }
        onDone(point)
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).noRippleClickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(t.countdownTitle, color = Color.White, style = Sadora.type.h2)
            Box(Modifier.size(180.dp).clip(CircleShape).background(Red), contentAlignment = Alignment.Center) {
                Text(
                    if (left > 0) left.toString() else "…",
                    color = Color.White,
                    style = Sadora.type.h1.copy(fontSize = 72.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            if (!located) Text(t.locating, color = Color.White.copy(alpha = 0.8f), style = Sadora.type.body)
            Spacer(Modifier.height(Spacing.sm))
            Box(
                Modifier
                    .padding(horizontal = Spacing.xl)
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .noRippleClickable(role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(t.cancel, color = Red, style = Sadora.type.h3.copy(fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
            }
        }
    }
}

/** Up to three people, each a name and a number; ✕ takes one off. */
@Composable
private fun ContactsCard(sos: SosController, contacts: List<SosContact>, onToast: (String) -> Unit) {
    val t = strings.sos
    val c = Sadora.colors
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    SadoraCard(padding = Spacing.sm, verticalGap = Spacing.xs) {
        CardLabel(t.contactsTitle)
        contacts.forEach { contact ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(contact.name, style = Sadora.type.body.copy(fontWeight = FontWeight.SemiBold), color = c.text)
                    Text(contact.phone, style = Sadora.type.body, color = c.muted)
                }
                Text(
                    "✕",
                    style = Sadora.type.h3,
                    color = c.muted,
                    modifier = Modifier.padding(Spacing.xs).noRippleClickable(role = Role.Button, onClick = {
                        scope.launch { if (sos.saveContacts(contacts - contact)) onToast(t.saved) }
                    }),
                )
            }
        }
        if (contacts.size < Limits.SOS_MAX_CONTACTS) {
            SadoraTextField(name, { name = it.take(40) }, label = t.name)
            SadoraTextField(phone, { phone = it }, label = t.phone, placeholder = "+998 90 123 45 67", keyboardType = KeyboardType.Phone)
            SadoraButton(
                t.add,
                enabled = name.isNotBlank() && phone.count(Char::isDigit) >= 9 && !sos.busy,
                tone = ButtonTone.Secondary,
                onClick = {
                    scope.launch {
                        if (sos.saveContacts(contacts + SosContact(name.trim(), phone.trim()))) {
                            name = ""
                            phone = ""
                            onToast(t.saved)
                        }
                    }
                },
            )
        }
        Text(t.contactsNote, style = Sadora.type.caption.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified), color = c.muted)
    }
}

private const val COUNTDOWN = 5
private const val EXTRA_WAIT = 5
