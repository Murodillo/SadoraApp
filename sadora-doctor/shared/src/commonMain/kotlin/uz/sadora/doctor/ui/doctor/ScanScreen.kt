package uz.sadora.doctor.ui.doctor

import uz.sadora.doctor.resources.Res
import uz.sadora.doctor.resources.ic3d_qr
import uz.sadora.doctor.ui.components.ArtIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.time.Clock
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.RecentPatient
import uz.sadora.doctor.data.patientTokenOf
import uz.sadora.doctor.design.IconSize
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.CameraAccess
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.QrScanner
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.SectionHeader
import uz.sadora.doctor.ui.components.rememberOpenAppSettings

/**
 * The third tab: the camera, pointed at the QR code a patient shows from her Sadora app.
 * A code that is hers opens her record; anything else says so and keeps looking. Pasting
 * the link does the same, for a link sent in a message or a phone with no camera.
 */
@Composable
fun ScanScreen(
    doctors: DoctorController,
    onOpenPatient: (String) -> Unit,
    onToast: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.tabs
    val c = Sadora.colors
    var access by remember { mutableStateOf(CameraAccess.Starting) }
    var link by remember { mutableStateOf("") }
    val openSettings = rememberOpenAppSettings()

    fun open(text: String): Boolean {
        val token = patientTokenOf(text)
        if (token == null) onToast(t.notPatientCode) else onOpenPatient(token)
        return token != null
    }

    // Back from her record with the patient still holding up the code, the camera would
    // read it again at once and push the record straight back. The code opened last from
    // the camera is ignored by the camera; the list below and the link field still open it.
    fun scanned(text: String) {
        val token = patientTokenOf(text)
        if (token != null && token == doctors.lastScannedToken) return
        if (open(text)) doctors.lastScannedToken = token
    }

    Column(modifier) {
        SadoraTopBar(t.scanTitle)
        ScreenContent(stagger = false) {
            item(key = "camera") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(Radius.card)
                        .background(c.surface2),
                    contentAlignment = Alignment.Center,
                ) {
                    QrScanner(
                        onAccess = { access = it },
                        onScanned = ::scanned,
                        modifier = Modifier.fillMaxSize(),
                    )
                    when (access) {
                        CameraAccess.Live -> Viewfinder()
                        CameraAccess.Starting -> CameraNote(t.cameraStarting)
                        CameraAccess.Denied -> CameraNote(t.cameraDenied) {
                            PillButton(t.openSettings, onClick = openSettings, tone = ButtonTone.Primary)
                        }
                        CameraAccess.Missing -> CameraNote(t.cameraMissing)
                    }
                }
            }
            item(key = "hint") { Text(t.scanHint, style = Sadora.type.body, color = c.muted) }
            item(key = "paste") {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SadoraTextField(
                        value = link,
                        onValueChange = { link = it.trim() },
                        label = t.pasteLabel,
                        placeholder = "https://…/share/…",
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                        keyboardActions = KeyboardActions(onGo = { if (open(link)) link = "" }),
                        modifier = Modifier.weight(1f),
                    )
                    PillButton(
                        t.open,
                        onClick = { if (open(link)) link = "" },
                        tone = ButtonTone.Primary,
                        enabled = link.isNotBlank(),
                        modifier = Modifier.padding(bottom = Spacing.xxs),
                    )
                }
            }
            val recent = doctors.recentPatients
            if (recent.isNotEmpty()) {
                item(key = "recent-title") { SectionHeader(t.recentTitle) }
                items(recent.size, key = { recent[it].token }) { index ->
                    RecentPatientRow(recent[index], onClick = { onOpenPatient(recent[index].token) })
                }
                item(key = "recent-note") {
                    Text(t.recentNote, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
                }
            }
        }
    }
}

/** Four corners around the middle of the preview: where to hold the code. */
@Composable
private fun Viewfinder() {
    Box(
        Modifier
            .fillMaxSize(0.62f)
            .border(3.dp, Sadora.colors.onPrimary.copy(alpha = 0.9f), Radius.card),
    )
}

@Composable
private fun CameraNote(text: String, action: (@Composable () -> Unit)? = null) {
    val c = Sadora.colors
    Column(
        Modifier.padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        ArtIcon(Res.drawable.ic3d_qr, 72.dp)
        Text(text, style = Sadora.type.body, color = c.muted, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
private fun RecentPatientRow(patient: RecentPatient, onClick: () -> Unit) {
    val c = Sadora.colors
    val t = strings.tabs
    SadoraCard(onClick = onClick, padding = Spacing.sm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AliasAvatar(patient.name, tint = patient.name.hashCode(), size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(patient.name, style = Sadora.type.h3, color = c.text, maxLines = 1)
                Text(
                    listOfNotNull(patient.age?.let(t::age), strings.dates.ago(patient.openedAt, Clock.System.now())).joinToString(" · "),
                    style = Sadora.type.body,
                    color = c.muted2,
                )
            }
            Icon(SadoraIcons.ChevronRight, contentDescription = null, Modifier.size(IconSize.md), tint = c.muted2)
        }
    }
}
