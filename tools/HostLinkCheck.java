import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Verifies that every JDK-platform member the pre-compiled RuneLite client jar (and our
 * own app classes) references actually exists in the stubs this repo provides.
 *
 * The client jar is compiled Java 11 bytecode: a missing class or a member with the wrong
 * descriptor is a NoClassDefFoundError / NoSuchMethodError on the device, at whatever
 * moment that code path first runs. This check turns those into a build-time list.
 *
 * Usage:
 *   java HostLinkCheck --provided &lt;jar|dir&gt;... --use &lt;jar|dir&gt;... [--prefix p1,p2,...]
 *                      [--ignore owner-substring]... [--ignore-list file]
 *
 * --provided    : everything that is on the class path at runtime (app dex classes, the
 *                 RuneLite runtime jars, the asset jars).
 * --use         : the classes whose references are checked (the cleaned client jars and our
 *                 own compiled app classes).
 * --prefix      : optional filter; only owners starting with one of these are checked.
 *                 Default: every owner.
 * --ignore      : skip any owner containing this substring (repeatable).
 * --ignore-list : file with one prefix per line, '#' comments and blank lines ignored.
 *                 A prefix drops a reference when it matches the referenced owner *or*
 *                 the referencing class: the first covers a library the port never loads
 *                 (lwjgl, JNA, flatlaf), the second a desktop-only consumer it never runs
 *                 (net.runelite.client.Updater).
 * --platform-extra : file listing references android.jar does not model but the *device*
 *                 runtime resolves (verified on the device), one per line after the kind
 *                 prefix {@code resolve()} prints: {@code owner.name desc} for a member,
 *                 a bare {@code owner} for a class. This is deliberately not an ignore
 *                 list: the gate still reports everything else about that owner, and an
 *                 entry that matches nothing is printed as a warning.
 */
public final class HostLinkCheck {

    private static final Map<String, byte[]> provided = new LinkedHashMap<>();
    private static final Map<String, byte[]> used = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        List<File> providedPaths = new ArrayList<>();
        List<File> usePaths = new ArrayList<>();
        // Default: check every reference. The prefix list is only an ad-hoc filter --
        // filtering by owner hid a real class of bugs, because the client calls an
        // *inherited* method through the subtype's static type: the constant-pool owner is
        // then a client-jar class (e.g. plugins/info/JRichTextPane.setHighlighter) whose
        // resolution has to end in one of our stubs.
        List<String> prefixes = new ArrayList<>();
        List<String> ignores = new ArrayList<>();
        List<String> ignorePrefixes = new ArrayList<>();
        List<String> platformExtra = new ArrayList<>();

        List<File> target = providedPaths;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--provided": target = providedPaths; break;
                case "--use": target = usePaths; break;
                case "--prefix": prefixes = Arrays.asList(args[++i].split(",")); break;
                case "--ignore": ignores.add(args[++i]); break;
                case "--ignore-list": ignorePrefixes.addAll(readFileList(new File(args[++i]))); break;
                case "--platform-extra": platformExtra.addAll(readFileList(new File(args[++i]))); break;
                default: target.add(new File(args[i]));
            }
        }

        for (File f : providedPaths) load(provided, f);
        for (File f : usePaths) load(used, f);

        Set<String> platformSeen = new TreeSet<>();
        List<String> missing = new ArrayList<>();
        Set<String> missingClasses = new TreeSet<>();
        int ignoredSources = 0;
        for (Map.Entry<String, byte[]> e : used.entrySet()) {
            // The ignore list covers both directions: the referenced owner (a library the
            // port never loads) and the referencing class (a desktop-only consumer that
            // the port never runs). Only the second makes a gap in an otherwise fine
            // class -- e.g. net.runelite.client.Updater's use of ProcessHandle -- skippable.
            if (startsWithAny(e.getKey(), ignorePrefixes)) {
                ignoredSources++;
                continue;
            }
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, ClassReader.SKIP_DEBUG);
            for (String owner : referencedOwners(cn)) {
                if (!selected(owner, prefixes, ignores, ignorePrefixes)) continue;
                for (String member : membersOf(cn, owner)) {
                    String problem = resolve(owner, member);
                    if (problem == null) continue;
                    String key = gapKey(problem);
                    if (platformExtra.contains(key)) {
                        platformSeen.add(key);
                        continue;
                    }
                    // The referencing class is part of the report: a gap is only
                    // actionable when you know which plugin drags it in.
                    missing.add(e.getKey().replace('/', '.') + ": " + problem);
                    missingClasses.add(e.getKey().replace('/', '.'));
                }
            }
        }

        Collections.sort(missing);
        for (String m : missing) System.out.println(m);
        System.out.println("# missing: " + missing.size() + " (provided classes: " + provided.size()
            + ", checked classes: " + (used.size() - ignoredSources) + ")");
        System.out.println("# missing classes: " + missingClasses.size());
        for (String extra : platformExtra) {
            if (!platformSeen.contains(extra)) {
                System.out.println("# warn: platform-extra entry matched nothing: " + extra);
            }
        }
        if (!missing.isEmpty()) System.exit(1);
    }

    /** The reference a gap names, without the {@code method missing:}-style kind prefix. */
    private static String gapKey(String problem) {
        int colon = problem.indexOf(": ");
        return colon < 0 ? problem : problem.substring(colon + 2);
    }

    /** One entry per line; blank lines and {@code #} comments are ignored. */
    private static List<String> readFileList(File f) throws IOException {
        List<String> out = new ArrayList<>();
        if (!f.isFile()) {
            System.out.println("# warn: list does not exist: " + f);
            return out;
        }
        for (String line : Files.readAllLines(f.toPath())) {
            int hash = line.indexOf('#');
            String v = (hash >= 0 ? line.substring(0, hash) : line).trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private static boolean startsWithAny(String value, List<String> prefixes) {
        for (String p : prefixes) if (value.startsWith(p)) return true;
        return false;
    }

    private static boolean selected(String owner, List<String> prefixes, List<String> ignores,
                                    List<String> ignorePrefixes) {
        for (String p : ignorePrefixes) if (owner.startsWith(p)) return false;
        for (String i : ignores) if (owner.contains(i)) return false;
        // An empty prefix list means "check everything": silently checking nothing is the
        // worst failure mode of a link gate.
        if (prefixes.isEmpty()) return true;
        for (String p : prefixes) if (owner.startsWith(p)) return true;
        return false;
    }

    /** Every owner this class refers to (member owners + type references). */
    private static Set<String> referencedOwners(ClassNode cn) {
        Set<String> owners = new TreeSet<>();
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                // An array type owns constant-pool entries too (ANEWARRAY's operand, and the
                // "array".clone() Methodref javac emits for every enum's values()), so an
                // owner name may be an array descriptor rather than an internal name.
                if (insn instanceof MethodInsnNode) addOwner(owners, ((MethodInsnNode) insn).owner);
                else if (insn instanceof FieldInsnNode) addOwner(owners, ((FieldInsnNode) insn).owner);
                else if (insn instanceof TypeInsnNode) addOwner(owners, ((TypeInsnNode) insn).desc);
                else if (insn instanceof MultiANewArrayInsnNode) collectDesc(owners, ((MultiANewArrayInsnNode) insn).desc);
                else if (insn instanceof LdcInsnNode) {
                    Object c = ((LdcInsnNode) insn).cst;
                    if (c instanceof Type) collectType(owners, (Type) c);
                    else if (c instanceof Handle) addOwner(owners, ((Handle) c).getOwner());
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode id = (InvokeDynamicInsnNode) insn;
                    addOwner(owners, id.bsm.getOwner());
                    for (Object a : id.bsmArgs) {
                        if (a instanceof Type) collectType(owners, (Type) a);
                        else if (a instanceof Handle) addOwner(owners, ((Handle) a).getOwner());
                    }
                }
            }
            for (TryCatchBlockNode t : m.tryCatchBlocks) if (t.type != null) addOwner(owners, t.type);
        }
        for (FieldNode f : cn.fields) collectDesc(owners, f.desc);
        for (MethodNode m : cn.methods) collectDesc(owners, m.desc);
        if (cn.superName != null) owners.add(cn.superName);
        owners.addAll(cn.interfaces);
        return owners;
    }

    /**
     * Adds an owner name, mapping an array descriptor to the class of its element type.
     * Verifying members on the array type itself is pointless -- its only member is
     * {@code clone()} -- so the element class is recorded as a plain type reference.
     */
    private static void addOwner(Set<String> owners, String owner) {
        if (owner.startsWith("[")) collectDesc(owners, owner);
        else owners.add(owner);
    }

    private static Set<String> membersOf(ClassNode cn, String owner) {
        Set<String> out = new TreeSet<>();
        // java.lang.invoke.VarHandle is a shape-only type: every access goes through a
        // signature-polymorphic method whose descriptor is call-site specific.
        if ("java/lang/invoke/VarHandle".equals(owner)) return out;
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    // MethodHandle.invoke/invokeExact are signature-polymorphic as well: the
                    // descriptor in the constant pool describes the call site, never the
                    // declared (native varargs) member. Our own transformClassBytes emits
                    // such a call, so this is not a hypothetical case.
                    if (mi.owner.equals("java/lang/invoke/MethodHandle")
                        && (mi.name.equals("invoke") || mi.name.equals("invokeExact"))) continue;
                    if (mi.owner.equals(owner)) {
                        // flags: S = INVOKESTATIC site, I = INVOKEINTERFACE site,
                        // E = INVOKESPECIAL site, - = INVOKEVIRTUAL site
                        int op = mi.getOpcode();
                        String flag = op == Opcodes.INVOKESTATIC ? "S"
                            : op == Opcodes.INVOKEINTERFACE ? "I"
                            : op == Opcodes.INVOKESPECIAL ? "E" : "-";
                        out.add("M " + flag + mi.name + " " + mi.desc);
                    }
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fi = (FieldInsnNode) insn;
                    if (fi.owner.equals(owner)) {
                        boolean stat = fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.PUTSTATIC;
                        out.add("F " + (stat ? "S" : "-") + fi.name + " " + fi.desc);
                    }
                }
            }
        }
        return out;
    }

    private static void collectDesc(Set<String> owners, String desc) {
        if (desc == null) return;
        for (Matcher m = Pattern.compile("L([^;]+);").matcher(desc); m.find(); ) owners.add(m.group(1));
    }

    private static void collectType(Set<String> owners, Type t) {
        try {
            if (t.getSort() == Type.OBJECT) owners.add(t.getInternalName());
            else if (t.getSort() == Type.ARRAY) owners.add(t.getElementType().getInternalName());
            else if (t.getSort() == Type.METHOD) collectDesc(owners, t.getDescriptor());
        } catch (Throwable ignored) {
        }
    }

    /** A member found somewhere in the owner's hierarchy. */
    private static final class Found {
        final String declaringClass;
        final int access;

        Found(String declaringClass, int access) {
            this.declaringClass = declaringClass;
            this.access = access;
        }
    }

    /** @return null when the reference resolves, otherwise a description of the gap. */
    private static String resolve(String owner, String member) {
        boolean isMethod = member.charAt(0) == 'M';
        String spec = member.substring(2);
        int sp = spec.indexOf(' ');
        String flag = spec.substring(0, 1);
        String name = spec.substring(1, sp);
        String desc = spec.substring(sp + 1);

        if (isMethod && "-".equals(flag) && isInterface(owner)) {
            // A virtual call site whose constant-pool owner is an interface: resolution
            // raises IncompatibleClassChangeError on the device (JVMS 5.4.3.3).
            return "virtual call on interface owner: " + owner + "." + name + " " + desc;
        }

        boolean[] reachedPlatform = new boolean[1];
        Found found = findDeclaring(owner, name, desc, isMethod, reachedPlatform);
        if (found == null && isMethod) {
            String d8Desc = d8CovariantDesc(owner, name, desc);
            if (d8Desc != null) {
                found = findDeclaring(owner, name, d8Desc, isMethod, reachedPlatform);
            }
        }
        if (found != null) {
            return shapeMismatch(owner, name, desc, flag, found.access, found.declaringClass);
        }
        if (reachedPlatform[0]) return null;
        if (!provided.containsKey(owner)) return "class missing: " + owner;
        return (isMethod ? "method missing: " : "field missing: ") + owner + "." + name + " " + desc;
    }

    /**
     * Walks {@code owner} (superclasses and interfaces) for a matching member.
     *
     * @param reachedPlatform set to true when a {@code java.*} type outside the provided
     *                        set was met, which means the Android platform supplies it and
     *                        the reference cannot be checked here.
     */
    private static Found findDeclaring(String owner, String name, String desc, boolean isMethod,
                                       boolean[] reachedPlatform) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(owner);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!seen.add(cur)) continue;
            byte[] bytes = provided.get(cur);
            if (bytes == null) {
                // a java.* type we do not stub is supplied by the Android platform
                if (cur.startsWith("java/")) reachedPlatform[0] = true;
                continue;
            }
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (isMethod) {
                for (MethodNode m : cn.methods) {
                    if (m.name.equals(name) && m.desc.equals(desc)) return new Found(cur, m.access);
                }
            } else {
                for (FieldNode f : cn.fields) {
                    if (f.name.equals(name) && f.desc.equals(desc)) return new Found(cur, f.access);
                }
            }
            if (cn.superName != null) queue.add(cn.superName);
            queue.addAll(cn.interfaces);
        }
        return null;
    }

    /**
     * The java.nio.Buffer-returning form of a covariant-return method, or null.
     *
     * d8 rewrites the OpenJDK-9+ covariant returns of the {@code java.nio.Buffer} family
     * to the signature the platform declares (verified against the shipped dex:
     * {@code java/nio/ByteBuffer;.position:(I)Ljava/nio/Buffer;}). The descriptor the jar
     * carries is therefore never the one that ships, so it is not a gap.
     */
    private static String d8CovariantDesc(String owner, String name, String desc) {
        if (!owner.startsWith("java/nio/") || !owner.endsWith("Buffer")) return null;
        if (!D8_COVARIANT_METHODS.contains(name)) return null;
        if (!desc.endsWith("L" + owner + ";")) return null;
        int close = desc.indexOf(')');
        if (close < 0) return null;
        return desc.substring(0, close + 1) + "Ljava/nio/Buffer;";
    }

    /** The java.nio.Buffer methods whose return type became covariant in Java 9. */
    private static final Set<String> D8_COVARIANT_METHODS = new HashSet<>(Arrays.asList(
        "position", "limit", "mark", "reset", "clear", "flip", "rewind"));

    /**
     * A member found in the hierarchy is only usable when the call site's shape matches it.
     *
     * The rules are JVMS 5.4.3.3/5.4.3.4:
     *  - INVOKESTATIC (S) requires a static member, and is legal on an interface owner
     *    (Java 8+ static interface methods).
     *  - INVOKEINTERFACE (I) requires the member to be declared by an interface -- except
     *    for the public {@code java.lang.Object} methods every interface implicitly
     *    declares (JVMS 9.2), e.g. {@code Editable.toString()}.
     *  - INVOKEVIRTUAL/INVOKESPECIAL (-/E) require a non-static member; resolving to a
     *    *superinterface* method is legal when the constant-pool owner is a class (that is
     *    how e.g. LinkedList.isEmpty ends up in java/util/List), so interface-ness of the
     *    declaring class is not an error here.
     */
    private static String shapeMismatch(String owner, String name, String desc, String flag,
                                        int access, String declaringClass) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        boolean wantStatic = "S".equals(flag);
        if (wantStatic != isStatic) {
            return (wantStatic ? "not static: " : "unexpectedly static: ") + owner + "." + name + " " + desc;
        }
        if (!"I".equals(flag) || isInterface(declaringClass)) {
            return null;
        }
        if ("java/lang/Object".equals(declaringClass) && (access & Opcodes.ACC_PUBLIC) != 0) {
            return null;
        }
        return "not an interface: " + owner + "." + name + " " + desc;
    }

    private static boolean isInterface(String owner) {
        byte[] bytes = provided.get(owner);
        if (bytes == null) return false;
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
            | ClassReader.SKIP_FRAMES);
        return (cn.access & Opcodes.ACC_INTERFACE) != 0;
    }

    private static void load(Map<String, byte[]> into, File f) throws IOException {
        if (!f.exists()) {
            System.out.println("# warn: provided/use path does not exist: " + f);
            return;
        }
        if (f.isDirectory()) {
            loadDir(into, f, f);
            return;
        }
        try (ZipFile zip = new ZipFile(f)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (!e.getName().endsWith(".class")) continue;
                try (InputStream in = zip.getInputStream(e)) {
                    into.put(e.getName().substring(0, e.getName().length() - ".class".length()), in.readAllBytes());
                }
            }
        }
    }

    /** Directory trees are indexed by their path relative to {@code root} (the class dir). */
    private static void loadDir(Map<String, byte[]> into, File root, File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children);
        for (File c : children) {
            if (c.isDirectory()) {
                loadDir(into, root, c);
            } else if (c.getName().endsWith(".class")) {
                String rel = root.toPath().relativize(c.toPath()).toString().replace(File.separatorChar, '/');
                into.put(rel.substring(0, rel.length() - ".class".length()), Files.readAllBytes(c.toPath()));
            }
        }
    }

    private HostLinkCheck() {
    }
}
