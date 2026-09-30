package uz.sadora.app.i18n

import kotlin.math.roundToInt
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import uz.sadora.app.model.Fmt
import uz.sadora.app.model.hasEnoughRatings
import uz.sadora.contract.DoctorAvailability

// A doctor's price, rating, reply time and hours in words. Plain functions over the
// string tables, so the rules — "Bepul" at zero, "Yangi shifokor" under three ratings,
// today's hour said as today, one decimal on a rating — are pinned by tests rather
// than by looking at a screen.

/** "Bepul", or "50 000 so'm / 24 soat". */
fun consultationPriceLabel(priceMinor: Long, d: DoctorStrings): String =
    if (priceMinor <= 0) d.free else d.pricePerWindow(Fmt.sum(priceMinor))

/**
 * "4,8" — one decimal, with the comma Uzbek and Russian write and the point English does.
 * The server rounds to one place already; this only keeps a 5.0 from reading "5".
 */
fun ratingValue(rating: Double, decimalPoint: Char = ','): String {
    val tenths = (rating * 10).roundToInt().coerceIn(0, 50)
    return "${tenths / 10}$decimalPoint${tenths % 10}"
}

/**
 * "★ 4,8 · 12 baho" once [hasEnoughRatings] — and "Yangi shifokor" until then, on her
 * page and on her card alike: two ratings are not yet a reputation, good or bad.
 */
fun ratingLine(rating: Double?, count: Int, d: DoctorStrings): String {
    if (rating == null || !hasEnoughRatings(count)) return d.newDoctor
    return d.ratingLabel(ratingValue(rating, d.decimalPoint), count)
}

/** "Bepul", or "50 000 so'm" on its own — the directory's card has no room for "/ 24 soat". */
fun doctorPriceLabel(priceMinor: Long, d: DoctorStrings): String =
    if (priceMinor <= 0) d.free else d.price(Fmt.sum(priceMinor))

/**
 * "odatda ~15 daqiqada javob beradi", and from an hour up "~2 soatda", rounded to the
 * nearest hour: "~95 daqiqada" is a number to work out rather than a sense of the wait.
 */
fun replyTimeLabel(minutes: Int, d: DoctorStrings): String =
    if (minutes >= 60) d.replyHours((minutes + 30) / 60) else d.replyMinutes(minutes.coerceAtLeast(1))

/** Which dot sits beside the availability line. */
enum class AvailabilityTone { Online, Busy, Away }

data class AvailabilityLine(val text: String, val tone: AvailabilityTone)

/**
 * "Hozir onlayn", "Band", or when she is next in her hours — in the phone's own zone,
 * since that is the clock the patient will wait by, and with today and tomorrow said
 * as words rather than as a weekday she has to work out.
 */
fun availabilityLine(
    availability: DoctorAvailability?,
    d: DoctorStrings,
    dates: DateStrings,
    now: Instant,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): AvailabilityLine? {
    val a = availability ?: return null
    return when {
        a.onlineNow -> AvailabilityLine(d.onlineNow, AvailabilityTone.Online)
        a.busy -> AvailabilityLine(d.busy, AvailabilityTone.Busy)
        a.nextAvailableAt != null -> {
            val at = a.nextAvailableAt!!.toLocalDateTime(zone)
            val today = now.toLocalDateTime(zone).date
            val day = when (at.date) {
                today -> dates.today.lowercase()
                today.plus(1, DateTimeUnit.DAY) -> dates.tomorrow.lowercase()
                else -> dates.weekdaysShort[at.date.dayOfWeek.ordinal]
            }
            AvailabilityLine(d.nextAvailable(day, Fmt.time(at)), AvailabilityTone.Away)
        }
        else -> AvailabilityLine(d.offlineNow, AvailabilityTone.Away)
    }
}
