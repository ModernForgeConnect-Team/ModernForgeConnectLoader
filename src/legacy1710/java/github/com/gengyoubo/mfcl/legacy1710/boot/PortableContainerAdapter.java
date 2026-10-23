package github.com.gengyoubo.mfcl.legacy1710.boot;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Original portable-container lifecycle; adapters supply storage semantics through a policy. */
public abstract class PortableContainerAdapter implements LegacyBytecodeAdapter {
    private final String OWNER;
    private final boolean authoritativeSlots;
    protected PortableContainerAdapter(String owner,PortableContainerHooks.Policy policy,boolean authoritativeSlots){
        OWNER=owner;this.authoritativeSlots=authoritativeSlots;PortableContainerHooks.register(owner.replace('/','.'),policy);
    }
    private static final String BRIDGE="github/com/gengyoubo/mfcl/legacy1710/boot/PortableContainerHooks";
    public boolean targets(String name){return name.equals(OWNER.replace('/','.'));}
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=BytecodePatches.read(bytes);int patched=0;
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"mfcl$portableContents","Ljava/lang/Object;",null,null));
        for(MethodNode method:node.methods) {
            if(method.name.equals("<init>")) {
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN) {
                    InsnList remember=new InsnList();remember.add(new VarInsnNode(Opcodes.ALOAD,0));remember.add(new VarInsnNode(Opcodes.ALOAD,0));
                    remember.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"remember","(Ljava/lang/Object;)Ljava/lang/Object;",false));
                    remember.add(new FieldInsnNode(Opcodes.PUTFIELD,OWNER,"mfcl$portableContents","Ljava/lang/Object;"));method.instructions.insertBefore(instruction,remember);
                }
                patched++;
            }else if(BytecodePatches.matches(method,"func_75144_a","(IIILnet/minecraft/entity/player/EntityPlayer;)Lnet/minecraft/item/ItemStack;")) {
                // Preserve the opening stack and validate number-key swaps on either side.
                LabelNode original=new LabelNode();InsnList guard=new InsnList();
                guard.add(new VarInsnNode(Opcodes.ALOAD,0));for(int index=1;index<=3;index++)guard.add(new VarInsnNode(Opcodes.ILOAD,index));guard.add(new VarInsnNode(Opcodes.ALOAD,4));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"clickAllowed","(Ljava/lang/Object;IIILjava/lang/Object;)Z",false));guard.add(new JumpInsnNode(Opcodes.IFNE,original));
                guard.add(new InsnNode(Opcodes.ACONST_NULL));guard.add(new InsnNode(Opcodes.ARETURN));guard.add(original);guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                method.instructions.insert(guard);
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.ARETURN)method.instructions.insertBefore(instruction,save());
                patched++;
            }else if(BytecodePatches.matches(method,"func_75134_a","(Lnet/minecraft/entity/player/EntityPlayer;)V")) {
                clear(method);
                method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"net/minecraft/inventory/Container",method.name,method.desc,false));
                method.instructions.add(save());method.instructions.add(new InsnNode(Opcodes.RETURN));patched++;
            }else if(BytecodePatches.matches(method,"func_75145_c","(Lnet/minecraft/entity/player/EntityPlayer;)Z")) {
                clear(method);method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"valid","(Ljava/lang/Object;Ljava/lang/Object;)Z",false));
                method.instructions.add(new InsnNode(Opcodes.IRETURN));patched++;
            }else if(authoritativeSlots&&BytecodePatches.matches(method,"func_75141_a","(ILnet/minecraft/item/ItemStack;)V")) {
                // Incoming server state includes nulls and player items; restrictions belong to user clicks.
                clear(method);method.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));method.instructions.add(new VarInsnNode(Opcodes.ILOAD,1));method.instructions.add(new VarInsnNode(Opcodes.ALOAD,2));
                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"net/minecraft/inventory/Container",method.name,method.desc,false));method.instructions.add(new InsnNode(Opcodes.RETURN));
            }
        }
        if(patched!=4)throw new IllegalStateException("Unsupported portable container layout: "+name+" ("+patched+")");
        return BytecodePatches.write(node);
    }
    private static InsnList save(){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"save","(Ljava/lang/Object;)V",false));return code;}
    private static void clear(MethodNode method){method.instructions.clear();method.tryCatchBlocks.clear();if(method.localVariables!=null)method.localVariables.clear();}
}
