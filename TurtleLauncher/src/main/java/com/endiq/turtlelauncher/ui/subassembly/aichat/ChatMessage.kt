package com.endiq.turtlelauncher.ui.subassembly.aichat

/** One message in the AI Chat screen - either from the user or the AI. */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    /**
     * Absolute path of an image this turn produced (the /image command). Images get their own
     * field because the chat renders them inline.
     *
     * A path, not the bytes: the transcript is written to disk as JSON on every turn, and the
     * picture itself already lives in the launcher's TurtleAI folder (see TurtleAiFiles).
     */
    val imagePath: String? = null,
    /** Path of another generated file - video or speech - shown as an "open it" row. */
    val mediaPath: String? = null,
    /** What to call that file in the row, e.g. "Open the video". */
    val mediaLabel: String? = null
)
