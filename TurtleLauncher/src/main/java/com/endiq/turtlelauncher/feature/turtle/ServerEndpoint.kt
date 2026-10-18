package com.endiq.turtlelauncher.feature.turtle

/** Parses host[:port] and [IPv6][:port] without network access on the UI thread. */
data class ServerEndpoint(val host: String, val port: Int = 25565) {
    val address: String get() = if (host.contains(':')) "[$host]:$port" else "$host:$port"

    companion object {
        fun parse(address: String): ServerEndpoint? {
            val value = address.trim()
            if (value.isEmpty() || value.any { it.isWhitespace() || it in "/\\?#@" }) return null
            val host: String
            val portText: String?
            if (value.startsWith('[')) {
                val end = value.indexOf(']')
                if (end < 2) return null
                host = value.substring(1, end)
                if (!host.contains(':') || host.any { it !in "0123456789abcdefABCDEF:.%" }) return null
                val suffix = value.substring(end + 1)
                if (suffix.isNotEmpty() && !suffix.startsWith(':')) return null
                portText = if (suffix.isEmpty()) null else suffix.drop(1)
            } else {
                if (value.count { it == ':' } > 1) return null // bracket IPv6 to disambiguate ports
                host = value.substringBefore(':')
                if (host.isEmpty() || host.any { !it.isLetterOrDigit() && it !in ".-_" }) return null
                portText = if (value.contains(':')) value.substringAfter(':') else null
            }
            val port = if (portText == null) 25565 else portText.toIntOrNull() ?: return null
            return if (port in 1..65535) ServerEndpoint(host, port) else null
        }
    }
}
