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
- English-sweep: all CJK string literals + ~450 highest-traffic source comments translated; locale data files (`mod_data.txt`, `modpack_data.txt`, `default_layout.json` matchQueues) intentionally kept bilingual.
README: remaining CJK code comments (~7k lines, deep internals) are queued for follow-up translation batches.
