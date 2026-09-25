package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import uz.sadora.app.ui.modules.averageLabel
import uz.sadora.app.ui.modules.barLabels
import uz.sadora.app.ui.modules.barValues
import uz.sadora.app.ui.modules.changeIsGood
import uz.sadora.app.ui.modules.changeLabel
import uz.sadora.app.ui.modules.sentence
import uz.sadora.contract.ErrorCodes
import uz.sadora.contract.InsightFinding
import uz.sadora.contract.InsightKeys
import uz.sadora.contract.InsightsSummary
import uz.sadora.contract.MetricTrend
import uz.sadora.contract.TrendMetric
import uz.sadora.contract.TrendPoint
import uz.sadora.app.i18n.StringsUz

/**
 * The Insights screen drew invented numbers for a long time. These pin the replacement:
 * a window is fetched once, a refused window is the paywall rather than an error, and
 * every label the screen renders comes back null when nothing was measured.
 */
class InsightsControllerTest {

    private val monday = LocalDate.parse("2026-08-31")

    private fun graph(recording: RecordingEngine) = SadoraGraph(
        tokenStorage = InMemoryTokenStorage(token = "refresh-0"),
        device = FixedDeviceIdentity(),
        environment = SadoraEnvironment("http://test.local"),
        engine = recording.build(),
    )

    private fun sleepTrend(values: List<Double?>, previous: Double? = null) = MetricTrend(
        metric = TrendMetric.SLEEP_MINUTES,
        points = values.mapIndexed { index, value ->
            TrendPoint(LocalDate.fromEpochDays(monday.toEpochDays() + index), value)
        },
        average = values.filterNotNull().takeIf { it.isNotEmpty() }?.average(),
        previousAverage = previous,
        daysWithData = values.count { it != null },
    )

    private fun summary(trends: List<MetricTrend>, findings: List<InsightFinding> = emptyList()) =
        InsightsSummary(
            from = monday,
            to = LocalDate.fromEpochDays(monday.toEpochDays() + 6),
            days = 7,
            trends = trends,
            findings = findings,
            findingsAvailable = findings.isNotEmpty(),
            daysLogged = trends.maxOfOrNull { it.daysWithData } ?: 0,
        )

    @Test
    fun `a window is fetched once and kept so switching ranges does not refetch`() = runTest {
        val recording = RecordingEngine { json(encode(summary(listOf(sleepTrend(listOf(420.0)))))) }
        val insights = graph(recording).insightsController()

        insights.load(7)
        insights.load(7)
        assertEquals(1, recording.paths.count { it == "/v1/insights" })
        assertNotNull(insights.summary(7))

        // A different range is a different window and is fetched on its own.
        insights.load(30)
        assertEquals(2, recording.paths.count { it == "/v1/insights" })
        assertTrue(recording.paths.isNotEmpty())
    }

    @Test
    fun `a refused window is the paywall not an error banner`() = runTest {
        val recording = RecordingEngine { request ->
            if (request.url.encodedQuery.contains("days=90")) {
                json(
                    errorBody(ErrorCodes.ENTITLEMENT_REQUIRED, "Premium", mapOf("feature" to "insights_history")),
                    HttpStatusCode.PaymentRequired,
                )
            } else {
                json(encode(summary(listOf(sleepTrend(listOf(420.0))))))
            }
        }
        val insights = graph(recording).insightsController()

        insights.load(90)
        assertEquals(90, insights.lockedWindow)
        assertNull(insights.error, "the screen draws a lock; a banner would be noise")
        assertNull(insights.summary(90))

        // The window she can see still loads.
        insights.load(7)
        assertNotNull(insights.summary(7))
    }

    @Test
    fun `a transport failure is reported because that one is worth saying`() = runTest {
        val recording = RecordingEngine {
            json(errorBody(ErrorCodes.INTERNAL_ERROR, "Server xatosi"), HttpStatusCode.InternalServerError)
        }
        val insights = graph(recording).insightsController()

        insights.load(7)
        assertNull(insights.summary(7))
        assertEquals(StringsUz.errors.unexpected, insights.error?.readable(StringsUz.errors))
        assertNull(insights.lockedWindow)
    }

    @Test
    fun `with no backend nothing is claimed`() = runTest {
        val insights = InsightsController(null)
        insights.load(7)
        assertNull(insights.summary(7))
        assertNull(insights.error)
        assertTrue(insights.isOffline)
    }

    // ---------------------------------------------------------------- wording

    @Test
    fun `a day with nothing recorded stays a gap rather than becoming a zero`() {
        val trend = sleepTrend(listOf(420.0, null, 400.0))
        assertEquals(listOf(420f, null, 400f), trend.barValues())
        assertEquals(2, trend.daysWithData)
    }

    @Test
    fun `no comparison window means no change label`() {
        assertNull(sleepTrend(listOf(420.0), previous = null).changeLabel(StringsUz.modules, StringsUz.common))
    }

    @Test
    fun `a change is worded in the metric's own units and rounded away from noise`() {
        val trend = sleepTrend(listOf(420.0, 420.0), previous = 408.0)
        assertEquals("+12 daqiqa", trend.changeLabel(StringsUz.modules, StringsUz.common))
        assertEquals(true, trend.changeIsGood())

        // Under the floor, a movement is rounding rather than a trend.
        assertNull(sleepTrend(listOf(420.0), previous = 418.0).changeLabel(StringsUz.modules, StringsUz.common))
    }

    @Test
    fun `an hour or more reads as hours and minutes like everywhere else in the app`() {
        assertEquals("7s 0d", sleepTrend(listOf(420.0)).averageLabel(StringsUz.modules, StringsUz.common))
        assertNull(sleepTrend(listOf(null)).averageLabel(StringsUz.modules, StringsUz.common), "an average of nothing is not zero")
    }

    @Test
    fun `weekday labels appear on a short window and are dropped on a long one`() {
        assertEquals(listOf("Du", "Se", "Ch"), sleepTrend(listOf(1.0, 2.0, 3.0)).barLabels(StringsUz.dates))
        assertTrue(sleepTrend(List(30) { 1.0 }).barLabels(StringsUz.dates).isEmpty())
    }

    /** The wording states both numbers and never a cause; an unknown key says nothing. */
    @Test
    fun `findings are worded as co-occurrence and an unknown key is not guessed at`() {
        val sentence = InsightFinding(InsightKeys.SLEEP_AND_ENERGY, daysConsidered = 12, high = 4.2, low = 2.8)
            .sentence(StringsUz.modules)
        assertNotNull(sentence)
        assertTrue("4,2" in sentence && "2,8" in sentence, sentence)
        assertTrue("sabab" !in sentence.lowercase(), sentence)

        assertNull(InsightFinding("something_new", 12, 1.0, 2.0).sentence(StringsUz.modules))
    }
}
