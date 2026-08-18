package com.movtery.zalithlauncher.feature.turtle.cursor

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.log.Logging
import java.io.File

/**
 * TurtleLauncher: central entry point turning a cursor file on disk (.png, .cur or .ani)
 * into a ready-to-draw Android [Drawable]. Replaces the old bare `Drawable.createFromPath()`
 * call, which silently returned null for .cur/.ani - Android's built-in image codecs have
 * never understood either format.
 */
object CustomCursorLoader {
    /** Extensions this loader knows how to decode, for upload/file-picker validation. */
    @JvmField
    val SUPPORTED_EXTENSIONS = arrayOf("png", "jpg", "jpeg", "webp", "bmp", "gif", "cur", "ani")

    @JvmStatic
    fun load(context: Context, file: File?): Drawable {
        if (file == null || !file.exists()) return fallback(context)

        return runCatching {
            when (file.extension.lowercase()) {
                "ani" -> {
                    val frames = AniDecoder.decode(file.readBytes())
                    if (frames.isNullOrEmpty()) fallback(context) else AnimatedCursorDrawable(frames).apply { start() }
                }
                "cur" -> {
                    val frame = CurIcoDecoder.decode(file.readBytes())
                    if (frame == null) fallback(context) else AnimatedCursorDrawable(listOf(frame))
                }
                else -> Drawable.createFromPath(file.absolutePath) ?: fallback(context)
            }
        }.getOrElse { e ->
            Logging.e("CustomCursorLoader", "Failed to decode custom cursor ${file.name}", e)
            fallback(context)
        }
    }

    /** @return whether [file] is something this loader can turn into a cursor. */
    @JvmStatic
    fun isSupportedCursorFile(file: File?): Boolean {
        if (file == null || file.isDirectory) return false
        val ext = file.extension.lowercase()
        if (ext == "ani" || ext == "cur") return true
        // Fall back to a real bitmap-bounds sniff for png/jpg/webp/etc, same check the
        // rest of the launcher already uses (ImageUtils.isImage), so anything previously
        // accepted there still works here.
        return runCatching {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.path, options)
            options.outWidth != -1 || options.outHeight != -1
        }.getOrDefault(false)
    }

    private fun fallback(context: Context): Drawable =
        ResourcesCompat.getDrawable(context.resources, R.drawable.ic_mouse_pointer, context.theme)!!
}
