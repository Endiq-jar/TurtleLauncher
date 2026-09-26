/*
 * Turtle Launcher
 * Copyright (C) 2025 Endiq and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.endiq.turtlelauncher.utils.network

import com.endiq.turtlelauncher.utils.logging.Logger
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Fallback DNS resolver
 *
 * Uses system DNS first; on failure (DNS pollution, ISP DNS outage,
 * private DNS unreachable, etc.), it falls back to public DNS-over-HTTPS,
 * preventing a mere resolution failure from taking the entire network request down.
 *
 * All DoH services are accessed via IP literals whose TLS certificates carry matching IP SANs, so
 * fallback resolution never depends on system DNS.
 */
object ResilientDns : Dns {
    private const val TAG = "ResilientDns"

    /** Cache duration for DoH results */
    private const val CACHE_TTL_MILLIS = 10 * 60 * 1000L

    /** Caches successful DoH results (system DNS results stay cached by the system) */
    private val cache = ConcurrentHashMap<String, Pair<List<InetAddress>, Long>>()

    /** Bootstrap client for DoH queries: IP literals only, no pre-resolution needed */
    private val dohClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * URL templates of public DoH services (all dns-json response format)
     * Sorted by priority; earlier entries are tried first
     */
    private val dohProviders = listOf(
        "https://1.1.1.1/dns-query?name=%s&type=A", //Cloudflare
        "https://8.8.8.8/resolve?name=%s&type=A", //Google
        "https://223.5.5.5/resolve?name=%s&type=A" //AliDNS
    )

    override fun lookup(hostname: String): List<InetAddress> {
        try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Logger.warning(TAG, "System DNS resolution failed for $hostname, falling back to DoH: ${e.message}")
        }

        //Cache hit: avoids frequent DoH queries against the same domain
        cache[hostname]?.let { (addresses, timestamp) ->
            if (System.currentTimeMillis() - timestamp < CACHE_TTL_MILLIS) {
                return addresses
            }
            cache.remove(hostname)
        }

        var lastError: Exception? = null
        for (template in dohProviders) {
            try {
                val addresses = dohLookup(template.format(hostname))
                if (addresses.isNotEmpty()) {
                    Logger.info(
                        TAG,
                        "Resolved $hostname via DoH: ${addresses.joinToString { it.hostAddress ?: "?" }}"
                    )
                    cache[hostname] = addresses to System.currentTimeMillis()
                    return addresses
                }
            } catch (e: Exception) {
                lastError = e
                Logger.warning(TAG, "DoH lookup failed for $hostname: ${e.message}")
            }
        }

        throw UnknownHostException("Unable to resolve $hostname: system DNS and all DoH providers failed").apply {
            lastError?.let { initCause(it) }
        }
    }

    /**
     * Resolves a domain via a dns-json DoH service
     * @return the resolved addresses, or an empty list when no valid records exist
     */
    private fun dohLookup(url: String): List<InetAddress> {
        val request = Request.Builder()
            .url(url)
            .header("accept", "application/dns-json")
            .build()

        dohClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("DoH request failed: HTTP ${response.code}")

            val json = JSONObject(response.body.string())
            val status = json.optInt("Status", -1)
            if (status != 0) throw IOException("DoH response status: $status")

            val answers = json.optJSONArray("Answer") ?: return emptyList()
            return (0 until answers.length())
                .mapNotNull { answers.optJSONObject(it) }
                .filter { it.optInt("type") == 1 || it.optInt("type") == 28 } //A and AAAA records
                .mapNotNull { record ->
                    record.optString("data").takeIf { it.isNotBlank() }?.let { ip ->
                        //IP literals never trigger extra DNS queries
                        runCatching { InetAddress.getByName(ip) }.getOrNull()
                    }
                }
                .distinctBy { it.hostAddress }
        }
    }
}
