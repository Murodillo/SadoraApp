package uz.sadora.server.telegram

import uz.sadora.contract.Language

/** What the bot says, in her app language. Reminders are neutral unless she chose "batafsil". */
class TelegramWords(private val language: Language) {
    private fun pick(uz: String, ru: String, en: String) = when (language) {
        Language.UZ -> uz
        Language.RU -> ru
        Language.EN -> en
    }

    val welcome get() = pick(
        "SADORA ulandi ✓\nEslatmalar endi shu yerga ham keladi. Pastdagi tugmalar bilan ilovani ochmasdan belgilashingiz mumkin.",
        "SADORA подключена ✓\nНапоминания теперь приходят и сюда. Кнопками ниже можно отмечать, не открывая приложение.",
        "SADORA is connected ✓\nYour reminders now come here too. Use the buttons below to log without opening the app.",
    )
    val notLinked get() = pick(
        "Bu bot SADORA ilovasi bilan ishlaydi. Ilovada Profil → Telegram bot → «Ulash» tugmasini bosing.",
        "Этот бот работает с приложением SADORA. В приложении откройте Профиль → Telegram-бот → «Подключить».",
        "This bot works with the SADORA app. In the app, open Profile → Telegram bot → \"Connect\".",
    )
    val codeExpired get() = pick(
        "Havola eskirgan yoki ishlatilgan. Ilovada «Ulash» tugmasini qaytadan bosing.",
        "Ссылка устарела или уже использована. Нажмите «Подключить» в приложении ещё раз.",
        "This link has expired or was already used. Tap \"Connect\" in the app again.",
    )
    val menu get() = pick("Nimani belgilaymiz?", "Что отметим?", "What shall we log?")
    val period get() = pick("🩸 Hayz boshlandi", "🩸 Начались месячные", "🩸 Period started")
    val water get() = pick("💧 +250 ml", "💧 +250 мл", "💧 +250 ml")
    val meds get() = pick("💊 Bugungi dorilar", "💊 Лекарства на сегодня", "💊 Today's medicines")
    val periodSaved get() = pick("Hayz bugundan boshlab belgilandi ✓", "Месячные отмечены с сегодняшнего дня ✓", "Period logged from today ✓")
    fun waterSaved(total: Int, goal: Int) = pick("Suv: $total / $goal ml 💧", "Вода: $total / $goal мл 💧", "Water: $total / $goal ml 💧")
    val moodSaved get() = pick("Kayfiyat belgilandi ✓", "Настроение отмечено ✓", "Mood logged ✓")
    val noDoses get() = pick("Bugun belgilanmagan dori yo'q ✓", "На сегодня отмечать нечего ✓", "Nothing left to log today ✓")
    val dosesTitle get() = pick("Bugungi dorilar — ichganingizni bosing:", "Лекарства на сегодня — нажмите, что приняли:", "Today's medicines — tap the ones you took:")
    fun doseDone(name: String, time: String, taken: Boolean) =
        if (taken) pick("✅ $name — $time: ichildi", "✅ $name — $time: принято", "✅ $name — $time: taken")
        else pick("⏭ $name — $time: o'tkazildi", "⏭ $name — $time: пропущено", "⏭ $name — $time: skipped")
    fun neutralDone(taken: Boolean) =
        if (taken) pick("✅ Belgilandi", "✅ Отмечено", "✅ Logged") else pick("⏭ O'tkazildi", "⏭ Пропущено", "⏭ Skipped")
    val failed get() = pick(
        "Belgilab bo'lmadi — ilovada tekshirib ko'ring.",
        "Не получилось отметить — проверьте в приложении.",
        "That could not be logged — please check in the app.",
    )
    val unlinked get() = pick(
        "Bot uzildi. Qayta ulash uchun ilovadan foydalaning.",
        "Бот отключён. Подключить снова можно в приложении.",
        "The bot is disconnected. You can connect again from the app.",
    )

    // ---- reminders
    val reminder get() = pick("🔔 Sizda yangi eslatma bor", "🔔 У вас новое напоминание", "🔔 You have a new reminder")
    fun dose(name: String, time: String) = pick("💊 Dori vaqti: $name — $time", "💊 Время лекарства: $name — $time", "💊 Medicine time: $name — $time")
    val kegel get() = pick("🌸 Kegel mashqi vaqti", "🌸 Время упражнений Кегеля", "🌸 Time for your Kegel exercises")
    fun periodSoon(days: Int) = pick(
        "🩸 Hayzingiz taxminan $days kundan keyin boshlanadi",
        "🩸 Месячные ожидаются примерно через $days дн.",
        "🩸 Your period is expected in about $days days",
    )
    val taken get() = pick("✅ Ichdim", "✅ Приняла", "✅ Taken")
    val skipped get() = pick("⏭ O'tkazdim", "⏭ Пропустила", "⏭ Skipped")
    val done get() = pick("✅ Bajardim", "✅ Готово", "✅ Done")
}
