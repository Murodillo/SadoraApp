package org.example.project.model

import org.example.project.i18n.StringsUz

/**
 * The answer to a question when there is no backend behind the app.
 *
 * The server runs the same rules against her real data; this copy exists so the
 * prototype and the previews still have a chat. Rule-based on purpose: it reads the
 * same numbers the tiles show, names which of them it used, and never diagnoses.
 */
fun localAnswerFor(question: String, state: AppState): String {
    val q = question.lowercase()
    val phase = state.currentPhase()
    // Uzbek throughout, deliberately: this is the offline stand-in for the model, and
    // the model itself only answers in Uzbek.
    val uz = StringsUz.common
    val context = "Sikl ${state.cycleDay}-kun (${uz.phase(phase).lowercase()}), " +
        "uyqu ${state.sleepLabel(format = ::uzbekHoursMinutes)}, suv ${Fmt.litres(state.waterMl)} l asosida."
    val body = when {
        "charch" in q || "energiya" in q || "toliq" in q -> when (phase) {
            CyclePhase.Luteal, CyclePhase.Period ->
                "Hayzdan oldin va hayz davrida progesteron o'zgarishi uyqu va energiyaga ta'sir qilishi mumkin. " +
                    "Magniyga boy ovqatlar, yengil yurish va nafas mashqlarini sinab ko'ring."
            else ->
                "Bu fazada energiya odatda o'sadi. Suv iste'moli va uyqu davomiyligi pastroq bo'lsa, " +
                    "charchoq shundan bo'lishi mumkin — bugun ${state.waterRemainingMl} ml suv qoldi."
        }
        "ye" in q || "ovqat" in q || "taom" in q ->
            "Barqaror energiya uchun oqsil va murakkab uglevodlarni birga oling: tuxum, " +
                "yog'urt, don mahsulotlari, sabzavot. Bugun ${Fmt.int(state.caloriesEaten)} / " +
                "${Fmt.int(state.calorieGoal)} kkal qayd etilgan. Ovqatlanish rejasini tuzib beraymi?"
        "teri" in q || "akne" in q ->
            "Sikl davomida gormonlar terining yog' ishlab chiqarishini o'zgartiradi: hayz oldidan " +
                "toshmalar ko'payishi odatiy. Yumshoq tozalash, yetarli suv va uyqu yordam beradi. " +
                "Uzoq davom etsa, dermatologga ko'rsating."
        "uyqu" in q || "uxla" in q ->
            "Kecha ${state.sleepLabel(format = ::uzbekHoursMinutes)} uxlagansiz. Kechqurun ekranni kamaytirish va bir xil " +
                "vaqtda yotish uyqu sifatini yaxshilaydi. Uyqu ma'lumotlarini kuzatishda davom eting."
        "sikl" in q || "hayz" in q || "ovulyats" in q ->
            "Hozir siklning ${state.cycleDay}-kuni — ${uz.phase(phase).lowercase()}. " +
                "${uz.phaseEnergy(phase)} Keyingi hayz taxminan ${state.daysToNextPeriod()} kundan keyin."
        else ->
            "Savolingizni tushundim. Sikl, ovqatlanish, kayfiyat va dorilaringiz bo'yicha " +
                "ma'lumotlaringizga tayanib javob bera olaman — aniqroq so'rasangiz, batafsil tushuntiraman."
    }
    return "$body\n\n$context Bu umumiy ma'lumot — tashxis emas."
}

/**
 * The duration format this file uses.
 *
 * [localAnswerFor] is written in Uzbek from end to end — it is the offline fallback for
 * the assistant, not a translated surface — so it names its own abbreviations rather
 * than reaching for the language the screen happens to be in.
 */
private fun uzbekHoursMinutes(hours: Int, minutes: Int): String = "${hours}s ${minutes}d"
