package com.endiq.turtlelauncher.feature.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The launcher ships one Horizon engine APK per architecture, so the pick between them is the
 * only thing standing between "the engine is built in" and "Android refused to install that file".
 */
class HorizonEngineAbiTest {
    private val bundled = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    @Test fun picksTheDevicePreferredEngine() {
        assertEquals("arm64-v8a", HorizonEngineInstaller.pickAbi(listOf("arm64-v8a", "armeabi-v7a"), bundled))
        assertEquals("x86_64", HorizonEngineInstaller.pickAbi(listOf("x86_64", "x86"), bundled))
        assertEquals("armeabi-v7a", HorizonEngineInstaller.pickAbi(listOf("armeabi-v7a"), bundled))
    }

    @Test fun respectsDeviceOrderWhenSeveralAreSupported() {
        // 32-bit-only devices report armeabi-v7a first; a device that lists both gets the 64-bit one.
        assertEquals("armeabi-v7a", HorizonEngineInstaller.pickAbi(listOf("armeabi-v7a", "arm64-v8a"), bundled))
    }

    @Test fun understandsAbiAliases() {
        assertEquals("arm64-v8a", HorizonEngineInstaller.pickAbi(listOf("arm64"), bundled))
        assertEquals("x86_64", HorizonEngineInstaller.pickAbi(listOf("x86_64"), listOf("x86_64")))
        assertEquals("x86", HorizonEngineInstaller.pickAbi(listOf("x86"), listOf("x86")))
    }

    @Test fun fallsBackToWhateverThisBuildCarries() {
        // A build that only carries one engine still beats no engine: Android rejects it if wrong,
        // and the launcher then uses the on-device engine.
        assertEquals("arm64-v8a", HorizonEngineInstaller.pickAbi(listOf("mips"), listOf("arm64-v8a")))
        assertNull(HorizonEngineInstaller.pickAbi(listOf("arm64-v8a"), emptyList()))
        assertNull(HorizonEngineInstaller.pickAbi(emptyList(), emptyList()))
    }

    @Test fun assetNamesArePerArchitecture() {
        assertEquals("horizon/horizon-engine-arm64-v8a.apk", HorizonEngineInstaller.bundledAssetName("arm64-v8a"))
        assertEquals("horizon/horizon-engine-x86_64.apk", HorizonEngineInstaller.bundledAssetName("x86_64"))
        assertEquals(listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64"), HorizonEngineInstaller.BUNDLED_ABIS)
    }

    @Test fun bundledVersionIsTheCurrentHorizonRelease() {
        assertEquals("0.7.7", HorizonEngineInstaller.BUNDLED_VERSION)
    }
}
