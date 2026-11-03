package com.endiq.turtlelauncher.feature.download.platform.curseforge

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.mod.CurseForgeKeys
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.UrlManager
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Low-level CurseForge v1 API access for the Download screen.
 *
 * CurseForge is kept next to Modrinth rather than replacing it: the two are exposed as
 * separate values of [com.endiq.turtlelauncher.feature.download.enums.Platform], and every
 * call here returns raw JSON so the mapping into the launcher's own item types stays in
 * [CurseForgeHelper] where it can be compared against the Modrinth one.
 *
 * All calls are synchronous and must run on a background thread; the Download fragments
 * already search through TaskExecutors.
 */
internal object CurseForgeApiClient {
    private const val BASE = "https://api.curseforge.com/v1"

    /** Minecraft's game id on CurseForge - every request is scoped to it. */
    const val MINECRAFT_GAME_ID = 432

    private val client by lazy { UrlManager.createOkHttpClient() }

    /** Thrown when the build/user has no key at all - callers turn this into a hint. */
    class MissingKeyException : IOException(
        "CurseForge needs an API key. Add CURSEFORGE_API_KEY as a repository secret " +
            "(it is then built into the APK) or paste a key in Settings -> Game -> CurseForge API Key."
    )

    class ApiException(val statusCode: Int, message: String) : IOException(message)

    private fun get(path: String, params: Map<String, String> = emptyMap()): JsonObject {
        val key = CurseForgeKeys.effectiveApiKey()
        if (key.isBlank()) throw MissingKeyException()

        val url: HttpUrl = HttpUrl.Builder()
            .scheme("https")
            .host("api.curseforge.com")
            .addPathSegments("v1/$path")
            .apply { params.forEach { (name, value) -> addQueryParameter(name, value) } }
            .build()

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", key)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Logging.e("CurseForgeApiClient", "GET $path -> HTTP ${response.code}")
                throw ApiException(response.code, "CurseForge returned HTTP ${response.code}.")
            }
            return JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                ?: throw ApiException(response.code, "CurseForge returned an unexpected response.")
        }
    }

    private fun dataArray(response: JsonObject): JsonArray? =
        response.getAsJsonArray("data")

    fun search(params: Map<String, String>): JsonObject = get("mods/search", params)

    fun getMod(modId: String): JsonObject =
        get("mods/$modId")

    fun getFiles(modId: String, params: Map<String, String> = emptyMap()): JsonArray? =
        dataArray(get("mods/$modId/files", params))

    /** Turns the paging block into the two numbers the search UI needs. */
    fun pagination(response: JsonObject): Pair<Int, Int> {
        val pagination = response.getAsJsonObject("pagination") ?: return 0 to 0
        val resultCount = pagination.get("resultCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val totalCount = pagination.get("totalCount")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        return resultCount to totalCount
    }

    fun items(response: JsonObject): List<JsonObject> =
        dataArray(response)?.mapNotNull { it.takeIf { element -> element.isJsonObject }?.asJsonObject }
            ?: emptyList()
}
