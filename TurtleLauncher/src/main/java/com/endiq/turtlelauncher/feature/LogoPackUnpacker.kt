package com.endiq.turtlelauncher.feature

import android.content.Context
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.file.FileTools.Companion.mkdirs
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * One-time extraction of the bundled logo pack (assets/logo_pack.zip, 2000 PNG
 * variants) into [PathManager.DIR_CUSTOM_LOGO], where [LogoPickerFragment]'s
 * existing file-list already picks up anything that lives in that directory -
 * same mechanism as a user-imported logo, just pre-seeded in bulk.
 *
 * Mirrors [DarkModeManager]'s bundled-asset pattern, except the dark GUI pack is
 * installed as a single zip (Minecraft reads it directly); this pack needs its
 * entries extracted individually so each variant shows up as its own selectable
 * logo in the picker grid.
 */
object LogoPackUnpacker {
    private const val TAG = "LogoPackUnpacker"

    /** Bundled asset name (lives in src/main/assets). */
    private const val PACK_ASSET_NAME = "logo_pack.zip"

    /** Runs the extraction synchronously - call from a background thread/Task. */
    @JvmStatic
    fun unpackIfNeeded(context: Context) {
        if (AllSettings.logoPackUnpacked.getValue()) return

        val destDir = File(PathManager.DIR_CUSTOM_LOGO)
        if (!destDir.exists()) mkdirs(destDir)

        try {
            context.assets.open(PACK_ASSET_NAME).use { assetStream ->
                ZipInputStream(assetStream).use { zip ->
                    var entry = zip.nextEntry
                    val buffer = ByteArray(8 * 1024)
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Flatten - the pack is a flat list of variant-NNNN.png files,
                            // but guard against path traversal from a malformed entry name.
                            val name = File(entry.name).name
                            if (name.isNotEmpty()) {
                                val outFile = File(destDir, name)
                                FileOutputStream(outFile).use { out ->
                                    var read: Int
                                    while (zip.read(buffer).also { read = it } != -1) {
                                        out.write(buffer, 0, read)
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            AllSettings.logoPackUnpacked.put(true).save()
        } catch (e: Exception) {
            Logging.e(TAG, "Failed to unpack the bundled logo pack", e)
        }
    }
}
