package uz.sadora.server.pet

import uz.sadora.contract.CyclePhase
import uz.sadora.contract.Language
import uz.sadora.contract.PetAction
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetPose
import uz.sadora.contract.PetTrigger

/** One thing the pet can say, with the drawing it says it in. */
data class PetLine(val text: String, val pose: PetPose, val action: PetAction? = null)

/**
 * What the pet says when the model does not write it — which is most of the time.
 *
 * The rules: general wellness only, never a diagnosis, never a number from her body,
 * and every line short enough for a speech bubble. A low mood gets a gentle hand and
 * the advice to see a doctor if it lasts, never a joke.
 */
object PetPhrases {

    fun lines(language: Language, trigger: PetTrigger, phase: CyclePhase? = null, dish: String? = null): List<PetLine> =
        when (language) {
            Language.UZ -> uz(trigger, phase, dish)
            Language.RU -> ru(trigger, phase, dish)
            Language.EN -> en(trigger, phase, dish)
        }

    /** The features she is reminded of on opening the app, one a day, in turn. */
    fun tips(language: Language): List<PetLine> = when (language) {
        Language.UZ -> listOf(
            PetLine("Bilasizmi? Ovqatni rasmga olsangiz, tarkibini o'zim hisoblab beraman.", PetPose.THINK, PetAction.FOOD_SCANNER),
            PetLine("Yaqiningizni Yaqinim orqali ulang — g'amxo'rlik kerak kunlarni u ham biladi.", PetPose.THINK, PetAction.PARTNER),
            PetLine("Nishonlarga bir qarang — ba'zilariga juda yaqinsiz!", PetPose.IDLE, PetAction.BADGES),
            PetLine("Shifokorga borasizmi? Ma'lumotlaringizni QR orqali bir zumda ulashing.", PetPose.THINK, PetAction.DOCTOR_SHARE),
            PetLine("Savolingiz bormi? AI suhbatda istalgan payt so'rang.", PetPose.IDLE, PetAction.AI_CHAT),
            PetLine("Bugungi maqola sizni kutyapti — atigi uch daqiqalik o'qish.", PetPose.IDLE, PetAction.LEARN),
            PetLine("Kun oxirida bir-ikki qator yozish uyquni yengillashtiradi.", PetPose.THINK, PetAction.MIND_JOURNAL),
            PetLine("Dori yoki vitamin ichasizmi? Eslatma qo'ysangiz, vaqtida aytaman.", PetPose.THINK, PetAction.MEDICATIONS),
            PetLine("Bugun suv ichishni unutmang — kichik stakan ham hisobga kiradi.", PetPose.IDLE, PetAction.WATER),
        )
        Language.RU -> listOf(
            PetLine("Знаете? Сфотографируйте еду — я посчитаю её состав.", PetPose.THINK, PetAction.FOOD_SCANNER),
            PetLine("Подключите близкого через «Яқиним» — он тоже будет знать, когда нужна забота.", PetPose.THINK, PetAction.PARTNER),
            PetLine("Загляните в значки — до некоторых совсем чуть-чуть!", PetPose.IDLE, PetAction.BADGES),
            PetLine("Идёте к врачу? Поделитесь данными по QR за секунду.", PetPose.THINK, PetAction.DOCTOR_SHARE),
            PetLine("Есть вопрос? Спросите в AI-чате в любое время.", PetPose.IDLE, PetAction.AI_CHAT),
            PetLine("Статья дня ждёт вас — всего три минуты чтения.", PetPose.IDLE, PetAction.LEARN),
            PetLine("Пара строк в дневнике перед сном помогает уснуть легче.", PetPose.THINK, PetAction.MIND_JOURNAL),
            PetLine("Пьёте лекарства или витамины? Поставьте напоминание — я подскажу вовремя.", PetPose.THINK, PetAction.MEDICATIONS),
            PetLine("Не забывайте пить воду — даже маленький стакан считается.", PetPose.IDLE, PetAction.WATER),
        )
        Language.EN -> listOf(
            PetLine("Did you know? Snap a photo of your meal and I'll work out what's in it.", PetPose.THINK, PetAction.FOOD_SCANNER),
            PetLine("Link someone close with Yaqinim — they'll know when you need some care too.", PetPose.THINK, PetAction.PARTNER),
            PetLine("Peek at your badges — you're really close to a few!", PetPose.IDLE, PetAction.BADGES),
            PetLine("Seeing a doctor? Share your records with a QR code in a second.", PetPose.THINK, PetAction.DOCTOR_SHARE),
            PetLine("Got a question? Ask the AI chat any time.", PetPose.IDLE, PetAction.AI_CHAT),
            PetLine("Today's article is waiting — just a three-minute read.", PetPose.IDLE, PetAction.LEARN),
            PetLine("A couple of lines in your journal at night makes sleep come easier.", PetPose.THINK, PetAction.MIND_JOURNAL),
            PetLine("Taking medicine or vitamins? Set a reminder and I'll nudge you on time.", PetPose.THINK, PetAction.MEDICATIONS),
            PetLine("Don't forget water today — even a small glass counts.", PetPose.IDLE, PetAction.WATER),
        )
    }

    /** Each pet's word before a cheer; the calm lotus needs none. */
    fun opener(language: Language, pet: PetKind): String = when (pet) {
        PetKind.NILUFAR -> ""
        PetKind.MOMIQ -> when (language) { Language.UZ -> "Miyov! "; Language.RU -> "Мяу! "; Language.EN -> "Meow! " }
        PetKind.LAYLO -> when (language) { Language.UZ -> "Voy, jonim! "; Language.RU -> "Ой, дорогая! "; Language.EN -> "Oh, dear! " }
        PetKind.ANORXON -> when (language) { Language.UZ -> "Zo'r! "; Language.RU -> "Супер! "; Language.EN -> "Yes! " }
        PetKind.OHU -> when (language) { Language.UZ -> "Hi-hi! "; Language.RU -> "Хи-хи! "; Language.EN -> "Hee-hee! " }
    }

    // ------------------------------------------------------------------ the model

    /** Who the pet is, for the model. Short: the instruction is paid for on every call. */
    private fun persona(pet: PetKind): String = when (pet) {
        PetKind.NILUFAR -> "Nilufar, a tiny calm lotus-flower spirit; gentle, wise, soothing"
        PetKind.MOMIQ -> "Momiq, a fluffy cosy kitten; warm, a little lazy, loves rest and comfort"
        PetKind.LAYLO -> "Laylo, a baby stork; caring like an older sister, encouraging"
        PetKind.ANORXON -> "Anorxon, a cheerful pomegranate; energetic, upbeat, loves healthy food"
        PetKind.OHU -> "Ohu, a shy baby fawn; sweet, soft-spoken, kind"
    }

    private fun languageName(language: Language) = when (language) {
        Language.UZ -> "Uzbek (Latin script)"
        Language.RU -> "Russian"
        Language.EN -> "English"
    }

    /**
     * Who answers in the AI chat. Added after the chat's own rules, which it never
     * loosens: the answer is still general wellness with the disclaimer appended by
     * code. The character is a voice, not a licence to be cute about symptoms.
     */
    fun chatPersona(pet: PetKind): String = """
        You are ${persona(pet)}, her personal companion in Sadora (the app calls you SADORA AI).
        Speak in first person, warmly, in your own character, but keep the character to a light
        touch: at most one short in-character phrase per answer, and none at all when she
        describes pain, bleeding, low mood or anything worrying — then be plainly calm and kind.
        Every rule above still applies in full.
    """.trimIndent()

    fun instruction(language: Language, pet: PetKind): String = """
        You are ${persona(pet)} — the companion mascot in Sadora, a women's health app.
        Write ONE short speech-bubble line in ${languageName(language)}, at most 140 characters,
        addressing the user politely (in Uzbek use "siz", in Russian "вы").
        General wellness only: no diagnosis, no medicine names or doses, no numbers,
        no calorie counts, no judgement about her body or weight. No emoji, no quotes,
        no hashtags. Answer with the line only.
    """.trimIndent()

    fun scanPrompt(dish: String): String =
        "She just photographed and logged this meal: \"$dish\". Say something kind and one light, practical tip that goes with it."

    fun phasePrompt(phase: CyclePhase): String {
        val phaseText = when (phase) {
            CyclePhase.PERIOD -> "her period"
            CyclePhase.FOLLICULAR -> "the follicular phase (energy usually rising)"
            CyclePhase.FERTILE -> "her fertile window"
            CyclePhase.LUTEAL -> "the luteal phase (the days before her period)"
        }
        return "She just logged her cycle day; she is in $phaseText. Give one gentle, practical self-care tip for this phase."
    }

    // ------------------------------------------------------------------ Uzbek

    private fun uz(trigger: PetTrigger, phase: CyclePhase?, dish: String?): List<PetLine> = when (trigger) {
        PetTrigger.CYCLE_LOGGED -> when (phase) {
            CyclePhase.PERIOD -> listOf(
                PetLine("Hayz kunlarida temirga boy ovqat — anor, ismaloq, loviya — kuch beradi.", PetPose.THINK),
                PetLine("Iliq choy va yengil cho'zilish og'riqni yumshatadi. O'zingizni ayang.", PetPose.IDLE),
            )
            CyclePhase.FOLLICULAR -> listOf(
                PetLine("Bu kunlarda energiya ko'payadi — yangi ishni boshlash uchun ayni payt!", PetPose.HAPPY),
            )
            CyclePhase.FERTILE -> listOf(
                PetLine("Unumdor kunlaringiz boshlandi — ular kalendarda belgilangan.", PetPose.THINK),
            )
            CyclePhase.LUTEAL -> listOf(
                PetLine("Hayzdan oldingi kunlarda shirinlikka moyillik oshadi — yong'oq va banan yordam beradi.", PetPose.THINK),
                PetLine("Bu kunlarda uyqu muhim. Bugun biroz ertaroq yotib ko'ring.", PetPose.IDLE),
            )
            null -> listOf(PetLine("Qayd qildingiz, barakalla! Har bir belgi bashoratni aniqroq qiladi.", PetPose.HAPPY))
        }
        PetTrigger.MEAL_LOGGED -> listOf(
            PetLine("Ovqat yozildi! Keyingi safar rasmga olib ko'ring — tarkibini o'zim hisoblayman.", PetPose.THINK, PetAction.FOOD_SCANNER),
            PetLine("Barakalla! Likopchada sabzavot ham bormi? Rang-barang likopcha — sog'lom likopcha.", PetPose.IDLE),
        )
        PetTrigger.FOOD_SCANNED -> if (dish != null) listOf(
            PetLine("$dish — yaxshi tanlov! Yoniga bir stakan suv ham iching.", PetPose.HAPPY),
        ) else listOf(
            PetLine("Mazali ko'rinadi! Tarkibini kunlik hisobga qo'shdim.", PetPose.HAPPY),
        )
        PetTrigger.WATER_GOAL -> listOf(PetLine("Bugungi suv me'yori bajarildi! Tanangiz rahmat aytadi.", PetPose.HAPPY))
        PetTrigger.MOOD_LOW -> listOf(
            PetLine("Bugun og'irroq kunga o'xshaydi. Bir-ikki qator yozib ko'ring — yengil tortasiz.", PetPose.IDLE, PetAction.MIND_JOURNAL),
            PetLine("Yoningizdaman. Agar bu holat uzoq davom etsa, shifokor bilan gaplashib ko'ring.", PetPose.IDLE, PetAction.MIND_JOURNAL),
        )
        PetTrigger.MOOD_GOOD -> listOf(PetLine("Kayfiyatingiz a'lo — men ham xursandman!", PetPose.HAPPY))
        PetTrigger.MED_TAKEN -> listOf(PetLine("Dori o'z vaqtida ichildi. Shunday davom eting!", PetPose.HAPPY))
        PetTrigger.BADGE_EARNED -> listOf(PetLine("Yangi nishon! Uni ismingiz yonida taqib yurishingiz mumkin.", PetPose.HAPPY, PetAction.BADGES))
        PetTrigger.STREAK_KEPT -> listOf(PetLine("Ketma-ket yana bir kun! Odatlar aynan shunday shakllanadi.", PetPose.HAPPY))
        PetTrigger.APP_OPEN -> emptyList()
    }

    // ------------------------------------------------------------------ Russian

    private fun ru(trigger: PetTrigger, phase: CyclePhase?, dish: String?): List<PetLine> = when (trigger) {
        PetTrigger.CYCLE_LOGGED -> when (phase) {
            CyclePhase.PERIOD -> listOf(
                PetLine("В дни месячных силы дают продукты с железом — гранат, шпинат, фасоль.", PetPose.THINK),
                PetLine("Тёплый чай и лёгкая растяжка смягчают боль. Берегите себя.", PetPose.IDLE),
            )
            CyclePhase.FOLLICULAR -> listOf(
                PetLine("Сейчас энергии больше — самое время начать что-то новое!", PetPose.HAPPY),
            )
            CyclePhase.FERTILE -> listOf(
                PetLine("Начались фертильные дни — они уже отмечены в календаре.", PetPose.THINK),
            )
            CyclePhase.LUTEAL -> listOf(
                PetLine("Перед месячными тянет на сладкое — орехи и банан помогут.", PetPose.THINK),
                PetLine("Сейчас особенно важен сон. Попробуйте лечь сегодня чуть раньше.", PetPose.IDLE),
            )
            null -> listOf(PetLine("Отмечено, умница! Каждая отметка делает прогноз точнее.", PetPose.HAPPY))
        }
        PetTrigger.MEAL_LOGGED -> listOf(
            PetLine("Записано! В следующий раз сфотографируйте блюдо — я посчитаю состав.", PetPose.THINK, PetAction.FOOD_SCANNER),
            PetLine("Умница! А овощи на тарелке есть? Яркая тарелка — здоровая тарелка.", PetPose.IDLE),
        )
        PetTrigger.FOOD_SCANNED -> if (dish != null) listOf(
            PetLine("$dish — хороший выбор! Выпейте к нему стакан воды.", PetPose.HAPPY),
        ) else listOf(
            PetLine("Выглядит вкусно! Состав уже в дневном итоге.", PetPose.HAPPY),
        )
        PetTrigger.WATER_GOAL -> listOf(PetLine("Норма воды на сегодня выполнена! Ваше тело говорит спасибо.", PetPose.HAPPY))
        PetTrigger.MOOD_LOW -> listOf(
            PetLine("Кажется, день непростой. Напишите пару строк — станет легче.", PetPose.IDLE, PetAction.MIND_JOURNAL),
            PetLine("Я рядом. Если такое состояние затянется, поговорите с врачом.", PetPose.IDLE, PetAction.MIND_JOURNAL),
        )
        PetTrigger.MOOD_GOOD -> listOf(PetLine("У вас отличное настроение — и я радуюсь вместе с вами!", PetPose.HAPPY))
        PetTrigger.MED_TAKEN -> listOf(PetLine("Лекарство принято вовремя. Так держать!", PetPose.HAPPY))
        PetTrigger.BADGE_EARNED -> listOf(PetLine("Новый значок! Его можно носить рядом с именем.", PetPose.HAPPY, PetAction.BADGES))
        PetTrigger.STREAK_KEPT -> listOf(PetLine("Ещё один день подряд! Именно так и складываются привычки.", PetPose.HAPPY))
        PetTrigger.APP_OPEN -> emptyList()
    }

    // ------------------------------------------------------------------ English

    private fun en(trigger: PetTrigger, phase: CyclePhase?, dish: String?): List<PetLine> = when (trigger) {
        PetTrigger.CYCLE_LOGGED -> when (phase) {
            CyclePhase.PERIOD -> listOf(
                PetLine("Iron-rich food — pomegranate, spinach, beans — helps on period days.", PetPose.THINK),
                PetLine("Warm tea and a gentle stretch can ease the cramps. Be kind to yourself.", PetPose.IDLE),
            )
            CyclePhase.FOLLICULAR -> listOf(
                PetLine("Energy tends to rise these days — a great time to start something new!", PetPose.HAPPY),
            )
            CyclePhase.FERTILE -> listOf(
                PetLine("Your fertile days have started — they're marked on the calendar.", PetPose.THINK),
            )
            CyclePhase.LUTEAL -> listOf(
                PetLine("Sweet cravings are common before a period — nuts and a banana can help.", PetPose.THINK),
                PetLine("Sleep matters a lot right now. Try turning in a little earlier tonight.", PetPose.IDLE),
            )
            null -> listOf(PetLine("Logged, well done! Every entry makes the forecast sharper.", PetPose.HAPPY))
        }
        PetTrigger.MEAL_LOGGED -> listOf(
            PetLine("Logged! Next time snap a photo — I'll work out what's in it.", PetPose.THINK, PetAction.FOOD_SCANNER),
            PetLine("Nice! Any veggies on the plate? A colourful plate is a healthy plate.", PetPose.IDLE),
        )
        PetTrigger.FOOD_SCANNED -> if (dish != null) listOf(
            PetLine("$dish — good choice! Have a glass of water with it.", PetPose.HAPPY),
        ) else listOf(
            PetLine("Looks tasty! I've added it to today's totals.", PetPose.HAPPY),
        )
        PetTrigger.WATER_GOAL -> listOf(PetLine("Today's water goal done! Your body says thank you.", PetPose.HAPPY))
        PetTrigger.MOOD_LOW -> listOf(
            PetLine("Looks like a heavy day. Try writing a few lines — it can lighten things.", PetPose.IDLE, PetAction.MIND_JOURNAL),
            PetLine("I'm right here. If this feeling lasts, it's worth talking to a doctor.", PetPose.IDLE, PetAction.MIND_JOURNAL),
        )
        PetTrigger.MOOD_GOOD -> listOf(PetLine("You're in a great mood — and that makes me happy too!", PetPose.HAPPY))
        PetTrigger.MED_TAKEN -> listOf(PetLine("Taken right on time. Keep it up!", PetPose.HAPPY))
        PetTrigger.BADGE_EARNED -> listOf(PetLine("A new badge! You can wear it next to your name.", PetPose.HAPPY, PetAction.BADGES))
        PetTrigger.STREAK_KEPT -> listOf(PetLine("Another day in a row! That's exactly how habits form.", PetPose.HAPPY))
        PetTrigger.APP_OPEN -> emptyList()
    }
}
