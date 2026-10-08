package uz.sadora.doctor.data

import kotlinx.datetime.LocalTime
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.Limits
import uz.sadora.contract.MedicationSchedule
import uz.sadora.contract.Prescription
import uz.sadora.contract.PrescriptionForm
import uz.sadora.contract.PrescriptionItem
import uz.sadora.contract.ScheduleKind
import uz.sadora.contract.SendPrescriptionRequest

/**
 * One medicine on the prescription she is writing, as the form holds it.
 *
 * The times are minutes of the day, set from a preset for "kuniga N marta" and then
 * moved half an hour a tap. Food relation starts unset on purpose: it is required, and a
 * default would let it go out unread.
 */
data class ItemDraft(
    /** Which card this is on the form, kept while others are added or removed. */
    val key: Int = 0,
    val name: String = "",
    val form: PrescriptionForm = PrescriptionForm.TABLET,
    val dose: String = "",
    /** Null while she has not typed one: the form's own word ("tabletka") fills in. */
    val unit: String? = null,
    val minutes: List<Int> = presetMinutes(2),
    /** Null is every day; a number is every that many days. */
    val everyDays: Int? = null,
    val food: FoodRelation? = null,
    val startDay: Int = 1,
    /** Digits as typed; empty with [ongoing] off is not finished. */
    val days: String = "",
    val ongoing: Boolean = false,
    val note: String = "",
) {
    val timesPerDay: Int get() = minutes.size

    /** Her word for the unit, else the form's. */
    fun unitOr(defaultUnit: String): String = unit ?: defaultUnit

    fun withTimesPerDay(count: Int): ItemDraft = copy(minutes = presetMinutes(count))

    /** Moves one time by [steps] half hours, keeping it inside the day and apart from the others. */
    fun stepTime(index: Int, steps: Int): ItemDraft {
        val moved = (minutes[index] + steps * TIME_STEP).coerceIn(0, DAY_MINUTES - TIME_STEP)
        if (minutes.withIndex().any { (other, value) -> other != index && value == moved }) return this
        return copy(minutes = minutes.toMutableList().also { it[index] = moved })
    }

    fun problems(): Set<ItemProblem> = buildSet {
        if (name.isBlank()) add(ItemProblem.Name)
        if (dose.isBlank()) add(ItemProblem.Dose)
        if (food == null) add(ItemProblem.Food)
        if (!ongoing && days.toIntOrNull()?.takeIf { it in 1..Limits.PRESCRIPTION_DAYS_MAX } == null) add(ItemProblem.Days)
    }

    fun toItem(defaultUnit: String): PrescriptionItem = PrescriptionItem(
        name = name.trim(),
        form = form,
        dose = dose.trim(),
        unit = unitOr(defaultUnit).trim().ifEmpty { null },
        schedule = MedicationSchedule(
            kind = if (everyDays == null) ScheduleKind.DAILY else ScheduleKind.INTERVAL,
            times = minutes.sorted().map { LocalTime(it / 60, it % 60) },
            intervalDays = everyDays,
        ),
        foodRelation = food ?: FoodRelation.ANY,
        startDay = startDay,
        days = if (ongoing) null else days.toIntOrNull(),
        note = note.trim().ifEmpty { null },
    )

    companion object {
        /** A copy of an item she sent before, to send again. */
        fun from(item: PrescriptionItem, key: Int = 0): ItemDraft = ItemDraft(
            key = key,
            name = item.name,
            form = item.form,
            dose = item.dose,
            unit = item.unit.orEmpty(),
            minutes = item.schedule.times.map { it.hour * 60 + it.minute },
            everyDays = item.schedule.intervalDays.takeIf { item.schedule.kind == ScheduleKind.INTERVAL },
            food = item.foodRelation,
            startDay = item.startDay,
            days = item.days?.toString().orEmpty(),
            ongoing = item.days == null,
            note = item.note.orEmpty(),
        )
    }
}

enum class ItemProblem { Name, Dose, Food, Days }

/** The whole prescription: its medicines and the note under them. */
data class PrescriptionDraft(
    val items: List<ItemDraft> = listOf(ItemDraft()),
    val note: String = "",
) {
    val canAddItem: Boolean get() = items.size < Limits.PRESCRIPTION_ITEMS_MAX
    val ready: Boolean get() = items.isNotEmpty() && items.all { it.problems().isEmpty() }

    fun update(index: Int, change: (ItemDraft) -> ItemDraft): PrescriptionDraft =
        copy(items = items.toMutableList().also { it[index] = change(it[index]) })

    /** A new medicine, from day 1: most are taken together, and a sequence is one field away. */
    fun addItem(): PrescriptionDraft =
        if (canAddItem) copy(items = items + ItemDraft(key = (items.maxOfOrNull { it.key } ?: -1) + 1)) else this

    /** The key of the medicine just added: the one the form opens. */
    val lastKey: Int get() = items.last().key

    fun removeItem(index: Int): PrescriptionDraft =
        if (items.size <= 1) this else copy(items = items.filterIndexed { i, _ -> i != index })

    fun toRequest(defaultUnit: (PrescriptionForm) -> String) = SendPrescriptionRequest(
        items = items.map { it.toItem(defaultUnit(it.form)) },
        note = note.trim().ifEmpty { null },
    )

    companion object {
        fun from(prescription: Prescription) = PrescriptionDraft(
            items = prescription.items.mapIndexed { index, item -> ItemDraft.from(item, key = index) },
            note = prescription.note.orEmpty(),
        )
    }
}

/** The usual hours for N doses a day; each can be moved after. */
fun presetMinutes(count: Int): List<Int> = when (count) {
    1 -> listOf(9 * 60)
    2 -> listOf(9 * 60, 21 * 60)
    3 -> listOf(8 * 60, 14 * 60, 20 * 60)
    else -> listOf(8 * 60, 12 * 60, 17 * 60, 21 * 60)
}

/** Digits only, at most three: a day count or a start day. */
fun acceptDayDigits(raw: String): String = raw.filter(Char::isDigit).take(3)

const val TIME_STEP = 30
private const val DAY_MINUTES = 24 * 60
