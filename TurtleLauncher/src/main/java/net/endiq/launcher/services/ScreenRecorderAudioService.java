package net.endiq.launcher.services;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.ResultReceiver;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.endiq.turtlelauncher.R;
import com.endiq.turtlelauncher.feature.log.Logging;

import net.endiq.launcher.Tools;
import net.endiq.launcher.utils.NotificationUtils;

/**
 * Foreground-service owner for AudioPlaybackCapture's MediaProjection.
 *
 * On Android 10+ MediaProjection must not be obtained until a foreground service of type
 * mediaProjection is actually foreground. startForegroundService() is asynchronous, so callers
 * must wait for RESULT_READY instead of calling MediaProjectionManager#getMediaProjection
 * immediately after start(). Doing both back-to-back races the service and crashes on devices
 * that enforce the requirement strictly (especially Android 14+).
 */
public class ScreenRecorderAudioService extends Service {
    public static final int RESULT_READY = 1;
    public static final int RESULT_FAILED = 0;
    private static final String EXTRA_READY_RECEIVER =
            "net.endiq.launcher.services.ScreenRecorderAudioService.READY_RECEIVER";
    private static final String TAG = "ScreenRecorderAudioService";

    public static void start(Context context) {
        start(context, null);
    }

    public static void start(Context context, @Nullable ResultReceiver readyReceiver) {
        Intent intent = new Intent(context, ScreenRecorderAudioService.class);
        if (readyReceiver != null) intent.putExtra(EXTRA_READY_RECEIVER, readyReceiver);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, ScreenRecorderAudioService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Tools.buildNotificationChannel(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ResultReceiver readyReceiver = null;
        if (intent != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                readyReceiver = intent.getParcelableExtra(EXTRA_READY_RECEIVER, ResultReceiver.class);
            } else {
                //noinspection deprecation
                readyReceiver = (ResultReceiver) intent.getParcelableExtra(EXTRA_READY_RECEIVER);
            }
        }
        try {
            Notification notification = new NotificationCompat.Builder(this, Tools.NOTIFICATION_CHANNEL_DEFAULT)
                    .setContentTitle("Recording game audio")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setSilent(true)
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NotificationUtils.NOTIFICATION_ID_RECORDING_AUDIO, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NotificationUtils.NOTIFICATION_ID_RECORDING_AUDIO, notification);
            }
            if (readyReceiver != null) readyReceiver.send(RESULT_READY, null);
            return START_NOT_STICKY;
        } catch (Throwable throwable) {
            // A foreground-service policy/permission failure must not bring down the entire game
            // process. Tell ScreenRecorder to continue video-only and tear this service down.
            Logging.e(TAG, "Unable to start media-projection foreground service", throwable);
            if (readyReceiver != null) readyReceiver.send(RESULT_FAILED, null);
            stopSelf(startId);
            return START_NOT_STICKY;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
