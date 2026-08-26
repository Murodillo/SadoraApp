package uz.sadora.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The enum spellings on the wire are part of the contract — the backend stores them and
 * the admin panel filters by them, so a rename here is a breaking change in three places.
 */
class WireFormatTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Test
    fun `enums serialise as their documented snake_case names`() {
        assertEquals("\"trying_to_conceive\"", json.encodeToString(LifeStage.TRYING_TO_CONCEIVE))
        assertEquals("\"uz\"", json.encodeToString(Language.UZ))
        assertEquals("\"understand_cycle\"", json.encodeToString(Goal.UNDERSTAND_CYCLE))
        assertEquals("\"app_store\"", json.encodeToString(SubscriptionSource.APP_STORE))
        assertEquals("\"deletion_pending\"", json.encodeToString(AccountStatus.DELETION_PENDING))
    }

    /** An older app build must survive a server that added a field. */
    @Test
    fun `unknown fields are ignored when decoding`() {
        val payload = """
            {"code":"limit_reached","message":"Limit tugadi","somethingNew":42}
        """.trimIndent()
        val error = json.decodeFromString<ApiError>(payload)
        assertEquals(ErrorCodes.LIMIT_REACHED, error.code)
    }

    /** Optional fields stay out of the payload rather than appearing as `null`. */
    @Test
    fun `absent optionals are omitted`() {
        val encoded = json.encodeToString(OtpRequest(phone = "+998901234567"))
        assertTrue("devCode" !in encoded)
        assertTrue("\"language\":\"uz\"" in encoded, encoded)
    }
}
