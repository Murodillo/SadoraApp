package uz.sadora.app.i18n

import uz.sadora.contract.KegelProgram
import uz.sadora.contract.LeakSituation

/**
 * "Tos tubi mashqlari": the program page, the guided session and the four-weekly check.
 *
 * Every warning ends at a doctor: pain, a high-risk pregnancy, a fresh birth. The check
 * records how often and how much; it never names a kind of incontinence.
 */
interface KegelStrings {
    val title: String
    val shortTitle: String
    val intro: String
    fun program(program: KegelProgram): String
    fun programNote(program: KegelProgram): String
    val programLabel: String
    val yourStage: String
    fun level(level: Int): String
    fun levelDetail(hold: Int, relax: Int, reps: Int, sets: Int, minutes: Int): String
    fun today(done: Int, goal: Int): String
    fun streak(days: Int): String
    fun nextLevel(days: Int, need: Int): String
    val allLevels: String
    val start: String
    val premium: String
    val lastWeeks: String

    val remindersTitle: String
    val remindersNote: String
    val addReminder: String
    val reminderTime: String
    val add: String
    val noReminders: String
    val saved: String

    val postpartumEarlyTitle: String
    val postpartumEarlyBody: String
    val howTitle: String
    val howSteps: List<String>
    val safety: String

    // ---- the check
    val checkTitle: String
    val checkNote: String
    val checkStart: String
    val later: String
    val qFrequency: String
    fun frequency(value: Int): String
    val qAmount: String
    fun amount(value: Int): String
    val qWhen: String
    fun situation(situation: LeakSituation): String
    val qBother: String
    val botherScale: String
    val save: String
    val checksHistory: String
    fun bother(value: Int): String
    val checkSaved: String

    // ---- the session
    val getReady: String
    val squeeze: String
    val relax: String
    val flick: String
    val rest: String
    fun set(index: Int, of: Int): String
    val pause: String
    val resume: String
    val stop: String
    val stopTitle: String
    val stopBody: String
    val doneTitle: String
    fun doneBody(reps: Int, minutes: Int): String
    fun coins(amount: Int): String
    fun levelOpened(level: Int): String
    val close: String
    val eyesClosed: String
}

object KegelStringsUz : KegelStrings {
    override val title = "Tos tubi mashqlari"
    override val shortTitle = "Kegel"
    override val intro = "Kegel mashqlari tos tubi mushaklarini kuchaytiradi: siydik tutish yaxshilanadi, tug'ruqqa tayyorlanish va undan keyin tiklanish osonlashadi."
    override fun program(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Asosiy dastur"
        KegelProgram.PREGNANCY -> "Homiladorlik dasturi"
        KegelProgram.POSTPARTUM -> "Tug'ruqdan keyingi dastur"
        KegelProgram.MENOPAUSE -> "Menopauza dasturi"
    }
    override fun programNote(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Kuniga ikki marta, bir necha daqiqadan. Natija odatda 6–8 haftada seziladi."
        KegelProgram.PREGNANCY -> "Yengil qisish va to'liq bo'shatish — tug'ruqqa tayyorlaydi. Homiladorlik xavfli kechayotgan bo'lsa yoki shifokor taqiqlagan bo'lsa, avval maslahatlashing."
        KegelProgram.POSTPARTUM -> "Juda yengil boshlanadi va asta kuchayadi. Choklar, og'riq yoki qon ketishi bo'lsa, shifokor ruxsatini kuting."
        KegelProgram.MENOPAUSE -> "Estrogen kamayganda tos tubi zaiflashadi. Uzoqroq ushlash siydik tutishni qo'llab-quvvatlaydi."
    }
    override val programLabel = "Dastur"
    override val yourStage = "bosqichingiz"
    override fun level(level: Int) = "$level-daraja"
    override fun levelDetail(hold: Int, relax: Int, reps: Int, sets: Int, minutes: Int) =
        "Qisish $hold s · bo'shatish $relax s · $reps marta × $sets · ~$minutes daq"
    override fun today(done: Int, goal: Int) = "Bugun: $done / $goal"
    override fun streak(days: Int) = "$days kun ketma-ket"
    override fun nextLevel(days: Int, need: Int) = "Keyingi darajagacha: $days / $need kun"
    override val allLevels = "Barcha darajalar ochildi"
    override val start = "Mashqni boshlash"
    override val premium = "Premium"
    override val lastWeeks = "So'nggi 4 hafta"
    override val remindersTitle = "Eslatmalar"
    override val remindersNote = "Kuniga 3 tagacha. Bildirishnomada mashq nomi yozilmaydi."
    override val addReminder = "Vaqt qo'shish"
    override val reminderTime = "Vaqt (SS:DD)"
    override val add = "Qo'shish"
    override val noReminders = "Eslatma qo'yilmagan"
    override val saved = "Saqlandi"
    override val postpartumEarlyTitle = "Tug'ruqdan hali 6 hafta o'tmagan"
    override val postpartumEarlyBody = "Ko'pchilik shifokorlar 6 haftalik ko'rikdan keyin boshlashni maslahat beradi. Shifokoringiz ruxsat bergan bo'lsa, eng yengil darajadan boshlang."
    override val howTitle = "Qanday bajariladi?"
    override val howSteps = listOf(
        "Mushakni toping: siydikni yarim yo'lda to'xtatmoqchi bo'lgandek qising. Bu faqat topish uchun — mashqni hojatxonada qilmang.",
        "Qorin, son va dumbani bo'sh qo'ying: faqat ichkaridagi mushak ishlaydi.",
        "Nafasni ushlamang, bemalol nafas oling.",
        "Bo'shatish qisish kabi muhim: har safar to'liq bo'shating.",
        "Og'riq sezsangiz to'xtating va shifokorga ayting.",
    )
    override val safety = "Mashq davolash o'rnini bosmaydi. Siydik tutolmaslik kuchaysa yoki og'riq bo'lsa, shifokorga murojaat qiling."
    override val checkTitle = "4 haftalik so'rov"
    override val checkNote = "Ikki daqiqa: o'zgarishni o'zingiz ko'rasiz, shifokor ham hisobotda ko'radi."
    override val checkStart = "Boshlash"
    override val later = "Keyinroq"
    override val qFrequency = "So'nggi 4 haftada siydik qanchalik tez-tez ixtiyorsiz chiqdi?"
    override fun frequency(value: Int) = when (value) {
        0 -> "Hech qachon"
        1 -> "Haftada bir marta yoki kamroq"
        2 -> "Haftada 2–3 marta"
        3 -> "Kuniga taxminan bir marta"
        else -> "Kuniga bir necha marta"
    }
    override val qAmount = "Odatda qancha?"
    override fun amount(value: Int) = when (value) {
        1 -> "Bir necha tomchi"
        2 -> "Oz miqdorda"
        else -> "Ko'proq"
    }
    override val qWhen = "Qachon bo'ladi? Bir nechtasini tanlash mumkin."
    override fun situation(situation: LeakSituation) = when (situation) {
        LeakSituation.COUGH -> "Yo'talganda, aksirganda, kulganda"
        LeakSituation.EXERCISE -> "Jismoniy harakatda, og'ir ko'targanda"
        LeakSituation.URGE -> "Hojatxonaga yetib bormasdan"
        LeakSituation.NIGHT -> "Tunda"
        LeakSituation.OTHER -> "Sababsiz"
    }
    override val qBother = "Bu kundalik hayotingizga qanchalik xalaqit beradi?"
    override val botherScale = "0 — umuman yo'q, 10 — juda ko'p"
    override val save = "Saqlash"
    override val checksHistory = "So'rovlar"
    override fun bother(value: Int) = "xalaqit $value / 10"
    override val checkSaved = "So'rov saqlandi"
    override val getReady = "Tayyorlaning"
    override val squeeze = "Qising"
    override val relax = "Bo'shating"
    override val flick = "Tez qising"
    override val rest = "Dam oling"
    override fun set(index: Int, of: Int) = "To'plam $index / $of"
    override val pause = "Pauza"
    override val resume = "Davom etish"
    override val stop = "Tugatish"
    override val stopTitle = "Mashqni tugatasizmi?"
    override val stopBody = "Bajarilgan qismi saqlanadi."
    override val doneTitle = "Barakalla!"
    override fun doneBody(reps: Int, minutes: Int) = "$reps ta qisish · $minutes daqiqa"
    override fun coins(amount: Int) = "+$amount Gul"
    override fun levelOpened(level: Int) = "$level-daraja ochildi!"
    override val close = "Yopish"
    override val eyesClosed = "Ko'zingizni yumib ham bajarishingiz mumkin: uzun titrash — qising, ikki qisqa — bo'shating."
}

object KegelStringsRu : KegelStrings {
    override val title = "Упражнения для тазового дна"
    override val shortTitle = "Кегель"
    override val intro = "Упражнения Кегеля укрепляют мышцы тазового дна: лучше удерживается моча, легче подготовиться к родам и восстановиться после них."
    override fun program(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Базовая программа"
        KegelProgram.PREGNANCY -> "Программа для беременных"
        KegelProgram.POSTPARTUM -> "Программа после родов"
        KegelProgram.MENOPAUSE -> "Программа для менопаузы"
    }
    override fun programNote(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Дважды в день по несколько минут. Результат обычно заметен через 6–8 недель."
        KegelProgram.PREGNANCY -> "Мягкое сжатие и полное расслабление готовят к родам. При беременности с осложнениями или запрете врача сначала посоветуйтесь."
        KegelProgram.POSTPARTUM -> "Начинается очень мягко и постепенно усиливается. Если есть швы, боль или кровотечение, дождитесь разрешения врача."
        KegelProgram.MENOPAUSE -> "Когда эстрогена становится меньше, тазовое дно слабеет. Более долгие удержания помогают удерживать мочу."
    }
    override val programLabel = "Программа"
    override val yourStage = "ваш этап"
    override fun level(level: Int) = "Уровень $level"
    override fun levelDetail(hold: Int, relax: Int, reps: Int, sets: Int, minutes: Int) =
        "Сжатие $hold с · расслабление $relax с · $reps раз × $sets · ~$minutes мин"
    override fun today(done: Int, goal: Int) = "Сегодня: $done / $goal"
    override fun streak(days: Int) = "$days ${ru(days, "день", "дня", "дней")} подряд"
    override fun nextLevel(days: Int, need: Int) = "До следующего уровня: $days из $need ${ru(need, "дня", "дней", "дней")}"
    override val allLevels = "Все уровни открыты"
    override val start = "Начать упражнение"
    override val premium = "Premium"
    override val lastWeeks = "Последние 4 недели"
    override val remindersTitle = "Напоминания"
    override val remindersNote = "До 3 в день. В уведомлении не будет названия упражнения."
    override val addReminder = "Добавить время"
    override val reminderTime = "Время (ЧЧ:ММ)"
    override val add = "Добавить"
    override val noReminders = "Напоминаний нет"
    override val saved = "Сохранено"
    override val postpartumEarlyTitle = "После родов ещё не прошло 6 недель"
    override val postpartumEarlyBody = "Большинство врачей советуют начинать после осмотра через 6 недель. Если врач разрешил, начните с самого лёгкого уровня."
    override val howTitle = "Как выполнять?"
    override val howSteps = listOf(
        "Найдите мышцу: сожмите так, будто хотите остановить мочеиспускание. Это только чтобы найти её — не делайте упражнение в туалете.",
        "Живот, бёдра и ягодицы расслаблены: работает только внутренняя мышца.",
        "Не задерживайте дыхание, дышите спокойно.",
        "Расслабление так же важно, как сжатие: каждый раз расслабляйтесь полностью.",
        "Если почувствуете боль, остановитесь и скажите врачу.",
    )
    override val safety = "Упражнения не заменяют лечение. Если подтекание усиливается или есть боль, обратитесь к врачу."
    override val checkTitle = "Опрос раз в 4 недели"
    override val checkNote = "Две минуты: вы увидите изменения, а врач увидит их в отчёте."
    override val checkStart = "Начать"
    override val later = "Позже"
    override val qFrequency = "Как часто за последние 4 недели подтекала моча?"
    override fun frequency(value: Int) = when (value) {
        0 -> "Никогда"
        1 -> "Раз в неделю или реже"
        2 -> "2–3 раза в неделю"
        3 -> "Примерно раз в день"
        else -> "Несколько раз в день"
    }
    override val qAmount = "Обычно сколько?"
    override fun amount(value: Int) = when (value) {
        1 -> "Несколько капель"
        2 -> "Немного"
        else -> "Больше"
    }
    override val qWhen = "Когда это бывает? Можно выбрать несколько."
    override fun situation(situation: LeakSituation) = when (situation) {
        LeakSituation.COUGH -> "При кашле, чихании, смехе"
        LeakSituation.EXERCISE -> "При нагрузке, подъёме тяжестей"
        LeakSituation.URGE -> "Не успеваю дойти до туалета"
        LeakSituation.NIGHT -> "Ночью"
        LeakSituation.OTHER -> "Без причины"
    }
    override val qBother = "Насколько это мешает вашей жизни?"
    override val botherScale = "0 — совсем не мешает, 10 — очень"
    override val save = "Сохранить"
    override val checksHistory = "Опросы"
    override fun bother(value: Int) = "мешает $value / 10"
    override val checkSaved = "Опрос сохранён"
    override val getReady = "Приготовьтесь"
    override val squeeze = "Сожмите"
    override val relax = "Расслабьте"
    override val flick = "Быстро сожмите"
    override val rest = "Отдых"
    override fun set(index: Int, of: Int) = "Подход $index / $of"
    override val pause = "Пауза"
    override val resume = "Продолжить"
    override val stop = "Закончить"
    override val stopTitle = "Закончить упражнение?"
    override val stopBody = "Сделанная часть сохранится."
    override val doneTitle = "Отлично!"
    override fun doneBody(reps: Int, minutes: Int) = "$reps ${ru(reps, "сжатие", "сжатия", "сжатий")} · $minutes мин"
    override fun coins(amount: Int) = "+$amount Gul"
    override fun levelOpened(level: Int) = "Открыт уровень $level!"
    override val close = "Закрыть"
    override val eyesClosed = "Можно делать с закрытыми глазами: длинная вибрация — сожмите, две короткие — расслабьте."
}

object KegelStringsEn : KegelStrings {
    override val title = "Pelvic floor exercises"
    override val shortTitle = "Kegel"
    override val intro = "Kegel exercises strengthen the pelvic floor: better bladder control, an easier time preparing for birth and recovering after it."
    override fun program(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Core program"
        KegelProgram.PREGNANCY -> "Pregnancy program"
        KegelProgram.POSTPARTUM -> "Postpartum program"
        KegelProgram.MENOPAUSE -> "Menopause program"
    }
    override fun programNote(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Twice a day, a few minutes each. Most people notice a change in 6–8 weeks."
        KegelProgram.PREGNANCY -> "Gentle squeezes and full release prepare you for birth. If your pregnancy is high-risk or your doctor said no, ask first."
        KegelProgram.POSTPARTUM -> "Starts very gently and builds slowly. If you have stitches, pain or bleeding, wait for your doctor's go-ahead."
        KegelProgram.MENOPAUSE -> "With less oestrogen the pelvic floor weakens. Longer holds support bladder control."
    }
    override val programLabel = "Program"
    override val yourStage = "your stage"
    override fun level(level: Int) = "Level $level"
    override fun levelDetail(hold: Int, relax: Int, reps: Int, sets: Int, minutes: Int) =
        "Squeeze $hold s · release $relax s · $reps × $sets · ~$minutes min"
    override fun today(done: Int, goal: Int) = "Today: $done / $goal"
    override fun streak(days: Int) = "$days ${en(days, "day", "days")} in a row"
    override fun nextLevel(days: Int, need: Int) = "To the next level: $days / $need days"
    override val allLevels = "Every level is open"
    override val start = "Start the exercise"
    override val premium = "Premium"
    override val lastWeeks = "Last 4 weeks"
    override val remindersTitle = "Reminders"
    override val remindersNote = "Up to 3 a day. The notification never names the exercise."
    override val addReminder = "Add a time"
    override val reminderTime = "Time (HH:MM)"
    override val add = "Add"
    override val noReminders = "No reminders"
    override val saved = "Saved"
    override val postpartumEarlyTitle = "It has been less than 6 weeks since the birth"
    override val postpartumEarlyBody = "Most doctors suggest starting after the 6-week check-up. If your doctor has said yes, begin with the lightest level."
    override val howTitle = "How to do it"
    override val howSteps = listOf(
        "Find the muscle: squeeze as if stopping your pee midway. That is only to find it — don't exercise on the toilet.",
        "Keep your belly, thighs and bottom relaxed: only the inner muscle works.",
        "Don't hold your breath; breathe easily.",
        "Letting go matters as much as squeezing: release fully every time.",
        "If it hurts, stop and tell your doctor.",
    )
    override val safety = "Exercise doesn't replace treatment. If leaks get worse or there is pain, see a doctor."
    override val checkTitle = "Four-weekly check"
    override val checkNote = "Two minutes: you will see the change, and your doctor sees it in the report."
    override val checkStart = "Start"
    override val later = "Later"
    override val qFrequency = "In the last 4 weeks, how often did you leak urine?"
    override fun frequency(value: Int) = when (value) {
        0 -> "Never"
        1 -> "About once a week or less"
        2 -> "2–3 times a week"
        3 -> "About once a day"
        else -> "Several times a day"
    }
    override val qAmount = "How much, usually?"
    override fun amount(value: Int) = when (value) {
        1 -> "A few drops"
        2 -> "A little"
        else -> "More"
    }
    override val qWhen = "When does it happen? Pick any."
    override fun situation(situation: LeakSituation) = when (situation) {
        LeakSituation.COUGH -> "When I cough, sneeze or laugh"
        LeakSituation.EXERCISE -> "When I exercise or lift"
        LeakSituation.URGE -> "Before I reach the toilet"
        LeakSituation.NIGHT -> "At night"
        LeakSituation.OTHER -> "For no obvious reason"
    }
    override val qBother = "How much does it get in the way of your life?"
    override val botherScale = "0 — not at all, 10 — a great deal"
    override val save = "Save"
    override val checksHistory = "Checks"
    override fun bother(value: Int) = "bother $value / 10"
    override val checkSaved = "Check saved"
    override val getReady = "Get ready"
    override val squeeze = "Squeeze"
    override val relax = "Release"
    override val flick = "Quick squeeze"
    override val rest = "Rest"
    override fun set(index: Int, of: Int) = "Set $index / $of"
    override val pause = "Pause"
    override val resume = "Resume"
    override val stop = "Finish"
    override val stopTitle = "Finish the exercise?"
    override val stopBody = "What you did is saved."
    override val doneTitle = "Well done!"
    override fun doneBody(reps: Int, minutes: Int) = "$reps ${en(reps, "squeeze", "squeezes")} · $minutes min"
    override fun coins(amount: Int) = "+$amount Gul"
    override fun levelOpened(level: Int) = "Level $level is open!"
    override val close = "Close"
    override val eyesClosed = "You can do it with your eyes closed: one long buzz — squeeze, two short — release."
}
