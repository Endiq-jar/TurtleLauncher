#!/usr/bin/env python3
"""Guard the Friends / social feature's wiring.

The launcher has no JVM in CI-less checks, so this pins the parts that silently rot:
the seven tabs and the four action containers stay in one-to-one correspondence, the
rows the adapter binds still exist in the item layouts, the conversation screen keeps
its list/input ids, the Discord redirect and notification entry points exist on the
launcher activity, and the repository keeps the API the UI calls.

It also re-checks two Kotlin traps this feature already hit: `HttpUrl.parse` (gone from
the bundled OkHttp) and `MutableList.replaceAll { }` (no lambda overload).
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
res = main / "res"
java = main / "java/com/endiq/turtlelauncher"
fragment_dir = java / "ui/fragment/friends"
adapter_file = java / "ui/subassembly/friends/SocialRowAdapter.kt"
message_adapter = java / "ui/subassembly/friends/SocialMessageAdapter.kt"
social_dir = java / "feature/social"

errors = []


def require(path: Path, needle: str, what: str) -> None:
    text = path.read_text()
    if needle not in text:
        errors.append(f"{path.name}: missing {what} (`{needle}`)")


def ids(path: Path) -> set:
    return set(re.findall(r'@\+id/([A-Za-z0-9_]+)', path.read_text()))


def camel(snake: str) -> str:
    head, *tail = snake.split("_")
    return head + "".join(part[:1].upper() + part[1:] for part in tail)


# --- Friends screen layout --------------------------------------------------
friends_layout = res / "layout/fragment_friends.xml"
friends_ids = ids(friends_layout)
for tab in ("friends", "requests", "messages", "voice", "play", "activity", "channels"):
    if f"{tab}_tab" not in friends_ids:
        errors.append(f"fragment_friends.xml: missing {tab}_tab")
for container in ("add_friend_row", "play_actions", "voice_actions", "channel_actions"):
    if container not in friends_ids:
        errors.append(f"fragment_friends.xml: missing {container}")
for control in (
    "account_button", "back_button", "status_text", "friends_list", "tab_scroll",
    "add_friend_input", "add_friend_button", "refresh_button",
    "party_code_input", "create_party_button", "join_party_button",
    "voice_join_button", "leave_voice_button", "mute_button", "deafen_button",
    "ptt_button", "noise_button", "link_channel_button", "unlink_channel_button",
):
    if control not in friends_ids:
        errors.append(f"fragment_friends.xml: missing {control}")

fragment = fragment_dir / "FriendsFragment.kt"
fragment_text = fragment.read_text()
# Exactly one action row may be visible per tab.
for container in ("add_friend_row", "play_actions", "voice_actions", "channel_actions"):
    if f"binding.{camel(container)}.visibility" not in fragment_text:
        errors.append(f"FriendsFragment.kt: never sets {camel(container)}.visibility")
if fragment_text.count("View.VISIBLE else View.GONE") < 4:
    errors.append("FriendsFragment.kt: action rows must be exclusive per tab")
for tab in ("friends", "requests", "messages", "voice", "play", "activity", "channels"):
    require(fragment, f"binding.{camel(tab + '_tab')}.setOnClickListener", f"{tab} tab click")

# --- Row adapter vs. item layouts -------------------------------------------
friend_item = ids(res / "layout/item_social_friend.xml")
request_item = ids(res / "layout/item_social_request.xml")
thread_item = ids(res / "layout/item_social_conversation.xml")
adapter_text = adapter_file.read_text()
for binding, layout_id in (
    ("friendName", "friend_name"), ("friendSource", "friend_source"),
    ("friendStatus", "friend_status"), ("friendActivity", "friend_activity"),
    ("presenceDot", "presence_dot"),
):
    if layout_id not in friend_item:
        errors.append(f"item_social_friend.xml: missing {layout_id}")
    if f"binding.{binding}" not in adapter_text:
        errors.append(f"SocialRowAdapter.kt: never binds {binding}")
for binding, layout_id in (
    ("requestName", "request_name"), ("requestDetail", "request_detail"),
    ("requestActions", "request_actions"), ("acceptButton", "accept_button"),
    ("rejectButton", "reject_button"),
):
    if layout_id not in request_item:
        errors.append(f"item_social_request.xml: missing {layout_id}")
    if f"binding.{binding}" not in adapter_text:
        errors.append(f"SocialRowAdapter.kt: never binds {binding}")
for binding, layout_id in (
    ("conversationTitle", "conversation_title"),
    ("conversationSubtitle", "conversation_subtitle"),
    ("unreadBadge", "unread_badge"),
):
    if layout_id not in thread_item:
        errors.append(f"item_social_conversation.xml: missing {layout_id}")
    if f"binding.{binding}" not in adapter_text:
        errors.append(f"SocialRowAdapter.kt: never binds {binding}")

# Companion members cannot be reached unqualified from the inner ViewHolders.
for line in adapter_text.splitlines():
    stripped = line.strip()
    if stripped.startswith(("fun ", "*", "//", "/*")):
        continue
    for call in ("presenceLabelRes", "presenceColor"):
        if f"{call}(" in line and f"SocialRowAdapter.{call}(" not in line:
            errors.append(
                f"SocialRowAdapter.kt: call {call}() must be qualified with SocialRowAdapter. "
                f"({stripped})"
            )

# --- Conversation screen -----------------------------------------------------
conversation_layout = res / "layout/fragment_social_conversation.xml"
conversation_ids = ids(conversation_layout)
for control in ("conversation_title", "conversation_subtitle", "conversation_status",
                "message_list", "message_input", "send_button", "back_button"):
    if control not in conversation_ids:
        errors.append(f"fragment_social_conversation.xml: missing {control}")
message_item = ids(res / "layout/item_social_message.xml")
for control in ("message_author", "message_body", "message_attachment", "message_meta"):
    if control not in message_item:
        errors.append(f"item_social_message.xml: missing {control}")

conversation_fragment = fragment_dir / "ConversationFragment.kt"
conversation_text = conversation_fragment.read_text()
require(conversation_fragment, "SocialRepository.loadHistory", "history loading")
require(conversation_fragment, "SocialRepository.sendMessage", "sending")
require(conversation_fragment, "SocialRepository.markRead", "read marking")
require(message_adapter, "message.pending", "pending state rendering")
require(message_adapter, "message.failed", "failed state rendering")

# --- Home entry point --------------------------------------------------------
launcher_layout = res / "layout/fragment_launcher.xml"
launcher_text = launcher_layout.read_text()
if "@+id/friends_button" not in launcher_text:
    errors.append("fragment_launcher.xml: missing friends_button")
else:
    rail = launcher_text.index("@+id/quick_actions_rail")
    content = launcher_text.index("@+id/terracotta_button")
    friends = launcher_text.index("@+id/friends_button")
    share = launcher_text.index("@+id/share_logs_button")
    if not rail < friends < share < content:
        errors.append(
            "fragment_launcher.xml: friends_button belongs in the quick actions rail, "
            "next to share_logs_button (the rail sits left of the Friends/LAN card)"
        )
menu = (main / "java/net/endiq/launcher/fragments/MainMenuFragment.java").read_text()
if "binding.friendsButton.setOnClickListener" not in menu:
    errors.append("MainMenuFragment.java: Friends rail button is not wired")
if "FriendsFragment.class" not in menu:
    errors.append("MainMenuFragment.java: Friends rail button does not open FriendsFragment")

# Keep the lifecycle guard the Glide crash fix depends on.
require(
    main / "java/net/endiq/launcher/fragments/MainMenuFragment.java",
    "binding == null || !isAdded()",
    "isAdded guard",
)

# --- Redirect + notification entry points -----------------------------------
manifest = (main / "AndroidManifest.xml").read_text()
if 'android:scheme="turtlelauncher"' not in manifest or 'android:host="discord-callback"' not in manifest:
    errors.append("AndroidManifest.xml: Discord redirect intent-filter is missing")
activity = (main / "java/net/endiq/launcher/LauncherActivity.java").read_text()
require(main / "java/net/endiq/launcher/LauncherActivity.java",
        "EXTRA_OPEN_FRIENDS", "notification extra handling")
require(main / "java/net/endiq/launcher/LauncherActivity.java",
        "isDiscordRedirect", "OAuth redirect handling")
require(main / "java/net/endiq/launcher/LauncherActivity.java",
        "openFriendsScreen", "Friends navigation from an intent")
require(main / "java/net/endiq/launcher/LauncherActivity.java",
        "SocialRepository.INSTANCE.attach()", "keeping the social store warm")
if "setData(null)" not in activity:
    errors.append("LauncherActivity.java: the redirect URI must be consumed")

# --- Repository API the UI depends on ---------------------------------------
repository = (social_dir / "SocialRepository.kt").read_text()
for method in (
    "fun refresh(", "fun addGameFriend(", "fun removeFriend(", "fun setBlocked(",
    "fun setIgnored(", "fun acceptRequest(", "fun rejectRequest(",
    "fun createLobby(", "fun joinLobby(", "fun leaveLobby(", "fun inviteToLobby(",
    "fun inviteToSession(", "fun linkChannel(", "fun listChannelChoices(",
    "fun unlinkChannel(", "fun openConversationForFriend(", "fun openLobbyConversation(",
    "fun openChannelConversation(", "fun loadHistory(", "fun messages(", "fun sendMessage(",
    "fun deleteMessage(", "fun markRead(", "fun closeConversation(", "fun renameIdentity(",
    "fun unlinkDiscord(",
):
    if method not in repository:
        errors.append(f"SocialRepository.kt: missing {method}")

# --- Permanent link, queued sends ---------------------------------------------
# The account is linked once and must stay linked: a stale token is refreshed silently, a
# temporary Discord outage only backs off and retries, and an outgoing message that could not be
# delivered is queued ("Sending…") and re-sent - never turned into a permanent "Not sent" row.
auth = (social_dir / "DiscordAuth.kt").read_text()
models = (social_dir / "SocialModels.kt").read_text()
friends_ui = fragment.read_text()
conversation_ui = conversation_fragment.read_text()
message_adapter_text = message_adapter.read_text()

if "fun accessToken(forceRefresh: Boolean = false): String?" not in auth:
    errors.append("DiscordAuth.kt: accessToken() needs a forceRefresh path (401 retry)")
if "fun grantedScopes()" not in auth or "fun hasScope(scope: String)" not in auth:
    errors.append("DiscordAuth.kt: granted scopes must be readable (scope-aware handling)")
if "fun reconnect(context: Context): Result<String>" not in auth:
    errors.append("DiscordAuth.kt: missing the optional manual reconnect()")

# Refresh tokens rotate, so concurrent refreshes would invalidate each other and log the user out
# of a healthy link. Refreshes must be serialised and rate limited.
if "private val refreshLock = Any()" not in auth or "synchronized(refreshLock)" not in auth:
    errors.append("DiscordAuth.kt: token refreshes must be serialised (rotating refresh tokens)")
if "REFRESH_COOLDOWN_MS" not in auth:
    errors.append("DiscordAuth.kt: a failed refresh needs a cooldown")

for needle, what in (
    ("private const val RELATIONSHIPS_SCOPE", "the relationships.read scope constant"),
    ("fun markDiscordOffline(reason: String?)", "a temporary-offline marker"),
    ("fun markDiscordOnline()", "an online marker that clears the status"),
    ("fun retryDiscordWork()", "background Discord recovery"),
    ("accessToken(forceRefresh = true)", "a refresh-and-retry on 401"),
    ("fun queuedDiscordMessage(", "queued outgoing messages"),
    ("fun retryQueuedMessages()", "queued-message delivery"),
    ("fun replaceMessage(", "in-place replacement of a delivered queued message"),
    ("discordBackoffMs", "retry backoff"),
    ("presenceScheduler.scheduleWithFixedDelay", "the retry tick"),
):
    if needle not in repository:
        errors.append(f"SocialRepository.kt: missing {what} (`{needle}`)")

# The link is permanent: the repository must never silently drop tokens on an HTTP failure.
if "DiscordOAuthClient.clearToken()" in repository:
    errors.append("SocialRepository.kt: must not clear Discord tokens (the link is permanent)")
if "reconnect your account" in repository.lower() or "tap Reconnect" in repository:
    errors.append("SocialRepository.kt: status copy must not ask the user to reconnect")
if "discordSessionInvalid = true" in repository:
    errors.append("SocialRepository.kt: a failed request must not mark the session invalid")

# Queued work survives restarts and old permanent failures are migrated on load.
if "pending = true" not in repository:
    errors.append("SocialRepository.kt: undeliverable Discord sends must be queued as pending")
if "message.failed && message.fromSelf" not in repository:
    errors.append("SocialRepository.kt: legacy \"Not sent\" rows must be migrated to the retry queue")

# A conversation shows its own state only - the global Discord status used to be printed there.
conv_status = conversation_ui.split("private fun renderStatus(", 1)[-1].split("private fun ", 1)[0]
if "snapshot.statusMessage" in conv_status:
    errors.append("ConversationFragment: the global status must not be rendered inside a chat")
if "social_messages_queued" not in conv_status:
    errors.append("ConversationFragment: queued messages need the sending hint")

# Pending messages render as "Sending…", not as a failure.
if "social_message_pending" not in message_adapter_text:
    errors.append("SocialMessageAdapter: pending messages must render as sending")
if "Discord presence needs the Social SDK connection" in repository:
    errors.append("SocialRepository.kt: internal presence wording must not reach the UI")
if "Social SDK" in conversation_ui or "Social SDK" in message_adapter_text:
    errors.append("friends UI: internal Social SDK wording must not be user-visible")

if "val discordSessionInvalid: Boolean = false" not in models:
    errors.append("SocialModels.kt: SocialSnapshot must carry discordSessionInvalid")
if "discordSessionInvalid = discordSessionInvalid" not in repository:
    errors.append("SocialRepository.publish() must publish discordSessionInvalid")

if "social_reconnect_discord" not in friends_ui:
    errors.append("FriendsFragment.kt: keep the manual Reconnect action in the account dialog")
if "DiscordOAuthClient.reconnect(" not in friends_ui:
    errors.append("FriendsFragment.kt: Reconnect must call DiscordOAuthClient.reconnect()")

# --- Scope hygiene ------------------------------------------------------------
# The OAuth2 URL Generator in the Developer Portal offers bot-only scopes (bot,
# applications.*, webhook.incoming). Pasting them into Settings makes Discord reject the
# whole authorize request, so they must be dropped and reported, never sent.
if "fun sanitizeScopes(raw: String): Pair<List<String>, List<String>>" not in auth:
    errors.append("DiscordAuth.kt: missing sanitizeScopes() (bot-only scope filter)")
if "val RECOMMENDED_SCOPES: String = DiscordConfig.DEFAULT_SCOPES" not in auth:
    errors.append("DiscordAuth.kt: RECOMMENDED_SCOPES must name the scopes the feature uses")
if "fun unsupportedScopes(): List<String>" not in auth:
    errors.append("DiscordAuth.kt: unsupportedScopes() must report dropped bot-only scopes")
for scope in ("\"bot\"", "\"applications.commands\"", "\"webhook.incoming\""):
    if scope not in auth.split("BOT_ONLYISH_SCOPES", 1)[-1].split(")", 1)[0]:
        errors.append(f"DiscordAuth.kt: bot-only scope {scope} must be filtered out")
if "sanitizeScopes(" not in auth.split("fun scopes(): String", 1)[-1].split("\n    }", 1)[0]:
    errors.append("DiscordAuth.scopes() must return the sanitized list")
if 'error == "invalid_scope"' not in auth:
    errors.append("DiscordAuth.kt: invalid_scope redirects need their own explanation")

if "unsupportedScopes()" not in friends_ui:
    errors.append("FriendsFragment.kt: the scope dialog must report ignored bot-only scopes")
if "social_scope_bot_only_ignored" not in friends_ui:
    errors.append("FriendsFragment.kt: linking must warn about bot-only scopes from Settings")

if "Discord session expired" in repository + auth:
    errors.append("the misleading 'Discord session expired' copy must not come back")

# --- Known Kotlin traps ------------------------------------------------------
for path in sorted(social_dir.glob("*.kt")) + [adapter_file, message_adapter,
                                               fragment, conversation_fragment]:
    text = path.read_text()
    if re.search(r"\bHttpUrl\.parse\(", text):
        errors.append(f"{path.name}: HttpUrl.parse was removed from the bundled OkHttp")
    if re.search(r"\.replaceAll\s*\{", text):
        errors.append(f"{path.name}: MutableList has no replaceAll lambda overload")
    if text.count("{") != text.count("}"):
        errors.append(f"{path.name}: unbalanced braces")
    if "package " not in text.splitlines()[0]:
        errors.append(f"{path.name}: missing package declaration")

if errors:
    print("Social checks failed:", flush=True)
    for error in errors:
        print(f"  - {error}")
    raise SystemExit(1)

print("Social checks passed: 7 tabs, permanent Discord link, queued sends, no leaked internals")
