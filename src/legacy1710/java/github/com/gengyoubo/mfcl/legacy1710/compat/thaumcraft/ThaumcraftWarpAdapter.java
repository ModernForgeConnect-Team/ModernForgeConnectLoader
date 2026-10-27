package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Death gaze must ask its host-backed player about PvP instead of looking up an absent old MinecraftServer. */
public final class ThaumcraftWarpAdapter implements LegacyBytecodeAdapter {
    private static final String PLAYER="net/minecraft/entity/player/EntityPlayer";
    public boolean targets(String name){return name.equals("thaumcraft.common.lib.WarpEvents");}
    private static AbstractInsnNode previous(AbstractInsnNode node){do{node=node.getPrevious();}while(node!=null&&node.getOpcode()<0);return node;}
    private static AbstractInsnNode next(AbstractInsnNode node){do{node=node.getNext();}while(node!=null&&node.getOpcode()<0);return node;}
    public byte[] adapt(String name,byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int replaced=0;
        for(MethodNode method:node.methods)if(method.name.equals("checkDeathGaze"))for(AbstractInsnNode instruction:method.instructions.toArray()){
            if(!(instruction instanceof MethodInsnNode))continue;MethodInsnNode call=(MethodInsnNode)instruction;
            if(!call.owner.equals("net/minecraft/server/MinecraftServer")||!call.name.equals("func_71276_C"))continue;
            AbstractInsnNode enabled=next(call),branch=previous(call),test=previous(branch),target=previous(test);
            if(!(enabled instanceof MethodInsnNode)||!((MethodInsnNode)enabled).name.equals("func_71219_W")
                    ||!(test instanceof TypeInsnNode)||!((TypeInsnNode)test).desc.equals(PLAYER)||!(target instanceof VarInsnNode))throw new IllegalStateException("Unknown original death-gaze PvP expression");
            InsnList replacement=new InsnList();replacement.add(new VarInsnNode(Opcodes.ALOAD,0));replacement.add(new VarInsnNode(Opcodes.ALOAD,((VarInsnNode)target).var));replacement.add(new TypeInsnNode(Opcodes.CHECKCAST,PLAYER));
            method.instructions.insertBefore(call,replacement);method.instructions.remove(call);
            method.instructions.set(enabled,new MethodInsnNode(Opcodes.INVOKEVIRTUAL,PLAYER,"func_96122_a","(L"+PLAYER+";)Z",false));replaced++;
        }
        if(replaced!=1)throw new IllegalStateException("Expected one original death-gaze PvP check, found "+replaced);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
