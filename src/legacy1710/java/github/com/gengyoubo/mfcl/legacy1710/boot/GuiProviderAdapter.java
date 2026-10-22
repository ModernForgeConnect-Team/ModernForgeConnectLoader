package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Old mods that open every GUI through one (player, provider) method keep their actual container and screen; the host
 * supplies transport and player authority through the provider registered under {@link #provider} in LegacyDeviceContainers.
 * The original method remains the fallback when no host provider handles the request.
 */
public abstract class GuiProviderAdapter implements LegacyBytecodeAdapter {
    private final String owner, method, descriptor, provider;
    private final int playerSlot, sourceSlot;
    protected GuiProviderAdapter(String owner,String method,String descriptor,String provider,int playerSlot,int sourceSlot) {
        this.owner=owner;this.method=method;this.descriptor=descriptor;this.provider=provider;this.playerSlot=playerSlot;this.sourceSlot=sourceSlot;
    }
    public boolean targets(String name) { return name.equals(owner); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=BytecodePatches.read(bytes);boolean patched=false;
        for(MethodNode candidate:node.methods)if(BytecodePatches.matches(candidate,method,descriptor)) {
            InsnList arguments=new InsnList();arguments.add(new LdcInsnNode(provider));
            arguments.add(new VarInsnNode(Opcodes.ALOAD,playerSlot));arguments.add(new VarInsnNode(Opcodes.ALOAD,sourceSlot));
            BytecodePatches.handledPrefix(candidate,arguments,"github/com/gengyoubo/mfcl/bridge/LegacyDeviceContainers","openProvider","(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)Z");
            patched=true;
        }
        if(!patched)throw new IllegalStateException("Unsupported "+provider+" GUI layout: "+owner+"."+method+descriptor);
        return BytecodePatches.write(node);
    }
}
