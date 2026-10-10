package com.endiq.turtlelauncher.feature.mod

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.download.enums.ModLoader
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.mod.parser.ModInfo
import org.jackhuang.hmcl.util.versioning.VersionNumber
import java.io.File
import java.util.zip.ZipFile

/**
 * Checks metadata that Fabric/Quilt themselves use to reject known-incompatible mod pairs.
 * This is deliberately advisory: it never disables or edits mods, and unknown version
 * predicate syntax is reported as a possible conflict instead of blocking launch.
 */
object ModMetadataConflictDetector {
    enum class Kind { DUPLICATE_ID, BREAKS, CONFLICTS }

    data class Finding(
        val kind: Kind,
        val modId: String,
        val message: String
    )

    private data class DeclaredRule(
        val targetId: String,
        val kind: Kind,
        /** An empty list means all versions; an array is an OR list per Fabric/Quilt metadata. */
        val versionPredicates: List<String> = emptyList(),
        val unlessId: String? = null,
        val reason: String? = null
    )

    private data class InstalledMod(
        val info: ModInfo,
        val file: File,
        val rules: List<DeclaredRule>
    ) {
        val name: String
            get() = info.name?.takeIf { it.isNotBlank() } ?: info.id?.takeIf { it.isNotBlank() } ?: file.name

        val version: String
            get() = info.version.orEmpty()

        val ids: Set<String>
            get() = (listOfNotNull(info.id) + info.providedIds.orEmpty())
                .map { ModMetadataConflictDetector.normalizeId(it) }
                .filter { it.isNotBlank() }
                .toSet()
    }

    @JvmStatic
    fun detect(modInfoList: List<ModInfo>, loader: ModLoader?): List<Finding> {
        if (modInfoList.isEmpty()) return emptyList()

        val seenPaths = mutableSetOf<String>()
        val installed = modInfoList.mapNotNull { info ->
            val file = info.file?.takeIf { it.isFile } ?: return@mapNotNull null
            val path = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
            if (!seenPaths.add(path)) return@mapNotNull null
            InstalledMod(info, file, readRules(file, loader))
        }
        if (installed.isEmpty()) return emptyList()

        val providers = linkedMapOf<String, MutableList<InstalledMod>>()
        installed.forEach { mod ->
            mod.ids.forEach { id -> providers.getOrPut(id) { mutableListOf() }.add(mod) }
        }

        val findings = mutableListOf<Finding>()
        providers.forEach { (id, mods) ->
            val distinctMods = mods.distinctBy { it.file.absolutePath }
            if (distinctMods.size > 1) {
                val names = distinctMods.joinToString(", ") { "${it.name} (${it.file.name})" }
                findings += Finding(
                    kind = Kind.DUPLICATE_ID,
                    modId = id,
                    message = "Multiple enabled jars declare or provide mod ID '$id': $names. " +
                        "The loader may reject the duplicate ID or resolve the wrong implementation."
                )
            }
        }

        installed.forEach { source ->
            source.rules.forEach ruleLoop@ { rule ->
                val targetId = normalizeId(rule.targetId)
                if (targetId.isBlank()) return@ruleLoop
                if (rule.unlessId?.let { providers[normalizeId(it)].orEmpty().isNotEmpty() } == true) {
                    return@ruleLoop
                }
                val targets = providers[targetId].orEmpty().filter { it.file.absolutePath != source.file.absolutePath }
                targets.forEach targetLoop@ { target ->
                    val match = matchesAnyPredicate(target.version, rule.versionPredicates)
                    if (match == false) return@targetLoop
                    val predicateText = rule.versionPredicates
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString(" OR ") { "'$it'" }
                    val relation = when (rule.kind) {
                        Kind.BREAKS -> "declares that it breaks with"
                        Kind.CONFLICTS -> "declares a conflict with"
                        Kind.DUPLICATE_ID -> "has a duplicate ID with"
                    }
                    val details = buildString {
                        append("${source.name} (${source.file.name}) $relation ")
                        append("${target.name} (${target.file.name}) [ID '$targetId']")
                        if (target.version.isNotBlank()) append(" at version '${target.version}'")
                        if (predicateText != null) append(" for version predicate $predicateText")
                        if (match == null) append("; Turtle Launcher could not evaluate that predicate")
                        rule.reason?.takeIf { it.isNotBlank() }?.let { append(". Mod author note: $it") }
                        append('.')
                    }
                    findings += Finding(rule.kind, targetId, details)
                }
            }
        }

        return findings.distinctBy { it.message }.sortedWith(
            compareBy<Finding> { it.kind.ordinal }.thenBy { it.modId }.thenBy { it.message }
        )
    }

    private fun readRules(file: File, loader: ModLoader?): List<DeclaredRule> {
        if (loader != ModLoader.FABRIC && loader != ModLoader.QUILT) return emptyList()
        return runCatching {
            ZipFile(file).use { zip ->
                val entryName = if (loader == ModLoader.FABRIC) "fabric.mod.json" else "quilt.mod.json"
                val entry = zip.getEntry(entryName) ?: return@use emptyList()
                val root = zip.getInputStream(entry).bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
                if (loader == ModLoader.FABRIC) {
                    readFabricRules(root)
                } else {
                    val quiltLoader = root.get("quilt_loader")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?: return@use emptyList()
                    readQuiltRules(quiltLoader)
                }
            }
        }.onFailure { e ->
            Logging.w("ModMetadataConflictDetector", "Could not read compatibility metadata from ${file.name}", e)
        }.getOrDefault(emptyList())
    }

    private fun readFabricRules(root: JsonObject): List<DeclaredRule> =
        readFabricRuleMap(root, "breaks", Kind.BREAKS) +
            readFabricRuleMap(root, "conflicts", Kind.CONFLICTS)

    /** Fabric values may be a single SemVer predicate or an array of OR predicates. */
    private fun readFabricRuleMap(root: JsonObject, field: String, kind: Kind): List<DeclaredRule> {
        val map = root.get(field)?.takeIf { it.isJsonObject }?.asJsonObject ?: return emptyList()
        return map.entrySet().mapNotNull { (id, value) ->
            if (id.isBlank()) return@mapNotNull null
            DeclaredRule(id, kind, readStringValues(value))
        }
    }

    /** Quilt's RFC defines `quilt_loader.breaks` as dependency objects with id/versions/unless/reason. */
    private fun readQuiltRules(quiltLoader: JsonObject): List<DeclaredRule> {
        val entries = quiltLoader.get("breaks")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return entries.mapNotNull { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString ->
                    element.asString.takeIf { it.isNotBlank() }?.let { DeclaredRule(it, Kind.BREAKS) }
                element.isJsonObject -> {
                    val rule = element.asJsonObject
                    val id = rule.get("id")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val unless = rule.get("unless")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    val reason = rule.get("reason")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                    DeclaredRule(id, Kind.BREAKS, readStringValues(rule.get("versions")), unless, reason)
                }
                else -> null
            }
        }
    }

    private fun readStringValues(value: JsonElement?): List<String> = when {
        value == null -> emptyList()
        value.isJsonPrimitive && value.asJsonPrimitive.isString -> listOf(value.asString)
        value.isJsonArray -> value.asJsonArray.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        }
        else -> emptyList()
    }

    /**
     * Returns true if a supported predicate matches, false if all supported alternatives
     * reject the version, and null if the metadata uses syntax this checker cannot parse.
     */
    internal fun matchesAnyPredicate(version: String, predicates: List<String>): Boolean? {
        if (predicates.isEmpty()) return true
        var hasUnknown = false
        for (predicate in predicates) {
            when (matchesPredicate(version, predicate)) {
                true -> return true
                null -> hasUnknown = true
                false -> Unit
            }
        }
        return if (hasUnknown) null else false
    }

    private fun matchesPredicate(version: String, predicate: String): Boolean? {
        if (version.isBlank() || predicate.isBlank()) return null
        var hasUnknown = false
        for (alternative in predicate.split("||")) {
            val tokens = alternative.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            var rejected = false
            var clauseUnknown = false
            for (token in tokens) {
                when (matchesToken(version, token)) {
                    true -> Unit
                    false -> {
                        rejected = true
                        break
                    }
                    null -> clauseUnknown = true
                }
            }
            if (!rejected && !clauseUnknown) return true
            if (!rejected && clauseUnknown) hasUnknown = true
        }
        return if (hasUnknown) null else false
    }

    private fun matchesToken(version: String, token: String): Boolean? {
        if (token == "*" || token.equals("x", ignoreCase = true)) return true
        if (token.contains(".x", ignoreCase = true) || token.contains(".*")) {
            return matchesWildcard(version, token)
        }

        val operator = when {
            token.startsWith(">=") -> ">="
            token.startsWith("<=") -> "<="
            token.startsWith(">") -> ">"
            token.startsWith("<") -> "<"
            token.startsWith("=") -> "="
            token.startsWith("~") -> "~"
            token.startsWith("^") -> "^"
            else -> "="
        }
        val requested = token.removePrefix(operator).trim()
        if (requested.isBlank()) return null
        val comparison = compareVersions(version, requested) ?: return null
        return when (operator) {
            ">=" -> comparison >= 0
            "<=" -> comparison <= 0
            ">" -> comparison > 0
            "<" -> comparison < 0
            "~", "^" -> {
                if (comparison < 0) false
                else {
                    val upperBound = nextRangeBound(requested, operator) ?: return null
                    val upperComparison = compareVersions(version, upperBound) ?: return null
                    upperComparison < 0
                }
            }
            else -> comparison == 0
        }
    }

    private fun matchesWildcard(version: String, predicate: String): Boolean? {
        val actualParts = version.substringBefore('+').substringBefore('-').split('.')
        val requestedParts = predicate.split('.')
        for (index in requestedParts.indices) {
            val requestedPart = requestedParts[index]
            if (requestedPart.equals("x", ignoreCase = true) || requestedPart == "*") return true
            val expected = requestedPart.toIntOrNull() ?: return null
            val actual = actualParts.getOrNull(index)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: return null
            if (actual != expected) return false
        }
        return actualParts.size == requestedParts.size
    }

    private fun nextRangeBound(version: String, operator: String): String? {
        val match = Regex("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?").find(version) ?: return null
        val major = match.groupValues[1].toIntOrNull() ?: return null
        val minor = match.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
        val patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toIntOrNull()
        return if (operator == "~") {
            if (minor == null) "${major + 1}.0"
            else "$major.${minor + 1}.0"
        } else {
            when {
                major > 0 -> "${major + 1}.0.0"
                minor == null -> "1.0.0"
                minor > 0 -> "0.${minor + 1}.0"
                patch == null -> "0.1.0"
                else -> "0.0.${patch + 1}"
            }
        }
    }

    private fun compareVersions(left: String, right: String): Int? =
        runCatching { VersionNumber.compare(left, right) }.getOrNull()

    private fun normalizeId(value: String): String = value.trim().lowercase()
}
