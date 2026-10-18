package uz.sadora.doctor.i18n

import uz.sadora.contract.LabFlag

/**
 * Her lab sheets: the section of her record, the card a sheet makes in a consultation,
 * and the sheet opened in full.
 *
 * The rows — names, results, units, the laboratory's ranges — arrive as she or the
 * sheet wrote them; only the labels around them are worded here.
 */
interface LabStrings {
    val title: String
    /** Before the laboratory's own range: "Norma: 120–150". */
    val range: String
    fun flag(flag: LabFlag): String
    /** "12 ta ko'rsatkich". */
    fun values(count: Int): String
    /** "3 tasi normadan tashqarida". */
    fun flagged(count: Int): String
    /** The rows were read from a photo of the sheet, not typed — worth checking against it. */
    val scanned: String
    val sheetPhoto: String
    /** A page, for a screen reader: "Varaq rasmi 2". */
    fun sheetPhotoNumber(page: Int): String
    /** On the card: opens the sheet with every row. */
    val allValues: String
    /** Under a card whose sheet is gone: only the text sent with it is left. */
    val deleted: String
}

object LabStringsUz : LabStrings {
    override val title = "Tahlil natijalari"
    override val range = "Norma"
    override fun flag(flag: LabFlag) = when (flag) {
        LabFlag.LOW -> "Past"
        LabFlag.NORMAL -> "Normada"
        LabFlag.HIGH -> "Yuqori"
    }
    override fun values(count: Int) = "$count ta ko'rsatkich"
    override fun flagged(count: Int) = "$count tasi normadan tashqarida"
    override val scanned = "Rasmdan o'qilgan"
    override val sheetPhoto = "Varaq rasmi"
    override fun sheetPhotoNumber(page: Int) = "Varaq rasmi $page"
    override val allValues = "Barcha ko'rsatkichlar"
    override val deleted = "Bemor bu tahlilni o'chirgan"
}

object LabStringsRu : LabStrings {
    override val title = "Результаты анализов"
    override val range = "Референс"
    override fun flag(flag: LabFlag) = when (flag) {
        LabFlag.LOW -> "Ниже нормы"
        LabFlag.NORMAL -> "В норме"
        LabFlag.HIGH -> "Выше нормы"
    }
    override fun values(count: Int) = "$count " + ru(count, "показатель", "показателя", "показателей")
    override fun flagged(count: Int) = "$count вне нормы"
    override val scanned = "Распознано по фото"
    override val sheetPhoto = "Фото бланка"
    override fun sheetPhotoNumber(page: Int) = "Фото бланка $page"
    override val allValues = "Все показатели"
    override val deleted = "Пациентка удалила этот анализ"
}

object LabStringsEn : LabStrings {
    override val title = "Lab results"
    override val range = "Range"
    override fun flag(flag: LabFlag) = when (flag) {
        LabFlag.LOW -> "Low"
        LabFlag.NORMAL -> "In range"
        LabFlag.HIGH -> "High"
    }
    override fun values(count: Int) = "$count " + en(count, "value", "values")
    override fun flagged(count: Int) = "$count out of range"
    override val scanned = "Read from a photo"
    override val sheetPhoto = "Sheet photo"
    override fun sheetPhotoNumber(page: Int) = "Sheet photo $page"
    override val allValues = "All values"
    override val deleted = "The patient deleted this sheet"
}
