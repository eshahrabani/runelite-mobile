import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Constant-pool reference scanner for jars/dirs of .class files.
 *
 *   refs <jar...>                     list referenced owners (desc sorted count)
 *   api  <jar-to-index...> --use <jar...>   report refs from --use jars missing in indexed set
 */
public class RefScan {
    static Map<String, byte[]> index = new LinkedHashMap<>();   // internal name -> class bytes
    static Map<String, String> rawClass = new LinkedHashMap<>(); // owner -> raw descriptor (from refs)

    static void loadInto(Map<String, byte[]> map, File f) throws IOException {
        if (f.isDirectory()) {
            for (File c : list(f)) loadInto(map, c);
            return;
        }
        if (f.getName().endsWith(".class")) { map.put(f.getName().replaceAll("\\.class$", ""), read(f)); return; }
        try (ZipFile z = new ZipFile(f)) {
            Enumeration<? extends ZipEntry> e = z.entries();
            while (e.hasMoreElements()) {
                ZipEntry en = e.nextElement();
                if (en.getName().endsWith(".class")) map.put(en.getName().replaceAll("\\.class$", ""), z.getInputStream(en).readAllBytes());
            }
        }
    }
    static List<File> list(File d) { File[] fs = d.listFiles(); Arrays.sort(fs); return fs == null ? new ArrayList<>() : Arrays.asList(fs); }
    static byte[] read(File f) throws IOException { try (FileInputStream in = new FileInputStream(f)) { return in.readAllBytes(); } }

    /** referenced (owner -> set of "name desc") for methods, (owner -> set of "name desc") for fields, plus class refs */
    static class Refs {
        Map<String, Set<String>> methods = new TreeMap<>();
        Map<String, Set<String>> fields = new TreeMap<>();
        Set<String> classes = new TreeSet<>();
    }

    static boolean withSource = false;

    static Refs scan(Map<String, byte[]> classes) {
        Refs r = new Refs();
        for (Map.Entry<String, byte[]> en : classes.entrySet()) {
            current = en.getKey();
            ClassNode cn = new ClassNode();
            new ClassReader(en.getValue()).accept(cn, ClassReader.SKIP_DEBUG);
            for (MethodNode m : cn.methods) {
                System.out.println("DECL\t" + cn.name + "\tM " + m.name + " " + m.desc);
            }
            for (FieldNode f : cn.fields) {
                System.out.println("DECL\t" + cn.name + "\tF " + f.name + " " + f.desc);
            }
            System.out.println("HIER\t" + cn.name + "\t" + (cn.superName == null ? "-" : cn.superName)
                + "\t" + String.join(",", cn.interfaces));
            if (cn.superName != null) r.classes.add(cn.superName);
            for (String i : cn.interfaces) r.classes.add(i);
            refDesc(r, cn.signature);
            for (FieldNode f : cn.fields) {
                refDesc(r, f.desc);
                refSig(r, f.signature);
            }
            for (MethodNode m : cn.methods) {
                refDesc(r, m.desc);
                refSig(r, m.signature);
                for (String ex : m.exceptions) r.classes.add(ex);
                for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode mi = (MethodInsnNode) insn;
                        // The flag records how the member is *used*: a stub must be static
                        // for an INVOKESTATIC site and an interface method for
                        // INVOKEINTERFACE, or ART throws IncompatibleClassChangeError.
                        add(r.methods, mi.owner, mi.name + " " + flag(mi.getOpcode(), Opcodes.INVOKESTATIC) + mi.desc);
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fi = (FieldInsnNode) insn;
                        boolean stat = fi.getOpcode() == Opcodes.GETSTATIC || fi.getOpcode() == Opcodes.PUTSTATIC;
                        add(r.fields, fi.owner, fi.name + " " + (stat ? "S" : "-") + fi.desc);
                    } else if (insn instanceof TypeInsnNode) {
                        r.classes.add(((TypeInsnNode) insn).desc);
                    } else if (insn instanceof MultiANewArrayInsnNode) {
                        refDesc(r, ((MultiANewArrayInsnNode) insn).desc);
                    } else if (insn instanceof LdcInsnNode) {
                        Object c = ((LdcInsnNode) insn).cst;
                        if (c instanceof Type) refType(r, (Type) c);
                        else if (c instanceof Handle) r.classes.add(((Handle) c).getOwner());
                        else if (c instanceof ConstantDynamic) refType(r, Type.getType(((ConstantDynamic) c).getDescriptor()));
                    } else if (insn instanceof InvokeDynamicInsnNode) {
                        InvokeDynamicInsnNode id = (InvokeDynamicInsnNode) insn;
                        r.classes.add(id.bsm.getOwner());
                        refDesc(r, id.desc);
                        for (Object a : id.bsmArgs) {
                            if (a instanceof Type) refType(r, (Type) a);
                            else if (a instanceof Handle) r.classes.add(((Handle) a).getOwner());
                            else if (a instanceof ConstantDynamic) refType(r, Type.getType(((ConstantDynamic) a).getDescriptor()));
                        }
                    }
                }
                for (TryCatchBlockNode tb : m.tryCatchBlocks) if (tb.type != null) r.classes.add(tb.type);
                for (LocalVariableNode lv : m.localVariables == null ? java.util.Collections.<LocalVariableNode>emptyList() : m.localVariables) refDesc(r, lv.desc);
            }
            for (InnerClassNode ic : cn.innerClasses) r.classes.add(ic.name);
        }
        return r;
    }

    static void refType(Refs r, Type t) {
        try {
            switch (t.getSort()) {
                case Type.OBJECT: r.classes.add(t.getInternalName()); break;
                case Type.ARRAY: r.classes.add(t.getElementType().getInternalName()); break;
                case Type.METHOD: refDesc(r, t.getDescriptor()); break;
                default: break;
            }
        } catch (Throwable ignored) {
        }
    }
    /** Generic signatures only contribute class references (T-types are not classes). */
    static void refSig(Refs r, String d) {
        if (d == null) return;
        for (java.util.regex.Matcher m = java.util.regex.Pattern.compile("L([^;]+);").matcher(d); m.find(); ) {
            r.classes.add(m.group(1));
        }
    }

    static void refDesc(Refs r, String d) {
        if (d == null) return;
        try { for (Type t : Type.getArgumentTypes(d)) refType(r, t); } catch (Throwable ignored) {}
        try { refType(r, Type.getReturnType(d)); } catch (Throwable ignored) {}
        try { for (Type t : Type.getArgumentTypes(Type.getMethodType(d).getDescriptor())) refType(r, t); } catch (Throwable ignored) {}
    }
    static String current = "";

    /** {@code "S"} when the opcode is the given static one, {@code "-"} otherwise. */
    static String flag(int opcode, int staticOpcode) {
        return opcode == staticOpcode ? "S" : "-";
    }


    static void add(Map<String, Set<String>> m, String owner, String member) {
        m.computeIfAbsent(owner, k -> new TreeSet<>()).add(withSource ? member + " <- " + current : member);
    }

    /** does the indexed class set contain owner + member (walking supertypes)? */
    static String missingMember(Map<String, byte[]> classes, String owner, String member) {
        int sp = member.indexOf(' ');
        String n = member.substring(0, sp), d = member.substring(sp + 1);
        boolean isMethod = d.contains("(");
        Set<String> seen = new HashSet<>();
        List<String> queue = new ArrayList<>();
        queue.add(owner);
        boolean reachedJdk = false;
        while (!queue.isEmpty()) {
            String cur = queue.remove(0);
            if (!seen.add(cur)) continue;
            if (cur.startsWith("java/") && !classes.containsKey(cur)) { reachedJdk = true; continue; }
            byte[] b = classes.get(cur);
            if (b == null) continue;
            ClassNode cn = new ClassNode();
            new ClassReader(b).accept(cn, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (isMethod) { for (MethodNode m : cn.methods) if (m.name.equals(n) && m.desc.equals(d)) return null; }
            else { for (FieldNode f : cn.fields) if (f.name.equals(n) && f.desc.equals(d)) return null; }
            if (cn.superName != null) queue.add(cn.superName);
            queue.addAll(cn.interfaces);
        }
        if (reachedJdk) return null;   // member inherited from a platform class the app does not stub
        return (isMethod ? "method " : "field ") + owner + "." + n + " " + d;
    }

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (mode.equals("refs") || mode.equals("members")) {
            Map<String, byte[]> use = new LinkedHashMap<>();
            Set<String> only = new HashSet<>();
            for (int i = 1; i < args.length; i++) {
                if (args[i].equals("--only")) { i++; for (String q : args[i].split(",")) only.add(q); continue; }
                if (args[i].equals("--withsource")) { withSource = true; continue; }
                loadInto(use, new File(args[i]));
            }
            Refs r = scan(use);
            if (mode.equals("members")) {
                for (Map.Entry<String, Set<String>> e : r.methods.entrySet()) {
                    if (!selected(e.getKey(), only)) continue;
                    for (String m : e.getValue()) System.out.println("M " + e.getKey() + "." + m);
                }
                for (Map.Entry<String, Set<String>> e : r.fields.entrySet()) {
                    if (!selected(e.getKey(), only)) continue;
                    for (String m : e.getValue()) System.out.println("F " + e.getKey() + "." + m);
                }
                for (String c : r.classes) if (selected(c, only)) System.out.println("C " + c);
                return;
            }
            Map<String, Integer> counts = new TreeMap<>();
            List<String> all = new ArrayList<>(r.methods.keySet());
            all.addAll(r.fields.keySet());
            all.addAll(r.classes);
            for (String o : all) {
                if (!only.isEmpty() && !only.stream().anyMatch(o::startsWith)) continue;
                counts.merge(o, 1, Integer::sum);
            }
            counts.forEach((k, v) -> System.out.println(k + " " + v));
            return;
        }
        if (mode.equals("api")) {
            Map<String, byte[]> provided = new LinkedHashMap<>();
            Map<String, byte[]> use = new LinkedHashMap<>();
            List<String> only = new ArrayList<>();
            boolean inUse = false;
            for (int i = 1; i < args.length; i++) {
                if (args[i].equals("--use")) { inUse = true; continue; }
                if (args[i].equals("--only")) { i++; for (String q : args[i].split(",")) only.add(q); continue; }
                if (args[i].equals("--withsource")) { withSource = true; continue; }
                loadInto(inUse ? use : provided, new File(args[i]));
            }
            Refs r = scan(use);
            int missing = 0;
            List<String> out = new ArrayList<>();
            for (Map.Entry<String, Set<String>> e : r.methods.entrySet()) {
                if (!selected(e.getKey(), only)) continue;
                for (String m : e.getValue()) { String res = missingMember(provided, e.getKey(), m); if (res != null) out.add(res); }
            }
            for (Map.Entry<String, Set<String>> e : r.fields.entrySet()) {
                if (!selected(e.getKey(), only)) continue;
                for (String m : e.getValue()) { String res = missingMember(provided, e.getKey(), m); if (res != null) out.add(res); }
            }
            for (String c : r.classes) {
                if (!selected(c, only)) continue;
                if (!provided.containsKey(c)) out.add("class " + c);
            }
            Collections.sort(out);
            for (String s : out) System.out.println(s);
            System.out.println("# missing: " + out.size() + "  (provided classes: " + provided.size() + ")");
            return;
        }
        System.out.println("usage: refs|api");
    }
    /** a reference is checked when it is a JDK/platform package we are expected to provide */
    static boolean selected(String owner, java.util.Collection<String> only) {
        if (only.isEmpty()) return true;
        for (String p : only) if (owner.startsWith(p)) return true;
        return false;
    }
}
