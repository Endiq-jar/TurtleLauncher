#!/usr/bin/env python3
"""Check the universal release APK's native ABIs and Java-8 guest bootstrap."""
import hashlib
import io
import re
import struct
import sys
import zipfile
from pathlib import Path

abis = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86": (1, 3), "x86_64": (2, 62)}
# Every ABI build of the Horizon engine the launcher carries (see bundleHorizonEngine), with a
# size floor so a truncated download cannot pass as a complete engine.
horizon_abis = {
    "arm64-v8a": 28 * 1024 * 1024,
    "armeabi-v7a": 24 * 1024 * 1024,
    "x86": 28 * 1024 * 1024,
    "x86_64": 30 * 1024 * 1024,
}
libraries = (
    "libdiscord_partner_sdk.so", "libturtlediscordrpc.so", "libc++_shared.so",
    "libturtlefiledialogs.so", "liblwjgl_tinyfd.so", "liblwjgl_nfd.so",
    "liblwjgl_nfd_portal.so", "libSimpleFPEWrapper.so", "libmobileglues.so",
)
paths = [Path(p) for p in sys.argv[1:]] or list(
    Path("TurtleLauncher/build/outputs/apk/release").glob("*.apk")
)
if not paths:
    sys.exit("No release APKs found")
for path in paths:
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        for abi, (elf_class, machine) in abis.items():
            for library in libraries:
                name = f"lib/{abi}/{library}"
                assert names.count(name) == 1, f"{path}: missing/duplicate {name}"
                with apk.open(name) as stream:
                    header = stream.read(20)
                assert header[:4] == b"\x7fELF", f"{name}: not ELF"
                assert header[4] == elf_class and header[5] == 1, f"{name}: wrong ELF class/endianness"
                assert struct.unpack_from("<H", header, 18)[0] == machine, f"{name}: wrong CPU ABI"
        notices = apk.read("assets/notices/discord/License-Notices.txt")
        assert hashlib.sha256(notices).hexdigest() == "e8afa66340c225431e69768543cc34a7240f3494a9d759189fe118620ea8eebf", "Missing/changed SDK notices"
        guest = "assets/components/lwjgl3/turtle-filedialogs.jar"
        with zipfile.ZipFile(io.BytesIO(apk.read(guest))) as jar:
            bootstrap = jar.read("com/endiq/turtlelauncher/bridge/GameFileDialogs.class")
            assert bootstrap[:4] == b"\xca\xfe\xba\xbe", "Invalid guest class"
            assert struct.unpack_from(">H", bootstrap, 6)[0] <= 52, "Guest bootstrap requires Java newer than 8"
        # The Horizon engine ships inside this APK for every ABI; choosing it in Phone settings
        # must not require a download. A build whose bundling step could not fetch an engine fails
        # here, loudly, instead of shipping a launcher that silently falls back to the feed.
        version = apk.read("assets/horizon/version").decode().strip()
        assert re.fullmatch(r"\d+\.\d+\.\d+", version), f"Bundled Horizon engine version looks wrong ({version!r})"
        for abi, minimum in horizon_abis.items():
            engine = f"assets/horizon/horizon-engine-{abi}.apk"
            assert engine in names, f"The release APK must ship the built-in Horizon engine for {abi}"
            engine_size = apk.getinfo(engine).file_size
            assert engine_size >= minimum, (
                f"Bundled Horizon engine for {abi} looks truncated ({engine_size} of at least {minimum} bytes)"
            )
            with zipfile.ZipFile(io.BytesIO(apk.read(engine))) as engine_apk:
                assert engine_apk.read("AndroidManifest.xml")[:4] == b"\x03\x00\x08\x00", \
                    f"Bundled Horizon engine for {abi} is not an Android APK"
        overlay = "assets/components/lwjgl-performance/turtle-performance.jar"
        with zipfile.ZipFile(io.BytesIO(apk.read(overlay))) as jar:
            glfw = jar.read("org/lwjgl/glfw/GLFW.class")
            assert struct.unpack_from(">H", glfw, 6)[0] <= 52, "Idle overlay needs Java newer than 8"
            assert apk.read("assets/components/lwjgl-performance/version").decode().strip() == hashlib.sha256(glfw).hexdigest()
    print(f"APK packaging passed: {path.name}; all four ABIs, SDK/shared STL, renderers, dialogs, Java-8 bootstrap, bundled Horizon engine for every ABI")
