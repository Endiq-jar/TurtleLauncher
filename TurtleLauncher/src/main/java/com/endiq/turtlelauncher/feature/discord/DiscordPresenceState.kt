package com.endiq.turtlelauncher.feature.discord

/** Pure state shared by the service and regression tests. Game clients take priority. */
internal class DiscordPresenceState<K> {
    private val clients = linkedMapOf<K, String?>()
    val active: Boolean get() = clients.isNotEmpty()
    val version: String? get() = clients.values.lastOrNull { !it.isNullOrBlank() }
    fun update(client: K, version: String?) { clients[client] = version?.takeIf { it.isNotBlank() } }
    fun remove(client: K) { clients.remove(client) }
}
