import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Dumps the local JDK's real shape (kind, modifiers, super, interfaces, public fields, enum constants) for a list of internal names. */
public class JdkInfo {
    static String n(Class<?> c) { return c == null ? "-" : c.getName().replace('.', '/'); }
    public static void main(String[] args) throws Exception {
        for (String raw : Files.readAllLines(Paths.get(args[0]))) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String internal = line.replace('.', '/');
            try {
                Class<?> c = Class.forName(internal.replace('/', '.'), false, JdkInfo.class.getClassLoader());
                StringBuilder sb = new StringBuilder("TYPE\t").append(internal).append('\t')
                    .append(c.isAnnotation() ? "annotation" : c.isEnum() ? "enum" : c.isInterface() ? "interface" : "class").append('\t')
                    .append(Modifier.isAbstract(c.getModifiers()) ? "abstract" : "-").append('\t')
                    .append(Modifier.isFinal(c.getModifiers()) ? "final" : "-").append('\t')
                    .append(Modifier.isStatic(c.getModifiers()) ? "static" : "-").append('\t')
                    .append(n(c.getSuperclass())).append('\t');
                List<String> itf = new ArrayList<>();
                for (Class<?> i : c.getInterfaces()) itf.add(n(i));
                sb.append(String.join(",", itf)).append('\t')
                  .append(c.getEnclosingClass() == null ? "-" : n(c.getEnclosingClass()));
                System.out.println(sb);
                for (Field f : c.getFields()) {
                    if (f.isSynthetic()) continue;
                    String val = null;
                    try {
                        Object v = f.get(null);
                        Class<?> t = f.getType();
                        if (t == int.class || t == short.class || t == byte.class) val = String.valueOf(((Number) v).intValue());
                        else if (t == long.class) val = v + "L";
                        else if (t == float.class) val = v + "f";
                        else if (t == double.class) val = v + "d";
                        else if (t == boolean.class) val = String.valueOf(v);
                        else if (t == char.class) val = "'" + (v.equals('\'') ? "\\'" : v.equals('\\') ? "\\\\" : v.toString()) + "'";
                        else if (t == String.class) val = "\"" + ((String) v).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
                    } catch (Throwable ignored) {
                    }
                    System.out.println("FIELD\t" + internal + "\t" + f.getName() + "\t" + n(f.getType()) + "\t"
                        + (Modifier.isStatic(f.getModifiers()) ? "static" : "-") + "\t"
                        + (Modifier.isFinal(f.getModifiers()) ? "final" : "-") + "\t"
                        + (val == null ? "-" : val));
                }
                if (c.isEnum()) for (Object o : c.getEnumConstants()) System.out.println("ENUM\t" + internal + "\t" + ((Enum<?>) o).name());
                java.util.Set<String> seen = new java.util.HashSet<>();
                java.util.List<Class<?>> hierarchy = new java.util.ArrayList<>();
                java.util.ArrayDeque<Class<?>> todo = new java.util.ArrayDeque<>();
                todo.add(c);
                while (!todo.isEmpty()) {
                    Class<?> k = todo.poll();
                    if (k == null || k == Object.class || hierarchy.contains(k)) continue;
                    hierarchy.add(k);
                    if (k.getSuperclass() != null) todo.add(k.getSuperclass());
                    for (Class<?> i : k.getInterfaces()) todo.add(i);
                }
                java.util.Set<String> concrete = new java.util.HashSet<>();
                for (Class<?> k : hierarchy) {
                    if (k.isInterface() || k == c) continue;
                    for (Method m : k.getDeclaredMethods()) {
                        if (!Modifier.isAbstract(m.getModifiers())) {
                            concrete.add(m.getName() + org.objectweb.asm.Type.getMethodDescriptor(m));
                        }
                    }
                }
                for (Class<?> k : hierarchy) {
                    for (Method m : k.getDeclaredMethods()) {
                        if (!Modifier.isAbstract(m.getModifiers()) || Modifier.isStatic(m.getModifiers())) continue;
                        if (!(Modifier.isPublic(m.getModifiers()) || Modifier.isProtected(m.getModifiers()))) continue;
                        String key = m.getName() + org.objectweb.asm.Type.getMethodDescriptor(m);
                        if (concrete.contains(key)) continue;
                        if (seen.add(key)) System.out.println("ABSTRACT\t" + internal + "\t" + m.getName() + "\t" + org.objectweb.asm.Type.getMethodDescriptor(m));
                    }
                }
                for (Constructor<?> ct : c.getDeclaredConstructors()) {
                    if (Modifier.isPrivate(ct.getModifiers())) continue;
                    System.out.println("CTOR\t" + internal + "\t" + org.objectweb.asm.Type.getConstructorDescriptor(ct));
                }
            } catch (Throwable t) {
                System.out.println("MISSING\t" + internal);
            }
        }
    }
}
