package uz.sadora.app.i18n

import uz.sadora.app.model.PregnancyWeeks

/**
 * The words of the week-by-week pregnancy card: what the baby compares to, what is
 * developing, and what the mother may notice.
 *
 * Each language answers with one list per text, indexed from week 4 to week 40 —
 * `StringsTest` checks every list is exactly that long, so a missing week is a failing
 * test rather than a blank card. The numbers live in [uz.sadora.app.model.PregnancyWeeks].
 *
 * The texts are general and deliberately cautious: they describe what is typical, and
 * nothing in them tells her something is wrong. That stays the doctor's, and the
 * check-in's foetal-movement question, which escalates.
 */
interface PregnancyWeekStrings {
    /** "Bolangiz hozir kivi kattaligida" — [fruit] is already the right form. */
    fun sizeOf(fruit: String): String
    val lengthLabel: String
    /** The measure named under the length, before week 20 and after. */
    val crownToRump: String
    val crownToHeel: String
    val weightLabel: String
    val weightTooSmall: String
    val mm: String
    val cm: String
    val g: String
    val kg: String
    val babyHeading: String
    val motherHeading: String
    val previousWeek: String
    val nextWeek: String
    val thisWeekCaps: String
    val averagesNote: String

    /** The fruit or vegetable, in the form [sizeOf] expects. Weeks 4..40. */
    val fruits: List<String>
    /** What is developing this week. Weeks 4..40. */
    val baby: List<String>
    /** What the mother may notice or do this week. Weeks 4..40. */
    val mother: List<String>
}

/** The line for [week] from one of the lists above, clamped the way the table is. */
fun List<String>.forWeek(week: Int): String =
    this[week.coerceIn(PregnancyWeeks.FIRST, PregnancyWeeks.LAST) - PregnancyWeeks.FIRST]
