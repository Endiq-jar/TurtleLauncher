package com.movtery.zalithlauncher.utils.skin

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.UrlManager
import com.movtery.zalithlauncher.utils.stringutils.StringUtils
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.utils.DownloadUtils
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

class SkinFileDownloader {
    private val mClient = UrlManager.createOkHttpClient()

    /**
     * 尝试下载yggdrasil皮肤
     *
     * TurtleLauncher: made defensive - a profile with no skin (empty/missing `properties`),
     * or one whose `properties` array doesn't lead with the `textures` entry, used to throw
     * here (IndexOutOfBounds / NPE on `properties.get(0)` / missing `SKIN`). That's the
     * normal case for any account that simply hasn't set a custom skin, so it should be a
     * quiet no-op (keep the default skin) rather than an exception. Only a genuinely
     * malformed response that fails to parse as JSON still throws, which the caller's
     * try/catch logs.
     */
    @Throws(Exception::class)
    fun yggdrasil(url: String, skinFile: File, uuid: String) {
        val profileJson = DownloadUtils.downloadString("${url.removeSuffix("/")}/session/minecraft/profile/$uuid")
        val profileObject = Tools.GLOBAL_GSON.fromJson(profileJson, JsonObject::class.java)

        val properties = profileObject?.get("properties") as? JsonArray ?: return
        // Find the "textures" property by name instead of assuming index 0 - the array order
        // isn't part of the Yggdrasil contract, only that one entry is named "textures".
        val texturesProperty = (0 until properties.size())
            .mapNotNull { properties.get(it) as? JsonObject }
            .firstOrNull { it.get("name")?.asString == "textures" }
            ?: return

        val rawValue = texturesProperty.get("value")?.asString ?: return
        val value = StringUtils.decodeBase64(rawValue)

        val valueObject = Tools.GLOBAL_GSON.fromJson(value, JsonObject::class.java)
        val textures = valueObject?.get("textures") as? JsonObject ?: return
        val skin = textures.get("SKIN") as? JsonObject ?: return
        val skinUrl = skin.get("url")?.asString ?: return

        downloadSkin(skinUrl, skinFile)
    }

    private fun downloadSkin(url: String, skinFile: File) {
        skinFile.parentFile?.apply {
            if (!exists()) mkdirs()
        }

        val request = Request.Builder()
            .url(url)
            .build()

        mClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RuntimeException("Unexpected code $response")
            }

            try {
                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(skinFile).use { outputStream ->
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } catch (e: Exception) {
                Logging.e("SkinFileDownloader", "Failed to download skin file", e)
            }
        }
    }
}