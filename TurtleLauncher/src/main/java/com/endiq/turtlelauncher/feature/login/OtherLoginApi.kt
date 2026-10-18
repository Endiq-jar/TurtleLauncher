package com.endiq.turtlelauncher.feature.login

import android.content.Context
import com.google.gson.Gson
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.UrlManager
import com.endiq.turtlelauncher.utils.path.UrlManager.Companion.createRequestBuilder
import com.endiq.turtlelauncher.utils.stringutils.StringUtilsKt
import net.endiq.launcher.Tools
import net.endiq.launcher.value.MinecraftAccount
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Objects
import java.util.UUID

object OtherLoginApi {
    private var client: OkHttpClient = UrlManager.createOkHttpClient()
    private var baseUrl: String? = null

    fun setBaseUrl(baseUrl: String) {
        var url = baseUrl
        if (baseUrl.endsWith("/")) {
            url = baseUrl.substring(0, baseUrl.length - 1)
        }
        this.baseUrl = url
    }

    fun login(context: Context, userName: String?, password: String?, listener: Listener) {
        if (Objects.isNull(baseUrl)) {
            listener.onFailed(context.getString(R.string.other_login_baseurl_not_set))
            return
        }
        val agent = AuthRequest.Agent().apply {
            this.name = "Minecraft"
            this.version = 1
        }
        val authRequest = AuthRequest().apply {
            this.username = userName
            this.password = password
            this.agent = agent
            this.requestUser = true
            this.clientToken = UUID.randomUUID().toString().lowercase()
        }
        val data = Gson().toJson(authRequest)
        callLogin(data, "/authserver/authenticate", listener)
    }

    fun refresh(context: Context, account: MinecraftAccount, select: Boolean, listener: Listener) {
        if (Objects.isNull(baseUrl)) {
            listener.onFailed(context.getString(R.string.other_login_baseurl_not_set))
            return
        }
        val refresh = Refresh().apply {
            this.clientToken = account.clientToken
            this.accessToken = account.accessToken
        }
        if (select) {
            val selectedProfile = Refresh.SelectedProfile().apply {
                this.name = account.username
                this.id = account.profileId
            }
            refresh.selectedProfile = selectedProfile
        }
        val data = Gson().toJson(refresh)
        callLogin(data, "/authserver/refresh", listener)
    }

    private fun callLogin(data: String, url: String, listener: Listener) {
        // Build the request *and* execute it inside a single runCatching so that DNS/network
        // failures (UnknownHostException, ConnectException, SocketTimeoutException, …) never
        // escape up to the worker thread as uncaught exceptions — they're delivered to the
        // listener's onFailed() instead, which is what the UI already expects.
        runCatching {
            val body = data.toRequestBody("application/json".toMediaTypeOrNull())
            val call = client.newCall(createRequestBuilder(baseUrl + url, body).build())
            call.execute().use { response ->
                val res = response.body?.string()
                if (response.code == 200) {
                    val result = Gson().fromJson(res, AuthResult::class.java)
                    listener.onSuccess(result)
                } else {
                    var errorMessage: String = res ?: "null"
                    runCatching parseMessage@{
                        if (errorMessage == "null") return@parseMessage

                        val jsonObject = JSONObject(errorMessage)
                        errorMessage = if (jsonObject.has("errorMessage")) {
                            jsonObject.getString("errorMessage")
                        } else if (jsonObject.has("message")) {
                            jsonObject.getString("message")
                        } else {
                            Logging.e("Other Login", "The error message returned by the server could not be retrieved.")
                            return@parseMessage
                        }

                        if (errorMessage.contains("\\u"))
                            errorMessage = StringUtilsKt.decodeUnicode(errorMessage.replace("\\\\u", "\\u"))
                    }.onFailure { e -> Logging.e("Other Login", Tools.printToString(e)) }
                    listener.onFailed("(${response.code}) $errorMessage")
                }
            }
        }.onFailure { e ->
            Logging.e("Other Login", "Login request to $baseUrl$url failed", e)
            listener.onFailed(e.message ?: e.toString())
        }
    }

    fun getServeInfo(context: Context, url: String): String? {
        // Entire body wrapped in runCatching — not just call.execute() — so that any problem
        // building the request, resolving DNS, connecting, or reading the body is contained.
        // The onFailure action itself is also guarded: if Tools.showError() throws (e.g. the
        // Activity is being destroyed when the worker thread finishes), the exception must
        // not propagate out to the ThreadPoolExecutor as an uncaught throwable.
        return runCatching {
            val call = client.newCall(createRequestBuilder(url).get().build())
            call.execute().use { response ->
                if (response.code == 200) response.body?.string() else null
            }
        }.onFailure { e ->
            Logging.w("OtherLoginApi", "Failed to fetch server info from $url", e)
            runCatching { Tools.showError(context, e) }
                .onFailure { inner -> Logging.e("OtherLoginApi", "showError itself failed", inner) }
        }.getOrNull()
    }

    interface Listener {
        fun onSuccess(authResult: AuthResult)
        fun onFailed(error: String)
    }
}