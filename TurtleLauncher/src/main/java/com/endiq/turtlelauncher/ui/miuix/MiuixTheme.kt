package com.endiq.turtlelauncher.ui.miuix

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt

/**
 * Miuix Design System theme tokens ported directly from
 * `top.yukonga.miuix.kmp.theme.Colors` and `top.yukonga.miuix.kmp.theme.TextStyles`
 * (https://github.com/compose-miuix-ui/miuix).
 */
data class MiuixColorScheme(
    @ColorInt val primary: Int,
    @ColorInt val onPrimary: Int,
    @ColorInt val primaryVariant: Int,
    @ColorInt val onPrimaryVariant: Int,
    @ColorInt val primaryContainer: Int,
    @ColorInt val onPrimaryContainer: Int,
    @ColorInt val disabledPrimary: Int,
    @ColorInt val disabledOnPrimary: Int,
    @ColorInt val disabledPrimaryButton: Int,
    @ColorInt val disabledOnPrimaryButton: Int,
    @ColorInt val disabledPrimarySlider: Int,
    @ColorInt val secondary: Int,
    @ColorInt val onSecondary: Int,
    @ColorInt val secondaryVariant: Int,
    @ColorInt val onSecondaryVariant: Int,
    @ColorInt val disabledSecondary: Int,
    @ColorInt val disabledOnSecondary: Int,
    @ColorInt val disabledSecondaryVariant: Int,
    @ColorInt val disabledOnSecondaryVariant: Int,
    @ColorInt val secondaryContainer: Int,
    @ColorInt val onSecondaryContainer: Int,
    @ColorInt val secondaryContainerVariant: Int,
    @ColorInt val onSecondaryContainerVariant: Int,
    @ColorInt val tertiaryContainer: Int,
    @ColorInt val onTertiaryContainer: Int,
    @ColorInt val tertiaryContainerVariant: Int,
    @ColorInt val background: Int,
    @ColorInt val onBackground: Int,
    @ColorInt val onBackgroundVariant: Int,
    @ColorInt val surface: Int,
    @ColorInt val onSurface: Int,
    @ColorInt val surfaceVariant: Int,
    @ColorInt val onSurfaceSecondary: Int,
    @ColorInt val onSurfaceVariantSummary: Int,
    @ColorInt val onSurfaceVariantActions: Int,
    @ColorInt val disabledOnSurface: Int,
    @ColorInt val surfaceContainer: Int,
    @ColorInt val onSurfaceContainer: Int,
    @ColorInt val surfaceContainerHigh: Int,
    @ColorInt val onSurfaceContainerHigh: Int,
    @ColorInt val surfaceContainerHighest: Int,
    @ColorInt val onSurfaceContainerHighest: Int,
    @ColorInt val outline: Int,
    @ColorInt val dividerLine: Int,
    @ColorInt val windowDimming: Int,
    @ColorInt val error: Int,
    @ColorInt val onError: Int,
    @ColorInt val errorContainer: Int,
    @ColorInt val onErrorContainer: Int,
    @ColorInt val sliderKeyPoint: Int,
    @ColorInt val sliderKeyPointForeground: Int,
    @ColorInt val sliderBackground: Int
)

object MiuixTheme {
    /**
     * Exact Miuix `darkColorScheme()` from `top.yukonga.miuix.kmp.theme.Colors`.
     */
    @JvmField
    val DarkColorScheme = MiuixColorScheme(
        primary = 0xFF277AF7.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        primaryVariant = 0xFF0073DD.toInt(),
        onPrimaryVariant = 0xFF99C7F1.toInt(),
        primaryContainer = 0xFF338FE4.toInt(),
        onPrimaryContainer = 0xFFFFFFFF.toInt(),
        disabledPrimary = 0xFF253E64.toInt(),
        disabledOnPrimary = 0xFF677993.toInt(),
        disabledPrimaryButton = 0xFF253E64.toInt(),
        disabledOnPrimaryButton = 0xFF677893.toInt(),
        disabledPrimarySlider = 0xFF44587C.toInt(),
        secondary = 0xFF505050.toInt(),
        onSecondary = 0xFFFFFFFF.toInt(),
        secondaryVariant = 0xFF434343.toInt(),
        onSecondaryVariant = 0xFFD9D9D9.toInt(),
        disabledSecondary = 0xFF404040.toInt(),
        disabledOnSecondary = 0xFF707070.toInt(),
        disabledSecondaryVariant = 0xFF404040.toInt(),
        disabledOnSecondaryVariant = 0xFF717171.toInt(),
        secondaryContainer = 0xFF434343.toInt(),
        onSecondaryContainer = 0xFF7C7C7C.toInt(),
        secondaryContainerVariant = 0xFF4F4F4F.toInt(),
        onSecondaryContainerVariant = 0xFF959595.toInt(),
        tertiaryContainer = 0xFF2B3B54.toInt(),
        onTertiaryContainer = 0xFF4788FF.toInt(),
        tertiaryContainerVariant = 0xFF505050.toInt(),
        background = 0xFF242424.toInt(),
        onBackground = 0xE6FFFFFF.toInt(),
        onBackgroundVariant = 0xFF787E96.toInt(),
        surface = 0xFF000000.toInt(),
        onSurface = 0xFFF2F2F2.toInt(),
        surfaceVariant = 0xFF242424.toInt(),
        onSurfaceSecondary = 0xCCFFFFFF.toInt(),
        onSurfaceVariantSummary = 0x80FFFFFF.toInt(),
        onSurfaceVariantActions = 0x66FFFFFF.toInt(),
        disabledOnSurface = 0xFF666666.toInt(),
        surfaceContainer = 0xFF242424.toInt(),
        onSurfaceContainer = 0xE6FFFFFF.toInt(),
        surfaceContainerHigh = 0xFF242424.toInt(),
        onSurfaceContainerHigh = 0xFF666666.toInt(),
        surfaceContainerHighest = 0xFF2D2D2D.toInt(),
        onSurfaceContainerHighest = 0xFFE9E9E9.toInt(),
        outline = 0xFF404040.toInt(),
        dividerLine = 0xFF393939.toInt(),
        windowDimming = 0x99000000.toInt(),
        error = 0xFFF12522.toInt(),
        onError = 0xFF690005.toInt(),
        errorContainer = 0xFF2E0603.toInt(),
        onErrorContainer = 0xFFFFDAD6.toInt(),
        sliderKeyPoint = 0x4D7A8AA6,
        sliderKeyPointForeground = 0xFF5DAAFF.toInt(),
        sliderBackground = 0x26FFFFFF
    )

    /**
     * Exact Miuix `lightColorScheme()` from `top.yukonga.miuix.kmp.theme.Colors`.
     */
    @JvmField
    val LightColorScheme = MiuixColorScheme(
        primary = 0xFF3482FF.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        primaryVariant = 0xFF3482FF.toInt(),
        onPrimaryVariant = 0xFFAECDFF.toInt(),
        primaryContainer = 0xFF5D9BFF.toInt(),
        onPrimaryContainer = 0xFFFFFFFF.toInt(),
        disabledPrimary = 0xFFC2D9FF.toInt(),
        disabledOnPrimary = 0xFFF3F8FF.toInt(),
        disabledPrimaryButton = 0xFFC2D9FF.toInt(),
        disabledOnPrimaryButton = 0xFFFFFFFF.toInt(),
        disabledPrimarySlider = 0xFFB8CFF5.toInt(),
        secondary = 0xFFE6E6E6.toInt(),
        onSecondary = 0xFFFFFFFF.toInt(),
        secondaryVariant = 0xFFF0F0F0.toInt(),
        onSecondaryVariant = 0xFF303030.toInt(),
        disabledSecondary = 0xFFF0F0F0.toInt(),
        disabledOnSecondary = 0xFFFCFCFC.toInt(),
        disabledSecondaryVariant = 0xFFF2F2F2.toInt(),
        disabledOnSecondaryVariant = 0xFFB2B2B2.toInt(),
        secondaryContainer = 0xFFF0F0F0.toInt(),
        onSecondaryContainer = 0xFFA9A9A9.toInt(),
        secondaryContainerVariant = 0xFFF0F0F0.toInt(),
        onSecondaryContainerVariant = 0xFFA8A8A8.toInt(),
        tertiaryContainer = 0xFFEAF2FF.toInt(),
        onTertiaryContainer = 0xFF3482FF.toInt(),
        tertiaryContainerVariant = 0xFFEAF2FF.toInt(),
        background = 0xFFFFFFFF.toInt(),
        onBackground = 0xFF000000.toInt(),
        onBackgroundVariant = 0xFF8C93B0.toInt(),
        surface = 0xFFF7F7F7.toInt(),
        onSurface = 0xFF000000.toInt(),
        surfaceVariant = 0xFFFFFFFF.toInt(),
        onSurfaceSecondary = 0xCC000000.toInt(),
        onSurfaceVariantSummary = 0x99000000.toInt(),
        onSurfaceVariantActions = 0x66000000,
        disabledOnSurface = 0xFFB2B2B2.toInt(),
        surfaceContainer = 0xFFFFFFFF.toInt(),
        onSurfaceContainer = 0xFF000000.toInt(),
        surfaceContainerHigh = 0xFFE8E8E8.toInt(),
        onSurfaceContainerHigh = 0xFFA2A2A2.toInt(),
        surfaceContainerHighest = 0xFFE8E8E8.toInt(),
        onSurfaceContainerHighest = 0xFF000000.toInt(),
        outline = 0xFFD9D9D9.toInt(),
        dividerLine = 0xFFE0E0E0.toInt(),
        windowDimming = 0x4D000000,
        error = 0xFFE94634.toInt(),
        onError = 0xFFFFFFFF.toInt(),
        errorContainer = 0xFFFDF6F4.toInt(),
        onErrorContainer = 0xFF410002.toInt(),
        sliderKeyPoint = 0x4DA3B3CD,
        sliderKeyPointForeground = 0xFF6EB5FF.toInt(),
        sliderBackground = 0x0F000000
    )

    /**
     * Typography sizes in sp from `top.yukonga.miuix.kmp.theme.TextStyles`.
     */
    object TextSizesSp {
        const val MAIN = 17f
        const val PARAGRAPH = 17f
        const val BODY1 = 16f
        const val BODY2 = 14f
        const val BUTTON = 17f
        const val FOOTNOTE1 = 13f
        const val FOOTNOTE2 = 11f
        const val HEADLINE1 = 17f
        const val HEADLINE2 = 16f
        const val SUBTITLE = 14f
        const val TITLE1 = 32f
        const val TITLE2 = 24f
        const val TITLE3 = 20f
        const val TITLE4 = 18f
    }

    /**
     * Standard Miuix component corner radii in dp.
     */
    object CornerRadiiDp {
        const val CARD = 16f
        const val BUTTON = 16f
        const val TEXT_FIELD = 16f
        const val TAB_ROW = 12f
        const val TAB_INDICATOR = 8f
        const val DIALOG = 32f
        const val POPUP = 20f
    }

    /**
     * Standard Miuix indication overlay alphas from `MiuixIndication.kt`.
     */
    object IndicationAlpha {
        const val HOVER = 0.06f
        const val FOCUS = 0.08f
        const val PRESS = 0.10f
        const val HOLD_DOWN = 0.10f
    }

    @JvmStatic
    fun isDarkMode(context: Context?): Boolean {
        if (context == null) return true
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask != Configuration.UI_MODE_NIGHT_NO
    }

    @JvmStatic
    fun colors(context: Context?): MiuixColorScheme {
        return if (isDarkMode(context)) DarkColorScheme else LightColorScheme
    }

    @JvmStatic
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * context.resources.displayMetrics.density
    }
}
