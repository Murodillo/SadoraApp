package org.example.project.data.api

import io.ktor.client.request.setBody
import org.example.project.data.ApiCaller
import org.example.project.data.ApiResult
import org.example.project.data.HttpMethodKind
import uz.sadora.contract.AiChatQuota
import uz.sadora.contract.AiChatReply
import uz.sadora.contract.AiChatRequest

/**
 * SADORA AI. The server spends the allowance and returns what is left with every
 * answer, so the counter on screen is never the app's own arithmetic.
 */
class AiApi(private val caller: ApiCaller) {

    suspend fun quota(): ApiResult<AiChatQuota> =
        caller.authenticated("v1/ai/chat/quota", HttpMethodKind.GET)

    suspend fun ask(question: String): ApiResult<AiChatReply> =
        caller.authenticated("v1/ai/chat", HttpMethodKind.POST) { setBody(AiChatRequest(question)) }
}
