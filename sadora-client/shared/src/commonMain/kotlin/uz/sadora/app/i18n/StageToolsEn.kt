package uz.sadora.app.i18n

import uz.sadora.contract.FeedingSide
import uz.sadora.contract.GoalsBasis
import uz.sadora.contract.HotFlushTrigger

object StageToolsEn : StageToolsStrings {

    override val fertileTodayTitle = "Your fertile days are now"
    override fun fertileTodayBody(ovulation: String) =
        "Ovulation around $ovulation. These are the days you're most likely to conceive."
    override fun fertileInDays(days: Int) = if (days == 1) "Fertile window starts tomorrow" else "Fertile window in $days days"
    override fun fertileWindow(from: String, to: String, ovulation: String) =
        "$from – $to, ovulation around $ovulation"
    override val fertilePassedTitle = "The fertile window has passed"
    override fun fertilePassedBody(nextPeriod: String) =
        "Your next period is expected around $nextPeriod. If it's late, take a pregnancy test."
    override fun periodLate(days: Int) = if (days == 1) "Your period is 1 day late" else "Your period is $days days late"
    override val periodLateBody =
        "Time for a pregnancy test — first-morning urine gives the clearest result."
    override val periodLateCycleBody =
        "A late period can come from stress, illness or pregnancy. If it's over a week, take a test or see a doctor."
    override val pregnantButton = "I'm pregnant"
    override val periodStartedButton = "My period started"
    override fun dueFromPeriod(date: String) = "From your last period: $date"

    override val birthPromptTitle = "Has your baby arrived?"
    override val birthPromptBody =
        "Move on to the postpartum stage for recovery, feeding and mood tracking."
    override val birthPromptButton = "Move to postpartum"
    override val lossButton = "My pregnancy ended differently"
    override val lossTitle = "We're so sorry"
    override val lossBody =
        "We're so sorry. If you'd like, we'll go back to cycle tracking and turn off pregnancy reminders. Talking to a doctor can help too."
    override val lossConfirm = "Back to cycle tracking"
    override val lossLater = "Not now"
    override val cycleBackTitle = "Back to cycle tracking?"
    override val cycleBackBody =
        "If your period has come back, cycle tracking will predict the next one and your fertile days."
    override val cycleBackButton = "Move to cycle tracking"

    override val flagsTitle = "Worth raising with a doctor"
    override fun shortCycles(days: Int) = "Your cycles average $days ${en(days, "day", "days")} — shorter than 21."
    override fun longCycles(days: Int) = "Your cycles average $days ${en(days, "day", "days")} — longer than 35."
    override fun irregularCycles(spread: Int) = "Your cycle lengths vary by up to $spread ${en(spread, "day", "days")}."
    override fun longPeriods(days: Int) = "Your periods average $days ${en(days, "day", "days")} — longer than 7."
    override val bleedingAfterMenopause =
        "Bleeding after menopause was recorded (in the last 12 months). Any such bleeding should be seen by a doctor."
    override val flagsNote = "This isn't a diagnosis — just a sign in your records."
    override val menopauseBleedingTitle = "Bleeding"
    override val menopauseBleedingBody =
        "Any bleeding after menopause, even a little, is a reason to see a doctor. Mark the day here."
    override val menopauseBleedingButton = "Mark bleeding"

    override fun goalsBasis(basis: GoalsBasis) = when (basis) {
        GoalsBasis.PREGNANCY_FIRST_TRIMESTER -> "Targets set for the first trimester of pregnancy"
        GoalsBasis.PREGNANCY_SECOND_TRIMESTER -> "Targets set for the second trimester of pregnancy"
        GoalsBasis.PREGNANCY_THIRD_TRIMESTER -> "Targets set for the third trimester of pregnancy"
        GoalsBasis.BREASTFEEDING -> "Targets set for breastfeeding — change them if you're not breastfeeding"
    }

    override val feedingTitle = "Feeding"
    override val feedingIntro = "Choose the side you're feeding from — the time counts itself."
    override fun side(side: FeedingSide) = when (side) {
        FeedingSide.LEFT -> "Left"
        FeedingSide.RIGHT -> "Right"
        FeedingSide.BOTTLE -> "Bottle"
        FeedingSide.PUMP -> "Expressed"
    }
    override fun lastFeed(ago: String, side: String) = "Last one $ago ago · $side"
    override fun feedsToday(count: Int) = "Today: $count"
    override val noFeedsToday = "Nothing recorded today yet"
    override val startFeed = "Start"
    override val stopFeed = "Stop"
    override fun feedRunning(side: String) = "Feeding: $side"
    override val bottleTitle = "Bottle or expressed milk"
    override val millilitres = "ml"
    override val saveBottle = "Save"
    override val nothingToday = "Nothing yet today."

    override val screenTitle = "Mood questionnaire"
    override val screenCardTitle = "Check in on your mood"
    override val screenCardBody =
        "10 questions, 2 minutes. Helps notice postnatal depression early."
    override fun lastScreen(date: String, score: Int) = "Last taken $date · score $score"
    override val screenIntro =
        "How have you felt in the past 7 days? Choose the answer that comes closest."
    override val questions = listOf(
        "I have been able to laugh and see the funny side of things",
        "I have looked forward with enjoyment to things",
        "I have blamed myself unnecessarily when things went wrong",
        "I have been anxious or worried for no good reason",
        "I have felt scared or panicky for no very good reason",
        "Things have been getting on top of me",
        "I have been so unhappy that I have had difficulty sleeping",
        "I have felt sad or miserable",
        "I have been so unhappy that I have been crying",
        "The thought of harming myself has occurred to me",
    )
    override val options = listOf(
        listOf("As much as I always could", "Not quite so much now", "Definitely not so much now", "Not at all"),
        listOf("As much as I ever did", "Rather less than I used to", "Definitely less than I used to", "Hardly at all"),
        listOf("Yes, most of the time", "Yes, some of the time", "Not very often", "No, never"),
        listOf("No, not at all", "Hardly ever", "Yes, sometimes", "Yes, very often"),
        listOf("Yes, quite a lot", "Yes, sometimes", "No, not much", "No, not at all"),
        listOf(
            "Yes, most of the time I haven't been able to cope at all",
            "Yes, sometimes I haven't been coping as well as usual",
            "No, most of the time I have coped quite well",
            "No, I have been coping as well as ever",
        ),
        listOf("Yes, most of the time", "Yes, sometimes", "Not very often", "No, not at all"),
        listOf("Yes, almost all the time", "Yes, quite often", "Not very often", "No, not at all"),
        listOf("Yes, almost all the time", "Yes, quite often", "Only occasionally", "No, never"),
        listOf("Yes, quite often", "Sometimes", "Hardly ever", "Never"),
    )
    override val screenSubmit = "See result"
    override fun screenScore(score: Int) = "Score: $score / 30"
    override val screenLow =
        "Few signs of depression for now. If your mood gets worse, take it again at any time or message a doctor."
    override val screenPossible =
        "There may be signs of depression. Talk to your doctor and take it again in 2 weeks."
    override val screenLikely =
        "Your answers match signs of postnatal depression. Please see a doctor or psychologist as soon as you can — it's treatable."
    override val screenSelfHarm =
        "Saying this is an important step. You're not alone: tell someone you trust right now, and contact a doctor or psychologist. " +
            "If you feel you're in danger, call 103 straight away."
    override val call103 = "Call 103"
    override val askDoctor = "Ask a doctor"
    override val screenSource =
        "Edinburgh Postnatal Depression Scale (EPDS; Cox, Holden, Sagovsky, 1987). Not a diagnosis — a screening tool."
    override fun answeredOf(answered: Int, total: Int) = "$answered / $total"

    override val kicksTitle = "Kick counter"
    override val kicksCardBody = "From week 28, once a day: how long it takes to feel 10 movements."
    override val kicksIntro =
        "When your baby is active, lie on your side and tap at every movement you feel. Usually 10 movements come within 2 hours."
    override val kickTap = "Movement"
    override fun kicksCount(count: Int, goal: Int) = "$count / $goal"
    override val kicksFinish = "Finish and save"
    override fun kicksResult(count: Int, duration: String) = "$count ${en(count, "movement", "movements")} · $duration"
    override val kicksSlow =
        "You didn't feel 10 movements in 2 hours. Contact your doctor or maternity unit without delay."
    override val previousCounts = "Earlier counts"

    override val contractionsTitle = "Contraction timer"
    override val contractionsCardBody = "Times how long contractions last and how often they come."
    override val contractionsIntro =
        "Tap “Started” when a contraction begins and “Ended” when it passes."
    override val contractionStart = "Started"
    override val contractionStop = "Ended"
    override fun contractionLasted(duration: String) = "lasted $duration"
    override fun contractionApart(interval: String) = "$interval after the last"
    override fun contractionsSummary(count: Int, duration: String, interval: String) =
        "$count in the last hour: $duration on average, every $interval"
    override val contractionsGo =
        "Contractions are coming every 5 minutes or more often, each lasting a minute or longer, and it's been like this for an hour — time to go to the maternity unit."
    override val contractionsUrgent =
        "If your waters break, you bleed or your baby moves less — go to the maternity unit straight away, contractions or not."
    override fun minutesSeconds(minutes: Int, seconds: Int) =
        if (minutes == 0) "$seconds s" else "$minutes min ${seconds.toString().padStart(2, '0')} s"

    override val hotFlushTitle = "Hot flushes"
    override val hotFlushLog = "Log one"
    override fun intensity(level: Int) = when (level) {
        1 -> "Mild"
        2 -> "Moderate"
        else -> "Strong"
    }
    override fun trigger(trigger: HotFlushTrigger) = when (trigger) {
        HotFlushTrigger.HEAT -> "Warm room"
        HotFlushTrigger.HOT_DRINK -> "Hot drink"
        HotFlushTrigger.SPICY_FOOD -> "Spicy food"
        HotFlushTrigger.CAFFEINE -> "Caffeine"
        HotFlushTrigger.ALCOHOL -> "Alcohol"
        HotFlushTrigger.STRESS -> "Stress"
        HotFlushTrigger.NIGHT -> "At night"
    }
    override val triggerQuestion = "What might have set it off?"
    override fun hotFlushCounts(today: Int, week: Int) = "Today $today · 7 days $week"
    override fun commonTrigger(name: String) = "Most often: $name"
    override val hotFlushSaved = "Logged"
    override fun ago(hours: Int, minutes: Int) = if (hours == 0) "$minutes min" else "$hours h $minutes min"
}
