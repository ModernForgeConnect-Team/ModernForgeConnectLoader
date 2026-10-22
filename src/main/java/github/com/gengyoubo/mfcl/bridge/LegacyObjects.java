package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;

/** Reflective reads of original 1.7.10 game objects shared by mod adapters; types come from the object's own loader. */
public final class LegacyObjects {
    private LegacyObjects() {}
    /** A field declared by the value's own class (packets keep their payload in private fields). */
    public static Field declaredField(Object value,String name) throws NoSuchFieldException {
        var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field;
    }
    public static Object declared(Object value,String name) throws Exception { return declaredField(value,name).get(value); }
    public static Object world(Object entity) throws Exception {
        return entity.getClass().getClassLoader().loadClass("net.minecraft.entity.Entity").getField("field_70170_p").get(entity);
    }
    public static int dimension(Object world) throws Exception {
        var loader=world.getClass().getClassLoader();
        Object provider=loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
        return loader.loadClass("net.minecraft.world.WorldProvider").getField("field_76574_g").getInt(provider);
    }
    public static int entityId(Object entity) throws Exception {
        return (int)entity.getClass().getClassLoader().loadClass("net.minecraft.entity.Entity").getMethod("func_145782_y").invoke(entity);
    }
    public static String playerName(Object player) throws Exception {
        return (String)player.getClass().getClassLoader().loadClass("net.minecraft.entity.player.EntityPlayer").getMethod("func_70005_c_").invoke(player);
    }
    public static Object heldStack(Object player) throws Exception {
        return player.getClass().getClassLoader().loadClass("net.minecraft.entity.player.EntityPlayer").getMethod("func_70694_bm").invoke(player);
    }
    /** The block's registry key ("Mod:name") in the old block registry. */
    public static String blockKey(Object block) throws Exception {
        Object registry=block.getClass().getClassLoader().loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
        return (String)registry.getClass().getMethod("func_148750_c",Object.class).invoke(registry,block);
    }
    /** The stack's item, or null for an empty slot. */
    public static Object item(Object stack) throws Exception {
        return stack==null?null:stack.getClass().getClassLoader().loadClass("net.minecraft.item.ItemStack").getMethod("func_77973_b").invoke(stack);
    }
}
