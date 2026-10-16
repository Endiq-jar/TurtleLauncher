# Renderer, runtime, content and file-dialog additions

## Sources / attribution

- TeamPojavLauncher/PojavLauncher, revision
  `67e2ee9b1e121919fb3a46bade22f943c54e3ca7`:
  https://github.com/TeamPojavLauncher/PojavLauncher
  - PGW-derived MobileGlues settings: `mobileglues/settings/MGConfig.kt`,
    `MGConfigCodec.kt` and their English string resources are adapted here. The
    native integer/string wire format is retained, including unknown-key preservation,
    GL-version clamping, cache limits, and normalized MultiDraw priorities.
  - `game/renderer/impl/GLESRenderSpec.java`: SFPEW renderer chain and Angelica detection.
  - `NewJREUtil.java` / `utils/SignatureCheckUtil.java`: explicit Java 8/17/21/25
    downloads and RSA/SHA-256 runtime-package verification. The public certificate
    `assets/cert.pem` is from the same revision. It is **not a private key**.
- SFPEW implementation: https://github.com/MobileGL-Dev/SimpleFPEWrapper,
  pinned to `d5d1580ce938acea3e7e4d4361fc14564575cb13`, including its pinned
  GLM/glslang/SPIRV-Cross/GoogleTest submodules. Its upstream LGPL license applies;
  upstream source and license files are fetched together by CMake. The build-copy
  CMake adaptation only scopes standalone-project include paths to its source directory.
- tinyfd/NFD bridging and the content-detail pages are TurtleLauncher integrations,
  **not claimed as direct ports**: no corresponding bridge was found in that upstream
  revision. JNI signatures were checked against LWJGL 3.2.3, 3.3.3 and current sources.

## User-facing paths

- **Settings → Video → MobileGlues settings**: error handling, GL version,
  compute/timer/direct-state-access extensions, shader-cache limit, FSR1 presets,
  global and per-function MultiDraw priority. Changes take effect on the next launch.
  Existing config at `<files>/mobileglues/config.json` is preserved; malformed JSON is
  reported instead of silently overwritten. Native probing/benchmarks and the Compose
  interface from upstream are not ported.
- **Settings → Video → Renderer**: SFPEW / MobileGlues, shown only when both native
  libraries are packaged. Angelica is checked in the **actual launched instance's**
  `mods` directory, not the launcher's UI selection; it bypasses SFPEW to avoid two FPEs.
- **Tap a downloaded-content search result**: the existing version/download pane now
  has Description, Gallery and Changelogs pages. Description and changelogs are rendered
  as selectable Markdown, not executable WebView content. Changelogs have a version
  selector. Gallery uses the existing image loader; failures can be retried.
- **Settings → Java → runtime manager → Download Java runtime**: select 8, 17, 21 or
  25. Explicit downloads propagate failures rather than calling a fallback runtime a
  success. Both archive parts must pass signature verification before installation.
  Manual and automatic installs share a synchronized installer. Local `.tar.xz` import
  remains available. Reopen the manager after installation to select the new runtime.
- ANGLE's renderer, plugin manager, preferences, automatic forcing, repair actions,
  package query and bundled EGL/GLES libraries were removed. Old saved renderer UUIDs
  use the existing renderer fallback. The MobileGlues native config is migrated to
  `enableANGLE=2` (ForceDisable); obsolete `enableAngle` is removed.

## Native file dialogs

`libturtlefiledialogs.so` holds the Android VM bridge. A small Java-8 guest bootstrap
initializes it for **GLFW and SDL launches**, without requiring a particular GLFW ABI.
It registers tinyfd's open/save/folder JNI entry points. New `liblwjgl_nfd.so` and
`liblwjgl_nfd_portal.so` register NFD JNI methods, including the different legacy (3.2)
and extended (3.3+) signatures and versioned `_With` open/save/folder arguments.
Other tinyfd dialogs continue using the existing native library.

The file manager is Android's Storage Access Framework. On successful selection,
files are streamed on a worker into private `files/native-dialogs/<request>/<index>/`;
only real filesystem paths are returned to the game. Multiple tinyfd results use its
`|` separator; NFD path sets have independent owned allocations, enumeration, count,
and free operations. Cancellation, missing host, concurrent requests, a ten-minute
wait limit, picker errors and host destruction return cancellation, not bogus paths.
No UI thread waits on a latch. Late picker results cannot complete a newer request.

Folder selection imports a **snapshot**, not a writable mount of the provider tree;
folder edits are not synchronized back. The snapshot is limited to 10,000 entries and
32 levels. Save selection returns a staging path and uses close-write/move notifications
to copy the mod's output to the selected document. Atomic renames are handled, but a
process kill before publication can still lose a save; document-provider behavior needs
device testing. Imports/staging files remain in private app storage rather than being
silently deleted while a mod might still hold their paths. Clearing app data removes them.

Filters are best-effort MIME hints, not strict extension validation. Unsupported MIME
extensions remain selectable. NFD multiple-folder selection is not implemented. Arbitrary
third-party native libraries using their own dialogs are outside this LWJGL JNI bridge.

## Build and verification

The new native targets are built with Android NDK **27.2.12479018** and CMake **3.22.1**.
SFPEW needs C++20 (including `<format>`); an older host GCC is not sufficient. The first
build needs GitHub access to fetch SFPEW and its pinned submodules. Existing native
libraries remain prebuilt; the old `jni/Android.mk` is deliberately not enabled wholesale.
The guest bridge is compiled by `:jre_lwjgl3glfw:fileDialogBridgeJar`, a dependency of
Android `preBuild`. It adds a separate jar without replacing the manually maintained
GLFW fat jar or SDL bootstrap.

Checks performed in the sandbox:

- Native bridge host compilation/linking for all three bridge targets.
- `tests/native/file_dialog_bridge_test.c` under AddressSanitizer and UBSan: passed
  (ownership, UTF-8 paths, enumeration, cancellation and legacy/extended layouts).
- `tests/kotlin/MobileGluesModelCheck.kt`: compiled and passed with the Kotlin compiler
  (wire values, version clamping, cache state and MultiDraw invariants).
- Android bridge Kotlin compiled against Android API 35 with minimal launcher/annotation
  stubs; its static native/JNI method signatures were also checked by JVM reflection.
  SFPEW Kotlin adapter compiled against its renderer interface with a config stub.
- Changed XML parsed and patch whitespace checked.
- The local sandbox cannot download the Gradle distribution, and its GCC 12 lacks
  SFPEW's `<format>`. Full Android validation is therefore performed in GitHub CI.
- GitHub run `38890705914` at `f890ee1` passed
  `:TurtleLauncher:testDebugUnitTest :TurtleLauncher:assembleRelease` using NDK 27
  on 2026-10-16 and uploaded the release APK. This validates Android compilation,
  not actual renderer output or Android/Discord runtime behavior.

Before release, require green CI for the current revision and test on real devices:

1. MobileGlues settings persist/reload and reach the native renderer after restart.
2. SFPEW starts old fixed-function and modern Minecraft; Angelica bypass uses the right instance.
3. Description/gallery/changelogs: empty, offline, retry, rotation and back navigation.
4. Java: all supported device ABIs, corrupt signatures, offline failure and runtime selection.
5. Flashback and another tinyfd/NFD mod: single/multiple imports, Unicode/space names,
   cancel, picker failure, rotation/destruction, folder snapshots and exported saves.
6. Existing ANGLE users upgrade with a saved renderer override without losing a launch.

Automatic launcher/game Discord presence is documented separately in
[DISCORD_PRESENCE.md](DISCORD_PRESENCE.md), including official token-free Android
RPC, build verification, SDK notices and remaining on-device/release checks.
