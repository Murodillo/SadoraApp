package uz.sadora.app.i18n

import uz.sadora.contract.CyclePhase
import uz.sadora.contract.WrappedKind
import uz.sadora.contract.WrappedPersona

/**
 * Cycle Wrapped: the story slides after a cycle and at the year's end.
 *
 * Warm and short, like a friend summing up; never a verdict on her body. The persona is
 * a game and says so.
 */
interface WrappedStrings {
    val listTitle: String
    val listIntro: String
    val empty: String
    fun itemTitle(kind: WrappedKind, year: Int): String
    fun freshTitle(kind: WrappedKind): String
    val freshBody: String
    val open: String
    val hideNumbers: String
    val share: String
    val brand: String

    // ---- a cycle
    val cycleIntro: String
    fun days(n: Int): String
    val cycleLasted: String
    fun periodLasted(n: Int): String
    fun vsUsual(diff: Int): String
    val forecastTitle: String
    fun forecast(diff: Int): String
    val forecastNote: String

    // ---- the year
    fun yearIntro(year: Int): String
    val daysWithApp: String
    val streakTitle: String
    fun longestRun(n: Int): String
    fun cyclesTitle(n: Int): String
    val averageCycle: String
    val averagePeriod: String
    val shortestLongest: String
    fun yearForecast(days: String): String

    // ---- shared
    val symptomsTitle: String
    val noSymptoms: String
    val moodTitle: String
    fun phase(phase: CyclePhase): String
    fun bestDay(date: String): String
    fun bestMonth(month: String): String
    val noMood: String
    val habitsTitle: String
    val bodyTitle: String
    val mindTitle: String
    val loggedDays: String
    val waterGoalDays: String
    val waterLitres: String
    val kegel: String
    val practiceMinutes: String
    val journal: String
    val meals: String
    val sleep: String
    fun hours(minutes: Int): String
    val gulTitle: String
    val gul: String
    val badges: String
    val personaTitle: String
    fun personaName(persona: WrappedPersona): String
    fun personaBody(persona: WrappedPersona): String
    val personaNote: String
    val outroTitle: String
    val outroBody: String
}

object WrappedStringsUz : WrappedStrings {
    override val listTitle = "Yakunlarim"
    override val listIntro = "Har sikl va har yil yakunida — siz haqingizda qisqa hikoya."
    override val empty = "Birinchi yakun siklingiz tugagach shu yerda paydo bo'ladi."
    override fun itemTitle(kind: WrappedKind, year: Int) = if (kind == WrappedKind.YEAR) "$year-yil yakuni" else "Sikl yakuni"
    override fun freshTitle(kind: WrappedKind) = if (kind == WrappedKind.YEAR) "Yilingiz yakuni tayyor 🎉" else "Siklingiz yakuni tayyor 🎉"
    override val freshBody = "Bir necha slayd: raqamlar, kayfiyat va odatlaringiz."
    override val open = "Ko'rish"
    override val hideNumbers = "Raqamlarni yashirish"
    override val share = "Ulashish"
    override val brand = "SADORA · sadora.app"
    override val cycleIntro = "Siklingiz yakunlandi"
    override fun days(n: Int) = "kun"
    override val cycleLasted = "davom etdi"
    override fun periodLasted(n: Int) = "Hayz: $n kun"
    override fun vsUsual(diff: Int) = when {
        diff == 0 -> "Xuddi odatdagidek"
        diff > 0 -> "Odatdagidan $diff kun uzunroq"
        else -> "Odatdagidan ${-diff} kun qisqaroq"
    }
    override val forecastTitle = "Prognoz qanchalik to'g'ri chiqdi?"
    override fun forecast(diff: Int) = when {
        diff == 0 -> "Hayz aynan kutilgan kuni keldi 🎯"
        diff > 0 -> "Kutilganidan $diff kun keyin keldi"
        else -> "Kutilganidan ${-diff} kun oldin keldi"
    }
    override val forecastNote = "Har bir sikl bilan prognoz aniqroq bo'ladi."
    override fun yearIntro(year: Int) = "$year — sizning yilingiz"
    override val daysWithApp = "kun SADORA bilan"
    override val streakTitle = "Birga o'tgan kunlar"
    override fun longestRun(n: Int) = "Eng uzun ketma-ketlik: $n kun"
    override fun cyclesTitle(n: Int) = "Yil davomida $n ta sikl"
    override val averageCycle = "O'rtacha sikl"
    override val averagePeriod = "O'rtacha hayz"
    override val shortestLongest = "Eng qisqa — eng uzun"
    override fun yearForecast(days: String) = "Prognoz o'rtacha $days kun farq bilan to'g'ri chiqdi"
    override val symptomsTitle = "Eng ko'p sezganingiz"
    override val noSymptoms = "Bu safar simptom yozilmadi"
    override val moodTitle = "Kayfiyatingiz sikl bo'ylab"
    override fun phase(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> "Hayz"
        CyclePhase.FOLLICULAR -> "Follikulyar"
        CyclePhase.FERTILE -> "Unumdor"
        CyclePhase.LUTEAL -> "Lyutein"
    }
    override fun bestDay(date: String) = "Eng yaxshi kuningiz: $date"
    override fun bestMonth(month: String) = "Eng quvnoq oyingiz: $month"
    override val noMood = "Kayfiyat belgilanmadi — keyingi safar belgilab ko'ring"
    override val habitsTitle = "Odatlaringiz"
    override val bodyTitle = "Tanangiz uchun"
    override val mindTitle = "Ruhingiz uchun"
    override val loggedDays = "kun belgilandi"
    override val waterGoalDays = "kun suv maqsadi bajarildi"
    override val waterLitres = "litr suv"
    override val kegel = "Kegel mashqi"
    override val practiceMinutes = "daqiqa nafas va meditatsiya"
    override val journal = "kundalik yozuvi"
    override val meals = "ovqat yozildi"
    override val sleep = "o'rtacha uyqu"
    override fun hours(minutes: Int) = "${minutes / 60} soat ${minutes % 60} daq"
    override val gulTitle = "Gul va nishonlar"
    override val gul = "Gul yig'ildi"
    override val badges = "yangi nishon"
    override val personaTitle = "Bu safar siz —"
    override fun personaName(persona: WrappedPersona) = when (persona) {
        WrappedPersona.ATIRGUL -> "Atirgul"
        WrappedPersona.LOLA -> "Lola"
        WrappedPersona.NILUFAR -> "Nilufar"
        WrappedPersona.BINAFSHA -> "Binafsha"
        WrappedPersona.MOYCHECHAK -> "Moychechak"
        WrappedPersona.YASMIN -> "Yasmin"
    }
    override fun personaBody(persona: WrappedPersona) = when (persona) {
        WrappedPersona.ATIRGUL -> "Deyarli har kuni o'zingizni belgiladingiz. Sadoqatli va e'tiborli."
        WrappedPersona.LOLA -> "Mashq, suv va harakat — energiyangiz yuqori!"
        WrappedPersona.NILUFAR -> "Sikl barqaror, kayfiyat yaxshi. Xotirjam va muvozanatli."
        WrappedPersona.BINAFSHA -> "Fikr va hislaringizni yozib bordingiz. Chuqur o'ylovchi."
        WrappedPersona.MOYCHECHAK -> "Dam olish va nafasni qadrlaysiz. Yumshoq va osoyishta."
        WrappedPersona.YASMIN -> "Hali kashf qilyapsiz — har yangi kun yangi imkoniyat."
    }
    override val personaNote = "Bu o'yin, tibbiy xulosa emas."
    override val outroTitle = "O'zingizga e'tibor berganingiz uchun rahmat 💜"
    override val outroBody = "Keyingi yakungacha — SADORA siz bilan."
}

object WrappedStringsRu : WrappedStrings {
    override val listTitle = "Мои итоги"
    override val listIntro = "В конце каждого цикла и года — короткая история о вас."
    override val empty = "Первые итоги появятся здесь, когда закончится цикл."
    override fun itemTitle(kind: WrappedKind, year: Int) = if (kind == WrappedKind.YEAR) "Итоги $year года" else "Итоги цикла"
    override fun freshTitle(kind: WrappedKind) = if (kind == WrappedKind.YEAR) "Итоги года готовы 🎉" else "Итоги цикла готовы 🎉"
    override val freshBody = "Несколько слайдов: цифры, настроение и привычки."
    override val open = "Смотреть"
    override val hideNumbers = "Скрыть цифры"
    override val share = "Поделиться"
    override val brand = "SADORA · sadora.app"
    override val cycleIntro = "Ваш цикл завершён"
    override fun days(n: Int) = ru(n, "день", "дня", "дней")
    override val cycleLasted = "длился цикл"
    override fun periodLasted(n: Int) = "Менструация: $n ${ru(n, "день", "дня", "дней")}"
    override fun vsUsual(diff: Int) = when {
        diff == 0 -> "Как обычно"
        diff > 0 -> "На $diff ${ru(diff, "день", "дня", "дней")} длиннее обычного"
        else -> "На ${-diff} ${ru(-diff, "день", "дня", "дней")} короче обычного"
    }
    override val forecastTitle = "Насколько точным был прогноз?"
    override fun forecast(diff: Int) = when {
        diff == 0 -> "Месячные пришли точно в срок 🎯"
        diff > 0 -> "Пришли на $diff ${ru(diff, "день", "дня", "дней")} позже прогноза"
        else -> "Пришли на ${-diff} ${ru(-diff, "день", "дня", "дней")} раньше прогноза"
    }
    override val forecastNote = "С каждым циклом прогноз становится точнее."
    override fun yearIntro(year: Int) = "$year — ваш год"
    override val daysWithApp = "дней с SADORA"
    override val streakTitle = "Дни вместе"
    override fun longestRun(n: Int) = "Самая длинная серия: $n ${ru(n, "день", "дня", "дней")}"
    override fun cyclesTitle(n: Int) = "За год — $n ${ru(n, "цикл", "цикла", "циклов")}"
    override val averageCycle = "Средний цикл"
    override val averagePeriod = "Средняя менструация"
    override val shortestLongest = "Самый короткий — самый длинный"
    override fun yearForecast(days: String) = "Прогноз в среднем ошибался на $days дн"
    override val symptomsTitle = "Чаще всего"
    override val noSymptoms = "В этот раз симптомы не отмечены"
    override val moodTitle = "Настроение по фазам"
    override fun phase(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> "Менструация"
        CyclePhase.FOLLICULAR -> "Фолликулярная"
        CyclePhase.FERTILE -> "Фертильная"
        CyclePhase.LUTEAL -> "Лютеиновая"
    }
    override fun bestDay(date: String) = "Лучший день: $date"
    override fun bestMonth(month: String) = "Самый радостный месяц: $month"
    override val noMood = "Настроение не отмечено — попробуйте в следующий раз"
    override val habitsTitle = "Ваши привычки"
    override val bodyTitle = "Для тела"
    override val mindTitle = "Для души"
    override val loggedDays = "дней отмечено"
    override val waterGoalDays = "дней с целью по воде"
    override val waterLitres = "литров воды"
    override val kegel = "упражнений Кегеля"
    override val practiceMinutes = "минут дыхания и медитации"
    override val journal = "записей в дневнике"
    override val meals = "приёмов пищи записано"
    override val sleep = "средний сон"
    override fun hours(minutes: Int) = "${minutes / 60} ч ${minutes % 60} мин"
    override val gulTitle = "Gul и значки"
    override val gul = "Gul собрано"
    override val badges = "новых значков"
    override val personaTitle = "В этот раз вы —"
    override fun personaName(persona: WrappedPersona) = when (persona) {
        WrappedPersona.ATIRGUL -> "Роза"
        WrappedPersona.LOLA -> "Тюльпан"
        WrappedPersona.NILUFAR -> "Лотос"
        WrappedPersona.BINAFSHA -> "Фиалка"
        WrappedPersona.MOYCHECHAK -> "Ромашка"
        WrappedPersona.YASMIN -> "Жасмин"
    }
    override fun personaBody(persona: WrappedPersona) = when (persona) {
        WrappedPersona.ATIRGUL -> "Вы отмечали себя почти каждый день. Преданная и внимательная."
        WrappedPersona.LOLA -> "Упражнения, вода и движение — у вас много энергии!"
        WrappedPersona.NILUFAR -> "Ровный цикл и хорошее настроение. Спокойная и гармоничная."
        WrappedPersona.BINAFSHA -> "Вы записывали мысли и чувства. Глубокая натура."
        WrappedPersona.MOYCHECHAK -> "Вы цените отдых и дыхание. Мягкая и безмятежная."
        WrappedPersona.YASMIN -> "Вы только начинаете — каждый день новая возможность."
    }
    override val personaNote = "Это игра, а не медицинский вывод."
    override val outroTitle = "Спасибо, что заботитесь о себе 💜"
    override val outroBody = "До следующих итогов — SADORA рядом."
}

object WrappedStringsEn : WrappedStrings {
    override val listTitle = "My recaps"
    override val listIntro = "At the end of every cycle and every year — a short story about you."
    override val empty = "Your first recap appears here when a cycle ends."
    override fun itemTitle(kind: WrappedKind, year: Int) = if (kind == WrappedKind.YEAR) "$year recap" else "Cycle recap"
    override fun freshTitle(kind: WrappedKind) = if (kind == WrappedKind.YEAR) "Your year recap is ready 🎉" else "Your cycle recap is ready 🎉"
    override val freshBody = "A few slides: your numbers, mood and habits."
    override val open = "View"
    override val hideNumbers = "Hide numbers"
    override val share = "Share"
    override val brand = "SADORA · sadora.app"
    override val cycleIntro = "Your cycle has ended"
    override fun days(n: Int) = en(n, "day", "days")
    override val cycleLasted = "it lasted"
    override fun periodLasted(n: Int) = "Period: $n ${en(n, "day", "days")}"
    override fun vsUsual(diff: Int) = when {
        diff == 0 -> "Just like usual"
        diff > 0 -> "$diff ${en(diff, "day", "days")} longer than usual"
        else -> "${-diff} ${en(-diff, "day", "days")} shorter than usual"
    }
    override val forecastTitle = "How close was the forecast?"
    override fun forecast(diff: Int) = when {
        diff == 0 -> "Your period came on the very day 🎯"
        diff > 0 -> "It came ${diff} ${en(diff, "day", "days")} after the forecast"
        else -> "It came ${-diff} ${en(-diff, "day", "days")} before the forecast"
    }
    override val forecastNote = "Every cycle makes the forecast sharper."
    override fun yearIntro(year: Int) = "$year — your year"
    override val daysWithApp = "days with SADORA"
    override val streakTitle = "Days together"
    override fun longestRun(n: Int) = "Longest streak: $n ${en(n, "day", "days")}"
    override fun cyclesTitle(n: Int) = "$n ${en(n, "cycle", "cycles")} this year"
    override val averageCycle = "Average cycle"
    override val averagePeriod = "Average period"
    override val shortestLongest = "Shortest — longest"
    override fun yearForecast(days: String) = "The forecast was off by $days days on average"
    override val symptomsTitle = "What you felt most"
    override val noSymptoms = "No symptoms logged this time"
    override val moodTitle = "Your mood across the cycle"
    override fun phase(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> "Period"
        CyclePhase.FOLLICULAR -> "Follicular"
        CyclePhase.FERTILE -> "Fertile"
        CyclePhase.LUTEAL -> "Luteal"
    }
    override fun bestDay(date: String) = "Your best day: $date"
    override fun bestMonth(month: String) = "Your happiest month: $month"
    override val noMood = "No mood logged — give it a try next time"
    override val habitsTitle = "Your habits"
    override val bodyTitle = "For your body"
    override val mindTitle = "For your mind"
    override val loggedDays = "days logged"
    override val waterGoalDays = "days at your water goal"
    override val waterLitres = "litres of water"
    override val kegel = "Kegel sessions"
    override val practiceMinutes = "minutes of breathing and meditation"
    override val journal = "journal entries"
    override val meals = "meals logged"
    override val sleep = "average sleep"
    override fun hours(minutes: Int) = "${minutes / 60} h ${minutes % 60} min"
    override val gulTitle = "Gul and badges"
    override val gul = "Gul collected"
    override val badges = "new badges"
    override val personaTitle = "This time you were —"
    override fun personaName(persona: WrappedPersona) = when (persona) {
        WrappedPersona.ATIRGUL -> "Rose"
        WrappedPersona.LOLA -> "Tulip"
        WrappedPersona.NILUFAR -> "Lotus"
        WrappedPersona.BINAFSHA -> "Violet"
        WrappedPersona.MOYCHECHAK -> "Chamomile"
        WrappedPersona.YASMIN -> "Jasmine"
    }
    override fun personaBody(persona: WrappedPersona) = when (persona) {
        WrappedPersona.ATIRGUL -> "You checked in nearly every day. Devoted and attentive."
        WrappedPersona.LOLA -> "Exercise, water and movement — your energy is high!"
        WrappedPersona.NILUFAR -> "A steady cycle and a good mood. Calm and balanced."
        WrappedPersona.BINAFSHA -> "You wrote down your thoughts and feelings. Deep and thoughtful."
        WrappedPersona.MOYCHECHAK -> "You value rest and slow breaths. Gentle and serene."
        WrappedPersona.YASMIN -> "Still exploring — every new day is a new chance."
    }
    override val personaNote = "Just for fun — not a medical reading."
    override val outroTitle = "Thank you for taking care of yourself 💜"
    override val outroBody = "Until the next recap — SADORA is with you."
}
