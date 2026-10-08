package uz.sadora.app.i18n

import uz.sadora.contract.FoodRelation
import uz.sadora.contract.PrescriptionItem
import uz.sadora.contract.ScheduleKind

/**
 * A doctor's prescription as she receives it: the card in the thread, the sheet that
 * adds it to her medications, and the list of them on the medications screen.
 *
 * The medicine names, doses and notes are the doctor's own words and are shown as sent.
 */
interface PrescriptionStrings {
    val title: String
    val disclaimer: String
    /** "Dr. Malika Karimova · 9-oktabr" */
    fun byline(doctor: String, date: String): String
    /** "1 tabletka · kuniga 2 marta: 09:00, 21:00 · ovqatdan keyin · 5 kun" */
    fun summary(item: PrescriptionItem): String
    fun food(relation: FoodRelation): String

    val addToPills: String
    val added: String
    val cancelled: String
    fun cancelReason(reason: String): String
    val shareImage: String
    val lastLine: String

    // ---- the add sheet
    val addTitle: String
    val addBody: String
    val startOn: String
    val today: String
    val tomorrow: String
    val earlier: String
    val later: String
    val nothingChosen: String
    val adding: String
    val addConfirm: String
    val addedToast: String

    // ---- on the medications screen
    val fromDoctor: String
    /** "Dr. Malika Karimova retsepti" on a course. */
    fun prescribedBy(doctor: String): String
    val lockedNote: String
    val listTitle: String
}

object PrescriptionStringsUz : PrescriptionStrings {
    override val title = "Retsept"
    override val disclaimer = "Maslahat retsepti, rasmiy retsept emas"
    override fun byline(doctor: String, date: String) = "$doctor · $date"
    override fun summary(item: PrescriptionItem) = listOfNotNull(
        listOfNotNull(item.dose, item.unit).joinToString(" "),
        schedule(item, "kuniga ${item.schedule.times.size} marta", "har ${item.schedule.intervalDays} kunda"),
        food(item.foodRelation).lowercase(),
        (if (item.startDay > 1) "${item.startDay}-kundan " else "") + (item.days?.let { "$it kun" } ?: "doimiy"),
    ).joinToString(" · ")
    override fun food(relation: FoodRelation) = when (relation) {
        FoodRelation.BEFORE -> "Ovqatdan oldin"
        FoodRelation.WITH -> "Ovqat bilan"
        FoodRelation.AFTER -> "Ovqatdan keyin"
        FoodRelation.ANY -> "Ovqatdan qat'i nazar"
    }
    override val addToPills = "Tabletkalarimga qo'shish"
    override val added = "Tabletkalaringizga qo'shilgan"
    override val cancelled = "Shifokor bekor qilgan"
    override fun cancelReason(reason: String) = "Sabab: $reason"
    override val shareImage = "Rasm sifatida ulashish"
    override val lastLine = "Retsept"

    override val addTitle = "Tabletkalarimga qo'shish"
    override val addBody = "Kerakli dorilarni belgilang va ichish vaqtini kuningizga moslang. Doza va davomiylikni shifokor belgilagan."
    override val startOn = "Qachondan boshlaysiz"
    override val today = "Bugun"
    override val tomorrow = "Ertaga"
    override val earlier = "erta"
    override val later = "kech"
    override val nothingChosen = "Kamida bitta dorini belgilang"
    override val adding = "Qo'shilmoqda…"
    override val addConfirm = "Qo'shish va eslatmalarni yoqish"
    override val addedToast = "Dorilar tabletkalaringizga qo'shildi"

    override val fromDoctor = "Shifokor retseptlari"
    override fun prescribedBy(doctor: String) = "$doctor retsepti"
    override val lockedNote = "Nomi, dozasi va davomiyligini shifokor belgilagan. Ichish vaqti va eslatmalarni o'zgartirishingiz mumkin."
    override val listTitle = "Shifokor retseptlari"
}

object PrescriptionStringsRu : PrescriptionStrings {
    override val title = "Рецепт"
    override val disclaimer = "Рекомендация врача, не официальный рецепт"
    override fun byline(doctor: String, date: String) = "$doctor · $date"
    override fun summary(item: PrescriptionItem) = listOfNotNull(
        listOfNotNull(item.dose, item.unit).joinToString(" "),
        schedule(item, timesPerDay(item.schedule.times.size), "раз в ${item.schedule.intervalDays} дн."),
        food(item.foodRelation).lowercase(),
        (if (item.startDay > 1) "с ${item.startDay}-го дня " else "") + (item.days?.let { "$it дн." } ?: "постоянно"),
    ).joinToString(" · ")
    private fun timesPerDay(count: Int) = "$count раз${if (count in 2..4) "а" else ""} в день"
    override fun food(relation: FoodRelation) = when (relation) {
        FoodRelation.BEFORE -> "До еды"
        FoodRelation.WITH -> "Во время еды"
        FoodRelation.AFTER -> "После еды"
        FoodRelation.ANY -> "Независимо от еды"
    }
    override val addToPills = "Добавить в мои лекарства"
    override val added = "Добавлено в ваши лекарства"
    override val cancelled = "Врач отменил рецепт"
    override fun cancelReason(reason: String) = "Причина: $reason"
    override val shareImage = "Поделиться картинкой"
    override val lastLine = "Рецепт"

    override val addTitle = "Добавить в мои лекарства"
    override val addBody = "Отметьте нужные препараты и подстройте время приёма под свой день. Дозу и длительность назначил врач."
    override val startOn = "Когда начинаете"
    override val today = "Сегодня"
    override val tomorrow = "Завтра"
    override val earlier = "раньше"
    override val later = "позже"
    override val nothingChosen = "Отметьте хотя бы один препарат"
    override val adding = "Добавляем…"
    override val addConfirm = "Добавить и включить напоминания"
    override val addedToast = "Препараты добавлены в ваши лекарства"

    override val fromDoctor = "Рецепты врачей"
    override fun prescribedBy(doctor: String) = "Рецепт: $doctor"
    override val lockedNote = "Название, дозу и длительность назначил врач. Время приёма и напоминания можно менять."
    override val listTitle = "Рецепты врачей"
}

object PrescriptionStringsEn : PrescriptionStrings {
    override val title = "Prescription"
    override val disclaimer = "Doctor's advice, not an official prescription"
    override fun byline(doctor: String, date: String) = "$doctor · $date"
    override fun summary(item: PrescriptionItem) = listOfNotNull(
        listOfNotNull(item.dose, item.unit).joinToString(" "),
        schedule(item, if (item.schedule.times.size == 1) "once a day" else "${item.schedule.times.size} times a day", "every ${item.schedule.intervalDays} days"),
        food(item.foodRelation).lowercase(),
        (if (item.startDay > 1) "from day ${item.startDay}, " else "") + (item.days?.let { "$it days" } ?: "ongoing"),
    ).joinToString(" · ")
    override fun food(relation: FoodRelation) = when (relation) {
        FoodRelation.BEFORE -> "Before food"
        FoodRelation.WITH -> "With food"
        FoodRelation.AFTER -> "After food"
        FoodRelation.ANY -> "With or without food"
    }
    override val addToPills = "Add to my medications"
    override val added = "Added to your medications"
    override val cancelled = "Cancelled by the doctor"
    override fun cancelReason(reason: String) = "Reason: $reason"
    override val shareImage = "Share as an image"
    override val lastLine = "Prescription"

    override val addTitle = "Add to my medications"
    override val addBody = "Tick the medicines you need and fit the times to your day. The dose and duration are the doctor's."
    override val startOn = "When do you start"
    override val today = "Today"
    override val tomorrow = "Tomorrow"
    override val earlier = "earlier"
    override val later = "later"
    override val nothingChosen = "Tick at least one medicine"
    override val adding = "Adding…"
    override val addConfirm = "Add and turn on reminders"
    override val addedToast = "Added to your medications"

    override val fromDoctor = "Doctors' prescriptions"
    override fun prescribedBy(doctor: String) = "Prescribed by $doctor"
    override val lockedNote = "The name, dose and duration are the doctor's. You can change the times and reminders."
    override val listTitle = "Doctors' prescriptions"
}

/** "kuniga 2 marta: 09:00, 21:00", the interval's words first when there is one. */
private fun schedule(item: PrescriptionItem, daily: String, interval: String): String {
    val times = item.schedule.times.joinToString(", ") { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
    val head = if (item.schedule.kind == ScheduleKind.INTERVAL) "$interval, $daily" else daily
    return "$head: $times"
}
