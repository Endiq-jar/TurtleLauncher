fun main() {
    // Verify the actual patched bundled class without initializing its native libraries.
    val type = Class.forName("org.lwjgl.glfw.GLFW", false, ClassLoader.getSystemClassLoader())
    check(type.getDeclaredMethod("glfwWaitEvents").returnType == Void.TYPE)
    check(type.getDeclaredMethod("glfwWaitEventsTimeout", Double::class.javaPrimitiveType).returnType == Void.TYPE)
    check(type.getDeclaredMethod("glfwPostEmptyEvent").returnType == Void.TYPE)
    println("Real bundled GLFW class linked and verified without changing its native API")
}
