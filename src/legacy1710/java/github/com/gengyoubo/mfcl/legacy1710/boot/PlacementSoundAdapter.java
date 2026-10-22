package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Custom placer items (doors, multi-block items) that set blocks themselves bypass ItemBlock's placement audio. After
 * each call to the item's own placing method, LegacyWorlds.placementSound plays the placed block's original step sound.
 */
public abstract class PlacementSoundAdapter implements LegacyBytecodeAdapter {
    private final String owner, method, placingMethod;
    private final int worldSlot, blockSlot, xSlot, ySlot, zSlot;
    /** {@code placingMethod} is a method of {@code owner} itself; the slots are locals of {@code method} once it has placed. */
    protected PlacementSoundAdapter(String owner,String method,String placingMethod,int worldSlot,int blockSlot,int xSlot,int ySlot,int zSlot) {
        this.owner=owner;this.method=method;this.placingMethod=placingMethod;
        this.worldSlot=worldSlot;this.blockSlot=blockSlot;this.xSlot=xSlot;this.ySlot=ySlot;this.zSlot=zSlot;
    }
    public boolean targets(String name) { return name.equals(owner); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=BytecodePatches.read(bytes);boolean patched=false;
        for(MethodNode candidate:node.methods)if(candidate.name.equals(method))
            for(AbstractInsnNode instruction:candidate.instructions.toArray())if(instruction instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)instruction;
                if(!call.owner.equals(node.name)||!call.name.equals(placingMethod))continue;
                InsnList sound=new InsnList();sound.add(new VarInsnNode(Opcodes.ALOAD,worldSlot));sound.add(new VarInsnNode(Opcodes.ALOAD,blockSlot));
                for(int slot:new int[]{xSlot,ySlot,zSlot})sound.add(new VarInsnNode(Opcodes.ILOAD,slot));
                sound.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","placementSound","(Ljava/lang/Object;Ljava/lang/Object;III)V",false));
                candidate.instructions.insert(call,sound);patched=true;
            }
        if(!patched)throw new IllegalStateException("Cannot find original placement call "+owner+"."+placingMethod+" in "+method);
        return BytecodePatches.write(node);
    }
}
