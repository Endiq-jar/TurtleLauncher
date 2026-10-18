#!/usr/bin/env python3
"""Validate the imported Tabler/Flowbite/Simple-Icons set and its animation wiring.

Static checks only (no Android SDK needed):
  * every converted icon parses, sits on the 24dp grid, and names its source;
  * NoBlocklist leftovers are really gone;
  * AnimatedIcon exists with distinct per-icon styles and the
    hover/press/click contract, sharing InteractionWiring with AnimatedLogo;
  * layouts use AnimatedIconView/AnimatedIconButton for ic_* views.
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
drawable = main / "res/drawable"

# --- NoBlocklist fully removed ---------------------------------------------
assert not (main / "assets/components/components/NoBlocklist.jar").exists()
assert not (repo / "tools/build_noblocklist.py").exists()
for path in (main / "java").rglob("*.kt"):
    assert "NO_BLOCKLIST" not in path.read_text() and "noBlocklist" not in path.read_text(), path
assert "no_blocklist" not in (main / "res/values/strings.xml").read_text()
assert "no_blocklist" not in (main / "res/layout/settings_fragment_game.xml").read_text()

# --- Icon files -------------------------------------------------------------
KEPT_CUSTOM = {"ic_launcher_background", "ic_launcher_monochrome", "ic_neoforge", "ic_optifine"}
converted = 0
for path in sorted(drawable.glob("ic_*.xml")):
    text = path.read_text()
    root = ET.fromstring(text)
    if path.stem in KEPT_CUSTOM:
        continue
    assert root.tag == "vector", path
    assert "Source:" in text and "docs/ICONS.md" in text, f"{path.name}: missing provenance"
    ns = "{http://schemas.android.com/apk/res/android}"
    assert root.get(f"{ns}viewportWidth") == "24", f"{path.name}: viewport must be the 24 grid"
    assert root.get(f"{ns}viewportHeight") == "24", path.name
    assert root.get(f"{ns}width") == "24dp" and root.get(f"{ns}height") == "24dp", path.name
    assert root.findall("path"), f"{path.name}: no paths"
    assert "M0 0h24v24" not in text, f"{path.name}: leaked SVG bounding box"
    converted += 1
assert converted >= 85, f"only {converted} converted icons"

for brand in ("ic_discord", "ic_github", "ic_curseforge", "ic_openjdk"):
    assert (drawable / f"{brand}.xml").is_file(), f"missing brand logo {brand}"

import_script = (repo / "tools/import_icons.py").read_text()
assert "MAPPING" in import_script and "tabler" in import_script and "flowbite" in import_script
assert (repo / "docs/ICONS.md").is_file()

# --- Animation wiring --------------------------------------------------------
anim = main / "java/com/endiq/turtlelauncher/utils/anim"
icon = (anim / "AnimatedIcon.kt").read_text()
logo = (anim / "AnimatedLogo.kt").read_text()
wiring = (anim / "InteractionWiring.kt").read_text()

for style in ("SPIN", "HEARTBEAT", "DROP", "BLINK", "SHAKE", "SWING", "GEAR", "FLIP_Y", "TADA"):
    assert style in icon, f"missing animation style {style}"
assert "fun styleFor" in icon, "icons need distinct per-icon styles"
for contract in ("ACTION_HOVER_ENTER", "ACTION_DOWN", "ACTION_UP", "OnGlobalFocusChangeListener", "playOnceIfIdle"):
    assert contract in icon, f"AnimatedIcon missing {contract}"
assert "InteractionWiring" in logo and "InteractionWiring" in icon, "logos and icons must share one wiring"
assert "private class TargetWiring" not in logo, "duplicated wiring left in AnimatedLogo"
assert "setOnHoverListener" in wiring and "resolveTarget" in wiring

ids = (main / "res/values/launcher_art_ids.xml").read_text()
assert "animated_icon_request" in ids

views = main / "java/com/endiq/turtlelauncher/ui/view"
assert "AnimatedIcon.bindAny" in (views / "AnimatedIconView.kt").read_text()
assert "AnimatedIcon.bindAny" in (views / "AnimatedIconButton.kt").read_text()

plain = 0
animated = 0
for layout in (main / "res/layout").glob("*.xml"):
    text = layout.read_text()
    animated += text.count("AnimatedIconView") + text.count("AnimatedIconButton")
    for m in re.finditer(r"<(ImageView|ImageButton)(?=[\s/>])[^>]*?(?:android:src|app:srcCompat)=\"@drawable/ic_", text, re.S):
        plain += 1
assert animated >= 250, f"only {animated} animated icon views in layouts"
assert plain == 0, f"{plain} icon views were not upgraded (run tools/apply_animated_icons.py)"

# --- File-type family (pastel green) -----------------------------------------
filetypes = sorted(drawable.glob("ic_filetype_*.xml"))
assert len(filetypes) >= 16, f"only {len(filetypes)} file-type icons"
for path in filetypes:
    assert 'android:tint="@color/file_icon_tint"' in path.read_text(),         f"{path.name}: file-type icons must use the pastel-green tint"
colors = (main / "res/values/colors.xml").read_text()
assert 'name="file_icon_tint">@color/palette_primary' in colors, "pastel-green tint missing"

resolver = (main / "java/com/endiq/turtlelauncher/ui/subassembly/filelist/FileTypeIcons.kt").read_text()
for needle in ("ic_filetype_jar", "ic_filetype_zip", "ic_filetype_json", "ic_filetype_data"):
    assert needle in resolver, f"resolver missing {needle}"
creator = (main / "java/com/endiq/turtlelauncher/ui/subassembly/filelist/FileRecyclerViewCreator.kt").read_text()
assert "FileTypeIcons.iconResFor" in creator and "fileIconRes" in creator
adapter = (main / "java/com/endiq/turtlelauncher/ui/subassembly/filelist/FileRecyclerAdapter.java").read_text()
assert "fileIconRes" in adapter and "setImageResource(fileItemBean.fileIconRes)" in adapter
assert "filetype_zip" in icon and "Style.POP" in icon, "per-filetype animation styles missing"

print(f"{converted} converted icons on the 24dp grid, {animated} animated icon views, "
      "hover/press/click wiring and NoBlocklist removal passed")
