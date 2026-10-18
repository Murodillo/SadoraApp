package uz.sadora.app.i18n

import uz.sadora.contract.DiaperKind
import uz.sadora.contract.GrowthMetric
import uz.sadora.contract.VaccineStatus

/**
 * The baby tracker: the baby's page, the profile form, growth, vaccines and milestones.
 *
 * Vaccine and milestone names come from the server in her language; only the app's own
 * words are here. Nothing in this file tells her a baby is "behind": a window is where
 * most babies are, and the screens send every worry to the paediatrician.
 */
interface BabyStrings {
    val title: String
    val entryTitle: String
    val entryBody: String
    val addBaby: String
    val emptyTitle: String
    val emptyBody: String
    /** "3 kunlik", "6 haftalik", "4 oylik", "1 yosh 2 oylik". */
    fun age(days: Int): String

    // ---- the day
    val today: String
    val feeds: String
    val sleep: String
    val diapers: String
    val feed: String
    val asleep: String
    val awake: String
    fun sleepingFor(duration: String): String
    fun awakeFor(duration: String): String
    val diaper: String
    fun diaperKind(kind: DiaperKind): String
    fun lastFeed(ago: String): String
    fun lastDiaper(ago: String): String
    /** "2 soat 10 daq oldin". */
    fun ago(span: String): String
    val justNow: String
    val week: String
    val saved: String

    // ---- the cards
    val growth: String
    val vaccines: String
    val milestones: String
    fun nextVaccine(name: String, date: String): String
    val vaccinesDone: String
    fun milestonesCount(reached: Int, total: Int): String
    fun latestGrowth(parts: String): String
    val noGrowth: String

    // ---- the profile
    val newBaby: String
    val editBaby: String
    val name: String
    val sex: String
    val girl: String
    val boy: String
    val sexNote: String
    val birthDate: String
    val birthWeight: String
    val birthLength: String
    val edit: String
    val delete: String
    val deleteTitle: String
    val deleteBody: String

    // ---- growth
    fun metric(metric: GrowthMetric): String
    val addMeasurement: String
    val measuredOn: String
    val weightField: String
    val lengthField: String
    val headField: String
    val kg: String
    val cm: String
    val grams: String
    val measurements: String
    val chartLockedTitle: String
    val chartLockedBody: String
    val upgrade: String
    fun percentile(value: Int): String
    val percentileNote: String
    val whoSource: String
    val curvesLegend: String
    /** The chart's right edge: "6 oy". */
    fun months(count: Int): String

    // ---- vaccines
    val vaccinesTitle: String
    val vaccinesSource: String
    val vaccinesNote: String
    fun status(status: VaccineStatus): String
    fun dueOn(date: String): String
    fun givenOn(date: String): String
    val markGiven: String
    val givenDate: String
    val unmark: String

    // ---- milestones
    val milestonesTitle: String
    val milestonesNote: String
    fun group(key: String): String
    fun window(from: Int, to: Int): String
    fun reachedOn(date: String): String
    val markReached: String
    val reachedDate: String
    val note: String
    val addPhoto: String
    val changePhoto: String
}

object BabyStringsUz : BabyStrings {
    override val title = "Chaqaloq"
    override val entryTitle = "Chaqaloq kuzatuvi"
    override val entryBody = "Emizish, uyqu, taglik, o'sish, emlash va rivojlanish — bir joyda"
    override val addBaby = "Chaqaloq qo'shish"
    override val emptyTitle = "Chaqaloq profilini yarating"
    override val emptyBody = "Ismi, jinsi va tug'ilgan sanasi bilan. Shunda emizish, uyqu, taglik, o'sish va emlashlarni shu yerda kuzatasiz."
    override fun age(days: Int) = when {
        days < 14 -> "$days kunlik"
        days < 91 -> "${days / 7} haftalik"
        days < 365 -> "${(days / 30.4375).toInt()} oylik"
        else -> {
            val months = (days / 30.4375).toInt()
            "${months / 12} yosh" + if (months % 12 > 0) " ${months % 12} oylik" else ""
        }
    }

    override val today = "Bugun"
    override val feeds = "Emizish"
    override val sleep = "Uyqu"
    override val diapers = "Taglik"
    override val feed = "Emizish"
    override val asleep = "Uxladi"
    override val awake = "Uyg'ondi"
    override fun sleepingFor(duration: String) = "Uxlayapti · $duration"
    override fun awakeFor(duration: String) = "Uyg'onganiga $duration"
    override val diaper = "Taglik"
    override fun diaperKind(kind: DiaperKind) = when (kind) {
        DiaperKind.WET -> "Ho'l"
        DiaperKind.DIRTY -> "Iflos"
        DiaperKind.BOTH -> "Ikkalasi"
    }
    override fun lastFeed(ago: String) = "Oxirgi emizish: $ago"
    override fun lastDiaper(ago: String) = "Oxirgi taglik: $ago"
    override fun ago(span: String) = "$span oldin"
    override val justNow = "hozirgina"
    override val week = "Oxirgi 7 kun"
    override val saved = "Saqlandi"

    override val growth = "O'sish"
    override val vaccines = "Emlash"
    override val milestones = "Rivojlanish"
    override fun nextVaccine(name: String, date: String) = "$name · $date"
    override val vaccinesDone = "Taqvimdagi hamma emlashlar qilingan"
    override fun milestonesCount(reached: Int, total: Int) = "$total tadan $reached tasi"
    override fun latestGrowth(parts: String) = "Oxirgi o'lchov: $parts"
    override val noGrowth = "Hali o'lchov yo'q"

    override val newBaby = "Yangi chaqaloq"
    override val editBaby = "Chaqaloq profili"
    override val name = "Ismi"
    override val sex = "Jinsi"
    override val girl = "Qiz"
    override val boy = "O'g'il"
    override val sexNote = "Jinsi faqat WHO o'sish grafiklari uchun kerak: qiz va o'g'il bolalarning normalari farq qiladi."
    override val birthDate = "Tug'ilgan sana"
    override val birthWeight = "Tug'ilgandagi vazni, g"
    override val birthLength = "Tug'ilgandagi bo'yi, sm"
    override val edit = "Tahrirlash"
    override val delete = "O'chirish"
    override val deleteTitle = "Chaqaloq profili o'chirilsinmi?"
    override val deleteBody = "Emizish, uyqu, taglik, o'sish, emlash va rivojlanish yozuvlari butunlay o'chadi."

    override fun metric(metric: GrowthMetric) = when (metric) {
        GrowthMetric.WEIGHT -> "Vazn"
        GrowthMetric.LENGTH -> "Bo'y"
        GrowthMetric.HEAD -> "Bosh aylanasi"
    }
    override val addMeasurement = "O'lchov qo'shish"
    override val measuredOn = "Sana"
    override val weightField = "Vazn, g"
    override val lengthField = "Bo'y, sm"
    override val headField = "Bosh aylanasi, sm"
    override val kg = "kg"
    override val cm = "sm"
    override val grams = "g"
    override val measurements = "O'lchovlar"
    override val chartLockedTitle = "WHO grafigi — Premium"
    override val chartLockedBody = "O'lchovlaringiz bepul saqlanadi. Premium'da ular WHO o'sish egri chiziqlarida, persentil bilan ko'rinadi."
    override val upgrade = "Premium"
    override fun percentile(value: Int) = "$value-persentil"
    override val percentileNote = "Persentil — 100 nafar tengdoshi orasidagi o'rni. Ko'pchilik bolalar 3- va 97-persentil oralig'ida bo'ladi. Savollar bo'lsa, pediatrga murojaat qiling."
    override val whoSource = "WHO o'sish standartlari, 2006"
    override val curvesLegend = "Chiziqlar: 3, 15, 50, 85, 97-persentil"
    override fun months(count: Int) = "$count oy"

    override val vaccinesTitle = "Emlash taqvimi"
    override val vaccinesSource = "O'zbekiston milliy emlash taqvimi (SSV, 2026)"
    override val vaccinesNote = "Sanalar tug'ilgan kundan hisoblanadi. Aniq vaqtni poliklinikangiz belgilaydi. Eslatma muddatdan 3 kun oldin va o'sha kuni keladi."
    override fun status(status: VaccineStatus) = when (status) {
        VaccineStatus.DONE -> "Qilindi"
        VaccineStatus.OVERDUE -> "Muddati o'tdi"
        VaccineStatus.DUE -> "Yaqinda"
        VaccineStatus.UPCOMING -> "Kutilmoqda"
    }
    override fun dueOn(date: String) = "Muddati: $date"
    override fun givenOn(date: String) = "Qilingan: $date"
    override val markGiven = "Qilindi deb belgilash"
    override val givenDate = "Qilingan sana"
    override val unmark = "Belgini olib tashlash"

    override val milestonesTitle = "Rivojlanish bosqichlari"
    override val milestonesNote = "Har bir bola o'z sur'atida rivojlanadi. Oraliqlar ko'pchilik bolalar uchun berilgan; xavotir bo'lsa, pediatr bilan gaplashing."
    override fun group(key: String) = when (key) {
        "skills" -> "Harakat"
        "social" -> "Muloqot"
        "language" -> "Nutq"
        "teeth" -> "Tishlar"
        else -> "Boshqa"
    }
    override fun window(from: Int, to: Int) = "Odatda $from–$to oyda"
    override fun reachedOn(date: String) = "$date ✓"
    override val markReached = "Bo'ldi!"
    override val reachedDate = "Sana"
    override val note = "Izoh"
    override val addPhoto = "Rasm qo'shish"
    override val changePhoto = "Rasmni almashtirish"
}

object BabyStringsRu : BabyStrings {
    override val title = "Малыш"
    override val entryTitle = "Дневник малыша"
    override val entryBody = "Кормления, сон, подгузники, рост, прививки и развитие — в одном месте"
    override val addBaby = "Добавить ребёнка"
    override val emptyTitle = "Создайте профиль малыша"
    override val emptyBody = "Имя, пол и дата рождения — и здесь можно будет следить за кормлениями, сном, подгузниками, ростом и прививками."
    override fun age(days: Int) = when {
        days < 14 -> "$days дн."
        days < 91 -> "${days / 7} нед."
        days < 365 -> "${(days / 30.4375).toInt()} мес."
        else -> {
            val months = (days / 30.4375).toInt()
            "${months / 12} г." + if (months % 12 > 0) " ${months % 12} мес." else ""
        }
    }

    override val today = "Сегодня"
    override val feeds = "Кормления"
    override val sleep = "Сон"
    override val diapers = "Подгузники"
    override val feed = "Кормление"
    override val asleep = "Уснул(а)"
    override val awake = "Проснулся(ась)"
    override fun sleepingFor(duration: String) = "Спит · $duration"
    override fun awakeFor(duration: String) = "Бодрствует $duration"
    override val diaper = "Подгузник"
    override fun diaperKind(kind: DiaperKind) = when (kind) {
        DiaperKind.WET -> "Мокрый"
        DiaperKind.DIRTY -> "Грязный"
        DiaperKind.BOTH -> "Оба"
    }
    override fun lastFeed(ago: String) = "Последнее кормление: $ago"
    override fun lastDiaper(ago: String) = "Последний подгузник: $ago"
    override fun ago(span: String) = "$span назад"
    override val justNow = "только что"
    override val week = "Последние 7 дней"
    override val saved = "Сохранено"

    override val growth = "Рост"
    override val vaccines = "Прививки"
    override val milestones = "Развитие"
    override fun nextVaccine(name: String, date: String) = "$name · $date"
    override val vaccinesDone = "Все прививки календаря сделаны"
    override fun milestonesCount(reached: Int, total: Int) = "$reached из $total"
    override fun latestGrowth(parts: String) = "Последнее измерение: $parts"
    override val noGrowth = "Измерений пока нет"

    override val newBaby = "Новый малыш"
    override val editBaby = "Профиль малыша"
    override val name = "Имя"
    override val sex = "Пол"
    override val girl = "Девочка"
    override val boy = "Мальчик"
    override val sexNote = "Пол нужен только для графиков роста ВОЗ: нормы для девочек и мальчиков разные."
    override val birthDate = "Дата рождения"
    override val birthWeight = "Вес при рождении, г"
    override val birthLength = "Рост при рождении, см"
    override val edit = "Изменить"
    override val delete = "Удалить"
    override val deleteTitle = "Удалить профиль малыша?"
    override val deleteBody = "Кормления, сон, подгузники, рост, прививки и развитие удалятся навсегда."

    override fun metric(metric: GrowthMetric) = when (metric) {
        GrowthMetric.WEIGHT -> "Вес"
        GrowthMetric.LENGTH -> "Рост"
        GrowthMetric.HEAD -> "Окружность головы"
    }
    override val addMeasurement = "Добавить измерение"
    override val measuredOn = "Дата"
    override val weightField = "Вес, г"
    override val lengthField = "Рост, см"
    override val headField = "Окружность головы, см"
    override val kg = "кг"
    override val cm = "см"
    override val grams = "г"
    override val measurements = "Измерения"
    override val chartLockedTitle = "График ВОЗ — Premium"
    override val chartLockedBody = "Измерения сохраняются бесплатно. В Premium они видны на кривых роста ВОЗ, с перцентилем."
    override val upgrade = "Premium"
    override fun percentile(value: Int) = "$value-й перцентиль"
    override val percentileNote = "Перцентиль — место среди 100 сверстников. У большинства детей он между 3-м и 97-м. Если есть вопросы, обратитесь к педиатру."
    override val whoSource = "Стандарты роста ВОЗ, 2006"
    override val curvesLegend = "Линии: 3, 15, 50, 85, 97-й перцентили"
    override fun months(count: Int) = "$count мес."

    override val vaccinesTitle = "Календарь прививок"
    override val vaccinesSource = "Национальный календарь прививок Узбекистана (Минздрав, 2026)"
    override val vaccinesNote = "Даты считаются от дня рождения. Точное время назначает ваша поликлиника. Напоминание придёт за 3 дня и в сам день."
    override fun status(status: VaccineStatus) = when (status) {
        VaccineStatus.DONE -> "Сделана"
        VaccineStatus.OVERDUE -> "Просрочена"
        VaccineStatus.DUE -> "Скоро"
        VaccineStatus.UPCOMING -> "Впереди"
    }
    override fun dueOn(date: String) = "Срок: $date"
    override fun givenOn(date: String) = "Сделана: $date"
    override val markGiven = "Отметить сделанной"
    override val givenDate = "Дата прививки"
    override val unmark = "Снять отметку"

    override val milestonesTitle = "Этапы развития"
    override val milestonesNote = "Каждый ребёнок развивается в своём темпе. Сроки даны для большинства детей; если что-то беспокоит, поговорите с педиатром."
    override fun group(key: String) = when (key) {
        "skills" -> "Движение"
        "social" -> "Общение"
        "language" -> "Речь"
        "teeth" -> "Зубы"
        else -> "Другое"
    }
    override fun window(from: Int, to: Int) = "Обычно в $from–$to мес."
    override fun reachedOn(date: String) = "$date ✓"
    override val markReached = "Получилось!"
    override val reachedDate = "Дата"
    override val note = "Заметка"
    override val addPhoto = "Добавить фото"
    override val changePhoto = "Заменить фото"
}

object BabyStringsEn : BabyStrings {
    override val title = "Baby"
    override val entryTitle = "Baby tracker"
    override val entryBody = "Feeds, sleep, nappies, growth, vaccines and milestones — in one place"
    override val addBaby = "Add a baby"
    override val emptyTitle = "Create your baby's profile"
    override val emptyBody = "A name, sex and birth date — then feeds, sleep, nappies, growth and vaccines are tracked here."
    override fun age(days: Int) = when {
        days < 14 -> "$days ${if (days == 1) "day" else "days"} old"
        days < 91 -> "${days / 7} weeks old"
        days < 365 -> "${(days / 30.4375).toInt()} months old"
        else -> {
            val months = (days / 30.4375).toInt()
            "${months / 12} y" + if (months % 12 > 0) " ${months % 12} m" else ""
        }
    }

    override val today = "Today"
    override val feeds = "Feeds"
    override val sleep = "Sleep"
    override val diapers = "Nappies"
    override val feed = "Feed"
    override val asleep = "Fell asleep"
    override val awake = "Woke up"
    override fun sleepingFor(duration: String) = "Asleep · $duration"
    override fun awakeFor(duration: String) = "Awake for $duration"
    override val diaper = "Nappy"
    override fun diaperKind(kind: DiaperKind) = when (kind) {
        DiaperKind.WET -> "Wet"
        DiaperKind.DIRTY -> "Dirty"
        DiaperKind.BOTH -> "Both"
    }
    override fun lastFeed(ago: String) = "Last feed: $ago"
    override fun lastDiaper(ago: String) = "Last nappy: $ago"
    override fun ago(span: String) = "$span ago"
    override val justNow = "just now"
    override val week = "Last 7 days"
    override val saved = "Saved"

    override val growth = "Growth"
    override val vaccines = "Vaccines"
    override val milestones = "Milestones"
    override fun nextVaccine(name: String, date: String) = "$name · $date"
    override val vaccinesDone = "Every vaccine on the calendar is done"
    override fun milestonesCount(reached: Int, total: Int) = "$reached of $total"
    override fun latestGrowth(parts: String) = "Last measured: $parts"
    override val noGrowth = "No measurements yet"

    override val newBaby = "New baby"
    override val editBaby = "Baby's profile"
    override val name = "Name"
    override val sex = "Sex"
    override val girl = "Girl"
    override val boy = "Boy"
    override val sexNote = "The sex is only for the WHO growth charts: girls and boys have different standards."
    override val birthDate = "Birth date"
    override val birthWeight = "Birth weight, g"
    override val birthLength = "Birth length, cm"
    override val edit = "Edit"
    override val delete = "Delete"
    override val deleteTitle = "Delete this baby's profile?"
    override val deleteBody = "Feeds, sleep, nappies, growth, vaccines and milestones are deleted for good."

    override fun metric(metric: GrowthMetric) = when (metric) {
        GrowthMetric.WEIGHT -> "Weight"
        GrowthMetric.LENGTH -> "Length"
        GrowthMetric.HEAD -> "Head"
    }
    override val addMeasurement = "Add a measurement"
    override val measuredOn = "Date"
    override val weightField = "Weight, g"
    override val lengthField = "Length, cm"
    override val headField = "Head circumference, cm"
    override val kg = "kg"
    override val cm = "cm"
    override val grams = "g"
    override val measurements = "Measurements"
    override val chartLockedTitle = "WHO chart — Premium"
    override val chartLockedBody = "Your measurements are saved for free. With Premium they are drawn on the WHO growth curves, with a percentile."
    override val upgrade = "Premium"
    override fun percentile(value: Int) = "${value}th percentile"
    override val percentileNote = "A percentile is the place among 100 babies of the same age. Most babies are between the 3rd and the 97th. Ask your paediatrician if you have questions."
    override val whoSource = "WHO Child Growth Standards, 2006"
    override val curvesLegend = "Lines: 3rd, 15th, 50th, 85th, 97th percentile"
    override fun months(count: Int) = "$count months"

    override val vaccinesTitle = "Vaccine calendar"
    override val vaccinesSource = "Uzbekistan national immunisation calendar (Ministry of Health, 2026)"
    override val vaccinesNote = "Dates count from the birth date; your clinic sets the exact day. A reminder comes 3 days before and on the day."
    override fun status(status: VaccineStatus) = when (status) {
        VaccineStatus.DONE -> "Done"
        VaccineStatus.OVERDUE -> "Overdue"
        VaccineStatus.DUE -> "Soon"
        VaccineStatus.UPCOMING -> "Upcoming"
    }
    override fun dueOn(date: String) = "Due: $date"
    override fun givenOn(date: String) = "Given: $date"
    override val markGiven = "Mark as given"
    override val givenDate = "Date given"
    override val unmark = "Remove the mark"

    override val milestonesTitle = "Milestones"
    override val milestonesNote = "Every baby grows at their own pace. The windows are where most babies are; if something worries you, talk to your paediatrician."
    override fun group(key: String) = when (key) {
        "skills" -> "Movement"
        "social" -> "Social"
        "language" -> "Language"
        "teeth" -> "Teeth"
        else -> "Other"
    }
    override fun window(from: Int, to: Int) = "Usually at $from–$to months"
    override fun reachedOn(date: String) = "$date ✓"
    override val markReached = "Done!"
    override val reachedDate = "Date"
    override val note = "Note"
    override val addPhoto = "Add a photo"
    override val changePhoto = "Change photo"
}
