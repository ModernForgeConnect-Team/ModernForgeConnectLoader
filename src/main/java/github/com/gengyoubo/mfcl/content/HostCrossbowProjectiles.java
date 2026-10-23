package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/** Native loading/aim/multishot with an adapter-selected original projectile and impact logic. */
final class HostCrossbowProjectiles {
    private record Loaded(LegacyClientData.Stack stack,LegacyCrossbowAmmo.Factory factory) {}
    private HostCrossbowProjectiles() {}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener((EntityJoinLevelEvent event)->join(content,event));}
    private static Loaded loaded(ModernContentBridge content,ItemStack crossbow) {
        if(!(crossbow.getItem() instanceof CrossbowItem)||crossbow.getTag()==null)return null;
        var projectiles=crossbow.getTag().getList("ChargedProjectiles",net.minecraft.nbt.Tag.TAG_COMPOUND);
        for(int index=0;index<projectiles.size();index++) {
            var stack=content.toLegacy(ItemStack.of(projectiles.getCompound(index)));if(stack==null)continue;
            var factory=content.services().crossbowAmmo().factory(stack.key());
            if(factory.isPresent())return new Loaded(stack,factory.get());
        }
        return null;
    }
    private static void join(ModernContentBridge content,EntityJoinLevelEvent event) {
        if(ModernContentBridge.active()!=content||!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof AbstractArrow arrow)
                ||!arrow.shotFromCrossbow()||!(arrow.getOwner() instanceof ServerPlayer player))return;
        var selected=loaded(content,player.getMainHandItem());if(selected==null)selected=loaded(content,player.getOffhandItem());if(selected==null)return;
        var ammunition=selected;var services=content.services();
        try {
            Object world=services.worlds().bound(level),actor=content.itemUse().clientActor(world,ModernContentBridge.placer(player),null);
            Object original=services.items().inLegacy(()->{
                Object value=ammunition.factory().create(world,actor,ammunition.stack());
                var loader=services.loader();var originalArrow=loader.loadClass("net.minecraft.entity.projectile.EntityArrow");
                if(!originalArrow.isInstance(value))throw new IllegalArgumentException("Crossbow factory did not create an original arrow: "+ammunition.stack().key());
                var entity=loader.loadClass("net.minecraft.entity.Entity");var motion=arrow.getDeltaMovement();
                entity.getMethod("func_70107_b",double.class,double.class,double.class).invoke(value,arrow.getX(),arrow.getY(),arrow.getZ());
                entity.getField("field_70159_w").setDouble(value,motion.x);entity.getField("field_70181_x").setDouble(value,motion.y);entity.getField("field_70179_y").setDouble(value,motion.z);
                entity.getField("field_70177_z").setFloat(value,arrow.getYRot());entity.getField("field_70125_A").setFloat(value,arrow.getXRot());
                originalArrow.getMethod("func_70243_d",boolean.class).invoke(value,arrow.isCritArrow());return value;
            });
            if(LegacyWorlds.spawn(world,original))event.setCanceled(true);
        } catch(Exception failure){LogUtils.getLogger().error("MFCL cannot fire original crossbow ammunition {}",ammunition.stack().key(),failure);}
    }
}
