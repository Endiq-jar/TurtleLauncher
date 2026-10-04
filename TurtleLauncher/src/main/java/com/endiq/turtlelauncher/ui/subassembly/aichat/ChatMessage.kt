package com.endiq.turtlelauncher.ui.subassembly.aichat

/** One message in the AI Chat screen - either from the user or the AI. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    /**
     * Absolute path of an image this turn produced ([TurtleAssistant]'s /image command).
     * A path, not the bytes: the transcript is written to disk as JSON on every turn, and the
     * picture itself already lives in the app's files directory (see TurtleAiImages).
     */
    val imagePath: String? = null
)
