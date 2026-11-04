#!/usr/bin/env python3
"""Guard dual channel network acceleration.

The feature splits one download across Wi-Fi and mobile data, so the checks here are about
safety first: it must be opt-out-able, it must never run without a second validated link, it
must refuse anything it cannot verify (no byte ranges, unknown size, small file), and every
failure has to hand the file back to the ordinary single-stream downloader untouched.

It also pins the two hooks that make it real: the game-file downloader calls it, and the DNS
resolver races two providers instead of waiting on one.
"""
import re
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
java = main / "java"
module = java / "com/endiq/turtlelauncher/feature/network/DualChannelNetwork.kt"
settings = java / "com/endiq/turtlelauncher/setting/AllSettings.kt"
download_utils = java / "net/endiq/launcher/utils/DownloadUtils.java"
custom_dns = java / "com/endiq/turtlelauncher/utils/path/CustomDns.kt"
layout = main / "res/layout/settings_fragment_launcher.xml"
strings = main / "res/values/strings.xml"
launcher_settings = java / "com/endiq/turtlelauncher/ui/fragment/settings/LauncherSettingsFragment.kt"

errors = []


def read(path: Path) -> str:
    if not path.is_file():
        errors.append(f"{path.name}: missing")
        return ""
    return path.read_text()


if not module.is_file():
    errors.append("feature/network/DualChannelNetwork.kt: missing")

source = read(module)

# ── The split has to be provable before it is attempted ────────────────────────────────
for needle, what in (
    ("const val MIN_SPLIT_BYTES: Long = 4L * 1024 * 1024", "a minimum size worth splitting"),
    ('header("Range", "bytes=$from-$to")', "an explicit byte range per channel"),
    ("response.code == 206", "a 206 check (a full-body 200 must not be joined)"),
    ("Accept-Ranges", "range support probing"),
    ("if (!layout.ranges || layout.size < MIN_SPLIT_BYTES)", "refusing unsplittable or small files"),
    ("skipHosts", "remembering hosts that cannot split, so a probe is not paid per file"),
    ("if (target.length() != total)", "verifying the joined length"),
    ("return false", "a fallback signal instead of an exception"),
):
    if needle not in source:
        errors.append(f"DualChannelNetwork.kt: missing {what} (`{needle}`)")

# Two *different* transports, both validated, or nothing happens at all.
if "NET_CAPABILITY_VALIDATED" not in source:
    errors.append("DualChannelNetwork.kt: only validated networks may be used")
if "channels.size < 2" not in source:
    errors.append("DualChannelNetwork.kt: a single link must not trigger the split path")
if "network.socketFactory" not in source:
    errors.append("DualChannelNetwork.kt: sockets must be bound to their channel's network")
if "network.getAllByName" not in source:
    errors.append("DualChannelNetwork.kt: each channel must resolve DNS on its own network")

# Failure hygiene: partials deleted, target removed, caller falls back.
for needle, what in (
    ('File(target.parentFile, target.name + ".dc0")', "named part files"),
    ("parts.forEach { runCatching { it.delete() } }", "part cleanup on every exit path"),
    ("runCatching { target.delete() }", "removing a half-written target"),
):
    if needle not in source:
        errors.append(f"DualChannelNetwork.kt: missing {what} (`{needle}`)")

# ── Switching and placement ────────────────────────────────────────────────────────────
settings_src = read(settings)
if 'BooleanSettingUnit("dualChannelNetwork", true)' not in settings_src:
    errors.append("AllSettings: dualChannelNetwork must exist (a switch, on by default)")
if "fun isEnabled(): Boolean" not in source:
    errors.append("DualChannelNetwork: the setting must gate the feature")

for needle, what in (
    ("setting_dual_channel_title", "the settings row title"),
    ("setting_dual_channel_desc", "the settings row description"),
):
    if needle not in read(strings):
        errors.append(f"strings.xml: missing {what} (`{needle}`)")
for needle, what in (
    ('@+id/dualChannelNetwork', "the switch"),
    ('@+id/dualChannelNetwork_value', "the live link summary"),
):
    if needle not in read(layout):
        errors.append(f"settings_fragment_launcher.xml: missing {what}")

launcher_src = read(launcher_settings)
if "AllSettings.dualChannelNetwork" not in launcher_src:
    errors.append("LauncherSettingsFragment: the switch must be wired to the setting")
if "DualChannelNetwork.describe(" not in launcher_src:
    errors.append("LauncherSettingsFragment: the row must say which links are live")

# ── It has to actually accelerate something ────────────────────────────────────────────
download_src = read(download_utils)
if "DualChannelNetwork\n                .accelerate(" not in download_src and "DualChannelNetwork.accelerate(" not in download_src:
    errors.append("DownloadUtils: game-file downloads must go through dual channel acceleration")
if "monitor::updateProgress" not in download_src:
    errors.append("DownloadUtils: progress must keep flowing while the split runs")
monitored = download_src.split("downloadFileMonitored", 1)[-1]
if "DualChannelNetwork" not in monitored:
    errors.append("DownloadUtils.downloadFileMonitored: the hook must live in the monitored download")
elif monitored.index("DualChannelNetwork") > monitored.index("newCall(request)"):
    errors.append("DownloadUtils: the dual channel attempt must come before the single-stream path")

dns_src = read(custom_dns)
for needle, what in (
    ("AllSettings.dualChannelNetwork.getValue()", "honouring the setting"),
    ("first.countDown()", "racing two resolvers"),
    ("winner.get()", "taking the first usable answer"),
):
    if needle not in dns_src:
        errors.append(f"CustomDns.kt: missing {what} (`{needle}`)")

# No third-party code, no hardcoded hosts, no new permission requirement.
manifest = read(main / "AndroidManifest.xml")
if "android.permission.ACCESS_NETWORK_STATE" not in manifest:
    errors.append("AndroidManifest.xml: ACCESS_NETWORK_STATE is needed to enumerate channels")
if "android.permission.CHANGE_NETWORK_STATE" in source:
    errors.append("DualChannelNetwork.kt: never changes network state, only uses it")
if re.search(r'"[^"]*\.(com|net|org)/"', source):
    errors.append("DualChannelNetwork.kt: no hardcoded endpoints")

if errors:
    print("Dual channel checks failed:")
    for error in errors:
        print("  - " + error)
    raise SystemExit(1)

print("Dual channel checks passed: validated two-link split, verified ranges, safe fallback, "
      "wired into downloads and DNS")
