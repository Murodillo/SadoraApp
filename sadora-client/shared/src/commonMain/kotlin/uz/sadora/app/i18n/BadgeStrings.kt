package uz.sadora.app.i18n

import uz.sadora.contract.Badges

/**
 * The badge board and the unlock moment.
 *
 * The badge keys and thresholds come from the contract; only the words are here. A key
 * a newer server sends that this release does not know is skipped by the board rather
 * than shown with a raw name — [name] returns null for it.
 */
interface BadgeStrings {
    val title: String
    /** The line under the board's header. */
    val subtitle: String
    /** "5 / 41" — tiers reached out of all there are. */
    fun earnedOf(earned: Int, total: Int): String
    fun tierName(tier: Int, maxTier: Int): String
    val locked: String
    /** "Keyingi: Kumush — 12 / 30" */
    fun next(tierName: String, progress: Int, target: Int): String
    val allDone: String
    /** The note at the bottom of the detail sheet: what badges are for. */
    val principle: String

    // ---- the unlock overlay ----
    val newBadge: String
    /** "Kumush bosqich!" */
    fun tierReached(tierName: String): String
    val continueLabel: String
    /** "Yana 3 ta" — on the overlay when several arrived at once. */
    fun more(count: Int): String
    val skipAll: String
    val viewAll: String

    // ---- wearing one ----
    val wear: String
    val takeOff: String
    val wearing: String
    /** Under the button: where the worn badge shows. */
    val wearHint: String
    /** The button for a free account: wearing is Premium. */
    val wearPremium: String
    val wearPremiumHint: String

    // ---- the badges page ----
    val earnedSection: String
    val lockedSection: String
    /** Above the worn badge's name on the page's header. */
    val wornLabel: String
    val wornNone: String
    /** The same line for a free account, which opens the paywall. */
    val wornPremiumOnly: String

    fun name(key: String): String?
    /** What a tier asks for, with its threshold already in it. */
    fun goal(key: String, target: Int): String
}

object BadgesUz : BadgeStrings {
    override val title = "Nishonlar"
    override val subtitle = "Har bir odatingiz uchun — sog'ligingiz raqamlari uchun emas"
    override fun earnedOf(earned: Int, total: Int) = "$earned / $total"
    override fun tierName(tier: Int, maxTier: Int) = when {
        maxTier == 1 -> "Maxsus"
        tier <= 1 -> "Bronza"
        tier == 2 -> "Kumush"
        else -> "Oltin"
    }
    override val locked = "Hali ochilmagan"
    override fun next(tierName: String, progress: Int, target: Int) = "Keyingisi: $tierName — $progress / $target"
    override val allDone = "Barcha bosqichlar olindi!"
    override val principle =
        "Nishonlar qilgan ishingiz uchun beriladi — belgilash, o'qish, g'amxo'rlik. Tanangiz ko'rsatkichlari uchun hech qachon nishon yo'q."
    override val newBadge = "Yangi nishon!"
    override fun tierReached(tierName: String) = "$tierName bosqich"
    override val continueLabel = "Ajoyib!"
    override fun more(count: Int) = "Yana $count ta nishon"
    override val skipAll = "Hammasini yopish"
    override val viewAll = "Hammasi"
    override val earnedSection = "Olingan nishonlar"
    override val lockedSection = "Keyingi maqsadlar"
    override val wornLabel = "Ismingiz yonida"
    override val wornNone = "Hali nishon taqmagansiz — olingan nishonni bosing va ismingiz yonida taqing."
    override val wornPremiumOnly = "Premium bilan sevimli nishoningizni profil va postlarda ismingiz yonida taqing."
    override val wearPremium = "Premium bilan taqish"
    override val wearPremiumHint = "Nishonni ism yonida taqish — Premium imkoniyati. Nishonlarni olish esa hamma uchun bepul."
    override val wear = "Ismim yonida taqish"
    override val takeOff = "Yechib olish"
    override val wearing = "Taqilgan"
    override val wearHint = "Profilingizda va chatdagi post-izohlaringizda ismingizdan keyin ko'rinadi."

    override fun name(key: String) = when (key) {
        Badges.FIRST_STEP -> "Birinchi qadam"
        Badges.STREAK -> "Olovli ketma-ketlik"
        Badges.LOYAL -> "Sodiq hamroh"
        Badges.CYCLE -> "Oy kalendari"
        Badges.DAILY_LOG -> "O'zimni tinglayman"
        Badges.WATER -> "Suv parisi"
        Badges.MEDS -> "Intizom"
        Badges.MEALS -> "Ongli ovqatlanish"
        Badges.JOURNAL -> "Kundalik egasi"
        Badges.MIND -> "Xotirjamlik"
        Badges.READER -> "Bilimdon"
        Badges.FRIENDS -> "Dugonalar"
        Badges.PARTNER -> "Yaqinim"
        Badges.DOCTOR -> "G'amxo'rlik"
        Badges.COMMUNITY -> "Ovozim"
        Badges.SYMPTOMS -> "Tana kuzatuvchisi"
        Badges.MOOD -> "Kayfiyat kundaligi"
        Badges.CALM_MINUTES -> "Sokin daqiqalar"
        Badges.SCANNER -> "Skaner ustasi"
        Badges.DEVICES -> "Aqlli hamroh"
        Badges.HYDRO -> "Daryo"
        Badges.CURIOUS -> "Qiziquvchan"
        Badges.SHARE -> "Ochiq yurak"
        Badges.HELPER -> "Mehribon"
        Badges.LOVED -> "Sevimli"
        Badges.SHOPPER -> "Xaridor"
        Badges.GARDENER -> "Gul bog'boni"
        else -> null
    }

    override fun goal(key: String, target: Int) = when (key) {
        Badges.FIRST_STEP -> "Sadora bilan birinchi kuningiz"
        Badges.STREAK -> "Ilovaga $target kun ketma-ket kiring"
        Badges.LOYAL -> "Jami $target kun Sadora bilan"
        Badges.CYCLE -> "$target ta hayz davrini belgilang"
        Badges.DAILY_LOG -> "$target kun holatingizni belgilang"
        Badges.WATER -> "Suv maqsadiga $target kun yeting"
        Badges.MEDS -> "Dori qabulini $target marta tasdiqlang"
        Badges.MEALS -> "$target ta ovqat qo'shing"
        Badges.JOURNAL -> "Kundalikka $target ta yozuv qoldiring"
        Badges.MIND -> "$target ta nafas yoki meditatsiya mashg'uloti"
        Badges.READER -> "$target ta maqolani o'qing"
        Badges.FRIENDS -> "$target ta dugonangizni taklif qiling"
        Badges.PARTNER -> "Yaqiningizni ulang"
        Badges.DOCTOR -> "Shifokor bilan $target marta bog'laning yoki qabul qo'shing"
        Badges.COMMUNITY -> "Chatda $target ta post yozing"
        Badges.SYMPTOMS -> "$target kun alomatlaringizni belgilang"
        Badges.MOOD -> "$target kun kayfiyatingizni belgilang"
        Badges.CALM_MINUTES -> "Jami $target daqiqa nafas va meditatsiya"
        Badges.SCANNER -> "Ovqatni $target marta skanerlang"
        Badges.DEVICES -> "Soat yoki telefon sog'lik ilovasini ulang"
        Badges.HYDRO -> "Jami $target litr suv belgilang"
        Badges.CURIOUS -> "Sadora AI'ga $target ta savol bering"
        Badges.SHARE -> "Shifokorga $target marta profilingizni ko'rsating"
        Badges.HELPER -> "Chatda $target ta izoh qoldiring"
        Badges.LOVED -> "Postlaringiz $target ta yurak olsin"
        Badges.SHOPPER -> "Gul do'konidan $target marta xarid qiling"
        Badges.GARDENER -> "Jami $target gul yig'ing"
        else -> ""
    }
}

object BadgesRu : BadgeStrings {
    override val title = "Значки"
    override val subtitle = "За привычки — никогда за цифры вашего тела"
    override fun earnedOf(earned: Int, total: Int) = "$earned / $total"
    override fun tierName(tier: Int, maxTier: Int) = when {
        maxTier == 1 -> "Особый"
        tier <= 1 -> "Бронза"
        tier == 2 -> "Серебро"
        else -> "Золото"
    }
    override val locked = "Ещё не открыт"
    override fun next(tierName: String, progress: Int, target: Int) = "Дальше: $tierName — $progress / $target"
    override val allDone = "Все уровни получены!"
    override val principle =
        "Значки даются за то, что вы делаете, — отмечаете, читаете, заботитесь о себе. За показатели тела значков не бывает."
    override val newBadge = "Новый значок!"
    override fun tierReached(tierName: String) = "Уровень: $tierName"
    override val continueLabel = "Здорово!"
    override fun more(count: Int) = "Ещё значков: $count"
    override val skipAll = "Закрыть все"
    override val viewAll = "Все"
    override val earnedSection = "Полученные значки"
    override val lockedSection = "Следующие цели"
    override val wornLabel = "Рядом с вашим именем"
    override val wornNone = "Вы ещё не носите значок — нажмите на полученный и наденьте его рядом с именем."
    override val wornPremiumOnly = "С Premium любимый значок будет рядом с вашим именем в профиле и постах."
    override val wearPremium = "Носить с Premium"
    override val wearPremiumHint = "Носить значок рядом с именем — возможность Premium. Получать значки может каждый, бесплатно."
    override val wear = "Носить рядом с именем"
    override val takeOff = "Снять"
    override val wearing = "Надет"
    override val wearHint = "Виден после вашего имени в профиле и в ваших постах и комментариях в чате."

    override fun name(key: String) = when (key) {
        Badges.FIRST_STEP -> "Первый шаг"
        Badges.STREAK -> "Огненная серия"
        Badges.LOYAL -> "Верная спутница"
        Badges.CYCLE -> "Лунный календарь"
        Badges.DAILY_LOG -> "Слышу себя"
        Badges.WATER -> "Водная фея"
        Badges.MEDS -> "Дисциплина"
        Badges.MEALS -> "Осознанное питание"
        Badges.JOURNAL -> "Хранительница дневника"
        Badges.MIND -> "Безмятежность"
        Badges.READER -> "Знаток"
        Badges.FRIENDS -> "Подруги"
        Badges.PARTNER -> "Мой близкий"
        Badges.DOCTOR -> "Забота"
        Badges.COMMUNITY -> "Мой голос"
        Badges.SYMPTOMS -> "Слежу за телом"
        Badges.MOOD -> "Дневник настроения"
        Badges.CALM_MINUTES -> "Тихие минуты"
        Badges.SCANNER -> "Мастер сканера"
        Badges.DEVICES -> "Умная спутница"
        Badges.HYDRO -> "Река"
        Badges.CURIOUS -> "Любознательная"
        Badges.SHARE -> "Открытое сердце"
        Badges.HELPER -> "Добрая душа"
        Badges.LOVED -> "Любимица"
        Badges.SHOPPER -> "Покупательница"
        Badges.GARDENER -> "Садовница"
        else -> null
    }

    override fun goal(key: String, target: Int) = when (key) {
        Badges.FIRST_STEP -> "Ваш первый день с Sadora"
        Badges.STREAK -> "Заходите $target дней подряд"
        Badges.LOYAL -> "Всего $target дней с Sadora"
        Badges.CYCLE -> "Отметьте месячные: $target"
        Badges.DAILY_LOG -> "Отмечайте самочувствие $target дней"
        Badges.WATER -> "Цель по воде: $target дней"
        Badges.MEDS -> "Подтвердите приём лекарств $target раз"
        Badges.MEALS -> "Добавьте приёмы пищи: $target"
        Badges.JOURNAL -> "Записи в дневнике: $target"
        Badges.MIND -> "Дыхание или медитация: $target раз"
        Badges.READER -> "Прочитайте статей: $target"
        Badges.FRIENDS -> "Пригласите подруг: $target"
        Badges.PARTNER -> "Подключите близкого человека"
        Badges.DOCTOR -> "Обратитесь к врачу или запишите визит: $target"
        Badges.COMMUNITY -> "Напишите постов в чате: $target"
        Badges.SYMPTOMS -> "Отмечайте симптомы $target дней"
        Badges.MOOD -> "Отмечайте настроение $target дней"
        Badges.CALM_MINUTES -> "Всего $target минут дыхания и медитации"
        Badges.SCANNER -> "Отсканируйте еду: $target раз"
        Badges.DEVICES -> "Подключите часы или «Здоровье» на телефоне"
        Badges.HYDRO -> "Всего $target литров воды"
        Badges.CURIOUS -> "Задайте Sadora AI вопросов: $target"
        Badges.SHARE -> "Покажите профиль врачу: $target раз"
        Badges.HELPER -> "Оставьте комментариев в чате: $target"
        Badges.LOVED -> "Соберите сердечек на постах: $target"
        Badges.SHOPPER -> "Покупки в магазине Gul: $target"
        Badges.GARDENER -> "Соберите всего $target gul"
        else -> ""
    }
}

object BadgesEn : BadgeStrings {
    override val title = "Badges"
    override val subtitle = "For your habits — never for your body's numbers"
    override fun earnedOf(earned: Int, total: Int) = "$earned / $total"
    override fun tierName(tier: Int, maxTier: Int) = when {
        maxTier == 1 -> "Special"
        tier <= 1 -> "Bronze"
        tier == 2 -> "Silver"
        else -> "Gold"
    }
    override val locked = "Not unlocked yet"
    override fun next(tierName: String, progress: Int, target: Int) = "Next: $tierName — $progress / $target"
    override val allDone = "Every tier earned!"
    override val principle =
        "Badges are for what you do — logging, reading, looking after yourself. There is never a badge for a number your body produced."
    override val newBadge = "New badge!"
    override fun tierReached(tierName: String) = "$tierName tier"
    override val continueLabel = "Lovely!"
    override fun more(count: Int) = "$count more"
    override val skipAll = "Close all"
    override val viewAll = "See all"
    override val earnedSection = "Earned badges"
    override val lockedSection = "Next goals"
    override val wornLabel = "Next to your name"
    override val wornNone = "You aren't wearing a badge yet — tap one you've earned to wear it next to your name."
    override val wornPremiumOnly = "With Premium, wear your favourite badge next to your name on your profile and posts."
    override val wearPremium = "Wear it with Premium"
    override val wearPremiumHint = "Wearing a badge next to your name is a Premium feature. Earning badges stays free for everyone."
    override val wear = "Wear next to my name"
    override val takeOff = "Take off"
    override val wearing = "Wearing"
    override val wearHint = "Shown after your name on your profile and on your chat posts and comments."

    override fun name(key: String) = when (key) {
        Badges.FIRST_STEP -> "First step"
        Badges.STREAK -> "On fire"
        Badges.LOYAL -> "Faithful friend"
        Badges.CYCLE -> "Moon keeper"
        Badges.DAILY_LOG -> "Body listener"
        Badges.WATER -> "Water fairy"
        Badges.MEDS -> "Right on time"
        Badges.MEALS -> "Mindful eater"
        Badges.JOURNAL -> "Journal keeper"
        Badges.MIND -> "Inner calm"
        Badges.READER -> "Bookworm"
        Badges.FRIENDS -> "Girl gang"
        Badges.PARTNER -> "My person"
        Badges.DOCTOR -> "Self-care"
        Badges.COMMUNITY -> "My voice"
        Badges.SYMPTOMS -> "Body tracker"
        Badges.MOOD -> "Mood diary"
        Badges.CALM_MINUTES -> "Quiet minutes"
        Badges.SCANNER -> "Scanner pro"
        Badges.DEVICES -> "Smart companion"
        Badges.HYDRO -> "River"
        Badges.CURIOUS -> "Curious mind"
        Badges.SHARE -> "Open heart"
        Badges.HELPER -> "Kind soul"
        Badges.LOVED -> "Beloved"
        Badges.SHOPPER -> "Shopper"
        Badges.GARDENER -> "Gul gardener"
        else -> null
    }

    override fun goal(key: String, target: Int) = when (key) {
        Badges.FIRST_STEP -> "Your first day with Sadora"
        Badges.STREAK -> "Open the app $target days in a row"
        Badges.LOYAL -> "$target days with Sadora in total"
        Badges.CYCLE -> "Log $target periods"
        Badges.DAILY_LOG -> "Log how you feel on $target days"
        Badges.WATER -> "Reach your water goal on $target days"
        Badges.MEDS -> "Confirm $target doses"
        Badges.MEALS -> "Log $target meals"
        Badges.JOURNAL -> "Write $target journal entries"
        Badges.MIND -> "Finish $target breathing or meditation sessions"
        Badges.READER -> "Read $target articles"
        Badges.FRIENDS -> "Invite $target friends"
        Badges.PARTNER -> "Connect your person"
        Badges.DOCTOR -> "See a doctor or add a visit $target times"
        Badges.COMMUNITY -> "Write $target posts in the chat"
        Badges.SYMPTOMS -> "Log symptoms on $target days"
        Badges.MOOD -> "Log your mood on $target days"
        Badges.CALM_MINUTES -> "$target minutes of breathing and meditation"
        Badges.SCANNER -> "Scan your food $target times"
        Badges.DEVICES -> "Connect a watch or your phone's health app"
        Badges.HYDRO -> "Log $target litres of water in total"
        Badges.CURIOUS -> "Ask Sadora AI $target questions"
        Badges.SHARE -> "Show your profile to a doctor $target times"
        Badges.HELPER -> "Leave $target comments in the chat"
        Badges.LOVED -> "Get $target hearts on your posts"
        Badges.SHOPPER -> "Redeem in the Gul shop $target times"
        Badges.GARDENER -> "Collect $target gul in total"
        else -> ""
    }
}
