#!/usr/bin/env python3
"""2,048 original-shape TurtleLauncher variants. Requires Pillow.

Extracts the actual eight-color turtle from the repository icon. Never redraws,
rotates, flips, stretches, or adds pixels to the turtle. Only palette swaps and
background/decorations change. All outputs are ignored under release/.
"""
from pathlib import Path
import csv
import hashlib
import json
import math
import random
import shutil
import zipfile
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
SIZE = 192
PALETTES = [
 ('Original', None, '#174849', '#66abb2'),
 ('Emerald', '#7ad99a', '#082e2c', '#367b68'),
 ('Ocean', '#77daeb', '#101d43', '#386f9b'),
 ('Sapphire', '#88afff', '#111c3e', '#556eb8'),
 ('Lavender', '#cfb4fa', '#29223e', '#8276a6'),
 ('Rose', '#f1accb', '#351f37', '#b57d9c'),
 ('Coral', '#ffa99b', '#352c45', '#b77c85'),
 ('Amber', '#f3c36e', '#382b27', '#a97d4b'),
 ('Lime', '#d3e878', '#20332d', '#788f55'),
 ('Glacier', '#c2f2ed', '#18323e', '#6b9daa'),
 ('Pearl', '#e8e7d9', '#303747', '#9b9ead'),
 ('Copper', '#dba57b', '#32252b', '#99654d'),
 ('Violet', '#c89bf2', '#22153a', '#7953a3'),
 ('Cherry', '#e98b9f', '#301d2b', '#914559'),
 ('Mint', '#a1e4bc', '#173732', '#629e8b'),
 ('Gold', '#f3da8d', '#252f35', '#8d855c'),
]
SCENES = ['Studio', 'Sunset', 'Moonrise', 'Mountains', 'Coast', 'Dunes',
          'Forest', 'Grid', 'Orbit', 'Arcade', 'Clouds', 'Rain',
          'Botanical', 'Mosaic', 'Northern-lights', 'Rings']
DECOR = ['Clean', 'Stars', 'Sparkles', 'Bubbles', 'Corners', 'Diamonds', 'Confetti', 'Fireflies']


def rgb(c):
    return tuple(bytes.fromhex(c.lstrip('#')))


def mix(a, b, t):
    return tuple(round(x * (1-t) + y*t) for x, y in zip(a, b))


def extract_original():
    original = Image.open(ROOT/'TurtleLauncher/src/main/res/mipmap-xxxhdpi/ic_launcher.png').convert('RGB')
    assert original.size == (192, 192)
    sprite = Image.new('RGBA', original.size)
    for y in range(192):
        for x in range(192):
            c = original.getpixel((x,y))
            # The original has exactly eight turtle colors; the blue gradient
            # and its tiny stars do not satisfy this green-palette predicate.
            if c[1] > c[2]*1.15 and c[0] < c[1]*1.1:
                sprite.putpixel((x,y), c+(255,))
    colors = sorted(set(sprite.get_flattened_data())-{(0,0,0,0)}, key=lambda c:sum(c[:3]))
    assert len(colors) == 8, 'Source artwork palette changed; review extraction.'
    assert sprite.getbbox() == (28,60,152,132)
    assert sum(1 for c in sprite.get_flattened_data() if c[3]) == 5312
    return original, sprite, colors


def recolor(source, colors, target):
    if target is None:
        return source.copy()
    base = rgb(target)
    # Bijective palette mapping preserves every original pixel region,
    # including shell details, outline, legs, face and eye.
    levels = [.14, .23, .42, .57, .72, .84, .94, 1.0]
    mapping = {old:mix((7,14,20), base, t)+(255,) for old,t in zip(colors, levels)}
    result = Image.new('RGBA', source.size)
    result.putdata([mapping.get(c,(0,0,0,0)) for c in source.get_flattened_data()])
    assert result.getchannel('A').tobytes() == source.getchannel('A').tobytes()
    return result


def scene_image(palette, scene, decoration, seed, protected):
    rng = random.Random(seed)
    low, high = rgb(palette[2]), rgb(palette[3])
    light = mix(high,(237,248,233),.62)
    mid = mix(low,high,.55)
    im = Image.new('RGB',(192,192))
    d = ImageDraw.Draw(im)
    for y in range(192):
        d.line((0,y,191,y), fill=mix(high,low,y/191))
    if scene == 0:
        # A restrained studio halo, original-style clean backdrop.
        for r in range(85,0,-1):
            t = (1-r/85)*.14
            d.ellipse((96-r,92-r,96+r,92+r), fill=mix(mid,light,t))
    elif scene == 1:
        d.ellipse((114,22,166,74),fill=light)
        for y in range(82,192,9):
            d.rectangle((0,y,192,y+3),fill=mix(mid,low,(y-82)/110))
    elif scene == 2:
        d.ellipse((27,24,67,64),fill=light)
        d.ellipse((37,18,75,56),fill=mix(high,low,.2))
        for _ in range(30):
            x,y=rng.randrange(192),rng.randrange(100)
            d.point((x,y),fill=light)
    elif scene == 3:
        for n in range(3):
            y=110+n*24
            d.polygon([(0,y),(35,y-44),(65,y-12),(109,y-60),(150,y-18),(192,y-50),(192,192),(0,192)],fill=mix(high,low,.32+n*.23))
    elif scene == 4:
        d.ellipse((121,20,151,50),fill=light)
        d.rectangle((0,130,192,192),fill=low)
        for y in range(134,192,9):
            d.line([(0,y),(40,y-3),(85,y+1),(140,y-3),(192,y)],fill=mid,width=2)
    elif scene == 5:
        d.ellipse((29,29,63,63),fill=light)
        for y in (130,150,175):
            d.polygon([(0,y+8),(35,y-8),(75,y-15),(130,y+2),(192,y-9),(192,192),(0,192)],fill=mix(high,low,(y-100)/100))
    elif scene == 6:
        for n in range(14):
            x=n*17-12; y=rng.randrange(83,150)
            d.rectangle((x-2,y,x+2,192),fill=low)
            d.polygon([(x,y-48),(x-19,y+17),(x+19,y+17)],fill=mix(low,high,rng.uniform(.1,.5)))
    elif scene == 7:
        for y in range(0,192,16):
            d.line((0,y,191,y),fill=mid)
        for x in range(0,192,16):
            d.line((x,0,x,191),fill=mid)
    elif scene == 8:
        d.ellipse((30,28,162,160),outline=mid,width=2)
        d.arc((16,55,176,137),15,340,fill=light,width=2)
        d.ellipse((148,55,158,65),fill=light)
        d.ellipse((31,140,37,146),fill=light)
    elif scene == 9:
        for y in range(132,192,12):
            d.line((0,y,191,y),fill=mid,width=1)
        for x in range(-160,400,40):
            d.line((96,88,x,192),fill=mid)
        for x in range(16,180,20):
            height=rng.randrange(10,38)
            d.rectangle((x,188-height,x+9,188),fill=high)
    elif scene == 10:
        for x,y in [(12,29),(104,15),(144,48),(0,145),(111,157)]:
            for dx,dy,r in [(0,0,10),(12,-5,14),(28,0,10)]:
                d.ellipse((x+dx-r,y+dy-r,x+dx+r,y+dy+r),fill=mix(high,light,.45))
    elif scene == 11:
        for x in range(-120,192,13):
            d.line((x,0,x+90,192),fill=mid,width=2)
    elif scene == 12:
        for x,sign in [(16,1),(176,-1)]:
            d.line((x,165,x+sign*4,46),fill=mid,width=2)
            for y in range(52,166,16):
                d.ellipse((x-10,y-6,x+sign*16+10,y+6),outline=high,width=2)
    elif scene == 13:
        for y in range(0,192,16):
            for x in range(0,192,16):
                d.rectangle((x,y,x+14,y+14),fill=mix(low,high,rng.uniform(.1,.65)))
    elif scene == 14:
        for n in range(5):
            points=[(x,25+n*12+int(10*math.sin(x/25+n))) for x in range(192)]
            d.line(points,fill=mix(high,light,n*.09),width=5)
    else:
        for r in range(25,160,13):
            d.ellipse((96-r,96-r,96+r,96+r),outline=mid,width=2)
    # Decorations cannot cover the turtle or even approach within four pixels.
    overlay=Image.new('RGBA',(192,192)); od=ImageDraw.Draw(overlay)
    if decoration == 1:
        for x,y in [(25,23),(161,34),(18,154),(144,160),(84,27)]:
            od.line((x-3,y,x+3,y),fill=light,width=1)
            od.line((x,y-3,x,y+3),fill=light,width=1)
    elif decoration == 2:
        for x,y in [(30,34),(163,58),(39,160),(144,151)]:
            od.polygon([(x,y-6),(x+2,y-2),(x+6,y),(x+2,y+2),(x,y+6),(x-2,y+2),(x-6,y),(x-2,y-2)],fill=light)
    elif decoration == 3:
        for x,y,r in [(23,44,6),(165,81,8),(36,153,5),(149,149,9),(115,24,4)]:
            od.ellipse((x-r,y-r,x+r,y+r),outline=light,width=1)
    elif decoration == 4:
        for x,s in [(15,1),(177,-1)]:
            for y,t in [(15,1),(177,-1)]:
                od.line([(x+s*14,y),(x,y),(x,y+t*14)],fill=light,width=2)
    elif decoration == 5:
        for x,y in [(29,29),(159,29),(29,163),(159,163)]:
            od.polygon([(x,y-5),(x+5,y),(x,y+5),(x-5,y)],outline=light)
    elif decoration == 6:
        for _ in range(40):
            x,y=rng.randrange(10,182),rng.randrange(12,180)
            od.rectangle((x,y,x+2,y+2),fill=light)
    elif decoration == 7:
        for _ in range(25):
            x,y=rng.randrange(8,184),rng.randrange(15,180)
            od.ellipse((x,y,x+1,y+1),fill=light)
    overlay.putalpha(Image.composite(Image.new('L',(192,192),0),overlay.getchannel('A'),protected))
    im=Image.alpha_composite(im.convert('RGBA'),overlay).convert('RGB')
    return im


def svg_pixels(image):
    parts=['<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 192 192" shape-rendering="crispEdges">']
    for y in range(192):
        x=0
        while x<192:
            c=image.getpixel((x,y)); start=x
            while x<192 and image.getpixel((x,y))==c:
                x+=1
            parts.append('<path fill="#%02x%02x%02x" d="M%d %dh%dv1H%dz"/>'%(*c,start,y,x-start,start))
    return ''.join(parts)+'</svg>'


def main():
    out=ROOT/'release/same-turtle-v2'
    for folder in ['png','svg','original','source-palettes']:
        (out/folder).mkdir(parents=True,exist_ok=True)
    original,source,colors=extract_original()
    mask=source.getchannel('A')
    protected=mask.filter(ImageFilter.MaxFilter(9))
    source.save(out/'original/extracted-turtle.png')
    shutil.copyfile(ROOT/'TurtleLauncher/src/main/res/mipmap-xxxhdpi/ic_launcher.png',out/'original/original.png')
    rows=[]; seen=set(); samples=[]
    for pi,palette in enumerate(PALETTES):
        turtle=recolor(source,colors,palette[1])
        turtle.save(out/'source-palettes'/f'{pi:02d}-{palette[0].lower()}.png')
        assert turtle.getchannel('A').tobytes()==mask.tobytes()
        for si,scene in enumerate(SCENES):
            for di,decoration in enumerate(DECOR):
                i=pi*128+si*8+di
                stem=f'{i+1:04d}-{palette[0].lower()}-{scene.lower()}-{decoration.lower()}'
                im=scene_image(palette,si,di,i+801,protected)
                im.paste(turtle,(0,0),mask)
                # Exact pixel test, not just a silhouette check: every turtle
                # pixel equals the corresponding palette-swapped source pixel.
                for (x,y) in [(x,y) for y in range(60,132) for x in range(28,152) if mask.getpixel((x,y))]:
                    assert im.getpixel((x,y))==turtle.getpixel((x,y))[:3]
                digest=hashlib.sha256(im.tobytes()).hexdigest()
                assert digest not in seen
                seen.add(digest)
                im.resize((512,512),Image.Resampling.NEAREST).save(out/'png'/f'{stem}.png',optimize=True)
                (out/'svg'/f'{stem}.svg').write_text(svg_pixels(im))
                rows.append(dict(id=i+1,name=stem,turtle_palette=palette[0],background=scene,decoration=decoration,sha256_pixels=digest,sha256_turtle_alpha=hashlib.sha256(mask.tobytes()).hexdigest()))
                if si in [(pi*3+k*5)%16 for k in range(4)] and di==(pi+si)%8:
                    samples.append((stem,im))
        print(f'{pi+1}/16 palettes complete; original turtle shape preserved',flush=True)
    with (out/'manifest.csv').open('w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    (out/'manifest.json').write_text(json.dumps(rows,indent=2))
    cards=''.join(f'<a class="card" data-name="{r["name"]}" href="png/{r["name"]}.png"><img loading="lazy" width="160" height="160" src="png/{r["name"]}.png"><span>{r["name"]}</span></a>' for r in rows)
    (out/'index.html').write_text('''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Original Turtle / 2048 Variants</title><style>*{box-sizing:border-box}body{background:#101e25;color:#d8ebdf;margin:0;font:15px system-ui}header{padding:40px 5vw}h1{font-size:clamp(28px,5vw,52px);letter-spacing:-1px}p{color:#a6c5b9;line-height:1.8}input{background:#213a38;color:white;border:1px solid #53746b;padding:14px;border-radius:10px;width:min(500px,100%)}main{padding:0 5vw 40px;display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:18px}.card{padding:12px;background:#1c302f;border:1px solid #355047;border-radius:14px;text-decoration:none;color:#bedccc}.card img{width:100%;height:auto;border-radius:10px;image-rendering:pixelated}.card span{display:block;margin-top:10px;font-size:11px;overflow-wrap:anywhere}a{color:#b3e9cf}[hidden]{display:none!important}.original{float:right;width:96px;image-rendering:pixelated}</style><header><a href="original/original.png"><img class="original" src="original/original.png" alt="Unchanged original"></a><p>TURTLELAUNCHER / ORIGINAL SHAPE EDITION</p><h1>Same turtle. New worlds.</h1><p>2,048 variations using the actual original turtle pixels.<br>No redrawing, new anatomy, shell patterns, poses, stretching or flipping.<br>16 turtle palettes × 16 backgrounds × 8 surrounding decoration styles.<br><a href="original/original.png">Untouched original</a> · <a href="original/extracted-turtle.png">Original transparent turtle</a></p><input id="search" placeholder="Filter: original, ocean, moonrise, stars…" aria-label="Filter"><p id="count">2,048 icons</p></header><main>'''+cards+'''</main><script>const cards=[...document.querySelectorAll('.card')];document.querySelector('#search').addEventListener('input',e=>{let n=0;cards.forEach(c=>{c.hidden=!c.dataset.name.includes(e.target.value.toLowerCase());if(!c.hidden)n++});document.querySelector('#count').textContent=n+' icons'});</script></html>''')
    preview=Image.new('RGB',(1440,1690),'#101e25');d=ImageDraw.Draw(preview)
    fontpath='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
    big=ImageFont.truetype(fontpath,40); small=ImageFont.truetype(fontpath,13)
    d.text((32,22),'SAME TURTLE. NEW WORLDS.',font=big,fill='#d8ebdf')
    d.text((34,79),'2,048 icons • actual original turtle pixels • 16 palettes • no changes to shape or details',font=small,fill='#abc9bc')
    d.text((34,104),'64 samples below. Untouched original and offline gallery included in ZIP.',font=small,fill='#abc9bc')
    for n,(name,image) in enumerate(samples):
        x,y=32+(n%8)*176,146+(n//8)*190
        preview.paste(image.resize((160,160),Image.Resampling.NEAREST),(x,y))
        d.text((x,y+164),name[:20],font=small,fill='#abc9bc')
    preview.save(out/'PREVIEW.jpg',quality=94)
    (out/'README.txt').write_text('''TURTLELAUNCHER — ORIGINAL SHAPE EDITION (V2)

2,048 new variants + byte-for-byte unchanged original icon.
Open index.html after extracting to browse and filter offline.

png/: 2,048 512x512 PNGs. svg/: 2,048 matching pixel-vector SVGs.
original/original.png: unchanged repository launcher icon.
original/extracted-turtle.png: actual turtle, transparent background, no redraw.
source-palettes/: the same exact turtle in 16 color palettes.
PREVIEW.jpg: 64 samples. manifest.csv / manifest.json: complete catalog.

Every turtle uses the SAME position, shape, silhouette, pixel regions, face,
eye, shell, legs and tail as the original. No flipping, rotation, new poses,
new anatomy, drawing over the turtle, or replacement shell patterns.
Only 1:1 color palette substitutions are allowed. Palette Original keeps the
original turtle colors. Decorations stay outside a four-pixel safety margin.

16 palettes x 16 scene backgrounds x 8 decoration styles = 2,048 icons.
All 2,048 full images have unique pixel hashes. All turtle alpha hashes must
be IDENTICAL. Every turtle pixel is checked against its palette source.
This is a family of variants of ONE logo, not 2,048 different turtle shapes.
Generated procedurally with Pillow, not by an image model. Original is from
Endiq-jar/TurtleLauncher. Repository license included; no trademark rights
are granted. These are artwork PNGs, not an Android adaptive-icon installer.
''')
    shutil.copyfile(ROOT/'LICENSE',out/'LICENSE.repository')
    archive=out.parent/'TurtleLauncher-2048-same-turtle-v2.zip'
    with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6) as z:
        for path in sorted(out.rglob('*')):
            if path.is_file():z.write(path,Path('TurtleLauncher-Same-Turtle-V2')/path.relative_to(out))
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
        assert len([n for n in z.namelist() if '/png/' in n])==2048
        assert len([n for n in z.namelist() if '/svg/' in n])==2048
        assert z.read('TurtleLauncher-Same-Turtle-V2/original/original.png')==(ROOT/'TurtleLauncher/src/main/res/mipmap-xxxhdpi/ic_launcher.png').read_bytes()
    print(f'PASS: {len(seen)} unique images. All 2,048 turtles preserve original geometry and pixel details.')
    print(f'{archive}: {archive.stat().st_size/1024**2:.1f} MiB')

if __name__=='__main__':main()
