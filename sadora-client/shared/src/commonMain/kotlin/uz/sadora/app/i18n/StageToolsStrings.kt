package uz.sadora.app.i18n

import uz.sadora.contract.FeedingSide
import uz.sadora.contract.GoalsBasis
import uz.sadora.contract.HotFlushTrigger

/**
 * The words of the stage tools: the conception window, the prompts that move her on to
 * the next stage, the doctor flags, the feeding log, the mood questionnaire, the kick
 * counter, the contraction timer and the hot-flush log.
 *
 * Kept apart from [JourneyStrings] because they are tools with their own screens, and
 * that interface is already the longest in the app.
 */
interface StageToolsStrings {

    // ---- trying to conceive
    val fertileTodayTitle: String
    fun fertileTodayBody(ovulation: String): String
    fun fertileInDays(days: Int): String
    fun fertileWindow(from: String, to: String, ovulation: String): String
    val fertilePassedTitle: String
    fun fertilePassedBody(nextPeriod: String): String
    fun periodLate(days: Int): String
    val periodLateBody: String
    /** The same lateness outside trying to conceive, where a test is one possibility of several. */
    val periodLateCycleBody: String
    val pregnantButton: String
    val periodStartedButton: String
    /** Under the due-date picker, from the last period she recorded. */
    fun dueFromPeriod(date: String): String

    // ---- moving on to the next stage
    val birthPromptTitle: String
    val birthPromptBody: String
    val birthPromptButton: String
    val cycleBackTitle: String
    val cycleBackBody: String
    val cycleBackButton: String

    // ---- doctor flags
    val flagsTitle: String
    fun shortCycles(days: Int): String
    fun longCycles(days: Int): String
    fun irregularCycles(spread: Int): String
    fun longPeriods(days: Int): String
    val bleedingAfterMenopause: String
    val flagsNote: String
    val menopauseBleedingTitle: String
    val menopauseBleedingBody: String
    val menopauseBleedingButton: String

    // ---- nutrition
    fun goalsBasis(basis: GoalsBasis): String

    // ---- feeding
    val feedingTitle: String
    val feedingIntro: String
    fun side(side: FeedingSide): String
    fun lastFeed(ago: String, side: String): String
    fun feedsToday(count: Int): String
    val noFeedsToday: String
    val startFeed: String
    val stopFeed: String
    fun feedRunning(side: String): String
    val bottleTitle: String
    val millilitres: String
    val saveBottle: String
    val nothingToday: String

    // ---- mood questionnaire
    val screenTitle: String
    val screenCardTitle: String
    val screenCardBody: String
    fun lastScreen(date: String, score: Int): String
    val screenIntro: String
    val questions: List<String>
    /** Four per question, in the questionnaire's own order. */
    val options: List<List<String>>
    val screenSubmit: String
    fun screenScore(score: Int): String
    val screenLow: String
    val screenPossible: String
    val screenLikely: String
    val screenSelfHarm: String
    val askDoctor: String
    val screenSource: String
    fun answeredOf(answered: Int, total: Int): String

    // ---- kick counter
    val kicksTitle: String
    val kicksCardBody: String
    val kicksIntro: String
    val kickTap: String
    fun kicksCount(count: Int, goal: Int): String
    val kicksFinish: String
    fun kicksResult(count: Int, duration: String): String
    val kicksSlow: String
    val previousCounts: String

    // ---- contractions
    val contractionsTitle: String
    val contractionsCardBody: String
    val contractionsIntro: String
    val contractionStart: String
    val contractionStop: String
    fun contractionLasted(duration: String): String
    fun contractionApart(interval: String): String
    fun contractionsSummary(count: Int, duration: String, interval: String): String
    val contractionsGo: String
    val contractionsUrgent: String
    fun minutesSeconds(minutes: Int, seconds: Int): String

    // ---- hot flushes
    val hotFlushTitle: String
    val hotFlushLog: String
    fun intensity(level: Int): String
    fun trigger(trigger: HotFlushTrigger): String
    val triggerQuestion: String
    fun hotFlushCounts(today: Int, week: Int): String
    fun commonTrigger(name: String): String
    val hotFlushSaved: String
    fun ago(hours: Int, minutes: Int): String
}
