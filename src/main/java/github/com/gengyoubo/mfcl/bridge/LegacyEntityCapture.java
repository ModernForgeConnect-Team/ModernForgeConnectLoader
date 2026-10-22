package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Preserve host entity data through original capture-item logic without serializing an incomplete query mirror. */
public final class LegacyEntityCapture {
    private static final String DATA="mfcl:captured_host_entity";
    private static final Map<Object,String> RESTORED=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private LegacyEntityCapture(){}
    public static boolean write(Object entity,Object tag) throws Exception {
        var loader=entity.getClass().getClassLoader();var base=loader.loadClass("net.minecraft.entity.Entity");
        if(!LegacyEntityHooks.isMirror(entity))return (boolean)base.getMethod("func_98035_c",loader.loadClass("net.minecraft.nbt.NBTTagCompound")).invoke(entity,tag);
        var host=LegacyWorlds.access(base.getField("field_70170_p").get(entity));
        String data=host.entityData((UUID)base.getMethod("func_110124_au").invoke(entity));if(data==null)return false;
        String name=(String)loader.loadClass("net.minecraft.entity.EntityList").getMethod("func_75621_b",base).invoke(null,entity);if(name==null)return false;
        var nbt=loader.loadClass("net.minecraft.nbt.NBTTagCompound");
        nbt.getMethod("func_74778_a",String.class,String.class).invoke(tag,"id",name);
        nbt.getMethod("func_74778_a",String.class,String.class).invoke(tag,DATA,data);
        return true;
    }
    public static Object read(Object tag,Object world) throws Exception {
        var loader=world.getClass().getClassLoader();var nbt=loader.loadClass("net.minecraft.nbt.NBTTagCompound");
        String data=(String)nbt.getMethod("func_74779_i",String.class).invoke(tag,DATA);
        if(data.isEmpty())return loader.loadClass("net.minecraft.entity.EntityList").getMethod("func_75615_a",nbt,loader.loadClass("net.minecraft.world.World")).invoke(null,tag,world);
        String id=(String)nbt.getMethod("func_74779_i",String.class).invoke(tag,"id");
        Class<?> type=(Class<?>)((Map<?,?>)loader.loadClass("net.minecraft.entity.EntityList").getField("field_75625_b").get(null)).get(id);
        if(type==null)throw new IllegalStateException("Captured host entity has no original type: "+id);
        Object pos=nbt.getMethod("func_150295_c",String.class,int.class).invoke(tag,"Pos",6);
        var at=pos.getClass().getMethod("func_150309_d",int.class);
        var snapshot=new HostWorldAccess.Entity(UUID.randomUUID(),type.getName(),"",(double)at.invoke(pos,0),(double)at.invoke(pos,1),(double)at.invoke(pos,2),0,0,0,0,.6f,1.8f,false,true);
        Object entity=LegacyEntityAccess.mirror(LegacyWorlds.items(world),world,snapshot,null);
        RESTORED.put(entity,data);return entity;
    }
    public static void remove(Object entity) throws Exception {
        var loader=entity.getClass().getClassLoader();var base=loader.loadClass("net.minecraft.entity.Entity");
        if(!LegacyEntityHooks.isMirror(entity)){base.getMethod("func_70106_y").invoke(entity);return;}
        LegacyWorlds.access(base.getField("field_70170_p").get(entity)).removeEntity((UUID)base.getMethod("func_110124_au").invoke(entity));
        base.getField("field_70128_L").setBoolean(entity,true);
    }
    static Boolean spawn(Object world,Object entity) throws Exception {
        String data=RESTORED.remove(entity);if(data==null)return null;
        var loader=entity.getClass().getClassLoader();var base=loader.loadClass("net.minecraft.entity.Entity");
        var living=loader.loadClass("net.minecraft.entity.EntityLiving");String name=living.isInstance(entity)?(String)living.getMethod("func_94057_bL").invoke(entity):"";
        if(!LegacyWorlds.access(world).restoreEntity(data,base.getField("field_70165_t").getDouble(entity),base.getField("field_70163_u").getDouble(entity),base.getField("field_70161_v").getDouble(entity),name))
            throw new IllegalStateException("Host rejected captured entity restoration");
        return true;
    }
}
