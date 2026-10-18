package uz.sadora.server.lab

import uz.sadora.contract.LabMarker
import uz.sadora.contract.Language

/**
 * The markers the app knows by name: the ones a woman's sheets carry most often.
 *
 * Each has a common name and two sentences on what it is, in all three languages. The
 * sentences say what the marker measures and what it is usually checked for — never
 * what her number means for her, and never what to do about it beyond asking her doctor.
 * There is deliberately no reference range here: the laboratory prints its own, and
 * that is the only one the app compares against.
 *
 * [aliases] are how the marker is printed on sheets in Uzbekistan — Russian, Latin
 * abbreviations, English — matched after [normalise]. The model is also given the codes
 * and asked to pick one, so the aliases are the fallback, not the whole recognition.
 */
data class KnownMarker(
    val code: String,
    val group: String,
    val unit: String,
    val names: Names,
    val about: Names,
    val aliases: List<String>,
) {
    data class Names(val uz: String, val ru: String, val en: String) {
        fun of(language: Language) = when (language) {
            Language.UZ -> uz
            Language.RU -> ru
            Language.EN -> en
        }
    }

    fun dto(language: Language) = LabMarker(code, names.of(language), unit, about.of(language), group)
}

object LabMarkers {

    val all: List<KnownMarker> = listOf(
        // ------------------------------------------------------------ blood count
        KnownMarker(
            code = "hgb", group = "blood", unit = "g/L",
            names = KnownMarker.Names("Gemoglobin", "Гемоглобин", "Haemoglobin"),
            about = KnownMarker.Names(
                "Qizil qon tanachalaridagi kislorod tashuvchi oqsil. Kamqonlikni aniqlashda eng ko'p qaraladigan ko'rsatkich.",
                "Белок эритроцитов, переносящий кислород. Главный показатель при поиске анемии.",
                "The protein in red blood cells that carries oxygen. The main number checked for anaemia.",
            ),
            aliases = listOf("hemoglobin", "haemoglobin", "hgb", "hb", "гемоглобин", "gemoglobin"),
        ),
        KnownMarker(
            code = "rbc", group = "blood", unit = "10^12/L",
            names = KnownMarker.Names("Eritrotsitlar", "Эритроциты", "Red blood cells"),
            about = KnownMarker.Names(
                "Qondagi qizil qon tanachalari soni. Gemoglobin bilan birga kamqonlik turini baholashga yordam beradi.",
                "Количество красных клеток крови. Вместе с гемоглобином помогает оценить тип анемии.",
                "How many red cells the blood carries. Read together with haemoglobin to judge the kind of anaemia.",
            ),
            aliases = listOf("rbc", "erythrocytes", "red blood cells", "эритроциты", "eritrotsitlar", "eritrositlar"),
        ),
        KnownMarker(
            code = "hct", group = "blood", unit = "%",
            names = KnownMarker.Names("Gematokrit", "Гематокрит", "Haematocrit"),
            about = KnownMarker.Names(
                "Qon hajmining qancha qismi qizil qon tanachalaridan iboratligi. Suvsizlanish va kamqonlikda o'zgaradi.",
                "Доля объёма крови, которую занимают эритроциты. Меняется при обезвоживании и анемии.",
                "The share of the blood's volume made up of red cells. It shifts with dehydration and anaemia.",
            ),
            aliases = listOf("hct", "hematocrit", "haematocrit", "гематокрит", "gematokrit"),
        ),
        KnownMarker(
            code = "wbc", group = "blood", unit = "10^9/L",
            names = KnownMarker.Names("Leykotsitlar", "Лейкоциты", "White blood cells"),
            about = KnownMarker.Names(
                "Organizmni infeksiyadan himoya qiluvchi oq qon tanachalari soni. Yallig'lanish va infeksiyalarda o'zgaradi.",
                "Количество белых клеток крови, защищающих от инфекций. Меняется при воспалении и инфекциях.",
                "The count of white cells that fight infection. It changes with inflammation and infections.",
            ),
            aliases = listOf("wbc", "leukocytes", "white blood cells", "лейкоциты", "leykotsitlar", "leykositlar"),
        ),
        KnownMarker(
            code = "plt", group = "blood", unit = "10^9/L",
            names = KnownMarker.Names("Trombotsitlar", "Тромбоциты", "Platelets"),
            about = KnownMarker.Names(
                "Qon ivishida qatnashadigan hujayralar soni. Ko'p qon ketish yoki homiladorlikda alohida kuzatiladi.",
                "Количество клеток, участвующих в свёртывании крови. Важно при обильных кровотечениях и беременности.",
                "The cells that help blood clot. Watched closely with heavy bleeding and in pregnancy.",
            ),
            aliases = listOf("plt", "platelets", "тромбоциты", "trombotsitlar", "trombositlar"),
        ),
        KnownMarker(
            code = "esr", group = "blood", unit = "mm/h",
            names = KnownMarker.Names("EChT (SOE)", "СОЭ", "ESR"),
            about = KnownMarker.Names(
                "Eritrotsitlarning cho'kish tezligi — yallig'lanishning umumiy belgisi. Homiladorlikda tabiiy ravishda oshadi.",
                "Скорость оседания эритроцитов — общий признак воспаления. При беременности естественно повышается.",
                "How fast red cells settle — a general sign of inflammation. It rises naturally in pregnancy.",
            ),
            aliases = listOf("esr", "соэ", "soe", "echt", "скорость оседания эритроцитов", "erythrocyte sedimentation rate"),
        ),

        // ------------------------------------------------------------ iron and vitamins
        KnownMarker(
            code = "ferritin", group = "iron", unit = "ng/mL",
            names = KnownMarker.Names("Ferritin", "Ферритин", "Ferritin"),
            about = KnownMarker.Names(
                "Organizmdagi temir zaxirasini ko'rsatadi. Gemoglobin hali normal bo'lganda ham temir kamayganini erta bildiradi.",
                "Показывает запас железа в организме. Сообщает о нехватке железа раньше, чем падает гемоглобин.",
                "Shows the body's iron stores. It reveals low iron before haemoglobin falls.",
            ),
            aliases = listOf("ferritin", "ферритин"),
        ),
        KnownMarker(
            code = "iron", group = "iron", unit = "µmol/L",
            names = KnownMarker.Names("Zardob temiri", "Сывороточное железо", "Serum iron"),
            about = KnownMarker.Names(
                "Qonda hozir aylanib yurgan temir miqdori. Kun davomida va ovqatdan keyin ancha o'zgaradi.",
                "Количество железа, циркулирующего в крови сейчас. Сильно колеблется в течение дня и после еды.",
                "The iron in the blood right now. It moves a lot through the day and after meals.",
            ),
            aliases = listOf("iron", "serum iron", "fe", "железо", "сывороточное железо", "temir", "zardob temiri"),
        ),
        KnownMarker(
            code = "b12", group = "iron", unit = "pg/mL",
            names = KnownMarker.Names("B12 vitamini", "Витамин B12", "Vitamin B12"),
            about = KnownMarker.Names(
                "Qon hosil bo'lishi va asab tizimi uchun kerakli vitamin. Uning yetishmasligi ham kamqonlikka olib kelishi mumkin.",
                "Витамин, нужный для кроветворения и нервной системы. Его нехватка тоже может вызывать анемию.",
                "A vitamin needed to make blood and for the nerves. Too little of it can also cause anaemia.",
            ),
            aliases = listOf("b12", "vitamin b12", "cobalamin", "витамин b12", "кобаламин", "b12 vitamini"),
        ),
        KnownMarker(
            code = "folate", group = "iron", unit = "ng/mL",
            names = KnownMarker.Names("Foliy kislotasi", "Фолиевая кислота", "Folate"),
            about = KnownMarker.Names(
                "Hujayralar bo'linishi uchun zarur vitamin. Homiladorlikni rejalashtirish va boshlang'ich haftalarida ayniqsa muhim.",
                "Витамин, необходимый для деления клеток. Особенно важен при планировании и в первые недели беременности.",
                "A vitamin cells need to divide. It matters most when planning a pregnancy and in its first weeks.",
            ),
            aliases = listOf("folate", "folic acid", "фолиевая кислота", "фолаты", "foliy kislotasi"),
        ),
        KnownMarker(
            code = "vitd", group = "iron", unit = "ng/mL",
            names = KnownMarker.Names("D vitamini", "Витамин D", "Vitamin D"),
            about = KnownMarker.Names(
                "25(OH)D — organizmdagi D vitamini zaxirasi. Suyaklar, immunitet va kayfiyat bilan bog'liq.",
                "25(OH)D — запас витамина D в организме. Связан с костями, иммунитетом и настроением.",
                "25(OH)D — the body's store of vitamin D. Linked to bones, immunity and mood.",
            ),
            aliases = listOf("vitamin d", "25-oh", "25(oh)d", "25-oh vitamin d", "витамин d", "25-oh витамин d", "d vitamini", "calcidiol"),
        ),

        // ------------------------------------------------------------ thyroid
        KnownMarker(
            code = "tsh", group = "thyroid", unit = "mIU/L",
            names = KnownMarker.Names("TTG (TSH)", "ТТГ", "TSH"),
            about = KnownMarker.Names(
                "Qalqonsimon bezni boshqaruvchi gormon. Bez faoliyatini tekshirishda birinchi qaraladigan ko'rsatkich.",
                "Гормон, управляющий щитовидной железой. Первый показатель при проверке её работы.",
                "The hormone that drives the thyroid. The first number checked when looking at how it works.",
            ),
            aliases = listOf("tsh", "ттг", "ttg", "thyrotropin", "тиреотропный гормон", "thyroid stimulating hormone"),
        ),
        KnownMarker(
            code = "ft4", group = "thyroid", unit = "pmol/L",
            names = KnownMarker.Names("Erkin T4", "Свободный Т4", "Free T4"),
            about = KnownMarker.Names(
                "Qalqonsimon bez ishlab chiqaradigan asosiy gormon. Odatda TTG bilan birga baholanadi.",
                "Основной гормон, который вырабатывает щитовидная железа. Обычно оценивается вместе с ТТГ.",
                "The main hormone the thyroid makes. Usually read together with TSH.",
            ),
            aliases = listOf("ft4", "free t4", "т4 свободный", "свободный т4", "т4 св", "erkin t4", "free thyroxine"),
        ),
        KnownMarker(
            code = "anti_tpo", group = "thyroid", unit = "IU/mL",
            names = KnownMarker.Names("TPO ga antitana", "Антитела к ТПО", "Anti-TPO"),
            about = KnownMarker.Names(
                "Qalqonsimon bezga qarshi antitanalar. Autoimmun tiroiditni aniqlashda qaraladi.",
                "Антитела против щитовидной железы. Смотрят при подозрении на аутоиммунный тиреоидит.",
                "Antibodies against the thyroid. Checked when an autoimmune thyroiditis is suspected.",
            ),
            aliases = listOf("anti-tpo", "anti tpo", "atpo", "ат-тпо", "ат к тпо", "антитела к тпо", "tpo ab"),
        ),

        // ------------------------------------------------------------ hormones
        KnownMarker(
            code = "hcg", group = "hormones", unit = "mIU/mL",
            names = KnownMarker.Names("XGCh (hCG)", "ХГЧ", "hCG"),
            about = KnownMarker.Names(
                "Homiladorlik gormoni. Dastlabki haftalarda bir necha kunda ikki baravar oshib boradi, shuning uchun takroriy natijalar solishtiriladi.",
                "Гормон беременности. В первые недели удваивается каждые несколько дней, поэтому сравнивают повторные результаты.",
                "The pregnancy hormone. Early on it doubles every few days, so repeat results are compared.",
            ),
            aliases = listOf("hcg", "b-hcg", "beta-hcg", "хгч", "бета-хгч", "xgch", "human chorionic gonadotropin"),
        ),
        KnownMarker(
            code = "progesterone", group = "hormones", unit = "nmol/L",
            names = KnownMarker.Names("Progesteron", "Прогестерон", "Progesterone"),
            about = KnownMarker.Names(
                "Ovulyatsiyadan keyin ko'tariladigan gormon. Natija tsiklning qaysi kunida olinganiga qarab o'qiladi.",
                "Гормон, который поднимается после овуляции. Результат читают с учётом дня цикла.",
                "A hormone that rises after ovulation. The result is read against the day of the cycle it was taken on.",
            ),
            aliases = listOf("progesterone", "прогестерон", "progesteron"),
        ),
        KnownMarker(
            code = "estradiol", group = "hormones", unit = "pmol/L",
            names = KnownMarker.Names("Estradiol", "Эстрадиол", "Oestradiol"),
            about = KnownMarker.Names(
                "Asosiy ayol jinsiy gormoni. Uning darajasi tsikl davomida va menopauzaga yaqinlashganda o'zgaradi.",
                "Основной женский половой гормон. Его уровень меняется в течение цикла и с приближением менопаузы.",
                "The main female sex hormone. Its level moves through the cycle and as menopause nears.",
            ),
            aliases = listOf("estradiol", "oestradiol", "e2", "эстрадиол"),
        ),
        KnownMarker(
            code = "fsh", group = "hormones", unit = "mIU/mL",
            names = KnownMarker.Names("FSG (FSH)", "ФСГ", "FSH"),
            about = KnownMarker.Names(
                "Tuxumdonlarda follikul o'sishini boshqaruvchi gormon. Odatda tsiklning 2–5-kunida topshiriladi.",
                "Гормон, управляющий ростом фолликулов в яичниках. Обычно сдают на 2–5 день цикла.",
                "The hormone that drives follicles in the ovaries. Usually taken on day 2–5 of the cycle.",
            ),
            aliases = listOf("fsh", "фсг", "fsg", "follicle stimulating hormone", "фолликулостимулирующий гормон"),
        ),
        KnownMarker(
            code = "lh", group = "hormones", unit = "mIU/mL",
            names = KnownMarker.Names("LG (LH)", "ЛГ", "LH"),
            about = KnownMarker.Names(
                "Ovulyatsiyani boshlab beruvchi gormon. Ko'pincha FSG bilan nisbati ham baholanadi.",
                "Гормон, запускающий овуляцию. Часто оценивают и его соотношение с ФСГ.",
                "The hormone that triggers ovulation. Its ratio to FSH is often looked at too.",
            ),
            aliases = listOf("lh", "лг", "lg", "luteinizing hormone", "лютеинизирующий гормон"),
        ),
        KnownMarker(
            code = "prolactin", group = "hormones", unit = "mIU/L",
            names = KnownMarker.Names("Prolaktin", "Пролактин", "Prolactin"),
            about = KnownMarker.Names(
                "Sut ishlab chiqarishni boshqaruvchi gormon. Stress, uyqu va emizish uning darajasiga ta'sir qiladi.",
                "Гормон, управляющий выработкой молока. На его уровень влияют стресс, сон и кормление грудью.",
                "The hormone behind milk production. Stress, sleep and breastfeeding all move it.",
            ),
            aliases = listOf("prolactin", "prl", "пролактин", "prolaktin"),
        ),
        KnownMarker(
            code = "amh", group = "hormones", unit = "ng/mL",
            names = KnownMarker.Names("AMG (AMH)", "АМГ", "AMH"),
            about = KnownMarker.Names(
                "Tuxumdon zaxirasi haqida taxminiy tasavvur beradi. Homilador bo'lish imkoniyatini yolg'iz o'zi aniqlamaydi.",
                "Даёт примерное представление о запасе яйцеклеток. Сам по себе не определяет шансы на беременность.",
                "Gives a rough idea of the egg reserve. On its own it does not decide the chances of pregnancy.",
            ),
            aliases = listOf("amh", "амг", "amg", "anti-mullerian hormone", "антимюллеров гормон"),
        ),
        KnownMarker(
            code = "testosterone", group = "hormones", unit = "nmol/L",
            names = KnownMarker.Names("Testosteron", "Тестостерон", "Testosterone"),
            about = KnownMarker.Names(
                "Ayollarda oz miqdorda bo'ladigan gormon. Tartibsiz tsikl, husnbuzar yoki ortiqcha tuk o'sishida tekshiriladi.",
                "Гормон, который у женщин есть в небольшом количестве. Проверяют при нерегулярном цикле, акне или избыточном росте волос.",
                "A hormone women have in small amounts. Checked with irregular cycles, acne or extra hair growth.",
            ),
            aliases = listOf("testosterone", "total testosterone", "тестостерон", "тестостерон общий", "testosteron"),
        ),

        // ------------------------------------------------------------ metabolic
        KnownMarker(
            code = "glucose", group = "metabolic", unit = "mmol/L",
            names = KnownMarker.Names("Glyukoza", "Глюкоза", "Glucose"),
            about = KnownMarker.Names(
                "Qondagi qand miqdori, odatda och qoringa o'lchanadi. Homiladorlikda alohida kuzatiladi.",
                "Уровень сахара в крови, обычно натощак. При беременности за ним следят отдельно.",
                "The sugar in the blood, usually measured fasting. Watched separately in pregnancy.",
            ),
            aliases = listOf("glucose", "glu", "глюкоза", "glyukoza", "сахар крови", "fasting glucose"),
        ),
        KnownMarker(
            code = "hba1c", group = "metabolic", unit = "%",
            names = KnownMarker.Names("Glikirlangan gemoglobin", "Гликированный гемоглобин", "HbA1c"),
            about = KnownMarker.Names(
                "So'nggi 2–3 oydagi o'rtacha qand darajasini ko'rsatadi. Bir martalik glyukoza o'lchovidan barqarorroq.",
                "Показывает средний уровень сахара за последние 2–3 месяца. Устойчивее разового замера глюкозы.",
                "Shows the average blood sugar over the last 2–3 months. Steadier than a single glucose reading.",
            ),
            aliases = listOf("hba1c", "a1c", "гликированный гемоглобин", "гликогемоглобин", "glycated haemoglobin", "glycated hemoglobin"),
        ),
        KnownMarker(
            code = "cholesterol", group = "metabolic", unit = "mmol/L",
            names = KnownMarker.Names("Umumiy xolesterin", "Общий холестерин", "Total cholesterol"),
            about = KnownMarker.Names(
                "Qondagi yog'simon moddaning umumiy miqdori. Yurak-qon tomir salomatligini baholashda boshqa yog' ko'rsatkichlari bilan birga qaraladi.",
                "Общее количество жироподобного вещества в крови. Для оценки сердца и сосудов смотрят вместе с другими липидами.",
                "All of the blood's cholesterol together. Read with the other fats when judging heart health.",
            ),
            aliases = listOf("cholesterol", "total cholesterol", "chol", "холестерин", "холестерин общий", "общий холестерин", "xolesterin"),
        ),
        KnownMarker(
            code = "crp", group = "metabolic", unit = "mg/L",
            names = KnownMarker.Names("C-reaktiv oqsil", "С-реактивный белок", "C-reactive protein"),
            about = KnownMarker.Names(
                "Yallig'lanish paytida jigar ishlab chiqaradigan oqsil. Infeksiya yoki yallig'lanish borligini ko'rsatadi, sababini emas.",
                "Белок, который печень вырабатывает при воспалении. Показывает наличие воспаления, но не его причину.",
                "A protein the liver makes during inflammation. It shows that there is inflammation, not its cause.",
            ),
            aliases = listOf("crp", "c-reactive protein", "срб", "с-реактивный белок", "crb"),
        ),
        KnownMarker(
            code = "alt", group = "metabolic", unit = "U/L",
            names = KnownMarker.Names("ALT", "АЛТ", "ALT"),
            about = KnownMarker.Names(
                "Jigar hujayralaridagi ferment. Jigar faoliyatini tekshirishda qaraladigan asosiy ko'rsatkichlardan biri.",
                "Фермент клеток печени. Один из основных показателей при проверке работы печени.",
                "An enzyme in liver cells. One of the main numbers checked for how the liver is doing.",
            ),
            aliases = listOf("alt", "alat", "алт", "алат", "alanine aminotransferase", "аланинаминотрансфераза"),
        ),
        KnownMarker(
            code = "creatinine", group = "metabolic", unit = "µmol/L",
            names = KnownMarker.Names("Kreatinin", "Креатинин", "Creatinine"),
            about = KnownMarker.Names(
                "Mushaklarda hosil bo'lib, buyrak orqali chiqadigan modda. Buyraklar qanchalik yaxshi ishlashini baholashga yordam beradi.",
                "Вещество из мышц, которое выводят почки. Помогает оценить, насколько хорошо они работают.",
                "A waste product of muscle that the kidneys clear. It helps show how well they work.",
            ),
            aliases = listOf("creatinine", "crea", "креатинин", "kreatinin"),
        ),
    )

    private val byCode: Map<String, KnownMarker> = all.associateBy { it.code }
    private val byAlias: Map<String, KnownMarker> = all
        .flatMap { marker -> (marker.aliases + marker.code + listOf(marker.names.uz, marker.names.ru, marker.names.en)).map { normalise(it) to marker } }
        .toMap()

    fun byCode(code: String?): KnownMarker? = code?.let { byCode[it.trim().lowercase()] }

    /**
     * The marker a printed name stands for. The whole name first, then the name with
     * whatever is in brackets dropped — "Гемоглобин (HGB)" — then each bracketed part.
     */
    fun match(printed: String): KnownMarker? {
        val whole = normalise(printed)
        byAlias[whole]?.let { return it }
        val outside = normalise(printed.replace(BRACKETED, " "))
        byAlias[outside]?.let { return it }
        return BRACKETED.findAll(printed).firstNotNullOfOrNull { byAlias[normalise(it.value)] }
    }

    /** The marker for a row: the code the model or the app gave, else the printed name. */
    fun resolve(code: String?, printed: String): KnownMarker? = byCode(code) ?: match(printed)

    /** Lower case, no punctuation but the ones names are made of, single spaces. */
    fun normalise(raw: String): String =
        raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}()\\- ]"), " ")
            .replace(Regex("[()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private val BRACKETED = Regex("\\(([^)]*)\\)")
}
