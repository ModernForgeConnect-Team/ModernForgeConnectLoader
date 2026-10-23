package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyDroppedItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.item.ItemExpireEvent;

/** Native item entities drive original lifetimes and expiration events, including world-changing subscribers. */
final class HostDroppedItems {
    private final ModernContentBridge content;
    private final LegacyDroppedItems legacy;
    HostDroppedItems(ModernContentBridge content) {
        this.content = content; this.legacy = new LegacyDroppedItems(content.services());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(this::expire);
    }
    int lifespan(ItemStack stack, Level level) {
        try { return legacy.lifespan(content.services().worlds().bound(level), content.renderSnapshot(stack)); }
        catch (Exception failure) { throw new IllegalStateException("Cannot evaluate original dropped-item lifespan", failure); }
    }
    private void expire(ItemExpireEvent event) {
        var drop = event.getEntity(); var level = drop.level();
        if (level.isClientSide || drop.isRemoved()) return;
        try {
            var snapshot = HostEntityQueries.snapshot(level, drop);
            if (snapshot == null) return;
            var result = content.logicTransaction(level, drop.blockPosition(), () -> legacy.expire(
                    content.services().worlds().bound(level), snapshot, drop.getAge(), drop.lifespan, event.getExtraLife()), true);
            if (result.canceled()) { event.setCanceled(true); event.setExtraLife(result.extraLife()); }
            if (result.removed()) drop.discard();
            else if (!ModernContentBridge.sameLegacy(snapshot.item(), result.stack())) drop.setItem(content.applyTo(drop.getItem(), result.stack()));
        } catch (Exception | LinkageError failure) {
            github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.legacyFailure("item expiration", failure);
            com.mojang.logging.LogUtils.getLogger().error("MFCL original item expiration failed for {}", drop.getItem(), failure);
        }
    }
}
