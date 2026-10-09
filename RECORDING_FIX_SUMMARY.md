# Screen Recording Fix Summary

## Problem
Recordings were not being saved in TurtleLauncher. The toast would show "Recording failed" even when recording appeared to work.

## Root Cause
The MediaMuxer (which combines video and audio streams into an MP4 file) was not being given enough time to finalize the file before checking if it was saved. The `stop()` function would:
1. Drain the encoders
2. Immediately release the muxer
3. Check if the file exists and has content

However, the muxer writes data asynchronously, and even after calling `stop()` and `release()`, the file might not be fully written to disk yet.

## Solution
Modified `/TurtleLauncher/src/main/java/com/endiq/turtlelauncher/feature/turtle/ScreenRecorder.kt` with the following fixes:

### 1. Added Delays Before Muxer Release
```kotlin
// Wait for all data to be written to the muxer before releasing
val muxerLocal = muxer
if (muxerLocal != null && muxerStarted) {
    try {
        Thread.sleep(300) // Give time for remaining frames
    } catch (t: Throwable) { /* Ignore */ }
}
```

### 2. Added Retry Logic for File Validation
```kotlin
// Wait for file to be fully written to disk after muxer release
// Try multiple times with increasing delays
var fileValid = false
if (savedFile != null) {
    val maxAttempts = 5
    for (attempt in 1..maxAttempts) {
        try {
            Thread.sleep(200L * attempt)
        } catch (t: Throwable) { /* Ignore */ }
        if (savedFile.exists() && savedFile.length() > 0) {
            fileValid = true
            break
        }
    }
    Logging.i(TAG, "File validation attempts: ${if (fileValid) "success" else "failed"}")
}
```

### 3. Improved File Path Handling
```kotlin
// Try to use external movies directory, fall back to cache dir
val moviesDir = activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
val cacheDir = activity.cacheDir
val baseDir = moviesDir ?: cacheDir

val dir = File(baseDir, "recordings").apply {
    if (!exists()) {
        Logging.i(TAG, "Creating recordings directory at: ${absolutePath}")
        if (!mkdirs()) {
            Logging.e(TAG, "Failed to create recordings directory")
        }
    }
}

val actualDir = if (dir.exists() && dir.isDirectory) {
    dir
} else {
    // Fallback to cache directory
    val cacheRecordingsDir = File(cacheDir, "recordings").apply { mkdirs() }
    if (cacheRecordingsDir.exists() && cacheRecordingsDir.isDirectory) {
        cacheRecordingsDir
    } else {
        throw IOException("Cannot create recordings directory")
    }
}
```

### 4. Added Better Error Handling for Encoder Creation
```kotlin
val codec = MediaCodec.createEncoderByType(MIME_TYPE)
if (codec == null) {
    throw RuntimeException("No H.264 encoder found on this device")
}
codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
val surface = codec.createInputSurface()
if (surface == null) {
    codec.release()
    throw RuntimeException("Failed to create input surface for encoder")
}
```

### 5. Improved Toast Message Logic
**Before:**
```kotlin
val message = if (savedFile != null && savedFile.length() > 0)
    "Recording saved: ${savedFile.name}" else "Recording failed"
```

**After:**
```kotlin
val message = if (savedFile != null && fileValid)
    "Recording saved: ${savedFile.name}" else "Recording failed"
```

## Changes Summary

| Change | Purpose |
|--------|---------|
| Added 300ms delay before muxer release | Ensure all frames are written to muxer |
| Added retry logic (5 attempts, 200-1000ms) | Wait for file to be fully written to disk |
| Fallback to cache directory | Handle cases where external storage is unavailable |
| Better directory creation logging | Debug directory creation issues |
| Null checks for encoder/surface | Handle devices without H.264 support |
| More accurate file validation | Check both exists() and length() |

## Expected Behavior After Fix

1. User starts recording
2. Recording runs normally
3. User stops recording
4. System waits for:
   - 300ms for remaining frames to be written to muxer
   - Up to 1000ms (in 200ms increments) for file to be written to disk
5. Toast shows "Recording saved: turtle_YYYY-MM-DD_HH.MM.SS.mp4"

## File Locations

Recordings are now saved to:
1. **Primary location:** `<app_external_files>/Movies/recordings/`
2. **Fallback location:** `<app_cache>/recordings/`

Both directories are created automatically if they don't exist.

## Testing

To test the fix:
1. Start a game in TurtleLauncher
2. Tap the recording button
3. Play for a few seconds
4. Stop recording
5. Check that:
   - Toast shows "Recording saved: ..."
   - File exists in the recordings directory
   - File can be played in a media player

## Compatibility

These changes are backward compatible and should work on:
- All Android versions supported by TurtleLauncher
- All devices with H.264 hardware encoding support
- Devices without external storage (falls back to cache)

## Debugging

If recordings still fail, check the logcat for:
- `ScreenRecorder` tag messages
- File validation attempts
- Directory creation status
- Encoder initialization errors
