package uz.sadora.doctor.i18n

/**
 * Her profile photo: asking for it, the guidance beside the picker, changing and
 * removing it. The three languages sit beside the interface, as the work strings do.
 */
interface PhotoStrings {
    /** The Home card and the one-time sheet. */
    val askTitle: String
    val askBody: String
    /** What a photo should be, and that staff may take an unsuitable one down. */
    val guidance: String
    val choose: String
    val change: String
    val remove: String
    val fromGallery: String
    val takePhoto: String
    val later: String
    val uploading: String
    val saved: String
    val removed: String
    val removeTitle: String
    val removeBody: String
    /** The Profile tab's card. */
    val sectionTitle: String
    /** The step after the application form. */
    val afterApplyTitle: String
    val afterApplyBody: String
    val done: String
    /** What a screen reader hears for a photo of [name]. */
    fun photoOf(name: String): String
}

object PhotoStringsUz : PhotoStrings {
    override val askTitle = "Rasmingizni qo'ying"
    override val askBody = "Bemorlar rasmli shifokorga ko'proq ishonadi va yozadi."
    override val guidance =
        "Yuzingiz aniq ko'rinadigan, professional rasm. SADORA xodimlari mos bo'lmagan rasmni olib tashlashi mumkin."
    override val choose = "Rasm tanlash"
    override val change = "Rasmni o'zgartirish"
    override val remove = "O'chirish"
    override val fromGallery = "Galereyadan tanlash"
    override val takePhoto = "Suratga olish"
    override val later = "Keyinroq"
    override val uploading = "Yuklanmoqda…"
    override val saved = "Rasmingiz saqlandi"
    override val removed = "Rasmingiz o'chirildi"
    override val removeTitle = "Rasmni o'chirasizmi?"
    override val removeBody = "Sahifangizda rasm o'rniga ismingizning bosh harflari ko'rinadi."
    override val sectionTitle = "Rasmingiz"
    override val afterApplyTitle = "Arizangiz yuborildi"
    override val afterApplyBody =
        "Endi rasmingizni qo'ying: tasdiqlangach, bemorlar sahifangizda va javoblaringiz yonida uni ko'radi."
    override val done = "Tayyor"
    override fun photoOf(name: String) = "$name rasmi"
}

object PhotoStringsRu : PhotoStrings {
    override val askTitle = "Добавьте своё фото"
    override val askBody = "Пациентки больше доверяют врачу с фото и чаще ему пишут."
    override val guidance =
        "Профессиональное фото, на котором хорошо видно лицо. Сотрудники SADORA могут удалить неподходящее фото."
    override val choose = "Выбрать фото"
    override val change = "Изменить фото"
    override val remove = "Удалить"
    override val fromGallery = "Выбрать из галереи"
    override val takePhoto = "Сделать снимок"
    override val later = "Позже"
    override val uploading = "Загрузка…"
    override val saved = "Фото сохранено"
    override val removed = "Фото удалено"
    override val removeTitle = "Удалить фото?"
    override val removeBody = "Вместо фото на вашей странице будут видны инициалы."
    override val sectionTitle = "Ваше фото"
    override val afterApplyTitle = "Заявка отправлена"
    override val afterApplyBody =
        "Теперь добавьте фото: после одобрения пациентки увидят его на вашей странице и рядом с вашими ответами."
    override val done = "Готово"
    override fun photoOf(name: String) = "Фото: $name"
}

object PhotoStringsEn : PhotoStrings {
    override val askTitle = "Add your photo"
    override val askBody = "Patients trust a doctor with a photo more, and write more often."
    override val guidance =
        "A professional photo with your face clearly visible. SADORA staff may remove a photo that is not suitable."
    override val choose = "Choose a photo"
    override val change = "Change photo"
    override val remove = "Remove"
    override val fromGallery = "Choose from gallery"
    override val takePhoto = "Take a photo"
    override val later = "Later"
    override val uploading = "Uploading…"
    override val saved = "Your photo is saved"
    override val removed = "Your photo is removed"
    override val removeTitle = "Remove your photo?"
    override val removeBody = "Your page will show your initials instead."
    override val sectionTitle = "Your photo"
    override val afterApplyTitle = "Application sent"
    override val afterApplyBody =
        "Now add your photo: once you are approved, patients will see it on your page and next to your answers."
    override val done = "Done"
    override fun photoOf(name: String) = "Photo of $name"
}
