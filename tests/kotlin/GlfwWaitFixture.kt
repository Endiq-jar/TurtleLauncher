package org.lwjgl.glfw

/** Stand-in for the prebuilt GLFW class; the production bytecode patcher patches this jar too. */
object GLFW {
    @JvmField var polls = 0
    @JvmField var compatibilitySentinel = "preserved"
    @JvmStatic fun glfwPollEvents() { polls++ }
    @JvmStatic fun glfwWaitEvents() {}
    @JvmStatic fun glfwWaitEventsTimeout(timeout: Double) {}
    @JvmStatic fun glfwPostEmptyEvent() {}
    @JvmStatic fun untouchedMethod(value: String): String = "compat:$value"
}
