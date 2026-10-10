package com.endiq.turtlelauncher.feature

import android.content.Context
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import net.endiq.launcher.Tools
import java.io.File

/**
 * Dark Mode: installs the bundled "Turtle Dark GUI" resource pack into the current
 * version's `resourcepacks/` folder and adds/removes it from the options.txt
 * `resourcePacks` list so every in-game GUI/menu/container renders darker.
 *
 * The pack is GUI/UI-only (menus, buttons, container panels, tiled backgrounds) and
 * never touches world/block/item textures, so enabling it can't affect gameplay.
 *
 * Minecraft only reads `resourcePacks` when it (re)loads resources, so a toggle takes
 * effect on the next launch or an in-game resource reload (F3+T). [applyOnLaunch]
 * reconciles options.txt with the saved preference on every start so the state is
 * always correct even if the running game rewrote options.txt.
 */
object DarkModeManager {
    private const val TAG = "DarkModeManager"

    /** Bundled asset name (lives in src/main/assets). */
    private const val PACK_ASSET_NAME = "turtle_dark_gui.zip"

    /** File name inside `<gamedir>/resourcepacks`. */
    const val PACK_FILE_NAME = "turtle_dark_gui.zip"

    /** The options.txt `resourcePacks` entry for a file-based pack. */
    private val PACK_ID = "file/$PACK_FILE_NAME"

    private fun getResourcePacksDir(): File =
        File(MCOptions.getGameDir(), "resourcepacks")

    /** Copy the bundled pack into the version's resourcepacks folder (idempotent). */
    private fun ensurePackInstalled(context: Context): Boolean {
        return runCatching {
            val dir = getResourcePacksDir()
            dir.mkdirs()
            Tools.copyAssetFile(context, PACK_ASSET_NAME, dir.absolutePath, PACK_FILE_NAME, true)
            true
        }.getOrElse { e ->
            Logging.e(TAG, "Failed to install the dark GUI resource pack", e)
            false
        }
    }

    /**
     * Add or remove the dark GUI pack from the current options.txt `resourcePacks`
     * value. When enabling, the pack is inserted before "vanilla" so vanilla stays the
     * base; every other enabled pack keeps its existing order.
     */
    private fun applyToOptions(enabled: Boolean) {
        val raw = MCOptions.get("resourcePacks") ?: "[]"
        val list = parsePackList(raw).toMutableList()

        if (enabled) {
            if (PACK_ID in list) return
            val vanillaIndex = list.indexOf("vanilla")
            if (vanillaIndex >= 0) list.add(vanillaIndex, PACK_ID) else list.add(PACK_ID)
        } else {
            if (PACK_ID !in list) return
            list.removeAll { it == PACK_ID }
        }

        val serialized = list.joinToString(",", "[", "]") { "\"$it\"" }
        MCOptions.set("resourcePacks", serialized)
        MCOptions.save()
    }

    /** Parse a Minecraft `resourcePacks` options.txt value into a list of pack ids. */
    private fun parsePackList(raw: String): List<String> =
        raw.trim()
            .removePrefix("[").removeSuffix("]")
            .split(',')
            .map { it.trim().trim('"') }
            .filter { it.isNotEmpty() }

    /**
     * Toggle dark mode from the UI: persist the preference, make sure the pack is
     * installed when enabling, and update options.txt so the change is picked up on the
     * next resource reload (F3+T) or launch.
     */
    @JvmStatic
    fun setEnabled(context: Context, enabled: Boolean) {
        AllSettings.darkModeEnabled.put(enabled).save()
        if (enabled && !ensurePackInstalled(context)) return
        applyToOptions(enabled)
    }

    /**
     * Reconcile the installed pack + options.txt with the saved preference. Call this
     * once at game start (before the JVM reads options.txt).
     */
    @JvmStatic
    fun applyOnLaunch(context: Context) {
        val enabled = AllSettings.darkModeEnabled.getValue()
        if (enabled) ensurePackInstalled(context)
        applyToOptions(enabled)
    }
}
