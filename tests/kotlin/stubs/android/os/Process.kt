package android.os

/** Headless test stand-in only; never part of the Android application source set. */
object Process {
    const val THREAD_PRIORITY_BACKGROUND = 10
    fun setThreadPriority(priority: Int) { check(priority == THREAD_PRIORITY_BACKGROUND) }
}
