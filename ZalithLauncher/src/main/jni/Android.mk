LOCAL_PATH := $(call my-dir)
HERE_PATH := $(LOCAL_PATH)

# include $(HERE_PATH)/crash_dump/libbase/Android.mk
# include $(HERE_PATH)/crash_dump/libbacktrace/Android.mk
# include $(HERE_PATH)/crash_dump/debuggerd/Android.mk


LOCAL_PATH := $(HERE_PATH)

$(call import-module,prefab/bytehook)
LOCAL_PATH := $(HERE_PATH)


include $(CLEAR_VARS)
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_MODULE := pojavexec
LOCAL_SHARED_LIBRARIES := driver_helper
LOCAL_CFLAGS += -rdynamic
LOCAL_SRC_FILES := \
    bigcoreaffinity.c \
    egl_bridge.c \
    ctxbridges/br_loader.c \
    ctxbridges/gl_bridge.c \
    ctxbridges/osm_bridge.c \
    ctxbridges/egl_loader.c \
    ctxbridges/osmesa_loader.c \
    ctxbridges/swap_interval_no_egl.c \
    ctxbridges/virgl_bridge.c \
    environ/environ.c \
    input_bridge_v3.c \
    jre_launcher.c \
    utils.c \
    stdio_is.c \
    java_exec_hooks.c \
    lwjgl_dlopen_hook.c \
    logger/zl_log.c

ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS += -DADRENO_POSSIBLE
LOCAL_LDLIBS += -lEGL -lGLESv2
endif
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := exithook
LOCAL_LDLIBS := -ldl -llog
LOCAL_SHARED_LIBRARIES := bytehook pojavexec
LOCAL_SRC_FILES := exit_hook.c
include $(BUILD_SHARED_LIBRARY)


# TurtleLauncher CRASH FIX (MC 26.3+ SDL3 SIGSEGV): process-wide native hook of
# SDL_InitSubSystem - see sdl_hook.c's file doc for why this exists alongside
# (not instead of) SdlAndroidJniPrep's Java-side token-Surface workaround.
include $(CLEAR_VARS)
LOCAL_MODULE := sdlhook
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_SHARED_LIBRARIES := bytehook
LOCAL_SRC_FILES := \
    sdl_hook.c \
    logger/zl_log.c
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_LDLIBS := -ldl -llog -landroid
LOCAL_MODULE := driver_helper
LOCAL_SRC_FILES := \
    driver_helper/driver_helper.c \
    driver_helper/nsbypass.c
LOCAL_CFLAGS += -g -rdynamic

ifeq ($(TARGET_ARCH_ABI),arm64-v8a)
LOCAL_CFLAGS += -DADRENO_POSSIBLE
LOCAL_LDLIBS += -lEGL -lGLESv2
endif
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := linkerhook
LOCAL_SRC_FILES := \
    linkerhook/linkerhook.cpp \
    linkerhook/linkerns.c
LOCAL_LDFLAGS := -z global
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := pojavexec_awt
LOCAL_SRC_FILES := \
    awt_bridge.c
include $(BUILD_SHARED_LIBRARY)


include $(CLEAR_VARS)
LOCAL_MODULE := awt_headless
# STATIC, not SHARED: this module has zero real source and exists only to
# satisfy awt_xawt's link-time dependency below. A SHARED build produces an
# installable .so that AGP's mergeDebugNativeLibs can pick up in place of the
# real bundled jniLibs/*/libawt_headless.so that JREUtils.java actually
# dlopen()s at runtime - which is exactly the cause of the Font.initIDs
# UnsatisfiedLinkError/SIGSEGV crash. STATIC never gets packaged, so the
# real prebuilt always wins with no rm hack needed.
include $(BUILD_STATIC_LIBRARY)


LOCAL_PATH := $(HERE_PATH)/awt_xawt
include $(CLEAR_VARS)
LOCAL_MODULE := awt_xawt
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
LOCAL_STATIC_LIBRARIES := awt_headless
LOCAL_SRC_FILES := xawt_fake.c
include $(BUILD_SHARED_LIBRARY)


# delete stale prebuilts so the freshly source-built versions replace them.
# NOTE: this $(shell) runs at makefile PARSE time, before any module actually
# compiles - fine here since libawt_xawt.so/libdriver_helper.so are both
# genuinely meant to be replaced by their from-source build (xawt_fake.c is
# an intentional dummy matching the runtime already installs via
# MultiRTUtils.copyDummyNativeLib; driver_helper.c is the real, current
# implementation). Do NOT add libawt_headless.so back here - see the static
# library note above for why.
$(info $(shell (rm $(HERE_PATH)/../jniLibs/*/libawt_xawt.so)))
$(info $(shell (rm $(HERE_PATH)/../jniLibs/*/libdriver_helper.so)))

