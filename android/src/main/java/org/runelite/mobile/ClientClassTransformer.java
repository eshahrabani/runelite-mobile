package org.runelite.mobile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Bytecode transform applied to RuneLite's injected client so it can run on
 * Android ART instead of a desktop JVM. Java port of the transform embedded in
 * android/build.gradle (downloadAndDexJar) - KEEP BOTH IN SYNC.
 *
 * What it does:
 *  - strips Java 9+ makeConcatWithConstants invokedynamic string concatenation
 *  - remaps java/lang/ProcessHandle(+$Info) -> org/runelite/mobile/ProcessHandle
 *  - remaps sun/misc/Unsafe ARRAY_* offsets -> org/runelite/mobile/UnsafeHelper
 *  - remaps sun/misc/Unsafe.copyMemory calls -> UnsafeHelper.copyMemory
 */
public final class ClientClassTransformer {

    private ClientClassTransformer() {}

    public static byte[] transformClassBytes(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);

        boolean modified = false;

        // Inject the scene snapshot into the in-game frame loop: client.ij calls
        // gp.az (the 3D scene render) and then draws the 2D UI on top. The hook
        // runs right AFTER the scene render (read-only diagnostic snapshot of
        // the display buffer at the moment the scene has drawn, before the UI
        // pass). Kept in sync with the transform in build.gradle.
        if ("client".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("ij".equals(method.name) && "(I)V".equals(method.desc)) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.getOpcode() == Opcodes.INVOKEVIRTUAL
                                && "gp".equals(min.owner) && "az".equals(min.name)
                                && "(IILvv;III)V".equals(min.desc)) {
                                InsnList inj = new InsnList();
                                inj.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                inj.add(new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "org/runelite/mobile/TileCompositor",
                                    "composite",
                                    "(Ljava/lang/Object;)V",
                                    false));
                                AbstractInsnNode next = min.getNext();
                                if (next == null) {
                                    method.instructions.add(inj);
                                } else {
                                    method.instructions.insertBefore(next, inj);
                                }
                                modified = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if ("client".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("ij".equals(method.name) && "(I)V".equals(method.desc)) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode) {
                            MethodInsnNode min = (MethodInsnNode) insn;
                            if (min.getOpcode() == Opcodes.INVOKEVIRTUAL
                                && "client".equals(min.owner) && "iz".equals(min.name)
                                && "(I)V".equals(min.desc)) {
                                InsnList inj = new InsnList();
                                inj.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                inj.add(new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "org/runelite/mobile/TileCompositor",
                                    "compositeEnd",
                                    "(Ljava/lang/Object;)V",
                                    false));
                                AbstractInsnNode next = min.getNext();
                                if (next == null) {
                                    method.instructions.add(inj);
                                } else {
                                    method.instructions.insertBefore(next, inj);
                                }
                                modified = true;
                                break;
                            }
                        }
                    }
                }
            }
        }

        if ("fu".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("ax".equals(method.name) && "(DILva;)Z".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceTileEnter",
                        "(Ljava/lang/Object;)V",
                        false));
                    method.instructions.insert(inj);
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof JumpInsnNode
                            && insn.getOpcode() == Opcodes.IFNONNULL
                            && ((JumpInsnNode) insn).label != null) {
                            InsnList inj2 = new InsnList();
                            inj2.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            inj2.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "org/runelite/mobile/TileCompositor",
                                "traceTileRender",
                                "(Ljava/lang/Object;)V",
                                false));
                            method.instructions.insert(((JumpInsnNode) insn).label, inj2);
                            modified = true;
                            break;
                        }
                    }
                    modified = true;
                }
                if ("mx".equals(method.name) && "(Lfu;DILyz;I)Z".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceMxEnter",
                        "(Ljava/lang/Object;)V",
                        false));
                    method.instructions.insert(inj);
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn.getOpcode() == Opcodes.IRETURN) {
                            InsnList inj2 = new InsnList();
                            inj2.add(new InsnNode(Opcodes.DUP));
                            inj2.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "org/runelite/mobile/TileCompositor",
                                "traceMxResult",
                                "(I)V",
                                false));
                            method.instructions.insertBefore(insn, inj2);
                            modified = true;
                        }
                    }
                    modified = true;
                }
            }
        }

        if ("gp".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("az".equals(method.name) && "(IILvv;III)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    inj.add(new VarInsnNode(Opcodes.ILOAD, 2));
                    inj.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceGate",
                        "(IILjava/lang/Object;)V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
                if ("em".equals(method.name) && "(Lgp;[Llw;IIIIIIIIIILvv;II)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceGpEm",
                        "(Ljava/lang/Object;)V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
            }
        }

        if ("yw".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("es".equals(method.name) && "(IIII)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new VarInsnNode(Opcodes.ILOAD, 0));
                    inj.add(new VarInsnNode(Opcodes.ILOAD, 1));
                    inj.add(new VarInsnNode(Opcodes.ILOAD, 2));
                    inj.add(new VarInsnNode(Opcodes.ILOAD, 3));
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceYwEs",
                        "(IIII)V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
            }
        }

        if ("ff".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("aj".equals(method.name) && "(FFFFFFFFFIIIIIIIIIIIII)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceFqAj",
                        "()V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
                if ("ct".equals(method.name) && "([I[IIIIIIIIFFIIIIII)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceFqCt",
                        "()V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
                if ("bh".equals(method.name) && "([I[FIIIF)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceFfBh",
                        "()V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
            }
        }

        if ("client".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("dg".equals(method.name) && "(Lzv;Lzv;Lzv;)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "traceDg",
                        "()V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
            }
        }

        if ("tg".equals(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ("af".equals(method.name) && "(IIB)V".equals(method.desc)) {
                    InsnList inj = new InsnList();
                    inj.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    inj.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "org/runelite/mobile/TileCompositor",
                        "tracePresent",
                        "(Ljava/lang/Object;)V",
                        false));
                    method.instructions.insert(inj);
                    modified = true;
                }
            }
        }

        if (classNode.signature != null) {
            if (classNode.signature.contains("Ljava/lang/ProcessHandle;")) {
                modified = true;
                classNode.signature = classNode.signature.replace("Ljava/lang/ProcessHandle;", "Lorg/runelite/mobile/ProcessHandle;");
            }
            if (classNode.signature.contains("Ljava/lang/ProcessHandle$Info;")) {
                modified = true;
                classNode.signature = classNode.signature.replace("Ljava/lang/ProcessHandle$Info;", "Lorg/runelite/mobile/ProcessHandle$Info;");
            }
        }

        for (FieldNode field : classNode.fields) {
            if (field.desc != null && field.desc.contains("Ljava/lang/ProcessHandle;")) {
                modified = true;
                field.desc = field.desc.replace("Ljava/lang/ProcessHandle;", "Lorg/runelite/mobile/ProcessHandle;");
            }
            if (field.desc != null && field.desc.contains("Ljava/lang/ProcessHandle$Info;")) {
                modified = true;
                field.desc = field.desc.replace("Ljava/lang/ProcessHandle$Info;", "Lorg/runelite/mobile/ProcessHandle$Info;");
            }
        }

        for (MethodNode method : classNode.methods) {
            if (method.desc != null && method.desc.contains("Ljava/lang/ProcessHandle;")) {
                modified = true;
                method.desc = method.desc.replace("Ljava/lang/ProcessHandle;", "Lorg/runelite/mobile/ProcessHandle;");
            }
            if (method.desc != null && method.desc.contains("Ljava/lang/ProcessHandle$Info;")) {
                modified = true;
                method.desc = method.desc.replace("Ljava/lang/ProcessHandle$Info;", "Lorg/runelite/mobile/ProcessHandle$Info;");
            }

            InsnList instructions = method.instructions;
            AbstractInsnNode[] insnArray = instructions.toArray();
            for (AbstractInsnNode insn : insnArray) {
                if (insn instanceof InvokeDynamicInsnNode) {
                    InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) insn;
                    Handle bsm = indy.bsm;
                    if (bsm.getOwner().equals("java/lang/invoke/StringConcatFactory")
                        && bsm.getName().equals("makeConcatWithConstants")) {
                        modified = true;
                        String recipe = (String) indy.bsmArgs[0];
                        Type[] argTypes = Type.getArgumentTypes(indy.desc);

                        InsnList newInstructions = new InsnList();

                        int[] localSlots = new int[argTypes.length];
                        int currentSlot = method.maxLocals;
                        for (int j = argTypes.length - 1; j >= 0; j--) {
                            Type type = argTypes[j];
                            localSlots[j] = currentSlot;
                            newInstructions.add(new VarInsnNode(type.getOpcode(Opcodes.ISTORE), currentSlot));
                            currentSlot += type.getSize();
                        }
                        method.maxLocals = Math.max(method.maxLocals, currentSlot);

                        newInstructions.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
                        newInstructions.add(new InsnNode(Opcodes.DUP));
                        newInstructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false));

                        StringBuilder literal = new StringBuilder();
                        int dynamicArgIndex = 0;
                        int constantArgIndex = 0;

                        for (int j = 0; j < recipe.length(); j++) {
                            char ch = recipe.charAt(j);
                            if (ch == '\u0001') {
                                if (literal.length() > 0) {
                                    newInstructions.add(new LdcInsnNode(literal.toString()));
                                    newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
                                    literal.setLength(0);
                                }
                                Type type = argTypes[dynamicArgIndex];
                                int slot = localSlots[dynamicArgIndex];
                                newInstructions.add(new VarInsnNode(type.getOpcode(Opcodes.ILOAD), slot));
                                String appendDesc = getAppendDesc(type);
                                newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", appendDesc, false));
                                dynamicArgIndex++;
                            } else if (ch == '\u0002') {
                                if (literal.length() > 0) {
                                    newInstructions.add(new LdcInsnNode(literal.toString()));
                                    newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
                                    literal.setLength(0);
                                }
                                Object constVal = indy.bsmArgs[1 + constantArgIndex];
                                constantArgIndex++;

                                if (constVal instanceof org.objectweb.asm.ConstantDynamic) {
                                    org.objectweb.asm.ConstantDynamic cd = (org.objectweb.asm.ConstantDynamic) constVal;
                                    if (cd.getBootstrapMethod().getOwner().equals("java/lang/invoke/ConstantBootstraps")
                                        && cd.getBootstrapMethod().getName().equals("invoke")) {
                                        Handle targetMethod = (Handle) cd.getBootstrapMethodArgument(0);
                                        for (int k = 1; k < cd.getBootstrapMethodArgumentCount(); k++) {
                                            Object arg = cd.getBootstrapMethodArgument(k);
                                            newInstructions.add(new LdcInsnNode(arg));
                                        }
                                        int opcode = getInvokeOpcode(targetMethod.getTag());
                                        boolean isInterface = targetMethod.getTag() == Opcodes.H_INVOKEINTERFACE;
                                        newInstructions.add(new MethodInsnNode(opcode, targetMethod.getOwner(), targetMethod.getName(), targetMethod.getDesc(), isInterface));
                                    } else {
                                        newInstructions.add(new LdcInsnNode(constVal));
                                    }
                                    newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false));
                                } else {
                                    newInstructions.add(new LdcInsnNode(constVal));
                                    String appendDesc = getConstantAppendDesc(constVal);
                                    newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", appendDesc, false));
                                }
                            } else {
                                literal.append(ch);
                            }
                        }
                        if (literal.length() > 0) {
                            newInstructions.add(new LdcInsnNode(literal.toString()));
                            newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false));
                        }

                        newInstructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false));

                        instructions.insertBefore(indy, newInstructions);
                        instructions.remove(indy);
                    }
                } else if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (ldc.cst instanceof Type) {
                        Type t = (Type) ldc.cst;
                        if (t.getSort() == Type.OBJECT) {
                            if (t.getInternalName().equals("java/lang/ProcessHandle")) {
                                modified = true;
                                ldc.cst = Type.getObjectType("org/runelite/mobile/ProcessHandle");
                            } else if (t.getInternalName().equals("java/lang/ProcessHandle$Info")) {
                                modified = true;
                                ldc.cst = Type.getObjectType("org/runelite/mobile/ProcessHandle$Info");
                            }
                        }
                    }
                    if (ldc.cst instanceof org.objectweb.asm.ConstantDynamic) {
                        org.objectweb.asm.ConstantDynamic cd = (org.objectweb.asm.ConstantDynamic) ldc.cst;
                        if (cd.getBootstrapMethod().getOwner().equals("java/lang/invoke/ConstantBootstraps")
                            && cd.getBootstrapMethod().getName().equals("invoke")) {
                            modified = true;
                            Handle targetMethod = (Handle) cd.getBootstrapMethodArgument(0);
                            InsnList newInstructions = new InsnList();
                            for (int k = 1; k < cd.getBootstrapMethodArgumentCount(); k++) {
                                Object arg = cd.getBootstrapMethodArgument(k);
                                newInstructions.add(new LdcInsnNode(arg));
                            }
                            int opcode = getInvokeOpcode(targetMethod.getTag());
                            boolean isInterface = targetMethod.getTag() == Opcodes.H_INVOKEINTERFACE;
                            newInstructions.add(new MethodInsnNode(opcode, targetMethod.getOwner(), targetMethod.getName(), targetMethod.getDesc(), isInterface));
                            instructions.insertBefore(ldc, newInstructions);
                            instructions.remove(ldc);
                        }
                    }
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    if (fieldInsn.getOpcode() == Opcodes.GETSTATIC
                        && fieldInsn.owner.equals("sun/misc/Unsafe")
                        && fieldInsn.name.startsWith("ARRAY_")
                        && fieldInsn.desc.equals("I")) {
                        modified = true;
                        fieldInsn.owner = "org/runelite/mobile/UnsafeHelper";
                    }
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (methodInsn.owner.equals("sun/misc/Unsafe") && methodInsn.name.equals("copyMemory")) {
                        modified = true;
                        methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                        methodInsn.owner = "org/runelite/mobile/UnsafeHelper";
                        if (methodInsn.desc.equals("(Ljava/lang/Object;JLjava/lang/Object;JJ)V")) {
                            methodInsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;JLjava/lang/Object;JJ)V";
                        } else if (methodInsn.desc.equals("(JJJ)V")) {
                            methodInsn.desc = "(Ljava/lang/Object;JJJ)V";
                        }
                    } else {
                        if (methodInsn.owner.equals("java/lang/ProcessHandle")) {
                            modified = true;
                            methodInsn.owner = "org/runelite/mobile/ProcessHandle";
                        } else if (methodInsn.owner.equals("java/lang/ProcessHandle$Info")) {
                            modified = true;
                            methodInsn.owner = "org/runelite/mobile/ProcessHandle$Info";
                        }
                        if (methodInsn.desc != null && methodInsn.desc.contains("Ljava/lang/ProcessHandle;")) {
                            modified = true;
                            methodInsn.desc = methodInsn.desc.replace("Ljava/lang/ProcessHandle;", "Lorg/runelite/mobile/ProcessHandle;");
                        }
                        if (methodInsn.desc != null && methodInsn.desc.contains("Ljava/lang/ProcessHandle$Info;")) {
                            modified = true;
                            methodInsn.desc = methodInsn.desc.replace("Ljava/lang/ProcessHandle$Info;", "Lorg/runelite/mobile/ProcessHandle$Info;");
                        }
                    }
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode typeInsn = (TypeInsnNode) insn;
                    if (typeInsn.desc.equals("java/lang/ProcessHandle")) {
                        modified = true;
                        typeInsn.desc = "org/runelite/mobile/ProcessHandle";
                    } else if (typeInsn.desc.equals("java/lang/ProcessHandle$Info")) {
                        modified = true;
                        typeInsn.desc = "org/runelite/mobile/ProcessHandle$Info";
                    }
                }
            }
        }

        if (modified) {
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    try {
                        return super.getCommonSuperClass(type1, type2);
                    } catch (Throwable t) {
                        return "java/lang/Object";
                    }
                }
            };
            classNode.accept(writer);
            return writer.toByteArray();
        }
        return classBytes;
    }

    private static int getInvokeOpcode(int tag) {
        switch (tag) {
            case Opcodes.H_INVOKESTATIC: return Opcodes.INVOKESTATIC;
            case Opcodes.H_INVOKEVIRTUAL: return Opcodes.INVOKEVIRTUAL;
            case Opcodes.H_INVOKESPECIAL: return Opcodes.INVOKESPECIAL;
            case Opcodes.H_INVOKEINTERFACE: return Opcodes.INVOKEINTERFACE;
            default: throw new IllegalArgumentException("Unsupported method handle tag: " + tag);
        }
    }

    private static String getAppendDesc(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: return "(Z)Ljava/lang/StringBuilder;";
            case Type.CHAR: return "(C)Ljava/lang/StringBuilder;";
            case Type.BYTE:
            case Type.SHORT:
            case Type.INT: return "(I)Ljava/lang/StringBuilder;";
            case Type.LONG: return "(J)Ljava/lang/StringBuilder;";
            case Type.FLOAT: return "(F)Ljava/lang/StringBuilder;";
            case Type.DOUBLE: return "(D)Ljava/lang/StringBuilder;";
            case Type.OBJECT:
                if (type.getInternalName().equals("java/lang/String")) {
                    return "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
                }
                return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            case Type.ARRAY: return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
            default: return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
        }
    }

    private static String getConstantAppendDesc(Object constVal) {
        if (constVal instanceof Integer) return "(I)Ljava/lang/StringBuilder;";
        if (constVal instanceof Long) return "(J)Ljava/lang/StringBuilder;";
        if (constVal instanceof Float) return "(F)Ljava/lang/StringBuilder;";
        if (constVal instanceof Double) return "(D)Ljava/lang/StringBuilder;";
        if (constVal instanceof String) return "(Ljava/lang/String;)Ljava/lang/StringBuilder;";
        if (constVal instanceof Character) return "(C)Ljava/lang/StringBuilder;";
        if (constVal instanceof Boolean) return "(Z)Ljava/lang/StringBuilder;";
        return "(Ljava/lang/Object;)Ljava/lang/StringBuilder;";
    }
}
