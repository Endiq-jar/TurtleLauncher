#!/usr/bin/env python3
"""Guard the Discord Rich Presence (token-free RPC) so it stays always-on and cannot break.

Presence is the launcher's most visible integration, and it is deliberately fragile in one
way: it only exists while some Activity process holds the SDK client and the election
service keeps it leased. The failure modes this check pins down:

  * it must be registered from the Application, so it starts on every launcher launch;
  * it must survive backgrounding - `onActivityStopped` may not tear the client down
    (a previous version closed it 2s after the last launcher Activity was hidden);
  * it must be kept alive by a heartbeat, because Discord being installed/started *after*
    the launcher, force-stopped Discord, or a dropped RPC pipe raises no lifecycle event;
  * a lane that holds the election but is not connected must be fully re-armed;
  * the social/Friends layer must never touch the SDK bridge, the RPC manager or the
    launcher's startup path in a way that can take presence down with it;
  * the native bridge keeps its bounded refresh (the second recovery net underneath).
"""
import re
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
java = main / "java"
discord = java / "com/endiq/turtlelauncher/feature/discord"

errors = []


def read(path: Path) -> str:
    return path.read_text()


def require(path: Path, needle: str, what: str) -> None:
    if needle not in read(path):
        errors.append(f"{path.name}: missing {what} (`{needle}`)")


lifecycle = discord / "DiscordPresenceLifecycle.kt"
client = discord / "DiscordPresenceClient.kt"
manager = discord / "DiscordRpcManager.kt"
application = java / "net/endiq/launcher/TurtleApplication.java"
launcher = java / "net/endiq/launcher/LauncherActivity.java"
game_service = java / "net/endiq/launcher/services/GameService.java"
native = main / "native/discord/discord_rpc_bridge.cpp"

for path in (lifecycle, client, manager, application, launcher, game_service, native):
    if not path.is_file():
        errors.append(f"missing {path.relative_to(repo)}")
if errors:
    print("Discord presence checks failed:", flush=True)
    for error in errors:
        print(f"  - {error}")
    raise SystemExit(1)

lifecycle_text = read(lifecycle)

# --- starts with the app, not only from a settings screen --------------------
require(application, "DiscordPresenceLifecycle.register(this)",
        "presence registration at Application.onCreate")
require(lifecycle, "application.registerActivityLifecycleCallbacks(this)",
        "activity lifecycle registration")

# --- stays up while backgrounded --------------------------------------------
stopped = lifecycle_text[lifecycle_text.index("override fun onActivityStopped"):]
stopped_body = stopped[:stopped.index("override fun onActivityDestroyed")]
# Strip comments: the body deliberately documents the old tear-down it replaced.
stopped_code = "\n".join(
    line for line in stopped_body.splitlines() if not line.strip().startswith("//")
)
if re.search(r"client\?\.close\(\)", stopped_code):
    errors.append("onActivityStopped tears the presence client down when backgrounded")

# --- heartbeat + recovery ----------------------------------------------------
for needle, what in (
    ("HEARTBEAT_MS", "heartbeat interval"),
    ("RECOVER_INTERVAL_MS", "recover interval"),
    ("private val heartbeat", "keep-alive runnable"),
    ("live.refresh()", "heartbeat re-publish"),
    ("live.recover()", "heartbeat re-arm"),
    ("live.activated &&", "recovery only for the elected lane"),
    ("DiscordRpcManager.isConfigured()", "recovery requires the SDK to be usable"),
    ("!DiscordRpcManager.isConnected()", "recovery is driven by the connection state"),
):
    require(lifecycle, needle, what)
if "handler.postDelayed(this, HEARTBEAT_MS)" not in lifecycle_text:
    errors.append("the heartbeat must re-schedule itself")
if "handler.removeCallbacks(heartbeat)" not in lifecycle_text:
    errors.append("closing the last lease must stop the heartbeat")
# The game process has its own lease; it has to be kept alive too.
require(lifecycle, "GameService.currentPresence()", "game-process lease lookup")
require(lifecycle, "GameService.isActive()", "keep ticking while a game session starts")

# --- client-side re-arm ------------------------------------------------------
require(client, "var activated: Boolean", "election ownership flag")
require(client, "fun recover()", "client recovery entry point")
recover_body = read(client)[read(client).index("fun recover()"):]
recover_body = recover_body[:recover_body.index("fun close()")]
if "DiscordRpcManager.stop(null)" not in recover_body or "refresh()" not in recover_body:
    errors.append("DiscordPresenceClient.recover must shut the SDK down and re-elect (refresh)")

# --- the switch is still respected, and on by default -----------------------
settings = read(java / "com/endiq/turtlelauncher/setting/AllSettings.kt")
if 'BooleanSettingUnit("discordRpcPresenceEnabled", true)' not in settings:
    errors.append("discordRpcPresenceEnabled must default to true")
require(manager, "AllSettings.discordRpcPresenceEnabled.getValue()",
        "the presence switch gate")
require(manager, "fun isConnected()", "connection state")
require(manager, "fun isConfigured()", "SDK availability state")

# --- native bridge still refreshes on its own -------------------------------
native_text = read(native)
if "kRefreshInterval" not in native_text or "ApplyCommand(*latest)" not in native_text:
    errors.append("native bridge lost its bounded presence refresh")
if "kPumpInterval" not in native_text or "RunCallbacks()" not in native_text:
    errors.append("native bridge must keep pumping RunCallbacks()")

# --- social code must not be able to break presence -------------------------
social_dir = java / "com/endiq/turtlelauncher/feature/social"
for path in sorted(social_dir.glob("*.kt")) + sorted(
        (java / "com/endiq/turtlelauncher/ui/fragment/friends").glob("*.kt")):
    text = read(path)
    if "DiscordSocialBridge" in text:
        errors.append(f"{path.name}: social code must not touch the Discord SDK bridge")
    if re.search(r"DiscordRpcManager\.(stop|releaseActivity|setActivity)\b", text):
        errors.append(f"{path.name}: social code must not stop/steal the presence lease")

require(game_service, "public static com.endiq.turtlelauncher.feature.discord.DiscordPresenceClient currentPresence()",
        "game lease accessor")

# Launcher startup: social warm-up is optional, must not run on the main thread or throw.
warm = read(launcher)
require(launcher, "private void warmUpSocial()", "guarded social warm-up")
warm_body = warm[warm.index("private void warmUpSocial()"):]
warm_body = warm_body[:warm_body.index("private void openFriendsScreen()")]
if "SocialRepository.INSTANCE.attach()" not in warm_body:
    errors.append("LauncherActivity must warm the social store from warmUpSocial()")
if "TaskExecutors.getDefault().execute" not in warm_body:
    errors.append("SocialRepository.attach() must not run on the main thread")
if warm_body.count("catch (Throwable") < 2:
    errors.append("warmUpSocial() needs to be exception-guarded")
if "SocialRepository.INSTANCE.attach()" in warm.replace(warm_body, ""):
    errors.append("SocialRepository.attach() must only be called from warmUpSocial()")

if errors:
    print("Discord presence checks failed:", flush=True)
    for error in errors:
        print(f"  - {error}")
    raise SystemExit(1)

print("Discord presence checks passed: always-on heartbeat, self-rearming lease, social isolation")
