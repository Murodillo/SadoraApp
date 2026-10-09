package uz.sadora.app.ui.modules

import uz.sadora.app.ui.components.loadingSemantics
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import uz.sadora.app.data.PartnerController
import uz.sadora.app.data.PetController
import uz.sadora.app.design.Radius
import uz.sadora.app.design.Sadora
import uz.sadora.app.design.Spacing
import uz.sadora.app.i18n.strings
import uz.sadora.app.model.Fmt
import uz.sadora.app.ui.components.ButtonTone
import uz.sadora.app.ui.components.LegendaryGold
import uz.sadora.app.ui.components.LegendaryGoldText
import uz.sadora.app.ui.components.LegendaryInk
import uz.sadora.app.ui.components.PetImage
import uz.sadora.app.ui.components.PetShowcase
import uz.sadora.app.ui.components.PillButton
import uz.sadora.app.ui.components.PremiumCtaButton
import uz.sadora.app.ui.components.SadoraBottomSheet
import uz.sadora.app.ui.components.SadoraButton
import uz.sadora.app.ui.components.SadoraCard
import uz.sadora.app.ui.components.SadoraTopBar
import uz.sadora.app.ui.components.ScreenContent
import uz.sadora.app.ui.components.art
import uz.sadora.app.ui.components.legendaryFrame
import uz.sadora.app.ui.partner.AskPartnerContent
import uz.sadora.contract.PaymentProvider
import uz.sadora.contract.PaymentRequestKind
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetPose
import uz.sadora.contract.PetProduct

/**
 * Picks the companion. Open to a free account too: the five are shown as they are, and
 * the one she picks sleeps on the header until Premium wakes it.
 *
 * The legendary pet, while it is on sale or hers, has a card of its own at the top: gold,
 * alive, and with its price. Tapping it opens the buy sheet rather than picking it.
 */
@Composable
fun PetPickerScreen(
    pets: PetController,
    onClose: () -> Unit,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    /** For "ask Yaqinim to pay"; null where there is no partner screen to send it from. */
    partner: PartnerController? = null,
) {
    val t = strings.pet
    val c = Sadora.colors
    val type = Sadora.type
    val scope = rememberCoroutineScope()
    var buying by remember { mutableStateOf<PetProduct?>(null) }
    var asking by remember { mutableStateOf(false) }
    var justBought by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        pets.load()
        pets.loadStorePrice()
    }

    Box(modifier) {
        Column {
            SadoraTopBar(t.title, onBack = onClose)
            ScreenContent {
                item {
                    SadoraCard(
                        verticalGap = Spacing.xs,
                        modifier = if (pets.pet.legendary) Modifier.legendaryFrame() else Modifier,
                    ) {
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
                        if (justBought) {
                            Text(
                                if (pets.active) t.bought else t.boughtAsleep,
                                style = type.body,
                                color = LegendaryGoldText,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (!pets.active) {
                            if (!justBought) {
                                Text(t.premiumBanner, style = type.body, color = c.textAccent, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            }
                            PremiumCtaButton(t.premiumButton, onClick = onUpgrade, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                // Legendary first, then the five.
                pets.available.sortedByDescending { it.legendary }.forEach { pet ->
                    item(key = pet.name) {
                        val selected = pet == pets.pet
                        val forSale = pets.forSale?.takeIf { it.pet == pet }
                        val choose: () -> Unit = {
                            when {
                                selected -> Unit
                                forSale != null -> buying = forSale
                                else -> scope.launch { pets.choose(pet) }
                            }
                        }
                        SadoraCard(
                            modifier = when {
                                pet.legendary -> Modifier.legendaryFrame()
                                selected -> Modifier.border(2.dp, c.primary, Radius.card)
                                else -> Modifier
                            },
                            onClick = choose,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                if (pet.legendary) {
                                    // Alive even before she owns it: she sees what she would buy.
                                    PetImage(pet, PetPose.IDLE, size = 72.dp)
                                } else {
                                    Image(painterResource(pet.art(PetPose.IDLE)), contentDescription = null, modifier = Modifier.size(72.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                                    if (pet.legendary) LegendaryTag()
                                    Text(t.name(pet), style = type.h3, color = c.text)
                                    Text(t.personality(pet), style = type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted)
                                }
                                PillButton(
                                    when {
                                        selected -> t.chosen
                                        forSale != null -> priceText(pets, forSale)
                                        else -> t.choose
                                    },
                                    onClick = choose,
                                    tone = if (selected || forSale != null) ButtonTone.Primary else ButtonTone.Secondary,
                                )
                            }
                        }
                    }
                }
            }
        }

        SadoraBottomSheet(visible = buying != null && !asking, title = t.name(buying?.pet ?: PetKind.HUMO), onDismiss = { buying = null }) {
            buying?.let { product ->
                BuyContent(
                    pets = pets,
                    product = product,
                    canAsk = partner != null,
                    onAsk = { asking = true },
                    onBought = {
                        buying = null
                        justBought = true
                    },
                )
            }
        }
        if (partner != null) {
            SadoraBottomSheet(visible = asking, title = strings.partner.askTitle, onDismiss = { asking = false }) {
                AskPartnerContent(
                    partner = partner,
                    kind = PaymentRequestKind.PET,
                    pet = buying?.pet ?: PetKind.HUMO,
                    onClose = {
                        asking = false
                        buying = null
                    },
                )
            }
        }
    }
}

/**
 * The buy sheet: the pet acting out all it can, what she gets, what Premium has to do
 * with it, and the ways to pay. A store build sells through the store's sheet only.
 */
@Composable
private fun BuyContent(
    pets: PetController,
    product: PetProduct,
    canAsk: Boolean,
    onAsk: () -> Unit,
    onBought: () -> Unit,
) {
    val t = strings.pet
    val c = Sadora.colors
    val type = Sadora.type
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val waiting = pets.paying
    val inStore = pets.store != null && pets.storeProductId(product) != null
    // A store build sells a digital thing through the store only, never Payme or Click.
    val direct = if (pets.store != null) emptyList() else product.providers.filter { it == PaymentProvider.PAYME || it == PaymentProvider.CLICK }

    LifecycleResumeEffect(waiting?.transactionId) {
        val job = if (waiting == null) null else scope.launch { if (pets.awaitPayment()) onBought() }
        onPauseOrDispose { job?.cancel() }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LegendaryTag()
        PetShowcase(product.pet, size = 156.dp)
        Text(t.personality(product.pet), style = type.body, color = c.muted, textAlign = TextAlign.Center)
    }
    Text(t.buyBody, style = type.body, color = c.text)
    if (!pets.active) Text(t.needsPremium, style = type.body, color = c.warning)

    when {
        waiting != null -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CircularProgressIndicator(Modifier.size(20.dp).loadingSemantics(), color = c.primary, strokeWidth = 2.5.dp)
                Text(t.paying, style = type.h3, color = c.text)
            }
            SadoraButton(t.payReopen, { uriHandler.openUri(waiting.url) }, tone = ButtonTone.Secondary)
            SadoraButton(strings.common.cancel, { pets.cancelCheckout() }, tone = ButtonTone.Ghost)
        }
        inStore -> {
            SadoraButton(
                t.buy(priceText(pets, product)),
                enabled = !pets.busy && !pets.storePending,
                onClick = { scope.launch { if (pets.buyInStore(product)) onBought() } },
            )
            if (pets.storePending) Text(t.storePending, style = type.body, color = c.muted)
        }
        direct.isEmpty() -> Text(t.noProvider, style = type.body, color = c.muted)
        else -> direct.forEach { provider ->
            SadoraButton(
                strings.partner.payWith(if (provider == PaymentProvider.PAYME) "Payme" else "Click") + " · " + priceText(pets, product),
                enabled = !pets.busy,
                onClick = {
                    scope.launch {
                        val session = pets.checkout(product.pet, provider) ?: return@launch
                        uriHandler.openUri(session.url)
                    }
                },
            )
        }
    }
    if (canAsk && waiting == null) SadoraButton(t.askYaqinim, onAsk, tone = ButtonTone.Secondary)
}

/** The store's own price in a store build; ours everywhere else. One price, either way. */
@Composable
private fun priceText(pets: PetController, product: PetProduct): String =
    pets.storePrice?.takeIf { pets.store != null } ?: strings.doctors.price(Fmt.sum(product.priceMinor))

@Composable
private fun LegendaryTag() {
    Text(
        "✦ " + strings.pet.legendary,
        style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified),
        color = LegendaryInk,
        modifier = Modifier
            .background(LegendaryGold, Radius.chip)
            .padding(horizontal = Spacing.xs, vertical = 2.dp),
    )
}
