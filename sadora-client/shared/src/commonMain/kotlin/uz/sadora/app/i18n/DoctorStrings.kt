package uz.sadora.app.i18n

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

}
