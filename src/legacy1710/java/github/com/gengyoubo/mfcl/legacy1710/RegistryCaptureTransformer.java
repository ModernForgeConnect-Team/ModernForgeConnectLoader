package github.com.gengyoubo.mfcl.legacy1710;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Retains registration ownership without replacing any original Forge registry implementation. */
public final class RegistryCaptureTransformer implements IClassTransformer {
    private static final String HOOK="github/com/gengyoubo/mfcl/bridge/LegacyRegistrations";
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null||!(name.equals("net.minecraft.stats.StatBase")||name.equals("net.minecraft.entity.ai.attributes.RangedAttribute")||name.equals("net.minecraft.entity.ai.attributes.BaseAttributeMap")||name.equals("cpw.mods.fml.common.registry.VillagerRegistry")||name.equals("cpw.mods.fml.common.registry.EntityRegistry")||name.equals("net.minecraftforge.common.ChestGenHooks")))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        for(MethodNode method:node.methods){
            String callback=null;boolean integer=false;
            if(name.endsWith("StatBase")&&method.name.equals("func_75971_g"))callback="statistic";
            if(name.endsWith("RangedAttribute")&&method.name.equals("<init>"))callback="attribute";
            if(name.endsWith("BaseAttributeMap")&&method.name.equals("func_111150_b"))callback="attribute";
            if(name.endsWith("VillagerRegistry")&&method.name.equals("registerVillagerId")){callback="profession";integer=true;}
            if(name.endsWith("ChestGenHooks")&&method.name.equals("addItem")&&method.desc.equals("(Lnet/minecraft/util/WeightedRandomChestContent;)V"))callback="loot";
            if(name.endsWith("ChestGenHooks")&&method.name.equals("removeItem")&&method.desc.equals("(Lnet/minecraft/item/ItemStack;)V"))callback="removeLoot";
            if(callback!=null)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN||instruction.getOpcode()==Opcodes.ARETURN){
                InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,name.endsWith("BaseAttributeMap")?1:0));
                String desc="(Ljava/lang/Object;)V";
                if(integer){hook.add(new VarInsnNode(Opcodes.ILOAD,1));desc="(Ljava/lang/Object;I)V";}
                if(callback.equals("loot")||callback.equals("removeLoot")){hook.add(new VarInsnNode(Opcodes.ALOAD,1));desc="(Ljava/lang/Object;Ljava/lang/Object;)V";}
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,callback,desc,false));method.instructions.insertBefore(instruction,hook);changed=true;
            }
            if(name.endsWith("EntityRegistry")&&method.name.equals("addSpawn")&&method.desc.startsWith("(Ljava/lang/Class;")){
                InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));for(int i=1;i<=3;i++)hook.add(new VarInsnNode(Opcodes.ILOAD,i));hook.add(new VarInsnNode(Opcodes.ALOAD,4));hook.add(new VarInsnNode(Opcodes.ALOAD,5));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"spawn","(Ljava/lang/Class;IIILjava/lang/Object;[Ljava/lang/Object;)V",false));
                for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(instruction,cloneHook(hook));changed=true;
            }
            if(name.endsWith("EntityRegistry")&&method.name.equals("removeSpawn")&&method.desc.startsWith("(Ljava/lang/Class;")){
                InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ALOAD,1));hook.add(new VarInsnNode(Opcodes.ALOAD,2));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"removeSpawn","(Ljava/lang/Class;Ljava/lang/Object;[Ljava/lang/Object;)V",false));for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction.getOpcode()==Opcodes.RETURN)method.instructions.insertBefore(instruction,cloneHook(hook));changed=true;
            }
        }
        if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static InsnList cloneHook(InsnList source){InsnList copy=new InsnList();for(AbstractInsnNode instruction:source.toArray())copy.add(instruction.clone(new java.util.HashMap<LabelNode,LabelNode>()));return copy;}
}
