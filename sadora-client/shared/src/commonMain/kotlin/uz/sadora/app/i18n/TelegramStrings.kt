package uz.sadora.app.i18n

/** "Telegram bot": what it does, linking, the detail switch, unlinking. */
interface TelegramStrings {
    val title: String
    val intro: String
    val features: List<String>
    val unavailable: String
    val connect: String
    val connectNote: String
    fun linked(username: String?): String
    val detailedTitle: String
    val detailedNote: String
    val disconnect: String
    val disconnectTitle: String
    val disconnectBody: String
    val disconnected: String
    val connectedToast: String
}

object TelegramStringsUz : TelegramStrings {
    override val title = "Telegram bot"
    override val intro = "Eslatmalaringiz Telegramga ham keladi, muhim narsalarni esa ilovani ochmasdan tugmalar bilan belgilaysiz."
    override val features = listOf(
        "🔔 Dori, Kegel, emlash va hayz yaqinlashishi eslatmalari",
        "🩸 «Hayz boshlandi» — bitta tugma",
        "😊 Kayfiyat va 💧 suv",
        "💊 Dorini «Ichdim» bilan belgilash",
    )
    override val unavailable = "Bot tez orada ishga tushadi."
    override val connect = "Telegramda ulash"
    override val connectNote = "Telegram ochiladi — «Start» tugmasini bosing. Havola 15 daqiqa amal qiladi."
    override fun linked(username: String?) = if (username != null) "Ulangan: @$username ✓" else "Ulangan ✓"
    override val detailedTitle = "Batafsil eslatmalar"
    override val detailedNote = "O'chiq bo'lsa, Telegramda faqat «Sizda yangi eslatma bor» yoziladi. Yoqsangiz, dori nomi va hayz kabi so'zlar ko'rinadi."
    override val disconnect = "Uzish"
    override val disconnectTitle = "Telegramni uzasizmi?"
    override val disconnectBody = "Eslatmalar faqat ilovaga keladi. Istalgan payt qayta ulash mumkin."
    override val disconnected = "Telegram uzildi"
    override val connectedToast = "Telegram ulandi"
}

object TelegramStringsRu : TelegramStrings {
    override val title = "Telegram-бот"
    override val intro = "Напоминания приходят и в Telegram, а главное можно отмечать кнопками, не открывая приложение."
    override val features = listOf(
        "🔔 Напоминания о лекарствах, Кегеле, прививках и скорых месячных",
        "🩸 «Начались месячные» — одна кнопка",
        "😊 Настроение и 💧 вода",
        "💊 Отметка «Приняла» для лекарств",
    )
    override val unavailable = "Бот скоро заработает."
    override val connect = "Подключить в Telegram"
    override val connectNote = "Откроется Telegram — нажмите «Start». Ссылка действует 15 минут."
    override fun linked(username: String?) = if (username != null) "Подключено: @$username ✓" else "Подключено ✓"
    override val detailedTitle = "Подробные напоминания"
    override val detailedNote = "Если выключено, в Telegram пишется только «У вас новое напоминание». Если включить, будут видны названия лекарств и слова вроде «месячные»."
    override val disconnect = "Отключить"
    override val disconnectTitle = "Отключить Telegram?"
    override val disconnectBody = "Напоминания будут приходить только в приложение. Подключить снова можно в любой момент."
    override val disconnected = "Telegram отключён"
    override val connectedToast = "Telegram подключён"
}

object TelegramStringsEn : TelegramStrings {
    override val title = "Telegram bot"
    override val intro = "Your reminders come to Telegram too, and you log the essentials with buttons without opening the app."
    override val features = listOf(
        "🔔 Medicine, Kegel, vaccine and period-coming reminders",
        "🩸 \"Period started\" in one tap",
        "😊 Mood and 💧 water",
        "💊 Mark a medicine \"Taken\"",
    )
    override val unavailable = "The bot is coming soon."
    override val connect = "Connect in Telegram"
    override val connectNote = "Telegram opens — tap \"Start\". The link works for 15 minutes."
    override fun linked(username: String?) = if (username != null) "Connected: @$username ✓" else "Connected ✓"
    override val detailedTitle = "Detailed reminders"
    override val detailedNote = "When off, Telegram only says \"You have a new reminder\". Turn it on to see medicine names and words like \"period\"."
    override val disconnect = "Disconnect"
    override val disconnectTitle = "Disconnect Telegram?"
    override val disconnectBody = "Reminders will come to the app only. You can connect again any time."
    override val disconnected = "Telegram disconnected"
    override val connectedToast = "Telegram connected"
}
