package uz.sadora.server

import uz.sadora.server.community.CommunityService
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommunityPhoneFilterTest {

    @Test
    fun `numbers people actually type are caught`() {
        listOf(
            "Menga yozing +998901234567",
            "tel: +998 90 123-45-67",
            "(90) 123 45 67 ga qo'ng'iroq qiling",
            "901234567",
            "90.123.45.67",
            // An id beside a number hides nothing, and digits either side of one do not join up.
            "3f2a9c1e-8b47-4d05-9123-456789012abc 901234567",
            "90 3f2a9c1e-8b47-4d05-9123-456789012abc 1234567 +998 90 123 45 67",
        ).forEach { assertTrue(CommunityService.containsPhoneNumber(it), it) }
    }

    @Test
    fun `dates, doses and counts are left alone`() {
        listOf(
            "29-hafta, 2026-yil 10-oktabr",
            "Temir 60 mg, kuniga 2 marta",
            "TTG natijasi 2.1 chiqdi",
            "sikl 21 kundan qisqa yoki 35 kundan uzun",
            "Bo'yi 38,6 sm, vazni 1,2 kg",
        ).forEach { assertFalse(CommunityService.containsPhoneNumber(it), it) }
    }

    @Test
    fun `a hyphenated hex id is not a phone number`() {
        listOf(
            "Panel sahifa 3f2a9c1e-8b47-4d05-9123-456789012abc",
            "Panel sahifa 12345678-1234-4123-8123-123456789012",
            "id: 0A1B2C3D-0000-4111-8222-333344445555.",
            "90 3f2a9c1e-8b47-4d05-9123-456789012abc 1234567",
        ).forEach { assertFalse(CommunityService.containsPhoneNumber(it), it) }
    }
}
