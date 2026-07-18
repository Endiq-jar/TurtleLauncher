package com.movtery.zalithlauncher.launch

import androidx.collection.ArrayMap
import com.movtery.zalithlauncher.InfoDistributor
import com.movtery.zalithlauncher.feature.accounts.AccountUtils
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome
import com.movtery.zalithlauncher.feature.customprofilepath.ProfilePathHome.Companion.getLibrariesHome
import com.movtery.zalithlauncher.feature.version.Version
import com.movtery.zalithlauncher.utils.ZHTools
import com.movtery.zalithlauncher.utils.path.LibPath
import com.movtery.zalithlauncher.utils.path.PathManager
import net.kdt.pojavlaunch.AWTCanvasView
import net.kdt.pojavlaunch.JMinecraftVersionList
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.multirt.Runtime
import net.kdt.pojavlaunch.utils.JSONUtils
import net.kdt.pojavlaunch.value.MinecraftAccount
import org.jackhuang.hmcl.util.versioning.VersionNumber
import java.io.File

class LaunchArgs(
    private val account: MinecraftAccount,
    private val gameDirPath: File,
    private val minecraftVersion: Version,
    private val versionInfo: JMinecraftVersionList.Version,
    private val versionFileName: String,
    private val runtime: Runtime,
    private val launchClassPath: String
) {
    fun getAllArgs(): List<String> {
        val argsList: MutableList<String> = ArrayList()

        argsList.addAll(getJavaArgs())
        argsList.addAll(getMinecraftJVMArgs())
        argsList.addAll(getCdsArgs())

        // ── TurtleLauncher CRASH FIX ────────────────────────────────────────
        // Minecraft's own version JSON (since ~1.19, and especially MC 26.x's
        // NativeLibrariesBootstrap) ships its own "-Djava.library.path=${natives_directory}"
        // / "-Djna.boot.library.path=${natives_directory}" JVM args. Those get appended
        // AFTER the correct ones we add in getJavaArgs(), and since the JVM applies -D
        // system properties strictly in argument order (last one for a given key wins),
        // Mojang's incomplete path — which only contains the writable natives cache dir,
        // NOT PathManager.DIR_NATIVE_LIB where liblwjgl.so / libpojavexec.so / libopenal.so
        // etc. actually live on disk (the app's /lib/arm64 folder) — silently overrides ours.
        // This was the root cause of:
        //   java.lang.UnsatisfiedLinkError: no pojavexec in java.library.path
        //   java.lang.UnsatisfiedLinkError: Failed to locate library: liblwjgl.so
        // on every Minecraft version we tested (1.21.5, 26.1.2, 26.2).
        // Fix: strip every occurrence of these two properties (ours AND Mojang's) from
        // the combined arg list, then re-add our corrected, complete value LAST so it is
        // always the one that wins, regardless of where Mojang's JSON places its own copy.
        argsList.removeAll { it.startsWith("-Djava.library.path=") || it.startsWith("-Djna.boot.library.path=") }
        val nativeLibraryPath = resolveNativeLibraryPath()
        argsList.add("-Djava.library.path=$nativeLibraryPath")
        argsList.add("-Djna.boot.library.path=$nativeLibraryPath")

        argsList.add("-cp")
        argsList.add("${Tools.getLWJGL3ClassPath()}:$launchClassPath")

        if (runtime.javaVersion > 8) {
            argsList.add("--add-exports")
            val pkg: String = versionInfo.mainClass.substring(0, versionInfo.mainClass.lastIndexOf("."))
            argsList.add("$pkg/$pkg=ALL-UNNAMED")
        }

        argsList.add(versionInfo.mainClass)
        argsList.addAll(getMinecraftClientArgs())

        return argsList
    }

    private fun getJavaArgs(): List<String> {
        val argsList: MutableList<String> = ArrayList()

        if (AccountUtils.isOtherLoginAccount(account)) {
            val baseUrl = account.otherBaseUrl ?: ""
            if (baseUrl.contains("auth.mc-user.com") || baseUrl.contains("nide8auth")) {
                val serverId = extractNide8ServerId(baseUrl)
                argsList.add("-javaagent:${LibPath.NIDE_8_AUTH.absolutePath}=$serverId")
                argsList.add("-Dnide8auth.client=true")
            } else if (net.kdt.pojavlaunch.authenticator.BattlyAuthlibManager.isBattlyServer(baseUrl)) {
                // Battly ships its own authlib-injector build rather than the generic bundled
                // one, fetched from their file manifest — see BattlyAuthlibManager for why.
                net.kdt.pojavlaunch.authenticator.BattlyAuthlibManager.addJvmArgumentsIfAvailable(argsList)
            } else {
                argsList.add("-javaagent:${LibPath.AUTHLIB_INJECTOR.absolutePath}=$baseUrl")
            }
        }

        argsList.addAll(getCacioJavaArgs(runtime.javaVersion == 8))

        val is7 = VersionNumber.compare(VersionNumber.asVersion(versionInfo.id ?: "0.0").canonical, "1.12") < 0
        val configFilePath = if (is7) LibPath.LOG4J_XML_1_7 else LibPath.LOG4J_XML_1_12
        argsList.add("-Dlog4j.configurationFile=${configFilePath.absolutePath}")

        // Native library path is no longer added here — see resolveNativeLibraryPath(),
        // which is applied once, last, in getAllArgs() so it can never be silently
        // overridden by Minecraft's own version JSON JVM args.

        return argsList
    }

    /**
     * Builds the corrected, complete `java.library.path` value:
     * the writable per-version natives cache dir (used by LWJGL/JNA/Netty as their
     * extraction/scratch dir) PLUS [PathManager.DIR_NATIVE_LIB] — the app's actual
     * APK native library folder where liblwjgl.so, libpojavexec.so, libopenal.so,
     * libfreetype.so etc. are installed. Both must be present for the game to find
     * every native library it needs.
     */
    private fun resolveNativeLibraryPath(): String {
        val versionSpecificNativesDir = File(PathManager.DIR_CACHE, "natives/${minecraftVersion.getVersionName()}")
        if (!versionSpecificNativesDir.exists()) versionSpecificNativesDir.mkdirs()
        return "${versionSpecificNativesDir.absolutePath}:${PathManager.DIR_NATIVE_LIB}"
    }

    /**
     * TurtleLauncher: JVM class-data-sharing args for faster startup. See
     * [CdsArchiveManager] for the strategy. Never blocks a launch on failure -
     * worst case the JVM logs a warning internally and just doesn't use CDS.
     */
    private fun getCdsArgs(): List<String> {
        val versionJsonFile = File(minecraftVersion.getVersionPath(), "${minecraftVersion.getVersionName()}.json")
        return CdsArchiveManager.resolveArgs(
            javaVersion = runtime.javaVersion,
            runtimeName = runtime.name,
            versionId = minecraftVersion.getVersionName(),
            versionJsonFile = versionJsonFile
        )
    }

    private fun getMinecraftJVMArgs(): Array<String> {
        val versionInfo = Tools.getVersionInfo(minecraftVersion, true)

        val varArgMap: MutableMap<String, String?> = android.util.ArrayMap()
        varArgMap["classpath_separator"] = ":"
        varArgMap["library_directory"] = getLibrariesHome()
        varArgMap["version_name"] = versionInfo.id
        // TurtleLauncher fix: MC 26.x's NativeLibrariesBootstrap calls
        // Files.createDirectories() on subfolders of natives_directory
        // (used for SharedLibraryExtractPath / jna.tmpdir / netty.native.workdir).
        // PathManager.DIR_NATIVE_LIB is the app's read-only APK lib directory
        // (/data/app/.../lib/arm64) — mkdir there throws AccessDeniedException.
        // Use the writable per-version natives cache dir instead, falling back
        // to DIR_NATIVE_LIB only if that cache dir doesn't exist yet.
        val writableNativesDir = File(PathManager.DIR_CACHE, "natives/${versionInfo.id}").apply {
            if (!exists()) mkdirs()
        }
        varArgMap["natives_directory"] = writableNativesDir.absolutePath

        val minecraftArgs: MutableList<String> = java.util.ArrayList()
        versionInfo.arguments?.let {
            fun String.addIgnoreListIfHas(): String {
                if (startsWith("-DignoreList=")) return "$this,$versionFileName.jar"
                return this
            }
            it.jvm?.forEach { arg ->
                if (arg is String) {
                    minecraftArgs.add(arg.addIgnoreListIfHas())
                }
            }
        }
        return JSONUtils.insertJSONValueList(minecraftArgs.toTypedArray<String>(), varArgMap)
    }

    private fun getMinecraftClientArgs(): Array<String> {
        val verArgMap: MutableMap<String, String> = ArrayMap()
        verArgMap["auth_session"] = account.accessToken
        verArgMap["auth_access_token"] = account.accessToken
        verArgMap["auth_player_name"] = account.username
        verArgMap["auth_uuid"] = account.profileId.replace("-", "")
        verArgMap["auth_xuid"] = account.xuid
        verArgMap["assets_root"] = ProfilePathHome.getAssetsHome()
        verArgMap["assets_index_name"] = versionInfo.assets
        verArgMap["game_assets"] = ProfilePathHome.getAssetsHome()
        verArgMap["game_directory"] = gameDirPath.absolutePath
        verArgMap["user_properties"] = "{}"
        verArgMap["user_type"] = "msa"
        verArgMap["version_name"] = versionInfo.inheritsFrom ?: versionInfo.id

        setLauncherInfo(verArgMap)

        val minecraftArgs: MutableList<String> = ArrayList()
        versionInfo.arguments?.apply {
            game.forEach { if (it is String) minecraftArgs.add(it) }
        }

        return JSONUtils.insertJSONValueList(
            splitAndFilterEmpty(
                versionInfo.minecraftArguments ?:
                Tools.fromStringArray(minecraftArgs.toTypedArray())
            ), verArgMap
        )
    }

    private fun setLauncherInfo(verArgMap: MutableMap<String, String>) {
        verArgMap["launcher_name"] = InfoDistributor.LAUNCHER_NAME
        verArgMap["launcher_version"] = ZHTools.getVersionName()
        verArgMap["version_type"] = minecraftVersion.getCustomInfo()
            .takeIf { it.isNotEmpty() && it.isNotBlank() }
            ?: versionInfo.type
    }

    private fun splitAndFilterEmpty(arg: String): Array<String> {
        val list: MutableList<String> = ArrayList()
        arg.split(" ").forEach {
            if (it.isNotEmpty()) list.add(it)
        }
        return list.toTypedArray()
    }

    companion object {

        // ── Java version requirement map ─────────────────────────────────────
        // Strict 4-tier mapping, exactly as specified:
        //
        //   JRE 8   → MC 1.16.5 and below
        //   JRE 17  → MC 1.18 – 1.20.4
        //   JRE 21  → MC 1.20.5 – 1.21.11
        //   JRE 25  → MC 26.1 and later
        //
        // We fully own this mapping instead of trusting Mojang's JSON
        // majorVersion field, since it isn't reliable across the whole
        // version range (26.x still reports 21 even though Java 25 is
        // actually required).
        /**
         * Returns the actual Java major version required for [mcVersionId],
         * fully overriding Mojang's JSON value since it isn't reliable across
         * the whole version range (only used as a last-resort fallback for
         * versions we don't explicitly recognise, e.g. very old alphas).
         */
        @JvmStatic
        fun resolveRequiredJava(mcVersionId: String, jsonMajorVersion: Int): Int {
            // 26.x branch (new versioning scheme, no "1." prefix) — 26.1+ needs Java 25
            if (isMinecraftVersionAtLeast(mcVersionId, 26, 1, 0)) return 25

            // 1.x branch
            if (isMinecraftVersionAtLeast(mcVersionId, 1, 20, 5)) return 21
            if (isMinecraftVersionAtLeast(mcVersionId, 1, 18, 0)) return 17
            if (isMinecraftVersionAtLeast(mcVersionId, 1, 0, 0)) return 8

            // Unrecognised/non-numeric version id (e.g. custom modpack labels,
            // very early 26.0.x betas) — fall back to whatever Mojang's JSON
            // says rather than guessing.
            return if (jsonMajorVersion > 0) jsonMajorVersion else 8
        }

        /**
         * Returns true when [mcVersionId] is at least [major].[minor].[patch].
         *
         * Handles both new-style "26.x.y" and legacy "1.x.y" MC version IDs.
         * Missing patch segment is treated as 0.
         */
        @JvmStatic
        fun isMinecraftVersionAtLeast(
            mcVersionId: String,
            major: Int,
            minor: Int,
            patch: Int = 0
        ): Boolean {
            return try {
                val parts = mcVersionId.split(".")
                val vMajor = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val vMinor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val vPatch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                when {
                    vMajor != major -> vMajor > major
                    vMinor != minor -> vMinor > minor
                    else            -> vPatch >= patch
                }
            } catch (_: Exception) {
                false
            }
        }

        @JvmStatic
        fun getCacioJavaArgs(isJava8: Boolean): List<String> {
            val argsList: MutableList<String> = ArrayList()

            argsList.add("-Djava.awt.headless=false")
            argsList.add("-Dcacio.managed.screensize=" + AWTCanvasView.AWT_CANVAS_WIDTH + "x" + AWTCanvasView.AWT_CANVAS_HEIGHT)
            argsList.add("-Dcacio.font.fontmanager=sun.awt.X11FontManager")
            argsList.add("-Dcacio.font.fontscaler=sun.font.FreetypeFontScaler")
            argsList.add("-Dswing.defaultlaf=javax.swing.plaf.nimbus.NimbusLookAndFeel")
            if (isJava8) {
                argsList.add("-Dawt.toolkit=net.java.openjdk.cacio.ctc.CTCToolkit")
                argsList.add("-Djava.awt.graphicsenv=net.java.openjdk.cacio.ctc.CTCGraphicsEnvironment")
            } else {
                argsList.add("-Dawt.toolkit=com.github.caciocavallosilano.cacio.ctc.CTCToolkit")
                argsList.add("-Djava.awt.graphicsenv=com.github.caciocavallosilano.cacio.ctc.CTCGraphicsEnvironment")
                argsList.add("-javaagent:" + LibPath.CACIO_17_AGENT.getAbsolutePath())
                argsList.add("--add-exports=java.desktop/java.awt=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/java.awt.peer=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/sun.awt.image=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/sun.java2d=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/java.awt.dnd.peer=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/sun.awt=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/sun.awt.event=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/sun.awt.datatransfer=ALL-UNNAMED")
                argsList.add("--add-exports=java.desktop/sun.font=ALL-UNNAMED")
                argsList.add("--add-exports=java.base/sun.security.action=ALL-UNNAMED")
                argsList.add("--add-opens=java.base/java.util=ALL-UNNAMED")
                argsList.add("--add-opens=java.desktop/java.awt=ALL-UNNAMED")
                argsList.add("--add-opens=java.desktop/sun.font=ALL-UNNAMED")
                argsList.add("--add-opens=java.desktop/sun.java2d=ALL-UNNAMED")
                argsList.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
                argsList.add("--add-opens=java.base/java.net=ALL-UNNAMED")
            }

            val cacioClassPath = StringBuilder()
            cacioClassPath.append("-Xbootclasspath/").append(if (isJava8) "p" else "a")
            val cacioFiles = if (isJava8) LibPath.CACIO_8 else LibPath.CACIO_17
            cacioFiles.listFiles()?.onEach {
                if (it.name.endsWith(".jar")) cacioClassPath.append(":").append(it.absolutePath)
            }
            argsList.add(cacioClassPath.toString())

            return argsList
        }

        fun extractNide8ServerId(baseUrl: String): String {
            val cleaned = baseUrl.trimEnd('/')
            val prefixes = listOf(
                "https://auth.mc-user.com:233/",
                "http://auth.mc-user.com:233/",
                "https://nide8auth.com:233/",
                "http://nide8auth.com:233/"
            )
            for (prefix in prefixes) {
                if (cleaned.startsWith(prefix)) {
                    return cleaned.removePrefix(prefix).trimEnd('/')
                }
            }
            val lastSlash = cleaned.lastIndexOf('/')
            return if (lastSlash >= 0 && lastSlash < cleaned.length - 1)
                cleaned.substring(lastSlash + 1)
            else
                cleaned
        }
    }
}
