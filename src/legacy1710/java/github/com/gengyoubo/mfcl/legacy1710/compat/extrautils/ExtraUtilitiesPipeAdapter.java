package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve XU pipe lookup while replacing its hard-coded 0..255 guard with host bounds/loading. */
public final class ExtraUtilitiesPipeAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name){return name.equals("com.rwtema.extrautils.tileentity.transfernodes.TNHelper");}
    private static AbstractInsnNode next(AbstractInsnNode node){if(node==null)return null;do{node=node.getNext();}while(node!=null&&node.getOpcode()<0);return node;}
    public byte[] adapt(String name,byte[] bytes){
        ClassNode type=new ClassNode(Opcodes.ASM5);new ClassReader(bytes).accept(type,0);boolean changed=false;
        for(MethodNode method:type.methods)if(method.name.equals("getPipe")&&method.desc.startsWith("(Lnet/minecraft/world/IBlockAccess;III)")){
            for(AbstractInsnNode start:method.instructions.toArray()){
                if(!(start instanceof VarInsnNode)||start.getOpcode()!=Opcodes.ILOAD||((VarInsnNode)start).var!=2)continue;
                AbstractInsnNode low=next(start),load=next(low),limit=next(load),high=next(limit);
                if(low==null||low.getOpcode()!=Opcodes.IFLT||!(load instanceof VarInsnNode)||load.getOpcode()!=Opcodes.ILOAD||((VarInsnNode)load).var!=2||!(limit instanceof IntInsnNode)||((IntInsnNode)limit).operand!=256||high==null||high.getOpcode()!=Opcodes.IF_ICMPLT)continue;
                InsnList test=new InsnList();test.add(new VarInsnNode(Opcodes.ALOAD,0));for(int local=1;local<=3;local++)test.add(new VarInsnNode(Opcodes.ILOAD,local));
                test.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","validPosition","(Ljava/lang/Object;III)Z",false));test.add(new JumpInsnNode(Opcodes.IFNE,((JumpInsnNode)high).label));
                method.instructions.insertBefore(start,test);for(AbstractInsnNode old:new AbstractInsnNode[]{start,low,load,limit,high})method.instructions.remove(old);changed=true;break;
            }
        }
        if(!changed)throw new IllegalStateException("Cannot find original XU pipe height guard");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);type.accept(writer);return writer.toByteArray();
    }
}
