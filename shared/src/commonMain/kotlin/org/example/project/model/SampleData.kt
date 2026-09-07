package org.example.project.model

import uz.sadora.contract.MealSlot

data class Meal(
    val id: String,
    val slot: MealSlot,
    val time: String,
    val description: String,
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
) {
    /**
     * The thumbnail stand-in for a meal photo.
     *
     * Photos come from the scanner or, later, the server; until one exists the tile
     * shows the slot's own dish rather than a grey box.
     */
    val emoji: String
        get() = when (slot) {
            MealSlot.BREAKFAST -> "🥣"
            MealSlot.LUNCH -> "🍲"
            MealSlot.DINNER -> "🍽️"
            MealSlot.SNACK -> "🍎"
        }
}

enum class MedStatus { Taken, Pending, Skipped }

data class Medication(
    val id: String,
    val emoji: String,
    val name: String,
    val time: String,
    val schedule: String,
    val note: String,
    val status: MedStatus,
    /** Days of stock left, when the user tracks supply. */
    val stockDays: Int? = null,
)

data class Appointment(val day: String, val month: String, val title: String, val time: String, val who: String)

data class SleepStage(val label: String, val duration: String, val fraction: Float)

enum class SourceStatus { Connected, Expired, Disconnected }

data class DataSource(
    val name: String,
    val device: String?,
    val syncedAt: String?,
    val status: SourceStatus,
    val metrics: List<String> = emptyList(),
)


/** A catalogue entry. Values are per 100 g unless [perPiece] is set. */
data class FoodItem(
    val name: String,
    val kcal: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val perPiece: Boolean = false,
)

/** One symptom tile on the cycle screen: what it is, and how strongly it was felt (0–3). */
data class SymptomTile(val label: String, val emoji: String, val severity: Int)

/** Seed content matching the design's sample screens. */
object SampleData {

    val meals = listOf(
        Meal("m1", MealSlot.BREAKFAST, "08:30", "Yog'urt, granola, rezavorlar", 450, 25, 15, 50),
        Meal("m2", MealSlot.LUNCH, "13:00", "Tovuqli salat, non", 600, 30, 20, 65),
    )

    val medications = listOf(
        Medication("d1", "🌿", "Folik kislota 400 mkg", "08:00", "Har kuni", "Ovqatdan keyin", MedStatus.Taken),
        Medication("d3", "🩸", "Temir 30 mg", "20:00", "Har kuni", "Ovqatdan keyin", MedStatus.Pending, stockDays = 12),
    )

    /** The chips under the AI chat. Each opens a question in that area. */
    val aiTopics = listOf(
        "Energiya" to "Energiyamni qanday barqaror ushlasam bo'ladi?",
        "Ovqatlanish" to "Bugun nima yeganim ma'qul?",
        "Sikl" to "Nega hayzdan oldin charchoq sezaman?",
        "Teri" to "Sikl davomida terim nega o'zgaradi?",
    )


    /** Local dishes lead the catalogue — the design's food search is Uzbek-first. */
    val foods = listOf(
        FoodItem("Osh (go'shtli)", 248, 9, 12, 26),
        FoodItem("Osh (mastava)", 92, 4, 3, 12),
        FoodItem("Oshqovoqli somsa", 276, 7, 14, 30, perPiece = true),
        FoodItem("Tovuqli salat", 145, 12, 7, 8),
        FoodItem("Non (patir)", 270, 8, 4, 51),
    )

    /** The four tiles the cycle screen shows under "Simptomlar". */
    val cycleSymptomTiles = listOf(
        SymptomTile("Ajralma", "💧", 2),
        SymptomTile("Shish", "🎈", 1),
        SymptomTile("Bosh og'rig'i", "🤕", 3),
        SymptomTile("Akne", "✨", 1),
    )

    val cycleSymptoms = listOf("Ajralma", "Og'riq", "Ko'ngil aynishi", "Bosh og'rig'i")
    val pregnancySymptoms = listOf("Belda og'riq", "Ko'ngil aynishi", "Shish")
    val perimenopauseSymptoms = listOf("Issiqlik to'lqini", "Uyqusizlik", "Terlash", "Kayfiyat o'zgarishi")
    val menopauseSymptoms = listOf("Issiqlik to'lqini", "Bo'g'im og'rig'i")

    val appointments = listOf(
        Appointment("27", "AVG", "Skrining UTT", "10:30", "Shifokor ko'rigi"),
    )

    val sleepStages = listOf(
        SleepStage("Chuqur", "56d", 0.14f),
        SleepStage("Yengil", "2s 56d", 0.44f),
        SleepStage("REM", "1s 28d", 0.22f),
        SleepStage("Uyg'oq", "48d", 0.12f),
    )

    /** The eight platforms the deck lists as sources. Order matches the deck. */
    val dataSources = listOf(
        DataSource("Apple Health", "Apple Watch Series 9", "12:40", SourceStatus.Connected, listOf("Qadamlar", "Puls", "Mashqlar", "Energiya")),
        DataSource("Health Connect", null, null, SourceStatus.Disconnected),
        DataSource("Samsung Health", null, null, SourceStatus.Disconnected),
        DataSource("Huawei Health", null, null, SourceStatus.Disconnected),
        DataSource("Garmin", null, null, SourceStatus.Expired),
        DataSource("Oura", "Oura Ring Gen3", "07:05", SourceStatus.Connected, listOf("Uyqu", "HRV", "Tiklanish")),
        DataSource("WHOOP", null, null, SourceStatus.Disconnected),
        DataSource("Fitbit", null, null, SourceStatus.Disconnected),
    )

    val weekDays = listOf("Du", "Se", "Ch", "Pa", "Ju", "Sh", "Ya")

    /** The app never states causation — only co-occurrence. */
    val correlationDisclaimer =
        "Bog'liqliklar sabab-natija emas. \"Ko'pincha birga kuzatilgan\" degan ma'noni bildiradi."

    val medicalDisclaimer =
        "SADORA — salomatlik yordamchisi. Tashxis qo'ymaydi va dori tayinlamaydi."

    val predictionDisclaimer =
        "Bashoratlar taxminiy hisob-kitoblardir va tibbiy kafolat emas."
}
