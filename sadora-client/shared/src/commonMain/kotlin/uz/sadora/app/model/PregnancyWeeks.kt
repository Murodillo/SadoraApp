package uz.sadora.app.model

/**
 * The week-by-week size of a baby, from week 4 to week 40.
 *
 * Average values from the standard growth references (Hadlock and the tables obstetric
 * guides print from it) — every baby grows at its own pace, and the card says so. Until
 * week 19 the length is crown to rump, because the legs are still folded and that is
 * what an ultrasound measures; from week 20 it is head to heel. [crownToHeel] carries
 * the switch so the screen can name the measure rather than let the length jump.
 *
 * The words for each week — the fruit, what the baby is doing, what the mother may
 * notice — are in [uz.sadora.app.i18n.PregnancyWeekStrings], one file per language.
 * The emoji is the picture until illustrated artwork replaces it.
 */
data class PregnancyWeek(
    val week: Int,
    val emoji: String,
    /** Length in millimetres. */
    val lengthMm: Int,
    /** Weight in grams; null in the weeks where it is under a gram and says nothing. */
    val weightG: Int?,
    val crownToHeel: Boolean,
)

object PregnancyWeeks {
    const val FIRST = 4
    const val LAST = 40

    private val table: List<PregnancyWeek> = listOf(
        PregnancyWeek(4, "🌱", 1, null, false),
        PregnancyWeek(5, "🌱", 2, null, false),
        PregnancyWeek(6, "🫘", 5, null, false),
        PregnancyWeek(7, "🫐", 10, null, false),
        PregnancyWeek(8, "🍒", 16, 1, false),
        PregnancyWeek(9, "🫒", 23, 2, false),
        PregnancyWeek(10, "🍓", 31, 4, false),
        PregnancyWeek(11, "🌰", 41, 7, false),
        PregnancyWeek(12, "🥝", 54, 14, false),
        PregnancyWeek(13, "🍑", 74, 23, false),
        PregnancyWeek(14, "🍋", 87, 43, false),
        PregnancyWeek(15, "🍎", 101, 70, false),
        PregnancyWeek(16, "🥑", 116, 100, false),
        PregnancyWeek(17, "🍐", 130, 140, false),
        PregnancyWeek(18, "🫑", 142, 190, false),
        PregnancyWeek(19, "🥭", 153, 240, false),
        PregnancyWeek(20, "🍌", 256, 300, true),
        PregnancyWeek(21, "🥕", 267, 360, true),
        PregnancyWeek(22, "🌽", 278, 430, true),
        PregnancyWeek(23, "🍆", 289, 500, true),
        PregnancyWeek(24, "🥒", 300, 600, true),
        PregnancyWeek(25, "🥦", 346, 660, true),
        PregnancyWeek(26, "🥬", 356, 760, true),
        PregnancyWeek(27, "🥥", 366, 875, true),
        PregnancyWeek(28, "🍍", 376, 1005, true),
        PregnancyWeek(29, "🎃", 386, 1150, true),
        PregnancyWeek(30, "🥬", 399, 1320, true),
        PregnancyWeek(31, "🍇", 411, 1500, true),
        PregnancyWeek(32, "🍈", 424, 1700, true),
        PregnancyWeek(33, "🍍", 437, 1920, true),
        PregnancyWeek(34, "🎃", 450, 2150, true),
        PregnancyWeek(35, "🍈", 462, 2380, true),
        PregnancyWeek(36, "🎃", 474, 2620, true),
        PregnancyWeek(37, "🍈", 486, 2860, true),
        PregnancyWeek(38, "🍉", 498, 3080, true),
        PregnancyWeek(39, "🍉", 507, 3290, true),
        PregnancyWeek(40, "🍉", 512, 3460, true),
    )

    val all: List<PregnancyWeek> get() = table

    /**
     * The row for [week]. Before week 4 there is nothing to measure and past 40 the
     * baby is simply full term, so both ends clamp rather than fail.
     */
    fun of(week: Int): PregnancyWeek = table[week.coerceIn(FIRST, LAST) - FIRST]

    /** "5 mm", "3,1 sm" — the unit words come from the language. */
    fun lengthValue(mm: Int, mmUnit: String, cmUnit: String): String =
        if (mm < 10) "$mm $mmUnit" else "${Fmt.oneDecimal(mm / 10f)} $cmUnit"

    /** "14 g", "1,2 kg". */
    fun weightValue(g: Int, gUnit: String, kgUnit: String): String =
        if (g < 1000) "$g $gUnit" else "${Fmt.oneDecimal(g / 1000f)} $kgUnit"
}
