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
    fun action(action: PetAction): String
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
    }
    override fun personality(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "Lotus guli ruhi — xotirjam va dono"
        PetKind.MOMIQ -> "Paxmoq mushukcha — mehribon, dam olishni sevadi"
        PetKind.LAYLO -> "Laylakcha — opadek g'amxo'r"
        PetKind.ANORXON -> "Anorcha — quvnoq, sog'lom ovqat ishqibozi"
        PetKind.OHU -> "Ohu bolasi — uyatchan va nozik"
    }
    override val choose = "Tanlash"
    override val chosen = "Tanlangan"
    override val premiumBanner = "Hamrohingiz Premium bilan uyg'onadi va har kuni maslahat bera boshlaydi."
    override val premiumButton = "Premium bilan uyg'otish"
    override val teaser = "Zzz… Premium bilan uyg'onsam, har kuni sizga yordam beraman."
    override val wake = "Uyg'otish"
    override val close = "Yopish"
    override fun action(action: PetAction) = when (action) {
        PetAction.FOOD_SCANNER -> "Rasmga olish"
        PetAction.MIND_JOURNAL -> "Yozish"
        PetAction.WATER -> "Suv qo'shish"
        PetAction.PARTNER -> "Ulash"
        PetAction.BADGES -> "Ko'rish"
        PetAction.DOCTOR_SHARE -> "Ulashish"
        PetAction.AI_CHAT -> "So'rash"
        PetAction.LEARN -> "O'qish"
        PetAction.MEDICATIONS -> "Eslatma qo'yish"
    }
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
    }
    override fun personality(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "Дух лотоса — спокойная и мудрая"
        PetKind.MOMIQ -> "Пушистый котёнок — ласковый, любит отдыхать"
        PetKind.LAYLO -> "Аистёнок — заботливый, как старшая сестра"
        PetKind.ANORXON -> "Гранатик — весёлый фанат здоровой еды"
        PetKind.OHU -> "Оленёнок — застенчивый и нежный"
    }
    override val choose = "Выбрать"
    override val chosen = "Выбран"
    override val premiumBanner = "С Premium ваш компаньон проснётся и начнёт помогать каждый день."
    override val premiumButton = "Разбудить с Premium"
    override val teaser = "Zzz… С Premium я проснусь и буду помогать вам каждый день."
    override val wake = "Разбудить"
    override val close = "Закрыть"
    override fun action(action: PetAction) = when (action) {
        PetAction.FOOD_SCANNER -> "Сфотографировать"
        PetAction.MIND_JOURNAL -> "Написать"
        PetAction.WATER -> "Добавить воду"
        PetAction.PARTNER -> "Подключить"
        PetAction.BADGES -> "Посмотреть"
        PetAction.DOCTOR_SHARE -> "Поделиться"
        PetAction.AI_CHAT -> "Спросить"
        PetAction.LEARN -> "Читать"
        PetAction.MEDICATIONS -> "Напоминание"
    }
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
    }
    override fun personality(pet: PetKind) = when (pet) {
        PetKind.NILUFAR -> "A lotus spirit — calm and wise"
        PetKind.MOMIQ -> "A fluffy kitten — cuddly, loves a rest"
        PetKind.LAYLO -> "A baby stork — caring like a big sister"
        PetKind.ANORXON -> "A pomegranate — cheerful healthy-food fan"
        PetKind.OHU -> "A baby fawn — shy and gentle"
    }
    override val choose = "Choose"
    override val chosen = "Chosen"
    override val premiumBanner = "With Premium your companion wakes up and starts helping every day."
    override val premiumButton = "Wake with Premium"
    override val teaser = "Zzz… With Premium I'll wake up and help you every day."
    override val wake = "Wake up"
    override val close = "Close"
    override fun action(action: PetAction) = when (action) {
        PetAction.FOOD_SCANNER -> "Take a photo"
        PetAction.MIND_JOURNAL -> "Write"
        PetAction.WATER -> "Add water"
        PetAction.PARTNER -> "Connect"
        PetAction.BADGES -> "See them"
        PetAction.DOCTOR_SHARE -> "Share"
        PetAction.AI_CHAT -> "Ask"
        PetAction.LEARN -> "Read"
        PetAction.MEDICATIONS -> "Set a reminder"
    }
}
