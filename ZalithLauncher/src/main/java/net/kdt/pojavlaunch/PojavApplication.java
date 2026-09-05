package net.kdt.pojavlaunch;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.movtery.zalithlauncher.utils.ZHTools.getVersionCode;
import static com.movtery.zalithlauncher.utils.ZHTools.getVersionName;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.movtery.zalithlauncher.InfoDistributor;
import com.movtery.zalithlauncher.context.ContextExecutor;
import com.movtery.zalithlauncher.context.LocaleHelper;
import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.setting.AllSettings;
import com.movtery.zalithlauncher.ui.activity.ErrorActivity;
import com.movtery.zalithlauncher.utils.path.PathManager;
import com.movtery.zalithlauncher.utils.ZHTools;

import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

public class PojavApplication extends Application {
	public static final String CRASH_REPORT_TAG = "ZalithCrashReport";

	@Override
	public void onCreate() {
		ContextExecutor.setApplication(this);

		Thread.setDefaultUncaughtExceptionHandler((thread, th) -> {
			boolean storagePermAllowed = (Build.VERSION.SDK_INT >= 29 || ActivityCompat.checkSelfPermission(PojavApplication.this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) && Tools.checkStorageRoot();
			File crashFile = new File(storagePermAllowed ? PathManager.DIR_LAUNCHER_LOG : PathManager.DIR_DATA, "latestcrash.txt");
			try {
				// Same file NativeCrashCapture writes native/ANR deaths to - they used to have
				// separate filenames specifically to avoid overwriting each other; now that
				// they share one name, PREPEND (newest report first) and cap total size instead
				// of truncating on write, so this crash doesn't erase a different-typed one.
				FileUtils.ensureParentDirectory(crashFile);
				StringBuilder report = new StringBuilder();
				report.append(InfoDistributor.APP_NAME).append(" crash report\n");
				report.append(" - Time: ").append(DateFormat.getDateTimeInstance().format(new Date())).append("\n");
				report.append(" - Device: ").append(Build.PRODUCT).append(" ").append(Build.MODEL).append("\n");
				report.append(" - Android version: ").append(Build.VERSION.RELEASE).append("\n");
				report.append(" - Launcher version: ").append(getVersionName()).append(" (").append(String.valueOf(getVersionCode())).append(")").append("\n");
				report.append(" - Crash stack trace:\n");
				report.append(Log.getStackTraceString(th));

				String previous = null;
				if (crashFile.isFile()) {
					try {
						previous = new String(java.nio.file.Files.readAllBytes(crashFile.toPath()));
					} catch (Throwable ignored) { /* fall through with previous == null */ }
				}
				String combined = (previous == null || previous.trim().isEmpty())
					? report.toString()
					: report + "\n\n════════ earlier report(s) below ════════\n\n" + previous;
				final int maxChars = 256 * 1024;
				if (combined.length() > maxChars) combined = combined.substring(0, maxChars);

				PrintStream crashStream = new PrintStream(crashFile);
				crashStream.print(combined);
				crashStream.close();
			} catch (Throwable throwable) {
				Logging.e(CRASH_REPORT_TAG, " - Exception attempt saving crash stack trace:", throwable);
				Logging.e(CRASH_REPORT_TAG, " - The crash stack trace was:", th);
			}

			ErrorActivity.showLauncherCrash(PojavApplication.this, crashFile.getAbsolutePath(), th);

			// TurtleLauncher CRASH FIX (launcher closing instantly on open with no
			// logs): ErrorActivity used to run in this same process, so this
			// Process.killProcess() call - fired right after the startActivity()
			// above - was racing the system's own async activity-launch IPC and
			// regularly won, killing this process (and ErrorActivity along with it,
			// since it hadn't been declared with its own android:process yet)
			// before the crash screen ever got a window. ErrorActivity now runs in
			// its own ":crash" process (see AndroidManifest.xml), so it's no longer
			// at risk from this process dying.
			//
			// TurtleLauncher CRASH FIX (kept startup crash diagnostics visible on
			// Android 13 / ColorOS): ColorOS (OPPO/OnePlus/realme's Android skin) is
			// documented, in multiple public crash-reporting-library issue trackers
			// (ACRA, Bugsnag), to sometimes silently drop or badly delay a
			// startActivity() call made from an uncaught-exception handler - its own
			// background-activity-start throttling layers on top of stock Android's,
			// and reports of it appear concentrated on Android 13 builds
			// specifically. There's no supported API to detect or wait on that from
			// here, so this is a best-effort mitigation (a longer delay for
			// ColorOS/ColorOS-family devices, giving its async launch pipeline more
			// real wall-clock time to act before this process disappears), not a
			// confirmed fix - it hasn't been verified against a real affected
			// device.
			try {
				Thread.sleep(isLikelyColorOS() ? 1500 : 750);
			} catch (InterruptedException ignored) {
			}
			ZHTools.killProcess();
		});

		try {
			super.onCreate();
			PathManager.DIR_DATA = getDir("files", MODE_PRIVATE).getParent();
			PathManager.DIR_CACHE = getCacheDir();
			PathManager.DIR_ACCOUNT_NEW = PathManager.DIR_DATA + "/accounts";
			Tools.DEVICE_ARCHITECTURE = Architecture.getDeviceArchitecture();
			//Force x86 lib directory for Asus x86 based zenfones
			if(Architecture.isx86Device() && Architecture.is32BitsDevice()){
				String originalJNIDirectory = getApplicationInfo().nativeLibraryDir;
				getApplicationInfo().nativeLibraryDir = originalJNIDirectory.substring(0,
												originalJNIDirectory.lastIndexOf("/"))
												.concat("/x86");
			}
		} catch (Throwable throwable) {
			Intent ferrorIntent = new Intent(this, ErrorActivity.class);
			ferrorIntent.putExtra("throwable", throwable);
			ferrorIntent.setFlags(FLAG_ACTIVITY_NEW_TASK);
			startActivity(ferrorIntent);
		}

		// TurtleLauncher: AnrWatchdog, dark mode, and dynamic color theming - see
		// TurtleStartupInitializer for why this is triggered on-demand here rather than
		// via AndroidX Startup's automatic pre-onCreate discovery.
		androidx.startup.AppInitializer.getInstance(this)
			.initializeComponent(com.movtery.zalithlauncher.startup.TurtleStartupInitializer.class);
	}

	/**
	 * Best-effort ColorOS (OPPO/OnePlus/realme) detection for the crash-kill delay
	 * in onCreate()'s uncaught-exception handler above - Build.MANUFACTURER/
	 * Build.BRAND are the standard, widely-used way to spot this OEM family
	 * (there's no public API that reports "is ColorOS" or its version directly).
	 */
	private static boolean isLikelyColorOS() {
		String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT) : "";
		String brand = Build.BRAND != null ? Build.BRAND.toLowerCase(java.util.Locale.ROOT) : "";
		return manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme")
				|| brand.contains("oppo") || brand.contains("oneplus") || brand.contains("realme");
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		ContextExecutor.clearApplication();
	}

	@Override
	public void onTrimMemory(int level) {
		super.onTrimMemory(level);
		//智能内存管理：系统发出内存压力信号时，主动收缩图片缓存，而不是等到真的OOM才处理。
		//游戏运行期间内存最宝贵，这里趁早把不必要的缓存让出去给游戏本体用
		try {
			com.bumptech.glide.Glide.get(this).trimMemory(level);
		} catch (Throwable t) {
			Logging.e(CRASH_REPORT_TAG, "Failed to trim Glide memory", t);
		}
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		try {
			com.bumptech.glide.Glide.get(this).clearMemory();
		} catch (Throwable t) {
			Logging.e(CRASH_REPORT_TAG, "Failed to clear Glide memory", t);
		}
	}

	@Override
    protected void attachBaseContext(Context base) {
		ContextExecutor.setApplication(this);
        super.attachBaseContext(LocaleHelper.Companion.setLocale(base));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
		ContextExecutor.setApplication(this);
		LocaleHelper.Companion.setLocale(this);
    }
}
