package uz.sadora.server.partner

import kotlinx.datetime.LocalDate
import uz.sadora.contract.CyclePhase
import uz.sadora.contract.Language
import uz.sadora.contract.LifeStage
import uz.sadora.contract.MoodLevel
import uz.sadora.contract.PartnerView
import uz.sadora.server.share.DoctorPage

/**
 * Her web link in a browser: the same view the app draws, in a plainer frame.
 *
 * Every value that came from a person — her name, a visit's title and place, a symptom's
 * label — is escaped. Nothing is cached and the page asks not to be indexed; at the
 * bottom it says plainly what the app adds, since the browser page is the smaller door.
 */
internal object PartnerWebPage {

    fun render(view: PartnerView, language: Language): String {
        val t = Words(language)
        val e = DoctorPage::escape
        val body = StringBuilder()

        val cycle = view.cycle
        val phase = cycle?.phase
        body.card {
            append("<h1>").append(e(view.name)).append("</h1>")
            when {
                cycle != null && phase != null -> {
                    append("<p class=\"phase\" style=\"color:").append(color(phase)).append("\">")
                        .append(t.phaseTitle(phase)).append("</p>")
                    val day = if (cycle.periodNow) cycle.periodDay?.let(t::periodDay) else cycle.cycleDay?.let(t::cycleDay)
                    day?.let { append("<p class=\"big\">").append(it).append("</p>") }
                    append("<p class=\"muted\">").append(t.phaseFeel(phase)).append("</p>")
                }
                view.pregnancy?.week != null -> {
                    append("<p class=\"phase\">").append(t.pregnancyWeek(view.pregnancy!!.week!!)).append("</p>")
                    view.pregnancy!!.daysToGo?.let { append("<p class=\"big\">").append(t.daysToGo(it)).append("</p>") }
                }
                view.pregnancy?.babyAgeDays != null ->
                    append("<p class=\"phase\">").append(t.babyAge(view.pregnancy!!.babyAgeDays!!)).append("</p>")
                view.stage == LifeStage.PERIMENOPAUSE || view.stage == LifeStage.MENOPAUSE ->
                    append("<p class=\"phase\">").append(t.menopause).append("</p>")
                else -> append("<p class=\"muted\">").append(t.nothing).append("</p>")
            }
            if (cycle != null) {
                val next = cycle.nextPeriodStart
                val days = cycle.daysUntilNextPeriod
                if (!cycle.periodNow && next != null && days != null) {
                    append("<p><b>").append(t.periodIn(days)).append("</b> · ").append(date(next)).append("</p>")
                }
                val from = cycle.fertileFrom
                val until = cycle.fertileUntil
                if (from != null && until != null) {
                    append("<p class=\"fertile\">").append(t.fertile).append(": ").append(date(from)).append(" – ").append(date(until)).append("</p>")
                }
            }
        }

        view.day?.let { day ->
            body.card {
                append("<h2>").append(t.today).append("</h2>")
                day.mood?.let { append("<p>").append(t.moodLabel).append(": <b>").append(t.mood(it)).append("</b></p>") }
                day.energy?.let { append("<p>").append(t.energyLabel).append(": <b>").append(it).append(" / 5</b></p>") }
                if (day.symptoms.isNotEmpty()) {
                    append("<p>").append(day.symptoms.joinToString(" · ") { e(it) }).append("</p>")
                }
            }
        }

        if (view.appointments.isNotEmpty()) {
            body.card {
                append("<h2>").append(t.visits).append("</h2>")
                view.appointments.forEach { visit ->
                    append("<p><b>").append(e(visit.title)).append("</b><br><span class=\"muted\">").append(date(visit.scheduledOn))
                    visit.scheduledAt?.let { append(" · ").append("%02d:%02d".format(it.hour, it.minute)) }
                    visit.place?.let { append(" · ").append(e(it)) }
                    append("</span></p>")
                }
            }
        }

        return page(t.title(view.name), language, body.toString(), t.footer)
    }

    fun gone(language: Language): String {
        val t = Words(language)
        return page("SADORA", language, "<section><h1>${t.goneTitle}</h1><p class=\"muted\">${t.goneBody}</p></section>", t.footer)
    }

    private inline fun StringBuilder.card(block: StringBuilder.() -> Unit) {
        append("<section>")
        block()
        append("</section>")
    }

    private fun page(title: String, language: Language, body: String, footer: String) = """
        <!doctype html>
        <html lang="${language.name.lowercase()}">
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex, nofollow">
        <meta name="referrer" content="no-referrer">
        <title>$title</title>
        <style>
          body{margin:0;background:#f7f5ff;color:#1a1630;font:16px/1.5 -apple-system,Segoe UI,Roboto,sans-serif;overflow-wrap:anywhere}
          .top{padding:14px 18px;background:linear-gradient(90deg,#7b61ff,#ff6fb8);color:#fff;font-weight:800;letter-spacing:.18em}
          main{max-width:560px;margin:0 auto;padding:16px}
          section{background:#fff;border-radius:18px;padding:16px 18px;margin:0 0 14px;box-shadow:0 6px 24px rgba(123,97,255,.08)}
          h1{margin:0 0 6px;font-size:24px}h2{font-size:17px;margin:0 0 8px;color:#6247e0}
          .phase{font-size:20px;font-weight:700;margin:4px 0}.big{font-size:18px;font-weight:600;margin:4px 0}
          .muted{color:#6f6a8a}.fertile{color:#ff4fa8}footer{color:#6f6a8a;font-size:13px;text-align:center;padding:8px 16px 28px}
        </style>
        </head>
        <body><div class="top">SADORA</div><main>$body</main><footer>$footer</footer></body></html>
    """.trimIndent()

    private fun date(d: LocalDate): String = "%02d.%02d.%d".format(d.day, d.month.ordinal + 1, d.year)

    private fun color(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> "#ff4f9a"
        CyclePhase.FOLLICULAR -> "#2f9be0"
        CyclePhase.FERTILE -> "#ff4fa8"
        CyclePhase.LUTEAL -> "#7b61ff"
    }

    private class Words(private val language: Language) {
        private fun pick(uz: String, ru: String, en: String) = when (language) {
            Language.UZ -> uz
            Language.RU -> ru
            Language.EN -> en
        }

        fun title(name: String) = "SADORA · " + DoctorPage.escape(name)
        fun phaseTitle(phase: CyclePhase) = when (phase) {
            CyclePhase.PERIOD -> pick("Hayz kunlari", "Дни месячных", "Period days")
            CyclePhase.FOLLICULAR -> pick("Energiya ko'tarilmoqda", "Энергия растёт", "Energy rising")
            CyclePhase.FERTILE -> pick("Eng yuqori energiya", "Пик энергии", "Peak energy")
            CyclePhase.LUTEAL -> pick("Hayzdan oldingi kunlar", "Дни перед месячными", "The days before her period")
        }
        fun phaseFeel(phase: CyclePhase) = when (phase) {
            CyclePhase.PERIOD -> pick(
                "Charchoq, qorin og'rig'i va kayfiyat pasayishi bo'lishi mumkin. Iliqlik va sabr yordam beradi.",
                "Возможны усталость, боль и упадок настроения. Помогают тепло и терпение.",
                "Tiredness, cramps and a lower mood are common. Warmth and patience help.",
            )
            CyclePhase.FOLLICULAR -> pick(
                "Kuch qaytadi, kayfiyat yaxshilanadi — birga sayr qilish uchun yaxshi vaqt.",
                "Силы возвращаются, настроение улучшается — хорошее время для прогулки вместе.",
                "Strength comes back and mood lifts — a good time for a walk together.",
            )
            CyclePhase.FERTILE -> pick(
                "O'ziga ishonch va energiya eng yuqori cho'qqida.",
                "Уверенность и энергия на пике.",
                "Confidence and energy are at their highest.",
            )
            CyclePhase.LUTEAL -> pick(
                "Charchoq va ta'sirchanlik bo'lishi mumkin. Ko'proq tinglang, kichik g'amxo'rlik qiling.",
                "Возможны усталость и обидчивость. Больше слушайте, проявляйте маленькую заботу.",
                "Tiredness and sensitivity are common. Listen more, and small kindnesses go far.",
            )
        }
        fun cycleDay(day: Int) = pick("Siklning $day-kuni", "$day-й день цикла", "Cycle day $day")
        fun periodDay(day: Int) = pick("Hayzning $day-kuni", "$day-й день месячных", "Period day $day")
        fun periodIn(days: Int) = when {
            days <= 0 -> pick("Hayz bugun kutilmoqda", "Месячные ожидаются сегодня", "Period expected today")
            days == 1 -> pick("Hayz ertaga kutilmoqda", "Месячные ожидаются завтра", "Period expected tomorrow")
            else -> pick("Hayzgacha $days kun", "До месячных $days дн.", "Period in $days days")
        }
        fun pregnancyWeek(week: Int) = pick("Homiladorlikning $week-haftasi", "$week-я неделя беременности", "Week $week of pregnancy")
        fun daysToGo(days: Int) = if (days <= 0) {
            pick("Tug'ish sanasi yetib keldi", "Дата родов наступила", "The due date has come")
        } else {
            pick("Tug'ilishga $days kun qoldi", "До родов $days дн.", "$days days to go")
        }
        fun babyAge(days: Int) = if (days < 14) {
            pick("Chaqaloq $days kunlik", "Малышу $days дн.", "The baby is $days days old")
        } else {
            pick("Chaqaloq ${days / 7} haftalik", "Малышу ${days / 7} нед.", "The baby is ${days / 7} weeks old")
        }
        fun mood(mood: MoodLevel) = when (mood) {
            MoodLevel.GREAT -> pick("A'lo", "Отлично", "Great")
            MoodLevel.GOOD -> pick("Yaxshi", "Хорошо", "Good")
            MoodLevel.OK -> pick("O'rtacha", "Нормально", "Okay")
            MoodLevel.LOW -> pick("Past", "Плохо", "Low")
            MoodLevel.BAD -> pick("Yomon", "Очень плохо", "Bad")
        }
        val menopause get() = pick("Menopauza davri", "Период менопаузы", "Menopause years")
        val nothing get() = pick("Hozircha hech narsa ulashilmagan.", "Пока ничего не показано.", "Nothing is shared yet.")
        val fertile get() = pick("Unumdor kunlar", "Фертильные дни", "Fertile days")
        val today get() = pick("Bugun", "Сегодня", "Today")
        val moodLabel get() = pick("Kayfiyati", "Настроение", "Mood")
        val energyLabel get() = pick("Energiyasi", "Энергия", "Energy")
        val visits get() = pick("Shifokor uchrashuvlari", "Визиты к врачу", "Doctor visits")
        val goneTitle get() = pick("Havola ishlamaydi", "Ссылка не работает", "This link no longer works")
        val goneBody get() = pick(
            "Muddati tugagan yoki bekor qilingan. Yangi havolani so'rang.",
            "Срок истёк или ссылку отозвали. Попросите новую.",
            "It has expired or was taken back. Ask for a new one.",
        )
        val footer get() = pick(
            "Sanalar taxminiy. Sadora ilovasida xabarlar va har kungi maslahatlar ham bor.",
            "Даты примерные. В приложении Sadora есть ещё сообщения и советы на каждый день.",
            "Dates are estimates. The Sadora app also has messages and daily tips.",
        )
    }
}
