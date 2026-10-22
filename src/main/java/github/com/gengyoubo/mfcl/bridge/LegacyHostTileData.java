package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;

/** Native proxy tiles expose diagnostic NBT without entering the old persistent TileEntity registry. */
public final class LegacyHostTileData {
    private LegacyHostTileData() {}
    static void writer(ClassWriter writer) {
        var code=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_145841_b","(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);
        code.visitCode();code.visitVarInsn(Opcodes.ALOAD,0);code.visitVarInsn(Opcodes.ALOAD,1);
        code.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(LegacyHostTileData.class),"write","(Ljava/lang/Object;Ljava/lang/Object;)V",false);
        code.visitInsn(Opcodes.RETURN);code.visitMaxs(2,2);code.visitEnd();
    }
    public static void write(Object tile,Object target) throws Exception {
        var loader=tile.getClass().getClassLoader();var base=loader.loadClass("net.minecraft.tileentity.TileEntity");
        int x=base.getField("field_145851_c").getInt(tile),y=base.getField("field_145848_d").getInt(tile),z=base.getField("field_145849_e").getInt(tile);
        var host=LegacyWorlds.hostOrNull(base.getMethod("func_145831_w").invoke(tile));
        byte[] data=host==null?null:host.nativeTileData(x,y,z);
        var compound=loader.loadClass("net.minecraft.nbt.NBTTagCompound");
        if(data!=null) {
            copy(data,target);
        } else {
            compound.getMethod("func_74778_a",String.class,String.class).invoke(target,"id","mfcl:host");
            var integer=compound.getMethod("func_74768_a",String.class,int.class);integer.invoke(target,"x",x);integer.invoke(target,"y",y);integer.invoke(target,"z",z);
        }
    }
    static void copy(byte[] data,Object target) throws Exception {
        var loader=target.getClass().getClassLoader();var compound=loader.loadClass("net.minecraft.nbt.NBTTagCompound");
        var tracker=loader.loadClass("net.minecraft.nbt.NBTSizeTracker");
        Object snapshot=loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_152457_a",byte[].class,tracker)
                .invoke(null,data,tracker.getConstructor(long.class).newInstance(2L*1024*1024));
        var get=compound.getMethod("func_74781_a",String.class);var set=compound.getMethod("func_74782_a",String.class,loader.loadClass("net.minecraft.nbt.NBTBase"));
        for(Object key:(java.util.Set<?>)compound.getMethod("func_150296_c").invoke(snapshot))set.invoke(target,key,get.invoke(snapshot,key));
    }
}
