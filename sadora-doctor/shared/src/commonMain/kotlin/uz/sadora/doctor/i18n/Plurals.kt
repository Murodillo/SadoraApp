package uz.sadora.doctor.i18n

/**
 * The Russian word that agrees with [n]: `ru(21, "день", "дня", "дней")` is "день",
 * 23 takes "дня", 25 and 11–14 take "дней". Only the word — the caller places the number.
 */
internal fun ru(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

/** The English word that agrees with [n]: "1 day", "2 days" — never "1 days". */
internal fun en(n: Int, one: String, other: String): String = if (n == 1) one else other
