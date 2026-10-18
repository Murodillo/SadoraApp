package uz.sadora.doctor.i18n

import uz.sadora.contract.ConditionStatus
import uz.sadora.contract.HealthCondition
import uz.sadora.contract.PainArea
import uz.sadora.contract.PainKind

/**
 * Her conditions — PCOS, endometriosis — and the pain diary they turn on, as the record
 * shows them to a doctor. A suspicion is her own and is worded as one: never a diagnosis.
 */
interface ConditionStrings {
    val title: String
    fun condition(condition: HealthCondition): String
    fun status(status: ConditionStatus): String
    /** Over the pain figures: "Og'riq kundaligi · so'nggi 90 kun". */
    fun painTitle(days: Int): String
    /** "Og'riqli kunlar / qayd etilgan", the value "12 / 40". */
    val painDays: String
    val averageLevel: String
    val maxLevel: String
    val painkillerDays: String
    val missedDays: String
    val whenTitle: String
    val whereTitle: String
    fun kind(kind: PainKind): String
    fun area(area: PainArea): String
}

object ConditionStringsUz : ConditionStrings {
    override val title = "Holatlar"
    override fun condition(condition: HealthCondition) = when (condition) {
        HealthCondition.PCOS -> "Polikistoz tuxumdon sindromi (PCOS)"
        HealthCondition.ENDOMETRIOSIS -> "Endometrioz"
    }
    override fun status(status: ConditionStatus) = when (status) {
        ConditionStatus.DIAGNOSED -> "Shifokor tashxisi"
        ConditionStatus.SUSPECTED -> "Gumon (tashxis yo'q)"
    }
    override fun painTitle(days: Int) = "Og'riq kundaligi · so'nggi $days kun"
    override val painDays = "Og'riqli kunlar / qayd etilgan"
    override val averageLevel = "O'rtacha kuchi"
    override val maxLevel = "Eng kuchli"
    override val painkillerDays = "Og'riq qoldiruvchi ichilgan kunlar"
    override val missedDays = "Ish/o'qishga bora olmagan kunlar"
    override val whenTitle = "Qachon og'rigan"
    override val whereTitle = "Qayeri og'rigan"
    override fun kind(kind: PainKind) = when (kind) {
        PainKind.PERIOD -> "Hayzda"
        PainKind.OVULATION -> "Ovulyatsiyada"
        PainKind.INTERCOURSE -> "Jinsiy aloqada"
        PainKind.BOWEL -> "Ichak bo'shatishda"
        PainKind.URINATION -> "Siyishda"
        PainKind.CONSTANT -> "Doimiy"
    }
    override fun area(area: PainArea) = when (area) {
        PainArea.LOWER_ABDOMEN -> "Qorin pasti"
        PainArea.PELVIS_LEFT -> "Tos (chap)"
        PainArea.PELVIS_RIGHT -> "Tos (o'ng)"
        PainArea.LOWER_BACK -> "Bel"
        PainArea.LEGS -> "Oyoqlar"
        PainArea.RECTUM -> "To'g'ri ichak"
    }
}

object ConditionStringsRu : ConditionStrings {
    override val title = "Состояния"
    override fun condition(condition: HealthCondition) = when (condition) {
        HealthCondition.PCOS -> "Синдром поликистозных яичников (СПКЯ)"
        HealthCondition.ENDOMETRIOSIS -> "Эндометриоз"
    }
    override fun status(status: ConditionStatus) = when (status) {
        ConditionStatus.DIAGNOSED -> "Диагноз врача"
        ConditionStatus.SUSPECTED -> "Подозрение (без диагноза)"
    }
    override fun painTitle(days: Int) = "Дневник боли · " + (if (days % 10 == 1 && days % 100 != 11) "последний" else "последние") +
        " $days " + ru(days, "день", "дня", "дней")
    override val painDays = "Дни с болью / отмечено"
    override val averageLevel = "Средняя сила"
    override val maxLevel = "Самая сильная"
    override val painkillerDays = "Дни с обезболивающим"
    override val missedDays = "Пропущена работа или учёба"
    override val whenTitle = "Когда болело"
    override val whereTitle = "Где болело"
    override fun kind(kind: PainKind) = when (kind) {
        PainKind.PERIOD -> "При месячных"
        PainKind.OVULATION -> "При овуляции"
        PainKind.INTERCOURSE -> "При близости"
        PainKind.BOWEL -> "При дефекации"
        PainKind.URINATION -> "При мочеиспускании"
        PainKind.CONSTANT -> "Постоянная"
    }
    override fun area(area: PainArea) = when (area) {
        PainArea.LOWER_ABDOMEN -> "Низ живота"
        PainArea.PELVIS_LEFT -> "Таз слева"
        PainArea.PELVIS_RIGHT -> "Таз справа"
        PainArea.LOWER_BACK -> "Поясница"
        PainArea.LEGS -> "Ноги"
        PainArea.RECTUM -> "Прямая кишка"
    }
}

object ConditionStringsEn : ConditionStrings {
    override val title = "Conditions"
    override fun condition(condition: HealthCondition) = when (condition) {
        HealthCondition.PCOS -> "Polycystic ovary syndrome (PCOS)"
        HealthCondition.ENDOMETRIOSIS -> "Endometriosis"
    }
    override fun status(status: ConditionStatus) = when (status) {
        ConditionStatus.DIAGNOSED -> "Diagnosed by a doctor"
        ConditionStatus.SUSPECTED -> "Suspected (no diagnosis)"
    }
    override fun painTitle(days: Int) = "Pain diary · last $days " + en(days, "day", "days")
    override val painDays = "Days with pain / logged"
    override val averageLevel = "Average strength"
    override val maxLevel = "Strongest"
    override val painkillerDays = "Days on painkillers"
    override val missedDays = "Days she missed work or school"
    override val whenTitle = "When it hurt"
    override val whereTitle = "Where it hurt"
    override fun kind(kind: PainKind) = when (kind) {
        PainKind.PERIOD -> "With the period"
        PainKind.OVULATION -> "At ovulation"
        PainKind.INTERCOURSE -> "During sex"
        PainKind.BOWEL -> "On bowel movements"
        PainKind.URINATION -> "On urination"
        PainKind.CONSTANT -> "Constant"
    }
    override fun area(area: PainArea) = when (area) {
        PainArea.LOWER_ABDOMEN -> "Lower abdomen"
        PainArea.PELVIS_LEFT -> "Pelvis left"
        PainArea.PELVIS_RIGHT -> "Pelvis right"
        PainArea.LOWER_BACK -> "Lower back"
        PainArea.LEGS -> "Legs"
        PainArea.RECTUM -> "Rectum"
    }
}
