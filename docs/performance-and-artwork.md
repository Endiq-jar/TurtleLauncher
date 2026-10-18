# Performance, wiring and artwork pass

No existing feature, control, original image, world, mod, save, log, or disk cache was removed.

## Gameplay-side fixes

- Auto-tuning no longer assumes that 6 GB of RAM warrants uncapped rendering, string
  deduplication, or heap pre-touch. These options remain available as explicit choices.
  Fresh defaults for those three switches are off. Existing saved choices are retained.
- G1 pause targets are 50/100 ms rather than 10/50 ms. These are **soft GC goals**, not
  frame deadlines; setting 10 ms does not make a 100 FPS frame budget and can increase GC CPU work.
- Thermal state is re-evaluated on every launch, including Fast Boot. Explicit uncapping
  is withheld at Android thermal status MODERATE or higher, or in battery saver.
  This is a launch-time decision, not an in-game thermal governor.
- Auto graphics/profile recommendations track the previous automatic values. A divergent
  manual selection is not silently overwritten. Before the first tracked selection,
  only values matching defaults are considered eligible for auto-tuning.
- After auto-tuning, the running renderer and driver are resolved again from the instance
  configuration, preserving per-instance overrides. Previously only saved settings could
  change while the running launch still used the previously resolved renderer/driver.
- GPU detection preserves the caller's EGL context and tears down the probe resources it
  owns; the fallback does not initialize another leaked display.

### Reversible Unlimited FPS

Explicit Unlimited FPS still sets `maxFps:260` and `enableVsync:false`. Before editing,
`GameFpsOptions` stores their original values in the instance's
`.turtle-fps-options.properties`. Turning the switch off (or launching under thermal/battery
restrictions) restores values **only if they still equal Turtle's edits**. Subsequent
in-game player changes take precedence. Unrelated options and the backup file are retained.

**Legacy limitation:** options already changed by older launcher builds have no rollback
record. The old values cannot be reconstructed reliably. This patch does not guess and
silently overwrite them. On an existing installation, check Minecraft's Max Framerate and
V-Sync once, especially if Unlimited FPS was previously used. An already saved Unlimited
FPS preference is not silently reset by changing its default.

## Launcher/HUD overhead and formerly dead actions

- One lifecycle-owned main-thread callback replaces the HUD Timer and separate posts per
  widget. Pause/destroy cancels polling and TCP probes.
- Fast key/mouse feedback is bounded to 50–1000 ms. Memory/FPS/time/recording telemetry
  refreshes at most once per second; battery reads happen at most every 30 seconds.
  Memory text and the RAM graph share one memory snapshot. Unchanged held-key backgrounds
  are not invalidated repeatedly.
- The recording HUD button remains visible while idle, so it can actually start recording.
- Tapping the ping HUD opens an endpoint entry dialog. It supports hostnames, ports and
  bracketed IPv6. This is **TCP connect latency**, not Minecraft protocol ping. There are
  no probes without a target, no per-failure log spam, and no worker surviving HUD pause.
- Background resets cancel pending Glide requests. Loads use measured target sizing rather
  than potentially passing zero-width/height overrides. Callback updates no longer queue a
  stale image after a replacement request.
- About rows restore recycled button visibility. Artwork opens a larger preview. The
  original `image_about_developer.webp` is decoded by Glide to thumbnail bounds, rather than
  eagerly decoding a 1024px source multiplied by device density. Endiq's developer profile
  is linked separately; the pre-existing Zenkairux credit/artwork is preserved.

## Artwork and access

- **About → Launcher artwork:** six short light-sweep animated WebPs, including
  `ic_turtle_full_animated.webp`. The five other names match the requested original names,
  in `res/raw`, alongside the untouched static drawables. The About title and default
  version icon also use the animation binding.
- Each animation lasts 1.7 seconds, plays once, respects the launcher/system animation
  setting, and stops when its lifecycle pauses or its view detaches. Frame size is bounded
  (192px icons; 768px-wide title). API 26/27 retain static original artwork; native animated
  WebP decoding requires API 28+.
- **Custom Background → Built-in backgrounds:** Turtle Coast, Amethyst Night, Ember Peaks,
  and the original `image_error_background.webp`. Selection persists independently for
  Main Menu, Controls and In Game. Existing image import and reset remain available.
- Static voxel wallpapers are 960×540 WebP with no looping animation/GPU workload.
- Rebuild assets with `python tools/generate_launcher_art.py` (Pillow with libwebp).

## Validation

Executed in this sandbox:

- `python tests/check_resources.py`
- `python tests/check_launcher_art.py` (also added to existing build workflow)
- `JAVA_HOME=... KOTLINC=... tests/kotlin/run-launcher-performance.sh`
  - Tests the real pure Kotlin policy/parser/FPS option code.
  - Tests the real ping worker with local TCP sockets, rapid start/stop, duplicate starts,
    cancellation, and a no-target worker sleeping without polling.
  - Only Android's thread-priority call is replaced by a headless test stand-in.
- `JAVA_HOME=... tests/native/run.sh`
- `JAVA_HOME=... tests/native/run-discord.sh`
- `git diff --check`

Conventional JUnit policy/FPS rollback tests were added under `src/test` for an Android
build environment. **The full Android build and instrumentation tests were not run:** the
sandbox has no Android SDK, and downloading the Gradle distribution fails under its network
allowlist. A temporary Java runtime and Kotlin CLI were used for the headless tests only;
no toolchain binaries were added to the repository.

## Device verification / remaining work

No measured FPS or temperature improvement is claimed without a real Android run. Compare
identical Minecraft version, world, mods, renderer/driver, render distance, resolution,
Java version, heap, FPS cap and charging state. Warm up, then record frame-time distribution,
thermal status and battery temperature over at least 15 minutes on both builds. Test
background/foreground cycles, HUD module toggles and start/stop recording too.

This is not a claim that every repository stub is now implemented. In particular, the
existing `frameSkipping` preference has no presentation backend, and scheduler-tuning/
CPU-affinity environment flags still need a verified native consumer. They were not removed
or replaced with unsafe device-wide tuning. Frame Pacing and Low Latency descriptions now
state their actual existing JVM behavior instead of claiming unimplemented render pacing.
Further renderer/backend work needs device traces and ABI testing rather than speculative
changes to swap buffers or forced CPU clocks.

## Follow-up: idle CPU work and existing custom JVM settings

The second pass addresses further concrete defects rather than applying an arbitrary
Minecraft frame cap:

- The **actual bundled GLFW class**, not just its source, had no-op `glfwWaitEvents`,
  `glfwWaitEventsTimeout`, and `glfwPostEmptyEvent`. Callers expecting to sleep could
  busy-loop. Waits now park the calling thread and dispatch input afterward. Because the
  legacy native queue has no notification mechanism, native input is polled after at most
  an 8ms requested park (scheduler delays can still extend that). Empty events can unpark
  the waiting thread. This is bounded idle waiting, not a swap-buffer FPS limiter.
- `tools/patch_glfw_idle.py` creates a small **additive class overlay** at build time.
  It changes only those three method bodies, retains all 147 methods and the original
  constant-pool entries, and does not rebuild/overwrite the manually maintained fat jar.
  The new component has its own content-hash version and is explicitly first in the guest
  classpath. Its installation does not wipe its destination directory. Python 3 is now
  needed by `prepareGlfwIdleOverlay`; no third-party Python packages are required.
- Swing/AWT installer rendering stops on pause/hidden windows, uses an ownership-checked
  render worker across surface recreation, releases canvases/surfaces in `finally`, and
  avoids repeatedly resetting buffer dimensions after every frame. The **AWT canvas only**
  is bounded to 60 redraws/sec. Minecraft's gameplay render surface is not capped by this.
- Already queued speculative icon/directory prefetch and library/runtime page warming now
  check game-session state again during execution, not only when initially scheduled.
  Shared game downloads are not interrupted or deleted.
- The previous VM merger unconditionally appended G1 after custom flags and overrode
  custom pause settings. Custom Serial/Parallel/ZGC/etc. selections now take precedence,
  with at most one enabled primary collector. G1-only automatic options are not added
  when a different collector is selected. Explicit JVM option values beat automatic
  defaults, including existing saved settings.
- The argument handoff no longer strips every space or drops values with multiple `=`
  characters. Quoted paths/property values, multiline input, repeated agents, and legacy
  adjacent VM switches are parsed in order. Unterminated quotes produce an actionable
  error instead of silently mangling the options. User values containing spaces must be
  quoted, e.g. `-Dpack="My Pack"`.
- Opt-in periodic G1 cleanup now has a system-load threshold instead of using the default
  zero threshold that disables load checking. It remains available; it is not removed.

Additional validation executed:

- `python tests/check_glfw_overlay.py` compares every field/method/attribute against the
  real compatibility jar, verifies only the three empty wait bodies changed, checks
  deterministic packaging/version hashes, and confirms the input jar is untouched.
- `JAVA_HOME=... KOTLINC=... tests/kotlin/run-glfw-idle.sh` runs the production bytecode
  patcher on a functional fixture, executes the modified methods with `-Xverify:all`,
  checks sleep vs. CPU time and cross-thread empty events, then links/verifies the real
  patched GLFW class without loading Android native libraries. An example sandbox result
  was 243ms wall time and less than 1ms CPU for 30 waits; this is **not** a device benchmark.
- The performance check script also round-trips quoted JVM arguments, checks custom GC
  precedence, starts a real local JVM with the resulting Serial GC flags, and tests the
  AWT frame-budget calculation. Equivalent GC/parser assertions are in the JUnit suite.
- APK packaging checks now require the Java-8-compatible overlay and matching content
  hash. The full APK build is still unverified here due to the Gradle/SDK restrictions
  described above. AWT surface lifecycle behavior still needs Android instrumentation.

For device-specific diagnosis, collect the phone model/SoC, Minecraft and Java versions,
selected renderer/driver, resolution/render distance, mod list, FPS cap, and the comparison
launcher's equivalent settings. No universal FPS gain or temperature reduction is promised
without matching real-device runs.
