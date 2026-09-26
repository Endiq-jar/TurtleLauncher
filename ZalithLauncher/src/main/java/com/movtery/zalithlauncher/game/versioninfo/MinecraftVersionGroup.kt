/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
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

package com.movtery.zalithlauncher.game.versioninfo

/**
 * 一组同属于某个“大版本系列”的 Minecraft 版本，例如 "1.21" 分组下会包含
 * "1.21"、"1.21.1"、"1.21.2" ... "1.21.11" 等所有该系列下的版本
 *
 * @param key 该分组的标识/展示名称，例如 "1.21"、"26.3"
 * @param versions 归属于该分组的版本列表，已按发布时间从新到旧排序
 */
data class MinecraftVersionGroup(
    val key: String,
    val versions: List<MinecraftVersion>
) {
    /**
     * 该分组内发布时间最新的版本，用于确定分组卡片展示的图标、类型徽章、日期等信息
     */
    val latest: MinecraftVersion
        get() = versions.first()
}

/**
 * 匹配版本号开头的 "主版本.次版本" 部分，例如：
 * "1.21.11" -> "1.21"，"26.3-snapshot-4" -> "26.3"，"26.3" -> "26.3"
 *
 * 远古 Alpha/Beta（如 "b1.7.3"）、按年份周数命名的旧式快照（如 "23w13a"）等版本号
 * 并不匹配该正则，它们会在 [groupByVersionSeries] 中被动态归入其后最近的一个正式版分组
 */
private val majorMinorPrefixRegex = Regex("""^\d+\.\d+""")

private fun MinecraftVersion.directGroupKeyOrNull(): String? =
    majorMinorPrefixRegex.find(version.id)?.value

/**
 * 将版本列表按“大版本系列”动态分组。
 *
 * 分组规则：
 * 1. 如果版本号本身以 "主版本.次版本" 开头（无论正式版还是快照），直接使用该前缀作为分组标识；
 *    这使得新增的版本（例如未来的 "1.22"、"1.22.1"）无需任何硬编码即可被自动正确分组。
 * 2. 如果版本号无法直接解析出分组标识（远古 Alpha/Beta、按年份周数命名的旧式快照等），
 *    则归入其后最近发布的一个正式版所在的分组——这与官方启动器的呈现方式一致：
 *    快照总是从属于它即将发布的那个正式版本。
 * 3. 如果连这样的正式版都找不到（理论上仅会发生在数据表最前端且尚无任何正式版的极端情况），
 *    则以其自身版本号作为分组标识，保证每个版本都必定能被分到一个组，不会丢失。
 *
 * 分组结果按各分组内最新版本的发布时间，从新到旧排序。
 */
fun List<MinecraftVersion>.groupByVersionSeries(): List<MinecraftVersionGroup> {
    if (isEmpty()) return emptyList()

    //按发布时间从新到旧排列，这样在遍历到快照/远古版本时，
    //它所属的正式版必定已经被遍历过，可以直接复用
    val sortedDescending = this.sortedByDescending { it.version.releaseTime }

    var lastSeenReleaseGroup: String? = null
    val grouped = LinkedHashMap<String, MutableList<MinecraftVersion>>()

    for (v in sortedDescending) {
        val direct = v.directGroupKeyOrNull()
        val key = direct ?: lastSeenReleaseGroup ?: v.version.id
        if (direct != null && v.type == MinecraftVersion.Type.Release) {
            lastSeenReleaseGroup = direct
        }
        grouped.getOrPut(key) { mutableListOf() }.add(v)
    }

    return grouped.map { (key, versions) ->
        MinecraftVersionGroup(key = key, versions = versions)
    }.sortedByDescending { it.latest.version.releaseTime }
}
