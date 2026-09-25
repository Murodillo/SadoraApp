package uz.sadora.server.ai

import uz.sadora.contract.CyclePhase
import uz.sadora.contract.Language

/** Which part of her day it is, in her own timezone. */
enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * What the greeting is allowed to know.
 *
 * Deliberately small and deliberately coarse: booleans and a phase, never a raw number
 * she did not put there herself. The line under her name is the most-read sentence in
 * the app and it is not a place to quote her weight back at her.
 */
data class GreetingContext(
    val name: String,
    val part: DayPart,
    val streak: Int = 0,
    val phase: CyclePhase? = null,
    val cycleDay: Int? = null,
    /** True when last night cleared seven hours; null when nothing was recorded. */
    val sleptWell: Boolean? = null,
    /** True when today's check-in was 4 or 5; null when she has not checked in. */
    val feelingGood: Boolean? = null,
    val waterGoalMet: Boolean = false,
    val isNewAccount: Boolean = false,
) {
    /**
     * The cache key's context half.
     *
     * Only the fields the greeting actually varies on, so a step count ticking over does
     * not throw away a batch of lines the model was paid for.
     */
    fun signature(): String = listOf(
        part.name,
        streakBand(),
        phase?.name ?: "-",
        sleptWell?.toString() ?: "-",
        feelingGood?.toString() ?: "-",
        if (waterGoalMet) "w" else "-",
        if (isNewAccount) "new" else "-",
    ).joinToString(":")

    /** Streaks are banded, or every day would be a new cache key and a new model call. */
    fun streakBand(): String = when {
        streak >= 100 -> "100+"
        streak >= 30 -> "30+"
        streak >= 7 -> "7+"
        streak >= 3 -> "3+"
        streak >= 1 -> "1+"
        else -> "0"
    }
}

/**
 * The greeting under "Salom, Malika!".
 *
 * Two writers produce it. A model writes it when there is a key and the operator's
 * switch is on; this object writes it otherwise — and it is not a placeholder. The pools
 * below are the product's own voice, and picking from them at random is what makes the
 * fallback change every time too. A greeting that repeats is wallpaper, and she stops
 * reading it on the third day.
 *
 * Every line is a compliment or an encouragement, never an instruction and never a
 * judgement. "You slept badly" is not a greeting; "the day can be gentle" is.
 */
object GreetingPhrases {

    /**
     * A line for this moment, chosen at random from everything that fits.
     *
     * [seed] comes from the caller so a test can pin it; in production it is the clock,
     * which is what makes two opens a minute apart read differently.
     */
    fun line(language: Language, context: GreetingContext, seed: Long): String {
        val pool = pool(language, context)
        if (pool.isEmpty()) return fallback(language)
        val index = ((seed % pool.size) + pool.size) % pool.size
        return pool[index.toInt()]
    }

    /**
     * Everything that fits the moment: the time of day always, plus whatever the context
     * has earned. A long streak is worth saying out loud; an empty account is not.
     */
    fun pool(language: Language, context: GreetingContext): List<String> = buildList {
        addAll(timeOfDay(language, context.part))
        if (context.isNewAccount) {
            addAll(welcome(language))
            return@buildList
        }
        if (context.streak >= 3) addAll(streak(language, context.streak))
        context.phase?.let { addAll(phase(language, it)) }
        when (context.sleptWell) {
            true -> addAll(sleptWell(language))
            false -> addAll(sleptPoorly(language))
            null -> Unit
        }
        if (context.feelingGood == true) addAll(feelingGood(language))
        if (context.waterGoalMet) addAll(waterDone(language))
    }

    // ---------------------------------------------------------------- the model

    /**
     * What the model is told it is writing.
     *
     * The constraints are the product's, not the model's preferences: one sentence,
     * warm, no advice, no diagnosis, no exclamation storm — and above all no medical
     * claim, because this line sits above a health screen and would be read as one.
     */
    fun instruction(language: Language): String = when (language) {
        Language.UZ ->
            "Siz SADORA — ayollar salomatligi ilovasining iliq ovozisiz. Sizning vazifangiz: " +
                "bosh ekranda ismning tagida turadigan bitta qisqa jumla yozish. Qoidalar: " +
                "har bir jumla 4–9 so'z; iliq, samimiy, ozgina kompliment; maslahat bermang; " +
                "tashxis qo'ymang; dori yoki tibbiy ko'rsatma haqida yozmang; ayolning tanasi " +
                "haqida hukm chiqarmang; bitta emoji ruxsat (majburiy emas); undov belgisi ko'pi " +
                "bilan bitta. Faqat o'zbek tilida yozing."

        Language.RU ->
            "Вы — тёплый голос SADORA, приложения о женском здоровье. Задача: написать одну " +
                "короткую фразу, которая стоит на главном экране под именем. Правила: 4–9 слов; " +
                "тепло, искренне, с лёгким комплиментом; не давайте советов; не ставьте диагнозов; " +
                "ничего о лекарствах и медицинских предписаниях; никаких оценок её тела; можно один " +
                "эмодзи; не более одного восклицательного знака. Пишите только по-русски."

        Language.EN ->
            "You are the warm voice of SADORA, a women's health app. Your task: write one short " +
                "line that sits under her name on the home screen. Rules: 4–9 words; warm, sincere, " +
                "lightly complimentary; no advice; no diagnosis; nothing about medication or medical " +
                "instruction; no judgement about her body; one emoji allowed; at most one exclamation " +
                "mark. Write in English only."
    }

    /** The turn that asks for a batch, with the moment described and nothing else. */
    fun prompt(language: Language, context: GreetingContext, count: Int): String {
        val facts = facts(language, context)
        return when (language) {
            Language.UZ ->
                "Hozirgi holat: $facts. Shu holatga mos $count ta har xil jumla yozing. " +
                    "Har birini yangi qatorda, raqamsiz va tirnoqsiz bering."

            Language.RU ->
                "Ситуация сейчас: $facts. Напишите $count разных фраз под эту ситуацию. " +
                    "Каждую с новой строки, без нумерации и кавычек."

            Language.EN ->
                "Right now: $facts. Write $count different lines for this moment. " +
                    "One per line, no numbering and no quotation marks."
        }
    }

    /**
     * The moment, in words.
     *
     * Note what is not here: no name, no age, no weight, no raw numbers. The model is
     * told it is evening and that she has kept a streak — not who she is.
     */
    private fun facts(language: Language, context: GreetingContext): String = buildList {
        add(partWord(language, context.part))
        if (context.streak >= 3) add(streakWord(language, context.streak))
        context.phase?.let { add(phaseWord(language, it)) }
        when (context.sleptWell) {
            true -> add(word(language, "yaxshi uxlagan", "хорошо выспалась", "slept well"))
            false -> add(word(language, "kam uxlagan", "мало спала", "slept little"))
            null -> Unit
        }
        if (context.feelingGood == true) {
            add(word(language, "kayfiyati yaxshi", "настроение хорошее", "in good spirits"))
        }
        if (context.waterGoalMet) {
            add(word(language, "suv maqsadi bajarilgan", "цель по воде выполнена", "water goal met"))
        }
        if (context.isNewAccount) {
            add(word(language, "ilovaga endi qo'shildi", "только что присоединилась", "just joined"))
        }
    }.joinToString(", ")

    // ---------------------------------------------------------------- the pools

    private fun timeOfDay(language: Language, part: DayPart): List<String> = when (language) {
        Language.UZ -> when (part) {
            DayPart.MORNING -> listOf(
                "Yangi kun sizni kutyapti 🌸",
                "Bugun o'zingizga vaqt ajrating",
                "Ertalabki tinchlik — sizniki",
                "Sekin boshlang, shoshilmang",
                "Bugun ham yoningizdaman",
                "Kun sizning sur'atingizda ketsin",
            )
            DayPart.AFTERNOON -> listOf(
                "Kun o'rtasi — bir nafas oling",
                "Yaxshi ketyapsiz, davom eting",
                "Bir piyola choy yomon bo'lmasdi",
                "O'zingizga bir lahza ajrating",
                "Bugun ham ko'p narsa qildingiz",
            )
            DayPart.EVENING -> listOf(
                "Kechqurun tinchligi muborak 🌙",
                "Bugun uchun rahmat o'zingizga",
                "Kun tugadi — endi dam",
                "Yumshoq kechga o'ting",
                "Bugun qilganingiz yetarli",
            )
            DayPart.NIGHT -> listOf(
                "Tun tinch o'tsin 🌙",
                "Endi dam olish vaqti",
                "Yumshoq tushlar ko'ring",
                "Ekranni qo'yib, dam oling",
            )
        }

        Language.RU -> when (part) {
            DayPart.MORNING -> listOf(
                "Новый день уже ваш 🌸",
                "Уделите себе немного времени",
                "Утренняя тишина — для вас",
                "Начните спокойно, без спешки",
                "Сегодня я рядом с вами",
                "Пусть день идёт в вашем темпе",
            )
            DayPart.AFTERNOON -> listOf(
                "Середина дня — выдохните",
                "Вы отлично справляетесь",
                "Чашка чая была бы кстати",
                "Найдите минуту для себя",
                "Сегодня вы уже много сделали",
            )
            DayPart.EVENING -> listOf(
                "Тихого вам вечера 🌙",
                "Скажите себе спасибо за день",
                "День закончился — теперь отдых",
                "Переходите в мягкий вечер",
                "Сделанного сегодня достаточно",
            )
            DayPart.NIGHT -> listOf(
                "Пусть ночь будет спокойной 🌙",
                "Самое время отдохнуть",
                "Мягких вам снов",
                "Отложите экран и отдохните",
            )
        }

        Language.EN -> when (part) {
            DayPart.MORNING -> listOf(
                "A new day, already yours 🌸",
                "Give yourself some time today",
                "The quiet morning is yours",
                "Start slowly, there's no rush",
                "I'm here with you today",
                "Let the day keep your pace",
            )
            DayPart.AFTERNOON -> listOf(
                "Midday — take one breath",
                "You're doing well, keep going",
                "A cup of tea would suit now",
                "Find one minute for yourself",
                "You've done plenty already",
            )
            DayPart.EVENING -> listOf(
                "Wishing you a quiet evening 🌙",
                "Thank yourself for today",
                "The day is done — rest now",
                "Ease into a soft evening",
                "What you did today is enough",
            )
            DayPart.NIGHT -> listOf(
                "May the night be peaceful 🌙",
                "It's time to rest now",
                "Soft dreams tonight",
                "Put the screen down and rest",
            )
        }
    }

    private fun streak(language: Language, days: Int): List<String> = when (language) {
        Language.UZ -> listOf(
            "$days kun ketma-ket — ajoyib odat ✨",
            "$days kun o'zingizga g'amxo'rlik qildingiz",
            "Bu izchillik chinakam kuch",
            "$days kun — bu tasodif emas",
        )
        Language.RU -> listOf(
            "$days дней подряд — прекрасная привычка ✨",
            "$days дней заботы о себе",
            "Такая последовательность — настоящая сила",
            "$days дней — это не случайность",
        )
        Language.EN -> listOf(
            "$days days running — a fine habit ✨",
            "$days days of looking after yourself",
            "That consistency is real strength",
            "$days days is no accident",
        )
    }

    private fun phase(language: Language, phase: CyclePhase): List<String> = when (language) {
        Language.UZ -> when (phase) {
            CyclePhase.PERIOD -> listOf("Bugun o'zingizga yumshoqroq bo'ling", "Bu kunlar dam so'raydi")
            CyclePhase.FOLLICULAR -> listOf("Energiya qaytyapti — sezyapsizmi?", "Yangi narsaga yaxshi kun")
            CyclePhase.FERTILE -> listOf("Bugun o'zingizni yorqin his qilasiz", "Kuchingiz yuqori pallada")
            CyclePhase.LUTEAL -> listOf("Sekinroq sur'at ham to'g'ri", "Bugun tinchlik qidiring")
        }
        Language.RU -> when (phase) {
            CyclePhase.PERIOD -> listOf("Будьте сегодня мягче к себе", "Эти дни просят отдыха")
            CyclePhase.FOLLICULAR -> listOf("Энергия возвращается — чувствуете?", "Хороший день для нового")
            CyclePhase.FERTILE -> listOf("Сегодня вы особенно яркая", "Ваши силы на подъёме")
            CyclePhase.LUTEAL -> listOf("Более спокойный темп — тоже верно", "Поищите сегодня тишину")
        }
        Language.EN -> when (phase) {
            CyclePhase.PERIOD -> listOf("Be gentler with yourself today", "These days ask for rest")
            CyclePhase.FOLLICULAR -> listOf("Energy is coming back — feel it?", "A good day for something new")
            CyclePhase.FERTILE -> listOf("You're especially bright today", "Your energy is high right now")
            CyclePhase.LUTEAL -> listOf("A slower pace is right too", "Look for some quiet today")
        }
    }

    private fun sleptWell(language: Language): List<String> = when (language) {
        Language.UZ -> listOf("Yaxshi uyqu — kunning yarmi", "Bugun tetik ko'rinasiz ✨")
        Language.RU -> listOf("Хороший сон — половина дня", "Сегодня вы выглядите бодрой ✨")
        Language.EN -> listOf("Good sleep is half the day", "You look rested today ✨")
    }

    /**
     * A short night is never framed as a failure — and never followed by advice. The
     * app's job here is to be kind, not to prescribe an earlier bedtime.
     */
    private fun sleptPoorly(language: Language): List<String> = when (language) {
        Language.UZ -> listOf("Bugun sekinroq ketsak ham bo'ladi", "O'zingizdan ko'p talab qilmang")
        Language.RU -> listOf("Сегодня можно и помедленнее", "Не требуйте от себя многого")
        Language.EN -> listOf("Today can go slowly", "Don't ask too much of yourself")
    }

    private fun feelingGood(language: Language): List<String> = when (language) {
        Language.UZ -> listOf("Kayfiyatingiz yuqumli 🌸", "Bugungi kuchingiz ko'rinib turibdi")
        Language.RU -> listOf("Ваше настроение заразительно 🌸", "Сегодня в вас видно силу")
        Language.EN -> listOf("Your mood is catching 🌸", "Your strength shows today")
    }

    private fun waterDone(language: Language): List<String> = when (language) {
        Language.UZ -> listOf("Suv maqsadi bajarildi — zo'r 💧")
        Language.RU -> listOf("Цель по воде выполнена — отлично 💧")
        Language.EN -> listOf("Water goal met — well done 💧")
    }

    private fun welcome(language: Language): List<String> = when (language) {
        Language.UZ -> listOf(
            "Xush kelibsiz — birga boshlaymiz 🌸",
            "Shu yerda bo'lganingizdan xursandmiz",
            "Birinchi qadam eng muhimi",
        )
        Language.RU -> listOf(
            "Добро пожаловать — начнём вместе 🌸",
            "Рады, что вы здесь",
            "Первый шаг — самый важный",
        )
        Language.EN -> listOf(
            "Welcome — let's start together 🌸",
            "Glad you're here",
            "The first step matters most",
        )
    }

    private fun fallback(language: Language): String =
        word(language, "Bugun o'zingizga g'amxo'rlik qiling 🌸", "Позаботьтесь сегодня о себе 🌸", "Take care of yourself today 🌸")

    // ---------------------------------------------------------------- words

    private fun partWord(language: Language, part: DayPart): String = when (part) {
        DayPart.MORNING -> word(language, "ertalab", "утро", "morning")
        DayPart.AFTERNOON -> word(language, "kunduzi", "день", "afternoon")
        DayPart.EVENING -> word(language, "kechqurun", "вечер", "evening")
        DayPart.NIGHT -> word(language, "tun", "ночь", "night")
    }

    private fun streakWord(language: Language, days: Int): String =
        word(language, "$days kun ketma-ket ochgan", "заходит $days дней подряд", "$days-day streak")

    private fun phaseWord(language: Language, phase: CyclePhase): String = when (phase) {
        CyclePhase.PERIOD -> word(language, "hayz kunlari", "дни менструации", "period days")
        CyclePhase.FOLLICULAR -> word(language, "follikulyar faza", "фолликулярная фаза", "follicular phase")
        CyclePhase.FERTILE -> word(language, "unumdor kunlar", "фертильные дни", "fertile window")
        CyclePhase.LUTEAL -> word(language, "lyuteal faza", "лютеиновая фаза", "luteal phase")
    }

    private fun word(language: Language, uz: String, ru: String, en: String): String = when (language) {
        Language.UZ -> uz
        Language.RU -> ru
        Language.EN -> en
    }
}
