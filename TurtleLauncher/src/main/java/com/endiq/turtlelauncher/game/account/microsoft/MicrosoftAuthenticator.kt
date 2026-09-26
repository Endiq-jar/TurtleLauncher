/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.game.account.microsoft

import com.endiq.turtlelauncher.BuildKeys
import com.endiq.turtlelauncher.game.account.Account
import com.endiq.turtlelauncher.game.account.AccountType
import com.endiq.turtlelauncher.game.account.AccountsManager
import com.endiq.turtlelauncher.game.account.CredentialsExpiredException
import com.endiq.turtlelauncher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.BLOCKED_IP
import com.endiq.turtlelauncher.game.account.microsoft.MinecraftProfileException.ExceptionStatus.FREQUENT
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.BANNED
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.BLOCKED_REGION
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.NOT_ACCEPTED_SERVICE
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.REACHED_PLAYTIME_LIMIT
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.REQUIRES_PROOF_OF_AGE
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.RESTRICTED
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNDERAGE
import com.endiq.turtlelauncher.game.account.microsoft.XboxLoginException.ExceptionStatus.UNREGISTERED
import com.endiq.turtlelauncher.game.account.microsoft.models.DeviceCodeResponse
import com.endiq.turtlelauncher.game.account.microsoft.models.MinecraftAuthResponse
import com.endiq.turtlelauncher.game.account.microsoft.models.TokenResponse
import com.endiq.turtlelauncher.game.account.microsoft.models.XBLProperties
import com.endiq.turtlelauncher.game.account.microsoft.models.XBLRequest
import com.endiq.turtlelauncher.game.account.microsoft.models.XSTSAuthResult
import com.endiq.turtlelauncher.game.account.microsoft.models.XSTSProperties
import com.endiq.turtlelauncher.game.account.microsoft.models.XSTSRequest
import com.endiq.turtlelauncher.game.account.wardrobe.SkinModelType
import com.endiq.turtlelauncher.game.account.yggdrasil.findUsing
import com.endiq.turtlelauncher.game.account.yggdrasil.getPlayerProfile
import com.endiq.turtlelauncher.game.account.yggdrasil.getSkinModel
import com.endiq.turtlelauncher.path.GLOBAL_CLIENT
import com.endiq.turtlelauncher.utils.logging.Logger
import com.endiq.turtlelauncher.utils.network.httpPostJson
import com.endiq.turtlelauncher.utils.network.safeBodyAsJson
import com.endiq.turtlelauncher.utils.network.submitForm
import com.endiq.turtlelauncher.utils.string.toUuidStr
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "MicrosoftAuth"

private val SCOPES = listOf("XboxLive.signin", "offline_access", "openid", "profile", "email")
private const val TENANT = "/consumers"

/**
 * Maximum consecutive network failures allowed while polling for the token
 * On poor networks, occasional failures shouldn't abort the login, but sustained failures mean the network is down; give up early
 */
private const val MAX_CONSECUTIVE_POLL_FAILURES = 5

const val MICROSOFT_AUTH_URL = "https://login.microsoftonline.com"
const val LIVE_AUTH_URL = "https://login.live.com"
const val XBL_AUTH_URL = "https://user.auth.xboxlive.com"
const val XSTS_AUTH_URL = "https://xsts.auth.xboxlive.com"
const val MINECRAFT_SERVICES_URL = "https://api.minecraftservices.com"

/**
 * Fetches a device code response from the Microsoft identity endpoint
 * The device code authorizes the user on a separate device or browser
 */
suspend fun fetchDeviceCodeResponse(context: CoroutineContext): DeviceCodeResponse = coroutineScope {
    withRetry {
        submitForm(
            url = "$MICROSOFT_AUTH_URL$TENANT/oauth2/v2.0/devicecode",
            parameters = Parameters.build {
                append("client_id", BuildKeys.OAUTH_CLIENT_ID)
                append("scope", SCOPES.joinToString(" "))
            },
            context = context
        )
    }
}

/**
 * Retrieves access and refresh tokens from Microsoft Azure Active Directory via the device code flow
 * This function polls the Microsoft token endpoint regularly until a token is obtained or it times out
 */
suspend fun getTokenResponse(
    codeResponse: DeviceCodeResponse,
    context: CoroutineContext,
    checkCancelled: suspend (time: Int) -> Boolean
): TokenResponse = coroutineScope {
    var pollingInterval = codeResponse.interval * 1000L
    val expireTime = System.currentTimeMillis() + codeResponse.expiresIn * 1000L

    var cancelled = 0
    suspend fun checkIsReallyCancelled(): Boolean {
        if (checkCancelled(cancelled)) cancelled++
        return cancelled > 1
    }

    //Consecutive network-layer failures, to avoid polling pointlessly until the device code expires when the network is long down
    var consecutiveFailures = 0

    Logger.debug(TAG, "Polling for token, interval = ${pollingInterval}ms, expires in ${codeResponse.expiresIn}s")

    while (System.currentTimeMillis() < expireTime) {
        context.ensureActive()

        try {
            val response: JsonObject = submitForm(
                "$MICROSOFT_AUTH_URL$TENANT/oauth2/v2.0/token",
                parameters = Parameters.build {
                    append("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    append("device_code", codeResponse.deviceCode)
                    append("client_id", BuildKeys.OAUTH_CLIENT_ID)
                    append("tenant", TENANT)
                },
                context = context
            )
            consecutiveFailures = 0

            if (response["token_type"]?.jsonPrimitive?.content == "Bearer") {
                Logger.debug(TAG, "Access token successfully retrieved")
                return@coroutineScope TokenResponse(
                    accessToken = response["access_token"].text(),
                    refreshToken = response["refresh_token"].text(),
                    expiresIn = response["expires_in"]?.jsonPrimitive?.int ?: 0
                )
            }
            Logger.warning(TAG, "Token endpoint responded without a Bearer token, continuing to poll")
        } catch (e: ClientRequestException) {
            when (val error = e.errorCode()) {
                // Server responded normally: the network is usable
                "authorization_pending" -> consecutiveFailures = 0 // normal case, keep polling
                "slow_down" -> {
                    consecutiveFailures = 0
                    pollingInterval += 1000L
                    Logger.debug(TAG, "Slowing down polling to ${pollingInterval}ms")
                }
                else -> {
                    Logger.error(TAG, "Token endpoint rejected the polling request: error = $error", e)
                    throw e
                }
            }
        } catch (e: CancellationException) {
            Logger.debug(TAG, "Authentication cancelled")
            throw e
        } catch (e: Exception) {
            // Transient errors during polling (network jitter, DNS failures, server 5xx, request timeouts) shouldn't abort the whole login flow
            // Keep polling while the device code is still valid, but give up early when the network stays unavailable
            consecutiveFailures++
            if (consecutiveFailures >= MAX_CONSECUTIVE_POLL_FAILURES ||
                System.currentTimeMillis() + pollingInterval >= expireTime
            ) {
                Logger.error(TAG, "Polling failed $consecutiveFailures time(s) in a row, giving up", e)
                throw e
            }
            Logger.warning(TAG, "Polling failed, will retry in ${pollingInterval}ms: ${e::class.simpleName}: ${e.message}")
        }

        if (checkIsReallyCancelled()) {
            Logger.debug(TAG, "The user left the web page, cancelling the authentication")
            throw CancellationException("Authentication cancelled")
        }

        delay(pollingInterval.milliseconds).also {
            context.ensureActive()
        }
    }
    Logger.warning(TAG, "Device code expired before the user completed authorization")
    throw HttpRequestTimeoutException("Authentication timed out!", expireTime)
}

/**
 * Parses the error field from an OAuth error response
 */
private suspend fun ClientRequestException.errorCode(): String? {
    return runCatching {
        response.safeBodyAsJson<JsonObject>()["error"]?.jsonPrimitive?.content
    }.getOrNull()
}

/**
 * Asynchronously authenticates the user with the given auth type and retrieves their Minecraft account info
 * The function orchestrates the authentication through a series of steps, depending on the provided [authType].
 *
 * Supports refreshing an existing access token or using a provided one. It then continues with Xbox Live (XBL) and the Xbox Secure Token Service (XSTS), and finally Minecraft.
 *
 * It can verify the user owns the game and then builds the [Account] object.
 *
 * @param statusUpdate callback reporting which authentication step is running
 */
suspend fun microsoftAuthAsync(
    authType: AuthType,
    refreshToken: String,
    accessToken: String = "NULL",
    context: CoroutineContext,
    statusUpdate: (AsyncStatus) -> Unit,
): Account = coroutineScope {
    val (finalAccessToken, newRefreshToken) = when (authType) {
        AuthType.Refresh -> refreshAccessToken(refreshToken, statusUpdate, context)
        else -> Pair(accessToken, refreshToken)
    }

    Logger.debug(TAG, "Authenticating with Xbox Live (XBL)")
    val xblToken = authenticateXBL(finalAccessToken, statusUpdate)
    Logger.debug(TAG, "Authenticating with Xbox Secure Token Service (XSTS)")
    val xstsToken = authenticateXSTS(xblToken.first, xblToken.second, statusUpdate, context)
    Logger.debug(TAG, "Authenticating with Minecraft services")
    val authResponse = authenticateMinecraft(xstsToken, statusUpdate, context)
    Logger.debug(TAG, "Verifying Minecraft ownership")
    verifyGameOwnership(authResponse.accessToken, statusUpdate)

    return@coroutineScope createAccount(authResponse, newRefreshToken, xblToken.second, statusUpdate)
}

/**
 * Checks whether the cached accessToken is still accepted by the server
 * @return false when the server has rejected the credential
 */
suspend fun validateAccessToken(account: Account): Boolean = try {
    getPlayerProfile(MINECRAFT_SERVICES_URL, account.accessToken)
    true
} catch (e: MinecraftProfileException) {
    false
} catch (e: ResponseException) {
    when (e.response.status.value) {
        401, 403 -> false
        else -> throw e
    }
}

private suspend fun refreshAccessToken(
    refreshToken: String,
    update: (AsyncStatus) -> Unit,
    context: CoroutineContext
): Pair<String, String> {
    update(AsyncStatus.GETTING_ACCESS_TOKEN)

    return withRetry {
        try {
            val response = submitForm<JsonObject>(
                url = "$LIVE_AUTH_URL/oauth20_token.srf",
                parameters = Parameters.build {
                    append("client_id", BuildKeys.OAUTH_CLIENT_ID)
                    append("refresh_token", refreshToken)
                    append("grant_type", "refresh_token")
                },
                context = context
            )
            Pair(
                response["access_token"].text(),
                response["refresh_token"]?.jsonPrimitive?.content ?: refreshToken
            )
        } catch (e: ClientRequestException) {
            //The refresh token was revoked or expired; no automatic recovery possible
            if (e.errorCode() == "invalid_grant") throw CredentialsExpiredException()
            throw e
        }
    }
}

private suspend fun authenticateXBL(accessToken: String, update: (AsyncStatus) -> Unit): Pair<String, String> {
    update(AsyncStatus.GETTING_XBL_TOKEN)

    suspend fun requestXblToken(rpsTicket: String): Pair<String, String> {
        val requestBody = XBLRequest(
            properties = XBLProperties(
                authMethod = "RPS",
                siteName = "user.auth.xboxlive.com",
                rpsTicket = rpsTicket
            ),
            relyingParty = "http://auth.xboxlive.com",
            tokenType = "JWT"
        )

        val response = GLOBAL_CLIENT.post("$XBL_AUTH_URL/user/authenticate") {
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.safeBodyAsJson<JsonObject>()

        //Extract the uhs
        val uhs = response["DisplayClaims"]?.jsonObject
            ?.get("xui")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("uhs")?.jsonPrimitive
            ?.content ?: throw Exception("Missing uhs in XBL response")

        return Pair(response["Token"].text(), uhs)
    }

    return withRetry {
        try {
            requestXblToken("d=$accessToken")
        } catch (e: ClientRequestException) {
            // Per the wiki: if RpsTicket fails with 400 Bad Request, retry without the "d=" prefix
            // https://zh.minecraft.wiki/w/Tutorial:%E7%BC%96%E5%86%99%E5%90%AF%E5%8A%A8%E5%99%A8#Xbox_Live%E8%BA%AB%E4%BB%BD%E9%AA%8C%E8%AF%81
            if (e.response.status.value == 400) {
                Logger.warning(TAG, "XBL authentication rejected the d= prefixed RpsTicket, retrying without the prefix")
                requestXblToken(accessToken)
            } else throw e
        }
    }
}

private suspend fun authenticateXSTS(
    xblToken: String,
    uhs: String,
    update: (AsyncStatus) -> Unit,
    context: CoroutineContext
): XSTSAuthResult {
    update(AsyncStatus.GETTING_XSTS_TOKEN)

    return withRetry {
        try {
            val response = httpPostJson<JsonObject>(
                url = "$XSTS_AUTH_URL/xsts/authorize",
                body = XSTSRequest(
                    properties = XSTSProperties(
                        sandboxId = "RETAIL",
                        userTokens = listOf(xblToken)
                    ),
                    relyingParty = "rp://api.minecraftservices.com/",
                    tokenType = "JWT"
                ),
                context = context
            )

            XSTSAuthResult(token = response["Token"].text(), uhs = uhs)
        } catch (e: ClientRequestException) {
            // XSTS uniformly returns 4xx with an XErr code for account issues; expectSuccess throws early
            // so the XErr must be parsed from the error response body to show the user the real failure reason
            val errorBody = runCatching { e.response.safeBodyAsJson<JsonObject>() }.getOrNull()
            when (val xErr = errorBody?.get("XErr").text()) {
                //Reference : https://github.com/PrismarineJS/prismarine-auth/blob/1aef6e1/src/common/Constants.js#L50-L59
                "2148916227" -> throw XboxLoginException(BANNED)
                "2148916229" -> throw XboxLoginException(RESTRICTED)
                "2148916233" -> throw XboxLoginException(UNREGISTERED)
                "2148916234" -> throw XboxLoginException(NOT_ACCEPTED_SERVICE)
                "2148916235" -> throw XboxLoginException(BLOCKED_REGION)
                "2148916236" -> throw XboxLoginException(REQUIRES_PROOF_OF_AGE)
                "2148916237" -> throw XboxLoginException(REACHED_PLAYTIME_LIMIT)
                "2148916238" -> throw XboxLoginException(UNDERAGE)
                else -> {
                    Logger.error(
                        TAG,
                        "XSTS authentication failed: status = ${e.response.status}, XErr = $xErr, message = ${errorBody?.get("Message").text()}"
                    )
                    throw e
                }
            }
        }
    }
}

private suspend fun authenticateMinecraft(
    xstsResult: XSTSAuthResult,
    update: (AsyncStatus) -> Unit,
    context: CoroutineContext
): MinecraftAuthResponse {
    update(AsyncStatus.AUTHENTICATE_MINECRAFT)

    return withRetry {
        runCatching {
            httpPostJson<MinecraftAuthResponse>(
                url = "$MINECRAFT_SERVICES_URL/authentication/login_with_xbox",
                body = mapOf("identityToken" to "XBL3.0 x=${xstsResult.uhs};${xstsResult.token}"),
                context = context
            )
        }.onFailure { e ->
            if (e is ResponseException) {
                when (e.response.status.value) {
                    429 -> throw MinecraftProfileException(FREQUENT)
                    403 -> throw MinecraftProfileException(BLOCKED_IP)
                }
            }
        }.getOrThrow()
    }
}

private suspend fun verifyGameOwnership(accessToken: String, update: (AsyncStatus) -> Unit) {
    update(AsyncStatus.VERIFY_GAME_OWNERSHIP)
    withRetry {
        val response = GLOBAL_CLIENT.get("$MINECRAFT_SERVICES_URL/entitlements/mcstore") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        if (response.safeBodyAsJson<JsonObject>()["items"]?.jsonArray?.isEmpty() != false) {
            throw NotPurchasedMinecraftException()
        }
    }
}

private suspend fun createAccount(
    authResponse: MinecraftAuthResponse,
    refreshToken: String,
    uhs: String,
    statusUpdate: (AsyncStatus) -> Unit
): Account {
    statusUpdate(AsyncStatus.GETTING_PLAYER_PROFILE)

    val profile = getPlayerProfile(
        apiUrl = MINECRAFT_SERVICES_URL,
        accessToken = authResponse.accessToken
    )

    val profileId = profile.id
    //Avoid adding the same account repeatedly
    val account = AccountsManager.loadFromProfileID(profileId, AccountType.MICROSOFT.tag) ?: Account()

    return account.apply {
        this.username = profile.name
        this.accessToken = authResponse.accessToken
        this.expiresAt = System.currentTimeMillis() + authResponse.expiresIn * 1000
        this.accountType = AccountType.MICROSOFT.tag
        this.clientToken = BuildKeys.LAUNCHER_NAME.toUuidStr().replace("-", "")
        this.profileId = profileId
        this.refreshToken = refreshToken.ifEmpty { "None" }
        this.xUid = uhs
        this.skinModelType = profile.skins.findUsing()?.getSkinModel() ?: SkinModelType.NONE
    }
}

private fun JsonElement?.text() = this?.jsonPrimitive?.content.orEmpty()

private suspend fun <T> withRetry(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10_000,
    block: suspend () -> T
): T = com.endiq.turtlelauncher.utils.network.withRetry(
    "MicrosoftAuthenticator", maxRetries, initialDelay, maxDelay, block
)