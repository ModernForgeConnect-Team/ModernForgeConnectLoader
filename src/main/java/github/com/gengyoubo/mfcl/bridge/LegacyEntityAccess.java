package github.com.gengyoubo.mfcl.bridge;

import org.objectweb.asm.*;
import java.util.*;

/** Entity query snapshots; only motion/fall changes are committed back to the owning host world. */
final class LegacyEntityAccess {
    private static final Map<ClassLoader,Map<String,Class<?>>> FACADES=Collections.synchronizedMap(new WeakHashMap<>());
    private LegacyEntityAccess() {}
    static Object mirror(ClassLoader loader,Object world,HostWorldAccess.Entity snapshot,Object existing) throws Exception {
        boolean generic=snapshot.legacyClass().equals("net.minecraft.entity.Entity")||snapshot.legacyClass().equals("net.minecraft.entity.EntityLiving");
        Class<?> type=generic?facade(loader,snapshot.legacyClass()):loader.loadClass(snapshot.legacyClass());
        var unsafeField=Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");unsafeField.setAccessible(true);Object unsafe=unsafeField.get(null);
        Object entity=existing==null?unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,type):existing;
        var base=loader.loadClass("net.minecraft.entity.Entity");
        base.getField("field_70170_p").set(entity,world);
        base.getField("field_70165_t").setDouble(entity,snapshot.x());base.getField("field_70163_u").setDouble(entity,snapshot.y());base.getField("field_70161_v").setDouble(entity,snapshot.z());
        base.getField("field_70159_w").setDouble(entity,snapshot.motionX());base.getField("field_70181_x").setDouble(entity,snapshot.motionY());base.getField("field_70179_y").setDouble(entity,snapshot.motionZ());
        base.getField("field_70143_R").setFloat(entity,snapshot.fallDistance());base.getField("field_70130_N").setFloat(entity,snapshot.width());base.getField("field_70131_O").setFloat(entity,snapshot.height());
        if(existing==null) {
            for(var entry:Map.of("field_96093_i",(Object)snapshot.id(),"field_70146_Z",new Random()).entrySet()){var field=base.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(entity,entry.getValue());}
            Object watcher=loader.loadClass("net.minecraft.entity.DataWatcher").getConstructor(base).newInstance(entity);
            var add=watcher.getClass().getMethod("func_75682_a",int.class,Object.class);
            // Entity mirrors bypass constructors, so reproduce the vanilla DataWatcher entries old code reads.
            add.invoke(watcher,0,(byte)(snapshot.sneaking()?2:0));
            add.invoke(watcher,1,(short)300);
            Class<?> livingBase=loader.loadClass("net.minecraft.entity.EntityLivingBase");
            if(livingBase.isAssignableFrom(type)) {
                add.invoke(watcher,6,1f);add.invoke(watcher,7,0);add.invoke(watcher,8,(byte)0);add.invoke(watcher,9,(byte)0);
                if(loader.loadClass("net.minecraft.entity.EntityLiving").isAssignableFrom(type)) {add.invoke(watcher,10,"");add.invoke(watcher,11,(byte)0);}
            }
            var watched=base.getDeclaredField("field_70180_af");watched.setAccessible(true);watched.set(entity,watcher);
            Class<?> playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            if(playerType.isAssignableFrom(type)) {
                add.invoke(watcher,16,(byte)0);add.invoke(watcher,17,0f);add.invoke(watcher,18,0);
                var profile=playerType.getDeclaredField("field_146106_i");profile.setAccessible(true);
                profile.set(entity,loader.loadClass("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class).newInstance(snapshot.id(),snapshot.name()));
            }
        }
        double half=snapshot.width()/2;
        Object box=loader.loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a",double.class,double.class,double.class,double.class,double.class,double.class).invoke(null,snapshot.x()-half,snapshot.y(),snapshot.z()-half,snapshot.x()+half,snapshot.y()+snapshot.height(),snapshot.z()+half);
        var bounds=base.getField("field_70121_D");bounds.setAccessible(true);bounds.set(entity,box);
        if(generic&&existing==null)type.getField("mfclPushable").setBoolean(entity,snapshot.pushable());
        return entity;
    }
    static HostWorldAccess.Motion motion(ClassLoader loader,Object entity,UUID id) throws Exception {
        var type=loader.loadClass("net.minecraft.entity.Entity");
        return new HostWorldAccess.Motion(id,type.getField("field_70159_w").getDouble(entity),type.getField("field_70181_x").getDouble(entity),type.getField("field_70179_y").getDouble(entity),type.getField("field_70143_R").getFloat(entity));
    }
    private static Class<?> facade(ClassLoader loader,String parent) throws Exception {
        var types=FACADES.computeIfAbsent(loader,ignored->new HashMap<>());Class<?> existing=types.get(parent);if(existing!=null)return existing;
        String name="github/com/gengyoubo/mfcl/legacy1710/Host"+parent.substring(parent.lastIndexOf('.')+1)+"Snapshot";
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);writer.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,parent.replace('.','/'),null);
        writer.visitField(Opcodes.ACC_PUBLIC,"mfclPushable","Z",null,null).visitEnd();
        for(String method:List.of("func_70088_a","func_70037_a","func_70014_b")) {
            var mv=writer.visitMethod(Opcodes.ACC_PUBLIC,method,method.equals("func_70088_a")?"()V":"(Lnet/minecraft/nbt/NBTTagCompound;)V",null,null);mv.visitCode();mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,2);mv.visitEnd();
        }
        var mv=writer.visitMethod(Opcodes.ACC_PUBLIC,"func_70104_M","()Z",null,null);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,name,"mfclPushable","Z");mv.visitInsn(Opcodes.IRETURN);mv.visitMaxs(1,1);mv.visitEnd();writer.visitEnd();
        byte[] bytes=writer.toByteArray();var type=(Class<?>)loader.getClass().getMethod("defineLegacyClass",String.class,byte[].class,int.class,int.class).invoke(loader,name.replace('/','.'),bytes,0,bytes.length);types.put(parent,type);return type;
    }
}
