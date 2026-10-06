package com.endiq.turtlelauncher.ui.subassembly.aichat

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan

/**
 * Small dependency-free Markdown renderer for chat bubbles.
 *
 * Supports: # headings, **bold**, *italic* / _italic_, ***both***, ~~strike~~, `inline code`,
 * ``` fenced code ```, - / * / + / 1. lists (nested by indent), > quotes, --- rules,
 * [text](url) links and bare http(s) links. Anything else is shown as plain text, so a message
 * that is not Markdown at all comes out unchanged.
 */
object ChatMarkdown {

    private const val CODE_BG = 0x33808080
    private const val QUOTE_COLOR = 0xFF9AA0A6.toInt()

    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val BULLET = Regex("^(\\s*)[-*+•]\\s+(.*)$")
    private val NUMBERED = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
    private val QUOTE = Regex("^>\\s?(.*)$")
    private val RULE = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")
    private val FENCE = Regex("^\\s*```.*$")

    @JvmStatic
    fun render(source: String, @Suppress("UNUSED_PARAMETER") context: Context? = null): CharSequence {
        val out = SpannableStringBuilder()
        val lines = source.replace("\r\n", "\n").split("\n")
        var inFence = false
        var fenceStart = 0

        for ((i, raw) in lines.withIndex()) {
            val last = i == lines.lastIndex

            if (FENCE.matches(raw)) {
                if (!inFence) {
                    inFence = true
                    fenceStart = out.length
                } else {
                    inFence = false
                    trimTrailingNewline(out)
                    if (out.length > fenceStart) {
                        out.setSpan(TypefaceSpan("monospace"), fenceStart, out.length, FLAGS)
                        out.setSpan(BackgroundColorSpan(CODE_BG), fenceStart, out.length, FLAGS)
                    }
                    if (!last) out.append('\n')
                }
                continue
            }
            if (inFence) {
                out.append(raw)
                if (!last) out.append('\n')
                continue
            }

            val start = out.length
            val heading = HEADING.matchEntire(raw)
            val bullet = BULLET.matchEntire(raw)
            val numbered = NUMBERED.matchEntire(raw)
            val quote = QUOTE.matchEntire(raw)

            when {
                RULE.matches(raw) -> out.append("────────")
                heading != null -> {
                    inline(out, heading.groupValues[2])
                    val level = heading.groupValues[1].length
                    out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, FLAGS)
                    out.setSpan(RelativeSizeSpan(if (level <= 1) 1.3f else if (level == 2) 1.2f else 1.1f), start, out.length, FLAGS)
                }
                bullet != null -> {
                    val depth = bullet.groupValues[1].length / 2
                    out.append("•  ")
                    inline(out, bullet.groupValues[2])
                    out.setSpan(LeadingMarginSpan.Standard(depth * 36, depth * 36 + 36), start, out.length, FLAGS)
                }
                numbered != null -> {
                    val depth = numbered.groupValues[1].length / 2
                    out.append(numbered.groupValues[2]).append(".  ")
                    inline(out, numbered.groupValues[3])
                    out.setSpan(LeadingMarginSpan.Standard(depth * 36, depth * 36 + 36), start, out.length, FLAGS)
                }
                quote != null -> {
                    out.append("▎ ")
                    inline(out, quote.groupValues[1])
                    out.setSpan(ForegroundColorSpan(QUOTE_COLOR), start, out.length, FLAGS)
                }
                else -> inline(out, raw)
            }
            if (!last) out.append('\n')
        }
        // An unterminated fence (message cut off mid-block) still gets code styling.
        if (inFence && out.length > fenceStart) {
            out.setSpan(TypefaceSpan("monospace"), fenceStart, out.length, FLAGS)
            out.setSpan(BackgroundColorSpan(CODE_BG), fenceStart, out.length, FLAGS)
        }
        trimTrailingNewline(out)
        return out
    }

    private const val FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE

    private fun trimTrailingNewline(sb: SpannableStringBuilder) {
        while (sb.isNotEmpty() && sb[sb.length - 1] == '\n') sb.delete(sb.length - 1, sb.length)
    }

    /** One alternation, leftmost match wins; order of alternatives sets precedence at a position. */
    private val INLINE = Regex(
        "`([^`\n]+)`" +                                   // 1 code
            "|\\*\\*\\*(.+?)\\*\\*\\*" +                  // 2 bold+italic
            "|\\*\\*(.+?)\\*\\*" +                        // 3 bold
            "|__(.+?)__" +                                // 4 bold
            "|~~(.+?)~~" +                                // 5 strike
            "|\\[([^\\]\n]+)]\\((https?://[^)\\s]+)\\)" + // 6 text, 7 url
            "|(?<![*\\w])\\*(?![\\s*])(.+?)(?<![\\s*])\\*(?![*\\w])" + // 8 italic
            "|(?<![_\\w])_(?![\\s_])(.+?)(?<![\\s_])_(?![_\\w])" +     // 9 italic
            "|(https?://[^\\s<>)\\]]+[^\\s<>)\\].,;:!?'\"])"              // 10 bare url
    )

    private fun inline(out: SpannableStringBuilder, text: String) {
        var pos = 0
        for (m in INLINE.findAll(text)) {
            if (m.range.first > pos) out.append(text, pos, m.range.first)
            val g = m.groups
            val s = out.length
            when {
                g[1] != null -> {
                    out.append(g[1]!!.value)
                    out.setSpan(TypefaceSpan("monospace"), s, out.length, FLAGS)
                    out.setSpan(BackgroundColorSpan(CODE_BG), s, out.length, FLAGS)
                }
                g[2] != null -> {
                    inline(out, g[2]!!.value)
                    out.setSpan(StyleSpan(Typeface.BOLD_ITALIC), s, out.length, FLAGS)
                }
                g[3] != null || g[4] != null -> {
                    inline(out, (g[3] ?: g[4])!!.value)
                    out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, FLAGS)
                }
                g[5] != null -> {
                    inline(out, g[5]!!.value)
                    out.setSpan(StrikethroughSpan(), s, out.length, FLAGS)
                }
                g[6] != null -> {
                    out.append(g[6]!!.value)
                    out.setSpan(URLSpan(g[7]!!.value), s, out.length, FLAGS)
                    out.setSpan(UnderlineSpan(), s, out.length, FLAGS)
                }
                g[8] != null || g[9] != null -> {
                    inline(out, (g[8] ?: g[9])!!.value)
                    out.setSpan(StyleSpan(Typeface.ITALIC), s, out.length, FLAGS)
                }
                g[10] != null -> {
                    out.append(g[10]!!.value)
                    out.setSpan(URLSpan(g[10]!!.value), s, out.length, FLAGS)
                }
            }
            pos = m.range.last + 1
        }
        if (pos < text.length) out.append(text, pos, text.length)
    }
}
