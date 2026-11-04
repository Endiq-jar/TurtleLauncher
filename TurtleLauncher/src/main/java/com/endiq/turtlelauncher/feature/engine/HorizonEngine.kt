package com.endiq.turtlelauncher.feature.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Discovery and launch plumbing for the **Horizon** Minecraft engine on Android.
 *
 * Horizon (Minecraft: Java Edition rewritten in Rust, rendered through wgpu/Vulkan) ships as its
 * own Android app - it is a native `NativeActivity` runtime, not a library that can be loaded
 * into this launcher's process. It also prepares its own resources from the user's copy of the
 * game. This object therefore does three honest things and nothing more:
 *
 *  - find an installed Horizon build (the user's explicit pick first, then known package ids);
 *  - hand the launch to it, with advisory extras describing the selected instance;
 *  - report *why* nothing can be launched instead of pretending the engine is bundled.
 */
object HorizonEngine {
    private const val TAG = "HorizonEngine"

    /** Package id used by the upstream Android port of Horizon. */
    const val KNOWN_PACKAGE: String = "dev.horizon.android"

    /**
     * Ordered guesses for Horizon builds. Only ids that are actually installed are ever used;
     * anything else is ignored, so a wrong guess can never produce a broken launch.
     */
    private val KNOWN_PACKAGES = listOf(
        KNOWN_PACKAGE,
        "dev.horizon.launcher",
        "dev.horizon.horizon",
    )

    /**
     * Advisory extras. Horizon ignores extras it does not know, so sending them is safe; if a
     * build understands them it can open the same instance the launcher has selected.
     */
    const val EXTRA_VERSION_NAME: String = "horizon.versionName"
    const val EXTRA_GAME_DIR: String = "horizon.gameDir"

    const val APK_MIME_TYPE: String = "application/vnd.android.package-archive"

    /** Installed Horizon build. */
    data class Installation(
        val packageName: String,
        val label: String,
        val versionName: String?,
    )

    /**
     * Horizon 0.7.7 ships for ARM64, ARMv7, x86 and x86_64, so every device on a modern Android
     * ABI can run it - the launcher just has to hand the installer the matching build. This is a
     * capability check, not a Vulkan probe: Horizon itself reports a driver it cannot use.
     */
    @JvmStatic
    fun isDeviceSupported(): Boolean = Build.SUPPORTED_ABIS.any { abi ->
        abi.equals("arm64-v8a", ignoreCase = true) ||
            abi.equals("armeabi-v7a", ignoreCase = true) ||
            abi.equals("x86", ignoreCase = true) ||
            abi.equals("x86_64", ignoreCase = true)
    }

    private fun packageInfo(context: Context, packageName: String): PackageInfo? =
        runCatching { context.packageManager.getPackageInfo(packageName, 0) }.getOrNull()

    fun isInstalled(context: Context, packageName: String): Boolean =
        packageInfo(context, packageName) != null

    /** Reads label/version for an installed package, or null when it is not installed. */
    fun describe(context: Context, packageName: String): Installation? {
        val info = packageInfo(context, packageName) ?: return null
        val manager = context.packageManager
        val label = info.applicationInfo
            ?.let { applicationInfo ->
                runCatching { manager.getApplicationLabel(applicationInfo).toString() }.getOrNull()
            }
            ?.takeIf { it.isNotBlank() }
            ?: packageName
        return Installation(packageName, label, info.versionName)
    }

    /**
     * The build the launcher should use: the package the user picked in Settings first (even if
     * it is not one of the known ids), then the known ids.
     */
    fun resolve(context: Context): Installation? {
        val picked = AllSettings.horizonEnginePackage.getValue().trim()
        if (picked.isNotEmpty()) describe(context, picked)?.let { return it }
        return KNOWN_PACKAGES.firstNotNullOfOrNull { describe(context, it) }
    }

    /**
     * Launchable apps that look like a Horizon build, for the "choose engine app" picker: either
     * their package or their label mentions Horizon. Used when a build lives under a package id
     * this launcher does not know.
     */
    fun candidates(context: Context): List<Installation> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = runCatching { manager.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
        return activities.asSequence()
            .mapNotNull { it.activityInfo?.packageName }
            .distinct()
            .mapNotNull { describe(context, it) }
            .filter { candidate ->
                candidate.packageName.contains("horizon", ignoreCase = true) ||
                    candidate.label.contains("horizon", ignoreCase = true)
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /** The starting intent for an installed build, carrying the selected instance as extras. */
    fun launchIntent(
        context: Context,
        installation: Installation,
        versionName: String?,
        gameDir: String?,
    ): Intent? {
        val intent = runCatching {
            context.packageManager.getLaunchIntentForPackage(installation.packageName)
        }.getOrNull() ?: return null
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        versionName?.takeIf { it.isNotBlank() }?.let { intent.putExtra(EXTRA_VERSION_NAME, it) }
        gameDir?.takeIf { it.isNotBlank() }?.let { intent.putExtra(EXTRA_GAME_DIR, it) }
        return intent
    }

    /** Starts the engine. Failure is returned, never thrown. */
    fun launch(
        context: Context,
        installation: Installation,
        versionName: String?,
        gameDir: String?,
    ): Result<Unit> = runCatching {
        val intent = launchIntent(context, installation, versionName, gameDir)
            ?: error("${installation.packageName} has no launchable activity")
        context.startActivity(intent)
    }.onFailure { error ->
        Logging.w(TAG, "Could not start the Horizon engine ${installation.packageName}", error)
    }

    /** `ACTION_VIEW` for an APK the user picked, so the system installer can take over. */
    fun installIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, APK_MIME_TYPE)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
}
