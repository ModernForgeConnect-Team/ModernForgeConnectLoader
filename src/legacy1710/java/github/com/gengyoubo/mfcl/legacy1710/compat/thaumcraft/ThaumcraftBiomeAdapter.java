package github.com.gengyoubo.mfcl.legacy1710.compat.thaumcraft;

import github.com.gengyoubo.mfcl.legacy1710.boot.LegacyBytecodeAdapter;
import org.objectweb.asm.*;

/** TC4's single-column mutation must update modern palettes, not an obsolete chunk byte array. */
public final class ThaumcraftBiomeAdapter implements LegacyBytecodeAdapter {
    public boolean targets(String name){return name.equals("thaumcraft.common.lib.utils.Utils");}
    public byte[] adapt(String name,byte[] bytes){
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM5,writer){
            @Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
                MethodVisitor out=super.visitMethod(access,method,descriptor,signature,exceptions);
                if(!method.equals("setBiomeAt")||!descriptor.equals("(Lnet/minecraft/world/World;IILnet/minecraft/world/biome/BiomeGenBase;)V"))return out;
                out.visitCode();
                out.visitVarInsn(Opcodes.ALOAD,0);out.visitVarInsn(Opcodes.ILOAD,1);out.visitVarInsn(Opcodes.ILOAD,2);out.visitVarInsn(Opcodes.ALOAD,3);
                out.visitMethodInsn(Opcodes.INVOKESTATIC,"github/com/gengyoubo/mfcl/bridge/LegacyWorlds","setBiome","(Ljava/lang/Object;IILjava/lang/Object;)V",false);
                out.visitInsn(Opcodes.RETURN);out.visitMaxs(0,0);out.visitEnd();return null;
            }
        },0);
        // Adapted classes skip the normal transformer pipeline. Utils also expands the potion array through
        // Field.modifiers, which requires the same modern-Java reflection bridge as unadapted legacy classes.
        return new github.com.gengyoubo.mfcl.legacy1710.LegacyReflectionTransformer().transform(name,name,writer.toByteArray());
    }
}
