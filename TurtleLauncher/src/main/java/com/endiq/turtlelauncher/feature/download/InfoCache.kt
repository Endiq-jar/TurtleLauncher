package com.endiq.turtlelauncher.feature.download

import com.endiq.turtlelauncher.feature.download.item.DependenciesInfoItem
import com.endiq.turtlelauncher.feature.download.item.ModLikeVersionItem
import com.endiq.turtlelauncher.feature.download.item.ModVersionItem
import com.endiq.turtlelauncher.feature.download.item.VersionItem
import java.util.Collections
import java.util.LinkedHashMap

private const val DEPENDENCY_CACHE_MAX_ENTRIES = 48
private const val VERSION_CACHE_MAX_ENTRIES = 12
private const val MOD_VERSION_CACHE_MAX_ENTRIES = 24
private const val MODPACK_CACHE_MAX_ENTRIES = 12

/**
 * Cache search results in memory so the next load can reuse them directly.
 */
class InfoCache {
    abstract class CacheBase<V>(private val maxEntries: Int) {
        init {
            require(maxEntries > 0) { "maxEntries must be positive" }
        }

        // Access-ordered bounded cache: the old ConcurrentHashMaps could grow without limit
        // while browsing many projects. All access is synchronized by the wrapper; callers
        // never iterate the map, so the short critical sections keep the LRU policy simple.
        private val cache: MutableMap<String, V> = Collections.synchronizedMap(
            object : LinkedHashMap<String, V>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean {
                    return size > maxEntries
                }
            }
        )

        /**
         * Store a looked-up value in memory, keyed by mod id.
         */
        fun put(modId: String, value: V) {
            cache[modId] = value
        }

        /**
         * Read a stored value by mod id, or null when absent.
         */
        fun get(modId: String): V? {
            return cache[modId]
        }

        /**
         * Check whether a mod id is already cached in memory.
         */
        fun containsKey(modId: String): Boolean {
            return cache.containsKey(modId)
        }

        /**
         * Drop every entry. Reversible by design - this is a lookup cache keyed by mod id, so
         * the only effect is that the next lookup goes back to the network.
         */
        fun clear() {
            cache.clear()
        }
    }

    object DependencyInfoCache : CacheBase<DependenciesInfoItem>(DEPENDENCY_CACHE_MAX_ENTRIES)
    object VersionCache : CacheBase<MutableList<VersionItem>>(VERSION_CACHE_MAX_ENTRIES)
    object ModVersionCache : CacheBase<MutableList<ModVersionItem>>(MOD_VERSION_CACHE_MAX_ENTRIES)
    object ModPackVersionCache : CacheBase<MutableList<ModLikeVersionItem>>(MODPACK_CACHE_MAX_ENTRIES)

    companion object {
        /**
         * Releases every in-memory download/search cache this launcher keeps. Called before a
         * game session (see BackgroundServiceManager) so those results aren't holding RAM the
         * game JVM could be using; nothing on disk is touched.
         */
        @JvmStatic
        fun clearAll() {
            DependencyInfoCache.clear()
            VersionCache.clear()
            ModVersionCache.clear()
            ModPackVersionCache.clear()
        }
    }
}
