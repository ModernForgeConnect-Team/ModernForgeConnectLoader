package github.com.gengyoubo.mfcl.legacy1710.compat.ic2;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Retains IC2's actual container and screen while the host supplies transport and player authority. */
public final class Ic2GuiAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name) { return name.equals("ic2.core.Platform"); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean patched=false;
        for(MethodNode method:node.methods)if(method.name.equals("launchGui")&&method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;Lic2/core/IHasGui;)Z")) {
            LabelNode original=new LabelNode();InsnList prefix=new InsnList();
            prefix.add(new LdcInsnNode("ic2"));prefix.add(new VarInsnNode(Opcodes.ALOAD,1));prefix.add(new VarInsnNode(Opcodes.ALOAD,2));
            prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyDeviceContainers","openProvider","(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)Z",false));
            prefix.add(new JumpInsnNode(Opcodes.IFEQ,original));prefix.add(new InsnNode(Opcodes.ICONST_1));prefix.add(new InsnNode(Opcodes.IRETURN));
            prefix.add(original);prefix.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(prefix);patched=true;
        }
        if(!patched)throw new IllegalStateException("Unsupported IC2 GUI platform layout");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
