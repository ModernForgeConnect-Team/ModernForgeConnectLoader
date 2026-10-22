package github.com.gengyoubo.mfcl.legacy1710.compat.extrautils;

import github.com.gengyoubo.mfcl.legacy1710.boot.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Entity scanning inspects authoritative host data rather than serializing an incomplete query mirror. */
public final class ExtraUtilitiesScannerAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name){return name.equals("com.rwtema.extrautils.item.scanner.ScannerRegistry$scanEntity");}
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=BytecodePatches.read(bytes);boolean changed=false;
        for(MethodNode method:node.methods)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("net/minecraft/entity/Entity")&&call.name.equals("func_70039_c")&&call.desc.equals("(Lnet/minecraft/nbt/NBTTagCompound;)Z")) {
                method.instructions.set(call,new MethodInsnNode(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyEntityHooks","inspect","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));changed=true;
            }
        }
        if(!changed)throw new IllegalStateException("Cannot find XU entity scanner NBT read");return BytecodePatches.write(node);
    }
}
