package uz.sadora.doctor.i18n

import kotlinx.datetime.LocalDate
import uz.sadora.contract.CommunityTopic

object StringsRu : Strings {
    override val language = AppLanguage.Ru
    override val languageName = "Русский"

    override val common = object : CommonStrings {
        override val appName = "Sadora Doctor"
        override val back = "Назад"
        override val retry = "Повторить"
        override val cancel = "Отмена"
        override val saving = "Сохраняем…"
        override val send = "Отправить"
    }

    override val auth = object : AuthStrings {
        override val title = "Sadora для врачей"
        override val subtitle = "Войдите с тем же номером, что и в приложении Sadora. Подайте заявку, " +
            "а после проверки отвечайте на вопросы женщин."
        override val phoneLabel = "Номер телефона"
        override val phoneNote = "Номер нужен только для входа. Мы отправим на него одноразовый код по SMS."
        override val sendCode = "Отправить код"
        override val sending = "Отправляем…"
        override val codeTitle = "Введите код"
        override fun codeSubtitle(phone: String) = "Отправили 6-значный код на +998 $phone."
        override val confirm = "Подтвердить"
        override val checking = "Проверяем…"
        override fun resendIn(seconds: Int) = "Отправить снова · $seconds с"
        override val resend = "Отправить код снова"
        override val changeNumber = "Изменить номер"
        override val codeSecrecy = "Никому не сообщайте код. Сотрудники Sadora его не спрашивают."
        override val devCodeFilled = "Тестовый сервер вернул код — он подставлен автоматически."
        override fun otpEntered(entered: Int, length: Int) = "Код подтверждения: введено $entered из $length"
        override val deleteDigit = "Удалить последнюю цифру"
    }

    override val errors = object : ErrorStrings {
        override val phoneInvalid = "Номер неполный или такого кода оператора нет"
        override val network = "Не удалось подключиться к интернету. Попробуйте ещё раз."
        override val validation = "Введённые данные неверны."
        override val sessionExpired = "Сеанс закончился. Войдите снова."
        override val blocked = "Аккаунт заблокирован. Свяжитесь с Sadora."
        override val forbidden = "У вас нет доступа к этому действию."
        override val notFound = "Не найдено: возможно, это уже удалено."
        override fun retryAfter(seconds: Int) = "Слишком много попыток. Повторите через $seconds с."
        override val retrySoon = "Слишком много попыток. Повторите чуть позже."
        override val otpInvalid = "Код неверный или устарел."
        override val featureClosed = "Этот раздел пока закрыт."
        override val unexpected = "Что-то пошло не так. Попробуйте ещё раз."
    }

    override val dates = object : DateStrings {
        override val months = listOf(
            "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
            "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
        )

        /** The genitive: a day is "4 сентября", never "4 Сентябрь". */
        private val monthsOfDay = listOf(
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря",
        )

        override fun dayMonth(date: LocalDate) = "${date.day} ${monthsOfDay[date.month.ordinal]}"

        override val yesterday = "Вчера"
        override val justNow = "только что"
        override fun minutesAgo(minutes: Int) = "$minutes мин. назад"
        override fun hoursAgo(hours: Int) = "$hours ч. назад"
        override fun daysAgo(days: Int) = "$days дн. назад"
    }

    override val community = object : CommunityStrings {
        override fun topic(topic: CommunityTopic) = when (topic) {
            CommunityTopic.CYCLE -> "Цикл"
            CommunityTopic.PREGNANCY -> "Беременность"
            CommunityTopic.WELLBEING -> "Настроение"
            CommunityTopic.BODY -> "Тело"
        }
        override val you = "вы"
        override val readMore = "…ещё"
        override fun commentsCount(count: Int) = if (count == 0) "Комментарии" else "Комментариев: $count"
        override val noComments = "Комментариев пока нет. Ответьте первой."
        override val questionTitle = "Вопрос"
        override val postTitle = "Пост"
        override val postMissing = "Этот пост удалён или скрыт."
        override val answerHint = "Напишите ответ"
        override val answerSent = "Ответ отправлен"
        override val newPost = "Написать пост"
        override val newPostTitle = "Новый пост"
        override val topicLabel = "Тема"
        override val postHint = "Поделитесь полезным советом или объяснением для женщин…"
        override fun postTooShort(min: Int) = "Не меньше $min символов"
        override val publish = "Опубликовать"
        override val published = "Пост опубликован"
    }

    override val settings = object : SettingsStrings {
        override val title = "Настройки"
        override val language = "Язык"
        override val account = "Аккаунт"
        override fun signedInAs(phone: String) = "Вы вошли с номером $phone"
        override val signOut = "Выйти"
        override val signOutTitle = "Выйти из аккаунта?"
        override val signOutBody = "Чтобы войти снова, понадобится код из SMS."
    }

    override val doctors: DoctorStrings = DoctorStringsRu
}
