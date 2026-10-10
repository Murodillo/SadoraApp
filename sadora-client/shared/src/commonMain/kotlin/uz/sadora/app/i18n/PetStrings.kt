package uz.sadora.app.i18n

import uz.sadora.contract.PetAction
import uz.sadora.contract.PetKind

/**
 * The companion: its picker, and the words around its bubble. What the pet itself says
 * comes from the server in her language; only the frame is here.
 */
interface PetStrings {
    val title: String
    /** The picker's line under the title: what the pet is for. */
    val subtitle: String
    fun name(pet: PetKind): String
    /** One line of character under each name in the picker. */
    fun personality(pet: PetKind): String
    val choose: String
    val chosen: String
    /** On the picker for a free account. */
    val premiumBanner: String
    val premiumButton: String
    /** The sleeping pet's bubble for a free account. */
    val teaser: String
    val wake: String
    val close: String
    /** What a tap on the pet in its chat header does, read to a screen reader. */
    val poke: String
    fun action(action: PetAction): String

    // ---- in the AI chat, where the companion is the assistant
    /** Under the pet's name in the chat header. */
    val chatSubtitle: String
    /** The first thing it says before she asks anything. */
    fun chatIntro(name: String): String
    /** On the free plan's chat preview, under the sleeping pet. */
    fun chatAsleep(name: String): String

    // ---- the legendary pet, sold once
    /** The tag on its card. */
    val legendary: String
    /** On a pet she bought. */
    val owned: String
    fun buy(price: String): String
    /** Under its name on the buy sheet: what she gets. */
    val buyBody: String
    /** Told before she pays, when she has no Premium: it would sleep. */
    val needsPremium: String
    /** The moments it acts out, shown on the buy sheet. */
    fun moment(index: Int): String
    val momentCount: Int
    val askYaqinim: String
    /** What a request to Yaqinim asks for. */
    val requestWhat: String
    val askBody: String
    val paying: String
    val payReopen: String
    val storePending: String
    val noProvider: String
    val bought: String
    /** After buying it without Premium: it sleeps until Premium. */
    val boughtAsleep: String
    /** Its one-off visit. */
    val offer: String
    val offerSee: String
}

object PetUz : PetStrings {
    override val title = "AI hamroh"
    override val subtitle = "Har bir qaydingizdan keyin maslahat beradi va ilovaning foydali imkoniyatlarini eslatib turadi."
    override fun name(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "Nilufar"
        PetKind.MOMIQ -> "Momiq"
        PetKind.LAYLO -> "Laylo"
        PetKind.ANORXON -> "Anorxon"
        PetKind.OHU -> "Ohu"
        PetKind.HUMO -> "Humo"
    }
    override fun personality(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "Lotus guli ruhi — xotirjam va dono"
        PetKind.MOMIQ -> "Paxmoq mushukcha — mehribon, dam olishni sevadi"
        PetKind.LAYLO -> "Laylakcha — opadek g'amxo'r"
        PetKind.ANORXON -> "Anorcha — quvnoq, sog'lom ovqat ishqibozi"
        PetKind.OHU -> "Ohu bolasi — uyatchan va nozik"
        PetKind.HUMO -> "Afsonaviy baxt qushi — dono va ulug'vor"
    }
    override val choose = "Tanlash"
    override val chosen = "Tanlangan"
    override val premiumBanner = "Hamrohingiz Premium bilan uyg'onadi va har kuni maslahat bera boshlaydi."
    override val premiumButton = "Premium bilan uyg'otish"
    override val teaser = "Zzz… Premium bilan uyg'onsam, har kuni sizga yordam beraman."
    override val wake = "Uyg'otish"
    override val close = "Yopish"
    override val poke = "Erkalash"
    override fun action(action: PetAction) = when (action) {
        PetAction.FOOD_SCANNER -> "Rasmga olish"
        PetAction.MIND_JOURNAL -> "Yozish"
        PetAction.WATER -> "Suv qo'shish"
        PetAction.PARTNER -> "Yaqinni ulash"
        PetAction.BADGES -> "Ko'rish"
        PetAction.DOCTOR_SHARE -> "Shifokorga ko'rsatish"
        PetAction.AI_CHAT -> "So'rash"
        PetAction.LEARN -> "O'qish"
        PetAction.MEDICATIONS -> "Eslatma qo'yish"
    }
    override val chatSubtitle = "SADORA AI · shaxsiy yordamchingiz"
    override fun chatIntro(name: String) = "Salom! Men $name — sizning shaxsiy yordamchingiz. " +
        "Sikl, ovqatlanish, uyqu yoki kayfiyat haqida so'rang, ma'lumotlaringizga qarab javob beraman. " +
        "Men shifokor emasman va tashxis qo'ymayman."
    override fun chatAsleep(name: String) = "$name hozir uxlayapti. Premium bilan uyg'onib, savollaringizga javob beradi."
    override val legendary = "Afsonaviy"
    override val owned = "Sizniki"
    override fun buy(price: String) = "Sotib olish · $price"
    override val buyBody = "Bir marta sotib olinadi va hisobingizda doim qoladi. O'n xil jonli harakat, oltin maslahat oynasi va o'z duolari bilan."
    override val needsPremium = "Humo gapirishi uchun Premium kerak. Premium bo'lmasa, u boshqa hamrohlar kabi uxlab turadi."
    private val moments = listOf("Uchib kelish", "Uchib ketish", "Salomlashish", "Quvonch", "O'ylash", "Uyqu", "Tasalli", "Bayram", "Suv va ovqat", "Pat tozalash")
    override fun moment(index: Int) = moments[index]
    override val momentCount = moments.size
    override val askYaqinim = "Yaqinimdan so'rash"
    override val requestWhat = "Humo — afsonaviy AI hamroh (gapirishi uchun Premium kerak)"
    override val askBody = "Yaqiningizdan Humoni sovg'a qilishni so'rang. To'lagach, u darhol sizniki bo'ladi. Eslatma: Humo faqat Premium bilan gapiradi."
    override val paying = "To'lov kutilmoqda…"
    override val payReopen = "To'lov sahifasini qayta ochish"
    override val storePending = "To'lov tasdiqlanishi kutilmoqda — tushishi bilan Humo o'zi keladi."
    override val noProvider = "Hozircha bu yerda to'lov usuli yo'q."
    override val bought = "Humo endi sizniki! 💛"
    override val boughtAsleep = "Humo sizniki, lekin hozir uxlayapti. Premium bilan uyg'onib, maslahat bera boshlaydi."
    override val offer = "Salom! Men Humo — baxt qushi. Meni ham uyingizga olasizmi?"
    override val offerSee = "Ko'rish"
}

object PetRu : PetStrings {
    override val title = "AI-компаньон"
    override val subtitle = "После каждой записи подскажет что-то полезное и напомнит о возможностях приложения."
    override fun name(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "Нилуфар"
        PetKind.MOMIQ -> "Момик"
        PetKind.LAYLO -> "Лайло"
        PetKind.ANORXON -> "Анорхон"
        PetKind.OHU -> "Оху"
        PetKind.HUMO -> "Хумо"
    }
    override fun personality(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "Дух лотоса — спокойная и мудрая"
        PetKind.MOMIQ -> "Пушистый котёнок — ласковый, любит отдыхать"
        PetKind.LAYLO -> "Аистёнок — заботливый, как старшая сестра"
        PetKind.ANORXON -> "Гранатик — весёлый фанат здоровой еды"
        PetKind.OHU -> "Оленёнок — застенчивый и нежный"
        PetKind.HUMO -> "Легендарная птица счастья — мудрая и величественная"
    }
    override val choose = "Выбрать"
    override val chosen = "Выбран"
    override val premiumBanner = "С Premium ваш компаньон проснётся и начнёт помогать каждый день."
    override val premiumButton = "Разбудить с Premium"
    override val teaser = "Zzz… С Premium я проснусь и буду помогать вам каждый день."
    override val wake = "Разбудить"
    override val close = "Закрыть"
    override val poke = "Погладить"
    override fun action(action: PetAction) = when (action) {
        PetAction.FOOD_SCANNER -> "Сфотографировать"
        PetAction.MIND_JOURNAL -> "Написать"
        PetAction.WATER -> "Добавить воду"
        PetAction.PARTNER -> "Подключить близкого"
        PetAction.BADGES -> "Посмотреть"
        PetAction.DOCTOR_SHARE -> "Показать врачу"
        PetAction.AI_CHAT -> "Спросить"
        PetAction.LEARN -> "Читать"
        PetAction.MEDICATIONS -> "Напоминание"
    }
    override val chatSubtitle = "SADORA AI · ваш личный помощник"
    override fun chatIntro(name: String) = "Привет! Я $name — ваш личный помощник. " +
        "Спрашивайте о цикле, питании, сне или настроении — отвечу с учётом ваших данных. " +
        "Я не врач и не ставлю диагнозов."
    override fun chatAsleep(name: String) = "$name сейчас спит. С Premium проснётся и ответит на ваши вопросы."
    override val legendary = "Легендарный"
    override val owned = "Ваш"
    override fun buy(price: String) = "Купить · $price"
    override val buyBody = "Покупается один раз и навсегда остаётся в вашем аккаунте. Десять живых движений, золотое окно советов и свои благословения."
    override val needsPremium = "Чтобы Хумо заговорил, нужен Premium. Без него он спит, как и другие компаньоны."
    private val moments = listOf("Прилёт", "Отлёт", "Привет", "Радость", "Раздумье", "Сон", "Утешение", "Праздник", "Вода и еда", "Чистит перья")
    override fun moment(index: Int) = moments[index]
    override val momentCount = moments.size
    override val askYaqinim = "Попросить близкого"
    override val requestWhat = "Хумо — легендарный AI-компаньон (чтобы он говорил, нужен Premium)"
    override val askBody = "Попросите близкого человека подарить вам Хумо. Как только он(а) оплатит, Хумо станет вашим. Учтите: Хумо говорит только с Premium."
    override val paying = "Ждём оплату…"
    override val payReopen = "Открыть страницу оплаты снова"
    override val storePending = "Ждём подтверждения оплаты — как только она пройдёт, Хумо прилетит сам."
    override val noProvider = "Здесь пока нет способа оплаты."
    override val bought = "Хумо теперь ваш! 💛"
    override val boughtAsleep = "Хумо ваш, но сейчас спит. С Premium он проснётся и начнёт помогать."
    override val offer = "Привет! Я Хумо — птица счастья. Возьмёте меня к себе?"
    override val offerSee = "Посмотреть"
}

object PetEn : PetStrings {
    override val title = "AI companion"
    override val subtitle = "After each entry it offers a helpful tip and reminds you of what the app can do."
    override fun name(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "Nilufar"
        PetKind.MOMIQ -> "Momiq"
        PetKind.LAYLO -> "Laylo"
        PetKind.ANORXON -> "Anorxon"
        PetKind.OHU -> "Ohu"
        PetKind.HUMO -> "Humo"
    }
    override fun personality(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "A lotus spirit — calm and wise"
        PetKind.MOMIQ -> "A fluffy kitten — cuddly, loves a rest"
        PetKind.LAYLO -> "A baby stork — caring like a big sister"
        PetKind.ANORXON -> "A pomegranate — cheerful healthy-food fan"
        PetKind.OHU -> "A baby fawn — shy and gentle"
        PetKind.HUMO -> "The legendary bird of happiness — wise and regal"
    }
    override val choose = "Choose"
    override val chosen = "Chosen"
    override val premiumBanner = "With Premium your companion wakes up and starts helping every day."
    override val premiumButton = "Wake with Premium"
    override val teaser = "Zzz… With Premium I'll wake up and help you every day."
    override val wake = "Wake up"
    override val close = "Close"
    override val poke = "Pet it"
    override fun action(action: PetAction) = when (action) {
        PetAction.FOOD_SCANNER -> "Take a photo"
        PetAction.MIND_JOURNAL -> "Write"
        PetAction.WATER -> "Add water"
        PetAction.PARTNER -> "Connect someone close"
        PetAction.BADGES -> "See them"
        PetAction.DOCTOR_SHARE -> "Show your doctor"
        PetAction.AI_CHAT -> "Ask"
        PetAction.LEARN -> "Read"
        PetAction.MEDICATIONS -> "Set a reminder"
    }
    override val chatSubtitle = "SADORA AI · your personal assistant"
    override fun chatIntro(name: String) = "Hi! I'm $name, your personal assistant. " +
        "Ask me about your cycle, food, sleep or mood and I'll answer with your own data in mind. " +
        "I'm not a doctor and can't diagnose."
    override fun chatAsleep(name: String) = "$name is asleep right now. With Premium it wakes up and answers your questions."
    override val legendary = "Legendary"
    override val owned = "Yours"
    override fun buy(price: String) = "Buy · $price"
    override val buyBody = "Bought once and stays in your account for good. Ten living moves, a golden tip bubble and blessings of its own."
    override val needsPremium = "Humo needs Premium to speak. Without it, it sleeps like the other companions."
    private val moments = listOf("Flying in", "Flying off", "Hello", "Joy", "Thinking", "Sleep", "Comfort", "Celebration", "Water and food", "Preening")
    override fun moment(index: Int) = moments[index]
    override val momentCount = moments.size
    override val askYaqinim = "Ask Yaqinim"
    override val requestWhat = "Humo — the legendary AI companion (needs Premium to speak)"
    override val askBody = "Ask someone close to give you Humo. As soon as they pay, it's yours. Note: Humo only speaks with Premium."
    override val paying = "Waiting for the payment…"
    override val payReopen = "Open payment page again"
    override val storePending = "Waiting for the payment to clear — Humo will fly in as soon as it does."
    override val noProvider = "There's no way to pay here yet."
    override val bought = "Humo is yours! 💛"
    override val boughtAsleep = "Humo is yours, but asleep for now. With Premium it wakes up and starts helping."
    override val offer = "Hello! I'm Humo, the bird of happiness. Will you take me home?"
    override val offerSee = "Take a look"
}
