package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.AiChatQuota
import uz.sadora.contract.AiChatReply
import uz.sadora.contract.AiChatRequest
import uz.sadora.contract.AiGreeting

/**
 * SADORA AI. The server spends the allowance and returns what is left with every
 * answer, so the counter on screen is never the app's own arithmetic.
 */
class AiApi(private val caller: ApiCaller) {

    /** The line under her name on Today. Free, unmetered, different on every open. */
    suspend fun greeting(): ApiResult<AiGreeting> =
        caller.authenticated("v1/ai/greeting", HttpMethodKind.GET)

    suspend fun quota(): ApiResult<AiChatQuota> =
        caller.authenticated("v1/ai/chat/quota", HttpMethodKind.GET)

    suspend fun ask(question: String): ApiResult<AiChatReply> =
        caller.authenticated("v1/ai/chat", HttpMethodKind.POST) { setBody(AiChatRequest(question)) }
}
