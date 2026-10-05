package com.endiq.turtlelauncher.feature.music

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import com.endiq.turtlelauncher.context.ContextExecutor
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings

/**
 * Owns the one [MediaPlayer] instance used for both the Music screen's live preview and
 * actual startup/background playback, plus the playlist's persistence (as JSON, see
 * [MusicTrack]) since there's no existing DB table for this.
 *
 * Playback scope (mirrors what's selectable on the Music screen):
 * - FULL_GAME: once started, keeps playing through [onGameLaunching] - only
 *   [stop] (or picking a different track) ends it.
 * - ONLY_STARTUP: meant to be started once from SplashActivity and left to finish or be
 *   stopped by the caller - [onGameLaunching] stops it like the other non-FULL_GAME case.
 * - ONLY_LAUNCHER: plays throughout the launcher UI, stops the moment [onGameLaunching]
 *   fires.
 */
object MusicManager {
    enum class Scope { FULL_GAME, ONLY_STARTUP, ONLY_LAUNCHER }

    private var player: MediaPlayer? = null
    private var isPreview = false

    fun getScope(): Scope =
        runCatching { Scope.valueOf(AllSettings.musicPlaybackScope.getValue()) }.getOrDefault(Scope.ONLY_LAUNCHER)

    fun setScope(scope: Scope) {
        AllSettings.musicPlaybackScope.put(scope.name).save()
    }

    fun getPlaylist(): MutableList<MusicTrack> = MusicTrack.listFromJson(AllSettings.musicPlaylistJson.getValue())

    fun savePlaylist(tracks: List<MusicTrack>) {
        AllSettings.musicPlaylistJson.put(MusicTrack.listToJson(tracks)).save()
    }

    fun addTrack(track: MusicTrack) {
        val tracks = getPlaylist()
        tracks.add(track)
        savePlaylist(tracks)
        if (AllSettings.musicSelectedTrackId.getValue().isBlank()) {
            setSelectedTrack(track.id)
        }
    }

    fun removeTrack(id: String) {
        val tracks = getPlaylist()
        tracks.removeAll { it.id == id }
        savePlaylist(tracks)
        if (AllSettings.musicSelectedTrackId.getValue() == id) {
            AllSettings.musicSelectedTrackId.put(tracks.firstOrNull()?.id ?: "").save()
        }
    }

    fun getSelectedTrack(): MusicTrack? {
        val id = AllSettings.musicSelectedTrackId.getValue()
        return getPlaylist().firstOrNull { it.id == id }
    }

    fun setSelectedTrack(id: String) {
        AllSettings.musicSelectedTrackId.put(id).save()
    }

    /** Reads duration/title hints out of a local content:// URI the user just picked. */
    fun probeLocalTrack(uriString: String, fallbackTitle: String): MusicTrack {
        var title = fallbackTitle
        var artist = "Unknown Artist"
        var durationMs = 0L
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(ContextExecutor.getApplication(), Uri.parse(uriString))
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
            }
        }.onFailure { e -> Logging.e("MusicManager", "Failed to read track metadata.", e) }
        return MusicTrack(title = title, artist = artist, durationMs = durationMs, source = uriString, isRemote = false)
    }

    private fun MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> Unit) {
        try {
            block(this)
        } finally {
            release()
        }
    }

    /** Preview playback from the Music screen - always allowed regardless of [getScope]. */
    fun playPreview(track: MusicTrack, onCompletion: () -> Unit = {}) {
        stop()
        isPreview = true
        play(track, onCompletion)
    }

    /** Startup/background playback, gated by [getScope] at the call sites below. */
    private fun playBackground(track: MusicTrack) {
        stop()
        isPreview = false
        play(track) {}
    }

    private fun play(track: MusicTrack, onCompletion: () -> Unit) {
        runCatching {
            val mp = MediaPlayer()
            if (track.isRemote) {
                mp.setDataSource(track.source)
            } else {
                mp.setDataSource(ContextExecutor.getApplication(), Uri.parse(track.source))
            }
            applyVolume(mp)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { onCompletion() }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
            player = mp
        }.onFailure { e -> Logging.e("MusicManager", "Failed to start playback for '${track.title}'.", e) }
    }

    fun setVolume(percent: Int) {
        AllSettings.musicVolume.put(percent.coerceIn(0, 100)).save()
        player?.let { applyVolume(it) }
    }

    private fun applyVolume(mp: MediaPlayer) {
        val vol = AllSettings.musicVolume.getValue().coerceIn(0, 100) / 100f
        mp.setVolume(vol, vol)
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        isPreview = false
    }

    fun isPlayingPreview(): Boolean = isPreview && player != null

    /** Call once when the launcher's Home screen first becomes visible after startup. */
    fun onLauncherStarted() {
        if (!AllSettings.musicEnabled.getValue()) return
        val track = getSelectedTrack() ?: return
        when (getScope()) {
            Scope.FULL_GAME, Scope.ONLY_LAUNCHER, Scope.ONLY_STARTUP -> playBackground(track)
        }
    }

    /** Call right before the game process actually starts (see LauncherActivity#launchGame). */
    fun onGameLaunching() {
        if (getScope() != Scope.FULL_GAME) {
            stop()
        }
    }
}
