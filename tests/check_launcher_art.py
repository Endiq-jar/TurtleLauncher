#!/usr/bin/env python3
"""Validate finite, bounded WebP artwork without Pillow or an Android SDK."""
import struct
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1] / "TurtleLauncher/src/main"
logos = ["ic_minecraft", "ic_turtle_full_animated", "ic_command_block", "app_name_title",
         "ic_old_cobblestone", "ic_old_grass_block"]


def chunks(path):
    data = path.read_bytes()
    assert data[:4] == b"RIFF" and data[8:12] == b"WEBP", path
    assert struct.unpack_from("<I", data, 4)[0] + 8 == len(data), path
    offset = 12
    while offset < len(data):
        kind = data[offset:offset + 4]
        size = struct.unpack_from("<I", data, offset + 4)[0]
        yield kind, data[offset + 8:offset + 8 + size]
        offset += 8 + size + size % 2
    assert offset == len(data), path


binding = (root / "java/com/endiq/turtlelauncher/utils/anim/AnimatedLogo.kt").read_text()
for logo in logos:
    path = root / "res/raw" / (logo + ".webp")
    parts = list(chunks(path))
    animation = next(value for kind, value in parts if kind == b"ANIM")
    assert int.from_bytes(animation[4:6], "little") == 1, "File must play once, not forever"
    frames = [value for kind, value in parts if kind == b"ANMF"]
    assert 2 <= len(frames) <= 24
    assert 0 < sum(int.from_bytes(frame[12:15], "little") for frame in frames) <= 2000
    vp8x = next(value for kind, value in parts if kind == b"VP8X")
    width, height = (int.from_bytes(vp8x[i:i + 3], "little") + 1 for i in (4, 7))
    assert width <= 768 and height <= 192, (path, width, height)
    assert path.stat().st_size < 150_000
    assert f"R.raw.{logo}" in binding, f"Unwired logo {logo}"
    original = "ic_turtle_full" if logo == "ic_turtle_full_animated" else logo
    assert (root / "res/drawable" / (original + ".webp")).is_file(), "Original art was removed"

manager = (root / "java/com/endiq/turtlelauncher/feature/background/BackgroundManager.kt").read_text()
for path in (root / "res/drawable-nodpi").glob("image_background_*.webp"):
    assert not any(kind == b"ANIM" for kind, _ in chunks(path)), "Wallpapers must remain static"
    assert f"R.drawable.{path.stem}" in manager, f"Unwired background {path.stem}"
    assert path.stat().st_size < 100_000
# Logos must only animate on interaction (hover / press / focus / click), never on
# their own when a screen appears - idle screens must stay free.
assert "ACTION_HOVER_ENTER" in binding, "hover-to-animate wiring missing"
assert "ACTION_DOWN" in binding, "press-to-animate wiring missing"
assert "OnGlobalFocusChangeListener" in binding, "focus-to-animate wiring missing"
assert "playIfVisible" not in binding, "logos must not auto-play when merely visible"
logo_view = (root / "java/com/endiq/turtlelauncher/ui/view/LogoImageView.kt").read_text()
assert "AnimatedLogo.playOnce" in logo_view, "standalone logos must animate on click"
file_bean = (root / "java/com/endiq/turtlelauncher/ui/subassembly/filelist/FileItemBean.kt").read_text()
assert "animatedIconRes" in file_bean, "version-list rows must carry their animated logo"

strings = ET.parse(root / "res/values/launcher_improvements.xml").getroot()
assert len(strings.find("string-array[@name='built_in_background_names']")) == 4
assert (root / "res/drawable/image_error_background.webp").is_file()
assert (root / "res/drawable/image_about_developer.webp").is_file()
print("Six finite animated WebPs, original artwork, background wiring and picker labels passed")
