package uz.sadora.server

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import uz.sadora.contract.BillingPeriod
import uz.sadora.server.billing.ClickGateway
import uz.sadora.server.billing.ClickProtocol
import uz.sadora.server.billing.duration
import uz.sadora.server.billing.toSum
import uz.sadora.server.config.ClickConfig

/**
 * The parts of billing that are arithmetic rather than orchestration: what a sum is in
 * each provider's units, and whether a signature is the one Click actually sends.
 *
 * These matter more than their size suggests. Every one of them is a place where a
 * mistake means somebody is charged the wrong amount or lets a stranger mark an order
 * paid.
 */
class BillingMathTest {

    private val config = ClickConfig(
        serviceId = "12345",
        merchantId = "54321",
        secretKey = "secret",
        checkoutUrl = "https://my.click.uz/services/pay",
    )
    @Test
    fun `tiyin become the sum Click quotes`() {
        assertEquals("39900", 3_990_000L.toSum())
        assertEquals("299000", 29_900_000L.toSum())
        // A price that is not a whole so'm keeps its coins rather than losing them.
        assertEquals("1000.50", 100_050L.toSum())
    }

    @Test
    fun `a sum with and without decimals is the same payment`() {
        assertTrue(ClickProtocol.amountMatches("39900", 3_990_000))
        assertTrue(ClickProtocol.amountMatches("39900.00", 3_990_000))
        assertTrue(ClickProtocol.amountMatches(" 39900.000 ", 3_990_000))
        assertFalse(ClickProtocol.amountMatches("39900.01", 3_990_000))
        assertFalse(ClickProtocol.amountMatches("3990", 3_990_000))
        assertFalse(ClickProtocol.amountMatches("not a number", 3_990_000))
    }

    /** The exact concatenation Click documents; a change here breaks every callback. */
    @Test
    fun `the prepare signature is md5 of Click's own field order`() {
        val request = ClickGateway.Request(
            clickTransId = "111",
            serviceId = "12345",
            merchantTransId = "order-1",
            amount = "39900.00",
            action = 0,
            signTime = "2026-09-05 10:00:00",
            signString = "",
        )
        val expected = md5("111" + "12345" + "secret" + "order-1" + "39900.00" + "0" + "2026-09-05 10:00:00")
        assertEquals(expected, ClickProtocol.expectedSignature(request, config.secretKey, prepareStage = true))
    }

    /** Complete adds the prepare id in the middle — the only difference between the two. */
    @Test
    fun `the complete signature includes the prepare id`() {
        val request = ClickGateway.Request(
            clickTransId = "111",
            serviceId = "12345",
            merchantTransId = "order-1",
            merchantPrepareId = "prep-1",
            amount = "39900.00",
            action = 1,
            signTime = "2026-09-05 10:00:00",
            signString = "",
        )
        val expected = md5(
            "111" + "12345" + "secret" + "order-1" + "prep-1" + "39900.00" + "1" + "2026-09-05 10:00:00",
        )
        assertEquals(expected, ClickProtocol.expectedSignature(request, config.secretKey, prepareStage = false))
    }

    @Test
    fun `without a secret there is no signature to compare, so nothing can match`() {
        val request = ClickGateway.Request(
            clickTransId = "1",
            serviceId = "12345",
            merchantTransId = "order-1",
            amount = "1",
            action = 0,
            signTime = "t",
            signString = "whatever",
        )
        assertEquals(null, ClickProtocol.expectedSignature(request, secret = null, prepareStage = true))
    }

    @Test
    fun `a month is thirty days and a year is three hundred and sixty five`() {
        assertEquals(30 * 24, BillingPeriod.MONTH.duration().inWholeHours / 1)
        assertEquals(365 * 24, BillingPeriod.YEAR.duration().inWholeHours / 1)
    }
}

private fun md5(value: String): String =
    MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
