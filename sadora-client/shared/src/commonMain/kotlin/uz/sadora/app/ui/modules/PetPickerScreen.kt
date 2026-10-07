package uz.sadora.app.ui.modules

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.data.PetController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.PetImage
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.PremiumCtaButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.art
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetPose

/**
 * Picks the companion. Open to a free account too: the five are shown as they are, and
 * the one she picks sleeps on the header until Premium wakes it.
 */
@Composable
fun PetPickerScreen(
    pets: PetController,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = strings.pet
    val c = Sadora.colors
    val type = Sadora.type
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { pets.load() }

    Column(modifier) {
        SadoraTopBar(t.title, onBack = onClose)
        ScreenContent {
            item {
                SadoraCard(verticalGap = Spacing.xs) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        PetImage(pets.pet, if (pets.active) PetPose.IDLE else PetPose.SLEEP, size = 168.dp)
                        Text(t.name(pets.pet), style = type.h2, color = c.text)
                        Text(t.personality(pets.pet), style = type.body, color = c.muted, textAlign = TextAlign.Center)
                    }
                    Text(
                        t.subtitle,
                        style = type.caption.copy(letterSpacing = TextUnit.Unspecified),
                        color = c.muted2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!pets.active) {
                        Text(t.premiumBanner, style = type.body, color = c.textAccent, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        PremiumCtaButton(t.premiumButton, onClick = onUpgrade, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            PetKind.entries.forEach { pet ->
                item(key = pet.name) {
                    val selected = pet == pets.pet
                    SadoraCard(
                        modifier = if (selected) Modifier.border(2.dp, c.primary, Radius.card) else Modifier,
                        onClick = { if (!selected) scope.launch { pets.choose(pet) } },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Image(painterResource(pet.art(PetPose.IDLE)), contentDescription = null, modifier = Modifier.size(72.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                Text(t.name(pet), style = type.h3, color = c.text)
                                Text(t.personality(pet), style = type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                            }
                            PillButton(
                                if (selected) t.chosen else t.choose,
                                onClick = { if (!selected) scope.launch { pets.choose(pet) } },
                                tone = if (selected) ButtonTone.Primary else ButtonTone.Secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
