package uz.sadora.app.i18n

/**
 * A count short enough for a card: whole up to 999, then in thousands or millions.
 *
 * One decimal while the figure before it is a single digit ("1,2 ming"), none after
 * ("12 ming"), and always cut rather than rounded: 1 999 readers are not yet two
 * thousand. A decimal of zero is left off.
 */
internal fun compactCount(count: Int, thousand: String, million: String, decimal: Char): String {
    val (unit, suffix) = when {
        count < 1_000 -> return count.toString()
        count < 1_000_000 -> 1_000 to thousand
        else -> 1_000_000 to million
    }
    val whole = count / unit
    val tenth = count % unit / (unit / 10)
    return if (whole < 10 && tenth > 0) "$whole$decimal$tenth$suffix" else "$whole$suffix"
}
