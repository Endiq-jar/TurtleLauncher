package com.endiq.turtlelauncher.feature.turtle

import android.content.Context
import android.os.Process
import androidx.tracing.Trace
import com.endiq.turtlelauncher.feature.accounts.AccountUtils
import com.endiq.turtlelauncher.feature.accounts.AccountsManager
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.version.VersionsManager
import com.endiq.turtlelauncher.renderer.Renderers
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.task.TaskExecutors
import com.endiq.turtlelauncher.utils.http.NetworkUtils
import com.endiq.turtlelauncher.utils.path.PathManager
import net.endiq.launcher.Tools
import net.endiq.launcher.authenticator.listener.DoneListener
import net.endiq.launcher.authenticator.listener.ErrorListener
import net.endiq.launcher.multirt.MultiRTUtils
import net.endiq.launcher.tasks.AsyncMinecraftDownloader
import net.endiq.launcher.tasks.MinecraftDownloader
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object LauncherWarmStart {
    private const val TAG = "LauncherWarmStart"
    private const val WARM_READ_CHUNK = 64 * 1024

    private val threadNumber = AtomicInteger(0)
    private val backgroundThreadFactory = ThreadFactory { runnable ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            runnable.run()
        }, "WarmStart-${threadNumber.incrementAndGet()}")
    }
    private val executor = Executors.newFixedThreadPool(2, backgroundThreadFactory)
    private val alreadyRunning = AtomicBoolean(false)

    /** Guards the Fast Boot game-file preload so only one runs at a time. */
    private val gameFilesRunning = AtomicBoolean(false)

    /** Version id whose game files were already preloaded this process; avoids redoing it. */
    @Volatile private var lastPreloadedVersionId: String? = null

    @JvmStatic
    fun warmStart(context: Context) {
        // Fast Boot game-file preload is gated by fastBoot and runs independently of the
        // smartWarmStart page-cache warming below.
        if (TaskExecutors.isGameSessionActive) return
        warmGameFiles(context)

        if (!AllSettings.smartWarmStart.getValue()) return
        if (!alreadyRunning.compareAndSet(false, true)) return

        executor.execute {
            Trace.beginSection("LauncherWarmStart.warmStart")
            try {
                if (TaskExecutors.isGameSessionActive) return@execute
                warmRendererLibraries()
                if (TaskExecutors.isGameSessionActive) return@execute
                VersionsManager.getCurrentVersion()?.let { warmJavaRuntime(it) }
                if (!TaskExecutors.isGameSessionActive) warmAuthentication(context)
            } finally {
                Trace.endSection()
                alreadyRunning.set(false)
            }
        }
    }

    /**
     * Fast Boot: pre-download / pre-extract the currently selected version's game files
     * (libraries, assets, client jar, natives) in the background while the user is still in the
     * launcher, so that when they press Play the launcher-side preparation is already done and
     * the game starts almost instantly.
     *
     * This reuses the exact [MinecraftDownloader] pipeline the real launch uses (now serialized
     * by a shared lock so the two never touch the same files at once), with a silent
     * [AsyncMinecraftDownloader.DoneListener] so it never triggers a launch. It runs at most once
     * per version per process and only when a network is available and no game is running.
     */
    private fun warmGameFiles(context: Context) {
        if (!AllSettings.fastBoot.getValue()) return
        if (TaskExecutors.isGameSessionActive) return
        if (!NetworkUtils.isNetworkAvailable(context)) return
        val version = VersionsManager.getCurrentVersion() ?: return
        val versionId = version.getVersionName()
        if (versionId.isBlank() || lastPreloadedVersionId == versionId) return
        if (!gameFilesRunning.compareAndSet(false, true)) return

        // Matches the real launch: the listed version may be null for custom/imported versions,
        // in which case the downloader falls back to the local version JSON.
        val listed = AsyncMinecraftDownloader.getListedVersion(versionId)
        Logging.i(TAG, "Fast Boot: preloading game files for $versionId")
        MinecraftDownloader().start(listed, versionId, object : AsyncMinecraftDownloader.DoneListener {
            override fun onDownloadDone() {
                lastPreloadedVersionId = versionId
                gameFilesRunning.set(false)
                Logging.i(TAG, "Fast Boot: game files ready for $versionId")
            }

            override fun onDownloadFailed(t: Throwable?) {
                gameFilesRunning.set(false)
                Logging.i(TAG, "Fast Boot: game-file preload skipped for $versionId: ${t?.message}")
            }
        })
    }

    /** Page-cache-warms the currently selected renderer's main library plus anything
     *  it declares needing dlopen'd alongside it. */
    private fun warmRendererLibraries() {
        runCatching {
            if (!Renderers.isCurrentRendererValid()) return
            val renderer = Renderers.getCurrentRenderer()
            val libNames = mutableListOf(renderer.getRendererLibrary())
            runCatching { libNames.addAll(renderer.getDlopenLibrary().value) }
            libNames.forEach { libName ->
                if (TaskExecutors.isGameSessionActive) return
                if (libName.isNotBlank() && !libName.startsWith("/")) {
                    warmReadFile(File(PathManager.DIR_NATIVE_LIB, libName))
                }
            }
        }.onFailure { e -> Logging.i(TAG, "Renderer warm skipped: ${e.message}") }
    }

    private fun warmJavaRuntime(version: com.endiq.turtlelauncher.feature.version.Version) {
        runCatching {
            val javaDir = version.getJavaDir()
            if (javaDir.isNullOrEmpty() || !javaDir.startsWith(Tools.LAUNCHERPROFILES_RTPREFIX)) return
            val runtimeName = javaDir.removePrefix(Tools.LAUNCHERPROFILES_RTPREFIX)
            if (runtimeName.isEmpty()) return
            val home = MultiRTUtils.getRuntimeHome(runtimeName)
            if (!home.isDirectory) return
            findFileByName(home, "libjvm.so", maxDepth = 5)?.let { warmReadFile(it) }
        }.onFailure { e -> Logging.i(TAG, "Runtime warm skipped: ${e.message}") }
    }

    /** Silent, best-effort proactive MSA token refresh - see class doc for why this is
     *  safe to fire-and-forget here. */
    private fun warmAuthentication(context: Context) {
        runCatching {
            val account = AccountsManager.currentAccount ?: return
            if (!AccountUtils.isMicrosoftAccount(account)) return
            AccountUtils.microsoftLogin(
                context,
                account,
                DoneListener { /* silent: preLaunch() re-checks for real at launch time */ },
                ErrorListener { e -> Logging.i(TAG, "Background auth warm skipped: ${e.message}") }
            )
        }.onFailure { e -> Logging.i(TAG, "Auth warm skipped: ${e.message}") }
    }

    private fun findFileByName(root: File, targetName: String, maxDepth: Int): File? {
        if (maxDepth < 0 || TaskExecutors.isGameSessionActive) return null
        val children = root.listFiles() ?: return null
        for (child in children) {
            if (child.isFile && child.name == targetName) return child
        }
        for (child in children) {
            if (child.isDirectory) {
                findFileByName(child, targetName, maxDepth - 1)?.let { return it }
            }
        }
        return null
    }

    private fun warmReadFile(file: File) {
        if (!file.isFile) return
        runCatching {
            file.inputStream().use { input ->
                val buffer = ByteArray(WARM_READ_CHUNK)
                while (!TaskExecutors.isGameSessionActive && input.read(buffer) >= 0) {
                    // Stop paging large runtime/renderer libraries into RAM once Play wins.
                }
            }
        }
    }
}
