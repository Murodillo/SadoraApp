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
    /** Read out for the tick beside a tier she has reached on the ladder. */
    val tierReached: String
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
    override val tierReached = "Erishildi"
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
        Badges.FIRST_STEP -> "SADORA bilan birinchi kuningiz"
        Badges.STREAK -> "Ilovaga $target kun ketma-ket kiring"
        Badges.LOYAL -> "Jami $target kun SADORA bilan"
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
        Badges.DOCTOR -> "Shifokor bilan $target marta bog'laning yoki ko'rik qo'shing"
        Badges.COMMUNITY -> "Chatda $target ta post yozing"
        Badges.SYMPTOMS -> "$target kun alomatlaringizni belgilang"
        Badges.MOOD -> "$target kun kayfiyatingizni belgilang"
        Badges.CALM_MINUTES -> "Jami $target daqiqa nafas va meditatsiya"
        Badges.SCANNER -> "Ovqatni $target marta skanerlang"
        Badges.DEVICES -> "Soat yoki telefon sog'lik ilovasini ulang"
        Badges.HYDRO -> "Jami $target litr suv belgilang"
        Badges.CURIOUS -> "SADORA AI'ga $target ta savol bering"
        Badges.SHARE -> "Shifokorga $target marta profilingizni ko'rsating"
        Badges.HELPER -> "Chatda $target ta izoh qoldiring"
        Badges.LOVED -> "Postlaringiz $target ta yurak olsin"
        Badges.SHOPPER -> "Gul do'konidan $target marta xarid qiling"
        Badges.GARDENER -> "Jami $target Gul yig'ing"
        else -> ""
    }
}

object BadgesRu : BadgeStrings {
    private fun days(n: Int) = ru(n, "день", "дня", "дней")
    private fun times(n: Int) = ru(n, "раз", "раза", "раз")
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
    override val tierReached = "Достигнуто"
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
        Badges.PARTNER -> "Yaqinim"
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
        Badges.FIRST_STEP -> "Ваш первый день с SADORA"
        Badges.STREAK -> "Заходите $target ${days(target)} подряд"
        Badges.LOYAL -> "Всего $target ${days(target)} с SADORA"
        Badges.CYCLE -> "Отметьте месячные $target ${times(target)}"
        Badges.DAILY_LOG -> "Отмечайте самочувствие $target ${days(target)}"
        Badges.WATER -> "Достигайте цели по воде $target ${days(target)}"
        Badges.MEDS -> "Подтвердите приём лекарств $target ${times(target)}"
        Badges.MEALS -> "Добавьте $target ${ru(target, "приём", "приёма", "приёмов")} пищи"
        Badges.JOURNAL -> "Сделайте $target ${ru(target, "запись", "записи", "записей")} в дневнике"
        Badges.MIND -> "Пройдите $target ${ru(target, "сеанс", "сеанса", "сеансов")} дыхания или медитации"
        Badges.READER -> "Прочитайте $target ${ru(target, "статью", "статьи", "статей")}"
        Badges.FRIENDS -> "Пригласите $target ${ru(target, "подругу", "подруги", "подруг")}"
        Badges.PARTNER -> "Подключите близкого человека"
        Badges.DOCTOR -> "Обратитесь к врачу или добавьте визит $target ${times(target)}"
        Badges.COMMUNITY -> "Напишите $target ${ru(target, "пост", "поста", "постов")} в чате"
        Badges.SYMPTOMS -> "Отмечайте симптомы $target ${days(target)}"
        Badges.MOOD -> "Отмечайте настроение $target ${days(target)}"
        Badges.CALM_MINUTES -> "Всего $target ${ru(target, "минута", "минуты", "минут")} дыхания и медитации"
        Badges.SCANNER -> "Отсканируйте еду $target ${times(target)}"
        Badges.DEVICES -> "Подключите часы или «Здоровье» на телефоне"
        Badges.HYDRO -> "Всего $target ${ru(target, "литр", "литра", "литров")} воды"
        Badges.CURIOUS -> "Задайте SADORA AI $target ${ru(target, "вопрос", "вопроса", "вопросов")}"
        Badges.SHARE -> "Покажите профиль врачу $target ${times(target)}"
        Badges.HELPER -> "Оставьте $target ${ru(target, "комментарий", "комментария", "комментариев")} в чате"
        Badges.LOVED -> "Соберите $target ${ru(target, "сердечко", "сердечка", "сердечек")} на постах"
        Badges.SHOPPER -> "Сделайте $target ${ru(target, "покупку", "покупки", "покупок")} в магазине Gul"
        Badges.GARDENER -> "Соберите всего $target Gul"
        else -> ""
    }
}

object BadgesEn : BadgeStrings {
    private fun times(n: Int) = when (n) {
        1 -> "once"
        2 -> "twice"
        else -> "$n times"
    }
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
    override val tierReached = "Reached"
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
        Badges.PARTNER -> "Yaqinim"
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
        Badges.FIRST_STEP -> "Your first day with SADORA"
        Badges.STREAK -> "Open the app $target ${en(target, "day", "days")} in a row"
        Badges.LOYAL -> "$target ${en(target, "day", "days")} with SADORA in total"
        Badges.CYCLE -> "Log $target ${en(target, "period", "periods")}"
        Badges.DAILY_LOG -> "Log how you feel on $target ${en(target, "day", "days")}"
        Badges.WATER -> "Reach your water goal on $target ${en(target, "day", "days")}"
        Badges.MEDS -> "Confirm $target ${en(target, "dose", "doses")}"
        Badges.MEALS -> "Log $target ${en(target, "meal", "meals")}"
        Badges.JOURNAL -> "Write $target journal ${en(target, "entry", "entries")}"
        Badges.MIND -> "Finish $target breathing or meditation ${en(target, "session", "sessions")}"
        Badges.READER -> "Read $target ${en(target, "article", "articles")}"
        Badges.FRIENDS -> "Invite $target ${en(target, "friend", "friends")}"
        Badges.PARTNER -> "Connect someone close"
        Badges.DOCTOR -> "See a doctor or add a visit ${times(target)}"
        Badges.COMMUNITY -> "Write $target ${en(target, "post", "posts")} in the chat"
        Badges.SYMPTOMS -> "Log symptoms on $target ${en(target, "day", "days")}"
        Badges.MOOD -> "Log your mood on $target ${en(target, "day", "days")}"
        Badges.CALM_MINUTES -> "$target ${en(target, "minute", "minutes")} of breathing and meditation"
        Badges.SCANNER -> "Scan your food ${times(target)}"
        Badges.DEVICES -> "Connect a watch or your phone's health app"
        Badges.HYDRO -> "Log $target ${en(target, "litre", "litres")} of water in total"
        Badges.CURIOUS -> "Ask SADORA AI $target ${en(target, "question", "questions")}"
        Badges.SHARE -> "Show your profile to a doctor ${times(target)}"
        Badges.HELPER -> "Leave $target ${en(target, "comment", "comments")} in the chat"
        Badges.LOVED -> "Get $target ${en(target, "heart", "hearts")} on your posts"
        Badges.SHOPPER -> "Redeem in the Gul shop ${times(target)}"
        Badges.GARDENER -> "Collect $target Gul in total"
        else -> ""
    }
}
