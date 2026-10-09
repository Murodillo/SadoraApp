package uz.sadora.app.i18n

import uz.sadora.contract.AvatarFrames

/** Avatar frames: their page, buying one, and the line a badge's gold tier adds. */
interface FrameStrings {
    val title: String
    val subtitle: String
    /** A frame's name; null for a key this build does not know. */
    fun name(key: String): String?

    val coinsSection: String
    val badgeSection: String
    val paidSection: String

    val wear: String
    val wearing: String
    val takeOff: String
    val owned: String
    /** On a frame that moves. */
    val animated: String
    fun coins(cost: Int): String
    fun balance(coins: Int): String
    /** A badge frame's lock: which badge, at gold, opens it. */
    fun badgeLock(badge: String): String
    fun short(missing: Int): String

    val buyTitle: String
    fun buyCoinsBody(cost: Int): String
    fun buyCoins(cost: Int): String
    val bought: String
    fun buy(price: String): String
    val paidBody: String
    val askYaqinim: String
    val requestWhat: String
    val askBody: String
    val paying: String
    val payReopen: String
    val storePending: String
    val noProvider: String

    val changePhoto: String
    /** The shop's door to the frames page. */
    val shopCard: String
    val shopCardNote: String
    /** On a badge's unlock: the frame that came with it. */
    fun unlocked(name: String): String
}

object FramesUz : FrameStrings {
    override val title = "Ramkalar"
    override val subtitle = "Profil rasmingiz va chatdagi taxallusingiz atrofida turadi — hamma ko'radi."
    override fun name(key: String) = when (key) {
        AvatarFrames.TULIP -> "Lola"
        AvatarFrames.LAVENDER -> "Lavanda"
        AvatarFrames.SAKURA -> "Sakura"
        AvatarFrames.MOON -> "Oy va yulduzlar"
        AvatarFrames.ROSE -> "Atirgul toji"
        AvatarFrames.GOLD_FLAME -> "Oltin olov"
        AvatarFrames.GOLD_LAUREL -> "Oltin dafna"
        AvatarFrames.RAINBOW -> "Kamalak"
        AvatarFrames.HUMO_WING -> "Humo qanoti"
        else -> null
    }
    override val coinsSection = "Gulga"
    override val badgeSection = "Nishon bilan"
    override val paidSection = "Maxsus"
    override val wear = "Taqish"
    override val wearing = "Taqilgan"
    override val takeOff = "Yechish"
    override val owned = "Sizniki"
    override val animated = "Jonli"
    override fun coins(cost: Int) = "$cost gul"
    override fun balance(coins: Int) = "Sizda $coins gul"
    override fun badgeLock(badge: String) = "«$badge» oltin nishoni bilan ochiladi"
    override fun short(missing: Int) = "Yana $missing gul kerak"
    override val buyTitle = "Ramkani olasizmi?"
    override fun buyCoinsBody(cost: Int) = "$cost gul hisobingizdan yechiladi. Ramka abadiy sizniki bo'ladi."
    override fun buyCoins(cost: Int) = "$cost gulga olish"
    override val bought = "Ramka sizniki! Endi uni taqishingiz mumkin."
    override fun buy(price: String) = "Sotib olish · $price"
    override val paidBody = "Bir marta to'laysiz — ramka abadiy sizniki va darhol rasmingizda turadi."
    override val askYaqinim = "Yaqinimdan so'rash"
    override val requestWhat = "Profil rasmi uchun ramka"
    override val askBody = "Yaqiningizdan bu ramkani sovg'a qilishni so'rang. To'lagach, u darhol rasmingizda turadi."
    override val paying = "To'lov kutilmoqda…"
    override val payReopen = "To'lov sahifasini qayta ochish"
    override val storePending = "To'lov tasdiqlanishi kutilmoqda — tushishi bilan ramka o'zi taqiladi."
    override val noProvider = "Hozircha bu yerda to'lov usuli yo'q."
    override val changePhoto = "Rasmni o'zgartirish"
    override val shopCard = "Avatar ramkalari"
    override val shopCardNote = "Gulga ramka oling — profilingiz va chatdagi taxallusingiz atrofida turadi"
    override fun unlocked(name: String) = "+ yangi ramka: $name"
}

object FramesRu : FrameStrings {
    override val title = "Рамки"
    override val subtitle = "Вокруг фото профиля и вашего псевдонима в чате — её видят все."
    override fun name(key: String) = when (key) {
        AvatarFrames.TULIP -> "Тюльпан"
        AvatarFrames.LAVENDER -> "Лаванда"
        AvatarFrames.SAKURA -> "Сакура"
        AvatarFrames.MOON -> "Луна и звёзды"
        AvatarFrames.ROSE -> "Венок из роз"
        AvatarFrames.GOLD_FLAME -> "Золотое пламя"
        AvatarFrames.GOLD_LAUREL -> "Золотой лавр"
        AvatarFrames.RAINBOW -> "Радуга"
        AvatarFrames.HUMO_WING -> "Крыло Хумо"
        else -> null
    }
    override val coinsSection = "За гули"
    override val badgeSection = "За значки"
    override val paidSection = "Особые"
    override val wear = "Надеть"
    override val wearing = "Надета"
    override val takeOff = "Снять"
    override val owned = "Ваша"
    override val animated = "Живая"
    override fun coins(cost: Int) = "$cost гулей"
    override fun balance(coins: Int) = "У вас $coins гулей"
    override fun badgeLock(badge: String) = "Откроется с золотым значком «$badge»"
    override fun short(missing: Int) = "Нужно ещё $missing гулей"
    override val buyTitle = "Взять рамку?"
    override fun buyCoinsBody(cost: Int) = "С вашего счёта спишется $cost гулей. Рамка останется вашей навсегда."
    override fun buyCoins(cost: Int) = "Взять за $cost гулей"
    override val bought = "Рамка ваша! Теперь её можно надеть."
    override fun buy(price: String) = "Купить · $price"
    override val paidBody = "Платите один раз — рамка ваша навсегда и сразу появится на фото."
    override val askYaqinim = "Попросить близкого"
    override val requestWhat = "Рамка для фото профиля"
    override val askBody = "Попросите близкого подарить вам эту рамку. Как только он оплатит, она появится на вашем фото."
    override val paying = "Ждём оплату…"
    override val payReopen = "Открыть страницу оплаты снова"
    override val storePending = "Ждём подтверждения оплаты — как только она пройдёт, рамка наденется сама."
    override val noProvider = "Здесь пока нет способа оплаты."
    override val changePhoto = "Сменить фото"
    override val shopCard = "Рамки для аватара"
    override val shopCardNote = "Возьмите рамку за гули — она будет вокруг фото и псевдонима в чате"
    override fun unlocked(name: String) = "+ новая рамка: $name"
}

object FramesEn : FrameStrings {
    override val title = "Frames"
    override val subtitle = "Around your profile photo and your alias in the chat — everyone sees it."
    override fun name(key: String) = when (key) {
        AvatarFrames.TULIP -> "Tulip"
        AvatarFrames.LAVENDER -> "Lavender"
        AvatarFrames.SAKURA -> "Sakura"
        AvatarFrames.MOON -> "Moon and stars"
        AvatarFrames.ROSE -> "Rose crown"
        AvatarFrames.GOLD_FLAME -> "Golden flame"
        AvatarFrames.GOLD_LAUREL -> "Golden laurel"
        AvatarFrames.RAINBOW -> "Rainbow"
        AvatarFrames.HUMO_WING -> "Humo's wing"
        else -> null
    }
    override val coinsSection = "For Gul"
    override val badgeSection = "With a badge"
    override val paidSection = "Special"
    override val wear = "Wear"
    override val wearing = "Wearing"
    override val takeOff = "Take off"
    override val owned = "Yours"
    override val animated = "Animated"
    override fun coins(cost: Int) = "$cost Gul"
    override fun balance(coins: Int) = "You have $coins Gul"
    override fun badgeLock(badge: String) = "Unlocks with the gold «$badge» badge"
    override fun short(missing: Int) = "$missing more Gul needed"
    override val buyTitle = "Get this frame?"
    override fun buyCoinsBody(cost: Int) = "$cost Gul will come off your balance. The frame is yours for good."
    override fun buyCoins(cost: Int) = "Get for $cost Gul"
    override val bought = "The frame is yours! You can wear it now."
    override fun buy(price: String) = "Buy · $price"
    override val paidBody = "Pay once — the frame is yours for good and goes on your photo right away."
    override val askYaqinim = "Ask someone close"
    override val requestWhat = "A frame for her profile photo"
    override val askBody = "Ask someone close to give you this frame. As soon as they pay, it goes on your photo."
    override val paying = "Waiting for the payment…"
    override val payReopen = "Open the payment page again"
    override val storePending = "Waiting for the payment to clear — the frame will go on by itself."
    override val noProvider = "There's no way to pay here yet."
    override val changePhoto = "Change photo"
    override val shopCard = "Avatar frames"
    override val shopCardNote = "Get a frame for Gul — it sits around your photo and your alias in the chat"
    override fun unlocked(name: String) = "+ new frame: $name"
}
