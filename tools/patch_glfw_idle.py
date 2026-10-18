#!/usr/bin/env python3
"""Make a tiny GLFW idle-wait overlay, retaining every other bundled compatibility method.
No dependency, no edits to the original fat jar. Java 8 straight-line bytecode only.
Usage: patch_glfw_idle.py INPUT_JAR OUTPUT_COMPONENT_DIRECTORY
"""
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

U2 = lambda n: struct.pack('>H', n)
U4 = lambda n: struct.pack('>I', n)
CLASS = 'org/lwjgl/glfw/GLFW'
FIELD = '_turtleEventWaiter'


def patch(data):
    assert data[:4] == b'\xca\xfe\xba\xbe', 'Not a class file'
    cp_count = struct.unpack_from('>H', data, 8)[0]
    strings = {}
    pos = 10
    index = 1
    while index < cp_count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            size = struct.unpack_from('>H', data, pos)[0]
            strings[index] = data[pos + 2:pos + 2 + size].decode('utf-8', errors='replace')
            pos += 2 + size
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        else:
            raise ValueError(f'Unknown constant pool tag {tag}')
        index += 1
    cp_end = pos
    extra = bytearray()
    count = cp_count

    def entry(tag, payload):
        nonlocal count
        result = count
        count += 2 if tag in (5, 6) else 1
        extra.extend(bytes([tag]) + payload)
        return result

    def utf(value):
        encoded = value.encode('utf-8')
        return entry(1, U2(len(encoded)) + encoded)

    def cls(value):
        return entry(7, U2(utf(value)))

    def ref(tag, owner, name, descriptor):
        name_type = entry(12, U2(utf(name)) + U2(utf(descriptor)))
        return entry(tag, U2(cls(owner)) + U2(name_type))

    current = ref(10, 'java/lang/Thread', 'currentThread', '()Ljava/lang/Thread;')
    waiter = ref(9, CLASS, FIELD, 'Ljava/lang/Thread;')
    park = ref(10, 'java/util/concurrent/locks/LockSupport', 'parkNanos', '(J)V')
    unpark = ref(10, 'java/util/concurrent/locks/LockSupport', 'unpark', '(Ljava/lang/Thread;)V')
    poll = ref(10, CLASS, 'glfwPollEvents', '()V')
    minimum = ref(10, 'java/lang/Math', 'min', '(DD)D')
    eight_ms = entry(5, struct.pack('>q', 8_000_000))
    timeout_cap = entry(6, struct.pack('>d', .008))
    nanos = entry(6, struct.pack('>d', 1_000_000_000.0))
    code_name = utf('Code')
    field_name = utf(FIELD)
    field_type = utf('Ljava/lang/Thread;')
    op = lambda opcode, value: bytes([opcode]) + U2(value)
    # Publish the waiting thread, then park. Native input is checked at least every
    # 8 ms because the legacy native queue has no notification primitive. Empty
    # events can wake immediately via unpark, including a pre-park permit.
    prefix = op(0xb8, current) + op(0xb3, waiter)
    suffix = op(0xb8, poll) + b'\xb1'
    bodies = {
        ('glfwWaitEvents', '()V'): (2, 0, prefix + op(0x14, eight_ms) + op(0xb8, park) + suffix),
        ('glfwWaitEventsTimeout', '(D)V'): (4, 2, prefix + b'\x26' + op(0x14, timeout_cap) + op(0xb8, minimum)
                                           + op(0x14, nanos) + b'\x6b\x8f' + op(0xb8, park) + suffix),
        ('glfwPostEmptyEvent', '()V'): (1, 0, op(0xb2, waiter) + op(0xb8, unpark) + b'\xb1'),
    }

    def read_u2():
        nonlocal pos
        result = struct.unpack_from('>H', data, pos)[0]
        pos += 2
        return result

    def attributes():
        nonlocal pos
        result = []
        for _ in range(read_u2()):
            start = pos
            name = read_u2()
            size = struct.unpack_from('>I', data, pos)[0]
            pos += 4 + size
            result.append((strings[name], data[start:pos]))
        return result

    header_start = pos
    pos += 6  # access, this_class, superclass
    interfaces = read_u2()
    pos += interfaces * 2
    header = data[header_start:pos]
    fields = []
    field_present = False
    for _ in range(read_u2()):
        start = pos
        read_u2()
        name = read_u2()
        descriptor = read_u2()
        attributes()
        if strings[name] == FIELD:
            assert strings[descriptor] == 'Ljava/lang/Thread;'
            field_present = True
        fields.append(data[start:pos])
    if not field_present:
        fields.append(U2(0x004a) + U2(field_name) + U2(field_type) + U2(0))  # private static volatile
    methods = []
    replaced = set()
    for _ in range(read_u2()):
        start = pos
        access, name, descriptor = read_u2(), read_u2(), read_u2()
        attrs = attributes()
        key = strings[name], strings[descriptor]
        if key not in bodies:
            methods.append(data[start:pos])
            continue
        assert access & 0x0008, 'Expected a static method'
        old_code = next(raw[6:] for kind, raw in attrs if kind == 'Code')
        old_length = struct.unpack_from('>I', old_code, 4)[0]
        assert old_code[8:8 + old_length] == b'\xb1', 'Bundled wait changed; inspect instead of replacing it'
        stack, locals_, instructions = bodies[key]
        code = U2(stack) + U2(locals_) + U4(len(instructions)) + instructions + U2(0) + U2(0)
        new_attrs = [raw for kind, raw in attrs if kind != 'Code']
        new_attrs.append(U2(code_name) + U4(len(code)) + code)
        methods.append(U2(access) + U2(name) + U2(descriptor) + U2(len(new_attrs)) + b''.join(new_attrs))
        replaced.add(key)
    assert replaced == set(bodies), f'Missing GLFW wait methods: {set(bodies) - replaced}'
    assert count < 65535
    return (data[:8] + U2(count) + data[10:cp_end] + extra + header + U2(len(fields)) + b''.join(fields)
            + U2(len(methods)) + b''.join(methods) + data[pos:])


def build(source, destination):
    with zipfile.ZipFile(source) as archive:
        assert archive.namelist().count(CLASS + '.class') == 1, 'Missing/duplicate bundled GLFW class'
        original = archive.read(CLASS + '.class')
    updated = patch(original)
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination / 'turtle-performance.jar', 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        info = zipfile.ZipInfo(CLASS + '.class', (2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, updated)
    (destination / 'version').write_text(hashlib.sha256(updated).hexdigest() + '\n')
    print('Built GLFW idle overlay; original jar and all non-wait methods preserved.')


if __name__ == '__main__':
    build(Path(sys.argv[1]), Path(sys.argv[2]))
