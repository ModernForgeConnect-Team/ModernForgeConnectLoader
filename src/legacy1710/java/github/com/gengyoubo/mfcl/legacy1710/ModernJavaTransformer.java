package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Changes only bytes defined by the private legacy LaunchClassLoader. */
public final class ModernJavaTransformer implements IClassTransformer {
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null) return null;
        if (!name.equals("cpw.mods.fml.common.Loader") && !name.equals("net.minecraftforge.common.util.EnumHelper")
                && !name.equals("cpw.mods.fml.common.registry.ObjectHolderRef") && !name.equals("cpw.mods.fml.common.discovery.ModDiscoverer")
                && !name.equals("net.minecraftforge.common.ForgeVersion") && !name.equals("cpw.mods.fml.common.registry.GameData")) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (name.equals("cpw.mods.fml.common.registry.GameData") && (method.name.equals("registerItem") || method.name.equals("registerBlock"))) {
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/LegacyRegistrationSeal", "check", "()V", false));
            }
            if (name.equals("net.minecraftforge.common.ForgeVersion") && method.name.equals("startVersionCheck")) {
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (name.equals("cpw.mods.fml.common.discovery.ModDiscoverer") && method.name.equals("findClasspathMods")) {
                // Runtime libraries are not mod candidates. The validated direct version directory is authoritative.
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (name.equals("cpw.mods.fml.common.Loader") && method.name.equals("identifyMods")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) insn;
                    if (call.owner.equals("java/io/File") && call.name.equals("isDirectory")) {
                        method.instructions.insertBefore(call, new InsnNode(Opcodes.POP));
                        method.instructions.set(call, new InsnNode(Opcodes.ICONST_0));
                    }
                }
            }
            if (name.equals("cpw.mods.fml.common.registry.ObjectHolderRef")) {
                if (method.name.equals("makeWritable")) {
                    method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                } else if (method.name.equals("apply")) {
                    boolean tail = false;
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof FieldInsnNode && ((FieldInsnNode) insn).name.equals("newFieldAccessor")) tail = true;
                        if (tail) method.instructions.remove(insn);
                    }
                    if (!tail) throw new IllegalStateException("Unrecognized ObjectHolderRef layout");
                    method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                    method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "field", "Ljava/lang/reflect/Field;"));
                    method.instructions.add(new InsnNode(Opcodes.ACONST_NULL)); method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/ModernEnumSupport", "setFailsafeFieldValue", "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V", false));
                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                }
            }
            if (name.equals("cpw.mods.fml.common.Loader") && method.name.equals("initializeLoader")) {
                for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof LdcInsnNode && "mods".equals(((LdcInsnNode) insn).cst))
                    ((LdcInsnNode) insn).cst = "mods/1.7.10";
            }
            if (!name.equals("net.minecraftforge.common.util.EnumHelper")) continue;
            if (method.name.equals("setup")) {
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.ICONST_1));
                method.instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, "isSetup", "Z"));
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            } else if (method.name.equals("makeEnum") || method.name.equals("setFailsafeFieldValue") || method.name.equals("cleanEnumCache")) {
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                Type[] args = Type.getArgumentTypes(method.desc); int slot = 0;
                for (Type arg : args) { method.instructions.add(new VarInsnNode(arg.getOpcode(Opcodes.ILOAD), slot)); slot += arg.getSize(); }
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "github/com/gengyoubo/mfcl/legacy1710/ModernEnumSupport", method.name, method.desc, false));
                method.instructions.add(new InsnNode(Type.getReturnType(method.desc).getOpcode(Opcodes.IRETURN)));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
