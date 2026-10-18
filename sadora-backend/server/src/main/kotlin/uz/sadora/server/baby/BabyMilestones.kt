package uz.sadora.server.baby

import uz.sadora.contract.Language

/**
 * The milestones of the first two years the app lists, with the window most babies
 * reach each one in.
 *
 * The six gross-motor ones use the WHO Multicentre Growth Reference Study windows (WHO
 * Motor Development Study, 2006: 1st–99th percentile), rounded outward to whole months.
 * The rest use the usual paediatric ranges. Every sentence says what the milestone is —
 * never that a baby is late — because a window is where most babies are, not a rule.
 */
data class MilestoneDef(
    val code: String,
    val group: String,
    val fromMonth: Int,
    val toMonth: Int,
    val names: Words,
    val about: Words,
) {
    data class Words(val uz: String, val ru: String, val en: String) {
        fun of(language: Language) = when (language) {
            Language.UZ -> uz
            Language.RU -> ru
            Language.EN -> en
        }
    }
}

object BabyMilestones {

    private fun m(code: String, group: String, from: Int, to: Int, names: MilestoneDef.Words, about: MilestoneDef.Words) =
        MilestoneDef(code, group, from, to, names, about)

    private fun w(uz: String, ru: String, en: String) = MilestoneDef.Words(uz, ru, en)

    val all: List<MilestoneDef> = listOf(
        m(
            "social_smile", "social", 1, 3,
            w("Birinchi tabassum", "Первая улыбка", "First social smile"),
            w(
                "Sizning yuzingiz yoki ovozingizga javoban jilmayadi — refleks emas, muloqot.",
                "Улыбается в ответ на ваше лицо или голос — уже не рефлекс, а общение.",
                "Smiles back at your face or voice — not a reflex any more, but a conversation.",
            ),
        ),
        m(
            "holds_head", "skills", 2, 4,
            w("Boshini tutadi", "Держит голову", "Holds head steady"),
            w(
                "Qo'lda tik tutganda boshi chayqalmay turadi.",
                "Когда вы держите малыша вертикально, голова не качается.",
                "Held upright, the head stays steady without wobbling.",
            ),
        ),
        m(
            "laughs", "social", 3, 5,
            w("Ovoz chiqarib kuladi", "Смеётся вслух", "Laughs out loud"),
            w("O'yin paytida ovoz chiqarib kuladi.", "Смеётся вслух во время игры.", "Laughs out loud when you play."),
        ),
        m(
            "rolls_over", "skills", 4, 6,
            w("Ag'dariladi", "Переворачивается", "Rolls over"),
            w(
                "Qornidan chalqanchasiga yoki aksincha o'zi ag'dariladi.",
                "Сам переворачивается с животика на спину или обратно.",
                "Rolls from tummy to back, or back to tummy, on their own.",
            ),
        ),
        m(
            "babbles", "language", 4, 9,
            w("G'o'ldiraydi", "Лепечет", "Babbles"),
            w("\"Ba-ba\", \"ma-ma\" kabi bo'g'inlarni takrorlaydi.", "Повторяет слоги вроде «ба-ба», «ма-ма».", "Repeats sounds such as \"ba-ba\" and \"ma-ma\"."),
        ),
        m(
            "sits_alone", "skills", 4, 9,
            w("Mustaqil o'tiradi", "Сидит без опоры", "Sits without support"),
            w(
                "Suyanmasdan, boshini tik tutib o'tiradi. WHO: 4–9 oy.",
                "Сидит без опоры, держа голову ровно. ВОЗ: 4–9 мес.",
                "Sits up straight without being propped. WHO: 4–9 months.",
            ),
        ),
        m(
            "first_tooth", "teeth", 4, 12,
            w("Birinchi tish", "Первый зуб", "First tooth"),
            w(
                "Ko'pincha pastki old tishlar birinchi chiqadi; muddati bolalarda juda farq qiladi.",
                "Чаще первыми появляются нижние передние зубы; сроки у детей очень разные.",
                "Usually a lower front tooth comes first; the timing varies a lot between babies.",
            ),
        ),
        m(
            "stands_assisted", "skills", 5, 11,
            w("Suyanib turadi", "Стоит с опорой", "Stands with support"),
            w(
                "Mebelga yoki qo'lingizga suyanib oyog'ida turadi. WHO: 5–11 oy.",
                "Стоит на ногах, держась за мебель или вашу руку. ВОЗ: 5–11 мес.",
                "Stands holding on to furniture or your hands. WHO: 5–11 months.",
            ),
        ),
        m(
            "crawls", "skills", 5, 14,
            w("Emaklaydi", "Ползает", "Crawls"),
            w(
                "Qo'l va tizzalarida oldinga harakatlanadi. Ba'zi bolalar bu bosqichni o'tkazib yuboradi. WHO: 5–14 oy.",
                "Передвигается на руках и коленях. Некоторые дети пропускают этот этап. ВОЗ: 5–14 мес.",
                "Moves forward on hands and knees. Some babies skip this stage. WHO: 5–14 months.",
            ),
        ),
        m(
            "responds_name", "social", 6, 9,
            w("Ismiga javob beradi", "Откликается на имя", "Responds to name"),
            w("Ismini aytganingizda o'girilib qaraydi.", "Поворачивается, когда вы зовёте по имени.", "Turns to look when you say their name."),
        ),
        m(
            "walks_assisted", "skills", 6, 14,
            w("Qo'ldan ushlab yuradi", "Ходит с поддержкой", "Walks with support"),
            w(
                "Qo'lidan ushlaganda yoki mebelga suyanib qadam tashlaydi. WHO: 6–14 oy.",
                "Шагает, держась за руку или мебель. ВОЗ: 6–14 мес.",
                "Takes steps holding your hand or the furniture. WHO: 6–14 months.",
            ),
        ),
        m(
            "waves", "social", 8, 12,
            w("Qo'l silkitib xayrlashadi", "Машет «пока»", "Waves bye-bye"),
            w("Xayrlashganda qo'l silkitadi.", "Машет ручкой на прощание.", "Waves goodbye."),
        ),
        m(
            "pincer", "skills", 8, 12,
            w("Barmoqlari bilan teradi", "Берёт двумя пальцами", "Picks up with two fingers"),
            w(
                "Mayda narsani bosh va ko'rsatkich barmog'i bilan oladi.",
                "Берёт мелкие предметы большим и указательным пальцами.",
                "Picks up small things between thumb and forefinger.",
            ),
        ),
        m(
            "stands_alone", "skills", 7, 17,
            w("Mustaqil turadi", "Стоит самостоятельно", "Stands alone"),
            w(
                "Hech narsaga suyanmay bir necha soniya turadi. WHO: 7–17 oy.",
                "Стоит без опоры несколько секунд. ВОЗ: 7–17 мес.",
                "Stands without holding on for a few seconds. WHO: 7–17 months.",
            ),
        ),
        m(
            "first_word", "language", 10, 15,
            w("Birinchi so'z", "Первое слово", "First word"),
            w(
                "Ma'nosini bilib bitta so'z aytadi: \"ona\", \"ota\", \"ber\".",
                "Говорит первое осмысленное слово: «мама», «папа», «дай».",
                "Says a first word with meaning: \"mama\", \"dada\", \"give\".",
            ),
        ),
        m(
            "walks_alone", "skills", 8, 18,
            w("Mustaqil yuradi", "Ходит самостоятельно", "Walks alone"),
            w(
                "Hech kimga suyanmay kamida besh qadam tashlaydi. WHO: 8–18 oy.",
                "Делает не меньше пяти шагов без опоры. ВОЗ: 8–18 мес.",
                "Takes at least five steps without holding on. WHO: 8–18 months.",
            ),
        ),
        m(
            "points", "social", 12, 18,
            w("Barmog'i bilan ko'rsatadi", "Показывает пальцем", "Points at things"),
            w("Qiziqqan narsasini barmog'i bilan ko'rsatadi.", "Показывает пальцем на то, что интересно.", "Points to show you something interesting."),
        ),
        m(
            "two_words", "language", 18, 24,
            w("Ikki so'zni qo'shadi", "Соединяет два слова", "Puts two words together"),
            w("\"Ona ber\" kabi ikki so'zli ibora aytadi.", "Говорит фразы из двух слов, вроде «мама дай».", "Says two-word phrases such as \"mama give\"."),
        ),
    ).sortedWith(compareBy<MilestoneDef> { it.fromMonth }.thenBy { it.toMonth })

    private val byCode = all.associateBy { it.code }

    fun byCode(code: String): MilestoneDef? = byCode[code]
}
