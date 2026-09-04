package uz.sadora.server.ai

import uz.sadora.contract.CyclePhase

/**
 * What an answer may draw on. Every field is optional because every source can be
 * absent — no wearable, no meals logged today, a stage with no cycle.
 */
data class AiContext(
    val cycleDay: Int? = null,
    val phase: CyclePhase? = null,
    val daysUntilNextPeriod: Int? = null,
    val sleepMinutes: Int? = null,
    val steps: Int? = null,
    val waterMl: Int? = null,
    val waterGoalMl: Int? = null,
    val kcal: Int? = null,
    val kcalGoal: Int? = null,
) {
    /** "Sikl 14-kun (follikulyar faza), uyqu 6s 40d, suv 1,2 l" — what the answer used. */
    fun summary(): String = buildList {
        if (cycleDay != null) add("Sikl $cycleDay-kun" + (phase?.let { " (${it.label()})" } ?: ""))
        sleepMinutes?.let { add("uyqu ${it / 60}s ${it % 60}d") }
        if (waterMl != null) add("suv ${litres(waterMl)} l")
        if (kcal != null && kcalGoal != null) add("$kcal / $kcalGoal kkal")
        steps?.let { add("$it qadam") }
    }.joinToString(", ")

    val isEmpty: Boolean
        get() = cycleDay == null && sleepMinutes == null && waterMl == null && kcal == null && steps == null
}

/** Produces the text of a reply. The rule engine is the first implementation; a model is the next. */
fun interface AiAnswerer {
    fun answer(question: String, context: AiContext?): String
}

/**
 * The rule-based answerer.
 *
 * Deliberately narrow: it keys on the words a question in each area actually contains,
 * reads only the numbers in [AiContext], names which of them it used, and never
 * diagnoses. Without a context — no consent to AI insights — it answers in general
 * terms and says so, rather than pretending to know her.
 */
object RuleBasedAnswerer : AiAnswerer {

    override fun answer(question: String, context: AiContext?): String {
        val q = question.lowercase()
        val body = when {
            matches(q, "charch", "energiya", "toliq", "holsiz") -> energyAnswer(context)
            matches(q, "ye", "ovqat", "taom", "ovqatlan") -> foodAnswer(context)
            matches(q, "teri", "akne", "toshma", "husnbuzar") -> skinAnswer()
            matches(q, "uyqu", "uxla", "uyqusiz") -> sleepAnswer(context)
            matches(q, "sikl", "hayz", "ovulyats", "kechik") -> cycleAnswer(context)
            matches(q, "suv", "ich") -> waterAnswer(context)
            matches(q, "stress", "asab", "xavotir", "tashvish") -> stressAnswer()
            else -> fallbackAnswer()
        }
        val basis = context?.takeUnless { it.isEmpty }?.summary()
        val footer = if (basis != null) {
            "$basis asosida. $DISCLAIMER"
        } else {
            "$GENERAL_NOTE $DISCLAIMER"
        }
        return "$body\n\n$footer"
    }

    private fun matches(question: String, vararg stems: String) = stems.any { it in question }

    private fun energyAnswer(context: AiContext?): String {
        val phase = context?.phase
        return when (phase) {
            CyclePhase.LUTEAL, CyclePhase.PERIOD ->
                "Hayzdan oldin va hayz davrida progesteron o'zgarishi uyqu va energiyaga ta'sir qilishi mumkin. " +
                    "Magniyga boy ovqatlar, yengil yurish va nafas mashqlarini sinab ko'ring."
            CyclePhase.FOLLICULAR, CyclePhase.FERTILE -> {
                val water = context.waterRemaining()
                "Bu fazada energiya odatda o'sadi. Suv iste'moli va uyqu davomiyligi pastroq bo'lsa, " +
                    "charchoq shundan bo'lishi mumkin" +
                    (water?.let { " — bugun yana $it ml suv qoldi." } ?: ".")
            }
            null ->
                "Charchoqning eng ko'p uchraydigan sabablari — uyqu yetishmasligi, kam suv ichish va " +
                    "notekis ovqatlanish. Uch-to'rt kun uyqu va suvni kuzatib ko'ring; sikl bosqichi ham " +
                    "energiyaga ta'sir qiladi."
        }
    }

    private fun foodAnswer(context: AiContext?): String {
        val eaten = context?.kcal
        val goal = context?.kcalGoal
        val progress = if (eaten != null && goal != null) " Bugun $eaten / $goal kkal qayd etilgan." else ""
        return "Barqaror energiya uchun oqsil va murakkab uglevodlarni birga oling: tuxum, yog'urt, " +
            "don mahsulotlari, sabzavot.$progress Ovqatlanish rejasini tuzib beraymi?"
    }

    private fun skinAnswer(): String =
        "Sikl davomida gormonlar terining yog' ishlab chiqarishini o'zgartiradi: hayz oldidan toshmalar " +
            "ko'payishi odatiy. Yumshoq tozalash, yetarli suv va uyqu yordam beradi. Uzoq davom etsa, " +
            "dermatologga ko'rsating."

    private fun sleepAnswer(context: AiContext?): String {
        val slept = context?.sleepMinutes?.let { " Kecha ${it / 60}s ${it % 60}d uxlagansiz." }.orEmpty()
        return "Kechqurun ekranni kamaytirish va har kuni bir xil vaqtda yotish uyqu sifatini " +
            "yaxshilaydi.$slept Uyqu ma'lumotlarini kuzatishda davom eting."
    }

    private fun cycleAnswer(context: AiContext?): String {
        val day = context?.cycleDay
        val phase = context?.phase
        if (day == null || phase == null) {
            return "Sikl fazalari energiya, kayfiyat va teriga turlicha ta'sir qiladi. Bir necha sikl " +
                "kuzatilgach, ilova sizning shaxsiy naqshingizni ko'rsata oladi."
        }
        val next = context.daysUntilNextPeriod?.let { " Keyingi hayz taxminan $it kundan keyin." }.orEmpty()
        return "Hozir siklning $day-kuni — ${phase.label()}. ${phase.energyNote()}$next"
    }

    private fun waterAnswer(context: AiContext?): String {
        val remaining = context?.waterRemaining()
        return if (remaining != null) {
            "Bugun maqsadgacha yana $remaining ml suv qoldi. Kichik bo'laklarda, kun davomida ichish " +
                "bir martada ko'p ichishdan foydaliroq."
        } else {
            "Kuniga 1,5–2 litr suv ko'pchilik uchun yetarli; issiq kunlar va faollikda ko'proq kerak bo'ladi."
        }
    }

    private fun stressAnswer(): String =
        "To'rt-yetti-sakkiz nafas mashqi — to'rt soniya nafas olish, yetti ushlab turish, sakkiz " +
            "chiqarish — bir necha daqiqada tinchlantiradi. Uyqu va harakat ham stressni sezilarli " +
            "kamaytiradi. Holat uzoq davom etsa, mutaxassis bilan gaplashing."

    private fun fallbackAnswer(): String =
        "Savolingizni tushundim. Sikl, ovqatlanish, kayfiyat, uyqu va dorilaringiz bo'yicha " +
            "ma'lumotlaringizga tayanib javob bera olaman — aniqroq so'rasangiz, batafsil tushuntiraman."

    private fun AiContext?.waterRemaining(): Int? {
        val drunk = this?.waterMl ?: return null
        val goal = this.waterGoalMl ?: return null
        return (goal - drunk).coerceAtLeast(0)
    }

    const val DISCLAIMER = "Bu umumiy ma'lumot — tashxis emas."
    const val GENERAL_NOTE = "Umumiy javob: AI insights roziligisiz ma'lumotlaringiz o'qilmaydi."
}

private fun CyclePhase.label(): String = when (this) {
    CyclePhase.PERIOD -> "hayz"
    CyclePhase.FOLLICULAR -> "follikulyar faza"
    CyclePhase.FERTILE -> "ovulyatsiya davri"
    CyclePhase.LUTEAL -> "lyuteal faza"
}

private fun CyclePhase.energyNote(): String = when (this) {
    CyclePhase.PERIOD -> "Tanangiz dam olmoqda — o'zingizga yumshoq bo'ling."
    CyclePhase.FOLLICULAR -> "Energiya oshmoqda — yangi boshlanishlar uchun ajoyib vaqt."
    CyclePhase.FERTILE -> "Energiya cho'qqisida — faol kunlar uchun foydalaning."
    CyclePhase.LUTEAL -> "Energiya asta pasayadi — dam olishga vaqt ajrating."
}

private fun litres(ml: Int): String {
    val tenths = (ml + 50) / 100
    return "${tenths / 10},${tenths % 10}"
}
