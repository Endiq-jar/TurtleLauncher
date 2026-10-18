package com.endiq.turtlelauncher.feature.background

import android.content.Context
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.endiq.turtlelauncher.InfoDistributor
import com.endiq.turtlelauncher.R
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.utils.path.PathManager
import com.endiq.turtlelauncher.utils.file.FileTools.Companion.mkdirs
import com.endiq.turtlelauncher.utils.image.ImageUtils.Companion.isImage
import net.endiq.launcher.Tools
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.Properties

object BackgroundManager {
    private val FILE_BACKGROUND_PROPERTIES: File = File(PathManager.DIR_DATA, "background.properties")
    const val NULL: String = "null"

    private val defaultProperties: Properties
        get() {
            val properties = Properties()
            properties.setProperty(BackgroundType.MAIN_MENU.name, NULL)
            properties.setProperty(BackgroundType.CUSTOM_CONTROLS.name, NULL)
            properties.setProperty(BackgroundType.IN_GAME.name, NULL)
            return properties
        }

    val properties: Properties
        get() {
            FILE_BACKGROUND_PROPERTIES.apply {
                if (!exists()) {
                    return@apply
                }

                val properties = Properties()
                runCatching {
                    FileReader(this).use { fileReader ->
                        properties.load(fileReader)
                    }
                }.getOrElse { e ->
                    Logging.e("BackgroundManager", Tools.printToString(e))
                    return@apply
                }

                return properties
            }

            return defaultProperties
        }

    @JvmStatic
    fun setBackgroundImage(
        context: Context,
        backgroundType: BackgroundType,
        backgroundView: ImageView,
        callback: CallbackDrawableImageViewTarget.Callback? = null
    ) {
        Glide.with(backgroundView).clear(backgroundView)
        backgroundView.setImageDrawable(
            ContextCompat.getDrawable(context, R.color.background_app)
        )

        val backgroundImage = getBackgroundModel(backgroundType) ?: run {
            callback?.callback(false)
            return
        }

        Glide.with(backgroundView).load(backgroundImage)
            .centerCrop()
            .into(CallbackDrawableImageViewTarget(backgroundView, callback))
    }

    @JvmStatic
    fun clearBackgroundImage(
        backgroundView: ImageView
    ) {
        Glide.with(backgroundView).clear(backgroundView)
        backgroundView.background = null
        backgroundView.setImageDrawable(null)
    }

    /** Stable keys, not translated labels or spinner indices, are persisted. */
    val builtInBackgrounds: Map<String, Int> = linkedMapOf(
        "builtin:turtle_coast" to R.drawable.image_background_turtle_coast,
        "builtin:amethyst_night" to R.drawable.image_background_amethyst_night,
        "builtin:ember_peaks" to R.drawable.image_background_ember_peaks,
        "builtin:error_art" to R.drawable.image_error_background
    )

    @JvmStatic
    fun getBackgroundModel(backgroundType: BackgroundType): Any? {
        val selection = properties.getProperty(backgroundType.name) ?: return null
        return builtInBackgrounds[selection] ?: getBackgroundImage(backgroundType)
    }

    @JvmStatic
    fun hasBackgroundImage(backgroundType: BackgroundType): Boolean =
        getBackgroundModel(backgroundType) != null

    /** Imported files remain supported exactly as before, alongside built-in choices. */
    fun getBackgroundImage(backgroundType: BackgroundType): File? {
        val name = properties.getProperty(backgroundType.name) ?: return null
        if (name == NULL || name.startsWith("builtin:")) return null
        val image = File(PathManager.DIR_BACKGROUND, name)
        if (image.parentFile?.canonicalFile != PathManager.DIR_BACKGROUND.canonicalFile) return null
        return image.takeIf { it.isFile && isImage(it) }
    }

    private fun saveProperties(properties: Properties) {
        PathManager.DIR_BACKGROUND.apply {
            if (!exists()) mkdirs(this)
        }

        runCatching {
            FileWriter(FILE_BACKGROUND_PROPERTIES).use { writer ->
                properties.store(writer, "${InfoDistributor.APP_NAME} Background Properties File")
            }
        }.getOrElse { e -> Logging.e("saveProperties", Tools.printToString(e)) }
    }

    fun saveProperties(map: Map<BackgroundType, String>) {
        val properties = Properties()
        properties.setProperty(
            BackgroundType.MAIN_MENU.name,
            map[BackgroundType.MAIN_MENU] ?: NULL
        )
        properties.setProperty(
            BackgroundType.CUSTOM_CONTROLS.name,
            map[BackgroundType.CUSTOM_CONTROLS] ?: NULL
        )
        properties.setProperty(
            BackgroundType.IN_GAME.name,
            map[BackgroundType.IN_GAME] ?: NULL
        )

        saveProperties(properties)
    }
}