package uz.sadora.server.baby

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uz.sadora.contract.BabySex
import uz.sadora.contract.GrowthCurvePoint
import uz.sadora.contract.GrowthMetric

/**
 * The WHO Child Growth Standards (2006), birth to two years, as L, M and S per completed
 * month — the Box-Cox parameters every WHO chart is drawn from.
 *
 * The numbers are not typed here: `baby/who_lms.json` was built from the tables WHO
 * publishes (its `sources` list says which files), and this object only does the
 * arithmetic WHO documents. Between two months the parameters are interpolated linearly,
 * which is what the monthly tables are meant for.
 */
object WhoGrowth {

    @Serializable
    private data class Table(val boys: List<List<Double>>, val girls: List<List<Double>>)

    @Serializable
    private data class File(val weight: Table, val length: Table, val head: Table, val sources: List<String> = emptyList())

    /** One month's parameters. */
    data class Lms(val month: Int, val l: Double, val m: Double, val s: Double)

    private val tables: Map<Pair<GrowthMetric, BabySex>, List<Lms>> by lazy {
        val text = WhoGrowth::class.java.getResourceAsStream("/baby/who_lms.json")!!.bufferedReader().readText()
        val file = Json { ignoreUnknownKeys = true }.decodeFromString<File>(text)
        fun rows(table: List<List<Double>>) = table.map { Lms(it[0].toInt(), it[1], it[2], it[3]) }.sortedBy { it.month }
        mapOf(
            (GrowthMetric.WEIGHT to BabySex.BOY) to rows(file.weight.boys),
            (GrowthMetric.WEIGHT to BabySex.GIRL) to rows(file.weight.girls),
            (GrowthMetric.LENGTH to BabySex.BOY) to rows(file.length.boys),
            (GrowthMetric.LENGTH to BabySex.GIRL) to rows(file.length.girls),
            (GrowthMetric.HEAD to BabySex.BOY) to rows(file.head.boys),
            (GrowthMetric.HEAD to BabySex.GIRL) to rows(file.head.girls),
        )
    }

    const val MAX_MONTHS = 24

    /** Average month length WHO uses to turn days into months. */
    const val DAYS_PER_MONTH = 30.4375

    fun lms(metric: GrowthMetric, sex: BabySex, ageMonths: Double): Lms? {
        val rows = tables.getValue(metric to sex)
        if (ageMonths < 0 || ageMonths > rows.last().month) return null
        val lower = rows.last { it.month <= ageMonths }
        val upper = rows.firstOrNull { it.month >= ageMonths } ?: lower
        if (upper.month == lower.month) return lower
        val t = (ageMonths - lower.month) / (upper.month - lower.month)
        fun mix(a: Double, b: Double) = a + (b - a) * t
        return Lms(lower.month, mix(lower.l, upper.l), mix(lower.m, upper.m), mix(lower.s, upper.s))
    }

    /** The z-score of [value] (kg or cm), WHO's Box-Cox formula. */
    fun zScore(p: Lms, value: Double): Double =
        if (abs(p.l) < 1e-9) ln(value / p.m) / p.s else ((value / p.m).pow(p.l) - 1) / (p.l * p.s)

    /** The measurement at z-score [z]. */
    fun valueAt(p: Lms, z: Double): Double =
        if (abs(p.l) < 1e-9) p.m * exp(p.s * z) else p.m * (1 + p.l * p.s * z).pow(1 / p.l)

    /** 0–100, or null past the standards' range. */
    fun percentile(metric: GrowthMetric, sex: BabySex, ageMonths: Double, value: Double): Double? {
        val p = lms(metric, sex, ageMonths) ?: return null
        return (normalCdf(zScore(p, value)) * 100).coerceIn(0.0, 100.0)
    }

    /** The 3rd, 15th, 50th, 85th and 97th percentile curves, one point per month. */
    fun curves(metric: GrowthMetric, sex: BabySex): List<GrowthCurvePoint> =
        tables.getValue(metric to sex).map { p ->
            GrowthCurvePoint(
                month = p.month,
                p3 = valueAt(p, Z3),
                p15 = valueAt(p, Z15),
                p50 = p.m,
                p85 = valueAt(p, -Z15),
                p97 = valueAt(p, -Z3),
            )
        }

    /** The z-scores of the 3rd and 15th percentiles; the 85th and 97th are their mirror. */
    private const val Z3 = -1.880793608
    private const val Z15 = -1.036433389

    /** The standard normal CDF (Abramowitz & Stegun 7.1.26 via erf), ample for a percentile shown as a whole number. */
    fun normalCdf(z: Double): Double = 0.5 * (1 + erf(z / sqrt(2.0)))

    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-ax * ax)
        return sign * y
    }
}
