package uz.sadora.app.i18n

import uz.sadora.app.model.CyclePhase
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerRelation

object PartnerRu : PartnerStrings {
    override val title = "Мой близкий"
    override val profileRowNote = "Делитесь своим состоянием с мужем или близким человеком"
    override val joinEntry = "Меня пригласил близкий"

    override val introTitle = "Пусть близкий понимает вас лучше"
    override val introBody =
        "Муж, мама или подруга видят на своём телефоне, как вы сегодня: день цикла, " +
            "сколько осталось до месячных и чем вам можно помочь. " +
            "Что показывать, решаете вы, и можете остановить это в любой момент."
    override val whoLabel = "Кто он(а) для вас?"
    override fun relation(relation: PartnerRelation) = when (relation) {
        PartnerRelation.HUSBAND -> "Муж"
        PartnerRelation.MOTHER -> "Мама"
        PartnerRelation.SISTER -> "Сестра"
        PartnerRelation.FRIEND -> "Подруга"
        PartnerRelation.OTHER -> "Другое"
    }
    override val createInvite = "Создать код приглашения"
    override val creating = "Создаём…"
    override val codeTitle = "Отправьте этот код близкому"
    override val codeSteps =
        "Он(а) устанавливает Sadora, нажимает «Меня пригласил близкий» и вводит код. Затем вы даёте разрешение."
    override fun codeExpires(date: String) = "Код действует до $date"
    override val shareCode = "Отправить код"
    override fun shareMessage(code: String, url: String?) =
        "Привет! Хочу делиться с тобой своим состоянием в приложении Sadora. Установи приложение, " +
            "нажми «Меня пригласил близкий» и введи код: $code" + (url?.let { "\n$it" } ?: "")
    override val inviteOut = "Приглашение отправлено"
    override fun inviteOutBody(date: String) =
        "Код действует до $date. Чтобы показать код снова, создайте новый — старый перестанет работать."
    override val newCode = "Новый код"
    override val cancelInvite = "Отменить"
    override fun requestTitle(name: String) = "$name хочет видеть ваше состояние"
    override val requestBody = "Если разрешите, он(а) увидит только то, что вы выбрали."
    override val approve = "Разрешить"
    override val decline = "Отклонить"
    override fun linkedTitle(name: String) = "$name видит ваше состояние"
    override fun pausedTitle(name: String) = "Приостановлено для: $name"
    override fun lastViewed(ago: String) = "Последний просмотр: $ago"
    override val neverViewed = "Ещё не открывал(а)"
    override val showsTitle = "Что я показываю"
    override val permCycle = "Цикл и месячные"
    override val permCycleNote = "День цикла, фаза, начало месячных и когда следующие"
    override val permFertile = "Фертильные дни"
    override val permFertileNote = "Овуляция и фертильное окно"
    override val permMood = "Настроение и энергия"
    override val permMoodNote = "Настроение, которое вы отметили сегодня"
    override val permSymptoms = "Симптомы"
    override val permSymptomsNote = "Названия сегодняшних симптомов"
    override val permPregnancy = "Беременность"
    override val permPregnancyNote = "Неделя и дата родов, потом возраст малыша"
    override val permAppointments = "Визиты к врачу"
    override val permAppointmentsNote = "Дата, время и место — без ваших заметок"
    override val permCare = "Уход по этапу"
    override val permCareNote = "Число кормлений или приливы"
    override val neverTitle = "Никогда не видно"
    override val neverList = listOf(
        "Ваши личные заметки и дневник",
        "Интимная жизнь и контрацепция",
        "Ваш вес",
        "Результат теста настроения",
        "Чаты с ИИ, Чат и консультации врачей",
    )
    override val pause = "Приостановить"
    override val pauseNote = "Он(а) увидит только надпись «приостановлено»"
    override val disconnect = "Отключить"
    override val disconnectConfirmTitle = "Отключить близкого?"
    override fun disconnectConfirmBody(name: String) =
        "$name больше не увидит ваше состояние. Позже можно подключить снова новым кодом."
    override val disconnected = "Отключено"
    override val approved = "Разрешено"
    override val followingTitle = "За кем я слежу"
    override val haveCode = "У меня есть код"
    override val enterCodeTitle = "Введите код приглашения"
    override val codeLabel = "Код"
    override val codeHint = "K7M2-QP4X"
    override val follow = "Подключиться"
    override val statusPending = "Ждёт разрешения"
    override val statusPaused = "Приостановлено"
    override val statusActive = "Активно"
    override fun requestSent(name: String) = "Запрос отправлен — $name должна разрешить"

    override fun pendingTitle(name: String) = "$name ещё не дала разрешение"
    override val pendingBody = "Как только она разрешит, её состояние появится здесь — мы вам сообщим."
    override fun pausedViewTitle(name: String) = "$name временно приостановила доступ"
    override val pausedViewBody = "Когда она включит его снова, состояние опять появится здесь."
    override fun nothingShared(name: String) = "$name пока ничем не делится"
    override val todayHeading = "Сегодня"
    override fun cycleDay(day: Int) = "$day-й день цикла"
    override fun periodDay(day: Int) = "$day-й день месячных"
    override fun periodIn(days: Int) = when {
        days <= 0 -> "Месячные ожидаются сегодня"
        days == 1 -> "Месячные ожидаются завтра"
        else -> "До месячных $days дн."
    }
    override fun periodAround(date: String) = "Примерно $date"
    override fun phaseTitle(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> "Дни месячных"
        CyclePhase.Follicular -> "Энергия растёт"
        CyclePhase.Fertile -> "Пик энергии"
        CyclePhase.Luteal -> "Дни перед месячными"
    }
    override fun phaseFeel(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> "Возможны усталость, боль внизу живота и упадок настроения."
        CyclePhase.Follicular -> "Силы возвращаются, настроение улучшается, хочется новых дел."
        CyclePhase.Fertile -> "Уверенность и энергия на пике."
        CyclePhase.Luteal -> "Возможны усталость, обидчивость, отёки и тяга к сладкому."
    }
    override fun phaseTips(phase: CyclePhase) = when (phase) {
        CyclePhase.Period -> listOf(
            "Предложите горячий чай или грелку",
            "Возьмите на себя часть домашних дел",
            "Будьте терпеливы — перепады настроения естественны",
            "Любимая еда или шоколад станут приятным сюрпризом",
        )
        CyclePhase.Follicular -> listOf(
            "Запланируйте прогулку или спорт вместе",
            "Хорошее время попробовать что-то новое вдвоём",
            "Поддержите её планы и идеи",
        )
        CyclePhase.Fertile -> listOf(
            "Проведите время вместе — свидание или ужин",
            "Уделите ей внимание и скажите тёплые слова",
        )
        CyclePhase.Luteal -> listOf(
            "Больше слушайте, меньше советуйте",
            "Создайте условия для спокойного вечера и хорошего сна",
            "Маленькая забота значит очень много",
            "Если она раздражена, не принимайте на свой счёт",
        )
    }
    override fun fertileWindow(from: String, to: String) = "Фертильные дни: $from – $to"
    override val fertileToday = "Сегодня фертильный день"
    override val estimatedNote = "Даты примерные: они основаны на её записях."
    override val moodLabel = "Настроение"
    override val energyLabel = "Энергия"
    override val symptomsLabel = "Что она чувствует сегодня"
    override fun pregnancyWeek(week: Int) = "$week-я неделя беременности"
    override fun daysToGo(days: Int) = if (days <= 0) "Дата родов наступила" else "До родов $days дн."
    override fun babySize(fruit: String) = "Малыш сейчас размером с $fruit"
    override fun pregnancyTips(week: Int) = when {
        week <= 13 -> listOf(
            "Тошнота и усталость естественны — дайте ей отдохнуть",
            "Держите подальше еду с резким запахом",
            "Ходите вместе на приёмы к врачу",
        )
        week <= 27 -> listOf(
            "Вместе спланируйте детскую и нужные вещи",
            "Вечерние прогулки полезны и ей, и малышу",
            "Если болит спина, предложите лёгкий массаж",
        )
        else -> listOf(
            "Соберите сумку в роддом",
            "Держите телефон включённым и при себе",
            "Тяжёлую работу делайте сами — ей нужен отдых",
            "Заранее узнайте дорогу до роддома",
        )
    }
    override fun babyAge(days: Int) = if (days < 14) "Малышу $days дн." else "Малышу ${days / 7} нед."
    override val postpartumTips = listOf(
        "Вставайте к малышу ночью по очереди — ей нужен сон",
        "Возьмите на себя еду и домашние дела",
        "Если ей грустно, выслушайте и не оставляйте одну",
        "Ограничьте гостей — восстановлению нужно время",
    )
    override val menopauseTips = listOf(
        "Держите в комнате прохладу — приливы переносятся легче",
        "Если сон нарушен, создайте условия для спокойного вечера",
        "Относитесь к перепадам настроения с терпением",
        "Совместные прогулки полезны вам обоим",
    )
    override val appointmentsTitle = "Визиты к врачу"
    override fun feedsToday(count: Int) = "Сегодня кормлений: $count"
    override fun lastFeed(ago: String) = "Последнее $ago"
    override fun hotFlushesToday(count: Int) = "Сегодня приливов: $count"
    override val helpTitle = "Чем помочь ей сегодня"
    override val leave = "Перестать следить"
    override fun leaveConfirmBody(name: String) =
        "Состояние $name больше не будет вам видно. Чтобы подключиться снова, понадобится новый код."

    override val emptyFollowingTitle = "Вы пока ни за кем не следите"
    override val emptyFollowingBody = "Введите код приглашения, который прислал близкий человек."
    override val settingsTitle = "Настройки"
    override val followAnother = "Ввести ещё один код"

    override val joinTitle = "Введите код от близкого человека"
    override val joinSubtitle = "Код из 8 символов, например K7M2-QP4X"
    override val yourNameLabel = "Ваше имя"
    override val yourNameHint = "Например, Азиз"
    override val yourNameNote = "Это имя она увидит в запросе"
    override val joinTermsLead = "Продолжая, вы соглашаетесь с:"
    override val termsLink = "Условиями использования"
    override val privacyLink = "Политикой конфиденциальности"

    override val labourButton = "Сообщить близкому: начались роды"
    override val labourConfirmTitle = "Отправить сообщение близкому?"
    override val labourConfirmBody = "Он(а) сразу получит уведомление «Начались роды!»."
    override val labourSend = "Отправить"
    override val labourSent = "Сообщение отправлено"

    override val messagesTitle = "Сообщения"
    override fun sendTo(name: String) = "Для: $name"
    override fun askFrom(name: String) = "Попросить: $name"
    override fun kind(kind: PartnerMessageKind) = when (kind) {
        PartnerMessageKind.HEART -> "❤️ Сердечко"
        PartnerMessageKind.HUG -> "🤗 Обнимаю"
        PartnerMessageKind.THINKING -> "💭 Думаю о тебе"
        PartnerMessageKind.ON_IT -> "🏃 Уже иду!"
        PartnerMessageKind.DONE -> "✓ Готово"
        PartnerMessageKind.TEA -> "☕ Горячий чай"
        PartnerMessageKind.SWEETS -> "🍫 Сладкое"
        PartnerMessageKind.REST -> "😴 Мне нужен отдых"
        PartnerMessageKind.CALL -> "📞 Позвони"
        PartnerMessageKind.QUIET -> "🤫 Немного тишины"
        PartnerMessageKind.CUSTOM -> "✍️ Своими словами"
    }
    override val youPrefix = "Вы"
    override fun asked(name: String) = "$name просит"
    override val noMessages = "Сообщений пока нет — отправьте первое"
    override val customTitle = "Напишите сообщение"
    override val customHint = "Например, погуляем вечером?"
    override val send = "Отправить"
    override val sent = "Отправлено"
    override fun unreadCount(count: Int) = "Новых сообщений: $count"

    override val webTitle = "Ссылка для тех, у кого нет приложения"
    override val webBody =
        "Близкий человек без приложения увидит ваше состояние в браузере. Ссылка работает несколько дней, её можно отозвать в любой момент."
    override val webShows = "Ссылка показывает:"
    override val webCreate = "Создать ссылку"
    override fun webExpires(date: String) = "Работает до $date"
    override fun webViews(count: Int) = "Открыта раз: $count"
    override val webNever = "Ещё не открывали"
    override val webShare = "Отправить ссылку"
    override val webRevoke = "Отозвать"
    override val webNew = "Новая ссылка"
    override fun webShareMessage(url: String) = "Моё состояние можно посмотреть по этой ссылке (Sadora):\n$url"
    override val webRevoked = "Ссылка отозвана"
    override fun webDays(days: Int) = "$days дн."
    override val webOutBody = "Чтобы показать ссылку снова, создайте новую — старая перестанет работать."

    override val askPartner = "Попросить близкого 💝"
    override val askTitle = "Попросить близкого"
    override val askBodyPremium = "Близкому придёт уведомление. Когда он оплатит, Premium откроется у вас автоматически."
    override val askBodyConsultation = "Близкому придёт уведомление. Когда он оплатит, чат с врачом откроется у вас автоматически."
    override val askPeriod = "Срок"
    override fun period(year: Boolean) = if (year) "1 год" else "1 месяц"
    override val askNoteHint = "Короткая записка (необязательно)"
    override val askSend = "Отправить просьбу"
    override fun premiumWhat(year: Boolean) = "Premium (${period(year)})"
    override fun consultationWhat(doctor: String?) = "Консультация" + (doctor?.let { " · $it" } ?: "")
    override val sentToPartner = "Отправлено близкому 💝"
    override val shareHint = "Ссылку можно отправить и другому близкому. Он сможет оплатить через Payme или Click без приложения."
    override val shareLink = "Поделиться ссылкой"
    override fun shareRequestMessage(what: String, url: String) =
        "Здравствуйте! Хочу $what в Sadora — поможете? 💝\n$url"
    override fun requestStatus(status: uz.sadora.contract.PaymentRequestStatus) = when (status) {
        uz.sadora.contract.PaymentRequestStatus.OPEN -> "Ждём ответа"
        uz.sadora.contract.PaymentRequestStatus.PAID -> "Оплачено 💝"
        else -> "Просьба закрыта"
    }
    override fun requestExpires(date: String) = "Действует до $date"
    override val cancelRequest = "Отменить просьбу"
    override val requestCancelled = "Просьба отменена"

    override fun incomingTitle(name: String) = "$name просит вас о помощи 💝"
    override val incomingBody = "После оплаты всё откроется у неё автоматически."
    override fun giveGift(price: String) = if (price.isBlank()) "Подарить" else "Подарить · $price"
    override fun payWith(provider: String) = "Оплатить через $provider"
    override val payWaiting = "Ждём оплату…"
    override val payReopen = "Открыть страницу оплаты снова"
    override val notNow = "Не сейчас"
    override val giftThanks = "Спасибо! Подарок доставлен 💝"
    override val giftStorePending = "Ждём подтверждения оплаты"
    override val giftNoProvider = "Здесь пока нельзя оплатить"
    override val acceptRequests = "Принимать просьбы об оплате"
    override val acceptRequestsNote = "Если выключить, она не сможет попросить вас об оплате"
}
