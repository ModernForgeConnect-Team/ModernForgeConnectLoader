package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** /hat may place chest armor in the head slot. Never read an unrelated/empty chest as a harness. */
public final class ThaumcraftHoverHarnessAdapter implements LegacyBytecodeAdapter {
    private static final String ITEM="thaumcraft/common/items/armor/ItemHoverHarness",MODEL="thaumcraft/client/renderers/models/gear/ModelHoverHarness";
    public boolean targets(String name){return name.equals(ITEM.replace('/','.'))||name.equals(MODEL.replace('/','.'));}
    public byte[] adapt(String name,byte[] bytes) {
        ClassNode node=BytecodePatches.read(bytes);int patched=0;
        for(MethodNode method:node.methods){
            InsnList guard=new InsnList();LabelNode original=new LabelNode();
            if(name.equals(ITEM.replace('/','.'))&&BytecodePatches.matches(method,"getArmorModel","(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;I)Lnet/minecraft/client/model/ModelBiped;")){
                guard.add(new VarInsnNode(Opcodes.ILOAD,3));guard.add(new InsnNode(Opcodes.ICONST_1));guard.add(new JumpInsnNode(Opcodes.IF_ICMPEQ,original));
                guard.add(new InsnNode(Opcodes.ACONST_NULL));guard.add(new InsnNode(Opcodes.ARETURN));
            }else if(name.equals(ITEM.replace('/','.'))&&BytecodePatches.matches(method,"onArmorTick","(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)V")){
                chest(guard,2,false);guard.add(new VarInsnNode(Opcodes.ALOAD,3));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/minecraft/item/ItemStack","func_77989_b","(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z",false));
                guard.add(new JumpInsnNode(Opcodes.IFNE,original));guard.add(new InsnNode(Opcodes.RETURN));
            }else if(name.equals(MODEL.replace('/','.'))&&BytecodePatches.matches(method,"func_78088_a","(Lnet/minecraft/entity/Entity;FFFFFF)V")){
                LabelNode skip=new LabelNode();
                guard.add(new VarInsnNode(Opcodes.ALOAD,1));guard.add(new TypeInsnNode(Opcodes.INSTANCEOF,"net/minecraft/entity/player/EntityPlayer"));guard.add(new JumpInsnNode(Opcodes.IFEQ,skip));
                chest(guard,1,true);guard.add(new JumpInsnNode(Opcodes.IFNULL,skip));
                chest(guard,1,true);guard.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/item/ItemStack","func_77973_b","()Lnet/minecraft/item/Item;",false));
                guard.add(new TypeInsnNode(Opcodes.INSTANCEOF,ITEM));guard.add(new JumpInsnNode(Opcodes.IFNE,original));
                guard.add(skip);guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));guard.add(new InsnNode(Opcodes.RETURN));
            }else continue;
            guard.add(original);guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(guard);patched++;
        }
        int expected=name.equals(ITEM.replace('/','.'))?2:1;
        if(patched!=expected)throw new IllegalStateException("Unsupported TC4 hover harness layout: "+name+" ("+patched+")");
        return BytecodePatches.write(node);
    }
    private static void chest(InsnList code,int player,boolean cast){
        code.add(new VarInsnNode(Opcodes.ALOAD,player));if(cast)code.add(new TypeInsnNode(Opcodes.CHECKCAST,"net/minecraft/entity/player/EntityPlayer"));
        code.add(new FieldInsnNode(Opcodes.GETFIELD,"net/minecraft/entity/player/EntityPlayer","field_71071_by","Lnet/minecraft/entity/player/InventoryPlayer;"));
        code.add(new InsnNode(Opcodes.ICONST_2));code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"net/minecraft/entity/player/InventoryPlayer","func_70440_f","(I)Lnet/minecraft/item/ItemStack;",false));
    }
}
