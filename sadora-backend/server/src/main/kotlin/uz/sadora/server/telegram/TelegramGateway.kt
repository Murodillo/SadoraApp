package uz.sadora.server.telegram

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.sadora.server.config.TelegramConfig

/** The few Bot API calls the bot makes. A fake stands in for it in tests. */
interface TelegramGateway {
    suspend fun updates(offset: Long, timeoutSeconds: Int): List<TgUpdate>

    /** Returns the sent message's id, or null when Telegram refused it. */
    suspend fun send(chatId: Long, text: String, buttons: List<List<TgButton>> = emptyList()): Long?

    suspend fun edit(chatId: Long, messageId: Long, text: String)

    suspend fun answer(callbackId: String, text: String? = null)
}

/** An inline button; its [data] comes back as the callback, at most 64 bytes. */
data class TgButton(val text: String, val data: String)

@Serializable
data class TgUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TgMessage? = null,
    @SerialName("callback_query") val callback: TgCallback? = null,
)

@Serializable
data class TgMessage(
    @SerialName("message_id") val messageId: Long,
    val chat: TgChat,
    val from: TgUser? = null,
    val text: String? = null,
)

@Serializable
data class TgChat(val id: Long, val type: String = "private")

@Serializable
data class TgUser(
    val id: Long,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("language_code") val languageCode: String? = null,
)

@Serializable
data class TgCallback(
    val id: String,
    val from: TgUser,
    val message: TgMessage? = null,
    val data: String? = null,
)

@Serializable
private data class TgResponse<T>(val ok: Boolean, val result: T? = null, val description: String? = null)

@Serializable
private data class TgSent(@SerialName("message_id") val messageId: Long)

@Serializable
private data class TgInlineButton(val text: String, @SerialName("callback_data") val data: String)

@Serializable
private data class TgMarkup(@SerialName("inline_keyboard") val keyboard: List<List<TgInlineButton>>)

@Serializable
private data class TgSendMessage(
    @SerialName("chat_id") val chatId: Long,
    val text: String,
    @SerialName("reply_markup") val markup: TgMarkup? = null,
)

@Serializable
private data class TgEditMessage(
    @SerialName("chat_id") val chatId: Long,
    @SerialName("message_id") val messageId: Long,
    val text: String,
)

@Serializable
private data class TgAnswer(@SerialName("callback_query_id") val id: String, val text: String? = null)

@Serializable
private data class TgGetUpdates(
    val offset: Long,
    val timeout: Int,
    @SerialName("allowed_updates") val allowed: List<String> = listOf("message", "callback_query"),
)

/** The Bot API over HTTPS. The token is part of the path, so it is never logged. */
class HttpTelegramGateway(private val client: HttpClient, private val config: TelegramConfig) : TelegramGateway {
    private val base get() = "${config.apiBase}/bot${config.token}"

    override suspend fun updates(offset: Long, timeoutSeconds: Int): List<TgUpdate> {
        val response = call("getUpdates", TgGetUpdates(offset, timeoutSeconds))
        if (!response.status.isSuccess()) return emptyList()
        return response.body<TgResponse<List<TgUpdate>>>().result.orEmpty()
    }

    override suspend fun send(chatId: Long, text: String, buttons: List<List<TgButton>>): Long? {
        val markup = buttons.takeIf { it.isNotEmpty() }?.let { rows -> TgMarkup(rows.map { row -> row.map { TgInlineButton(it.text, it.data) } }) }
        val response = call("sendMessage", TgSendMessage(chatId, text, markup))
        if (!response.status.isSuccess()) return null
        return response.body<TgResponse<TgSent>>().result?.messageId
    }

    override suspend fun edit(chatId: Long, messageId: Long, text: String) {
        call("editMessageText", TgEditMessage(chatId, messageId, text))
    }

    override suspend fun answer(callbackId: String, text: String?) {
        call("answerCallbackQuery", TgAnswer(callbackId, text))
    }

    private suspend inline fun <reified T : Any> call(method: String, body: T): HttpResponse =
        client.post("$base/$method") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
}
