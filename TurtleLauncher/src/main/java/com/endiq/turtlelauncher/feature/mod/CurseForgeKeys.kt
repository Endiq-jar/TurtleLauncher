package com.endiq.turtlelauncher.feature.mod

import com.endiq.turtlelauncher.BuildConfig
import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Single place that answers "which CurseForge API key should this request use?".
 *
 * CurseForge's v1 API has no keyless path: every endpoint (search, files, download URL
 * lookup) requires `x-api-key`. The launcher supports two sources, in priority order:
 *
 *  1. The key the person pasted in Settings -> Game -> CurseForge API Key. This wins, so
 *     anyone with their own (higher rate limit, their own Overwolf app) keeps control.
 *  2. A key compiled into the build from the `CURSEFORGE_API_KEY` repository secret
 *     (see build.gradle.kts), exposed here as [BuildConfig.CURSEFORGE_API_KEY].
 *
 * Before this existed, only (1) was consulted and the secret was passed to the workflow
 * and then ignored, so a release APK behaved as if it had no key at all.
 *
 * Security note, same as the Gemini key: anything compiled into an APK can be extracted
 * from it. Treat the built-in key as a convenience for this project's own builds, not as
 * a secret storage mechanism.
 */
object CurseForgeKeys {

    /** Key baked in at build time (empty when the build had no CURSEFORGE_API_KEY). */
    @JvmStatic
    fun builtInApiKey(): String = runCatching { BuildConfig.CURSEFORGE_API_KEY }.getOrDefault("").trim()

    /** The key the user typed in Settings, or empty. */
    @JvmStatic
    fun userApiKey(): String = AllSettings.curseForgeApiKey.getValue().trim()

    /** Key every CurseForge call site should use: the user's, else the built-in one. */
    @JvmStatic
    fun effectiveApiKey(): String = userApiKey().ifBlank { builtInApiKey() }

    @JvmStatic
    fun hasAnyKey(): Boolean = effectiveApiKey().isNotBlank()

    /**
     * True when the key came from the build rather than the user. The settings screen uses
     * this to explain where CurseForge is getting its key from instead of nagging for one.
     */
    @JvmStatic
    fun usingBuiltInKey(): Boolean = userApiKey().isBlank() && builtInApiKey().isNotBlank()
}
