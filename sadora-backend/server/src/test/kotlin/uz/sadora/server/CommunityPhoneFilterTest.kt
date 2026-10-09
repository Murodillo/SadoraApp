package uz.sadora.server

import uz.sadora.server.community.CommunityService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityPhoneFilterTest {
    private val phone = CommunityService.PHONE_NUMBER

    @Test
    fun `numbers people actually type are caught`() {
        listOf(
            "Menga yozing +998901234567",
            "tel: +998 90 123-45-67",
            "(90) 123 45 67 ga qo'ng'iroq qiling",
            "901234567",
            "90.123.45.67",
        ).forEach { assertTrue(phone.containsMatchIn(it), it) }
    }

    @Test
    fun `dates, doses and counts are left alone`() {
        listOf(
            "29-hafta, 2026-yil 10-oktabr",
            "Temir 60 mg, kuniga 2 marta",
            "TTG natijasi 2.1 chiqdi",
            "sikl 21 kundan qisqa yoki 35 kundan uzun",
            "Bo'yi 38,6 sm, vazni 1,2 kg",
        ).forEach { assertFalse(phone.containsMatchIn(it), it) }
    }
}
