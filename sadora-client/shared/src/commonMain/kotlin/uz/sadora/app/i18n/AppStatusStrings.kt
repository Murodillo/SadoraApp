package uz.sadora.app.i18n

/**
 * The screens the server can put in front of the app: a build too old to run, a newer one
 * worth having, and maintenance. The three languages sit beside the interface, as the
 * health gate's do, so a line is added to all of them at once.
 */
interface AppStatusStrings {
    val updateTitle: String
    val updateBody: String
    val updateButton: String
    /** The store page is not known: she has to find the app in the store herself. */
    val updateNoStore: String
    val suggestedTitle: String
    val suggestedBody: String
    val later: String
    val maintenanceTitle: String
    /** When the team wrote no message of its own. */
    val maintenanceBody: String
    val retry: String
}

object AppStatusStringsUz : AppStatusStrings {
    override val updateTitle = "Ilovani yangilang"
    override val updateBody = "Siz foydalanayotgan versiya endi ishlamaydi. Yangi versiyani o'rnating — ma'lumotlaringiz joyida qoladi."
    override val updateButton = "Yangilash"
    override val updateNoStore = "Ilovani do'kondan qidirib yangilang."
    override val suggestedTitle = "Yangi versiya chiqdi"
    override val suggestedBody = "Yangi versiyada tuzatishlar va yangi imkoniyatlar bor."
    override val later = "Keyinroq"
    override val maintenanceTitle = "Texnik ishlar"
    override val maintenanceBody = "Ilovada texnik ishlar olib borilmoqda. Birozdan so'ng qayta urinib ko'ring."
    override val retry = "Qayta urinish"
}

object AppStatusStringsRu : AppStatusStrings {
    override val updateTitle = "Обновите приложение"
    override val updateBody = "Эта версия больше не работает. Установите новую — ваши данные сохранятся."
    override val updateButton = "Обновить"
    override val updateNoStore = "Найдите приложение в магазине и обновите его."
    override val suggestedTitle = "Вышла новая версия"
    override val suggestedBody = "В новой версии исправления и новые возможности."
    override val later = "Позже"
    override val maintenanceTitle = "Технические работы"
    override val maintenanceBody = "В приложении идут технические работы. Попробуйте немного позже."
    override val retry = "Повторить"
}

object AppStatusStringsEn : AppStatusStrings {
    override val updateTitle = "Update the app"
    override val updateBody = "This version no longer works. Install the new one — your data stays where it is."
    override val updateButton = "Update"
    override val updateNoStore = "Find the app in your store and update it."
    override val suggestedTitle = "A new version is out"
    override val suggestedBody = "The new version has fixes and new features."
    override val later = "Later"
    override val maintenanceTitle = "Maintenance"
    override val maintenanceBody = "The app is under maintenance. Please try again a little later."
    override val retry = "Try again"
}
