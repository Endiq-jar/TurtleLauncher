#!/usr/bin/env python3
"""Import launcher icons from Tabler Icons / Flowbite Icons / Simple Icons.

Converts the upstream 24x24 SVGs listed in MAPPING into Android
VectorDrawables in TurtleLauncher/src/main/res/drawable, normalising the
launcher's previously mixed icon set (Material fills, odd viewports like
16.92, strokeWidth=0 artefacts) onto one design system:

  * Tabler Icons (MIT)   - primary set. 5,184 outline + 1,054 filled icons,
    24px grid, 2px stroke, round caps/joins. Path-only SVGs, so conversion
    is lossless and the glyphs stay crisp at every density.
  * Flowbite Icons (MIT) - used where its glyph is a single simpler path
    than Tabler's (fewer pathData bytes to parse/render).
  * Simple Icons (CC0)   - brand logos only (Discord/GitHub/CurseForge/...),
    official single-path brand shapes, filled.

Why not bundle all ~7,000 icons from these libraries? Every unused drawable
resource still costs aapt compile time and APK size (and release shrinking
would strip them again). This imports every icon the launcher actually
shows, which is the set users see. To add another upstream icon later, add
one MAPPING line and re-run.

Usage (sparse clones, see docs/ICONS.md):
  git clone --depth 1 --filter=blob:none --sparse https://github.com/tabler/tabler-icons
  git -C tabler-icons sparse-checkout set icons/outline icons/filled
  git clone --depth 1 --filter=blob:none --sparse https://github.com/themesberg/flowbite-icons
  git -C flowbite-icons sparse-checkout set src
  python3 tools/import_icons.py --tabler tabler-icons --flowbite flowbite-icons --simple simple-icons-svgs

Existing per-icon `android:tint` is preserved when replacing a file.
"""

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DRAWABLE = REPO / "TurtleLauncher/src/main/res/drawable"

# target resource name -> (source set, upstream icon name)
# Sets: tabler (outline), tabler-filled, flowbite (outline, found by name
# anywhere under src/outline), flowbite-solid, simple (brand logo, filled).
MAPPING = {
    "ic_about": ("tabler", "info-circle"),
    "ic_add": ("tabler", "plus"),
    "ic_add_new": ("tabler", "square-plus"),
    "ic_ai_assistant": ("tabler", "sparkles"),
    "ic_anvil": ("tabler", "hammer"),
    "ic_bolt": ("tabler", "bolt"),
    "ic_cape": ("tabler", "shirt"),
    "ic_chat": ("tabler", "message-circle"),
    "ic_check": ("tabler", "check"),
    "ic_chevron_down": ("tabler", "chevron-down"),
    "ic_chevron_right": ("tabler", "chevron-right"),
    "ic_close": ("tabler", "x"),
    "ic_copy": ("tabler", "copy"),
    "ic_cursor": ("tabler", "pointer"),
    "ic_disabled": ("tabler", "ban"),
    "ic_discord": ("simple", "discord"),
    "ic_document": ("tabler", "file-text"),
    "ic_download": ("flowbite", "arrow-down-to-bracket"),
    "ic_download_alpha": ("tabler", "circle-letter-a"),
    "ic_download_beta": ("tabler", "circle-letter-b"),
    "ic_download_release": ("tabler", "circle-letter-r"),
    "ic_edit": ("tabler", "pencil"),
    "ic_extract": ("tabler", "package-export"),
    "ic_eye": ("flowbite", "eye"),
    "ic_favorite": ("tabler-filled", "heart"),
    "ic_favorite_border": ("tabler", "heart"),
    "ic_file": ("tabler", "file"),
    "ic_folder": ("flowbite", "folder"),
    "ic_friends_network": ("tabler", "users-group"),
    "ic_gamepad": ("tabler", "device-gamepad-2"),
    "ic_git_branch": ("tabler", "git-branch"),
    "ic_globe": ("tabler", "world"),
    "ic_hair": ("tabler", "scissors"),
    "ic_help": ("tabler", "help"),
    "ic_java": ("tabler", "coffee"),
    "ic_keyboard_tab": ("tabler", "keyboard"),
    "ic_launch_link": ("tabler", "external-link"),
    "ic_logout": ("tabler", "logout"),
    "ic_menu_accounts": ("tabler", "user-circle"),
    "ic_menu_close_clear_cancel": ("tabler", "x"),
    "ic_menu_custom_controls": ("tabler", "device-gamepad"),
    "ic_menu_delete_forever": ("tabler", "trash"),
    "ic_menu_emotes": ("tabler", "mood-smile"),
    "ic_menu_home": ("tabler", "home"),
    "ic_menu_mouse_keyboard": ("tabler", "mouse"),
    "ic_menu_settings": ("tabler", "settings"),
    "ic_mod_search": ("tabler", "list-search"),
    "ic_modpack": ("tabler", "package"),
    "ic_more": ("tabler", "dots-vertical"),
    "ic_move": ("tabler", "arrows-move"),
    "ic_music_note": ("tabler", "music"),
    "ic_paste": ("tabler", "clipboard"),
    "ic_play_circle": ("tabler", "circle-play"),
    "ic_profile_crash_report": ("tabler", "bug"),
    "ic_profile_folder": ("tabler", "folder"),
    "ic_profile_logs": ("tabler", "file-description"),
    "ic_profile_mods": ("tabler", "puzzle"),
    "ic_profile_resource_pack": ("tabler", "palette"),
    "ic_profile_saves": ("tabler", "device-floppy"),
    "ic_profile_shader_pack": ("tabler", "sun"),
    "ic_puzzle_piece": ("tabler", "puzzle"),
    "ic_refresh": ("flowbite", "refresh"),
    "ic_reset": ("tabler", "restore"),
    "ic_screenshot": ("tabler", "camera"),
    "ic_search": ("flowbite", "search"),
    "ic_setting": ("tabler", "settings"),
    "ic_setting_accessibility": ("tabler", "accessible"),
    "ic_setting_experimental": ("tabler", "flask"),
    "ic_setting_game": ("tabler", "device-gamepad-2"),
    "ic_setting_hud": ("tabler", "layout-dashboard"),
    "ic_setting_java": ("tabler", "coffee"),
    "ic_setting_launcher": ("tabler", "rocket"),
    "ic_setting_optimization": ("tabler", "gauge"),
    "ic_setting_phone": ("tabler", "device-mobile"),
    "ic_setting_recording": ("tabler", "video"),
    "ic_setting_video": ("tabler", "device-desktop"),
    "ic_share": ("tabler", "share"),
    "ic_skin_changer": ("tabler", "user-edit"),
    "ic_speaker": ("tabler", "volume"),
    "ic_spinner_arrow_right": ("tabler", "arrow-right"),
    "ic_storage_alert": ("tabler", "database-exclamation"),
    "ic_tasks": ("tabler", "list-check"),
    "ic_view_handle": ("tabler", "grip-vertical"),
    "ic_warning": ("tabler", "alert-triangle"),
    "ic_zip": ("tabler", "file-zip"),
    # File-type family for the file browser (pastel-green tint below).
    # Metaphors follow the popular filled-line file sets (file, code, zip,
    # media, text, image, pdf, music...); glyphs are the MIT Tabler/Flowbite
    # equivalents already used by the rest of the launcher.
    "ic_filetype_folder": ("tabler", "folder"),
    "ic_filetype_file": ("tabler", "file"),
    "ic_filetype_jar": ("tabler", "archive"),
    "ic_filetype_zip": ("tabler", "file-zip"),
    "ic_filetype_json": ("tabler", "file-code-2"),
    "ic_filetype_code": ("tabler", "file-code"),
    "ic_filetype_text": ("tabler", "file-text"),
    "ic_filetype_image": ("flowbite", "file-image"),
    "ic_filetype_audio": ("tabler", "file-music"),
    "ic_filetype_video": ("flowbite", "file-video"),
    "ic_filetype_data": ("tabler", "file-database"),
    "ic_filetype_config": ("tabler", "file-settings"),
    "ic_filetype_apk": ("tabler", "brand-android"),
    "ic_filetype_pdf": ("tabler", "file-type-pdf"),
    "ic_filetype_font": ("tabler", "file-typography"),
    "ic_filetype_3d": ("tabler", "file-3d"),
    # New brand logos (Simple Icons, CC0).
    "ic_github": ("simple", "github"),
    "ic_curseforge": ("simple", "curseforge"),
    "ic_openjdk": ("simple", "openjdk"),
}

SOURCE_LABEL = {
    "tabler": "Tabler Icons (MIT) icons/outline/{name}.svg",
    "tabler-filled": "Tabler Icons (MIT) icons/filled/{name}.svg",
    "flowbite": "Flowbite Icons (MIT) src/outline/**/{name}.svg",
    "flowbite-solid": "Flowbite Icons (MIT) src/solid/**/{name}.svg",
    "simple": "Simple Icons (CC0) icons/{name}.svg",
}

PATH_RE = re.compile(r"<path\b([^>]*?)/?>", re.S)
ATTR_RE = re.compile(r"([\w-]+)=\"([^\"]*)\"")


def find_source(kind: str, name: str, roots: dict) -> Path:
    if kind == "tabler":
        return roots["tabler"] / "icons/outline" / f"{name}.svg"
    if kind == "tabler-filled":
        return roots["tabler"] / "icons/filled" / f"{name}.svg"
    if kind == "simple":
        flat = roots["simple"] / f"{name}.svg"
        nested = roots["simple"] / "icons" / f"{name}.svg"
        return flat if flat.is_file() else nested
    subset = "outline" if kind == "flowbite" else "solid"
    hits = sorted((roots["flowbite"] / "src" / subset).rglob(f"{name}.svg"))
    return hits[0] if hits else roots["flowbite"] / "src" / subset / f"{name}.svg"


def convert(svg_text: str, kind: str) -> str:
    """Return the <path> elements of a VectorDrawable for one 24x24 SVG."""
    filled = kind in ("tabler-filled", "flowbite-solid", "simple")
    out = []
    for attrs_raw in PATH_RE.findall(svg_text):
        attrs = dict(ATTR_RE.findall(attrs_raw))
        d = " ".join(attrs.get("d", "").split())
        if not d:
            continue
        even_odd = attrs.get("fill-rule") == "evenodd" or attrs.get("clip-rule") == "evenodd"
        if filled:
            fill_type = '\n        android:fillType="evenOdd"' if even_odd else ""
            out.append(f'    <path\n        android:fillColor="#FF000000"{fill_type}\n'
                       f'        android:pathData="{d}" />')
        else:
            out.append('    <path\n        android:fillColor="@android:color/transparent"\n'
                       '        android:strokeColor="#FF000000"\n'
                       '        android:strokeWidth="1.8"\n'
                       '        android:strokeLineCap="round"\n'
                       '        android:strokeLineJoin="round"\n'
                       f'        android:pathData="{d}" />')
    assert out, "no <path> found in SVG"
    return "\n".join(out)


# Icons whose colour is semantic rather than the default theme tint.
TINT_OVERRIDES = {
    "ic_favorite_border": "@color/favorite_color",
}

def tint_for(target: str, dest: Path) -> str:
    if target.startswith("ic_filetype_"):
        return "@color/file_icon_tint"
    return TINT_OVERRIDES.get(target) or existing_tint(dest)


def existing_tint(path: Path) -> str:
    if path.is_file():
        m = re.search(r'android:tint="([^"]+)"', path.read_text())
        if m:
            return m.group(1)
    return "@color/black_or_white"


def render(target: str, kind: str, name: str, paths_xml: str, tint: str) -> str:
    label = SOURCE_LABEL[kind].format(name=name)
    return f"""<!-- Source: {label} - converted by tools/import_icons.py; see docs/ICONS.md -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:tint="{tint}"
    android:viewportWidth="24"
    android:viewportHeight="24">
{paths_xml}
</vector>
"""


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tabler", type=Path, default=Path("/tmp/tabler"))
    parser.add_argument("--flowbite", type=Path, default=Path("/tmp/flowbite"))
    parser.add_argument("--simple", type=Path, default=Path("/tmp/simple"))
    args = parser.parse_args(argv)
    roots = {"tabler": args.tabler, "flowbite": args.flowbite, "simple": args.simple}

    missing = []
    for target, (kind, name) in sorted(MAPPING.items()):
        src = find_source(kind, name, roots)
        if not src.is_file():
            missing.append(f"{target} <- {kind}:{name} ({src})")
    if missing:
        print("Missing upstream icons:\n  " + "\n  ".join(missing), file=sys.stderr)
        return 1

    total_bytes = 0
    for target, (kind, name) in sorted(MAPPING.items()):
        svg = find_source(kind, name, roots).read_text()
        dest = DRAWABLE / f"{target}.xml"
        xml = render(target, kind, name, convert(svg, kind), tint_for(target, dest))
        dest.write_text(xml)
        total_bytes += len(xml)
    print(f"Wrote {len(MAPPING)} icons ({total_bytes} bytes total, "
          f"avg {total_bytes // len(MAPPING)} bytes/icon)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
