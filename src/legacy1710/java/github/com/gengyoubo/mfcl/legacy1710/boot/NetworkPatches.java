package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Old packet transport and player-range broadcast, delivered by the host instead of the absent old Netty connection and server. */
public final class NetworkPatches {
    private NetworkPatches() {}
    /**
     * A per-player send method taking the encoded payload and the target player: the host delivers the bytes on
     * {@code channel} (an LegacyPackets client payload channel). Returns whether the method was found.
     */
    public static boolean addressedPayload(ClassNode node,String method,String descriptor,String channel,int payloadSlot,int playerSlot) {
        boolean patched=false;
        for(MethodNode candidate:node.methods)if(BytecodePatches.matches(candidate,method,descriptor)) {
            InsnList arguments=new InsnList();arguments.add(new VarInsnNode(Opcodes.ALOAD,playerSlot));arguments.add(new LdcInsnNode(channel));arguments.add(new VarInsnNode(Opcodes.ALOAD,payloadSlot));
            BytecodePatches.handledPrefix(candidate,arguments,"github/com/gengyoubo/mfcl/bridge/LegacyPackets","sendClientPayload","(Ljava/lang/Object;Ljava/lang/String;[B)Z");
            patched=true;
        }
        return patched;
    }
    /**
     * Broadcast range code reads {@code MinecraftServer.getServer().getConfigurationManager().getViewDistance()}; there is
     * no old server, so the chain becomes the host view distance of the world in {@code worldSlot}. Returns whether any
     * chain was replaced; a server lookup followed by anything else fails explicitly.
     */
    public static boolean hostViewDistance(MethodNode method,int worldSlot) {
        boolean replaced=false;
        for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)instruction;
            if(!call.owner.equals("net/minecraft/server/MinecraftServer")||!call.name.equals("func_71276_C"))continue;
            AbstractInsnNode manager=BytecodePatches.next(call),distance=BytecodePatches.next(manager);
            if(!(manager instanceof MethodInsnNode)||!(distance instanceof MethodInsnNode)
                    ||!((MethodInsnNode)manager).name.equals("func_71203_ab")||!((MethodInsnNode)distance).name.equals("func_72372_a"))
                throw new IllegalStateException("Unsupported player range layout in "+method.name+method.desc);
            InsnList replacement=new InsnList();replacement.add(new VarInsnNode(Opcodes.ALOAD,worldSlot));
            replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","viewDistance","(Ljava/lang/Object;)I",false));
            method.instructions.insertBefore(call,replacement);method.instructions.remove(call);method.instructions.remove(manager);method.instructions.remove(distance);replaced=true;
        }
        return replaced;
    }
}
