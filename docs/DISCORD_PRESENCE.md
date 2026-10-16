# Official token-free Android Discord presence

Turtle Launcher uses Discord Social SDK **1.10.19337** and public application ID
**1560576927419338863**. RPC does not use OAuth, a user token, a bot token, or a client
secret. Discord must be installed and signed in, with activity sharing allowed.
No account-linking or token-entry screen is needed. The settings button opens Discord.
Unused credentials from the former Gateway/OAuth implementations are erased locally
when the presence owner starts; no credentials are migrated into the Social SDK.

## Android lifecycle and process ownership

The SDK's Android RPC implementation uses
`DiscordSocialSdkInit.getEngineActivity()` to bind/unbind Discord. A real Activity
must therefore exist **in the SDK client's process**. An Activity reference cannot
be passed to an isolated process over Binder.

- `DiscordPresenceService` in `:presence` is a lightweight coordinator, not an SDK
  host. Visible LauncherActivity and the existing foreground GameService hold leases.
- The coordinator elects the game process whenever a game lease exists; otherwise
  it elects the visible launcher process. Only the elected process runs the SDK.
- Before starting a new owner, the coordinator asks the old owner to stop and waits
  for acknowledgment. Process death removes its lease. A three-second watchdog
  prevents a hung process from blocking all future handoffs.
- The chosen process calls `DiscordSocialSdkInit.setEngineActivity` with its actual
  LauncherActivity or MainActivity, then initializes the SDK. Native construction,
  publishing, callback pumping and shutdown are serialized away from the UI thread.
- GameService keeps the game lease when Minecraft is backgrounded. Killing only the
  launcher process with Quit Launcher does not kill the game's SDK client.
- Ending the game releases its lease. The launcher resumes browsing presence if its
  lease remains. The launcher has a two-second screen-transition grace period.
- Releasing the last lease shuts down and joins its SDK worker. Destroyed Activities
  are not used to initialize new SDK clients.

The native bridge defines `DISCORDPP_IMPLEMENTATION` in exactly one translation unit,
uses `Client::SetApplicationId` and `Client::UpdateRichPresence`, and never calls
`Client::Connect`. Pending updates can queue before SDK construction completes. Active
presence refreshes at most once per 30 seconds when unchanged, allowing recovery when
Discord becomes available later. Obsolete update callbacks cannot mark a cleared
activity connected. `isConnected()` reports only the latest locally accepted update,
not an independently verified Discord profile state.

## Presence and customization

- Browsing details are “Browsing Turtle Launcher”. Game details include the version.
- `{version}` inserts the running version and `{activity}` inserts “Playing Minecraft”
  or “Browsing Turtle Launcher”. Timers use the SDK's millisecond epoch units.
- Details, state, timer, activity type, app-ID override, artwork and buttons remain
  configurable. Discord controls the displayed application name; the old name editor
  and account-wide status controls are not offered as unsupported capabilities.
- Java UTF-16 strings are converted to standard UTF-8, including emoji and malformed
  surrogate replacement. Button-array lengths are bounded before JNI indexing.
- Missing Discord, signed-out accounts and SDK failures do not constitute a successful
  connection. The launcher logs failures without logging credentials.

## Build integration

`libs/discord_partner_sdk.aar` supplies Java classes, its manifest and consumer
ProGuard rules. `prepareDiscordSdk` stages only this AAR's headers/native binaries
into the module build directory. CMake imports the matching ABI library; matching JNI
copies ensure it is packaged even without global Prefab. The exact duplicate SDK
library is selected once by packaging rules. No unrelated native dependency is imported.

Global Prefab is deliberately disabled: MMKV's Prefab metadata declares a shared
library with a statically linked STL, which fails Prefab's compatibility checks when
combined with a consumer native build. MMKV continues to use its normal JNI library.
The new C++20 targets use NDK **27.2.12479018** and `c++_shared`. Old bundled
`libc++_shared.so` copies are removed so AGP packages the runtime used by that NDK.
The Discord bridge class has explicit R8 keeps for its JNI entrypoints.

## SDK notices and redistribution review

The SDK is proprietary software supplied under [Discord Social SDK Terms](https://support-dev.discord.com/hc/en-us/articles/30225844245271-Discord-Social-SDK-Terms),
not under this repository's GPL. Its original third-party open-source notices are
packaged unchanged at `assets/notices/discord/License-Notices.txt` and readable in
Discord settings. These notices are **not** a license for the SDK itself.
Maintainers must review SDK redistribution terms and compatibility with the project's
GPL/upstream licenses before publishing or merging the bundled SDK. No GPL linking
exception or additional redistribution rights are assumed or granted by this change.

Notice provenance: [SDK 1.10.19337 notices](https://github.com/SFG5453/Orchard/blob/3c8acc1e1e397a5934918c84accfa7bbb1313ab2/vendor/discord_social_sdk/License-Notices.txt),
cross-checked against the [mobile SDK checksum manifest](https://github.com/spotiflacapp/SpotiFLAC-Mobile/blob/5a8952659b247fd95f1914a56db60825657fe26d/scripts/prepare_discord_sdk.py).
Both the bundled AAR and its `discordpp.h` match that manifest exactly:

- AAR SHA-256: `b1b2491f1e1848c79fd6f1986d5aa1e0c8019e88a6e62c7be06b89e8e4870933`
- Header SHA-256: `822e71509bf11b7155becca5fb00d215a7db7b305f357c0f3b5f6e27ff45b6ab`
- Notices SHA-256: `e8afa66340c225431e69768543cc34a7240f3494a9d759189fe118620ea8eebf`

## Verification

- The native bridge compiles against the **real headers from the bundled AAR**.
  Every referenced Discord C symbol is exported by the supplied x86_64 SDK library;
  no unresolved `discordpp::` wrapper definitions remain in the compiled object.
- `tests/native/run-discord.sh` compiles the production bridge against a deliberately
  small **test SDK double**, with AddressSanitizer/UBSan. It tests pre-initialization
  command queuing, nonblocking initialization, stale callback rejection, millisecond
  timestamps, emoji/unpaired-surrogate conversion, shutdown/join and reacquisition.
  This does **not** execute the real SDK or simulate Android Binder.
- `tests/kotlin/DiscordPresenceStateCheck.kt` and the JUnit state tests cover game-first
  owner election and launcher/game lease removal.
- Kotlin client, coordinator, lifecycle, manager and JNI facade compile against Android
  API 35 and the actual SDK Java classes with minimal launcher/settings stubs.
- Existing native file-dialog sanitizer checks are rerun as a regression check.
- Payload unit tests enforce the SDK's 2–128-character state/details and image-tooltip
  bounds, preserve supplementary Unicode characters and reject invalid/overlong button
  URLs instead of truncating them.
- `tests/check_apk.py` checks the generated universal APK for all four native CPU ABIs,
  the SDK/bridge/shared STL, both renderers, dialog libraries and Java-8 guest bootstrap.

Full GitHub CI (run `38890705914`, commit `f890ee1`) passed both
`:TurtleLauncher:testDebugUnitTest` and `:TurtleLauncher:assembleRelease` on
2026-10-16 and uploaded the release APK. Subsequent changes still require their own
successful CI run, including the new packaging check.

The GitHub release build is the full Android compilation gate. Its result must be
checked separately; targeted checks are not a substitute for a successful APK build.
Device testing is still required: launcher/game handoff, launcher open during play,
Quit Launcher, process death, rotation, absent/signed-out Discord, network reconnect,
activity sharing settings and artwork/buttons. The optional minified build variants
also need separate R8/runtime validation; the standard release deliberately disables
minification because of existing Java AWT compatibility constraints. Android force
stop, OS process termination and outages prevent an absolute always-online guarantee.
