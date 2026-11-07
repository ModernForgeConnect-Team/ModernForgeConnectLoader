package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import java.util.Map;

/** Repairs stale split-item aliases before Forge constructs the staging registry for a local save. */
public final class SplitRegistryAliases {
    private SplitRegistryAliases() {}

    public static void beforeSnapshot(Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot, boolean injectFrozenData, boolean localWorld) {
        if (!injectFrozenData || !localWorld) return;
        var bridge = ModernContentBridge.active();
        if (bridge == null) return;
        var items = snapshot.get(Registries.ITEM.location());
        var frozen = RegistryManager.FROZEN.getRegistry(Registries.ITEM);
        if (items == null || frozen == null) return;
        int removed = 0;
        for (var entry : bridge.splitVariants().entrySet()) {
            var id = entry.getKey(); var item = entry.getValue();
            // Do not touch aliases for withdrawn variants or entries belonging to another mod.
            if (items.aliases.containsKey(id) && id.equals(frozen.getKey(item)) && frozen.getValue(id) == item) {
                items.aliases.remove(id);
                removed++;
            }
        }
        if (removed != 0) LogUtils.getLogger().warn("MFCL removed {} stale split-item aliases from the loaded registry snapshot", removed);
    }
}
