package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;

/** Original gameplay callbacks. Scheduling, authority and rollback belong to the host level. */
public final class LegacyBlockLogic {
    private static final ThreadLocal<Boolean> ACTIVATING = new ThreadLocal<>();
    public record Activation(boolean handled, java.util.List<LegacyClientData.Stack> inventory) {}
    public static void requireGuiBridge() {
        if (Boolean.TRUE.equals(ACTIVATING.get())) throw new UnsupportedOperationException("Original device container/GUI bridge is pending");
    }
    private final LegacyServices services;
    private final Class<?> blockType, worldType, tileType;
    private final Object registry;
    public LegacyBlockLogic(LegacyServices services) throws Exception {
        this.services = services; var loader = services.loader();
        blockType = loader.loadClass("net.minecraft.block.Block"); worldType = loader.loadClass("net.minecraft.world.World");
        tileType = loader.loadClass("net.minecraft.tileentity.TileEntity"); registry = blockType.getField("field_149771_c").get(null);
    }
    private Object block(String key) throws Exception { return registry.getClass().getMethod("func_82594_a",String.class).invoke(registry,key); }
    public boolean tick(Object tile) throws Exception {
        return services.items().inLegacy(() -> {
            if (tile == null || (boolean)tileType.getMethod("func_145837_r").invoke(tile) || !(boolean)tileType.getMethod("canUpdate").invoke(tile)) return false;
            tileType.getMethod("func_145845_h").invoke(tile); return true;
        });
    }
    public boolean event(String key, Object world, int x, int y, int z, int event, int data) throws Exception {
        return services.items().inLegacy(() -> (boolean)blockType.getMethod("func_149696_a",worldType,int.class,int.class,int.class,int.class,int.class)
                .invoke(block(key),world,x,y,z,event,data));
    }
    public Activation activate(String key, Object world, int x, int y, int z, LegacyTiles.Placer actor, java.util.List<LegacyClientData.Stack> contents, int selected, int side, float hitX, float hitY, float hitZ) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader(); var playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            var inventoryType = loader.loadClass("net.minecraft.entity.player.InventoryPlayer"); var stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Object player = services.tiles().actor(world,actor,null), inventory = playerType.getField("field_71071_by").get(player);
            for (int slot = 0; slot < contents.size(); slot++) inventoryType.getMethod("func_70299_a",int.class,stackType).invoke(inventory,slot,contents.get(slot) == null ? null : services.items().materialize(contents.get(slot)));
            inventoryType.getField("field_70461_c").setInt(inventory,selected);
            Object container = loader.loadClass("net.minecraft.inventory.ContainerPlayer").getConstructor(inventoryType,boolean.class,playerType).newInstance(inventory,false,player);
            playerType.getField("field_71069_bz").set(player,container); playerType.getField("field_71070_bA").set(player,container);
            ACTIVATING.set(true);
            boolean handled;
            try { handled = (boolean)blockType.getMethod("func_149727_a",worldType,int.class,int.class,int.class,playerType,int.class,float.class,float.class,float.class)
                    .invoke(block(key),world,x,y,z,player,side,hitX,hitY,hitZ); }
            finally { ACTIVATING.remove(); }
            var result = new java.util.ArrayList<LegacyClientData.Stack>();
            for (int slot = 0; slot < contents.size(); slot++) {
                Object value = inventoryType.getMethod("func_70301_a",int.class).invoke(inventory,slot);
                result.add(value == null || stackType.getField("field_77994_a").getInt(value) <= 0 ? null : services.items().snapshot(value));
            }
            return new Activation(handled,result);
        });
    }
    public void scheduled(String key, Object world, int x, int y, int z) throws Exception {
        services.items().inLegacy(() -> {
            blockType.getMethod("func_149674_a",worldType,int.class,int.class,int.class,java.util.Random.class)
                    .invoke(block(key),world,x,y,z,worldType.getField("field_73012_v").get(world)); return null;
        });
    }
}
