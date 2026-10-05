package com.endiq.turtlelauncher.feature.music

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One playlist entry. [source] is either a `content://` URI the user picked from their
 * device, or a direct http(s) URL to an audio file the user pasted in. There is no
 * YouTube/Spotify/YT Music extraction here - those services don't expose a playable
 * direct audio URL without going through their own licensed SDKs/APIs (Spotify App
 * Remote, YouTube Data API), which is a different, much bigger integration than "paste
 * a link"; building a scraper to pull a raw stream out of them isn't something this adds.
 */
data class MusicTrack(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val artist: String,
    val durationMs: Long,
    val source: String,
    val isRemote: Boolean
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("artist", artist)
        put("durationMs", durationMs)
        put("source", source)
        put("isRemote", isRemote)
    }

    companion object {
        fun fromJson(obj: JSONObject): MusicTrack = MusicTrack(
            id = obj.optString("id", UUID.randomUUID().toString()),
            title = obj.optString("title", "Unknown Title"),
            artist = obj.optString("artist", "Unknown Artist"),
            durationMs = obj.optLong("durationMs", 0L),
            source = obj.optString("source", ""),
            isRemote = obj.optBoolean("isRemote", false)
        )

        fun listToJson(tracks: List<MusicTrack>): String {
            val array = JSONArray()
            tracks.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun listFromJson(json: String): MutableList<MusicTrack> {
            val result = mutableListOf<MusicTrack>()
            runCatching {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    result.add(fromJson(array.getJSONObject(i)))
                }
            }
            return result
        }
    }
}
