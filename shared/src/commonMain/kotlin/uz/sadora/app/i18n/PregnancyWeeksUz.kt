package uz.sadora.app.i18n

object PregnancyWeeksUz : PregnancyWeekStrings {
    override fun sizeOf(fruit: String) = "Bolangiz hozir $fruit kattaligida"
    override val lengthLabel = "Bo'yi"
    override val crownToRump = "tepadan dumg'azagacha"
    override val crownToHeel = "boshdan tovongacha"
    override val weightLabel = "Vazni"
    override val weightTooSmall = "1 g dan kam"
    override val mm = "mm"
    override val cm = "sm"
    override val g = "g"
    override val kg = "kg"
    override val babyHeading = "Bolangiz"
    override val motherHeading = "Siz"
    override val previousWeek = "Oldingi hafta"
    override val nextWeek = "Keyingi hafta"
    override val thisWeekCaps = "SHU HAFTA"
    override val averagesNote =
        "O'rtacha qiymatlar. Har bir bola o'z sur'atida o'sadi — aniq o'lchamni UTT ko'rsatadi."

    override val fruits = listOf(
        "ko'knor urug'i", // 4
        "kunjut urug'i",
        "yasmiq doni",
        "chernika",
        "gilos", // 8
        "zaytun",
        "qulupnay",
        "kashtan",
        "kivi", // 12
        "shaftoli",
        "limon",
        "olma",
        "avokado", // 16
        "nok",
        "bolgar qalampiri",
        "mango",
        "banan", // 20
        "sabzi",
        "makkajo'xori so'tasi",
        "baqlajon",
        "uzun bodring", // 24
        "brokkoli",
        "kichik karam",
        "kokos yong'og'i",
        "ananas", // 28
        "kichik qovoq",
        "karam",
        "katta uzum shingili",
        "kichik qovun", // 32
        "katta ananas",
        "qovoq",
        "qovun",
        "katta qovoq", // 36
        "katta qovun",
        "kichik tarvuz",
        "tarvuz",
        "tarvuz", // 40
    )

    override val baby = listOf(
        // 4
        "Embrion bachadon devoriga joylashdi. Hujayralar qatlamlarga ajralmoqda — ulardan keyinchalik barcha a'zolar hosil bo'ladi. Yo'ldosh shakllana boshladi.",
        // 5
        "Yurak naychasi paydo bo'lib, ura boshlaydi. Bosh va orqa miyaga aylanadigan nerv naychasi shakllanmoqda.",
        // 6
        "Yurak urishi UTTda ko'rinishi mumkin. Qo'l va oyoq kurtaklari paydo bo'ldi, yuzning ilk belgilari shakllanmoqda.",
        // 7
        "Miya juda tez o'smoqda. Qo'l va oyoqlar kichik kurakchalarga o'xshaydi, buyraklar shakllanmoqda.",
        // 8
        "Barmoqlar paydo bo'lmoqda, hozircha ular parda bilan tutashgan. Embrion harakatlana boshladi, lekin siz buni hali sezmaysiz.",
        // 9
        "Barcha asosiy a'zolar shakllana boshladi. Ko'z qovoqlari ko'zni yopib turadi, quloqlar tashqi ko'rinishini ola boshladi.",
        // 10
        "Endi u embrion emas, homila deb ataladi. Hayotiy a'zolar shakllanib, ishlay boshladi, tirnoqlar paydo bo'lmoqda.",
        // 11
        "Boshi tanasining qariyb yarmicha. Barmoqlar bir-biridan ajraldi, suyaklar qattiqlasha boshladi.",
        // 12
        "Reflekslar paydo bo'ldi: barmoqlarini ochib-yumadi. Buyraklar siydik ishlab chiqara boshladi.",
        // 13
        "Tovush paychalari shakllanmoqda, ichaklar qorin bo'shlig'iga joylashdi. Barmoq izlari hosil bo'lmoqda.",
        // 14
        "Yuz ifodalari paydo bo'ldi — qovog'ini soladi, ko'zini qisadi. Tanani mayin tukcha (lanugo) qoplay boshladi.",
        // 15
        "Yopiq qovoqlar orqali yorug'likni seza oladi. Oyoqlari qo'llaridan uzunroq bo'lib qoldi, suyaklar UTTda aniq ko'rinadi.",
        // 16
        "Ko'zlari qovoq ostida harakatlanadi, yuz mushaklari ishlay boshladi. Yuragi har kuni ko'p miqdorda qon haydaydi.",
        // 17
        "Teri ostida yog' qatlami hosil bo'la boshladi. Tog'ay skelet asta-sekin suyakka aylanmoqda, kindik tizimchasi yo'g'onlashmoqda.",
        // 18
        "Quloqlar o'z joyiga keldi va u tovushlarni eshita boshlashi mumkin. Nervlar himoya qobig'i bilan qoplanmoqda.",
        // 19
        "Terisini oq moysimon qatlam (verniks) qoplay boshladi — u terini suvdan himoya qiladi. Sezgilar rivojlanmoqda.",
        // 20
        "Homiladorlikning yarmi! U qog'onoq suvini yutishni mashq qiladi, ichakda birinchi axlat (mekoniy) to'plana boshladi.",
        // 21
        "Harakatlari kuchliroq va aniqroq bo'lib qoldi. Uyqu va uyg'oqlik davrlari shakllanmoqda.",
        // 22
        "U kichkina chaqaloqqa o'xshab qoldi: qosh va lablar aniq ko'rinadi. Qo'lchasi bilan ushlay oladi.",
        // 23
        "Sizning ovozingizni eshitadi. O'pkada qon tomirlari rivojlanmoqda.",
        // 24
        "O'pka surfaktant ishlab chiqara boshladi — bu keyinchalik nafas olishga yordam beradi. Yuz deyarli to'liq shakllandi.",
        // 25
        "Sochlari o'smoqda, yog' qatlami ko'paymoqda. Tanish ovozlarga javob berishi mumkin.",
        // 26
        "Ko'zlarini ocha boshlaydi. Nafas olish harakatlarini mashq qiladi, lekin hali havo emas, suv bilan.",
        // 27
        "Miya juda faol ishlamoqda. Ba'zan hiqichoq tutadi — siz buni bir maromdagi mayda turtkilar kabi sezasiz.",
        // 28
        "Ko'zini yumib-ocha oladi, kipriklar o'sdi. Tush ko'radigan uyqu bosqichi (REM) paydo bo'ldi.",
        // 29
        "Mushaklar va o'pka yetilmoqda. Tepishlari kuchli va sezilarli bo'lib qoldi.",
        // 30
        "Miya tez o'smoqda, tanadagi mayin tukchalar kamaymoqda. Suyak iligi qizil qon hujayralarini ishlab chiqarmoqda.",
        // 31
        "Boshini u yoqdan-bu yoqqa bura oladi. Beshala sezgi a'zosi ishlamoqda.",
        // 32
        "Qo'l va oyoq tirnoqlari o'sdi. Nafas olishni mashq qiladi. Ko'pchilik bolalar shu davrda yoki birozdan keyin boshini pastga qaratib oladi.",
        // 33
        "Suyaklar qattiqlashmoqda, faqat bosh suyagi tug'ilish uchun yumshoq qoladi. Sizdan unga himoya antitanalari o'tmoqda.",
        // 34
        "Markaziy nerv tizimi yetilmoqda. O'pka yetilishda davom etmoqda, lekin hali to'liq tayyor emas.",
        // 35
        "Buyraklar to'liq rivojlandi, jigar yetilmoqda. U tez vazn to'plamoqda.",
        // 36
        "Joy torayib qoldi, shuning uchun tepishlar o'rniga ko'proq aylanish va cho'zilishni sezasiz. Harakatlar kamaymasligi kerak.",
        // 37
        "Erta to'liq muddat boshlandi. U so'rish va ushlashni mashq qiladi, boshi kichik chanoqqa tushishi mumkin.",
        // 38
        "A'zolar tashqi hayotga tayyor, mayin tukchalar deyarli to'kildi. Miya va o'pka yetilishda davom etadi.",
        // 39
        "To'liq muddat. Teri ostidagi yog' qatlami tug'ilgandan keyin tana haroratini saqlashga yordam beradi.",
        // 40
        "Taxminiy tug'ish sanasi. Bolalarning ozchiligi aynan shu kuni tug'iladi — ko'pchiligi undan bir-ikki hafta oldin yoki biroz keyin. 41-haftadan keyin shifokor sizni yaqinroq kuzatadi.",
    )

    override val mother = listOf(
        // 4
        "Hayz kechikdi, test ijobiy chiqishi mumkin. Foliy kislotasini hali boshlamagan bo'lsangiz, kuniga 400 mkg ichishni boshlang va shifokoringizga ayting.",
        // 5
        "Charchoq, ko'krak sezuvchanligi va ko'ngil aynishi boshlanishi mumkin. Bu gormonlar o'zgarishining odatiy belgilari.",
        // 6
        "Ko'ngil aynishi va tez-tez siyish ko'p uchraydi. Oz-ozdan, tez-tez ovqatlanish yengillik berishi mumkin. Qon ketsa yoki qorinning bir tomonida kuchli og'riq bo'lsa, darhol shifokorga murojaat qiling.",
        // 7
        "Ba'zi hidlar va ovqatlar yoqmay qolishi mumkin. Birinchi tashrifni rejalashtirish vaqti keldi.",
        // 8
        "Bachadon kattalashmoqda. Birinchi ko'rik odatda shu haftalarda bo'ladi — savollaringizni yozib boring.",
        // 9
        "Kayfiyat tez o'zgarishi va charchoq tabiiy. Dam olishga vaqt ajrating.",
        // 10
        "Tomirlar ko'proq ko'rinishi, bel biroz kengayishi mumkin. Qulay kiyimlarga o'tish vaqti.",
        // 11
        "Ba'zi ayollarda ko'ngil aynishi kamaya boshlaydi. Suvni yetarli iching.",
        // 12
        "11–14-haftalarda birinchi skrining UTT o'tkaziladi. Ko'pchilikda kuch-quvvat qaytmoqda.",
        // 13
        "Birinchi trimestr tugayapti. Ishtaha odatda yaxshilanadi.",
        // 14
        "Ikkinchi trimestr — ko'pchilik uchun eng yengil davr. Ishtaha oshishi mumkin.",
        // 15
        "Burundan yoki milkdan ozgina qon kelishi mumkin — bu qon hajmi oshgani sababli. Ko'p bo'lsa, shifokorga ayting.",
        // 16
        "Ba'zi ayollar, ayniqsa ikkinchi homiladorlikda, birinchi mayin qimirlashlarni seza boshlaydi.",
        // 17
        "Qorin ko'rina boshladi, belga og'irlik tushishi mumkin. Yonboshlab uxlash qulayroq bo'ladi.",
        // 18
        "18–22-haftalarda ikkinchi skrining (anomaliya) UTTsi o'tkaziladi.",
        // 19
        "Qorin yon tomonlarida sanchiq og'riq bo'lishi mumkin — bachadon boylamlari cho'zilmoqda.",
        // 20
        "Bachadon tubi kindik darajasiga yetdi. Qimirlashlar aniqroq seziladi.",
        // 21
        "Bolaning harakatlari muntazamroq bo'lib qoldi. Uning qachon faol ekanini kuzatib boring.",
        // 22
        "Qorin va sonlarda cho'zilish izlari paydo bo'lishi mumkin. Terini namlab turish yoqimli bo'ladi.",
        // 23
        "Oyoqlar biroz shishishi mumkin. Oyoqlarni ko'tarib dam oling. Yuz yoki qo'llar to'satdan shishsa, darhol shifokorga murojaat qiling.",
        // 24
        "24–28-haftalarda qandli diabetga glyukoza testi topshiriladi.",
        // 25
        "Jig'ildon qaynashi va uyqu qiyinlashishi mumkin. Kechki ovqatni ertaroq va yengilroq qiling.",
        // 26
        "Qon bosimi har tashrifda tekshiriladi. Kuchli bosh og'rig'i, ko'z oldi qorong'ilashishi yoki yuz va qo'llar shishsa, darhol shifokorga ayting.",
        // 27
        "Ikkinchi trimestr tugadi. Oyoqlarda tomir tortishishi bo'lishi mumkin.",
        // 28
        "Uchinchi trimestr. Bolaning qimirlashlarini har kuni kuzating. Rezus-manfiy bo'lsangiz, shifokor ukol haqida gapiradi.",
        // 29
        "Nafas qisishi va ich qotishi ko'p uchraydi. Tolali ovqat va suv yordam beradi.",
        // 30
        "Charchoq qaytishi mumkin. Mashq to'lg'oqlari (Brekston-Hiks) — og'riqsiz, notekis qisqarishlar bo'lishi mumkin.",
        // 31
        "Ko'krakdan ozgina og'iz suti chiqishi mumkin — bu normal holat.",
        // 32
        "Tez-tez siyishga borish yana boshlandi. Tashriflar ko'proq bo'ladi.",
        // 33
        "Uyqu qiyinlashishi mumkin. Tizzalar orasiga yostiq qo'yib, yonboshlab yotish yordam beradi.",
        // 34
        "Charchoq va shishlar ko'payishi mumkin. Tug'ruqxonaga olib boriladigan sumkani tayyorlay boshlang.",
        // 35
        "Bola pastga tushganda nafas olish yengillashadi, lekin kichik chanoqqa bosim ortadi. Ba'zi klinikalar 36–37-haftada B guruh streptokokiga (GBS) tahlil oladi.",
        // 36
        "Endi tashriflar har hafta bo'lishi mumkin. Qimirlashlar kamaysa, kutmasdan shifokorga ayting.",
        // 37
        "Tug'ruq belgilarini bilib oling: muntazam to'lg'oqlar, suv ketishi, qonli ajralma.",
        // 38
        "Uyni tayyorlash istagi kuchayishi mumkin. Dam olishni ham unutmang.",
        // 39
        "To'lg'oqlar muntazam bo'lsa yoki suv ketsa, shifokoringiz aytgan tartibda harakat qiling.",
        // 40
        "Tug'ruq boshlanmasa, shifokor keyingi qadamlarni muhokama qiladi. Qimirlashlarni kuzatishda davom eting.",
    )
}
