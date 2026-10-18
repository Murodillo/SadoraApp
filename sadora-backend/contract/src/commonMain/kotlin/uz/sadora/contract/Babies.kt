package uz.sadora.contract

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Her babies — one, or twins, or a toddler and a newborn — and what she tracks for each:
 * feeds, sleeps and nappies (as [StageEvent]s carrying the baby's id), growth against the
 * WHO standards, the national vaccine calendar, and the milestones of the first two years.
 *
 * The sex is asked for one reason only: the WHO growth curves differ for girls and boys.
 */
@Serializable
data class Baby(
    val id: String,
    val name: String,
    val sex: BabySex,
    val birthDate: LocalDate,
    /** Grams, as the maternity ward wrote it. */
    val birthWeightG: Int? = null,
    val birthLengthCm: Double? = null,
    val createdAt: Instant,
)

@Serializable
enum class BabySex {
    @SerialName("girl") GIRL,
    @SerialName("boy") BOY,
}

@Serializable
data class SaveBabyRequest(
    val name: String,
    val sex: BabySex,
    val birthDate: LocalDate,
    val birthWeightG: Int? = null,
    val birthLengthCm: Double? = null,
)

/** One calendar day of a baby, her time: what the daily strip and the week's bars draw. */
@Serializable
data class BabyDay(
    val date: LocalDate,
    val feeds: Int = 0,
    val breastMinutes: Int = 0,
    val bottleMl: Int = 0,
    /** Sleep that fell inside this day, a night split at midnight. */
    val sleepMinutes: Int = 0,
    val wetDiapers: Int = 0,
    val dirtyDiapers: Int = 0,
)

/** The front page of one baby: the last few days, what is running now, and what is next. */
@Serializable
data class BabyOverview(
    val baby: Baby,
    /** Oldest first, today last. */
    val days: List<BabyDay> = emptyList(),
    val lastFeed: StageEvent? = null,
    val lastDiaper: StageEvent? = null,
    /** The sleep running now, if one is. */
    val sleeping: StageEvent? = null,
    val lastSleepEndedAt: Instant? = null,
    val latestGrowth: GrowthEntry? = null,
    /** The next dose not given yet, overdue ones first. */
    val nextVaccine: VaccineDose? = null,
    val milestonesReached: Int = 0,
    val milestonesTotal: Int = 0,
)

// ---------------------------------------------------------------- growth

@Serializable
enum class GrowthMetric {
    @SerialName("weight") WEIGHT,
    @SerialName("length") LENGTH,
    @SerialName("head") HEAD,
}

/** One visit to the scales: any of the three may be missing. */
@Serializable
data class GrowthEntry(
    val id: String,
    val measuredOn: LocalDate,
    val ageDays: Int,
    val weightG: Int? = null,
    val lengthCm: Double? = null,
    val headCm: Double? = null,
    val note: String? = null,
)

@Serializable
data class SaveGrowthRequest(
    val measuredOn: LocalDate,
    val weightG: Int? = null,
    val lengthCm: Double? = null,
    val headCm: Double? = null,
    val note: String? = null,
)

/**
 * One metric against the WHO Child Growth Standards: the 3rd, 15th, 50th, 85th and 97th
 * percentile curves month by month to two years, and her measurements with the
 * percentile each one falls on. Premium — the measurements themselves are not.
 */
@Serializable
data class GrowthChart(
    val metric: GrowthMetric,
    val sex: BabySex,
    /** kg for weight, cm for length and head. */
    val unit: String,
    val curves: List<GrowthCurvePoint>,
    val points: List<GrowthChartPoint>,
)

@Serializable
data class GrowthCurvePoint(
    val month: Int,
    val p3: Double,
    val p15: Double,
    val p50: Double,
    val p85: Double,
    val p97: Double,
)

@Serializable
data class GrowthChartPoint(
    val measuredOn: LocalDate,
    /** Age in months, fractional: 2.5 is two and a half months. */
    val ageMonths: Double,
    val value: Double,
    /** 0–100; null past the standards' two years. */
    val percentile: Double? = null,
)

// ---------------------------------------------------------------- vaccines

@Serializable
enum class VaccineStatus {
    /** Given — [VaccineDose.givenOn] says when. */
    @SerialName("done") DONE,
    /** Past its date and not marked given. */
    @SerialName("overdue") OVERDUE,
    /** Due within the next fortnight. */
    @SerialName("due") DUE,
    @SerialName("upcoming") UPCOMING,
}

/** One dose of the national calendar, dated from the baby's birth. */
@Serializable
data class VaccineDose(
    val code: String,
    val name: String,
    /** "Tug'ilganda", "2 oylik", "6 yosh". */
    val ageLabel: String,
    val dueOn: LocalDate,
    val status: VaccineStatus,
    val givenOn: LocalDate? = null,
    val note: String? = null,
)

@Serializable
data class MarkVaccineRequest(val givenOn: LocalDate)

// ---------------------------------------------------------------- milestones

/**
 * A milestone of the first two years. The window is when most babies reach it — the WHO
 * motor milestones where there is one — and the screen says so in exactly those words:
 * a baby outside it is a question for her doctor, never a verdict from the app.
 */
@Serializable
data class Milestone(
    val code: String,
    val name: String,
    val about: String,
    /** "skills", "social", "language", "teeth". */
    val group: String,
    val fromMonth: Int,
    val toMonth: Int,
    val achievedOn: LocalDate? = null,
    val note: String? = null,
    val hasPhoto: Boolean = false,
)

/** Marks one reached; the photo is optional and replaces any earlier one. */
@Serializable
data class MarkMilestoneRequest(
    val achievedOn: LocalDate,
    val note: String? = null,
    val photo: LabPhotoUpload? = null,
)
