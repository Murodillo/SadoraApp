package uz.sadora.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import uz.sadora.contract.DoctorAvailability

/**
 * A doctor's price, rating and hours in words: zero is "Bepul", tiyin become grouped
 * so'm, a rating keeps its one decimal, and her next hour is read in the phone's zone
 * with today and tomorrow said as words.
 */
class ConsultationTextTest {

    private val tashkent = TimeZone.of("Asia/Tashkent")

    /** Monday 2026-09-28, 10:00 in Tashkent. */
    private val monday: Instant = Instant.parse("2026-09-28T05:00:00Z")

    @Test
    fun `a free doctor reads Bepul and a paid one her price per day`() {
        assertEquals("Bepul", consultationPriceLabel(0, DoctorStringsUz))
        assertEquals("50 000 so'm / 24 soat", consultationPriceLabel(5_000_000, DoctorStringsUz))
        assertEquals("1 250 000 сум / 24 часа", consultationPriceLabel(125_000_000, DoctorStringsRu))
        assertEquals("50 000 UZS / 24 hours", consultationPriceLabel(5_000_000, DoctorStringsEn))
    }

    @Test
    fun `a rating keeps one decimal in the language's own separator`() {
        assertEquals("★ 4,8 · 12 baho", ratingLine(4.8, 12, DoctorStringsUz))
        assertEquals("★ 5,0 · 1 оценка", ratingLine(5.0, 1, DoctorStringsRu))
        assertEquals("★ 4,5 · 5 оценок", ratingLine(4.5, 5, DoctorStringsRu))
        assertEquals("★ 4.8 · 12 ratings", ratingLine(4.8, 12, DoctorStringsEn))
        assertNull(ratingLine(null, 0, DoctorStringsUz), "nobody has rated her yet")
        assertNull(ratingLine(4.0, 0, DoctorStringsUz))
    }

    @Test
    fun `online and busy are said as they are`() {
        val dates = StringsUz.dates
        assertEquals(
            AvailabilityLine("Hozir onlayn", AvailabilityTone.Online),
            availabilityLine(DoctorAvailability(onlineNow = true), DoctorStringsUz, dates, monday, tashkent),
        )
        assertEquals(
            AvailabilityLine("Band", AvailabilityTone.Busy),
            availabilityLine(DoctorAvailability(onlineNow = false, busy = true), DoctorStringsUz, dates, monday, tashkent),
        )
        assertEquals(
            AvailabilityTone.Away,
            availabilityLine(DoctorAvailability(onlineNow = false), DoctorStringsUz, dates, monday, tashkent)?.tone,
        )
        assertNull(availabilityLine(null, DoctorStringsUz, dates, monday, tashkent))
    }

    @Test
    fun `her next hour is today or tomorrow or a weekday in local time`() {
        val dates = StringsUz.dates
        fun next(at: Instant) = availabilityLine(
            DoctorAvailability(onlineNow = false, nextAvailableAt = at),
            DoctorStringsUz,
            dates,
            monday,
            tashkent,
        )?.text

        assertEquals("Keyingi: bugun 14:00", next(monday + 4.hours))
        assertEquals("Keyingi: ertaga 09:00", next(monday + 23.hours))
        // Thursday 09:00 in Tashkent is 04:00 UTC.
        assertEquals("Keyingi: Pa 09:00", next(monday + 3.days - 1.hours))
    }
}
