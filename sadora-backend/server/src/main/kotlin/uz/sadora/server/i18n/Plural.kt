package uz.sadora.server.i18n

/**
 * Number agreement for the sentences the server writes itself — pushes, ledger rows,
 * greetings. Uzbek needs none ("5 kun"); Russian has three forms and English two, so
 * "21 дней" and "1 days" are never built by hand.
 */
object Plural {

    /** `ru(1, "день", "дня", "дней")` → "день"; 2–4 → "дня"; 5–20, 11–14 → "дней". */
    fun ru(n: Int, one: String, few: String, many: String): String {
        val abs = kotlin.math.abs(n)
        val lastTwo = abs % 100
        val last = abs % 10
        return when {
            lastTwo in 11..14 -> many
            last == 1 -> one
            last in 2..4 -> few
            else -> many
        }
    }

    fun en(n: Int, one: String, other: String): String = if (n == 1 || n == -1) one else other
}
