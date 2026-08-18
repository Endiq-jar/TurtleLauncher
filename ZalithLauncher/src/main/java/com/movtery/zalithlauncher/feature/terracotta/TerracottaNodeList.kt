package com.movtery.zalithlauncher.feature.terracotta

import com.google.gson.JsonParser
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.path.UrlManager
import java.util.concurrent.TimeUnit

/**
 * Optional extra Terracotta/EasyTier relay nodes, fetched from the same public list
 * FoldCraftLauncher uses (net.burningtnt.terracotta has its own built-in default nodes,
 * so this is purely a supplementary reliability improvement, not a requirement - Terracotta
 * works fine with extraNodes=null, which is what host/join fell back to before this file
 * existed). Adapted from FCL's TerracottaNodeList.java (FCL-Team/FoldCraftLauncher, GPLv3),
 * using this project's UrlManager/Gson instead of fclcore's HttpRequest.
 *
 * Best-effort: any failure (network, bad JSON) yields an empty list rather than blocking
 * host/join, exactly like the FCL original.
 *
 * Custom-node override ported from ZalithLauncher2 PR #1496 (closes ZL2#1486/#1211): the
 * default public node is unreliable for guests joining a session ("Cannot find scaffolding
 * server" / PingHostFail - the same known issue TerracottaFragment already warns about via
 * terracotta_join_known_issue). When AllSettings.enableTerracottaNodes is on and
 * AllSettings.terracottaNodes is non-blank, that user-supplied node is used exclusively and
 * the unreliable default fetch is skipped entirely, same as upstream's behavior.
 */
object TerracottaNodeList {
    private const val NODE_LIST_URL = "https://terracotta.glavo.site/nodes"

    @Volatile
    private var cached: List<String>? = null

    /** Fetches (and caches for the process lifetime) the extra node URL list. Blocking -
     *  call from a background thread. Returns an empty list on any failure. */
    @JvmStatic
    fun fetch(): List<String> {
        cached?.let { return it }

        synchronized(this) {
            cached?.let { return it }

            if (AllSettings.enableTerracottaNodes.getValue()) {
                val custom = AllSettings.terracottaNodes.getValue().trim()
                if (custom.isNotEmpty()) {
                    val result = listOf(custom)
                    cached = result
                    return result
                }
                // Blank -> fall through to the default node logic below, same as upstream.
            }

            val result = runCatching {
                val client = UrlManager.createOkHttpClientBuilder { it.callTimeout(10, TimeUnit.SECONDS) }.build()
                val request = UrlManager.createRequestBuilder(NODE_LIST_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use emptyList()
                    val body = response.body?.string() ?: return@use emptyList()
                    JsonParser.parseString(body).asJsonArray.mapNotNull { element ->
                        runCatching {
                            val obj = element.asJsonObject
                            obj.get("url")?.takeIf { !it.isJsonNull }?.asString
                        }.getOrNull()
                    }
                }
            }.onFailure { e ->
                Logging.w("TerracottaNodeList", "Failed to fetch Terracotta node list", e)
            }.getOrDefault(emptyList())

            cached = result
            return result
        }
    }

    /** Call after changing enableTerracottaNodes/terracottaNodes in Settings so the next
     *  host/join picks up the new value instead of the process-lifetime cache. */
    @JvmStatic
    fun invalidateCache() {
        cached = null
    }
}
