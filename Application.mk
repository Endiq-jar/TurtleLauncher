# NDK_TOOLCHAIN_VERSION := 4.9
APP_PLATFORM := android-21
# TurtleLauncher CI fix: was APP_STL := system, which provides no real libc++ on
# modern NDK. The mmkv prefab dependency (see build.gradle.kts) ships a native
# library built against libc++ and needs a real STL to link against - "system"
# made ndk-build fail during configureNdkBuildDebug with "User requested no STL
# but library requires libc++" on 64-bit ABIs. c++_shared satisfies that.
APP_STL := c++_shared
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
