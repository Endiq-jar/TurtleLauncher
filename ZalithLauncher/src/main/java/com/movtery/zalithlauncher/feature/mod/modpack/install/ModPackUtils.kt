package com.movtery.zalithlauncher.feature.mod.modpack.install

import android.app.Activity
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.utils.LauncherProfiles
import com.movtery.zalithlauncher.feature.download.item.ModLoaderWrapper
import com.movtery.zalithlauncher.feature.log.Logging
<<<<<<< HEAD
import com.movtery.zalithlauncher.feature.mod.models.CurseForgeManifest
=======
>>>>>>> 9c5f2f7990cd79a948e952a67446595d42eab51e
import com.movtery.zalithlauncher.feature.mod.models.MCBBSPackMeta
import com.movtery.zalithlauncher.utils.runtime.SelectRuntimeUtils
import net.kdt.pojavlaunch.JavaGUILauncherActivity
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex
import java.io.File
import java.util.zip.ZipFile

class ModPackUtils {
    companion object {
        @JvmStatic
        fun determineModpack(modpack: File): ModPackInfo {
            val zipName = modpack.name
            val suffix = zipName.substring(zipName.lastIndexOf('.'))
            runCatching {
                ZipFile(modpack).use { modpackZipFile ->
                    if (suffix == ".zip") {
                        val mcbbsEntry = modpackZipFile.getEntry("mcbbs.packmeta")
                        if (mcbbsEntry != null) {
                            val mcbbsPackMeta = Tools.GLOBAL_GSON.fromJson(
                                Tools.read(modpackZipFile.getInputStream(mcbbsEntry)),
                                MCBBSPackMeta::class.java
                            )
                            if (verifyMCBBSPackMeta(mcbbsPackMeta)) return ModPackInfo(mcbbsPackMeta.name, ModPackEnum.MCBBS)
                        }
<<<<<<< HEAD

                        val manifestEntry = modpackZipFile.getEntry("manifest.json")
                        if (manifestEntry != null) {
                            val manifest = Tools.GLOBAL_GSON.fromJson(
                                Tools.read(modpackZipFile.getInputStream(manifestEntry)),
                                CurseForgeManifest::class.java
                            )
                            if (verifyCurseForgeManifest(manifest)) return ModPackInfo(manifest.name, ModPackEnum.CURSEFORGE)
                        }

                        // Doesn't match a known modpack manifest, but it's still a real zip -
                        // treat it as a plain folder-structure import (mods/, config/,
                        // resourcepacks/, etc. at the zip root, extracted as-is). No loader
                        // detection possible without a manifest, so the person picks one
                        // afterward same as any manually-created instance.
                        return ModPackInfo(null, ModPackEnum.GENERIC_ZIP)
=======
>>>>>>> 9c5f2f7990cd79a948e952a67446595d42eab51e
                    } else if (suffix == ".mrpack") {
                        val entry = modpackZipFile.getEntry("modrinth.index.json")
                        if (entry != null) {
                            val modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                                Tools.read(modpackZipFile.getInputStream(entry)),
                                ModrinthIndex::class.java
                            )
                            if (verifyModrinthIndex(modrinthIndex)) return ModPackInfo(modrinthIndex.name, ModPackEnum.MODRINTH)
                        }
                    }
                }
            }.onFailure { e ->
                Logging.e("determineModpack", "There was a problem checking the ModPack", e)
            }

            return ModPackInfo(null, ModPackEnum.UNKNOWN)
        }

        @JvmStatic
        fun verifyModrinthIndex(modrinthIndex: ModrinthIndex): Boolean { //检测是否为modrinth整合包(通过modrinth.index.json内的数据进行判断)
            if ("minecraft" != modrinthIndex.game) return false
            if (modrinthIndex.formatVersion != 1) return false
            return modrinthIndex.dependencies != null
        }

        fun verifyMCBBSPackMeta(mcbbsPackMeta: MCBBSPackMeta): Boolean { //检测是否为MCBBS整合包(通过mcbbs.packmeta内的数据进行判断)
            if ("minecraftModpack" != mcbbsPackMeta.manifestType) return false
            if (mcbbsPackMeta.manifestVersion != 2) return false
            if (mcbbsPackMeta.addons == null) return false
            if (mcbbsPackMeta.addons[0].id == null) return false
            return (mcbbsPackMeta.addons[0].version != null)
        }

<<<<<<< HEAD
        /** Detects a CurseForge modpack export by its manifest.json (checked *after* MCBBS's
         *  mcbbs.packmeta, since both formats use plain .zip - a file can only be one or the
         *  other, this is just which signature file to look for). */
        @JvmStatic
        fun verifyCurseForgeManifest(manifest: CurseForgeManifest): Boolean {
            if ("minecraftModpack" != manifest.manifestType) return false
            if (manifest.minecraft?.version == null) return false
            return true
        }

=======
>>>>>>> 9c5f2f7990cd79a948e952a67446595d42eab51e
        @JvmStatic
        @Throws(Throwable::class)
        fun startModLoaderInstall(modLoader: ModLoaderWrapper, activity: Activity, modInstallFile: File, customName: String) {
            modLoader.getInstallationIntent(activity, modInstallFile, customName)?.let { installIntent ->
                SelectRuntimeUtils.selectRuntime(activity, activity.getString(R.string.version_install_new_modloader, modLoader.modLoader.loaderName)) { jreName ->
                    LauncherProfiles.generateLauncherProfiles()
                    installIntent.putExtra(JavaGUILauncherActivity.EXTRAS_JRE_NAME, jreName)
                    activity.startActivity(installIntent)
                }
            }
        }
    }

    enum class ModPackEnum {
<<<<<<< HEAD
        UNKNOWN, MCBBS, MODRINTH, CURSEFORGE, GENERIC_ZIP
=======
        UNKNOWN, MCBBS, MODRINTH
>>>>>>> 9c5f2f7990cd79a948e952a67446595d42eab51e
    }
}
