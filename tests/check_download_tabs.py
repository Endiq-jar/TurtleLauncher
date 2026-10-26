#!/usr/bin/env python3
"""Keep the Download screen's tabs, pages and classify values in one order.

`fragment_download.xml` contributes one tab per entry of
`DownloadFragment`'s page list, and both are indexed by position. They used to
be maintained separately and drifted: the layout put *Shader Pack* fourth and
*World* fifth, while `Classify` numbers World 3 and Shader Pack 4. The result
was that "Download World" opened the shader page and "Download Shader Pack"
opened the world page (which errors out on Modrinth) - two features that looked
broken.

This check asserts, offline and without the Android SDK:

  * the tab order in the layout equals `DownloadPages.entries`;
  * each `Classify` value's numeric `type` equals the index of its page;
  * `fragment_download.xml` has one tab container per page.
"""
import re
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
main = repo / "TurtleLauncher/src/main"
layout = main / "res/layout/fragment_download.xml"
pages_kt = main / "java/com/endiq/turtlelauncher/ui/fragment/DownloadPages.kt"
classify_kt = main / "java/com/endiq/turtlelauncher/feature/download/enums/Classify.kt"

# --- tab order in the layout -------------------------------------------------
root = ET.parse(layout).getroot()
android_ns = "{http://schemas.android.com/apk/res/android}"
tab_bar = next(
    node for node in root.iter() if node.tag == "com.angcyo.tablayout.DslTabLayout"
)
tab_ids = []
for child in tab_bar:
    ids = [
        grandchild.get(android_ns + "id")
        for grandchild in child.iter()
        if grandchild.get(android_ns + "id", "").startswith("@+id/")
    ]
    assert len(ids) == 1, f"tab entry without exactly one icon id: {ids}"
    tab_ids.append(ids[0].removeprefix("@+id/"))

EXPECTED_ICONS = [
    "mod_image",
    "modpack_image",
    "resource_pack_image",
    "world_image",
    "shader_pack_image",
]
assert tab_ids == EXPECTED_ICONS, (
    "fragment_download.xml tab order drifted from DownloadPages.entries:\n"
    f"  layout:   {tab_ids}\n  expected: {EXPECTED_ICONS}"
)

# --- DownloadPages.entries order --------------------------------------------
pages_source = pages_kt.read_text()
enum_body = pages_source.split("enum class Page(", 1)[1].split(")", 1)[1]
entries = re.findall(r'^\s{8}([A-Z_]+)\(Classify\.([A-Z_]+), "([a-z_]+)"\)', enum_body, re.M)
assert [name for name, _, _ in entries] == [
    "MODS",
    "MODPACKS",
    "RESOURCE_PACKS",
    "WORLDS",
    "SHADER_PACKS",
], entries
assert [classify for _, classify, _ in entries] == [
    "MOD",
    "MODPACK",
    "RESOURCE_PACK",
    "WORLD",
    "SHADER_PACK",
], entries

# --- Classify numeric values -------------------------------------------------
classify_source = classify_kt.read_text()
classify_values = dict(
    re.findall(r"^\s*([A-Z_]+)\((-?\d+)\)", classify_source, re.M)
)
assert classify_values.pop("ALL") == "-1", classify_values
# the enum's tab ids must match the layout order too
assert [tab_id for _, _, tab_id in entries] == EXPECTED_ICONS, (
    "DownloadPages.Page.tabId disagrees with fragment_download.xml:\n"
    f"  enum:     {[tab_id for _, _, tab_id in entries]}\n  layout:   {EXPECTED_ICONS}"
)

for index, (name, classify, tab_id) in enumerate(entries):
    assert classify_values[classify] == str(index), (
        f"Classify.{classify} is {classify_values[classify]} but DownloadPages "
        f"puts it at index {index}; PageSwapEvent filters on classify.type"
    )

# --- every page has a fragment ----------------------------------------------
fragment_source = (main / "java/com/endiq/turtlelauncher/ui/fragment/DownloadFragment.kt").read_text()
assert "DownloadPages.classifyAt(position)" in fragment_source, (
    "DownloadFragment must build its pages from DownloadPages, not a hardcoded when(position)"
)

print("Download tab order, page order and Classify values all agree")
