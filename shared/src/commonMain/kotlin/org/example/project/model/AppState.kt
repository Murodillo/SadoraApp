package org.example.project.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn

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

/** The fertile window the app assumes when the server has not supplied one. */
private val AssumedFertileCycleDays = 12..16

/**
 * The five moods, worst first.
 *
 * [caption] is the one-line reading the Mind screen shows under the big face, and
 * [faceIndex] picks which of the deck's five coloured faces represents it.
 */
enum class Mood(val emoji: String, val label: String, val score: Int, val caption: String) {
    Bad("😞", "Og'ir", 1, "Bugun o'zingizga mehribon bo'ling."),
    Low("😕", "So'lg'in", 2, "Sekinroq kun — bu ham normal."),
    Ok("😐", "O'rtacha", 3, "Muvozanat uchun oddiy kun."),
    Good("🙂", "Xotirjam", 4, "Muvozanat uchun yaxshi kun."),
    Great("😄", "Ajoyib", 5, "Energiyangiz yuqori — foydalaning!");

    companion object {
        fun forScore(score: Int): Mood = entries.firstOrNull { it.score == score } ?: Ok
    }
}

/** Today, in the device's own zone. */
fun deviceToday(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/** "Xayrli tong" until noon, "Xayrli kun" until six, "Xayrli kech" after. */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Xayrli tong"
    in 12..17 -> "Xayrli kun"
    else -> "Xayrli kech"
}

/**
 * Single in-memory store for the whole app.
 *
 * Screens read and write here directly; the server's answers are mirrored onto it by
 * the data layer, and local edits leave through [sync]. Everything is Compose state,
 * so any mutation recomposes the affected screens.
 */
class AppState {
    // ---- account / onboarding ----
    var language by mutableStateOf(AppLanguage.Uz)
    // Blank until she answers the first question. A prefilled name would be answered
    // for her, and the onboarding greets people by it.
    var name by mutableStateOf("")
    var email by mutableStateOf("malika@example.com")
    var phone by mutableStateOf("")
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

    /** The child's birth date, for the postpartum stage. */
    var childBirthDate by mutableStateOf<LocalDate?>(null)
    var babyBirthDate by mutableStateOf<LocalDate?>(null)

    /** "Did a doctor recommend SADORA?" — null until answered, and null when skipped. */
    var referredByDoctor by mutableStateOf<Boolean?>(null)

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

    // ---- what the server has switched on ----
    // Both default to open: a phone that has not heard from the server yet shows the
    // app whole, and the server's flags close a section rather than open one.
    var communityEnabled by mutableStateOf(true)
    var aiChatEnabled by mutableStateOf(true)

    // ---- the day ----
    /**
     * The day every "bugun" on screen refers to.
     *
     * Starts as the device's date and is replaced by the server's once the cycle
     * status loads, so the calendar and the ring agree with the backend about which
     * day it is even across a midnight the phone crossed while offline.
     */
    var today by mutableStateOf(deviceToday())

    // ---- cycle ----
    var cycleDay by mutableStateOf(14)
    var averageCycleLength by mutableStateOf(28)
    var averagePeriodLength by mutableStateOf(5)
    var pregnancyWeek by mutableStateOf(24)
    var postpartumWeek by mutableStateOf(7)

    /**
     * The first day of the current cycle.
     *
     * Kept separately from [markedPeriodDays] because those are handed to the server
     * and cleared once onboarding finishes, and the calendar still needs an anchor to
     * colour the days around today from.
     */
    var cycleStartDate by mutableStateOf<LocalDate?>(null)

    /** The server's phase for today, when it has given one. */
    var cyclePhase by mutableStateOf<CyclePhase?>(null)
    var daysUntilNextPeriod by mutableStateOf<Int?>(null)
    var fertileFrom by mutableStateOf<LocalDate?>(null)
    var fertileUntil by mutableStateOf<LocalDate?>(null)

    /**
     * False when the server has said it cannot predict yet — one data point, a stage
     * that does not cycle. The screens then say so instead of drawing a confident ring.
     */
    var hasCyclePrediction by mutableStateOf(true)

    // ---- daily data ----
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

    /**
     * True once she has answered the onboarding "how do you feel" question. [mood] has
     * a default, so without this the request could not tell an answer from the default.
     */
    var moodAnswered by mutableStateOf(false)

    /** 1–5, the Mind tab's second and third dials. Stress 5 is the most stressed. */
    var energy by mutableStateOf(4)
    var stress by mutableStateOf(2)

    var steps by mutableStateOf(6420)
    var sleepMinutes by mutableStateOf(400) // 6s 40d

    /** Seconds of breathing and meditation practised today. */
    var practiceSecondsToday by mutableStateOf(0)

    /**
     * The journal, newest first.
     *
     * Held here like every other health record so the screen reads the store rather than
     * the network, and so an entry she has just written is on screen before the server
     * has confirmed it.
     */
    val journal = mutableStateListOf<JournalNote>()

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

    /** The alias she posts under, once the server has assigned one. */
    var communityAlias by mutableStateOf<String?>(null)
    var communityTint by mutableStateOf(0)

    /** Set once there is a backend; every community edit below reports through it. */
    var communitySync: CommunitySync? = null

    /** Everyone else's likes plus hers, so the count moves the instant she taps. */
    fun likeCount(post: CommunityPost): Int =
        post.likes + if (post.id in likedPosts) 1 else 0

    fun commentsOf(post: CommunityPost): List<CommunityComment> =
        post.comments + ownComments[post.id].orEmpty()

    /** What the card shows: the server's count until the sheet has loaded the comments. */
    fun commentCountOf(post: CommunityPost): Int =
        maxOf(post.commentCount, post.comments.size) + ownComments[post.id].orEmpty().size

    fun toggleLike(postId: String) {
        val liked = !likedPosts.remove(postId)
        if (liked) likedPosts.add(postId)
        communitySync?.postLiked(postId, liked)
    }

    fun toggleSaved(postId: String) {
        val saved = !savedPosts.remove(postId)
        if (saved) savedPosts.add(postId)
        communitySync?.postSaved(postId, saved)
    }

    fun addComment(postId: String, body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        ownComments.getOrPut(postId) { mutableStateListOf() }
            .add(CommunityComment(alias = communityAlias ?: "Siz", tint = communityTint, ago = "hozir", body = text, isMine = true))
        communitySync?.commentAdded(postId, text)
    }

    /**
     * A new post. With a backend the feed is refreshed from the server's answer; without
     * one it goes straight to the top of the sample feed so the prototype still works.
     */
    fun createPost(topic: CommunityTopic, body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        val sync = communitySync
        if (sync != null) {
            sync.postCreated(topic, text)
            return
        }
        communityPosts.add(
            0,
            CommunityPost(
                id = "local-${communityPosts.size + 1}",
                alias = communityAlias ?: "Siz",
                tint = communityTint,
                topic = topic,
                ago = "hozir",
                body = text,
                likes = 0,
                isMine = true,
            ),
        )
    }

    fun deletePost(postId: String) {
        communityPosts.removeAll { it.id == postId }
        likedPosts.remove(postId)
        savedPosts.remove(postId)
        ownComments.remove(postId)
        communitySync?.postDeleted(postId)
    }

    fun reportPost(postId: String, reason: ReportReason, note: String?) {
        communitySync?.postReported(postId, reason, note)
    }

    /** The server's feed, replacing the samples and whatever she tapped before it arrived. */
    fun replaceCommunityFeed(posts: List<CommunityPost>, liked: Set<String>, saved: Set<String>) {
        // Comments loaded for a post survive the refresh, or the sheet would blank on every like.
        val keptComments = communityPosts.associate { it.id to it.comments }
        communityPosts.clear()
        communityPosts.addAll(posts.map { post -> post.copy(comments = keptComments[post.id].orEmpty()) })
        likedPosts.clear()
        likedPosts.addAll(liked)
        savedPosts.clear()
        savedPosts.addAll(saved)
    }

    /** The server's comments for one post; her optimistic ones are now among them. */
    fun replaceComments(postId: String, comments: List<CommunityComment>) {
        val index = communityPosts.indexOfFirst { it.id == postId }
        if (index >= 0) {
            communityPosts[index] = communityPosts[index].copy(comments = comments, commentCount = comments.size)
        }
        ownComments.remove(postId)
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

    /**
     * The marked periods as start..end ranges, clearing them as they are taken.
     *
     * The most recent start survives as [cycleStartDate]: the days go to the server,
     * but the calendar on the very next screen still needs to know where the cycle
     * began.
     */
    fun takeMarkedPeriods(): List<ClosedRange<LocalDate>> {
        val runs = periodRuns().map { it.first()..it.last() }
        runs.lastOrNull()?.let { cycleStartDate = it.start }
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
        val start = lastPeriodStart ?: cycleStartDate ?: return
        val elapsed = start.daysUntil(today)
        if (elapsed < 0) return
        this.today = today
        cycleStartDate = start
        val length = averageCycleLength.coerceAtLeast(1)
        cycleDay = elapsed % length + 1
        // The server has not spoken yet, so anything it would have said is unknown.
        cyclePhase = null
        daysUntilNextPeriod = null
        fertileFrom = null
        fertileUntil = null
    }

    // ---- cycle, derived ----

    /** Which phase a given cycle day falls in, from the averages alone. */
    fun phaseForCycleDay(day: Int): CyclePhase = when {
        day <= averagePeriodLength -> CyclePhase.Period
        day in AssumedFertileCycleDays -> CyclePhase.Fertile
        day < AssumedFertileCycleDays.first -> CyclePhase.Follicular
        else -> CyclePhase.Luteal
    }

    /** Today's phase: the server's answer, or the local estimate until it arrives. */
    fun currentPhase(): CyclePhase = cyclePhase ?: phaseForCycleDay(cycleDay)

    /**
     * The cycle day a date would be, counting from [cycleStartDate] and wrapping every
     * [averageCycleLength] days in both directions.
     *
     * Null when there is no anchor — the calendar then draws a plain month rather than
     * a guess, which is what the design rules require of an unmarked prediction.
     */
    fun cycleDayFor(date: LocalDate): Int? {
        val start = cycleStartDate ?: return null
        val length = averageCycleLength.coerceAtLeast(1)
        val elapsed = start.daysUntil(date)
        return ((elapsed % length) + length) % length + 1
    }

    fun phaseForDate(date: LocalDate): CyclePhase? = cycleDayFor(date)?.let(::phaseForCycleDay)

    /** The predicted first day of the next period. */
    fun nextPeriodStart(): LocalDate? {
        daysUntilNextPeriod?.let { return today.plus(it, DateTimeUnit.DAY) }
        val length = averageCycleLength.coerceAtLeast(1)
        return today.plus(length - cycleDay + 1, DateTimeUnit.DAY)
    }

    /** Days from today until [nextPeriodStart]. */
    fun daysToNextPeriod(): Int = daysUntilNextPeriod ?: (today.daysUntil(nextPeriodStart() ?: today))

    /** The fertile window as cycle days, from the server or the assumed window. */
    fun fertileWindowDays(): IntRange {
        val from = fertileFrom?.let(::cycleDayFor)
        val until = fertileUntil?.let(::cycleDayFor)
        return if (from != null && until != null && from <= until) from..until else AssumedFertileCycleDays
    }

    /** Whether a date is inside the fertile window. */
    fun isFertile(date: LocalDate): Boolean {
        val from = fertileFrom
        val until = fertileUntil
        if (from != null && until != null) return date in from..until
        return cycleDayFor(date)?.let { it in AssumedFertileCycleDays } ?: false
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

    /** Millilitres still to drink; never negative once the goal is passed. */
    val waterRemainingMl: Int get() = (waterGoalMl - waterMl).coerceAtLeast(0)

    fun markMedicationTaken(id: String) {
        setMedicationStatus(id, MedStatus.Taken)
        sync?.doseTaken(id)
    }

    fun markMedicationSkipped(id: String) {
        setMedicationStatus(id, MedStatus.Skipped)
        sync?.doseSkipped(id)
    }

    private fun setMedicationStatus(id: String, status: MedStatus) {
        val index = medications.indexOfFirst { it.id == id }
        if (index >= 0) medications[index] = medications[index].copy(status = status)
    }

    /** "1 / 2" — doses confirmed against doses due today. */
    val dosesTaken: Int get() = medications.count { it.status == MedStatus.Taken }
    val dosesDue: Int get() = medications.size

    fun logMeal(meal: Meal) {
        meals.add(meal)
        caloriesEaten += meal.calories
        proteinG += meal.protein
        fatG += meal.fat
        carbsG += meal.carbs
        sync?.mealLogged(meal)
    }

    /**
     * The Mind check-in. The three dials are saved as one record, so changing any of
     * them sends all three — the server replaces the day's check-in wholesale.
     */
    fun setCheckIn(mood: Mood = this.mood, energy: Int = this.energy, stress: Int = this.stress) {
        this.mood = mood
        this.energy = energy.coerceIn(1, 5)
        this.stress = stress.coerceIn(1, 5)
        sync?.checkInChanged(this.mood, this.energy, this.stress)
    }

    fun logPractice(kind: PracticeKind, seconds: Int) {
        if (seconds <= 0) return
        practiceSecondsToday += seconds
        sync?.practiceLogged(kind, seconds)
    }

    /**
     * Writes a journal entry.
     *
     * The entry appears at the top immediately under a local id; the server's copy
     * replaces the whole list on the next refresh, which is when it gains its real one.
     * Writing is the one thing in this app that must never feel like it is waiting.
     */
    fun addJournalNote(body: String) {
        val text = body.trim()
        if (text.isEmpty()) return
        journal.add(0, JournalNote(id = LOCAL_NOTE_ID, date = today, time = nowTimeLabel(), body = text))
        sync?.journalSaved(text)
    }

    /** Removes an entry. A note that never reached the server has nothing to delete there. */
    fun deleteJournalNote(note: JournalNote) {
        journal.remove(note)
        if (note.id != LOCAL_NOTE_ID) sync?.journalDeleted(note.id)
    }

    /** "6s 40d" — the app's sleep-duration format. */
    fun sleepLabel(minutes: Int = sleepMinutes): String = "${minutes / 60}s ${minutes % 60}d"
}

/**
 * One journal entry as a screen shows it.
 *
 * [time] is formatted where the entry is mapped rather than where it is drawn: the wire
 * carries an instant, and turning that into a wall clock needs the device's zone, which
 * is a data-layer concern.
 */
data class JournalNote(
    val id: String,
    val date: LocalDate,
    val time: String,
    val body: String,
)

/** The id an entry carries until the server has given it a real one. */
const val LOCAL_NOTE_ID: String = "local"
