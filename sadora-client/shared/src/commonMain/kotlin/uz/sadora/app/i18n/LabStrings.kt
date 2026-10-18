package uz.sadora.app.i18n

import uz.sadora.contract.LabFlag

/**
 * Lab results: the section, one sheet, a marker's chart, the editor, the scanner and the
 * card in a consultation.
 *
 * The marker names and their explanations come from the server in her language; the
 * names of rows the app does not know are shown as the laboratory printed them.
 */
interface LabStrings {
    val title: String
    val emptyTitle: String
    val emptyBody: String
    val scan: String
    val scanNote: String
    val manual: String
    val manualNote: String
    /** Under the scan buttons while the month's scans are counted: "Bu oy yana 2 ta skan qoldi". */
    fun scansLeft(left: Int, premiumHint: Boolean): String
    val markersTitle: String
    val reportsTitle: String
    /** "12 ta ko'rsatkich · 2 tasi normadan tashqarida" */
    fun countLine(values: Int, flagged: Int): String
    fun flag(flag: LabFlag): String
    val range: String
    val noRange: String
    val disclaimer: String
    val askDoctor: String
    val photos: String
    fun photo(page: Int): String
    val note: String
    val scanned: String
    val edit: String
    val delete: String
    val deleteTitle: String
    val deleteBody: String
    val deleted: String
    val saved: String

    // ---- a marker's chart
    val history: String
    val latest: String
    /** Results in another unit than the latest are listed but not drawn. */
    val otherUnits: String
    val chartLabel: String

    // ---- the editor
    val newTitle: String
    val editTitle: String
    val reviewTitle: String
    val reviewNote: String
    val date: String
    val dateHint: String
    val place: String
    val placeHint: String
    val noteHint: String
    val rows: String
    val addRow: String
    val pickMarker: String
    val customMarker: String
    val name: String
    val value: String
    val valueHint: String
    val unit: String
    val low: String
    val high: String
    val removeRow: String
    val rowIncomplete: String
    val noRows: String
    fun group(key: String): String

    // ---- the scanner
    val scanTitle: String
    val frameHint: String
    val lightHint: String
    fun pages(count: Int, max: Int): String
    val addPage: String
    val read: String
    val reading: String
    val readingWait: String
    val notLab: String
    val failed: String
    val failedBody: String
    val gallery: String
    val shutter: String
    val removePage: String
    val outOfScans: String
    val outOfScansBody: String
    val upgrade: String

    // ---- in a consultation
    val attach: String
    val attachNote: String
    val pickTitle: String
    val pickEmpty: String
    val lastLine: String
}

object LabStringsUz : LabStrings {
    override val title = "Tahlil natijalari"
    override val emptyTitle = "Hali tahlil yo'q"
    override val emptyBody = "Laboratoriya varag'ini suratga oling yoki natijalarni qo'lda kiriting. Har bir ko'rsatkich vaqt bo'yicha grafikda ko'rinadi."
    override val scan = "Skanerlash"
    override val scanNote = "Varaqni suratga oling — raqamlarni AI o'qib beradi"
    override val manual = "Qo'lda kiritish"
    override val manualNote = "Natijalarni o'zingiz yozing"
    override fun scansLeft(left: Int, premiumHint: Boolean) =
        (if (left > 0) "Bu oy yana $left ta skan qoldi" else "Bu oygi skanlar tugadi") + if (premiumHint) " · Premium'da ko'proq" else ""
    override val markersTitle = "Ko'rsatkichlar"
    override val reportsTitle = "Tahlillar"
    override fun countLine(values: Int, flagged: Int) =
        "$values ta ko'rsatkich" + if (flagged > 0) " · $flagged tasi normadan tashqarida" else ""
    override fun flag(flag: LabFlag) = when (flag) {
        LabFlag.LOW -> "Past"
        LabFlag.NORMAL -> "Normada"
        LabFlag.HIGH -> "Yuqori"
    }
    override val range = "Norma"
    override val noRange = "Norma ko'rsatilmagan"
    override val disclaimer = "Bu tashxis emas. Past yoki yuqori belgisi laboratoriyangiz ko'rsatgan norma bo'yicha qo'yiladi. Natijalarni shifokoringiz bilan muhokama qiling."
    override val askDoctor = "Shifokorga ko'rsatish"
    override val photos = "Varaq rasmlari"
    override fun photo(page: Int) = "Varaq rasmi $page"
    override val note = "Izoh"
    override val scanned = "Rasmdan o'qilgan"
    override val edit = "Tahrirlash"
    override val delete = "O'chirish"
    override val deleteTitle = "Tahlil o'chirilsinmi?"
    override val deleteBody = "Natijalar va varaq rasmlari butunlay o'chadi. Shifokorga yuborilgan xabarda faqat matni qoladi."
    override val deleted = "Tahlil o'chirildi"
    override val saved = "Tahlil saqlandi"

    override val history = "Natijalar tarixi"
    override val latest = "Oxirgi natija"
    override val otherUnits = "Boshqa birlikdagi natijalar grafikka qo'shilmagan"
    override val chartLabel = "Natijalar grafigi"

    override val newTitle = "Yangi tahlil"
    override val editTitle = "Tahlilni tahrirlash"
    override val reviewTitle = "Natijalarni tekshiring"
    override val reviewNote = "AI varaqni o'qidi. Har bir raqamni varaq bilan solishtiring — xato bo'lsa, tuzating."
    override val date = "Tahlil sanasi"
    override val dateHint = "12.10.2026"
    override val place = "Laboratoriya"
    override val placeHint = "Masalan, Invitro"
    override val noteHint = "Masalan, och qoringa topshirildi"
    override val rows = "Natijalar"
    override val addRow = "Ko'rsatkich qo'shish"
    override val pickMarker = "Ko'rsatkichni tanlang"
    override val customMarker = "Ro'yxatda yo'q ko'rsatkich"
    override val name = "Nomi"
    override val value = "Natija"
    override val valueHint = "Son yoki matn"
    override val unit = "Birlik"
    override val low = "Norma, dan"
    override val high = "Norma, gacha"
    override val removeRow = "Qatorni o'chirish"
    override val rowIncomplete = "Nomi va natijasini yozing"
    override val noRows = "Kamida bitta natija qo'shing"
    override fun group(key: String) = when (key) {
        "blood" -> "Umumiy qon tahlili"
        "iron" -> "Temir va vitaminlar"
        "thyroid" -> "Qalqonsimon bez"
        "hormones" -> "Gormonlar"
        "metabolic" -> "Bioximiya"
        else -> "Boshqa"
    }

    override val scanTitle = "Tahlilni skanerlash"
    override val frameHint = "Varaqni kadrga to'liq sig'diring"
    override val lightHint = "Yorug' joyda, soyasiz va tekis suratga oling"
    override fun pages(count: Int, max: Int) = "$count / $max sahifa"
    override val addPage = "Yana sahifa"
    override val read = "O'qish"
    override val reading = "Varaq o'qilmoqda…"
    override val readingWait = "Odatda 10–30 soniya"
    override val notLab = "Bu tahlil varag'iga o'xshamaydi"
    override val failed = "Varaqni o'qib bo'lmadi"
    override val failedBody = "Qaytadan suratga oling yoki natijalarni qo'lda kiriting."
    override val gallery = "Galereya"
    override val shutter = "Suratga olish"
    override val removePage = "Sahifani olib tashlash"
    override val outOfScans = "Bu oygi bepul skanlar tugadi"
    override val outOfScansBody = "Natijalarni qo'lda kiritishingiz mumkin. Premium'da skanlar ko'proq."
    override val upgrade = "Premium"

    override val attach = "Tahlil natijalari"
    override val attachNote = "Tahlil varag'ingizni shifokorga yuboring"
    override val pickTitle = "Qaysi tahlilni yuborasiz?"
    override val pickEmpty = "Hali tahlil qo'shilmagan. Uni Profil → Tahlil natijalari bo'limida qo'shing."
    override val lastLine = "Tahlil natijalari"
}

object LabStringsRu : LabStrings {
    override val title = "Результаты анализов"
    override val emptyTitle = "Анализов пока нет"
    override val emptyBody = "Сфотографируйте бланк лаборатории или введите результаты вручную. Каждый показатель появится на графике во времени."
    override val scan = "Сканировать"
    override val scanNote = "Сфотографируйте бланк — цифры прочитает ИИ"
    override val manual = "Ввести вручную"
    override val manualNote = "Впишите результаты сами"
    override fun scansLeft(left: Int, premiumHint: Boolean) =
        (if (left > 0) "В этом месяце осталось сканов: $left" else "Сканы на этот месяц закончились") + if (premiumHint) " · в Premium больше" else ""
    override val markersTitle = "Показатели"
    override val reportsTitle = "Анализы"
    override fun countLine(values: Int, flagged: Int) =
        "Показателей: $values" + if (flagged > 0) " · вне нормы: $flagged" else ""
    override fun flag(flag: LabFlag) = when (flag) {
        LabFlag.LOW -> "Ниже нормы"
        LabFlag.NORMAL -> "В норме"
        LabFlag.HIGH -> "Выше нормы"
    }
    override val range = "Референс"
    override val noRange = "Референс не указан"
    override val disclaimer = "Это не диагноз. Отметка «ниже» или «выше нормы» ставится по референсу вашей лаборатории. Обсудите результаты с врачом."
    override val askDoctor = "Показать врачу"
    override val photos = "Фото бланка"
    override fun photo(page: Int) = "Фото бланка $page"
    override val note = "Заметка"
    override val scanned = "Прочитано с фото"
    override val edit = "Изменить"
    override val delete = "Удалить"
    override val deleteTitle = "Удалить анализ?"
    override val deleteBody = "Результаты и фото бланка удалятся навсегда. В сообщении врачу останется только текст."
    override val deleted = "Анализ удалён"
    override val saved = "Анализ сохранён"

    override val history = "История результатов"
    override val latest = "Последний результат"
    override val otherUnits = "Результаты в других единицах на график не попали"
    override val chartLabel = "График результатов"

    override val newTitle = "Новый анализ"
    override val editTitle = "Изменить анализ"
    override val reviewTitle = "Проверьте результаты"
    override val reviewNote = "ИИ прочитал бланк. Сверьте каждую цифру с бланком и исправьте, если что-то не так."
    override val date = "Дата анализа"
    override val dateHint = "12.10.2026"
    override val place = "Лаборатория"
    override val placeHint = "Например, Invitro"
    override val noteHint = "Например, сдавала натощак"
    override val rows = "Результаты"
    override val addRow = "Добавить показатель"
    override val pickMarker = "Выберите показатель"
    override val customMarker = "Показателя нет в списке"
    override val name = "Название"
    override val value = "Результат"
    override val valueHint = "Число или текст"
    override val unit = "Единица"
    override val low = "Норма, от"
    override val high = "Норма, до"
    override val removeRow = "Удалить строку"
    override val rowIncomplete = "Укажите название и результат"
    override val noRows = "Добавьте хотя бы один результат"
    override fun group(key: String) = when (key) {
        "blood" -> "Общий анализ крови"
        "iron" -> "Железо и витамины"
        "thyroid" -> "Щитовидная железа"
        "hormones" -> "Гормоны"
        "metabolic" -> "Биохимия"
        else -> "Другое"
    }

    override val scanTitle = "Сканировать анализ"
    override val frameHint = "Поместите бланк в кадр целиком"
    override val lightHint = "Снимайте при хорошем свете, без тени и ровно"
    override fun pages(count: Int, max: Int) = "Страниц: $count из $max"
    override val addPage = "Ещё страница"
    override val read = "Прочитать"
    override val reading = "Читаем бланк…"
    override val readingWait = "Обычно 10–30 секунд"
    override val notLab = "Это не похоже на бланк анализа"
    override val failed = "Не удалось прочитать бланк"
    override val failedBody = "Сфотографируйте ещё раз или введите результаты вручную."
    override val gallery = "Галерея"
    override val shutter = "Сфотографировать"
    override val removePage = "Убрать страницу"
    override val outOfScans = "Бесплатные сканы в этом месяце закончились"
    override val outOfScansBody = "Результаты можно ввести вручную. В Premium сканов больше."
    override val upgrade = "Premium"

    override val attach = "Результаты анализов"
    override val attachNote = "Отправьте врачу бланк анализа"
    override val pickTitle = "Какой анализ отправить?"
    override val pickEmpty = "Анализов пока нет. Добавьте их в Профиль → Результаты анализов."
    override val lastLine = "Результаты анализов"
}

object LabStringsEn : LabStrings {
    override val title = "Lab results"
    override val emptyTitle = "No lab results yet"
    override val emptyBody = "Take a photo of the lab sheet or type the results in. Each marker shows up on a chart over time."
    override val scan = "Scan"
    override val scanNote = "Photograph the sheet — AI reads the numbers"
    override val manual = "Type in"
    override val manualNote = "Enter the results yourself"
    override fun scansLeft(left: Int, premiumHint: Boolean) =
        (if (left > 0) "$left ${if (left == 1) "scan" else "scans"} left this month" else "No scans left this month") + if (premiumHint) " · more with Premium" else ""
    override val markersTitle = "Markers"
    override val reportsTitle = "Sheets"
    override fun countLine(values: Int, flagged: Int) =
        "$values markers" + if (flagged > 0) " · $flagged out of range" else ""
    override fun flag(flag: LabFlag) = when (flag) {
        LabFlag.LOW -> "Low"
        LabFlag.NORMAL -> "In range"
        LabFlag.HIGH -> "High"
    }
    override val range = "Range"
    override val noRange = "No range printed"
    override val disclaimer = "This is not a diagnosis. Low or high is marked against the range your laboratory printed. Talk the results through with your doctor."
    override val askDoctor = "Show a doctor"
    override val photos = "Sheet photos"
    override fun photo(page: Int) = "Sheet photo $page"
    override val note = "Note"
    override val scanned = "Read from a photo"
    override val edit = "Edit"
    override val delete = "Delete"
    override val deleteTitle = "Delete this sheet?"
    override val deleteBody = "The results and the photos are deleted for good. A message to your doctor keeps only its text."
    override val deleted = "Sheet deleted"
    override val saved = "Sheet saved"

    override val history = "Results over time"
    override val latest = "Latest result"
    override val otherUnits = "Results in other units are not drawn"
    override val chartLabel = "Results chart"

    override val newTitle = "New lab sheet"
    override val editTitle = "Edit lab sheet"
    override val reviewTitle = "Check the results"
    override val reviewNote = "AI read the sheet. Compare every number with the sheet and correct anything it got wrong."
    override val date = "Test date"
    override val dateHint = "12.10.2026"
    override val place = "Laboratory"
    override val placeHint = "For example, Invitro"
    override val noteHint = "For example, taken fasting"
    override val rows = "Results"
    override val addRow = "Add a marker"
    override val pickMarker = "Choose a marker"
    override val customMarker = "Not on the list"
    override val name = "Name"
    override val value = "Result"
    override val valueHint = "A number or text"
    override val unit = "Unit"
    override val low = "Range from"
    override val high = "Range to"
    override val removeRow = "Remove row"
    override val rowIncomplete = "Enter a name and a result"
    override val noRows = "Add at least one result"
    override fun group(key: String) = when (key) {
        "blood" -> "Blood count"
        "iron" -> "Iron and vitamins"
        "thyroid" -> "Thyroid"
        "hormones" -> "Hormones"
        "metabolic" -> "Biochemistry"
        else -> "Other"
    }

    override val scanTitle = "Scan a lab sheet"
    override val frameHint = "Fit the whole sheet in the frame"
    override val lightHint = "In good light, without shadow, held flat"
    override fun pages(count: Int, max: Int) = "$count of $max pages"
    override val addPage = "Another page"
    override val read = "Read"
    override val reading = "Reading the sheet…"
    override val readingWait = "Usually 10–30 seconds"
    override val notLab = "This does not look like a lab sheet"
    override val failed = "The sheet could not be read"
    override val failedBody = "Take the photo again or type the results in."
    override val gallery = "Gallery"
    override val shutter = "Take photo"
    override val removePage = "Remove page"
    override val outOfScans = "This month's free scans are used up"
    override val outOfScansBody = "You can still type the results in. Premium has more scans."
    override val upgrade = "Premium"

    override val attach = "Lab results"
    override val attachNote = "Send a lab sheet to your doctor"
    override val pickTitle = "Which sheet do you want to send?"
    override val pickEmpty = "No lab sheets yet. Add one in Profile → Lab results."
    override val lastLine = "Lab results"
}
