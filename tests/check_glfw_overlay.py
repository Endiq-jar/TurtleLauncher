#!/usr/bin/env python3
"""Check the overlay against the real compatibility jar, not a reconstructed GLFW source."""
import hashlib
import importlib.util
import struct
import tempfile
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('idle_patcher', root / 'tools/patch_glfw_idle.py')
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)


def members(data):
    count = struct.unpack_from('>H', data, 8)[0]
    pos, index, names = 10, 1, {}
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, pos)[0]
            names[index] = data[pos + 2:pos + 2 + size].decode('utf-8', errors='replace')
            pos += 2 + size
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): pos += 4
        elif tag in (5, 6): pos += 8; index += 1
        elif tag in (7, 8, 16, 19, 20): pos += 2
        elif tag == 15: pos += 3
        else: raise AssertionError(tag)
        index += 1
    pool = data[10:pos]
    def u2():
        nonlocal pos
        value = struct.unpack_from('>H', data, pos)[0]; pos += 2
        return value
    def attrs():
        nonlocal pos
        values = {}
        for _ in range(u2()):
            name = names[u2()]
            size = struct.unpack_from('>I', data, pos)[0]; pos += 4
            values[name] = data[pos:pos + size]; pos += size
        return values
    pos += 6
    interfaces = u2()
    pos += interfaces * 2
    fields = {}
    for _ in range(u2()):
        access, name, desc = u2(), names[u2()], names[u2()]
        fields[(name, desc)] = (access, attrs())
    methods = {}
    for _ in range(u2()):
        access, name, desc = u2(), names[u2()], names[u2()]
        methods[(name, desc)] = (access, attrs())
    return pool, fields, methods


source = root / 'TurtleLauncher/src/main/assets/components/lwjgl3/lwjgl-glfw-classes.jar'
source_hash = hashlib.sha256(source.read_bytes()).digest()
with zipfile.ZipFile(source) as archive:
    original = archive.read('org/lwjgl/glfw/GLFW.class')
updated = patcher.patch(original)
old_pool, old_fields, old_methods = members(original)
new_pool, new_fields, new_methods = members(updated)
assert new_pool.startswith(old_pool), 'Existing constant pool changed'
assert original[:8] == updated[:8], 'Class-file version changed'
assert set(old_methods) == set(new_methods), 'Compatibility method removed/added'
for key, value in old_fields.items(): assert new_fields[key] == value, key
waits = {('glfwWaitEvents', '()V'), ('glfwWaitEventsTimeout', '(D)V'), ('glfwPostEmptyEvent', '()V')}
for key, value in old_methods.items():
    if key not in waits:
        assert new_methods[key] == value, f'Non-wait compatibility method changed: {key}'
    else:
        old_access, old_attrs = value
        new_access, new_attrs = new_methods[key]
        assert old_access == new_access
        assert {k: v for k, v in old_attrs.items() if k != 'Code'} == {k: v for k, v in new_attrs.items() if k != 'Code'}
        # Original wait methods really are no-ops; don't blindly overwrite a future implementation.
        code = old_attrs['Code']
        length = struct.unpack_from('>I', code, 4)[0]
        assert code[8:8 + length] == b'\xb1', f'Bundled {key} is no longer a no-op: inspect before patching'
        assert new_attrs['Code'] != code
with tempfile.TemporaryDirectory() as temporary:
    directory = Path(temporary)
    patcher.build(source, directory)
    with zipfile.ZipFile(directory / 'turtle-performance.jar') as overlay:
        assert overlay.namelist() == ['org/lwjgl/glfw/GLFW.class']
        assert overlay.read(overlay.namelist()[0]) == updated
    assert (directory / 'version').read_text().strip() == hashlib.sha256(updated).hexdigest()
assert hashlib.sha256(source.read_bytes()).digest() == source_hash
print(f'GLFW overlay passed: {len(old_methods)} methods retained; only three wait bodies changed; original jar untouched')
