package com.endiq.turtlelauncher.feature.ai

import com.endiq.turtlelauncher.setting.AllSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * Which TurtleAI model handles what, and what to fall back to when one runs out of quota.
 *
 * TurtleAI is the launcher's name for the Google Gemini models it talks to - the same models,
 * under the names this app shows. The numbering follows the model family: 3.x models are
 * **TurtleAI3.x** (3.8 down to 3.0) and the 2.5 generation is **TurtleAI2.5**, which is kept
 * around because it is usually the cheapest and most widely available thing to fall back to.
 *
 * ## Why a registry instead of one model id
 *
 *  - **Different jobs want different models.** Writing a paragraph, fixing a Kotlin compile
 *    error, working out how much RAM a 6GB phone can spare and classifying a skin texture all
 *    look like "ask an AI", but the cheap fast model is the right answer for two of them and
 *    the wrong answer for the others. [Task] names the job; [chain] turns it into an ordered
 *    list of models.
 *  - **Free-tier keys run out.** A single 429 ("quota exceeded") used to mean the whole
 *    feature went dead until tomorrow. Now the request moves down the chain to the next model,
 *    which for Gemini means a different quota bucket.
 *  - **Model ids change.** A name that exists today can be retired next quarter. The chain is
 *    data, not code, and [TurtleAiGemini] skips a model that 404s.
 *
 * The user can override any task's first choice in Settings; the rest of the chain still
 * applies underneath it, so an override never removes the safety net.
 */
object TurtleAiModels {

    /** What the assistant is being asked to do right now. */
    enum class Task(val settingLabel: String, val blurb: String) {
        CHAT("Chat and general answers", "Questions, explanations, translation, writing"),
        CODING("Code", "Writing and fixing code, mods, scripts, build files"),
        REASONING("Hard problems", "Planning, diagnosis, maths that needs care"),
        FAST("Quick and cheap", "Short lookups, titles, labels, simple rewrites"),
        VISION("Looking at images", "Reading screenshots, skins and capes"),
        IMAGE("Drawing images", "The /image command"),
        VIDEO("Making videos", "The /video command"),
        TTS("Speaking", "Reading answers aloud, /speak"),
        TRANSCRIBE("Transcribing", "Turning a recording into text"),
        LIVE("Voice conversation", "The live microphone session")
    }

    /**
     * One model the launcher knows how to call.
     *
     * @param id    the real API id - this is what actually goes on the wire.
     * @param label what the user sees: the TurtleAI name.
     * @param note  one line on what it is for, shown in the picker.
     */
    data class Model(
        val id: String,
        val label: String,
        val note: String
    )

    // ── The catalogue ───────────────────────────────────────────────────────────────

    // Text models. Order within each group is "best default first", which is what the chains
    // below lean on.
    private const val FLASH_38 = "gemini-3.8-flash"
    private const val FLASH_37 = "gemini-3.7-flash"
    private const val FLASH_36 = "gemini-3.6-flash"
    private const val FLASH_35 = "gemini-3.5-flash"
    private const val LITE_35 = "gemini-3.5-flash-lite"
    private const val LITE_31 = "gemini-3.1-flash-lite"
    private const val PRO_31 = "gemini-3.1-pro-preview"
    private const val FLASH_30 = "gemini-3-flash-preview"
    /** Google-maintained aliases that always point at a live model - the chain's safety net. */
    private const val FLASH_LATEST = "gemini-flash-latest"
    private const val LITE_LATEST = "gemini-flash-lite-latest"
    private const val FLASH_25 = "gemini-2.5-flash"
    private const val LITE_25 = "gemini-2.5-flash-lite"
    private const val PRO_25 = "gemini-2.5-pro"

    private const val IMAGE_31 = "gemini-3.1-flash-image"
    private const val IMAGE_31_LITE = "gemini-3.1-flash-lite-image"
    private const val IMAGE_PRO_3 = "gemini-3-pro-image"
    private const val IMAGE_25 = "gemini-2.5-flash-image"

    private const val TTS_38 = "gemini-3.8-flash-tts"
    private const val TTS_38_LITE = "gemini-3.8-flash-lite-tts"
    private const val TTS_31 = "gemini-3.1-flash-tts-preview"
    private const val TTS_25 = "gemini-2.5-flash-preview-tts"

    private const val TRANSCRIBE_35 = "gemini-3.5-transcribe"
    private const val TRANSCRIBE_LIVE_35 = "gemini-3.5-transcribe-live"

    private const val LIVE_38 = "gemini-3.8-live"
    private const val LIVE_38_THINKING = "gemini-3.8-live-extended-thinking"
    private const val LIVE_31 = "gemini-3.1-flash-live-preview"

    private const val VEO_31 = "veo-3.1-generate-preview"
    private const val VEO_31_LITE = "veo-3.1-lite-generate-preview"

    private const val EMBED_2 = "gemini-embedding-2-preview"
    private const val EMBED_1 = "gemini-embedding-001"

    /**
     * Every model the launcher can name. Kept as one list so the picker, the chains and the
     * labels all read from the same place; nothing here is validated against Google at build
     * time (see [TurtleAiGemini.listModels] for the live list), so a model that disappears
     * degrades into "skip to the next one in the chain" rather than a broken build.
     */
    val MODELS: List<Model> = listOf(
        Model(FLASH_38, "TurtleAI3.8 Flash", "Best all-round model - writing, questions, images in, code"),
        Model(FLASH_37, "TurtleAI3.7 Flash", "Strongest for code, agents and long multi-step tasks"),
        Model(FLASH_36, "TurtleAI3.6 Flash", "General multimodal - text, images, audio"),
        Model(FLASH_35, "TurtleAI3.5 Flash", "Fast general tasks, good balance of speed and quality"),
        Model(LITE_35, "TurtleAI3.5 Flash-Lite", "Cheapest and highest volume - short answers, labelling"),
        Model(LITE_31, "TurtleAI3.1 Flash-Lite", "Very efficient, very cheap"),
        Model(PRO_31, "TurtleAI3.1 Pro", "Advanced reasoning - hard maths, planning, tricky diagnosis"),
        Model(FLASH_30, "TurtleAI3 Flash (preview)", "Frontier preview model"),
        Model(FLASH_25, "TurtleAI2.5 Flash", "Previous generation - cheap and widely available"),
        Model(LITE_25, "TurtleAI2.5 Flash-Lite", "Previous generation, cheapest tier"),
        Model(PRO_25, "TurtleAI2.5 Pro", "Previous generation reasoning model"),

        Model(IMAGE_31, "TurtleAI3.1 Image", "Image generation and editing (Nano Banana 2)"),
        Model(IMAGE_31_LITE, "TurtleAI3.1 Image Lite", "Cheapest image generation"),
        Model(IMAGE_PRO_3, "TurtleAI3 Pro Image", "Highest quality images, higher resolution"),
        Model(IMAGE_25, "TurtleAI2.5 Image", "Previous generation image model"),

        Model(TTS_38, "TurtleAI3.8 TTS", "Natural speech from text"),
        Model(TTS_38_LITE, "TurtleAI3.8 Flash-Lite TTS", "Cheapest speech"),
        Model(TTS_31, "TurtleAI3.1 TTS (preview)", "Earlier speech model"),
        Model(TTS_25, "TurtleAI2.5 TTS", "Previous generation speech model"),

        Model(TRANSCRIBE_35, "TurtleAI3.5 Transcribe", "Audio to text"),
        Model(TRANSCRIBE_LIVE_35, "TurtleAI3.5 Transcribe Live", "Live audio to text"),

        Model(LIVE_38, "TurtleAI3.8 Live", "Realtime voice conversation"),
        Model(LIVE_38_THINKING, "TurtleAI3.8 Live Extended Thinking", "Voice, with more thinking time"),
        Model(LIVE_31, "TurtleAI3.1 Live (preview)", "Earlier realtime voice model"),

        Model(VEO_31, "TurtleAI3.1 Veo", "Video generation"),
        Model(VEO_31_LITE, "TurtleAI3.1 Veo Lite", "Cheaper, smaller video generation"),

        Model(EMBED_2, "TurtleAI Embedding 2", "Text embeddings (search and similarity)"),
        Model(EMBED_1, "TurtleAI Embedding", "Previous generation embeddings")
    )

    private val BY_ID: Map<String, Model> = MODELS.associateBy { it.id }

    /** The model ids this task tries, in order, before the user's override is applied. */
    private val DEFAULT_CHAINS: Map<Task, List<String>> = mapOf(
        Task.CHAT to listOf(FLASH_38, FLASH_36, FLASH_35, FLASH_25, FLASH_LATEST, LITE_LATEST),
        Task.CODING to listOf(FLASH_37, FLASH_38, PRO_31, FLASH_36, FLASH_35, FLASH_LATEST),
        Task.REASONING to listOf(PRO_31, FLASH_38, FLASH_36, PRO_25, FLASH_25, FLASH_LATEST),
        Task.FAST to listOf(LITE_35, LITE_31, LITE_25, FLASH_35, LITE_LATEST),
        Task.VISION to listOf(FLASH_36, LITE_35, FLASH_35, LITE_25),
        Task.IMAGE to listOf(IMAGE_31, IMAGE_31_LITE, IMAGE_PRO_3, IMAGE_25),
        Task.VIDEO to listOf(VEO_31, VEO_31_LITE),
        Task.TTS to listOf(TTS_38, TTS_38_LITE, TTS_31, TTS_25),
        Task.TRANSCRIBE to listOf(TRANSCRIBE_35, TRANSCRIBE_LIVE_35, FLASH_25),
        Task.LIVE to listOf(LIVE_38, LIVE_38_THINKING, LIVE_31)
    )

    /** The model the user picked for a task, or "" when they left it on automatic. */
    @JvmStatic
    fun preferred(task: Task): String {
        val raw = runCatching { settingFor(task).getValue() }.getOrDefault(AUTO).trim()
        // A stale pick (a model that no longer exists) must not pin the feature to a dead id:
        // favourites first, then "auto" -> the chain's own first entry.
        return if (raw.isEmpty() || raw == AUTO || raw == "default") "" else raw
    }

    /** The setting that holds this task's model choice (see AllSettings). */
    @JvmStatic
    fun settingFor(task: Task) = when (task) {
        Task.CHAT -> AllSettings.aiModelChat
        Task.CODING -> AllSettings.aiModelCoding
        Task.REASONING -> AllSettings.aiModelReasoning
        Task.FAST -> AllSettings.aiModelFast
        Task.VISION -> AllSettings.aiModelVision
        Task.IMAGE -> AllSettings.aiModelImage
        Task.VIDEO -> AllSettings.aiModelVideo
        Task.TTS -> AllSettings.aiModelTts
        Task.TRANSCRIBE -> AllSettings.aiModelTranscribe
        Task.LIVE -> AllSettings.aiModelLive
    }

    /**
     * The ordered list of real model ids to try for [task]: the user's pick (if any) first,
     * then the registry's chain. Duplicates removed, so a hand-picked chain entry is not
     * tried twice.
     */
    @JvmStatic
    fun chain(task: Task): List<String> {
        val preferred = preferred(task)
        val base = DEFAULT_CHAINS[task] ?: listOf(FLASH_38)
        return (if (preferred.isEmpty()) base else listOf(preferred) + base).distinct()
    }

    /** The single model a task would use if nothing goes wrong (for the settings summary). */
    @JvmStatic
    fun primary(task: Task): String = chain(task).firstOrNull() ?: FLASH_38

    /** What the user sees for a model id - the TurtleAI name, or the raw id if unknown. */
    @JvmStatic
    fun label(id: String): String {
        if (id.isBlank() || id == AUTO) return "Automatic"
        return BY_ID[id]?.label ?: id
    }

    @JvmStatic
    fun note(id: String): String = BY_ID[id]?.note.orEmpty()

    /**
     * Picker entries: value -> label, with the TurtleAI name first and the real id in the
     * label so nobody has to guess which Google model they are spending quota on.
     */
    @JvmStatic
    fun pickerEntries(task: Task): List<Pair<String, String>> {
        val entries = mutableListOf<Pair<String, String>>(AUTO to "Automatic (recommended)")
        // Only models that can plausibly do this job, then everything else, so the list stays
        // useful instead of showing video models under "Chat".
        val relevant = DEFAULT_CHAINS[task].orEmpty().mapNotNull { BY_ID[it] }
        val others = MODELS.filter { it !in relevant && it.id !in DEFAULT_CHAINS[task].orEmpty() }
        relevant.forEach { entries.add(it.id to it.label + " - " + it.note) }
        others.forEach { entries.add(it.id to it.label + " - " + it.note) }
        return entries
    }

    /**
     * One line for the settings screen: what is in use, and what happens when it runs out -
     * so the fallback is visible before it is ever needed.
     */
    @JvmStatic
    fun summary(task: Task): String {
        val chain = chain(task)
        val first = label(chain.firstOrNull() ?: "")
        val next = chain.drop(1).firstOrNull()?.let { label(it) }
        val picked = preferred(task).isNotEmpty()
        val prefix = if (picked) "Picked: " else "Automatic: "
        return if (next == null) prefix + first else prefix + first + " \u2192 " + next
    }

    /** True when this id is an image, video or speech model rather than a text one. */
    @JvmStatic
    fun isMediaModel(id: String): Boolean = id.contains("image") || id.contains("veo") ||
        id.contains("tts") || id.contains("transcribe") || id.contains("embedding")

    // ── Cooldowns ───────────────────────────────────────────────────────────────────

    /**
     * Models that recently failed in a way that will not fix itself in the next second
     * (quota, missing model, permission). Skipping them for a while is what keeps a run of
     * questions from paying a wasted round trip each time.
     */
    private val cooling = ConcurrentHashMap<String, Long>()

    @JvmStatic
    fun isCooling(modelId: String): Boolean {
        val until = cooling[modelId] ?: return false
        if (until <= System.currentTimeMillis()) {
            cooling.remove(modelId)
            return false
        }
        return true
    }

    /**
     * Marks [modelId] as unusable for [millis]. Quota resets are usually measured in minutes
     * to a day; there is no reliable "resets at" in the API response, so this is deliberately
     * short - a model that has recovered should come back on its own.
     */
    @JvmStatic
    fun cool(modelId: String, millis: Long) {
        cooling[modelId] = System.currentTimeMillis() + millis
    }

    /** Forgets every cooldown - used when the user asks to retry now. */
    @JvmStatic
    fun clearCooldowns() = cooling.clear()

    /** Human-readable cooldown state, for /model and the settings summary. */
    @JvmStatic
    fun coolingSummary(): String = cooling.entries
        .filter { it.value > System.currentTimeMillis() }
        .joinToString(", ") { label(it.key) }

    const val AUTO = "auto"
}
