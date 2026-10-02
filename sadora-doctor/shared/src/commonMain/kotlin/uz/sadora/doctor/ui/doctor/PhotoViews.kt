package uz.sadora.doctor.ui.doctor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.contract.DoctorAccount
import uz.sadora.contract.DoctorStatus
import uz.sadora.doctor.data.CapturedPhotoData
import uz.sadora.doctor.data.DoctorController
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.IconTile
import uz.sadora.doctor.ui.components.PhotoCapture
import uz.sadora.doctor.ui.components.PillButton
import uz.sadora.doctor.ui.components.SadoraBottomSheet
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.rememberPhotoCapture

// Her profile photo: the picker and its upload, the card that asks for one, the card on
// her Profile tab that changes or removes it, and the one-time sheet after sign-in.

/** The picker, bound to an upload, and whether that upload is still going. */
internal class ProfilePhotoPicker(val capture: PhotoCapture, val uploading: Boolean) {
    val canPick: Boolean get() = capture.available && !uploading
}

/**
 * The platform picker wired to [DoctorController.setPhoto]: a photo she picks or takes
 * goes up at once — the server crops it to a square — and [onSaved] runs once it is up.
 * A refusal (not an image, too small) stays in `doctors.photoCalls` for the card to show.
 */
@Composable
internal fun rememberProfilePhotoPicker(doctors: DoctorController, onSaved: () -> Unit): ProfilePhotoPicker {
    val scope = rememberCoroutineScope()
    val saved by rememberUpdatedState(onSaved)
    var uploading by remember { mutableStateOf(false) }
    val capture = rememberPhotoCapture { photo ->
        uploading = true
        scope.launch {
            val ok = doctors.setPhoto(CapturedPhotoData(photo.base64, photo.mimeType))
            uploading = false
            if (ok) saved()
        }
    }
    return ProfilePhotoPicker(capture, uploading)
}

/** What makes a good photo, and that staff may take one down. Under every picker. */
@Composable
internal fun PhotoGuidance(modifier: Modifier = Modifier) {
    Text(
        strings.photo.guidance,
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
        color = Sadora.colors.muted2,
        modifier = modifier,
    )
}

/**
 * The picker's buttons: the gallery first, the camera beside it where there is one.
 * [primaryText] is "Rasm tanlash" or "Rasmni o'zgartirish".
 */
@Composable
private fun PickButtons(picker: ProfilePhotoPicker, primaryText: String, primaryTone: ButtonTone = ButtonTone.Primary) {
    val p = strings.photo
    if (!picker.capture.available) {
        Text(strings.doctors.galleryUnavailable, style = Sadora.type.body, color = Sadora.colors.muted)
        return
    }
    SadoraButton(
        if (picker.uploading) p.uploading else primaryText,
        onClick = picker.capture::pickFromGallery,
        tone = primaryTone,
        enabled = picker.canPick,
        icon = SadoraIcons.Camera,
    )
    if (picker.capture.cameraAvailable) {
        SadoraButton(
            p.takePhoto,
            onClick = picker.capture::takePhoto,
            tone = ButtonTone.Outline,
            enabled = picker.canPick,
        )
    }
}

/**
 * "Rasmingizni qo'ying": the top of Home while she has no photo, and the pending panel.
 * The button opens the gallery straight away.
 */
@Composable
internal fun AskForPhotoCard(
    account: DoctorAccount,
    doctors: DoctorController,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = strings.photo
    val c = Sadora.colors
    val picker = rememberProfilePhotoPicker(doctors, onSaved)
    SadoraCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            val name = account.fullName
            if (name != null) {
                DoctorAvatar(name, size = 52.dp, verified = account.status == DoctorStatus.APPROVED)
            } else {
                IconTile(SadoraIcons.Camera, tint = c.primary, size = 52.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(p.askTitle, style = Sadora.type.h3, color = c.text)
                Text(p.askBody, style = Sadora.type.body, color = c.muted)
            }
        }
        PhotoGuidance()
        doctors.photoCalls.error?.let { ErrorStrip(it.readable()) }
        PickButtons(picker, p.choose)
    }
}

/**
 * Her photo on her own Profile tab: change it, take a new one, or take it down. The
 * photo itself is in the header above; this card is what she can do with it.
 */
@Composable
internal fun MyPhotoCard(
    account: DoctorAccount,
    doctors: DoctorController,
    onSaved: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = strings.photo
    val c = Sadora.colors
    val picker = rememberProfilePhotoPicker(doctors, onSaved)
    val hasPhoto = account.photoUrl != null
    SadoraCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(p.sectionTitle, style = Sadora.type.h3, color = c.text, modifier = Modifier.weight(1f))
            if (hasPhoto) {
                PillButton(p.remove, onClick = onRemove, enabled = !picker.uploading && !doctors.photoCalls.busy)
            }
        }
        if (!hasPhoto) Text(p.askBody, style = Sadora.type.body, color = c.muted)
        PhotoGuidance()
        doctors.photoCalls.error?.let { ErrorStrip(it.readable()) }
        PickButtons(picker, if (hasPhoto) p.change else p.choose, primaryTone = if (hasPhoto) ButtonTone.Secondary else ButtonTone.Primary)
    }
}

/**
 * Once per launch, after sign-in: an approved doctor without a photo is asked in a
 * sheet. The Home card goes on asking quietly after she closes it.
 */
@Composable
internal fun PhotoNudgeSheet(
    visible: Boolean,
    account: DoctorAccount?,
    doctors: DoctorController,
    onSaved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val p = strings.photo
    val c = Sadora.colors
    val picker = rememberProfilePhotoPicker(doctors) {
        onDismiss()
        onSaved()
    }
    SadoraBottomSheet(visible = visible, title = p.askTitle, onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DoctorAvatar(account?.fullName.orEmpty(), size = 52.dp)
            Text(p.askBody, style = Sadora.type.body, color = c.text, modifier = Modifier.weight(1f))
        }
        PhotoGuidance()
        doctors.photoCalls.error?.let { ErrorStrip(it.readable()) }
        PickButtons(picker, p.choose)
        SadoraButton(p.later, onClick = onDismiss, tone = ButtonTone.Ghost, enabled = !picker.uploading)
    }
}

/**
 * The step after the application form: the application is in, and now her photo — so
 * her page has a face on it the day she is approved. [onDone] leaves either way.
 */
@Composable
internal fun AfterApplyPhotoStep(
    account: DoctorAccount?,
    doctors: DoctorController,
    onDone: () -> Unit,
) {
    val p = strings.photo
    val c = Sadora.colors
    val picker = rememberProfilePhotoPicker(doctors, onSaved = {})
    val hasPhoto = account?.photoUrl != null
    SadoraCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DoctorAvatar(
                account?.fullName.orEmpty(),
                size = 64.dp,
                photoUrl = account?.photoUrl,
                verified = account?.status == DoctorStatus.APPROVED,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(if (hasPhoto) p.saved else p.askTitle, style = Sadora.type.h3, color = c.text)
                Text(p.afterApplyBody, style = Sadora.type.body, color = c.muted)
            }
        }
        PhotoGuidance()
        doctors.photoCalls.error?.let { ErrorStrip(it.readable()) }
        if (hasPhoto) {
            SadoraButton(p.done, onClick = onDone, enabled = !picker.uploading)
            PickButtons(picker, p.change, primaryTone = ButtonTone.Secondary)
        } else {
            PickButtons(picker, p.choose)
            SadoraButton(p.later, onClick = onDone, tone = ButtonTone.Ghost, enabled = !picker.uploading)
        }
    }
}
