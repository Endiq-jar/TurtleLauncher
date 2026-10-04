package com.endiq.turtlelauncher.feature.ai

import android.content.Context
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.path.UrlManager
import com.google.gson.JsonParser
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Internet search for Turtle AI.
 *
 * Design notes, because a search feature has a privacy and a correctness cost:
 *
 *  - **Off by default.** Nothing is sent anywhere until the user turns on
 *    `aiWebSearchEnabled` in Settings -> Experimental, and the setting spells out which hosts
 *    the query goes to.
 *  - **No keys required for the built-ins.** Minecraft Wiki, Wikipedia and DuckDuckGo's
 *    instant-answer endpoint are public HTTP APIs; that keeps the feature usable without the
 *    user signing up for anything, and the custom provider (below) is there for anyone who
 *    wants a real, privacy-respecting index (e.g. their own SearXNG) instead.
 *  - **The assistant never quotes a search result as fact.** [formatForPrompt] hands the whole
 *    block to the model along with the citation rules in [TurtleAiPrompt.WEB_SEARCH], and
 *    [formatForChat] shows the raw, attributed links when there is no model to synthesize with.
 *    So the user always sees where a claim came from - the assistant never launders a random
 *    blog post into "the launcher says".
 *
 * Every provider is a single HTTP GET + a tolerant JSON parse: any provider can change shape
 * without warning, so a parse failure is reported as a failure (and, in [PROVIDER_AUTO], the
 * next provider is tried) instead of being silently rendered as "no results".
 */
object TurtleAiWebSearch {

    private const val TAG = "TurtleAiWebSearch"

    const val PROVIDER_AUTO = "auto"
    const val PROVIDER_MINECRAFT_WIKI = "minecraftwiki"
    const val PROVIDER_WIKIPEDIA = "wikipedia"
    const val PROVIDER_DUCKDUCKGO = "duckduckgo"
    const val PROVIDER_CUSTOM = "custom"

    /** Provider choices for the settings picker: value -> label. */
    val PICKER: List<Pair<String, String>> = listOf(
        PROVIDER_AUTO to "Automatic (Minecraft Wiki, then Wikipedia, then DuckDuckGo)",
        PROVIDER_MINECRAFT_WIKI to "Minecraft Wiki",
        PROVIDER_WIKIPEDIA to "Wikipedia",
        PROVIDER_DUCKDUCKGO to "DuckDuckGo (instant answers)",
        PROVIDER_CUSTOM to "Custom (SearXNG-compatible JSON)"
    )

    private const val MAX_RESULTS = 4
    private const val MAX_SNIPPET_CHARS = 260
    private const val MAX_QUERY_CHARS = 200
    // Short on purpose: a search that hasn't answered in this long is not worth the wait,
    // and the assistant has a fallback for every outcome.
    private const val TIMEOUT_SECONDS = 12L

    /** Politeness/identification for the public wikis, which require a real User-Agent. */
    private const val USER_AGENT =
        "TurtleLauncher-TurtleAI/1.0 (Minecraft: Java Edition launcher for Android; " +
            "+https://github.com/Endiq-jar/TurtleLauncher)"

    data class Result(val title: String, val snippet: String, val url: String)

    sealed class Outcome {
        /** Search ran: [results] is non-empty. */
        data class Ok(val results: List<Result>, val provider: String) : Outcome()
        /** The user has not enabled web search. */
        object Disabled : Outcome()
        /** Search ran but nothing usable came back. */
        data class Empty(val tried: List<String>) : Outcome()
        /** Search could not run at all (network, HTTP error, unparseable response). */
        data class Failed(val reason: String) : Outcome()
    }

    // ── Settings ────────────────────────────────────────────────────────────────────

    @JvmStatic
    fun isEnabled(context: Context): Boolean =
        runCatching { AllSettings.aiWebSearchEnabled.getValue() }.getOrDefault(false)

    @JvmStatic
    fun provider(context: Context): String =
        runCatching { AllSettings.aiSearchProvider.getValue() }.getOrDefault(PROVIDER_AUTO)
            .trim().lowercase(Locale.ROOT).ifBlank { PROVIDER_AUTO }

    @JvmStatic
    fun providerLabel(value: String): String =
        PICKER.firstOrNull { it.first == value }?.second ?: value

    // ── Entry point ─────────────────────────────────────────────────────────────────

    /**
     * Runs [query] against the configured provider.
     *
     * Blocking - call from a background thread (the assistant already answers off the main
     * thread). Never throws: every failure path is an [Outcome].
     */
    @JvmStatic
    fun search(context: Context, query: String, languageTag: String): Outcome {
        if (!isEnabled(context)) return Outcome.Disabled
        val clean = query.replace(Regex("\\s+"), " ").trim().take(MAX_QUERY_CHARS)
        if (clean.isBlank()) return Outcome.Empty(emptyList())

        val chosen = provider(context)
        val order = if (chosen == PROVIDER_AUTO) {
            listOf(PROVIDER_MINECRAFT_WIKI, PROVIDER_WIKIPEDIA, PROVIDER_DUCKDUCKGO)
        } else {
            listOf(chosen)
        }

        val tried = mutableListOf<String>()
        val problems = mutableListOf<String>()
        for (candidate in order) {
            tried.add(candidate)
            val outcome = runCatching { runProvider(context, candidate, clean, languageTag) }
                .onFailure { t -> Logging.w(TAG, "Search provider $candidate threw", t) }
                .getOrElse { Outcome.Failed(it.message ?: it.javaClass.simpleName) }
            when (outcome) {
                is Outcome.Ok -> return outcome
                is Outcome.Failed -> problems.add(candidate + ": " + outcome.reason)
                else -> Unit // Empty: keep trying the next provider.
            }
        }
        // Everything either returned nothing or failed. A failure is more informative than
        // "no results", so report that when we have one.
        return if (problems.isNotEmpty() && problems.size == tried.size) {
            Outcome.Failed(problems.joinToString("; "))
        } else {
            Outcome.Empty(tried)
        }
    }

    private fun runProvider(
        context: Context,
        provider: String,
        query: String,
        languageTag: String
    ): Outcome = when (provider) {
        PROVIDER_MINECRAFT_WIKI -> mediaWiki(
            base = "https://minecraft.wiki",
            articlePath = "/w/",
            query = query,
            provider = provider
        )
        PROVIDER_WIKIPEDIA -> mediaWiki(
            base = "https://" + wikiLanguage(languageTag) + ".wikipedia.org",
            articlePath = "/wiki/",
            query = query,
            provider = provider
        )
        PROVIDER_DUCKDUCKGO -> duckDuckGo(query, provider)
        PROVIDER_CUSTOM -> custom(context, query, provider)
        else -> Outcome.Failed("unknown provider '$provider'")
    }

    /** Wikipedia has a subdomain per language; anything we don't know uses English. */
    private fun wikiLanguage(tag: String): String {
        val base = tag.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
        return if (base.length == 2 && base.all { it in 'a'..'z' }) base else "en"
    }

    // ── Providers ───────────────────────────────────────────────────────────────────

    /** Shared MediaWiki `list=search` handler - minecraft.wiki and Wikipedia use the same shape. */
    private fun mediaWiki(base: String, articlePath: String, query: String, provider: String): Outcome {
        val url = base + "/api.php?action=query&format=json&list=search&srlimit=" + MAX_RESULTS +
            "&srprop=snippet&srsearch=" + urlEncode(query)
        val json = getJson(url) ?: return Outcome.Failed("no response from " + hostOf(base))
        val hits = json.asJsonObject.getAsJsonObject("query")?.getAsJsonArray("search")
            ?: return Outcome.Failed("unexpected JSON from " + hostOf(base))
        val results = hits.mapNotNull { element ->
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val title = obj.get("title")?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val snippet = cleanSnippet(obj.get("snippet")?.asString.orEmpty())
            Result(
                title = title,
                snippet = snippet.ifBlank { "(no summary in the search index)" },
                url = base + articlePath + urlEncode(title.replace(' ', '_'))
            )
        }
        return if (results.isEmpty()) Outcome.Empty(listOf(provider))
        else Outcome.Ok(results.take(MAX_RESULTS), provider)
    }

    private fun duckDuckGo(query: String, provider: String): Outcome {
        val url = "https://api.duckduckgo.com/?format=json&no_html=1&skip_disambig=1&q=" +
            urlEncode(query)
        val json = getJson(url) ?: return Outcome.Failed("no response from api.duckduckgo.com")
        val root = json.asJsonObject
        val results = mutableListOf<Result>()

        val abstractText = root.get("AbstractText")?.asString.orEmpty()
        val abstractUrl = root.get("AbstractURL")?.asString.orEmpty()
        if (abstractText.isNotBlank() && abstractUrl.isNotBlank()) {
            results.add(
                Result(
                    title = root.get("Heading")?.asString?.ifBlank { null } ?: "DuckDuckGo",
                    snippet = cleanSnippet(abstractText),
                    url = abstractUrl
                )
            )
        }
        // RelatedTopics may be a flat list or one level of { Name, Topics: [...] } groups.
        val related = root.getAsJsonArray("RelatedTopics") ?: return finishDuckDuckGo(results, provider)
        for (element in related) {
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: continue
            val text = obj.get("Text")?.asString.orEmpty()
            val firstUrl = obj.get("FirstURL")?.asString.orEmpty()
            if (text.isNotBlank() && firstUrl.isNotBlank()) {
                results.add(
                    Result(
                        title = text.substringBefore(" - ").take(80).ifBlank { "Result" },
                        snippet = cleanSnippet(text),
                        url = firstUrl
                    )
                )
            } else {
                val nested = obj.getAsJsonArray("Topics") ?: continue
                for (inner in nested) {
                    val innerObj = runCatching { inner.asJsonObject }.getOrNull() ?: continue
                    val innerText = innerObj.get("Text")?.asString.orEmpty()
                    val innerUrl = innerObj.get("FirstURL")?.asString.orEmpty()
                    if (innerText.isNotBlank() && innerUrl.isNotBlank()) {
                        results.add(
                            Result(
                                title = innerText.substringBefore(" - ").take(80).ifBlank { "Result" },
                                snippet = cleanSnippet(innerText),
                                url = innerUrl
                            )
                        )
                    }
                }
            }
        }
        return finishDuckDuckGo(results, provider)
    }

    private fun finishDuckDuckGo(results: List<Result>, provider: String): Outcome =
        if (results.isEmpty()) Outcome.Empty(listOf(provider))
        else Outcome.Ok(results.distinctBy { it.url }.take(MAX_RESULTS), provider)

    /**
     * User-supplied endpoint. `{query}` (or `%s`) in the URL template is replaced with the
     * (encoded) query; the response is expected to be SearXNG-compatible, i.e. a JSON object
     * with a `results` array whose entries have `title`, `url` and `content`/`snippet`.
     */
    private fun custom(context: Context, query: String, provider: String): Outcome {
        val template = runCatching { AllSettings.aiSearchUrl.getValue() }.getOrDefault("").trim()
        if (template.isBlank() || template == "https://") {
            return Outcome.Failed("no custom search URL set (Settings -> Experimental)")
        }
        if (!template.startsWith("http://") && !template.startsWith("https://")) {
            return Outcome.Failed("custom search URL must start with http:// or https://")
        }
        val apiKey = runCatching { AllSettings.aiSearchApiKey.getValue() }.getOrDefault("").trim()
        val encoded = urlEncode(query)
        val url = when {
            template.contains("{query}") -> template.replace("{query}", encoded)
            template.contains("%s") -> template.replace("%s", encoded)
            else -> template + encoded
        }

        val json = getJson(url, if (apiKey.isEmpty()) null else apiKey)
            ?: return Outcome.Failed("no response from " + hostOf(url))
        val array = json.asJsonObject.getAsJsonArray("results")
            ?: return Outcome.Failed("custom endpoint did not return a \"results\" array")
        val results = array.mapNotNull { element ->
            val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val title = obj.get("title")?.asString?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val link = obj.get("url")?.asString?.takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: return@mapNotNull null
            val snippet = cleanSnippet(
                obj.get("content")?.asString
                    ?: obj.get("snippet")?.asString
                    ?: obj.get("description")?.asString.orEmpty()
            )
            Result(title, snippet.ifBlank { "(no summary in the search index)" }, link)
        }
        return if (results.isEmpty()) Outcome.Empty(listOf(provider))
        else Outcome.Ok(results.take(MAX_RESULTS), provider)
    }

    // ── Formatting ──────────────────────────────────────────────────────────────────

    /** The block handed to the model - see [TurtleAiPrompt.WEB_SEARCH] for how to use it. */
    @JvmStatic
    fun formatForPrompt(results: List<Result>, provider: String): String = buildString {
        append("Web search results (").append(provider).append("):\n")
        results.forEachIndexed { index, result ->
            append("\n[").append(index + 1).append("] ").append(result.title).append('\n')
            append(result.snippet).append('\n')
            append("Source: ").append(result.url).append('\n')
        }
    }

    /**
     * The block shown to the user when there is no model to synthesize an answer - raw,
     * attributed results rather than a paraphrase nobody can check.
     */
    @JvmStatic
    fun formatForChat(results: List<Result>, shell: TurtleAiLanguage.Shell, query: String): String =
        buildString {
            append(String.format(shell.searchHeader, shortQuery(query)))
            results.forEach { result ->
                append("\n\n\u2022 ").append(result.title)
                if (result.snippet.isNotBlank()) append('\n').append(result.snippet)
                append('\n').append(result.url)
            }
        }

    /** Footer listing where an answer's facts came from, for model-synthesized replies. */
    @JvmStatic
    fun sourcesFooter(results: List<Result>, shell: TurtleAiLanguage.Shell): String =
        if (results.isEmpty()) "" else "\n\n" + shell.sources + ":\n" +
            results.joinToString("\n") { "\u2022 " + it.url }

    private fun shortQuery(query: String): String {
        val clean = query.replace(Regex("\\s+"), " ").trim()
        return if (clean.length <= 80) clean else clean.take(80) + "\u2026"
    }

    // ── HTTP + parsing helpers ──────────────────────────────────────────────────────

    private fun urlEncode(value: String): String =
        runCatching { URLEncoder.encode(value, "UTF-8") }.getOrDefault(value)

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)

    /**
     * GET + parse. Returns null (and logs) on any failure - callers turn that into an
     * [Outcome.Failed] with a reason the user can act on.
     */
    private fun getJson(url: String, bearer: String? = null): com.google.gson.JsonElement? {
        return runCatching {
            val request = UrlManager.createRequestBuilder(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .apply { if (!bearer.isNullOrBlank()) header("Authorization", "Bearer $bearer") }
                .build()
            val client = UrlManager
                .createOkHttpClientBuilder { it.callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Logging.w(TAG, "Search HTTP " + response.code + " for " + hostOf(url))
                    return@runCatching null
                }
                val body = response.body?.string() ?: return@runCatching null
                if (body.isBlank()) return@runCatching null
                JsonParser.parseString(body)
            }
        }.onFailure { t -> Logging.w(TAG, "Search request failed for " + hostOf(url), t) }
            .getOrNull()
    }

    /** MediaWiki and DDG both hand back HTML-ish snippets; the model and the user want plain text. */
    private fun cleanSnippet(raw: String): String {
        val noTags = raw.replace(Regex("<[^>]*>"), " ")
        val unescaped = noTags
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
        val collapsed = unescaped.replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length <= MAX_SNIPPET_CHARS) collapsed
        else collapsed.take(MAX_SNIPPET_CHARS).trimEnd() + "\u2026"
    }
}
