package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.util.Map;

/** Original callback statistics backed by the host player's durable counters and advancement progress. */
public final class LegacyPlayerStatistics {
    private static final Map<Object,Object> OWNERS=new com.google.common.collect.MapMaker().weakKeys().weakValues().makeMap();
    private LegacyPlayerStatistics() {}
    public static Object create(ClassLoader loader,java.io.File path) throws Exception {return create(loader,path,null);}
    public static Object create(ClassLoader loader,java.io.File path,Object player) throws Exception {
        String name="github.com.gengyoubo.mfcl.legacy1710.HostPlayerStatistics";Class<?> type;
        try{type=loader.loadClass(name);}catch(ClassNotFoundException missing) {
            String owner=name.replace('.','/');var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
            writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,owner,null,"net/minecraft/stats/StatisticsFile",null);
            var mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Ljava/io/File;)V",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitInsn(Opcodes.ACONST_NULL);mv.visitVarInsn(Opcodes.ALOAD,1);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/stats/StatisticsFile","<init>","(Lnet/minecraft/server/MinecraftServer;Ljava/io/File;)V",false);mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(3,2);mv.visitEnd();
            mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_150873_a","(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/stats/StatBase;I)V",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitVarInsn(Opcodes.ALOAD,2);mv.visitVarInsn(Opcodes.ILOAD,3);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(LegacyPlayerStatistics.class),"update","(Ljava/lang/Object;Ljava/lang/Object;I)V",false);mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(3,4);mv.visitEnd();
            mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_77444_a","(Lnet/minecraft/stats/StatBase;)I",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitVarInsn(Opcodes.ALOAD,1);mv.visitMethodInsn(Opcodes.INVOKESTATIC,Type.getInternalName(LegacyPlayerStatistics.class),"read","(Ljava/lang/Object;Ljava/lang/Object;)I",false);mv.visitInsn(Opcodes.IRETURN);mv.visitMaxs(2,2);mv.visitEnd();writer.visitEnd();
            byte[] bytes=writer.toByteArray();type=(Class<?>)loader.getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class).invoke(loader,name,bytes,0,bytes.length);
        }
        Object result=type.getConstructor(java.io.File.class).newInstance(path);if(player!=null)OWNERS.put(result,player);return result;
    }
    @SuppressWarnings("unchecked") public static void update(Object statistics,Object stat,int value) throws Exception {
        var loader=statistics.getClass().getClassLoader();var field=loader.loadClass("net.minecraft.stats.StatFileWriter").getDeclaredField("field_150875_a");field.setAccessible(true);
        var values=(Map<Object,Object>)field.get(statistics);Object tuple=values.get(stat);
        if(tuple==null){tuple=loader.loadClass("net.minecraft.util.TupleIntJsonSerializable").getConstructor().newInstance();values.put(stat,tuple);}
        tuple.getClass().getMethod("func_151188_a",int.class).invoke(tuple,value);
        Object actor=OWNERS.get(statistics);if(actor!=null){Object world=loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p").get(actor);var host=LegacyWorlds.hostOrNull(world);
            if(host!=null&&!host.clientSide())host.statistic((java.util.UUID)actor.getClass().getMethod("func_110124_au").invoke(actor),(String)loader.loadClass("net.minecraft.stats.StatBase").getField("field_75975_e").get(stat),value);
        }
    }
    public static int read(Object statistics,Object stat)throws Exception {
        var loader=statistics.getClass().getClassLoader();Object actor=OWNERS.get(statistics);
        if(actor!=null){var host=LegacyWorlds.hostOrNull(loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p").get(actor));
            Integer value=host==null?null:host.statistic((java.util.UUID)actor.getClass().getMethod("func_110124_au").invoke(actor),(String)loader.loadClass("net.minecraft.stats.StatBase").getField("field_75975_e").get(stat));if(value!=null)return value;
        }
        var field=loader.loadClass("net.minecraft.stats.StatFileWriter").getDeclaredField("field_150875_a");field.setAccessible(true);Object tuple=((Map<?,?>)field.get(statistics)).get(stat);
        return tuple==null?0:(int)tuple.getClass().getMethod("func_151189_a").invoke(tuple);
    }
}
