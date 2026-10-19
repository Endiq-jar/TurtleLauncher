package com.endiq.turtlelauncher.utils.anim

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.endiq.turtlelauncher.R

/**
 * Interaction animations for every vector icon in the launcher (the Tabler/Flowbite
 * set imported by tools/import_icons.py) and a kinetic companion for the animated
 * WebP logos in [AnimatedLogo].
 *
 * Same rules as the logos - nothing moves on its own:
 *
 *  * hover (mouse / stylus / trackpad over the icon or its row/card) - loops,
 *  * press (touch held down) - loops while held; releasing finishes the sweep,
 *  * click/tap - exactly one full play-through,
 *  * focus (keyboard / gamepad) - loops while focused.
 *
 * Each icon gets a distinct motion. Semantic styles come first (refresh spins,
 * favourites heartbeat, downloads drop, eyes blink, warnings swing, deletes
 * shake...), and the remaining icons draw from the full kinetic pool. On top of
 * the style, every icon has a stable per-resource seed that varies amplitude,
 * direction and duration by +/-15%, so two icons that share a style still move
 * differently from one another. Logos use a separate, slower style family
 * ([Style.LOGO_GLIDE] ... [Style.LOGO_PULSE]) layered over their WebP playback,
 * which also gives API 26/27 (where WebP animation is unavailable) real motion.
 *
 * Styles animate view properties only (scale / rotation / translation / alpha),
 * which is GPU-composited and allocation-light; a fresh cycle is built per play
 * so a cancelled animator can never leak state.
 */
object AnimatedIcon {

    enum class Style {
        // General icon vocabulary.
        PULSE, POP, HEARTBEAT, BOUNCE_Y, DROP, LIFT, LAUNCH, SLIDE_X,
        SPIN, GEAR, SHAKE, WOBBLE, WIGGLE, SWING, BLINK, SCAN, TADA, FLIP_X, FLIP_Y,
        // Extra kinetic vocabulary - each one is a visibly different keyframe set.
        ORBIT, JELLO, RUBBER, TWIST, ZOOM_FLIP, DROP_BOUNCE, SPIN_POP,
        SWING_Y, PENDULUM, SKATE, DRIFT, ELASTIC, RIPPLE, HOVER, FLOAT,
        NUDGE, CROSS, TILT,
        // Logo-only family. Never returned by [styleFor]; assigned by [logoStyleFor].
        LOGO_GLIDE, LOGO_BOUNCE, LOGO_TWIST, LOGO_SHIMMER, LOGO_RATTLE,
        LOGO_SWAY, LOGO_PULSE
    }

    /** Every non-logo style, used as the stable fallback pool. */
    private val KINETIC = listOf(
        Style.PULSE, Style.BOUNCE_Y, Style.WOBBLE, Style.POP,
        Style.SWING, Style.FLIP_X, Style.LIFT, Style.TADA,
        Style.ORBIT, Style.JELLO, Style.RUBBER, Style.TWIST,
        Style.ZOOM_FLIP, Style.DROP_BOUNCE, Style.SPIN_POP,
        Style.SWING_Y, Style.PENDULUM, Style.SKATE, Style.DRIFT,
        Style.ELASTIC, Style.RIPPLE, Style.HOVER, Style.FLOAT,
        Style.NUDGE, Style.CROSS, Style.TILT, Style.SLIDE_X,
        Style.GEAR, Style.SCAN, Style.LAUNCH
    )

    /** Style for an icon resource, by resource entry name (stable across runs). */
    fun styleFor(entryName: String): Style {
        val n = entryName.lowercase()
        return when {
            // File-type family (ic_filetype_*): each type moves its own way.
            "filetype_folder" in n -> Style.BOUNCE_Y
            "filetype_jar" in n -> Style.DROP
            "filetype_zip" in n -> Style.POP
            "filetype_json" in n -> Style.SCAN
            "filetype_code" in n -> Style.FLIP_X
            "filetype_text" in n -> Style.SWING
            "filetype_image" in n -> Style.BLINK
            "filetype_audio" in n -> Style.WOBBLE
            "filetype_video" in n -> Style.PULSE
            "filetype_data" in n -> Style.FLIP_Y
            "filetype_config" in n -> Style.GEAR
            "filetype_apk" in n -> Style.TADA
            "filetype_pdf" in n -> Style.SLIDE_X
            "filetype_font" in n -> Style.WIGGLE
            "filetype_3d" in n -> Style.SPIN
            "filetype_file" in n -> Style.LIFT

            // Navigation / lifecycle.
            "refresh" in n || "reset" in n || "spinner" in n -> Style.SPIN
            "loading" in n -> Style.ORBIT
            "favorite" in n || "heart" in n -> Style.HEARTBEAT
            // Specific channel downloads before the generic rule so Alpha/Beta/Release
            // do not all collapse into the same DROP.
            "download_alpha" in n -> Style.DROP_BOUNCE
            "download_beta" in n -> Style.PENDULUM
            "download_release" in n -> Style.LAUNCH
            "download" in n || "extract" in n || "chevron_down" in n -> Style.DROP
            "chevron" in n || "arrow" in n || "launch_link" in n -> Style.SLIDE_X
            "eye" in n || "screenshot" in n -> Style.BLINK
            "search" in n -> Style.SCAN
            "delete" in n || "disabled" in n || "crash" in n -> Style.SHAKE
            "close" in n || "cancel" in n -> Style.WIGGLE
            "warning" in n || "help" in n || "about" in n || "alert" in n -> Style.SWING
            "music" in n || "speaker" in n || "emotes" in n -> Style.WOBBLE
            "play" in n -> Style.PULSE
            "add" in n -> Style.POP
            "share" in n || "logout" in n -> Style.LAUNCH
            "check" in n || "tasks" in n -> Style.POP
            "copy" in n || "paste" in n || "file" in n || "folder" in n ||
                "document" in n || "zip" in n -> Style.LIFT
            "chat" in n || "_ai" in n || "ai_" in n -> Style.TADA
            "edit" in n || "hair" in n || "skin" in n || "cape" in n ||
                "move" in n || "handle" in n -> Style.WIGGLE
            "globe" in n || "friends" in n || "network" in n || "accounts" in n -> Style.FLIP_Y
            "setting" in n || "optimization" in n || "git" in n -> Style.GEAR

            // Icon-specific vocabulary.
            "home" in n -> Style.LIFT
            "bolt" in n -> Style.NUDGE
            "pin" in n -> Style.DROP_BOUNCE
            "more" in n -> Style.BOUNCE_Y
            "gamepad" in n -> Style.TILT
            "puzzle" in n || "modpack" in n -> Style.TWIST
            "anvil" in n -> Style.DROP_BOUNCE
            "keyboard" in n -> Style.NUDGE
            "cursor" in n || "drag" in n || "view_handle" in n -> Style.SKATE
            "storage" in n -> Style.RIPPLE
            "profile" in n -> Style.LIFT
            "resource_pack" in n || "shader" in n -> Style.ORBIT
            "saves" in n -> Style.LIFT
            "java" in n || "openjdk" in n || "neoforge" in n ||
                "optifine" in n || "curseforge" in n || "discord" in n ||
                "github" in n -> Style.TADA
            "screenshot" in n -> Style.BLINK
            "menu_emotes" in n -> Style.WOBBLE
            "menu_mouse_keyboard" in n || "menu_custom_controls" in n -> Style.TILT
            "menu_close_clear_cancel" in n -> Style.WIGGLE
            "menu_delete_forever" in n -> Style.SHAKE
            "menu_accounts" in n -> Style.FLIP_Y
            "menu_home" in n || "menu_settings" in n -> Style.GEAR
            else -> KINETIC[n.hashCode().ushr(1) % KINETIC.size]
        }
    }

    /** Slow, logo-specific transform family. One distinct style per shipped logo. */
    @JvmStatic
    fun logoStyleFor(resource: Int): Style = when (resource) {
        R.drawable.ic_turtle_full -> Style.LOGO_GLIDE
        R.drawable.ic_minecraft -> Style.LOGO_BOUNCE
        R.drawable.ic_command_block -> Style.LOGO_TWIST
        R.drawable.app_name_title -> Style.LOGO_SHIMMER
        R.drawable.ic_old_cobblestone -> Style.LOGO_RATTLE
        R.drawable.ic_old_grass_block -> Style.LOGO_SWAY
        else -> Style.LOGO_PULSE
    }

    /** Reads `android:src` / `app:srcCompat` from XML attrs (0 when absent). */
    @JvmStatic
    fun readSource(context: Context, attrs: AttributeSet?): Int {
        if (attrs == null) return 0
        val values = context.obtainStyledAttributes(
            attrs, intArrayOf(android.R.attr.src, androidx.appcompat.R.attr.srcCompat)
        )
        val compat = values.getResourceId(1, 0)
        val source = if (compat != 0) compat else values.getResourceId(0, 0)
        values.recycle()
        return source
    }

    /** Routes WebP logos to [AnimatedLogo] and everything else to the icon styles. */
    @JvmStatic
    fun bindAny(view: ImageView, resource: Int) {
        if (resource == 0) return
        if (AnimatedLogo.isAnimatedLogo(resource)) {
            clear(view)
            AnimatedLogo.bind(view, resource)
        } else {
            bind(view, resource)
        }
    }

    @JvmStatic
    fun playOnceAny(view: ImageView) {
        playOnce(view)
        AnimatedLogo.playOnce(view)
    }

    @JvmStatic
    fun bind(view: ImageView, resource: Int) {
        AnimatedLogo.clear(view) // a view is either a vector icon or a WebP logo
        clear(view)
        if (resource == 0 || !TurtleTransitions.isEnabled()) return
        val name = runCatching { view.resources.getResourceEntryName(resource) }
            .getOrDefault("icon_$resource")
        install(view, styleFor(name), name.hashCode())
    }

    /**
     * Binds a style to any [ImageView] whose resource name is not available at runtime
     * (images set programmatically by an adapter, avatars, thumbnails).
     */
    @JvmStatic
    @JvmOverloads
    fun bindNamed(view: ImageView, key: String, style: Style = styleFor(key)) {
        AnimatedLogo.clear(view)
        clear(view)
        if (!TurtleTransitions.isEnabled()) return
        install(view, style, key.hashCode())
    }

    /** Binds an explicit style + seed. Used for programmatic / recycled image views. */
    @JvmStatic
    @JvmOverloads
    fun bindStyled(view: ImageView, style: Style, seed: Int = style.ordinal) {
        AnimatedLogo.clear(view)
        clear(view)
        if (!TurtleTransitions.isEnabled()) return
        install(view, style, seed)
    }

    /**
     * Adds the kinetic layer to a WebP logo without touching its [AnimatedLogo] request.
     * Called by [AnimatedLogo.bind] / [AnimatedLogo.bindWatermark]; also the fallback that
     * keeps logos moving on API 26/27, where [android.graphics.drawable.AnimatedImageDrawable]
     * does not exist.
     */
    @JvmStatic
    fun bindLogoMotion(view: ImageView, resource: Int) {
        clear(view)
        if (resource == 0 || !TurtleTransitions.isEnabled()) return
        install(view, logoStyleFor(resource), resource * 31 + 17)
    }

    private fun install(view: ImageView, style: Style, seed: Int) {
        // A stable camera distance keeps rotationX/rotationY from clipping through the
        // view's own bounds on tall icons. Scaling by density matches the framework's
        // default reference.
        view.cameraDistance = 12000f * view.resources.displayMetrics.density
        view.setTag(R.id.animated_icon_request, Request(view, style, seed))
    }

    /** One play-through for clicks without a touch sequence. No-op if playing. */
    @JvmStatic
    fun playOnce(view: ImageView) {
        (view.getTag(R.id.animated_icon_request) as? Request)?.playOnceIfIdle()
    }

    @JvmStatic
    fun clear(view: ImageView) {
        (view.getTag(R.id.animated_icon_request) as? Request)?.close()
        view.setTag(R.id.animated_icon_request, null)
    }

    // -- cycle factory -------------------------------------------------------

    private fun anim(view: View, property: String, vararg values: Float): ObjectAnimator =
        ObjectAnimator.ofFloat(view, property, *values)

    /**
     * Builds one distinct keyframe cycle. [seed] is derived from the resource name:
     * it flips the travel direction for every other icon and varies amplitude
     * (0.86x .. 1.14x) and duration, so even the same [Style] never looks repeated
     * across neighbouring icons.
     */
    private fun cycle(view: View, style: Style, seed: Int): AnimatorSet {
        val d = 2f * view.resources.displayMetrics.density // 2dp travel unit
        // ushr(1) keeps Int.MIN_VALUE non-negative; abs() would leave it negative.
        val key = seed.ushr(1)
        val amp = 0.86f + (key % 29) / 100f
        val dir = if ((key / 29) % 2 == 0) 1f else -1f

        fun r(value: Float) = value * amp * dir
        fun t(value: Float) = value * amp * dir
        fun s(value: Float) = 1f + (value - 1f) * amp

        val parts: List<Animator> = when (style) {
            Style.PULSE -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(1.18f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.18f), 1f))
            Style.POP -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(1.3f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.3f), 1f))
            Style.HEARTBEAT -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(1.28f), 1f, s(1.18f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.28f), 1f, s(1.18f), 1f))
            Style.BOUNCE_Y -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, t(-1.6f * d), 0f, t(-0.7f * d), 0f))
            Style.DROP -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, t(1.6f * d), 0f, t(0.6f * d), 0f))
            Style.LIFT -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, t(-1.5f * d), 0f),
                anim(view, View.SCALE_X.name, 1f, s(1.06f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.06f), 1f))
            Style.LAUNCH -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, t(d), 0f),
                anim(view, View.TRANSLATION_Y.name, 0f, t(-d), 0f))
            Style.SLIDE_X -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, t(1.6f * d), 0f))
            Style.SPIN -> listOf(anim(view, View.ROTATION.name, 0f, 360f * dir))
            Style.GEAR -> listOf(anim(view, View.ROTATION.name, 0f, r(35f), 0f))
            Style.SHAKE -> listOf(
                anim(view, View.TRANSLATION_X.name,
                    0f, t(-d), t(d), t(-0.6f * d), t(0.6f * d), 0f))
            Style.WOBBLE -> listOf(
                anim(view, View.ROTATION.name, 0f, r(-8f), r(6f), r(-4f), 0f),
                anim(view, View.TRANSLATION_X.name, 0f, t(-0.5f * d), t(0.5f * d), 0f))
            Style.WIGGLE -> listOf(
                anim(view, View.ROTATION.name, 0f, r(-12f), r(12f), 0f))
            Style.SWING -> listOf(
                anim(view, View.ROTATION.name, 0f, r(14f), r(-10f), r(5f), 0f))
            Style.BLINK -> listOf(
                anim(view, View.SCALE_Y.name, 1f, s(0.12f), 1f))
            Style.SCAN -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(1.16f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.16f), 1f),
                anim(view, View.ROTATION.name, 0f, r(-10f), 0f))
            Style.TADA -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(0.9f), s(1.12f), s(1.16f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(0.9f), s(1.12f), s(1.16f), 1f),
                anim(view, View.ROTATION.name, 0f, r(-3f), r(3f), r(-3f), 0f))
            Style.FLIP_X -> listOf(anim(view, View.ROTATION_X.name, 0f, 360f * dir))
            Style.FLIP_Y -> listOf(anim(view, View.ROTATION_Y.name, 0f, 360f * dir))

            // -- extra kinetic family ----------------------------------------
            Style.ORBIT -> listOf(
                anim(view, View.ROTATION.name, 0f, 360f * dir),
                anim(view, View.SCALE_X.name, 1f, s(0.92f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(0.92f), 1f))
            Style.JELLO -> listOf(
                anim(view, View.SCALE_X.name,
                    1f, s(1.22f), s(0.94f), s(1.08f), 1f),
                anim(view, View.SCALE_Y.name,
                    1f, s(0.82f), s(1.1f), s(0.96f), 1f))
            Style.RUBBER -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(1.28f), s(0.92f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(0.78f), s(1.06f), 1f))
            Style.TWIST -> listOf(
                anim(view, View.ROTATION.name, 0f, r(-18f), r(18f), 0f),
                anim(view, View.ROTATION_Y.name, 0f, r(22f), 0f))
            Style.ZOOM_FLIP -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(0.82f), s(1.12f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(0.82f), s(1.12f), 1f),
                anim(view, View.ROTATION_X.name, 0f, r(18f), r(-18f), 0f))
            Style.DROP_BOUNCE -> listOf(
                anim(view, View.TRANSLATION_Y.name,
                    0f, t(2.4f * d), 0f, t(1f * d), 0f),
                anim(view, View.SCALE_Y.name, 1f, s(0.86f), s(1.04f), 1f))
            Style.SPIN_POP -> listOf(
                anim(view, View.ROTATION.name, 0f, 360f * dir),
                anim(view, View.SCALE_X.name, 1f, s(0.8f), s(1.15f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(0.8f), s(1.15f), 1f))
            Style.SWING_Y -> listOf(
                anim(view, View.ROTATION.name, 0f, r(12f), r(-9f), 0f))
            Style.PENDULUM -> listOf(
                anim(view, View.ROTATION.name, 0f, r(-18f), r(18f), r(-12f), r(12f), 0f))
            Style.SKATE -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, t(-1.4f * d), t(1.4f * d), 0f),
                anim(view, View.ROTATION.name, 0f, r(-5f), r(5f), 0f))
            Style.DRIFT -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, t(-1.3f * d), 0f),
                anim(view, View.TRANSLATION_X.name, 0f, t(0.6f * d), 0f),
                anim(view, View.ALPHA.name, 1f, 0.82f, 1f))
            Style.ELASTIC -> listOf(
                anim(view, View.SCALE_Y.name, 1f, s(1.35f), s(0.9f), s(1.06f), 1f),
                anim(view, View.SCALE_X.name, 1f, s(0.85f), s(1.08f), 1f))
            Style.RIPPLE -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(1.22f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.22f), 1f),
                anim(view, View.ALPHA.name, 1f, 0.7f, 1f))
            Style.HOVER -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, t(-1.2f * d), 0f),
                anim(view, View.SCALE_X.name, 1f, s(1.08f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.08f), 1f))
            Style.FLOAT -> listOf(
                anim(view, View.TRANSLATION_Y.name, 0f, t(-1.8f * d), 0f),
                anim(view, View.ROTATION.name, 0f, r(-3f), 0f))
            Style.NUDGE -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, t(0.8f * d), 0f),
                anim(view, View.SCALE_X.name, 1f, s(1.08f), 1f))
            Style.CROSS -> listOf(
                anim(view, View.ROTATION.name, 0f, r(-7f), r(7f), 0f),
                anim(view, View.SCALE_X.name, 1f, s(1.12f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.12f), 1f))
            Style.TILT -> listOf(
                anim(view, View.ROTATION_Y.name, 0f, r(35f), r(-25f), 0f))

            // -- logo-only family (slow, weighty, layered over WebP playback) --
            Style.LOGO_GLIDE -> listOf(
                anim(view, View.ROTATION.name, 0f, r(3.5f), r(-3f), 0f),
                anim(view, View.TRANSLATION_Y.name, 0f, t(-3f * d), 0f),
                anim(view, View.SCALE_X.name, 1f, s(1.04f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.04f), 1f))
            Style.LOGO_BOUNCE -> listOf(
                anim(view, View.TRANSLATION_Y.name,
                    0f, t(-6f * d), 0f, t(-2f * d), 0f),
                anim(view, View.SCALE_Y.name, 1f, s(0.92f), s(1.06f), 1f),
                anim(view, View.SCALE_X.name, 1f, s(1.05f), s(0.97f), 1f))
            Style.LOGO_TWIST -> listOf(
                anim(view, View.ROTATION_Y.name, 0f, r(28f), r(-22f), 0f),
                anim(view, View.SCALE_X.name, 1f, s(1.06f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.06f), 1f))
            Style.LOGO_SHIMMER -> listOf(
                anim(view, View.TRANSLATION_X.name, 0f, t(2f * d), t(-2f * d), 0f),
                anim(view, View.ALPHA.name, 1f, 0.86f, 1f),
                anim(view, View.SCALE_X.name, 1f, s(1.05f), 1f))
            Style.LOGO_RATTLE -> listOf(
                anim(view, View.TRANSLATION_X.name,
                    0f, t(-1.4f * d), t(1.4f * d), t(-0.8f * d), 0f),
                anim(view, View.ROTATION.name, 0f, r(-3f), r(3f), 0f))
            Style.LOGO_SWAY -> listOf(
                anim(view, View.ROTATION.name, 0f, r(7f), r(-6f), r(3f), 0f))
            Style.LOGO_PULSE -> listOf(
                anim(view, View.SCALE_X.name, 1f, s(1.12f), 1f),
                anim(view, View.SCALE_Y.name, 1f, s(1.12f), 1f),
                anim(view, View.TRANSLATION_Y.name, 0f, t(-1.5f * d), 0f))
        }

        val isLogo = style.name.startsWith("LOGO_")
        val durationMs = when {
            isLogo -> 1150L + (key % 450)
            style == Style.SPIN || style == Style.FLIP_X || style == Style.FLIP_Y ||
                style == Style.ORBIT -> 850L + (key % 220)
            else -> 640L + (key % 200)
        }
        return AnimatorSet().apply {
            playTogether(*parts.toTypedArray())
            this.duration = durationMs
            setInterpolator(when (style) {
                Style.SPIN, Style.FLIP_X, Style.FLIP_Y, Style.ORBIT -> LinearInterpolator()
                Style.POP, Style.TADA, Style.JELLO, Style.RUBBER, Style.ELASTIC,
                Style.SPIN_POP, Style.LOGO_BOUNCE -> OvershootInterpolator(1.35f)
                else -> AccelerateDecelerateInterpolator()
            })
        }
    }

    // -- request -------------------------------------------------------------

    private class Request(
        private val view: ImageView,
        private val style: Style,
        private val seed: Int
    ) : View.OnAttachStateChangeListener, DefaultLifecycleObserver, InteractionListener {

        private var owner: LifecycleOwner? = null
        private var target: View? = null
        private var wiring: InteractionWiring? = null
        private var focusObserver: ViewTreeObserver? = null

        private var hovered = false
        private var pressed = false
        private var focused = false
        /** Interaction ended: let the current sweep finish, then rest. */
        private var finishing = false
        private var running = false
        private var animator: AnimatorSet? = null
        private var closed = false

        override fun onHover(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE ->
                    if (!hovered) { hovered = true; update() }
                MotionEvent.ACTION_HOVER_EXIT ->
                    if (hovered) { hovered = false; finishSoon() }
            }
        }

        override fun onTouch(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { pressed = true; update() }
                MotionEvent.ACTION_UP -> { pressed = false; finishSoon() }
                MotionEvent.ACTION_CANCEL -> { pressed = false; finishSoon() }
            }
        }

        private val focusListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            val now = newFocus != null && (newFocus === view || InteractionWiring.isWithin(newFocus, target))
            if (now != focused) { focused = now; if (now) update() else finishSoon() }
        }

        init {
            view.addOnAttachStateChangeListener(this)
            if (view.isAttachedToWindow) onViewAttachedToWindow(view)
        }

        private fun isInteracting() = hovered || pressed || focused

        private fun update() {
            if (isInteracting()) {
                finishing = false
                startCycle()
            } else {
                finishSoon()
            }
        }

        private fun finishSoon() {
            finishing = true
            if (!running) { finishing = false; resetProps() }
        }

        fun playOnceIfIdle() {
            if (running || closed || !TurtleTransitions.isEnabled()) return
            finishing = true
            startCycle()
        }

        private fun startCycle() {
            if (running || closed || !TurtleTransitions.isEnabled()) return
            when (style) {
                // Hang from the top edge like a bell.
                Style.SWING, Style.PENDULUM -> {
                    view.pivotX = view.width / 2f
                    view.pivotY = 0f
                }
                // Hinge from the bottom edge.
                Style.SWING_Y, Style.LOGO_SWAY -> {
                    view.pivotX = view.width / 2f
                    view.pivotY = view.height.toFloat()
                }
                // Off-centre rotation: a true orbit instead of a plain spin.
                Style.ORBIT -> {
                    view.pivotX = view.width * 0.15f
                    view.pivotY = view.height / 2f
                }
                else -> {
                    view.pivotX = view.width / 2f
                    view.pivotY = view.height / 2f
                }
            }
            running = true
            animator = cycle(view, style, seed).apply {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        running = false
                        animator = null
                        resetProps()
                        // Hover/press/focus keeps looping; a finished tap rests.
                        if (!closed && isInteracting()) startCycle() else finishing = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        running = false
                        animator = null
                        resetProps()
                    }
                })
                start()
            }
        }

        private fun resetProps() {
            view.scaleX = 1f; view.scaleY = 1f
            view.rotation = 0f; view.rotationX = 0f; view.rotationY = 0f
            view.translationX = 0f; view.translationY = 0f
            view.alpha = 1f
            view.pivotX = view.width / 2f; view.pivotY = view.height / 2f
        }

        private fun stopNow() {
            finishing = false
            animator?.cancel() // cancel() also resets via the listener
            animator = null
            running = false
            resetProps()
        }

        // -- wiring / lifecycle (same contract as AnimatedLogo) --------------

        override fun onViewAttachedToWindow(v: View) {
            owner = view.findViewTreeLifecycleOwner()
            owner?.lifecycle?.addObserver(this)
            removeWiring()
            val resolved = InteractionWiring.resolveTarget(view)
            target = resolved
            wiring = InteractionWiring.of(resolved).also { it.attach(this) }
            hovered = resolved.isHovered
            focused = resolved.isFocused || view.isFocused
            val observer = view.viewTreeObserver
            if (observer.isAlive) {
                observer.addOnGlobalFocusChangeListener(focusListener)
                focusObserver = observer
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            hovered = false; pressed = false; focused = false
            stopNow()
            removeWiring()
            owner?.lifecycle?.removeObserver(this)
            owner = null
        }

        private fun removeWiring() {
            wiring?.detach(this)
            wiring = null
            target = null
            focusObserver?.let { if (it.isAlive) it.removeOnGlobalFocusChangeListener(focusListener) }
            focusObserver = null
        }

        override fun onPause(owner: LifecycleOwner) {
            pressed = false
            stopNow()
        }

        override fun onDestroy(owner: LifecycleOwner) = close()

        fun close() {
            if (closed) return
            closed = true
            stopNow()
            removeWiring()
            owner?.lifecycle?.removeObserver(this)
            owner = null
            view.removeOnAttachStateChangeListener(this)
        }
    }
}
