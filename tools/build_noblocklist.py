#!/usr/bin/env python3
"""Build assets/components/components/NoBlocklist.jar (MojoLauncher/NoBlocklist 1.0).

Why this script exists
----------------------
The launcher bundles the NoBlocklist javaagent (prevents Minecraft from loading
Mojang's blocked-servers list). Upstream ships a prebuilt
``NoBlocklist-1.0-SNAPSHOT-all.jar`` in its GitHub releases:

    https://github.com/MojoLauncher/NoBlocklist/releases/download/1.0/NoBlocklist-1.0-SNAPSHOT-all.jar

If you can download that file, you can simply drop it in as ``NoBlocklist.jar``
(same Premain-Class, same behaviour) and bump
``assets/components/components/version`` instead of running this script.

This script reproduces the same artefact from source in environments without a
JDK/Gradle/Maven (it was written for a sandbox whose network allowlist blocks
GitHub release downloads and Maven Central):

* The two agent classes from the upstream 1.0 tag (``PreMain``,
  ``BlockerTransformer`` + its two inner ASM visitors) are emitted with the
  tiny class-file assembler below, at class-file version 49 (Java 5), which
  every game runtime the launcher ships (Java 8/17/21/25) can verify.
* One deliberate deviation from upstream 1.0: ``URLMethodVisitor`` used
  ``Random.nextInt(1000, 1100)``, a Java 17+ API, so the upstream jar is a
  silent no-op on Java 8 (the ``NoSuchMethodError`` is swallowed by its own
  ``catch (Throwable)``). Minecraft < 1.17 launches on Java 8 here, so this
  build uses the equivalent ``1000 + nextInt(100)``.
* ASM (needed by the transformer at runtime) is reused from the copy already
  bundled in this repo inside ``authlib-injector.jar`` (core ASM only, with
  ``V26`` class-file support) and relocated
  ``moe/yushi/authlibinjector/internal/org/objectweb/asm`` ->
  ``git/artdeell/shasm`` - the same relocation upstream's Shadow build applies
  (``git.artdeell.shasm``), so it can never clash with a mod loader's ASM.

Usage:  python3 tools/build_noblocklist.py [--donor PATH] [--out PATH]
"""

import argparse
import hashlib
import struct
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
DEFAULT_DONOR = REPO / "TurtleLauncher/src/main/assets/components/other_login/authlib-injector.jar"
DEFAULT_OUT = REPO / "TurtleLauncher/src/main/assets/components/components/NoBlocklist.jar"

DONOR_PREFIX = b"moe/yushi/authlibinjector/internal/org/objectweb/asm"
DONOR_PREFIX_DOTTED = b"moe.yushi.authlibinjector.internal.org.objectweb.asm"
SHASM = "git/artdeell/shasm"
SHASM_B = SHASM.encode()
SHASM_DOTTED = b"git.artdeell.shasm"

AGENT_PKG = "git/artdeell/noblocklist"
PREMAIN = f"{AGENT_PKG}/PreMain"
TRANSFORMER = f"{AGENT_PKG}/BlockerTransformer"
URL_VISITOR = f"{AGENT_PKG}/BlockerTransformer$URLVisitor"
URL_METHOD_VISITOR = f"{AGENT_PKG}/BlockerTransformer$URLMethodVisitor"

ASM9 = 589824  # org.objectweb.asm.Opcodes.ASM9
COMPUTE_FRAMES = 2  # org.objectweb.asm.ClassWriter.COMPUTE_FRAMES

# --------------------------------------------------------------------------
# Minimal class-file assembler (constant pool, methods with label fixups,
# Code / Exceptions / InnerClasses / SourceFile attributes).
# --------------------------------------------------------------------------

class ConstantPool:
    def __init__(self):
        self.entries = []          # raw encoded entries (index = i + 1)
        self._cache = {}

    def _add(self, key, raw):
        if key in self._cache:
            return self._cache[key]
        self.entries.append(raw)
        idx = len(self.entries)
        self._cache[key] = idx
        return idx

    def utf8(self, text):
        raw = text.encode("utf-8")
        return self._add(("utf8", text), b"\x01" + struct.pack(">H", len(raw)) + raw)

    def integer(self, value):
        return self._add(("int", value), b"\x03" + struct.pack(">i", value))

    def class_(self, internal_name):
        return self._add(("class", internal_name),
                         b"\x07" + struct.pack(">H", self.utf8(internal_name)))

    def string(self, text):
        return self._add(("string", text),
                         b"\x08" + struct.pack(">H", self.utf8(text)))

    def name_and_type(self, name, descriptor):
        return self._add(("nat", name, descriptor),
                         b"\x0c" + struct.pack(">HH", self.utf8(name), self.utf8(descriptor)))

    def fieldref(self, owner, name, descriptor):
        return self._add(("field", owner, name, descriptor),
                         b"\x09" + struct.pack(">HH", self.class_(owner),
                                               self.name_and_type(name, descriptor)))

    def methodref(self, owner, name, descriptor):
        return self._add(("method", owner, name, descriptor),
                         b"\x0a" + struct.pack(">HH", self.class_(owner),
                                               self.name_and_type(name, descriptor)))

    def interface_methodref(self, owner, name, descriptor):
        return self._add(("imethod", owner, name, descriptor),
                         b"\x0b" + struct.pack(">HH", self.class_(owner),
                                               self.name_and_type(name, descriptor)))

    def to_bytes(self):
        return struct.pack(">H", len(self.entries) + 1) + b"".join(self.entries)


class MethodBuilder:
    """Bytecode emitter with symbolic labels (16-bit branch fixups)."""

    def __init__(self, cp, access, name, descriptor, exceptions=()):
        self.cp = cp
        self.access = access
        self.name = name
        self.descriptor = descriptor
        self.exceptions = list(exceptions)
        self.code = bytearray()
        self.labels = {}
        self.fixups = []           # (offset_of_branch_opcode, label)
        self.handlers = []         # (start_label, end_label, handler_label, catch_internal|None)
        self.max_stack = 0
        self.max_locals = 0

    # -- plumbing ----------------------------------------------------------
    def label(self, name):
        self.labels[name] = len(self.code)
        return self

    def raw(self, *bs):
        self.code.extend(bs)
        return self

    def u1(self, v):
        self.code.append(v & 0xFF)
        return self

    def u2(self, v):
        self.code.extend(struct.pack(">H", v))
        return self

    def branch(self, opcode, label):
        self.fixups.append((len(self.code), label))
        self.code.extend((opcode, 0, 0))
        return self

    def try_catch(self, start, end, handler, catch_internal):
        self.handlers.append((start, end, handler, catch_internal))
        return self

    # -- opcodes -----------------------------------------------------------
    def aconst_null(self): return self.raw(0x01)
    def iconst(self, v):
        if -1 <= v <= 5: return self.raw(0x03 + v)
        if -128 <= v <= 127: return self.raw(0x10, v & 0xFF)
        if -32768 <= v <= 32767: return self.raw(0x11, (v >> 8) & 0xFF, v & 0xFF)
        return self.ldc_int(v)
    def ldc_int(self, v):
        idx = self.cp.integer(v)
        return self.raw(0x12, idx) if idx <= 0xFF else self.raw(0x13, idx >> 8, idx & 0xFF)
    def ldc_string(self, s):
        idx = self.cp.string(s)
        return self.raw(0x12, idx) if idx <= 0xFF else self.raw(0x13, idx >> 8, idx & 0xFF)
    def ldc_class(self, internal):
        idx = self.cp.class_(internal)
        return self.raw(0x12, idx) if idx <= 0xFF else self.raw(0x13, idx >> 8, idx & 0xFF)
    def aload(self, i): return self.raw(0x2A + i) if i < 4 else self.raw(0x19, i)
    def astore(self, i): return self.raw(0x4B + i) if i < 4 else self.raw(0x3A, i)
    def dup(self): return self.raw(0x59)
    def pop(self): return self.raw(0x57)
    def iadd(self): return self.raw(0x60)
    def areturn(self): return self.raw(0xB0)
    def return_(self): return self.raw(0xB1)
    def athrow(self): return self.raw(0xBF)
    def new(self, internal): return self.raw(0xBB).u2(self.cp.class_(internal))
    def anewarray(self, internal): return self.raw(0xBD).u2(self.cp.class_(internal))
    def aastore(self): return self.raw(0x53)
    def getstatic(self, o, n, d): return self.raw(0xB2).u2(self.cp.fieldref(o, n, d))
    def getfield(self, o, n, d): return self.raw(0xB4).u2(self.cp.fieldref(o, n, d))
    def putfield(self, o, n, d): return self.raw(0xB5).u2(self.cp.fieldref(o, n, d))
    def invokevirtual(self, o, n, d): return self.raw(0xB6).u2(self.cp.methodref(o, n, d))
    def invokespecial(self, o, n, d): return self.raw(0xB7).u2(self.cp.methodref(o, n, d))
    def invokestatic(self, o, n, d): return self.raw(0xB8).u2(self.cp.methodref(o, n, d))
    def invokeinterface(self, o, n, d, count):
        return self.raw(0xB9).u2(self.cp.interface_methodref(o, n, d)).raw(count, 0)
    def ifeq(self, label): return self.branch(0x99, label)
    def ifne(self, label): return self.branch(0x9A, label)

    # -- serialisation -----------------------------------------------------
    def build(self, cp):
        code = bytearray(self.code)
        for off, label in self.fixups:
            rel = self.labels[label] - off
            assert -32768 <= rel <= 32767, f"branch to {label} out of range"
            struct.pack_into(">h", code, off + 1, rel)
        handler_bytes = b""
        for start, end, handler, catch in self.handlers:
            catch_idx = cp.class_(catch) if catch else 0
            handler_bytes += struct.pack(">HHHH", self.labels[start], self.labels[end],
                                         self.labels[handler], catch_idx)
        code_attr = (
            struct.pack(">HHI", self.max_stack, self.max_locals, len(code)) + bytes(code)
            + struct.pack(">H", len(self.handlers)) + handler_bytes
            + struct.pack(">H", 0)  # no code sub-attributes (no StackMapTable: v49)
        )
        attrs = [(cp.utf8("Code"), code_attr)]
        if self.exceptions:
            exc = struct.pack(">H", len(self.exceptions)) + b"".join(
                struct.pack(">H", cp.class_(e)) for e in self.exceptions)
            attrs.append((cp.utf8("Exceptions"), exc))
        out = struct.pack(">HHHH", self.access, cp.utf8(self.name),
                          cp.utf8(self.descriptor), len(attrs))
        for name_idx, body in attrs:
            out += struct.pack(">HI", name_idx, len(body)) + body
        return out


ACC_PUBLIC, ACC_PRIVATE, ACC_STATIC, ACC_FINAL, ACC_SUPER = 0x0001, 0x0002, 0x0008, 0x0010, 0x0020


class ClassBuilder:
    def __init__(self, access, internal, super_internal, interfaces=(),
                 source_file=None, inner_classes=()):
        self.cp = ConstantPool()
        self.access = access
        self.internal = internal
        self.super_internal = super_internal
        self.interfaces = list(interfaces)
        self.source_file = source_file
        # (inner_internal, outer_internal|None, simple_name|None, inner_access)
        self.inner_classes = list(inner_classes)
        self.fields = []   # (access, name, descriptor)
        self.methods = []  # MethodBuilder

    def field(self, access, name, descriptor):
        self.fields.append((access, name, descriptor))
        return self

    def method(self, access, name, descriptor, exceptions=()):
        m = MethodBuilder(self.cp, access, name, descriptor, exceptions)
        self.methods.append(m)
        return m

    def trivial_ctor(self):
        m = self.method(ACC_PUBLIC, "<init>", "()V")
        m.max_stack, m.max_locals = 1, 1
        m.aload(0).invokespecial(self.super_internal, "<init>", "()V").return_()
        return self

    def build(self):
        cp = self.cp
        # Methods first: building them populates the CP, which must be final
        # before we serialise it. Method bytes are collected aside.
        method_bytes = [m.build(cp) for m in self.methods]
        this_idx = cp.class_(self.internal)
        super_idx = cp.class_(self.super_internal)
        iface_idx = [cp.class_(i) for i in self.interfaces]
        field_bytes = b"".join(
            struct.pack(">HHHH", a, cp.utf8(n), cp.utf8(d), 0) for a, n, d in self.fields)
        attrs = []
        if self.source_file:
            attrs.append((cp.utf8("SourceFile"), struct.pack(">H", cp.utf8(self.source_file))))
        if self.inner_classes:
            body = struct.pack(">H", len(self.inner_classes))
            for inner, outer, simple, flags in self.inner_classes:
                body += struct.pack(">HHHH", cp.class_(inner),
                                    cp.class_(outer) if outer else 0,
                                    cp.utf8(simple) if simple else 0, flags)
            attrs.append((cp.utf8("InnerClasses"), body))
        out = b"\xca\xfe\xba\xbe" + struct.pack(">HH", 0, 49)  # Java 5: no StackMapTable needed
        out += cp.to_bytes()
        out += struct.pack(">HHH", self.access, this_idx, super_idx)
        out += struct.pack(">H", len(iface_idx)) + b"".join(struct.pack(">H", i) for i in iface_idx)
        out += struct.pack(">H", len(self.fields)) + field_bytes
        out += struct.pack(">H", len(method_bytes)) + b"".join(method_bytes)
        out += struct.pack(">H", len(attrs))
        for name_idx, body in attrs:
            out += struct.pack(">HI", name_idx, len(body)) + body
        return out


# --------------------------------------------------------------------------
# The NoBlocklist classes (upstream 1.0 logic, see module docstring).
# --------------------------------------------------------------------------

L = "L%s;"  # descriptor helper


def build_premain() -> bytes:
    cls = ClassBuilder(ACC_PUBLIC | ACC_SUPER, PREMAIN, "java/lang/Object",
                       source_file="PreMain.java")
    cls.trivial_ctor()
    inst = "java/lang/instrument/Instrumentation"
    transformer_iface = "java/lang/instrument/ClassFileTransformer"

    m = cls.method(ACC_PRIVATE | ACC_STATIC, "doUrlTransform", f"(L{inst};)V")
    m.max_stack, m.max_locals = 6, 4
    (m.new(TRANSFORMER).dup().invokespecial(TRANSFORMER, "<init>", "()V").astore(1)
     .aload(0).aload(1).iconst(1)
     .invokeinterface(inst, "addTransformer", f"(L{transformer_iface};Z)V", 3)
     .label("try_start")
     .aload(0).iconst(1).anewarray("java/lang/Class").dup().iconst(0)
     .ldc_class("java/net/URL").aastore()
     .invokeinterface(inst, "retransformClasses", "([Ljava/lang/Class;)V", 2)
     .label("try_end")
     .aload(0).aload(1)
     .invokeinterface(inst, "removeTransformer", f"(L{transformer_iface};)Z", 2)
     .pop().return_()
     .label("handler")
     .astore(2).new("java/lang/RuntimeException").dup().aload(2)
     .invokespecial("java/lang/RuntimeException", "<init>", "(Ljava/lang/Throwable;)V")
     .athrow()
     .try_catch("try_start", "try_end", "handler",
                "java/lang/instrument/UnmodifiableClassException"))

    m = cls.method(ACC_PUBLIC | ACC_STATIC, "premain", f"(Ljava/lang/String;L{inst};)V")
    m.max_stack, m.max_locals = 1, 2
    (m.aload(1).invokestatic(PREMAIN, "doUrlTransform", f"(L{inst};)V")
     .invokestatic("java/lang/System", "gc", "()V").return_())
    return cls.build()


def build_transformer() -> bytes:
    inners = [
        (URL_VISITOR, TRANSFORMER, "URLVisitor", ACC_PRIVATE | ACC_STATIC),
        (URL_METHOD_VISITOR, TRANSFORMER, "URLMethodVisitor", ACC_PRIVATE | ACC_STATIC),
    ]
    cls = ClassBuilder(ACC_PUBLIC | ACC_SUPER, TRANSFORMER, "java/lang/Object",
                       interfaces=["java/lang/instrument/ClassFileTransformer"],
                       source_file="BlockerTransformer.java", inner_classes=inners)
    cls.trivial_ctor()
    cr, cw, cv = f"{SHASM}/ClassReader", f"{SHASM}/ClassWriter", f"{SHASM}/ClassVisitor"

    desc = ("(Ljava/lang/ClassLoader;Ljava/lang/String;Ljava/lang/Class;"
            "Ljava/security/ProtectionDomain;[B)[B")
    m = cls.method(ACC_PUBLIC, "transform", desc,
                   exceptions=["java/lang/instrument/IllegalClassFormatException"])
    m.max_stack, m.max_locals = 5, 10
    # if (!"java/net/URL".equals(className)) return null;
    (m.ldc_string("java/net/URL").aload(2)
     .invokevirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
     .ifne("is_url")
     .aconst_null().areturn()
     .label("is_url").label("try_start")
     # ClassReader cr = new ClassReader(classfileBuffer);
     .new(cr).dup().aload(5).invokespecial(cr, "<init>", "([B)V").astore(6)
     # ClassWriter cw = new ClassWriter(cr, COMPUTE_FRAMES);
     .new(cw).dup().aload(6).iconst(COMPUTE_FRAMES)
     .invokespecial(cw, "<init>", f"(L{cr};I)V").astore(7)
     # URLVisitor cv = new URLVisitor(cw);
     .new(URL_VISITOR).dup().aload(7)
     .invokespecial(URL_VISITOR, "<init>", f"(L{cv};)V").astore(8)
     # cr.accept(cv, 0);
     .aload(6).aload(8).iconst(0).invokevirtual(cr, "accept", f"(L{cv};I)V")
     # System.out.println("[NoBlocklist] ...");
     .getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
     .ldc_string("[NoBlocklist] Now, the blocked server list should not be available")
     .invokevirtual("java/io/PrintStream", "println", "(Ljava/lang/String;)V")
     # return cw.toByteArray();
     .aload(7).invokevirtual(cw, "toByteArray", "()[B").label("try_end").areturn()
     # catch (Throwable t) { t.printStackTrace(); } return null;
     .label("handler").astore(9).aload(9)
     .invokevirtual("java/lang/Throwable", "printStackTrace", "()V")
     .aconst_null().areturn()
     .try_catch("try_start", "try_end", "handler", "java/lang/Throwable"))
    return cls.build()


def build_url_visitor() -> bytes:
    cv, mv = f"{SHASM}/ClassVisitor", f"{SHASM}/MethodVisitor"
    inners = [
        (URL_VISITOR, TRANSFORMER, "URLVisitor", ACC_PRIVATE | ACC_STATIC),
        (URL_METHOD_VISITOR, TRANSFORMER, "URLMethodVisitor", ACC_PRIVATE | ACC_STATIC),
    ]
    cls = ClassBuilder(ACC_SUPER, URL_VISITOR, cv,
                       source_file="BlockerTransformer.java", inner_classes=inners)

    m = cls.method(ACC_PUBLIC, "<init>", f"(L{cv};)V")
    m.max_stack, m.max_locals = 3, 2
    (m.aload(0).ldc_int(ASM9).aload(1)
     .invokespecial(cv, "<init>", f"(IL{cv};)V").return_())

    desc = (f"(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)L{mv};")
    m = cls.method(ACC_PUBLIC, "visitMethod", desc)
    m.max_stack, m.max_locals = 7, 7
    (m.aload(0).raw(0x1B)  # iload_1 (access) - raw: MethodBuilder has no iload helper need beyond this
     .aload(2).aload(3).aload(4).aload(5)
     .invokespecial(cv, "visitMethod", desc).astore(6)
     # if (!"openConnection".equals(name)) return mv;
     .ldc_string("openConnection").aload(2)
     .invokevirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
     .ifeq("return_mv")
     # if (descA.equals(descriptor) || descB.equals(descriptor)) -> wrap; else return mv;
     .ldc_string("(Ljava/net/Proxy;)Ljava/net/URLConnection;").aload(3)
     .invokevirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
     .ifne("wrap")
     .ldc_string("()Ljava/net/URLConnection;").aload(3)
     .invokevirtual("java/lang/String", "equals", "(Ljava/lang/Object;)Z")
     .ifeq("return_mv")
     .label("wrap").new(URL_METHOD_VISITOR).dup().aload(6)
     .invokespecial(URL_METHOD_VISITOR, "<init>", f"(L{mv};)V").areturn()
     .label("return_mv").aload(6).areturn())
    return cls.build()


def build_url_method_visitor() -> bytes:
    mv, label = f"{SHASM}/MethodVisitor", f"{SHASM}/Label"
    inners = [
        (URL_VISITOR, TRANSFORMER, "URLVisitor", ACC_PRIVATE | ACC_STATIC),
        (URL_METHOD_VISITOR, TRANSFORMER, "URLMethodVisitor", ACC_PRIVATE | ACC_STATIC),
    ]
    cls = ClassBuilder(ACC_SUPER, URL_METHOD_VISITOR, mv,
                       source_file="BlockerTransformer.java", inner_classes=inners)
    cls.field(ACC_PRIVATE | ACC_FINAL, "plausibleLineNumber", "I")

    # URLMethodVisitor(MethodVisitor mv) { super(ASM9, mv);
    #   plausibleLineNumber = 1000 + new Random(System.nanoTime()).nextInt(100); }
    m = cls.method(ACC_PUBLIC, "<init>", f"(L{mv};)V")
    m.max_stack, m.max_locals = 7, 2
    (m.aload(0).ldc_int(ASM9).aload(1)
     .invokespecial(mv, "<init>", f"(IL{mv};)V")
     .aload(0).iconst(1000)
     .new("java/util/Random").dup()
     .invokestatic("java/lang/System", "nanoTime", "()J")
     .invokespecial("java/util/Random", "<init>", "(J)V")
     .iconst(100).invokevirtual("java/util/Random", "nextInt", "(I)I")
     .iadd().putfield(URL_METHOD_VISITOR, "plausibleLineNumber", "I")
     .return_())

    def load_mv(mm):
        return mm.aload(0).getfield(mv, "mv", f"L{mv};")

    # private void urlStringCondition(String method, String expectedValue, Label onNeqLabel)
    m = cls.method(ACC_PRIVATE, "urlStringCondition",
                   f"(Ljava/lang/String;Ljava/lang/String;L{label};)V")
    m.max_stack, m.max_locals = 7, 4
    load_mv(m).iconst(25).iconst(0)  # mv.visitVarInsn(ALOAD, 0)
    m.invokevirtual(mv, "visitVarInsn", "(II)V")
    (load_mv(m).iconst(182).ldc_string("java/net/URL").aload(1)  # INVOKEVIRTUAL URL.<method>()
     .ldc_string("()Ljava/lang/String;").iconst(0)
     .invokevirtual(mv, "visitMethodInsn", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V")
     )
    load_mv(m).aload(2).invokevirtual(mv, "visitLdcInsn", "(Ljava/lang/Object;)V")
    (load_mv(m).iconst(182).ldc_string("java/lang/String").ldc_string("equalsIgnoreCase")
     .ldc_string("(Ljava/lang/String;)Z").iconst(0)
     .invokevirtual(mv, "visitMethodInsn", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V")
     )
    load_mv(m).iconst(153).aload(3).invokevirtual(mv, "visitJumpInsn", f"(IL{label};)V")  # IFEQ
    m.return_()

    # public void visitCode()
    m = cls.method(ACC_PUBLIC, "visitCode", "()V")
    m.max_stack, m.max_locals = 7, 3
    (m.new(label).dup().invokespecial(label, "<init>", "()V").astore(1)   # allowedLabel
     .new(label).dup().invokespecial(label, "<init>", "()V").astore(2)   # fakeLineMarker
     .aload(0).ldc_string("getHost").ldc_string("sessionserver.mojang.com").aload(1)
     .invokespecial(URL_METHOD_VISITOR, "urlStringCondition",
                    f"(Ljava/lang/String;Ljava/lang/String;L{label};)V")
     .aload(0).ldc_string("getPath").ldc_string("/blockedservers").aload(1)
     .invokespecial(URL_METHOD_VISITOR, "urlStringCondition",
                    f"(Ljava/lang/String;Ljava/lang/String;L{label};)V"))
    (load_mv(m).aload(0).getfield(URL_METHOD_VISITOR, "plausibleLineNumber", "I").aload(2)
     .invokevirtual(mv, "visitLineNumber", f"(IL{label};)V"))
    load_mv(m).aload(2).invokevirtual(mv, "visitLabel", f"(L{label};)V")
    load_mv(m).iconst(187).ldc_string("java/io/IOException") \
        .invokevirtual(mv, "visitTypeInsn", "(ILjava/lang/String;)V")          # NEW IOException
    load_mv(m).iconst(89).invokevirtual(mv, "visitInsn", "(I)V")               # DUP
    (load_mv(m).iconst(183).ldc_string("java/io/IOException").ldc_string("<init>")
     .ldc_string("()V").iconst(0)
     .invokevirtual(mv, "visitMethodInsn", "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)V"))
    load_mv(m).iconst(191).invokevirtual(mv, "visitInsn", "(I)V")              # ATHROW
    load_mv(m).aload(1).invokevirtual(mv, "visitLabel", f"(L{label};)V")       # allowedLabel
    m.aload(0).invokespecial(mv, "visitCode", "()V").return_()                 # super.visitCode()
    return cls.build()


AGENT_CLASSES = {
    f"{PREMAIN}.class": build_premain,
    f"{TRANSFORMER}.class": build_transformer,
    f"{URL_VISITOR}.class": build_url_visitor,
    f"{URL_METHOD_VISITOR}.class": build_url_method_visitor,
}

# --------------------------------------------------------------------------
# ASM relocation (constant-pool Utf8 rewrite; rest of file untouched).
# --------------------------------------------------------------------------

_CP_SIZE = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
            15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}


def relocate_class(data: bytes) -> bytes:
    assert data[:4] == b"\xca\xfe\xba\xbe", "not a class file"
    cp_count = struct.unpack_from(">H", data, 8)[0]
    out = bytearray(data[:10])
    off, i = 10, 1
    while i < cp_count:
        tag = data[off]
        if tag == 1:  # Utf8
            ln = struct.unpack_from(">H", data, off + 1)[0]
            body = data[off + 3: off + 3 + ln]
            body = body.replace(DONOR_PREFIX, SHASM_B).replace(DONOR_PREFIX_DOTTED, SHASM_DOTTED)
            out += b"\x01" + struct.pack(">H", len(body)) + body
            off += 3 + ln
        elif tag in _CP_SIZE:
            size = _CP_SIZE[tag]
            out += data[off: off + 1 + size]
            off += 1 + size
            if tag in (5, 6):
                i += 1  # long/double take two slots
        else:
            raise ValueError(f"unknown CP tag {tag}")
        i += 1
    out += data[off:]
    result = bytes(out)
    assert b"moe/yushi" not in result and b"org/objectweb/asm" not in result, \
        "unrelocated reference left behind"
    return result


def collect_asm(donor: Path) -> dict:
    prefix = DONOR_PREFIX.decode() + "/"
    classes = {}
    with zipfile.ZipFile(donor) as z:
        for name in z.namelist():
            if name.startswith(prefix) and name.endswith(".class"):
                target = SHASM + "/" + name[len(prefix):]
                classes[target] = relocate_class(z.read(name))
    assert len(classes) >= 30, f"donor ASM looks incomplete: {len(classes)} classes"
    # The donor ASM must be able to read the newest game runtime's class files
    # (Java 25 for MC 26.x): Opcodes must carry a V25 (or newer) constant name.
    opcodes = classes[f"{SHASM}/Opcodes.class"]
    assert b"V26" in opcodes or b"V25" in opcodes, "donor ASM too old for Java 25"
    return classes


# --------------------------------------------------------------------------
# Packaging.
# --------------------------------------------------------------------------

MANIFEST = (
    "Manifest-Version: 1.0\r\n"
    "Premain-Class: git.artdeell.noblocklist.PreMain\r\n"
    "Can-Redefine-Classes: true\r\n"
    "Can-Retransform-Classes: true\r\n"
    "\r\n"
).encode()


def build_jar(donor: Path, out: Path) -> None:
    entries = collect_asm(donor)
    for path, builder in AGENT_CLASSES.items():
        entries[path] = builder()
    out.parent.mkdir(parents=True, exist_ok=True)
    fixed = zipfile.ZipInfo("META-INF/MANIFEST.MF", date_time=(2010, 1, 1, 0, 0, 0))
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr(fixed, MANIFEST)
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, date_time=(2010, 1, 1, 0, 0, 0))
            info.external_attr = 0o644 << 16
            z.writestr(info, entries[name], zipfile.ZIP_DEFLATED)
    # Self-check: every class parses as far as (magic, version) and is Java-8 loadable.
    with zipfile.ZipFile(out) as z:
        assert z.read("META-INF/MANIFEST.MF") == MANIFEST
        for name in z.namelist():
            if name.endswith(".class"):
                data = z.read(name)
                major = struct.unpack_from(">H", data, 6)[0]
                assert data[:4] == b"\xca\xfe\xba\xbe" and major <= 52, (name, major)
    digest = hashlib.sha256(out.read_bytes()).hexdigest()
    print(f"Built {out} ({out.stat().st_size} bytes, sha256 {digest})")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--donor", type=Path, default=DEFAULT_DONOR,
                        help="jar containing core ASM to relocate")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args(argv)
    if not args.donor.is_file():
        print(f"donor jar not found: {args.donor}", file=sys.stderr)
        return 1
    build_jar(args.donor, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
