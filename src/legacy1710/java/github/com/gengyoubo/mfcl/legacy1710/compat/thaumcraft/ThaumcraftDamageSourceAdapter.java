package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** TC4's setters write private shadow fields that vanilla's getters never read. Use the inherited setters. */
public final class ThaumcraftDamageSourceAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name) { return name.equals("thaumcraft.api.damagesource.DamageSourceIndirectThaumcraftEntity"); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods) {
            if(!(method.name.equals("func_76348_h")||method.name.equals("func_76361_j"))||!method.desc.equals("()Lnet/minecraft/util/DamageSource;"))continue;
            method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();
            method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"net/minecraft/util/DamageSource",method.name,method.desc,false));
            method.instructions.add(new InsnNode(Opcodes.ARETURN));
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
