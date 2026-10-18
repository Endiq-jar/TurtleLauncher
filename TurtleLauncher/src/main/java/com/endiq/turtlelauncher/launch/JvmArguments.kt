package com.endiq.turtlelauncher.launch

/** Argument handling shared by the launch merger and the actual JNI argument handoff. */
object JvmArguments {
    private val collectors = setOf("UseSerialGC", "UseParallelGC", "UseConcMarkSweepGC", "UseG1GC", "UseZGC", "UseShenandoahGC", "UseEpsilonGC")

    @JvmStatic
    fun parse(source: String): ArrayList<String> {
        val result = arrayListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var index = 0
        var started = false
        fun flush() {
            if (started) result += token.toString()
            token.setLength(0)
            started = false
        }
        while (index < source.length) {
            val character = source[index]
            val next = source.getOrNull(index + 1)
            when {
                character == '\\' && next != null && (next == quote || next == '\\' || (quote == null && next.isWhitespace())) -> {
                    token.append(next); started = true; index++
                }
                quote != null -> {
                    if (character == quote) quote = null else token.append(character)
                }
                character == '\'' || character == '"' -> { quote = character; started = true }
                character.isWhitespace() -> flush()
                else -> {
                    // Retain legacy adjacent VM switches, without splitting -D values or
                    // agent paths that happen to contain a dash/VM-looking substring.
                    if (token.startsWith("-X") && character == '-' &&
                        (source.startsWith("-XX:", index) || source.startsWith("-Xmx", index) ||
                         source.startsWith("-Xms", index) || source.startsWith("-D", index) ||
                         source.startsWith("-javaagent:", index))) flush()
                    token.append(character); started = true
                }
            }
            index++
        }
        require(quote == null) { "Unclosed quote in JVM arguments" }
        flush()
        return result
    }

    private fun key(arg: String): String? = when {
        arg.startsWith("-XX:+") || arg.startsWith("-XX:-") -> arg.substring(5)
        arg.startsWith("-XX:") -> arg.substring(4).substringBefore('=')
        arg.startsWith("-D") -> arg.substringBefore('=')
        else -> null
    }

    private fun selectedCollector(args: List<String>): String? {
        val enabled = linkedSetOf<String>()
        args.forEach { arg ->
            val flag = key(arg)
            if (flag in collectors && (arg.startsWith("-XX:+") || arg.startsWith("-XX:-"))) {
                enabled.remove(flag)
                if (arg.startsWith("-XX:+")) enabled += flag!!
            }
        }
        return enabled.lastOrNull()?.let { "-XX:+$it" }
    }

    fun usesG1(user: String): Boolean {
        val args = parse(user)
        val selected = selectedCollector(args)
        return if (selected != null) selected == "-XX:+UseG1GC"
            else args.lastOrNull { key(it) == "UseG1GC" } != "-XX:-UseG1GC"
    }

    /** Launcher defaults first; explicit user choices last. Never enable two primary GCs. */
    fun merge(user: String, defaults: String): String {
        val explicit = parse(user)
        val explicitCollector = selectedCollector(explicit)
        val out = arrayListOf<String>()
        (parse(defaults) + explicit).forEach { arg ->
            val flag = key(arg)
            if (arg.startsWith("-XX:+") && flag in collectors) {
                if (explicitCollector != null && arg != explicitCollector) return@forEach
                out.removeAll { it.startsWith("-XX:+") && key(it) in collectors }
            }
            if (flag != null) out.removeAll { key(it) == flag }
            out += arg
        }
        return out.joinToString(" ") { arg ->
            if (arg.any { it.isWhitespace() || it == '"' || it == '\\' || it == '\'' })
                "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            else arg
        }
    }
}
