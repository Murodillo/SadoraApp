package uz.sadora.doctor.i18n

import uz.sadora.contract.FoodRelation
import uz.sadora.contract.PrescriptionForm
import uz.sadora.contract.PrescriptionItem
import uz.sadora.contract.ScheduleKind

/**
 * Writing a prescription, its card in the thread, and the patient's history of them.
 *
 * What the doctor types — names, doses, notes — is sent as she wrote it; only the
 * labels and the summary line around it are worded here.
 */
interface PrescriptionStrings {
    // ---- the "+" menu
    val attachTitle: String
    val attach: String
    val photo: String
    val prescription: String

    // ---- the writer
    val writerTitle: String
    fun medicine(number: Int): String
    /** Under a folded medicine that has no name yet. */
    val unnamed: String
    val filled: String
    val notFilled: String
    /** Under an empty field the prescription needs. */
    val required: String
    val expand: String
    val collapse: String
    val addMedicine: String
    val remove: String
    val name: String
    val namePlaceholder: String
    val formLabel: String
    fun form(form: PrescriptionForm): String
    /** The unit the form suggests: "tabletka", "ml"; empty where none fits. */
    fun defaultUnit(form: PrescriptionForm): String
    val dose: String
    val unit: String
    val when_: String
    fun timesPerDay(count: Int): String
    val everyDay: String
    fun everyDays(days: Int): String
    val earlier: String
    val later: String
    val food: String
    fun food(relation: FoodRelation): String
    val startDay: String
    val days: String
    val ongoing: String
    val itemNote: String
    val note: String
    val notePlaceholder: String
    val copyPrevious: String
    val send: String
    val sending: String
    val sent: String
    val missing: String

    // ---- the card
    val title: String
    val disclaimer: String
    /** "1 tabletka · kuniga 2 marta: 09:00, 21:00 · ovqatdan keyin · 5 kun". */
    fun summary(item: PrescriptionItem): String
    val added: String
    val notAdded: String
    val cancelled: String
    fun cancelReason(reason: String): String
    val cancel: String
    val cancelTitle: String
    val cancelBody: String
    val reasonHint: String
    val confirmCancel: String
    val cancelledToast: String

    // ---- the patient's page
    val history: String
    val historyEmpty: String
}

object PrescriptionStringsUz : PrescriptionStrings {
    override val attachTitle = "Biriktirish"
    override val attach = "Biriktirish"
    override val photo = "Rasm"
    override val prescription = "Retsept"

    override val writerTitle = "Retsept yozish"
    override fun medicine(number: Int) = "$number-dori"
    override val unnamed = "Nomi kiritilmagan"
    override val filled = "Tayyor"
    override val notFilled = "To'ldirilmagan"
    override val required = "To'ldirilishi shart"
    override val expand = "Ochish"
    override val collapse = "Yig'ish"
    override val addMedicine = "Dori qo'shish"
    override val remove = "Olib tashlash"
    override val name = "Dori nomi"
    override val namePlaceholder = "Masalan, Amoksitsillin"
    override val formLabel = "Shakli"
    override fun form(form: PrescriptionForm) = when (form) {
        PrescriptionForm.TABLET -> "Tabletka"
        PrescriptionForm.CAPSULE -> "Kapsula"
        PrescriptionForm.SYRUP -> "Sirop"
        PrescriptionForm.DROPS -> "Tomchi"
        PrescriptionForm.INJECTION -> "Ukol"
        PrescriptionForm.OINTMENT -> "Surtma"
        PrescriptionForm.POWDER -> "Kukun"
        PrescriptionForm.OTHER -> "Boshqa"
    }
    override fun defaultUnit(form: PrescriptionForm) = when (form) {
        PrescriptionForm.TABLET -> "tabletka"
        PrescriptionForm.CAPSULE -> "kapsula"
        PrescriptionForm.SYRUP, PrescriptionForm.INJECTION -> "ml"
        PrescriptionForm.DROPS -> "tomchi"
        PrescriptionForm.POWDER -> "paket"
        PrescriptionForm.OINTMENT, PrescriptionForm.OTHER -> ""
    }
    override val dose = "Doza"
    override val unit = "Birligi"
    override val when_ = "Qachon ichiladi"
    override fun timesPerDay(count: Int) = "Kuniga $count marta"
    override val everyDay = "Har kuni"
    override fun everyDays(days: Int) = "Har $days kunda"
    override val earlier = "erta"
    override val later = "kech"
    override val food = "Ovqatga nisbatan"
    override fun food(relation: FoodRelation) = when (relation) {
        FoodRelation.BEFORE -> "Ovqatdan oldin"
        FoodRelation.WITH -> "Ovqat bilan"
        FoodRelation.AFTER -> "Ovqatdan keyin"
        FoodRelation.ANY -> "Farqi yo'q"
    }
    override val startDay = "Nechanchi kundan"
    override val days = "Necha kun"
    override val ongoing = "Doimiy"
    override val itemNote = "Izoh (ixtiyoriy)"
    override val note = "Umumiy izoh"
    override val notePlaceholder = "Ko'p suv iching, 2 haftadan keyin qayta ko'rinish…"
    override val copyPrevious = "Oldingi retseptdan nusxa"
    override val send = "Retseptni yuborish"
    override val sending = "Yuborilmoqda…"
    override val sent = "Retsept yuborildi"
    override val missing = "Nomi, doza, ovqatga nisbatan va davomiylikni to'ldiring"

    override val title = "Retsept"
    override val disclaimer = "Maslahat retsepti, rasmiy retsept emas"
    override fun summary(item: PrescriptionItem) = listOfNotNull(
        listOfNotNull(item.dose, item.unit).joinToString(" "),
        schedule(item, "Kuniga ${item.schedule.times.size} marta", "har ${item.schedule.intervalDays} kunda"),
        food(item.foodRelation).lowercase(),
        (if (item.startDay > 1) "${item.startDay}-kundan " else "") + (item.days?.let { "$it kun" } ?: "doimiy"),
    ).joinToString(" · ")
    override val added = "Bemor tabletkalariga qo'shdi"
    override val notAdded = "Bemor hali qo'shmagan"
    override val cancelled = "Bekor qilingan"
    override fun cancelReason(reason: String) = "Sabab: $reason"
    override val cancel = "Bekor qilish"
    override val cancelTitle = "Retseptni bekor qilish"
    override val cancelBody = "Bemor xabar oladi, retseptdan qo'shgan dorilari to'xtatiladi."
    override val reasonHint = "Sababi, masalan: doza xato yozildi"
    override val confirmCancel = "Bekor qilish"
    override val cancelledToast = "Retsept bekor qilindi"

    override val history = "Retseptlar"
    override val historyEmpty = "Bu bemorga hali retsept yozilmagan."
}

object PrescriptionStringsRu : PrescriptionStrings {
    override val attachTitle = "Прикрепить"
    override val attach = "Прикрепить"
    override val photo = "Фото"
    override val prescription = "Рецепт"

    override val writerTitle = "Выписать рецепт"
    override fun medicine(number: Int) = "Препарат $number"
    override val unnamed = "Без названия"
    override val filled = "Готово"
    override val notFilled = "Не заполнено"
    override val required = "Обязательное поле"
    override val expand = "Развернуть"
    override val collapse = "Свернуть"
    override val addMedicine = "Добавить препарат"
    override val remove = "Убрать"
    override val name = "Название"
    override val namePlaceholder = "Например, Амоксициллин"
    override val formLabel = "Форма"
    override fun form(form: PrescriptionForm) = when (form) {
        PrescriptionForm.TABLET -> "Таблетки"
        PrescriptionForm.CAPSULE -> "Капсулы"
        PrescriptionForm.SYRUP -> "Сироп"
        PrescriptionForm.DROPS -> "Капли"
        PrescriptionForm.INJECTION -> "Инъекции"
        PrescriptionForm.OINTMENT -> "Мазь"
        PrescriptionForm.POWDER -> "Порошок"
        PrescriptionForm.OTHER -> "Другое"
    }
    override fun defaultUnit(form: PrescriptionForm) = when (form) {
        PrescriptionForm.TABLET -> "табл."
        PrescriptionForm.CAPSULE -> "капс."
        PrescriptionForm.SYRUP, PrescriptionForm.INJECTION -> "мл"
        PrescriptionForm.DROPS -> "кап."
        PrescriptionForm.POWDER -> "пакет"
        PrescriptionForm.OINTMENT, PrescriptionForm.OTHER -> ""
    }
    override val dose = "Доза"
    override val unit = "Единица"
    override val when_ = "Когда принимать"
    override fun timesPerDay(count: Int) = "$count раз${if (count in 2..4) "а" else ""} в день"
    override val everyDay = "Каждый день"
    override fun everyDays(days: Int) = "Раз в $days дн."
    override val earlier = "раньше"
    override val later = "позже"
    override val food = "Относительно еды"
    override fun food(relation: FoodRelation) = when (relation) {
        FoodRelation.BEFORE -> "До еды"
        FoodRelation.WITH -> "Во время еды"
        FoodRelation.AFTER -> "После еды"
        FoodRelation.ANY -> "Неважно"
    }
    override val startDay = "С какого дня"
    override val days = "Сколько дней"
    override val ongoing = "Постоянно"
    override val itemNote = "Примечание (необязательно)"
    override val note = "Общее примечание"
    override val notePlaceholder = "Пить больше воды, повторный приём через 2 недели…"
    override val copyPrevious = "Скопировать прошлый рецепт"
    override val send = "Отправить рецепт"
    override val sending = "Отправляем…"
    override val sent = "Рецепт отправлен"
    override val missing = "Заполните название, дозу, приём относительно еды и длительность"

    override val title = "Рецепт"
    override val disclaimer = "Рекомендация врача, не официальный рецепт"
    override fun summary(item: PrescriptionItem) = listOfNotNull(
        listOfNotNull(item.dose, item.unit).joinToString(" "),
        schedule(item, timesPerDay(item.schedule.times.size), "раз в ${item.schedule.intervalDays} дн."),
        food(item.foodRelation).lowercase(),
        (if (item.startDay > 1) "с ${item.startDay}-го дня " else "") + (item.days?.let { "$it дн." } ?: "постоянно"),
    ).joinToString(" · ")
    override val added = "Пациентка добавила в лекарства"
    override val notAdded = "Пациентка ещё не добавила"
    override val cancelled = "Отменён"
    override fun cancelReason(reason: String) = "Причина: $reason"
    override val cancel = "Отменить"
    override val cancelTitle = "Отменить рецепт"
    override val cancelBody = "Пациентка получит уведомление, добавленные из рецепта лекарства будут остановлены."
    override val reasonHint = "Причина, например: ошибка в дозе"
    override val confirmCancel = "Отменить рецепт"
    override val cancelledToast = "Рецепт отменён"

    override val history = "Рецепты"
    override val historyEmpty = "Этой пациентке рецептов пока не выписывали."
}

object PrescriptionStringsEn : PrescriptionStrings {
    override val attachTitle = "Attach"
    override val attach = "Attach"
    override val photo = "Photo"
    override val prescription = "Prescription"

    override val writerTitle = "Write a prescription"
    override fun medicine(number: Int) = "Medicine $number"
    override val unnamed = "No name yet"
    override val filled = "Ready"
    override val notFilled = "Not filled in"
    override val required = "Required"
    override val expand = "Expand"
    override val collapse = "Collapse"
    override val addMedicine = "Add a medicine"
    override val remove = "Remove"
    override val name = "Name"
    override val namePlaceholder = "e.g. Amoxicillin"
    override val formLabel = "Form"
    override fun form(form: PrescriptionForm) = when (form) {
        PrescriptionForm.TABLET -> "Tablet"
        PrescriptionForm.CAPSULE -> "Capsule"
        PrescriptionForm.SYRUP -> "Syrup"
        PrescriptionForm.DROPS -> "Drops"
        PrescriptionForm.INJECTION -> "Injection"
        PrescriptionForm.OINTMENT -> "Ointment"
        PrescriptionForm.POWDER -> "Powder"
        PrescriptionForm.OTHER -> "Other"
    }
    override fun defaultUnit(form: PrescriptionForm) = when (form) {
        PrescriptionForm.TABLET -> "tablet"
        PrescriptionForm.CAPSULE -> "capsule"
        PrescriptionForm.SYRUP, PrescriptionForm.INJECTION -> "ml"
        PrescriptionForm.DROPS -> "drops"
        PrescriptionForm.POWDER -> "sachet"
        PrescriptionForm.OINTMENT, PrescriptionForm.OTHER -> ""
    }
    override val dose = "Dose"
    override val unit = "Unit"
    override val when_ = "When to take"
    override fun timesPerDay(count: Int) = if (count == 1) "Once a day" else "$count times a day"
    override val everyDay = "Every day"
    override fun everyDays(days: Int) = "Every $days days"
    override val earlier = "earlier"
    override val later = "later"
    override val food = "With food"
    override fun food(relation: FoodRelation) = when (relation) {
        FoodRelation.BEFORE -> "Before food"
        FoodRelation.WITH -> "With food"
        FoodRelation.AFTER -> "After food"
        FoodRelation.ANY -> "Doesn't matter"
    }
    override val startDay = "From day"
    override val days = "For days"
    override val ongoing = "Ongoing"
    override val itemNote = "Note (optional)"
    override val note = "General note"
    override val notePlaceholder = "Drink plenty of water, follow-up in 2 weeks…"
    override val copyPrevious = "Copy the last prescription"
    override val send = "Send prescription"
    override val sending = "Sending…"
    override val sent = "Prescription sent"
    override val missing = "Fill in the name, dose, food and duration"

    override val title = "Prescription"
    override val disclaimer = "Doctor's advice, not an official prescription"
    override fun summary(item: PrescriptionItem) = listOfNotNull(
        listOfNotNull(item.dose, item.unit).joinToString(" "),
        schedule(item, timesPerDay(item.schedule.times.size), "every ${item.schedule.intervalDays} days"),
        food(item.foodRelation).lowercase(),
        (if (item.startDay > 1) "from day ${item.startDay}, " else "") + (item.days?.let { "$it days" } ?: "ongoing"),
    ).joinToString(" · ")
    override val added = "Added to her medications"
    override val notAdded = "Not added yet"
    override val cancelled = "Cancelled"
    override fun cancelReason(reason: String) = "Reason: $reason"
    override val cancel = "Cancel"
    override val cancelTitle = "Cancel the prescription"
    override val cancelBody = "She is notified, and the medicines she added from it stop."
    override val reasonHint = "Why, e.g. wrong dose"
    override val confirmCancel = "Cancel prescription"
    override val cancelledToast = "Prescription cancelled"

    override val history = "Prescriptions"
    override val historyEmpty = "No prescriptions for her yet."
}

/** "Kuniga 2 marta: 09:00, 21:00", or the interval's own words before the times. */
private fun schedule(item: PrescriptionItem, daily: String, interval: String): String {
    val times = item.schedule.times.joinToString(", ") { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
    val head = if (item.schedule.kind == ScheduleKind.INTERVAL) "$interval, ${daily.lowercase()}" else daily.lowercase()
    return "$head: $times"
}
