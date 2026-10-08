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

    // ---- Yaqinim (the person who sees her)
    /** A code lives two days: long enough to reach a husband abroad, short enough to forget. */
    const val PARTNER_INVITE_HOURS = 48
    const val PARTNER_CODE_LENGTH = 8
    const val PARTNER_MAX_FOLLOWING = 5
    const val PARTNER_NAME_MAX = 60
    const val PARTNER_MESSAGE_MAX = 200
    /** Per sender, per link, per day: plenty for love, too few for a flood. */
    const val PARTNER_MESSAGES_PER_DAY = 60
    const val PARTNER_MESSAGES_SHOWN = 30
    val PARTNER_WEB_TTL_HOURS = 1..24 * 7

    // ---- secret chat
    const val POST_MIN = 2
    const val POST_MAX = 2000
    const val COMMENT_MAX = 1000
    const val REPORT_NOTE_MAX = 500
    /** The line under an alias on its profile. */
    const val BIO_MAX = 160
    /** One private message. */
    const val MESSAGE_MAX = 1000
    /** A photo in a message, decoded. The apps resize to a 1280-pixel edge well under it. */
    const val MESSAGE_IMAGE_MAX_BYTES = 3_000_000
    /** How long a consultation takes messages once the patient opens it. */
    const val CONSULTATION_HOURS = 24

    // ---- prescriptions, written by a doctor inside a consultation
    const val PRESCRIPTION_ITEMS_MAX = 10
    const val PRESCRIPTION_DOSE_MAX = 40
    const val PRESCRIPTION_UNIT_MAX = 24
    const val PRESCRIPTION_NOTE_MAX = 1000
    const val PRESCRIPTION_ITEM_NOTE_MAX = 300
    /** The last day a course may start on, counted from day 1. */
    const val PRESCRIPTION_START_DAY_MAX = 90
    const val PRESCRIPTION_DAYS_MAX = 365
    const val PRESCRIPTION_CANCEL_REASON_MAX = 300

    /**
     * Ratings a doctor needs before her average is shown as a number; below it the
     * apps say "Yangi shifokor". One five-star rating is not a reputation.
     */
    const val DOCTOR_RATING_MIN = 3

    /** Her usual first reply, in minutes, at or under which she wears "Tez javob beradi". */
    const val DOCTOR_FAST_REPLY_MINUTES = 30
    /** How long "yozmoqda…" lasts after the last keystroke report. */
    const val TYPING_SECONDS = 6

    // ---- doctors
    const val DOCTOR_NAME_MIN = 5
    const val DOCTOR_NAME_MAX = 120
    const val DOCTOR_WORKPLACE_MAX = 160
    const val DOCTOR_LICENSE_MAX = 64
    const val DOCTOR_BIO_MAX = 500
    val DOCTOR_EXPERIENCE_YEARS = 0..70
    /** Diploma, licence and whatever else proves it: at least one page, at most four. */
    const val DOCTOR_DOCUMENTS_MAX = 4
    /** Why an application came back; the doctor reads it. */
    const val DOCTOR_REVIEW_NOTE_MAX = 500
}
