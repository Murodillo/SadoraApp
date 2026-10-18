package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.TelegramLink
import uz.sadora.contract.TelegramSettingsRequest
import uz.sadora.contract.TelegramStatus

/** The Telegram bot from the app's side: status, a one-time link, the detail switch, unlinking. */
class TelegramApi(private val caller: ApiCaller) {

    suspend fun status(): ApiResult<TelegramStatus> =
        caller.authenticated("v1/telegram", HttpMethodKind.GET)

    suspend fun link(): ApiResult<TelegramLink> =
        caller.authenticated("v1/telegram/link", HttpMethodKind.POST)

    suspend fun setDetailed(detailed: Boolean): ApiResult<TelegramStatus> =
        caller.authenticated("v1/telegram/settings", HttpMethodKind.PUT) { setBody(TelegramSettingsRequest(detailed)) }

    suspend fun unlink(): ApiResult<TelegramStatus> =
        caller.authenticated("v1/telegram", HttpMethodKind.DELETE)
}
