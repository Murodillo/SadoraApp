package uz.sadora.doctor.i18n

import kotlinx.datetime.LocalDate
import uz.sadora.contract.CommunityTopic

/** Uzbek (Latin): the default language, and the one the others are translated from. */
object StringsUz : Strings {
    override val language = AppLanguage.Uz
    override val languageName = "O'zbekcha"

    override val common = object : CommonStrings {
        override val appName = "Sadora Doctor"
        override val back = "Ortga"
        override val retry = "Qayta urinish"
        override val cancel = "Bekor qilish"
        override val saving = "Saqlanmoqda…"
        override val send = "Yuborish"
    }

    override val auth = object : AuthStrings {
        override val title = "Shifokorlar uchun Sadora"
        override val subtitle = "Sadora ilovasidagi raqamingiz bilan kiring. Ariza topshiring, " +
            "tasdiqlangach esa ayollarning savollariga javob bering."
        override val phoneLabel = "Telefon raqami"
        override val phoneNote = "Raqam faqat kirish uchun kerak. Unga bir martalik SMS kod yuboramiz."
        override val sendCode = "Kodni yuborish"
        override val sending = "Yuborilmoqda…"
        override val codeTitle = "Kodni kiriting"
        override fun codeSubtitle(phone: String) = "+998 $phone raqamiga 6 xonali kod yubordik."
        override val confirm = "Tasdiqlash"
        override val checking = "Tekshirilmoqda…"
        override fun resendIn(seconds: Int) = "Qayta yuborish · ${seconds}s"
        override val resend = "Kodni qayta yuborish"
        override val changeNumber = "Raqamni o'zgartirish"
        override val codeSecrecy = "Kodni hech kimga aytmang. Sadora xodimlari kodni so'ramaydi."
        override val devCodeFilled = "Test serveri kodni qaytardi — u avtomatik to'ldirildi."
        override fun otpEntered(entered: Int, length: Int) = "Tasdiqlash kodi: $length tadan $entered ta raqam kiritildi"
        override val deleteDigit = "Oxirgi raqamni o'chirish"
    }

    override val errors = object : ErrorStrings {
        override val phoneInvalid = "Raqam to'liq emas yoki bunday operator kodi yo'q"
        override val network = "Internetga ulanib bo'lmadi. Qayta urinib ko'ring."
        override val validation = "Kiritilgan ma'lumot noto'g'ri."
        override val sessionExpired = "Sessiya tugadi. Qaytadan kiring."
        override val blocked = "Hisob bloklangan. Sadora bilan bog'laning."
        override val forbidden = "Bu amal uchun ruxsat yo'q."
        override val notFound = "Topilmadi: u o'chirilgan bo'lishi mumkin."
        override fun retryAfter(seconds: Int) = "Juda ko'p urinish. $seconds soniyadan keyin qayta urining."
        override val retrySoon = "Juda ko'p urinish. Birozdan keyin qayta urining."
        override val otpInvalid = "Kod noto'g'ri yoki muddati tugagan."
        override val featureClosed = "Bu bo'lim hozircha yopiq."
        override val unexpected = "Nimadir noto'g'ri ketdi. Qayta urinib ko'ring."
    }

    override val dates = object : DateStrings {
        override val months = listOf(
            "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
            "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr",
        )

        override fun dayMonth(date: LocalDate) = "${date.day}-${months[date.month.ordinal].lowercase()}"

        override val yesterday = "Kecha"
        override val justNow = "hozir"
        override fun minutesAgo(minutes: Int) = "$minutes daqiqa oldin"
        override fun hoursAgo(hours: Int) = "$hours soat oldin"
        override fun daysAgo(days: Int) = "$days kun oldin"
    }

    override val community = object : CommunityStrings {
        override fun topic(topic: CommunityTopic) = when (topic) {
            CommunityTopic.CYCLE -> "Sikl"
            CommunityTopic.PREGNANCY -> "Homiladorlik"
            CommunityTopic.WELLBEING -> "Kayfiyat"
            CommunityTopic.BODY -> "Tana"
        }
        override val you = "siz"
        override val readMore = "…ko'proq"
        override fun commentsCount(count: Int) = if (count == 0) "Izohlar" else "$count izoh"
        override val noComments = "Hali izoh yo'q. Birinchi bo'lib javob bering."
        override val questionTitle = "Savol"
        override val postTitle = "Post"
        override val postMissing = "Bu post o'chirilgan yoki yashirilgan."
        override val answerHint = "Javobingizni yozing"
        override val answerSent = "Javobingiz yuborildi"
        override val newPost = "Post yozish"
        override val newPostTitle = "Yangi post"
        override val topicLabel = "Mavzu"
        override val postHint = "Ayollar uchun foydali maslahat yoki tushuntirish yozing…"
        override fun postTooShort(min: Int) = "Kamida $min ta belgi"
        override val publish = "Joylash"
        override val published = "Post joylandi"
    }

    override val settings = object : SettingsStrings {
        override val title = "Sozlamalar"
        override val language = "Til"
        override val account = "Hisob"
        override fun signedInAs(phone: String) = "Kirilgan raqam: $phone"
        override val signOut = "Chiqish"
        override val signOutTitle = "Hisobdan chiqasizmi?"
        override val signOutBody = "Qayta kirish uchun SMS kod kerak bo'ladi."
    }

    override val doctors: DoctorStrings = DoctorStringsUz
}
