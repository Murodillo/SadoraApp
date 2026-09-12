package uz.sadora.contract

/**
 * How long a field may be, and what range a number may sit in.
 *
 * Shared rather than written twice: the server refuses what is outside these, and the
 * app should never send it in the first place. When the two drifted apart the symptom
 * was always the same — a form that looked accepted and came back rejected, with the
 * reason attached to a field the screen had already left behind.
 *
 * The server is still the one that enforces them. This is what lets the app agree.
 */
object Limits {

    // ---- profile
    const val NAME_MAX = 60
    val HEIGHT_CM = 80..250
    val WEIGHT_KG = 25..300
    /** Nobody using a cycle app was born before this, and nobody is born after today. */
    val BIRTH_YEAR = 1940..2020

    // ---- cycle
    const val CYCLE_LENGTH_MIN = 15
    const val CYCLE_LENGTH_MAX = 60
    const val PERIOD_LENGTH_MIN = 1
    const val PERIOD_LENGTH_MAX = 15
    val CYCLE_LENGTH_DAYS = CYCLE_LENGTH_MIN..CYCLE_LENGTH_MAX
    val PERIOD_LENGTH_DAYS = PERIOD_LENGTH_MIN..PERIOD_LENGTH_MAX

    // ---- daily log
    const val DAY_NOTE_MAX = 1000

    // ---- mind
    const val JOURNAL_MAX = 5000

    // ---- nutrition
    const val MEAL_DESCRIPTION_MAX = 200
    const val WATER_STEP_MAX_ML = 2000

    // ---- medications
    const val MEDICATION_NAME_MAX = 120
    const val MEDICATION_STOCK_MAX = 10_000
    const val MEDICATION_TIMES_PER_DAY_MAX = 8

    // ---- appointments
    const val APPOINTMENT_TITLE_MAX = 120
    const val APPOINTMENT_PLACE_MAX = 200

    // ---- library articles, written in the admin panel
    const val ARTICLE_SLUG_MAX = 80
    const val ARTICLE_TITLE_MAX = 160
    const val ARTICLE_EXCERPT_MAX = 400
    const val ARTICLE_PERSON_MAX = 120
    const val ARTICLE_DISCLAIMER_MAX = 500
    val ARTICLE_READ_MINUTES = 1..120

    // ---- profile share (the QR code a doctor scans)
    const val SHARE_DEFAULT_HOURS = 24
    const val SHARE_MAX_HOURS = 24 * 7
    val SHARE_TTL_HOURS = 1..SHARE_MAX_HOURS

    // ---- secret chat
    const val POST_MIN = 2
    const val POST_MAX = 2000
    const val COMMENT_MAX = 1000
    const val REPORT_NOTE_MAX = 500
}
