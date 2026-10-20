package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** TC4's custom door item bypasses ItemBlock's placement audio. Emit it once after a successful placement. */
public final class ThaumcraftDoorAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name) { return name.equals("thaumcraft.common.blocks.ItemArcaneDoor"); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods)if(method.name.equals("func_77648_a"))
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(call.owner.equals(node.name)&&call.name.equals("placeDoorBlock")) {
                    InsnList sound=new InsnList();sound.add(new VarInsnNode(Opcodes.ALOAD,3));sound.add(new VarInsnNode(Opcodes.ALOAD,11));
                    for(int slot=4;slot<=6;slot++)sound.add(new VarInsnNode(Opcodes.ILOAD,slot));
                    sound.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","placementSound","(Ljava/lang/Object;Ljava/lang/Object;III)V",false));
                    method.instructions.insert(call,sound);
                }
            }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
