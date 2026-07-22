package com.movtery.zalithlauncher.feature.unpack

import android.content.Context
import com.movtery.zalithlauncher.feature.log.Logging.e
import com.movtery.zalithlauncher.utils.CopyDefaultFromAssets.Companion.copyFromAssets
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.Tools

class UnpackSingleFilesTask(val context: Context) : AbstractUnpackTask() {
    override fun isNeedUnpack(): Boolean = true

    override fun run() {
        runCatching {
            copyFromAssets(context)
            Tools.copyAssetFile(context, "resolv.conf", PathManager.DIR_DATA, false)
        }.getOrElse { e("AsyncAssetManager", "Failed to unpack critical components !") }
    }
}