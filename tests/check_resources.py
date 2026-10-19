#!/usr/bin/env python3
"""Catch broken resource references before an expensive Android build.

Three checks, all local and offline:

1. `R.string.x` in Kotlin/Java resolves to a string this module defines.
2. `@type/name` in res XML and the manifest resolves to something this module
   defines, or is on the documented allowlist of library-provided resources.
3. `R.type.name` in Kotlin/Java likewise.

aapt2 aborts the whole link on the first malformed *values* entry, so a single
typo hides every dangling reference behind it and the real damage only shows up
minutes later in Gradle - or not at all until the next build. Checking here turns
that into a failure in seconds. This is the regression guard for the class of
breakage where a palette/style rewrite deletes tokens that drawables, layouts,
the manifest and code still point at.
"""
import re
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "TurtleLauncher/src/main"
res = root / "res"

# Types whose references legitimately point outside this module. Framework and
# AndroidX/Material resources are merged into the app's R class at build time, so
# they cannot be enumerated from the source tree - hence an explicit allowlist
# rather than a lookup. Keep it narrow: every entry is a reference this checker
# will never validate.
EXTERNAL = {
    # Framework attributes and anything the theme/library declares
    # (appcompat's colorAccent, DslTabLayout's tab_indicator_*, ...).
    "attr": lambda name: True,
    # com.github.PojavLauncherTeam:portrait-sdp / portrait-ssp generate the whole
    # `_4sdp` / `_11ssp` scale (including the `_minus4sdp` variant).
    "dimen": re.compile(r"_-?\w*s[sd]p").fullmatch,
    # AppCompat / Material styles, and the Material3 shape system. Deliberately *not*
    # PreferenceThemeOverlay*: androidx.preference is not a dependency of this module, so
    # referencing its styles/attrs is an aapt2 link error, not an external resource.
    "style": lambda name: name.startswith((
        "Animation.AppCompat.", "Base.", "MaterialThemeOverlay.", "Platform.",
        "ShapeAppearance.Material3.",
        "ShapeAppearance.MaterialComponents.", "TextAppearance.AppCompat.",
        "TextAppearance.Material3.", "TextAppearance.MaterialComponents.",
        "Theme.AppCompat.", "Theme.Material3.", "Theme.MaterialComponents.",
        "ThemeOverlay.AppCompat.", "ThemeOverlay.Material3.",
        "ThemeOverlay.MaterialComponents.", "Widget.AppCompat.", "Widget.Design.",
        "Widget.Material3.", "Widget.MaterialComponents.",
    )),
    # android.R.layout list/spinner item layouts.
    "layout": lambda name: name.startswith(("simple_", "support_simple_")),
}

# Types that are not worth checking here: ids are declared inline with `@+id/` and
# also come from every library layout, and styleable/attr indices are generated.
SKIP_REF_TYPES = {"styleable", "id"}


def defined_resources():
    """Map of resource type -> names this module defines, plus implied style parents.

    The second return value lists `(style, implied_parent, path)` for every dotted style that
    does not set `parent=` explicitly: aapt2 inherits those from the name, and fails the link
    if the implied parent does not exist.
    """
    defined = defaultdict(set)
    implied_parents = []
    for path in sorted(res.rglob("*")):
        if not path.is_file():
            continue
        folder = path.parent.name.split("-")[0]
        if folder != "values":
            defined[folder].add(path.stem)
            continue
        if path.suffix != ".xml":
            continue
        for item in ET.parse(path).getroot():
            name = item.attrib.get("name")
            if item.tag == "declare-styleable":
                defined["styleable"].add(name)
                defined["attr"].update(
                    attr.attrib["name"] for attr in item if attr.attrib.get("name")
                )
                continue
            if not name:
                continue
            kind = item.tag
            if kind == "item":
                kind = item.attrib.get("type", "item")
            elif kind in ("string-array", "integer-array", "array"):
                kind = "array"
            defined[kind].add(name)
            if kind == "style" and "." in name and "parent" not in item.attrib:
                implied_parents.append((name, name.rsplit(".", 1)[0], path))
    return defined, implied_parents


def is_external(kind, name):
    check = EXTERNAL.get(kind)
    return bool(check and check(name))


def check_xml(defined):
    """Dangling `@type/name` references in res XML and the manifest."""
    reference = re.compile(r"@(?!\+)([\w-]+)/([\w.\-]+)")
    files = [root / "AndroidManifest.xml"] + sorted(res.rglob("*.xml"))
    missing = []
    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        for kind, name in set(reference.findall(text)):
            if kind in SKIP_REF_TYPES or name.startswith("android:"):
                continue
            if "+" in name:  # `@+id/...` declarations are handled above
                continue
            if name in defined.get(kind, ()) or is_external(kind, name):
                continue
            missing.append(f"{path.relative_to(root)}: missing {kind} {name}")
    return missing


def check_code(defined):
    """Dangling `R.type.name` references in Kotlin/Java sources."""
    reference = re.compile(r"(?<!android\.)(?<![\w.])R\.(\w+)\.(\w+)")
    missing = []
    for path in sorted((root / "java").rglob("*")):
        if path.suffix not in (".kt", ".java"):
            continue
        text = path.read_text(encoding="utf-8", errors="replace")
        for kind, name in set(reference.findall(text)):
            if kind in SKIP_REF_TYPES:
                continue
            # Style names lose their dots in the R class.
            candidates = {name, name.replace("_", ".")}
            if candidates & defined.get(kind, ()) or is_external(kind, name):
                continue
            missing.append(f"{path.relative_to(root)}: missing {kind} {name}")
    return missing


def main():
    defined, implied_parents = defined_resources()
    missing = []

    strings = defined.get("string", set())
    for path in sorted((root / "java").rglob("*")):
        if path.suffix not in (".kt", ".java"):
            continue
        for name in set(re.findall(r"(?<!android\.)\bR\.string\.(\w+)", path.read_text(encoding="utf-8", errors="replace"))) - strings:
            missing.append(f"{path.relative_to(root)}: missing string {name}")

    missing += check_xml(defined)
    missing += check_code(defined)

    for style, parent, path in implied_parents:
        if parent not in defined.get("style", ()) and not is_external("style", parent):
            missing.append(
                f"{path.relative_to(root)}: style {style} inherits from missing style {parent}"
            )

    if missing:
        sys.exit("\n".join(sorted(set(missing))))
    print("Resource references passed (strings, @type/name in XML, R.type.name in code)")


if __name__ == "__main__":
    main()
