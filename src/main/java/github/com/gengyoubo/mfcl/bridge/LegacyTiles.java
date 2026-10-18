package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Real old TileEntity construction, lifecycle and binary NBT. The host owns positions and scheduling. */
public final class LegacyTiles {
    public record Placer(UUID id, String name, double x, double y, double z, float yaw, float pitch, boolean creative, boolean sneaking) {
        public Placer(UUID id, String name, double x, double y, double z, float yaw, float pitch) { this(id,name,x,y,z,yaw,pitch,false,false); }
    }
    private final LegacyServices services;
    private final ClassLoader loader;
    private final Class<?> blockType, tileType, worldType, nbtType;
    private final Object registry;
    public LegacyTiles(LegacyServices services) throws Exception {
        this.services = services; loader = services.loader();
        blockType = loader.loadClass("net.minecraft.block.Block"); tileType = loader.loadClass("net.minecraft.tileentity.TileEntity");
        worldType = loader.loadClass("net.minecraft.world.World"); nbtType = loader.loadClass("net.minecraft.nbt.NBTTagCompound");
        registry = blockType.getField("field_149771_c").get(null);
    }
    private Object block(String key) throws Exception {
        Object block = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, key);
        if (!key.equals(registry.getClass().getMethod("func_148750_c", Object.class).invoke(registry, block))) throw new IllegalArgumentException("Unknown old block " + key);
        return block;
    }
    public Object create(String key, int metadata, Object world, int x, int y, int z) throws Exception {
        return services.items().inLegacy(() -> {
            Object tile = blockType.getMethod("createTileEntity", worldType, int.class).invoke(block(key), world, metadata);
            if (tile == null) return null; // A technical block may report hasTileEntity but expose no tile for this metadata.
            attach(tile, key, metadata, world, x, y, z); return tile;
        });
    }
    public void attach(Object tile, String key, int metadata, Object world, int x, int y, int z) throws Exception {
        if (!tileType.isInstance(tile) || !worldType.isInstance(world)) throw new IllegalArgumentException("Tile/world belongs to another runtime");
        tileType.getMethod("func_145834_a", worldType).invoke(tile, world);
        tileType.getField("field_145851_c").setInt(tile, x); tileType.getField("field_145848_d").setInt(tile, y); tileType.getField("field_145849_e").setInt(tile, z);
        tileType.getField("field_145847_g").setInt(tile, metadata); tileType.getField("field_145854_h").set(tile, block(key));
        tileType.getMethod("func_145829_t").invoke(tile);
    }
    public byte[] save(Object tile) throws Exception {
        return services.items().inLegacy(() -> {
            Object nbt = nbtType.getConstructor().newInstance(); tileType.getMethod("func_145841_b", nbtType).invoke(tile, nbt);
            return (byte[]) loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a", nbtType).invoke(null, nbt);
        });
    }
    public void load(Object tile, byte[] bytes) throws Exception {
        services.items().inLegacy(() -> {
            Class<?> tracker = loader.loadClass("net.minecraft.nbt.NBTSizeTracker");
            Object nbt = loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_152457_a", byte[].class, tracker)
                    .invoke(null, bytes, tracker.getConstructor(long.class).newInstance(8L * 1024 * 1024));
            tileType.getMethod("func_145839_a", nbtType).invoke(tile, nbt); return null;
        });
    }
    public void invalidate(Object tile) throws Exception { services.items().inLegacy(() -> { tileType.getMethod("func_145843_s").invoke(tile); return null; }); }
    public void metadata(Object tile, int metadata) throws Exception { tileType.getField("field_145847_g").setInt(tile, metadata); }
    public void placed(String key, Object world, int x, int y, int z, Placer placer, LegacyClientData.Stack stack) throws Exception {
        services.items().inLegacy(() -> {
            Object actor = actor(world, placer, stack);
            blockType.getMethod("func_149689_a", worldType, int.class, int.class, int.class, loader.loadClass("net.minecraft.entity.EntityLivingBase"), loader.loadClass("net.minecraft.item.ItemStack"))
                    .invoke(block(key), world, x, y, z, actor, services.items().materialize(stack));
            return null;
        });
    }
    public Object actor(Object world, Placer placer, LegacyClientData.Stack stack) throws Exception {
        if (placer == null) return null;
        Class<?> actorType = loader.loadClass("net.minecraft.client.entity.EntityClientPlayerMP");
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); field.setAccessible(true); Object unsafe = field.get(null);
        Object actor = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, actorType);
        Class<?> player = loader.loadClass("net.minecraft.entity.player.EntityPlayer"), entity = loader.loadClass("net.minecraft.entity.Entity");
        var profile = player.getDeclaredField("field_146106_i"); profile.setAccessible(true);
        profile.set(actor, loader.loadClass("com.mojang.authlib.GameProfile").getConstructor(UUID.class, String.class).newInstance(placer.id(), placer.name()));
        Object inventory = loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getConstructor(player).newInstance(actor);
        player.getField("field_71071_by").set(actor, inventory);
        if (stack != null) inventory.getClass().getMethod("func_70299_a",int.class,loader.loadClass("net.minecraft.item.ItemStack")).invoke(inventory,0,services.items().materialize(stack));
        Object capabilities = loader.loadClass("net.minecraft.entity.player.PlayerCapabilities").getConstructor().newInstance();
        capabilities.getClass().getField("field_75098_d").setBoolean(capabilities, placer.creative());
        player.getField("field_71075_bZ").set(actor, capabilities);
        Object watcher = loader.loadClass("net.minecraft.entity.DataWatcher").getConstructor(entity).newInstance(actor);
        var data = entity.getDeclaredField("field_70180_af"); data.setAccessible(true); data.set(actor,watcher);
        watcher.getClass().getMethod("func_75682_a",int.class,Object.class).invoke(watcher,0,(byte)(placer.sneaking()?2:0));
        watcher.getClass().getMethod("func_75682_a",int.class,Object.class).invoke(watcher,6,20f);
        entity.getField("field_70170_p").set(actor, world);
        entity.getField("field_70165_t").setDouble(actor, placer.x()); entity.getField("field_70163_u").setDouble(actor, placer.y()); entity.getField("field_70161_v").setDouble(actor, placer.z());
        entity.getField("field_70177_z").setFloat(actor, placer.yaw()); entity.getField("field_70125_A").setFloat(actor, placer.pitch());
        var potions = loader.loadClass("net.minecraft.entity.EntityLivingBase").getDeclaredField("field_70713_bf"); potions.setAccessible(true); potions.set(actor,new HashMap<>());
        return actor;
    }
    public List<LegacyClientData.Stack> inventory(Object tile) throws Exception {
        return services.items().inLegacy(() -> {
            Class<?> inventory = loader.loadClass("net.minecraft.inventory.IInventory");
            if (!inventory.isInstance(tile)) return List.of();
            var result = new ArrayList<LegacyClientData.Stack>();
            int size = (int) inventory.getMethod("func_70302_i_").invoke(tile);
            for (int slot = 0; slot < size; slot++) {
                Object value = inventory.getMethod("func_70301_a", int.class).invoke(tile, slot);
                if (value != null) result.add(services.items().snapshot(value));
            }
            return List.copyOf(result);
        });
    }
}
