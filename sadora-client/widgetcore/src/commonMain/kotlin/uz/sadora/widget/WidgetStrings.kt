package uz.sadora.widget

import uz.sadora.contract.CyclePhase
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage

/**
 * The few sentences a widget says, in the app's three languages.
 *
 * Not the app's own string tables: those live in the Compose module, which the widget
 * extension and the watch do not link. Kept short on purpose — a widget is read in a
 * glance, and every word here has to fit on a 2×2 tile.
 */
internal sealed class WidgetStrings {
    abstract val cycleDay: String
    abstract val cycle: String
    abstract fun phase(phase: CyclePhase): String
    abstract fun untilPeriod(days: Int): String
    abstract val periodToday: String
    abstract fun periodLate(days: Int): String
    abstract val logPeriod: String
    abstract val weeks: String
    abstract fun stageTitle(stage: LifeStage): String
    abstract fun weekAndDays(weeks: Int, days: Int): String
    abstract fun untilBirth(days: Int): String
    abstract fun babyAge(days: Int): String
    abstract fun month(number: Int): String
    abstract val water: String
    abstract val liters: String
    abstract val doses: String
    abstract fun anonymousDose(index: Int): String
    abstract fun metric(metric: HealthMetric): String
    abstract fun sleep(minutes: Int): String
    abstract val bpm: String
    abstract val ms: String
    abstract val connectWatch: String
    abstract val premiumLock: String
    abstract val ml: String
    abstract val updating: String
    abstract val signIn: String
    /** Decimal separator for litres. */
    open val decimal: Char = '.'
    /** Thousands separator for step counts. */
    open val thousands: String = " "

    data object Uz : WidgetStrings() {
        override val cycleDay = "Sikl kuni"
        override val cycle = "Sikl"
        override fun phase(phase: CyclePhase) = when (phase) {
            CyclePhase.PERIOD -> "Hayz"
            CyclePhase.FOLLICULAR -> "Follikulyar faza"
            CyclePhase.FERTILE -> "Unumdor kunlar"
            CyclePhase.LUTEAL -> "Lyuteal faza"
        }
        override fun untilPeriod(days: Int) = "Hayzgacha $days kun"
        override val periodToday = "Hayz bugun kutilmoqda"
        override fun periodLate(days: Int) = "Hayz $days kun kechikmoqda"
        override val logPeriod = "Hayz kunini belgilang"
        override val weeks = "hafta"
        override fun stageTitle(stage: LifeStage) = when (stage) {
            LifeStage.CYCLE -> cycle
            LifeStage.TRYING_TO_CONCEIVE -> "Homiladorlikka tayyorgarlik"
            LifeStage.PREGNANCY -> "Homiladorlik"
            LifeStage.POSTPARTUM -> "Tug'ruqdan keyin"
            LifeStage.PERIMENOPAUSE -> "Perimenopauza"
            LifeStage.MENOPAUSE -> "Menopauza"
        }
        override fun weekAndDays(weeks: Int, days: Int) = "$weeks hafta $days kun"
        override fun untilBirth(days: Int) = "Tug'ruqqacha $days kun"
        override fun babyAge(days: Int) = "Chaqaloq $days kunlik"
        override fun month(number: Int) = listOf(
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentyabr", "oktyabr", "noyabr", "dekabr",
        )[number - 1]
        override val water = "Suv"
        override val liters = "L"
        override val doses = "Dorilar"
        override fun anonymousDose(index: Int) = "Dori $index"
        override fun metric(metric: HealthMetric) = when (metric) {
            HealthMetric.STEPS -> "Qadam"
            HealthMetric.SLEEP_DURATION -> "Uyqu"
            HealthMetric.RESTING_HEART_RATE -> "Tinch puls"
            HealthMetric.RECOVERY -> "Tiklanish"
            else -> metricShort(metric)
        }
        override fun sleep(minutes: Int) = "${minutes / 60} s ${minutes % 60} daq"
        override val bpm = "zarba/daq"
        override val ms = "ms"
        override val connectWatch = "Soatni ulang"
        override val premiumLock = "Premium bilan suv va dorilar"
        override val ml = "ml"
        override val updating = "Yangilanmoqda…"
        override val signIn = "Kirish uchun oching"
    }

    data object Ru : WidgetStrings() {
        override val cycleDay = "День цикла"
        override val cycle = "Цикл"
        override fun phase(phase: CyclePhase) = when (phase) {
            CyclePhase.PERIOD -> "Месячные"
            CyclePhase.FOLLICULAR -> "Фолликулярная фаза"
            CyclePhase.FERTILE -> "Фертильные дни"
            CyclePhase.LUTEAL -> "Лютеиновая фаза"
        }
        override fun untilPeriod(days: Int) = "До месячных $days дн."
        override val periodToday = "Месячные ожидаются сегодня"
        override fun periodLate(days: Int) = "Задержка $days дн."
        override val logPeriod = "Отметьте начало месячных"
        override val weeks = "нед."
        override fun stageTitle(stage: LifeStage) = when (stage) {
            LifeStage.CYCLE -> cycle
            LifeStage.TRYING_TO_CONCEIVE -> "Планирование"
            LifeStage.PREGNANCY -> "Беременность"
            LifeStage.POSTPARTUM -> "После родов"
            LifeStage.PERIMENOPAUSE -> "Перименопауза"
            LifeStage.MENOPAUSE -> "Менопауза"
        }
        override fun weekAndDays(weeks: Int, days: Int) = "$weeks нед. $days дн."
        override fun untilBirth(days: Int) = "До родов $days дн."
        override fun babyAge(days: Int) = "Малышу $days дн."
        override fun month(number: Int) = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )[number - 1]
        override val water = "Вода"
        override val liters = "л"
        override val doses = "Лекарства"
        override fun anonymousDose(index: Int) = "Лекарство $index"
        override fun metric(metric: HealthMetric) = when (metric) {
            HealthMetric.STEPS -> "Шаги"
            HealthMetric.SLEEP_DURATION -> "Сон"
            HealthMetric.RESTING_HEART_RATE -> "Пульс покоя"
            HealthMetric.RECOVERY -> "Восстановление"
            else -> metricShort(metric)
        }
        override fun sleep(minutes: Int) = "${minutes / 60} ч ${minutes % 60} мин"
        override val bpm = "уд/мин"
        override val ms = "мс"
        override val connectWatch = "Подключите часы"
        override val premiumLock = "Вода и лекарства — в Premium"
        override val ml = "мл"
        override val updating = "Обновляется…"
        override val signIn = "Откройте, чтобы войти"
        override val decimal = ','
    }

    data object En : WidgetStrings() {
        override val cycleDay = "Cycle day"
        override val cycle = "Cycle"
        override fun phase(phase: CyclePhase) = when (phase) {
            CyclePhase.PERIOD -> "Period"
            CyclePhase.FOLLICULAR -> "Follicular phase"
            CyclePhase.FERTILE -> "Fertile days"
            CyclePhase.LUTEAL -> "Luteal phase"
        }
        override fun untilPeriod(days: Int) = if (days == 1) "Period in 1 day" else "Period in $days days"
        override val periodToday = "Period expected today"
        override fun periodLate(days: Int) = if (days == 1) "Period 1 day late" else "Period $days days late"
        override val logPeriod = "Log your period"
        override val weeks = "weeks"
        override fun stageTitle(stage: LifeStage) = when (stage) {
            LifeStage.CYCLE -> cycle
            LifeStage.TRYING_TO_CONCEIVE -> "Trying to conceive"
            LifeStage.PREGNANCY -> "Pregnancy"
            LifeStage.POSTPARTUM -> "Postpartum"
            LifeStage.PERIMENOPAUSE -> "Perimenopause"
            LifeStage.MENOPAUSE -> "Menopause"
        }
        override fun weekAndDays(weeks: Int, days: Int) = "${weeks}w ${days}d"
        override fun untilBirth(days: Int) = "$days days to go"
        override fun babyAge(days: Int) = "Baby is $days days old"
        override fun month(number: Int) = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
        )[number - 1]
        override val water = "Water"
        override val liters = "L"
        override val doses = "Medications"
        override fun anonymousDose(index: Int) = "Medication $index"
        override fun metric(metric: HealthMetric) = when (metric) {
            HealthMetric.STEPS -> "Steps"
            HealthMetric.SLEEP_DURATION -> "Sleep"
            HealthMetric.RESTING_HEART_RATE -> "Resting HR"
            HealthMetric.RECOVERY -> "Recovery"
            else -> metricShort(metric)
        }
        override fun sleep(minutes: Int) = "${minutes / 60}h ${minutes % 60}m"
        override val bpm = "bpm"
        override val ms = "ms"
        override val connectWatch = "Connect a watch"
        override val premiumLock = "Water and meds with Premium"
        override val ml = "ml"
        override val updating = "Updating…"
        override val signIn = "Open to sign in"
        override val thousands = ","
    }

    companion object {
        fun of(language: Language): WidgetStrings = when (language) {
            Language.UZ -> Uz
            Language.RU -> Ru
            Language.EN -> En
        }

        /** The neutral title discreet mode shows instead of a stage. */
        const val NEUTRAL_TITLE = "Sadora"

        private fun metricShort(metric: HealthMetric) = when (metric) {
            HealthMetric.HRV -> "HRV"
            HealthMetric.SPO2 -> "SpO₂"
            else -> metric.name.lowercase().replace('_', ' ')
        }
    }
}
