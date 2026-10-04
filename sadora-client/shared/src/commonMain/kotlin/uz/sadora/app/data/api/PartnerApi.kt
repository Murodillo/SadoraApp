package uz.sadora.app.data.api

import io.ktor.client.request.setBody
import uz.sadora.app.data.ApiCaller
import uz.sadora.app.data.ApiResult
import uz.sadora.app.data.HttpMethodKind
import uz.sadora.contract.Ack
import uz.sadora.contract.AcceptPartnerInviteRequest
import uz.sadora.contract.CreatePartnerInviteRequest
import uz.sadora.contract.CreatePartnerWebLinkRequest
import uz.sadora.contract.PartnerMessage
import uz.sadora.contract.PartnerMessageKind
import uz.sadora.contract.PartnerMessages
import uz.sadora.contract.PartnerWebLink
import uz.sadora.contract.SendPartnerMessageRequest
import uz.sadora.contract.FollowedPerson
import uz.sadora.contract.PartnerInvite
import uz.sadora.contract.PartnerPermissions
import uz.sadora.contract.PartnerRelation
import uz.sadora.contract.PartnerState
import uz.sadora.contract.PartnerView
import uz.sadora.contract.PausePartnerRequest

/** Yaqinim: her side under `/partner`, the follower's under `/partner/following`. */
class PartnerApi(private val caller: ApiCaller) {

    suspend fun state(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner", HttpMethodKind.GET)

    suspend fun invite(relation: PartnerRelation): ApiResult<PartnerInvite> =
        caller.authenticated("v1/partner/invite", HttpMethodKind.POST) { setBody(CreatePartnerInviteRequest(relation)) }

    suspend fun approve(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/approve", HttpMethodKind.POST)

    suspend fun savePermissions(permissions: PartnerPermissions): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/permissions", HttpMethodKind.PUT) { setBody(permissions) }

    suspend fun pause(paused: Boolean): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/pause", HttpMethodKind.POST) { setBody(PausePartnerRequest(paused)) }

    suspend fun end(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner", HttpMethodKind.DELETE)

    suspend fun messages(linkId: String): ApiResult<PartnerMessages> =
        caller.authenticated("v1/partner/links/$linkId/messages", HttpMethodKind.GET)

    suspend fun send(linkId: String, kind: PartnerMessageKind, text: String?): ApiResult<PartnerMessage> =
        caller.authenticated("v1/partner/links/$linkId/messages", HttpMethodKind.POST) {
            setBody(SendPartnerMessageRequest(kind, text))
        }

    suspend fun markRead(linkId: String): ApiResult<PartnerMessages> =
        caller.authenticated("v1/partner/links/$linkId/messages/read", HttpMethodKind.POST)

    suspend fun createWebLink(ttlHours: Int, permissions: PartnerPermissions): ApiResult<PartnerWebLink> =
        caller.authenticated("v1/partner/web", HttpMethodKind.POST) { setBody(CreatePartnerWebLinkRequest(ttlHours, permissions)) }

    suspend fun revokeWebLink(): ApiResult<PartnerState> =
        caller.authenticated("v1/partner/web", HttpMethodKind.DELETE)

    suspend fun labourAlert(): ApiResult<Ack> =
        caller.authenticated("v1/partner/alert/labour", HttpMethodKind.POST)

    suspend fun accept(code: String, name: String?, asPartnerAccount: Boolean): ApiResult<FollowedPerson> =
        caller.authenticated("v1/partner/accept", HttpMethodKind.POST) {
            setBody(AcceptPartnerInviteRequest(code, name, asPartnerAccount))
        }

    suspend fun view(linkId: String): ApiResult<PartnerView> =
        caller.authenticated("v1/partner/following/$linkId", HttpMethodKind.GET)

    suspend fun leave(linkId: String): ApiResult<Ack> =
        caller.authenticated("v1/partner/following/$linkId", HttpMethodKind.DELETE)
}
