#!/usr/bin/env python3
"""Check the universal release APK's native ABIs and Java-8 guest bootstrap."""
import hashlib
import io
import struct
import sys
import zipfile
from pathlib import Path

abis = {"armeabi-v7a": (1, 40), "arm64-v8a": (2, 183), "x86": (1, 3), "x86_64": (2, 62)}
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
        # The Horizon engine ships inside this APK; choosing it in Phone settings must not require
        # a download. A build whose bundling step could not fetch the engine fails here, loudly,
        # instead of shipping a launcher that silently falls back to the release feed.
        # One engine APK per Horizon architecture, all inside this launcher: choosing the engine
        # must work without a download on arm64, armv7, x86 and x86_64 alike. A build whose bundling
        # step could not fetch one of them fails here, loudly, instead of shipping a launcher that
        # silently falls back to the release feed on that architecture.
        for engine_abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
            engine = f"assets/horizon/horizon-engine-{engine_abi}.apk"
            assert engine in names, f"The release APK must ship the built-in Horizon engine for {engine_abi}"
            engine_size = apk.getinfo(engine).file_size
            assert engine_size > 10 * 1024 * 1024, \
                f"Bundled Horizon engine for {engine_abi} looks truncated ({engine_size} bytes)"
            with zipfile.ZipFile(io.BytesIO(apk.read(engine))) as engine_apk:
                engine_names = engine_apk.namelist()
                assert engine_apk.read("AndroidManifest.xml")[:4] == b"\x03\x00\x08\x00", \
                    f"Bundled Horizon engine for {engine_abi} is not an Android APK"
                assert any(n.startswith(f"lib/{engine_abi}/") for n in engine_names), \
                    f"Bundled Horizon engine for {engine_abi} carries no {engine_abi} native code"
        overlay = "assets/components/lwjgl-performance/turtle-performance.jar"
        with zipfile.ZipFile(io.BytesIO(apk.read(overlay))) as jar:
            glfw = jar.read("org/lwjgl/glfw/GLFW.class")
            assert struct.unpack_from(">H", glfw, 6)[0] <= 52, "Idle overlay needs Java newer than 8"
            assert apk.read("assets/components/lwjgl-performance/version").decode().strip() == hashlib.sha256(glfw).hexdigest()
    print(f"APK packaging passed: {path.name}; all four ABIs, SDK/shared STL, renderers, dialogs, Java-8 bootstrap, four bundled Horizon engines")
