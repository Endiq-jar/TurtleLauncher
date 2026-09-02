# NDK_TOOLCHAIN_VERSION := 4.9
APP_PLATFORM := android-21
# TurtleLauncher CI fix: was APP_STL := system, which provides no real libc++ on
# modern NDK. Prefab dependencies (e.g. bytehook) need a real STL to link against
# on 64-bit ABIs - "system" made ndk-build fail during configureNdkBuildDebug
# with "User requested no STL but library requires libc++". c++_shared satisfies
# that. (MMKV is deliberately NOT a dependency - its prefab libs are built with a
# statically-linked STL and are missing 32-bit variants, which AGP rejects when
# the native build is enabled: CXX1210/CXX1211.)
APP_STL := c++_shared
APP_ABI := armeabi-v7a arm64-v8a x86 x86_64
