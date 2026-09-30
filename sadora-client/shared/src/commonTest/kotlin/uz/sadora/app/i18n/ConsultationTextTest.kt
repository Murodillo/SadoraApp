package uz.sadora.app.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import uz.sadora.contract.DoctorAvailability
import uz.sadora.contract.Limits

/**
 * A doctor's price, rating, reply time and hours in words: zero is "Bepul", tiyin
 * become grouped so'm, a rating keeps its one decimal and waits for three patients,
 * and her next hour is read in the phone's zone with today and tomorrow said as words.
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
        assertEquals("★ 5,0 · 21 оценка", ratingLine(5.0, 21, DoctorStringsRu))
        assertEquals("★ 4,5 · 5 оценок", ratingLine(4.5, 5, DoctorStringsRu))
        assertEquals("★ 4,0 · 3 оценки", ratingLine(4.0, 3, DoctorStringsRu))
        assertEquals("★ 4.8 · 12 ratings", ratingLine(4.8, 12, DoctorStringsEn))
    }

    @Test
    fun `under three ratings she is a new doctor rather than her stars`() {
        assertEquals(3, Limits.DOCTOR_RATING_MIN, "the tests below are written for three")
        assertEquals("Yangi shifokor", ratingLine(null, 0, DoctorStringsUz), "nobody has rated her yet")
        assertEquals("Yangi shifokor", ratingLine(5.0, 1, DoctorStringsUz), "one early five is not a reputation")
        assertEquals("Yangi shifokor", ratingLine(1.0, 2, DoctorStringsUz), "nor are two ones")
        assertEquals("★ 4,7 · 3 baho", ratingLine(4.7, 3, DoctorStringsUz), "three is enough")
        assertEquals("Новый врач", ratingLine(4.0, 2, DoctorStringsRu))
        assertEquals("New doctor", ratingLine(4.0, 2, DoctorStringsEn))
        assertEquals("Yangi shifokor", ratingLine(null, 7, DoctorStringsUz), "a count with no average is still no stars")
    }

    @Test
    fun `the card's price is Bepul or the sum on its own`() {
        assertEquals("Bepul", doctorPriceLabel(0, DoctorStringsUz))
        assertEquals("50 000 so'm", doctorPriceLabel(5_000_000, DoctorStringsUz))
        assertEquals("50 000 сум", doctorPriceLabel(5_000_000, DoctorStringsRu))
        assertEquals("Free", doctorPriceLabel(0, DoctorStringsEn))
    }

    @Test
    fun `a reply time is minutes under an hour and whole hours from one`() {
        assertEquals("odatda ~15 daqiqada javob beradi", replyTimeLabel(15, DoctorStringsUz))
        assertEquals("odatda ~1 daqiqada javob beradi", replyTimeLabel(0, DoctorStringsUz), "never ~0")
        assertEquals("odatda ~59 daqiqada javob beradi", replyTimeLabel(59, DoctorStringsUz))
        assertEquals("odatda ~1 soatda javob beradi", replyTimeLabel(60, DoctorStringsUz))
        assertEquals("odatda ~1 soatda javob beradi", replyTimeLabel(89, DoctorStringsUz))
        assertEquals("odatda ~2 soatda javob beradi", replyTimeLabel(90, DoctorStringsUz), "rounded to the nearest hour")
        assertEquals("обычно отвечает за ~3 ч", replyTimeLabel(170, DoctorStringsRu))
        assertEquals("usually replies in ~20 min", replyTimeLabel(20, DoctorStringsEn))
    }

    @Test
    fun `consultations are counted in each language's plural`() {
        assertEquals("12 konsultatsiya", DoctorStringsUz.consultations(12))
        assertEquals("1 консультация", DoctorStringsRu.consultations(1))
        assertEquals("3 консультации", DoctorStringsRu.consultations(3))
        assertEquals("11 консультаций", DoctorStringsRu.consultations(11))
        assertEquals("1 consultation", DoctorStringsEn.consultations(1))
        assertEquals("2 consultations", DoctorStringsEn.consultations(2))
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
