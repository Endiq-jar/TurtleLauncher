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
 * One-time extraction of the bundled logo pack (assets/logo_pack.zip) into
 * [PathManager.DIR_CUSTOM_LOGO], where [LogoPickerFragment]'s existing file-list
 * already picks up anything that lives in that directory - same mechanism as a
 * user-imported logo, just pre-seeded in bulk.
 *
 * Mirrors [DarkModeManager]'s bundled-asset pattern, except the dark GUI pack is
 * installed as a single zip (Minecraft reads it directly); this pack needs its
 * entries extracted individually so each variant shows up as its own selectable
 * logo in the picker grid.
 *
 * The bundled set has been swapped a couple of times (v1: 2000 `variant-NNNN`
 * icons; v2: those replaced by 2048 named icons; v3: back to the 2000
 * `variant-NNNN` icons plus a different 2048-icon "same turtle" set, both
 * pngquant/optipng-optimized). Different bundled packs can share the same
 * `NNNN-slug.png` naming, so which files belong to "the bundled pack" can't be
 * told apart by name alone - [MANIFEST_NAME] instead records exactly which
 * filenames the last extraction wrote, so purging it never touches a file the
 * user imported themselves. The manifest itself lives in the app's internal
 * files dir rather than [PathManager.DIR_CUSTOM_LOGO] - that folder is listed
 * as-is by [FileRecyclerViewCreator] with no dotfile filtering, so anything
 * dropped inside it would show up as a (broken) selectable "logo". [AllSettings
 * .logoPackUnpacked]'s key is still bumped on every pack swap, so stale devices
 * actually re-run this instead of sitting on an old set forever.
 */
object LogoPackUnpacker {
    private const val TAG = "LogoPackUnpacker"

    /** Bundled asset name (lives in src/main/assets). */
    private const val PACK_ASSET_NAME = "logo_pack.zip"

    /** Manifest filename (one bundled-logo filename per line) under context.filesDir. */
    private const val MANIFEST_NAME = "logo_pack_manifest.txt"

    /** Runs the extraction synchronously - call from a background thread/Task. */
    @JvmStatic
    fun unpackIfNeeded(context: Context) {
        if (AllSettings.logoPackUnpacked.getValue()) return

        val destDir = File(PathManager.DIR_CUSTOM_LOGO)
        if (!destDir.exists()) mkdirs(destDir)
        purgePreviousPack(context, destDir)

        val extractedNames = mutableListOf<String>()
        try {
            context.assets.open(PACK_ASSET_NAME).use { assetStream ->
                ZipInputStream(assetStream).use { zip ->
                    var entry = zip.nextEntry
                    val buffer = ByteArray(8 * 1024)
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            // Flatten - the pack is a flat list of NNNN-name.png files,
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
                                extractedNames.add(name)
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            File(context.filesDir, MANIFEST_NAME).writeText(extractedNames.joinToString("\n"))
            AllSettings.logoPackUnpacked.put(true).save()
        } catch (e: Exception) {
            Logging.e(TAG, "Failed to unpack the bundled logo pack", e)
        }
    }

    /** Deletes exactly the files the previous bundled-pack extraction wrote (per its manifest). */
    private fun purgePreviousPack(context: Context, dir: File) {
        val manifest = File(context.filesDir, MANIFEST_NAME)
        if (!manifest.exists()) return
        manifest.readLines()
            .filter { it.isNotBlank() }
            .forEach { name -> File(dir, name).delete() }
        manifest.delete()
    }
}
