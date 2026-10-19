package uz.sadora.app.i18n

import uz.sadora.contract.MoodLevel
import uz.sadora.contract.VoicePeriod

/** "Ovoz bilan belgilash": speak, check what was heard, save. */
interface VoiceStrings {
    val title: String
    val hint: String
    val tapToSpeak: String
    val tapToStop: String
    val listening: String
    val heard: String
    val understood: String
    val notUnderstood: String
    val save: String
    val again: String
    val saved: String
    fun mood(mood: MoodLevel): String
    fun water(ml: Int): String
    fun dose(name: String, time: String): String
    fun period(period: VoicePeriod): String
    fun allowance(left: Int): String
    val unlimited: String
    val lockedTitle: String
    val lockedBody: String
    val upgrade: String
    val privacy: String
    val unavailable: String
}

object VoiceStringsUz : VoiceStrings {
    override val title = "Ovoz bilan belgilash"
    override val hint = "Bugun qandayligingizni ayting. Masalan: «Boshim og'riyapti, kayfiyatim yomon, ikki stakan suv ichdim»."
    override val tapToSpeak = "Gapirish uchun bosing"
    override val tapToStop = "Tugatish uchun bosing"
    override val listening = "Tinglanmoqda…"
    override val heard = "Siz aytdingiz:"
    override val understood = "Tushunganim — keraksizini olib tashlang:"
    override val notUnderstood = "Tushunolmadim. Sekinroq va aniqroq gapirib ko'ring."
    override val save = "Saqlash"
    override val again = "Qayta yozish"
    override val saved = "Saqlandi"
    override fun mood(mood: MoodLevel) = "Kayfiyat: " + when (mood) {
        MoodLevel.BAD -> "yomon"
        MoodLevel.LOW -> "past"
        MoodLevel.OK -> "o'rtacha"
        MoodLevel.GOOD -> "yaxshi"
        MoodLevel.GREAT -> "a'lo"
    }
    override fun water(ml: Int) = "💧 +$ml ml suv"
    override fun dose(name: String, time: String) = "💊 $name ($time) — ichildi"
    override fun period(period: VoicePeriod) = if (period == VoicePeriod.STARTED) "🩸 Hayz boshlandi" else "🩸 Hayz tugadi"
    override fun allowance(left: Int) = "Bugun yana $left ta"
    override val unlimited = "Premium: cheksiz"
    override val lockedTitle = "Bugungi bepul ovozli belgilashlar tugadi"
    override val lockedBody = "Premium bilan cheksiz. Aks holda ertaga yana 3 ta."
    override val upgrade = "Premium'ni ko'rish"
    override val privacy = "Yozuv saqlanmaydi: tushunilgach o'chiriladi."
    override val unavailable = "Ovoz bilan belgilash hozircha ishlamayapti."
}

object VoiceStringsRu : VoiceStrings {
    override val title = "Отметить голосом"
    override val hint = "Скажите, как вы сегодня. Например: «Болит голова, настроение плохое, выпила два стакана воды»."
    override val tapToSpeak = "Нажмите и говорите"
    override val tapToStop = "Нажмите, чтобы закончить"
    override val listening = "Слушаю…"
    override val heard = "Вы сказали:"
    override val understood = "Что я поняла — уберите лишнее:"
    override val notUnderstood = "Не удалось понять. Скажите медленнее и чётче."
    override val save = "Сохранить"
    override val again = "Записать снова"
    override val saved = "Сохранено"
    override fun mood(mood: MoodLevel) = "Настроение: " + when (mood) {
        MoodLevel.BAD -> "плохое"
        MoodLevel.LOW -> "так себе"
        MoodLevel.OK -> "нормальное"
        MoodLevel.GOOD -> "хорошее"
        MoodLevel.GREAT -> "отличное"
    }
    override fun water(ml: Int) = "💧 +$ml мл воды"
    override fun dose(name: String, time: String) = "💊 $name ($time) — принято"
    override fun period(period: VoicePeriod) = if (period == VoicePeriod.STARTED) "🩸 Месячные начались" else "🩸 Месячные закончились"
    override fun allowance(left: Int) = "Сегодня осталось: $left"
    override val unlimited = "Premium: без ограничений"
    override val lockedTitle = "Бесплатные голосовые отметки на сегодня закончились"
    override val lockedBody = "С Premium — без ограничений. Иначе завтра снова 3."
    override val upgrade = "Посмотреть Premium"
    override val privacy = "Запись не сохраняется: после распознавания она удаляется."
    override val unavailable = "Голосовая отметка сейчас недоступна."
}

object VoiceStringsEn : VoiceStrings {
    override val title = "Log by voice"
    override val hint = "Say how you are today. For example: \"I have a headache, I feel low, I drank two glasses of water\"."
    override val tapToSpeak = "Tap and speak"
    override val tapToStop = "Tap to finish"
    override val listening = "Listening…"
    override val heard = "You said:"
    override val understood = "What I understood — remove anything wrong:"
    override val notUnderstood = "I couldn't understand that. Try speaking slower and clearer."
    override val save = "Save"
    override val again = "Record again"
    override val saved = "Saved"
    override fun mood(mood: MoodLevel) = "Mood: " + when (mood) {
        MoodLevel.BAD -> "bad"
        MoodLevel.LOW -> "low"
        MoodLevel.OK -> "okay"
        MoodLevel.GOOD -> "good"
        MoodLevel.GREAT -> "great"
    }
    override fun water(ml: Int) = "💧 +$ml ml water"
    override fun dose(name: String, time: String) = "💊 $name ($time) — taken"
    override fun period(period: VoicePeriod) = if (period == VoicePeriod.STARTED) "🩸 Period started" else "🩸 Period ended"
    override fun allowance(left: Int) = "$left left today"
    override val unlimited = "Premium: unlimited"
    override val lockedTitle = "Today's free voice notes are used"
    override val lockedBody = "Unlimited with Premium. Otherwise 3 more tomorrow."
    override val upgrade = "See Premium"
    override val privacy = "The recording is not kept: it is deleted once understood."
    override val unavailable = "Voice logging is not available right now."
}
