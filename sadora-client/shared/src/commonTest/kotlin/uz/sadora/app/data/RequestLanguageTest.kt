package uz.sadora.app.data

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import uz.sadora.app.i18n.RequestLanguage

/**
 * The server words a refusal in the language each request asks for, so the header has
 * to follow the app's language from one call to the next — not the one it started in.
 */
class RequestLanguageTest {

    @AfterTest
    fun reset() {
        RequestLanguage.tag = "uz"
    }

    @Test
    fun `every request asks for the language the app is in now`() = runTest {
        val asked = mutableListOf<String?>()
        val client = createSadoraHttpClient(
            SadoraEnvironment("http://test.local"),
            MockEngine { request ->
                asked += request.headers[HttpHeaders.AcceptLanguage]
                json("{}")
            },
        )

        client.get("v1/me")
        RequestLanguage.tag = "ru"
        client.get("v1/me")

        assertEquals(listOf<String?>("uz", "ru"), asked)
    }
}
