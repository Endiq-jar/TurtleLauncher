package net.endiq.launcher.prefs;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;
import static net.endiq.launcher.Architecture.is32BitsDevice;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;

import com.endiq.turtlelauncher.feature.log.Logging;
import com.endiq.turtlelauncher.feature.unpack.Jre;
import com.endiq.turtlelauncher.setting.AllSettings;
import com.endiq.turtlelauncher.setting.AllStaticSettings;
import com.endiq.turtlelauncher.setting.Settings;
import com.endiq.turtlelauncher.ui.activity.BaseActivity;

import net.endiq.launcher.Tools;
import net.endiq.launcher.multirt.MultiRTUtils;
import net.endiq.launcher.utils.JREUtils;

import java.util.Arrays;
import java.util.List;

public class LauncherPreferences {
    public static void loadPreferences() {
        String javaArgs = AllSettings.getJavaArgs().getValue();
        // One-time migration of the exact old stock JVM preset (see the javaArgs
        // default in AllSettings): it pinned -XX:MaxGCPauseMillis=4, which keeps
        // G1 cycling almost constantly (sustained GC CPU + device heat), and
        // -XX:+AlwaysPreTouch without the heap-share safety guard. Only an exact
        // token-for-token match is replaced, so user-customized args (even ones
        // that merely contain similar tokens) are never rewritten.
        List<String> oldStockArgs = Arrays.asList(
                "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=4", "-XX:G1HeapRegionSize=32M",
                "-XX:+UnlockExperimentalVMOptions", "-XX:G1NewSizePercent=40", "-XX:G1ReservePercent=15",
                "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:+PerfDisableSharedMem",
                "-XX:+UseStringDeduplication");
        if (JREUtils.parseJavaArguments(javaArgs).equals(oldStockArgs)) {
            javaArgs = AllSettings.getJavaArgs().getDefaultValue();
            AllSettings.getJavaArgs().put(javaArgs).save();
            Logging.i("LauncherPreferences", "Migrated stock JVM args to the cooler defaults");
        }
        String argLwjglLibname = "-Dorg.lwjgl.opengl.libname=";
        for (String arg : JREUtils.parseJavaArguments(javaArgs)) {
            if (arg.startsWith(argLwjglLibname)) {
                // purge arg
                AllSettings.getJavaArgs().put(javaArgs.replace(arg, "")).save();
            }
        }

        reloadRuntime();
    }

    public static void reloadRuntime() {
        if (!Settings.Manager.contains("defaultRuntime") && !MultiRTUtils.getRuntimes().isEmpty()) {
            // Set the default runtime.
            AllSettings.getDefaultRuntime().put("Internal-17").save();
        }
    }

    /**
     * This functions aims at finding the best default RAM amount,
     * according to the RAM amount of the physical device.
     * Put not enough RAM ? Minecraft will lag and crash.
     * Put too much RAM ?
     * The GC will lag, android won't be able to breathe properly.
     * @param ctx Context needed to get the total memory of the device.
     * @return The best default value found.
     */
    public static int findBestRAMAllocation(Context ctx){
        int deviceRam = Tools.getTotalDeviceMemory(ctx);

        if (is32BitsDevice()) {
            if (deviceRam < 1024) return 384;
            if (deviceRam < 1536) return 512;
            return 696; // hard cap regardless of how much more physical RAM exists
        }

        if (deviceRam < 1024) return 384;
        if (deviceRam < 1536) return 512;
        if (deviceRam < 2048) return 768;
        if (deviceRam < 4096) return 1536;
        if (deviceRam < 8192) return 2058;
        if (deviceRam < 12288) return 3072; // 8-12GB devices
        if (deviceRam < 16384) return 4096; // 12-16GB devices
        return 6144;                        // 16GB+ devices
    }

    /** Compute the notch size to avoid being out of bounds */
    public static void computeNotchSize(BaseActivity activity) {
        if (Build.VERSION.SDK_INT < P) return;
        try {
            final Rect cutout;
            if(SDK_INT >= Build.VERSION_CODES.S){
                cutout = activity.getWindowManager().getCurrentWindowMetrics().getWindowInsets().getDisplayCutout().getBoundingRects().get(0);
            } else {
                cutout = activity.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout().getBoundingRects().get(0);
            }

            // Notch values are rotation sensitive, handle all cases
            int orientation = activity.getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_PORTRAIT) AllStaticSettings.notchSize = cutout.height();
            else if (orientation == Configuration.ORIENTATION_LANDSCAPE) AllStaticSettings.notchSize = cutout.width();
            else AllStaticSettings.notchSize = Math.min(cutout.width(), cutout.height());

        }catch (Exception e){
            Logging.i("NOTCH DETECTION", "No notch detected, or the device if in split screen mode");
            AllStaticSettings.notchSize = -1;
        }
        Tools.updateWindowSize(activity);
    }
}
