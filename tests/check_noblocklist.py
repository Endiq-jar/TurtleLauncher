#!/usr/bin/env python3
"""Validate the bundled NoBlocklist agent jar and its launcher wiring.

Static checks only (no JDK/Android SDK needed): the jar must be a loadable
Java-8-era javaagent with relocated ASM, and LaunchArgs/settings must agree
with LibPath so the unpacked component is actually passed to the game.
Rebuild the jar with tools/build_noblocklist.py (or drop in the upstream
release jar - see assets/notices/noblocklist/NoBlocklist-Notices.txt).
"""
import struct
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
jar_path = main / "assets/components/components/NoBlocklist.jar"

assert jar_path.is_file(), "NoBlocklist.jar missing from components assets"
assert (repo / "tools/build_noblocklist.py").is_file(), "reproducible build script missing"

with zipfile.ZipFile(jar_path) as jar:
    names = set(jar.namelist())
    manifest = jar.read("META-INF/MANIFEST.MF").decode()
    assert "Premain-Class: git.artdeell.noblocklist.PreMain" in manifest
    assert "Can-Retransform-Classes: true" in manifest

    for cls in ("git/artdeell/noblocklist/PreMain.class",
                "git/artdeell/noblocklist/BlockerTransformer.class",
                "git/artdeell/noblocklist/BlockerTransformer$URLVisitor.class",
                "git/artdeell/noblocklist/BlockerTransformer$URLMethodVisitor.class",
                "git/artdeell/shasm/ClassReader.class",
                "git/artdeell/shasm/ClassWriter.class",
                "git/artdeell/shasm/Opcodes.class"):
        assert cls in names, f"missing {cls}"

    for name in names:
        if not name.endswith(".class"):
            continue
        data = jar.read(name)
        assert data[:4] == b"\xca\xfe\xba\xbe", name
        major = struct.unpack_from(">H", data, 6)[0]
        assert major <= 52, f"{name} needs Java > 8 (major {major}) - MC < 1.17 runs on JRE 8"
        assert b"org/objectweb/asm" not in data, f"{name} has unrelocated ASM refs"
        assert b"moe/yushi" not in data, f"{name} leaks donor-package refs"

    agent = b"".join(jar.read(n) for n in names if "noblocklist/" in n and n.endswith(".class"))
    assert b"(II)I" not in agent, "Random.nextInt(int,int) is Java 17+; agent must stay Java-8 clean"
    assert b"blockedservers" in agent and b"sessionserver.mojang.com" in agent
    opcodes = jar.read("git/artdeell/shasm/Opcodes.class")
    assert b"V25" in opcodes or b"V26" in opcodes, "ASM must read Java 25 game-runtime classes"

# Existing installs only re-unpack components when the version stamp changes.
version = (main / "assets/components/components/version").read_text().strip()
assert version > "2025/12/18", f"components version not bumped for NoBlocklist: {version}"

lib_path = (main / "java/com/endiq/turtlelauncher/utils/path/LibPath.kt").read_text()
assert "NO_BLOCKLIST" in lib_path and '"NoBlocklist.jar"' in lib_path

launch_args = (main / "java/com/endiq/turtlelauncher/launch/LaunchArgs.kt").read_text()
assert "LibPath.NO_BLOCKLIST" in launch_args and "-javaagent:" in launch_args
assert "noBlocklistEnabled" in launch_args, "agent must honour the user toggle"

settings = (main / "java/com/endiq/turtlelauncher/setting/AllSettings.kt").read_text()
assert 'BooleanSettingUnit("noBlocklistEnabled"' in settings

strings = ET.parse(main / "res/values/strings.xml").getroot()
names = {s.get("name") for s in strings.iter("string")}
assert {"setting_no_blocklist_title", "setting_no_blocklist_desc"} <= names

layout = (main / "res/layout/settings_fragment_game.xml").read_text()
assert 'android:id="@+id/no_blocklist"' in layout and "@string/setting_no_blocklist_title" in layout

fragment = (main / "java/com/endiq/turtlelauncher/ui/fragment/settings/GameSettingsFragment.kt").read_text()
assert "AllSettings.noBlocklistEnabled" in fragment and "binding.noBlocklist" in fragment

print(f"NoBlocklist jar ({jar_path.stat().st_size} bytes), Java-8 bytecode, relocated ASM and launcher wiring passed")
