# Turtle Launcher – Feature Parity Checklist

Status after the Turtle rebrand round (audit vs. the external feature changelog that was provided).

Legend: ✅ already present · ➕ added in this round · ❌ missing (candidate for port)

## Rendering & Performance
| Feature | Status | Notes |
|---|---|---|
| NW (wrapper, experimental) renderer | ➕ | `game/renderer/renderers/NWRenderer.kt` + `libnw.so` for arm64-v8a / armeabi-v7a / x86 / x86_64 under `src/main/jniLibs/`; registered in `Renderers.kt`; excluded from the Mesa/Zink env overrides in `GameLauncher`. |
| Shader cache | ✅ | `MESA_GLSL_CACHE_DIR` wired in `GameLauncher.setRendererEnv()`. |
| EGL configuration / driver selection | ✅ | Renderer env + `SDL_EGL_LIBRARY` plumbing in `GameLauncher`; per-renderer EGL overrides in `game/renderer/`. |
| Adaptive VSync / Low-Latency modes | ◑ | Zink VSync toggle exists (`AllSettings.zinkVsync`); no per-mode adaptive selector. |
| JNI / GL4ES environment toggles | ✅ partial | GL4ES env maps in `game/renderer/renderers/*Renderer.kt`; LIBGL_* toggles editable via renderer settings. |
| Fast Boot / Warm Start / Auto Graphics Optimizer / Battery Saver / cache cleanup / Performance Heatmap | ❌ | Not present in this codebase; port targets from the other repository. |
| Playtime tracking | ❌ | Not present (SavesManagerScreen shows save metadata only). |

## Logs & Diagnostics
| Feature | Status | Notes |
|---|---|---|
| In-game log level colors | ➕ | `LogBox` highlighter: whole ERROR/FATAL lines get a pastel-red highlight, WARN lines pastel-yellow; INFO/DEBUG/normal lines keep the plain background. (`log_parser/Highlighter.kt`, `LogLevelRule.kt`) |
| Log viewer screen | ✅ | `LogViewScreen` (Sora editor). |
| Crash log upload (mclo.gs) | ✅ | `crashlogs/platform/MCLogsAPI.kt`. |
| Crash Analyzer + AI diagnosis / diagnostics export bundle | ❌ | Not present; large port. |
| Screen recording / HUD toggle | ❌ | Not present. |

## Mod & Modpack Management
| Feature | Status | Notes |
|---|---|---|
| Modrinth integration | ✅ | Download/search/info in `game/download/assets/platform/modrinth/`, `ModrinthPackParser`. |
| CurseForge integration | ✅ | API key interception in `UrlManager`, platform clients. |
| Modpack importer (.mrpack / CurseForge / MCBBS) | ✅ | `game/download/modpack/` parsers + install flow. |
| .mrpack export | ✅ | `game/version/export/platform/ModrinthPackExporter.kt`. |
| Mod file fingerprint caching / recommended mods / inline enable-disable / conflict detection / "Apply from URL" / gallery & per-player lookup / 3D preview | ◑ | Mod manager exists with Jar inspection & mod database lookup; 3D skin preview ✅ (`assets/skinview`); recommended-mods rail and URL-apply dialogs ❌. |

## UI / Platform
| Feature | Status | Notes |
|---|---|---|
| Material 3, dark theme, adaptive icons | ✅ | M3 theme in `ui/theme/`; `res/mipmap-anydpi-v26` adaptive icons. |
| Home top bar community links + cursor editor shortcut | ➕ | GitHub, Discord and cursor-settings buttons in `MainScreen` top bar; cursor button deep-links into Settings → Control. |
| About screen Discord card | ➕ | Endiq card + acknowledgements section link to `https://discord.gg/gf3YcV57j`. |
| Self-updater | ✅➕ | `LauncherUpgradeViewModel` now consumes `latest_version_md.json` from *this* repository's GitHub contents API (Zalith-Info API + miawa mirror removed). |
| Home Dashboard + Quick Actions Dock / Clean-room loader flow | ◑ | Launcher home + task menu exist; dedicated dashboard/dock ❌. |

## This round's housekeeping
- Deleted: `api.github.com/repos/ZalithLauncher/Zalith-Info/...` usage, miawa Zalith-info mirror, the MovTery afdian link and the donation-nag dialog.
- Temporarily disabled: the bangbang93 afdian donation button (card kept, `button = {}` + comment) – re-enable by restoring the `Button` in `AboutInfoScreen.kt`.
- All BMCL mirror endpoints (`bmclapi2.bangbang93.com`) intentionally kept: they are download mirrors for Chinese users, not donation links.
- English-sweep: **complete** — every CJK line is now translated across the whole repo (app main + tests, all library modules, and LWJGL patches): verified programmatically at 0 remaining CJK code/comment lines. CJK-in-resources is limited to intentional items: `values/chinese_festivals.xml` is translated, only `translatable="false"` language display names keep native script by design, and mod/modpack locale data assets plus `default_layout.json` locale match-keys stay bilingual on purpose.

## Turtle-Launcher imports (this round)
| Import | Status | Notes |
|---|---|---|
| LTW renderer | ➕ | `LTWRenderer` (id `e7dcb6d0-bf40-44f0-9703-791c7b24c69e`) with bundled `libltw.so` x4 ABIs from Turtle-Launcher's Toast/MojoLauncher integration; exempt from MESA env block. |
| MobileGlues renderer | ➕ | `MobileGluesRenderer` + `libmobileglues.so` x4 ABIs (official release V2.0.0, 2026-08-09 — the binary is unreachable for direct download, so the Turtle-Launcher bundled copy is used); sets `MG_DIR_PATH`, uses itself as EGL, exempt from MESA env block. |
| lwjgl-nanovg natives | ➕ | `liblwjgl_nanovg.so` x4 ABIs in jniLibs (the Java jar was already in `assets/app_runtime/lwjgl/3.3.3` & `3.4.1`); plus `NanoVGNativesFix` copying `assets/compat_mods/lwjgl-nanovg-natives-1.0.2.jar` into `mods/` before launch for Fabric/Quilt instances. |
| Default + Survival control presets | ➕ | Converted from Turtle-Launcher's legacy ZL1 layouts into LayerController v12 JSON (expressions evaluated at 1280x720 reference); seeded alongside the existing default via `unpackDefaultControl`. |
| Modern options.txt seed | ➕ | The 190-line modern defaults file replaces the stale 1.16-era 31-line `assets/game/options.txt`. |

## Image optimization
- All launcher raster resources now prefer WebP: 79 res images + `assets/steve.png` + root `1.jpg` converted (lossless first; only when WebP wins). Five pixel-art textures (`img_chicken_old`, `img_diamond_block`, `img_minecraft`, `img_old_cobblestone`, `img_old_grass_block`) stay PNG because WebP lossless performs worse on them.
- `ic_launcher-playstore.png` intentionally untouched (Play Store upload asset).

## Build fix notes
- `ShizukuManager`: `Shizuku.newUserServiceArgs(...)` → `Shizuku.UserServiceArgs(...)` constructor (the factory method does not exist in shizuku-api 13.x; CI `compileDebugKotlin` failed on it).
- CI failure annotations now print `::error::` lines via the patched Build workflow for readable logs.
