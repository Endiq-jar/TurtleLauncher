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
			try {
				Logging.e(CRASH_REPORT_TAG, " - Exception attempt saving crash stack trace:", throwable);
				Logging.e(CRASH_REPORT_TAG, " - The crash stack trace was:", th);
			} catch (Throwable ignored) {
				Log.e(CRASH_REPORT_TAG, "Failed to log crash", th);
			}
		}

		try {
			// Do not kill the process immediately after startActivity(). On some Android 13
			// devices (notably ColorOS) the activity launch transaction is asynchronous; the
			// old code killed :launcher before ErrorActivity could draw, making a launcher
			// exception look like a silent instant close. Give the system a short window to
			// attach and render the diagnostic screen. The delayed kill still prevents a
			// broken launcher process from remaining alive if the screen cannot be shown.
			ErrorActivity.showLauncherCrash(PojavApplication.this, crashFile.getAbsolutePath(), th);
			new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
					ZHTools::killProcess, 1200L);
		} catch (Throwable errorActivityFailure) {
			Log.e(CRASH_REPORT_TAG, "Failed to start ErrorActivity", errorActivityFailure);
			// A crash during very early application startup may not have a usable main
			// looper. Preserve the original behaviour in that rare case, but the report has
			// already been written above and can be retrieved from launcher_log/latestcrash.txt.
			ZHTools.killProcess();
		}
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
		try {
			androidx.startup.AppInitializer.getInstance(this)
				.initializeComponent(com.movtery.zalithlauncher.startup.TurtleStartupInitializer.class);
		} catch (Throwable t) {
			Log.e(CRASH_REPORT_TAG, "TurtleStartupInitializer failed", t);
		}
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
