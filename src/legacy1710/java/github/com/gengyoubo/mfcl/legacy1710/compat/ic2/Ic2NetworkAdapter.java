package github.com.gengyoubo.mfcl.legacy1710.compat.ic2;

import github.com.gengyoubo.mfcl.legacy1710.boot.BytecodePatches;
import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import github.com.gengyoubo.mfcl.legacy1710.boot.NetworkPatches;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** IC2's visual explosion packet is delivered by the host instead of the absent old Netty connection. */
public final class Ic2NetworkAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name) { return name.equals("ic2.core.network.NetworkManager"); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=BytecodePatches.read(bytes);boolean patched=false;
        for(MethodNode method:node.methods)if(BytecodePatches.matches(method,"initiateExplosionEffect","(Lnet/minecraft/world/World;DDD)V")) {
            InsnList arguments=new InsnList();arguments.add(new VarInsnNode(Opcodes.ALOAD,1));
            for(int slot=2;slot<=6;slot+=2)arguments.add(new VarInsnNode(Opcodes.DLOAD,slot));
            BytecodePatches.handledPrefix(method,arguments,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","explosionEffect","(Ljava/lang/Object;DDD)Z");
            patched=true;
        }
        if(!patched)throw new IllegalStateException("Unsupported IC2 explosion packet layout");
        if(!NetworkPatches.addressedPayload(node,"sendPacket","([BLnet/minecraft/entity/player/EntityPlayerMP;)V","mfcl:ic2",1,2))
            throw new IllegalStateException("Unsupported IC2 addressed packet layout");
        boolean range=false;
        for(MethodNode method:node.methods)if(method.name.equals("getPlayersInRange"))range|=NetworkPatches.hostViewDistance(method,0);
        if(!range)throw new IllegalStateException("Unsupported IC2 player range method");
        return BytecodePatches.write(node);
    }
}
