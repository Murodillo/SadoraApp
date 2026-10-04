package uz.sadora.app.i18n

import uz.sadora.app.model.CyclePhase
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerRelation

/**
 * Yaqinim: the words for her side, for the person who follows her, and for joining.
 *
 * The follower's half speaks to someone who does not track a cycle and may never have
 * been told what one does. So it explains in plain words — what she may feel in this
 * phase, and what would help — and never uses a clinical name without saying what it
 * means. Nothing in it tells the follower that something is wrong with her.
 */
interface PartnerStrings {
    // ---------------------------------------------------------------- entry points
    val title: String
    val profileRowNote: String
    /** The welcome page's link for the person who was sent a code. */
    val joinEntry: String

    // ---------------------------------------------------------------- hers
    val introTitle: String
    val introBody: String
    val whoLabel: String
    fun relation(relation: PartnerRelation): String
    val createInvite: String
    val creating: String
    val codeTitle: String
    val codeSteps: String
    fun codeExpires(date: String): String
    val shareCode: String
    fun shareMessage(code: String, url: String?): String
    val inviteOut: String
    fun inviteOutBody(date: String): String
    val newCode: String
    val cancelInvite: String
    fun requestTitle(name: String): String
    val requestBody: String
    val approve: String
    val decline: String
    fun linkedTitle(name: String): String
    fun pausedTitle(name: String): String
    fun lastViewed(ago: String): String
    val neverViewed: String
    val showsTitle: String
    val permCycle: String
    val permCycleNote: String
    val permFertile: String
    val permFertileNote: String
    val permMood: String
    val permMoodNote: String
    val permSymptoms: String
    val permSymptomsNote: String
    val permPregnancy: String
    val permPregnancyNote: String
    val permAppointments: String
    val permAppointmentsNote: String
    val permCare: String
    val permCareNote: String
    val neverTitle: String
    val neverList: List<String>
    val pause: String
    val pauseNote: String
    val disconnect: String
    val disconnectConfirmTitle: String
    fun disconnectConfirmBody(name: String): String
    val disconnected: String
    val approved: String
    val followingTitle: String
    val haveCode: String
    val enterCodeTitle: String
    val codeLabel: String
    val codeHint: String
    val follow: String
    val statusPending: String
    val statusPaused: String
    val statusActive: String
    fun requestSent(name: String): String

    // ---------------------------------------------------------------- theirs
    fun pendingTitle(name: String): String
    val pendingBody: String
    fun pausedViewTitle(name: String): String
    val pausedViewBody: String
    fun nothingShared(name: String): String
    val todayHeading: String
    fun cycleDay(day: Int): String
    fun periodDay(day: Int): String
    fun periodIn(days: Int): String
    fun periodAround(date: String): String
    /** The phase as a follower is told it — a name a husband can repeat. */
    fun phaseTitle(phase: CyclePhase): String
    fun phaseFeel(phase: CyclePhase): String
    fun phaseTips(phase: CyclePhase): List<String>
    fun fertileWindow(from: String, to: String): String
    val fertileToday: String
    val estimatedNote: String
    val moodLabel: String
    val energyLabel: String
    val symptomsLabel: String
    fun pregnancyWeek(week: Int): String
    fun daysToGo(days: Int): String
    fun babySize(fruit: String): String
    fun pregnancyTips(week: Int): List<String>
    fun babyAge(days: Int): String
    val postpartumTips: List<String>
    val menopauseTips: List<String>
    val appointmentsTitle: String
    fun feedsToday(count: Int): String
    fun lastFeed(ago: String): String
    fun hotFlushesToday(count: Int): String
    val helpTitle: String
    val leave: String
    fun leaveConfirmBody(name: String): String

    // ---------------------------------------------------------------- the follower-only app
    val emptyFollowingTitle: String
    val emptyFollowingBody: String
    val settingsTitle: String
    val followAnother: String

    // ---------------------------------------------------------------- joining
    val joinTitle: String
    val joinSubtitle: String
    val yourNameLabel: String
    val yourNameHint: String
    val yourNameNote: String
    val joinTermsLead: String
    val termsLink: String
    val privacyLink: String

    // ---------------------------------------------------------------- labour
    val labourButton: String
    val labourConfirmTitle: String
    val labourConfirmBody: String
    val labourSend: String
    val labourSent: String

    // ---------------------------------------------------------------- messages
    val messagesTitle: String
    fun sendTo(name: String): String
    fun askFrom(name: String): String
    /** The chip and the line in the list: an emoji and a few words. */
    fun kind(kind: PartnerMessageKind): String
    val youPrefix: String
    fun asked(name: String): String
    val noMessages: String
    val customTitle: String
    val customHint: String
    val send: String
    val sent: String
    fun unreadCount(count: Int): String

    // ---------------------------------------------------------------- the web link
    val webTitle: String
    val webBody: String
    val webShows: String
    val webCreate: String
    fun webExpires(date: String): String
    fun webViews(count: Int): String
    val webNever: String
    val webShare: String
    val webRevoke: String
    val webNew: String
    fun webShareMessage(url: String): String
    val webRevoked: String
    fun webDays(days: Int): String
    val webOutBody: String
}
