package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/**
 * Old code guards world lookups with a hard-coded {@code y < 0 || y >= 256} test. Host worlds have other bounds and
 * unloaded chunks, so the guard becomes LegacyWorlds.validPosition(world, x, y, z) while the original lookup is kept.
 * Both compiled shapes are accepted: a jump to the valid branch on {@code y < 256}, or to the invalid one on {@code y >= 256}.
 */
public abstract class HeightGuardAdapter implements LegacyBytecodeAdapter {
    private final String owner, method, descriptorPrefix;
    private final int worldSlot, xSlot, ySlot, zSlot;
    protected HeightGuardAdapter(String owner,String method,String descriptorPrefix,int worldSlot,int xSlot,int ySlot,int zSlot) {
        this.owner=owner;this.method=method;this.descriptorPrefix=descriptorPrefix;this.worldSlot=worldSlot;this.xSlot=xSlot;this.ySlot=ySlot;this.zSlot=zSlot;
    }
    public boolean targets(String name) { return name.equals(owner); }
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode type=BytecodePatches.read(bytes);boolean changed=false;
        for(MethodNode candidate:type.methods)if(candidate.name.equals(method)&&candidate.desc.startsWith(descriptorPrefix))changed|=replaceGuard(candidate);
        if(!changed)throw new IllegalStateException("Cannot find original height guard in "+owner+"."+method);
        return BytecodePatches.write(type);
    }
    private boolean replaceGuard(MethodNode target) {
        for(AbstractInsnNode start:target.instructions.toArray()){
            if(!isLoad(start))continue;
            AbstractInsnNode low=BytecodePatches.next(start),load=BytecodePatches.next(low),limit=BytecodePatches.next(load),high=BytecodePatches.next(limit);
            if(low==null||low.getOpcode()!=Opcodes.IFLT||!isLoad(load)||!(limit instanceof IntInsnNode)||((IntInsnNode)limit).operand!=256||high==null)continue;
            int jump;
            if(high.getOpcode()==Opcodes.IF_ICMPLT)jump=Opcodes.IFNE;
            else if(high.getOpcode()==Opcodes.IF_ICMPGE&&((JumpInsnNode)high).label==((JumpInsnNode)low).label)jump=Opcodes.IFEQ;
            else continue;
            InsnList test=new InsnList();test.add(new VarInsnNode(Opcodes.ALOAD,worldSlot));
            for(int local:new int[]{xSlot,ySlot,zSlot})test.add(new VarInsnNode(Opcodes.ILOAD,local));
            test.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","validPosition","(Ljava/lang/Object;III)Z",false));
            test.add(new JumpInsnNode(jump,((JumpInsnNode)high).label));
            target.instructions.insertBefore(start,test);for(AbstractInsnNode old:new AbstractInsnNode[]{start,low,load,limit,high})target.instructions.remove(old);
            return true;
        }
        return false;
    }
    private boolean isLoad(AbstractInsnNode node) { return node instanceof VarInsnNode&&node.getOpcode()==Opcodes.ILOAD&&((VarInsnNode)node).var==ySlot; }
}
