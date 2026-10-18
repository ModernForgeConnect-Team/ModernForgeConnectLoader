package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** CCC assumes a named-class development environment also has MCP member names. Our cache uses SRG members. */
final class SrgNamespaceAdapter {
    static byte[] adapt(String name, byte[] bytes) {
        if (bytes == null) return null;
        boolean mappings = name.equals("codechicken.lib.asm.ObfMapping");
        boolean devRemapper = name.equals("codechicken.core.asm.MCPDeobfuscationTransformer");
        boolean logging = name.equals("cpw.mods.fml.relauncher.FMLRelaunchLog");
        boolean delegated = name.equals("codechicken.core.asm.DelegatedTransformer");
        if (!mappings && !devRemapper && !logging && !delegated) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (delegated && method.name.equals("<init>")) for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof LdcInsnNode) {
                    LdcInsnNode ldc = (LdcInsnNode) insn;
                    if (Type.getType(ClassLoader.class).equals(ldc.cst)) ldc.cst = Type.getType(LegacyLaunchLoader.class);
                    if ("defineClass".equals(ldc.cst)) ldc.cst = "defineLegacyClass";
                }
            }
            if (mappings && method.name.equals("loadMCPRemapper") || devRemapper && method.name.equals("load")) {
                method.instructions.clear(); method.tryCatchBlocks.clear(); if (method.localVariables != null) method.localVariables.clear();
                method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
            if (logging) for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.owner.equals("java/lang/System") && (call.name.equals("setOut") || call.name.equals("setErr")))
                    method.instructions.set(call, new InsnNode(Opcodes.POP));
            }
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
