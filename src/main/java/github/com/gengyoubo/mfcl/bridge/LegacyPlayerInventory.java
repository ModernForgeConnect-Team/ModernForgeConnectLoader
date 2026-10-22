package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;
import java.util.*;

/** Exposes the host main inventory to old item logic and applies old-code changes back, slot by slot. */
public final class LegacyPlayerInventory {
    /** The host collects and synchronizes this actor's inventory after its callback transaction commits. */
    public static void synchronize(Object ignored,Object actor) throws Exception {
        var loader=actor.getClass().getClassLoader();var entity=loader.loadClass("net.minecraft.entity.Entity");
        if(LegacyWorlds.hostOrNull(entity.getField("field_70170_p").get(actor))!=null)return;
        if(ignored!=null)ignored.getClass().getMethod("func_72385_f",loader.loadClass("net.minecraft.entity.player.EntityPlayerMP")).invoke(ignored,actor);
    }
    private final LegacyServices services;
    private final Field inventoryField, mainField, currentField;
    public LegacyPlayerInventory(LegacyServices services) throws Exception {
        this.services = services;
        ClassLoader loader = services.loader();
        inventoryField = loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by");
        Class<?> inventoryType = loader.loadClass("net.minecraft.entity.player.InventoryPlayer");
        mainField = inventoryType.getField("field_70462_a");
        currentField = inventoryType.getField("field_70461_c");
    }
    public void write(Object player, List<LegacyClientData.Stack> main) throws Exception {
        services.items().inLegacy(() -> {
            Object[] slots = (Object[]) mainField.get(inventoryField.get(player));
            for (int index = 0; index < slots.length; index++) {
                LegacyClientData.Stack value = index < main.size() ? main.get(index) : null;
                slots[index] = value == null ? null : services.items().materialize(value);
            }
            return null;
        });
    }
    public List<LegacyClientData.Stack> read(Object player) throws Exception {
        return services.items().inLegacy(() -> {
            Object[] slots = (Object[]) mainField.get(inventoryField.get(player));
            var result = new ArrayList<LegacyClientData.Stack>(slots.length);
            for (Object slot : slots) result.add(slot == null || slot.getClass().getField("field_77994_a").getInt(slot)<=0 ? null : services.items().snapshot(slot));
            return result;
        });
    }
    public void select(Object player, int slot) throws Exception {
        services.items().inLegacy(() -> { currentField.setInt(inventoryField.get(player), slot); return null; });
    }
}
