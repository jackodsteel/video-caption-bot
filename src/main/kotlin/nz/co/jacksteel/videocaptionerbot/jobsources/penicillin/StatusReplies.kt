package nz.co.jacksteel.videocaptionerbot.jobsources.penicillin

import io.ktor.client.HttpClient
import io.ktor.client.call.call
import io.ktor.client.call.receive
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.response.HttpResponse
import io.ktor.http.*
import jp.nephy.jsonkt.JsonObject
import jp.nephy.jsonkt.delegation.lambda
import jp.nephy.jsonkt.delegation.string
import jp.nephy.penicillin.core.auth.AuthorizationType
import jp.nephy.penicillin.core.request.ApiRequestBuilder
import jp.nephy.penicillin.core.request.header
import jp.nephy.penicillin.core.request.parameters
import jp.nephy.penicillin.core.session.ApiClient
import jp.nephy.penicillin.core.session.get
import jp.nephy.penicillin.endpoints.Statuses
import jp.nephy.penicillin.extensions.parseModel
import jp.nephy.penicillin.extensions.penicillinModel
import jp.nephy.penicillin.models.PenicillinModel
import jp.nephy.penicillin.models.Status
import jp.nephy.penicillin.models.User
import nz.co.jacksteel.videocaptionerbot.util.SharedConstants

internal typealias Option = Pair<String, Any?>

private val REQUIRED_COOKIES = listOf("_twitter_sess", "personalization_id", "guest_id", "ct0", "auth_token")
private val AUTHENTICITY_TOKEN_REGEX = "<input type=\"hidden\" value=\"([a-z0-9]+)\" name=\"authenticity_token\">".toRegex()

/*
 STEPS:
     GET https://twitter.com/login for cookies:
        _twitter_sess
        personalization_id
        guest_id
        ct0
        Also must get authenticity_token to include in next request
    POST https://twitter.com/sessions:
        With Form-URL-Encoded:
            session[username_or_email] = username
            session[password] = password
            authenticity_token = from last GET request
        For cookie:
            auth_token
        You can also follow the 2 redirects to get the html to grep for the new authentication header if needed:
            https://abs.twimg.com/responsive-web/web/main.*.js:
                grep that JS file for lots of repeated A's, currently under u=, slightly shorter than the other AAA one, and comes first
    Can then POST to https://api.twitter.com/2/timeline/conversation/{TWEET_ID}.json
        include the given cookies from earlier
        include x-csrf-token which = ct0
        include authorization header:
            Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA
*/
suspend fun Statuses.authenticate(
        username: String,
        password: String
): StatusAuthenticationResponse {
    HttpClient {
        followRedirects = false
    }.use { httpClient ->

        val initialReq = httpClient.call("https://twitter.com/login")

        val requiredCookies = initialReq.response.setCookieFixed().filter { it.name in REQUIRED_COOKIES }

        if (requiredCookies.size != 4) {
            throw TwitterAuthenticationException("Didn't get four expected cookies in the initial /login response")
        }
        val xCsrfToken = requiredCookies.find { it.name == "ct0" }?.value
                ?: throw TwitterAuthenticationException("Didn't find ct0 cookie in the /sessions response")

        val initialReqBody = initialReq.response.receive<String>()

        val authenticityToken = AUTHENTICITY_TOKEN_REGEX.find(initialReqBody)?.groups?.get(1)?.value
                ?: throw TwitterAuthenticationException("Didn't find authenticity_token in the initial /login response")

        val secondReq = httpClient.post<HttpResponse>("https://twitter.com/sessions") {
            body = FormDataContent(Parameters.build {
                append("session[username_or_email]", username)
                append("session[password]", password)
                append("authenticity_token", authenticityToken)
            })
            cookies(requiredCookies)
            header("x-csrf-token", xCsrfToken)
        }

        val authToken = secondReq.setCookieFixed().find { it.name == "auth_token" }
                ?: throw TwitterAuthenticationException("Didn't find auth_token cookie in the /sessions response")

        return StatusAuthenticationResponse(
                cookies = requiredCookies.plus(authToken),
                xCsrfToken = xCsrfToken,
                authorization = SharedConstants.TWITTER_AUTH_TOKEN
        )
    }
}


fun Statuses.conversation(
        conversationId: Long,
        authenticationData: StatusAuthenticationResponse,
        vararg options: Option
) = client.session.get("/2/timeline/conversation/$conversationId.json") {
    conversationAuth(authenticationData)
    parameters(
//            "include_profile_interstitial_type" to "1",
//            "include_blocking" to "1",
//            "include_blocked_by" to "1",
//            "include_followed_by" to "1",
//            "include_want_retweets" to "1",
//            "include_mute_edge" to "1",
//            "include_can_dm" to "1",
//            "include_can_media_tag" to "1",
//            "skip_status" to "1",
//            "cards_platform" to "Web-12",
//            "include_cards" to "1",
//            "include_composer_source" to "true",
//            "include_ext_alt_text" to "true",
//            "include_reply_count" to "1",
//            "tweet_mode" to "extended",
//            "include_entities" to "true",
//            "include_user_entities" to "true",
//            "include_ext_media_color" to "true",
//            "include_ext_media_availability" to "true",
//            "send_error_codes" to "true",
//            "count" to "20",
//            "ext" to "mediaStats%2ChighlightedLabel%2CcameraMoment"
    )
    parameters(
            *options
    )
}.jsonObject<ConversationResponse>()

data class StatusAuthenticationResponse(
        val cookies: List<Cookie>,
        val xCsrfToken: String,
        val authorization: String
)

data class ConversationResponse(override val json: JsonObject, override val client: ApiClient) : PenicillinModel {
    val globalObjects by penicillinModel<GlobalObjects>()
    val timeline by penicillinModel<Timeline>()

    data class GlobalObjects(override val json: JsonObject, override val client: ApiClient) : PenicillinModel {
        val statuses by lambda(key = "tweets") { it.jsonObject.values.map { it.parseModel(Status::class) } }
        val users by lambda { it.jsonObject.values.map { it.parseModel(User::class) } }
    }

    data class Timeline(override val json: JsonObject, override val client: ApiClient) : PenicillinModel {
        val id by string
    }
}

class TwitterAuthenticationException(message: String) : Exception(message)

fun HttpRequestBuilder.cookies(cookies: List<Cookie>) {
    headers.append("Cookie", cookies.joinToString(separator = "; ") { "${it.name}=${it.value}" })
}

fun HttpMessage.setCookieFixed(): List<Cookie> =
        headers.getAll(HttpHeaders.SetCookie)?.map {
            val fixed = "(Expires=[a-zA-Z]{3}, )([0-9])( .*GMT;)".toRegex().replace(it) { m ->
                "${m.groupValues[1]}0${m.groupValues[2]}${m.groupValues[3]}"
            }
            parseServerSetCookieHeader(fixed)
        } ?: emptyList()

fun ApiRequestBuilder.cookies(cookies: List<Cookie>) {
    headers["Cookie"] = cookies.joinToString(separator = "; ") { "${it.name}=${it.value}" }
}

fun ApiRequestBuilder.conversationAuth(authenticationData: StatusAuthenticationResponse) {
    authorizationType = AuthorizationType.None
    cookies(authenticationData.cookies)
    header("x-csrf-token", authenticationData.xCsrfToken)
    header("authorization", authenticationData.authorization)
}