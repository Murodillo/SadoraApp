package uz.sadora.doctor.i18n

import uz.sadora.contract.KegelProgram
import uz.sadora.contract.LeakSituation

/**
 * Her pelvic floor (Kegel) training and the four-weekly leak check, as the record shows them
 * to a doctor. The words are the QR page's (`DoctorPage.PageText`), so both read the same.
 */
interface KegelStrings {
    val title: String
    fun program(program: KegelProgram): String
    /** "Daraja 3". */
    fun level(level: Int): String
    val sessions: String
    val activeDays: String
    val checksTitle: String
    val bother: String
    /** Under the checks: she filled them in herself, they diagnose nothing. */
    val note: String
    /** 0 never … 4 several times a day. */
    fun frequency(value: Int): String
    /** 0 none … 3 more. */
    fun amount(value: Int): String
    fun situation(situation: LeakSituation): String
}

object KegelStringsUz : KegelStrings {
    override val title = "Tos tubi mashqlari (Kegel)"
    override fun program(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Asosiy dastur"
        KegelProgram.PREGNANCY -> "Homiladorlik dasturi"
        KegelProgram.POSTPARTUM -> "Tug'ruqdan keyingi dastur"
        KegelProgram.MENOPAUSE -> "Menopauza dasturi"
    }
    override fun level(level: Int) = "Daraja $level"
    override val sessions = "Mashg'ulotlar"
    override val activeDays = "Faol kunlar"
    override val checksTitle = "Siydik tutolmaslik bo'yicha so'rov"
    override val bother = "Bezovtalik"
    override val note = "Bemor o'zi to'ldirgan qisqa so'rov; tashxis vositasi emas."
    override fun frequency(value: Int) = when (value) {
        0 -> "hech qachon"
        1 -> "haftada ≤1"
        2 -> "haftada 2–3"
        3 -> "kuniga ~1"
        else -> "kuniga bir necha"
    }
    override fun amount(value: Int) = when (value) {
        0 -> "—"
        1 -> "tomchi"
        2 -> "oz"
        else -> "ko'p"
    }
    override fun situation(situation: LeakSituation) = when (situation) {
        LeakSituation.COUGH -> "yo'tal/aksirish/kulish"
        LeakSituation.EXERCISE -> "jismoniy harakat"
        LeakSituation.URGE -> "to'satdan qistash"
        LeakSituation.NIGHT -> "tunda"
        LeakSituation.OTHER -> "boshqa"
    }
}

object KegelStringsRu : KegelStrings {
    override val title = "Упражнения для тазового дна (Кегель)"
    override fun program(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Базовая программа"
        KegelProgram.PREGNANCY -> "Программа для беременных"
        KegelProgram.POSTPARTUM -> "Программа после родов"
        KegelProgram.MENOPAUSE -> "Программа для менопаузы"
    }
    override fun level(level: Int) = "Уровень $level"
    override val sessions = "Тренировки"
    override val activeDays = "Активные дни"
    override val checksTitle = "Опрос о подтекании мочи"
    override val bother = "Беспокоит"
    override val note = "Короткий опрос, заполненный пациенткой; не диагностический инструмент."
    override fun frequency(value: Int) = when (value) {
        0 -> "никогда"
        1 -> "≤1 раза в неделю"
        2 -> "2–3 раза в неделю"
        3 -> "~1 раз в день"
        else -> "несколько раз в день"
    }
    override fun amount(value: Int) = when (value) {
        0 -> "—"
        1 -> "капли"
        2 -> "немного"
        else -> "много"
    }
    override fun situation(situation: LeakSituation) = when (situation) {
        LeakSituation.COUGH -> "кашель/чихание/смех"
        LeakSituation.EXERCISE -> "нагрузка"
        LeakSituation.URGE -> "внезапный позыв"
        LeakSituation.NIGHT -> "ночью"
        LeakSituation.OTHER -> "другое"
    }
}

object KegelStringsEn : KegelStrings {
    override val title = "Pelvic floor exercises (Kegel)"
    override fun program(program: KegelProgram) = when (program) {
        KegelProgram.CYCLE -> "Core program"
        KegelProgram.PREGNANCY -> "Pregnancy program"
        KegelProgram.POSTPARTUM -> "Postpartum program"
        KegelProgram.MENOPAUSE -> "Menopause program"
    }
    override fun level(level: Int) = "Level $level"
    override val sessions = "Sessions"
    override val activeDays = "Active days"
    override val checksTitle = "Leak check"
    override val bother = "Bother"
    override val note = "A short questionnaire she filled in herself; not a diagnostic instrument."
    override fun frequency(value: Int) = when (value) {
        0 -> "never"
        1 -> "≤ once a week"
        2 -> "2–3 times a week"
        3 -> "about daily"
        else -> "several times a day"
    }
    override fun amount(value: Int) = when (value) {
        0 -> "—"
        1 -> "drops"
        2 -> "a little"
        else -> "more"
    }
    override fun situation(situation: LeakSituation) = when (situation) {
        LeakSituation.COUGH -> "cough/sneeze/laugh"
        LeakSituation.EXERCISE -> "exercise/lifting"
        LeakSituation.URGE -> "sudden urge"
        LeakSituation.NIGHT -> "at night"
        LeakSituation.OTHER -> "other"
    }
}

/** One check on one line: "haftada 2–3 · tomchi · yo'tal/aksirish/kulish · Bezovtalik 4 / 10". */
fun KegelStrings.checkLine(check: uz.sadora.contract.KegelCheck): String =
    listOfNotNull(
        frequency(check.frequency),
        amount(check.amount).takeIf { check.amount > 0 },
        check.situations.takeIf { it.isNotEmpty() }?.joinToString(", ") { situation(it) },
        "$bother ${check.bother} / 10",
    ).joinToString(" · ")
