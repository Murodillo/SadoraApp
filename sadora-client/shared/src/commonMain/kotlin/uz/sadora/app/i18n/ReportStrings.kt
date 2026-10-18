package uz.sadora.app.i18n

import uz.sadora.contract.ReportSection

/**
 * "Shifokor uchun hisobot": the PDF she makes for a doctor, from its own screen or straight
 * into a consultation. The file's own words are the server's, in the language she picks.
 */
interface ReportStrings {
    val title: String
    val intro: String
    val period: String
    fun months(months: Int): String
    val sections: String
    fun section(section: ReportSection): String
    val pickSection: String
    val language: String
    fun allowance(remaining: Int): String
    val unlimited: String
    val make: String
    val note: String
    val lockedTitle: String
    val lockedBody: String
    val upgrade: String
    val linkNote: String

    // ---- in a consultation
    val attach: String
    val attachNote: String
    val send: String
    val lastLine: String
    val bubbleTitle: String
    fun size(bytes: Int): String
}

private fun megabytes(bytes: Int, kb: String, mb: String): String =
    if (bytes < 1_000_000) "${(bytes + 999) / 1000} $kb" else "${bytes / 100_000 / 10.0} $mb"

object ReportStringsUz : ReportStrings {
    override val title = "Shifokor uchun hisobot"
    override val intro = "Davr va bo'limlarni tanlang — SADORA ularni shifokor bir qarashda o'qiydigan PDF faylga yig'adi."
    override val period = "Davr"
    override fun months(months: Int) = "$months oy"
    override val sections = "Bo'limlar"
    override fun section(section: ReportSection) = when (section) {
        ReportSection.CYCLE -> "Hayz sikli"
        ReportSection.SYMPTOMS -> "Simptomlar"
        ReportSection.MEDICATIONS -> "Dorilar"
        ReportSection.LABS -> "Tahlil natijalari"
        ReportSection.CONDITIONS -> "Holatlar va og'riq"
        ReportSection.MOOD -> "Kayfiyat"
        ReportSection.PREGNANCY -> "Homiladorlik"
        ReportSection.PELVIC_FLOOR -> "Tos tubi (Kegel)"
    }
    override val pickSection = "Kamida bitta bo'limni tanlang"
    override val language = "Hisobot tili"
    override fun allowance(remaining: Int) =
        if (remaining > 0) "Bu oy $remaining ta bepul hisobot qoldi" else "Bu oygi bepul hisobot ishlatilgan"
    override val unlimited = "Premium: hisobotlar cheksiz"
    override val make = "PDF tayyorlash va ulashish"
    override val note = "Hisobot tashxis emas: unda faqat siz yozgan va qurilmangiz o'lchagan ma'lumotlar bo'ladi."
    override val lockedTitle = "Bu oygi bepul hisobot ishlatildi"
    override val lockedBody = "Premium bilan istalgancha hisobot tayyorlaysiz. Aks holda keyingi bepul hisobot kelasi oy."
    override val upgrade = "Premium'ni ko'rish"
    override val linkNote = "3 yoki 6 oylik ma'lumotlaringiz bitta PDF faylda"
    override val attach = "Hisobot (PDF)"
    override val attachNote = "Tanlangan davr va bo'limlar, PDF fayl bo'lib"
    override val send = "Shifokorga yuborish"
    override val lastLine = "Hisobot (PDF)"
    override val bubbleTitle = "Shifokor uchun hisobot"
    override fun size(bytes: Int) = megabytes(bytes, "KB", "MB")
}

object ReportStringsRu : ReportStrings {
    override val title = "Отчёт для врача"
    override val intro = "Выберите период и разделы — SADORA соберёт их в PDF, который врач прочтёт с одного взгляда."
    override val period = "Период"
    override fun months(months: Int) = "$months мес."
    override val sections = "Разделы"
    override fun section(section: ReportSection) = when (section) {
        ReportSection.CYCLE -> "Цикл"
        ReportSection.SYMPTOMS -> "Симптомы"
        ReportSection.MEDICATIONS -> "Лекарства"
        ReportSection.LABS -> "Анализы"
        ReportSection.CONDITIONS -> "Состояния и боль"
        ReportSection.MOOD -> "Настроение"
        ReportSection.PREGNANCY -> "Беременность"
        ReportSection.PELVIC_FLOOR -> "Тазовое дно (Кегель)"
    }
    override val pickSection = "Выберите хотя бы один раздел"
    override val language = "Язык отчёта"
    override fun allowance(remaining: Int) =
        if (remaining > 0) "В этом месяце бесплатных отчётов: $remaining" else "Бесплатный отчёт этого месяца использован"
    override val unlimited = "Premium: отчёты без ограничений"
    override val make = "Создать PDF и поделиться"
    override val note = "Отчёт — не диагноз: в нём только то, что записали вы и измерило ваше устройство."
    override val lockedTitle = "Бесплатный отчёт этого месяца использован"
    override val lockedBody = "С Premium отчётов сколько угодно. Иначе следующий бесплатный — в следующем месяце."
    override val upgrade = "Посмотреть Premium"
    override val linkNote = "Ваши данные за 3 или 6 месяцев одним PDF-файлом"
    override val attach = "Отчёт (PDF)"
    override val attachNote = "Выбранный период и разделы, PDF-файлом"
    override val send = "Отправить врачу"
    override val lastLine = "Отчёт (PDF)"
    override val bubbleTitle = "Отчёт для врача"
    override fun size(bytes: Int) = megabytes(bytes, "КБ", "МБ")
}

object ReportStringsEn : ReportStrings {
    override val title = "Report for your doctor"
    override val intro = "Pick a period and the sections — SADORA puts them into a PDF a doctor can read at a glance."
    override val period = "Period"
    override fun months(months: Int) = "$months months"
    override val sections = "Sections"
    override fun section(section: ReportSection) = when (section) {
        ReportSection.CYCLE -> "Cycle"
        ReportSection.SYMPTOMS -> "Symptoms"
        ReportSection.MEDICATIONS -> "Medications"
        ReportSection.LABS -> "Lab results"
        ReportSection.CONDITIONS -> "Conditions and pain"
        ReportSection.MOOD -> "Mood"
        ReportSection.PREGNANCY -> "Pregnancy"
        ReportSection.PELVIC_FLOOR -> "Pelvic floor (Kegel)"
    }
    override val pickSection = "Pick at least one section"
    override val language = "Report language"
    override fun allowance(remaining: Int) =
        if (remaining > 0) "Free reports left this month: $remaining" else "This month's free report is used"
    override val unlimited = "Premium: unlimited reports"
    override val make = "Make the PDF and share"
    override val note = "The report is not a diagnosis: it holds only what you recorded and your device measured."
    override val lockedTitle = "This month's free report is used"
    override val lockedBody = "With Premium you can make as many as you like. Otherwise the next free one comes next month."
    override val upgrade = "See Premium"
    override val linkNote = "Three or six months of your records in one PDF"
    override val attach = "Report (PDF)"
    override val attachNote = "The period and sections you pick, as a PDF file"
    override val send = "Send to the doctor"
    override val lastLine = "Report (PDF)"
    override val bubbleTitle = "Report for the doctor"
    override fun size(bytes: Int) = megabytes(bytes, "KB", "MB")
}
