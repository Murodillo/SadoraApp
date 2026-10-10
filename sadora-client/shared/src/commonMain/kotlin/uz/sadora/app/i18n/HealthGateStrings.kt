package uz.sadora.app.i18n

/**
 * The page before the app: the phone's health store, asked for — never required — and her
 * history brought up once. The three languages sit beside the interface, as the doctor strings
 * do, so a new line is added to all of them at once.
 */
interface HealthGateStrings {
    /** The store's own name on this phone: "Health Connect" or "Apple Health". */
    fun title(store: String): String
    fun body(store: String): String
    /** The kinds of data read, drawn as a list. Same length in every language. */
    val kinds: List<String>
    val privacy: String
    val allow: String
    fun partial(granted: Int, total: Int): String
    /** Why more types help, and that she can go on now and allow more later. */
    val partialBody: String
    /** Going on with the types she allowed. */
    val continueWith: String
    /** Going on with none — the ask, or a phone without the store. */
    val skip: String
    val openSettings: String
    fun install(store: String): String
    fun installBody(store: String): String
    val installButton: String
    val importing: String
    val importingBody: String
    fun importPercent(percent: Int): String
    val importFailed: String
    val retry: String
    val later: String
}

object HealthGateStringsUz : HealthGateStrings {
    override fun title(store: String) = "$store ma'lumotlariga ruxsat bering"
    override fun body(store: String) =
        "SADORA telefoningizdagi $store'dan ilovada ishlatiladigan sog'liq ma'lumotlarini — butun tarixi bilan — o'qiydi " +
            "va himoyalangan hisobingizga saqlaydi. Shu bilan sikl bashorati, tahlillar va shifokorga ko'rsatiladigan karta to'liq bo'ladi."
    override val kinds = listOf(
        "Hayz sikli va bazal harorat",
        "Qadam, masofa va faol kaloriya",
        "Puls, tinch holatdagi puls, HRV, nafas va SpO₂",
        "Tana va teri harorati",
        "Uyqu va uning bosqichlari",
        "Vazn",
    )
    override val privacy = "Ma'lumotlar faqat sizning hisobingizda saqlanadi va hech kimga sotilmaydi. " +
        "Ruxsatni istalgan vaqtda telefon sozlamalaridan olib qo'yishingiz mumkin."
    override val allow = "Ruxsat berish"
    override fun partial(granted: Int, total: Int) = "$total ta ruxsatdan $granted tasi berildi"
    override val partialBody = "Qancha ko'p turga ruxsat bersangiz, bashorat va tahlillar shuncha aniq bo'ladi. " +
        "Ruxsatni keyinroq sozlamalardan ham berishingiz mumkin."
    override val continueWith = "Shu bilan davom etish"
    override val skip = "Hozircha o'tkazib yuborish"
    override val openSettings = "Sozlamalarni ochish"
    override fun install(store: String) = "$store o'rnatilmagan"
    override fun installBody(store: String) = "Sog'liq ma'lumotlarini o'qish uchun $store kerak. Google Play'dan o'rnating yoki yangilang."
    override val installButton = "O'rnatish"
    override val importing = "Tarixingiz yuklanmoqda"
    override val importingBody = "Telefoningizdagi yozuvlar himoyalangan hisobingizga saqlanmoqda. Birinchi marta bir necha daqiqa olishi mumkin."
    override fun importPercent(percent: Int) = "$percent%"
    override val importFailed = "Yuklash to'xtadi — internetni tekshirib, qayta urinib ko'ring."
    override val retry = "Qayta urinish"
    override val later = "Keyinroq davom ettirish"
}

object HealthGateStringsRu : HealthGateStrings {
    override fun title(store: String) = "Разрешите доступ к $store"
    override fun body(store: String) =
        "SADORA читает из $store на телефоне данные о здоровье, которые использует приложение, — со всей историей — " +
            "и сохраняет их в вашем защищённом аккаунте. Так прогноз цикла, аналитика и карта для врача будут полными."
    override val kinds = listOf(
        "Менструальный цикл и базальная температура",
        "Шаги, дистанция и активные калории",
        "Пульс, пульс в покое, ВСР, дыхание и SpO₂",
        "Температура тела и кожи",
        "Сон и его фазы",
        "Вес",
    )
    override val privacy = "Данные хранятся только в вашем аккаунте и никому не продаются. " +
        "Доступ можно отозвать в настройках телефона в любой момент."
    override val allow = "Разрешить"
    override fun partial(granted: Int, total: Int) = "Выдано $granted из $total ${ru(total, "разрешения", "разрешений", "разрешений")}"
    override val partialBody = "Чем больше типов данных вы разрешите, тем точнее будут прогноз и аналитика. " +
        "Доступ можно выдать и позже в настройках."
    override val continueWith = "Продолжить с этим"
    override val skip = "Пропустить пока"
    override val openSettings = "Открыть настройки"
    override fun install(store: String) = "$store не установлен"
    override fun installBody(store: String) = "Чтобы читать данные о здоровье, нужен $store. Установите или обновите его в Google Play."
    override val installButton = "Установить"
    override val importing = "Загружаем вашу историю"
    override val importingBody = "Сохраняем записи с телефона в вашем защищённом аккаунте. В первый раз это может занять несколько минут."
    override fun importPercent(percent: Int) = "$percent%"
    override val importFailed = "Загрузка прервалась — проверьте интернет и попробуйте снова."
    override val retry = "Повторить"
    override val later = "Продолжить позже"
}

object HealthGateStringsEn : HealthGateStrings {
    override fun title(store: String) = "Allow access to $store"
    override fun body(store: String) =
        "SADORA reads the health data the app uses from $store on your phone — with its whole history — " +
            "and keeps it in your protected account. That is what makes the cycle forecast, the insights and the record for your doctor complete."
    override val kinds = listOf(
        "Menstrual cycle and basal temperature",
        "Steps, distance and active calories",
        "Heart rate, resting heart rate, HRV, breathing and SpO₂",
        "Body and skin temperature",
        "Sleep and its stages",
        "Weight",
    )
    override val privacy = "Your data stays in your account and is never sold. " +
        "You can take the access back in your phone's settings at any time."
    override val allow = "Allow"
    override fun partial(granted: Int, total: Int) = "$granted of $total permissions granted"
    override val partialBody = "The more types you allow, the more accurate your forecast and insights will be. " +
        "You can also allow more later in the settings."
    override val continueWith = "Continue with these"
    override val skip = "Skip for now"
    override val openSettings = "Open settings"
    override fun install(store: String) = "$store isn't installed"
    override fun installBody(store: String) = "Reading health data needs $store. Install or update it from Google Play."
    override val installButton = "Install"
    override val importing = "Bringing your history over"
    override val importingBody = "Your phone's records are being saved to your protected account. The first time can take a few minutes."
    override fun importPercent(percent: Int) = "$percent%"
    override val importFailed = "The upload stopped — check your connection and try again."
    override val retry = "Try again"
    override val later = "Continue later"
}
