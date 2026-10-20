# TurtleLauncher — original-shape icon pack (V2)

[Download the corrected ZIP: 2,048 variants + original (39 MB)](../downloads/TurtleLauncher-2048-same-turtle-v2.zip)

On GitHub, click **Download raw file**. Extract the archive and open
`index.html` for a searchable offline gallery. `PREVIEW.jpg` shows 64 samples.
This replaces the earlier pack that redrew the turtle.

## What stays unchanged

The actual turtle is extracted from
`TurtleLauncher/src/main/res/mipmap-xxxhdpi/ic_launcher.png`. Its eight original
palette colors identify all 5,312 turtle pixels, including outline and eye.
Every variant uses this exact pixel mask, position, shape, anatomy, face, eye,
shell details, legs and tail. There is no redrawing, rotation, flipping,
stretching, new pose, or new shell pattern. Only one-to-one palette swaps are
allowed; the **Original** palette keeps the original turtle colors unchanged.

## What varies

16 turtle palettes × 16 scene backgrounds × 8 surrounding decoration styles
= 2,048 unique full images. Decorative overlays cannot come within four pixels
of the turtle. Background scenery is drawn behind it.

- `png/`: 2,048 512 × 512 PNGs (nearest-neighbor scaling)
- `svg/`: 2,048 matching scalable pixel-vector SVGs
- `original/original.png`: byte-for-byte unchanged launcher icon
- `original/extracted-turtle.png`: original turtle on transparency
- `source-palettes/`: the identical turtle geometry in 16 palettes
- CSV/JSON catalogs, preview, offline gallery and repository license

## Reproduce

```sh
python3 -m venv /home/user/.venv
/home/user/.venv/bin/pip install Pillow
/home/user/.venv/bin/python tools/generate_turtle_icon_pack.py
```

Output: `release/TurtleLauncher-2048-same-turtle-v2.zip` (ignored generated
output). The downloadable copy is intentionally tracked under `downloads/`.
No Android app resources are modified.

The generator checks that all 2,048 full image hashes are unique, all turtle
alpha masks are **identical**, and every turtle pixel matches the appropriate
palette source. It also checks ZIP integrity, PNG/SVG counts, and original PNG
preservation. Art is procedurally composited using Pillow, not an image model.
These are variants of one logo, not 2,048 unrelated logos. PNGs have opaque
square backgrounds; this is not an Android adaptive-icon installation pack.
Original artwork and licensing come from this repository. No separate
trademark rights are granted.
