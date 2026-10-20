package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/**
 * TC4 primal arrows on the host crossbow, which 1.7.10 never had. They load as ordinary #minecraft:arrows ammo;
 * firing replaces the host arrow the crossbow would create with the original EntityPrimalArrow, so its
 * element (armor piercing, fire, slowness, weakness, wither) and renderer come from TC4 itself.
 */
final class ThaumcraftCrossbow {
    private static final String ARROW = "Thaumcraft:PrimalArrow";
    private ThaumcraftCrossbow() {}
    static void install(LegacyServices services) {
        // Crossbows only load #minecraft:arrows (Forge cannot subscribe to LivingGetProjectileEvent in 1.20.1).
        // The vanilla bow stays with TC4's own ArrowNock/ArrowLoose handlers, which take primal arrows first.
        services.tagItem("minecraft:arrows", ARROW);
        MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> join(services, event));
    }
    private static boolean primal(ItemStack stack) {
        var content = ModernContentBridge.active();
        return content != null && !stack.isEmpty() && stack.getItem() == content.items().get(ARROW);
    }
    private static void join(LegacyServices services, EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof AbstractArrow arrow) || !arrow.shotFromCrossbow()
                || !(arrow.getOwner() instanceof ServerPlayer player)) return;
        var loaded = loadedPrimal(player.getMainHandItem());
        if (loaded == null) loaded = loadedPrimal(player.getOffhandItem());
        if (loaded == null) return;
        var content = ModernContentBridge.active();
        try {
            var stack = content.gameplayStack(loaded);
            Object world = services.worlds().bound(level);
            Object actor = content.itemUse().clientActor(world, new LegacyTiles.Placer(player.getUUID(), player.getName().getString(), player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(), player.getAbilities().instabuild, player.isShiftKeyDown()), null);
            var motion = arrow.getDeltaMovement(); boolean critical = arrow.isCritArrow();
            Object original = services.items().inLegacy(() -> {
                var loader = services.loader(); var entity = loader.loadClass("net.minecraft.entity.Entity");
                Object value = loader.loadClass("thaumcraft.common.entities.projectile.EntityPrimalArrow")
                        .getConstructor(loader.loadClass("net.minecraft.world.World"), loader.loadClass("net.minecraft.entity.EntityLivingBase"), float.class, int.class)
                        .newInstance(world, actor, 3.0f, stack.metadata());
                // The crossbow already aimed (and spread multishot); keep its trajectory instead of the shooter's look.
                entity.getMethod("func_70107_b", double.class, double.class, double.class).invoke(value, arrow.getX(), arrow.getY(), arrow.getZ());
                entity.getField("field_70159_w").setDouble(value, motion.x); entity.getField("field_70181_x").setDouble(value, motion.y); entity.getField("field_70179_y").setDouble(value, motion.z);
                entity.getField("field_70177_z").setFloat(value, arrow.getYRot()); entity.getField("field_70125_A").setFloat(value, arrow.getXRot());
                loader.loadClass("net.minecraft.entity.projectile.EntityArrow").getMethod("func_70243_d", boolean.class).invoke(value, critical);
                return value;
            });
            if (LegacyWorlds.spawn(world, original)) event.setCanceled(true);
        } catch (Exception failure) { LogUtils.getLogger().error("MFCL cannot fire an original primal arrow from a crossbow", failure); }
    }
    /** The primal arrow a charged crossbow is about to fire; its charged projectiles are cleared only after shooting. */
    private static ItemStack loadedPrimal(ItemStack crossbow) {
        if (!(crossbow.getItem() instanceof CrossbowItem) || crossbow.getTag() == null) return null;
        var charged = crossbow.getTag().getList("ChargedProjectiles", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < charged.size(); index++) { var stack = ItemStack.of(charged.getCompound(index)); if (primal(stack)) return stack; }
        return null;
    }
}
