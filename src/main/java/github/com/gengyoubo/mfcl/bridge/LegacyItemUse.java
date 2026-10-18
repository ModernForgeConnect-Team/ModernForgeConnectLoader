package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Original right-click / held-use callbacks. The old player is cached per UUID so research, item-in-use and
 * other per-player state survive across the begin/tick/stop sequence the modern engine drives.
 */
public final class LegacyItemUse {
    public record Result(boolean used, int useDuration, String prompt) {}
    /** A finished legacy scan, ready to be replayed against the authoritative player. */
    public record Scan(byte type, int id, int meta, String phenomena) {}
    private final LegacyServices services;
    // The same UUID exists on both the integrated server and the client; keep their actors apart.
    private final Map<String, Object> actors = new HashMap<>();
    public LegacyItemUse(LegacyServices services) { this.services = services; }
    public Result rightClick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            Object returned = itemType.getMethod("func_77659_a", stackType, worldType, playerType).invoke(item, stack, world, player);
            int duration = (int) itemType.getMethod("func_77626_a", stackType).invoke(item, stack);
            boolean used = duration > 0 || !services.items().snapshot(returned).equals(snapshot);
            String prompt = scanPrompt(item, loader);
            boolean remote = loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world);
            LogUtils.getLogger().info("MFCL legacy use {}@{}: remote={}, duration={}, target={}", snapshot.key(), snapshot.metadata(), remote, duration, prompt);
            return new Result(used, duration, prompt);
        });
    }
    /**
     * A held-use item that records a target while scanning (Thaumcraft's thaumometer stores it in the package-private
     * {@code startScan}) exposes no modern overlay yet, so surface the target as text instead of leaving the click silent.
     */
    private static String scanPrompt(Object item, ClassLoader loader) {
        try {
            Object scan = scanField(item);
            if (scan == null) return null;
            byte type = scan.getClass().getField("type").getByte(scan);
            if (type == 2) { Object entity = scan.getClass().getField("entity").get(scan); return entity == null ? null : (String) entity.getClass().getMethod("func_70005_c_").invoke(entity); }
            if (type == 3) return (String) scan.getClass().getField("phenomena").get(scan);
            int id = scan.getClass().getField("id").getInt(scan), meta = scan.getClass().getField("meta").getInt(scan);
            Object scanned = loader.loadClass("net.minecraft.item.Item").getMethod("func_150899_d", int.class).invoke(null, id);
            if (scanned == null) return null;
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Object stack = stackType.getConstructor(loader.loadClass("net.minecraft.item.Item"), int.class, int.class).newInstance(scanned, 1, meta);
            String key = (String) scanned.getClass().getMethod("func_77667_c", stackType).invoke(scanned, stack);
            Method translate = loader.loadClass("net.minecraft.util.StatCollector").getMethod("func_74838_a", String.class);
            translate.setAccessible(true);
            String localized = (String) translate.invoke(null, key + ".name");
            return localized.equals(key + ".name") ? (String) translate.invoke(null, key) : localized;
        } catch (Throwable absent) { return null; }
    }
    private static Object scanField(Object item) throws Exception {
        for (Class<?> type = item.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField("startScan"); field.setAccessible(true); return field.get(item); }
            catch (NoSuchFieldException absent) { }
        }
        return null;
    }
    public Scan usingTick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            Object before = scanField(item);
            itemType.getMethod("onUsingTick", stackType, playerType, int.class).invoke(item, stack, player, remaining);
            Object after = scanField(item);
            if (remaining <= 6) LogUtils.getLogger().info("MFCL legacy use tick {}@{}: remaining={}, target={}",
                    snapshot.key(), snapshot.metadata(), remaining, scanPrompt(item, loader));
            // The original clears startScan when it reaches the completion branch; replay that target authoritatively.
            if (remaining <= 5 && before != null && after == null) {
                Class<?> scanType = before.getClass();
                return new Scan(scanType.getField("type").getByte(before), scanType.getField("id").getInt(before),
                        scanType.getField("meta").getInt(before), (String) scanType.getField("phenomena").get(before));
            }
            return null;
        });
    }
    /** Runs the original {@code ScanManager.completeScan} against the authoritative (server) old player. */
    public void applyScan(Object world, LegacyTiles.Placer placer, byte type, int id, int meta, String phenomena, String prefix) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> entityType = loader.loadClass("net.minecraft.entity.Entity");
            Object scan = loader.loadClass("thaumcraft.api.research.ScanResult").getConstructor(byte.class, int.class, int.class, entityType, String.class)
                    .newInstance(type, id, meta, null, phenomena);
            Object player = actor(world, placer, null);
            loader.loadClass("thaumcraft.common.lib.research.ScanManager")
                    .getMethod("completeScan", loader.loadClass("net.minecraft.entity.player.EntityPlayer"), loader.loadClass("thaumcraft.api.research.ScanResult"), String.class)
                    .invoke(null, player, scan, prefix == null ? "@" : prefix);
            return null;
        });
    }
    public void release(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Class<?> worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            itemType.getMethod("func_77615_a", stackType, worldType, playerType, int.class).invoke(item, stack, world, player, remaining);
            return null;
        });
    }
    public int useDuration(LegacyClientData.Stack snapshot) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Object stack = services.items().materialize(snapshot);
            return (int) itemType.getMethod("func_77626_a", stackType).invoke(stackType.getMethod("func_77973_b").invoke(stack), stack);
        });
    }
    private Object actor(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        var loader = services.loader();
        // Thaumcraft's held-use tick compares player names with `==`, so on the client the actor must be the very
        // old client player that Minecraft.getMinecraft().thePlayer exposes, not an equal-but-distinct instance.
        if (loader.loadClass("net.minecraft.world.World").getField("field_72995_K").getBoolean(world)) {
            services.containers().preparePlayer();
            Class<?> mc = loader.loadClass("net.minecraft.client.Minecraft");
            Object instance = mc.getMethod("func_71410_x").invoke(null);
            Object client = mc.getField("field_71439_g").get(instance);
            if (client != null) { refresh(client, world, placer, snapshot); return client; }
        }
        String key = System.identityHashCode(world) + "|" + placer.id();
        Object actor = actors.get(key);
        if (actor == null) { actor = services.tiles().actor(world, placer, snapshot); actors.put(key, actor); }
        else refresh(actor, world, placer, snapshot);
        return actor;
    }
    private void refresh(Object actor, Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot) throws Exception {
        Class<?> entity = services.loader().loadClass("net.minecraft.entity.Entity");
        entity.getField("field_70170_p").set(actor, world);
        entity.getField("field_70165_t").setDouble(actor, placer.x()); entity.getField("field_70163_u").setDouble(actor, placer.y()); entity.getField("field_70161_v").setDouble(actor, placer.z());
        entity.getField("field_70177_z").setFloat(actor, placer.yaw()); entity.getField("field_70125_A").setFloat(actor, placer.pitch());
        // Range/ray-trace helpers read the bounding box; the reused host player may never have had one built.
        Object box = services.loader().loadClass("net.minecraft.util.AxisAlignedBB").getMethod("func_72330_a", double.class, double.class, double.class, double.class, double.class, double.class)
                .invoke(null, placer.x() - 0.3, placer.y(), placer.z() - 0.3, placer.x() + 0.3, placer.y() + 1.8, placer.z() + 0.3);
        Field bbox = entity.getDeclaredField("field_70121_D"); bbox.setAccessible(true); bbox.set(actor, box);
        entity.getField("field_70130_N").setFloat(actor, 0.6f); entity.getField("field_70131_O").setFloat(actor, 1.8f);
        var inventory = services.loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(actor);
        if (inventory != null && snapshot != null)
            inventory.getClass().getMethod("func_70299_a", int.class, services.loader().loadClass("net.minecraft.item.ItemStack"))
                    .invoke(inventory, 0, services.items().materialize(snapshot));
    }
}
