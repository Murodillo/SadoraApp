package uz.sadora.server.share

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.DoctorSummary
import uz.sadora.contract.FetalMovement
import uz.sadora.contract.FlowLevel
import uz.sadora.contract.FoodRelation
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.SymptomSeverity

/**
 * The page a doctor sees after scanning the QR code.
 *
 * Plain HTML with the styles inlined and no script: it opens on any phone camera's
 * browser, it prints, and there is nothing in it to load from anywhere else. The
 * structure is what a clinician reads first to last — who, the cycle or the pregnancy,
 * the record day by day, what she takes, what her device measured — with the disclaimer
 * that this is what she recorded, not a diagnosis.
 *
 * Text is in the three languages the app speaks, chosen by `?lang=` or by her own
 * setting, so a Russian-speaking doctor in Tashkent is not handed a page in Uzbek.
 */
object DoctorPage {

    fun render(summary: DoctorSummary, language: Language): String {
        val t = PageText.of(language)
        val p = summary.person
        val esc = ::escape
        return buildString {
            append("<!doctype html><html lang=\"${language.name.lowercase()}\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<meta name=\"robots\" content=\"noindex, nofollow\">")
            append("<title>SADORA · ${esc(p.name)}</title><style>").append(CSS).append("</style></head><body>")
            append("<header class=\"top\"><div class=\"brand\">SADORA</div>")
            append("<nav class=\"lang\">")
            Language.entries.forEach { option ->
                val cls = if (option == language) "on" else ""
                append("<a class=\"$cls\" href=\"?lang=${option.name.lowercase()}\">${option.name}</a>")
            }
            append("</nav></header><main>")

            // ---- who
            append("<section class=\"hero\"><h1>${esc(p.name)}</h1><p class=\"sub\">${esc(t.stage(p.lifeStage))}")
            p.age?.let { append(" · ").append(t.years(it)) }
            append("</p><div class=\"facts\">")
            fact(t.birthDate, p.birthDate?.let(::date))
            fact(t.height, p.heightCm?.let { "$it ${t.cm}" })
            fact(t.weight, p.weightKg?.let { "$it ${t.kg}" })
            fact(t.memberSince, date(p.memberSince))
            fact(t.generatedAt, dateTime(summary))
            append("</div></section>")

            append("<p class=\"note\">${esc(t.disclaimer)}</p>")

            // ---- cycle
            summary.cycle?.let { c ->
                section(t.cycleTitle) {
                    append("<div class=\"facts\">")
                    fact(t.cycleDay, c.cycleDay?.toString())
                    fact(t.phase, c.phase?.let { t.phase(it) })
                    fact(t.lastPeriod, c.lastPeriodStart?.let(::date))
                    fact(t.averageCycle, c.history.averageCycleLength?.let { t.days(it) })
                    fact(t.averagePeriod, c.history.averagePeriodLength?.let { t.days(it) })
                    fact(t.range, if (c.history.shortestCycle != null && c.history.longestCycle != null) "${c.history.shortestCycle}–${c.history.longestCycle} ${t.daysWord}" else null)
                    fact(t.nextPeriod, c.history.prediction.nextPeriodStart?.let { "${date(it)} (${t.estimated})" })
                    append("</div>")
                    if (c.history.cycles.isNotEmpty()) {
                        append("<table><thead><tr><th>${t.cycleStart}</th><th>${t.cycleLength}</th><th>${t.periodLength}</th></tr></thead><tbody>")
                        c.history.cycles.take(12).forEach { entry ->
                            append("<tr><td>${date(entry.startedOn)}</td><td>${t.days(entry.cycleLength)}</td><td>${entry.periodLength?.let { t.days(it) } ?: "—"}</td></tr>")
                        }
                        append("</tbody></table>")
                    } else {
                        append("<p class=\"muted\">${esc(t.noCycles)}</p>")
                    }
                }
            }

            // ---- pregnancy
            summary.pregnancy?.let { g ->
                section(t.pregnancyTitle) {
                    append("<div class=\"facts\">")
                    fact(t.week, g.week?.toString())
                    fact(t.dueDate, g.dueDate?.let(::date))
                    fact(t.childBirth, g.childBirthDate?.let(::date))
                    append("</div>")
                    if (g.lessMovementDays.isNotEmpty()) {
                        append("<p class=\"warn\">${esc(t.lessMovement)}: ${g.lessMovementDays.joinToString(", ") { date(it) }}</p>")
                    }
                }
            }

            // ---- symptoms summary
            if (summary.symptomCounts.isNotEmpty()) {
                section(t.symptomsTitle) {
                    append("<p class=\"muted\">${esc(t.windowNote(DoctorSummary.SHARE_WINDOW_DAYS))}</p><div class=\"chips\">")
                    summary.symptomCounts.forEach { s ->
                        append("<span class=\"chip\">${esc(s.label)} <b>${s.days}</b></span>")
                    }
                    append("</div>")
                }
            }

            // ---- mind
            summary.mind?.takeIf { it.daysLogged > 0 || it.journalEntries > 0 }?.let { m ->
                section(t.mindTitle) {
                    append("<div class=\"facts\">")
                    fact(t.daysLogged, "${m.daysLogged} / ${m.windowDays}")
                    fact(t.averageMood, m.averageMood?.let { one(it) + " / 5" })
                    fact(t.averageEnergy, m.averageEnergy?.let { one(it) + " / 5" })
                    fact(t.averageStress, m.averageStress?.let { one(it) + " / 5" })
                    fact(t.journalEntries, m.journalEntries.toString())
                    fact(t.practiceMinutes, m.practiceMinutes.toString())
                    append("</div><p class=\"muted\">${esc(t.journalPrivate)}</p>")
                }
            }

            // ---- medications
            if (summary.medications.isNotEmpty()) {
                section(t.medsTitle) {
                    append("<table><thead><tr><th>${t.medName}</th><th>${t.dose}</th><th>${t.schedule}</th><th>${t.period}</th><th>${t.adherence}</th></tr></thead><tbody>")
                    summary.medications.forEach { m ->
                        val dose = listOfNotNull(m.dosage, m.unit).joinToString(" ")
                        val when_ = m.schedule.describe() + " · " + t.foodRelation(m.foodRelation)
                        val period = date(m.startedOn) + (m.endedOn?.let { " – " + date(it) } ?: "")
                        val adherence = m.adherencePercent?.let { "$it% (${m.takenCount}/${m.takenCount + m.skippedCount})" } ?: "—"
                        append("<tr class=\"${if (m.active) "" else "off"}\"><td>${esc(m.name)}</td><td>${esc(dose)}</td><td>${esc(when_)}</td><td>$period</td><td>$adherence</td></tr>")
                    }
                    append("</tbody></table>")
                }
            }

            // ---- appointments
            if (summary.appointments.isNotEmpty()) {
                section(t.appointmentsTitle) {
                    append("<table><thead><tr><th>${t.date}</th><th>${t.appointment}</th><th>${t.place}</th></tr></thead><tbody>")
                    summary.appointments.sortedByDescending { it.scheduledOn }.forEach { a ->
                        val time = a.scheduledAt?.let { " %02d:%02d".format(it.hour, it.minute) }.orEmpty()
                        append("<tr><td>${date(a.scheduledOn)}$time</td><td>${esc(a.title)}${if (a.isDone) " ✓" else ""}</td><td>${esc(a.place.orEmpty())}</td></tr>")
                    }
                    append("</tbody></table>")
                }
            }

            // ---- nutrition
            summary.nutrition?.takeIf { it.daysLogged > 0 }?.let { n ->
                section(t.nutritionTitle) {
                    append("<div class=\"facts\">")
                    fact(t.daysLogged, "${n.daysLogged} / ${n.windowDays}")
                    fact(t.averageKcal, n.averageKcal?.let { "$it / ${n.goals.calorieGoal} kcal" })
                    fact(t.averageWater, n.averageWaterMl?.let { "$it / ${n.goals.waterGoalMl} ml" })
                    append("</div>")
                }
            }

            // ---- wearable
            summary.wearable?.let { w ->
                section(t.wearableTitle) {
                    append("<p class=\"muted\">${esc(t.providers)}: ${w.providers.joinToString(", ") { t.provider(it.name) }}</p>")
                    append("<div class=\"facts\">")
                    w.averages.forEach { m -> fact(t.metric(m.metric), format(m.metric, m.value) + " " + t.unit(m.metric)) }
                    append("</div>")
                    val columns = w.averages.map { it.metric }
                    append("<div class=\"scroll\"><table><thead><tr><th>${t.date}</th>")
                    columns.forEach { append("<th>${t.metric(it)}</th>") }
                    append("</tr></thead><tbody>")
                    w.days.take(31).forEach { day ->
                        append("<tr><td>${date(day.date)}</td>")
                        columns.forEach { metric ->
                            append("<td>${day.value(metric)?.let { format(metric, it) } ?: "—"}</td>")
                        }
                        append("</tr>")
                    }
                    append("</tbody></table></div>")
                }
            }

            // ---- the daily record
            if (summary.days.isNotEmpty()) {
                section(t.recordTitle) {
                    append("<p class=\"muted\">${esc(t.windowNote(DoctorSummary.SHARE_WINDOW_DAYS))}</p>")
                    append("<div class=\"scroll\"><table><thead><tr><th>${t.date}</th><th>${t.flow}</th><th>${t.mood}</th><th>${t.energy}</th><th>${t.stress}</th><th>${t.symptoms}</th>")
                    if (summary.pregnancy != null) append("<th>${t.movement}</th>")
                    append("</tr></thead><tbody>")
                    summary.days.forEach { d ->
                        append("<tr><td>${date(d.date)}</td><td>${d.flow?.let { t.flow(it) } ?: ""}</td><td>${d.mood?.let { t.mood(it) } ?: ""}</td>")
                        append("<td>${d.energy ?: ""}</td><td>${d.stress ?: ""}</td><td>")
                        append(d.symptoms.joinToString(", ") { esc(it.label) + t.severityMark(it.severity) })
                        append("</td>")
                        if (summary.pregnancy != null) append("<td>${d.fetalMovement?.let { t.movement(it) } ?: ""}</td>")
                        append("</tr>")
                    }
                    append("</tbody></table></div>")
                }
            }

            append("</main><footer><p>${esc(t.footer)}</p></footer></body></html>")
        }
    }

    /** The page for a link that no longer works. One wording for every reason, on purpose. */
    fun gone(language: Language): String {
        val t = PageText.of(language)
        return "<!doctype html><html lang=\"${language.name.lowercase()}\"><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta name=\"robots\" content=\"noindex\">" +
            "<title>SADORA</title><style>$CSS</style></head><body><header class=\"top\"><div class=\"brand\">SADORA</div></header>" +
            "<main><section class=\"hero\"><h1>${escape(t.goneTitle)}</h1><p class=\"sub\">${escape(t.goneBody)}</p></section></main></body></html>"
    }

    // ---------------------------------------------------------------- helpers

    private inline fun StringBuilder.section(title: String, body: StringBuilder.() -> Unit) {
        append("<section><h2>").append(escape(title)).append("</h2>")
        body()
        append("</section>")
    }

    private fun StringBuilder.fact(label: String, value: String?) {
        if (value == null) return
        append("<div class=\"fact\"><span>").append(escape(label)).append("</span><b>").append(escape(value)).append("</b></div>")
    }

    private fun date(d: LocalDate): String = "%02d.%02d.%d".format(d.day, d.month.ordinal + 1, d.year)

    private fun dateTime(summary: DoctorSummary): String {
        val local = summary.generatedAt.toLocalDateTime(TimeZone.of("Asia/Tashkent"))
        return date(local.date) + " %02d:%02d".format(local.hour, local.minute)
    }

    private fun one(value: Double): String = "%.1f".format(value)

    private fun format(metric: HealthMetric, value: Double): String = when (metric) {
        HealthMetric.STEPS, HealthMetric.ACTIVE_ENERGY, HealthMetric.DISTANCE, HealthMetric.SLEEP_DURATION,
        HealthMetric.SLEEP_DEEP, HealthMetric.SLEEP_REM, HealthMetric.SLEEP_LIGHT, HealthMetric.SLEEP_AWAKE,
        HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE, HealthMetric.RECOVERY,
        HealthMetric.SLEEP_PERFORMANCE, HealthMetric.SLEEP_EFFICIENCY, HealthMetric.SPO2,
        -> value.toInt().toString()
        else -> one(value)
    }

    internal fun escape(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private val CSS = """
        :root{--bg:#f7f5ff;--card:#fff;--ink:#1a1630;--muted:#6f6a8a;--line:#eae6fa;--primary:#6247e0;--pink:#ff6fb8;--warn:#9a6200}
        *{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;overflow-wrap:anywhere}
        .top{display:flex;justify-content:space-between;align-items:center;padding:14px 18px;background:linear-gradient(90deg,#7b61ff,#ff6fb8);color:#fff}
        .brand{font-weight:800;letter-spacing:.18em}.lang a{color:#fff;opacity:.7;margin-left:10px;text-decoration:none;font-weight:600}.lang a.on{opacity:1;text-decoration:underline}
        main{max-width:920px;margin:0 auto;padding:16px}section{background:var(--card);border-radius:18px;padding:16px 18px;margin:0 0 14px;box-shadow:0 6px 24px rgba(123,97,255,.08);overflow-x:auto}
        .hero h1{margin:0 0 2px;font-size:26px}.sub{margin:0 0 10px;color:var(--muted)}h2{font-size:17px;margin:0 0 10px;color:var(--primary)}
        .facts{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:8px}.fact{background:#f1edff;border-radius:12px;padding:8px 10px}.fact span{display:block;font-size:12px;color:var(--muted)}.fact b{font-size:15px}
        .note{margin:0 0 14px;padding:10px 14px;border-left:4px solid var(--pink);background:#fff;border-radius:10px;color:var(--muted);font-size:13px}
        .muted{color:var(--muted);font-size:13px;margin:0 0 8px}.warn{color:var(--warn);font-weight:600}
        table{width:100%;border-collapse:collapse;font-size:13px}th{text-align:left;color:var(--muted);font-weight:600;border-bottom:1px solid var(--line);padding:6px 6px}td{padding:6px;border-bottom:1px solid var(--line);vertical-align:top}tr.off{opacity:.55}
        .scroll{overflow-x:auto}.chips{display:flex;flex-wrap:wrap;gap:6px}.chip{background:#f1edff;border-radius:999px;padding:4px 10px;font-size:13px}.chip b{color:var(--primary);margin-left:4px}
        footer{text-align:center;color:var(--muted);font-size:12px;padding:8px 16px 28px}@media print{.top{background:#fff;color:#000}.lang{display:none}section{box-shadow:none;border:1px solid #ddd}}
    """.trimIndent()
}

/** The page's words, in the three languages. Server-side, because the page has no app behind it. */
internal class PageText private constructor(private val language: Language) {

    companion object {
        fun of(language: Language) = PageText(language)
    }

    private fun pick(uz: String, ru: String, en: String): String = when (language) {
        Language.UZ -> uz
        Language.RU -> ru
        Language.EN -> en
    }

    val disclaimer get() = pick(
        "Bu sahifada bemor SADORA ilovasida o'zi qayd etgan ma'lumotlar va qurilmasi o'lchagan ko'rsatkichlar keltirilgan. Bu tashxis emas — tibbiy xulosani faqat shifokor chiqaradi.",
        "На этой странице — то, что пациентка сама записала в приложении SADORA, и показатели её устройства. Это не диагноз: медицинское заключение делает только врач.",
        "This page shows what the patient recorded herself in the SADORA app and what her device measured. It is not a diagnosis; only a clinician draws a medical conclusion.",
    )
    val footer get() = pick(
        "Havola vaqtinchalik va bemor tomonidan bekor qilinishi mumkin. SADORA — ayollar salomatligi ilovasi.",
        "Ссылка временная, пациентка может отозвать её в любой момент. SADORA — приложение о женском здоровье.",
        "This link is temporary and can be revoked by the patient at any time. SADORA — a women's health app.",
    )
    fun years(age: Int) = pick("$age yosh", "$age лет", "$age years")
    val birthDate get() = pick("Tug'ilgan sana", "Дата рождения", "Date of birth")
    val height get() = pick("Bo'y", "Рост", "Height")
    val weight get() = pick("Vazn", "Вес", "Weight")
    val cm get() = pick("sm", "см", "cm")
    val kg get() = pick("kg", "кг", "kg")
    val memberSince get() = pick("Ilovada", "В приложении с", "Member since")
    val generatedAt get() = pick("Tuzilgan vaqt", "Сформировано", "Generated")
    val cycleTitle get() = pick("Hayz sikli", "Менструальный цикл", "Menstrual cycle")
    val cycleDay get() = pick("Sikl kuni", "День цикла", "Cycle day")
    val phase get() = pick("Faza", "Фаза", "Phase")
    val lastPeriod get() = pick("Oxirgi hayz", "Последняя менструация", "Last period")
    val averageCycle get() = pick("O'rtacha sikl", "Средний цикл", "Average cycle")
    val averagePeriod get() = pick("O'rtacha hayz", "Средняя менструация", "Average period")
    val range get() = pick("Diapazon", "Разброс", "Range")
    val nextPeriod get() = pick("Keyingi hayz", "Следующая менструация", "Next period")
    val estimated get() = pick("taxminiy", "прогноз", "estimated")
    val cycleStart get() = pick("Boshlanish", "Начало", "Start")
    val cycleLength get() = pick("Sikl", "Цикл", "Cycle")
    val periodLength get() = pick("Hayz", "Менструация", "Period")
    val noCycles get() = pick("Tugallangan sikllar hali yo'q.", "Завершённых циклов пока нет.", "No completed cycles yet.")
    val pregnancyTitle get() = pick("Homiladorlik", "Беременность", "Pregnancy")
    val week get() = pick("Hafta", "Неделя", "Week")
    val dueDate get() = pick("Taxminiy tug'ruq sanasi", "Предполагаемая дата родов", "Due date")
    val childBirth get() = pick("Tug'ruq sanasi", "Дата родов", "Birth date")
    val lessMovement get() = pick("Bola harakati kam sezilgan kunlar", "Дни с ослабленным шевелением", "Days with less foetal movement")
    val symptomsTitle get() = pick("Simptomlar", "Симптомы", "Symptoms")
    fun windowNote(days: Int) = pick("Oxirgi $days kun", "Последние $days дней", "Last $days days")
    val mindTitle get() = pick("Kayfiyat va holat", "Настроение и состояние", "Mood and wellbeing")
    val daysLogged get() = pick("Qayd etilgan kunlar", "Дней с записями", "Days logged")
    val averageMood get() = pick("O'rtacha kayfiyat", "Среднее настроение", "Average mood")
    val averageEnergy get() = pick("O'rtacha energiya", "Средняя энергия", "Average energy")
    val averageStress get() = pick("O'rtacha stress", "Средний стресс", "Average stress")
    val journalEntries get() = pick("Kundalik yozuvlari", "Записей в дневнике", "Journal entries")
    val practiceMinutes get() = pick("Nafas/meditatsiya, daqiqa", "Дыхание/медитация, мин", "Breathing/meditation, min")
    val journalPrivate get() = pick("Kundalik matni ko'rsatilmaydi — u faqat bemorning o'ziga tegishli.", "Текст дневника не показывается — он принадлежит только пациентке.", "Journal text is not shown; it belongs to the patient alone.")
    val medsTitle get() = pick("Dorilar va qo'shimchalar", "Лекарства и добавки", "Medications and supplements")
    val medName get() = pick("Nomi", "Название", "Name")
    val dose get() = pick("Doza", "Доза", "Dose")
    val schedule get() = pick("Jadval", "График", "Schedule")
    val period get() = pick("Davr", "Период", "Period")
    val adherence get() = pick("Qabul", "Приём", "Adherence")
    val appointmentsTitle get() = pick("Ko'riklar va tekshiruvlar", "Приёмы и обследования", "Appointments")
    val date get() = pick("Sana", "Дата", "Date")
    val appointment get() = pick("Nima", "Что", "What")
    val place get() = pick("Joy", "Место", "Place")
    val nutritionTitle get() = pick("Ovqatlanish", "Питание", "Nutrition")
    val averageKcal get() = pick("O'rtacha kaloriya", "Средняя калорийность", "Average calories")
    val averageWater get() = pick("O'rtacha suv", "Среднее количество воды", "Average water")
    val wearableTitle get() = pick("Qurilma ko'rsatkichlari", "Показатели устройства", "Device measurements")
    val providers get() = pick("Manbalar", "Источники", "Sources")
    val recordTitle get() = pick("Kunlik qaydlar", "Ежедневные записи", "Daily record")
    val flow get() = pick("Qon ketishi", "Кровотечение", "Flow")
    val mood get() = pick("Kayfiyat", "Настроение", "Mood")
    val energy get() = pick("Energiya", "Энергия", "Energy")
    val stress get() = pick("Stress", "Стресс", "Stress")
    val symptoms get() = pick("Simptomlar", "Симптомы", "Symptoms")
    val movement get() = pick("Bola harakati", "Шевеление", "Movement")
    val daysWord get() = pick("kun", "дн.", "days")
    val goneTitle get() = pick("Havola ochilmaydi", "Ссылка не открывается", "This link does not open")
    val goneBody get() = pick(
        "Havola muddati tugagan yoki bemor uni bekor qilgan. Yangi QR kodni so'rang.",
        "Срок ссылки истёк, или пациентка отозвала её. Попросите новый QR-код.",
        "The link has expired or the patient revoked it. Ask for a fresh QR code.",
    )
    fun days(n: Int) = "$n $daysWord"

    fun stage(stage: LifeStage) = when (stage) {
        LifeStage.CYCLE -> pick("Sikl kuzatuvi", "Наблюдение цикла", "Cycle tracking")
        LifeStage.TRYING_TO_CONCEIVE -> pick("Homiladorlikni rejalashtirish", "Планирование беременности", "Trying to conceive")
        LifeStage.PREGNANCY -> pick("Homiladorlik", "Беременность", "Pregnancy")
        LifeStage.POSTPARTUM -> pick("Tug'ruqdan keyingi davr", "Послеродовой период", "Postpartum")
        LifeStage.PERIMENOPAUSE -> pick("Perimenopauza", "Перименопауза", "Perimenopause")
        LifeStage.MENOPAUSE -> pick("Menopauza", "Менопауза", "Menopause")
    }

    fun phase(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> pick("Hayz", "Менструация", "Period")
        CyclePhase.FOLLICULAR -> pick("Follikulyar", "Фолликулярная", "Follicular")
        CyclePhase.FERTILE -> pick("Ovulyatsiya oynasi", "Овуляторное окно", "Fertile window")
        CyclePhase.LUTEAL -> pick("Lyuteal", "Лютеиновая", "Luteal")
    }

    fun flow(flow: FlowLevel) = when (flow) {
        FlowLevel.SPOTTING -> pick("dog'", "мажущие", "spotting")
        FlowLevel.LIGHT -> pick("kam", "скудные", "light")
        FlowLevel.MEDIUM -> pick("o'rtacha", "умеренные", "medium")
        FlowLevel.HEAVY -> pick("ko'p", "обильные", "heavy")
    }

    fun mood(mood: MoodLevel) = when (mood) {
        MoodLevel.BAD -> pick("yomon", "плохое", "bad")
        MoodLevel.LOW -> pick("past", "пониженное", "low")
        MoodLevel.OK -> pick("o'rtacha", "нормальное", "ok")
        MoodLevel.GOOD -> pick("yaxshi", "хорошее", "good")
        MoodLevel.GREAT -> pick("a'lo", "отличное", "great")
    }

    fun movement(m: FetalMovement) = when (m) {
        FetalMovement.USUAL -> pick("odatdagidek", "обычное", "usual")
        FetalMovement.LESS -> pick("kam", "меньше", "less")
        FetalMovement.MORE -> pick("ko'p", "больше", "more")
    }

    fun severityMark(s: SymptomSeverity) = when (s) {
        SymptomSeverity.MILD -> " ·"
        SymptomSeverity.MODERATE -> " ··"
        SymptomSeverity.SEVERE -> " ···"
    }

    fun foodRelation(r: FoodRelation) = when (r) {
        FoodRelation.ANY -> pick("ovqatga bog'liq emas", "независимо от еды", "any time")
        FoodRelation.BEFORE -> pick("ovqatdan oldin", "до еды", "before food")
        FoodRelation.WITH -> pick("ovqat bilan", "во время еды", "with food")
        FoodRelation.AFTER -> pick("ovqatdan keyin", "после еды", "after food")
    }

    fun provider(name: String) = name.lowercase().split('_').joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    fun metric(m: HealthMetric) = when (m) {
        HealthMetric.STEPS -> pick("Qadam", "Шаги", "Steps")
        HealthMetric.ACTIVE_ENERGY -> pick("Faol energiya", "Активная энергия", "Active energy")
        HealthMetric.DISTANCE -> pick("Masofa", "Дистанция", "Distance")
        HealthMetric.HEART_RATE -> pick("Puls", "Пульс", "Heart rate")
        HealthMetric.RESTING_HEART_RATE -> pick("Tinch puls", "Пульс в покое", "Resting HR")
        HealthMetric.HRV -> "HRV"
        HealthMetric.RESPIRATORY_RATE -> pick("Nafas", "Дыхание", "Respiratory rate")
        HealthMetric.BODY_TEMPERATURE -> pick("Tana harorati", "Температура тела", "Body temperature")
        HealthMetric.SKIN_TEMPERATURE -> pick("Teri harorati", "Температура кожи", "Skin temperature")
        HealthMetric.SLEEP_DURATION -> pick("Uyqu", "Сон", "Sleep")
        HealthMetric.SLEEP_DEEP -> pick("Chuqur uyqu", "Глубокий сон", "Deep sleep")
        HealthMetric.SLEEP_REM -> "REM"
        HealthMetric.SLEEP_LIGHT -> pick("Yengil uyqu", "Лёгкий сон", "Light sleep")
        HealthMetric.SLEEP_AWAKE -> pick("Uyg'oq", "Бодрствование", "Awake")
        HealthMetric.SLEEP_PERFORMANCE -> pick("Uyqu samarasi", "Качество сна", "Sleep performance")
        HealthMetric.SLEEP_EFFICIENCY -> pick("Uyqu samaradorligi", "Эффективность сна", "Sleep efficiency")
        HealthMetric.WEIGHT -> pick("Vazn", "Вес", "Weight")
        HealthMetric.RECOVERY -> pick("Tiklanish", "Восстановление", "Recovery")
        HealthMetric.STRAIN -> pick("Yuklama", "Нагрузка", "Strain")
        HealthMetric.SPO2 -> "SpO₂"
    }

    fun unit(m: HealthMetric) = when (m.canonicalUnit) {
        "min" -> pick("daq", "мин", "min")
        "count" -> ""
        "bpm" -> pick("zarb/daq", "уд/мин", "bpm")
        "percent" -> "%"
        "c" -> "°C"
        "score" -> ""
        else -> m.canonicalUnit
    }
}
