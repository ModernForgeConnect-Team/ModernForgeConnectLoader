package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Mirrors host equipment onto old player actors, so original "is wearing X" checks (goggles revealing nodes and
 * aspects, vis discounts, warping gear, runic shields...) see it: slots 0..3 are armorInventory (boots first), slots
 * 4..7 the original Baubles inventory (its 0..3) when Baubles is loaded. The host decides per slot; on the logical
 * server, changes original code makes to a supplied stack are reported back. No Minecraft type crosses this boundary.
 */
public final class LegacyPlayerArmor {
    public static final int SLOTS = 8;
    /** One supplied slot; {@code stack} null is an empty slot. */
    public record Worn(LegacyClientData.Stack stack) {}
    public interface Source {
        /** Per slot (SLOTS entries), null leaves that old slot untouched; a null list means no such host player. */
        List<Worn> worn(UUID player, boolean client);
        /** Original code changed a supplied slot (server actors only); {@code after} null means it was removed. */
        default void changed(UUID player, int slot, LegacyClientData.Stack after) {}
    }
    private static final class Applied {
        final List<Worn> worn; final LegacyClientData.Stack[] written = new LegacyClientData.Stack[SLOTS]; final boolean[] supplied = new boolean[SLOTS];
        Applied(List<Worn> worn) { this.worn = worn; }
    }
    private record Access(Field inventory, Field armor, Method baubles, Field baubleStacks, Class<?> bauble, Method wornTick,
                          Method item, Method armorTick, Class<?> world, Class<?> player, Class<?> stack) {
        static Access of(ClassLoader loader) {
            try {
                var player = loader.loadClass("net.minecraft.entity.player.EntityPlayer"); var stack = loader.loadClass("net.minecraft.item.ItemStack");
                var world = loader.loadClass("net.minecraft.world.World");
                Method baubles = null; Field stacks = null; Class<?> bauble = null; Method wornTick = null;
                try {
                    baubles = loader.loadClass("baubles.common.lib.PlayerHandler").getMethod("getPlayerBaubles", player);
                    stacks = loader.loadClass("baubles.common.container.InventoryBaubles").getField("stackList");
                    bauble = loader.loadClass("baubles.api.IBauble");
                    wornTick = bauble.getMethod("onWornTick", stack, loader.loadClass("net.minecraft.entity.EntityLivingBase"));
                } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException absent) { baubles = null; stacks = null; bauble = null; wornTick = null; }
                return new Access(player.getField("field_71071_by"), loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getField("field_70460_b"),
                        baubles, stacks, bauble, wornTick, stack.getMethod("func_77973_b"),
                        loader.loadClass("net.minecraft.item.Item").getMethod("onArmorTick", world, player, stack), world, player, stack);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Old player inventory API is incomplete", failure); }
        }
        boolean hasBaubles() { return baubles != null; }
    }
    private static volatile Source source = (player, client) -> null;
    // Identity keys: old Entity.equals compares entity ids, which the client player and server actors now share.
    private static final Map<Object, Applied> APPLIED = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    private static final Map<ClassLoader, Access> ACCESS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<String> TICK_FAILURES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private LegacyPlayerArmor() {}
    public static void source(Source value) { source = Objects.requireNonNull(value); }
    private static Access access(ClassLoader loader) { return ACCESS.computeIfAbsent(loader, Access::of); }
    /** Whether original Baubles storage exists in this runtime. */
    public static boolean baubles(ClassLoader loader) { return access(loader).hasBaubles(); }
    /** Forgets cached client stacks, so the next refresh rebuilds them. */
    public static void invalidate() { APPLIED.clear(); }

    public static void apply(LegacyServices services, Object player, UUID id, boolean client) throws Exception {
        // Changes made since the last refresh go back first, so the host answers with the updated stacks.
        if (!client) collect(services, player, id);
        List<Worn> worn;
        try { worn = source.worn(id, client); }
        catch (RuntimeException unavailable) { return; }
        if (worn == null) return;
        // Old renderers ask for the client player every frame; only rebuild its old stacks when the host equipment changed.
        var previous = APPLIED.get(player);
        if (client && previous != null && worn.equals(previous.worn)) return;
        var applied = new Applied(Collections.unmodifiableList(new ArrayList<>(worn)));
        for (int slot = 0; slot < SLOTS && slot < worn.size(); slot++) {
            Worn value = worn.get(slot);
            Object[] target = slots(services.loader(), player, slot);
            int index = slot % 4;
            if (value == null || target == null || index >= target.length) continue;
            target[index] = value.stack() == null ? null : services.items().materialize(value.stack());
            applied.supplied[slot] = true; applied.written[slot] = value.stack();
        }
        APPLIED.put(player, applied);
    }

    /** Reports supplied slots original code changed since they were applied. Logical server only. */
    public static void collect(LegacyServices services, Object player, UUID id) throws Exception {
        var applied = APPLIED.get(player);
        if (applied == null) return;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!applied.supplied[slot]) continue;
            Object[] target = slots(services.loader(), player, slot);
            if (target == null || slot % 4 >= target.length) continue;
            Object current = target[slot % 4];
            var now = current == null || current.getClass().getField("field_77994_a").getInt(current) <= 0 ? null : services.items().snapshot(current);
            if (same(now, applied.written[slot])) continue;
            applied.written[slot] = now;
            try { source.changed(id, slot, now); }
            catch (RuntimeException failure) { LogUtils.getLogger().error("MFCL cannot write back old equipment slot {} for {}", slot, id, failure); }
        }
    }

    /**
     * One player tick of the supplied slots in {@code mask} (bit = slot): onArmorTick for armor, IBauble.onWornTick for
     * baubles, on the stacks actually in the old slots so in-place changes are seen by {@link #collect}.
     */
    public static void tick(LegacyServices services, Object world, Object player, int mask) throws Exception {
        var applied = APPLIED.get(player); if (applied == null) return;
        var access = access(services.loader());
        for (int slot = 0; slot < SLOTS; slot++) {
            if ((mask & 1 << slot) == 0 || !applied.supplied[slot]) continue;
            Object[] target = slots(services.loader(), player, slot);
            if (target == null || slot % 4 >= target.length || target[slot % 4] == null) continue;
            Object stack = target[slot % 4], item = access.item.invoke(stack);
            String key = item.getClass().getName();
            if (TICK_FAILURES.contains(key)) continue;
            try {
                if (slot < 4) access.armorTick.invoke(item, world, player, stack);
                else if (access.bauble.isInstance(item)) access.wornTick.invoke(item, stack, player);
            } catch (InvocationTargetException failure) {
                if (TICK_FAILURES.add(key)) LogUtils.getLogger().error("MFCL original worn tick failed for {}; disabled for this item", key, failure.getCause());
            }
        }
    }

    private static Object[] slots(ClassLoader loader, Object player, int slot) throws Exception {
        var access = access(loader);
        if (slot < 4) {
            Object inventory = access.inventory.get(player);
            return inventory == null ? null : (Object[]) access.armor.get(inventory);
        }
        if (!access.hasBaubles()) return null;
        Object inventory = access.baubles.invoke(null, player);
        return inventory == null ? null : (Object[]) access.baubleStacks.get(inventory);
    }
    private static boolean same(LegacyClientData.Stack left, LegacyClientData.Stack right) {
        if (left == null || right == null) return left == right;
        return left.key().equals(right.key()) && left.metadata() == right.metadata() && left.count() == right.count() && left.tag().equals(right.tag());
    }
}
