package uz.sadora.server.insights

import kotlin.uuid.Uuid
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import uz.sadora.contract.FeatureKeys
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.InsightFinding
import uz.sadora.contract.InsightKeys
import uz.sadora.contract.InsightsSummary
import uz.sadora.contract.MetricTrend
import uz.sadora.contract.TrendMetric
import uz.sadora.contract.TrendPoint
import uz.sadora.server.core.ValidationException
import uz.sadora.server.core.dayIn
import uz.sadora.server.core.now
import uz.sadora.server.entitlement.EntitlementService
import uz.sadora.server.health.HealthAccess
import uz.sadora.server.health.HealthRepository
import uz.sadora.server.health.NutritionRepository
import uz.sadora.server.wearable.WearableRepository

/**
 * The Tahlillar screen's numbers, computed from what she actually logged.
 *
 * Two rules run through all of it. A day with nothing recorded is a gap, never a zero —
 * averaging a gap as zero would make a week off look like a collapse. And nothing is
 * reported that was not measured: an average over one day is not an average, so a series
 * with too little in it comes back with a null average and the screen says so rather
 * than drawing a confident line.
 *
 * Depth and the narrative are separate entitlements: the trends are her own data and are
 * free, a window longer than a week needs `insights_history`, and the observations need
 * `ai_insights`.
 */
class InsightsService(
    private val access: HealthAccess,
    private val health: HealthRepository,
    private val nutrition: NutritionRepository,
    private val wearables: WearableRepository,
    private val entitlements: EntitlementService,
) {

    suspend fun summary(userId: Uuid, days: Int): InsightsSummary {
        if (days !in ALLOWED_WINDOWS) {
            throw ValidationException("days", "Faqat ${ALLOWED_WINDOWS.joinToString(", ")} kun")
        }
        val user = access.requireUser(userId)
        if (days > FREE_WINDOW_DAYS) {
            entitlements.requireAvailable(userId, FeatureKeys.INSIGHTS_HISTORY, user.timezone)
        }

        val today = now().dayIn(user.timezone)
        val from = today.minus(days - 1, DateTimeUnit.DAY)
        // The comparison window sits immediately before this one and is the same length,
        // so "+12 daqiqa" always means "against the same number of days".
        val previousFrom = from.minus(days, DateTimeUnit.DAY)
        val previousTo = from.minus(1, DateTimeUnit.DAY)

        val logs = health.logsBetween(userId, previousFrom, today).associateBy { it.date }
        val water = nutrition.waterBetween(userId, previousFrom, today)
        val kcal = nutrition.kcalBetween(userId, previousFrom, today)
        val wearableDays = wearables.aggregatesBetween(userId, previousFrom, today)

        fun valueOn(metric: TrendMetric, date: LocalDate): Double? = when (metric) {
            TrendMetric.SLEEP_MINUTES -> wearableDays[date]?.firstOrNull { it.metric == HealthMetric.SLEEP_DURATION }?.value
            TrendMetric.STEPS -> wearableDays[date]?.firstOrNull { it.metric == HealthMetric.STEPS }?.value
            TrendMetric.WATER_ML -> water[date]?.toDouble()
            TrendMetric.KCAL -> kcal[date]?.toDouble()
            TrendMetric.MOOD -> logs[date]?.mood?.score?.toDouble()
            TrendMetric.ENERGY -> logs[date]?.energy?.toDouble()
            TrendMetric.STRESS -> logs[date]?.stress?.toDouble()
        }

        val window = datesBetween(from, today)
        val previousWindow = datesBetween(previousFrom, previousTo)

        val trends = TrendMetric.entries.map { metric ->
            val points = window.map { TrendPoint(it, valueOn(metric, it)) }
            val values = points.mapNotNull { it.value }
            MetricTrend(
                metric = metric,
                points = points,
                average = values.averageOrNull(),
                previousAverage = previousWindow.mapNotNull { valueOn(metric, it) }.averageOrNull(),
                daysWithData = values.size,
            )
        }

        val findingsAvailable = entitlements.resolve(userId, user.timezone)
            .isAvailable(FeatureKeys.AI_INSIGHTS)

        return InsightsSummary(
            from = from,
            to = today,
            days = days,
            trends = trends,
            findings = if (findingsAvailable) findings(window, ::valueOn, logs) else emptyList(),
            findingsAvailable = findingsAvailable,
            daysLogged = window.count { date ->
                TrendMetric.entries.any { valueOn(it, date) != null }
            },
        )
    }

    // ---------------------------------------------------------------- findings

    /**
     * The observations, as plain arithmetic over the window.
     *
     * Each one splits the days by the median of one metric and compares the mean of
     * another across the two halves. That is a co-occurrence and nothing more, which is
     * exactly what the screen is allowed to say; the minimum day count and the minimum
     * difference are there so a quiet week cannot produce a confident-sounding sentence.
     */
    private fun findings(
        window: List<LocalDate>,
        valueOn: (TrendMetric, LocalDate) -> Double?,
        logs: Map<LocalDate, uz.sadora.contract.DailyLog>,
    ): List<InsightFinding> = buildList {
        splitFinding(
            key = InsightKeys.SLEEP_AND_ENERGY,
            window = window,
            by = TrendMetric.SLEEP_MINUTES,
            measure = TrendMetric.ENERGY,
            unit = TrendMetric.ENERGY.unit,
            valueOn = valueOn,
        )?.let(::add)

        splitFinding(
            key = InsightKeys.STEPS_AND_MOOD,
            window = window,
            by = TrendMetric.STEPS,
            measure = TrendMetric.MOOD,
            unit = TrendMetric.MOOD.unit,
            valueOn = valueOn,
        )?.let(::add)

        headacheFinding(window, valueOn, logs)?.let(::add)
    }

    private fun splitFinding(
        key: String,
        window: List<LocalDate>,
        by: TrendMetric,
        measure: TrendMetric,
        unit: String,
        valueOn: (TrendMetric, LocalDate) -> Double?,
    ): InsightFinding? {
        val pairs = window.mapNotNull { date ->
            val split = valueOn(by, date) ?: return@mapNotNull null
            val value = valueOn(measure, date) ?: return@mapNotNull null
            split to value
        }
        val comparison = InsightMath.split(pairs, MIN_DAYS_FOR_FINDING, MIN_SCALE_DIFFERENCE) ?: return null
        return InsightFinding(
            key = key,
            daysConsidered = comparison.days,
            high = comparison.high,
            low = comparison.low,
            unit = unit,
        )
    }

    /**
     * Headache days against water.
     *
     * Counted as a rate rather than a mean, because a headache is a yes or a no. Needs a
     * few of them in the window: "0% against 0%" is not an observation.
     */
    private fun headacheFinding(
        window: List<LocalDate>,
        valueOn: (TrendMetric, LocalDate) -> Double?,
        logs: Map<LocalDate, uz.sadora.contract.DailyLog>,
    ): InsightFinding? {
        val pairs = window.mapNotNull { date ->
            val log = logs[date] ?: return@mapNotNull null
            val drunk = valueOn(TrendMetric.WATER_ML, date) ?: return@mapNotNull null
            drunk to (if (log.symptoms.any { it.key == HEADACHE_KEY }) 1.0 else 0.0)
        }
        if (pairs.count { it.second > 0 } < MIN_HEADACHE_DAYS) return null
        // Rates, so the threshold is in percentage points and the halves are scaled up.
        val comparison = InsightMath.split(
            pairs.map { it.first to it.second * 100 },
            MIN_DAYS_FOR_FINDING,
            MIN_RATE_DIFFERENCE,
        ) ?: return null

        return InsightFinding(
            key = InsightKeys.WATER_AND_HEADACHE,
            daysConsidered = comparison.days,
            high = comparison.high,
            low = comparison.low,
            unit = "%",
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        if (from > to) emptyList() else (0..from.daysUntil(to)).map { from.plus(it, DateTimeUnit.DAY) }

    private fun List<Double>.averageOrNull(): Double? = InsightMath.averageOrNull(this)

    companion object {
        val ALLOWED_WINDOWS = listOf(7, 30, 90)
        const val FREE_WINDOW_DAYS = 7
        const val MIN_DAYS_FOR_FINDING = 8
        const val MIN_HEADACHE_DAYS = 2
        const val MIN_SCALE_DIFFERENCE = 0.4
        const val MIN_RATE_DIFFERENCE = 15.0
        const val HEADACHE_KEY = "headache"
    }
}
