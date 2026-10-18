#!/usr/bin/env python3
"""Reproducible Turtle art. Requires Pillow with libwebp; never overwrites original art.
Run: python tools/generate_launcher_art.py
Animated logos are short, finite light sweeps (not perpetual render loops).
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageChops, ImageFont

ROOT = Path(__file__).resolve().parents[1] / 'TurtleLauncher/src/main/res'
RAW = ROOT / 'raw'
DRAWABLE = ROOT / 'drawable-nodpi'
DRAWABLE.mkdir(exist_ok=True)
SOURCES = ['ic_minecraft', 'ic_turtle_full', 'ic_command_block', 'app_name_title',
           'ic_old_cobblestone', 'ic_old_grass_block']
for name in SOURCES:
    source = Image.open(ROOT / 'drawable' / (name + '.webp')).convert('RGBA')
    source.thumbnail((768, 96) if name == 'app_name_title' else (192, 192), Image.Resampling.LANCZOS)
    frames = []
    w, h = source.size
    for index in range(20):
        frame = source.copy()
        sweep = Image.new('RGBA', source.size)
        draw = ImageDraw.Draw(sweep)
        x = int(-w * .4 + index / 19 * w * 1.8)
        width = max(8, w // 9)
        draw.polygon([(x, 0), (x + width, 0), (x - h // 3 + width, h), (x - h // 3, h)], fill=(198, 255, 216, 65))
        sweep.putalpha(ImageChops.multiply(sweep.getchannel('A'), source.getchannel('A')))
        frame = Image.alpha_composite(frame, sweep)
        frames.append(frame)
    out = 'ic_turtle_full_animated' if name == 'ic_turtle_full' else name
    frames[0].save(RAW / (out + '.webp'), save_all=True, append_images=frames[1:], duration=85, loop=1, lossless=True, method=6)

# Small native-resolution voxel landscapes: nearest-neighbour pixels, no GPU animation.
palettes = {
    'image_background_turtle_coast': ((13, 32, 43), (67, 134, 139), (137, 208, 159), (18, 73, 63), (20, 108, 111)),
    'image_background_amethyst_night': ((13, 15, 37), (81, 57, 114), (195, 160, 232), (48, 34, 76), (64, 63, 113)),
    'image_background_ember_peaks': ((34, 20, 34), (156, 70, 65), (255, 199, 135), (79, 37, 47), (111, 52, 50)),
}
for seed, (name, (top, horizon, light, land, water)) in enumerate(palettes.items()):
    rng = random.Random(42 + seed)
    im = Image.new('RGB', (320, 180))
    d = ImageDraw.Draw(im)
    for y in range(180):
        t = min(1, y / 150)
        d.line((0, y, 320, y), fill=tuple(int(a + (b - a) * t) for a, b in zip(top, horizon)))
    for _ in range(55):
        x, y = rng.randrange(320), rng.randrange(95)
        d.point((x, y), fill=light)
    d.rectangle((239, 30, 256, 47), fill=light)
    for layer in range(3):
        color = tuple(int(c * (1.5 - layer * .28)) for c in land)
        points = [(0, 180), (0, 100 + layer * 18)]
        for x in range(0, 329, 8):
            y = int(95 + layer * 19 + math.sin(x / 39 + layer) * 15 + rng.randrange(-5, 6))
            points.extend([(x, y), (x + 8, y)])
        d.polygon(points + [(320, 180)], fill=color)
    d.polygon([(118, 180), (181, 124), (201, 124), (164, 180)], fill=water)
    for _ in range(30):
        x, y = rng.randrange(320), rng.randrange(145, 180)
        d.rectangle((x, y, x + 2, y), fill=light)
    # Turtle silhouette at the shore, subtle enough to leave UI text legible.
    d.rectangle((228, 140, 245, 151), fill=(32, 109, 77))
    d.rectangle((233, 137, 241, 154), fill=(43, 133, 89))
    d.rectangle((245, 142, 251, 148), fill=(116, 190, 130))
    d.point((249, 143), fill=(7, 24, 22))
    im.resize((960, 540), Image.Resampling.NEAREST).save(DRAWABLE / (name + '.webp'), quality=88, method=6)
print('Generated six animated logos and three static voxel backgrounds; originals preserved.')
