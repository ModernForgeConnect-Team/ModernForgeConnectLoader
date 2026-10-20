package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** The copied TC4 arrow loop owns movement and impact; the inherited vanilla arrow loop must not hit first. */
public final class ThaumcraftPrimalArrowAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name) { return name.equals("thaumcraft.common.entities.projectile.EntityPrimalArrow"); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods)if(method.name.equals("func_70071_h_")&&method.desc.equals("()V"))
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.getOpcode()==Opcodes.INVOKESPECIAL&&call.owner.equals("net/minecraft/entity/projectile/EntityArrow")&&call.name.equals("func_70071_h_")) {
                    // Entity.onUpdate only calls onEntityUpdate. Keep base lifetime/fire/portal bookkeeping once.
                    method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/Entity","func_70030_z","()V",false));
                }
            }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
