package uz.sadora.doctor.data

import uz.sadora.contract.DoctorHours

// The rules behind her working day that no screen should hold on its own: money in
// so'm, the price she may set, the week of hours, a quick reply dropped into a draft,
// and the link a push carries. Kept apart from Compose so the tests can pin them.

// ---------------------------------------------------------------- money

/** The cheapest paid price the server takes, in so'm; 0 keeps her free. */
const val MinPriceSom = 1_000L

/** The dearest price the server takes, in so'm. */
const val MaxPriceSom = 2_000_000L

/** Tiyin in a so'm: money travels as tiyin and is shown as so'm. */
private const val TiyinPerSom = 100L

/** "50 000": tiyin as whole so'm, grouped by threes with spaces, the way the app writes money. */
fun groupedSom(minor: Long): String {
    val som = minor / TiyinPerSom
    val digits = kotlin.math.abs(som).toString()
    val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
    return if (som < 0) "-$grouped" else grouped
}

fun somToMinor(som: Long): Long = som * TiyinPerSom

fun minorToSom(minor: Long): Long = minor / TiyinPerSom

/** What she keeps of [priceMinor] once Sadora's share is taken — the server's own sum. */
fun netOf(priceMinor: Long, commissionPercent: Int): Long =
    priceMinor - priceMinor * commissionPercent / 100

/** What the price field holds, as the save button needs to know it. */
sealed interface PriceCheck {
    /** Empty or 0: her consultations are free. */
    data object Free : PriceCheck

    data class Paid(val minor: Long) : PriceCheck

    /** Above zero and under [MinPriceSom]. */
    data object TooLow : PriceCheck

    data object TooHigh : PriceCheck

    val ok: Boolean get() = this is Free || this is Paid

    /** Tiyin to send, or null when the price cannot be saved. */
    val minorOrNull: Long?
        get() = when (this) {
            Free -> 0L
            is Paid -> minor
            else -> null
        }
}

/** The field keeps digits only, and no more than the dearest price could need. */
fun acceptPriceDigits(raw: String): String = raw.filter(Char::isDigit).trimStart('0').take(MaxPriceSom.toString().length + 1)

fun checkPrice(digits: String): PriceCheck {
    val som = digits.filter(Char::isDigit).toLongOrNull() ?: 0L
    return when {
        som == 0L -> PriceCheck.Free
        som < MinPriceSom -> PriceCheck.TooLow
        som > MaxPriceSom -> PriceCheck.TooHigh
        else -> PriceCheck.Paid(somToMinor(som))
    }
}

// ---------------------------------------------------------------- the week

/** The editor's steps: half an hour, which is as fine as a clinic's day is cut. */
const val HoursStepMinutes = 30

private const val DayMinutes = 24 * 60

/** A day the editor has not seen before starts as an ordinary working day. */
private const val DefaultStart = 9 * 60
private const val DefaultEnd = 18 * 60

/** One weekday in the editor: switched on or off, and its hours either way. */
data class DayHours(val weekday: Int, val on: Boolean, val start: Int, val end: Int) {
    val valid: Boolean get() = start in 0 until DayMinutes && end in 1..DayMinutes && end > start
}

/** The whole week, Monday first, from what the server holds: a missing day is a day off. */
fun weekOf(hours: List<DoctorHours>): List<DayHours> = (1..7).map { weekday ->
    val set = hours.firstOrNull { it.weekday == weekday }
    if (set != null) {
        DayHours(weekday, on = true, start = set.startMinute, end = set.endMinute)
    } else {
        DayHours(weekday, on = false, start = DefaultStart, end = DefaultEnd)
    }
}

/** What goes back to the server: the days she works, and only those. */
fun List<DayHours>.toHours(): List<DoctorHours> =
    filter { it.on }.map { DoctorHours(it.weekday, it.start, it.end) }

/** Every day she works is a real span, so the server has nothing to refuse. */
fun List<DayHours>.allValid(): Boolean = filter { it.on }.all { it.valid }

/** Moves the start by [steps] half-hours, never past midnight and never onto the end. */
fun DayHours.stepStart(steps: Int): DayHours =
    copy(start = (start + steps * HoursStepMinutes).coerceIn(0, end - HoursStepMinutes))

/** Moves the end by [steps] half-hours, never before the start and never past midnight. */
fun DayHours.stepEnd(steps: Int): DayHours =
    copy(end = (end + steps * HoursStepMinutes).coerceIn(start + HoursStepMinutes, DayMinutes))

/** "09:00"; the end of the day is "24:00", not "00:00", so a day reads as a span. */
fun clockOf(minute: Int): String {
    val hours = minute / 60
    val minutes = minute % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}"
}

/** "09:00–18:00". */
fun spanOf(start: Int, end: Int): String = "${clockOf(start)}–${clockOf(end)}"

// ---------------------------------------------------------------- the composer

/**
 * A quick reply dropped into what she is writing. Into an empty field it is the whole
 * text; after words of her own it goes on a new line, so a greeting she typed stays in
 * front. Cut at [max], the most a message may hold.
 */
fun insertReply(draft: String, body: String, max: Int): String {
    val joined = if (draft.isBlank()) body else draft.trimEnd() + "\n" + body
    return joined.take(max)
}

// ---------------------------------------------------------------- push links

/**
 * The conversation a push opens: `sadora://conversation/{id}`. Anything else — another
 * link, a path with more in it, nothing at all — opens nothing.
 */
fun conversationIdFromLink(link: String?): String? {
    val prefix = "sadora://conversation/"
    if (link == null || !link.startsWith(prefix)) return null
    val id = link.removePrefix(prefix).substringBefore('?').trimEnd('/')
    return id.takeIf { it.isNotEmpty() && '/' !in it }
}
