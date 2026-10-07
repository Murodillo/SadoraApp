package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.ChoosePetRequest
import uz.sadora.contract.PetKind
import uz.sadora.contract.PetNudgeAnswer
import uz.sadora.contract.PetNudgeRequest
import uz.sadora.contract.PetState
import uz.sadora.contract.PetTrigger

/** The companion. Picking is free; the nudges are Premium and answer 402 otherwise. */
class PetApi(private val caller: ApiCaller) {

    suspend fun state(): ApiResult<PetState> =
        caller.authenticated("v1/pet", HttpMethodKind.GET)

    suspend fun choose(pet: PetKind): ApiResult<PetState> =
        caller.authenticated("v1/pet", HttpMethodKind.PUT) { setBody(ChoosePetRequest(pet)) }

    suspend fun nudge(trigger: PetTrigger, detail: String?): ApiResult<PetNudgeAnswer> =
        caller.authenticated("v1/pet/nudge", HttpMethodKind.POST) { setBody(PetNudgeRequest(trigger, detail)) }
}
