package uz.sadora.widget

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.DoseStatus
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MedicationDose
import uz.sadora.contract.WidgetSummary

/**
 * Everything one widget draws, already in words.
 *
 * The renderers — Glance on Android, SwiftUI in the widget extension and on the watch —
 * only lay these strings out; none of them counts a week or names a phase, so the four
 * of them cannot drift apart. Built from the snapshot and the device's clock, because a
 * widget is redrawn at midnight without anyone fetching anything.
 */
data class WidgetContent(
    val stage: StageBlock,
    val water: WaterBlock?,
    val doses: DosesBlock?,
    val health: HealthBlock?,
    /** Premium blocks were asked for on a free account: draw the lock in their place. */
    val locked: Boolean,
    val lockText: String,
    /** Words were left out on purpose: draw the neutral mark. */
    val discreet: Boolean,
    /** The snapshot is from an earlier day: ask the OS for a refresh soon. */
    val needsRefresh: Boolean,
    val updatingText: String,
) {
    companion object {
        fun build(snapshot: WidgetSnapshot, config: WidgetConfig, now: LocalDateTime): WidgetContent {
            val summary = snapshot.summary
            val strings = WidgetStrings.of(summary.language)
            val today = now.date
            val sameDay = summary.today == today
            val discreet = summary.discreet || (config.lockScreen && !config.revealOnLockScreen)
            // The stage alone is free; any block beyond it is Premium.
            val premiumAsked = config.blocks.isNotEmpty()
            val allowed = summary.premium
            val wants = { block: WidgetBlock -> allowed && block in config.blocks }

            return WidgetContent(
                stage = stageBlock(summary, today, strings, discreet),
                water = if (wants(WidgetBlock.WATER)) waterBlock(snapshot, today, sameDay, config, strings) else null,
                doses = if (wants(WidgetBlock.DOSES) && sameDay) dosesBlock(snapshot, now.time, strings, discreet) else null,
                health = if (wants(WidgetBlock.HEALTH)) healthBlock(summary, sameDay, config, strings) else null,
                locked = !allowed && premiumAsked,
                lockText = strings.premiumLock,
                discreet = discreet,
                needsRefresh = !sameDay,
                updatingText = strings.updating,
            )
        }

        // ------------------------------------------------------------ stage

        private fun stageBlock(summary: WidgetSummary, today: LocalDate, s: WidgetStrings, discreet: Boolean): StageBlock {
            val full = when (summary.lifeStage) {
                LifeStage.CYCLE, LifeStage.TRYING_TO_CONCEIVE -> cycleStage(summary, today, s)
                LifeStage.PREGNANCY -> pregnancyStage(summary, today, s)
                LifeStage.POSTPARTUM -> postpartumStage(summary, today, s)
                LifeStage.PERIMENOPAUSE, LifeStage.MENOPAUSE -> StageBlock(
                    big = today.day.toString(),
                    unit = s.month(today.month.number),
                    title = s.stageTitle(summary.lifeStage),
                    line1 = null,
                    line2 = null,
                    phase = null,
                    progress = null,
                )
            }
            // A number and a colour, nothing that names what the number counts.
            return if (discreet) full.copy(title = WidgetStrings.NEUTRAL_TITLE, unit = null, line1 = null, line2 = null) else full
        }

        private fun cycleStage(summary: WidgetSummary, today: LocalDate, s: WidgetStrings): StageBlock {
            val cycle = summary.cycle
            val cycleDay = cycle?.cycleDay
            if (cycle == null || cycleDay == null) {
                return StageBlock("—", null, s.cycle, null, s.logPeriod, null, null)
            }
            // Counted forward from the day the server answered, so a widget left alone
            // overnight still turns the page at midnight.
            val elapsed = cycle.today.daysUntil(today).coerceAtLeast(0)
            val day = cycleDay + elapsed
            val until = cycle.daysUntilNextPeriod?.minus(elapsed)
            val phase = if (elapsed == 0) cycle.phase else phaseOn(cycle, today) ?: cycle.phase
            val line2 = when {
                until == null -> null
                until > 0 -> s.untilPeriod(until)
                until == 0 -> s.periodToday
                else -> s.periodLate(-until)
            }
            val length = cycle.prediction.averageCycleLength ?: (cycleDay + (cycle.daysUntilNextPeriod ?: 0)).takeIf { it > 0 }
            return StageBlock(
                big = day.toString(),
                unit = null,
                title = s.cycleDay,
                line1 = phase?.let(s::phase),
                line2 = line2,
                phase = phase,
                progress = length?.let { (day.toFloat() / it).coerceIn(0f, 1f) },
            )
        }

        /** The phase on a later day, from the prediction's own dates. */
        private fun phaseOn(cycle: uz.sadora.contract.CycleStatus, day: LocalDate): CyclePhase? {
            val p = cycle.prediction
            val fertileFrom = p.fertileFrom
            val fertileUntil = p.fertileUntil
            val next = p.nextPeriodStart
            return when {
                next != null && day >= next -> CyclePhase.PERIOD
                fertileFrom != null && fertileUntil != null && day in fertileFrom..fertileUntil -> CyclePhase.FERTILE
                fertileUntil != null && day > fertileUntil -> CyclePhase.LUTEAL
                else -> null
            }
        }

        private fun pregnancyStage(summary: WidgetSummary, today: LocalDate, s: WidgetStrings): StageBlock {
            val due = summary.stage?.dueDate
                ?: return StageBlock("—", null, s.stageTitle(LifeStage.PREGNANCY), null, null, null, null)
            val toDue = today.daysUntil(due)
            val gestation = (PREGNANCY_DAYS - toDue).coerceIn(0, MAX_GESTATION_DAYS)
            val weeks = (gestation / 7).coerceAtLeast(1)
            return StageBlock(
                big = weeks.toString(),
                unit = s.weeks,
                title = s.stageTitle(LifeStage.PREGNANCY),
                line1 = s.weekAndDays(gestation / 7, gestation % 7),
                line2 = if (toDue > 0) s.untilBirth(toDue) else null,
                phase = null,
                progress = gestation.toFloat() / PREGNANCY_DAYS,
            )
        }

        private fun postpartumStage(summary: WidgetSummary, today: LocalDate, s: WidgetStrings): StageBlock {
            val born = summary.stage?.birthDate
                ?: return StageBlock("—", null, s.stageTitle(LifeStage.POSTPARTUM), null, null, null, null)
            val age = born.daysUntil(today).coerceAtLeast(0)
            return StageBlock(
                big = (age / 7).toString(),
                unit = s.weeks,
                title = s.stageTitle(LifeStage.POSTPARTUM),
                line1 = null,
                line2 = s.babyAge(age),
                phase = null,
                progress = null,
            )
        }

        // ------------------------------------------------------------ water

        private fun waterBlock(
            snapshot: WidgetSnapshot,
            today: LocalDate,
            sameDay: Boolean,
            config: WidgetConfig,
            s: WidgetStrings,
        ): WaterBlock {
            val water = snapshot.summary.water
            val queued = snapshot.pending.filterIsInstance<PendingAction.Water>().filter { it.day == today }.sumOf { it.ml }
            val ml = ((if (sameDay) water.waterMl else 0) + queued).coerceAtLeast(0)
            val goal = water.waterGoalMl
            return WaterBlock(
                ml = ml,
                goalMl = goal,
                label = "${liters(ml, s)} / ${liters(goal, s)} ${s.liters}",
                title = s.water,
                stepMl = config.waterStepMl,
                stepLabel = "+${config.waterStepMl} ${s.ml}",
                progress = if (goal > 0) (ml.toFloat() / goal).coerceIn(0f, 1f) else 0f,
            )
        }

        private fun liters(ml: Int, s: WidgetStrings): String {
            val whole = ml / 1000
            val rest = (ml % 1000).toString().padStart(3, '0').trimEnd('0')
            return if (rest.isEmpty()) whole.toString() else "$whole${s.decimal}$rest"
        }

        // ------------------------------------------------------------ doses

        private fun dosesBlock(snapshot: WidgetSnapshot, now: LocalTime, s: WidgetStrings, discreet: Boolean): DosesBlock {
            val marked = snapshot.pending.filterIsInstance<PendingAction.Dose>()
            val doses = snapshot.summary.medications.doses
                .map { dose ->
                    val queued = marked.lastOrNull {
                        it.medicationId == dose.medicationId && it.dueOn == dose.dueOn && it.dueAt == dose.dueAt
                    }
                    if (queued != null) dose.copy(status = queued.status) else dose
                }
                .sortedBy { it.dueAt }
            // Discreet names count the medicines, not the doses: two of the same pill stay one name.
            val anonymous = doses.map { it.medicationId }.distinct().withIndex().associate { it.value to it.index + 1 }
            val rows = doses.map { it.toLine(if (discreet) s.anonymousDose(anonymous.getValue(it.medicationId)) else it.name) }
            val pending = rows.filter { it.status == DoseStatus.PENDING }
            // The next dose is the earliest one still open — an overdue one first, it is the
            // one she most needs to see.
            val next = pending.firstOrNull { it.dueAt <= now } ?: pending.firstOrNull()
            return DosesBlock(
                title = s.doses,
                label = "${rows.count { it.status == DoseStatus.TAKEN }} / ${rows.size}",
                next = next,
                rows = rows,
            )
        }

        private fun MedicationDose.toLine(shownName: String) = DoseLine(
            medicationId = medicationId,
            name = shownName,
            time = dueAt.toString().take(5),
            dueOn = dueOn,
            dueAt = dueAt,
            status = status,
        )

        // ------------------------------------------------------------ health

        private fun healthBlock(summary: WidgetSummary, sameDay: Boolean, config: WidgetConfig, s: WidgetStrings): HealthBlock {
            val day = summary.health?.takeIf { sameDay }
            val items = config.metrics.mapNotNull { metric ->
                val value = day?.value(metric) ?: return@mapNotNull null
                HealthItem(metric, s.metric(metric), format(metric, value, s))
            }
            return HealthBlock(items = items, connectPrompt = summary.health == null, connectText = s.connectWatch)
        }

        private fun format(metric: HealthMetric, value: Double, s: WidgetStrings): String = when (metric) {
            HealthMetric.STEPS -> grouped(value.toInt(), s.thousands)
            HealthMetric.SLEEP_DURATION -> s.sleep(value.toInt())
            HealthMetric.RESTING_HEART_RATE, HealthMetric.HEART_RATE -> "${value.toInt()} ${s.bpm}"
            HealthMetric.HRV -> "${value.toInt()} ${s.ms}"
            HealthMetric.RECOVERY, HealthMetric.SPO2 -> "${value.toInt()}%"
            else -> value.toInt().toString()
        }

        private fun grouped(n: Int, separator: String): String =
            n.toString().reversed().chunked(3).joinToString(separator).reversed()

        private const val PREGNANCY_DAYS = 280
        private const val MAX_GESTATION_DAYS = 42 * 7
    }
}

data class StageBlock(
    /** The one number: cycle day, pregnancy or baby week, or the date. "—" when unknown. */
    val big: String,
    val unit: String?,
    val title: String,
    val line1: String?,
    val line2: String?,
    /** Colours the cycle widget; also the only thing discreet mode keeps of the phase. */
    val phase: CyclePhase?,
    /** How far through the cycle or the pregnancy, 0..1, for the ring. */
    val progress: Float?,
)

data class WaterBlock(
    val ml: Int,
    val goalMl: Int,
    val label: String,
    val title: String,
    val stepMl: Int,
    val stepLabel: String,
    val progress: Float,
)

data class DosesBlock(
    val title: String,
    /** "taken / due". */
    val label: String,
    val next: DoseLine?,
    val rows: List<DoseLine>,
)

data class DoseLine(
    val medicationId: String,
    val name: String,
    val time: String,
    val dueOn: LocalDate,
    val dueAt: LocalTime,
    val status: DoseStatus,
)

data class HealthBlock(
    val items: List<HealthItem>,
    /** Nothing is connected: the block becomes "connect a watch". */
    val connectPrompt: Boolean,
    val connectText: String,
)

data class HealthItem(val metric: HealthMetric, val label: String, val value: String)
