package org.example.project.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.example.project.design.Sadora
import org.example.project.design.Spacing
import org.example.project.ui.components.SadoraTopBar
import org.example.project.ui.components.SystemBackHandler

/** The two documents a user has to be able to read before agreeing to anything. */
enum class LegalDocument(val title: String) {
    Terms("Foydalanish shartlari"),
    Privacy("Maxfiylik siyosati"),
}

/** A heading followed by its paragraphs. */
private data class Section(val heading: String, val body: List<String>)

/**
 * Effective date of the copy below.
 *
 * It is stated on the screen rather than computed: a document whose date moves on
 * its own would tell the user it changed when it did not.
 */
private const val EffectiveDate = "3-sentabr, 2026"

private val terms = listOf(
    Section(
        "1. Ushbu shartlar haqida",
        listOf(
            "SADORA — ayollar salomatligini kuzatish ilovasi. Ilovadan foydalanishni " +
                "boshlash orqali siz ushbu shartlarni qabul qilasiz. Agar rozi " +
                "bo'lmasangiz, ilovadan foydalanmang.",
            "Shartlar o'zgarsa, sizga ilova ichida xabar beramiz va yangi tahrirni " +
                "qabul qilishingizni so'raymiz.",
        ),
    ),
    Section(
        "2. SADORA tibbiy xizmat emas",
        listOf(
            "Ilovadagi bashoratlar, xulosalar va tavsiyalar — umumiy ma'lumot. Ular " +
                "shifokor tashxisi, maslahati yoki davolash rejasining o'rnini bosmaydi.",
            "Sikl bashoratlari statistik hisob-kitobga asoslanadi va homiladorlikdan " +
                "saqlanish vositasi sifatida ishlatilmasligi kerak.",
            "Salomatligingizga oid har qanday qaror uchun shifokorga murojaat qiling. " +
                "Shoshilinch holatda tez yordamga qo'ng'iroq qiling.",
        ),
    ),
    Section(
        "3. Hisobingiz",
        listOf(
            "Hisob telefon raqamingiz va bir martalik SMS kodi orqali ochiladi. Kod " +
                "kelgan qurilmani va raqamdan foydalanishni nazorat qilish sizning " +
                "zimmangizda.",
            "Ilovadan 13 yoshdan boshlab foydalanish mumkin. 18 yoshgacha bo'lganlar " +
                "ota-ona yoki qonuniy vakil roziligi bilan foydalanadi.",
            "Bir hisobni bir necha kishi bilan bo'lishmang: xulosalar bitta odamning " +
                "ma'lumotiga moslanadi.",
        ),
    ),
    Section(
        "4. Nima qilish mumkin emas",
        listOf(
            "Ilovani qonunga zid maqsadda ishlatish, boshqa foydalanuvchilar " +
                "ma'lumotiga ruxsatsiz kirishga urinish, xizmatga ortiqcha yuk berish " +
                "yoki himoya choralarini chetlab o'tish taqiqlanadi.",
            "Ilova kodini, dizaynini va kontentini SADORA yozma ruxsatisiz nusxalash " +
                "yoki qayta sotish mumkin emas.",
        ),
    ),
    Section(
        "5. Pullik obuna",
        listOf(
            "SADORA Premium obuna asosida ishlaydi. To'lov App Store yoki Google Play " +
                "hisobingizdan yechiladi va obuna avtomatik uzaytiriladi.",
            "Obunani istalgan vaqtda do'kon sozlamalaridan bekor qilishingiz mumkin. " +
                "Bekor qilish joriy davr tugagach kuchga kiradi.",
            "Pulni qaytarish shartlari siz obunani sotib olgan do'kon qoidalariga " +
                "bo'ysunadi.",
        ),
    ),
    Section(
        "6. Sizning kontentingiz",
        listOf(
            "Ilovaga kiritgan yozuvlaringiz — sizniki. Bizga ularni faqat xizmatni " +
                "ko'rsatish uchun saqlash va qayta ishlash huquqini berasiz.",
            "Hisobingizni o'chirsangiz, kontentingiz ham o'chiriladi.",
        ),
    ),
    Section(
        "7. Xizmatning uzluksizligi",
        listOf(
            "Xizmat \"qanday bo'lsa, shundayligicha\" taqdim etiladi. Texnik ishlar, " +
                "yangilanishlar yoki sizga bog'liq bo'lmagan sabablarga ko'ra ilova " +
                "vaqtincha ishlamasligi mumkin.",
            "Qonun ruxsat bergan doirada SADORA bilvosita zararlar uchun javobgar emas.",
        ),
    ),
    Section(
        "8. Hisobni to'xtatish",
        listOf(
            "Ushbu shartlar buzilganda hisobingizni cheklashimiz yoki yopishimiz mumkin. " +
                "Buning sababini imkon qadar tushuntiramiz.",
            "Siz ham istalgan vaqtda hisobingizni Profil → Maxfiylik va xavfsizlik " +
                "bo'limidan o'chira olasiz.",
        ),
    ),
    Section(
        "9. Bog'lanish",
        listOf(
            "Savollar bo'yicha: support@sadora.uz",
        ),
    ),
)

private val privacy = listOf(
    Section(
        "1. Qanday ma'lumot yig'amiz",
        listOf(
            "Hisob ma'lumotlari: telefon raqami, ism va til tanlovi.",
            "Salomatlik ma'lumotlari: sikl sanalari, simptomlar, kayfiyat, uyqu, " +
                "ovqatlanish, suv, dori qabuli va siz kiritgan boshqa yozuvlar.",
            "Qurilma ma'lumotlari: o'rnatish identifikatori, ilova versiyasi va " +
                "operatsion tizim. Bular hech qachon apparat identifikatori emas.",
        ),
    ),
    Section(
        "2. Nima uchun ishlatamiz",
        listOf(
            "Siklni hisoblash, eslatmalarni yuborish va shaxsiy xulosalar tayyorlash " +
                "uchun — ya'ni ilovaning asosiy vazifasi uchun.",
            "AI xulosalari uchun ma'lumotlaringiz faqat siz shunga alohida rozilik " +
                "bergan bo'lsangiz ishlatiladi.",
            "Anonim analitika ixtiyoriy va uni istalgan vaqtda o'chirib qo'yishingiz " +
                "mumkin.",
        ),
    ),
    Section(
        "3. Kim ko'ra oladi",
        listOf(
            "Salomatlik ma'lumotlaringizni reklama beruvchilarga, sug'urta " +
                "kompaniyalariga yoki ish beruvchilarga sotmaymiz va bermaymiz.",
            "Ma'lumot faqat xizmatni ishlatib turish uchun zarur bo'lgan texnik " +
                "hamkorlarga (server va xabar yuborish) va faqat zarur hajmda " +
                "topshiriladi.",
            "Qonuniy talab bo'lgan hollarda ma'lumot berilishi mumkin; imkoni bo'lsa " +
                "bu haqda sizni xabardor qilamiz.",
        ),
    ),
    Section(
        "4. Qanday saqlaymiz",
        listOf(
            "Ma'lumot uzatishda va serverda shifrlangan holda saqlanadi.",
            "Kirish kaliti qurilmangizning himoyalangan xotirasida saqlanadi — iOS'da " +
                "Keychain, Android'da Keystore.",
        ),
    ),
    Section(
        "5. Qancha vaqt saqlanadi",
        listOf(
            "Hisobingiz faol turganda saqlanadi. Hisobni o'chirsangiz, ma'lumotlar 30 " +
                "kun ichida butunlay o'chiriladi.",
        ),
    ),
    Section(
        "6. Sizning huquqlaringiz",
        listOf(
            "Ma'lumotlaringizni ko'rish, tuzatish, eksport qilish va o'chirish " +
                "huquqiga egasiz.",
            "Berilgan roziliklarni Profil → Maxfiylik va xavfsizlik bo'limidan " +
                "istalgan vaqtda qaytarib olishingiz mumkin.",
        ),
    ),
    Section(
        "7. Bog'lanish",
        listOf(
            "Maxfiylik bo'yicha savollar: privacy@sadora.uz",
        ),
    ),
)

/**
 * A full legal document, scrollable, with a back affordance.
 *
 * The text lives in the app rather than behind a link on purpose: onboarding asks
 * for consent before an account exists, and a user should never have to leave the
 * flow — or have a network connection — to read what she is agreeing to.
 */
@Composable
fun LegalScreen(
    document: LegalDocument,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sadora.colors
    val sections = when (document) {
        LegalDocument.Terms -> terms
        LegalDocument.Privacy -> privacy
    }

    SystemBackHandler(enabled = true, onBack = onClose)

    Column(modifier.fillMaxSize().background(c.bg).navigationBarsPadding()) {
        SadoraTopBar(document.title, onBack = onClose)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                "Kuchga kirgan sana: $EffectiveDate",
                style = Sadora.type.caption,
                color = c.muted2,
            )
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.heading, style = Sadora.type.h3, color = c.text)
                    section.body.forEach {
                        Text(it, style = Sadora.type.body, color = c.muted)
                    }
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

/**
 * Slides a legal document up over whatever raised it.
 *
 * A sheet rather than a pushed route because the consent screen is not on the
 * navigator's stack — onboarding is a phase — and because coming back to a consent
 * screen with the boxes exactly as they were left is the whole point.
 */
@Composable
fun LegalOverlay(
    document: LegalDocument?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = document != null,
        enter = slideInVertically(
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            initialOffsetY = { it },
        ) + fadeIn(tween(200)),
        exit = slideOutVertically(
            animationSpec = tween(260, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ) + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        // Held so the document keeps rendering through the exit animation instead of
        // blanking the moment the state clears.
        val shown = remember { mutableStateOf(document) }
        document?.let { shown.value = it }
        shown.value?.let { LegalScreen(it, onClose) }
    }
}
