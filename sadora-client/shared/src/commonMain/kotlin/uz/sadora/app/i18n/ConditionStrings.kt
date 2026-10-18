package uz.sadora.app.i18n

import uz.sadora.contract.CycleAttention
import uz.sadora.contract.PainArea
import uz.sadora.contract.PainKind

/**
 * "Holatlarim": PCOS and endometriosis, the pain diary, the report, and the cycle page's
 * range and notices. The conditions' own descriptions come from the server.
 *
 * Every sentence that touches a symptom ends at the doctor: the app records and shows,
 * it never reads a meaning into her numbers.
 */
interface ConditionStrings {
    val title: String
    val intro: String
    val none: String
    val suspected: String
    val diagnosed: String
    val diagnosedOn: String
    val whatChanges: String
    val findDoctor: String
    val pcosSigns: String
    val logWeight: String
    val weightKg: String
    val weightSaved: String
    val weightNote: String
    val saved: String

    // ---- the pain diary
    val painTitle: String
    val painNote: String
    val todayLevel: String
    fun levelWord(level: Int): String
    val where: String
    fun area(area: PainArea): String
    val whenLabel: String
    fun kind(kind: PainKind): String
    val painkiller: String
    val painkillerName: String
    val missed: String
    val note: String
    val history: String
    val noEntries: String
    fun levelShort(level: Int): String

    // ---- the report
    val reportTitle: String
    val reportNote: String
    fun months(count: Int): String
    val lockedTitle: String
    val lockedBody: String
    val upgrade: String
    val cycles: String
    fun cycleLength(days: Int): String
    val running: String
    fun periodDays(days: Int): String
    val painByMonth: String
    fun painDays(days: Int): String
    fun averagePain(level: String): String
    val missedShort: String
    val signs: String
    fun signDays(days: Int): String
    val weight: String
    val showDoctor: String
    val noData: String
    val summary: String

    // ---- the cycle page
    fun periodWindow(from: String, to: String): String
    fun attentionTitle(attention: CycleAttention): String
    fun attentionBody(attention: CycleAttention): String
    val journeyBody: String
}

object ConditionStringsUz : ConditionStrings {
    override val title = "Holatlarim"
    override val intro = "Shifokor qo'ygan tashxisni yoki gumoningizni belgilang — ilova kuzatuvni shunga moslaydi. Ilova tashxis qo'ymaydi."
    override val none = "Yo'q"
    override val suspected = "Gumon qilaman"
    override val diagnosed = "Tashxis qo'yilgan"
    override val diagnosedOn = "Tashxis sanasi (ixtiyoriy)"
    override val whatChanges = "Nima o'zgaradi"
    override val findDoctor = "Shifokor topish"
    override val pcosSigns = "Ortiqcha tuk, soch to'kilishi va toshma kunlik kundalikdagi simptomlar ro'yxatida."
    override val logWeight = "Vazn qo'shish"
    override val weightKg = "Vazn, kg"
    override val weightSaved = "Vazn saqlandi"
    override val weightNote = "Vazn o'zgarishi holat hisobotida ko'rinadi"
    override val saved = "Saqlandi"

    override val painTitle = "Og'riq kundaligi"
    override val painNote = "Har kuni bir daqiqa: kuchi, joyi va qachon og'rigani. 2–3 tsikldan keyin shifokorga ko'rsatsangiz, eng kerakli ma'lumot shu."
    override val todayLevel = "Og'riq kuchi"
    override fun levelWord(level: Int) = when {
        level <= 0 -> "Og'riq yo'q"
        level <= 3 -> "Yengil"
        level <= 6 -> "O'rtacha"
        level <= 9 -> "Kuchli"
        else -> "Chidab bo'lmas"
    }
    override val where = "Qayeri og'riydi"
    override fun area(area: PainArea) = when (area) {
        PainArea.LOWER_ABDOMEN -> "Qorin pasti"
        PainArea.PELVIS_LEFT -> "Tos, chap"
        PainArea.PELVIS_RIGHT -> "Tos, o'ng"
        PainArea.LOWER_BACK -> "Bel"
        PainArea.LEGS -> "Oyoqlar"
        PainArea.RECTUM -> "To'g'ri ichak"
    }
    override val whenLabel = "Qachon og'riydi"
    override fun kind(kind: PainKind) = when (kind) {
        PainKind.PERIOD -> "Hayzda"
        PainKind.OVULATION -> "Ovulyatsiyada"
        PainKind.INTERCOURSE -> "Jinsiy aloqada"
        PainKind.BOWEL -> "Hojatxonada (ichak)"
        PainKind.URINATION -> "Siyishda"
        PainKind.CONSTANT -> "Doimiy"
    }
    override val painkiller = "Og'riq qoldiruvchi ichdim"
    override val painkillerName = "Qaysi dori (ixtiyoriy)"
    override val missed = "Ish yoki o'qishga bora olmadim, rejalarimni bekor qildim"
    override val note = "Izoh"
    override val history = "So'nggi kunlar"
    override val noEntries = "Hali yozuv yo'q"
    override fun levelShort(level: Int) = "$level/10"

    override val reportTitle = "Holat hisoboti"
    override val reportNote = "Tsikl, og'riq va belgilar oylar bo'yicha — shifokorga ko'rsatish uchun"
    override fun months(count: Int) = "$count oy"
    override val lockedTitle = "Holat hisoboti — Premium"
    override val lockedBody = "Kundalik bepul. Premium'da 3–6 oylik tsikl, og'riq va belgilar bitta hisobotda jamlanadi."
    override val upgrade = "Premium"
    override val cycles = "Tsikllar"
    override fun cycleLength(days: Int) = "$days kun"
    override val running = "davom etmoqda"
    override fun periodDays(days: Int) = "hayz $days kun"
    override val painByMonth = "Og'riq oylar bo'yicha"
    override fun painDays(days: Int) = "$days kun og'riq"
    override fun averagePain(level: String) = "o'rtacha $level/10"
    override val missedShort = "kun ishdan qoldi"
    override val signs = "Belgilar"
    override fun signDays(days: Int) = "$days kun"
    override val weight = "Vazn"
    override val showDoctor = "Shifokorga ko'rsatish"
    override val noData = "Bu davrda yozuv yo'q"
    override val summary = "Umumiy"

    override fun periodWindow(from: String, to: String) = "Keyingi hayz: $from – $to oralig'ida"
    override fun attentionTitle(attention: CycleAttention) = when (attention) {
        CycleAttention.LONG_CYCLES -> "Tsikllaringiz uzun"
        CycleAttention.NO_PERIOD_90 -> "3 oydan beri hayz yo'q"
    }
    override fun attentionBody(attention: CycleAttention) = when (attention) {
        CycleAttention.LONG_CYCLES -> "O'rtacha tsiklingiz 35 kundan uzun. Buning sabablari ko'p va ko'pincha oddiy — lekin ginekologga ko'rsatgan ma'qul."
        CycleAttention.NO_PERIOD_90 -> "Homiladorlik ehtimoli bo'lsa, test qiling. Har holda ginekologga murojaat qilish tavsiya etiladi."
    }
    override val journeyBody = "Og'riq kundaligi va holat hisoboti"
}

object ConditionStringsRu : ConditionStrings {
    override val title = "Мои состояния"
    override val intro = "Отметьте диагноз врача или подозрение — приложение настроит отслеживание. Приложение не ставит диагнозов."
    override val none = "Нет"
    override val suspected = "Подозреваю"
    override val diagnosed = "Диагноз поставлен"
    override val diagnosedOn = "Дата диагноза (необязательно)"
    override val whatChanges = "Что изменится"
    override val findDoctor = "Найти врача"
    override val pcosSigns = "Избыточный рост волос, выпадение волос и высыпания — в списке симптомов дневника."
    override val logWeight = "Добавить вес"
    override val weightKg = "Вес, кг"
    override val weightSaved = "Вес сохранён"
    override val weightNote = "Изменение веса видно в отчёте о состоянии"
    override val saved = "Сохранено"

    override val painTitle = "Дневник боли"
    override val painNote = "Минута в день: сила, место и когда болело. Через 2–3 цикла это самое ценное, что можно показать врачу."
    override val todayLevel = "Сила боли"
    override fun levelWord(level: Int) = when {
        level <= 0 -> "Без боли"
        level <= 3 -> "Слабая"
        level <= 6 -> "Средняя"
        level <= 9 -> "Сильная"
        else -> "Невыносимая"
    }
    override val where = "Где болит"
    override fun area(area: PainArea) = when (area) {
        PainArea.LOWER_ABDOMEN -> "Низ живота"
        PainArea.PELVIS_LEFT -> "Таз слева"
        PainArea.PELVIS_RIGHT -> "Таз справа"
        PainArea.LOWER_BACK -> "Поясница"
        PainArea.LEGS -> "Ноги"
        PainArea.RECTUM -> "Прямая кишка"
    }
    override val whenLabel = "Когда болит"
    override fun kind(kind: PainKind) = when (kind) {
        PainKind.PERIOD -> "При месячных"
        PainKind.OVULATION -> "При овуляции"
        PainKind.INTERCOURSE -> "При близости"
        PainKind.BOWEL -> "В туалете (кишечник)"
        PainKind.URINATION -> "При мочеиспускании"
        PainKind.CONSTANT -> "Постоянно"
    }
    override val painkiller = "Принимала обезболивающее"
    override val painkillerName = "Какое лекарство (необязательно)"
    override val missed = "Пропустила работу или учёбу, отменила планы"
    override val note = "Заметка"
    override val history = "Последние дни"
    override val noEntries = "Записей пока нет"
    override fun levelShort(level: Int) = "$level/10"

    override val reportTitle = "Отчёт о состоянии"
    override val reportNote = "Цикл, боль и признаки по месяцам — чтобы показать врачу"
    override fun months(count: Int) = "$count мес."
    override val lockedTitle = "Отчёт о состоянии — Premium"
    override val lockedBody = "Дневник бесплатный. В Premium цикл, боль и признаки за 3–6 месяцев собраны в один отчёт."
    override val upgrade = "Premium"
    override val cycles = "Циклы"
    override fun cycleLength(days: Int) = "$days дн."
    override val running = "идёт"
    override fun periodDays(days: Int) = "месячные $days дн."
    override val painByMonth = "Боль по месяцам"
    override fun painDays(days: Int) = "дней с болью: $days"
    override fun averagePain(level: String) = "в среднем $level/10"
    override val missedShort = "дн. пропущено"
    override val signs = "Признаки"
    override fun signDays(days: Int) = "$days дн."
    override val weight = "Вес"
    override val showDoctor = "Показать врачу"
    override val noData = "За этот период записей нет"
    override val summary = "Итого"

    override fun periodWindow(from: String, to: String) = "Следующие месячные: между $from и $to"
    override fun attentionTitle(attention: CycleAttention) = when (attention) {
        CycleAttention.LONG_CYCLES -> "Ваши циклы длинные"
        CycleAttention.NO_PERIOD_90 -> "Нет месячных 3 месяца"
    }
    override fun attentionBody(attention: CycleAttention) = when (attention) {
        CycleAttention.LONG_CYCLES -> "Средний цикл длиннее 35 дней. Причин много, и часто они безобидны — но стоит показаться гинекологу."
        CycleAttention.NO_PERIOD_90 -> "Если возможна беременность, сделайте тест. В любом случае рекомендуется обратиться к гинекологу."
    }
    override val journeyBody = "Дневник боли и отчёт о состоянии"
}

object ConditionStringsEn : ConditionStrings {
    override val title = "My conditions"
    override val intro = "Mark a diagnosis from your doctor or a suspicion, and tracking adapts to it. The app never diagnoses."
    override val none = "None"
    override val suspected = "I suspect it"
    override val diagnosed = "Diagnosed"
    override val diagnosedOn = "Date of diagnosis (optional)"
    override val whatChanges = "What changes"
    override val findDoctor = "Find a doctor"
    override val pcosSigns = "Excess hair, hair loss and acne are in the daily log's symptom list."
    override val logWeight = "Add weight"
    override val weightKg = "Weight, kg"
    override val weightSaved = "Weight saved"
    override val weightNote = "Weight changes show in the condition report"
    override val saved = "Saved"

    override val painTitle = "Pain diary"
    override val painNote = "A minute a day: how bad, where and when it hurt. After 2–3 cycles it is the most useful thing to show a doctor."
    override val todayLevel = "Pain level"
    override fun levelWord(level: Int) = when {
        level <= 0 -> "No pain"
        level <= 3 -> "Mild"
        level <= 6 -> "Moderate"
        level <= 9 -> "Severe"
        else -> "Unbearable"
    }
    override val where = "Where it hurts"
    override fun area(area: PainArea) = when (area) {
        PainArea.LOWER_ABDOMEN -> "Lower abdomen"
        PainArea.PELVIS_LEFT -> "Pelvis, left"
        PainArea.PELVIS_RIGHT -> "Pelvis, right"
        PainArea.LOWER_BACK -> "Lower back"
        PainArea.LEGS -> "Legs"
        PainArea.RECTUM -> "Rectum"
    }
    override val whenLabel = "When it hurts"
    override fun kind(kind: PainKind) = when (kind) {
        PainKind.PERIOD -> "With the period"
        PainKind.OVULATION -> "At ovulation"
        PainKind.INTERCOURSE -> "During sex"
        PainKind.BOWEL -> "On the toilet (bowel)"
        PainKind.URINATION -> "When peeing"
        PainKind.CONSTANT -> "Constant"
    }
    override val painkiller = "I took a painkiller"
    override val painkillerName = "Which one (optional)"
    override val missed = "I missed work or school, or cancelled plans"
    override val note = "Note"
    override val history = "Recent days"
    override val noEntries = "No entries yet"
    override fun levelShort(level: Int) = "$level/10"

    override val reportTitle = "Condition report"
    override val reportNote = "Cycle, pain and signs month by month — to show your doctor"
    override fun months(count: Int) = "$count months"
    override val lockedTitle = "Condition report — Premium"
    override val lockedBody = "The diary is free. Premium gathers 3–6 months of cycles, pain and signs into one report."
    override val upgrade = "Premium"
    override val cycles = "Cycles"
    override fun cycleLength(days: Int) = "$days days"
    override val running = "ongoing"
    override fun periodDays(days: Int) = "period $days days"
    override val painByMonth = "Pain by month"
    override fun painDays(days: Int) = "$days days with pain"
    override fun averagePain(level: String) = "average $level/10"
    override val missedShort = "days missed"
    override val signs = "Signs"
    override fun signDays(days: Int) = "$days days"
    override val weight = "Weight"
    override val showDoctor = "Show a doctor"
    override val noData = "Nothing recorded in this period"
    override val summary = "Overall"

    override fun periodWindow(from: String, to: String) = "Next period: between $from and $to"
    override fun attentionTitle(attention: CycleAttention) = when (attention) {
        CycleAttention.LONG_CYCLES -> "Your cycles are long"
        CycleAttention.NO_PERIOD_90 -> "No period for 3 months"
    }
    override fun attentionBody(attention: CycleAttention) = when (attention) {
        CycleAttention.LONG_CYCLES -> "Your average cycle is longer than 35 days. There are many reasons, often harmless — but it is worth seeing a gynaecologist."
        CycleAttention.NO_PERIOD_90 -> "If you could be pregnant, take a test. Either way, seeing a gynaecologist is recommended."
    }
    override val journeyBody = "Pain diary and condition report"
}
