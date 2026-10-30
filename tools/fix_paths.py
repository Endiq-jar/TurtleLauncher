#!/usr/bin/env python3
"""Normalize SVG path data in Android VectorDrawable XML files.

Android's native PathParser (especially on Android 11-13) uses stricter
tokenization than web SVG renderers. Compact upstream SVG path strings
(e.g. from Simple Icons) often contain patterns like ``00-`` (two arc flags
running directly into a signed number) or ``.123.456`` (a leading-dot
fractional immediately after another number's fractional digits) that cause
`IllegalArgumentException: a needs to be followed by a multiple of 7 floats`.

This script rewrites every drawable XML in the project with fully-tokenized
path data (single space between every command/number/flag), which both
Android and web renderers accept. It uses a command-aware tokenizer that
respects the SVG BNF, so arc flags (single 0/1 characters) are always
emitted as their own token rather than being greedily merged into adjacent
digits.
"""
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DRAWABLE = REPO / "TurtleLauncher/src/main/res/drawable"

PATH_DATA_RE = re.compile(r'android:pathData="([^"]*)"')


def _read_number(d: str, i: int):
    """Read one SVG number starting at index i. Return (value_string, new_i)."""
    n = len(d)
    start = i
    # sign
    if i < n and d[i] in "+-":
        i += 1
    # integer part (digits)
    while i < n and d[i].isdigit():
        i += 1
    # fractional part
    if i < n and d[i] == ".":
        i += 1
        while i < n and d[i].isdigit():
            i += 1
    # exponent
    if i < n and d[i] in "eE":
        i += 1
        if i < n and d[i] in "+-":
            i += 1
        while i < n and d[i].isdigit():
            i += 1
    if i == start:
        return None, i
    return d[start:i], i


def _read_flag(d: str, i: int):
    """Read a single arc flag (0 or 1) at index i. Return (value, new_i)."""
    n = len(d)
    # skip wsp/comma
    while i < n and (d[i].isspace() or d[i] == ","):
        i += 1
    if i < n and d[i] in "01":
        return d[i], i + 1
    # Fallback: try reading a number (0 or 1 can appear as " 0 " or " 1 ")
    val, ni = _read_number(d, i)
    return val, ni


def _skip_wsp(d: str, i: int) -> int:
    n = len(d)
    while i < n and (d[i].isspace() or d[i] == ","):
        i += 1
    return i


def _read_coordinate(d: str, i: int):
    """Read one coordinate (float) honouring the SVG rule that after a
    separator a number may start with '.', and after a digit or dot a new
    sign starts a new number."""
    i = _skip_wsp(d, i)
    return _read_number(d, i)


def tokenize_path(d: str):
    """Yield tokens (commands and numbers) from an SVG path `d` string.

    Uses the SVG 1.1 BNF grammar so arc flags are emitted as single
    '0'/'1' tokens rather than being greedily merged with following digits.
    """
    i = 0
    n = len(d)
    # Argument counts per drawing command (repeating while same command
    # letter is implied after implicit-repeat).  None means the command
    # takes no args (z/Z).
    # Each entry is (n_numeric, n_flag_pairs) per sub-segment; flags are
    # always 0/1 single characters.
    ARG_FORMATS = {
        # moveto: 2 coords (repeats as lineto pairs after first)
        "M": ("line",), "m": ("line",),
        "L": ("line",), "l": ("line",),
        "H": ("scalar",), "h": ("scalar",),
        "V": ("scalar",), "v": ("scalar",),
        "C": ("cubic",), "c": ("cubic",),
        "S": ("quad",), "s": ("quad",),
        "Q": ("quad",), "q": ("quad",),
        "T": ("line",), "t": ("line",),
        "A": ("arc",), "a": ("arc",),
        "Z": (None,), "z": (None,),
    }

    def expect(fmt):
        nonlocal i
        if fmt is None:
            return
        if fmt == "scalar":
            val, i = _read_coordinate(d, i)
            if val is not None:
                yield val
            return
        if fmt == "line":
            for _ in range(2):
                val, i = _read_coordinate(d, i)
                if val is not None:
                    yield val
            return
        if fmt == "cubic":
            for _ in range(6):
                val, i = _read_coordinate(d, i)
                if val is not None:
                    yield val
            return
        if fmt == "quad":
            for _ in range(4):
                val, i = _read_coordinate(d, i)
                if val is not None:
                    yield val
            return
        if fmt == "arc":
            # rx ry x-axis-rotation large-arc-flag sweep-flag x y
            for _ in range(3):
                val, i = _read_coordinate(d, i)
                if val is not None:
                    yield val
            for _ in range(2):
                val, i = _read_flag(d, i)
                if val is not None:
                    yield val
            for _ in range(2):
                val, i = _read_coordinate(d, i)
                if val is not None:
                    yield val
            return
        raise ValueError(f"unknown fmt {fmt!r}")

    cmds = set("MmZzLlHhVvCcSsQqTtAa")
    last_cmd = None
    last_fmt = None
    while i < n:
        i = _skip_wsp(d, i)
        if i >= n:
            break
        ch = d[i]
        if ch in cmds:
            yield ch
            last_cmd = ch
            last_fmt = ARG_FORMATS[ch][0]
            i += 1
            # Implicit-repeat: after reading arguments, if more numbers
            # follow they're arguments of the same command.  We handle
            # this by looping arg reads until we hit the next command.
            for tok in expect(last_fmt):
                yield tok
        else:
            # Implicit repeat of last command.
            if last_fmt is None:
                # Z has no args; nothing to do but advance past noise.
                i += 1
                continue
            for tok in expect(last_fmt):
                yield tok


def normalize_path(d: str) -> str:
    return " ".join(tokenize_path(d))


def fix_file(path: Path) -> bool:
    text = path.read_text()

    def repl(m):
        return f'android:pathData="{normalize_path(m.group(1))}"'

    new_text = PATH_DATA_RE.sub(repl, text)
    if new_text != text:
        path.write_text(new_text)
        return True
    return False


def main(argv=None):
    roots = argv[1:] if argv and len(argv) > 1 else [DRAWABLE]
    total = 0
    changed = 0
    for root in roots:
        # resolve() so xml.relative_to(REPO) below also works when the caller
        # passes a relative root (REPO is absolute).
        root = Path(root).resolve()
        for xml in sorted(root.glob("*.xml")):
            total += 1
            if fix_file(xml):
                changed += 1
                print(f"  normalized {xml.relative_to(REPO)}")
    print(f"Checked {total} drawable XMLs, normalized {changed}.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
