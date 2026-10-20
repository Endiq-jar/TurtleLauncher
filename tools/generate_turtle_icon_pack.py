#!/usr/bin/env python3
"""Build 2,048 deterministic pixel-art TurtleLauncher concepts + the original.

Requires Python 3.10+ and Pillow. Outputs stay in ignored release/ by default.
No launcher resources are modified. All new art is drawn procedurally, not by
an image model. PNGs are 512px; SVGs preserve the crisp pixel geometry.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import random
import shutil
import zipfile

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
THEMES = [
    ('Meadow', '#76ad50', '#c0e987', '#324b29', '#163d39', '#417b65'),
    ('Ocean', '#34ad9a', '#a5efdb', '#17465c', '#08283f', '#166b8c'),
    ('Cosmic', '#9084d9', '#dfccff', '#332252', '#151329', '#463569'),
    ('Cyber', '#34d6b6', '#c1ffec', '#135260', '#09242c', '#146170'),
    ('Royal', '#a690d8', '#eee0ff', '#463667', '#2d173c', '#78508e'),
    ('Ember', '#d88b3e', '#ffda8c', '#713b2b', '#351b25', '#984329'),
    ('Glacier', '#80c8df', '#e4fbff', '#365b86', '#14283e', '#486e9b'),
    ('Dune', '#bca16b', '#ffebaf', '#675139', '#3c3030', '#987350'),
    ('Sakura', '#cf83ab', '#ffe4f2', '#683954', '#36233d', '#946280'),
    ('Autumn', '#bb8143', '#f9d782', '#634126', '#302d23', '#7a673e'),
    ('Lunar', '#9ba9b6', '#f0f2df', '#3f4d62', '#151d2c', '#46536a'),
    ('Jungle', '#57a85b', '#c3eb80', '#254a39', '#102d31', '#29694c'),
    ('Arcade', '#c37cdb', '#ffdefb', '#603575', '#241538', '#773f83'),
    ('Coral', '#df8b79', '#ffdec4', '#704846', '#263a4b', '#497b86'),
    ('Aurora', '#62baab', '#d8f6b0', '#285955', '#1c2947', '#526a91'),
    ('Golden', '#c9a654', '#fff0b0', '#6b542a', '#25363c', '#607a71'),
]
ACCESSORIES = ['Classic', 'Explorer', 'Crown', 'Sprout', 'Astronaut', 'Wizard',
               'Headphones', 'Scarf', 'Crystal', 'Flower', 'Flag', 'Wings',
               'Antenna', 'Sail', 'Mushroom', 'Lightning']


def rgb(color):
    return tuple(bytes.fromhex(color.lstrip('#')))


def mix(a, b, t):
    return tuple(round(x * (1 - t) + y * t) for x, y in zip(a, b))


def make_turtle(number, theme):
    rng = random.Random(8191 + number * 7919)
    _, body_hex, light_hex, dark_hex, _, _ = theme
    body, light, dark = map(rgb, (body_hex, light_hex, dark_hex))
    shell = mix(body, dark, .35)
    ink = mix(dark, (8, 18, 24), .35)
    gold = (255, 218, 121)
    im = Image.new('RGBA', (96, 96))
    d = ImageDraw.Draw(im)
    accessory = (number // 8) % 16
    pose = number % 8
    # Geometry is independently varied, not merely recoloured.
    cx, cy = 44 + rng.randrange(-3, 4), 49 + rng.randrange(-3, 4)
    rx, ry = rng.randrange(19, 26), rng.randrange(13, 20)
    head_len = rng.randrange(10, 16)
    leg = rng.randrange(7, 13)
    if pose in (2, 3, 6):  # overhead swimming / walking turtle
        rx, ry = rng.randrange(15, 21), rng.randrange(20, 26)
        cx, cy = 48 + rng.randrange(-3, 4), 48 + rng.randrange(-2, 3)
        for side in (-1, 1):
            for level in (-1, 1):
                x, y = cx + side * (rx - 3), cy + level * (ry - 7)
                if pose == 3:
                    pts = [(x, y - 4), (x + side * leg, y + level * 7),
                           (x + side * (leg + 3), y + level * 12), (x - side * 3, y + 5)]
                    d.polygon(pts, fill=ink)
                    d.line(pts[:3], fill=body, width=4)
                else:
                    d.rectangle((min(x, x + side * leg), y - 3, max(x, x + side * leg), y + 4), fill=ink)
                    d.rectangle((min(x, x + side * (leg - 2)), y - 1, max(x, x + side * (leg - 2)), y + 2), fill=body)
        d.polygon([(cx - 4, cy + ry - 2), (cx + 3, cy + ry - 2), (cx + rng.randrange(-5, 6), cy + ry + 8)], fill=ink)
        d.rounded_rectangle((cx - 8, cy - ry - head_len, cx + 8, cy - ry + 5), radius=5, fill=ink)
        d.rounded_rectangle((cx - 6, cy - ry - head_len + 2, cx + 6, cy - ry + 5), radius=3, fill=light)
        for x in (cx - 5, cx + 4):
            d.rectangle((x, cy - ry - head_len + 5, x + 1, cy - ry - head_len + 7), fill=ink)
        shell_box = (cx - rx, cy - ry, cx + rx, cy + ry)
        ax, ay = cx, cy - ry - head_len
    else:  # recognisable side-view descendant of the repository icon
        foot_y = cy + ry + (3 if pose == 4 else 6)
        for x in (cx - rx + 6, cx + rx - 8):
            step = rng.randrange(-4, 5)
            if pose in (1, 5):
                d.polygon([(x - 4, cy + 6), (x + 6, cy + 7), (x + leg, foot_y + step), (x + 1, foot_y + step + 3)], fill=ink)
                d.line([(x, cy + 10), (x + leg - 2, foot_y + step)], fill=body, width=4)
            else:
                d.rectangle((x - 4, cy + 5, x + 4, foot_y + step), fill=ink)
                d.rectangle((x - 2, cy + 7, x + 2, foot_y + step - 2), fill=body)
        d.polygon([(cx - rx + 3, cy + 6), (cx - rx - rng.randrange(6, 12), cy + ry - 1), (cx - rx + 5, cy + ry - 1)], fill=ink)
        d.line([(cx - rx - 5, cy + ry - 2), (cx - rx + 4, cy + 8)], fill=body, width=3)
        hx, hy = cx + rx - 2, cy + rng.randrange(-3, 5)
        d.rounded_rectangle((hx - 5, hy - 6, hx + head_len, hy + 9), radius=4, fill=ink)
        d.rectangle((hx - 4, hy - 3, hx + head_len - 3, hy + 6), fill=light)
        d.rectangle((hx + head_len - 7, hy - 2, hx + head_len - 5, hy + 1), fill=ink)
        d.point((hx + head_len - 7, hy - 2), fill=(255, 255, 239))
        d.line((hx + head_len - 5, hy + 5, hx + head_len - 3, hy + 5), fill=dark)
        shell_box = (cx - rx, cy - ry, cx + rx, cy + ry - 2)
        ax, ay = hx + 4, hy - 7
    # Clipped shell patterns: eight genuinely different structures.
    mask = Image.new('L', (96, 96))
    md = ImageDraw.Draw(mask)
    md.ellipse(shell_box, fill=255)
    texture = Image.new('RGBA', (96, 96), shell + (255,))
    td = ImageDraw.Draw(texture)
    pattern = rng.randrange(8)
    if pattern == 0:  # scutes
        for row, y in enumerate(range(cy - ry, cy + ry, 9)):
            for x in range(cx - rx - 8, cx + rx + 8, 11):
                x += (row % 2) * 5
                td.polygon([(x, y), (x + 7, y), (x + 10, y + 4), (x + 7, y + 8), (x, y + 8), (x - 3, y + 4)], fill=body, outline=dark)
    elif pattern == 1:
        for x in range(cx - rx, cx + rx, rng.randrange(5, 9)):
            td.line((x, cy - ry, x + 12, cy + ry), fill=light, width=3)
    elif pattern == 2:
        for _ in range(24):
            x, y = rng.randrange(cx - rx, cx + rx), rng.randrange(cy - ry, cy + ry)
            td.rectangle((x, y, x + 3, y + 3), fill=light if rng.random() > .4 else dark)
    elif pattern == 3:
        for radius in range(3, rx + 5, 5):
            td.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=light, width=2)
    elif pattern == 4:
        for x in range(cx - rx, cx + rx, 8):
            for y in range(cy - ry, cy + ry, 8):
                td.rectangle((x, y, x + 5, y + 5), fill=body if (x + y) % 16 else light)
    elif pattern == 5:
        for y in range(cy - ry, cy + ry, 6):
            td.line([(cx - rx, y), (cx - 5, y + 6), (cx + 7, y), (cx + rx, y + 5)], fill=light, width=2)
    elif pattern == 6:
        for a in range(0, 360, 45):
            td.line((cx, cy, cx + math.cos(math.radians(a)) * 35, cy + math.sin(math.radians(a)) * 35), fill=light, width=2)
        td.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=body, outline=dark, width=2)
    else:
        td.polygon([(cx, cy - ry + 3), (cx + rx - 3, cy), (cx, cy + ry - 3), (cx - rx + 3, cy)], fill=body, outline=light, width=2)
        td.rectangle((cx - 3, cy - 3, cx + 3, cy + 3), fill=gold)
    im.paste(texture, (0, 0), mask)
    d = ImageDraw.Draw(im)
    d.ellipse(shell_box, outline=ink, width=3)
    d.arc((shell_box[0] + 4, shell_box[1] + 4, shell_box[2] - 4, shell_box[3] - 4), 195, 265, fill=light, width=2)
    sx, sy = cx, cy - ry
    if accessory == 1:  # explorer cap
        d.rectangle((ax - 8, ay - 5, ax + 6, ay), fill=gold)
        d.rectangle((ax - 11, ay, ax + 11, ay + 2), fill=dark)
    elif accessory == 2:
        d.polygon([(ax - 8, ay), (ax - 9, ay - 9), (ax - 3, ay - 5), (ax, ay - 12), (ax + 4, ay - 5), (ax + 9, ay - 9), (ax + 8, ay)], fill=gold)
        d.rectangle((ax - 1, ay - 4, ax + 1, ay - 2), fill=(238, 103, 127))
    elif accessory == 3:
        d.line((sx, sy + 2, sx, sy - 8), fill=light, width=2)
        d.ellipse((sx - 9, sy - 12, sx, sy - 6), fill=body)
        d.ellipse((sx + 1, sy - 15, sx + 10, sy - 8), fill=light)
    elif accessory == 4:
        d.arc((ax - 12, ay - 12, ax + 13, ay + 17), 160, 380, fill=(219, 247, 255), width=2)
        d.rectangle((ax - 10, ay + 8, ax + 10, ay + 10), fill=gold)
    elif accessory == 5:
        d.polygon([(ax - 10, ay), (ax + 1, ay - 17), (ax + 8, ay), (ax + 12, ay + 2), (ax - 12, ay + 2)], fill=(112, 91, 185))
        d.rectangle((ax - 1, ay - 7, ax + 1, ay - 5), fill=gold)
    elif accessory == 6:
        d.arc((ax - 10, ay - 8, ax + 10, ay + 13), 180, 360, fill=gold, width=3)
        d.rectangle((ax - 11, ay + 1, ax - 7, ay + 8), fill=(225, 137, 145))
        d.rectangle((ax + 7, ay + 1, ax + 11, ay + 8), fill=(225, 137, 145))
    elif accessory == 7:
        d.rectangle((ax - 9, ay + 10, ax + 5, ay + 13), fill=(234, 114, 99))
        d.polygon([(ax - 8, ay + 12), (ax - 17, ay + 18), (ax - 10, ay + 20), (ax - 4, ay + 12)], fill=gold)
    elif accessory == 8:
        for dx, size in [(-9, 10), (0, 17), (9, 12)]:
            d.polygon([(sx + dx - 4, sy + 3), (sx + dx - 3, sy - size + 5), (sx + dx, sy - size), (sx + dx + 4, sy - size + 6), (sx + dx + 3, sy + 3)], fill=light, outline=dark)
    elif accessory == 9:
        for dx, dy in [(0, -5), (-5, 0), (5, 0), (0, 5)]:
            d.ellipse((ax + dx - 3, ay + dy - 8, ax + dx + 3, ay + dy - 2), fill=(255, 184, 210))
        d.rectangle((ax - 2, ay - 7, ax + 2, ay - 3), fill=gold)
    elif accessory == 10:
        d.line((sx, sy + 1, sx, sy - 19), fill=gold, width=2)
        d.polygon([(sx + 2, sy - 19), (sx + 16, sy - 14), (sx + 2, sy - 9)], fill=light)
    elif accessory == 11:
        for sign in (-1, 1):
            d.polygon([(cx, cy), (cx + sign * 27, cy - 17), (cx + sign * 23, cy - 4), (cx + sign * 14, cy + 2)], fill=light, outline=dark)
    elif accessory == 12:
        d.line((ax, ay, ax + 4, ay - 12), fill=gold, width=2)
        d.rectangle((ax + 1, ay - 16, ax + 6, ay - 12), fill=light)
    elif accessory == 13:
        d.line((sx, sy + 3, sx, sy - 20), fill=gold, width=2)
        d.polygon([(sx + 3, sy - 19), (sx + 18, sy - 3), (sx + 3, sy - 3)], fill=light)
    elif accessory == 14:
        d.rectangle((sx - 3, sy - 7, sx + 3, sy + 2), fill=gold)
        d.pieslice((sx - 12, sy - 22, sx + 12, sy - 3), 180, 360, fill=(224, 111, 117))
        for dx in (-6, 3):
            d.rectangle((sx + dx, sy - 15, sx + dx + 3, sy - 12), fill=(255, 239, 206))
    elif accessory == 15:
        d.polygon([(sx + 2, sy - 20), (sx - 8, sy - 7), (sx - 1, sy - 7), (sx - 5, sy + 3), (sx + 9, sy - 11), (sx + 2, sy - 11)], fill=gold)
    if pose in (4, 6, 7):
        im = im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    return im, pose, accessory, pattern


def background(number, theme):
    rng = random.Random(number + 591)
    low, high = map(rgb, theme[4:6])
    im = Image.new('RGBA', (96, 96))
    d = ImageDraw.Draw(im)
    for y in range(96):
        d.line((0, y, 95, y), fill=mix(high, low, y / 95) + (255,))
    motif = (number // 16) % 8
    accent = mix(high, (235, 251, 239), .35)
    if motif == 0:
        for _ in range(25):
            x, y = rng.randrange(8, 88), rng.randrange(6, 86)
            d.point((x, y), fill=accent)
    elif motif == 1:
        for x in range(-30, 110, 15):
            d.line((x, 0, x + 60, 95), fill=mix(high, low, .25), width=3)
    elif motif == 2:
        d.ellipse((9, 9, 87, 87), outline=accent, width=1)
        d.ellipse((14, 14, 82, 82), outline=mix(high, low, .1), width=2)
    elif motif == 3:
        for x in range(0, 96, 12):
            d.line((x, 0, x, 96), fill=mix(high, low, .3))
            d.line((0, x, 96, x), fill=mix(high, low, .3))
    elif motif == 4:
        d.ellipse((58, 9, 80, 31), fill=accent)
        for y in (76, 84, 92):
            d.line([(0, y), (24, y - 3), (48, y + 1), (72, y - 4), (96, y)], fill=high, width=2)
    elif motif == 5:
        for _ in range(12):
            x, y = rng.randrange(5, 90), rng.randrange(5, 88)
            d.rectangle((x, y, x + 2, y + 2), outline=accent)
    elif motif == 6:
        for y in (70, 80, 90):
            d.polygon([(0, y), (18, y - 14), (35, y - 3), (60, y - 17), (96, y - 3), (96, 96), (0, 96)], fill=mix(low, high, (96 - y) / 45))
    else:
        for n in range(4):
            d.arc((8 - n * 7, 8 - n * 7, 88 + n * 7, 88 + n * 7), 200, 330, fill=accent, width=2)
    d.ellipse((22, 76, 76, 82), fill=low)
    return im, motif


def svg_from_pixels(im):
    """Lossless vector run-length encoding, with no embedded raster images."""
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 96 96" shape-rendering="crispEdges">']
    for y in range(96):
        x = 0
        while x < 96:
            c = im.getpixel((x, y))
            start = x
            while x < 96 and im.getpixel((x, y)) == c:
                x += 1
            if c[3]:
                color = '#%02x%02x%02x' % c[:3]
                parts.append(f'<path fill="{color}" d="M{start} {y}h{x-start}v1H{start}z"/>')
    parts.append('</svg>')
    return ''.join(parts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / 'release/turtle-icon-pack')
    args = parser.parse_args()
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    for folder in ('png', 'svg', 'original'):
        (out / folder).mkdir(exist_ok=True)
    original = ROOT / 'TurtleLauncher/src/main/res/mipmap-xxxhdpi/ic_launcher.png'
    shutil.copyfile(original, out / 'original/original.png')
    shutil.copyfile(ROOT / 'TurtleLauncher/src/main/res/drawable/ic_turtle_full.webp', out / 'original/ic_turtle_full.webp')
    rows, hashes, silhouettes = [], set(), set()
    sample = []
    for i in range(2048):
        theme = THEMES[i // 128]
        turtle, pose, accessory, pattern = make_turtle(i, theme)
        silhouette = hashlib.sha256(turtle.getchannel('A').tobytes()).hexdigest()
        assert silhouette not in silhouettes, f'Duplicate silhouette at {i}'
        silhouettes.add(silhouette)
        image, motif = background(i, theme)
        image.alpha_composite(turtle)
        image = image.convert('RGB')
        digest = hashlib.sha256(image.tobytes()).hexdigest()
        assert digest not in hashes, f'Duplicate image at {i}'
        hashes.add(digest)
        stem = f'{i+1:04d}-{theme[0].lower()}-{ACCESSORIES[accessory].lower()}'
        png = image.resize((512, 512), Image.Resampling.NEAREST)
        png.save(out / 'png' / f'{stem}.png', optimize=True)
        (out / 'svg' / f'{stem}.svg').write_text(svg_from_pixels(image.convert('RGBA')))
        rows.append(dict(id=i+1, name=stem, theme=theme[0], accessory=ACCESSORIES[accessory], pose=pose, shell_pattern=pattern, background=motif, sha256_pixels=digest, sha256_silhouette=silhouette))
        # Sample varied views/accessories, not just every 32nd (same-pose) icon.
        if i % 128 in {(i // 128 * 7 + k * 33) % 128 for k in range(4)}:
            sample.append((stem, png.copy()))
        if (i + 1) % 128 == 0:
            print(f'{i+1}/2048 generated: {theme[0]}', flush=True)
    with (out / 'manifest.csv').open('w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    (out / 'manifest.json').write_text(json.dumps(rows, indent=2))
    cards = ''.join(f'<a class="card" data-name="{r["name"]}" href="png/{r["name"]}.png"><img loading="lazy" width="128" height="128" src="png/{r["name"]}.png"><span>{r["name"]}</span></a>' for r in rows)
    (out / 'index.html').write_text('''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>TurtleLauncher • 2048 icons</title><style>
    *{box-sizing:border-box}body{margin:0;background:#101d23;color:#e3f5e9;font:15px system-ui}header{padding:42px 5vw 28px;border-bottom:1px solid #335044}h1{font-size:clamp(28px,5vw,54px);letter-spacing:-2px;margin:8px 0}p{color:#acc1b6;line-height:1.7}input{width:min(500px,100%);padding:14px;border-radius:10px;border:1px solid #547366;background:#20362e;color:white;font:inherit}main{padding:24px 5vw;display:grid;grid-template-columns:repeat(auto-fill,minmax(155px,1fr));gap:18px}.card{display:flex;align-items:center;flex-direction:column;background:#1b2d2a;padding:14px;border:1px solid #2c453c;border-radius:14px;color:#c8e1d0;text-decoration:none}.card:hover{border-color:#9cddb3}.card img{width:100%;height:auto;aspect-ratio:1;image-rendering:pixelated;border-radius:10px}.card span{font-size:11px;margin-top:12px;overflow-wrap:anywhere}a{color:#b5efc7} .original{width:64px;image-rendering:pixelated;float:right} [hidden]{display:none!important}</style>
    <header><a href="original/original.png"><img class="original" src="original/original.png" alt="Original TurtleLauncher icon"></a><p>TURTLELAUNCHER / PIXEL EXPLORATIONS</p><h1>One turtle. 2,048 identities.</h1><p>2,048 unique designs • 16 themes • PNG 512 × 512 + scalable SVG<br>Click an icon to open the PNG. Matching SVG files are in the svg folder.<br><a href="original/original.png">Untouched original</a> · <a href="manifest.csv">Catalog CSV</a></p><input id="search" placeholder="Filter: ocean, wizard, crown, 0042…" aria-label="Filter icons"><p id="count">2,048 icons</p></header><main>''' + cards + '''</main><script>const cards=[...document.querySelectorAll('.card')];document.querySelector('#search').addEventListener('input',e=>{let n=0;const q=e.target.value.toLowerCase();cards.forEach(c=>{c.hidden=!c.dataset.name.includes(q);if(!c.hidden)n++});document.querySelector('#count').textContent=n.toLocaleString()+' icons'});</script></html>''')
    preview = Image.new('RGB', (1440, 1660), '#101d23')
    draw = ImageDraw.Draw(preview)
    fontpath = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    font = ImageFont.truetype(fontpath, 38) if Path(fontpath).exists() else ImageFont.load_default()
    small = ImageFont.truetype(fontpath, 13) if Path(fontpath).exists() else ImageFont.load_default()
    draw.text((38, 25), 'TURTLELAUNCHER / 2,048 UNIQUE ICONS', fill='#def3e3', font=font)
    draw.text((40, 84), '64 examples • 16 themes • untouched original included in ZIP', fill='#a4c1b0', font=small)
    for n, (name, image) in enumerate(sample):
        x, y = 32 + (n % 8) * 176, 132 + (n // 8) * 188
        preview.paste(image.resize((160, 160), Image.Resampling.NEAREST), (x, y))
        draw.text((x, y + 164), name[:24], fill='#b6cebf', font=small)
    preview.save(out / 'PREVIEW.jpg', quality=92)
    (out / 'README.txt').write_text('''TURTLELAUNCHER — 2,048 NEW ICONS + ORIGINAL

Open index.html in a browser after extracting the ZIP to browse and filter.

png/       2,048 new 512 x 512 pixel-art app-icon concepts, RGB PNG.
svg/       2,048 matching scalable vectors (pixel geometry, not embedded PNGs).
original/  Unmodified repository launcher PNG and full turtle WebP artwork.
PREVIEW.jpg  A sample of 64 icons (not the entire collection).
manifest.csv / manifest.json  Names, design parameters, pixel and silhouette hashes.

These are procedurally drawn designs inspired by the existing pixel turtle.
They vary anatomy, pose, shell pattern, accessories, theme and background.
All 2,048 pixel hashes AND binary foreground silhouette hashes are unique.
The generator checks both before packaging. Color differences alone are not
used as the uniqueness criterion. Subjective resemblance is still possible
within a related turtle-icon family; these are not 2,048 unrelated brands.

SVGs are crisp vector pixel art, not smooth Bezier illustrations.
PNGs have opaque square backgrounds. These are design concepts, not an
Android adaptive-icon installation pack. No app resources were replaced.

The original artwork is from Endiq-jar/TurtleLauncher; the repository license
is included as LICENSE.repository. No separate trademark rights are granted.
Reproduce using tools/generate_turtle_icon_pack.py (Python + Pillow).
''')
    shutil.copyfile(ROOT / 'LICENSE', out / 'LICENSE.repository')
    archive = out.parent / 'TurtleLauncher-2048-icons-plus-original.zip'
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path in sorted(out.rglob('*')):
            if path.is_file():
                z.write(path, Path('TurtleLauncher-Icon-Pack') / path.relative_to(out))
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert len([n for n in z.namelist() if '/png/' in n]) == 2048
        assert len([n for n in z.namelist() if '/svg/' in n]) == 2048
        assert z.read('TurtleLauncher-Icon-Pack/original/original.png') == original.read_bytes()
    print(f'Validated {len(hashes)} unique images and {len(silhouettes)} unique silhouettes.')
    print(f'ZIP: {archive} ({archive.stat().st_size / 1024**2:.1f} MiB)')


if __name__ == '__main__':
    main()
