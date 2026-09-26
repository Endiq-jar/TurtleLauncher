package com.endiq.turtlelauncher.shizuku;

import android.os.ParcelFileDescriptor;

/**
 * ADB-level file service exposed through Shizuku (runs as the shell user).
 * Lets the app read/write/delete files that are otherwise unreachable
 * (for example Android/data on Android 11+ without SAF grants).
 */
interface IShizukuFileService {
    /** Stop the user service process. */
    void destroy() = 16777114;

    /** Lists the children of [path]; each entry is "name|isDir|size|lastModified". */
    String[] listFiles(String path) = 1;

    boolean exists(String path) = 2;

    boolean isDirectory(String path) = 3;

    long length(String path) = 4;

    long lastModified(String path) = 5;

    boolean deleteRecursively(String path) = 6;

    boolean mkdirs(String path) = 7;

    boolean renameTo(String from, String to) = 8;

    /** Opens [path] for reading; caller must close the descriptor. */
    ParcelFileDescriptor openRead(String path) = 9;

    /** Opens [path] for writing (created/truncated); caller must close the descriptor. */
    ParcelFileDescriptor openWrite(String path) = 10;

    /** Runs [command] with sh and returns stdout+stderr (trimmed). */
    String exec(String command) = 11;
}
