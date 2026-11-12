package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Random;

/** A 1.7 biome manager whose answers come from the host world's actual biome columns. */
public final class LegacyBiomeManager {
    private static final String NAME="github/com/gengyoubo/mfcl/legacy1710/HostBiomeManager";
    private static final String BASE="net/minecraft/world/biome/WorldChunkManager";
    private static final String WORLD="net/minecraft/world/World";
    private static final String BIOME="net/minecraft/world/biome/BiomeGenBase";
    private LegacyBiomeManager() {}

    static Object create(ClassLoader loader,Object world) throws Exception {
        Class<?> type;
        synchronized(loader) {
            try { type=loader.loadClass(NAME.replace('/','.')); }
            catch(ClassNotFoundException absent) { type=define(loader); }
        }
        return type.getConstructor(loader.loadClass(WORLD.replace('/','.'))).newInstance(world);
    }
    private static Class<?> define(ClassLoader loader) throws Exception {
        var w=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        w.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC|Opcodes.ACC_FINAL,NAME,null,BASE,null);
        w.visitField(Opcodes.ACC_PRIVATE|Opcodes.ACC_FINAL,"world","L"+WORLD+";",null,null).visitEnd();
        var c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(L"+WORLD+";)V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitMethodInsn(Opcodes.INVOKESPECIAL,BASE,"<init>","()V",false);
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitFieldInsn(Opcodes.PUTFIELD,NAME,"world","L"+WORLD+";");
        c.visitInsn(Opcodes.RETURN);c.visitMaxs(2,2);c.visitEnd();
        forward(w,"func_76935_a","(II)L"+BIOME+";","biome","(Ljava/lang/Object;II)Ljava/lang/Object;",BIOME,false);
        forward(w,"func_76937_a","([L"+BIOME+";IIII)[L"+BIOME+";","grid","(Ljava/lang/Object;[Ljava/lang/Object;IIIIZ)[Ljava/lang/Object;","[L"+BIOME+";",true);
        forward(w,"func_76933_b","([L"+BIOME+";IIII)[L"+BIOME+";","grid","(Ljava/lang/Object;[Ljava/lang/Object;IIIIZ)[Ljava/lang/Object;","[L"+BIOME+";",false);
        forward(w,"func_76931_a","([L"+BIOME+";IIIIZ)[L"+BIOME+";","gridCached","(Ljava/lang/Object;[Ljava/lang/Object;IIIIZ)[Ljava/lang/Object;","[L"+BIOME+";",false);
        forward(w,"func_76936_a","([FIIII)[F","rainfall","(Ljava/lang/Object;[FIIII)[F",null,false);
        forward(w,"func_76940_a","(IIILjava/util/List;)Z","viable","(Ljava/lang/Object;IIILjava/util/List;)Z",null,false);
        forward(w,"func_150795_a","(IIILjava/util/List;Ljava/util/Random;)Lnet/minecraft/world/ChunkPosition;","find","(Ljava/lang/Object;IIILjava/util/List;Ljava/util/Random;)Ljava/lang/Object;","net/minecraft/world/ChunkPosition",false);
        w.visitEnd();byte[] bytes=w.toByteArray();
        return (Class<?>)loader.getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class).invoke(loader,NAME.replace('/','.'),bytes,0,bytes.length);
    }
    private static void forward(ClassWriter w,String name,String desc,String callback,String target,String cast,boolean quarter) {
        var m=w.visitMethod(Opcodes.ACC_PUBLIC,name,desc,null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);
        m.visitFieldInsn(Opcodes.GETFIELD,NAME,"world","L"+WORLD+";");
        int slot=1;for(Type t:Type.getArgumentTypes(desc)){m.visitVarInsn(t.getOpcode(Opcodes.ILOAD),slot);slot+=t.getSize();}
        if(name.equals("func_76937_a")||name.equals("func_76933_b"))m.visitInsn(quarter?Opcodes.ICONST_1:Opcodes.ICONST_0);
        m.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(LegacyBiomeManager.class),callback,target,false);
        if(cast!=null)m.visitTypeInsn(Opcodes.CHECKCAST,cast);
        m.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));m.visitMaxs(0,0);m.visitEnd();
    }
    public static Object biome(Object world,int x,int z) {return LegacyWorlds.biome(world,x,z);}
    public static Object[] grid(Object world,Object[] reuse,int x,int z,int width,int height,boolean quarter) {
        int length=Math.multiplyExact(width,height);
        if(reuse==null||reuse.length<length) {
            try { reuse=(Object[])Array.newInstance(world.getClass().getClassLoader().loadClass(BIOME.replace('/','.')),length); }
            catch(ClassNotFoundException e){throw new IllegalStateException(e);}
        }
        for(int dz=0;dz<height;dz++)for(int dx=0;dx<width;dx++)reuse[dx+dz*width]=biome(world,quarter?(x+dx)<<2:x+dx,quarter?(z+dz)<<2:z+dz);
        return reuse;
    }
    public static Object[] gridCached(Object world,Object[] reuse,int x,int z,int width,int height,boolean cache) {return grid(world,reuse,x,z,width,height,false);}
    public static float[] rainfall(Object world,float[] reuse,int x,int z,int width,int height) {
        int length=Math.multiplyExact(width,height);if(reuse==null||reuse.length<length)reuse=new float[length];
        try {var field=world.getClass().getClassLoader().loadClass(BIOME.replace('/','.')).getField("field_76751_G");
            for(int dz=0;dz<height;dz++)for(int dx=0;dx<width;dx++)reuse[dx+dz*width]=Math.min(1f,(int)(field.getFloat(biome(world,x+dx,z+dz))*65536f)/65536f);
            return reuse;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static boolean viable(Object world,int x,int z,int radius,List<?> allowed) {
        for(int qz=(z-radius)>>2;qz<=((z+radius)>>2);qz++)for(int qx=(x-radius)>>2;qx<=((x+radius)>>2);qx++)if(!allowed.contains(biome(world,qx<<2,qz<<2)))return false;
        return true;
    }
    public static Object find(Object world,int x,int z,int radius,List<?> allowed,Random random) {
        Object found=null;int selected=0;
        try {var ctor=world.getClass().getClassLoader().loadClass("net.minecraft.world.ChunkPosition").getConstructor(int.class,int.class,int.class);
            for(int qz=(z-radius)>>2;qz<=((z+radius)>>2);qz++)for(int qx=(x-radius)>>2;qx<=((x+radius)>>2);qx++)
                if(allowed.contains(biome(world,qx<<2,qz<<2))&&(found==null||random.nextInt(selected+1)==0)){found=ctor.newInstance(qx<<2,0,qz<<2);selected++;}
            return found;
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
}
