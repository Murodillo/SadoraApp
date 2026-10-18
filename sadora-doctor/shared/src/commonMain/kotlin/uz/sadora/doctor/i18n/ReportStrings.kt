package uz.sadora.doctor.i18n

/**
 * The PDF report a patient sends into a consultation: its card in the thread and the
 * conversation list's preview of it.
 */
interface ReportStrings {
    val title: String
    /** On the card: downloads the file and hands it to a PDF viewer. */
    val open: String
    /** While the file is on its way. */
    val opening: String
    /** The file could not be fetched or no app could show it. */
    val failed: String
    /** "240 KB", "1.4 MB": the file's size on the card. */
    fun size(bytes: Int): String
}

/** [bytes] as KB below a megabyte and MB with one decimal above, with [decimal] as the separator. */
internal fun fileSize(bytes: Int, kb: String, mb: String, decimal: Char): String {
    val kilo = 1024
    if (bytes < kilo * kilo) return "${((bytes + kilo - 1) / kilo).coerceAtLeast(1)} $kb"
    val tenths = (bytes * 10L + kilo * kilo / 2) / (kilo * kilo)
    val whole = tenths / 10
    val rest = tenths % 10
    return if (rest == 0L) "$whole $mb" else "$whole$decimal$rest $mb"
}

object ReportStringsUz : ReportStrings {
    override val title = "Hisobot (PDF)"
    override val open = "PDF ochish"
    override val opening = "Yuklanmoqda…"
    override val failed = "Faylni ochib bo'lmadi"
    override fun size(bytes: Int) = fileSize(bytes, "KB", "MB", ',')
}

object ReportStringsRu : ReportStrings {
    override val title = "Отчёт (PDF)"
    override val open = "Открыть PDF"
    override val opening = "Загрузка…"
    override val failed = "Не удалось открыть файл"
    override fun size(bytes: Int) = fileSize(bytes, "КБ", "МБ", ',')
}

object ReportStringsEn : ReportStrings {
    override val title = "Report (PDF)"
    override val open = "Open PDF"
    override val opening = "Loading…"
    override val failed = "Couldn't open the file"
    override fun size(bytes: Int) = fileSize(bytes, "KB", "MB", '.')
}
