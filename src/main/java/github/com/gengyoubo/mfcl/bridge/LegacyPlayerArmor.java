package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/**
 * Mirrors the host player's worn armor onto old player actors, so original "is wearing X" checks (goggles revealing
 * nodes and aspects, set bonuses...) see it. The host supplies the stacks; no Minecraft type crosses this boundary.
 */
public final class LegacyPlayerArmor {
    /** Host armor for a player, old armorInventory order (0 boots .. 3 helmet); null entries are empty or unmapped. */
    public interface Source { List<LegacyClientData.Stack> worn(UUID player, boolean client); }
    private static volatile Source source = (player, client) -> null;
    private static final Map<Object, List<LegacyClientData.Stack>> APPLIED = Collections.synchronizedMap(new WeakHashMap<>());
    private LegacyPlayerArmor() {}
    public static void source(Source value) { source = Objects.requireNonNull(value); }

    public static void apply(LegacyServices services, Object player, UUID id, boolean client) throws Exception {
        List<LegacyClientData.Stack> worn;
        try { worn = source.worn(id, client); }
        catch (RuntimeException unavailable) { return; }
        if (worn == null) return;
        // Old renderers ask for the client player every frame; only rebuild its old stacks when the host armor changed.
        if (client && worn.equals(APPLIED.get(player))) return;
        var loader = services.loader();
        Object inventory = loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        if (inventory == null) return;
        Object[] armor = (Object[]) loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getField("field_70460_b").get(inventory);
        for (int slot = 0; slot < armor.length && slot < worn.size(); slot++)
            armor[slot] = worn.get(slot) == null ? null : services.items().materialize(worn.get(slot));
        if (client) APPLIED.put(player, Collections.unmodifiableList(new ArrayList<>(worn)));
    }
}
