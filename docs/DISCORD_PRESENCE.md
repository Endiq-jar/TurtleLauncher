# Automatic Discord presence

## Behavior

- Rich Presence is published through the official **Discord Social SDK** (1.10+,
  Android RPC support added August 2026) using `Client::SetApplicationId` +
  `Client::UpdateRichPresence`. There is no `Client::Connect()` call, no OAuth token, and
  no Discord account credential involved - the SDK talks directly to the Discord app
  installed and signed in on the device over its native RPC channel. See
  `src/main/native/discord/discord_rpc_bridge.cpp` and
  `feature/discord/DiscordSocialBridge.kt`.
- This replaces the previous implementation, which drove the Discord Gateway directly
  using a stored user session token ("self-botting" client automation). That transport
  is removed entirely; `AllSettings.discordRpcToken` no longer exists.
- The Discord settings screen has no token editor and no RPC on/off switch - presence is
  always on while the launcher or Minecraft is in the foreground. A `Discord status`
  picker (online/idle/dnd/invisible) is also removed: the SDK's token-free RPC only
  publishes an *activity*, not the account's online status.
- A single bound service in the `:presence` process owns the native SDK client. Visible
  launcher activities hold one binding; the existing foreground `GameService` holds
  another for the game session. No new foreground-service notification is added.
- Launcher browsing publishes "Browsing Turtle Launcher". Minecraft publishes its running
  version. Game presence takes priority when the launcher is opened during play, and
  quitting the launcher process does not kill the game's presence lease - the same
  `DiscordPresenceState` ownership logic as before is unchanged.
- Customization remains available: name, details, state, activity type, elapsed-timer
  toggle, large/small image asset keys and hover text, and up to two buttons.
  `{version}` inserts the running Minecraft version; `{activity}` inserts "Playing
  Minecraft" or "Browsing Turtle Launcher". `AllSettings.discordRpcApplicationId` can
  override the application ID the activity is published under (defaults to Turtle
  Launcher's own registered ID); custom image asset keys must be uploaded to whichever
  application ID is in effect.
- If the Discord app isn't installed or isn't signed in, `UpdateRichPresence` calls fail
  silently (tracked via `DiscordRpcManager.isConnected()`); nothing here crashes, blocks,
  or retries aggressively.

## What changed and why

The previous transport authenticated directly to the Discord Gateway with a saved user
session token and issued a Gateway `IDENTIFY`, impersonating the signed-in account to
publish presence. Driving a real user account over the Gateway this way is what Discord
calls self-bot automation, and it's against Discord's Terms of Service regardless of
intent - it risks the linked account, not just the launcher. That code path (the
WebSocket client, heartbeat loop, and `discordRpcToken` setting) has been deleted rather
than kept around for "existing sessions."

The Social SDK's Rich-Presence-without-authentication mode is the mechanism Discord
actually ships for this: no token, no login, nothing to store, and nothing that risks
the user's account standing.

## Build integration

- `discord_partner_sdk.aar` (Discord Social SDK, C++/Android) lives in `libs/` and is
  picked up by the module's existing `fileTree(dir = "libs", ...)` dependency - no
  `build.gradle.kts` dependency-block change was needed.
- Its native library and headers are consumed via AGP's `prefab` integration (already
  enabled: `buildFeatures.prefab = true`). `src/main/native/CMakeLists.txt` does
  `find_package(discord_partner_sdk REQUIRED CONFIG)` and links the new
  `turtlediscordrpc` target against `discord_partner_sdk::discord_partner_sdk`.
  `turtlediscordrpc` is added to `externalNativeBuild.cmake.targets` in
  `build.gradle.kts`.
- The AAR's own manifest (merged in automatically) declares the `<queries>` entry for
  the `com.discord` package required on Android 11+, plus `INTERNET`; no changes to the
  launcher's own `AndroidManifest.xml` were needed.

## Validation

- `tests/kotlin/DiscordPresenceStateCheck.kt` and the JUnit `DiscordPresenceStateTest`
  suite cover presence ownership/handoff (`DiscordPresenceState`) and are unaffected by
  this change - no WebSocket/Gateway stubbing is needed anymore since there's no socket
  code left to stub.
- The native bridge and its JNI signatures were checked by hand against
  `discordpp.h`/`cdiscord.h` (SDK 1.10+) and the SDK's own "Rich Presence Without
  Authentication" sample. Full on-device validation (an actual Discord app seeing the
  activity, button clicks, asset resolution, reconnect/teardown timing) still needs a
  real device/emulator with Gradle and the Android NDK available, which this sandbox
  does not have.

Device checks still required: launcher/game handoff, opening the launcher during play,
Quit Launcher enabled, returning from the game, killing either client process, Discord
app not installed, Discord signed out mid-session, customization refresh, and unlink
during play (which no longer affects presence at all, since presence doesn't depend on
the OAuth link).
