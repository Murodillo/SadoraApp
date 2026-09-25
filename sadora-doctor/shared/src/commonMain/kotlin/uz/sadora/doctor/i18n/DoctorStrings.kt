package uz.sadora.doctor.i18n

import uz.sadora.contract.DoctorDocumentKind
import uz.sadora.contract.DoctorSpecialty

/**
 * The doctor role: the check mark, her public page, and the panel she applies and works
 * from. Ported from the client app, where these screens were first written, less the
 * lines only the client's chat needs. The three languages sit beside the interface so a
 * new line is added to all of them at once.
 */
interface DoctorStrings {
    fun specialty(specialty: DoctorSpecialty): String
    fun documentKind(kind: DoctorDocumentKind): String

    // ---- the check mark, and answers in a thread
    val verified: String
    val doctorAnswer: String
    fun answeredBy(count: Int): String
    /** Under a doctor's post and at the top of her page: an answer here is not a diagnosis. */
    val disclaimer: String
    fun writingAs(name: String): String

    // ---- a doctor's page
    val profileTitle: String
    fun verifiedSince(date: String): String
    val statPosts: String
    val statAnswers: String
    /** Under the number of years she has practised. */
    val statYears: String
    val herPosts: String
    val noPosts: String
    val noPostsBody: String

    // ---- the panel
    val panelTitle: String
    val introTitle: String
    val introBody: String
    /** Same length in every language; drawn as bullets. */
    val introPoints: List<String>
    val applyButton: String
    val pendingTitle: String
    val pendingBody: String
    val rejectedTitle: String
    val reapply: String
    val adminNote: String
    val suspendedTitle: String
    val suspendedBody: String
    val approvedTitle: String
    val approvedBody: String
    val myPage: String
    val editTitle: String
    val save: String
    val saved: String
    val questionsTitle: String
    val questionsHint: String
    val questionsEmpty: String
    val questionsEmptyBody: String
    fun submittedOn(date: String): String

    // ---- the application
    val applyTitle: String
    val fullName: String
    val fullNameHint: String
    val specialtyLabel: String
    val workplace: String
    val workplaceHint: String
    val experienceLabel: String
    val license: String
    val bio: String
    val bioHint: String
    val documents: String
    val documentsHint: String
    val addDocument: String
    val remove: String
    val galleryUnavailable: String
    val submit: String
    val submitted: String
    val confirmNote: String
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
    override fun documentKind(kind: DoctorDocumentKind) = when (kind) {
        DoctorDocumentKind.DIPLOMA -> "Diplom"
        DoctorDocumentKind.LICENSE -> "Litsenziya"
        DoctorDocumentKind.OTHER -> "Boshqa"
    }

    override val verified = "Tasdiqlangan shifokor"
    override val doctorAnswer = "Shifokor javobi"
    override fun answeredBy(count: Int) = if (count <= 1) "Shifokor javob berdi" else "$count shifokor javob berdi"
    override val disclaimer = "Shifokorning chatdagi javobi umumiy maslahat, tashxis emas. Shoshilinch holatda 103 ga qo'ng'iroq qiling."
    override fun writingAs(name: String) = "Siz shifokor sifatida yozasiz: $name ✓"

    override val profileTitle = "Shifokor"
    override fun verifiedSince(date: String) = "Tasdiqlangan: $date"
    override val statPosts = "post"
    override val statAnswers = "javob"
    override val statYears = "yil tajriba"
    override val herPosts = "Postlari"
    override val noPosts = "Hali post yozmagan."
    override val noPostsBody = "Birinchi postingizni yozing — u lentada ismingiz va ✓ belgisi bilan chiqadi."

    override val panelTitle = "Shifokor paneli"
    override val introTitle = "Sadora'da shifokor bo'ling"
    override val introBody = "Ayollar savollariga javob bering, maslahatlaringizni ulashing. Tasdiqlangandan keyin Chatdagi postlaringiz va izohlaringiz ismingiz va ✓ belgisi bilan chiqadi."
    override val introPoints = listOf(
        "Diplom va litsenziyangizni Sadora admini tekshiradi",
        "Postlaringiz lentada \"Tasdiqlangan shifokor\" deb ko'rinadi",
        "Javob kutayotgan savollar ro'yxati sizga alohida ko'rsatiladi",
    )
    override val applyButton = "Ariza topshirish"
    override val pendingTitle = "Arizangiz ko'rib chiqilmoqda"
    override val pendingBody = "Admin hujjatlaringizni tekshirib chiqqach, sizga bildirishnoma keladi. Odatda 1–2 ish kuni."
    override val rejectedTitle = "Arizangiz qaytarildi"
    override val reapply = "Qayta topshirish"
    override val adminNote = "Admin izohi"
    override val suspendedTitle = "Shifokor hisobingiz to'xtatilgan"
    override val suspendedBody = "Bu vaqtda postlaringiz lentada ko'rinmaydi. Savollar bo'lsa, Sadora bilan bog'laning."
    override val approvedTitle = "Siz tasdiqlangan shifokorsiz"
    override val approvedBody = "Chatda yozgan post va izohlaringiz ismingiz va ✓ belgisi bilan chiqadi."
    override val myPage = "Mening sahifam"
    override val editTitle = "Profilni tahrirlash"
    override val save = "Saqlash"
    override val saved = "Saqlandi"
    override val questionsTitle = "Javob kutayotgan savollar"
    override val questionsHint = "So'nggi 30 kunda yozilgan va hali shifokor javob bermagan postlar."
    override val questionsEmpty = "Hozircha javobsiz savol yo'q."
    override val questionsEmptyBody = "Yangi savollar paydo bo'lishi bilan shu yerda ko'rinadi."
    override fun submittedOn(date: String) = "Yuborilgan: $date"

    override val applyTitle = "Shifokor arizasi"
    override val fullName = "To'liq ism (F.I.Sh.)"
    override val fullNameHint = "Postlaringizda aynan shu ism ko'rinadi"
    override val specialtyLabel = "Mutaxassislik"
    override val workplace = "Ish joyi"
    override val workplaceHint = "Klinika yoki shifoxona, shahar"
    override val experienceLabel = "Tajriba (yil)"
    override val license = "Litsenziya yoki diplom raqami"
    override val bio = "O'zingiz haqingizda"
    override val bioHint = "Ixtiyoriy: nima bilan shug'ullanasiz, qaysi savollarga javob berasiz"
    override val documents = "Hujjatlar"
    override val documentsHint = "Diplom va litsenziya rasmini yuklang (1–4 ta). Ularni faqat Sadora admini ko'radi."
    override val addDocument = "Rasm qo'shish"
    override val remove = "Olib tashlash"
    override val galleryUnavailable = "Bu qurilmada galereyadan rasm tanlab bo'lmaydi."
    override val submit = "Yuborish"
    override val submitted = "Arizangiz yuborildi"
    override val confirmNote = "Yuborish orqali ma'lumotlaringiz to'g'ri ekanini tasdiqlaysiz."
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
    override fun documentKind(kind: DoctorDocumentKind) = when (kind) {
        DoctorDocumentKind.DIPLOMA -> "Диплом"
        DoctorDocumentKind.LICENSE -> "Лицензия"
        DoctorDocumentKind.OTHER -> "Другое"
    }

    override val verified = "Подтверждённый врач"
    override val doctorAnswer = "Ответ врача"
    override fun answeredBy(count: Int) = if (count <= 1) "Врач ответил" else "Ответили врачи: $count"
    override val disclaimer = "Ответ врача в чате — общий совет, а не диагноз. В экстренном случае звоните 103."
    override fun writingAs(name: String) = "Вы пишете как врач: $name ✓"

    override val profileTitle = "Врач"
    override fun verifiedSince(date: String) = "Подтверждён: $date"
    override val statPosts = "посты"
    override val statAnswers = "ответы"
    override val statYears = "лет практики"
    override val herPosts = "Посты"
    override val noPosts = "Пока нет постов."
    override val noPostsBody = "Напишите первый пост — он появится в ленте с вашим именем и отметкой ✓."

    override val panelTitle = "Панель врача"
    override val introTitle = "Станьте врачом в Sadora"
    override val introBody = "Отвечайте на вопросы женщин и делитесь советами. После проверки ваши посты и комментарии в чате будут подписаны вашим именем и отметкой ✓."
    override val introPoints = listOf(
        "Диплом и лицензию проверяет администратор Sadora",
        "В ленте ваши посты отмечены как «Подтверждённый врач»",
        "Вопросы, ждущие ответа, собраны для вас отдельным списком",
    )
    override val applyButton = "Подать заявку"
    override val pendingTitle = "Заявка на рассмотрении"
    override val pendingBody = "Когда администратор проверит документы, придёт уведомление. Обычно 1–2 рабочих дня."
    override val rejectedTitle = "Заявка возвращена"
    override val reapply = "Подать снова"
    override val adminNote = "Комментарий администратора"
    override val suspendedTitle = "Аккаунт врача приостановлен"
    override val suspendedBody = "Пока он приостановлен, ваши посты не видны в ленте. По вопросам свяжитесь с Sadora."
    override val approvedTitle = "Вы подтверждённый врач"
    override val approvedBody = "Ваши посты и комментарии в чате подписаны вашим именем и отметкой ✓."
    override val myPage = "Моя страница"
    override val editTitle = "Редактировать профиль"
    override val save = "Сохранить"
    override val saved = "Сохранено"
    override val questionsTitle = "Вопросы без ответа"
    override val questionsHint = "Посты за последние 30 дней, на которые ещё не ответил врач."
    override val questionsEmpty = "Сейчас вопросов без ответа нет."
    override val questionsEmptyBody = "Новые вопросы появятся здесь, как только их зададут."
    override fun submittedOn(date: String) = "Отправлено: $date"

    override val applyTitle = "Заявка врача"
    override val fullName = "Полное имя (ФИО)"
    override val fullNameHint = "Именно это имя будет у ваших постов"
    override val specialtyLabel = "Специальность"
    override val workplace = "Место работы"
    override val workplaceHint = "Клиника или больница, город"
    override val experienceLabel = "Стаж (лет)"
    override val license = "Номер лицензии или диплома"
    override val bio = "О себе"
    override val bioHint = "Необязательно: чем занимаетесь, на какие вопросы отвечаете"
    override val documents = "Документы"
    override val documentsHint = "Загрузите фото диплома и лицензии (1–4). Их видит только администратор Sadora."
    override val addDocument = "Добавить фото"
    override val remove = "Убрать"
    override val galleryUnavailable = "На этом устройстве нельзя выбрать фото из галереи."
    override val submit = "Отправить"
    override val submitted = "Заявка отправлена"
    override val confirmNote = "Отправляя заявку, вы подтверждаете, что данные верны."
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
    override fun documentKind(kind: DoctorDocumentKind) = when (kind) {
        DoctorDocumentKind.DIPLOMA -> "Diploma"
        DoctorDocumentKind.LICENSE -> "Licence"
        DoctorDocumentKind.OTHER -> "Other"
    }

    override val verified = "Verified doctor"
    override val doctorAnswer = "Doctor's answer"
    override fun answeredBy(count: Int) = if (count <= 1) "A doctor answered" else "$count doctors answered"
    override val disclaimer = "A doctor's answer in the chat is general advice, not a diagnosis. In an emergency, call 103."
    override fun writingAs(name: String) = "You are writing as a doctor: $name ✓"

    override val profileTitle = "Doctor"
    override fun verifiedSince(date: String) = "Verified since $date"
    override val statPosts = "posts"
    override val statAnswers = "answers"
    override val statYears = "years in practice"
    override val herPosts = "Posts"
    override val noPosts = "No posts yet."
    override val noPostsBody = "Write your first post — it shows in the feed with your name and a ✓."

    override val panelTitle = "Doctor panel"
    override val introTitle = "Join Sadora as a doctor"
    override val introBody = "Answer women's questions and share your advice. Once verified, your posts and comments in the chat carry your name and a ✓."
    override val introPoints = listOf(
        "A Sadora admin checks your diploma and licence",
        "Your posts show in the feed as \"Verified doctor\"",
        "Questions still waiting for an answer are listed for you",
    )
    override val applyButton = "Apply"
    override val pendingTitle = "Your application is being reviewed"
    override val pendingBody = "You'll get a notification once an admin has checked your documents. Usually 1–2 working days."
    override val rejectedTitle = "Your application was returned"
    override val reapply = "Apply again"
    override val adminNote = "Admin's note"
    override val suspendedTitle = "Your doctor account is suspended"
    override val suspendedBody = "While it is, your posts are hidden from the feed. Contact Sadora with any questions."
    override val approvedTitle = "You are a verified doctor"
    override val approvedBody = "Your posts and comments in the chat carry your name and a ✓."
    override val myPage = "My page"
    override val editTitle = "Edit profile"
    override val save = "Save"
    override val saved = "Saved"
    override val questionsTitle = "Questions waiting for an answer"
    override val questionsHint = "Posts from the last 30 days that no doctor has answered yet."
    override val questionsEmpty = "No unanswered questions right now."
    override val questionsEmptyBody = "New questions show up here as soon as they are asked."
    override fun submittedOn(date: String) = "Sent: $date"

    override val applyTitle = "Doctor application"
    override val fullName = "Full name"
    override val fullNameHint = "Your posts will carry exactly this name"
    override val specialtyLabel = "Specialty"
    override val workplace = "Workplace"
    override val workplaceHint = "Clinic or hospital, city"
    override val experienceLabel = "Experience (years)"
    override val license = "Licence or diploma number"
    override val bio = "About you"
    override val bioHint = "Optional: what you do, which questions you answer"
    override val documents = "Documents"
    override val documentsHint = "Upload photos of your diploma and licence (1–4). Only a Sadora admin sees them."
    override val addDocument = "Add photo"
    override val remove = "Remove"
    override val galleryUnavailable = "Picking a photo from the gallery isn't available on this device."
    override val submit = "Submit"
    override val submitted = "Application sent"
    override val confirmNote = "By submitting, you confirm your details are accurate."
}
