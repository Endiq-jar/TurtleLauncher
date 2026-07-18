package com.movtery.zalithlauncher.launch

import com.movtery.zalithlauncher.feature.log.Logging
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * TurtleLauncher: best-effort VirGL support via Mesa's software vtest transport.
 *
 * UNTESTED ON A REAL DEVICE — flagged deliberately, not a hedge. Real Android phones
 * have no virtio-gpu kernel device, so VirGL's normal "hardware" gallium path
 * (GALLIUM_DRIVER=virgl talking straight to /dev/virtio-gpu, the way Android
 * emulators/Cuttlefish do it) isn't available here. The only path that can work on
 * physical hardware is Mesa's vtest transport: a local "virgl_test_server" process does
 * the actual GL work, and the game process talks to it over a Unix socket as
 * GALLIUM_DRIVER=virpipe — the same mechanism community Termux desktop-GL-on-Android
 * setups use. This project already bundles the vtest server binary
 * (libvirgl_test_server.so, present on every ABI including arm64-v8a) and VirglRenderer
 * reuses the same libgallium_dri.so already used by the Zink renderer path as the client
 * driver — but whether that particular Mesa build actually has the virpipe/virgl gallium
 * driver compiled in has NOT been verified against a real device. If it doesn't,
 * GALLIUM_DRIVER=virpipe just fails to find a usable driver and Minecraft won't start
 * under this renderer specifically; every other renderer is unaffected either way.
 *
 * Socket path matches the VTEST_SOCKET_NAME VulkanZinkRenderer already referenced
 * (previously vestigial - nothing was starting a server for it to actually connect to).
 */
object VirglTestServerManager {
    private const val TAG = "VirglTestServerManager"
    private const val SERVER_LIB = "libvirgl_test_server.so"

    val socketPath: String by lazy { File(PathManager.DIR_CACHE, ".virgl_test").absolutePath }

    @Volatile
    private var serverProcess: Process? = null

    /**
     * Starts the vtest server bound to [socketPath]. Returns true if the process
     * launched — NOT a guarantee it's actually serving; there's no real handshake here,
     * just a short grace period for the socket file to appear before the game process
     * (started right after this returns) tries to connect to it.
     */
    @JvmStatic
    fun start(): Boolean {
        stop() // clean up any leftover instance from a previous crashed launch
        File(socketPath).delete()

        val serverBinary = File(PathManager.DIR_NATIVE_LIB, SERVER_LIB)
        if (!serverBinary.exists()) {
            Logging.e(TAG, "$SERVER_LIB not found in ${PathManager.DIR_NATIVE_LIB} - can't start the VirGL transport")
            return false
        }

        return try {
            val process = ProcessBuilder(serverBinary.absolutePath)
                .redirectErrorStream(true)
                .apply { environment()["VTEST_SOCKET_NAME"] = socketPath }
                .start()
            serverProcess = process

            var waited = 0
            while (!File(socketPath).exists() && waited < 2000) {
                Thread.sleep(50)
                waited += 50
            }
            if (!File(socketPath).exists()) {
                Logging.w(TAG, "vtest server started but its socket never appeared after ${waited}ms - continuing anyway, the game launch may fail")
            }
            true
        } catch (e: Exception) {
            Logging.e(TAG, "Failed to start the vtest server", e)
            serverProcess = null
            false
        }
    }

    /** Always safe to call, including when no server is running. */
    @JvmStatic
    fun stop() {
        val process = serverProcess ?: return
        serverProcess = null
        runCatching { process.destroy() }
        runCatching {
            if (process.isAlive && !process.waitFor(500, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly()
            }
        }
        runCatching { File(socketPath).delete() }
    }
}
