#!/usr/bin/env python3
"""Catch missing application string references before an expensive Android build."""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(__file__).resolve().parents[1] / "TurtleLauncher/src/main"
strings = set()
for path in (root / "res").rglob("*.xml"):
    resource = ET.parse(path).getroot()
    strings.update(item.attrib.get("name") for item in resource if item.tag == "string")
missing = []
for path in (root / "java").rglob("*"):
    if path.suffix not in (".kt", ".java"):
        continue
    for name in set(re.findall(r"(?<!android\.)\bR\.string\.(\w+)", path.read_text())) - strings:
        missing.append(f"{path.relative_to(root)}: missing string {name}")
if missing:
    sys.exit("\n".join(sorted(missing)))
print("Application string references and resource XML passed")
