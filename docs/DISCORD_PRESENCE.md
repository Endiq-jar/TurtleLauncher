# Automatic Discord presence

## Behavior

- The Discord settings screen no longer contains a token editor/removal menu or an
  RPC on/off switch. The obsolete `discordRpcEnabled` preference is ignored.
- A single bound service in the `:presence` process owns the Gateway connection.
  Visible launcher activities hold one binding; the existing foreground `GameService`
  holds another for the game session. No new foreground-service notification is added.
- Launcher browsing publishes “Browsing Turtle Launcher”. Minecraft publishes its
  running version. Game presence takes priority when the launcher is opened during play.
- Quitting the launcher process does not kill the game's presence owner. Removing a
  binding, a client process dying, or finishing the game releases its presence lease.
  The last lease stops presence and Android destroys the bound service. Launcher
  screen transitions have a two-second grace period to avoid needless reconnections.
- Network failures retry with capped exponential backoff, missed heartbeat ACKs trigger
  reconnection, and invalid authentication/configuration does not retry indefinitely.
  This does not guarantee connectivity through outages, Android force-stop, or OS kills.
- Customization remains available. `{version}` inserts the running Minecraft version;
  `{activity}` inserts “Playing Minecraft” or “Browsing Turtle Launcher”. The default
  name is Turtle Launcher. Browsing uses its own details rather than stale game details.
- Unlink also removes the existing legacy RPC session locally and stops publishing.
  Unlink remains available even if only a legacy session exists, without OAuth identity.

## Important limitation: not token-free authorization

The pre-existing transport authenticates directly to the Discord Gateway using a
previously saved user session. This change retains that private setting **only for
existing authenticated installations**; it does not collect new session tokens, embed
shared credentials, or turn OAuth identify access tokens into Gateway credentials.
Direct user-account Gateway automation may violate Discord's rules and risk the account.

Official Discord on Android does not expose desktop-style local RPC. The existing
`DiscordAuthManager` still has a placeholder application client ID and an identify-only
OAuth flow. Account linking therefore does **not** grant permission to publish presence.
A fresh installation without a legacy authenticated session cannot publish RPC: the
settings screen explicitly reports this instead of claiming to be connected.

Truly automatic, token-free RPC for new installations remains blocked on a supported
Discord SDK/companion transport, a registered application, and an appropriate authorized
presence flow. Removing the settings alone cannot provide those capabilities.

## Validation

- `tests/kotlin/DiscordPresenceStateCheck.kt`: compiled and executed successfully.
- Presence client/service/lifecycle compiled against Android API 35 with minimal
  launcher/manager stubs (not a full Android build).
- RPC manager compiled and exercised with a stubbed JSON/WebSocket transport: pending
  connection reuse, READY-vs-socket reporting, game-to-browsing reset, and missing-session
  behavior passed. No real Discord session or credentials were used for validation.
- JUnit state tests are provided for the normal Gradle test suite, but that suite was not
  run here because the Gradle distribution/Android build environment is unavailable.

Device checks still required: launcher/game handoff, opening launcher during play,
Quit Launcher enabled, returning from game, killing either client process, network
loss/resume, authentication rejection, customization refresh, and unlink during play.
