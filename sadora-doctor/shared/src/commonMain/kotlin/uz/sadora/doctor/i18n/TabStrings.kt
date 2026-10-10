package uz.sadora.doctor.i18n

import uz.sadora.contract.CyclePhase
import uz.sadora.contract.FlowLevel
import uz.sadora.contract.HealthMetric
import uz.sadora.contract.LifeStage
import uz.sadora.contract.ReportReason

/**
 * The approved doctor's five tabs — Home, Messages, Scan, Chat, Profile — and the patient
 * record a scanned QR code opens. The three languages sit beside the interface, as the
 * doctor strings do, so a new line is added to all of them at once.
 */
interface TabStrings {
    // ---- the bar
    val home: String
    val messages: String
    val scan: String
    val community: String
    val profile: String

    // ---- Home
    /** By the hour on her phone: 0–23. */
    fun greeting(hour: Int): String
    val homeSubtitle: String
    val statWaiting: String
    val statUnread: String
    val scanPatient: String
    val scanPatientBody: String
    val seeAll: String

    // ---- Messages
    val messagesEmpty: String
    val messagesEmptyBody: String
    val messageHint: String
    val conversationClosed: String
    fun unread(count: Int): String
    /** An open consultation, with the time left in its window. */
    fun consultationOpen(hoursLeft: Int, minutesLeft: Int): String
    val consultationClosed: String
    val consultationClosedBody: String
    val closeConsultation: String
    val closeConfirmTitle: String
    val closeConfirmBody: String
    val closeConfirm: String
    val typing: String
    val photo: String
    val record: String
    val recordCardTitle: String
    val recordCardBody: String
    val viewRecord: String
    val recordClosed: String
    val attachPhoto: String
    val photoFailed: String
    val report: String
    val reportTitle: String
    fun reportReason(reason: ReportReason): String
    val reportSent: String
    val sent: String
    val read: String
    val namesNotice: String
    val acceptsTitle: String
    val acceptsBody: String

    // ---- Scan
    val scanTitle: String
    val scanHint: String
    val cameraStarting: String
    val cameraDenied: String
    val openSettings: String
    val cameraMissing: String
    val notPatientCode: String
    val pasteLabel: String
    val pastePlaceholder: String
    val open: String
    val recentTitle: String
    val recentNote: String

    // ---- Chat
    val communityTitle: String
    val filterAll: String
    val filterDoctors: String
    val feedEmpty: String
    val feedEmptyBody: String

    // ---- the patient record
    val recordTitle: String
    val recordGone: String
    val recordGoneBody: String
    val recordDisclaimer: String
    fun generatedAt(time: String): String
    fun age(years: Int): String
    val heightLabel: String
    val weightLabel: String
    fun cm(value: Int): String
    fun kg(value: Int): String
    fun days(count: Int): String
    fun lifeStage(stage: LifeStage): String
    fun memberSince(date: String): String
    val nothingYet: String

    val cycleTitle: String
    val cycleDay: String
    val phaseLabel: String
    fun phase(phase: CyclePhase): String
    val lastPeriod: String
    val averageCycle: String
    val averagePeriod: String
    val cycleRange: String
    val nextPeriod: String
    val estimated: String

    val pregnancyTitle: String
    val weekLabel: String
    val dueDate: String
    val birthDate: String
    fun lessMovement(days: Int): String

    // ---- the stage tools
    val feedingTitle: String
    fun lastDays(days: Int): String
    fun lastHours(hours: Int): String
    val feedsPerDay: String
    val breastFeeds: String
    fun averageMinutes(minutes: Int): String
    val bottleFeeds: String
    val lastOne: String
    val kicksTitle: String
    fun kicksResult(kicks: Int, duration: String): String
    val kicksSlow: String
    val contractionsTitle: String
    val count: String
    val averageLength: String
    val averageInterval: String
    val hotFlushTitle: String
    val perDay: String
    val strong: String
    fun trigger(trigger: uz.sadora.contract.HotFlushTrigger): String
    val epdsTitle: String
    fun epdsResult(score: Int): String
    val epdsSelfHarm: String

    val symptomsTitle: String
    fun symptomsWindow(days: Int): String

    val recentDaysTitle: String
    fun flow(level: FlowLevel): String

    val mindTitle: String
    val daysLogged: String
    val averageMood: String
    val averageEnergy: String
    val averageStress: String

    val medsTitle: String
    fun adherence(percent: Int): String
    val medFinished: String

    val appointmentsTitle: String

    val nutritionTitle: String
    val averageKcal: String
    val averageWater: String

    val wearableTitle: String
    /** Null for the metrics the record does not show. */
    fun metric(metric: HealthMetric): String?
    /** The unit after a value of [metric]; sleep is in minutes, as the server sends it. */
    fun unit(metric: HealthMetric): String
    fun kcal(value: Int): String
    fun ml(value: Int): String
    /** A night's sleep, which the server counts in minutes. */
    fun hoursMinutes(minutes: Int): String
}

object TabStringsUz : TabStrings {
    override val home = "Bosh sahifa"
    override val messages = "Xabarlar"
    override val scan = "Skaner"
    override val community = "Hamjamiyat"
    override val profile = "Profil"

    override fun greeting(hour: Int) = when (hour) {
        in 5..11 -> "Xayrli tong"
        in 12..17 -> "Xayrli kun"
        else -> "Xayrli kech"
    }
    override val homeSubtitle = "Bugun sizni kutayotganlar"
    override val statWaiting = "Javob kutayotgan savollar"
    override val statUnread = "O'qilmagan xabarlar"
    override val scanPatient = "Bemorni skanerlash"
    override val scanPatientBody = "Bemor ko'rsatgan QR kod orqali uning sikli, alomatlari, dorilari va ko'rsatkichlarini oching."
    override val seeAll = "Barchasi"

    override val messagesEmpty = "Hozircha xabarlar yo'q"
    override val messagesEmptyBody = "Bemor sahifangizdan konsultatsiya ochganda suhbat shu yerda paydo bo'ladi."
    override val messageHint = "Xabar yozing…"
    override val conversationClosed = "Bu suhbat yopilgan: yangi xabar yuborib bo'lmaydi."
    override fun unread(count: Int) = "$count ta yangi"
    override fun consultationOpen(hoursLeft: Int, minutesLeft: Int) = "Ochiq · ${if (hoursLeft > 0) "$hoursLeft soat " else ""}$minutesLeft daqiqa qoldi"
    override val consultationClosed = "Yopilgan"
    override val consultationClosedBody = "Konsultatsiya yopilgan. Bemor yangisini ochsa, yana yozishingiz mumkin."
    override val closeConsultation = "Konsultatsiyani yakunlash"
    override val closeConfirmTitle = "Konsultatsiya yakunlansinmi?"
    override val closeConfirmBody = "Endi na siz, na bemor yoza olmaydi, biriktirilgan karta ham yopiladi. Bemor xohlasa yangi konsultatsiya ochadi — narx belgilagan bo'lsangiz, u qayta to'lanadi."
    override val closeConfirm = "Yakunlash"
    override val typing = "yozmoqda…"
    override val photo = "Rasm"
    override val record = "Bemor kartasi"
    override val recordCardTitle = "Bemor kartasi biriktirildi"
    override val recordCardBody = "Sikl, alomatlar, dorilar va ko'rsatkichlar — kartani ochgan paytingizdagi holati."
    override val viewRecord = "Kartani ochish"
    override val recordClosed = "Konsultatsiya yopilgan — karta endi ko'rinmaydi."
    override val attachPhoto = "Rasm yuborish"
    override val photoFailed = "Rasm yuklanmadi"
    override val report = "Shikoyat qilish"
    override val reportTitle = "Nima bo'ldi?"
    override fun reportReason(reason: ReportReason) = when (reason) {
        ReportReason.SPAM -> "Spam"
        ReportReason.ABUSE -> "Haqorat yoki tahdid"
        ReportReason.MISINFORMATION -> "Noto'g'ri ma'lumot"
        ReportReason.PERSONAL_DATA -> "Shaxsiy ma'lumot"
        ReportReason.OTHER -> "Boshqa"
    }
    override val reportSent = "Shikoyat yuborildi"
    override val sent = "Yuborildi"
    override val read = "O'qildi"
    override val namesNotice = "Bemor ismingiz va mutaxassisligingizni ko'radi, siz esa uning ismi va yoshini. Javobingiz tashxis emas — kerak bo'lsa ko'rikka chaqiring."
    override val acceptsTitle = "Yangi konsultatsiyalarni qabul qilish"
    override val acceptsBody = "O'chirsangiz, bemorlar yangi konsultatsiya ocha olmaydi; ochiqlari davom etadi."

    override val scanTitle = "Bemorni skanerlash"
    override val scanHint = "Bemor SADORA ilovasida «Shifokorga ko'rsatish» QR kodini ochadi — kamerani shu kodga qarating."
    override val cameraStarting = "Kamera ishga tushmoqda…"
    override val cameraDenied = "Kameraga ruxsat berilmagan. Ruxsatni sozlamalarda yoqing yoki havolani pastga joylashtiring."
    override val openSettings = "Sozlamalarni ochish"
    override val cameraMissing = "Bu qurilmada kamera ochilmadi. Bemor havolasini pastga joylashtiring."
    override val notPatientCode = "Bu SADORA bemor kodi emas"
    override val pasteLabel = "Yoki bemor havolasi"
    override val pastePlaceholder = "Havolani shu yerga qo'ying"
    override val open = "Ochish"
    override val recentTitle = "Shu safar ochilganlar"
    override val recentNote = "Ro'yxat telefonga yozilmaydi: ilova yopilganda yoki hisobdan chiqqaningizda o'chadi. Havola muddatini bemor o'zi belgilaydi."

    override val communityTitle = "Hamjamiyat"
    override val filterAll = "Hammasi"
    override val filterDoctors = "Shifokorlar"
    override val feedEmpty = "Hozircha postlar yo'q"
    override val feedEmptyBody = "Birinchi bo'lib yozing: postingiz ismingiz va ✓ belgisi bilan chiqadi."

    override val recordTitle = "Bemor kartasi"
    override val recordGone = "Havola endi ishlamaydi"
    override val recordGoneBody = "Muddati tugagan yoki bemor uni bekor qilgan. Bemordan yangi QR kod so'rang."
    override val recordDisclaimer = "Bu bemor o'zi kiritgan va qurilmalari o'lchagan ma'lumotlar, tashxis emas. Kundalik matni ko'rsatilmaydi."
    override fun generatedAt(time: String) = "Tuzilgan: $time"
    override fun age(years: Int) = "$years yosh"
    override val heightLabel = "Bo'y"
    override val weightLabel = "Vazn"
    override fun cm(value: Int) = "$value sm"
    override fun kg(value: Int) = "$value kg"
    override fun days(count: Int) = "$count kun"
    override fun lifeStage(stage: LifeStage) = when (stage) {
        LifeStage.CYCLE -> "Hayz sikli"
        LifeStage.TRYING_TO_CONCEIVE -> "Homiladorlikni rejalashtirmoqda"
        LifeStage.PREGNANCY -> "Homiladorlik"
        LifeStage.POSTPARTUM -> "Tug'ruqdan keyingi davr"
        LifeStage.PERIMENOPAUSE -> "Perimenopauza"
        LifeStage.MENOPAUSE -> "Menopauza"
    }
    override fun memberSince(date: String) = "Ilovada ${date}dan beri"
    override val nothingYet = "Ma'lumot yo'q"

    override val cycleTitle = "Hayz sikli"
    override val cycleDay = "Sikl kuni"
    override val phaseLabel = "Faza"
    override fun phase(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> "Hayz"
        CyclePhase.FOLLICULAR -> "Follikulyar"
        CyclePhase.FERTILE -> "Unumdor kunlar"
        CyclePhase.LUTEAL -> "Lyutein"
    }
    override val lastPeriod = "Oxirgi hayz"
    override val averageCycle = "O'rtacha sikl uzunligi"
    override val averagePeriod = "O'rtacha hayz davomiyligi"
    override val cycleRange = "Sikl uzunligi oralig'i"
    override val nextPeriod = "Keyingi hayz"
    override val estimated = "bashorat"

    override val pregnancyTitle = "Homiladorlik"
    override val weekLabel = "Hafta"
    override val dueDate = "Taxminiy tug'ruq sanasi"
    override val birthDate = "Tug'ruq sanasi"
    override fun lessMovement(days: Int) = "Homila harakati kam sezilgan kunlar: $days"
    override val feedingTitle = "Emizish"
    override fun lastDays(days: Int) = "Oxirgi $days kun"
    override fun lastHours(hours: Int) = "Oxirgi $hours soat"
    override val feedsPerDay = "Kuniga o'rtacha"
    override val breastFeeds = "Ko'krak bilan"
    override fun averageMinutes(minutes: Int) = "o'rtacha $minutes daq"
    override val bottleFeeds = "Shisha / sog'ilgan"
    override val lastOne = "Oxirgisi"
    override val kicksTitle = "Homila harakatlarini sanash"
    override fun kicksResult(kicks: Int, duration: String) = "$kicks ta harakat · $duration"
    override val kicksSlow = "10 ta harakat 2 soatdan uzoq vaqtda sezilgan sanashlar bor."
    override val contractionsTitle = "To'lg'oqlar"
    override val count = "Soni"
    override val averageLength = "O'rtacha davomiyligi"
    override val averageInterval = "O'rtacha oralig'i"
    override val hotFlushTitle = "Issiq toshishlar"
    override val perDay = "Kuniga o'rtacha"
    override val strong = "Kuchli"
    override fun trigger(trigger: uz.sadora.contract.HotFlushTrigger) = when (trigger) {
        uz.sadora.contract.HotFlushTrigger.HEAT -> "Issiq xona"
        uz.sadora.contract.HotFlushTrigger.HOT_DRINK -> "Issiq ichimlik"
        uz.sadora.contract.HotFlushTrigger.SPICY_FOOD -> "Achchiq taom"
        uz.sadora.contract.HotFlushTrigger.CAFFEINE -> "Kofein"
        uz.sadora.contract.HotFlushTrigger.ALCOHOL -> "Spirtli ichimlik"
        uz.sadora.contract.HotFlushTrigger.STRESS -> "Stress"
        uz.sadora.contract.HotFlushTrigger.NIGHT -> "Tunda"
    }
    override val epdsTitle = "Kayfiyat so'rovnomasi (EPDS)"
    override fun epdsResult(score: Int) = "$score / 30 · " + when {
        score >= 13 -> "depressiya ehtimoli yuqori"
        score >= 10 -> "depressiya ehtimoli bor"
        else -> "alomatlar kam"
    }
    override val epdsSelfHarm = "O'ziga zarar yetkazish fikrlari haqida javob bergan (10-savol) — shoshilinch baholang."

    override val symptomsTitle = "Alomatlar"
    override fun symptomsWindow(days: Int) = "Oxirgi $days kun, eng ko'p uchraganlari"

    override val recentDaysTitle = "Oxirgi kunlar"
    override fun flow(level: FlowLevel) = when (level) {
        FlowLevel.SPOTTING -> "Dog'"
        FlowLevel.LIGHT -> "Kam"
        FlowLevel.MEDIUM -> "O'rtacha"
        FlowLevel.HEAVY -> "Ko'p"
    }

    override val mindTitle = "Kayfiyat va holat"
    override val daysLogged = "Qayd etilgan kunlar"
    override val averageMood = "O'rtacha kayfiyat"
    override val averageEnergy = "O'rtacha energiya"
    override val averageStress = "O'rtacha stress"

    override val medsTitle = "Dorilar va qo'shimchalar"
    override fun adherence(percent: Int) = "Qabul: $percent%"
    override val medFinished = "Tugagan"

    override val appointmentsTitle = "Ko'riklar va tekshiruvlar"

    override val nutritionTitle = "Ovqatlanish"
    override val averageKcal = "O'rtacha kaloriya"
    override val averageWater = "O'rtacha suv"

    override val wearableTitle = "Qurilma ko'rsatkichlari"
    override fun metric(metric: HealthMetric) = when (metric) {
        HealthMetric.STEPS -> "Qadamlar"
        HealthMetric.RESTING_HEART_RATE -> "Tinch holatdagi puls"
        HealthMetric.HEART_RATE -> "Puls"
        HealthMetric.HRV -> "HRV"
        HealthMetric.SLEEP_DURATION -> "Uyqu"
        HealthMetric.SPO2 -> "SpO₂, %"
        HealthMetric.BODY_TEMPERATURE -> "Tana harorati"
        HealthMetric.RESPIRATORY_RATE -> "Nafas tezligi"
        HealthMetric.WEIGHT -> "Vazn"
        else -> null
    }
    override fun unit(metric: HealthMetric) = when (metric) {
        HealthMetric.STEPS -> "qadam"
        HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> "zarba/daq"
        HealthMetric.HRV -> "ms"
        HealthMetric.SLEEP_DURATION -> "daq"
        HealthMetric.RESPIRATORY_RATE -> "nafas/daq"
        HealthMetric.BODY_TEMPERATURE -> "°C"
        HealthMetric.WEIGHT -> "kg"
        else -> ""
    }
    override fun kcal(value: Int) = "$value kkal"
    override fun ml(value: Int) = "$value ml"
    override fun hoursMinutes(minutes: Int) = "${minutes / 60} soat ${minutes % 60} daq"
}

object TabStringsRu : TabStrings {
    override val home = "Главная"
    override val messages = "Сообщения"
    override val scan = "Сканер"
    override val community = "Сообщество"
    override val profile = "Профиль"

    override fun greeting(hour: Int) = when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        else -> "Добрый вечер"
    }
    override val homeSubtitle = "Что ждёт вас сегодня"
    override val statWaiting = "Вопросы ждут ответа"
    override val statUnread = "Непрочитанные сообщения"
    override val scanPatient = "Сканировать QR пациентки"
    override val scanPatientBody = "По QR-коду пациентки откройте её цикл, симптомы, лекарства и показатели."
    override val seeAll = "Все"

    override val messagesEmpty = "Сообщений пока нет"
    override val messagesEmptyBody = "Когда пациентка откроет консультацию с вашей страницы, беседа появится здесь."
    override val messageHint = "Напишите сообщение…"
    override val conversationClosed = "Эта беседа закрыта: новые сообщения отправить нельзя."
    override fun unread(count: Int) = "Новых: $count"
    override fun consultationOpen(hoursLeft: Int, minutesLeft: Int) = "Открыта · осталось ${if (hoursLeft > 0) "$hoursLeft ч " else ""}$minutesLeft мин"
    override val consultationClosed = "Закрыта"
    override val consultationClosedBody = "Консультация закрыта. Если пациентка откроет новую, вы снова сможете писать."
    override val closeConsultation = "Завершить консультацию"
    override val closeConfirmTitle = "Завершить консультацию?"
    override val closeConfirmBody = "Писать больше не сможете ни вы, ни пациентка, прикреплённая карта тоже закроется. Пациентка может открыть новую консультацию — если вы назначили цену, она оплачивается заново."
    override val closeConfirm = "Завершить"
    override val typing = "печатает…"
    override val photo = "Фото"
    override val record = "Карта пациентки"
    override val recordCardTitle = "Прикреплена карта пациентки"
    override val recordCardBody = "Цикл, симптомы, лекарства и показатели — на тот момент, когда вы открываете карту."
    override val viewRecord = "Открыть карту"
    override val recordClosed = "Консультация закрыта — карта больше не доступна."
    override val attachPhoto = "Отправить фото"
    override val photoFailed = "Фото не загрузилось"
    override val report = "Пожаловаться"
    override val reportTitle = "Что случилось?"
    override fun reportReason(reason: ReportReason) = when (reason) {
        ReportReason.SPAM -> "Спам"
        ReportReason.ABUSE -> "Оскорбление или угроза"
        ReportReason.MISINFORMATION -> "Недостоверная информация"
        ReportReason.PERSONAL_DATA -> "Личные данные"
        ReportReason.OTHER -> "Другое"
    }
    override val reportSent = "Жалоба отправлена"
    override val sent = "Отправлено"
    override val read = "Прочитано"
    override val namesNotice = "Пациентка видит ваше имя и специальность, вы — её имя и возраст. Ваш ответ — не диагноз; при необходимости пригласите на визит."
    override val acceptsTitle = "Принимать новые консультации"
    override val acceptsBody = "Если выключить, пациентки не смогут открыть новую консультацию; открытые продолжатся."

    override val scanTitle = "Сканировать QR пациентки"
    override val scanHint = "Пациентка открывает в SADORA QR-код «Показать врачу» — наведите на него камеру."
    override val cameraStarting = "Камера запускается…"
    override val cameraDenied = "Нет доступа к камере. Разрешите его в настройках или вставьте ссылку ниже."
    override val openSettings = "Открыть настройки"
    override val cameraMissing = "Камера на этом устройстве не открылась. Вставьте ссылку пациентки ниже."
    override val notPatientCode = "Это не код пациентки SADORA"
    override val pasteLabel = "Или ссылка пациентки"
    override val pastePlaceholder = "Вставьте ссылку сюда"
    override val open = "Открыть"
    override val recentTitle = "Открытые в этот раз"
    override val recentNote = "Список не сохраняется на телефоне: он исчезает, когда приложение закрывается или вы выходите из аккаунта. Срок ссылки задаёт сама пациентка."

    override val communityTitle = "Сообщество"
    override val filterAll = "Все"
    override val filterDoctors = "Врачи"
    override val feedEmpty = "Постов пока нет"
    override val feedEmptyBody = "Напишите первый пост: он выйдет с вашим именем и значком ✓."

    override val recordTitle = "Карта пациентки"
    override val recordGone = "Ссылка больше не работает"
    override val recordGoneBody = "Срок истёк или пациентка её отозвала. Попросите новый QR-код."
    override val recordDisclaimer = "Это данные, которые пациентка внесла сама и измерили её устройства, а не диагноз. Текст дневника не показывается."
    override fun generatedAt(time: String) = "Сформировано: $time"
    override fun age(years: Int): String {
        val word = when {
            years % 100 in 11..14 -> "лет"
            years % 10 == 1 -> "год"
            years % 10 in 2..4 -> "года"
            else -> "лет"
        }
        return "$years $word"
    }
    override val heightLabel = "Рост"
    override val weightLabel = "Вес"
    override fun cm(value: Int) = "$value см"
    override fun kg(value: Int) = "$value кг"
    override fun days(count: Int) = "$count ${ru(count, "день", "дня", "дней")}"
    override fun lifeStage(stage: LifeStage) = when (stage) {
        LifeStage.CYCLE -> "Менструальный цикл"
        LifeStage.TRYING_TO_CONCEIVE -> "Планирует беременность"
        LifeStage.PREGNANCY -> "Беременность"
        LifeStage.POSTPARTUM -> "После родов"
        LifeStage.PERIMENOPAUSE -> "Перименопауза"
        LifeStage.MENOPAUSE -> "Менопауза"
    }
    override fun memberSince(date: String) = "В приложении с $date"
    override val nothingYet = "Нет данных"

    override val cycleTitle = "Менструальный цикл"
    override val cycleDay = "День цикла"
    override val phaseLabel = "Фаза"
    override fun phase(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> "Месячные"
        CyclePhase.FOLLICULAR -> "Фолликулярная"
        CyclePhase.FERTILE -> "Фертильное окно"
        CyclePhase.LUTEAL -> "Лютеиновая"
    }
    override val lastPeriod = "Последние месячные"
    override val averageCycle = "Средняя длина цикла"
    override val averagePeriod = "Средняя длительность месячных"
    override val cycleRange = "Разброс длины цикла"
    override val nextPeriod = "Следующие месячные"
    override val estimated = "прогноз"

    override val pregnancyTitle = "Беременность"
    override val weekLabel = "Неделя"
    override val dueDate = "Предполагаемая дата родов"
    override val birthDate = "Дата родов"
    override fun lessMovement(days: Int) = "Дни с ослабленным шевелением: $days"
    override val feedingTitle = "Кормление"
    override fun lastDays(days: Int) = "${ru(days, "Последний", "Последние", "Последние")} $days ${ru(days, "день", "дня", "дней")}"
    override fun lastHours(hours: Int) = "Последние $hours ч"
    override val feedsPerDay = "В среднем за день"
    override val breastFeeds = "Грудью"
    override fun averageMinutes(minutes: Int) = "в среднем $minutes мин"
    override val bottleFeeds = "Бутылочка / сцеженное"
    override val lastOne = "Последнее"
    override val kicksTitle = "Подсчёт шевелений"
    override fun kicksResult(kicks: Int, duration: String) = "Шевелений: $kicks · $duration"
    override val kicksSlow = "Есть подсчёты, где 10 шевелений заняли больше 2 часов."
    override val contractionsTitle = "Схватки"
    override val count = "Количество"
    override val averageLength = "Средняя длительность"
    override val averageInterval = "Средний интервал"
    override val hotFlushTitle = "Приливы"
    override val perDay = "В среднем за день"
    override val strong = "Сильные"
    override fun trigger(trigger: uz.sadora.contract.HotFlushTrigger) = when (trigger) {
        uz.sadora.contract.HotFlushTrigger.HEAT -> "Жаркое помещение"
        uz.sadora.contract.HotFlushTrigger.HOT_DRINK -> "Горячий напиток"
        uz.sadora.contract.HotFlushTrigger.SPICY_FOOD -> "Острая еда"
        uz.sadora.contract.HotFlushTrigger.CAFFEINE -> "Кофеин"
        uz.sadora.contract.HotFlushTrigger.ALCOHOL -> "Алкоголь"
        uz.sadora.contract.HotFlushTrigger.STRESS -> "Стресс"
        uz.sadora.contract.HotFlushTrigger.NIGHT -> "Ночью"
    }
    override val epdsTitle = "Опросник настроения (EPDS)"
    override fun epdsResult(score: Int) = "$score / 30 · " + when {
        score >= 13 -> "вероятна депрессия"
        score >= 10 -> "возможна депрессия"
        else -> "признаков мало"
    }
    override val epdsSelfHarm = "Сообщила о мыслях причинить себе вред (вопрос 10) — нужна срочная оценка."

    override val symptomsTitle = "Симптомы"
    override fun symptomsWindow(days: Int) = "За ${ru(days, "последний", "последние", "последние")} $days ${ru(days, "день", "дня", "дней")}, самые частые"

    override val recentDaysTitle = "Последние дни"
    override fun flow(level: FlowLevel) = when (level) {
        FlowLevel.SPOTTING -> "Мажущие"
        FlowLevel.LIGHT -> "Скудные"
        FlowLevel.MEDIUM -> "Умеренные"
        FlowLevel.HEAVY -> "Обильные"
    }

    override val mindTitle = "Настроение и состояние"
    override val daysLogged = "Дней с записями"
    override val averageMood = "Среднее настроение"
    override val averageEnergy = "Средняя энергия"
    override val averageStress = "Средний стресс"

    override val medsTitle = "Лекарства и добавки"
    override fun adherence(percent: Int) = "Приём: $percent%"
    override val medFinished = "Завершён"

    override val appointmentsTitle = "Визиты и обследования"

    override val nutritionTitle = "Питание"
    override val averageKcal = "Средняя калорийность"
    override val averageWater = "Среднее количество воды"

    override val wearableTitle = "Показатели устройства"
    override fun metric(metric: HealthMetric) = when (metric) {
        HealthMetric.STEPS -> "Шаги"
        HealthMetric.RESTING_HEART_RATE -> "Пульс в покое"
        HealthMetric.HEART_RATE -> "Пульс"
        HealthMetric.HRV -> "ВСР"
        HealthMetric.SLEEP_DURATION -> "Сон"
        HealthMetric.SPO2 -> "SpO₂, %"
        HealthMetric.BODY_TEMPERATURE -> "Температура тела"
        HealthMetric.RESPIRATORY_RATE -> "Частота дыхания"
        HealthMetric.WEIGHT -> "Вес"
        else -> null
    }
    override fun unit(metric: HealthMetric) = when (metric) {
        HealthMetric.STEPS -> "шагов"
        HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> "уд/мин"
        HealthMetric.HRV -> "мс"
        HealthMetric.SLEEP_DURATION -> "мин"
        HealthMetric.RESPIRATORY_RATE -> "вдох/мин"
        HealthMetric.BODY_TEMPERATURE -> "°C"
        HealthMetric.WEIGHT -> "кг"
        else -> ""
    }
    override fun kcal(value: Int) = "$value ккал"
    override fun ml(value: Int) = "$value мл"
    override fun hoursMinutes(minutes: Int) = "${minutes / 60} ч ${minutes % 60} мин"
}

object TabStringsEn : TabStrings {
    override val home = "Home"
    override val messages = "Messages"
    override val scan = "Scan"
    override val community = "Community"
    override val profile = "Profile"

    override fun greeting(hour: Int) = when (hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    override val homeSubtitle = "What is waiting for you today"
    override val statWaiting = "Questions waiting"
    override val statUnread = "Unread messages"
    override val scanPatient = "Scan a patient"
    override val scanPatientBody = "Open her cycle, symptoms, medications and measurements from the QR code she shows you."
    override val seeAll = "All"

    override val messagesEmpty = "No messages yet"
    override val messagesEmptyBody = "When a patient opens a consultation from your page, the conversation appears here."
    override val messageHint = "Write a message…"
    override val conversationClosed = "This conversation is closed: no new messages can be sent."
    override fun unread(count: Int) = "$count new"
    override fun consultationOpen(hoursLeft: Int, minutesLeft: Int) = "Open · ${if (hoursLeft > 0) "$hoursLeft h " else ""}$minutesLeft min left"
    override val consultationClosed = "Closed"
    override val consultationClosedBody = "This consultation is closed. If the patient opens a new one, you can write again."
    override val closeConsultation = "End consultation"
    override val closeConfirmTitle = "End this consultation?"
    override val closeConfirmBody = "Neither you nor the patient can write any more, and the attached record closes too. She can open a new consultation — if you charge, she pays again."
    override val closeConfirm = "End"
    override val typing = "typing…"
    override val photo = "Photo"
    override val record = "Patient record"
    override val recordCardTitle = "Patient record attached"
    override val recordCardBody = "Cycle, symptoms, medications and measurements — as they are when you open it."
    override val viewRecord = "Open record"
    override val recordClosed = "The consultation is closed — the record is no longer available."
    override val attachPhoto = "Send a photo"
    override val photoFailed = "The photo did not load"
    override val report = "Report"
    override val reportTitle = "What happened?"
    override fun reportReason(reason: ReportReason) = when (reason) {
        ReportReason.SPAM -> "Spam"
        ReportReason.ABUSE -> "Abuse or threats"
        ReportReason.MISINFORMATION -> "Misinformation"
        ReportReason.PERSONAL_DATA -> "Personal data"
        ReportReason.OTHER -> "Other"
    }
    override val reportSent = "Report sent"
    override val sent = "Sent"
    override val read = "Read"
    override val namesNotice = "The patient sees your name and specialty; you see her name and age. Your answer is not a diagnosis — invite her for a visit when needed."
    override val acceptsTitle = "Take new consultations"
    override val acceptsBody = "When off, patients cannot open a new consultation; open ones carry on."

    override val scanTitle = "Scan a patient"
    override val scanHint = "The patient opens the “Show my doctor” QR code in SADORA — point the camera at it."
    override val cameraStarting = "Starting the camera…"
    override val cameraDenied = "No access to the camera. Allow it in settings, or paste the link below."
    override val openSettings = "Open settings"
    override val cameraMissing = "The camera would not open on this device. Paste the patient's link below."
    override val notPatientCode = "This is not a SADORA patient code"
    override val pasteLabel = "Or the patient's link"
    override val pastePlaceholder = "Paste the link here"
    override val open = "Open"
    override val recentTitle = "Opened this time"
    override val recentNote = "The list is never saved to the phone: it's gone when the app closes or you sign out. The patient decides how long her link lasts."

    override val communityTitle = "Community"
    override val filterAll = "All"
    override val filterDoctors = "Doctors"
    override val feedEmpty = "No posts yet"
    override val feedEmptyBody = "Be the first: your post goes out with your name and the ✓ mark."

    override val recordTitle = "Patient record"
    override val recordGone = "This link no longer works"
    override val recordGoneBody = "It has expired, or the patient took it back. Ask her for a new QR code."
    override val recordDisclaimer = "This is what the patient recorded and what her devices measured, not a diagnosis. Journal text is not shown."
    override fun generatedAt(time: String) = "Generated $time"
    override fun age(years: Int) = "$years ${en(years, "year", "years")} old"
    override val heightLabel = "Height"
    override val weightLabel = "Weight"
    override fun cm(value: Int) = "$value cm"
    override fun kg(value: Int) = "$value kg"
    override fun days(count: Int) = "$count ${en(count, "day", "days")}"
    override fun lifeStage(stage: LifeStage) = when (stage) {
        LifeStage.CYCLE -> "Menstrual cycle"
        LifeStage.TRYING_TO_CONCEIVE -> "Trying to conceive"
        LifeStage.PREGNANCY -> "Pregnancy"
        LifeStage.POSTPARTUM -> "Postpartum"
        LifeStage.PERIMENOPAUSE -> "Perimenopause"
        LifeStage.MENOPAUSE -> "Menopause"
    }
    override fun memberSince(date: String) = "In the app since $date"
    override val nothingYet = "No data"

    override val cycleTitle = "Menstrual cycle"
    override val cycleDay = "Cycle day"
    override val phaseLabel = "Phase"
    override fun phase(phase: CyclePhase) = when (phase) {
        CyclePhase.PERIOD -> "Period"
        CyclePhase.FOLLICULAR -> "Follicular"
        CyclePhase.FERTILE -> "Fertile window"
        CyclePhase.LUTEAL -> "Luteal"
    }
    override val lastPeriod = "Last period"
    override val averageCycle = "Average cycle length"
    override val averagePeriod = "Average period length"
    override val cycleRange = "Cycle length range"
    override val nextPeriod = "Next period"
    override val estimated = "forecast"

    override val pregnancyTitle = "Pregnancy"
    override val weekLabel = "Week"
    override val dueDate = "Due date"
    override val birthDate = "Birth date"
    override fun lessMovement(days: Int) = "Days with less foetal movement: $days"
    override val feedingTitle = "Feeding"
    override fun lastDays(days: Int) = "Last $days ${en(days, "day", "days")}"
    override fun lastHours(hours: Int) = "Last $hours ${en(hours, "hour", "hours")}"
    override val feedsPerDay = "Per day on average"
    override val breastFeeds = "At the breast"
    override fun averageMinutes(minutes: Int) = "$minutes min on average"
    override val bottleFeeds = "Bottle / expressed"
    override val lastOne = "Last"
    override val kicksTitle = "Kick counts"
    override fun kicksResult(kicks: Int, duration: String) = "$kicks ${en(kicks, "movement", "movements")} · $duration"
    override val kicksSlow = "Some counts took longer than 2 hours to reach 10 movements."
    override val contractionsTitle = "Contractions"
    override val count = "Count"
    override val averageLength = "Average length"
    override val averageInterval = "Average interval"
    override val hotFlushTitle = "Hot flushes"
    override val perDay = "Per day on average"
    override val strong = "Strong"
    override fun trigger(trigger: uz.sadora.contract.HotFlushTrigger) = when (trigger) {
        uz.sadora.contract.HotFlushTrigger.HEAT -> "Warm room"
        uz.sadora.contract.HotFlushTrigger.HOT_DRINK -> "Hot drink"
        uz.sadora.contract.HotFlushTrigger.SPICY_FOOD -> "Spicy food"
        uz.sadora.contract.HotFlushTrigger.CAFFEINE -> "Caffeine"
        uz.sadora.contract.HotFlushTrigger.ALCOHOL -> "Alcohol"
        uz.sadora.contract.HotFlushTrigger.STRESS -> "Stress"
        uz.sadora.contract.HotFlushTrigger.NIGHT -> "At night"
    }
    override val epdsTitle = "Mood questionnaire (EPDS)"
    override fun epdsResult(score: Int) = "$score / 30 · " + when {
        score >= 13 -> "probable depression"
        score >= 10 -> "possible depression"
        else -> "few signs"
    }
    override val epdsSelfHarm = "Reported thoughts of self-harm (item 10) — assess urgently."

    override val symptomsTitle = "Symptoms"
    override fun symptomsWindow(days: Int) = "Last $days ${en(days, "day", "days")}, most frequent first"

    override val recentDaysTitle = "Recent days"
    override fun flow(level: FlowLevel) = when (level) {
        FlowLevel.SPOTTING -> "Spotting"
        FlowLevel.LIGHT -> "Light"
        FlowLevel.MEDIUM -> "Medium"
        FlowLevel.HEAVY -> "Heavy"
    }

    override val mindTitle = "Mood and wellbeing"
    override val daysLogged = "Days logged"
    override val averageMood = "Average mood"
    override val averageEnergy = "Average energy"
    override val averageStress = "Average stress"

    override val medsTitle = "Medications and supplements"
    override fun adherence(percent: Int) = "Adherence: $percent%"
    override val medFinished = "Finished"

    override val appointmentsTitle = "Visits"

    override val nutritionTitle = "Nutrition"
    override val averageKcal = "Average calories"
    override val averageWater = "Average water"

    override val wearableTitle = "Device measurements"
    override fun metric(metric: HealthMetric) = when (metric) {
        HealthMetric.STEPS -> "Steps"
        HealthMetric.RESTING_HEART_RATE -> "Resting heart rate"
        HealthMetric.HEART_RATE -> "Heart rate"
        HealthMetric.HRV -> "HRV"
        HealthMetric.SLEEP_DURATION -> "Sleep"
        HealthMetric.SPO2 -> "SpO₂, %"
        HealthMetric.BODY_TEMPERATURE -> "Body temperature"
        HealthMetric.RESPIRATORY_RATE -> "Respiratory rate"
        HealthMetric.WEIGHT -> "Weight"
        else -> null
    }
    override fun unit(metric: HealthMetric) = when (metric) {
        HealthMetric.STEPS -> "steps"
        HealthMetric.HEART_RATE, HealthMetric.RESTING_HEART_RATE -> "bpm"
        HealthMetric.HRV -> "ms"
        HealthMetric.SLEEP_DURATION -> "min"
        HealthMetric.RESPIRATORY_RATE -> "breaths/min"
        HealthMetric.BODY_TEMPERATURE -> "°C"
        HealthMetric.WEIGHT -> "kg"
        else -> ""
    }
    override fun kcal(value: Int) = "$value kcal"
    override fun ml(value: Int) = "$value ml"
    override fun hoursMinutes(minutes: Int) = "${minutes / 60} h ${minutes % 60} min"
}
