package uz.sadora.app.i18n

/**
 * The app's own words about its widgets and watch: the settings page, the Today card and
 * discreet mode. What a widget itself says lives in widgetcore, which the widget
 * extension and the watch link without this module.
 */
interface WidgetUiStrings {
    val title: String
    val subtitle: String
    val look: String
    val small: String
    val medium: String
    val large: String
    val blocks: String
    val stageBlock: String
    val stageBlockNote: String
    val waterBlock: String
    val dosesBlock: String
    val healthBlock: String
    val waterStep: String
    val metrics: String
    val addToHome: String
    /** iOS has no button that places a widget: the steps instead. */
    val howToAddTitle: String
    val howToAddSteps: List<String>
    val pinned: String
    val premiumNote: String
    val premiumButton: String
    val watchTitle: String
    val watchConnected: String
    val watchNone: String
    val watchNote: String
    val previewEmpty: String

    // Today's one-time card
    val cardTitle: String
    val cardBody: String
    val cardAdd: String
    val cardDismiss: String

    // Settings → Privacy
    val discreetTitle: String
    val discreetNote: String
    val lockScreenNote: String
}

object WidgetUiUz : WidgetUiStrings {
    override val title = "Widgetlar va soat"
    override val subtitle = "Sikl kuni, suv va dorilar — ilovani ochmasdan"
    override val look = "Ko'rinishi"
    override val small = "Kichik"
    override val medium = "O'rta"
    override val large = "Katta"
    override val blocks = "Bloklar"
    override val stageBlock = "Bosqich"
    override val stageBlockNote = "Har doim bor: sikl kuni yoki homiladorlik haftasi"
    override val waterBlock = "Suv"
    override val dosesBlock = "Dorilar"
    override val healthBlock = "Soat ma'lumoti"
    override val waterStep = "Suv tugmasi"
    override val metrics = "Soatdan nimalar"
    override val addToHome = "Bosh ekranga qo'shish"
    override val howToAddTitle = "Widgetni qanday qo'shish mumkin"
    override val howToAddSteps = listOf(
        "Bosh ekranda bo'sh joyni bosib turing",
        "Yuqoridagi «+» tugmasini bosing",
        "Ro'yxatdan Sadora'ni tanlang",
        "O'lchamni tanlab, «Widget qo'shish»ni bosing",
    )
    override val pinned = "Widget bosh ekranga qo'shildi"
    override val premiumNote = "Kichik widget bepul. Suv, dorilar, katta o'lcham va soat — Premium bilan."
    override val premiumButton = "Premium'ni ko'rish"
    override val watchTitle = "Soat"
    override val watchConnected = "Soat ulangan"
    override val watchNone = "Soat topilmadi"
    override val watchNote = "Apple Watch yoki Wear OS soatiga Sadora'ni o'rnating — u telefondan o'zi ulanadi."
    override val previewEmpty = "Ma'lumot yuklanmoqda…"
    override val cardTitle = "Sikl kunini ilovani ochmasdan ko'ring"
    override val cardBody = "Bosh ekranga Sadora widgetini qo'shing"
    override val cardAdd = "Qo'shish"
    override val cardDismiss = "Kerak emas"
    override val discreetTitle = "Yashirin rejim"
    override val discreetNote = "Widget, soat va bildirishnomalarda sikl, homiladorlik va dori nomlari yozilmaydi"
    override val lockScreenNote = "Lock screen widgetlari doim yashirin ko'rinadi"
}

object WidgetUiRu : WidgetUiStrings {
    override val title = "Виджеты и часы"
    override val subtitle = "День цикла, вода и лекарства — не открывая приложение"
    override val look = "Вид"
    override val small = "Маленький"
    override val medium = "Средний"
    override val large = "Большой"
    override val blocks = "Блоки"
    override val stageBlock = "Этап"
    override val stageBlockNote = "Всегда есть: день цикла или неделя беременности"
    override val waterBlock = "Вода"
    override val dosesBlock = "Лекарства"
    override val healthBlock = "Данные часов"
    override val waterStep = "Кнопка воды"
    override val metrics = "Что показывать с часов"
    override val addToHome = "Добавить на экран"
    override val howToAddTitle = "Как добавить виджет"
    override val howToAddSteps = listOf(
        "Нажмите и удерживайте пустое место на экране «Домой»",
        "Нажмите «+» вверху",
        "Выберите Sadora в списке",
        "Выберите размер и нажмите «Добавить виджет»",
    )
    override val pinned = "Виджет добавлен на экран"
    override val premiumNote = "Маленький виджет бесплатный. Вода, лекарства, большой размер и часы — в Premium."
    override val premiumButton = "Посмотреть Premium"
    override val watchTitle = "Часы"
    override val watchConnected = "Часы подключены"
    override val watchNone = "Часы не найдены"
    override val watchNote = "Установите Sadora на Apple Watch или часы с Wear OS — они подключатся через телефон."
    override val previewEmpty = "Загружаем данные…"
    override val cardTitle = "День цикла — не открывая приложение"
    override val cardBody = "Добавьте виджет Sadora на экран «Домой»"
    override val cardAdd = "Добавить"
    override val cardDismiss = "Не нужно"
    override val discreetTitle = "Скрытый режим"
    override val discreetNote = "В виджетах, на часах и в уведомлениях не будет слов о цикле, беременности и названий лекарств"
    override val lockScreenNote = "Виджеты на экране блокировки всегда скрытые"
}

object WidgetUiEn : WidgetUiStrings {
    override val title = "Widgets and watch"
    override val subtitle = "Your cycle day, water and meds — without opening the app"
    override val look = "Look"
    override val small = "Small"
    override val medium = "Medium"
    override val large = "Large"
    override val blocks = "Blocks"
    override val stageBlock = "Stage"
    override val stageBlockNote = "Always there: cycle day or pregnancy week"
    override val waterBlock = "Water"
    override val dosesBlock = "Medications"
    override val healthBlock = "Watch data"
    override val waterStep = "Water button"
    override val metrics = "From your watch"
    override val addToHome = "Add to home screen"
    override val howToAddTitle = "How to add the widget"
    override val howToAddSteps = listOf(
        "Touch and hold an empty spot on your Home Screen",
        "Tap «+» at the top",
        "Choose Sadora in the list",
        "Pick a size and tap «Add Widget»",
    )
    override val pinned = "Widget added to your home screen"
    override val premiumNote = "The small widget is free. Water, meds, the large size and the watch come with Premium."
    override val premiumButton = "See Premium"
    override val watchTitle = "Watch"
    override val watchConnected = "Watch connected"
    override val watchNone = "No watch found"
    override val watchNote = "Install Sadora on your Apple Watch or Wear OS watch — it connects through your phone."
    override val previewEmpty = "Loading…"
    override val cardTitle = "See your cycle day without opening the app"
    override val cardBody = "Add the Sadora widget to your home screen"
    override val cardAdd = "Add"
    override val cardDismiss = "Not now"
    override val discreetTitle = "Discreet mode"
    override val discreetNote = "Widgets, the watch and notifications leave out cycle, pregnancy and medicine names"
    override val lockScreenNote = "Lock-screen widgets are always discreet"
}
