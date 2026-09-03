package org.example.project.model

data class Meal(
    val id: String,
    val slot: String,
    val time: String,
    val description: String,
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
)

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

data class KnowledgeItem(
    val kind: String,
    val category: String,
    val duration: String,
    val title: String,
    val reviewedBy: String? = null,
    val premium: Boolean = false,
)

enum class SourceStatus { Connected, Expired, Disconnected }

data class DataSource(
    val name: String,
    val device: String?,
    val syncedAt: String?,
    val status: SourceStatus,
    val metrics: List<String> = emptyList(),
)

data class Conversation(val title: String, val whenLabel: String)

/** A catalogue entry. Values are per 100 g unless [perPiece] is set. */
data class FoodItem(
    val name: String,
    val kcal: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val perPiece: Boolean = false,
)

/** Seed content matching the design's sample screens. */
object SampleData {

    /**
     * Seed posts for the secret chat.
     *
     * They stand in for a feed the server does not serve yet, and they are written the
     * way the room is meant to read — questions people are embarrassed to ask out loud,
     * answered without judgement.
     */
    val communityPosts: List<CommunityPost> = listOf(
        CommunityPost(
            id = "p1",
            alias = "Anonim",
            tint = 0,
            topic = CommunityTopic.Cycle,
            ago = "20 daqiqa oldin",
            body = "Siklim har oy 3–4 kunga surilib ketyapti. Shifokorga borishim kerakmi, " +
                "yoki bu normami? 24 yoshdaman.",
            likes = 34,
            comments = listOf(
                CommunityComment(
                    "Anonim", 2, "12 daqiqa oldin",
                    "Menda ham shunday edi. Bir necha oy kuzatib, keyin ginekologga " +
                        "ko'rsatdim — hammasi joyida chiqdi.",
                ),
                CommunityComment(
                    "Anonim", 3, "5 daqiqa oldin",
                    "3–4 kun odatda normal deb hisoblanadi, lekin tinchlanish uchun " +
                        "tekshiruvdan o'tgan yaxshi.",
                ),
            ),
        ),
        CommunityPost(
            id = "p2",
            alias = "Anonim",
            tint = 1,
            topic = CommunityTopic.Wellbeing,
            ago = "1 soat oldin",
            body = "Hayzdan oldingi hafta juda asabiy bo'lib qolaman va keyin o'zimni " +
                "ayblayman. Shu bilan qanday kurashasizlar?",
            likes = 78,
            comments = listOf(
                CommunityComment(
                    "Anonim", 0, "40 daqiqa oldin",
                    "Men o'sha kunlarni kalendarga belgilab qo'yaman. Oldindan bilganim " +
                        "uchun o'zimni ayblamay qo'ydim.",
                ),
            ),
        ),
        CommunityPost(
            id = "p3",
            alias = "Anonim",
            tint = 2,
            topic = CommunityTopic.Pregnancy,
            ago = "3 soat oldin",
            body = "12-haftadaman va hali ham hech kimga aytmadim. Qachon aytish " +
                "kerakligi haqida qoida bormi?",
            likes = 51,
            comments = emptyList(),
        ),
        CommunityPost(
            id = "p4",
            alias = "Anonim",
            tint = 3,
            topic = CommunityTopic.Body,
            ago = "kecha",
            body = "Ko'krak og'rig'i hayzdan bir hafta oldin boshlanadi. Bu normalmi " +
                "yoki tekshirtirish kerakmi?",
            likes = 19,
            comments = listOf(
                CommunityComment(
                    "Anonim", 1, "kecha",
                    "Gormonal o'zgarish sababli bo'lishi mumkin. Lekin qattiq og'riq " +
                        "bo'lsa, ko'rsatgan ma'qul.",
                ),
            ),
        ),
        CommunityPost(
            id = "p5",
            alias = "Anonim",
            tint = 1,
            topic = CommunityTopic.Cycle,
            ago = "2 kun oldin",
            body = "Birinchi marta shu ilovada siklimni kuzata boshladim va nihoyat " +
                "tanamni tushunayotgandekman. Kimga qiyin bo'lsa — boshlang, arziydi.",
            likes = 142,
            comments = emptyList(),
        ),
    )


    val meals = listOf(
        Meal("m1", "Nonushta", "08:20", "Yog'urt, granola, rezavorlar", 340, 18, 11, 42),
        Meal("m2", "Tushlik", "13:05", "Tovuqli salat, non", 520, 34, 19, 48),
    )

    val medications = listOf(
        Medication("d1", "🌿", "Folik kislota 400 mkg", "08:00", "Har kuni", "Ovqatdan keyin", MedStatus.Taken),
        Medication("d2", "☀️", "D3 vitamini 2000 IU", "09:00", "Du, Cho, Ju", "Ovqat bilan", MedStatus.Taken),
        Medication("d3", "🩸", "Temir 30 mg", "20:00", "Har kuni", "Ovqatdan keyin", MedStatus.Pending, stockDays = 12),
    )

    val suggestedQuestions = listOf(
        "Nega o'zimni charchagan his qilyapman?",
        "Bugun nima yeganim ma'qul?",
        "Uyqumni tushunishga yordam bering",
    )

    val recentConversations = listOf(
        Conversation("Uyqu sifatini qanday yaxshilash mumkin", "Kecha"),
        Conversation("Haftalik ovqatlanish rejasi", "3 kun"),
    )

    /** Local dishes lead the catalogue — the design's food search is Uzbek-first. */
    val foods = listOf(
        FoodItem("Osh (go'shtli)", 248, 9, 12, 26),
        FoodItem("Osh (mastava)", 92, 4, 3, 12),
        FoodItem("Oshqovoqli somsa", 276, 7, 14, 30, perPiece = true),
        FoodItem("Tovuqli salat", 145, 12, 7, 8),
        FoodItem("Non (patir)", 270, 8, 4, 51),
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

    val knowledge = listOf(
        KnowledgeItem("KURS", "SIKL", "5 dars", "Siklni tushunish: gormonlar va kayfiyat", "Dr. N. Karimova tomonidan ko'rib chiqilgan", premium = true),
        KnowledgeItem("MAQOLA", "OVQATLANISH", "6 daqiqa", "Temirga boy taomlar ro'yxati"),
        KnowledgeItem("VIDEO", "UYQU", "9 daqiqa", "Kechki tartib: 30 daqiqalik amal"),
    )

    val knowledgeCategories = listOf("Barchasi", "Sikl", "Gormonlar", "Ovqatlanish", "Uyqu")

    val dataSources = listOf(
        DataSource("Apple Health", "Apple Watch Series 9", "12:40", SourceStatus.Connected, listOf("Qadamlar", "Puls", "Mashqlar", "Energiya")),
        DataSource("Oura", "Oura Ring Gen3", "07:05", SourceStatus.Connected),
        DataSource("Garmin", null, null, SourceStatus.Expired),
        DataSource("Samsung Health", null, null, SourceStatus.Disconnected),
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
