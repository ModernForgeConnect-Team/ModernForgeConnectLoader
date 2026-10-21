package github.com.gengyoubo.mfcl.legacy1710.compat.ic2;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** IC2's visual explosion packet is delivered by the host instead of the absent old Netty connection. */
public final class Ic2NetworkAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name) { return name.equals("ic2.core.network.NetworkManager"); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean patched=false;
        for(MethodNode method:node.methods)if(method.name.equals("initiateExplosionEffect")&&method.desc.equals("(Lnet/minecraft/world/World;DDD)V")) {
            LabelNode original=new LabelNode();InsnList prefix=new InsnList();prefix.add(new VarInsnNode(Opcodes.ALOAD,1));
            for(int slot=2;slot<=6;slot+=2)prefix.add(new VarInsnNode(Opcodes.DLOAD,slot));
            prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","explosionEffect","(Ljava/lang/Object;DDD)Z",false));
            prefix.add(new JumpInsnNode(Opcodes.IFEQ,original));prefix.add(new InsnNode(Opcodes.RETURN));
            prefix.add(original);prefix.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(prefix);patched=true;
        }
        if(!patched)throw new IllegalStateException("Unsupported IC2 explosion packet layout");
        boolean transport=false;
        for(MethodNode method:node.methods)if(method.name.equals("sendPacket")&&method.desc.equals("([BLnet/minecraft/entity/player/EntityPlayerMP;)V")) {
            LabelNode original=new LabelNode();InsnList prefix=new InsnList();prefix.add(new VarInsnNode(Opcodes.ALOAD,2));prefix.add(new LdcInsnNode("mfcl:ic2"));prefix.add(new VarInsnNode(Opcodes.ALOAD,1));
            prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyPackets","sendClientPayload","(Ljava/lang/Object;Ljava/lang/String;[B)Z",false));
            prefix.add(new JumpInsnNode(Opcodes.IFEQ,original));prefix.add(new InsnNode(Opcodes.RETURN));prefix.add(original);
            prefix.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(prefix);transport=true;
        }
        if(!transport)throw new IllegalStateException("Unsupported IC2 addressed packet layout");
        boolean range=false;
        for(MethodNode method:node.methods)if(method.name.equals("getPlayersInRange")) {
            for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(!call.owner.equals("net/minecraft/server/MinecraftServer")||!call.name.equals("func_71276_C"))continue;
                AbstractInsnNode manager=nextInstruction(call),distance=nextInstruction(manager);
                if(!(manager instanceof MethodInsnNode)||!(distance instanceof MethodInsnNode)
                        ||!((MethodInsnNode)manager).name.equals("func_71203_ab")||!((MethodInsnNode)distance).name.equals("func_72372_a"))
                    throw new IllegalStateException("Unsupported IC2 player range layout");
                InsnList replacement=new InsnList();replacement.add(new VarInsnNode(Opcodes.ALOAD,0));
                replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","viewDistance","(Ljava/lang/Object;)I",false));
                method.instructions.insertBefore(call,replacement);method.instructions.remove(call);method.instructions.remove(manager);method.instructions.remove(distance);range=true;
            }
        }
        if(!range)throw new IllegalStateException("Unsupported IC2 player range method");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static AbstractInsnNode nextInstruction(AbstractInsnNode node) {
        AbstractInsnNode next=node.getNext();while(next!=null&&next.getOpcode()<0)next=next.getNext();return next;
    }
}
