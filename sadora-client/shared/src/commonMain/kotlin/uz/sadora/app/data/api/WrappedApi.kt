package uz.sadora.app.data.api

import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.Wrapped
import uz.sadora.contract.WrappedIndex

/** Cycle Wrapped: the list of recaps, one recap, and "she has opened it". */
class WrappedApi(private val caller: ApiCaller) {

    suspend fun index(): ApiResult<WrappedIndex> =
        caller.authenticated("v1/wrapped", HttpMethodKind.GET)

    suspend fun get(key: String): ApiResult<Wrapped> =
        caller.authenticated("v1/wrapped/$key", HttpMethodKind.GET)

    suspend fun seen(key: String): ApiResult<Ack> =
        caller.authenticated("v1/wrapped/$key/seen", HttpMethodKind.POST)
}
