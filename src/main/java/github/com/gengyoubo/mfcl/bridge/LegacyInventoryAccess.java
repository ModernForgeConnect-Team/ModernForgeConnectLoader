package github.com.gengyoubo.mfcl.bridge;

import java.util.stream.IntStream;

/** Preserves original IInventory/ISidedInventory rules and copies stacks across the runtime boundary. */
public final class LegacyInventoryAccess {
    private final LegacyServices services;
    private final Class<?> inventory, sided, stack;
    public LegacyInventoryAccess(LegacyServices services) throws Exception {
        this.services = services; var loader = services.loader();
        inventory = loader.loadClass("net.minecraft.inventory.IInventory"); sided = loader.loadClass("net.minecraft.inventory.ISidedInventory"); stack = loader.loadClass("net.minecraft.item.ItemStack");
    }
    public boolean supports(Object tile) { return inventory.isInstance(tile); }
    public int[] slots(Object tile, Integer side) throws Exception {
        return services.items().inLegacy(() -> side != null && sided.isInstance(tile)
                ? ((int[])sided.getMethod("func_94128_d",int.class).invoke(tile,side)).clone()
                : IntStream.range(0,(int)inventory.getMethod("func_70302_i_").invoke(tile)).toArray());
    }
    public int limit(Object tile) throws Exception { return services.items().inLegacy(() -> (int)inventory.getMethod("func_70297_j_").invoke(tile)); }
    public LegacyClientData.Stack get(Object tile, int slot) throws Exception {
        return services.items().inLegacy(() -> {
            Object value = inventory.getMethod("func_70301_a",int.class).invoke(tile,slot);
            return value == null || stack.getField("field_77994_a").getInt(value) <= 0 ? null : services.items().snapshot(value);
        });
    }
    public boolean allows(Object tile, int slot, LegacyClientData.Stack value, Integer side, boolean insert) throws Exception {
        return services.items().inLegacy(() -> {
            Object old = services.items().materialize(value);
            if (insert && !(boolean)inventory.getMethod("func_94041_b",int.class,stack).invoke(tile,slot,old)) return false;
            return side == null || !sided.isInstance(tile) || (boolean)sided.getMethod(insert ? "func_102007_a" : "func_102008_b",int.class,stack,int.class).invoke(tile,slot,old,side);
        });
    }
    public void set(Object tile, int slot, LegacyClientData.Stack value) throws Exception {
        services.items().inLegacy(() -> {
            inventory.getMethod("func_70299_a",int.class,stack).invoke(tile,slot,value == null ? null : services.items().materialize(value));
            inventory.getMethod("func_70296_d").invoke(tile); return null;
        });
    }
    public LegacyClientData.Stack remove(Object tile, int slot, int count) throws Exception {
        return services.items().inLegacy(() -> {
            Object value = inventory.getMethod("func_70298_a",int.class,int.class).invoke(tile,slot,count);
            if (value != null) inventory.getMethod("func_70296_d").invoke(tile);
            return value == null ? null : services.items().snapshot(value);
        });
    }
}
