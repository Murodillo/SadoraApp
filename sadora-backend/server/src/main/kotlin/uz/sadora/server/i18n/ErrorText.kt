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
            return template.fill(language, match.groupValues.drop(1))
        }
        return text
    }

    /** Whether [text] is known in every language — the guard the source test uses. */
    internal fun covers(text: String): Boolean =
        exact.containsKey(text) || templated.any { it.pattern.matches(text) }

    internal val entries: List<Entry> = listOf(
        // ---------------------------------------------------------------- the envelope
        Entry("So'rov ma'lumotlari noto'g'ri", "Неверные данные запроса", "The request data is invalid"),
        Entry("So'rov tanasi noto'g'ri formatda", "Тело запроса в неверном формате", "The request body is malformed"),
        Entry("So'rov noto'g'ri", "Неверный запрос", "Bad request"),
        Entry("So'rov hajmi juda katta", "Запрос слишком большой", "The request is too large"),
        Entry("So'rov JSON formatida bo'lishi kerak", "Запрос должен быть в формате JSON", "The request must be JSON"),
        Entry("Serverda kutilmagan xatolik", "Непредвиденная ошибка на сервере", "Unexpected server error"),
        Entry("Bunday endpoint yo'q", "Такого адреса нет", "No such endpoint"),
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

        // ---------------------------------------------------------------- shared field rules
        Entry("Bo'sh bo'lishi mumkin emas", "Не может быть пустым", "Cannot be empty"),
        Entry("Ko'rsatilishi shart", "Обязательное поле", "Required"),
        Entry("Manfiy bo'lishi mumkin emas", "Не может быть отрицательным", "Cannot be negative"),
        Entry("Nol bo'lishi mumkin emas", "Не может быть нулём", "Cannot be zero"),
        Entry("Nol yoki noaniq bo'lishi mumkin emas", "Не может быть нулём или неопределённым", "Cannot be zero or undefined"),
        Entry("Noldan katta bo'lishi kerak", "Должно быть больше нуля", "Must be greater than zero"),
        Entry("Butun son bo'lishi kerak", "Должно быть целым числом", "Must be a whole number"),
        Entry("UUID formatida bo'lishi kerak", "Должно быть в формате UUID", "Must be a UUID"),
        Entry("YYYY-MM-DD formatida bo'lishi kerak", "Должно быть в формате ГГГГ-ММ-ДД", "Must be in YYYY-MM-DD format"),
        Entry("Noto'g'ri format", "Неверный формат", "Invalid format"),
        Entry("Noma'lum qiymat: {0}", "Неизвестное значение: {0}", "Unknown value: {0}"),
        Entry("Sana noto'g'ri", "Неверная дата", "Invalid date"),
        Entry("Sana juda eski", "Дата слишком давняя", "The date is too far in the past"),
        Entry("Kelajakdagi sana bo'lishi mumkin emas", "Дата не может быть в будущем", "The date cannot be in the future"),
        Entry("Eng ko'pi {0} belgi", "Не больше {0} символов", "At most {0} characters"),
        Entry("Eng ko'pi {0}", "Не больше {0}", "At most {0}"),
        Entry("Kamida {0} ta belgi", "Не меньше {0} символов", "At least {0} characters"),
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
        Entry("Faqat {0} kun", "Только {0} дн.", "Only {0} days"),
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
        Entry("O'zbekiston raqami formatida bo'lishi kerak", "Должен быть номер Узбекистана", "Must be an Uzbekistan phone number"),
        Entry("{0} xonali raqam bo'lishi kerak", "Должно быть {0}-значное число", "Must be a {0}-digit number"),
        Entry("Harf va raqamdan iborat bo'lishi kerak", "Должно состоять из букв и цифр", "Must contain letters and digits"),
        Entry("Juda ko'p kod so'raldi. Birozdan keyin urinib ko'ring.", "Запрошено слишком много кодов. Попробуйте чуть позже.", "Too many codes requested. Try again a little later."),
        Entry("SMS yuborib bo'lmadi. Birozdan keyin urinib ko'ring.", "Не удалось отправить SMS. Попробуйте чуть позже.", "The SMS could not be sent. Try again a little later."),
        Entry("Juda ko'p urinish. Yangi kod so'rang.", "Слишком много попыток. Запросите новый код.", "Too many attempts. Request a new code."),
        Entry("Kod noto'g'ri", "Неверный код", "Wrong code"),
        Entry("Kod muddati tugadi", "Срок действия кода истёк", "The code has expired"),
        Entry("Kod topilmadi", "Код не найден", "Code not found"),
        Entry("Bu kod allaqachon ishlatilgan", "Этот код уже использован", "This code has already been used"),
        Entry("Sessiya muddati tugadi", "Срок сессии истёк", "The session has expired"),
        Entry("Sessiya topilmadi", "Сессия не найдена", "Session not found"),
        Entry("Sessiya bekor qilindi. Qaytadan kiring.", "Сессия отменена. Войдите снова.", "The session was revoked. Please sign in again."),
        Entry("Hisob bloklangan", "Аккаунт заблокирован", "The account is blocked"),
        Entry("Hisobni o'chirish so'rovi yuborilgan", "Запрошено удаление аккаунта", "Account deletion has been requested"),
        Entry("Foydalanuvchi topilmadi", "Пользователь не найден", "User not found"),
        Entry("Token o'qib bo'lmadi", "Не удалось прочитать токен", "The token could not be read"),
        Entry("Token manbasi noto'g'ri", "Неверный источник токена", "The token comes from the wrong issuer"),
        Entry("Token tekshiruvdan o'tmadi", "Токен не прошёл проверку", "The token failed verification"),
        Entry("Faqat apple yoki google qo'llab-quvvatlanadi", "Поддерживаются только apple и google", "Only apple or google are supported"),
        Entry("{0} kirish sozlanmagan ({1})", "Вход через {0} не настроен ({1})", "{0} sign-in is not configured ({1})"),
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
        Entry("Tadbir topilmadi", "Событие не найдено", "Event not found"),
        Entry("Dori topilmadi", "Лекарство не найдено", "Medication not found"),
        Entry("Shifokor kuniga {0} marta yozgan", "Врач назначил {0} раз в день", "The doctor prescribed {0} times a day"),
        Entry("Retsept topilmadi", "Рецепт не найден", "Prescription not found"),
        Entry("Retseptni faqat shifokor yozadi", "Рецепт выписывает только врач", "Only the doctor writes a prescription"),
        Entry("Retseptni faqat uni yozgan shifokor bekor qiladi", "Отменить рецепт может только выписавший его врач", "Only the doctor who wrote it can cancel a prescription"),
        Entry("Sababini yozing", "Укажите причину", "Write the reason"),
        Entry("Retsept allaqachon bekor qilingan", "Рецепт уже отменён", "The prescription is already cancelled"),
        Entry("Retsept bekor qilingan", "Рецепт отменён", "The prescription was cancelled"),
        Entry("Retsept allaqachon qo'shilgan", "Рецепт уже добавлен", "The prescription is already added"),
        Entry("Kamida bitta dorini tanlang", "Выберите хотя бы один препарат", "Choose at least one medicine"),
        Entry("Dorilar takrorlanmasligi kerak", "Препараты не должны повторяться", "Medicines must not repeat"),
        Entry("Bugundan {0} kun ichida", "В течение {0} дней от сегодня", "Within {0} days from today"),
        Entry("Bunday dori yo'q", "Такого препарата нет", "There is no such medicine"),
        Entry("Kamida bitta dori kerak", "Нужен хотя бы один препарат", "At least one medicine is needed"),
        Entry("Dori nomi kerak", "Нужно название препарата", "The medicine name is needed"),
        Entry("Doza kerak", "Нужна доза", "The dose is needed"),
        Entry("Har kuni yoki har N kunda", "Каждый день или раз в N дней", "Every day or every N days"),
        Entry("Nomi bo'sh bo'lishi mumkin emas", "Название не может быть пустым", "The name cannot be empty"),
        Entry("Nomi {0} ta belgidan oshmasligi kerak", "Название не должно превышать {0} символов", "The name must be at most {0} characters"),
        Entry("Joyi {0} ta belgidan oshmasligi kerak", "Место не должно превышать {0} символов", "The place must be at most {0} characters"),
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
        Entry("Noma'lum simptom: {0}", "Неизвестный симптом: {0}", "Unknown symptom: {0}"),
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
        Entry("Kod topilmadi yoki muddati o'tgan", "Код не найден или устарел", "The code was not found or has expired"),
        Entry("O'zingizning kodingizni kirita olmaysiz", "Нельзя ввести собственный код", "You cannot enter your own code"),
        Entry("Ulanish hozir faol emas", "Связь сейчас не активна", "The connection is not active right now"),
        Entry("Bugun juda ko'p xabar yuborildi", "Сегодня отправлено слишком много сообщений", "Too many messages sent today"),
        Entry("Ko'pi bilan {0} kishini kuzatish mumkin", "Можно следить не больше чем за {0} людьми", "You can follow at most {0} people"),
        Entry("Hisob faol emas", "Аккаунт не активен", "The account is not active"),

        // ---------------------------------------------------------------- wearables
        Entry("Noma'lum provayder: {0}", "Неизвестный провайдер: {0}", "Unknown provider: {0}"),
        Entry("Noma'lum yoki eskirgan so'rov", "Неизвестный или устаревший запрос", "Unknown or expired request"),
        Entry("Bu ulanish boshqa hisob uchun boshlangan", "Это подключение начато для другого аккаунта", "This connection was started for another account"),
        Entry("Ruxsat kodi qabul qilinmadi", "Код доступа не принят", "The authorization code was not accepted"),
        Entry("Ulanish topilmadi", "Подключение не найдено", "Connection not found"),

        // ---------------------------------------------------------------- chat
        Entry("Chatda yozish vaqtincha cheklangan", "Писать в чате временно нельзя", "Writing in the chat is temporarily restricted"),
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
        Entry("Xabar bo'sh bo'lishi mumkin emas", "Сообщение не может быть пустым", "The message cannot be empty"),
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
        Entry("Sabab ko'rsatilishi shart", "Укажите причину", "A reason is required"),
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
        Entry("Bu holatda ({0}) bu amal bajarilmaydi", "В этом состоянии ({0}) это действие недоступно", "This cannot be done in this state ({0})"),
        Entry("approve, reject, suspend yoki reinstate", "approve, reject, suspend или reinstate", "approve, reject, suspend or reinstate"),
        Entry("Konsultatsiya topilmadi", "Консультация не найдена", "Consultation not found"),
        Entry("Konsultatsiya allaqachon ochiq", "Консультация уже открыта", "The consultation is already open"),
        Entry("Konsultatsiya yopilgan — karta endi ko'rinmaydi", "Консультация закрыта — карта больше не видна", "The consultation is closed — the record is no longer visible"),
        Entry("Konsultatsiyani faqat shifokor yopadi", "Закрыть консультацию может только врач", "Only the doctor can close a consultation"),
        Entry("Konsultatsiyani bemor baholaydi", "Консультацию оценивает пациентка", "The patient rates the consultation"),
        Entry("Baholanadigan konsultatsiya yo'q", "Нет консультации для оценки", "There is no consultation to rate"),
        Entry("Allaqachon baholangansiz", "Вы уже поставили оценку", "You have already rated this"),
        Entry("Bu shifokor bilan konsultatsiya bepul", "Консультация с этим врачом бесплатна", "Consultations with this doctor are free"),
        Entry("Shifokor hozir konsultatsiya qabul qilmayapti", "Врач сейчас не принимает консультации", "The doctor is not taking consultations right now"),
        Entry("Shifokor hali javob bermagan", "Врач ещё не ответил", "The doctor has not replied yet"),
        Entry("Tibbiy karta topilmadi", "Медицинская карта не найдена", "Medical record not found"),
        Entry("Tibbiy kartani faqat bemor shifokoriga biriktiradi", "Медкарту прикрепляет только пациентка своему врачу", "Only the patient attaches her record for her doctor"),
        Entry("Bu holatda ({0}) qaytarib bo'lmaydi", "В этом состоянии ({0}) возврат невозможен", "It cannot be refunded in this state ({0})"),
        Entry("Narx 0 (bepul) yoki {0}–{1} so'm oralig'ida bo'lsin", "Цена — 0 (бесплатно) или от {0} до {1} сум", "The price must be 0 (free) or between {0} and {1} so'm"),
        Entry("Summa musbat bo'lsin", "Сумма должна быть положительной", "The amount must be positive"),
        Entry("Matn 1–{0} belgi", "Текст от 1 до {0} символов", "Text of 1 to {0} characters"),
        Entry("Sarlavha 1–60 belgi", "Заголовок от 1 до 60 символов", "A title of 1 to 60 characters"),
        Entry("Eng ko'pi {0} ta tayyor javob", "Не больше {0} готовых ответов", "At most {0} quick replies"),
        Entry("Hafta kuni 1–7", "День недели от 1 до 7", "Weekday from 1 to 7"),
        Entry("Har kun bir marta", "Каждый день — один раз", "Each day only once"),

        // ---------------------------------------------------------------- rewards, shop and billing
        Entry("Gul yetarli emas", "Недостаточно гулей", "Not enough Gul"),
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
        Entry("Sabab yozilishi shart", "Нужно указать причину", "A reason must be written"),
        Entry("To'lov topilmadi", "Платёж не найден", "Payment not found"),
        Entry("Chek tasdiqlanmadi", "Чек не подтверждён", "The receipt was not confirmed"),
        Entry("Bu chek allaqachon qayd etilgan", "Этот чек уже учтён", "This receipt has already been recorded"),
        // Requests to pay (Yaqinim).
        Entry("Bu hisobdan so'rov yuborib bo'lmaydi", "С этого аккаунта нельзя отправить просьбу", "This account cannot send a request"),
        Entry("Sizda ochiq so'rov bor", "У вас уже есть открытая просьба", "You already have an open request"),
        Entry("So'rov yopilgan", "Просьба закрыта", "The request is closed"),
        Entry("Konsultatsiya store orqali to'lanmaydi", "Консультация не оплачивается через магазин", "A consultation is not paid through the store"),
        Entry("Faqat to'langan sovg'ani qaytarish mumkin", "Вернуть можно только оплаченный подарок", "Only a paid gift can be refunded"),
        Entry(
            "Konsultatsiya narxi o'zgargan yoki allaqachon to'langan",
            "Цена консультации изменилась или она уже оплачена",
            "The consultation's price changed or it is already paid",
        ),
        Entry("Bu xarid boshqa hisobga tegishli", "Эта покупка принадлежит другому аккаунту", "This purchase belongs to another account"),
        Entry("Bu provayder store emas", "Этот провайдер — не магазин приложений", "This provider is not an app store"),
        Entry("Store xaridi ilova ichida bo'ladi", "Покупка в магазине делается внутри приложения", "Store purchases happen inside the app"),
        Entry("Bu hamroh sotib olinmagan", "Этот компаньон не куплен", "This companion has not been bought"),
        Entry("Bu hamroh allaqachon sizniki", "Этот компаньон уже ваш", "This companion is already yours"),
        Entry("Narx juda past", "Цена слишком низкая", "The price is too low"),
        Entry("Bunday ramka yo'q", "Такой рамки нет", "No such frame"),
        Entry("Bu ramka Gulga sotilmaydi", "Эта рамка не продаётся за гули", "This frame is not sold for Gul"),
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

    internal class Entry(val uz: String, val ru: String, val en: String) {
        val isTemplate: Boolean get() = Placeholder.containsMatchIn(uz)

        val pattern: Regex by lazy {
            val parts = uz.split(Placeholder)
            Regex(parts.joinToString("(.+?)") { Regex.escape(it) })
        }

        /** Which captured group feeds each `{n}`, in the order the Uzbek wrote them. */
        private val order: List<Int> by lazy {
            Placeholder.findAll(uz).map { it.groupValues[1].toInt() }.toList()
        }

        fun pick(language: Language): String = when (language) {
            Language.RU -> ru
            Language.EN -> en
            Language.UZ -> uz
        }

        fun fill(language: Language, captured: List<String>): String {
            val byIndex = order.zip(captured).toMap()
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
