package uz.sadora.server.i18n

import uz.sadora.contract.Language

/**
 * The words of an error, in the language the app asked for.
 *
 * Every refusal is written once, in Uzbek, where it is thrown — that is the sentence a
 * reviewer reads next to the rule. This catalogue turns the finished sentence into
 * Russian or English on the way out, so a Russian-speaking woman whose number was
 * mistyped is told so in Russian, and not in a language she chose not to read.
 *
 * Entries are templates: `{0}`, `{1}` stand for whatever the throw site put in — a
 * limit, a name, a date — and are carried across unchanged, in whatever order the
 * target sentence wants them. A sentence with no entry goes out in Uzbek, which is
 * still a true answer; `ErrorTextTest` reads the sources so that does not happen by
 * accident.
 *
 * The staff panels ask for Uzbek, so their messages are left out on purpose.
 */
object ErrorText {

    // First: the entries below are sorted with it while the object initialises.
    private val Placeholder = Regex("""\{(\d)}""")

    fun translate(text: String, language: Language): String {
        if (language == Language.UZ || text.isBlank()) return text
        exact[text]?.let { return it.pick(language) }
        for (template in templated) {
            val match = template.pattern.matchEntire(text) ?: continue
            val captured = match.groupValues.drop(1)
            if (!template.labelled) return template.fill(language, captured)
            // `{0}` names the field; a name nobody translated is not this template.
            val label = labelsByUz[captured[template.labelGroup]] ?: continue
            return template.fill(language, captured, label.pick(language))
        }
        return text
    }

    /** Whether [text] is known in every language — the guard the source test uses. */
    internal fun covers(text: String): Boolean =
        exact.containsKey(text) || templated.any { template ->
            val match = template.pattern.matchEntire(text) ?: return@any false
            !template.labelled || match.groupValues.drop(1)[template.labelGroup] in labelsByUz
        }

    /**
     * What a field is called, for the field-aware rules below: "Dori nomini yozing"
     * says which box is wrong, where "Bo'sh bo'lishi mumkin emas" under a toast did not.
     * A throw site writes the Uzbek name; a name missing here fails the source test.
     */
    internal val labels: List<Entry> = listOf(
        Entry("Ism", "Имя", "Name"),
        Entry("Ism-familiya", "Имя и фамилия", "Full name"),
        Entry("Dori nomi", "Название лекарства", "Medication name"),
        Entry("Doza", "Доза", "Dose"),
        Entry("Birlik", "Единица", "Unit"),
        Entry("Izoh", "Заметка", "Note"),
        Entry("Sabab", "Причина", "Reason"),
        Entry("Xabar", "Сообщение", "Message"),
        Entry("Matn", "Текст", "Text"),
        Entry("Savol", "Вопрос", "Question"),
        Entry("Kundalik yozuvi", "Запись в дневнике", "Journal entry"),
        Entry("Taom tavsifi", "Описание блюда", "Meal description"),
        Entry("Ko'rik nomi", "Название визита", "Visit name"),
        Entry("Ko'rik joyi", "Место визита", "Visit place"),
        Entry("O'zingiz haqingizda", "О себе", "About you"),
        Entry("Ish joyi", "Место работы", "Workplace"),
        Entry("Litsenziya raqami", "Номер лицензии", "Licence number"),
        Entry("Sharh", "Отзыв", "Review"),
        Entry("Tavsiya", "Рекомендация", "Advice"),
    )

    private val labelsByUz: Map<String, Entry> = labels.associateBy { it.uz }

    internal val entries: List<Entry> = listOf(
        // ---------------------------------------------------------------- the envelope
        Entry("So'rov ma'lumotlari noto'g'ri", "Неверные данные запроса", "The request data is invalid"),
        Entry("So'rov tanasi noto'g'ri formatda", "Тело запроса в неверном формате", "The request body is malformed"),
        Entry("So'rov noto'g'ri", "Неверный запрос", "Bad request"),
        Entry("So'rov hajmi juda katta", "Запрос слишком большой", "The request is too large"),
        Entry("So'rov JSON formatida bo'lishi kerak", "Запрос должен быть в формате JSON", "The request must be JSON"),
        Entry("Serverda kutilmagan xatolik", "Непредвиденная ошибка на сервере", "Unexpected server error"),
        Entry("Bunday sahifa yo'q", "Такой страницы нет", "No such page"),
        Entry("Avtorizatsiya talab qilinadi", "Требуется авторизация", "Sign-in required"),
        Entry("Ruxsat yo'q", "Нет доступа", "Access denied"),
        Entry("Bu amal uchun ruxsat yo'q", "Нет доступа к этому действию", "You are not allowed to do this"),
        Entry("Topilmadi", "Не найдено", "Not found"),
        Entry("Juda ko'p so'rov yuborildi", "Слишком много запросов", "Too many requests"),
        Entry("Limit tugadi", "Лимит исчерпан", "Limit reached"),
        Entry("Bu bo'lim hozircha yopiq", "Этот раздел пока закрыт", "This section is closed for now"),
        Entry("Bu funksiya Premium obunada mavjud", "Эта функция доступна в Premium", "This feature is part of Premium"),
        Entry("Konsultatsiya pullik — avval to'lang", "Консультация платная — сначала оплатите", "This consultation is paid — please pay first"),
        Entry(
            "Sog'liq ma'lumotlarini saqlash uchun rozilik kerak",
            "Для хранения данных о здоровье нужно ваше согласие",
            "Your consent is needed to store health data",
        ),
        Entry("{0} hozircha ulanmaydi", "{0} пока нельзя подключить", "{0} cannot be connected yet"),

        // ---------------------------------------------------------------- field-aware rules ({0} is a label above)
        Entry("{0}ni yozing", "Заполните поле «{0}»", "{0} is required", labelled = true),
        Entry("{0} eng ko'pi {1} belgi bo'lsin", "Поле «{0}»: не больше {1} символов", "{0}: {1} characters at most", labelled = true),
        Entry("{0} kamida {1} ta belgi bo'lsin", "Поле «{0}»: не меньше {1} символов", "{0}: at least {1} characters", labelled = true),

        // ---------------------------------------------------------------- shared field rules
        Entry("Bo'sh bo'lishi mumkin emas", "Не может быть пустым", "Cannot be empty"),
        Entry("Ko'rsatilishi shart", "Обязательное поле", "Required"),
        Entry("Manfiy bo'lishi mumkin emas", "Не может быть отрицательным", "Cannot be negative"),
        Entry("Nol bo'lishi mumkin emas", "Не может быть нулём", "Cannot be zero"),
        Entry("Nol yoki noaniq bo'lishi mumkin emas", "Не может быть нулём или неопределённым", "Cannot be zero or undefined"),
        Entry("Noldan katta bo'lishi kerak", "Должно быть больше нуля", "Must be greater than zero"),
        Entry("Butun son bo'lishi kerak", "Должно быть целым числом", "Must be a whole number"),
        Entry("Identifikator noto'g'ri — havolani qaytadan oching", "Неверный идентификатор — откройте ссылку заново", "Invalid ID — open the link again"),
        Entry("Noto'g'ri format", "Неверный формат", "Invalid format"),
        Entry("Noma'lum qiymat: {0}", "Неизвестное значение: {0}", "Unknown value: {0}"),
        Entry("Sana noto'g'ri", "Неверная дата", "Invalid date"),
        Entry("Sana juda eski", "Дата слишком давняя", "The date is too far in the past"),
        Entry("Kelajakdagi sana bo'lishi mumkin emas", "Дата не может быть в будущем", "The date cannot be in the future"),
        Entry("Eng ko'pi {0} belgi", "Не больше {0} символов", "At most {0} characters"),
        Entry("Eng ko'pi {0}", "Не больше {0}", "At most {0}"),
        Entry("Kamida {0} ta belgi", "Не меньше {0} символов", "At least {0} characters"),
        Entry(
            "Chatda telefon raqam yozib bo'lmaydi — shaxsiy xabar orqali yozing",
            "В чате нельзя писать номер телефона — напишите в личные сообщения",
            "Phone numbers can't be posted in the chat — send a private message instead",
        ),
        Entry("Kamida {0} ta belgi bo'lishi kerak", "Должно быть не меньше {0} символов", "Must be at least {0} characters"),
        Entry("{0}–{1} oralig'ida bo'lishi kerak", "Должно быть от {0} до {1}", "Must be between {0} and {1}"),
        Entry("{0}–{1} oralig'ida", "От {0} до {1}", "Between {0} and {1}"),
        Entry("{0}–{1} kun oralig'ida", "От {0} до {1} дней", "Between {0} and {1} days"),
        Entry("{0}–{1} soat oralig'ida", "От {0} до {1} часов", "Between {0} and {1} hours"),
        Entry("{0}–{1} soniya oralig'ida", "От {0} до {1} секунд", "Between {0} and {1} seconds"),
        Entry("{0} dan {1} gacha bo'lishi kerak", "Должно быть от {0} до {1}", "Must be between {0} and {1}"),
        Entry("{0} dan {1} gacha", "От {0} до {1}", "Between {0} and {1}"),
        Entry("Eng ko'pi {0} kunlik oraliq", "Период не больше {0} дней", "A range of at most {0} days"),
        Entry("Eng ko'pi {0} kun", "Не больше {0} дней", "At most {0} days"),
        Entry("Faqat {0} kun", "Только {0} дней", "Only {0} days"),
        Entry("Faqat JPEG yoki PNG", "Только JPEG или PNG", "JPEG or PNG only"),
        Entry("Faqat JPEG yoki PNG rasm", "Только изображение JPEG или PNG", "A JPEG or PNG image only"),
        Entry("Faqat JPEG, PNG yoki WEBP", "Только JPEG, PNG или WEBP", "JPEG, PNG or WEBP only"),
        Entry("Rasm bo'sh", "Изображение пустое", "The image is empty"),
        Entry("Rasm juda katta — kichikroq qilib yuboring", "Изображение слишком большое — отправьте поменьше", "The image is too large — send a smaller one"),
        Entry("Rasm juda kichik", "Изображение слишком маленькое", "The image is too small"),
        Entry("Rasm topilmadi", "Изображение не найдено", "Image not found"),
        Entry("Rasm yo'q", "Изображения нет", "There is no image"),
        Entry("Rasmni o'qib bo'lmadi", "Не удалось прочитать изображение", "The image could not be read"),
        Entry(
            "Rasmni o'qib bo'lmadi. Qaytadan urinib ko'ring yoki qo'lda kiriting.",
            "Не удалось прочитать изображение. Попробуйте ещё раз или введите вручную.",
            "The image could not be read. Try again or enter it by hand.",
        ),
        Entry("Boshlanish sanasi tugashdan keyin bo'lishi mumkin emas", "Дата начала не может быть позже даты окончания", "The start date cannot be after the end date"),
        Entry("Boshlanish tugashdan oldin bo'lsin", "Начало должно быть раньше окончания", "The start must be before the end"),
        Entry("Boshlanishdan oldin tugashi mumkin emas", "Не может закончиться раньше начала", "Cannot end before it starts"),

        // ---------------------------------------------------------------- sign-in and account
        Entry("Raqamni +998 va yana 9 ta raqam bilan kiriting", "Введите номер: +998 и ещё 9 цифр", "Enter the number as +998 followed by 9 digits"),
        Entry("SMSdagi {0} xonali kodni kiriting", "Введите {0}-значный код из SMS", "Enter the {0}-digit code from the SMS"),
        Entry("Harf va raqamdan iborat bo'lishi kerak", "Должно состоять из букв и цифр", "Must contain letters and digits"),
        Entry("Juda ko'p kod so'raldi. Birozdan keyin urinib ko'ring.", "Запрошено слишком много кодов. Попробуйте чуть позже.", "Too many codes requested. Try again a little later."),
        Entry("SMS yuborib bo'lmadi. Birozdan keyin urinib ko'ring.", "Не удалось отправить SMS. Попробуйте чуть позже.", "The SMS could not be sent. Try again a little later."),
        Entry("Juda ko'p urinish. Yangi kod so'rang.", "Слишком много попыток. Запросите новый код.", "Too many attempts. Request a new code."),
        Entry("Kod noto'g'ri — SMSni tekshirib, qayta kiriting", "Неверный код — проверьте SMS и введите снова", "Wrong code — check the SMS and enter it again"),
        Entry("Kod muddati tugadi. Yangi kod so'rang.", "Срок действия кода истёк. Запросите новый код.", "The code has expired. Request a new one."),
        Entry("Kod topilmadi. Yangi kod so'rang.", "Код не найден. Запросите новый код.", "Code not found. Request a new one."),
        Entry("Bu kod allaqachon ishlatilgan. Yangi kod so'rang.", "Этот код уже использован. Запросите новый код.", "This code has already been used. Request a new one."),
        Entry("Seans muddati tugadi. Qaytadan kiring.", "Сеанс истёк. Войдите снова.", "Your session has expired. Please sign in again."),
        Entry("Seans topilmadi. Qaytadan kiring.", "Сеанс не найден. Войдите снова.", "Session not found. Please sign in again."),
        Entry("Seans bekor qilindi. Qaytadan kiring.", "Сеанс завершён. Войдите снова.", "The session was ended. Please sign in again."),
        Entry("Hisob bloklangan", "Аккаунт заблокирован", "The account is blocked"),
        Entry(
            "Hisobni o'chirish so'rovi yuborilgan. Bekor qilish uchun qo'llab-quvvatlash xizmatiga yozing.",
            "Запрошено удаление аккаунта. Чтобы отменить, напишите в поддержку.",
            "Account deletion has been requested. To cancel it, contact support.",
        ),
        Entry("Foydalanuvchi topilmadi", "Пользователь не найден", "User not found"),
        Entry("Kirish ma'lumotini o'qib bo'lmadi. Qaytadan kiring.", "Не удалось прочитать данные входа. Войдите снова.", "Couldn't read the sign-in details. Please sign in again."),
        Entry("Kirish boshqa ilova uchun berilgan. Qaytadan kiring.", "Данные входа выданы другому приложению. Войдите снова.", "This sign-in was issued for another app. Please sign in again."),
        Entry("Kirishni tasdiqlab bo'lmadi. Qaytadan kiring.", "Не удалось подтвердить вход. Войдите снова.", "Couldn't verify the sign-in. Please sign in again."),
        Entry("Faqat Apple yoki Google orqali kirish mumkin", "Войти можно только через Apple или Google", "Only Apple or Google sign-in is supported"),
        Entry("{0} orqali kirish hozircha ishlamayapti. Telefon raqam bilan kiring.", "Вход через {0} пока не работает. Войдите по номеру телефона.", "{0} sign-in isn't available right now. Sign in with your phone number."),
        Entry("'{0}' deb yozing", "Напишите «{0}»", "Type '{0}'"),
        Entry("Noma'lum vaqt mintaqasi", "Неизвестный часовой пояс", "Unknown time zone"),
        Entry("Tug'ilgan yil noto'g'ri", "Неверный год рождения", "Invalid year of birth"),

        // ---------------------------------------------------------------- health records
        Entry("Bu sana uchun hayz allaqachon qayd etilgan", "Месячные на эту дату уже отмечены", "A period is already logged for this date"),
        Entry("Hamma savollarga javob bering", "Ответьте на все вопросы", "Answer every question"),
        Entry("Hayz yozuvi topilmadi", "Запись о месячных не найдена", "Period record not found"),
        Entry("Bu kunlar boshqa hayz yozuvi bilan ustma-ust tushadi", "Эти дни пересекаются с другой записью о месячных", "These days overlap another period you recorded"),
        Entry("Yozuv topilmadi", "Запись не найдена", "Entry not found"),
        Entry("Ovqat topilmadi", "Блюдо не найдено", "Meal not found"),
        Entry("Ko'rik topilmadi", "Визит не найден", "Visit not found"),
        Entry("Dori topilmadi", "Лекарство не найдено", "Medication not found"),
        Entry("Shifokor kuniga {0} marta yozgan — shuncha vaqt tanlang", "Врач назначил {0} раз в день — выберите столько же времени приёма", "The doctor prescribed {0} times a day — choose that many times"),
        Entry("Retsept topilmadi", "Рецепт не найден", "Prescription not found"),
        Entry("Retseptni faqat shifokor yozadi", "Рецепт выписывает только врач", "Only the doctor writes a prescription"),
        Entry("Retseptni faqat uni yozgan shifokor bekor qiladi", "Отменить рецепт может только выписавший его врач", "Only the doctor who wrote it can cancel a prescription"),
        Entry("Sababini yozing", "Укажите причину", "Please give a reason"),
        Entry("Retsept allaqachon bekor qilingan", "Рецепт уже отменён", "The prescription is already cancelled"),
        Entry("Retsept bekor qilingan", "Рецепт отменён", "The prescription was cancelled"),
        Entry("Retsept allaqachon qo'shilgan", "Рецепт уже добавлен", "The prescription is already added"),
        Entry("Kamida bitta dorini tanlang", "Выберите хотя бы одно лекарство", "Choose at least one medication"),
        Entry("Dorilar takrorlanmasligi kerak", "Лекарства не должны повторяться", "Medications must not repeat"),
        Entry("Bugundan {0} kun ichida", "В течение {0} дней от сегодня", "Within {0} days from today"),
        Entry("Bunday dori yo'q", "Такого лекарства нет", "There is no such medication"),
        Entry("Kamida bitta dori kerak", "Нужно хотя бы одно лекарство", "At least one medication is needed"),
        Entry("Dori nomi kerak", "Нужно название лекарства", "The medication name is needed"),
        Entry("Doza kerak", "Нужна доза", "The dose is needed"),
        Entry("Har kuni yoki har N kunda", "Каждый день или раз в N дней", "Every day or every N days"),
                // The staff panel's shop form.
        Entry("Nomi {0} ta belgidan oshmasligi kerak", "Название не должно превышать {0} символов", "The name must be at most {0} characters"),
        Entry("Kamida bitta kun tanlanishi kerak", "Выберите хотя бы один день", "Choose at least one day"),
        Entry("Kamida bitta qabul vaqti kerak", "Нужно хотя бы одно время приёма", "At least one dose time is needed"),
        Entry("Vaqtlar takrorlanmasligi kerak", "Время не должно повторяться", "Times must not repeat"),
        Entry("Kuniga eng ko'pi {0} marta", "Не больше {0} раз в день", "At most {0} times a day"),
        Entry("Bir marta eng ko'pi {0} ml", "Не больше {0} мл за раз", "At most {0} ml at a time"),
        Entry("Bir marta eng ko'pi {0} namuna", "Не больше {0} записей за раз", "At most {0} samples at a time"),
        Entry("Kelajakdagi kun uchun ovqat qo'shib bo'lmaydi", "Нельзя добавить еду на будущий день", "A meal cannot be added for a future day"),
        Entry("Kelajakdagi kun uchun yozuv qo'shib bo'lmaydi", "Нельзя добавить запись на будущий день", "An entry cannot be added for a future day"),
        Entry("Kelajakdagi qabulni belgilab bo'lmaydi", "Нельзя отметить приём в будущем", "A future dose cannot be marked"),
        Entry("Bu vaqtda qabul rejalashtirilmagan", "На это время приём не запланирован", "No dose is scheduled at this time"),
        Entry("Noma'lum alomat: {0}", "Неизвестный симптом: {0}", "Unknown symptom: {0}"),
        Entry("Sokin vaqtning ikkala chegarasi ham kerak", "Нужны обе границы тихого времени", "Quiet hours need both a start and an end"),
        Entry("Kunlik chegaradan kichik bo'lishi mumkin emas", "Не может быть меньше дневного лимита", "Cannot be lower than the daily limit"),
        Entry("Bildirishnoma topilmadi", "Уведомление не найдено", "Notification not found"),
        Entry(
            "Skaner hozircha ishlamayapti. Taomni qo'lda qo'shishingiz mumkin.",
            "Сканер пока не работает. Блюдо можно добавить вручную.",
            "The scanner is not working right now. You can add the meal by hand.",
        ),
        Entry("Havola topilmadi", "Ссылка не найдена", "Link not found"),

        // ---------------------------------------------------------------- Yaqinim
        Entry("Bu hisobda ulashiladigan ma'lumot yo'q", "В этом аккаунте нечем делиться", "This account has nothing to share"),
        Entry(
            "Sizda yaqin allaqachon ulangan — avval uni uzing",
            "У вас уже подключён близкий — сначала отключите его",
            "Someone is already connected — disconnect them first",
        ),
        Entry("So'rov topilmadi", "Запрос не найден", "Request not found"),
        Entry("Yaqin topilmadi", "Близкий не найден", "No one is connected"),
        Entry("Kodni to'liq kiriting — {0} ta belgi", "Введите код полностью — {0} символов", "Enter the full code — {0} characters"),
        Entry("Kod topilmadi yoki muddati o'tgan", "Код не найден или устарел", "The code was not found or has expired"),
        Entry("O'zingizning kodingizni kirita olmaysiz", "Нельзя ввести собственный код", "You cannot enter your own code"),
        Entry("Ulanish hozir faol emas", "Связь сейчас не активна", "The connection is not active right now"),
        Entry("Bugun juda ko'p xabar yuborildi. Ertaga yana yozishingiz mumkin.", "Сегодня отправлено слишком много сообщений. Завтра можно снова.", "Too many messages today. You can write again tomorrow."),
        Entry("Ko'pi bilan {0} kishining holatini ko'ra olasiz", "Можно видеть состояние не больше {0} человек", "You can see updates from up to {0} people"),
        Entry("Hisob faol emas", "Аккаунт не активен", "The account is not active"),

        // ---------------------------------------------------------------- wearables
        Entry("Noma'lum provayder: {0}", "Неизвестный провайдер: {0}", "Unknown provider: {0}"),
        Entry("Noma'lum yoki eskirgan so'rov", "Неизвестный или устаревший запрос", "Unknown or expired request"),
        Entry("Bu ulanish boshqa hisob uchun boshlangan", "Это подключение начато для другого аккаунта", "This connection was started for another account"),
        Entry("Ruxsat kodi qabul qilinmadi", "Код доступа не принят", "The authorization code was not accepted"),
        Entry("Ulanish topilmadi", "Подключение не найдено", "Connection not found"),

        // ---------------------------------------------------------------- chat
        Entry("Qoidalar buzilgani uchun chatda yozish cheklangan", "Из-за нарушения правил писать в чате нельзя", "Because the rules were broken, writing in the chat is restricted"),
        Entry("Qoidalar buzilgani uchun chatda yozish {0}gacha cheklangan", "Из-за нарушения правил писать в чате нельзя до {0}", "Because the rules were broken, you can't write in the chat until {0}"),
        Entry("Bir kunda {0} tadan ko'p post yozib bo'lmaydi", "Нельзя написать больше {0} постов в день", "You cannot write more than {0} posts a day"),
        Entry("Bir kunda {0} tadan ko'p izoh yozib bo'lmaydi", "Нельзя написать больше {0} комментариев в день", "You cannot write more than {0} comments a day"),
        Entry("Bir kunda {0} tadan ko'p xabar yozib bo'lmaydi", "Нельзя написать больше {0} сообщений в день", "You cannot write more than {0} messages a day"),
        Entry("Post topilmadi", "Пост не найден", "Post not found"),
        Entry("Izoh topilmadi", "Комментарий не найден", "Comment not found"),
        Entry("Xabar topilmadi", "Сообщение не найдено", "Message not found"),
        Entry("Suhbat topilmadi", "Переписка не найдена", "Conversation not found"),
        Entry("Taxallus topilmadi", "Псевдоним не найден", "Alias not found"),
        Entry("Taxallus noto'g'ri", "Неверный псевдоним", "Invalid alias"),
        Entry("Taxallus tanlab bo'lmadi, qayta urinib ko'ring", "Не удалось подобрать псевдоним, попробуйте ещё раз", "Could not pick an alias, please try again"),
        Entry("O'zingizga xabar yozib bo'lmaydi", "Нельзя написать сообщение самой себе", "You cannot message yourself"),
        Entry("O'zingizga yozib bo'lmaydi", "Нельзя написать самой себе", "You cannot write to yourself"),
        Entry("O'zingizni bloklab bo'lmaydi", "Нельзя заблокировать саму себя", "You cannot block yourself"),
        Entry("O'z postingizga shikoyat qilib bo'lmaydi", "Нельзя пожаловаться на свой пост", "You cannot report your own post"),
        Entry("O'z izohingizga shikoyat qilib bo'lmaydi", "Нельзя пожаловаться на свой комментарий", "You cannot report your own comment"),
        Entry("Bu post allaqachon shikoyat qilingan", "На этот пост уже пожаловались", "This post has already been reported"),
        Entry("Bu izoh allaqachon shikoyat qilingan", "На этот комментарий уже пожаловались", "This comment has already been reported"),
        Entry("Bu xabar allaqachon shikoyat qilingan", "На это сообщение уже пожаловались", "This message has already been reported"),
        Entry("Shikoyat qiladigan xabar yo'q", "Нет сообщения для жалобы", "There is no message to report"),
        Entry("Shikoyat topilmadi", "Жалоба не найдена", "Report not found"),
        Entry("Xabar shikoyati topilmadi", "Жалоба на сообщение не найдена", "Message report not found"),
        Entry("Bu shikoyat allaqachon ko'rib chiqilgan", "Эта жалоба уже рассмотрена", "This report has already been reviewed"),
        Entry("dismiss yoki hide", "dismiss или hide", "dismiss or hide"),

        // ---------------------------------------------------------------- doctors and consultations
        Entry("Shifokor topilmadi", "Врач не найден", "Doctor not found"),
        Entry("Shifokor profili yo'q", "Профиля врача нет", "There is no doctor profile"),
        Entry("Faqat tasdiqlangan shifokorlar uchun", "Только для подтверждённых врачей", "For verified doctors only"),
        Entry("Siz allaqachon tasdiqlangan shifokorsiz", "Вы уже подтверждённый врач", "You are already a verified doctor"),
        Entry("Arizangiz ko'rib chiqilmoqda", "Ваша заявка на рассмотрении", "Your application is under review"),
        Entry("Avval shifokor arizasini yuboring", "Сначала отправьте заявку врача", "Send your doctor application first"),
        Entry("Hisobingiz to'xtatilgan — Sadora bilan bog'laning", "Ваш аккаунт приостановлен — свяжитесь с Sadora", "Your account is suspended — please contact Sadora"),
        Entry("Kamida bitta hujjat rasmini yuklang", "Загрузите хотя бы одно фото документа", "Upload at least one photo of a document"),
        Entry("Eng ko'pi {0} ta hujjat", "Не больше {0} документов", "At most {0} documents"),
        Entry("Hujjat topilmadi", "Документ не найден", "Document not found"),
        Entry("Sababini yozing — shifokor uni o'qiydi", "Напишите причину — врач её прочитает", "Write the reason — the doctor will read it"),
        Entry("Ariza holati «{0}» — bu amal bajarilmaydi", "Статус заявки «{0}» — это действие недоступно", "The application is «{0}» — this can't be done"),
        Entry("approve, reject, suspend yoki reinstate", "approve, reject, suspend или reinstate", "approve, reject, suspend or reinstate"),
        Entry("Konsultatsiya topilmadi", "Консультация не найдена", "Consultation not found"),
        Entry("Konsultatsiya allaqachon ochiq", "Консультация уже открыта", "The consultation is already open"),
        Entry("Konsultatsiya yopilgan — bemor kartasi endi ko'rinmaydi", "Консультация закрыта — карта пациентки больше не видна", "The consultation is closed — the patient record is no longer visible"),
        Entry("Konsultatsiyani faqat shifokor yopadi", "Закрыть консультацию может только врач", "Only the doctor can close a consultation"),
        Entry("Konsultatsiyani bemor baholaydi", "Консультацию оценивает пациентка", "The patient rates the consultation"),
        Entry("Baholanadigan konsultatsiya yo'q", "Нет консультации для оценки", "There is no consultation to rate"),
        Entry("Siz bu konsultatsiyaga baho qo'ygansiz", "Вы уже оценили эту консультацию", "You've already rated this consultation"),
        Entry("Bu shifokor bilan konsultatsiya bepul", "Консультация с этим врачом бесплатна", "Consultations with this doctor are free"),
        Entry("Shifokor hozir konsultatsiya qabul qilmayapti", "Врач сейчас не принимает консультации", "The doctor is not taking consultations right now"),
        Entry("Shifokor hali javob bermagan", "Врач ещё не ответил", "The doctor has not replied yet"),
        Entry("Bemor kartasi topilmadi", "Карта пациентки не найдена", "Patient record not found"),
        Entry("Bemor kartasini faqat bemorning o'zi shifokoriga biriktiradi", "Карту пациентки прикрепляет своему врачу только она сама", "Only the patient can attach her record for her doctor"),
        Entry("To'lov holati «{0}» — qaytarildi deb belgilab bo'lmaydi", "Статус оплаты «{0}» — нельзя отметить возврат", "The payment is «{0}» — it can't be marked refunded"),
        Entry("Narx 0 (bepul) yoki {0}–{1} so'm oralig'ida bo'lsin", "Цена — 0 (бесплатно) или от {0} до {1} сум", "The price must be 0 (free) or between {0} and {1} UZS"),
        Entry("Summa musbat bo'lsin", "Сумма должна быть положительной", "The amount must be positive"),
        Entry("Matn 1–{0} belgi", "Текст от 1 до {0} символов", "Text of 1 to {0} characters"),
        Entry("Sarlavha 1–60 belgi", "Заголовок от 1 до 60 символов", "A title of 1 to 60 characters"),
        Entry("Eng ko'pi {0} ta tayyor javob", "Не больше {0} быстрых ответов", "At most {0} quick replies"),
        Entry("Hafta kuni 1–7", "День недели от 1 до 7", "Weekday from 1 to 7"),
        Entry("Har kun bir marta", "Каждый день — один раз", "Each day only once"),

        // ---------------------------------------------------------------- rewards, shop and billing
        Entry("Gul yetarli emas", "Недостаточно Gul", "Not enough Gul"),
        Entry("Mahsulot topilmadi", "Товар не найден", "Product not found"),
        Entry("Mahsulot tugadi", "Товар закончился", "The product is sold out"),
        Entry("Xarid topilmadi", "Покупка не найдена", "Purchase not found"),
        Entry("Bunday mahsulot yo'q", "Такого товара нет", "No such product"),
        Entry("Bunday tarif yo'q", "Такого тарифа нет", "No such plan"),
        Entry("Bunday qoida yo'q: {0}", "Такого правила нет: {0}", "No such rule: {0}"),
        Entry("Bunday nishon yo'q", "Такого значка нет", "No such badge"),
        Entry("Bu nishon hali olinmagan", "Этот значок ещё не получен", "This badge has not been earned yet"),
        Entry("Bunday slug allaqachon bor", "Такой slug уже есть", "This slug already exists"),
        Entry("Bo'sh tartib saqlanmaydi", "Пустой порядок не сохраняется", "An empty layout cannot be saved"),
        Entry("Balansdan ko'p ayirib bo'lmaydi", "Нельзя списать больше баланса", "Cannot deduct more than the balance"),
        Entry("Faqat Premium mahsulot uchun", "Только для товара Premium", "For a Premium product only"),
        Entry("Faqat kichik harflar, raqamlar va chiziqcha", "Только строчные буквы, цифры и дефис", "Lowercase letters, digits and hyphens only"),
        Entry("Faqat kichik harf, raqam va chiziqcha", "Только строчные буквы, цифры и дефис", "Lowercase letters, digits and hyphens only"),
        Entry("Kod yaratib bo'lmadi, qayta urinib ko'ring", "Не удалось создать код, попробуйте ещё раз", "Could not create a code, please try again"),
        Entry("Taklif kodini yaratib bo'lmadi, qayta urinib ko'ring", "Не удалось создать код приглашения, попробуйте ещё раз", "Could not create an invite code, please try again"),
        Entry("Narx kerak — chegirma nimadan hisoblanadi", "Нужна цена — от неё считается скидка", "A price is needed — the discount is taken from it"),
        Entry("Nomi bo'sh", "Название пустое", "The name is empty"),
        Entry("Premium uchun kunlar soni kerak", "Для Premium нужно число дней", "Premium needs a number of days"),
        Entry("To'lov topilmadi", "Платёж не найден", "Payment not found"),
        Entry("Chek tasdiqlanmadi", "Чек не подтверждён", "The receipt was not confirmed"),
        Entry("Bu chek allaqachon qayd etilgan", "Этот чек уже учтён", "This receipt has already been recorded"),
        // Requests to pay (Yaqinim).
        Entry("Bu hisobdan so'rov yuborib bo'lmaydi", "С этого аккаунта нельзя отправить просьбу", "This account cannot send a request"),
        Entry("Sizda ochiq so'rov bor", "У вас уже есть открытая просьба", "You already have an open request"),
        Entry("So'rov yopilgan", "Просьба закрыта", "The request is closed"),
        Entry("Konsultatsiya App Store yoki Google Play orqali to'lanmaydi", "Консультация не оплачивается через App Store или Google Play", "A consultation can't be paid through the App Store or Google Play"),
        Entry("Faqat to'langan sovg'ani qaytarish mumkin", "Вернуть можно только оплаченный подарок", "Only a paid gift can be refunded"),
        Entry(
            "Konsultatsiya narxi o'zgargan yoki allaqachon to'langan",
            "Цена консультации изменилась или она уже оплачена",
            "The consultation's price changed or it is already paid",
        ),
        Entry("Bu xarid boshqa hisobga tegishli", "Эта покупка принадлежит другому аккаунту", "This purchase belongs to another account"),
        Entry("Bu to'lov usuli App Store yoki Google Play emas", "Этот способ оплаты — не App Store и не Google Play", "This payment method isn't the App Store or Google Play"),
        Entry("App Store yoki Google Play xaridi ilova ichida bo'ladi", "Покупка через App Store или Google Play делается в приложении", "App Store and Google Play purchases are made in the app"),
        Entry("Bu hamroh sotib olinmagan", "Этот компаньон не куплен", "This companion has not been bought"),
        Entry("Bu hamroh allaqachon sizniki", "Этот компаньон уже ваш", "This companion is already yours"),
        Entry("Narx juda past", "Цена слишком низкая", "The price is too low"),
        Entry("Bunday ramka yo'q", "Такой рамки нет", "No such frame"),
        Entry("Bu ramka Gulga sotilmaydi", "Эта рамка не продаётся за Gul", "This frame is not sold for Gul"),
        Entry("Bu ramka allaqachon sizniki", "Эта рамка уже ваша", "This frame is already yours"),
        Entry("Bu ramka hali sizniki emas", "Эта рамка ещё не ваша", "This frame is not yours yet"),
        Entry("Nishon ramkasi faqat nishon bilan beriladi", "Рамку значка даёт только сам значок", "A badge frame comes only with its badge"),

        // ---------------------------------------------------------------- articles
        Entry("Maqola topilmadi", "Статья не найдена", "Article not found"),
        Entry("Bunday kategoriya yo'q", "Такой категории нет", "No such category"),
        Entry("Bu slug band", "Этот slug занят", "This slug is taken"),
        Entry("Sarlavha bo'sh bo'lmasin", "Заголовок не должен быть пустым", "The title must not be empty"),
    )

    private val exact: Map<String, Entry> = entries.filterNot { it.isTemplate }.associateBy { it.uz }

    /**
     * Most specific first: with the placeholders able to swallow words, "At most {0}"
     * would otherwise answer for "At most {0} characters" and leave "characters" in
     * Uzbek inside the number.
     */
    private val templated: List<Entry> = entries
        .filter { it.isTemplate }
        .sortedByDescending { it.uz.replace(Placeholder, "").length }

    internal class Entry(val uz: String, val ru: String, val en: String, val labelled: Boolean = false) {
        val isTemplate: Boolean get() = Placeholder.containsMatchIn(uz)

        /** Which captured group holds `{0}`, the field's name, in a [labelled] entry. */
        val labelGroup: Int get() = order.indexOf(0)

        val pattern: Regex by lazy {
            val parts = uz.split(Placeholder)
            Regex(parts.joinToString("(.+?)") { Regex.escape(it) })
        }

        /** Which captured group feeds each `{n}`, in the order the Uzbek wrote them. */
        val order: List<Int> by lazy {
            Placeholder.findAll(uz).map { it.groupValues[1].toInt() }.toList()
        }

        fun pick(language: Language): String = when (language) {
            Language.RU -> ru
            Language.EN -> en
            Language.UZ -> uz
        }

        fun fill(language: Language, captured: List<String>, label: String? = null): String {
            val byIndex = order.zip(captured).toMap().let { if (label != null) it + (0 to label) else it }
            return Placeholder.replace(pick(language)) { byIndex[it.groupValues[1].toInt()].orEmpty() }
        }
    }
}

/**
 * The language an app asked for in `Accept-Language`. Only the first tag counts — the
 * apps send exactly one — and anything this API does not speak is Uzbek.
 */
fun errorLanguageOf(header: String?): Language {
    val first = header?.substringBefore(',')?.substringBefore(';')?.trim()?.lowercase().orEmpty()
    return when (first.substringBefore('-')) {
        "ru" -> Language.RU
        "en" -> Language.EN
        else -> Language.UZ
    }
}
