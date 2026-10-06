package com.endiq.turtlelauncher.feature.ai

import android.content.Context
import android.os.Build
import android.os.StatFs
import com.endiq.turtlelauncher.InfoDistributor
import com.endiq.turtlelauncher.feature.log.CrashAnalyzer
import com.endiq.turtlelauncher.feature.log.LatestLogResolver
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.feature.version.VersionsManager
import com.endiq.turtlelauncher.renderer.RendererCatalog
import com.endiq.turtlelauncher.renderer.Renderers
import com.endiq.turtlelauncher.renderer.renderers.HolyGL4ESRenderer
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.utils.ZHTools
import com.endiq.turtlelauncher.utils.path.PathManager
import net.endiq.launcher.Tools
import java.io.File
import java.util.Locale

object TurtleAssistant {

    private const val TAG = "TurtleAssistant"

    /** One topic the assistant can answer. [keywords] drive matching (see [score]);
     *  [answer] builds the reply text, and may read live device/launcher state. */
    private class Topic(
        val id: String,
        /** Short label shown as a tappable suggestion chip. */
        val label: String,
        val keywords: List<String>,
        val answer: (Context) -> String,
        /** Follow-up suggestion chips offered after this topic is answered. */
        val followUps: List<String> = emptyList()
    )

    /**
     * Renders a video and files it. Veo takes minutes, so this is the one command where the
     * chat really does wait - the screen says so the whole time (see AiChatFragment).
     */
    private fun videoReply(prompt: String): Reply {
        if (prompt.isBlank()) {
            return Reply(
                "Tell me what the video should show, for example: " +
                    "/video a creeper walking through a flower forest at sunset",
                startingSuggestions()
            )
        }
        if (TurtleAiGemini.apiKey().isEmpty()) {
            return Reply(TurtleAiLanguage.shell(TurtleAiLanguage.ENGLISH).videoFailed, startingSuggestions())
        }
        val video = TurtleAiGemini.generateVideo(prompt)
            ?: return Reply(
                TurtleAiLanguage.shell(TurtleAiLanguage.ENGLISH).videoFailed,
                startingSuggestions()
            )
        val file = TurtleAiFiles.save(video.bytes, video.mimeType, "video")
            ?: return Reply(
                TurtleAiLanguage.shell(TurtleAiLanguage.ENGLISH).videoFailed,
                startingSuggestions()
            )
        return Reply(
            "Here's your video.\n\nPrompt: " + prompt + "\nModel: " +
                TurtleAiModels.label(video.modelId) +
                "\nSaved as " + file.name + " in the launcher's TurtleAI folder.",
            listOf("/video a slow pan over a Minecraft village at sunrise"),
            mediaPath = file.absolutePath,
            mediaLabel = "Open the video"
        )
    }

    /**
     * Reads text aloud and files the audio. Bare "/speak" reads the assistant's previous answer,
     * which is what the command is usually for - hence [lastAssistantAnswer].
     */
    private fun speakReply(context: Context, text: String): Reply {
        val shell = TurtleAiLanguage.shell(TurtleAiLanguage.replyLanguage(context, text))
        val toSay = if (text == LAST_ANSWER_PLACEHOLDER) {
            lastAssistantAnswer() ?: return Reply(
                "There's no answer to read out yet - ask me something first, or send " +
                    "/speak <text>.",
                startingSuggestions()
            )
        } else {
            text
        }
        if (toSay.isBlank()) {
            return Reply(
                "Tell me what to say, for example: /speak the launcher released 100MB of RAM",
                startingSuggestions()
            )
        }
        val speech = TurtleAiGemini.speak(toSay.take(SPEAK_MAX_CHARS))
            ?: return Reply(shell.speechFailed, startingSuggestions())
        val file = TurtleAiFiles.save(speech.bytes, speech.mimeType, "speech")
            ?: return Reply(shell.speechFailed, startingSuggestions())
        val voice = TurtleAiGemini.voiceName()
        return Reply(
            "Ready - " + TurtleAiModels.label(speech.modelId) + " reading it in voice " + voice +
                ".\n\n\"" + toSay.take(180).trim() + if (toSay.length > 180) "\u2026\"" else "\"" +
                "\n\nSaved as " + file.name + " in the launcher's TurtleAI folder.",
            listOf("Read it aloud again"),
            mediaPath = file.absolutePath,
            mediaLabel = "Play the audio"
        )
    }

    /**
     * Transcribes a recording the user handed to the launcher (Android share sheet ->
     * ShareReceiverActivity -> AiChatFragment). Public because it is an entry point of its own,
     * the same way [analyzeSharedLog] is.
     */
    @JvmStatic
    fun transcribeFile(context: Context, file: java.io.File): Reply = transcribeReply(file.absolutePath)

    /** Turns an audio file into text. */
    private fun transcribeReply(path: String): Reply {
        if (path.isBlank()) {
            return Reply(
                "Point me at an audio file - /transcribe <path> - or share a recording with " +
                    "the launcher and I'll transcribe it.",
                startingSuggestions()
            )
        }
        val file = java.io.File(path)
        if (!file.isFile) {
            return Reply("I can't find a file at \"" + path + "\".", startingSuggestions())
        }
        if (file.length() > TRANSCRIBE_MAX_BYTES) {
            return Reply(
                "That recording is " + (file.length() / (1024 * 1024)) +
                    "MB - too big to send. Split it and try again.",
                startingSuggestions()
            )
        }
        val bytes = runCatching { file.readBytes() }.getOrNull()
            ?: return Reply("I couldn't read \"" + file.name + "\".", startingSuggestions())
        val text = TurtleAiGemini.transcribe(bytes, TurtleAiFiles.mimeTypeOf(file))
            ?: return Reply(
                TurtleAiLanguage.shell(TurtleAiLanguage.ENGLISH).speechFailed,
                startingSuggestions()
            )
        return Reply(
            "Transcript of " + file.name + ":\n\n" + text,
            listOf("Summarize the transcript", "Speak it back")
        )
    }

    /**
     * The assistant's own last answer, for bare "/speak". Read from the saved transcript, so it
     * works after a restart too - and skipped when it is a footer-only or image-only turn.
     */
    private fun lastAssistantAnswer(): String? = runCatching {
        AssistantHistory.load().lastOrNull { !it.isUser && it.text.isNotBlank() }?.text
    }.getOrNull()

    /** Bare "/speak" means "read your last answer" - this marks the difference. */
    private const val LAST_ANSWER_PLACEHOLDER = "\u0000last-answer\u0000"

    /** Speech is billed by input length, and nobody wants a five-minute read-out by accident. */
    private const val SPEAK_MAX_CHARS = 4000

    /** Inline audio for transcription has a hard request-size ceiling; refuse politely first. */
    private const val TRANSCRIBE_MAX_BYTES = 18L * 1024 * 1024

    /** Code-shaped requests, routed to the coding model. */
    private val CODING_MARKERS = listOf(
        " code", "coding", "program", "script", "function", "method", "class ", "snippet",
        "compile", "compiler", "stack trace", "exception", "refactor", "unit test", "test case",
        "regex", "python", "javascript", "typescript", "kotlin", "java ", "c++", "rust ",
        "bash", "shell command", "gradle", "json", "html", "css", "sql", "yaml", "xml",
        "implement ", "algorithm", "pseudo", "api call", "endpoint", "parse ", "mod code",
        "mixins", "fabric api", "neoforge", "compile error", "error log"
    )

    /** Requests that need careful thinking rather than a quick answer. */
    private val REASONING_MARKERS = listOf(
        "calculate", "compute", "how much", "how many", "convert ", "percentage", "average",
        "sum of", "multiply", "divide", "equation", "formula", "probability", "estimate the",
        "in mib", "in gib", "per tick", "why does", "why is", "why did", "root cause",
        "diagnose", "plan ", "strategy", "compare ", "trade-off", "tradeoff", "which is better",
        "optimi", "benchmark"
    )

    /** Ways of asking for a video, mirroring the image markers. */
    private val VIDEO_MARKERS = listOf(
        "make a video of ", "make me a video of ", "make a video ", "generate a video of ",
        "generate video of ", "create a video of ", "create a video ", "render a video of ",
        "animate "
    )

    /**
     * Phrases that mean "produce something for me", checked against the normalised message.
     * Kept short and specific on purpose: every entry here is a case where the rule engine
     * would otherwise answer a request for work with a topic blurb.
     */
    private val GENERAL_TASK_MARKERS = listOf(
        // writing
        "write ", "writing ", "draft ", "compose ", "rewrite ", "reword ", "proofread ",
        "summarize ", "summarise ", "translate ", "explain in ", "make a list", "write me ",
        "essay", "poem", "email", "cover letter", "readme", "changelog", "release notes",
        "documentation for", "guide for", "guide to writing",
        // code
        " code", "coding", "program ", "script ", "function ", "method ", "class ", "snippet",
        "compile", "debug ", "stack trace", "refactor", "unit test", "test case", "regex",
        "python", "javascript", "typescript", "kotlin code", "java code", "c++", "rust ",
        "bash ", "shell command", "gradle", "json", "html", "css", "sql", "yaml", "xml",
        "implement ", "algorithm", "pseudo", "api call", "endpoint for", "parse ",
        // calculation
        "calculate", "compute ", "how much is", "how many is", "convert ", "percentage",
        "average", "sum of", "multiply", "divide ", "equation", "formula", "probability",
        "how long will", "how much does", "estimate the", "in mib", "in gib", "per tick"
    )

    /** A finished assistant turn: the text to show plus optional suggestion chips. */
    data class Reply(
        val text: String,
        val suggestions: List<String> = emptyList(),
        /** Absolute path of an image this turn produced, when it produced one (see /image). */
        val imagePath: String? = null,
        /** Absolute path of another generated file (video, speech) and what to call it in the
         *  chat - images get their own slot because the chat renders them inline. */
        val mediaPath: String? = null,
        val mediaLabel: String? = null
    )

    /**
     * First message shown when a conversation starts - in the launcher's language, so the
     * assistant speaks the user's language from the very first line (see [TurtleAiLanguage]).
     */
    @JvmStatic
    fun greeting(context: Context): Reply {
        val shell = TurtleAiLanguage.shell(TurtleAiLanguage.resolve(context))
        return Reply(
            shell.greeting,
            listOf("Status", "Best renderer?", "Why did my game crash?", "Not enough RAM?")
        )
    }

    /** Suggestion chips shown before the user has typed anything. */
    @JvmStatic
    fun startingSuggestions(): List<String> = listOf(
        "Status", "Best renderer?", "Why did my game crash?", "FPS is low",
        "How do I install mods?", "Draw a creeper at sunset"
    )

    /**
     * Answers [input]. [history] is the earlier part of the conversation, oldest first, used
     * only for the cloud brain: without it a follow-up ("make it faster", "now in Python") is
     * an unanswerable message, because the subject of the question is in the previous turn.
     * The on-device engine ignores it - its answers never depend on context.
     */
    @JvmStatic
    fun respond(
        context: Context,
        input: String,
        history: List<TurtleAiGemini.Exchange> = emptyList()
    ): Reply {
        val raw = input.trim()
        // The language this message should be answered in: what the user actually wrote in,
        // falling back to the launcher/device language. See TurtleAiLanguage.
        val language = TurtleAiLanguage.replyLanguage(context, raw)
        val shell = TurtleAiLanguage.shell(language)
        if (raw.isEmpty()) return Reply("Type a question, or tap one of the suggestions.", startingSuggestions())

        // Slash-commands: explicit, so they always win over keyword matching.
        when (raw.lowercase(Locale.ROOT)) {
            "/help", "/?", "help" -> return helpReply()
            "/status", "status" -> return Reply(statusAnswer(context), listOf("Best renderer?", "Why did my game crash?"))
            "/diagnose", "diagnose" -> return crashReply(context)
            "/renderer", "/renderers" -> return topicReply(context, "renderer")
            "/tips" -> return Reply(tipsAnswer(context), listOf("Best renderer?", "Not enough RAM?"))
            "/about" -> return Reply(aboutAnswer(context))
            "/language", "/lang" -> return languageReply(context, language)
            "/models", "/model" -> return modelsReply()
        }

        // Small talk never needs the network: answer it on-device, instantly.
        smallTalkReply(raw, language)?.let { return it }

        // Generation commands. An explicit command always runs (typing it *is* the consent).
        imageRequest(raw)?.let { return imageReply(it) }
        videoRequest(raw)?.let { return videoReply(it) }
        speakRequest(raw)?.let { return speakReply(context, it) }
        transcribeRequest(raw)?.let { return transcribeReply(it) }

        val normalized = normalize(raw)
        val scored = topics.map { it to score(normalized, it.keywords) }
            .sortedWith(compareByDescending<Pair<Topic, Int>> { it.second }.thenBy { it.first.id })
        val best = scored.firstOrNull()
        // Same rule as before: a very weak match (one short keyword) is not an answer.
        val matchedTopic = best?.takeIf { it.second >= 2 }?.first

        val brainReady = TurtleAiGemini.isConfigured()
        // Which model chain this message should use - code goes to the coding model, hard
        // problems to the reasoning one, everything else to the general one.
        val task = taskFor(raw)

        // Three things send a message to the brain instead of the rule engine:
        //  - it is written in another language (the local engine only answers in English);
        //  - nothing in the topic table matches it;
        //  - it asks for something to be *produced* - code, a calculation, a piece of writing.
        //    Keyword matching is the wrong tool for those: "write a Fabric mod that adds a
        //    block" contains the word "fabric", so the mod topic would happily answer a
        //    request for code with an explanation about mods. When a topic did match, its
        //    answer is still passed along as authoritative launcher context, so the model
        //    writes the code and gets the launcher facts right at the same time.
        val generalTask = looksLikeGeneralTask(raw)
        if (brainReady && (!TurtleAiLanguage.isEnglish(language) || matchedTopic == null || generalTask)) {
            return brainReply(
                context = context,
                question = raw,
                language = language,
                shell = shell,
                localAnswer = matchedTopic?.let { safeAnswer(context, it) },
                suggestions = matchedTopic?.followUps ?: startingSuggestions(),
                history = history,
                task = task
            )
        }

        if (matchedTopic != null) {
            val answer = safeAnswer(context, matchedTopic)
            val text = if (TurtleAiLanguage.isEnglish(language)) answer else answer + "\n\n" + shell.englishOnly
            return Reply(text, matchedTopic.followUps)
        }

        // Nothing local and no brain: attributed web results are the honest answer, if the
        // user turned search on.
        if (TurtleAiWebSearch.isEnabled(context)) return searchReply(context, raw, language, shell)

        return Reply(unknownAnswer(raw, shell), startingSuggestions())
    }

    private val GREETINGS = setOf(
        "hi", "hii", "hiii", "hello", "hey", "heya", "hola", "yo", "sup", "hi there",
        "hello there", "hey there", "good morning", "good afternoon", "good evening"
    )
    private val THANKS = setOf("thanks", "thank you", "thx", "ty", "thanks a lot", "thank u")
    private val HOW_ARE_YOU = setOf("how are you", "how are you doing", "hows it going", "whats up", "wassup")
    private val BYE = setOf("bye", "goodbye", "cya", "see you", "see ya")
    private val WHO_ARE_YOU = setOf("who are you", "what are you", "what can you do")

    /** Instant on-device replies for greetings and chit-chat (English only; other languages go to the brain). */
    private fun smallTalkReply(raw: String, language: String): Reply? {
        if (!TurtleAiLanguage.isEnglish(language)) return null
        val t = normalize(raw).replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty() || t.length > 30) return null
        val chips = listOf("Status", "Best renderer?", "Why did my game crash?", "FPS is low")
        return when (t) {
            in GREETINGS -> Reply("Hey! What do you need - renderers, crashes, mods, FPS?", chips)
            in THANKS -> Reply("Anytime.", chips)
            in HOW_ARE_YOU -> Reply("All good. What can I help with?", chips)
            in BYE -> Reply("See you.")
            in WHO_ARE_YOU -> Reply(
                "I'm the Turtle assistant. I help with renderers, crashes, mods, FPS and launcher settings. Type /help for more.",
                chips
            )
            else -> null
        }
    }

    /** Live answer for "/language": what the assistant is speaking, and why. */
    private fun languageReply(context: Context, language: String): Reply {
        val setting = runCatching { AllSettings.aiLanguage.getValue() }
            .getOrDefault(TurtleAiLanguage.AUTO)
        val device = TurtleAiLanguage.deviceLanguage(context)
        val brain = TurtleAiGemini.isConfigured()
        val search = TurtleAiGemini.searchEnabled()
        return Reply(
            buildString {
                append("Language: ").append(TurtleAiLanguage.displayName(language))
                    .append(" (").append(language).append(")\n")
                append("Launcher/device language: ").append(TurtleAiLanguage.displayName(device))
                    .append(" (").append(device).append(")\n")
                append("Setting: ").append(
                    if (setting == TurtleAiLanguage.AUTO) "Automatic (device language)"
                    else TurtleAiLanguage.displayName(setting)
                ).append('\n')
                append("Offline answers: English only\n")
                append("AI brain (other languages, open questions): ")
                    .append(
                        if (brain) "on (" + TurtleAiModels.label(TurtleAiModels.primary(TurtleAiModels.Task.CHAT)) + ")\n"
                        else "off - Settings -> Experimental -> \"Assistant: use Gemini\"\n"
                    )
                append("Web search: ")
                    .append(if (search) "on (Google Search grounding)\n" else "off - Settings -> Experimental -> \"Assistant: search the internet\"\n")
                append("\nI always reply in the language you write in when the AI brain is on.")
            },
            listOf("Help", "Status")
        )
    }

    /**
     * Routes a question to Gemini, handing it the launcher's own answer as authoritative
     * context when the rule engine had one and turning on Google Search grounding when the
     * user asked for search. If the call fails it falls back to the keyless search providers
     * (raw, attributed results), and only then to the localized "couldn't reach the service"
     * line - never to a made-up answer.
     */
    private fun brainReply(
        context: Context,
        question: String,
        language: String,
        shell: TurtleAiLanguage.Shell,
        localAnswer: String?,
        suggestions: List<String>,
        history: List<TurtleAiGemini.Exchange>,
        task: TurtleAiModels.Task
    ): Reply {
        val search = TurtleAiGemini.searchEnabled()
        val answer = TurtleAiGemini.ask(
            question = question,
            languageTag = language,
            localAnswer = localAnswer,
            deviceFacts = liveDeviceFacts(context),
            history = history,
            useSearch = search,
            task = task
        )
        if (answer != null) {
            return Reply(
                answer.text + sourceFooter(answer.sources, shell) +
                    modelFooter(answer.modelId, task, shell),
                suggestions
            )
        }

        // Gemini failed. If the user also switched on search, the keyless providers are still
        // worth a try: attributed results beat "couldn't reach the service".
        if (search) {
            when (val outcome = TurtleAiWebSearch.search(context, question, language)) {
                is TurtleAiWebSearch.Outcome.Ok ->
                    return Reply(
                        TurtleAiWebSearch.formatForChat(outcome.results, shell, question) +
                            "\n\n" + shell.requestFailed +
                            (TurtleAiGemini.failureReason()?.let { "\n" + it } ?: ""),
                        suggestions
                    )
                else -> Unit
            }
        }
        val reason = TurtleAiGemini.failureReason()
        return Reply(
            shell.requestFailed + (if (reason != null) "\n" + reason else "") + "\n\n" + shell.offlineHint,
            suggestions
        )
    }

    /**
     * The sources Google Search grounding actually used, appended under the answer. These
     * come from the API's grounding metadata, not from the model's text: a URL the model
     * typed itself could be hallucinated, a grounding chunk cannot.
     */
    private fun sourceFooter(
        sources: List<TurtleAiGemini.Source>,
        shell: TurtleAiLanguage.Shell
    ): String =
        if (sources.isEmpty()) "" else "\n\n" + shell.sources + ":\n" +
            sources.joinToString("\n") { "\u2022 " + it.url }

    /** Phrases that open a request for a picture (drawing is done on-device by [TurtlePixelArt]). */
    private val DRAW_MARKERS = listOf(
        "draw me ", "draw a ", "draw an ", "draw the ",
        "generate an image of ", "generate image of ", "generate a picture of ",
        "create an image of ", "create a picture of ", "make an image of ",
        "make a picture of ", "make me an image of ", "paint me ", "paint a ",
        "imagine a picture of "
    )

    /**
     * The scene description for the built-in image generator, or null when this message is not
     * a picture request: the explicit "/image ..." (or "/draw ...") command, or an unmistakable
     * "draw me a creeper".
     */
    private fun imageRequest(raw: String): String? {
        val lower = raw.trim().lowercase(Locale.ROOT)
        if (lower.startsWith("/image ") || lower.startsWith("/draw ")) {
            return raw.trim().substringAfter(' ').trim()
        }
        if (lower == "/image" || lower == "/draw") return ""
        for (marker in DRAW_MARKERS) {
            if (lower.startsWith(marker)) {
                val prompt = raw.trim().substring(marker.length).trim()
                if (prompt.isNotEmpty()) return prompt
            }
        }
        return null
    }

    /** Renders the picture on-device (no network, no key) and files it in the TurtleAI folder. */
    private fun imageReply(prompt: String): Reply {
        if (prompt.isBlank()) {
            return Reply(
                "Tell me what to draw, for example: /image a creeper at sunset in the snow",
                startingSuggestions()
            )
        }
        val art = TurtlePixelArt.generate(prompt)
            ?: return Reply("I couldn't draw that - the on-device image generator failed.", startingSuggestions())
        val file = TurtleAiFiles.save(art.bytes, art.mimeType, "image")
            ?: return Reply("I drew it but couldn't save the file.", startingSuggestions())
        return Reply(
            "Here's your " + art.summary + " scene.\n\nDrawn on your device by Turtle's built-in " +
                "pixel-art generator (no internet, no Gemini), saved as " + file.name +
                " in the launcher's TurtleAI folder. Ask again for a different one.",
            listOf("Draw a creeper at night", "Draw steve in the snow"),
            file.absolutePath
        )
    }

    /**
     * Which chain a question belongs to. Deliberately coarse: the *features* that are not chat
     * (the skin filter, /image, /video, /speak, /transcribe, the live session) pick their own
     * task, and a chat message only has to choose between code, hard problems and everything
     * else.
     */
    private fun taskFor(raw: String): TurtleAiModels.Task {
        val text = " " + normalize(raw).replace(Regex("\\s+"), " ").trim() + " "
        if (CODING_MARKERS.any { text.contains(it) }) return TurtleAiModels.Task.CODING
        if (REASONING_MARKERS.any { text.contains(it) }) return TurtleAiModels.Task.REASONING
        return TurtleAiModels.Task.CHAT
    }

    /**
     * The model that answered, in one line under the answer. Worth the two lines it costs:
     * the user can see which TurtleAI model spent their quota, and can tell when a fallback
     * answered because the first choice ran out.
     */
    private fun modelFooter(
        modelId: String,
        task: TurtleAiModels.Task,
        shell: TurtleAiLanguage.Shell
    ): String {
        if (modelId.isBlank()) return ""
        val label = TurtleAiModels.label(modelId)
        val preferred = TurtleAiModels.preferred(task)
        val fellBack = preferred.isNotEmpty() && preferred != modelId
        return "\n\n\u2014 " + label + if (fellBack) " " + shell.fallbackNote else ""
    }

    /** "/model": what each job is using, and which models are resting after a failure. */
    private fun modelsReply(): Reply {
        val cooling = TurtleAiModels.coolingSummary()
        val lines = TurtleAiModels.Task.values().joinToString("\n") { task ->
            "\u2022 " + task.settingLabel + ": " +
                TurtleAiModels.label(TurtleAiModels.primary(task)) +
                " (" + task.blurb + ")"
        }
        val body = buildString {
            append("TurtleAI models in use:\n").append(lines)
            append("\n\nFallback: each job tries its model, then the next cheapest one ")
            append("that can do the same work - so a model that runs out of quota no longer ")
            append("takes the feature down with it.")
            if (cooling.isNotBlank()) {
                append("\n\nResting after a failure: ").append(cooling)
            }
            append("\n\nChange any of these in Settings \u2192 Experimental \u2192 AI models.")
        }
        return Reply(body, startingSuggestions())
    }

    /**
     * The prompt for video generation, or null when this message is not asking for a video.
     * "/video" always works; a spoken-style "make me a video of ..." only while the brain is on.
     */
    private fun videoRequest(raw: String): String? {
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.startsWith("/video ")) return raw.substringAfter(' ').trim()
        if (lower == "/video") return ""
        if (!TurtleAiGemini.isConfigured()) return null
        for (marker in VIDEO_MARKERS) {
            if (lower.startsWith(marker)) {
                val prompt = raw.substring(marker.length).trim()
                if (prompt.isNotEmpty()) return prompt
            }
        }
        return null
    }

    /**
     * "/speak" with text, or a request to read the last answer aloud. The friendly phrasings
     * are matched as whole messages (never a prefix), so "read it aloud and also explain the
     * renderer part" is a normal question rather than an accidental read-out.
     */
    private fun speakRequest(raw: String): String? {
        val lower = raw.lowercase(Locale.ROOT).trim()
        if (lower.startsWith("/speak ")) return raw.substringAfter(' ').trim()
        if (lower == "/speak" || lower == "/read") return LAST_ANSWER_PLACEHOLDER
        if (lower in SPEAK_PHRASES) return LAST_ANSWER_PLACEHOLDER
        return null
    }

    /** Whole-message ways of asking for a read-out. */
    private val SPEAK_PHRASES = setOf(
        "read it aloud", "read that aloud", "read it aloud again", "read that aloud again",
        "read it out loud", "read that out loud", "read it to me", "read that to me",
        "read it again", "speak it back", "say it out loud", "read your last answer",
        "read your answer aloud"
    )

    /**
     * "/transcribe <path>" - turns a recording into text. The path is what the launcher's file
     * browser shows; a shared audio file arrives through the same function (see
     * AiChatFragment), which is the way most people will use this.
     */
    private fun transcribeRequest(raw: String): String? {
        val lower = raw.lowercase(Locale.ROOT)
        if (lower.startsWith("/transcribe ")) return raw.substringAfter(' ').trim()
        if (lower == "/transcribe") return ""
        return null
    }

    /**
     * No brain configured, but web search is on. Results are shown attributed and unparaphrased:
     * without a model to synthesize them, a paraphrase would be the assistant guessing.
     */
    private fun searchReply(
        context: Context,
        question: String,
        language: String,
        shell: TurtleAiLanguage.Shell
    ): Reply = when (val outcome = TurtleAiWebSearch.search(context, question, language)) {
        is TurtleAiWebSearch.Outcome.Ok ->
            Reply(TurtleAiWebSearch.formatForChat(outcome.results, shell, question), startingSuggestions())
        // Failed = the service was unreachable (and `reason` is a technical English detail, the
        // same way an error message from the launcher would be).
        is TurtleAiWebSearch.Outcome.Failed ->
            Reply(shell.requestFailed + "\n\n(" + outcome.reason + ")", startingSuggestions())
        else -> Reply(shell.searchNone, startingSuggestions())
    }

    /**
     * Analyzes a log the user handed to the launcher through the Android share menu
     * (ShareReceiverActivity -> AiChatFragment). Runs the exact same [CrashAnalyzer] rule
     * engine the post-crash screen uses over [logText] and reports what matched - this is
     * the destination of "share your game log with the Assistant".
     */
    @JvmStatic
    fun analyzeSharedLog(context: Context, logText: String): Reply {
        if (logText.isBlank()) {
            return Reply(
                "The log you shared was empty, so there's nothing for me to read. " +
                    "If the game crashed, try sharing the log again right after it happens.",
                listOf("Help")
            )
        }
        // Keep the analysis bounded - shared files can be huge and the rule engine only ever
        // needs the tail where the actual failure sits.
        val tail = if (logText.length > 200_000) logText.takeLast(200_000) else logText
        val diagnoses = runCatching { CrashAnalyzer.analyze(tail) }
            .onFailure { e -> Logging.e(TAG, "Failed to analyze a shared log", e) }
            .getOrDefault(emptyList())
        if (diagnoses.isEmpty()) {
            return Reply(
                "I read the log you shared, but it doesn't contain anything my rules can " +
                    "explain (no crash, no error lines I recognize). If the game misbehaved, " +
                    "share the log again right after it happens.",
                listOf("Why did my game crash?", "Status")
            )
        }
        val formatted = runCatching { CrashAnalyzer.formatForDisplay(diagnoses) }.getOrDefault("")
        val text = if (formatted.isNotBlank()) formatted else diagnoses.joinToString("\n\n") {
            "${it.title}\n${it.cause}"
        }
        return Reply(
            "I read the log you shared. Here's what my rules found:\n\n$text",
            listOf("Why did my game crash?", "Best renderer?", "Status")
        )
    }

    private fun topicReply(context: Context, id: String): Reply {
        val topic = topics.firstOrNull { it.id == id } ?: return helpReply()
        return Reply(safeAnswer(context, topic), topic.followUps)
    }

    private fun helpReply(): Reply = Reply(
        "Here's what I can help with:\n" +
            "• Renderers - which one to use, how to change it, shader support\n" +
            "• Crashes - read and explain your last game log\n" +
            "• Memory / RAM - how much to allocate on this device\n" +
            "• Memory before launch - what the launcher releases for the game\n" +
            "• Performance - FPS, resolution scale, FPS boost flags\n" +
            "• Renderer overhead / GL state - what can and can't be tuned per frame\n" +
            "• Mods, modpacks, resource packs, shaders, worlds\n" +
            "• Controls, custom buttons, gamepads\n" +
            "• Skins and capes\n" +
            "• Accounts and login\n" +
            "• Game versions, snapshots, LWJGL compatibility\n" +
            "• Friends / LAN play\n" +
            "• Storage, files, screenshots and recording\n" +
            "• Writing, code, maths and general questions (with Gemini)\n" +
            "• Drawing - /image a creeper at sunset (built-in, on-device pixel art)\n" +
            "• Video - /video a creeper walking through a flower forest\n" +
            "• Speaking - /speak (reads my last answer), /speak <text>\n" +
            "• Voice conversation - the microphone button in the chat header\n" +
            "• Transcribing - /transcribe <path>, or share a recording with the launcher\n\n" +
            "Commands: /status /diagnose /renderer /tips /language /image /video /speak " +
            "/models /about",
        startingSuggestions()
    )

    /**
     * True when the message asks for something to be written, calculated, converted or
     * explained as a general skill rather than answered from the launcher's knowledge base.
     *
     * Deliberately about *intent words*, not topics: a plain keyword match would fire on
     * "explain the renderer" (which the rule engine answers better) while missing "turn that
     * into Java". The words below are the ones a request for produced work actually uses.
     */
    private fun looksLikeGeneralTask(raw: String): Boolean {
        val text = " " + normalize(raw).replace(Regex("\\s+"), " ").trim() + " "
        return GENERAL_TASK_MARKERS.any { text.contains(it) }
    }

    /** Never let one broken live-state read take the whole assistant down. */
    private fun safeAnswer(context: Context, topic: Topic): String =
        runCatching { topic.answer(context) }
            .onFailure { e -> Logging.e(TAG, "Topic '${topic.id}' failed to answer", e) }
            .getOrDefault("I hit an error while putting that answer together (${topic.id}). " +
                "Try again, or ask something else - /help lists what I know.")

    private fun normalize(text: String): String =
        text.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9 /]"), " ")

    private fun score(normalized: String, keywords: List<String>): Int {
        var total = 0
        for (keyword in keywords) {
            val key = keyword.lowercase(Locale.ROOT)
            if (key.isEmpty()) continue
            if (normalized.contains(key)) {
                // Longer keywords are more specific, so they carry more weight.
                total += if (key.length >= 6) 3 else 2
            }
        }
        return total
    }

    private fun unknownAnswer(input: String, shell: TurtleAiLanguage.Shell): String {
        val shown = if (input.length > 60) input.take(60) + "…" else input
        // shell.unknown carries the "%s"; the wording (and the promise not to make things up)
        // is in the user's language where we ship one, English otherwise.
        return String.format(shell.unknown, shown) + "\n\n" + shell.offlineHint
    }

    // ── Live-state answers ──────────────────────────────────────────────────────────

    /**
     * The device facts a model cannot know and must never guess: what this machine has, what
     * the launcher is set to, and what is currently in use. Sent with every cloud answer so a
     * question like "how much RAM can I put on this phone" or "is 4GB enough" starts from real
     * numbers instead of a plausible-sounding invention. Deliberately English and terse: it is
     * machine context handed to the model, not text the user reads - the answer itself comes
     * back in the user's language.
     */
    private fun liveDeviceFacts(context: Context): String {
        val totalMb = runCatching { Tools.getTotalDeviceMemory(context) }.getOrDefault(0)
        val ramMb = runCatching { AllSettings.ramAllocation.value.getValue() }.getOrDefault(0)
        val facts = mutableListOf(
            "Launcher: " + runCatching { InfoDistributor.APP_NAME }.getOrDefault("TurtleLauncher") +
                " v" + runCatching { ZHTools.getVersionName() }.getOrDefault("?"),
            "Device: " + Build.MANUFACTURER + " " + Build.MODEL +
                ", Android API " + Build.VERSION.SDK_INT +
                ", ABI " + (Build.SUPPORTED_ABIS.firstOrNull() ?: "?"),
            "Renderer in use: " + currentRendererName(context)
        )
        if (totalMb > 0) {
            facts.add(
                "RAM: " + totalMb + "MB total on the device, " + ramMb +
                    "MB currently allocated to Minecraft"
            )
        } else {
            facts.add("RAM allocated to Minecraft: " + ramMb + "MB")
        }
        freeStorageGb()?.let { facts.add("Free storage on the game drive: " + it + "GB") }
        val versions = runCatching { VersionsManager.getVersions() }.getOrDefault(emptyList())
        facts.add("Installed game versions: " + versions.size +
            (runCatching { VersionsManager.getCurrentVersion()?.getVersionName() }.getOrNull()
                ?.let { " (selected: " + it + ")" } ?: " (none selected)"))
        runCatching { AllSettings.aiLanguage.getValue() }.getOrDefault("")
            .takeIf { it.isNotBlank() && it != TurtleAiLanguage.AUTO }
            ?.let { facts.add("Launcher language setting: " + it) }
        return facts.joinToString("\n")
    }

    private fun statusAnswer(context: Context): String {
        val deviceTotalMb = runCatching { Tools.getTotalDeviceMemory(context) }.getOrDefault(0)
        val ramMb = runCatching { AllSettings.ramAllocation.value.getValue() }.getOrDefault(0)
        val rendererName = currentRendererName(context)
        val versions = runCatching { VersionsManager.getVersions() }.getOrDefault(emptyList())
        val current = runCatching { VersionsManager.getCurrentVersion()?.getVersionName() }
            .getOrNull()
        val freeGb = freeStorageGb()
        val launcherVersion = runCatching { ZHTools.getVersionName() }.getOrDefault("?")

        val sb = StringBuilder()
        sb.append("Here's your setup right now:\n")
        sb.append("• Launcher: ${runCatching { InfoDistributor.APP_NAME }.getOrDefault("TurtleLauncher")} v$launcherVersion\n")
        sb.append("• Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.SDK_INT} (${Build.SUPPORTED_ABIS.firstOrNull() ?: "?"})\n")
        if (deviceTotalMb > 0) sb.append("• RAM: ${deviceTotalMb}MB total, ${ramMb}MB allocated to Minecraft\n")
        sb.append("• Renderer: $rendererName\n")
        sb.append("• Versions installed: ${versions.size}" +
            (current?.let { " (selected: $it)" } ?: " (none selected)") + "\n")
        if (freeGb != null) sb.append("• Free storage on the game drive: ${freeGb}GB\n")
        sb.append("• Last crash: ")
        val log = readMostRecentGameLog()
        sb.append(if (log.isNullOrBlank()) "none recorded" else "a log is available - ask me \"why did my game crash?\"")
        return sb.toString()
    }

    private fun readMostRecentGameLog(): String? {
        val inMemory = runCatching { CrashAnalyzer.getLastLogText() }.getOrNull()
        if (!inMemory.isNullOrBlank()) return inMemory
        return runCatching {
            val file = LatestLogResolver.resolveLastGameLogFile() ?: return@runCatching null
            CrashAnalyzer.tailOf(file, 64 * 1024)
        }.getOrNull()
    }

    /** Human-readable name of the renderer this launcher would actually use right now. */
    private fun currentRendererName(context: Context): String = runCatching {
        val wanted = AllSettings.renderer.getValue()
        Renderers.init(false)
        val compatible = Renderers.getCompatibleRenderers(context).second
        val match = compatible.firstOrNull { it.getUniqueIdentifier() == wanted }
        when {
            match != null -> {
                val badge = RendererCatalog.get(match.getRendererId())?.badge
                "${match.getRendererName()}" + (badge?.let { " (${it.name.lowercase(Locale.ROOT)})" } ?: "")
            }
            // Exactly the situation Renderers.setCurrentRenderer(retryToFirstOnFailure=true)
            // resolves at launch time - say so instead of printing a UUID that means nothing.
            compatible.isNotEmpty() ->
                "${compatible[0].getRendererName()} (auto-fallback - the saved choice isn't available on this device)"
            else -> "none available on this device"
        }
    }.getOrDefault("unknown")

    /**
     * Renderer id the launcher would actually launch with right now - the same resolution
     * [currentRendererName] does, without needing [Renderers.getCurrentRenderer] (which
     * throws until setCurrentRenderer has run).
     */
    private fun currentRendererId(context: Context): String? = runCatching {
        val wanted = AllSettings.renderer.getValue()
        Renderers.init(false)
        val compatible = Renderers.getCompatibleRenderers(context).second
        (compatible.firstOrNull { it.getUniqueIdentifier() == wanted } ?: compatible.firstOrNull())
            ?.getRendererId()
    }.getOrNull()

    private fun onOff(value: Boolean): String = if (value) "on" else "off"

    private fun freeStorageGb(): String? = runCatching {
        val path = PathManager.DIR_GAME_HOME
        if (path.isBlank()) return@runCatching null
        val target = File(path).takeIf { it.exists() } ?: File(path).parentFile ?: return@runCatching null
        val stat = StatFs(target.absolutePath)
        val bytes = stat.availableBytes
        String.format(Locale.ROOT, "%.1f", bytes / (1024.0 * 1024.0 * 1024.0))
    }.getOrNull()

    /** Reads the most recent game log through the same [CrashAnalyzer] rules the crash
     *  screen uses, then summarizes the result in chat. No network, no key. */
    private fun crashReply(context: Context): Reply {
        val log = readMostRecentGameLog()
        if (log.isNullOrBlank()) {
            return Reply(
                "I don't have a recent game log to look at.\n\n" +
                    "Launch the game once and let it crash (or exit with an error) - the " +
                    "launcher keeps the tail of that log, and I can then read it for you. " +
                    "You can also open the full log from the home screen's \"Last Game Log\" " +
                    "card, or share it via Share Logs.",
                listOf("Status", "Best renderer?")
            )
        }
        val diagnoses = runCatching { CrashAnalyzer.analyze(log) }.getOrDefault(emptyList())
        if (diagnoses.isEmpty()) {
            return Reply(
                "I read the last game log, but none of my built-in rules matched it, so I " +
                    "can't tell you what went wrong with any confidence.\n\n" +
                    "What usually helps next:\n" +
                    "• Share Logs (home screen rail) - uploads the log so someone can read the raw output\n" +
                    "• Settings → Experimental → \"AI crash help\" - optional, sends the log tail to " +
                    "Google's Gemini API (your key, or the one built into the app), off by default\n" +
                    "• Try a different renderer for this version (Settings → Video → Renderer)",
                listOf("Best renderer?", "Status")
            )
        }
        val formatted = runCatching { CrashAnalyzer.formatForDisplay(diagnoses) }.getOrDefault("")
        val text = if (formatted.isNotBlank()) formatted else diagnoses.joinToString("\n\n") {
            "${it.title}\n${it.cause}"
        }
        return Reply(
            "I read the last game log. Here's what my rules found:\n\n$text",
            listOf("Best renderer?", "Not enough RAM?", "Status")
        )
    }

    private fun tipsAnswer(context: Context): String {
        val ramMb = runCatching { AllSettings.ramAllocation.value.getValue() }.getOrDefault(0)
        return "Quick wins on this device:\n" +
            "• Renderer: ${currentRendererName(context)} - change it in Settings → Video → Renderer\n" +
            "• RAM: ${ramMb}MB allocated (Settings → Game). Leave 2GB+ free for Android itself\n" +
            "• Resolution scale (Settings → Video) is the single biggest FPS lever on phones\n" +
            "• Settings → Optimization has the FPS boost flags; turn them off one at a time if something breaks\n" +
            "• Ask me \"why did my game crash?\" after a crash - I read the log locally"
    }

    /**
     * What the assistant is, told from the current configuration rather than from what it was
     * originally built as. Privacy questions ("do you send my data?") land on this topic, so it
     * must not claim that nothing ever leaves the device while the user has the AI brain or
     * internet search switched on - and it must not claim those are running when they aren't.
     */
    private fun assistantSelfDescription(context: Context): String {
        val brain = TurtleAiGemini.isConfigured()
        val search = TurtleAiGemini.searchEnabled()
        val model = TurtleAiModels.label(TurtleAiModels.primary(TurtleAiModels.Task.CHAT))

        return buildString {
            append("I'm the launcher's built-in assistant. My answers about this launcher come ")
            append("from a knowledge base plus live readings from this device, so those need no ")
            append("key and no internet - and I don't guess: if something isn't in that knowledge ")
            append("base, I say so.\n\n")

            if (brain) {
                append("Gemini is on ($model), so open questions - writing, code, maths, ")
                append("anything outside the launcher - go to Google with the context I gather ")
                append("here: version, renderer, RAM, log excerpts.\n\n")
            }
            if (search) {
                append("Internet search is on: answers are grounded in Google Search, and I show ")
                append("the pages the search actually used underneath the answer.\n\n")
            }
            if (!brain && !search) {
                append("The Gemini brain and internet search are both off, so nothing leaves this ")
                append("device. Both are in Settings \u2192 Experimental.\n\n")
            } else {
                append("Both are switchable in Settings \u2192 Experimental.\n\n")
            }

        }
    }

    private fun aboutAnswer(context: Context): String {
        val versions = runCatching { VersionsManager.getVersions() }.getOrDefault(emptyList())
        return "TurtleLauncher is a TurtleLauncher Minecraft: Java Edition " +
            "launcher for Android.\n\n" +
            "• Version: ${runCatching { ZHTools.getVersionName() }.getOrDefault("?")} " +
            "(code ${runCatching { ZHTools.getVersionCode() }.getOrDefault(0)})\n" +
            "• Installed game versions: ${versions.size}\n" +
            "• Renderer in use: ${currentRendererName(context)}\n\n" +
            "This assistant is part of the launcher itself. " + when {
                !TurtleAiGemini.isConfigured() && !TurtleAiWebSearch.isEnabled(context) ->
                    "It answers on-device - it doesn't call any AI service."
                TurtleAiGemini.isConfigured() && TurtleAiGemini.searchEnabled() ->
                    "It answers on-device for launcher questions; the Gemini brain and Google " +
                        "Search grounding you enabled answer the rest - see Settings \u2192 Experimental."
                TurtleAiGemini.isConfigured() ->
                    "It answers on-device for launcher questions; the Gemini brain you enabled " +
                        "handles open questions, writing and code - see Settings \u2192 Experimental."
                else ->
                    "It answers on-device for launcher questions; the internet search you " +
                        "enabled looks the rest up - see Settings \u2192 Experimental."
            }
    }

    private val topics: List<Topic> = listOf(
        Topic(
            id = "renderer",
            label = "Best renderer?",
            keywords = listOf(
                "renderer", "renderers", "mobileglues", "mobile glues", "gl4es", "zink",
                "virgl", "ltw", "freedreno", "vgpu", "angle", "vulkan", "opengl",
                "graphics backend", "driver", "turnip", "which renderer", "best renderer",
                "shader", "shaders pack", "iris", "optifine"
            ),
            answer = { context ->
                val current = currentRendererName(context)
                val hasVulkan = runCatching {
                    Tools.checkVulkanSupport(context.packageManager)
                }.getOrDefault(false)
                buildString {
                    append("You're currently on: $current\n\n")
                    append("How to change it: Settings → Video → Renderer. The Graphics Backend switch " +
                        "above it is a shortcut - on = OpenGL family, off = Vulkan (Zink).\n\n")
                    append("Rough guide:\n")
                    append("• MobileGlues - this launcher's default. A real GL implementation on top of " +
                        "your own GLES 3.x, self-contained, and the GL-family option that renders shader packs.\n")
                    append("• Zink - Minecraft's GL translated to Vulkan. Best on Adreno/Mali with a working " +
                        "Vulkan driver" + (if (hasVulkan) " (your device does report Vulkan support)." else " - your device doesn't report Vulkan support, so skip it.") + "\n")
                    append("• LTW / Holy GL4ES - GL→GLES translators, the safe fallback when the above glitch. " +
                        "Holy GL4ES is documented up to MC 1.21.4 only.\n")
                    append("• Freedreno (+ Turnip driver) - Adreno-specific.\n")
                    append("• VirGL / VGPU / NW / ANGLE - niche, badged experimental.\n\n")
                    append("One real caveat: Amethyst-Android's release notes list MobileGlues and Krypton " +
                        "Wrapper as crashing on Minecraft 26.3-snapshot4 and above because of how SDL now " +
                        "creates its EGL window. On those versions, try Zink or LTW instead.")
                }
            },
            followUps = listOf("Shaders not working", "Why did my game crash?", "Status")
        ),
        Topic(
            id = "shader",
            label = "Shaders not working",
            keywords = listOf("shader", "shaders", "iris", "optifine", "shader pack", "shadow"),
            answer = {
                "Shader packs only render on some renderers. In this launcher, RendererCatalog marks " +
                    "MobileGlues, VirGL and Zink as shader-capable (that mapping comes from " +
                    "FCL-Team/FoldCraftLauncher's own README).\n\n" +
                    "So: switch to MobileGlues or Zink in Settings → Video → Renderer, install Iris " +
                    "(or OptiFine) for your exact game version from the Download screen, then put the " +
                    "shader pack in the instance's shaderpacks folder (Files screen). If a pack renders " +
                    "black or crashes, that's usually the pack needing a GL feature the renderer doesn't " +
                    "implement - try a lighter pack before blaming the launcher."
            },
            followUps = listOf("Best renderer?", "How do I install mods?")
        ),
        Topic(
            id = "crash",
            label = "Why did my game crash?",
            keywords = listOf(
                "crash", "crashed", "crashing", "exit code", "sigsegv", "signal", "error log",
                "why did my game", "game closes", "force close", "freeze", "frozen", "stuck",
                "black screen", "diagnose", "hs_err", "anr"
            ),
            answer = { context -> crashReply(context).text },
            followUps = listOf("Best renderer?", "Not enough RAM?", "Status")
        ),
        Topic(
            id = "memory",
            label = "Not enough RAM?",
            keywords = listOf(
                "ram", "memory", "heap", "allocation", "xmx", "out of memory", "oom",
                "outofmemory", "lag", "how much ram", "gc"
            ),
            answer = { context ->
                val deviceTotalMb = runCatching { Tools.getTotalDeviceMemory(context) }.getOrDefault(0)
                val ramMb = runCatching { AllSettings.ramAllocation.value.getValue() }.getOrDefault(0)
                buildString {
                    append("This device has ${deviceTotalMb}MB of RAM in total, and Minecraft is currently " +
                        "allocated ${ramMb}MB (Settings → Game → RAM allocation).\n\n")
                    append("Rules of thumb:\n")
                    append("• Vanilla: 1024-2048MB is plenty. Modpacks: 3072-4096MB.\n")
                    append("• Never allocate more than about half of the device total - Android kills the " +
                        "game (or the launcher) when memory gets tight, which looks exactly like a random crash.\n")
                    append("• More RAM does not add FPS. If you're stuttering, lower the resolution scale " +
                        "first (Settings → Video).\n")
                    append("• Settings → Optimization → Auto RAM Calculator keeps this value tuned to the device.")
                }
            },
            followUps = listOf("FPS is low", "Status")
        ),
        // The launcher-side half of memory: what the launcher itself releases before the game
        // starts (feature/turtle/BackgroundServiceManager.kt), and the policy to recommend when
        // launcher and game compete for RAM. The full spec lives in
        // TurtleAiPrompt.LAUNCHER_SIDE_MEMORY_MANAGEMENT.
        Topic(
            id = "memory_prelaunch",
            label = "Memory before launch",
            keywords = listOf(
                // Multi-word keys on purpose: the plain words ("memory", "ram", "cache")
                // belong to the RAM / storage topics, and a tie is resolved alphabetically -
                // which would hand these questions to those topics instead of this one.
                "before launch", "before launching", "launcher memory", "launcher cache",
                "free ram", "free up ram", "background apps", "background work",
                "close apps", "flush caches", "memory before"
            ),
            answer = { context ->
                val deviceTotalMb = runCatching { Tools.getTotalDeviceMemory(context) }.getOrDefault(0)
                val ramMb = runCatching { AllSettings.ramAllocation.value.getValue() }.getOrDefault(0)
                val cleanupOn = runCatching { AllSettings.backgroundServiceOptimization.getValue() }
                    .getOrDefault(false)
                val monitorOn = runCatching { AllSettings.memoryPressureMonitor.getValue() }
                    .getOrDefault(false)
                val prefetchOn = runCatching { AllSettings.backgroundAssetPrefetch.getValue() }
                    .getOrDefault(false)
                buildString {
                    append("Before Minecraft starts, the launcher releases its own memory and gets " +
                        "out of the way - every MB it holds is one Android can't give the game JVM.\n\n")
                    append("What happens at launch:\n")
                    append("• Image/bitmap caches are dropped (cover art, screenshots, launcher " +
                        "backgrounds) - they re-decode on demand, nothing on disk is touched.\n")
                    append("• The in-memory download/search caches are dropped - they refill the " +
                        "next time you open the Download screen.\n")
                    append("• Background work is held back for the whole session: asset prefetching " +
                        "and the plugin-update check skip themselves, and the launcher's task " +
                        "threads drop to background priority.\n")
                    append("• Launcher animations pause during the session and resume when you " +
                        "come back.\n")
                    append("• No world, save, mod, log or shader-cache file is deleted to make room.\n\n")
                    append("On this device: ${deviceTotalMb}MB total RAM, ${ramMb}MB allocated to " +
                        "Minecraft.\n")
                    append("• Pre-launch cleanup (Settings → Experimental → Background Service " +
                        "Optimization): " + onOff(cleanupOn) + "\n")
                    append("• Memory pressure monitor (Settings → Phone Settings): " + onOff(monitorOn) + "\n")
                    append("• Background asset prefetch: " + onOff(prefetchOn) + "\n\n")
                    append("The rule that matters: keep the heap at roughly half the device total " +
                        "or less, and leave 2GB+ for Android itself. If the launcher and the game " +
                        "are fighting over RAM, a smaller heap helps and a bigger one makes it " +
                        "worse - an oversized heap pushes the system into killing processes, " +
                        "which looks exactly like a random crash. More RAM never adds FPS.")
                }
            },
            followUps = listOf("Not enough RAM?", "FPS is low", "Status")
        ),
        Topic(
            id = "performance",
            label = "FPS is low",
            keywords = listOf(
                "fps", "slow", "laggy", "performance", "frame", "frames", "stutter", "boost",
                "speed up", "optimize", "optimise", "heat", "hot", "thermal", "battery"
            ),
            answer = { context ->
                buildString {
                    append("In rough order of how much they actually help on a phone:\n")
                    append("1. Settings → Video → Resolution scale - drop it to 80-90%. Biggest single win.\n")
                    append("2. Renderer choice - ask me \"best renderer?\"; the wrong one can cost half your FPS.\n")
                    append("3. Settings → Optimization - the FPS boost flags (frame skipping, adaptive frame timing).\n")
                    append("4. Settings → Video → Sustained performance - stops the CPU throttling down mid-session, " +
                        "at the cost of more heat/battery.\n")
                    append("5. In-game: render distance down, and a performance mod (Sodium/Fabric or Embeddium) " +
                        "from the Download screen.\n\n")
                    append("Current renderer: ${currentRendererName(context)}.")
                }
            },
            followUps = listOf("Best renderer?", "Not enough RAM?")
        ),
        // The per-frame half of performance: what the renderer/EGL layer can actually be told
        // to do about GL state overhead, and what is renderer-internal. Rules come from
        // TurtleAiPrompt.glStateRules() so this answer can't drift from the AI spec.
        Topic(
            id = "renderer_state",
            label = "Renderer overhead / GL state",
            keywords = listOf(
                "gl state", "opengl state", "state change", "state changes", "redundant",
                "draw call", "draw calls", "bottleneck", "overhead", "frame time", "frame times",
                "jni batching", "object pooling", "buffer uploads", "readback", "glfinish"
            ),
            answer = { context ->
                val rendererId = currentRendererId(context)
                val isGl4es = rendererId == HolyGL4ESRenderer.ID
                val shaderCache = runCatching { AllSettings.rendererShaderCacheEnabled.getValue() }
                    .getOrDefault(false)
                val forceVsync = runCatching { AllSettings.forceVsync.getValue() }.getOrDefault(false)
                val adaptiveVsync = runCatching { AllSettings.adaptiveVsync.getValue() }.getOrDefault(false)
                val vsyncInZink = runCatching { AllSettings.vsyncInZink.getValue() }.getOrDefault(false)
                val lowLatency = runCatching { AllSettings.lowLatencyFrontBuffer.getValue() }
                    .getOrDefault(false)
                buildString {
                    append("Current renderer: ${currentRendererName(context)}\n\n")
                    append("Per-frame GL overhead is where a translated renderer loses time. " +
                        "Here's what this launcher can actually tell it to do:\n")
                    if (isGl4es) {
                        append("• JNI batching (LIBGL_BATCH): " + onOff(
                            runCatching { AllSettings.jniBatching.getValue() }.getOrDefault(false)
                        ) + " - batches GL calls instead of one JNI crossing each\n")
                        append("• Cached buffer references (LIBGL_USEVBO): " + onOff(
                            runCatching { AllSettings.jniCachedReferences.getValue() }.getOrDefault(false)
                        ) + " - keeps vertex data in VBOs instead of re-uploading client arrays\n")
                        append("• Native object pooling (LIBGL_RECYCLEFBO): " + onOff(
                            runCatching { AllSettings.nativeObjectPooling.getValue() }.getOrDefault(false)
                        ) + " - reuses framebuffer objects instead of reallocating them\n")
                        append("• Skip redundant texture copies (LIBGL_SKIPTEXCOPIES): " + onOff(
                            runCatching { AllSettings.reducedJniCalls.getValue() }.getOrDefault(false)
                        ) + " - drops a redundant copy on the texture upload path\n")
                    } else {
                        append("• The GL4ES state flags (JNI batching, cached buffer references, " +
                            "native object pooling, skip redundant texture copies) do not apply " +
                            "to this renderer - they only exist on the GL4ES path (Holy GL4ES). " +
                            "The equivalent caching is internal to whatever renderer you're on.\n")
                    }
                    append("• Renderer shader cache: " + onOff(shaderCache) + " - persists compiled " +
                        "program caches for Zink and the gallium-based renderers\n")
                    append("• Swap/pacing: VSync " + onOff(forceVsync) + ", adaptive VSync " +
                        onOff(adaptiveVsync) + ", Zink VSync " + onOff(vsyncInZink) + ", " +
                        "low-latency front buffer " + onOff(lowLatency) + "\n\n")
                    append("What is NOT a switch here - and I won't pretend otherwise: skipping " +
                        "redundant state changes and texture binds, avoiding redundant clears, " +
                        "and avoiding GPU readbacks are internal to the renderer and the game. " +
                        "If one of those is the bottleneck, the lever is a different renderer, " +
                        "not a setting.\n\n")
                    append("If you're chasing stutter: check frame-time consistency first " +
                        "(thermal throttling, GC pauses, other apps) - a 60 FPS average with " +
                        "35ms spikes is not fixed by any of the above. Then change one flag at a " +
                        "time and relaunch; the GL4ES flags trade speed for stability on some " +
                        "devices.")
                }
            },
            followUps = listOf("FPS is low", "Best renderer?", "Why did my game crash?")
        ),
        Topic(
            id = "mods",
            label = "How do I install mods?",
            keywords = listOf(
                "mod", "mods", "modpack", "mod pack", "fabric", "forge", "neoforge", "quilt",
                "optifine jar", "install mod", "curseforge", "modrinth", "resource pack",
                "texture pack", "world download", "mrpack"
            ),
            answer = {
                "Mods and packs: top bar → Download.\n" +
                    "• Mods / Modpacks / Resource packs / Shader packs / Worlds are separate tabs there, " +
                    "searching Modrinth and CurseForge.\n" +
                    "• Mod loaders (Fabric, Forge, NeoForge, Quilt, OptiFine, Cleanroom) are installed per " +
                    "version - open the version's manager (the gear next to the version selector on the home " +
                    "screen) and pick your loader.\n" +
                    "• A .mrpack/.zip modpack you already have: home screen rail → Modpack Importer.\n" +
                    "• A loose .jar mod or library: rail → Install JAR.\n" +
                    "• Files screen gets you straight to the instance's mods/ folder.\n\n" +
                    "A mod must match BOTH the game version and the loader version, or it won't load - that's " +
                    "the most common \"mod does nothing\" cause."
            },
            followUps = listOf("Shaders not working", "Which game version?")
        ),
        Topic(
            id = "controls",
            label = "Controls",
            keywords = listOf(
                "control", "controls", "button", "buttons", "joystick", "gamepad", "controller",
                "keyboard", "mouse", "cursor", "touch", "layout", "keybind", "key binding",
                "hotbar", "remap"
            ),
            answer = {
                "Home screen rail → Custom Controls opens the control editor: add/move/resize buttons, " +
                    "joysticks and hotbars, save layouts as presets (a survival preset ships with the launcher).\n\n" +
                "• Settings → Control: sensitivity, control layout picker, gamepad options.\n" +
                "• Settings → Accessibility: button size/opacity, and the notch/ignore-notch options.\n" +
                "• Top bar → Cursor opens the custom mouse/cursor manager (you can import .cur/.ani/.ico files).\n" +
                "• A physical gamepad is detected automatically while the game runs; mapping is in the " +
                "control editor's remapper.\n" +
                "• In-game: the pause menu button opens the launcher's game menu (screenshot, controls, log)."
            },
            followUps = listOf("Screenshots and recording", "FPS is low")
        ),
        Topic(
            id = "skins",
            label = "Skins and capes",
            keywords = listOf("skin", "skins", "cape", "capes", "avatar", "appearance"),
            answer = {
                "Account screen → your account → Skin/Cape. You can browse a built-in gallery, import a " +
                    "64x64 (or 64x32 legacy) PNG from your device, and pick classic/slim arms.\n\n" +
                "With a Microsoft account the skin is applied to your real profile. With a local (offline) " +
                "account it's applied locally through the launcher's own skin server, so only you see it.\n\n" +
                "There's also an optional AI skin filter (Settings → Experimental) that screens gallery " +
                "images - it needs your own API key and is off by default."
            },
            followUps = listOf("Accounts and login", "Status")
        ),
        Topic(
            id = "accounts",
            label = "Accounts and login",
            keywords = listOf(
                "account", "accounts", "login", "log in", "sign in", "microsoft", "offline",
                "premium", "token", "auth", "authlib", "nide8", "session", "demo"
            ),
            answer = {
                "Top bar → Account (the person icon).\n" +
                    "• Microsoft: the real login flow, needed for online play on official servers.\n" +
                    "• Local: an offline username - single player and LAN/offline-mode servers only.\n" +
                    "• Other: third-party auth servers (authlib-injector / nide8auth style) via a custom URL.\n\n" +
                "If a Microsoft login stalls, it's nearly always the device clock being wrong or a browser " +
                    "handoff being cancelled - retry from the Account screen. Being dropped to Demo.Player " +
                    "means the token refresh failed while offline; reconnect and log in again."
            },
            followUps = listOf("Friends / LAN", "Status")
        ),
        Topic(
            id = "versions",
            label = "Which game version?",
            keywords = listOf(
                "version", "versions", "snapshot", "release", "1.21", "26.1", "26.2", "26.3",
                "update minecraft", "lwjgl", "which version", "install version", "old version"
            ),
            answer = {
                "Home screen → the version card (or its dropdown) lists installed versions; Install Game " +
                    "adds new ones, including snapshots and old releases.\n\n" +
                "• Newer isn't automatically better on a phone: mod support and renderer compatibility both " +
                "matter more than the version number.\n" +
                "• Minecraft 26.3+ moved its windowing from GLFW to SDL (org.lwjgl:lwjgl-sdl). This launcher " +
                "detects that per version and swaps in the matching LWJGL native; Settings → Experimental → " +
                "\"LWJGL compatibility mode\" can force new/legacy if a specific version misbehaves.\n" +
                "• If a brand-new snapshot crashes at startup, try the previous release before reporting it - " +
                "and ask me \"why did my game crash?\" so I can read the log."
            },
            followUps = listOf("Best renderer?", "Why did my game crash?")
        ),
        Topic(
            id = "terracotta",
            label = "Friends / LAN",
            keywords = listOf(
                "friend", "friends", "lan", "multiplayer", "terracotta", "join", "host", "room",
                "code", "p2p", "play with friends", "server"
            ),
            answer = {
                "Home screen → Friends/LAN (Terracotta). One player hosts a room and shares the code; the " +
                    "other joins with it. It builds a direct peer-to-peer link (EasyTier based) - no port " +
                    "forwarding - and there's a built-in chat while connected.\n\n" +
                "Known rough edges, so you don't chase them:\n" +
                "• Joining can take a few tries; the launcher retries on its own before showing an error.\n" +
                "• \"Use EasyTier public node\" points at the official public node - if it's unreachable, " +
                "Settings → Terracotta lets you put in your own node address.\n" +
                "• Both players need the same game version and compatible mods."
            },
            followUps = listOf("Accounts and login", "Status")
        ),
        Topic(
            id = "storage",
            label = "Storage and files",
            keywords = listOf(
                "storage", "space", "files", "folder", "directory", "game dir", "profile path",
                "delete", "clean", "cleanup", "cache", "where are", "screenshot folder", "saves"
            ),
            answer = { context ->
                val free = freeStorageGb()
                buildString {
                    append("Top bar → Files opens the game directory; from there you can reach saves/, " +
                        "resourcepacks/, mods/ and the logs.\n\n")
                    if (free != null) append("Free space on that drive right now: ${free}GB.\n")
                    append("• Settings → Launcher → Profile path moves the whole game directory (useful when " +
                        "internal storage is tight).\n")
                    append("• Settings → Experimental has cleanup tools for old caches and logs.\n")
                    append("• Each version keeps its own folder under versions/, so deleting one version " +
                        "doesn't touch the others.")
                }
            },
            followUps = listOf("Screenshots and recording", "Status")
        ),
        Topic(
            id = "recording",
            label = "Screenshots and recording",
            keywords = listOf(
                "screenshot", "screenshots", "record", "recording", "video", "capture", "share log",
                "share logs", "upload log"
            ),
            answer = {
                "While the game runs, the in-game menu button has Screenshot and Record.\n" +
                    "• Settings → Recording: resolution, bitrate and format for the recorder.\n" +
                    "• Screenshots land in the instance's screenshots/ folder (Files screen).\n" +
                    "• Home rail → Share Logs packages the launcher + game logs and gives you a link you " +
                    "can paste into a bug report - that's the fastest way to get help with a crash."
            },
            followUps = listOf("Why did my game crash?", "Storage and files")
        ),
        Topic(
            id = "java",
            label = "Java / runtime",
            keywords = listOf(
                "java", "jre", "jdk", "runtime", "java args", "jvm", "java 8", "java 17",
                "java 21", "class version", "unsupported class"
            ),
            answer = {
                "The launcher ships its own runtimes (8, 17, 21, 25) and picks per version automatically - " +
                    "Settings → Java shows the selection and lets you force one.\n\n" +
                "• \"Unsupported class version\" / \"class file version\" errors mean the wrong runtime was " +
                "picked: newer Minecraft needs 17 or 21, very old versions need 8.\n" +
                "• Custom JVM arguments go in Settings → Java, but a bad argument there stops the game from " +
                "starting at all - if it won't launch after editing them, clear them first.\n" +
                "• A sandbox policy is applied to the game JVM by default (Settings → Java) - some very old " +
                "mods need it turned off."
            },
            followUps = listOf("Not enough RAM?", "Why did my game crash?")
        ),
        Topic(
            id = "shizuku",
            label = "Shizuku",
            keywords = listOf("shizuku", "root", "permission", "adb", "system"),
            answer = {
                "Settings → Shizuku. Shizuku is an optional helper app that lets this launcher do a few " +
                    "things a normal Android app can't - e.g. inspect/manage other apps' data or run shell " +
                    "commands for setup - without rooting your device.\n\n" +
                "It's entirely optional: nothing in the launcher requires it, and every feature that can use " +
                    "it has a normal fallback. If Shizuku isn't installed or isn't running, the screen just " +
                    "tells you and the rest of the launcher carries on."
            },
            followUps = listOf("Status", "Storage and files")
        ),
        Topic(
            id = "updates",
            label = "Updating",
            keywords = listOf(
                "update", "updates", "upgrade", "new version of the launcher", "plugin update",
                "renderer update", "outdated", "beta", "release channel"
            ),
            answer = {
                "The launcher checks for its own updates on start and shows an update dialog when there's " +
                    "one; Settings → Launcher has the release channel.\n\n" +
                "Separately, Settings → Renderer Manager / Experimental can check upstream for renderer and " +
                    "driver plugin updates (for example MobileGlues' own releases) - that only touches " +
                    "downloaded plugins, never the built-in renderers bundled in the APK.\n\n" +
                "You're on version " + runCatching { ZHTools.getVersionName() }.getOrDefault("?") + "."
            },
            followUps = listOf("About", "Status")
        ),
        Topic(
            id = "assistant",
            label = "About this assistant",
            keywords = listOf(
                "assistant", "ai", "chatgpt", "gpt", "chat bot", "are you", "who are you",
                "what are you", "api key", "offline", "privacy", "do you send", "llm"
            ),
            answer = { ctx -> assistantSelfDescription(ctx) },
            followUps = listOf("Help", "Status")
        ),
        Topic(
            id = "help",
            label = "Help",
            keywords = listOf("help", "commands", "what can you do", "menu", "options"),
            answer = { helpReply().text },
            followUps = startingSuggestions()
        ),
        Topic(
            id = "about",
            label = "About",
            keywords = listOf("about", "turtle launcher", "turtlelauncher", "what is this app", "version of the launcher"),
            answer = { context -> aboutAnswer(context) },
            followUps = listOf("Status", "Updating")
        )
    )
}
