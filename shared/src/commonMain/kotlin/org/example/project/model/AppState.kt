package org.example.project.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class AppLanguage(val code: String, val native: String, val english: String) {
    Uz("UZ", "O'zbekcha", "Uzbek"),
    Ru("RU", "Русский", "Russian"),
    En("EN", "English", "English"),
}

/** The eight onboarding goals. Selected goals surface first on the Today screen. */
enum class Goal(val label: String) {
    UnderstandCycle("Siklni tushunish"),
    SleepBetter("Yaxshi uxlash"),
    MoreEnergy("Energiyani oshirish"),
    LessStress("Stressni kamaytirish"),
    EatBalanced("Muvozanatli ovqatlanish"),
    DrinkWater("Ko'proq suv ichish"),
    BeActive("Faolroq bo'lish"),
    RememberMeds("Dorilarni eslab qolish"),
}

/**
 * How long she has been trying to conceive.
 *
 * Asked only of that life stage, and only after the sensitive-topic notice.
 */
enum class ConceptionWindow(val label: String) {
    JustStarted("Endi boshladim"),
    UnderThreeMonths("3 oygacha"),
    ThreeToSix("3–6 oy"),
    SixToTwelve("6–12 oy"),
    OverAYear("Bir yildan ko'p"),
}

/**
 * Contraception used in the last six months.
 *
 * It changes predictions rather than describing her: hormonal methods suppress
 * ovulation, so the first cycles after stopping one are not a baseline worth
 * predicting from, and the app should say so instead of guessing confidently.
 */
enum class BirthControl(val label: String) {
    None("Yo'q"),
    StillUsing("Hozir ham ishlatyapman"),
    Pill("Ha, tabletka"),
    Iud("Ha, spiral (IUD)"),
    Barrier("Ha, prezervativ yoki boshqa nogormonal usul"),
    Other("Ha, boshqa usul"),
    Undisclosed("Aytishni xohlamayman"),
}

/** How many period starts the onboarding calendar collects. */
const val MaxEnteredCycles = 3

/** Gaps outside this range are mistaps, not cycles. Mirrors the server's own filter. */
private val PlausibleCycleDays = 15..60

enum class Mood(val emoji: String, val label: String, val score: Int) {
    Bad("😞", "Yomon", 1),
    Low("😕", "So'lg'in", 2),
    Ok("😐", "O'rtacha", 3),
    Good("🙂", "Yaxshi", 4),
    Great("😄", "Ajoyib", 5),
}

/**
 * Single in-memory store for the whole prototype.
 *
 * There is no backend in this project yet, so screens read and write here directly.
 * Everything is Compose state, so any mutation recomposes the affected screens.
 */
class AppState {
    // ---- account / onboarding ----
    var language by mutableStateOf(AppLanguage.Uz)
    // Blank until she answers the first question. A prefilled name would be answered
    // for her, and the onboarding greets people by it.
    var name by mutableStateOf("")
    var email by mutableStateOf("malika@example.com")
    var phone by mutableStateOf("90 123 45 67")
    var birthDate by mutableStateOf("14.03.1994")
    var heightCm by mutableStateOf("164")
    var weightKg by mutableStateOf("58")
    var lifeStage by mutableStateOf(LifeStage.Cycle)
    // Empty until the onboarding grid is answered, for the same reason [name] is blank.
    val goals = mutableStateListOf<Goal>()

    // ---- cycle baseline, answered during onboarding ----
    /**
     * Every day she has marked as a period day, in no particular order.
     *
     * Days rather than starts, because a period is a span she edits: the first tap
     * fills in a typical length as a convenience, and she is then free to shorten it,
     * extend it, or cut it down to the single day she is sure of. Empty means "not
     * answered", which stays distinguishable from any marking.
     */
    val markedPeriodDays = mutableStateListOf<LocalDate>()

    /**
     * The marked days grouped into periods — one run of consecutive days each, oldest
     * first.
     *
     * Deriving the periods from the days rather than storing them separately is what
     * lets a single tap edit any day without the two representations drifting apart.
     */
    fun periodRuns(): List<List<LocalDate>> {
        val sorted = markedPeriodDays.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val runs = mutableListOf(mutableListOf(sorted.first()))
        sorted.zipWithNext { previous, day ->
            if (previous.plus(1, DateTimeUnit.DAY) == day) {
                runs.last().add(day)
            } else {
                runs.add(mutableListOf(day))
            }
        }
        return runs
    }

    /** The first day of each marked period, oldest first. */
    val recentPeriodStarts: List<LocalDate> get() = periodRuns().map { it.first() }

    /** The anchor the baseline carries: the most recent period's first day. */
    val lastPeriodStart: LocalDate? get() = recentPeriodStarts.lastOrNull()

    fun isPeriodDay(date: LocalDate): Boolean = date in markedPeriodDays

    var cycleIsRegular by mutableStateOf(true)
    var conceptionWindow by mutableStateOf<ConceptionWindow?>(null)
    var birthControl by mutableStateOf<BirthControl?>(null)

    /** Pregnancy due date, and the birth date behind a postpartum stage. */
    var dueDate by mutableStateOf<LocalDate?>(null)
    var babyBirthDate by mutableStateOf<LocalDate?>(null)

    var notificationsAllowed by mutableStateOf(true)
    var healthDataAllowed by mutableStateOf(true)
    var cameraAllowed by mutableStateOf(false)

    // The consent gate in front of onboarding is what turns these on, so they start
    // off: a box that arrives pre-ticked is not consent, it is a default.
    var consentStoreHealth by mutableStateOf(false)
    var consentAiInsights by mutableStateOf(true)
    var consentAnalytics by mutableStateOf(false)

    /** Acceptance of the Terms of Use and the Privacy Policy. Required to continue. */
    var consentTerms by mutableStateOf(false)

    // ---- subscription ----
    /**
     * Free until the server says otherwise.
     *
     * The prototype defaulted to true so the Premium screens were visible; with real
     * entitlements behind it that default would show paid content to someone who has
     * not paid, so the tier now only ever comes from [Entitlements].
     */
    var isPremium by mutableStateOf(false)
    var premiumRenewal by mutableStateOf("14-mart 2027-yilgacha")

    // ---- appearance ----
    var darkTheme by mutableStateOf(false)

    // ---- daily data ----
    var cycleDay by mutableStateOf(14)
    var averageCycleLength by mutableStateOf(28)
    var averagePeriodLength by mutableStateOf(5)
    var pregnancyWeek by mutableStateOf(24)
    var postpartumWeek by mutableStateOf(7)

    var waterMl by mutableStateOf(1200)
    var waterGoalMl by mutableStateOf(2000)

    var caloriesEaten by mutableStateOf(1240)
    var calorieGoal by mutableStateOf(1850)
    var proteinG by mutableStateOf(61)
    var proteinGoalG by mutableStateOf(85)
    var fatG by mutableStateOf(38)
    var fatGoalG by mutableStateOf(62)
    var carbsG by mutableStateOf(132)
    var carbsGoalG by mutableStateOf(210)

    /**
     * True until the user has logged anything. Drives Today's empty state — the
     * fourth Today state in the design, alongside free, premium and skeleton.
     */
    var isNewUser by mutableStateOf(false)

    var mood by mutableStateOf(Mood.Good)
    var steps by mutableStateOf(6420)
    var sleepMinutes by mutableStateOf(400) // 6s 40d

    // Filled by the onboarding check-in, then by the symptom sheet.
    val symptoms = mutableStateListOf<String>()
    val meals = mutableStateListOf(*SampleData.meals.toTypedArray())
    val medications = mutableStateListOf(*SampleData.medications.toTypedArray())

    // ---- secret chat ----
    /**
     * The feed, and what she has done to it.
     *
     * Her own likes, saves and comments are kept apart from the posts rather than
     * folded into them: a post is everyone's, and her reaction to it is only hers, so
     * the two have different owners the moment there is a server behind this.
     */
    val communityPosts = mutableStateListOf(*SampleData.communityPosts.toTypedArray())
    val likedPosts = mutableStateListOf<String>()
    val savedPosts = mutableStateListOf<String>()
    private val ownComments = mutableStateMapOf<String, SnapshotStateList<CommunityComment>>()

    var communityTopic by mutableStateOf(CommunityTopic.All)
    var communityFilter by mutableStateOf(CommunityFilter.Feed)

    /** Everyone else's likes plus hers, so the count moves the instant she taps. */
    fun likeCount(post: CommunityPost): Int =
        post.likes + if (post.id in likedPosts) 1 else 0

    fun commentsOf(post: CommunityPost): List<CommunityComment> =
        post.comments + ownComments[post.id].orEmpty()

    fun toggleLike(postId: String) {
        if (!likedPosts.remove(postId)) likedPosts.add(postId)
    }

    fun toggleSaved(postId: String) {
        if (!savedPosts.remove(postId)) savedPosts.add(postId)
    }

    fun addComment(postId: String, body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        ownComments.getOrPut(postId) { mutableStateListOf() }
            .add(CommunityComment(alias = "Siz", tint = 0, ago = "hozir", body = text))
    }

    /** The posts the feed should show, given the room and the saved filter. */
    fun visiblePosts(): List<CommunityPost> = communityPosts.filter { post ->
        val inTopic = communityTopic == CommunityTopic.All || post.topic == communityTopic
        val inFilter = communityFilter == CommunityFilter.Feed || post.id in savedPosts
        inTopic && inFilter
    }

    /**
     * Set once the app has a backend. Every mutation below reports through it, so the
     * screens stay unaware that anything is being synced.
     */
    var sync: AppStateSync? = null

    /**
     * Gaps between the entered period starts, in days.
     *
     * Implausible gaps are dropped rather than averaged in: a mistapped date would
     * otherwise drag the average somewhere no cycle goes, and the same filter runs on
     * the server, so the two agree on what counts.
     */
    fun observedCycleLengths(): List<Int> =
        recentPeriodStarts.sorted()
            .zipWithNext { earlier, later -> earlier.daysUntil(later) }
            .filter { it in PlausibleCycleDays }

    /** The average of [observedCycleLengths], or null until two dates are entered. */
    fun averageFromEnteredCycles(): Int? =
        observedCycleLengths().takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

    /**
     * Marks or unmarks one day of a period.
     *
     * Three cases, in the order someone actually uses them:
     *  - an unmarked day on its own starts a period, filling in
     *    [averagePeriodLength] days as a convenience — that is the common case, and it
     *    saves five taps;
     *  - an unmarked day touching an existing period joins it, so she can lengthen a
     *    period the fill-in got wrong;
     *  - a marked day is removed on its own, so she can shorten one, or cut it back to
     *    the single day she is sure of.
     *
     * The auto-fill is only ever a starting point. Nothing here is a fixed span, which
     * is why the days are stored rather than the starts.
     */
    fun togglePeriodDay(date: LocalDate, latestAllowed: LocalDate) {
        if (markedPeriodDays.remove(date)) return

        val touchesExisting = isPeriodDay(date.plus(1, DateTimeUnit.DAY)) ||
            isPeriodDay(date.minus(1, DateTimeUnit.DAY))
        if (touchesExisting) {
            markedPeriodDays.add(date)
            return
        }

        // A fresh period. Days already marked or still in the future are skipped rather
        // than filled, so the convenience never invents a day she did not bleed.
        val length = averagePeriodLength.coerceAtLeast(1)
        val days = (0 until length)
            .map { date.plus(it, DateTimeUnit.DAY) }
            .filter { it <= latestAllowed && !isPeriodDay(it) }
        markedPeriodDays.addAll(days)
        dropOldestPeriodsBeyondLimit()
    }

    /** Keeps at most [MaxEnteredCycles] periods, dropping the oldest whole runs. */
    private fun dropOldestPeriodsBeyondLimit() {
        var runs = periodRuns()
        while (runs.size > MaxEnteredCycles) {
            markedPeriodDays.removeAll(runs.first())
            runs = periodRuns()
        }
    }

    /** The marked periods as start..end ranges, clearing them as they are taken. */
    fun takeMarkedPeriods(): List<ClosedRange<LocalDate>> {
        val runs = periodRuns().map { it.first()..it.last() }
        markedPeriodDays.clear()
        return runs
    }

    /**
     * Recomputes the local cycle position from the onboarding answers.
     *
     * The server owns the real prediction, but it only answers on the next load, and
     * the first screen after onboarding is Today. Deriving the day here means that
     * screen is right immediately instead of showing a stale default until the first
     * sync lands — and the server's answer overwrites it as soon as it arrives.
     */
    fun recomputeCycleDay(today: LocalDate) {
        val start = lastPeriodStart ?: return
        val elapsed = start.daysUntil(today)
        if (elapsed < 0) return
        val length = averageCycleLength.coerceAtLeast(1)
        cycleDay = elapsed % length + 1
    }

    fun toggleGoal(goal: Goal) {
        if (!goals.remove(goal)) goals.add(goal)
    }

    fun toggleSymptom(symptom: String) {
        val added = !symptoms.remove(symptom)
        if (added) symptoms.add(symptom)
        sync?.symptomToggled(symptom, added)
    }

    fun addWater(ml: Int) {
        waterMl = (waterMl + ml).coerceAtLeast(0)
        sync?.waterAdded(ml)
    }

    fun markMedicationTaken(id: String) {
        val index = medications.indexOfFirst { it.id == id }
        if (index >= 0) medications[index] = medications[index].copy(status = MedStatus.Taken)
        sync?.doseTaken(id)
    }

    fun logMeal(meal: Meal) {
        meals.add(meal)
        caloriesEaten += meal.calories
        proteinG += meal.protein
        fatG += meal.fat
        carbsG += meal.carbs
        sync?.mealLogged(meal)
    }

    /**
     * Which phase a day of the current month falls in.
     *
     * Purely derived from the averages — the calendar marks anything after today as
     * predicted, so this never has to distinguish recorded from forecast itself.
     */
    fun phaseForDay(dayOfMonth: Int): CyclePhase {
        // Day 1 of the cycle fell on 6 August in the sample data.
        val cycleDayForDate = ((dayOfMonth - 6) % averageCycleLength + averageCycleLength) %
            averageCycleLength + 1
        return when {
            cycleDayForDate <= averagePeriodLength -> CyclePhase.Period
            cycleDayForDate in 12..16 -> CyclePhase.Fertile
            cycleDayForDate < 12 -> CyclePhase.Follicular
            else -> CyclePhase.Luteal
        }
    }

    /** "6s 40d" — the app's sleep-duration format. */
    fun sleepLabel(minutes: Int = sleepMinutes): String = "${minutes / 60}s ${minutes % 60}d"
}
