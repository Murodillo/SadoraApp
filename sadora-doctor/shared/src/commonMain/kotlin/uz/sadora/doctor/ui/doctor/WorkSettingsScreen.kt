package uz.sadora.doctor.ui.doctor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uz.sadora.contract.DoctorSettings
import uz.sadora.doctor.data.DayHours
import uz.sadora.doctor.data.PriceCheck
import uz.sadora.doctor.data.WorkController
import uz.sadora.doctor.data.acceptPriceDigits
import uz.sadora.doctor.data.allValid
import uz.sadora.doctor.data.checkPrice
import uz.sadora.doctor.data.clockOf
import uz.sadora.doctor.data.minorToSom
import uz.sadora.doctor.data.netOf
import uz.sadora.doctor.data.readable
import uz.sadora.doctor.data.stepEnd
import uz.sadora.doctor.data.stepStart
import uz.sadora.doctor.data.toHours
import uz.sadora.doctor.data.weekOf
import uz.sadora.doctor.design.Radius
import uz.sadora.doctor.design.Sadora
import uz.sadora.doctor.design.SadoraIcons
import uz.sadora.doctor.design.Spacing
import uz.sadora.doctor.i18n.strings
import uz.sadora.doctor.ui.components.CircleIconButton
import uz.sadora.doctor.ui.components.ErrorStrip
import uz.sadora.doctor.ui.components.SadoraButton
import uz.sadora.doctor.ui.components.SadoraCard
import uz.sadora.doctor.ui.components.SadoraTextField
import uz.sadora.doctor.ui.components.SadoraTopBar
import uz.sadora.doctor.ui.components.ScreenContent
import uz.sadora.doctor.ui.components.Skeleton
import uz.sadora.doctor.ui.components.noRippleToggleable

/**
 * "Ish vaqti va narx": her price, the busy switch and her week.
 *
 * The switch takes effect the moment she flips it, as the one on Home does. The price
 * and the hours are a form: they go to the server together, on the save button, and only
 * once the price is one the server takes and every day she works is a real span.
 */
@Composable
fun WorkSettingsScreen(
    work: WorkController,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = strings.work
    val scope = rememberCoroutineScope()
    val calls = work.settingsCalls

    LaunchedEffect(Unit) {
        calls.clearError()
        work.loadSettings(silent = false)
    }
    val settings = work.settings

    // The form starts from the server's settings the first time they are there, and is
    // hers from then on: a later reload must not undo what she is typing.
    var priceDigits by remember { mutableStateOf<String?>(null) }
    var week by remember { mutableStateOf<List<DayHours>?>(null) }
    LaunchedEffect(settings != null) {
        val loaded = settings ?: return@LaunchedEffect
        if (priceDigits == null) priceDigits = minorToSom(loaded.priceMinor).takeIf { it > 0 }?.toString().orEmpty()
        if (week == null) week = weekOf(loaded.hours)
    }
    var saving by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }

    Column(modifier) {
        SadoraTopBar(w.settingsTitle, onBack = onClose)
        ScreenContent(stagger = false) {
            calls.error?.let { failure ->
                item(key = "error") {
                    ErrorStrip(failure.readable(), onRetry = if (settings == null) { { scope.launch { work.loadSettings(silent = false) } } } else null)
                }
            }
            val digits = priceDigits
            val days = week
            if (settings == null || digits == null || days == null) {
                if (calls.error == null) {
                    items(3, key = { "skeleton-$it" }) { Skeleton(Modifier.fillMaxWidth().height(96.dp), shape = Radius.card) }
                }
                return@ScreenContent
            }
            val price = checkPrice(digits)

            item(key = "busy") {
                SwitchCard(
                    title = w.busyTitle,
                    body = w.busyBody,
                    on = settings.busy,
                    enabled = !switching,
                    tint = Sadora.colors.warning,
                    onToggle = { wanted ->
                        switching = true
                        scope.launch {
                            work.setBusy(wanted)
                            switching = false
                        }
                    },
                )
            }

            item(key = "price") { PriceCard(digits, price, settings, onDigits = { priceDigits = acceptPriceDigits(it) }) }

            item(key = "hours") {
                HoursCard(
                    week = days,
                    timezone = settings.timezone,
                    onChange = { changed -> week = days.map { if (it.weekday == changed.weekday) changed else it } },
                )
            }

            item(key = "save") {
                val changed = price.minorOrNull != settings.priceMinor || days.toHours() != settings.hours
                SadoraButton(
                    if (saving) strings.common.saving else strings.doctors.save,
                    enabled = changed && price.ok && days.allValid() && !saving,
                    onClick = {
                        val minor = price.minorOrNull ?: return@SadoraButton
                        saving = true
                        scope.launch {
                            val ok = work.saveWork(minor, days.toHours())
                            saving = false
                            if (ok) onSaved()
                        }
                    },
                )
            }
        }
    }
}

/** The price field, what is wrong with it, and what she keeps of it. */
@Composable
private fun PriceCard(digits: String, price: PriceCheck, settings: DoctorSettings, onDigits: (String) -> Unit) {
    val w = strings.work
    val c = Sadora.colors
    SadoraCard {
        Text(w.priceTitle, style = Sadora.type.h3, color = c.text)
        SadoraTextField(
            value = digits,
            onValueChange = onDigits,
            label = w.priceLabel,
            placeholder = "0",
            keyboardType = KeyboardType.Number,
            error = when (price) {
                PriceCheck.TooLow -> w.priceTooLow
                PriceCheck.TooHigh -> w.priceTooHigh
                else -> null
            },
        )
        when (price) {
            is PriceCheck.Paid -> {
                Text(somText(price.minor), style = Sadora.type.h2, color = c.textAccent)
                Text(w.commission(settings.commissionPercent), style = Sadora.type.body, color = c.muted)
                Text(w.youGet(somText(netOf(price.minor, settings.commissionPercent))), style = Sadora.type.body, color = c.text)
            }
            PriceCheck.Free -> Text(w.free, style = Sadora.type.h2, color = c.successText)
            else -> Unit
        }
        Text(w.priceNote, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    }
}

/** The seven days, each on or off, with its hours in half-hour steps while it is on. */
@Composable
private fun HoursCard(week: List<DayHours>, timezone: String, onChange: (DayHours) -> Unit) {
    val w = strings.work
    val c = Sadora.colors
    SadoraCard {
        Text(w.hoursTitle, style = Sadora.type.h3, color = c.text)
        Text(w.hoursNote, style = Sadora.type.body, color = c.muted)
        week.forEach { day -> DayRow(day, onChange) }
        if (!week.allValid()) Text(w.hoursInvalid, style = Sadora.type.body, color = c.dangerText)
        Text(w.timezone(timezone), style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
    }
}

@Composable
private fun DayRow(day: DayHours, onChange: (DayHours) -> Unit) {
    val w = strings.work
    val c = Sadora.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(
            Modifier
                .fillMaxWidth()
                .noRippleToggleable(value = day.on, role = Role.Switch) { onChange(day.copy(on = it)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f)) {
                Text(w.weekday(day.weekday), style = Sadora.type.h3, color = if (day.on) c.text else c.muted)
                if (!day.on) Text(w.dayOff, style = Sadora.type.caption.copy(letterSpacing = TextUnit.Unspecified), color = c.muted2)
            }
            SwitchThumb(day.on)
        }
        AnimatedVisibility(
            visible = day.on,
            enter = fadeIn(tween(180)) + expandVertically(tween(180)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(140)),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                TimeStepper(w.hoursFrom, day.start, onEarlier = { onChange(day.stepStart(-1)) }, onLater = { onChange(day.stepStart(1)) })
                TimeStepper(w.hoursTo, day.end, onEarlier = { onChange(day.stepEnd(-1)) }, onLater = { onChange(day.stepEnd(1)) })
            }
        }
    }
}

/** "Boshlanishi   ‹ 09:00 ›": half an hour a tap, the time between the arrows. */
@Composable
private fun TimeStepper(label: String, minute: Int, onEarlier: () -> Unit, onLater: () -> Unit) {
    val w = strings.work
    val c = Sadora.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, style = Sadora.type.body, color = c.muted, modifier = Modifier.weight(1f))
        CircleIconButton(SadoraIcons.ChevronLeft, contentDescription = "$label: ${w.earlier}", onClick = onEarlier)
        Text(
            clockOf(minute),
            style = Sadora.type.h3,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        CircleIconButton(SadoraIcons.ChevronRight, contentDescription = "$label: ${w.later}", onClick = onLater)
    }
}
