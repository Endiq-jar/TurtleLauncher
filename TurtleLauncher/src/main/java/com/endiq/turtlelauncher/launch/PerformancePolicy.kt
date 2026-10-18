package com.endiq.turtlelauncher.launch

/** Hardware RAM size alone says nothing about sustainable GPU power or frame throughput. */
object PerformancePolicy {
    fun mayAutoTune(current: String, lastAutomatic: String?, defaultValue: String): Boolean =
        current == (lastAutomatic ?: defaultValue)

    fun resolutionPercent(totalRamMb: Long, severeThermal: Boolean): Int = when {
        severeThermal -> 70
        totalRamMb in 1..3071 -> 80
        else -> 100
    }

    /** G1's target is a soft GC budget, not a render-frame deadline. 10ms causes excess GC work. */
    fun gcPauseTargetMs(adaptive: Boolean): Int = if (adaptive) 50 else 100

    fun allowUncapped(requested: Boolean, thermalStatus: Int, powerSaving: Boolean): Boolean =
        requested && thermalStatus < 2 && !powerSaving
}
