# Maximum FPS Configuration Guide

This guide explains how to configure TurtleLauncher for maximum FPS in Minecraft while ensuring the launcher uses minimal RAM and Minecraft gets maximum resources.

## Changes Made

### 1. Default JVM Arguments (javaArgs)
Updated the default JVM arguments to be more aggressive for FPS:
- Reduced `MaxGCPauseMillis` from 20ms to 4ms (minimizes GC pauses)
- Increased `G1HeapRegionSize` from 16M to 32M (better for larger heaps)
- Increased `G1NewSizePercent` from 20% to 40% (larger young generation)
- Decreased `G1ReservePercent` from 20% to 15% (less reserved space)
- Added `-XX:+DisableExplicitGC` (prevents mod-induced GC calls)
- Added `-XX:+AlwaysPreTouch` (pre-allocates heap to avoid runtime stalls)
- Added `-XX:+PerfDisableSharedMem` (reduces I/O jitter)
- Added `-XX:+UseStringDeduplication` (reduces memory overhead from strings)

### 2. Auto Settings Optimizer
Enabled by default (`autoSettingsOptimizer = true`) to automatically:
- Select the best renderer for your GPU
- Allocate optimal RAM based on your device
- Enable FPS-boosting features for high-end devices

### 3. RAM Preset
Changed default from "balanced" to "high" to allocate more RAM to Minecraft by default.

### 4. Maximum Performance Preset
Created a new preset file at `TurtleLauncher/src/main/assets/turtle_presets/maximum_performance.json` with:
- 4096MB RAM allocation (adjust based on your device)
- All FPS-boosting features enabled
- Aggressive GC tuning
- Equal heap sizes (-Xms = -Xmx)

## How to Use

### Option 1: Use the Maximum Performance Preset
1. Open TurtleLauncher
2. Go to Settings > Presets
3. Select "Maximum Performance" preset
4. Apply and launch Minecraft

### Option 2: Manual Configuration
For maximum FPS with custom RAM allocation:

1. **Increase Minecraft RAM:**
   - Go to Settings > Game Settings
   - Set `RAM Allocation` to the maximum your device can handle (recommended: 70-75% of total RAM)
   - Enable `Equal Heap Sizes` (reduces GC pauses)

2. **Enable FPS Boost Features:**
   - Settings > Phone Settings > TurtleLauncher FPS Boost:
     - ✅ Unlimited FPS (removes 300 FPS cap)
     - ✅ Low Latency Rendering (JVM optimizations)
     - ✅ Frame Pacing (smoother frame delivery)
     - ✅ Frame Skipping (reduces input lag when overloaded)
     - ✅ Adaptive Frame Timing (short GC pause target)
     - ✅ Auto Memory Cleanup (periodic GC to prevent spikes)

3. **Optimize JVM Arguments:**
   - Settings > Game Settings > Java Arguments:
   ```
   -XX:+UseG1GC -XX:MaxGCPauseMillis=4 -XX:G1HeapRegionSize=32M \
   -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=50 -XX:G1ReservePercent=15 \
   -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:+PerfDisableSharedMem \
   -XX:+UseStringDeduplication -XX:+UnlockExperimentalVMOptions -XX:G1PeriodicGCInterval=300000
   ```

4. **Renderer Optimization:**
   - Settings > Video Settings:
     - Select the best renderer for your GPU (Freedreno for Adreno, Zink for Mali with Vulkan)
     - Enable all JNI optimizations:
       - ✅ JNI Batching
       - ✅ JNI Cached References
       - ✅ Native Object Pooling
       - ✅ Reduced JNI Calls

5. **CPU Settings:**
   - Settings > Phone Settings > CPU:
     - ✅ Auto Detect Cores
     - ✅ Big Core Affinity (use big CPU cores for performance)
     - ✅ Scheduler Tuning (lower thread priority for steadier FPS)

## Launcher Memory Reduction

The launcher itself uses minimal RAM by default:
- No `android:largeHeap` attribute in AndroidManifest
- Memory is automatically trimmed when the app is in the background
- Glide image caching is properly managed

To further reduce launcher memory usage:
1. Disable unnecessary features:
   - Settings > Launcher Settings:
     - ❌ Resource Image Cache (if you don't need cached version icons)
     - ❌ Animation (reduces memory used by animations)
     - ❌ Music (if you don't need background music)

2. Clear cached data periodically:
   - Settings > Launcher Settings > Auto Cleanup

## Recommended Settings by Device Tier

### High-End Devices (8GB+ RAM)
- RAM Allocation: 6000-7000MB (75% of total RAM)
- Resolution Ratio: 100%
- All FPS Boost features: ✅ Enabled
- Renderer: Best for your GPU
- Java Arguments: Use aggressive preset above

### Mid-Range Devices (4-8GB RAM)
- RAM Allocation: 3000-4000MB (60-70% of total RAM)
- Resolution Ratio: 100%
- FPS Boost features: Enable most, disable Frame Skipping if you prefer stability
- Renderer: Best for your GPU

### Low-End Devices (<4GB RAM)
- RAM Allocation: 1500-2000MB (50% of total RAM)
- Resolution Ratio: 80-90%
- FPS Boost features: Enable Adaptive Frame Timing, disable others
- Renderer: MobileGlues or Freedreno

## Monitoring Performance

Enable the in-game HUD to monitor FPS and memory usage:
- Settings > Game Settings:
  - ✅ Show FPS in Game Menu
  - ✅ Show Memory in Game Menu
  - Set refresh rate to 250ms for smooth updates

## Troubleshooting

If you experience crashes or instability:
1. Reduce RAM allocation by 500-1000MB
2. Disable Frame Skipping
3. Increase MaxGCPauseMillis to 10-20ms
4. Check the launcher log for specific errors

## Technical Details

### Why These Settings Work

1. **Lower GC Pause Target (4ms)**: Minimizes frame stutter caused by garbage collection
2. **Larger G1 Regions (32M)**: Better handles large object allocations common in Minecraft
3. **Higher Young Generation (50%)**: More objects stay in young gen, reducing full GC frequency
4. **DisableExplicitGC**: Prevents mods from triggering expensive full GC cycles
5. **AlwaysPreTouch**: Pre-allocates the entire heap at startup, avoiding runtime allocation stalls
6. **PerfDisableSharedMem**: Removes I/O jitter from perf data sharing
7. **StringDeduplication**: Reduces memory overhead from duplicate strings

### Launcher vs Minecraft Memory

- **Launcher RAM**: Managed by Android, typically 100-300MB. Uses default heap size.
- **Minecraft RAM**: Controlled by `-Xmx` flag (ramAllocation setting). This is where you want to allocate the most memory.

The launcher exits or minimizes when Minecraft is running, so its memory usage doesn't directly affect Minecraft's available RAM.

## Version Compatibility

These settings work best with:
- Minecraft 1.17+ (modern Java versions)
- Java 17+ (for best G1GC performance)
- Android 10+ (for proper memory management)

For older versions, some JVM flags may not be available and will be ignored by the JVM.
