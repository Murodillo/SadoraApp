package uz.sadora.server.community

import kotlin.random.Random

/**
 * Makes up the name a user posts under.
 *
 * Two short Uzbek words — a quality and something from nature — chosen so that no
 * combination is a name anyone is actually called. The words are deliberately warm:
 * the room is for questions people are embarrassed to ask, and "Iliq Shabnam" reads
 * kinder than "user_48213". A numeric suffix is added only when the plain form is taken,
 * so the first few thousand aliases stay clean.
 */
object AliasGenerator {

    private val qualities = listOf(
        "Sokin", "Iliq", "Yorug'", "Mehribon", "Quvnoq", "Dadil", "Oydin", "Bahorgi",
        "Mayin", "Tiniq", "Ochiq", "Xotirjam", "Shirin", "Nozik", "Erkin", "Yashil",
    )

    private val subjects = listOf(
        "Bulut", "Yulduz", "Shabnam", "Kapalak", "Tong", "Shamol", "Daryo", "Kamalak",
        "Barg", "Tolqin", "Nur", "Osmon", "Chechak", "Yomg'ir", "Dengiz", "Qor",
    )

    /** How many combinations exist before a suffix is needed. */
    val plainCombinations: Int get() = qualities.size * subjects.size

    /** One candidate. The caller checks it against the table and asks again on a clash. */
    fun candidate(random: Random = Random.Default, attempt: Int = 0): String {
        val quality = qualities[random.nextInt(qualities.size)]
        val subject = subjects[random.nextInt(subjects.size)]
        val base = "$quality $subject"
        // The first few tries go for a plain alias; after that a two-digit suffix keeps
        // the search from circling the same names when the table is nearly full.
        return if (attempt < PLAIN_ATTEMPTS) base else "$base ${random.nextInt(10, 100)}"
    }

    /** The avatar tint is picked once with the alias, so the same alias always draws the same colour. */
    fun tint(random: Random = Random.Default): Int = random.nextInt(TINT_COUNT)

    private const val PLAIN_ATTEMPTS = 5
    const val TINT_COUNT = 4
}
