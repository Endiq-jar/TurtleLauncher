# Launcher repository and recent-commit comparison

**Audit date: 17 October 2026. Target: TurtleLauncher's current working tree**, not an installed or released APK. This is a source audit, not a performance benchmark.

## Bottom line

Turtle already has much of the common feature set: renderer/driver selection, SFPEW/MobileGlues integration, Android native file dialogs, Java 8/17/21/25 runtime installation, mod/dependency/update tools, MultiMC/Prism imports, favorites, screenshots and recording. Those are not all new features Turtle needs to copy.

The clearest gaps in the inspected code are **26.3+ NeoForge early-window configuration, display refresh-rate selection, newer-Java Cacio-agent gating, an actual Adreno UBWC workaround, and per-instance renderer environment settings**. Some other differences are UI refinements or unverified upstream claims rather than proven speed improvements.

## Method and limits

- Retrieved repository metadata, README and up to **35 latest default-branch commits per repository**. Reviewed selected substantive commit patches, not just titles; inspected matching local implementations and call sites. The snapshot inventory below pins the branches and heads examined.
- This is a targeted comparison, **not an exhaustive review of all history, branches, releases, native binaries or dependencies**. “Not found” means no matching implementation was located in the inspected launcher sources; it does not prove an APK or opaque dependency cannot provide it.
- **Present** = implementation and a relevant caller found. **Partial** = related capability exists, but not the full upstream behavior. **Not found** = the specific addition was not located. **Unknown** = insufficient public source or testing.
- A setting name, comment, README promise or commit title alone does **not** count as a working feature. “Present” still does not mean device-tested. No APKs were installed, no FPS/temperature measurements made, and no full Android build was performed for this audit.
- Several launchers share Mojo/Pojav commits. The same inherited SHA is **one shared change**, not separate optimization evidence for each brand.

## 1. Copper

Correct Android project: [repository](https://github.com/CopperLauncher/Copper-Android). Its older Amethyst-based tree is [repository](https://github.com/CopperLauncher/Copper-AAMC); the similarly named CRIILET1221 desktop project is unrelated.

| Recent upstream change | Turtle status | Evidence / qualification |
|---|---|---|
| SFPEW/MobileGlues setup and Angelica handling, [`91f3a0e14`](https://github.com/CopperLauncher/Copper-Android/commit/91f3a0e14e5c3452c9ab83e6f955a58729fd3edc) | **Present, basic behavior** | `SFPEWRenderer.prepareForGame`, `SFPEW_EGL`, `MG_DIR_PATH`, and JRE handoff exist (T1). This is not proof of binary-version equality. |
| New MobileGlues settings architecture, including isolated query/service code, [`d16a5a3bd`](https://github.com/CopperLauncher/Copper-Android/commit/d16a5a3bd4dffff90344606eb46f93e8b4a142a6) | **Partial** | Turtle has local MobileGlues settings; the new Copper AIDL/query-service architecture was not found (T1). |
| tinyfd/NFD → Android file-manager bridge, [`c27585b51`](https://github.com/CopperLauncher/Copper-Android/commit/c27585b511d6a520cc63a18647a25346cbfde766) | **Present** | Native targets, activity install/result/release and launch bootstrap exist (T2). No need to port this from scratch. |
| Only one runtime download at a time, [`71b1839dd`](https://github.com/CopperLauncher/Copper-Android/commit/71b1839dd095863b0e82e05bb4378c36e2df926d) | **Present at installer level** | Turtle's automatic and explicit Java-install entry points are synchronized (T3). Copper's exact RecyclerView button behavior is not claimed. |
| Show incompatible content versions on demand, [`0056cd30e`](https://github.com/CopperLauncher/Copper-Android/commit/0056cd30ebcdf40e1b2d78285bfe9fb6aa37ad9b) | **Partial / exact toggle not found** | Turtle has loader/version filters and content detail views. This specific detail-screen override was not located (T4). |

The older Copper-AAMC tree also adds Android Game Mode configuration, [`c11e6f995`](https://github.com/CopperLauncher/Copper-AAMC/commit/c11e6f99585d435286afd1fbfd386e3ed0ff490a). Turtle is marked `android:isGame="true"` in its manifest, but the equivalent `game_mode_config` resource/metadata was not found. That is **partial platform integration**, not proof of a performance gain.

## 2. Mojo

[repository](https://github.com/MojoLauncher/MojoLauncher) is a major source of shared renderer/runtime work.

| Recent upstream change | Turtle status | Evidence / qualification |
|---|---|---|
| Optional NoBlocklist agent, [`56841412a`](https://github.com/MojoLauncher/MojoLauncher/commit/56841412a9794b5798e1cc74b866f753f90ef10b) | **Not found** | No matching setting, extracted component or launch-agent injection located. This is a server-access policy feature, not an FPS improvement. |
| Adreno 740 UBWC workaround, `FD_DEV_FEATURES=enable_tp_ubwc_flag_hint=1`, [`ec3c66b9c`](https://github.com/MojoLauncher/MojoLauncher/commit/ec3c66b9c8dc12dfb9cff955df9a738bb0c91c4a) | **Not found as an effective workaround** | Turtle has `Tools.shouldUseUBWC()`, but no caller or corresponding environment-variable emission was found (T5). A helper alone is not parity. |
| Default LTW selection for Better Than Adventure 8+, [`b9959e8cd`](https://github.com/MojoLauncher/MojoLauncher/commit/b9959e8cd07ce74b0cdb2fccdcafe364b98819a7) | **Not found** | LTW itself exists, but a dedicated BTA installer/version detector and this automatic default were not located. |
| System ANGLE library-path corrections, [`4511b7919`](https://github.com/MojoLauncher/MojoLauncher/commit/4511b79197eafe5248c226ce06deaf046c8a9bc8) | **Unknown exact parity** | Turtle has renderer/plugin selection, but the equivalent system-ANGLE path-selection behavior was not established. |

## 3. Pojav Reborn

Identified positively: [repository](https://github.com/TeamPojavLauncher/PojavLauncher) calls itself **“PojavLauncher (a.k.a. Pojav Reborn)”** in its [README](https://github.com/TeamPojavLauncher/PojavLauncher/blob/67e2ee9b1e121919fb3a46bade22f943c54e3ca7/README.md). Do not confuse it with the discontinued original PojavLauncherTeam repository.

Its recent history merges Mojo, including the **same** NoBlocklist commit [`56841412a`](https://github.com/TeamPojavLauncher/PojavLauncher/commit/56841412a9794b5798e1cc74b866f753f90ef10b). Consequently, Turtle's gaps for that shared addition are the same as above, not a separate Reborn-only performance engine. Reborn also adds the MobileGlues directory environment handoff, [`34c965b3d`](https://github.com/TeamPojavLauncher/PojavLauncher/commit/34c965b3db043ad67dc5572da06119fbb7e8ee81); Turtle **already has `MG_DIR_PATH`** (T1). Module/package renaming is not a missing gameplay feature.

## 4. ZalithLauncher 2

[repository](https://github.com/ZalithLauncher/ZalithLauncher2) has several directly relevant compatibility and lifecycle changes.

| Recent upstream change | Turtle status | Evidence / qualification |
|---|---|---|
| Request the highest-refresh display mode **at the current resolution**, [`3f1fa2f81`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/3f1fa2f817fa87b58f2135e39b1e14ea8b3d30a3) | **Not found** | No display-mode selection / `preferredDisplayModeId` / `Surface.setFrameRate` path located. Turtle's FPS options and installer-only AWT pacing are different features (T6). |
| SDL EGL path resolution **and file-existence validation**, [`60ef063fb`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/60ef063fbc00f5cf2225969b8ee32cc71da461fb) | **Partial** | Turtle preserves absolute EGL paths, but the inspected assignment does not validate file existence and bare names use the launcher's native directory (T7). |
| Disable NeoForge's new early-loading window via `fml.toml`, [`c103183a1`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/c103183a1ab82531ba267b240f3c68364ee3593e) | **Not found** | Turtle still supplies the older `-Dfml.earlyprogresswindow=false`; no matching `earlyWindowControl` configuration writer located (T8). |
| Restore render state after surface recreation, [`1862e3609`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/1862e3609ac6c310d624d1b795c503dd68edb1fe) | **Related implementation present; exact fix unverified** | Turtle republishes rebuilt surfaces to SDL and reattaches bridge windows (T9). Different architecture: do not infer this exact regression is fixed. |
| SDL URL/directory opening through launcher integration, [`1f490ed76`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/1f490ed7681d8e1dbc13a7f158207a5fdd737e24) | **Partial** | Turtle's SDL `openURL` still directly launches a generic `ACTION_VIEW` intent using `mSingleton`, rather than the same launcher-aware utility path (T10). |
| Per-version renderer environment configuration, [`5b5ac4126`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/5b5ac4126fd319f88356734299124e18b45b41a7) | **Not found** | Turtle has per-version renderer/driver selection and global `custom_env.txt`, not a matching per-instance environment-settings model (T11). |
| Zip and share a save from save management, [`abff8fa8f`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/abff8fa8fa091ae833c8fdbac2fe7282e2e4009e) | **Partial** | File-manager ZIP creation exists; the dedicated save ZIP-and-share flow was not located (T12). |
| Correct stale-date mod-update prompts, [`b2fd2734d`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/b2fd2734d5377f03fb2e34d5dbfdc47842b5ab6c) | **Different implementation; needs regression test** | Turtle's checker compares installed and candidate version IDs after hash lookup (T13). That is not enough to assert the upstream bug applies or is fully impossible. |

## 5. Zyron

[repository](https://github.com/ZyronLauncherOfficial/ZyronLauncher) explicitly says it is **closed-source/proprietary** and that GitHub hosts documentation only ([README](https://github.com/ZyronLauncherOfficial/ZyronLauncher/blob/d98e55ff2bf857e5db22543eb3aaf8c770b058e7/README.md)).

Advertised downloads, Microsoft/offline accounts and Vulkan rendering overlap Turtle's visible features, but **its rendering optimizations and commit-level implementation cannot be audited here**. No honest “Zyron is faster” or “Turtle has every Zyron optimization” conclusion can be drawn from this repository.

## 6. CS

Two different evidence levels matter:

- [CS official web portal](https://github.com/craftstudioteam/cs-launcher) contains web code and release/download announcements, not the V3 Android implementation. Its [`873637a9f`](https://github.com/craftstudioteam/cs-launcher/commit/873637a9f9085bab7f82a646aef6774f850d6a8e) advertises **V3.1 with CS Client built in**. Turtle's parity with that client is **unknown**, not proven absent or present.
- [CS-LAUNCHER-v2 source](https://github.com/amjadthedev/CS-LAUNCHER-v2) contains Android source. Recent patches add Java 25 / MobileGlues integration, [`4ec1093e6`](https://github.com/amjadthedev/CS-LAUNCHER-v2/commit/4ec1093e65c2904dbf668c910f92d9f9a17abe04), and JRE 21 download URL/fallback changes, [`fb12d362c`](https://github.com/amjadthedev/CS-LAUNCHER-v2/commit/fb12d362cc93000438302b8db1e4a52fd3fea27e).

Turtle **already has Java 25 selection/download plumbing and MobileGlues integration** (T1, T3). Exact download-mirror/fallback equivalence is unverified. The V2 README's “260+ FPS,” “zero stutter” and “no mid-game OOM” promises are **not validated by these commits**. They must not be treated as guaranteed performance results. V2 source does not establish V3.1 implementation parity. An unrelated small `Angel08991/CS-Launcher` repository was excluded from the Craft Studio comparison.

## 7. Cryonix

Reviewed both [V4](https://github.com/cryonixlauncherteam/CryonixLauncherV4) and [V3](https://github.com/cryonixlauncherteam/CryonixLauncherV3).

- V4's [`efbee4753`](https://github.com/cryonixlauncherteam/CryonixLauncherV4/commit/efbee47530ed553f183838d4f4dd0309b157db5c) changes startup/auth/loading/play screens, main-menu UI, preference styling and resources; it also advances a native submodule. The native submodule's internal performance behavior was not audited.
- V3 adds jelly/fade/bounce entrance effects and then disables the global hook to fix startup crashes, [`8cd0a336d`](https://github.com/cryonixlauncherteam/CryonixLauncherV3/commit/8cd0a336d0464cf2260d817cf7da37a3318c9d8d). This is a good example of why an animation addition is not automatically a performance improvement.

**Turtle:** animated logos/backgrounds/transitions, accounts and instance/version UI are already implemented. It does not have to duplicate Cryonix's exact sidebar or splash design. No distinct, proven Cryonix FPS/thermal advantage was established by these inspected patches.

## 8. Hyper

The older [repository](https://github.com/ashmeet-op/HyperLauncher) README says it is no longer maintained and links **Hyper Launcher v3**. Following that link redirects to [repository](https://github.com/ashmeet-op/Hyper_Launcher_v3); comparing only the older repository would miss current work.

- V3 adds game-surface recording and an in-app recordings gallery, [`586f74009`](https://github.com/ashmeet-op/Hyper_Launcher_v3/commit/586f7400934c690ac1966366811bbe2a6c624859), followed by cursor-in-recording and recorder preference work, [`86a92fef1`](https://github.com/ashmeet-op/Hyper_Launcher_v3/commit/86a92fef1d4b8b9ca40c2c67e30501100292feac).
- Turtle **already has game recording, a game-menu control and MediaStore publication** (T14). That is not the same as Hyper's dedicated in-app gallery or guaranteed cursor-overlay capture: those details remain **partial/unverified**.
- V3's [`4f3299837`](https://github.com/ashmeet-op/Hyper_Launcher_v3/commit/4f3299837e6c470d881f99349a6d6a92509e9063) is titled “big core CPU affinity” but that particular patch changes native-plugin management files. It is **not by itself sufficient proof** of a functioning affinity scheduler. Turtle likewise has a `bigCoreAffinity` setting and emits `POJAV_BIG_CORE_AFFINITY`, but an effective native consumer was not established (T15).
- The latest patches include build/JVM-launch integration, [`3fd69f60d`](https://github.com/ashmeet-op/Hyper_Launcher_v3/commit/3fd69f60d704cab82500da59e4602a50e976ec1c), and a renderer display-name correction, [`42f93decf`](https://github.com/ashmeet-op/Hyper_Launcher_v3/commit/42f93decf3c152310a497c8e58ce0d49c87bc6f6). Generic “updates and fixes” messages should not be counted as measured FPS gains.

## 9. Flame

Correct Android source: [repository](https://github.com/FlameLaunchers/FlameLauncher-Android). Its iOS JIT features are not Android feature gaps.

| Recent upstream change | Turtle status | Evidence / qualification |
|---|---|---|
| Avoid Cacio agent on Java 24+, [`b982f398a`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/b982f398ab98493ec8adeb0284098ae254342831) | **Not found** | Turtle adds its Cacio 17 agent for every non-Java-8 runtime (T16). This is a priority compatibility review, not proof that the two bundled agents are identical. |
| Correct newer/suffixed Minecraft version classification, [`ef20b34c0`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/ef20b34c04c80118b86e9a756676f6b42b47d4c3) | **Related support present; exact cases unverified** | Turtle has suffix-aware numeric parsing and a newer SDL/LWJGL launch path (T16). Flame's exact 26.4 snapshot regression needs a local regression case. |
| Install Podium alongside Sodium, including individual mod installs, [`b432105d5`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/b432105d56c585b84e859e6ac355e116370eee77) | **Not found** | Generic dependency resolution exists, but no Podium-specific companion-install rule was found (T17). |
| Fix desktop-native classifier filtering and classpath collisions, [`3aa2ede1a`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/3aa2ede1ad1886fab91627e9c32928fae6d900ee) / [`aa3db72bf`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/aa3db72bf6c01edaf87ff9fbd2f14af65bde829e) | **Partial / not regression-verified** | Turtle deliberately orders its Android bridge before stock LWJGL jars (T16). That is related protection, not verification of all Flame classifier cases. |
| Parallelize loader libraries, enlarge connection pool, default to MobileGlues, [`f8d573f31`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/f8d573f31e9a3a308ac9ed21500e063c5f63b488) | **Related infrastructure present** | Turtle has download-task infrastructure and MobileGlues. Exact concurrency limits/default-renderer policy differ; no download-speed benchmark was made. |
| NeoForge 26.3 launch and beta-list handling, [`f170e3e1b`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/f170e3e1b654f77b0b589c58af6e08bb1303c0f8) | **Unknown exact parity** | Having a NeoForge installer does not prove these newer patch-jar and listing cases work. Needs specific integration tests. |

## 10. Amethyst

[repository](https://github.com/AngelAuraMC/Amethyst-Android) is another source of renderer/SDL work.

- **Conditional MioLib patches / ASM backport selection**, [`f0e225721`](https://github.com/AngelAuraMC/Amethyst-Android/commit/f0e225721b90919807f45a68eed169e02fa4c2c8), and **Arcmetica conflict handling**, [`0be48c39e`](https://github.com/AngelAuraMC/Amethyst-Android/commit/0be48c39e78c13e25632ff631c9af3114667741d): Turtle injects MioLibPatcher, but the matching argument-level selection/conflict logic was **not found** (T18). The internal bundled agent was not reverse-engineered; do not infer every patch is necessarily missing internally.
- **SDL joystick/gamepad opening for detection**, [`bc0736010`](https://github.com/AngelAuraMC/Amethyst-Android/commit/bc0736010be22a9b9beb3e4f7ae7a760942fbfa9): Turtle has Android gamepad support and SDL surface hooks, but the equivalent SDL detection hooks were **not located** in the inspected launcher native sources. Opaque linked SDL libraries were not audited.
- **IME clamping**, [`8da3cf972`](https://github.com/AngelAuraMC/Amethyst-Android/commit/8da3cf972d2597c7c790736e2a6c1cb12f2d1573): **exact parity unverified**; Turtle's input architecture differs, so an IME/controller device regression test is needed rather than copying that class blindly.
- **MobileGlues `LIBGL_ES` setup**, [`3ad110090`](https://github.com/AngelAuraMC/Amethyst-Android/commit/3ad1100904fef8e3aaa7f50f1b6a05cef918b29c): Turtle **does have** `LIBGL_ES` assignment in renderer environment setup (T7). Native-library versions/behavior still require verification.

## 11. Fold Craft Launcher

[repository](https://github.com/FCL-Team/FoldCraftLauncher) is especially useful for current Android compatibility fixes.

| Recent upstream change | Turtle status | Evidence / qualification |
|---|---|---|
| Gate `fml.toml` early-window workaround to MC 26.3+ and restore older loading animation, [`3c433ddc1`](https://github.com/FCL-Team/FoldCraftLauncher/commit/3c433ddc177d047c73ab926d58bda06bbbaf9089) | **Not found** | Same newer-NeoForge gap as Zalith, with a useful version-gating safeguard (T8). Do not disable every old Forge loading screen globally. |
| Avoid null-device gamepad disconnect crash, [`1d72a88b6`](https://github.com/FCL-Team/FoldCraftLauncher/commit/1d72a88b60b6b25e1d3da23d12147bd4c42127dc) | **Equivalent guard present** | Turtle checks `event.getDevice() != null` before dereferencing in `Gamepad`, and guards the D-pad helper too (T19). Other disconnect paths still need device testing. |
| In-game fullscreen switching, [`d8e793e2e`](https://github.com/FCL-Team/FoldCraftLauncher/commit/d8e793e2e44cb112bfd7193b61a468b4a14b1b26) | **Not found in game menu** | No matching fullscreen toggle/caller located in Turtle's inspected game menu/activity. |
| SDL external-keyboard text fix, [`c10f827a7`](https://github.com/FCL-Team/FoldCraftLauncher/commit/c10f827a7beab2fd96c17e06933f0bf3e43d52cd) | **Unknown exact parity** | Turtle has SDL input integration; the exact external-keyboard scenario was not validated. |
| Favorite/pin/reorder versions, [`dda3199ab`](https://github.com/FCL-Team/FoldCraftLauncher/commit/dda3199abd3a4f080621f0a9cfe2badd2d1700d9) | **Partial** | Favorite groups and UI actions exist. Equivalent pinning and drag-reordering were not located (T20). |

## 12. Plain Craft Launcher

[PCL](https://github.com/Meloong-Git/PCL) redirects to **Meloong-Git/PCL**. Also reviewed [PCL Community Edition](https://github.com/PCL-Community/PCL-CE). These are **Windows desktop launchers**, not Android renderer/FPS comparisons.

- Community Edition improves MultiMC pack handling, [`f00b458e7`](https://github.com/PCL-Community/PCL-CE/commit/f00b458e7bcf1368ea4e00148cf5db09bd1f5133). Turtle **already recognizes and installs MultiMC/Prism instance exports** (T21); equality across every pack/component edge case is unverified.
- It adds mod relations, embedded-mod detection and richer dependency parsing, [`ded201ef5`](https://github.com/PCL-Community/PCL-CE/commit/ded201ef59bd0a1cf8d9cdd08011c5ce62c6a7e8). Turtle has a dependency resolver, parser, provided-ID handling and dependency graph exporter, but **not established parity with full jar-in-jar/version-constraint relations UI** (T17).
- Their desktop packaging/runtime code should not be copied wholesale into Android. The useful comparison is UX, pack formats and dependency correctness, not FPS.

## 13. RX

[repository](https://github.com/RxLauncherTeam/RxLauncher) advertises a high-performance open-source Android launcher in its [README](https://github.com/RxLauncherTeam/RxLauncher/blob/1ab394f910ef89ad37d7414599e014ad29aa174c/README.md). However, the current recursive default-branch tree returned only **25 entries**, containing documentation, assets, wrappers and a skeletal LWJGL module; **the Android application implementation is not present in that snapshot**.

Recent default-branch commits are largely README/uploads/deletions. Therefore RX-specific FPS/thermal changes are **not auditable from this source snapshot**. This is not proof that no implementation exists elsewhere or in an older revision; a current source link or identified release source would be needed.

## 14. Additional useful projects

### Copper ModBundle — content-manager ideas, not a separate game renderer

[repository](https://github.com/CopperLauncher/ModBundle) adds content descriptions/gallery/changelog/share UI, [`9729ccca7`](https://github.com/CopperLauncher/ModBundle/commit/9729ccca75201f4a141a474d44cf8e837536e397), and a shared hash-resolution cache with **in-flight deduplication**, [`399a3bab9`](https://github.com/CopperLauncher/ModBundle/commit/399a3bab9ed57945a72cd637ea26915e2bdf42d8). Turtle already has content detail/gallery and hash-based update lookup (T4, T13), but an equivalent shared cross-caller hash-lookup cache/dedup implementation was **not located** in its inspected update path. This is worth considering for installed-content responsiveness/network usage, not in-game FPS.

### HMCL — desktop UX and account work

[repository](https://github.com/HMCL-dev/HMCL) adds screenshot management, [`5768cd446`](https://github.com/HMCL-dev/HMCL/commit/5768cd446e9884a99686d9bef07bd9b6e19c511b), and persisted Microsoft `CompleteGameProfile` caching, [`e4893a6df`](https://github.com/HMCL-dev/HMCL/commit/e4893a6df92403cbfc3d7450ac2550c7cb44b275). Turtle has screenshot capture and a screenshots-folder entry, not an established equivalent of HMCL's richer screenshot manager (T22). Turtle's exact account-cache lifecycle was **not established**; this should remain unknown rather than claiming parity from the presence of account files.

### Original PojavLauncher

[repository](https://github.com/PojavLauncherTeam/PojavLauncher) is archived; its final default-branch notice is [`b12ad0481`](https://github.com/PojavLauncherTeam/PojavLauncher/commit/b12ad048157b3aa255d078c235dd4571e1900309). It is a historical baseline, not the active Pojav Reborn project.

## Recommended order for Turtle

These are recommendations only. **No launcher features were ported, changed or removed for this audit.**

1. **Compatibility first:** reproduce/test NeoForge 26.3+ loading and version-gated `fml.toml` handling; test the current Cacio agent on Java 25 before deciding whether Flame's gating applies; validate renderer plugin EGL/OpenGL paths before handing them to SDL.
2. **Display/GPU correctness:** offer opt-in same-resolution high-refresh mode selection with lifecycle handling; wire and device-test an optional UBWC workaround on affected Adreno hardware. High refresh can increase battery use and heat; it must not be marketed as a cooling fix.
3. **Per-instance control:** renderer environment overrides, favorite pinning/reordering, dedicated save export/share.
4. **Mod correctness/UX:** test Sodium/Podium compatibility policy, nested dependencies/version constraints, update/downgrade detection and shared hash lookup deduplication.
5. **Do not prioritize redoing what exists:** file-dialog bridges, Java 25 plumbing, basic SFPEW/MobileGlues integration, generic recording, basic favorites and MultiMC import.

There is **no evidence from this audit to rank launchers by actual FPS or heat**. That requires matched-device, matched-renderer, matched-modpack tests with frame-time percentiles, sustained temperatures and battery measurements.

## Turtle implementation evidence

Paths are relative to this repository; line anchors refer to the working tree at audit time.

- **T1 — SFPEW and MobileGlues:** [com/endiq/turtlelauncher/renderer/renderers/SFPEWRenderer.kt:10](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/renderer/renderers/SFPEWRenderer.kt#L10). prepareForGame / getRendererEnv; called in JREUtils.java around line 403. MobileGluesSettings supplies local settings.
- **T2 — Native dialogs:** [net/endiq/launcher/MainActivity.java:131](../TurtleLauncher/src/main/java/net/endiq/launcher/MainActivity.java#L131). NativeFileDialogs install/release/result calls; src/main/native/CMakeLists.txt lines 22–27 builds the bridge/NFD targets; LaunchArgs.kt line 82 bootstraps GameFileDialogs.
- **T3 — Runtime installation:** [com/endiq/turtlelauncher/launch/TurtleJREAutoInstaller.kt:64](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/launch/TurtleJREAutoInstaller.kt#L64). 8/17/21/25 mappings; synchronized ensureJavaInstalled/downloadJava; LaunchGame uses the installer.
- **T4 — Content UI:** [com/endiq/turtlelauncher/ui/fragment/DownloadModFragment.kt:80](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/ui/fragment/DownloadModFragment.kt#L80). Detail/gallery tabs and screenshot loading; feature/download/platform/modrinth/ModrinthCommonUtils.kt supplies version facets.
- **T5 — UBWC helper only:** [net/endiq/launcher/Tools.java:943](../TurtleLauncher/src/main/java/net/endiq/launcher/Tools.java#L943). shouldUseUBWC exists, but no caller or FD_DEV_FEATURES emission located.
- **T6 — Existing FPS policy is not display-mode selection:** [com/endiq/turtlelauncher/launch/GameFpsOptions.kt:1](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/launch/GameFpsOptions.kt#L1). FPS option policy; also launch/FrameBudget.kt. No high-refresh display-mode request located in inspected activity/surface sources.
- **T7 — Renderer environment setup:** [net/endiq/launcher/utils/JREUtils.java:264](../TurtleLauncher/src/main/java/net/endiq/launcher/utils/JREUtils.java#L264). Renderer LIBGL_ES handling around 330; SDL_EGL_LIBRARY assignment around 407–418 lacks an existence check.
- **T8 — Old Forge JVM workaround:** [net/endiq/launcher/utils/JREUtils.java:619](../TurtleLauncher/src/main/java/net/endiq/launcher/utils/JREUtils.java#L619). -Dfml.earlyprogresswindow=false; no fml.toml / earlyWindowControl writer located.
- **T9 — Surface lifecycle:** [net/endiq/launcher/MinecraftGLSurface.java:181](../TurtleLauncher/src/main/java/net/endiq/launcher/MinecraftGLSurface.java#L181). Surface recreation reattaches bridge window and republishes to SDL; size/render-state regression equivalence not device-tested.
- **T10 — SDL URL opening:** [org/libsdl/app/SDLActivity.java:1917](../TurtleLauncher/src/main/java/org/libsdl/app/SDLActivity.java#L1917). Generic ACTION_VIEW using mSingleton, without the same launcher-aware directory handling as Zalith.
- **T11 — Version configuration:** [com/endiq/turtlelauncher/feature/version/VersionConfig.kt:18](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/feature/version/VersionConfig.kt#L18). Per-instance renderer/driver/Java fields; JREUtils.java line 348 reads global custom_env.txt. No per-instance env map located.
- **T12 — ZIP support:** [com/endiq/turtlelauncher/ui/fragment/FilesFragment.kt:391](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/ui/fragment/FilesFragment.kt#L391). Compression UI invokes ZipExtractUtils; not the same as save-specific ZIP-and-share.
- **T13 — Hash-based updates:** [com/endiq/turtlelauncher/feature/mod/ModUpdateChecker.kt:40](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/feature/mod/ModUpdateChecker.kt#L40). Hash lookup followed by current/candidate version-ID comparison. ModrinthDirectApi.kt line 94 implements bulk lookup.
- **T14 — Recording:** [com/endiq/turtlelauncher/feature/turtle/ScreenRecorder.kt:1](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/feature/turtle/ScreenRecorder.kt#L1). GameMenuViewWrapper.kt lines 352–353 calls toggle; MediaStore export around line 1145. Dedicated Hyper-style in-app gallery/cursor capture not established.
- **T15 — Affinity setting is not proof:** [com/endiq/turtlelauncher/setting/AllSettings.kt:215](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/setting/AllSettings.kt#L215). bigCoreAffinity setting exists; JREUtils.java around line 255 emits POJAV_BIG_CORE_AFFINITY. An effective native consumer was not established.
- **T16 — Classpath, version parsing and Cacio:** [com/endiq/turtlelauncher/launch/LaunchArgs.kt:44](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/launch/LaunchArgs.kt#L44). Bridge-first classpath; Cacio helper called at 118; suffix parser around 248; unconditional non-Java-8 agent at 297.
- **T17 — Dependencies:** [com/endiq/turtlelauncher/feature/mod/ModDependencyResolver.kt:93](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/feature/mod/ModDependencyResolver.kt#L93). Provided-ID-aware missing-dependency detection. Also ModParser.kt and feature/turtle/DependencyGraphExporter.kt; full nested/version-constraint relations parity not established.
- **T18 — MioLib injection:** [net/endiq/launcher/utils/JREUtils.java:479](../TurtleLauncher/src/main/java/net/endiq/launcher/utils/JREUtils.java#L479). Unconditional agent injection; no matching miolibpatcher.asmBackport / arcmetica argument-level handling found.
- **T19 — Gamepad null checks:** [net/endiq/launcher/customcontrols/gamepad/Gamepad.java:195](../TurtleLauncher/src/main/java/net/endiq/launcher/customcontrols/gamepad/Gamepad.java#L195). getDevice null guard; GamepadDpad.java line 11 also checks for null.
- **T20 — Favorites:** [com/endiq/turtlelauncher/ui/fragment/VersionsListFragment.kt:101](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/ui/fragment/VersionsListFragment.kt#L101). Favorite tabs/actions; VersionAdapter.kt line 245 handles favorite clicks. Pin/drag ordering not located.
- **T21 — MultiMC import:** [com/endiq/turtlelauncher/feature/download/platform/multimc/MultiMCModPackInstallHelper.kt:25](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/feature/download/platform/multimc/MultiMCModPackInstallHelper.kt#L25). Real mmc-pack.json parsing and install flow; ModPackUtils.kt detects the export type.
- **T22 — Screenshots:** [com/endiq/turtlelauncher/ui/fragment/VersionManagerFragment.kt:92](../TurtleLauncher/src/main/java/com/endiq/turtlelauncher/ui/fragment/VersionManagerFragment.kt#L92). Screenshots-folder navigation; feature/turtle/ScreenshotHelper.kt captures screenshots from the game-menu action.

## Upstream snapshots examined

Latest commit date is the default-branch head date, not repository `pushed_at` or a release date. Repositories may have moved since retrieval.

| Project / resolved repository | Default branch | Head | Head date (UTC) | Recent entries read |
|---|---|---|---|---|
| [CopperLauncher/Copper-Android](https://github.com/CopperLauncher/Copper-Android) | `v3_openjdk` | [`cd1929613`](https://github.com/CopperLauncher/Copper-Android/commit/cd19296137943c62503d548eadd2a6ff132b998f) | 2026-10-17 | 35 |
| [CopperLauncher/Copper-AAMC](https://github.com/CopperLauncher/Copper-AAMC) | `v3_openjdk` | [`480d6550f`](https://github.com/CopperLauncher/Copper-AAMC/commit/480d6550f5237babece2c433df1eeaa24e9c1ea7) | 2026-09-22 | 35 |
| [MojoLauncher/MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) | `v3_openjdk` | [`dcbb4e3fa`](https://github.com/MojoLauncher/MojoLauncher/commit/dcbb4e3fa8faee63c9184edfd41edf1964ae73a2) | 2026-10-17 | 35 |
| [TeamPojavLauncher/PojavLauncher](https://github.com/TeamPojavLauncher/PojavLauncher) | `v3_openjdk` | [`67e2ee9b1`](https://github.com/TeamPojavLauncher/PojavLauncher/commit/67e2ee9b1e121919fb3a46bade22f943c54e3ca7) | 2026-10-14 | 35 |
| [ZalithLauncher/ZalithLauncher2](https://github.com/ZalithLauncher/ZalithLauncher2) | `main` | [`57fd80ce2`](https://github.com/ZalithLauncher/ZalithLauncher2/commit/57fd80ce24cb28f7d70762a703bc3bc48b708c18) | 2026-10-16 | 35 |
| [ZyronLauncherOfficial/ZyronLauncher](https://github.com/ZyronLauncherOfficial/ZyronLauncher) | `main` | [`d98e55ff2`](https://github.com/ZyronLauncherOfficial/ZyronLauncher/commit/d98e55ff2bf857e5db22543eb3aaf8c770b058e7) | 2026-08-15 | 8 |
| [craftstudioteam/cs-launcher](https://github.com/craftstudioteam/cs-launcher) | `main` | [`873637a9f`](https://github.com/craftstudioteam/cs-launcher/commit/873637a9f9085bab7f82a646aef6774f850d6a8e) | 2026-08-21 | 20 |
| [amjadthedev/CS-LAUNCHER-v2](https://github.com/amjadthedev/CS-LAUNCHER-v2) | `main` | [`fb12d362c`](https://github.com/amjadthedev/CS-LAUNCHER-v2/commit/fb12d362cc93000438302b8db1e4a52fd3fea27e) | 2026-06-01 | 35 |
| [cryonixlauncherteam/CryonixLauncherV4](https://github.com/cryonixlauncherteam/CryonixLauncherV4) | `main` | [`efbee4753`](https://github.com/cryonixlauncherteam/CryonixLauncherV4/commit/efbee47530ed553f183838d4f4dd0309b157db5c) | 2026-10-10 | 3 |
| [cryonixlauncherteam/CryonixLauncherV3](https://github.com/cryonixlauncherteam/CryonixLauncherV3) | `v3_openjdk` | [`c59d62f66`](https://github.com/cryonixlauncherteam/CryonixLauncherV3/commit/c59d62f669d1fb8656258f31f0055da5059a99ca) | 2026-10-04 | 35 |
| [ashmeet-op/HyperLauncher](https://github.com/ashmeet-op/HyperLauncher) | `v3_openjdk` | [`899fa7d63`](https://github.com/ashmeet-op/HyperLauncher/commit/899fa7d63da68fff01aa9e63fcc2d90dc8550dd1) | 2026-09-19 | 35 |
| [ashmeet-op/Hyper_Launcher_v3](https://github.com/ashmeet-op/Hyper_Launcher_v3) | `master` | [`3fd69f60d`](https://github.com/ashmeet-op/Hyper_Launcher_v3/commit/3fd69f60d704cab82500da59e4602a50e976ec1c) | 2026-10-11 | 35 |
| [FlameLaunchers/FlameLauncher-Android](https://github.com/FlameLaunchers/FlameLauncher-Android) | `main` | [`ef20b34c0`](https://github.com/FlameLaunchers/FlameLauncher-Android/commit/ef20b34c04c80118b86e9a756676f6b42b47d4c3) | 2026-10-08 | 33 |
| [AngelAuraMC/Amethyst-Android](https://github.com/AngelAuraMC/Amethyst-Android) | `v3_openjdk` | [`330c6eae3`](https://github.com/AngelAuraMC/Amethyst-Android/commit/330c6eae3164df64bdc4828e946a9e62cc5169e4) | 2026-09-21 | 35 |
| [FCL-Team/FoldCraftLauncher](https://github.com/FCL-Team/FoldCraftLauncher) | `main` | [`cd14d179c`](https://github.com/FCL-Team/FoldCraftLauncher/commit/cd14d179ce22a806c9eda6353836e5abfa93cf30) | 2026-10-17 | 35 |
| [Meloong-Git/PCL](https://github.com/Meloong-Git/PCL) | `main` | [`ade6e04c6`](https://github.com/Meloong-Git/PCL/commit/ade6e04c6eab65e945b0fc51faae1ba8e714245c) | 2026-10-16 | 35 |
| [PCL-Community/PCL-CE](https://github.com/PCL-Community/PCL-CE) | `dev` | [`f00b458e7`](https://github.com/PCL-Community/PCL-CE/commit/f00b458e7bcf1368ea4e00148cf5db09bd1f5133) | 2026-10-14 | 35 |
| [RxLauncherTeam/RxLauncher](https://github.com/RxLauncherTeam/RxLauncher) | `main` | [`1ab394f91`](https://github.com/RxLauncherTeam/RxLauncher/commit/1ab394f910ef89ad37d7414599e014ad29aa174c) | 2026-09-12 | 35 |
| [CopperLauncher/ModBundle](https://github.com/CopperLauncher/ModBundle) | `main` | [`f5f980b21`](https://github.com/CopperLauncher/ModBundle/commit/f5f980b21f0b3ff140ebf683f8491d834eae521a) | 2026-10-06 | 35 |
| [HMCL-dev/HMCL](https://github.com/HMCL-dev/HMCL) | `main` | [`d03ad38e2`](https://github.com/HMCL-dev/HMCL/commit/d03ad38e248a59fc4d9686c9e0ad60281712eac5) | 2026-10-17 | 35 |
| [PojavLauncherTeam/PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher) | `v3_openjdk` | [`b12ad0481`](https://github.com/PojavLauncherTeam/PojavLauncher/commit/b12ad048157b3aa255d078c235dd4571e1900309) | 2025-09-23 | 35 |
| [FlameLaunchers/FlameLauncher-SDL3](https://github.com/FlameLaunchers/FlameLauncher-SDL3) | `main` | [`26baafb37`](https://github.com/FlameLaunchers/FlameLauncher-SDL3/commit/26baafb37b72bf2dc025f7ea225b5d52d4a9d56c) | 2026-09-26 | 5 |

FlameLauncher-SDL3 was inspected as a companion dependency repository, not treated as an additional Android launcher implementation. No downloaded APK or upstream source archive was added to this repository.
