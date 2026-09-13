package uz.sadora.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import uz.sadora.app.data.health.PeriodImport
import uz.sadora.app.data.health.PeriodSpan
import uz.sadora.app.data.health.SleepMetricNames
import uz.sadora.app.data.health.SleepNights
import uz.sadora.app.data.health.SleepSegment
import uz.sadora.app.data.health.SleepStage
import uz.sadora.contract.HealthProvider
import uz.sadora.contract.PeriodEntry

class HealthReadingRulesTest {

    private val zone = TimeZone.of("Asia/Tashkent")

    private fun at(text: String): Instant = LocalDateTime.parse(text).toInstant(zone)

    private fun day(text: String) = LocalDate.parse(text)

    private fun days(vararg texts: String) = texts.map(::day).toSet()

    private fun entry(start: String, end: String?) =
        PeriodEntry(id = start, startedOn = day(start), endedOn = end?.let(::day), createdAt = Instant.fromEpochMilliseconds(0))

    // ------------------------------------------------------------------ periods

    @Test
    fun `one clear day inside a period keeps it one period and two clear days split it`() {
        assertEquals(
            listOf(PeriodSpan(day("2026-08-01"), day("2026-08-04"))),
            PeriodImport.spans(days("2026-08-01", "2026-08-02", "2026-08-04")),
        )
        assertEquals(
            listOf(PeriodSpan(day("2026-08-01"), day("2026-08-02")), PeriodSpan(day("2026-08-05"), day("2026-08-05"))),
            PeriodImport.spans(days("2026-08-01", "2026-08-02", "2026-08-05")),
        )
    }

    @Test
    fun `a run longer than a period can last is cut to the longest one the server takes`() {
        val month = (1..20).map { LocalDate(2026, 7, it) }.toSet()
        assertEquals(listOf(PeriodSpan(day("2026-07-01"), day("2026-07-15"))), PeriodImport.spans(month))
    }

    @Test
    fun `a period she already logged is left alone even a couple of days off`() {
        val existing = listOf(entry("2026-08-10", "2026-08-14"))
        val spans = listOf(
            PeriodSpan(day("2026-07-12"), day("2026-07-16")),
            PeriodSpan(day("2026-08-15"), day("2026-08-18")),
        )
        assertEquals(listOf(spans.first()), PeriodImport.missing(spans, existing, today = day("2026-09-13")))
    }

    @Test
    fun `an open period swallows anything recent so nothing is logged twice`() {
        val existing = listOf(entry("2026-09-11", null))
        val spans = listOf(PeriodSpan(day("2026-09-12"), day("2026-09-13")))
        assertTrue(PeriodImport.missing(spans, existing, today = day("2026-09-13")).isEmpty())
    }

    @Test
    fun `a span reaching yesterday goes up still open and an older one goes up ended`() {
        val today = day("2026-09-13")
        assertTrue(PeriodImport.isOngoing(PeriodSpan(day("2026-09-10"), day("2026-09-12")), emptyList(), today))
        assertFalse(PeriodImport.isOngoing(PeriodSpan(day("2026-09-01"), day("2026-09-05")), emptyList(), today))
        assertFalse(
            PeriodImport.isOngoing(PeriodSpan(day("2026-09-10"), day("2026-09-12")), listOf(entry("2026-08-01", null)), today),
            "only one period can be open",
        )
    }

    // ------------------------------------------------------------------ sleep

    private val names = SleepMetricNames("asleep", "deep", "rem", "light", "awake")

    @Test
    fun `a night across midnight is one night on the day she woke`() {
        val nights = SleepNights.from(
            listOf(
                SleepSegment("watch", at("2026-09-12T23:00"), at("2026-09-13T01:00"), SleepStage.LIGHT),
                SleepSegment("watch", at("2026-09-13T01:00"), at("2026-09-13T02:00"), SleepStage.DEEP),
                SleepSegment("watch", at("2026-09-13T02:00"), at("2026-09-13T02:20"), SleepStage.AWAKE),
                SleepSegment("watch", at("2026-09-13T02:20"), at("2026-09-13T07:00"), SleepStage.REM),
            ),
            zone,
        )
        val night = nights.single()
        assertEquals(day("2026-09-13"), night.date)
        assertEquals((2 * 60 + 60 + 4 * 60 + 40) * 60L, night.asleepSeconds)
        assertEquals(20 * 60L, night.awakeSeconds)
        assertEquals(at("2026-09-13T07:00"), night.wakeAt)
    }

    @Test
    fun `a phone and a watch on the same night are not added together and the staged one wins`() {
        val nights = SleepNights.from(
            listOf(
                SleepSegment("phone", at("2026-09-12T22:30"), at("2026-09-13T07:30"), SleepStage.ASLEEP),
                SleepSegment("watch", at("2026-09-12T23:00"), at("2026-09-13T03:00"), SleepStage.LIGHT),
                SleepSegment("watch", at("2026-09-13T03:00"), at("2026-09-13T06:30"), SleepStage.REM),
            ),
            zone,
        )
        val night = nights.single()
        assertEquals("watch", night.source)
        assertEquals(7 * 3600L + 30 * 60, night.asleepSeconds)
    }

    @Test
    fun `a nap hours after waking is its own session added to the same day`() {
        val nights = SleepNights.from(
            listOf(
                SleepSegment("watch", at("2026-09-13T00:00"), at("2026-09-13T06:00"), SleepStage.ASLEEP),
                SleepSegment("watch", at("2026-09-13T14:00"), at("2026-09-13T14:40"), SleepStage.ASLEEP),
            ),
            zone,
        )
        assertEquals(6 * 3600L + 40 * 60, nights.single().asleepSeconds)
    }

    @Test
    fun `a night without stages sends only its length and a staged one sends all five`() {
        val plain = SleepNights.samples(
            SleepNights.from(listOf(SleepSegment("phone", at("2026-09-12T23:00"), at("2026-09-13T06:00"), SleepStage.ASLEEP)), zone),
            HealthProvider.HEALTH_CONNECT,
            names,
        )
        assertEquals(listOf("asleep"), plain.map { it.metric })
        assertEquals("sleep:2026-09-13:asleep", plain.single().externalId)

        val staged = SleepNights.samples(
            SleepNights.from(listOf(SleepSegment("watch", at("2026-09-12T23:00"), at("2026-09-13T06:00"), SleepStage.DEEP)), zone),
            HealthProvider.HEALTH_CONNECT,
            names,
        )
        assertEquals(listOf("asleep", "deep", "rem", "light", "awake"), staged.map { it.metric })
    }

    @Test
    fun `time only in bed is not sleep`() {
        val nights = SleepNights.from(
            listOf(SleepSegment("phone", at("2026-09-12T23:00"), at("2026-09-13T06:00"), SleepStage.IN_BED)),
            zone,
        )
        assertTrue(nights.isEmpty())
    }
}
