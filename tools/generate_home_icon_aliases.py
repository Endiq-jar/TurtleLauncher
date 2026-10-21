#!/usr/bin/env python3
"""Generates one home-screen icon per bundled logo.

Android can only change the launcher icon by enabling one of several pre-declared
`activity-alias` entries, and each needs its own icon resource. This script builds both
from assets/logo_pack.zip so every logo in the picker can become the home-screen icon:

  * res/mipmap-nodpi/home_icon_<key>.png   - the icon, copied from the pack
  * AndroidManifest.xml                     - one disabled activity-alias per icon
                                             (the default alias stays enabled)

<key> is the pack file name without ".png", with "-" replaced by "_". The Kotlin side
(feature/logo/HomeScreenIcon.kt) derives the same names from the file name, so no
lookup table is needed there.

Run from the repository root after the logo pack changes:
    python3 tools/generate_home_icon_aliases.py
"""
import glob
import os
import re
import zipfile

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
MAIN = os.path.join(ROOT, "TurtleLauncher", "src", "main")
PACK = os.path.join(MAIN, "assets", "logo_pack.zip")
RES = os.path.join(MAIN, "res", "mipmap-nodpi")
MANIFEST = os.path.join(MAIN, "AndroidManifest.xml")
PREFIX_RES = "home_icon_"
PREFIX_ALIAS = "HomeIcon_"
BLOCK_START = "        <!-- Home-screen icon choices."
BLOCK_END = "        <activity\n"  # first activity element after the alias block

DEFAULT_ALIAS = """        <!-- Home-screen icon choices. Exactly one launcher entry is enabled at a time.
             HomeScreenIcon enables the one matching the picked logo; the rest are
             generated below by tools/generate_home_icon_aliases.py. -->
        <activity-alias
            android:name="com.endiq.turtlelauncher.HomeIconDefault"
            android:enabled="true"
            android:exported="true"
            android:icon="@mipmap/ic_launcher"
            android:roundIcon="@mipmap/ic_launcher_round"
            android:label="Turtle Launcher"
            android:targetActivity="com.endiq.turtlelauncher.ui.activity.SplashActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
"""


def alias_xml(key: str) -> str:
    return f"""        <activity-alias
            android:name="com.endiq.turtlelauncher.{PREFIX_ALIAS}{key}"
            android:enabled="false"
            android:exported="true"
            android:icon="@mipmap/{PREFIX_RES}{key}"
            android:label="Turtle Launcher"
            android:targetActivity="com.endiq.turtlelauncher.ui.activity.SplashActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity-alias>
"""


def main() -> None:
    with zipfile.ZipFile(PACK) as z:
        names = sorted(n for n in z.namelist() if n.lower().endswith(".png"))
        for n in names:
            if not re.fullmatch(r"[a-z0-9_-]+\.png", n):
                raise SystemExit(f"Logo name is not a valid resource name: {n}")

        keys = [n[:-4].replace("-", "_") for n in names]
        if len(set(keys)) != len(keys):
            raise SystemExit("Two logo names map to the same resource name")

        os.makedirs(RES, exist_ok=True)
        # Drop icons from earlier runs so renamed or removed logos don't linger.
        for old in glob.glob(os.path.join(RES, PREFIX_RES + "*.png")):
            os.remove(old)
        for name, key in zip(names, keys):
            with open(os.path.join(RES, f"{PREFIX_RES}{key}.png"), "wb") as out:
                out.write(z.read(name))

    with open(MANIFEST, encoding="utf-8") as f:
        manifest = f.read()
    start = manifest.index(BLOCK_START)
    end = manifest.index(BLOCK_END, start)  # the block ends where the next activity begins
    body = DEFAULT_ALIAS + "".join(alias_xml(k) for k in keys)
    manifest = manifest[:start] + body + manifest[end:]
    with open(MANIFEST, "w", encoding="utf-8") as f:
        f.write(manifest)

    print(f"Wrote {len(keys)} home-screen icons and aliases.")


if __name__ == "__main__":
    main()
