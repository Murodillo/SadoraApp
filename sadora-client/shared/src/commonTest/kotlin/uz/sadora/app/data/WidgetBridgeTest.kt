package uz.sadora.app.data

import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import uz.sadora.app.data.widget.AppWidgetApi
import uz.sadora.app.data.widget.WidgetBridge
import uz.sadora.app.data.widget.WidgetPlatform
import uz.sadora.contract.CompanionKind
import uz.sadora.widget.CompanionTokens
import uz.sadora.widget.MemoryKeyValues
import uz.sadora.widget.WidgetApi
import uz.sadora.widget.WidgetStore
import uz.sadora.widget.WidgetSync

class WidgetBridgeTest {
    private val summaryJson = """
        {"today":"2026-08-27","lifeStage":"cycle","water":{"date":"2026-08-27","waterMl":500,"waterGoalMl":2000},
         "medications":{"date":"2026-08-27"},"premium":false,"discreet":false,"language":"uz"}
    """.trimIndent()

    private class FakePlatform(override val hasWatch: Boolean = false) : WidgetPlatform {
        override val store = WidgetStore(MemoryKeyValues())
        override val widgetTokens = CompanionTokens(MemoryKeyValues())
        var reloads = 0
        /** (refresh token handed over, snapshot sent) per call. */
        val toWatch = mutableListOf<Pair<String?, Boolean>>()
        override fun sync(appApi: WidgetApi) = WidgetSync(appApi, store, { TestNow }, { "id" })
        override fun reload() { reloads++ }
        override fun sendToWatch(tokens: uz.sadora.contract.TokenPair?, snapshotJson: String?) { toWatch += tokens?.refreshToken to (snapshotJson != null) }
    }

    private fun graphAnd(engine: RecordingEngine) =
        SadoraGraph(InMemoryTokenStorage(token = "app-refresh"), FixedDeviceIdentity(), SadoraEnvironment("https://api.test"), engine = engine.build())

    private fun companionFor(kind: String) = encode(testTokens("$kind-access", "$kind-refresh"))

    @Test
    fun `signing in hands the widgets a session of their own and draws them`() = runTest {
        val engine = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/companion" -> json(companionFor(if ("watch" in (request.body as io.ktor.http.content.TextContent).text) "watch" else "widget"))
                "/v1/widget/summary" -> json(summaryJson)
                else -> json("{}")
            }
        }
        val graph = graphAnd(engine)
        graph.session.saveTokens(testTokens("app-access", "app-refresh"))
        val platform = FakePlatform(hasWatch = true)
        val bridge = WidgetBridge(platform, AppWidgetApi(graph), graph.api, graph.session, "https://api.test", backgroundScope)

        bridge.signedIn()

        assertEquals("widget-refresh", platform.widgetTokens.refresh())
        assertEquals("https://api.test", platform.store.baseUrl())
        assertEquals(500, platform.store.snapshot()?.summary?.water?.waterMl)
        assertEquals(1, platform.reloads)
        assertEquals(listOf("watch-refresh" to false, null to true), platform.toWatch, "its session, then the snapshot")
        assertTrue(engine.bodies[engine.paths.indexOf("/v1/auth/companion")].contains("app-refresh"))

        // A second sign-in event does not mint another session.
        bridge.signedIn()
        assertEquals(2, engine.countOf("/v1/auth/companion"), "one for the widgets, one for the watch")
    }

    @Test
    fun `signing out leaves nothing a widget could draw`() = runTest {
        val engine = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/companion" -> json(companionFor("widget"))
                "/v1/widget/summary" -> json(summaryJson)
                else -> json("{}")
            }
        }
        val graph = graphAnd(engine)
        graph.session.saveTokens(testTokens("app-access", "app-refresh"))
        val platform = FakePlatform(hasWatch = true)
        val bridge = WidgetBridge(platform, AppWidgetApi(graph), graph.api, graph.session, "https://api.test", backgroundScope)
        bridge.signedIn()

        bridge.signedOut()
        assertNull(platform.store.snapshot())
        assertNull(platform.widgetTokens.refresh())
        assertEquals(null to false, platform.toWatch.last(), "the watch is told to forget too")
        assertEquals(2, platform.reloads)
    }

    @Test
    fun `a failed handover leaves the widgets signed out — not the app`() = runTest {
        val engine = RecordingEngine { request ->
            when (request.url.encodedPath) {
                "/v1/auth/companion" -> json(errorBody("server_error", "x"), HttpStatusCode.InternalServerError)
                "/v1/widget/summary" -> json(summaryJson)
                else -> json("{}")
            }
        }
        val graph = graphAnd(engine)
        graph.session.saveTokens(testTokens("app-access", "app-refresh"))
        val platform = FakePlatform()
        WidgetBridge(platform, AppWidgetApi(graph), graph.api, graph.session, "https://api.test", backgroundScope).signedIn()
        assertNull(platform.widgetTokens.refresh())
        assertEquals("app-refresh", graph.session.currentRefreshToken())
        assertEquals(500, platform.store.snapshot()?.summary?.water?.waterMl, "the app still draws them from its own session")
    }
}
