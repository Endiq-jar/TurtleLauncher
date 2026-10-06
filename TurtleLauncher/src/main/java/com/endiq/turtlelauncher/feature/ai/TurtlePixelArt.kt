package com.endiq.turtlelauncher.feature.ai

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import java.util.Random

/**
 * Turtle's built-in image generator. Fully on-device and offline: no Gemini, no web, no key.
 *
 * It is a procedural Minecraft-style pixel-art renderer, not a diffusion model: the prompt is
 * read for a time of day (sunset, night, ...), a biome (snow, desert, nether, ocean, ...) and
 * subjects (creeper, steve, zombie, pig), and a scene is composed from blocks - sky, sun or
 * moon, clouds, hills, trees, a layered ground with ores, and the subjects standing on it.
 * Every call uses a fresh random seed, so the same prompt gives a different picture each time.
 */
object TurtlePixelArt {

    class Result(val bytes: ByteArray, val mimeType: String, val summary: String)

    private enum class Time { DAY, SUNSET, SUNRISE, NIGHT }
    private enum class Biome { GRASS, SNOW, DESERT, NETHER, OCEAN }

    private const val UPSCALE = 10

    /** Canvas of the finished picture is (grid * UPSCALE); portrait by default (wallpaper). */
    @JvmStatic
    fun generate(prompt: String): Result? = runCatching { render(prompt) }.getOrNull()

    // --- prompt reading -------------------------------------------------------------------

    private fun has(text: String, vararg words: String) = words.any { text.contains(it) }

    private fun render(prompt: String): Result {
        val p = prompt.lowercase()
        val rnd = Random(System.nanoTime() xor prompt.hashCode().toLong())

        val landscape = has(p, "landscape", "wide", "desktop", "pc wallpaper", "banner", "horizontal")
        val gw = if (landscape) 192 else 96
        val gh = if (landscape) 108 else 208

        val time = when {
            has(p, "night", "moon", "stars", "dark", "midnight") -> Time.NIGHT
            has(p, "sunset", "dusk", "evening", "golden hour") -> Time.SUNSET
            has(p, "sunrise", "dawn", "morning") -> Time.SUNRISE
            else -> Time.DAY
        }
        val biome = when {
            has(p, "nether", "lava", "hell", "fire") -> Biome.NETHER
            has(p, "snow", "winter", "ice", "frozen", "cold") -> Biome.SNOW
            has(p, "desert", "sand", "dune", "cactus") -> Biome.DESERT
            has(p, "ocean", "sea", "lake", "water", "beach", "island") -> Biome.OCEAN
            else -> Biome.GRASS
        }
        val subjects = ArrayList<String>()
        // Ordered by where they appear in the prompt, so "steve and a creeper" keeps that order.
        listOf("creeper", "steve", "zombie", "pig").map { it to p.indexOf(it) }
            .filter { it.second >= 0 }.sortedBy { it.second }.forEach { subjects.add(it.first) }
        val wantTrees = has(p, "tree", "forest", "wood", "oak", "jungle") ||
            (biome == Biome.GRASS && subjects.isEmpty())

        val px = IntArray(gw * gh)
        val groundTop = (gh * if (landscape) 0.74f else 0.72f).toInt()

        paintSky(px, gw, gh, groundTop, time, biome, rnd)
        paintHills(px, gw, groundTop, time, biome, rnd)
        paintGround(px, gw, gh, groundTop, biome, rnd)
        if (wantTrees && biome != Biome.OCEAN) paintTrees(px, gw, groundTop, biome, rnd, subjects.size)
        if (biome == Biome.DESERT && has(p, "cactus")) paintCactus(px, gw, groundTop, rnd)

        val scale = minOf(gw / 32, (groundTop * 0.5f / 20f).toInt()).coerceAtLeast(2)
        subjects.take(3).forEachIndexed { i, name ->
            val slot = (i + 1f) / (subjects.size.coerceAtMost(3) + 1f)
            val sprite = spriteFor(name) ?: return@forEachIndexed
            val sx = (gw * slot).toInt() - sprite.width * scale / 2
            val sy = groundTop - sprite.height * scale + 1
            drawSprite(px, gw, gh, sprite, sx, sy, scale, rnd)
        }

        val small = Bitmap.createBitmap(px, gw, gh, Bitmap.Config.ARGB_8888)
        val big = Bitmap.createScaledBitmap(small, gw * UPSCALE, gh * UPSCALE, false)
        val out = ByteArrayOutputStream()
        big.compress(Bitmap.CompressFormat.PNG, 100, out)

        val what = buildString {
            append(time.name.lowercase()).append(" ").append(biome.name.lowercase())
            if (subjects.isNotEmpty()) append(" with ").append(subjects.joinToString(" and "))
        }
        return Result(out.toByteArray(), "image/png", what)
    }

    // --- colour helpers -------------------------------------------------------------------

    private fun rgb(r: Int, g: Int, b: Int) = Color.rgb(r, g, b)

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    private fun shade(c: Int, delta: Int) = rgb(
        (Color.red(c) + delta).coerceIn(0, 255),
        (Color.green(c) + delta).coerceIn(0, 255),
        (Color.blue(c) + delta).coerceIn(0, 255)
    )

    private fun put(px: IntArray, gw: Int, x: Int, y: Int, color: Int) {
        if (x < 0 || y < 0 || x >= gw) return
        val i = y * gw + x
        if (i in px.indices) px[i] = color
    }

    private fun rect(px: IntArray, gw: Int, x: Int, y: Int, w: Int, h: Int, color: Int) {
        for (yy in y until y + h) for (xx in x until x + w) put(px, gw, xx, yy, color)
    }

    // --- sky ------------------------------------------------------------------------------

    private fun paintSky(px: IntArray, gw: Int, gh: Int, groundTop: Int, time: Time, biome: Biome, rnd: Random) {
        var top: Int
        var bottom: Int
        when (time) {
            Time.DAY -> { top = rgb(70, 130, 220); bottom = rgb(170, 215, 250) }
            Time.SUNSET -> { top = rgb(60, 40, 110); bottom = rgb(255, 150, 70) }
            Time.SUNRISE -> { top = rgb(110, 130, 200); bottom = rgb(255, 205, 140) }
            Time.NIGHT -> { top = rgb(8, 10, 30); bottom = rgb(30, 40, 90) }
        }
        if (biome == Biome.NETHER) { top = rgb(60, 10, 10); bottom = rgb(170, 40, 20) }

        val bands = 14
        for (y in 0 until groundTop + 12) {
            val band = (y.toFloat() / groundTop * bands).toInt().coerceAtMost(bands - 1)
            val c = lerp(top, bottom, band / (bands - 1f))
            for (x in 0 until gw) put(px, gw, x, y, c)
        }

        val sunX = (gw * (0.25f + rnd.nextFloat() * 0.5f)).toInt()
        val horizon = (groundTop * 0.62f).toInt()
        when {
            biome == Biome.NETHER -> {}
            time == Time.NIGHT -> {
                repeat(gw * groundTop / 70) {
                    val sx = rnd.nextInt(gw); val sy = rnd.nextInt((groundTop * 0.8f).toInt())
                    put(px, gw, sx, sy, if (rnd.nextInt(5) == 0) rgb(255, 245, 190) else rgb(230, 235, 255))
                }
                val mx = sunX; val my = (groundTop * 0.18f).toInt()
                rect(px, gw, mx, my, 9, 9, rgb(236, 238, 245))
                rect(px, gw, mx + 2, my + 2, 2, 2, rgb(205, 208, 220))
                rect(px, gw, mx + 5, my + 5, 3, 2, rgb(205, 208, 220))
            }
            else -> {
                val sy = when (time) {
                    Time.DAY -> (groundTop * 0.14f).toInt()
                    else -> horizon
                }
                val glow = if (time == Time.DAY) rgb(255, 245, 190) else rgb(255, 190, 110)
                rect(px, gw, sunX - 3, sy - 3, 16, 16, lerp(glow, px[(sy.coerceAtLeast(0)) * gw + sunX.coerceIn(0, gw - 1)], 0.55f))
                rect(px, gw, sunX, sy, 10, 10, rgb(255, 235, 90))
                rect(px, gw, sunX + 1, sy + 1, 8, 8, rgb(255, 248, 170))
            }
        }

        if (biome != Biome.NETHER && time != Time.NIGHT) {
            val cloud = when (time) {
                Time.SUNSET -> rgb(255, 205, 200)
                Time.SUNRISE -> rgb(255, 235, 225)
                else -> rgb(255, 255, 255)
            }
            repeat(3 + rnd.nextInt(3)) {
                val cx = rnd.nextInt(gw); val cy = 8 + rnd.nextInt((groundTop * 0.45f).toInt().coerceAtLeast(10))
                val w = 12 + rnd.nextInt(10)
                rect(px, gw, cx, cy, w, 3, cloud)
                rect(px, gw, cx + 3, cy - 2, w - 7, 2, cloud)
                rect(px, gw, cx + 1, cy + 3, w - 3, 1, shade(cloud, -25))
            }
        }
    }

    // --- hills ----------------------------------------------------------------------------

    private fun heights(gw: Int, period: Int, rnd: Random): IntArray {
        val points = gw / period + 3
        val lattice = FloatArray(points) { rnd.nextFloat() }
        return IntArray(gw) { x ->
            val i = x / period
            val t = (x % period) / period.toFloat()
            val s = t * t * (3 - 2 * t)
            ((lattice[i] * (1 - s) + lattice[i + 1] * s) * 100).toInt()
        }
    }

    private fun paintHills(px: IntArray, gw: Int, groundTop: Int, time: Time, biome: Biome, rnd: Random) {
        val base = when (biome) {
            Biome.SNOW -> rgb(215, 228, 240)
            Biome.DESERT -> rgb(215, 180, 110)
            Biome.NETHER -> rgb(110, 25, 30)
            Biome.OCEAN -> rgb(90, 140, 110)
            Biome.GRASS -> rgb(70, 140, 70)
        }
        val haze = when (time) {
            Time.NIGHT -> rgb(25, 32, 70)
            Time.SUNSET -> rgb(200, 110, 110)
            Time.SUNRISE -> rgb(230, 175, 160)
            Time.DAY -> rgb(150, 190, 225)
        }
        for (layer in 0 until 3) {
            val h = heights(gw, 14 + layer * 6, rnd)
            val amp = 0.16f - layer * 0.03f
            val lift = (groundTop * (0.09f + layer * 0.06f)).toInt()
            val color = lerp(base, haze, 0.62f - layer * 0.25f)
            for (x in 0 until gw) {
                val top = groundTop - lift - (h[x] / 100f * groundTop * amp).toInt()
                for (y in top until groundTop) put(px, gw, x, y, shade(color, if ((x + y) % 7 == 0) -6 else 0))
            }
        }
    }

    // --- ground ---------------------------------------------------------------------------

    private fun paintGround(px: IntArray, gw: Int, gh: Int, groundTop: Int, biome: Biome, rnd: Random) {
        val top = when (biome) {
            Biome.GRASS -> rgb(95, 160, 55)
            Biome.SNOW -> rgb(245, 248, 252)
            Biome.DESERT -> rgb(222, 205, 140)
            Biome.NETHER -> rgb(120, 40, 45)
            Biome.OCEAN -> rgb(50, 100, 200)
        }
        val mid = when (biome) {
            Biome.GRASS, Biome.SNOW -> rgb(125, 85, 55)
            Biome.DESERT -> rgb(205, 188, 125)
            Biome.NETHER -> rgb(95, 30, 35)
            Biome.OCEAN -> rgb(215, 200, 140)
        }
        val deep = if (biome == Biome.NETHER) rgb(75, 22, 28) else rgb(128, 128, 128)
        val topRows = if (biome == Biome.OCEAN) 14 else 3
        val midRows = if (biome == Biome.DESERT) 14 else 9
        for (y in groundTop until gh) {
            val d = y - groundTop
            for (x in 0 until gw) {
                val n = ((x * 31 + y * 17) xor (x * y)) and 7
                var c = when {
                    d < topRows -> top
                    d < topRows + midRows -> mid
                    else -> deep
                }
                c = shade(c, n - 3)
                if (biome == Biome.OCEAN && d < topRows && (x / 4 + d / 2) % 5 == 0) c = shade(c, 22)
                put(px, gw, x, y, c)
            }
        }
        // Ores and lava pockets in the deep layer.
        val deepStart = groundTop + topRows + midRows
        repeat(gw * (gh - deepStart) / 55) {
            val ox = rnd.nextInt(gw); val oy = deepStart + rnd.nextInt((gh - deepStart).coerceAtLeast(1))
            val color = when (rnd.nextInt(10)) {
                0, 1, 2, 3 -> rgb(35, 35, 35)       // coal
                4, 5, 6 -> rgb(215, 170, 130)       // iron
                7, 8 -> rgb(250, 215, 60)           // gold
                else -> rgb(80, 235, 235)           // diamond
            }
            val c = if (biome == Biome.NETHER && rnd.nextInt(3) == 0) rgb(255, 130, 20) else color
            rect(px, gw, ox, oy, 2, 2, c)
        }
        if (biome == Biome.NETHER) {
            repeat(3) {
                val lx = rnd.nextInt(gw - 14); val ly = groundTop + 1 + rnd.nextInt(2)
                rect(px, gw, lx, ly, 8 + rnd.nextInt(8), 2, rgb(255, 120, 20))
            }
        }
    }

    // --- trees and plants -----------------------------------------------------------------

    private fun paintTrees(px: IntArray, gw: Int, groundTop: Int, biome: Biome, rnd: Random, subjects: Int) {
        val count = if (subjects > 0) 2 else 3 + gw / 90
        val leaf = if (biome == Biome.SNOW) rgb(60, 110, 70) else rgb(45, 115, 40)
        repeat(count) {
            val tx = 6 + rnd.nextInt((gw - 14).coerceAtLeast(1))
            val th = 9 + rnd.nextInt(4)
            rect(px, gw, tx, groundTop - th, 2, th, rgb(100, 72, 40))
            rect(px, gw, tx - 3, groundTop - th - 3, 8, 4, shade(leaf, rnd.nextInt(9) - 4))
            rect(px, gw, tx - 2, groundTop - th - 6, 6, 3, shade(leaf, rnd.nextInt(9) - 4))
            if (biome == Biome.SNOW) rect(px, gw, tx - 2, groundTop - th - 7, 6, 1, rgb(250, 252, 255))
            for (k in 0 until 6) put(px, gw, tx - 3 + rnd.nextInt(8), groundTop - th - 3 + rnd.nextInt(4), shade(leaf, 18))
        }
    }

    private fun paintCactus(px: IntArray, gw: Int, groundTop: Int, rnd: Random) {
        repeat(2 + rnd.nextInt(2)) {
            val cx = 6 + rnd.nextInt((gw - 12).coerceAtLeast(1)); val ch = 6 + rnd.nextInt(4)
            rect(px, gw, cx, groundTop - ch, 3, ch, rgb(35, 130, 45))
            rect(px, gw, cx - 3, groundTop - ch + 2, 3, 2, rgb(35, 130, 45))
            rect(px, gw, cx - 3, groundTop - ch, 2, 3, rgb(35, 130, 45))
        }
    }

    // --- sprites --------------------------------------------------------------------------

    private class Sprite(val rows: List<String>, val palette: Map<Char, Int>) {
        val width: Int get() = rows.maxOf { it.length }
        val height: Int get() = rows.size
    }

    private fun spriteFor(name: String): Sprite? = when (name) {
        "creeper" -> Sprite(
            listOf(
                "GGGGGGGG", "GGGGGGGG", "GKKGGKKG", "GKKGGKKG",
                "GGGKKGGG", "GGKKKKGG", "GGKKKKGG", "GGKGGKGG",
                "..GGGG..", "..GGGG..", "..GGGG..", "..GGGG..",
                "GGGGGGGG", "GGGGGGGG", "GGG..GGG", "GGG..GGG"
            ),
            mapOf('G' to rgb(80, 175, 70), 'K' to rgb(20, 40, 20))
        )
        "steve" -> Sprite(
            listOf(
                "HHHHHHHH", "HHHHHHHH", "HSSSSSSH", "SWESSEWS",
                "SSSSSSSS", "SSSMMSSS", "SSSSSSSS", "..SSSS..",
                "SCCCCCCS", "SCCCCCCS", "SCCCCCCS", "SCCCCCCS",
                "SCCCCCCS", "SCCCCCCS", "BBBBBBBB", "BBBBBBBB",
                "BBBBBBBB", "BBB..BBB", "BBB..BBB", "DDD..DDD"
            ),
            mapOf(
                'H' to rgb(70, 45, 25), 'S' to rgb(200, 150, 110), 'W' to rgb(240, 240, 245),
                'E' to rgb(70, 60, 160), 'M' to rgb(120, 70, 55), 'C' to rgb(0, 170, 170),
                'B' to rgb(55, 55, 145), 'D' to rgb(75, 75, 75)
            )
        )
        "zombie" -> Sprite(
            listOf(
                "GGGGGGGG", "GGGGGGGG", "GKKGGKKG", "GKKGGKKG",
                "GGGGGGGG", "GGGKKGGG", "GGGGGGGG", "..GGGG..",
                "GCCCCCCG", "GCCCCCCG", "GCCCCCCG", "GCCCCCCG",
                "GCCCCCCG", "GCCCCCCG", "BBBBBBBB", "BBBBBBBB",
                "BBBBBBBB", "BBB..BBB", "BBB..BBB", "DDD..DDD"
            ),
            mapOf(
                'G' to rgb(85, 140, 70), 'K' to rgb(25, 40, 25), 'C' to rgb(0, 150, 150),
                'B' to rgb(50, 50, 130), 'D' to rgb(70, 70, 70)
            )
        )
        "pig" -> Sprite(
            listOf(
                "PPPPPPPP", "PPPPPPPP", "PEPPPPEP", "PPPPPPPP",
                "PPQQQQPP", "PPQNQNPP", "PPQQQQPP", "PPPPPPPP",
                "PPPPPPPP", "PPPPPPPP", "PPPPPPPP", "PPPPPPPP",
                "PPP..PPP", "PPP..PPP"
            ),
            mapOf(
                'P' to rgb(240, 160, 165), 'E' to rgb(40, 40, 60),
                'Q' to rgb(250, 185, 190), 'N' to rgb(150, 70, 80)
            )
        )
        else -> null
    }

    private fun drawSprite(px: IntArray, gw: Int, gh: Int, s: Sprite, x0: Int, y0: Int, scale: Int, rnd: Random) {
        for ((ry, row) in s.rows.withIndex()) for ((rx, ch) in row.withIndex()) {
            val base = s.palette[ch] ?: continue
            for (dy in 0 until scale) for (dx in 0 until scale) {
                val jitter = ((rx * 7 + ry * 13 + dx + dy * 3) % 5) - 2
                put(px, gw, x0 + rx * scale + dx, y0 + ry * scale + dy, shade(base, jitter * 3))
            }
        }
        // Soft contact shadow so the subject sits on the ground instead of floating.
        rect(px, gw, x0 + scale, y0 + s.height * scale, (s.width - 2) * scale, 1, rgb(40, 60, 30))
    }
}
