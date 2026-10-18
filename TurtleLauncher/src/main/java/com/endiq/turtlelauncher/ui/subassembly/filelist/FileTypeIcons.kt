package com.endiq.turtlelauncher.ui.subassembly.filelist

import com.endiq.turtlelauncher.R
import java.io.File

/**
 * File-type icons for the file browser: the pastel-green `ic_filetype_*` family
 * (Tabler/Flowbite glyphs tinted `@color/file_icon_tint` - see docs/ICONS.md).
 *
 * Rows render these through `setImageResource`, so each type also picks up its
 * own hover/press/click animation style from
 * [com.endiq.turtlelauncher.utils.anim.AnimatedIcon] (zip pops, JSON scans,
 * audio wobbles, 3D turntables...). Unknown types fall back to the plain file.
 */
object FileTypeIcons {

    @JvmStatic
    fun iconResFor(file: File): Int {
        if (file.isDirectory) return R.drawable.ic_filetype_folder
        val name = file.name.lowercase()
        // Disabled mods ("coolmod.jar.disabled") still read as jars.
        val base = name.removeSuffix(".disabled")
        if (base.endsWith(".tar.gz") || base.endsWith(".tar.xz") || base.endsWith(".tar.bz2")) {
            return R.drawable.ic_filetype_zip
        }
        return when (base.substringAfterLast('.', "")) {
            "jar", "litemod" -> R.drawable.ic_filetype_jar
            "zip", "rar", "7z", "tar", "gz", "xz", "bz2", "tgz",
            "mrpack", "mcpack", "mcaddon", "mcworld" -> R.drawable.ic_filetype_zip
            "json", "jsonc", "mcmeta", "bbmodel" -> R.drawable.ic_filetype_json
            "java", "kt", "kts", "js", "ts", "py", "sh", "bat", "xml", "html", "css",
            "glsl", "vsh", "fsh", "gsh", "shader", "accesswidener" -> R.drawable.ic_filetype_code
            "txt", "log", "md", "lang" -> R.drawable.ic_filetype_text
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "svg" -> R.drawable.ic_filetype_image
            "ogg", "mp3", "wav", "flac", "mid", "midi" -> R.drawable.ic_filetype_audio
            "mp4", "mkv", "webm", "mov", "avi" -> R.drawable.ic_filetype_video
            "dat", "nbt", "mca", "mcr", "db", "sqlite" -> R.drawable.ic_filetype_data
            "properties", "cfg", "conf", "toml", "ini", "yml", "yaml", "env", "lock" ->
                R.drawable.ic_filetype_config
            "apk" -> R.drawable.ic_filetype_apk
            "pdf" -> R.drawable.ic_filetype_pdf
            "ttf", "otf", "woff", "woff2" -> R.drawable.ic_filetype_font
            "obj", "fbx", "gltf", "glb" -> R.drawable.ic_filetype_3d
            else -> R.drawable.ic_filetype_file
        }
    }
}
