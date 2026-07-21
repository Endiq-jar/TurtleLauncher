package com.movtery.zalithlauncher.renderer

import android.content.Context
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.launch.LaunchArgs
import com.movtery.zalithlauncher.renderer.renderers.HolyGL4ESRenderer
import com.movtery.zalithlauncher.setting.AllSettings

/**
 * Picks the next renderer to try after the previous launch failed, per the fallback chain
 * in [RendererCatalog] (Holy GL4ES -> Krypton Wrapper -> VirGL -> Zink -> Freedreno).
 *
 * Honest limitation, stated up front: there is no way to catch a renderer failure and
 * retry within the SAME launch attempt - by the time a bad native renderer library has
 * crashed the process, that process is gone. What this actually does is detect, at the
 * START of the next launch, that the previous one never exited cleanly, and switch to the
 * next fallback candidate for that retry - "continue with the next compatible renderer"
 * across attempts, not a mid-crash recovery. See where this is called from LaunchGame.kt.
 */
object RendererFallbackManager {
    private const val TAG = "RendererFallbackManager"

    /**
     * @return the UUID to switch to for this launch, or null if no switch is needed
     * (either the previous launch succeeded, or the user already picked a different
     * renderer than the one that failed - in which case that's their call, not ours to
     * override).
     */
    fun resolveForThisLaunch(context: Context, mcVersion: String, hasSodiumOrEmbeddium: Boolean): String? {
        if (!AllSettings.lastLaunchFailed.getValue()) return null

        val currentUuid = AllSettings.renderer.getValue()
        val failedUuid = AllSettings.lastLaunchRendererId.getValue()
        if (currentUuid != failedUuid) {
            // User already switched renderer themselves since the failed attempt -
            // that's a deliberate choice, don't second-guess it.
            AllSettings.lastLaunchFailed.put(false).save()
            return null
        }

        val failedRenderer = Renderers.getCompatibleRenderers(context)
            .second.find { it.getUniqueIdentifier() == failedUuid }
        val failedId = failedRenderer?.getRendererId()

        val next = nextCandidate(context, failedId, mcVersion, hasSodiumOrEmbeddium)
        if (next == null) {
            Logging.w(TAG, "Renderer fallback chain exhausted after ${failedRenderer?.getRendererName()} failed - leaving renderer as-is")
            return null
        }

        Logging.i(TAG, "Previous launch with ${failedRenderer?.getRendererName()} didn't exit cleanly - falling back to $next for this attempt")
        return next
    }

    /** Walks the chain starting after [afterRendererId], applying the Holy GL4ES gating
     *  condition from the spec (skip it if the version is 1.21.4+, or Sodium/Embeddium is
     *  installed - GL4ES's ES2 translation isn't a good match for either). Returns a UUID. */
    private fun nextCandidate(context: Context, afterRendererId: String?, mcVersion: String, hasSodiumOrEmbeddium: Boolean): String? {
        val chain = RendererCatalog.fallbackChain()
        val startIndex = chain.indexOf(afterRendererId).let { if (it == -1) -1 else it }

        for (i in (startIndex + 1) until chain.size) {
            val candidateId = chain[i]
            if (candidateId == HolyGL4ESRenderer.ID) {
                val versionTooNew = LaunchArgs.isMinecraftVersionAtLeast(mcVersion, 1, 21, 4)
                if (versionTooNew || hasSodiumOrEmbeddium) continue
            }
            return uuidFor(context, candidateId)
        }
        return null
    }

    private fun uuidFor(context: Context, rendererId: String): String? =
        Renderers.getCompatibleRenderers(context)
            .second.find { it.getRendererId() == rendererId }?.getUniqueIdentifier()
}
