package com.movtery.zalithlauncher.feature.terracotta;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.movtery.zalithlauncher.feature.log.Logging;
import com.movtery.zalithlauncher.task.TaskExecutors;

import net.burningtnt.terracotta.TerracottaAndroidAPI;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * Orchestration layer around TerracottaAndroidAPI - adapted from FoldCraftLauncher's
 * Terracotta.java (FCL-Team/FoldCraftLauncher, GPLv3). Simplified: uses a plain
 * listener list + TaskExecutors.runInUIThread for state updates instead of FCL's
 * fclcore FX-style ReadOnlyObjectProperty/InvocationDispatcher, since that
 * framework isn't part of this project. Orchestration logic (initialize, poll
 * loop, host/guest mode switching, VPN service lifecycle) is otherwise unchanged.
 */
public class Terracotta {

    public enum TerracottaMode { HOST, GUEST }

    public interface StateListener {
        void onStateChanged(TerracottaState.Ready state);
    }

    private static volatile boolean initialized = false;
    private static volatile TerracottaAndroidAPI.Metadata metadata = null;
    private static volatile TerracottaMode mode = null;

    private static final AtomicReference<TerracottaState.Ready> STATE = new AtomicReference<>(null);
    private static final List<StateListener> LISTENERS = new CopyOnWriteArrayList<>();

    @Nullable
    public static TerracottaMode getMode() {
        return mode;
    }

    @Nullable
    public static TerracottaState.Ready getState() {
        return STATE.get();
    }

    public static void addStateListener(StateListener listener) {
        LISTENERS.add(listener);
    }

    public static void removeStateListener(StateListener listener) {
        LISTENERS.remove(listener);
    }

    public static TerracottaAndroidAPI.Metadata getMetadata() {
        return metadata == null ? new TerracottaAndroidAPI.Metadata("unknown", 0, "unknown") : metadata;
    }

    /** Call once, from a real Activity, before host/join is used. Starts the VPN
     *  permission flow (if needed) and a background poll loop watching for state
     *  changes - mirrors FCL's exact polling interval (0.5ms) since that's a
     *  proven-working value from a real integration, not a guess. */
    public static synchronized void initialize(Activity activity) {
        if (initialized) return;

        metadata = TerracottaAndroidAPI.initialize(activity, () ->
            TaskExecutors.runInUIThread(() -> startTerracottaVpn(activity))
        );

        Thread daemon = new Thread(() -> {
            while (true) {
                TerracottaState.Ready current = STATE.get();
                int index = current == null ? -1 : current.getIndex();
                try {
                    String stateJson = TerracottaAndroidAPI.getState();
                    TerracottaState.Ready next = TerracottaState.parse(stateJson);
                    if (next.getIndex() > index && STATE.compareAndSet(current, next)) {
                        TaskExecutors.runInUIThread(() -> {
                            for (StateListener listener : LISTENERS) listener.onStateChanged(next);
                        });
                    }
                } catch (Exception e) {
                    Logging.e("Terracotta", "State poll failed: " + e);
                }
                LockSupport.parkNanos(500_000);
            }
        }, "Terracotta Background Daemon");
        daemon.setDaemon(true);
        daemon.start();

        initialized = true;
    }

    public static void setWaiting(Context context, boolean manual) {
        if (!initialized) return;
        if (manual) stopTerracottaVpn(context);
        TerracottaAndroidAPI.setWaiting();
    }

    /** Host a room. player/extraNodes may be null. */
    public static void setScanning(@Nullable String room, @Nullable String player, @Nullable List<String> extraNodes) throws Exception {
        if (!initialized) throw new IllegalStateException("Call Terracotta.initialize() first");
        if (!(getState() instanceof TerracottaState.Waiting)) throw new IllegalStateException("Reset to waiting state first");

        mode = TerracottaMode.HOST;
        TerracottaAndroidAPI.setScanning(room, player, extraNodes);
    }

    /** Join a room by code. Returns false if the room code was rejected outright. */
    public static boolean setGuesting(String room, @Nullable String player, @Nullable List<String> extraNodes) throws Exception {
        if (!initialized) throw new IllegalStateException("Call Terracotta.initialize() first");
        if (!(getState() instanceof TerracottaState.Waiting)) throw new IllegalStateException("Reset to waiting state first");

        mode = TerracottaMode.GUEST;
        return TerracottaAndroidAPI.setGuesting(room, player, extraNodes);
    }

    @Nullable
    public static TerracottaAndroidAPI.RoomType parseRoomCode(String room) {
        if (!initialized || room == null) return null;
        return TerracottaAndroidAPI.parseRoomCode(room);
    }

    @Nullable
    public static String collectLogs() {
        if (!initialized) return null;
        try (Reader reader = TerracottaAndroidAPI.collectLogs(); StringWriter writer = new StringWriter()) {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) writer.write(buf, 0, n);
            return writer.toString();
        } catch (IOException e) {
            Logging.e("Terracotta", "collectLogs failed: " + e);
            return "Failed to collect logs: " + e.getMessage();
        }
    }

    private static void startTerracottaVpn(Activity activity) {
        Intent intent = VpnService.prepare(activity);
        if (intent != null) {
            activity.startActivityForResult(intent, TerracottaVpnService.VPN_PERMISSION_REQUEST_CODE);
            // The activity is responsible for calling onVpnPermissionResult() from
            // its own onActivityResult() - see TerracottaVpnService for the constant.
        } else {
            Intent vpnIntent = new Intent(activity, TerracottaVpnService.class).setAction(TerracottaVpnService.ACTION_START);
            ContextCompat.startForegroundService(activity, vpnIntent);
        }
    }

    /** Call from the hosting Activity's onActivityResult() for VPN_PERMISSION_REQUEST_CODE. */
    public static void onVpnPermissionResult(Activity activity, boolean granted) {
        if (granted) {
            Intent vpnIntent = new Intent(activity, TerracottaVpnService.class).setAction(TerracottaVpnService.ACTION_START);
            ContextCompat.startForegroundService(activity, vpnIntent);
        } else {
            TerracottaAndroidAPI.getPendingVpnServiceRequest().reject();
            setWaiting(activity, true);
            Toast.makeText(activity, "VPN permission is required for Friends/LAN play", Toast.LENGTH_SHORT).show();
        }
    }

    private static void stopTerracottaVpn(Context context) {
        if (TerracottaVpnService.isRunning()) {
            Intent intent = new Intent(context, TerracottaVpnService.class).setAction(TerracottaVpnService.ACTION_STOP);
            ContextCompat.startForegroundService(context, intent);
        }
    }
}
