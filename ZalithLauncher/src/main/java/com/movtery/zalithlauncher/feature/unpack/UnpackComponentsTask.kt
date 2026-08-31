package com.movtery.zalithlauncher.feature.unpack

import android.content.Context
import android.content.res.AssetManager
import com.movtery.zalithlauncher.feature.log.Logging.i
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class UnpackComponentsTask(val context: Context, val component: Components) : AbstractUnpackTask() {
    private lateinit var am: AssetManager
    private lateinit var rootDir: String
    private lateinit var versionFile: File
    private lateinit var input: InputStream
    private var isCheckFailed: Boolean = false

    init {
        runCatching {
            am = context.assets
            rootDir = if (component.privateDirectory) PathManager.DIR_DATA else PathManager.DIR_GAME_HOME
            versionFile = File("$rootDir/${component.component}/version")
            input = am.open("components/${component.component}/version")
        }.getOrElse {
            isCheckFailed = true
        }
    }

    fun isCheckFailed() = isCheckFailed

    override fun isNeedUnpack(): Boolean {
        if (isCheckFailed) return false

        if (!versionFile.exists()) {
            requestEmptyParentDir(versionFile)
            i("Unpack Components", "${component.component}: Pack was installed manually, or does not exist...")
            return true
        } else {
            val fis = FileInputStream(versionFile)
            val release1 = Tools.read(input)
            val release2 = Tools.read(fis)
            if (release1 != release2) {
                requestEmptyParentDir(versionFile)
                return true
            } else {
                i("UnpackPrep", "${component.component}: Pack is up-to-date with the launcher, continuing...")
                return false
            }
        }
    }

    override fun run() {
        listener?.onTaskStart()
        // TurtleLauncher FIX: unlike UnpackJreTask.run()/UnpackSingleFilesTask.run() (both
        // wrap their body in runCatching + log-and-continue), this had ZERO exception
        // handling. InstallableAdapter.startAllTasks() runs every task's run() on a raw
        // background Thread with no try/catch of its own, so any IOException here (a
        // transient asset-copy failure, a locked/missing destination file, etc.) became an
        // uncaught exception on that thread - the app's global
        // Thread.setDefaultUncaughtExceptionHandler (PojavApplication) still catches it and
        // writes latestcrash.txt, but since this crash can happen before the user has ever
        // granted storage access or before PathManager's directories are fully set up on a
        // truly first-ever launch, that crash-log write can itself fail silently, leaving
        // nothing to look at - "crashes without logs". Matching the sibling tasks' pattern:
        // catch, log, and still call onTaskEnd() so the UI doesn't hang on this item forever
        // and the launcher's other setup tasks (JRE, etc.) can still finish and let the user
        // proceed rather than getting stuck at the setup screen entirely.
        runCatching {
            val fileList = am.list("components/${component.component}")
            for (fileName in fileList!!) {
                Tools.copyAssetFile(context, "components/${component.component}/$fileName", "$rootDir/${component.component}", true)
            }
        }.getOrElse { e ->
            com.movtery.zalithlauncher.feature.log.Logging.e("UnpackComponents", "Failed to unpack ${component.component}", e)
        }
        listener?.onTaskEnd()
    }

    private fun requestEmptyParentDir(file: File) {
        file.parentFile!!.apply {
            if (exists() and isDirectory) {
                FileUtils.deleteDirectory(this)
            }
            mkdirs()
        }
    }
}