package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Developer API call-site redirects. Matching calls become
 * {@code LegacyRedirects.dispatch(site, CallerClass.class, new Object[]{receiver?, arguments...})}, cast or unboxed to the
 * original return type. Constructors and invokespecial calls are never rewritten.
 */
public final class LegacyRedirectTransformer implements IClassTransformer {
    private static final String BRIDGE = "github/com/gengyoubo/mfcl/bridge/LegacyRedirects";
    private String[][] sites;
    private String[][] sites() {
        if (sites == null) try {
            sites = (String[][]) Class.forName(BRIDGE.replace('/', '.'), true, getClass().getClassLoader()).getMethod("sites").invoke(null);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot read legacy redirect sites", failure); }
        return sites;
    }
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName.startsWith("github.com.gengyoubo.mfcl.") || transformedName.startsWith("net.minecraft.launchwrapper.")) return bytes;
        String[][] all = sites();
        if (all.length == 0) return bytes;
        String text = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean candidate = false;
        for (String[] site : all) if (transformedName.startsWith(site[0]) && text.contains(site[2]) && text.contains(site[1])) { candidate = true; break; }
        if (!candidate) return bytes;
        ClassNode node = new ClassNode(Opcodes.ASM5); new ClassReader(bytes).accept(node, 0);
        // The caller is passed as a class constant, which needs class file version 49 (Java 5) or later.
        if ((node.version & 0xFFFF) < Opcodes.V1_5) return bytes;
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode) || insn.getOpcode() == Opcodes.INVOKESPECIAL) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                for (int id = 0; id < all.length; id++) {
                    String[] site = all[id];
                    if (!transformedName.startsWith(site[0]) || !call.owner.equals(site[1]) || !call.name.equals(site[2]) || !call.desc.equals(site[3])) continue;
                    InsnList replacement = replacement(method, call, id, node.name);
                    AbstractInsnNode last = replacement.getLast();
                    method.instructions.insert(call, replacement); method.instructions.remove(call);
                    insn = last; changed = true;
                    break;
                }
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
        return writer.toByteArray();
    }
    private static InsnList replacement(MethodNode method, MethodInsnNode call, int id, String caller) {
        Type[] arguments = Type.getArgumentTypes(call.desc);
        boolean instance = call.getOpcode() != Opcodes.INVOKESTATIC;
        int count = arguments.length + (instance ? 1 : 0);
        Type[] values = new Type[count]; int[] locals = new int[count];
        if (instance) values[0] = Type.getObjectType(call.owner);
        System.arraycopy(arguments, 0, values, instance ? 1 : 0, arguments.length);
        int next = method.maxLocals;
        for (int index = 0; index < count; index++) { locals[index] = next; next += values[index].getSize(); }
        method.maxLocals = next;
        InsnList list = new InsnList();
        // The operand stack holds the receiver and arguments; park them in fresh locals, last first.
        for (int index = count - 1; index >= 0; index--) list.add(new VarInsnNode(values[index].getOpcode(Opcodes.ISTORE), locals[index]));
        list.add(new LdcInsnNode(id)); list.add(new LdcInsnNode(Type.getObjectType(caller)));
        list.add(new LdcInsnNode(count)); list.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        for (int index = 0; index < count; index++) {
            list.add(new InsnNode(Opcodes.DUP)); list.add(new LdcInsnNode(index));
            list.add(new VarInsnNode(values[index].getOpcode(Opcodes.ILOAD), locals[index]));
            box(list, values[index]);
            list.add(new InsnNode(Opcodes.AASTORE));
        }
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "dispatch", "(ILjava/lang/Class;[Ljava/lang/Object;)Ljava/lang/Object;", false));
        unbox(list, Type.getReturnType(call.desc));
        return list;
    }
    private static void box(InsnList list, Type type) {
        String owner = boxed(type);
        if (owner != null) list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, owner, "valueOf", "(" + type.getDescriptor() + ")L" + owner + ";", false));
    }
    private static void unbox(InsnList list, Type type) {
        switch (type.getSort()) {
            case Type.VOID: list.add(new InsnNode(Opcodes.POP)); return;
            case Type.BOOLEAN: unbox(list, "java/lang/Boolean", "booleanValue", "()Z"); return;
            case Type.CHAR: unbox(list, "java/lang/Character", "charValue", "()C"); return;
            case Type.BYTE: unbox(list, "java/lang/Number", "byteValue", "()B"); return;
            case Type.SHORT: unbox(list, "java/lang/Number", "shortValue", "()S"); return;
            case Type.INT: unbox(list, "java/lang/Number", "intValue", "()I"); return;
            case Type.FLOAT: unbox(list, "java/lang/Number", "floatValue", "()F"); return;
            case Type.LONG: unbox(list, "java/lang/Number", "longValue", "()J"); return;
            case Type.DOUBLE: unbox(list, "java/lang/Number", "doubleValue", "()D"); return;
            default: list.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getSort() == Type.ARRAY ? type.getDescriptor() : type.getInternalName()));
        }
    }
    private static void unbox(InsnList list, String owner, String method, String descriptor) {
        list.add(new TypeInsnNode(Opcodes.CHECKCAST, owner)); list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, method, descriptor, false));
    }
    private static String boxed(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN: return "java/lang/Boolean";
            case Type.CHAR: return "java/lang/Character";
            case Type.BYTE: return "java/lang/Byte";
            case Type.SHORT: return "java/lang/Short";
            case Type.INT: return "java/lang/Integer";
            case Type.FLOAT: return "java/lang/Float";
            case Type.LONG: return "java/lang/Long";
            case Type.DOUBLE: return "java/lang/Double";
            default: return null;
        }
    }
}
