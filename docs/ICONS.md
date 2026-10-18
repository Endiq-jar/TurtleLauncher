# Icon system

Turtle Launcher's icons were unified (Oct 2026) from a mixed bag of Material
fills, one-off exports with odd viewports (`16.92`), and `strokeWidth="0"`
artefacts onto one design system. This document is the comparison behind the
choice and the regeneration recipe.

## Sources compared

| | [Tabler Icons](https://github.com/tabler/tabler-icons) | [Flowbite Icons](https://github.com/themesberg/flowbite-icons) | macOS-style sets (icon-icons.com “macos”) | [Simple Icons](https://github.com/simple-icons/simple-icons) |
|---|---|---|---|---|
| Count | 5,184 outline + 1,054 filled | 411 outline + 340 solid | varies (third-party recreations) | ~3,300 brand logos |
| Grid / style | 24px, 2px stroke, round caps/joins | 24px, 2px stroke (outline) or solid fills | squircle tiles, gradients, gloss | 24px single-path fills, official brand shapes |
| Design language | Consistent, friendly, slightly rounded - closest to the launcher's rounded cards/chips | Same Feather/Lucide lineage, marginally plainer glyphs | App-icon tiles, not UI glyphs; wrong abstraction level for rows/menus | Logos only, no UI metaphors |
| Aesthetics in-app | Reads well at 16-24dp next to text; one stroke weight everywhere | Equally clean; a few glyphs (download, folder, refresh) are simpler single paths | Would clash with flat Material surfaces and dark theme | Perfect for Discord/GitHub/platform marks |
| Optimisation | Path-only SVGs → lossless VectorDrawable; stroke rendering is GPU-cheap; tintable | Path-only; winning icons here have the fewest pathData bytes | Raster-style detail would need WebP/PNG per density - heaviest option | One path per logo, smallest possible |
| Licence | MIT | MIT | Mixed/unclear per icon; Apple artwork is trademarked - **not** bundled | CC0 |

**Decision:** Tabler as the primary set (79 icons), Flowbite where its glyph
is the simpler single path (5: download, eye, folder, refresh, search),
Simple Icons for brand logos (Discord replacement, GitHub, CurseForge,
OpenJDK). The macOS contribution is the *treatment*, not the artwork:
logo-style icons sit in the existing squircle chip
(`background_icon_chip`), which is the macOS visual idea without shipping
Apple-derived art. Four custom marks stay untouched because no library has
them: `ic_launcher_background`, `ic_launcher_monochrome` (adaptive icon),
`ic_neoforge`, `ic_optifine`.

**Why not literally all ~7,000 library icons?** 6,238 Tabler + 751 Flowbite
files would add minutes to every aapt run and megabytes to the APK for icons
nobody ever sees (release resource-shrinking would strip them back out).
Instead, *every icon the launcher renders* comes from these sets - adding a
new one later is one line in `tools/import_icons.py`.

Size honesty: stroke vectors repeat stroke attributes per path, so the set
grew from 89 icons / 67,586 B to 92 icons / 115,189 B of XML. In the packaged
APK this is compiled binary XML inside a zip (roughly +15 KB), in exchange
for one stroke weight, one 24dp grid, and perfect scaling at all densities.

## File-type icons (pastel green)

The file browser uses its own 16-icon family (`ic_filetype_*`), tinted
`@color/file_icon_tint` (Turtle's pastel-green `palette_primary`, #C7E8B0)
instead of the off-white theme tint, so file rows read as one soft-green set:
folder, file, jar, zip, JSON, code, text, image, audio, video, data
(dat/nbt/mca), config, APK, PDF, font and 3D. The metaphors follow the
popular filled-line file sets (Octicons/Bootstrap-style file, code, zip,
media, image, pdf, music...); the glyphs are the equivalent MIT Tabler and
Flowbite icons, chosen for Minecraft's actual file zoo (`.jar` mods,
`.mrpack`, `pack.mcmeta`, shader sources, `level.dat`, region files).

`FileTypeIcons.iconResFor(file)` resolves by extension (disabled mods keep
their jar identity, compound `.tar.gz` counts as zip). Rows set them via
`setImageResource`, so every type also gets its own animation from
`AnimatedIcon.styleFor`: folders bounce, zips pop, JSON scans, audio
wobbles, images blink, 3D files spin like a turntable, APKs tada...

## Animation

Every icon and logo animates **only on interaction** - see
`utils/anim/AnimatedIcon.kt` (vectors) and `AnimatedLogo.kt` (WebP logos):

- **Hover** (mouse/stylus/trackpad over the icon or its row/card) → loops
- **Press** (touch hold) → loops while held; releasing finishes the sweep
- **Click/tap** → exactly one full play-through; focus loops like hover

Styles are per-icon, not one-size-fits-all (19 styles): refresh/reset spin,
favourites heartbeat, downloads drop, eyes blink, warnings swing, deletes
shake, search scans, gears rock, shares launch, files lift, globes flip,
the rest get a stable per-name pick. Layouts opt in via
`ui.view.AnimatedIconView` / `AnimatedIconButton` - run
`tools/apply_animated_icons.py` after adding layouts. Everything obeys the
global animation toggle / reduced-motion and stops off-screen.

## Regenerating

```bash
git clone --depth 1 --filter=blob:none --sparse https://github.com/tabler/tabler-icons
git -C tabler-icons sparse-checkout set icons/outline icons/filled
git clone --depth 1 --filter=blob:none --sparse https://github.com/themesberg/flowbite-icons
git -C flowbite-icons sparse-checkout set src
# brand SVGs (discord/github/curseforge/openjdk) into a flat dir, e.g. from
# the simple-icons repo icons/ folder
python3 tools/import_icons.py --tabler tabler-icons --flowbite flowbite-icons --simple simple-svgs
python3 tools/apply_animated_icons.py
python3 tests/check_icons.py
```

## Licences

- Tabler Icons - MIT © Paweł Kuna / Tabler. https://github.com/tabler/tabler-icons
- Flowbite Icons - MIT © Themesberg. https://github.com/themesberg/flowbite-icons
- Simple Icons - CC0 1.0. Brand names/logos remain trademarks of their owners.
  https://github.com/simple-icons/simple-icons

Each converted file in `res/drawable` carries its source in an XML comment.
