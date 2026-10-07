#!/usr/bin/env python3
"""Generates the data-only JDK stubs under core/src/main/java for the RuneLite client.

The Android runtime has no java.desktop, so the pre-compiled RuneLite client jar (and the
injected game client) cannot link against the real java.awt/javax.swing/... classes. This
generator writes the stubs they need, using the real call sites as the specification:

  * the member set comes from RefScan (constant-pool scan of the client jars), so every
    emitted descriptor is exactly the one the client's bytecode asks for;
  * hierarchy, kind (class/interface/enum/annotation), constant values and the
    abstract-method set come from the local JDK through JdkInfo, so ART's verifier sees
    the same type relationships;
  * hand-written files (no marker header) are never touched, and generated files are
    rewritten on every run.

Usage (also wired as the :android:generateJdkStubs Gradle task):

    python3 tools/gen_stubs.py --rl-jars android/build/rl-jars \
        --asm-cp "$(find ~/.gradle/caches -name 'asm-9.6.jar' | head -1):$(find ~/.gradle/caches -name 'asm-tree-9.6.jar' | head -1)" \
        --out core/src/main/java --work build/stubgen

Run it after a RuneLite client bump, then rebuild and run :android:verifyHostLinks.
"""
import argparse, collections, os, re, subprocess, sys

ap = argparse.ArgumentParser()
ap.add_argument("--rl-jars", default="android/build/rl-jars")
ap.add_argument("--asm-cp", required=True, help="classpath holding asm/asm-tree for the helpers")
ap.add_argument("--out", default="core/src/main/java")
ap.add_argument("--work", default="build/stubgen")
ap.add_argument("--repo", default=".")
args = ap.parse_args()

ROOT = os.path.abspath(args.repo)
SRC = os.path.join(ROOT, args.out)
WORK = os.path.join(ROOT, args.work)
os.makedirs(WORK, exist_ok=True)
MEMBERS = os.path.join(WORK, "members.txt")
JDKINFO = os.path.join(WORK, "jdkinfo.txt")
MARKER = "Data-only stub for {@code"

# JDK packages the pre-compiled client jars reference that Android does not provide.
SCAN_PREFIXES = ("java/awt/", "java/applet/", "javax/", "com/sun/net/", "com/sun/management/",
                 "netscape/", "java/lang/management/")

HAND_EXACT = set("""
java/awt/Graphics java/awt/Graphics2D java/awt/Image java/awt/Shape java/awt/Polygon java/awt/Rectangle
java/awt/Color java/awt/Point java/awt/Dimension java/awt/Insets
java/awt/Component java/awt/Container java/awt/Canvas java/awt/Frame java/awt/Window java/awt/Panel
java/awt/Toolkit java/awt/Desktop java/awt/EventQueue java/awt/Cursor java/awt/LayoutManager
java/awt/Font java/awt/FontMetrics java/awt/GraphicsEnvironment java/awt/AWTException java/awt/FontFormatException
java/awt/Stroke java/awt/Composite java/awt/Paint java/awt/GradientPaint java/awt/AlphaComposite
java/awt/BasicStroke java/awt/RenderingHints java/awt/RenderingHints$Key
java/awt/GraphicsConfiguration java/awt/GraphicsDevice
javax/swing/SwingUtilities javax/swing/Timer javax/swing/Box
javax/swing/text/StyleContext
java/applet/Applet java/applet/AppletStub java/applet/AppletContext
netscape/javascript/JSObject netscape/javascript/JSException
java/lang/management/ManagementFactory java/lang/management/RuntimeMXBean
javax/imageio/ImageIO javax/imageio/ImageReader javax/imageio/stream/MemoryCacheImageInputStream
""".split())
HAND_PREFIX = (
    "java/awt/geom/", "java/awt/image/", "java/awt/color/", "java/awt/event/", "java/awt/datatransfer/",
    "javax/imageio/", "org/runelite/", "java/lang/", "java/util/", "java/io/", "java/nio/", "java/net/",
    "java/security/", "java/text/", "java/math/", "java/time/", "java/sql/", "javax/net/", "javax/crypto/",
    "javax/security/", "javax/xml/", "javax/inject", "javax/annotation", "sun/", "com/sun/jna",
    "java/applet/", "netscape/",
)

# Packages the Android platform already provides: never stub these (a stub in the app
# dex would shadow nothing, but it would be dead weight and could mask a real API).
PLATFORM_PREFIX = (
    "java/lang/", "java/util/", "java/io/", "java/nio/", "java/net/", "java/security/", "java/text/",
    "java/math/", "java/time/", "java/sql/", "java/awt/font/", "javax/net/", "javax/crypto/",
    "javax/security/", "javax/xml/", "javax/naming/", "javax/inject/", "javax/annotation/", "sun/",
    "com/sun/jna", "org/bouncycastle", "android/", "ch/qos/", "io/netty/", "com/google/",
)

def platform_owner(o):
    return any(o.startswith(p) for p in PLATFORM_PREFIX)

# Source files split by origin: generated files (this generator's marker) are rewritten
# on every run, hand-written files are never touched.
provided = set()
handwritten = set()
# The host shims live outside the app source set (they are dexed into the asset dex) but
# they are part of this build: they must never be treated as "client classes to attribute
# to a stub ancestor".
for root_dir in (SRC, os.path.join(ROOT, "hostshims/src")):
  for dirpath, _, files in os.walk(root_dir):
    for f in files:
        if not f.endswith(".java"):
            continue
        internal = os.path.relpath(os.path.join(dirpath, f), root_dir)[:-5]
        provided.add(internal)
        with open(os.path.join(dirpath, f), errors="replace") as fh:
            if MARKER not in fh.read(4096):
                handwritten.add(internal)

def exists(o):
    return o in handwritten or o in HAND_EXACT

def hand_owner(o):
    """Types that must not be generated (already provided, or platform-provided)."""
    return platform_owner(o) or exists(o)

# ------------------------------------------------------- helpers / input loading
def java(class_name, *class_args):
    cmd = ["java", "-cp", os.path.join(WORK, "tools") + os.pathsep + args.asm_cp, class_name] + list(class_args)
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        raise SystemExit(class_name + " failed:\n" + res.stdout + res.stderr)
    return res.stdout


def compile_helpers():
    out = os.path.join(WORK, "tools")
    os.makedirs(out, exist_ok=True)
    sources = [os.path.join(ROOT, "tools", name) for name in ("RefScan.java", "JdkInfo.java")]
    subprocess.run(["javac", "-cp", args.asm_cp, "-d", out] + sources, check=True)


# Classes the build strips out of the asset dex and replaces with host shims: their
# members must never be attributed to a stub ancestor (they are not shipped).
REPLACED_PREFIXES = ("net/runelite/client/ui/laf/", "net/runelite/client/ui/components/")
REPLACED_EXACT = {
    "net/runelite/client/ui/ClientUI", "net/runelite/client/ui/SplashScreen",
    "net/runelite/client/ui/ContainableFrame", "net/runelite/client/ui/ClientPanel",
    "net/runelite/client/ui/ClientToolbarPanel", "net/runelite/client/ui/FatalErrorDialog",
    "net/runelite/client/ui/OSXFullScreenAdapter", "net/runelite/client/ui/MultiplexingPluginPanel",
    "net/runelite/client/ui/ClientToolbar", "net/runelite/client/ui/PluginPanel",
    "net/runelite/client/ui/NavigationButton", "net/runelite/client/ui/UnitFormatter",
    "net/runelite/client/ui/UnitFormatterFactory", "net/runelite/client/ui/Activatable",
}


def replaced_class(owner):
    if any(owner.startswith(p) for p in REPLACED_PREFIXES):
        return True
    return any(owner == e or owner.startswith(e + "$")
               for e in tuple(REPLACED_EXACT) + tuple(REPLACED_PREFIXES))


def scan_members():
    # The *downloaded* jars, not the cleaned ones: the host shims mirror the classes the
    # build strips (ui/components/**, ui/laf/**), so those classes' references to
    # java.desktop types (JDialog, JProgressBar, ...) are exactly the stub surface the
    # shims need. Attribution below skips the replaced namespace so their own members
    # cannot leak into the stubs.
    jars = []
    for name in sorted(os.listdir(os.path.join(ROOT, args.rl_jars))):
        if not name.endswith(".jar") or name.endswith("-runtime.jar"):
            continue
        jars.append(os.path.join(ROOT, args.rl_jars, name))
    text = java("RefScan", "members", *jars)
    with open(MEMBERS, "w") as fh:
        fh.write(text)


def load_members():
    methods = collections.defaultdict(set)
    fields = collections.defaultdict(set)
    classrefs = set()
    for line in open(MEMBERS):
        parts = line.rstrip("\n").split(" ", 2)
        if len(parts) < 2:
            continue
        kind = parts[0]
        if kind == "C":
            classrefs.add(parts[1])
        elif kind in ("M", "F") and len(parts) == 3:
            owner, name = parts[1].rsplit(".", 1)
            (methods if kind == "M" else fields)[owner].add((name, parts[2]))
    return methods, fields, classrefs


def load_declared():
    """(class, "M"/"F", "name desc") for members a class declares itself."""
    declared = set()
    for line in open(MEMBERS):
        if not line.startswith("DECL\t"):
            continue
        _, owner, member = line.rstrip("\n").split("\t", 2)
        kind, rest = member.split(" ", 1)
        name, flagDesc = rest.split(" ", 1)
        declared.add((owner, kind, name + " " + flagDesc))
    return declared


def decl_key(owner, kind, member):
    """The DECL-style key for a member reference tuple (name, "<flag>desc")."""
    name, flagDesc = member
    return (owner, kind, name + " " + flagDesc[1:])


def load_hierarchy():
    """Class -> (super, interfaces) for every scanned class, used to attribute a member
    the client calls through a *subtype* to the stub ancestor that must declare it."""
    hier = {}
    for line in open(MEMBERS):
        if not line.startswith("HIER\t"):
            continue
        _, name, sup, ifaces = line.rstrip("\n").split("\t")
        hier[name] = (sup, [i for i in ifaces.split(",") if i])
    return hier


def load_jdkinfo():
    jdk = {}
    abstracts = collections.defaultdict(list)
    ctors = collections.defaultdict(list)
    for line in open(JDKINFO):
        p = line.rstrip("\n").split("\t")
        if p[0] == "TYPE":
            jdk[p[1]] = {"kind": p[2], "abstract": p[3] == "abstract", "final": p[4] == "final",
                         "static": p[5], "super": p[6], "ifaces": [x for x in p[7].split(",") if x and x != "-"],
                         "enclosing": p[8], "fields": {}, "enums": []}
        elif p[0] == "FIELD" and p[1] in jdk:
            jdk[p[1]]["fields"][p[2]] = {"type": p[3], "value": None if p[6] == "-" else p[6]}
        elif p[0] == "ENUM" and p[1] in jdk:
            jdk[p[1]]["enums"].append(p[2])
        elif p[0] == "ABSTRACT" and p[1] in jdk:
            abstracts[p[1]].append((p[2], p[3]))
        elif p[0] == "CTOR" and p[1] in jdk:
            ctors[p[1]].append(p[2])
    return jdk, abstracts, ctors


compile_helpers()
scan_members()
methods, fields, classrefs = load_members()
hier = load_hierarchy()
declared = load_declared()

# The client calls inherited methods through the subtype's static type, so the
# constant-pool owner can be a client-jar class (e.g.
# plugins/info/JRichTextPane.setHighlighter, inherited from JTextComponent). Attribute
# such a member to the nearest class we provide, or the stub would never declare it and
# ART would throw NoSuchMethodError on device.
attributed_m = collections.defaultdict(set)
attributed_f = collections.defaultdict(set)


def first_jdk_ancestor(owner, member, kind):
    """Walks a *scanned* (client/api) class hierarchy looking for the class that owns a
    member the client calls through a subtype.

    Returns None when the member is provided by the client jar itself (declared by the
    owner or by another scanned class), otherwise the first ancestor that is not part of
    the scanned jars -- a JDK class, i.e. one of our stubs (or a platform class we do not
    need to provide).
    """
    seen = set()
    queue = [owner]
    while queue:
        cur = queue.pop(0)
        if cur in seen or not cur or cur == "-":
            continue
        seen.add(cur)
        if cur in hier:
            if cur != owner and decl_key(cur, kind, member) in declared:
                return None          # the client jar declares it: nothing to add
            sup, ifaces = hier[cur]
            queue.extend([sup] + ifaces)
        else:
            return cur               # left the scanned jars: a JDK class
    return None


for table, sink, kind in ((methods, attributed_m, "M"), (fields, attributed_f, "F")):
    for owner in list(table):
        if hand_owner(owner) or replaced_class(owner) or owner not in hier:
            continue
        for member in list(table[owner]):
            if decl_key(owner, kind, member) in declared:
                continue
            # Only members whose signature is expressible in our stubs are worth
            # attributing: a member that mentions a client-jar type (e.g. a callback
            # taking RuneliteColorPicker) cannot be declared by a core stub.
            types = re.findall(r"L([^;]+);", member[1])
            if any(not (t.startswith("java/") or t.startswith("javax/") or t in provided
                        or t in HAND_EXACT) for t in types):
                continue
            target = first_jdk_ancestor(owner, member, kind)
            if target and not platform_owner(target):
                sink[target].add(member)
            table[owner].discard(member)
        if not table[owner]:
            del table[owner]
for owner, members in attributed_m.items():
    methods[owner].update(members)
for owner, members in attributed_f.items():
    fields[owner].update(members)

open(JDKINFO, "w").close()
jdk, abstracts, ctors = load_jdkinfo()

# ------------------------------------------------------- transitive type closure
def type_names(desc):
    out = []
    if desc is None:
        return out
    if desc.startswith("(") or desc.startswith("["):
        out += re.findall(r"L([^;]+);", desc)
    elif desc.startswith("L") and desc.endswith(";"):
        out.append(desc[1:-1])
    return out

def bogus(o):
    return any(c in o for c in "<>;()[") or o.endswith("/")

need = set()
for owner in list(methods) + list(fields):
    if not hand_owner(owner) and not bogus(owner):
        need.add(owner)
for c in classrefs:
    if not hand_owner(c) and not bogus(c):
        need.add(c)

def enclosing_chain(o):
    info = jdk.get(o)
    while info and info["enclosing"] not in ("-", None):
        yield info["enclosing"]
        info = jdk.get(info["enclosing"])

queue = list(need)
while queue:
    o = queue.pop()
    for e in enclosing_chain(o):
        if e not in need:
            need.add(e)
            queue.append(e)
    info = jdk.get(o)
    if not info:
        continue
    cands = []
    if info["super"] not in ("-", "java/lang/Object"):
        cands.append(info["super"])
    cands += info["ifaces"]
    for name, desc in methods.get(o, ()):
        cands += type_names(desc[1:])
    for _, d in abstracts.get(o, ()):
        cands += type_names(d)
    for c in cands:
        if c and not hand_owner(c) and not bogus(c) and c not in need:
            need.add(c)
            queue.append(c)

# all types this build provides: generated + hand-written + generated-by-closure
stubs = set(need) | set(HAND_EXACT) | provided

# ------------------------------------------------------------------- emit code
PRIM_DEFAULT = {"V": None, "Z": "false", "B": "0", "C": "0", "S": "0", "I": "0", "J": "0L", "F": "0f", "D": "0d"}
PRIM_JAVA = {"V": "void", "Z": "boolean", "B": "byte", "C": "char", "S": "short", "I": "int", "J": "long",
             "F": "float", "D": "double"}
ENUM_BUILTINS = {"ordinal", "values", "valueOf", "name", "compareTo", "equals", "hashCode", "toString",
                 "getDeclaringClass", "describeConstable"}

def parse_desc(desc):
    i = 1
    args = []
    while desc[i] != ")":
        arr = 0
        while desc[i] == "[":
            arr += 1
            i += 1
        c = desc[i]
        if c == "L":
            j = desc.index(";", i)
            t = desc[i + 1:j]
            i = j + 1
        else:
            t = PRIM_JAVA[c]
            i += 1
        args.append(t + "[]" * arr)
    ret = desc[i + 1:]
    arr = 0
    while ret.startswith("["):
        arr += 1
        ret = ret[1:]
    rt = ret[1:-1] if ret.startswith("L") else PRIM_JAVA[ret]
    return args, rt + "[]" * arr, ret

def simple(internal):
    return internal.rsplit("/", 1)[-1].split("$")[-1]

def to_src(t, pkg):
    """JVM type descriptor (object/array/primitive) -> Java source type."""
    arr = 0
    while t.startswith("["):
        arr += 1
        t = t[1:]
    if t.startswith("L") and t.endswith(";"):
        base = ref(t[1:-1], pkg)
    elif "/" in t:
        base = ref(t, pkg)
    else:
        base = PRIM_JAVA.get(t, t)
    return base + "[]" * arr

def ref(internal, pkg):
    """Java source name for a type, using the simple name inside its own package."""
    internal = internal.replace("$", "$")
    parts = internal.rsplit("/", 1)
    own_pkg = parts[0] if len(parts) == 2 else ""
    name = simple(internal)
    outer = internal.rsplit("/", 1)[-1].rsplit("$", 1)
    if own_pkg == pkg:
        # same package: unqualified (a qualified javax.swing.X reference is resolved
        # against the JDK's java.desktop module, which is not in the module graph)
        return (outer[0] + "." if len(outer) == 2 else "") + name
    return internal.replace("/", ".").replace("$", ".")

def default_value(desc):
    if desc == "V":
        return None
    if desc.startswith("L") or desc.startswith("["):
        return "null"
    return PRIM_DEFAULT[desc]

def default_return(desc):
    v = default_value(desc.split(")")[1])
    return "return;" if v is None else "return " + v + ";"

def super_args(sup, pkg):
    """Arguments for an explicit super(...) call: [] when the super has a no-arg ctor."""
    if sup in ("-", "java/lang/Object"):
        return None
    if sup in need:
        own = [d[1:] for n, d in methods.get(sup, ()) if n == "<init>"]
        if not own:
            # generated class with no referenced ctor: it gets a synthetic no-arg ctor
            return []
        options = own
    elif sup in handwritten or sup in HAND_EXACT:
        # hand-written core stubs are plain classes with a no-arg ctor
        return []
    else:
        options = ctors.get(sup, [])
        if not options:
            return []
    if any(parse_desc(d)[0] == [] for d in options):
        return []
    best = sorted(options, key=lambda d: (len(parse_desc(d)[0]), d))[0]
    args = []
    for a in parse_desc(best)[0]:
        if a == "int" or a == "short" or a == "byte" or a == "char":
            args.append("0")
        elif a == "long":
            args.append("0L")
        elif a == "float":
            args.append("0f")
        elif a == "double":
            args.append("0d")
        elif a == "boolean":
            args.append("false")
        else:
            args.append("(" + a.replace("/", ".").replace("$", ".") + ") null")
    return args

def emit_members(o, info, out, pkg, indent="    "):
    enum_constants = set(info["enums"])
    for name, desc in sorted(fields.get(o, ())):
        if name in enum_constants:
            continue
        static_field = desc.startswith("S")
        desc = desc[1:]
        t = desc
        jtype = to_src(t, pkg)
        real = info["fields"].get(name)
        if real and real["value"] is not None:
            value = real["value"]
        elif jtype in ("int", "long", "float", "double", "boolean", "byte", "char", "short"):
            value = PRIM_DEFAULT[t]
        else:
            value = "null"
        if static_field or info["kind"] in ("interface", "annotation"):
            # interface fields are implicitly static final and must be initialised
            out.append(indent + "public static final " + jtype + " " + name + " = " + value + ";")
        else:
            out.append(indent + "public " + jtype + " " + name + ";")

    own_ctors = [d for n, d in methods.get(o, ()) if n == "<init>"]
    sup_args = super_args(info["super"], pkg)
    if info["kind"] == "class" and sup_args is not None and not own_ctors:
        out.append(indent + "public " + simple(o) + "() { super(" + ", ".join(sup_args) + "); }")

    for name, desc in sorted(methods.get(o, ())):
        if info["kind"] == "enum" and name in ENUM_BUILTINS:
            continue
        static_method = desc.startswith("S")
        desc = desc[1:]
        args, rt, raw = parse_desc(desc)
        args = [to_src(a, pkg) for a in args]
        rt = to_src(rt, pkg)
        params = ", ".join(a + " a" + str(i) for i, a in enumerate(args))
        if name == "<init>":
            call = "super(" + ", ".join(sup_args) + "); " if (info["kind"] == "class" and sup_args is not None) else ""
            out.append(indent + "public " + simple(o) + "(" + params + ") { " + call + "}")
        elif static_method:
            out.append(indent + "public static " + rt + " " + name + "(" + params + ") { " + default_return(desc) + " }")
        elif info["kind"] == "annotation":
            value = '""' if rt == "String" else ("0" if rt in ("int", "long", "short", "byte") else "false" if rt == "boolean" else "null")
            out.append(indent + "public " + rt + " " + name + "() default " + value + ";")
        elif info["kind"] == "interface":
            out.append(indent + "default " + rt + " " + name + "(" + params + ") { " + default_return(desc) + " }")
        else:
            out.append(indent + "public " + rt + " " + name + "(" + params + ") { " + default_return(desc) + " }")

    # concrete classes must satisfy abstract methods inherited from JDK supertypes
    inherited = [a for a in abstracts.get(o, ()) if a[0] not in [n for n, _ in methods.get(o, ())]]
    if inherited and info["kind"] == "class":
        if not own_ctors:
            # nothing instantiates this stub (no ctor call site): leave the inherited
            # abstract methods unimplemented rather than faking a Map/InputStream body.
            # clone() is the exception: Object.clone() is protected, so a public
            # interface clone() must be declared explicitly or javac rejects the class.
            info["abstract"] = True
            info["final"] = False
            inherited = [a for a in inherited if a[0] == "clone"]
        for name, desc in inherited:
            args, rt, raw = parse_desc(desc)
            args = [to_src(a, pkg) for a in args]
            rt = to_src(rt, pkg)
            params = ", ".join(a + " a" + str(i) for i, a in enumerate(args))
            out.append(indent + "public " + rt + " " + name + "(" + params + ") { " + default_return(desc) + " }")

def decl(o, info, pkg, indent=""):
    kind = info["kind"]
    mods = []
    if kind != "enum":
        if info["abstract"]:
            mods.append("abstract")
        if info["final"]:
            mods.append("final")
    if info["enclosing"] != "-":
        mods.append("static")
    head = indent + "public " + (" ".join(mods) + " " if mods else "")
    if kind == "enum":
        return head + "enum " + simple(o) + " {"
    if kind == "annotation":
        return head + "@interface " + simple(o) + " {"
    if kind == "interface":
        return head + "interface " + simple(o) + extends_clause(info, pkg) + " {"
    return head + "class " + simple(o) + extends_clause(info, pkg) + " {"

def extends_clause(info, pkg):
    ifs = [i for i in info["ifaces"] if i and i != "java/io/Serializable" and not i.startswith("java/lang/")]
    if info["kind"] == "interface":
        return (" extends " + ", ".join(ref(i, pkg) for i in ifs)) if ifs else ""
    if info["kind"] == "annotation":
        return ""
    s = ""
    sup = info["super"]
    if sup not in ("-", "java/lang/Object"):
        s = " extends " + ref(sup, pkg)
    if ifs:
        s += " implements " + ", ".join(ref(i, pkg) for i in ifs)
    return s

def gen_file(outer, nested):
    info = jdk.get(outer)
    if not info:
        return None
    pkg = outer.rsplit("/", 1)[0].replace("/", ".")
    lines = ["package " + pkg + ";", ""]
    lines += ["/**",
              " * " + MARKER + " " + outer.replace("/", ".").replace("$", ".") + "}.",
              " *",
              " * The Android runtime has no java.desktop, so every type the pre-compiled RuneLite",
              " * client jar references has to exist here with the same name, hierarchy and member",
              " * signatures (the member set is taken from the real call sites). Nothing paints, lays",
              " * out or dispatches events: the native side panel replaces the desktop Swing shell.",
              " * Generated by tools/gen_stubs.py -- do not hand-edit.", " */"]
    outer_body = []
    emit_members(outer, info, outer_body, pkg, indent="    ")
    lines.append(decl(outer, info, pkg))
    lines += outer_body
    for n in nested:
        ni = jdk[n]
        body = []
        emit_members(n, ni, body, pkg, indent="        ")
        lines.append("")
        lines.append(decl(n, ni, pkg, indent="    "))
        if ni["kind"] == "enum":
            for c in ni["enums"]:
                body.insert(0, "        " + c + ",")
        lines += body
        lines.append("    }")
    lines.append("}")
    return "\n".join(lines) + "\n"

for _pass in range(6):
    query = set(need) | set(HAND_EXACT) | provided
    for o in list(query):
        info = jdk.get(o)
        if info:
            query.add(info["super"])
            query.update(info["ifaces"])
    query = {q for q in query if q and q != "-"}
    with open(os.path.join(WORK, "query.txt"), "w") as fh:
        fh.write("\n".join(sorted(query)) + "\n")
    with open(JDKINFO, "w") as fh:
        fh.write(java("JdkInfo", os.path.join(WORK, "query.txt")))
    jdk, abstracts, ctors = load_jdkinfo()
    before = set(need)
    for o in sorted(need):
        info = jdk.get(o)
        if not info:
            continue
        cands = [info["super"]] + info["ifaces"]
        for name, desc in methods.get(o, ()):
            cands += type_names(desc[1:])
        for name, desc in fields.get(o, ()):
            cands += type_names(desc[1:])
        for _, d in abstracts.get(o, ()):
            cands += type_names(d)
        for c in cands:
            if c and c != "-" and not hand_owner(c) and not bogus(c):
                need.add(c)
    if need == before:
        break

groups = collections.defaultdict(list)
for o in sorted(need):
    if o not in jdk:
        continue
    info = jdk[o]
    outer = o
    while jdk.get(outer, {}).get("enclosing") not in ("-", None) and not hand_owner(jdk[outer]["enclosing"]):
        outer = jdk[outer]["enclosing"]
    groups[outer].append(o)

written, skipped = [], []
for outer, members in sorted(groups.items()):
    if hand_owner(outer):
        continue
    if "$" in outer:
        # The enclosing type is hand-written, so the nested type has to be declared
        # inside that hand-written file (javac requires it). Nothing to generate.
        skipped.append(outer)
        continue
    path = os.path.join(SRC, outer + ".java")
    if os.path.exists(path):
        with open(path) as fh:
            head = fh.read(4096)
        if MARKER not in head:
            skipped.append(outer)
            continue
    text = gen_file(outer, [m for m in members if m != outer])
    if text is None:
        continue
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(text)
    written.append(outer)

# drop stale generated files (e.g. a nested type that used to be emitted as its own
# file named Outer$Nested.java, which javac rejects)
for internal in sorted(provided):
    if internal in written:
        continue
    stale = os.path.join(SRC, internal + ".java")
    if os.path.exists(stale):
        with open(stale, errors="replace") as fh:
            if MARKER in fh.read(4096):
                os.remove(stale)
                print("removed stale", internal)

with open(os.path.join(WORK, "need.txt"), "w") as fh:
    fh.write("\n".join(sorted(need)) + "\n")
print("generated", len(written), "files; kept hand-written:", sorted(skipped))
print("not in local JDK (skipped):", sorted(o for o in need if o not in jdk))
