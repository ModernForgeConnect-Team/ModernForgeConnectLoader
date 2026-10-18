package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/**
 * Original right-click / held-use callbacks. The old player is cached per UUID so research, item-in-use and
 * other per-player state survive across the begin/tick/stop sequence the modern engine drives.
 */
public final class LegacyItemUse {
    public record Result(boolean used, int useDuration) {}
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
            return new Result(used, duration);
        });
    }
    public void usingTick(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot, int remaining) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            Object stack = services.items().materialize(snapshot);
            Object item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = actor(world, placer, snapshot);
            itemType.getMethod("onUsingTick", stackType, playerType, int.class).invoke(item, stack, player, remaining);
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
        String key = System.identityHashCode(world) + "|" + placer.id();
        Object actor = actors.get(key);
        if (actor == null) { actor = services.tiles().actor(world, placer, snapshot); actors.put(key, actor); return actor; }
        // Refresh the cached actor so world, position and held stack track the modern player.
        Class<?> entity = services.loader().loadClass("net.minecraft.entity.Entity");
        entity.getField("field_70170_p").set(actor, world);
        entity.getField("field_70165_t").setDouble(actor, placer.x()); entity.getField("field_70163_u").setDouble(actor, placer.y()); entity.getField("field_70161_v").setDouble(actor, placer.z());
        entity.getField("field_70177_z").setFloat(actor, placer.yaw()); entity.getField("field_70125_A").setFloat(actor, placer.pitch());
        var inventory = services.loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(actor);
        if (inventory != null && snapshot != null)
            inventory.getClass().getMethod("func_70299_a", int.class, services.loader().loadClass("net.minecraft.item.ItemStack"))
                    .invoke(inventory, 0, services.items().materialize(snapshot));
        return actor;
    }
}
