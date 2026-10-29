#!/usr/bin/env python3
"""Build every launcher-icon asset from the supplied moonrise-turtle artwork.

The master (``tools/art/turtle_moonrise_source.webp``) is the full-bleed
"turtle under a crescent moon" artwork used as the app's home-screen icon
(``home_icon_0018_original_moonrise_stars``). Unlike the previous "C + turtle"
emblem, this source has no separate studio backdrop to key out and no
wordmark to trim - the whole 512x512 square is the finished picture, so every
asset below is built by scaling/cropping that square directly.

What is generated
-----------------
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png          legacy app tile
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png    legacy app tile
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_background.webp
                                                           adaptive background
                                                           (cover-cropped art)
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.webp
                                                           adaptive foreground
                                                           (art inset to the
                                                           66/108 safe zone)
  res/drawable/ic_launcher_monochrome.webp                 themed-icon glyph
  res/drawable/ic_turtle_full.webp                         in-app logo tile
  res/raw/ic_turtle_full_animated.webp                     finite light sweep
                                                           (same interaction
                                                           contract as the
                                                           other animated
                                                           logos)

Legacy tiles
------------
``ic_launcher.png`` / ``ic_launcher_round.png`` are just the square artwork
resized per density, with no extra masking - the same approach already used
for the committed ``home_icon_*`` tiles in ``res/mipmap-nodpi``. Android and
OEM launchers apply their own corner/circle mask to a square icon at display
time, so a second hand-cropped round variant isn't needed.

Adaptive background/foreground
-------------------------------
The artwork is already a finished square picture, so it is the background
layer alone, scaled 1:1 to fill the 108dp canvas (the source is square, so
this is a plain resize, no crop). The foreground layer is left fully
transparent. Splitting the same photo across both layers at different crops
was tried first and left a visible seam where the two scales met, so a
single layer is used instead - exactly how the committed ``home_icon_*``
tiles already work as the plain manifest icon.

Run from the repository root:
    python3 tools/generate_app_icon.py

Requires Pillow (WebP support) and numpy. Re-running regenerates identical
files.
"""
import hashlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1] / "TurtleLauncher/src/main/res"
SOURCE = Path(__file__).resolve().parents[1] / "tools/art/turtle_moonrise_source.webp"

# Legacy launcher icons: 48dp base.
LAUNCHER_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
# Adaptive icons: 108dp canvas, safe zone is the inner 66dp.
FOREGROUND_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def _master():
    return Image.open(SOURCE).convert("RGB")


def _cover(image, size):
    """Scale ``image`` up to ``size`` and centre-crop to a square, no padding."""
    scale = max(size / image.width, size / image.height)
    resized = image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.LANCZOS,
    )
    left = (resized.width - size) // 2
    top = (resized.height - size) // 2
    return resized.crop((left, top, left + size, top + size))


def _contain(image, box_w, box_h):
    scale = min(box_w / image.width, box_h / image.height)
    return image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.LANCZOS,
    )


def _legacy_tile(master, size):
    """Square legacy icon: the artwork, resized, no masking (matches home_icon_*)."""
    return _cover(master, size).convert("RGBA")


def _background(master, size):
    """Adaptive background: the artwork, scaled 1:1 to fill the canvas."""
    return _cover(master, size).convert("RGBA")


def _foreground(size):
    """Adaptive foreground: fully transparent - the art lives in the background."""
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def _monochrome(master, size):
    """Single-colour glyph for Android 13+ themed icons.

    The artwork has no flat silhouette and a global brightness threshold just
    picks out the lighter half of the sky gradient, so this instead keys on
    colour: the turtle's shell is distinctly greener than the teal sky
    (G channel well above B), and the moon/stars are near-white (all
    channels high and close together). Everything else - sky, outline,
    pedestal shadow - drops out.
    """
    safe_px = round(size * 66 / 108)
    art = _contain(master, safe_px, safe_px).convert("RGB")
    pixels = np.asarray(art, dtype=np.int16)
    r, g, b = pixels[:, :, 0], pixels[:, :, 1], pixels[:, :, 2]
    turtle = (g - b) > 28
    moonlight = (r > 165) & (g > 165) & (b > 165)
    mask = np.where(turtle | moonlight, 255, 0).astype(np.uint8)
    glyph_mask = Image.fromarray(mask, "L").filter(ImageFilter.GaussianBlur(max(0.6, size / 400)))

    glyph = Image.new("RGBA", art.size, (255, 255, 255, 0))
    glyph.putalpha(glyph_mask)

    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    canvas.alpha_composite(glyph, ((size - glyph.width) // 2, (size - glyph.height) // 2))
    return canvas


def _rounded_tile(master, size, radius_ratio=0.19):
    """In-app logo tile: the artwork, resized, with rounded corners."""
    canvas = _cover(master, size).convert("RGBA")
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * radius_ratio), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(max(0.5, size / 192)))
    canvas.putalpha(ImageChops.multiply(canvas.getchannel("A"), mask))
    return canvas


def _animated_logo(tile, frames=20, size=192, duration=85):
    """Finite diagonal light sweep, same contract as tools/generate_launcher_art.py."""
    source = tile.resize((size, size), Image.Resampling.LANCZOS)
    width, height = source.size
    out = []
    for index in range(frames):
        frame = source.copy()
        sweep = Image.new("RGBA", source.size)
        draw = ImageDraw.Draw(sweep)
        x = int(-width * 0.4 + index / (frames - 1) * width * 1.8)
        bar = max(8, width // 9)
        draw.polygon(
            [(x, 0), (x + bar, 0), (x - height // 3 + bar, height), (x - height // 3, height)],
            fill=(198, 255, 216, 65),
        )
        sweep.putalpha(ImageChops.multiply(sweep.getchannel("A"), source.getchannel("A")))
        out.append(Image.alpha_composite(frame, sweep))
    return out, duration


def main():
    for required in ("mipmap-mdpi", "mipmap-xxxhdpi", "drawable", "raw"):
        (ROOT / required).mkdir(parents=True, exist_ok=True)

    master = _master()
    print(f"master: {master.width}x{master.height}")

    old_vector_bg = ROOT / "drawable/ic_launcher_background.xml"
    if old_vector_bg.exists():
        old_vector_bg.unlink()

    # In-app logo (splash, about, logo picker, notifications): a 512px tile.
    _rounded_tile(master, 512).save(ROOT / "drawable/ic_turtle_full.webp", quality=93, method=6)

    for density, size in LAUNCHER_SIZES.items():
        folder = ROOT / f"mipmap-{density}"
        _legacy_tile(master, size).save(folder / "ic_launcher.png", optimize=True)
        _legacy_tile(master, size).save(folder / "ic_launcher_round.png", optimize=True)

    for density, size in FOREGROUND_SIZES.items():
        folder = ROOT / f"mipmap-{density}"
        _background(master, size).save(folder / "ic_launcher_background.webp", lossless=True, method=6)
        _foreground(size).save(folder / "ic_launcher_foreground.webp", lossless=True, method=6)

    _monochrome(master, FOREGROUND_SIZES["xxxhdpi"]).save(
        ROOT / "drawable/ic_launcher_monochrome.webp", lossless=True, method=6
    )

    frames, duration = _animated_logo(Image.open(ROOT / "drawable/ic_turtle_full.webp").convert("RGBA"))
    frames[0].save(
        ROOT / "raw/ic_turtle_full_animated.webp",
        save_all=True,
        append_images=frames[1:],
        duration=duration,
        loop=1,
        quality=84,
        method=6,
    )

    print("wrote launcher icons, adaptive layers, in-app logo and animated sweep")
    for path in sorted((ROOT / "mipmap-xxxhdpi").glob("ic_launcher*")):
        digest = hashlib.sha1(path.read_bytes()).hexdigest()[:12]
        print(f"  {path.name}: {path.stat().st_size} bytes, sha1 {digest}")


if __name__ == "__main__":
    main()
