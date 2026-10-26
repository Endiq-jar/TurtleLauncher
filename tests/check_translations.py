#!/usr/bin/env python3
"""Validate that the launcher's translations are usable and complete.

The launcher-language setting used to be a no-op: it offered ~130 Minecraft
language codes while the app shipped no `values-*` folder at all, so picking any
language changed the Locale and nothing else. This check keeps the fix honest:

  * every `res/values-<locale>/strings.xml` parses and has no duplicate keys;
  * every translated key also exists in `res/values/` (typos would otherwise be
    silently ignored by Android and never show up);
  * format placeholders (`%s`, `%d`, `%1$s`, ...) match the English source, so a
    translation can never crash `getString(id, args)`;
  * every locale has a `values-*` folder for its `launcher_language_values`
    entry and vice versa - the picker lists exactly what works;
  * the Chinese/Japanese/Korean files actually contain CJK text (catching a file
    that was copied from English and never translated).
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

repo = Path(__file__).resolve().parents[1]
res = repo / "TurtleLauncher/src/main/res"

PLACEHOLDER = re.compile(r"%(?:\d+\$)?[a-zA-Z]")

# launcher_language_values entry -> res/ folder suffix
LOCALE_FOLDERS = {
    "en": None,                 # English is the default res/values/
    "zh_cn": "zh-rCN",
    "zh_tw": "zh-rTW",
    "ja_jp": "ja",
    "ru_ru": "ru",
    "es_es": "es",
    "pt_br": "pt-rBR",
}

CJK_REQUIRED = {"zh-rCN", "zh-rTW", "ja"}


def load_strings(path: Path) -> dict:
    root = ET.parse(path).getroot()
    out = {}
    for element in root.iter("string"):
        name = element.get("name")
        if not name:
            continue
        if name in out:
            raise AssertionError(f"{path}: duplicate string name '{name}'")
        out[name] = "".join(element.itertext())
    return out


errors = []

values_dir = res / "values"
english = {}
for path in sorted(values_dir.glob("*.xml")):
    try:
        english.update(load_strings(path))
    except ET.ParseError as error:
        errors.append(f"{path.name}: XML parse error: {error}")

# --- the picker list ---------------------------------------------------------
array_source = (values_dir / "launcher_languages.xml").read_text()
values_block = array_source.split('name="launcher_language_values"', 1)[1]
picker_values = re.findall(r"<item>([^<]+)</item>", values_block)
names_block = array_source.split('name="launcher_language_names"', 1)[1]
picker_names = re.findall(r"<item>([^<]+)</item>", names_block.split("</string-array>", 1)[0])
if len(picker_names) != len(picker_values):
    errors.append(
        f"launcher_language_names has {len(picker_names)} entries but "
        f"launcher_language_values has {len(picker_values)}"
    )
if picker_values[:1] != ["system"]:
    errors.append("launcher_language_values must start with 'system' (follow device language)")

picker_languages = picker_values[1:]
for value in picker_languages:
    if value not in LOCALE_FOLDERS:
        errors.append(f"launcher_language_values lists '{value}' but no folder is mapped for it")
for value, folder in LOCALE_FOLDERS.items():
    if folder is not None and value not in picker_languages:
        errors.append(f"values-{folder} exists but '{value}' is missing from launcher_language_values")

existing_folders = {
    p.name.split("-", 1)[1]: p for p in res.glob("values-*") if p.name not in {"values-night"}
}
mapped_folders = {folder for folder in LOCALE_FOLDERS.values() if folder}
for folder in sorted(existing_folders):
    if folder not in mapped_folders and folder != "night":
        errors.append(f"res/values-{folder} has no entry in launcher_languages.xml")

# --- per-locale files -------------------------------------------------------
for folder in sorted(mapped_folders):
    file = res / f"values-{folder}" / "strings.xml"
    if not file.is_file():
        errors.append(f"missing {file.relative_to(repo)}")
        continue
    try:
        translated = load_strings(file)
    except ET.ParseError as error:
        errors.append(f"values-{folder}/strings.xml: XML parse error: {error}")
        continue
    if len(translated) < 100:
        errors.append(f"values-{folder}: only {len(translated)} strings translated (expected the launcher chrome)")
    for key, text in translated.items():
        if key not in english:
            errors.append(f"values-{folder}: '{key}' is not defined in res/values")
            continue
        missing = sorted(set(PLACEHOLDER.findall(english[key])) - set(PLACEHOLDER.findall(text)))
        if missing:
            errors.append(f"values-{folder}: '{key}' drops placeholder(s) {missing}")
        if not text.strip():
            errors.append(f"values-{folder}: '{key}' is empty")
    if folder in CJK_REQUIRED and not any(re.search(r"[\u4e00-\u9fff\u3040-\u30ff]", t) for t in translated.values()):
        errors.append(f"values-{folder}: no CJK text found - the file looks like untranslated English")

# Keys the chrome depends on must be translated everywhere.
CHROME_KEYS = [
    "main_launch", "main_nav_settings", "main_nav_download", "main_nav_files",
    "download_classify_world", "download_classify_shader_pack",
    "settings_row_optimization_title", "settings_row_video_title",
    "generic_confirm", "generic_return", "version_select_hint",
]
for folder in sorted(mapped_folders):
    file = res / f"values-{folder}" / "strings.xml"
    if not file.is_file():
        continue
    translated = load_strings(file)
    for key in CHROME_KEYS:
        if key not in translated:
            errors.append(f"values-{folder}: missing chrome key '{key}'")

if errors:
    print("Translation checks failed:", file=sys.stderr)
    for error in errors:
        print(f"  - {error}", file=sys.stderr)
    sys.exit(1)

print(
    f"Translations OK: {len(mapped_folders)} locales, "
    f"{len(load_strings(res / 'values-zh-rCN' / 'strings.xml'))} chrome strings each"
)
