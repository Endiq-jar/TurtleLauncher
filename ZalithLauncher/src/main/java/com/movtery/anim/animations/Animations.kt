package com.movtery.anim.animations

import com.movtery.anim.animations.bounce.BounceEnlargeAnimator
import com.movtery.anim.animations.bounce.BounceInDownAnimator
import com.movtery.anim.animations.bounce.BounceInLeftAnimator
import com.movtery.anim.animations.bounce.BounceInRightAnimator
import com.movtery.anim.animations.bounce.BounceInUpAnimator
import com.movtery.anim.animations.bounce.BounceShrinkAnimator
import com.movtery.anim.animations.fade.FadeInAnimator
import com.movtery.anim.animations.fade.FadeInDownAnimator
import com.movtery.anim.animations.fade.FadeInLeftAnimator
import com.movtery.anim.animations.fade.FadeInRightAnimator
import com.movtery.anim.animations.fade.FadeInUpAnimator
import com.movtery.anim.animations.fade.FadeOutAnimator
import com.movtery.anim.animations.fade.FadeOutDownAnimator
import com.movtery.anim.animations.fade.FadeOutLeftAnimator
import com.movtery.anim.animations.fade.FadeOutRightAnimator
import com.movtery.anim.animations.fade.FadeOutUpAnimator
import com.movtery.anim.animations.other.PulseAnimator
import com.movtery.anim.animations.other.ShakeAnimator
import com.movtery.anim.animations.other.WobbleAnimator
import com.movtery.anim.animations.slide.SlideInDownAnimator
import com.movtery.anim.animations.slide.SlideInLeftAnimator
import com.movtery.anim.animations.slide.SlideInRightAnimator
import com.movtery.anim.animations.slide.SlideInUpAnimator
import com.movtery.anim.animations.slide.SlideOutDownAnimator
import com.movtery.anim.animations.slide.SlideOutLeftAnimator
import com.movtery.anim.animations.slide.SlideOutRightAnimator
import com.movtery.anim.animations.slide.SlideOutUpAnimator
import com.movtery.anim.animations.zoom.ZoomInAnimator
import com.movtery.anim.animations.zoom.ZoomInDownAnimator
import com.movtery.anim.animations.zoom.ZoomInLeftAnimator
import com.movtery.anim.animations.zoom.ZoomInRightAnimator
import com.movtery.anim.animations.zoom.ZoomInUpAnimator
import com.movtery.anim.animations.zoom.ZoomOutAnimator
import com.movtery.anim.animations.zoom.ZoomOutDownAnimator
import com.movtery.anim.animations.zoom.ZoomOutLeftAnimator
import com.movtery.anim.animations.zoom.ZoomOutRightAnimator
import com.movtery.anim.animations.zoom.ZoomOutUpAnimator
import com.movtery.anim.animations.sheet.SheetInAnimator
import com.movtery.anim.animations.sheet.SheetOutAnimator


enum class Animations(val animator: BaseAnimator) {
    //Bounce
    BounceInDown(BounceInDownAnimator()),
    BounceInLeft(BounceInLeftAnimator()),
    BounceInRight(BounceInRightAnimator()),
    BounceInUp(BounceInUpAnimator()),
    BounceEnlarge(BounceEnlargeAnimator()),
    BounceShrink(BounceShrinkAnimator()),

    //Fade in
    FadeIn(FadeInAnimator()),
    FadeInLeft(FadeInLeftAnimator()),
    FadeInRight(FadeInRightAnimator()),
    FadeInUp(FadeInUpAnimator()),
    FadeInDown(FadeInDownAnimator()),

    //Fade out
    FadeOut(FadeOutAnimator()),
    FadeOutLeft(FadeOutLeftAnimator()),
    FadeOutRight(FadeOutRightAnimator()),
    FadeOutUp(FadeOutUpAnimator()),
    FadeOutDown(FadeOutDownAnimator()),

    //Slide in
    SlideInLeft(SlideInLeftAnimator()),
    SlideInRight(SlideInRightAnimator()),
    SlideInUp(SlideInUpAnimator()),
    SlideInDown(SlideInDownAnimator()),

    //Slide out
    SlideOutLeft(SlideOutLeftAnimator()),
    SlideOutRight(SlideOutRightAnimator()),
    SlideOutUp(SlideOutUpAnimator()),
    SlideOutDown(SlideOutDownAnimator()),


    //Zoom - the animation library shipped with no zoom animators at all, so these are new
    // (see ZoomInAnimator et al). Direction variants also drift in from that side.
    ZoomIn(ZoomInAnimator()),
    ZoomInDown(ZoomInDownAnimator()),
    ZoomInLeft(ZoomInLeftAnimator()),
    ZoomInRight(ZoomInRightAnimator()),
    ZoomInUp(ZoomInUpAnimator()),
    ZoomOut(ZoomOutAnimator()),
    ZoomOutDown(ZoomOutDownAnimator()),
    ZoomOutLeft(ZoomOutLeftAnimator()),
    ZoomOutRight(ZoomOutRightAnimator()),
    ZoomOutUp(ZoomOutUpAnimator()),
    //Sheet - enters from below the screen edge and settles with a bounce. This is what a
    // settings-style panel opened from the home screen should feel like: it comes up off the
    // bottom of the display rather than sliding a fixed distance from somewhere mid-screen.
    SheetIn(SheetInAnimator()),
    SheetOut(SheetOutAnimator()),

    //Other
    Pulse(PulseAnimator()),
    Wobble(WobbleAnimator()),
    Shake(ShakeAnimator())
}