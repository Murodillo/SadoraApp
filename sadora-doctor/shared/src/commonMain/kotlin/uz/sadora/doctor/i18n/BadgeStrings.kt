package uz.sadora.doctor.i18n

import uz.sadora.contract.DoctorBadges

/**
 * Her badge board and the unlock moment.
 *
 * The keys and thresholds come from the contract ([DoctorBadges]); only the words are
 * here. A key a newer server sends that this release does not know is skipped by the
 * board rather than shown with a raw name — [name] returns null for it.
 */
interface BadgeStrings {
    val title: String
    /** The line under the board's header. */
    val subtitle: String
    /** "5 / 41" — tiers reached out of all there are. */
    fun earnedOf(earned: Int, total: Int): String
    fun tierName(tier: Int, maxTier: Int): String
    val locked: String
    /** "Keyingisi: Kumush — 12 / 30" */
    fun next(tierName: String, progress: Int, target: Int): String
    val allDone: String
    /** The note under the board and in the detail sheet: what badges are for. */
    val principle: String

    // ---- the unlock overlay ----
    val newBadge: String
    /** "Kumush bosqich" */
    fun tierReached(tierName: String): String
    val continueLabel: String
    /** "Yana 3 ta nishon" — on the overlay when several arrived at once. */
    fun more(count: Int): String
    val skipAll: String

    // ---- the badges page ----
    val earnedSection: String
    val lockedSection: String

    fun name(key: String): String?
    /** What a tier asks for, with its threshold already in it. */
    fun goal(key: String, target: Int): String
}

object BadgeStringsUz : BadgeStrings {
    override val title = "Nishonlar"
    override val subtitle = "Qilgan ishingiz uchun — daromad uchun emas"
    override fun earnedOf(earned: Int, total: Int) = "$earned / $total"
    override fun tierName(tier: Int, maxTier: Int) = when {
        maxTier == 1 -> "Maxsus"
        tier <= 1 -> "Bronza"
        tier == 2 -> "Kumush"
        else -> "Oltin"
    }
    override val locked = "Hali ochilmagan"
    override fun next(tierName: String, progress: Int, target: Int) = "Keyingisi: $tierName — $progress / $target"
    override val allDone = "Barcha bosqichlar olindi!"
    override val principle =
        "Nishonlar qilgan ishingiz uchun beriladi — javoblar, konsultatsiyalar, bemorlarga g'amxo'rlik. Daromad yoki narx uchun nishon yo'q."
    override val newBadge = "Yangi nishon!"
    override fun tierReached(tierName: String) = "$tierName bosqich"
    override val continueLabel = "Ajoyib!"
    override fun more(count: Int) = "Yana $count ta nishon"
    override val skipAll = "Hammasini yopish"
    override val earnedSection = "Olingan"
    override val lockedSection = "Keyingi maqsadlar"

    override fun name(key: String): String? = when (key) {
        DoctorBadges.VERIFIED -> "Tasdiqlangan shifokor"
        DoctorBadges.PHOTO -> "Tanish yuz"
        DoctorBadges.ANSWERS -> "Savollarga javob"
        DoctorBadges.POSTS -> "Muallif"
        DoctorBadges.CONSULTS -> "Konsultant"
        DoctorBadges.PATIENTS -> "Bemorlar ishonchi"
        DoctorBadges.FAST_REPLY -> "Tezkor javob"
        DoctorBadges.MESSAGES -> "Suhbatdosh"
        DoctorBadges.RATED -> "Baholangan"
        DoctorBadges.FIVE_STARS -> "Besh yulduz"
        DoctorBadges.RECORDS -> "Diqqatli shifokor"
        DoctorBadges.NOTES -> "Eslatmalar"
        DoctorBadges.QUICK_REPLIES -> "Tayyor javoblar"
        DoctorBadges.THANKED -> "Minnatdorlik"
        DoctorBadges.TENURE -> "Sadoqat"
        else -> null
    }

    override fun goal(key: String, target: Int): String = when (key) {
        DoctorBadges.VERIFIED -> "Sadora sizni shifokor sifatida tasdiqladi"
        DoctorBadges.PHOTO -> "Sahifangizga rasm qo'ying"
        DoctorBadges.ANSWERS -> "Chatda $target ta savolga javob bering"
        DoctorBadges.POSTS -> "$target ta post yozing"
        DoctorBadges.CONSULTS -> "$target ta konsultatsiya o'tkazing"
        DoctorBadges.PATIENTS -> "$target nafar bemor bilan ishlang"
        DoctorBadges.FAST_REPLY -> "$target ta konsultatsiyada birinchi javobni 1 soat ichida bering"
        DoctorBadges.MESSAGES -> "Konsultatsiyalarda $target ta xabar yozing"
        DoctorBadges.RATED -> "Bemorlardan $target ta baho oling"
        DoctorBadges.FIVE_STARS -> "$target ta 5 yulduzli baho oling"
        DoctorBadges.RECORDS -> "Bemorlarning $target ta yozuvini oching"
        DoctorBadges.NOTES -> "$target nafar bemorga eslatma yozing"
        DoctorBadges.QUICK_REPLIES -> "$target ta tayyor javob saqlang"
        DoctorBadges.THANKED -> "Postlaringiz $target ta yoqtirish olsin"
        DoctorBadges.TENURE -> "Sadorada $target kun"
        else -> ""
    }
}

object BadgeStringsRu : BadgeStrings {
    override val title = "Награды"
    override val subtitle = "За то, что вы делаете, — не за доход"
    override fun earnedOf(earned: Int, total: Int) = "$earned / $total"
    override fun tierName(tier: Int, maxTier: Int) = when {
        maxTier == 1 -> "Особая"
        tier <= 1 -> "Бронза"
        tier == 2 -> "Серебро"
        else -> "Золото"
    }
    override val locked = "Ещё не открыта"
    override fun next(tierName: String, progress: Int, target: Int) = "Следующая: $tierName — $progress / $target"
    override val allDone = "Все ступени пройдены!"
    override val principle =
        "Награды даются за работу — ответы, консультации, заботу о пациентах. Наград за доход или цену нет."
    override val newBadge = "Новая награда!"
    override fun tierReached(tierName: String) = "Ступень: $tierName"
    override val continueLabel = "Отлично!"
    override fun more(count: Int) = "Ещё ${plural(count, "награда", "награды", "наград")}"
    override val skipAll = "Закрыть все"
    override val earnedSection = "Получены"
    override val lockedSection = "Следующие цели"

    override fun name(key: String): String? = when (key) {
        DoctorBadges.VERIFIED -> "Подтверждённый врач"
        DoctorBadges.PHOTO -> "Знакомое лицо"
        DoctorBadges.ANSWERS -> "Ответы на вопросы"
        DoctorBadges.POSTS -> "Автор"
        DoctorBadges.CONSULTS -> "Консультант"
        DoctorBadges.PATIENTS -> "Доверие пациентов"
        DoctorBadges.FAST_REPLY -> "Быстрый ответ"
        DoctorBadges.MESSAGES -> "Собеседник"
        DoctorBadges.RATED -> "Есть отзывы"
        DoctorBadges.FIVE_STARS -> "Пять звёзд"
        DoctorBadges.RECORDS -> "Внимательный врач"
        DoctorBadges.NOTES -> "Заметки"
        DoctorBadges.QUICK_REPLIES -> "Готовые ответы"
        DoctorBadges.THANKED -> "Благодарность"
        DoctorBadges.TENURE -> "Верность"
        else -> null
    }

    override fun goal(key: String, target: Int): String = when (key) {
        DoctorBadges.VERIFIED -> "Sadora подтвердила вас как врача"
        DoctorBadges.PHOTO -> "Поставьте фото на свою страницу"
        DoctorBadges.ANSWERS -> "Ответьте в чате на ${plural(target, "вопрос", "вопроса", "вопросов")}"
        DoctorBadges.POSTS -> "Напишите ${plural(target, "пост", "поста", "постов")}"
        DoctorBadges.CONSULTS -> "Проведите ${plural(target, "консультацию", "консультации", "консультаций")}"
        DoctorBadges.PATIENTS -> "Примите ${plural(target, "пациента", "пациентов", "пациентов")}"
        DoctorBadges.FAST_REPLY -> "Ответьте в течение часа в ${plural(target, "консультации", "консультациях", "консультациях")}"
        DoctorBadges.MESSAGES -> "Напишите в консультациях ${plural(target, "сообщение", "сообщения", "сообщений")}"
        DoctorBadges.RATED -> "Получите ${plural(target, "отзыв", "отзыва", "отзывов")} от пациентов"
        DoctorBadges.FIVE_STARS -> "Получите ${plural(target, "оценку", "оценки", "оценок")} «5»"
        DoctorBadges.RECORDS -> "Откройте ${plural(target, "медкарту", "медкарты", "медкарт")} пациентов"
        DoctorBadges.NOTES -> "Ведите заметки о ${plural(target, "пациенте", "пациентах", "пациентах")}"
        DoctorBadges.QUICK_REPLIES -> "Сохраните ${plural(target, "готовый ответ", "готовых ответа", "готовых ответов")}"
        DoctorBadges.THANKED -> "Соберите ${plural(target, "отметку", "отметки", "отметок")} «нравится» на постах"
        DoctorBadges.TENURE -> "${plural(target, "день", "дня", "дней")} в Sadora"
        else -> ""
    }

    /** "5 вопросов", "21 вопрос", "23 вопроса". */
    private fun plural(n: Int, one: String, few: String, many: String): String {
        val tens = n % 100
        val word = when {
            tens in 11..14 -> many
            n % 10 == 1 -> one
            n % 10 in 2..4 -> few
            else -> many
        }
        return "$n $word"
    }
}

object BadgeStringsEn : BadgeStrings {
    override val title = "Badges"
    override val subtitle = "For the work you do — never for what you earn"
    override fun earnedOf(earned: Int, total: Int) = "$earned / $total"
    override fun tierName(tier: Int, maxTier: Int) = when {
        maxTier == 1 -> "Special"
        tier <= 1 -> "Bronze"
        tier == 2 -> "Silver"
        else -> "Gold"
    }
    override val locked = "Not yet unlocked"
    override fun next(tierName: String, progress: Int, target: Int) = "Next: $tierName — $progress / $target"
    override val allDone = "Every tier reached!"
    override val principle =
        "Badges are for the work you do — answers, consultations, care for your patients. There is no badge for income or price."
    override val newBadge = "New badge!"
    override fun tierReached(tierName: String) = "$tierName tier"
    override val continueLabel = "Wonderful!"
    override fun more(count: Int) = if (count == 1) "1 more badge" else "$count more badges"
    override val skipAll = "Close all"
    override val earnedSection = "Earned"
    override val lockedSection = "Next goals"

    override fun name(key: String): String? = when (key) {
        DoctorBadges.VERIFIED -> "Verified doctor"
        DoctorBadges.PHOTO -> "A familiar face"
        DoctorBadges.ANSWERS -> "Answers"
        DoctorBadges.POSTS -> "Author"
        DoctorBadges.CONSULTS -> "Consultant"
        DoctorBadges.PATIENTS -> "Patients' trust"
        DoctorBadges.FAST_REPLY -> "Quick to answer"
        DoctorBadges.MESSAGES -> "In conversation"
        DoctorBadges.RATED -> "Reviewed"
        DoctorBadges.FIVE_STARS -> "Five stars"
        DoctorBadges.RECORDS -> "Attentive"
        DoctorBadges.NOTES -> "Note keeper"
        DoctorBadges.QUICK_REPLIES -> "Ready answers"
        DoctorBadges.THANKED -> "Thanked"
        DoctorBadges.TENURE -> "With Sadora"
        else -> null
    }

    override fun goal(key: String, target: Int): String = when (key) {
        DoctorBadges.VERIFIED -> "Sadora verified you as a doctor"
        DoctorBadges.PHOTO -> "Put a photo on your page"
        DoctorBadges.ANSWERS -> "Answer $target questions in the Chat"
        DoctorBadges.POSTS -> if (target == 1) "Write your first post" else "Write $target posts"
        DoctorBadges.CONSULTS -> if (target == 1) "Hold your first consultation" else "Hold $target consultations"
        DoctorBadges.PATIENTS -> "See $target patients"
        DoctorBadges.FAST_REPLY -> "Reply within an hour in $target consultations"
        DoctorBadges.MESSAGES -> "Write $target messages in consultations"
        DoctorBadges.RATED -> "Receive $target reviews from patients"
        DoctorBadges.FIVE_STARS -> "Receive $target five-star ratings"
        DoctorBadges.RECORDS -> if (target == 1) "Open a patient's record" else "Open $target patient records"
        DoctorBadges.NOTES -> "Keep notes on $target patients"
        DoctorBadges.QUICK_REPLIES -> if (target == 1) "Save a quick reply" else "Save $target quick replies"
        DoctorBadges.THANKED -> "Get $target likes on your posts"
        DoctorBadges.TENURE -> "$target days with Sadora"
        else -> ""
    }
}
