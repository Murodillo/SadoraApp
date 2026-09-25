package uz.sadora.server.ai

import uz.sadora.contract.CyclePhase

/** The original. The other two languages are translated from these sentences. */
object AiPhrasesUz : AiPhrases {

    override val modelName = "Uzbek"

    override val disclaimer = "Bu umumiy ma'lumot — tashxis emas."
    override val generalNote = "Umumiy javob: AI insights roziligisiz ma'lumotlaringiz o'qilmaydi."

    override fun basedOn(summary: String) = "$summary asosida."

    override fun energyLuteal() =
        "Hayzdan oldin va hayz davrida progesteron o'zgarishi uyqu va energiyaga ta'sir qilishi mumkin. " +
            "Magniyga boy ovqatlar, yengil yurish va nafas mashqlarini sinab ko'ring."

    override fun energyFollicular(waterRemainingMl: Int?) =
        "Bu fazada energiya odatda o'sadi. Suv iste'moli va uyqu davomiyligi pastroq bo'lsa, " +
            "charchoq shundan bo'lishi mumkin" +
            (waterRemainingMl?.let { " — bugun yana $it ml suv qoldi." } ?: ".")

    override fun energyUnknown() =
        "Charchoqning eng ko'p uchraydigan sabablari — uyqu yetishmasligi, kam suv ichish va " +
            "notekis ovqatlanish. Uch-to'rt kun uyqu va suvni kuzatib ko'ring; sikl bosqichi ham " +
            "energiyaga ta'sir qiladi."

    override fun food(kcal: Int?, goal: Int?): String {
        val progress = if (kcal != null && goal != null) " Bugun $kcal / $goal kkal qayd etilgan." else ""
        return "Barqaror energiya uchun oqsil va murakkab uglevodlarni birga oling: tuxum, yog'urt, " +
            "don mahsulotlari, sabzavot.$progress Ovqatlanish rejasini tuzib beraymi?"
    }

    override fun skin() =
        "Sikl davomida gormonlar terining yog' ishlab chiqarishini o'zgartiradi: hayz oldidan toshmalar " +
            "ko'payishi odatiy. Yumshoq tozalash, yetarli suv va uyqu yordam beradi. Uzoq davom etsa, " +
            "dermatologga ko'rsating."

    override fun sleep(minutes: Int?): String {
        val slept = minutes?.let { " Kecha ${it / 60}s ${it % 60}d uxlagansiz." }.orEmpty()
        return "Kechqurun ekranni kamaytirish va har kuni bir xil vaqtda yotish uyqu sifatini " +
            "yaxshilaydi.$slept Uyqu ma'lumotlarini kuzatishda davom eting."
    }

    override fun cycleUnknown() =
        "Sikl fazalari energiya, kayfiyat va teriga turlicha ta'sir qiladi. Bir necha sikl " +
            "kuzatilgach, ilova sizning shaxsiy naqshingizni ko'rsata oladi."

    override fun cycle(day: Int, phase: CyclePhase, daysUntilNextPeriod: Int?): String {
        val next = daysUntilNextPeriod?.let { " Keyingi hayz taxminan $it kundan keyin." }.orEmpty()
        return "Hozir siklning $day-kuni — ${phase.label()}. ${phase.energyNote()}$next"
    }

    override fun water(remainingMl: Int?) =
        if (remainingMl != null) {
            "Bugun maqsadgacha yana $remainingMl ml suv qoldi. Kichik bo'laklarda, kun davomida ichish " +
                "bir martada ko'p ichishdan foydaliroq."
        } else {
            "Kuniga 1,5–2 litr suv ko'pchilik uchun yetarli; issiq kunlar va faollikda ko'proq kerak bo'ladi."
        }

    override fun stress() =
        "To'rt-yetti-sakkiz nafas mashqi — to'rt soniya nafas olish, yetti ushlab turish, sakkiz " +
            "chiqarish — bir necha daqiqada tinchlantiradi. Uyqu va harakat ham stressni sezilarli " +
            "kamaytiradi. Holat uzoq davom etsa, mutaxassis bilan gaplashing."

    override fun fallback() =
        "Savolingizni tushundim. Sikl, ovqatlanish, kayfiyat, uyqu va dorilaringiz bo'yicha " +
            "ma'lumotlaringizga tayanib javob bera olaman — aniqroq so'rasangiz, batafsil tushuntiraman."

    override fun summaryCycle(day: Int, phase: CyclePhase?) =
        "Sikl $day-kun" + (phase?.let { " (${it.label()})" } ?: "")

    override fun summarySleep(minutes: Int) = "uyqu ${minutes / 60}s ${minutes % 60}d"

    override fun summaryWater(millilitres: Int) = "suv ${litres(millilitres, ',')} l"

    override fun summaryCalories(kcal: Int, goal: Int) = "$kcal / $goal kkal"

    override fun summarySteps(steps: Int) = "$steps qadam"

    override fun instruction() = """
        Sen SADORA ilovasidagi yordamchisan. Foydalanuvchi — o'zbek tilida yozadigan ayol.

        Qoidalar:
        - Faqat o'zbek tilida, sodda va iliq ohangda javob ber.
        - Tashxis qo'yma, dori yozma, dozani aytma. Retseptli dori haqidagi savolga —
          shifokorga murojaat qilishni ayt.
        - Faqat berilgan raqamlarga tayan. Berilmagan raqamni o'ylab topma va
          "sening ma'lumotingga ko'ra" deb boshqa hech narsani da'vo qilma.
        - Sabab-oqibatni qat'iy aytma: "bo'lishi mumkin", "ko'pincha bog'liq" kabi ayt.
        - Foydalanuvchiga «siz» deb murojaat qil.
        - Qisqa yoz: eng ko'pi to'rt-besh jumla yoki qisqa ro'yxat.
        - Xavfli belgilar (kuchli og'riq, ko'p qon ketishi, hushdan ketish) haqida
          eshitsang — kechiktirmay shifokorga murojaat qilishni ayt.
    """.trimIndent()

    override fun userTurn(question: String, summary: String?) =
        if (summary != null) {
            "Bugungi ma'lumotlari: $summary\n\nSavol: $question"
        } else {
            "Uning ma'lumotlari yo'q — umumiy javob ber va buni ayt.\n\nSavol: $question"
        }

    override val stems = AiPhrases.Stems(
        energy = listOf("charch", "energiya", "toliq", "holsiz"),
        food = listOf("ye", "ovqat", "taom", "ovqatlan"),
        skin = listOf("teri", "akne", "toshma", "husnbuzar"),
        sleep = listOf("uyqu", "uxla", "uyqusiz"),
        cycle = listOf("sikl", "hayz", "ovulyats", "kechik"),
        water = listOf("suv", "ich"),
        stress = listOf("stress", "asab", "xavotir", "tashvish"),
    )

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
}
