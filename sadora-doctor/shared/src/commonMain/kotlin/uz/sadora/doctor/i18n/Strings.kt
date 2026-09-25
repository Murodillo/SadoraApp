package uz.sadora.doctor.i18n

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.CommunityTopic
import uz.sadora.contract.Language

/** The three languages the app speaks. Uzbek is the default and the reference. */
enum class AppLanguage(val code: String, val wire: Language) {
    Uz("uz", Language.UZ),
    Ru("ru", Language.RU),
    En("en", Language.EN),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: Uz
    }
}

/**
 * Every word the doctor app shows, for one language.
 *
 * The client app's arrangement, much smaller: an interface per area, so a line added to
 * one language fails to compile until the other two have it too.
 */
interface Strings {
    val language: AppLanguage

    /** The language's name in itself — "O'zbekcha", "Русский" — for the switch. */
    val languageName: String

    val common: CommonStrings
    val auth: AuthStrings
    val errors: ErrorStrings
    val dates: DateStrings
    val community: CommunityStrings
    val settings: SettingsStrings
    val doctors: DoctorStrings
}

interface CommonStrings {
    val appName: String
    val back: String
    val retry: String
    val cancel: String
    val saving: String
    val send: String
}

/** The sign-in screen: the number, then the code. */
interface AuthStrings {
    val title: String
    val subtitle: String
    val phoneLabel: String
    val phoneNote: String
    val sendCode: String
    val sending: String
    val codeTitle: String
    /** [phone] arrives formatted: `90 123 45 67`. */
    fun codeSubtitle(phone: String): String
    val confirm: String
    val checking: String
    fun resendIn(seconds: Int): String
    val resend: String
    val changeNumber: String
    val codeSecrecy: String
    /** Shown only when a development server sent the code back and it was filled in. */
    val devCodeFilled: String
    /** What a screen reader hears for the code boxes. */
    fun otpEntered(entered: Int, length: Int): String
    val deleteDigit: String
}

interface ErrorStrings {
    val phoneInvalid: String
    val network: String
    val validation: String
    val sessionExpired: String
    val blocked: String
    /** A plain refusal — not hers, or closed to her — which is not the same as a block. */
    val forbidden: String
    val notFound: String
    fun retryAfter(seconds: Int): String
    val retrySoon: String
    val otpInvalid: String
    val featureClosed: String
    val unexpected: String
}

interface DateStrings {
    /** Month names as a month is named on its own, January first. */
    val months: List<String>

    /** "4-sentabr" — the day inside its month. */
    fun dayMonth(date: LocalDate): String

    /** "Sentabr 2026". */
    fun monthYear(year: Int, month: Int): String = "${months[month - 1]} $year"

    val yesterday: String
    val justNow: String
    fun minutesAgo(minutes: Int): String
    fun hoursAgo(hours: Int): String
    fun daysAgo(days: Int): String

    /** How old something is, the way a feed reads it. Past a month it says the date. */
    fun ago(at: Instant, now: Instant): String {
        val seconds = (now - at).inWholeSeconds
        return when {
            seconds < 60 -> justNow
            seconds < 3600 -> minutesAgo((seconds / 60).toInt())
            seconds < 86_400 -> hoursAgo((seconds / 3600).toInt())
            seconds < 2 * 86_400 -> yesterday
            seconds < 30 * 86_400 -> daysAgo((seconds / 86_400).toInt())
            else -> dayMonth(at.toLocalDateTime(TimeZone.currentSystemDefault()).date)
        }
    }
}

/** A question's page, her answer, and a post of her own. */
interface CommunityStrings {
    fun topic(topic: CommunityTopic): String
    val you: String
    val readMore: String
    fun commentsCount(count: Int): String
    val noComments: String
    val questionTitle: String
    val postTitle: String
    val postMissing: String
    val answerHint: String
    val answerSent: String
    val newPost: String
    val newPostTitle: String
    val topicLabel: String
    val postHint: String
    fun postTooShort(min: Int): String
    val publish: String
    val published: String
}

interface SettingsStrings {
    val title: String
    val language: String
    val account: String
    fun signedInAs(phone: String): String
    val signOut: String
    val signOutTitle: String
    val signOutBody: String
}
