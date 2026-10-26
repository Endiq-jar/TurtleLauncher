#!/usr/bin/env python3
"""Build every launcher-icon asset from the supplied emblem artwork.

The master (``tools/art/turtle_c_emblem_source.webp``) is the "C + turtle"
logo: an isometric green cube shaped like an open C with a voxel turtle inside,
on a dark studio background, with the "CURTLE / LAUNCHER" wordmark underneath.
It is stored at 1024x1024 so these assets stay reproducible without keeping the
1.5 MB original PNG in the repository.

What is generated
-----------------
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher.png         rounded app tile
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_round.png   circular app tile
  res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.webp
                                                          adaptive foreground
  res/drawable/ic_launcher_monochrome.webp                themed-icon glyph
  res/drawable/ic_turtle_full.webp                        in-app logo tile
  res/raw/ic_turtle_full_animated.webp                    finite light sweep
                                                          (same interaction
                                                          contract as the other
                                                          animated logos)

Background removal
------------------
The studio background is a vignette that fades into the artwork's glow, so a
global colour threshold would either eat the dark pedestal faces or keep a dark
halo. Instead the background is flood-filled inward from the border, and the
fill may only cross pixels that are locally smooth (no hard edge). Sharp
artwork edges - the cube's outline, the turtle, the pedestal - stop the fill,
which is exactly what should stay opaque.

Run from the repository root:
    python3 tools/generate_app_icon.py

Requires Pillow (WebP support) and numpy; the same family of dependency as
tools/generate_launcher_art.py. Re-running regenerates identical files.
"""
import hashlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1] / "TurtleLauncher/src/main/res"
SOURCE = Path(__file__).resolve().parents[1] / "tools/art/turtle_c_emblem_source.webp"

# Legacy launcher icons: 48dp base.
LAUNCHER_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
# Adaptive icons: 108dp canvas, safe zone is the inner 66dp.
FOREGROUND_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# The artwork's own backdrop, sampled at the glow centre and at a corner. The
# adaptive background gradient in res/drawable/ic_launcher_background.xml uses
# the same pair - update both together.
STUDIO_INNER = (14, 52, 34)
STUDIO_OUTER = (1, 6, 5)
# The wordmark occupies the bottom of the master; everything above is emblem.
WORDMARK_TOP = 0.645


def _master():
    return Image.open(SOURCE).convert("RGB")


def _key_background(image, smoothness=7):
    """Return the artwork with its studio background made transparent."""
    a = np.asarray(image).astype(np.int16)
    height, width, _ = a.shape

    def neighbour_delta(dy, dx):
        shifted = np.roll(a, (dy, dx), axis=(0, 1))
        return np.abs(a - shifted).max(axis=2)

    local = np.zeros((height, width), np.int16)
    for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
        local = np.maximum(local, neighbour_delta(dy, dx))
    walkable = local <= smoothness

    # Flood fill inward from every border pixel, crossing only smooth pixels.
    outside = np.zeros((height, width), bool)
    outside[0, :] = outside[-1, :] = True
    outside[:, 0] = outside[:, -1] = True
    outside &= walkable
    while True:
        grown = outside.copy()
        grown[1:, :] |= outside[:-1, :]
        grown[:-1, :] |= outside[1:, :]
        grown[:, 1:] |= outside[:, :-1]
        grown[:, :-1] |= outside[:, 1:]
        grown &= walkable
        if (grown == outside).all():
            break
        outside = grown

    alpha = np.where(outside, 0, 255).astype(np.uint8)
    alpha = Image.fromarray(alpha, "L")
    # Remove single-pixel fill artefacts, then anti-alias the cut edge.
    alpha = alpha.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    result = image.convert("RGBA")
    result.putalpha(alpha)
    return result


def _drop_specks(image, min_pixels=48):
    """Clear isolated islands left behind in the background."""
    pixels = np.array(image)
    alpha = pixels[:, :, 3]
    height, width = alpha.shape
    seen = np.zeros((height, width), bool)
    keep = np.zeros_like(alpha)
    for y in range(height):
        for x in range(width):
            if alpha[y, x] <= 8 or seen[y, x]:
                continue
            stack = [(y, x)]
            seen[y, x] = True
            component = []
            while stack:
                cy, cx = stack.pop()
                component.append((cy, cx))
                for ny in (cy - 1, cy, cy + 1):
                    for nx in (cx - 1, cx, cx + 1):
                        if 0 <= ny < height and 0 <= nx < width and alpha[ny, nx] > 8 and not seen[ny, nx]:
                            seen[ny, nx] = True
                            stack.append((ny, nx))
            if len(component) >= min_pixels:
                for cy, cx in component:
                    keep[cy, cx] = alpha[cy, cx]
    out = pixels.copy()
    out[:, :, 3] = keep
    return Image.fromarray(out, "RGBA")


def _trim(image, threshold=6):
    bbox = image.getchannel("A").point(lambda v: 255 if v > threshold else 0).getbbox()
    return image.crop(bbox) if bbox else image


def _emblem(master):
    """Keyed, trimmed emblem without the wordmark."""
    height = master.height
    emblem_area = master.crop((0, 0, master.width, round(height * WORDMARK_TOP)))
    keyed = _drop_specks(_key_background(emblem_area))
    return _trim(keyed)


def _fit(image, box_w, box_h):
    scale = min(box_w / image.width, box_h / image.height)
    return image.resize(
        (max(1, round(image.width * scale)), max(1, round(image.height * scale))),
        Image.Resampling.LANCZOS,
    )


def _studio_background(size):
    """Radial studio backdrop matching the master artwork."""
    gradient = Image.radial_gradient("L").resize((size, size), Image.Resampling.BICUBIC)
    channels = [
        gradient.point(lambda v, i=i, o=o: int(i + (o - i) * (v / 255) ** 1.5))
        for i, o in zip(STUDIO_INNER, STUDIO_OUTER)
    ]
    return Image.merge("RGB", channels).convert("RGBA")


def _tile(emblem, size, shape):
    """Square or round app tile: keyed emblem on the studio backdrop."""
    canvas = _studio_background(size)
    # 0.86 keeps the C's outer corners inside the rounded/circular mask
    # (launcher masks only show the inner ~80% of a legacy icon).
    content = _fit(emblem, round(size * 0.86), round(size * 0.88))
    canvas.alpha_composite(content, ((size - content.width) // 2, (size - content.height) // 2))

    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    if shape == "circle":
        draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=round(size * 0.19), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(max(0.5, size / 192)))
    canvas.putalpha(ImageChops.multiply(canvas.getchannel("A"), mask))
    return canvas


def _foreground(emblem, size):
    """Adaptive foreground: the emblem, inside the 66/108 safe zone."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    safe = size * 66 / 108
    content = _fit(emblem, round(safe * 1.04), round(safe * 1.04))
    canvas.alpha_composite(content, ((size - content.width) // 2, (size - content.height) // 2))
    return canvas


def _monochrome(emblem, size):
    """Single-colour glyph for Android 13+ themed icons."""
    alpha = np.array(emblem.getchannel("A")) > 96
    # Fill fully enclosed holes (the dark pedestal faces, the gap under the
    # turtle) so the mark reads as one solid silhouette rather than confetti.
    height, width = alpha.shape
    reachable = np.zeros_like(alpha)
    reachable[0, :] = ~alpha[0, :]
    reachable[-1, :] = ~alpha[-1, :]
    reachable[:, 0] = ~alpha[:, 0]
    reachable[:, -1] = ~alpha[:, -1]
    holes = ~alpha
    while True:
        grown = reachable.copy()
        grown[1:, :] |= reachable[:-1, :]
        grown[:-1, :] |= reachable[1:, :]
        grown[:, 1:] |= reachable[:, :-1]
        grown[:, :-1] |= reachable[:, 1:]
        grown &= holes
        if (grown == reachable).all():
            break
        reachable = grown
    filled = alpha | (holes & ~reachable)
    mask = Image.fromarray((filled * 255).astype(np.uint8), "L")
    mask = mask.filter(ImageFilter.GaussianBlur(max(0.4, size / 600)))
    glyph = Image.new("RGBA", emblem.size, (255, 255, 255, 0))
    glyph.putalpha(mask)

    canvas = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    safe = size * 66 / 108
    content = _fit(glyph, round(safe * 0.98), round(safe * 0.98))
    canvas.alpha_composite(content, ((size - content.width) // 2, (size - content.height) // 2))
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

    emblem = _emblem(_master())
    print(f"emblem: {emblem.width}x{emblem.height} after background removal")

    # In-app logo (splash, about, logo picker, notifications): a 512px tile.
    _tile(emblem, 512, "rounded").save(ROOT / "drawable/ic_turtle_full.webp", quality=93, method=6)

    for density, size in LAUNCHER_SIZES.items():
        folder = ROOT / f"mipmap-{density}"
        _tile(emblem, size, "rounded").save(folder / "ic_launcher.png", optimize=True)
        _tile(emblem, size, "circle").save(folder / "ic_launcher_round.png", optimize=True)

    for density, size in FOREGROUND_SIZES.items():
        folder = ROOT / f"mipmap-{density}"
        _foreground(emblem, size).save(folder / "ic_launcher_foreground.webp", lossless=True, method=6)

    _monochrome(emblem, FOREGROUND_SIZES["xxxhdpi"]).save(
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
