package uz.sadora.doctor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import uz.sadora.contract.UzbekPhone
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.AppLanguage
import uz.sadora.doctor.i18n.stringsFor
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.ButtonTone
import uz.sadora.doctor.ui.components.ChipFlowRow
import uz.sadora.doctor.ui.components.SadoraBottomSheet
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SelectChip

/**
 * Behind the gear on the panel: the language, and the account she is signed in with.
 * That is all the doctor app keeps on the phone.
 */
@Composable
fun SettingsSheet(
    visible: Boolean,
    /** `+998901234567`, from the signed-in profile; null before it is known. */
    phone: String?,
    language: AppLanguage,
    onLanguage: (AppLanguage) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Sadora.colors
    val t = strings.settings
    SadoraBottomSheet(visible = visible, title = t.title, onDismiss = onDismiss) {
        Text(t.language.uppercase(), style = Sadora.type.caption, color = c.muted)
        LanguageOptions(current = language, onSelect = onLanguage)
        Text(t.account.uppercase(), style = Sadora.type.caption, color = c.muted)
        phone?.let {
            Text(t.signedInAs("+${UzbekPhone.COUNTRY_CODE} ${UzbekPhone.format(it)}"), style = Sadora.type.body, color = c.text)
        }
        SadoraButton(t.signOut, onClick = onSignOut, tone = ButtonTone.Destructive)
    }
}

/** Just the languages, for the sign-in screen, where there is no account yet. */
@Composable
fun LanguageSheet(
    visible: Boolean,
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    SadoraBottomSheet(visible = visible, title = strings.settings.language, onDismiss = onDismiss) {
        LanguageOptions(current = current, onSelect = onSelect)
    }
}

/** Each language named in itself, so the one she reads is findable whatever the app is in now. */
@Composable
private fun LanguageOptions(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ChipFlowRow {
            AppLanguage.entries.forEach { language ->
                SelectChip(
                    label = stringsFor(language).languageName,
                    selected = language == current,
                    onClick = { onSelect(language) },
                )
            }
        }
    }
}
