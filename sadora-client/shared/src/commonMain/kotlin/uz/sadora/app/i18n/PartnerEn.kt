package uz.sadora.app.i18n

import uz.sadora.app.model.CyclePhase
import uz.sadora.contract.PartnerRelation

object PartnerEn : PartnerStrings {
    override val title = "My person"
    override val profileRowNote = "Share how you are with your husband or someone close"
    override val joinEntry = "I have an invite"

    override val introTitle = "Let someone close understand you better"
    override val introBody =
        "Your husband, mother or friend sees on their own phone how you are today: your cycle day, " +
            "how long until your period, and what would help you. " +
            "You choose what they see, and you can stop it at any moment."
    override val whoLabel = "Who are they to you?"
    override fun relation(relation: PartnerRelation) = when (relation) {
        PartnerRelation.HUSBAND -> "Husband"
        PartnerRelation.MOTHER -> "Mother"
        PartnerRelation.SISTER -> "Sister"
        PartnerRelation.FRIEND -> "Friend"
        PartnerRelation.OTHER -> "Other"
    }
    override val createInvite = "Create an invite code"
    override val creating = "Creating…"
    override val codeTitle = "Send this code to your person"
    override val codeSteps =
        "They install Sadora, tap “I have an invite” and enter the code. Then you say yes."
    override fun codeExpires(date: String) = "The code works until $date"
    override val shareCode = "Send the code"
    override fun shareMessage(code: String, url: String?) =
        "Hi! I'd like to share how I am with you in the Sadora app. Install it, " +
            "tap “I have an invite” and enter this code: $code" + (url?.let { "\n$it" } ?: "")
    override val inviteOut = "Invite sent"
    override fun inviteOutBody(date: String) =
        "The code works until $date. To show the code again, create a new one — the old one stops working."
    override val newCode = "New code"
    override val cancelInvite = "Cancel"
    override fun requestTitle(name: String) = "$name wants to see how you are"
    override val requestBody = "If you allow it, they see only what you choose."
    override val approve = "Allow"
    override val decline = "Decline"
    override fun linkedTitle(name: String) = "$name sees how you are"
    override fun pausedTitle(name: String) = "Paused for $name"
    override fun lastViewed(ago: String) = "Last looked $ago"
    override val neverViewed = "Not opened yet"
    override val showsTitle = "What I show"
    override val permCycle = "Cycle and period"
    override val permCycleNote = "Cycle day, phase, when your period started and the next one"
    override val permFertile = "Fertile days"
    override val permFertileNote = "Ovulation and the fertile window"
    override val permMood = "Mood and energy"
    override val permMoodNote = "The mood you marked today"
    override val permSymptoms = "Symptoms"
    override val permSymptomsNote = "The names of today's symptoms"
    override val permPregnancy = "Pregnancy"
    override val permPregnancyNote = "Week and due date, then the baby's age"
    override val permAppointments = "Doctor visits"
    override val permAppointmentsNote = "Date, time and place — never your note"
    override val permCare = "Stage care"
    override val permCareNote = "How many feeds, or hot flushes"
    override val neverTitle = "Never shown"
    override val neverList = listOf(
        "Your private notes and journal",
        "Intimacy and contraception",
        "Your weight",
        "Mood questionnaire results",
        "AI chats, the Chat and doctor consultations",
    )
    override val pause = "Pause for now"
    override val pauseNote = "They only see that it is paused"
    override val disconnect = "Disconnect"
    override val disconnectConfirmTitle = "Disconnect?"
    override fun disconnectConfirmBody(name: String) =
        "$name will no longer see how you are. You can connect again later with a new code."
    override val disconnected = "Disconnected"
    override val approved = "Allowed"
    override val followingTitle = "People I follow"
    override val haveCode = "I have a code"
    override val enterCodeTitle = "Enter the invite code"
    override val codeLabel = "Code"
    override val codeHint = "K7M2-QP4X"
    override val follow = "Connect"
    override val statusPending = "Waiting for her"
    override val statusPaused = "Paused"
    override val statusActive = "Active"
    override fun requestSent(name: String) = "Request sent — $name needs to allow it"

    override fun pendingTitle(name: String) = "$name has not allowed it yet"
    override val pendingBody = "As soon as she does, how she is will show here — we'll let you know."
    override fun pausedViewTitle(name: String) = "$name has paused sharing for now"
    override val pausedViewBody = "When she turns it back on, it will show here again."
    override fun nothingShared(name: String) = "$name is not sharing anything yet"
    override val todayHeading = "Today"
    override fun cycleDay(day: Int) = "Cycle day $day"
    override fun periodDay(day: Int) = "Period day $day"
    override fun periodIn(days: Int) = when {
        days <= 0 -> "Period expected today"
        days == 1 -> "Period expected tomorrow"
        else -> "Period in $days days"
    }
    override fun periodAround(date: String) = "Around $date"
    override fun phaseTitle(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> "Period days"
        CyclePhase.Follicular -> "Energy rising"
        CyclePhase.Fertile -> "Peak energy"
        CyclePhase.Luteal -> "The days before her period"
    }
    override fun phaseFeel(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> "Tiredness, cramps and a lower mood are common."
        CyclePhase.Follicular -> "Strength comes back, mood lifts, new plans feel easier."
        CyclePhase.Fertile -> "Confidence and energy are at their highest."
        CyclePhase.Luteal -> "Tiredness, sensitivity, bloating and sweet cravings are common."
    }
    override fun phaseTips(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> listOf(
            "Offer a hot tea or a hot-water bottle",
            "Take on some of the housework",
            "Be patient — mood changes are natural",
            "Her favourite food or some chocolate makes a nice surprise",
        )
        CyclePhase.Follicular -> listOf(
            "Plan a walk or some exercise together",
            "A good time to try something new together",
            "Back her plans and ideas",
        )
        CyclePhase.Fertile -> listOf(
            "Spend time together — a date or a dinner",
            "Give her your attention and some warm words",
        )
        CyclePhase.Luteal -> listOf(
            "Listen more, advise less",
            "Make room for a calm evening and good sleep",
            "Small kindnesses go a long way",
            "If she is irritable, don't take it personally",
        )
    }
    override fun fertileWindow(from: String, to: String) = "Fertile days: $from – $to"
    override val fertileToday = "Today is a fertile day"
    override val estimatedNote = "Dates are estimates based on her records."
    override val moodLabel = "Mood"
    override val energyLabel = "Energy"
    override val symptomsLabel = "What she feels today"
    override fun pregnancyWeek(week: Int) = "Week $week of pregnancy"
    override fun daysToGo(days: Int) = if (days <= 0) "The due date has come" else "$days days to go"
    override fun babySize(fruit: String) = "The baby is about the size of $fruit"
    override fun pregnancyTips(week: Int) = when {
        week <= 13 -> listOf(
            "Nausea and tiredness are normal — let her rest",
            "Keep strong-smelling food away",
            "Go to the doctor visits together",
        )
        week <= 27 -> listOf(
            "Plan the nursery and the things you need together",
            "Evening walks are good for her and the baby",
            "If her back aches, offer a gentle massage",
        )
        else -> listOf(
            "Pack the hospital bag",
            "Keep your phone on and with you",
            "Do the heavy lifting yourself — she needs rest",
            "Know the way to the maternity hospital in advance",
        )
    }
    override fun babyAge(days: Int) = if (days < 14) "The baby is $days days old" else "The baby is ${days / 7} weeks old"
    override val postpartumTips = listOf(
        "Take turns with the baby at night — she needs sleep",
        "Take over meals and housework",
        "If she feels low, listen and don't leave her alone",
        "Keep visitors few — recovery takes time",
    )
    override val menopauseTips = listOf(
        "Keep the room cool — hot flushes are easier",
        "If her sleep is broken, make room for a calm evening",
        "Meet mood changes with patience",
        "Walks together are good for you both",
    )
    override val appointmentsTitle = "Doctor visits"
    override fun feedsToday(count: Int) = "$count feeds today"
    override fun lastFeed(ago: String) = "Last one $ago"
    override fun hotFlushesToday(count: Int) = "$count hot flushes today"
    override val helpTitle = "What would help her today"
    override val leave = "Stop following"
    override fun leaveConfirmBody(name: String) =
        "You will no longer see how $name is. To connect again you will need a new code."

    override val emptyFollowingTitle = "You are not following anyone yet"
    override val emptyFollowingBody = "Enter the invite code your person sent you."
    override val settingsTitle = "Settings"
    override val followAnother = "Enter another code"

    override val joinTitle = "Enter the code you were sent"
    override val joinSubtitle = "The code has 8 characters, for example K7M2-QP4X"
    override val yourNameLabel = "Your name"
    override val yourNameHint = "For example, Aziz"
    override val yourNameNote = "She sees this name in the request"
    override val joinTermsLead = "By continuing you agree to the"
    override val termsLink = "Terms of use"
    override val privacyLink = "Privacy policy"

    override val labourButton = "Tell my person: labour has started"
    override val labourConfirmTitle = "Send the message?"
    override val labourConfirmBody = "They get a “Labour has started!” notification right away."
    override val labourSend = "Send"
    override val labourSent = "Message sent"
}
