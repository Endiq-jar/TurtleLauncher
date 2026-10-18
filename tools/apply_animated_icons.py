#!/usr/bin/env python3
"""Upgrade icon views in layouts to their animated counterparts.

Every <ImageView>/<AppCompatImageView> whose src/srcCompat is an @drawable/ic_*
resource becomes com.endiq.turtlelauncher.ui.view.AnimatedIconView, and every
such <ImageButton>/<AppCompatImageButton> becomes AnimatedIconButton, so all
launcher icons animate on hover/press/focus/click via AnimatedIcon (WebP
logos route to AnimatedLogo). Both replacement classes keep the original
attribute and binding contracts (AnimatedIconButton IS an ImageButton).

Views without an ic_* source (wallpapers, avatars, screenshots, banners...)
are untouched, as are ShapeableImageView/FloatingActionButton (Material
components with their own motion) and LogoImageView (already animated).

Idempotent; re-run after adding new layouts: python3 tools/apply_animated_icons.py
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
LAYOUTS = REPO / "TurtleLauncher/src/main/res/layout"

SWAP = {
    "ImageView": "com.endiq.turtlelauncher.ui.view.AnimatedIconView",
    "androidx.appcompat.widget.AppCompatImageView":
        "com.endiq.turtlelauncher.ui.view.AnimatedIconView",
    "ImageButton": "com.endiq.turtlelauncher.ui.view.AnimatedIconButton",
    "androidx.appcompat.widget.AppCompatImageButton":
        "com.endiq.turtlelauncher.ui.view.AnimatedIconButton",
}

TAG_RE = re.compile(
    r"<(ImageView|ImageButton|androidx\.appcompat\.widget\.AppCompatImageView|"
    r"androidx\.appcompat\.widget\.AppCompatImageButton)(?=[\s/>])([^>]*?)(/?)>",
    re.S,
)
SRC_RE = re.compile(r"(?:android:src|app:srcCompat)=\"@drawable/ic_[\w]+\"")


def upgrade(text: str) -> tuple[str, int]:
    out = []
    count = 0
    pos = 0
    # Closing tags of swapped non-self-closing elements, in document order.
    pending_close = []
    for m in TAG_RE.finditer(text):
        chunk = text[pos:m.start()]
        # Flush closing tags that occur in this chunk before the next opening tag.
        if pending_close:
            def close_sub(cm):
                if pending_close and cm.group(1) == pending_close[0]:
                    return f"</{SWAP[pending_close.pop(0)]}>"
                return cm.group(0)
            chunk = re.sub(r"</(ImageView|ImageButton|androidx\.appcompat\.widget\.AppCompatImageView|"
                           r"androidx\.appcompat\.widget\.AppCompatImageButton)>", close_sub, chunk)
        out.append(chunk)
        tag, attrs, self_closing = m.group(1), m.group(2), m.group(3)
        if SRC_RE.search(attrs):
            out.append(f"<{SWAP[tag]}{attrs}{self_closing}>")
            count += 1
            if not self_closing:
                pending_close.append(tag)
        else:
            out.append(m.group(0))
        pos = m.end()
    tail = text[pos:]
    if pending_close:
        def close_sub(cm):
            if pending_close and cm.group(1) == pending_close[0]:
                return f"</{SWAP[pending_close.pop(0)]}>"
            return cm.group(0)
        tail = re.sub(r"</(ImageView|ImageButton|androidx\.appcompat\.widget\.AppCompatImageView|"
                      r"androidx\.appcompat\.widget\.AppCompatImageButton)>", close_sub, tail)
    out.append(tail)
    return "".join(out), count


def main() -> int:
    total = files = 0
    for path in sorted(LAYOUTS.glob("*.xml")):
        original = path.read_text()
        upgraded, n = upgrade(original)
        if n:
            path.write_text(upgraded)
            total += n
            files += 1
    print(f"Upgraded {total} icon views across {files} layouts")
    return 0


if __name__ == "__main__":
    sys.exit(main())
