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
 *                      [--ignore owner-substring]...
 *
 * --provided : everything that is on the class path at runtime (app dex classes, the
 *              RuneLite runtime jars, the asset jars).
 * --use      : the classes whose references are checked (the cleaned client jars and our
 *              own compiled app classes).
 * --prefix   : only owners starting with one of these are checked; the default is the JDK
 *              surface this repo has to re-implement for Android.
 */
public final class HostLinkCheck {

    private static final String[] DEFAULT_PREFIXES = {
        "java/awt/", "java/beans/", "java/applet/", "java/lang/management/",
        "javax/swing/", "javax/sound/", "javax/imageio/", "javax/management/",
        "javax/accessibility/", "com/sun/net/", "com/sun/management/", "netscape/",
    };

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

        List<File> target = providedPaths;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--provided": target = providedPaths; break;
                case "--use": target = usePaths; break;
                case "--prefix": prefixes = Arrays.asList(args[++i].split(",")); break;
                case "--ignore": ignores.add(args[++i]); break;
                default: target.add(new File(args[i]));
            }
        }

        for (File f : providedPaths) load(provided, f);
        for (File f : usePaths) load(used, f);

        List<String> missing = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : used.entrySet()) {
            ClassNode cn = new ClassNode();
            new ClassReader(e.getValue()).accept(cn, ClassReader.SKIP_DEBUG);
            for (String owner : referencedOwners(cn)) {
                if (!selected(owner, prefixes, ignores)) continue;
                for (String member : membersOf(cn, owner)) {
                    String problem = resolve(owner, member);
                    if (problem != null) missing.add(problem);
                }
            }
        }

        Collections.sort(missing);
        for (String m : missing) System.out.println(m);
        System.out.println("# missing: " + missing.size() + " (provided classes: " + provided.size()
            + ", checked classes: " + used.size() + ")");
        if (!missing.isEmpty()) System.exit(1);
    }

    private static boolean selected(String owner, List<String> prefixes, List<String> ignores) {
        for (String p : prefixes) {
            if (owner.startsWith(p)) {
                for (String i : ignores) if (owner.contains(i)) return false;
                return true;
            }
        }
        return false;
    }

    /** Every owner this class refers to (member owners + type references). */
    private static Set<String> referencedOwners(ClassNode cn) {
        Set<String> owners = new TreeSet<>();
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) owners.add(((MethodInsnNode) insn).owner);
                else if (insn instanceof FieldInsnNode) owners.add(((FieldInsnNode) insn).owner);
                else if (insn instanceof TypeInsnNode) owners.add(((TypeInsnNode) insn).desc);
                else if (insn instanceof MultiANewArrayInsnNode) collectDesc(owners, ((MultiANewArrayInsnNode) insn).desc);
                else if (insn instanceof LdcInsnNode) {
                    Object c = ((LdcInsnNode) insn).cst;
                    if (c instanceof Type) collectType(owners, (Type) c);
                    else if (c instanceof Handle) owners.add(((Handle) c).getOwner());
                } else if (insn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode id = (InvokeDynamicInsnNode) insn;
                    owners.add(id.bsm.getOwner());
                    for (Object a : id.bsmArgs) {
                        if (a instanceof Type) collectType(owners, (Type) a);
                        else if (a instanceof Handle) owners.add(((Handle) a).getOwner());
                    }
                }
            }
            for (TryCatchBlockNode t : m.tryCatchBlocks) if (t.type != null) owners.add(t.type);
        }
        for (FieldNode f : cn.fields) collectDesc(owners, f.desc);
        for (MethodNode m : cn.methods) collectDesc(owners, m.desc);
        if (cn.superName != null) owners.add(cn.superName);
        owners.addAll(cn.interfaces);
        return owners;
    }

    private static Set<String> membersOf(ClassNode cn, String owner) {
        Set<String> out = new TreeSet<>();
        for (MethodNode m : cn.methods) {
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode mi = (MethodInsnNode) insn;
                    if (mi.owner.equals(owner)) {
                        // flags: S = INVOKESTATIC site, I = INVOKEINTERFACE site, - = virtual
                        String flag = mi.getOpcode() == Opcodes.INVOKESTATIC ? "S"
                            : mi.getOpcode() == Opcodes.INVOKEINTERFACE ? "I" : "-";
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

    /** @return null when the reference resolves, otherwise a description of the gap. */
    private static String resolve(String owner, String member) {
        boolean isMethod = member.charAt(0) == 'M';
        String spec = member.substring(2);
        int sp = spec.indexOf(' ');
        String flag = spec.substring(0, 1);
        String name = spec.substring(1, sp);
        String desc = spec.substring(sp + 1);

        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(owner);
        boolean reachedPlatform = false;
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (!seen.add(cur)) continue;
            byte[] bytes = provided.get(cur);
            if (bytes == null) {
                // a java.* type we do not stub is supplied by the Android platform
                if (cur.startsWith("java/")) reachedPlatform = true;
                continue;
            }
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (isMethod) {
                for (MethodNode m : cn.methods) {
                    if (m.name.equals(name) && m.desc.equals(desc)) {
                        return staticMismatch(owner, name, desc, "S".equals(flag), m.access,
                            cn.access, "I".equals(flag));
                    }
                }
            } else {
                for (FieldNode f : cn.fields) {
                    if (f.name.equals(name) && f.desc.equals(desc)) {
                        return staticMismatch(owner, name, desc, "S".equals(flag), f.access, 0, false);
                    }
                }
            }
            if (cn.superName != null) queue.add(cn.superName);
            queue.addAll(cn.interfaces);
        }
        if (reachedPlatform) return null;
        if (!provided.containsKey(owner)) return "class missing: " + owner;
        return (isMethod ? "method missing: " : "field missing: ") + owner + "." + name + " " + desc;
    }

    /** A member found in the hierarchy is only usable when its static/interface shape matches the call site. */
    private static String staticMismatch(String owner, String name, String desc, boolean wantStatic,
                                        int access, int ownerAccess, boolean wantInterface) {
        boolean isStatic = (access & Opcodes.ACC_STATIC) != 0;
        if (wantStatic != isStatic) {
            return (wantStatic ? "not static: " : "unexpectedly static: ") + owner + "." + name + " " + desc;
        }
        boolean isInterface = (ownerAccess & Opcodes.ACC_INTERFACE) != 0;
        if (wantInterface != isInterface) {
            return (wantInterface ? "not an interface: " : "unexpectedly an interface: ")
                + owner + "." + name + " " + desc;
        }
        return null;
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
