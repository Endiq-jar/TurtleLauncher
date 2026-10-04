package com.endiq.turtlelauncher.feature.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.endiq.turtlelauncher.feature.log.Logging
import com.endiq.turtlelauncher.setting.AllSettings
import com.endiq.turtlelauncher.ui.subassembly.aichat.ChatMessage
import com.endiq.turtlelauncher.utils.path.PathManager
import java.io.File

object AssistantHistory {

    private const val TAG = "AssistantHistory"
    private const val MAX_MESSAGES = 200

    private fun historyFile(): File = File(File(PathManager.DIR_FILE, "ai_chat"), "history.json")

    @JvmStatic
    fun load(): List<ChatMessage> {
        if (!runCatching { AllSettings.aiAssistantHistoryEnabled.getValue() }.getOrDefault(true)) {
            return emptyList()
        }
        return runCatching {
            val file = historyFile()
            if (!file.isFile) return@runCatching emptyList()
            val text = file.readText()
            if (text.isBlank()) return@runCatching emptyList()
            val array = JsonParser.parseString(text).asJsonArray
            array.mapNotNull { element ->
                if (!element.isJsonObject) return@mapNotNull null
                val obj = element.asJsonObject
                val msgText = obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                    ?: return@mapNotNull null
                val isUser = obj.get("user")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
                val imagePath = obj.get("image")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.takeIf { it.isNotBlank() }
                val mediaPath = obj.get("media")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.takeIf { it.isNotBlank() }
                val mediaLabel = obj.get("mediaLabel")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.takeIf { it.isNotBlank() }
                ChatMessage(msgText, isUser, imagePath, mediaPath, mediaLabel)
            }
        }.onFailure { e ->
            // A corrupt/partial file (process killed mid-write is the realistic case) must
            // not stop the screen from opening - drop it and start clean.
            Logging.w(TAG, "Couldn't read assistant history, starting a fresh conversation", e)
            runCatching { historyFile().delete() }
        }.getOrDefault(emptyList())
    }

    @JvmStatic
    fun save(messages: List<ChatMessage>) {
        if (!runCatching { AllSettings.aiAssistantHistoryEnabled.getValue() }.getOrDefault(true)) return
        runCatching {
            val trimmed = if (messages.size > MAX_MESSAGES) messages.takeLast(MAX_MESSAGES) else messages
            val array = JsonArray()
            trimmed.forEach { message ->
                val obj = JsonObject()
                obj.addProperty("text", message.text)
                obj.addProperty("user", message.isUser)
                message.imagePath?.takeIf { it.isNotBlank() }?.let { obj.addProperty("image", it) }
                message.mediaPath?.takeIf { it.isNotBlank() }?.let { obj.addProperty("media", it) }
                message.mediaLabel?.takeIf { it.isNotBlank() }
                    ?.let { obj.addProperty("mediaLabel", it) }
                array.add(obj)
            }
            val file = historyFile()
            file.parentFile?.mkdirs()
            file.writeText(array.toString())
        }.onFailure { e -> Logging.e(TAG, "Couldn't save assistant history", e) }
    }

    /**
     * Deletes the transcript *and* the images it produced: "clear conversation" has to mean
     * the pictures go too, or the user cannot actually get rid of anything they generated.
     */
    @JvmStatic
    fun clear() {
        runCatching {
            val file = historyFile()
            if (file.exists()) file.delete()
        }.onFailure { e -> Logging.e(TAG, "Couldn't clear assistant history", e) }
        TurtleAiFiles.deleteAll()
    }
}
