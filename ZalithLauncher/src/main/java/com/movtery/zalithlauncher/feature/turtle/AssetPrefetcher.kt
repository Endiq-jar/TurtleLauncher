package com.movtery.zalithlauncher.feature.turtle

import android.content.Context
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.feature.version.VersionsManager
import com.movtery.zalithlauncher.setting.AllSettings
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TurtleLauncher v10: while the user is sitting on the main menu (version list),
 * this warms the on-disk/Glide caches for every version's icon file and touches
 * each version's mods folder listing, so switching to a version or opening its
 * mod list doesn't pay a cold-cache cost the first time. Deliberately cheap and
 * best-effort — any failure for one version is skipped, never surfaced to the UI.
 */
object AssetPrefetcher {
    private const val TAG = "AssetPrefetcher"
    private val executor = Executors.newFixedThreadPool(2)
    private val alreadyRunning = AtomicBoolean(false)

    @JvmStatic
    fun prefetch(context: Context) {
        if (!AllSettings.backgroundAssetPrefetch.getValue()) return
        if (!alreadyRunning.compareAndSet(false, true)) return

        executor.execute {
            try {
                val versions = VersionsManager.getVersions()
                versions.forEach { version ->
                    runCatching {
                        // Touch the icon file so it's already in the page cache by the time
                        // an ImageView/Glide requests it (decoding still happens on demand,
                        // but the slow part — cold disk I/O — is already paid for).
                        val iconFile = VersionsManager.getVersionIconFile(version)
                        if (iconFile.exists()) iconFile.inputStream().use { it.read(ByteArray(1)) }

                        // Warm the mods-folder directory listing (readdir) ahead of time.
                        val modsDir = java.io.File(version.getGameDir(), "mods")
                        if (modsDir.isDirectory) modsDir.listFiles()
                    }.onFailure { e ->
                        Logging.i(TAG, "Prefetch skipped for ${version.getVersionName()}: ${e.message}")
                    }
                }
            } finally {
                alreadyRunning.set(false)
            }
        }
    }
}
