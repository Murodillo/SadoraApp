package uz.sadora.app.i18n

import kotlin.time.Duration
import uz.sadora.contract.DoctorSpecialty

/**
 * Doctors as the chat shows them: the check mark, a doctor's answer, her public page.
 * The panel a doctor applies and works from is in sadora-doctor, with its own strings.
 * Its own file, like [LegalTexts], with the three languages beside the interface so a
 * new line is added to all of them at once.
 */
interface DoctorStrings {
    fun specialty(specialty: DoctorSpecialty): String

    // ---- in the chat
    val verified: String
    val filterChip: String
    val doctorAnswer: String
    fun answeredBy(count: Int): String
    val nothingYet: String
    val nothingYetBody: String
    /** Under a doctor's answer and on her page: an answer here is not a diagnosis. */
    val disclaimer: String
    fun writingAs(name: String): String

    // ---- a doctor's page
    val profileTitle: String
    fun verifiedSince(date: String): String
    val statPosts: String
    val statAnswers: String
    val statExperience: String
    val herPosts: String
    val noPosts: String

    // ---- a consultation: writing to her, and the thread with her
    val messageDoctor: String
    val openConsultation: String
    val viewHistory: String
    /** Under the button: what a consultation costs and how long it lasts. */
    val messageDoctorNote: String
    val cannotMessage: String
    val consentTitle: String
    /**
     * Read before she writes: free for now, a day long, her real name and age are seen,
     * an answer is not a diagnosis, an emergency is 103. In that order.
     */
    val consentPoints: List<String>
    val consentConfirm: String
    val consultationOpen: String
    fun hoursLeft(hours: Int): String
    fun minutesLeft(minutes: Int): String

    /**
     * "23 soat qoldi" — whole hours while there is an hour or more, then minutes, and
     * never "0": a window that is still open has at least a minute in the words too.
     */
    fun timeLeft(remaining: Duration): String {
        val minutes = remaining.inWholeMinutes
        return if (minutes >= 60) hoursLeft((minutes / 60).toInt()) else minutesLeft(minutes.coerceAtLeast(1).toInt())
    }
    val consultationExpired: String
    val consultationClosed: String
    val consultationClosedBody: String
    val reopen: String
    /** In place of the composer while the consultation is shut. */
    val composerClosed: String
    val chipOpen: String
    val chipClosed: String
    /** At the top of a consultation thread. */
    val threadNote: String
    val doctorPage: String
}

object DoctorStringsUz : DoctorStrings {
    override fun specialty(specialty: DoctorSpecialty) = when (specialty) {
        DoctorSpecialty.GYNECOLOGIST -> "Ginekolog"
        DoctorSpecialty.OBSTETRICIAN -> "Akusher"
        DoctorSpecialty.REPRODUCTOLOGIST -> "Reproduktolog"
        DoctorSpecialty.ENDOCRINOLOGIST -> "Endokrinolog"
        DoctorSpecialty.MAMMOLOGIST -> "Mammolog"
        DoctorSpecialty.PSYCHOLOGIST -> "Psixolog"
        DoctorSpecialty.NUTRITIONIST -> "Nutritsiolog"
        DoctorSpecialty.PEDIATRICIAN -> "Pediatr"
        DoctorSpecialty.GENERAL -> "Umumiy amaliyot shifokori"
        DoctorSpecialty.OTHER -> "Boshqa mutaxassis"
    }

    override val verified = "Tasdiqlangan shifokor"
    override val filterChip = "Shifokorlar"
    override val doctorAnswer = "Shifokor javobi"
    override fun answeredBy(count: Int) = if (count <= 1) "Shifokor javob berdi" else "$count shifokor javob berdi"
    override val nothingYet = "Hali shifokor posti yo'q"
    override val nothingYetBody = "Tasdiqlangan shifokorlar yozganda shu yerda ko'rinadi."
    override val disclaimer = "Shifokorning chatdagi javobi umumiy maslahat, tashxis emas. Shoshilinch holatda 103 ga qo'ng'iroq qiling."
    override fun writingAs(name: String) = "Siz shifokor sifatida yozasiz: $name ✓"

    override val profileTitle = "Shifokor"
    override fun verifiedSince(date: String) = "$date dan beri tasdiqlangan"
    override val statPosts = "post"
    override val statAnswers = "javob"
    override val statExperience = "yil tajriba"
    override val herPosts = "Postlari"
    override val noPosts = "Hali post yozmagan."

    override val messageDoctor = "Shifokorga yozish"
    override val openConsultation = "Suhbatni ochish"
    override val viewHistory = "Oldingi suhbatni ko'rish"
    override val messageDoctorNote = "Hozircha bepul · konsultatsiya 24 soat ochiq turadi"
    override val cannotMessage = "Shifokor hozir konsultatsiya qabul qilmayapti."
    override val consentTitle = "Shifokor bilan konsultatsiya"
    override val consentPoints = listOf(
        "Hozircha bepul. Konsultatsiya 24 soat ochiq turadi — keyin yangisini ochishingiz mumkin.",
        "Bu yerda taxallus yo'q: shifokor ismingiz va yoshingizni ko'radi.",
        "Shifokorning javobi tashxis emas. To'liq tekshiruv uchun qabulga boring.",
        "Shoshilinch holatda kutmang — 103 ga qo'ng'iroq qiling.",
    )
    override val consentConfirm = "Tushundim, yozaman"
    override val consultationOpen = "Konsultatsiya ochiq"
    override fun hoursLeft(hours: Int) = "$hours soat qoldi"
    override fun minutesLeft(minutes: Int) = "$minutes daqiqa qoldi"
    override val consultationExpired = "Konsultatsiya muddati tugadi"
    override val consultationClosed = "Shifokor konsultatsiyani yakunladi"
    override val consultationClosedBody = "Yozishni davom ettirish uchun yangi konsultatsiya oching — yana 24 soat, hozircha bepul."
    override val reopen = "Yangi konsultatsiya ochish"
    override val composerClosed = "Konsultatsiya yopilgan — yangisini oching"
    override val chipOpen = "Ochiq"
    override val chipClosed = "Yopiq"
    override val threadNote = "Shifokor ismingiz va yoshingizni ko'radi. Javob tashxis emas; shoshilinch holatda 103 ga qo'ng'iroq qiling."
    override val doctorPage = "Shifokor sahifasi"
}

object DoctorStringsRu : DoctorStrings {
    override fun specialty(specialty: DoctorSpecialty) = when (specialty) {
        DoctorSpecialty.GYNECOLOGIST -> "Гинеколог"
        DoctorSpecialty.OBSTETRICIAN -> "Акушер"
        DoctorSpecialty.REPRODUCTOLOGIST -> "Репродуктолог"
        DoctorSpecialty.ENDOCRINOLOGIST -> "Эндокринолог"
        DoctorSpecialty.MAMMOLOGIST -> "Маммолог"
        DoctorSpecialty.PSYCHOLOGIST -> "Психолог"
        DoctorSpecialty.NUTRITIONIST -> "Нутрициолог"
        DoctorSpecialty.PEDIATRICIAN -> "Педиатр"
        DoctorSpecialty.GENERAL -> "Врач общей практики"
        DoctorSpecialty.OTHER -> "Другой специалист"
    }

    override val verified = "Подтверждённый врач"
    override val filterChip = "Врачи"
    override val doctorAnswer = "Ответ врача"
    override fun answeredBy(count: Int) = if (count <= 1) "Врач ответил" else "Ответили врачи: $count"
    override val nothingYet = "Постов от врачей пока нет"
    override val nothingYetBody = "Когда подтверждённые врачи напишут, посты появятся здесь."
    override val disclaimer = "Ответ врача в чате — общий совет, а не диагноз. В экстренном случае звоните 103."
    override fun writingAs(name: String) = "Вы пишете как врач: $name ✓"

    override val profileTitle = "Врач"
    override fun verifiedSince(date: String) = "Подтверждён с $date"
    override val statPosts = "посты"
    override val statAnswers = "ответы"
    override val statExperience = "лет стажа"
    override val herPosts = "Посты"
    override val noPosts = "Пока нет постов."

    override val messageDoctor = "Написать врачу"
    override val openConsultation = "Открыть переписку"
    override val viewHistory = "Посмотреть прошлую переписку"
    override val messageDoctorNote = "Пока бесплатно · консультация открыта 24 часа"
    override val cannotMessage = "Врач сейчас не принимает консультации."
    override val consentTitle = "Консультация с врачом"
    override val consentPoints = listOf(
        "Пока бесплатно. Консультация открыта 24 часа — потом можно открыть новую.",
        "Здесь нет псевдонима: врач увидит ваше имя и возраст.",
        "Ответ врача — не диагноз. Для полного обследования запишитесь на приём.",
        "В экстренном случае не ждите — звоните 103.",
    )
    override val consentConfirm = "Понятно, написать"
    override val consultationOpen = "Консультация открыта"
    override fun hoursLeft(hours: Int) = "Осталось $hours ч"
    override fun minutesLeft(minutes: Int) = "Осталось $minutes мин"
    override val consultationExpired = "Время консультации истекло"
    override val consultationClosed = "Врач завершил консультацию"
    override val consultationClosedBody = "Чтобы продолжить, откройте новую консультацию — ещё 24 часа, пока бесплатно."
    override val reopen = "Открыть новую консультацию"
    override val composerClosed = "Консультация закрыта — откройте новую"
    override val chipOpen = "Открыта"
    override val chipClosed = "Закрыта"
    override val threadNote = "Врач видит ваше имя и возраст. Ответ — не диагноз; в экстренном случае звоните 103."
    override val doctorPage = "Страница врача"
}

object DoctorStringsEn : DoctorStrings {
    override fun specialty(specialty: DoctorSpecialty) = when (specialty) {
        DoctorSpecialty.GYNECOLOGIST -> "Gynecologist"
        DoctorSpecialty.OBSTETRICIAN -> "Obstetrician"
        DoctorSpecialty.REPRODUCTOLOGIST -> "Fertility specialist"
        DoctorSpecialty.ENDOCRINOLOGIST -> "Endocrinologist"
        DoctorSpecialty.MAMMOLOGIST -> "Breast specialist"
        DoctorSpecialty.PSYCHOLOGIST -> "Psychologist"
        DoctorSpecialty.NUTRITIONIST -> "Nutritionist"
        DoctorSpecialty.PEDIATRICIAN -> "Pediatrician"
        DoctorSpecialty.GENERAL -> "General practitioner"
        DoctorSpecialty.OTHER -> "Other specialist"
    }

    override val verified = "Verified doctor"
    override val filterChip = "Doctors"
    override val doctorAnswer = "Doctor's answer"
    override fun answeredBy(count: Int) = if (count <= 1) "A doctor answered" else "$count doctors answered"
    override val nothingYet = "No posts from doctors yet"
    override val nothingYetBody = "When verified doctors write, their posts show up here."
    override val disclaimer = "A doctor's answer in the chat is general advice, not a diagnosis. In an emergency, call 103."
    override fun writingAs(name: String) = "You are writing as a doctor: $name ✓"

    override val profileTitle = "Doctor"
    override fun verifiedSince(date: String) = "Verified since $date"
    override val statPosts = "posts"
    override val statAnswers = "answers"
    override val statExperience = "years in practice"
    override val herPosts = "Posts"
    override val noPosts = "No posts yet."

    override val messageDoctor = "Message the doctor"
    override val openConsultation = "Open the conversation"
    override val viewHistory = "See the earlier conversation"
    override val messageDoctorNote = "Free for now · a consultation stays open for 24 hours"
    override val cannotMessage = "The doctor is not taking consultations right now."
    override val consentTitle = "A consultation with a doctor"
    override val consentPoints = listOf(
        "Free for now. A consultation stays open for 24 hours — after that you can open a new one.",
        "There is no alias here: the doctor sees your name and age.",
        "A doctor's answer is not a diagnosis. For a full examination, book an appointment.",
        "In an emergency, do not wait — call 103.",
    )
    override val consentConfirm = "Understood, write"
    override val consultationOpen = "Consultation open"
    override fun hoursLeft(hours: Int) = if (hours == 1) "1 hour left" else "$hours hours left"
    override fun minutesLeft(minutes: Int) = if (minutes == 1) "1 minute left" else "$minutes minutes left"
    override val consultationExpired = "The consultation has ended"
    override val consultationClosed = "The doctor closed the consultation"
    override val consultationClosedBody = "To keep writing, open a new consultation — another 24 hours, free for now."
    override val reopen = "Open a new consultation"
    override val composerClosed = "The consultation is closed — open a new one"
    override val chipOpen = "Open"
    override val chipClosed = "Closed"
    override val threadNote = "The doctor sees your name and age. An answer is not a diagnosis; in an emergency, call 103."
    override val doctorPage = "Doctor's page"
}
