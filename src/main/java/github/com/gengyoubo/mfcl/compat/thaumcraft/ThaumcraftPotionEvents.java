package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;

/** TC4 potion behavior that lives in event handlers rather than Potion.performEffect. */
final class ThaumcraftPotionEvents {
    static void install(LegacyServices services)throws Exception {
        var loader=services.loader();var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");var stack=loader.loadClass("net.minecraft.item.ItemStack");
        var gaze=loader.loadClass("thaumcraft.common.lib.WarpEvents").getMethod("checkDeathGaze",playerType);
        var warp=loader.loadClass("thaumcraft.common.lib.WarpEvents").getMethod("checkWarpEvent",playerType);
        var wuss=loader.loadClass("thaumcraft.common.config.Config").getField("wuss");
        var ticks=loader.loadClass("net.minecraft.entity.Entity").getField("field_70173_aa");
        var handlerType=loader.loadClass("thaumcraft.common.lib.events.EventHandlerEntity");Object handler=handlerType.getConstructor().newInstance();
        var finishType=loader.loadClass("net.minecraftforge.event.entity.player.PlayerUseItemEvent$Finish");
        var finish=handlerType.getMethod("finishedUsingItem",finishType);
        LegacyModAdapter.withHostContext(()->{
        MinecraftForge.EVENT_BUS.addListener((TickEvent.PlayerTickEvent event)->{
            if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player)||player.tickCount<=0||player.tickCount%10!=0)return;
            var content=ModernContentBridge.active();if(content==null||content.services()!=services||!content.registered())return;
            if(content.players().forwarding(github.com.gengyoubo.mfcl.api.LegacyEvents.Kind.LIVING_UPDATE))return;
            boolean gazeDue=player.hasEffect(content.potions().effect("thaumcraft:deathgaze"));
            boolean warpDue=player.tickCount%2000==0&&!player.hasEffect(content.potions().effect("thaumcraft:warpward"));
            if(!gazeDue&&!warpDue)return;
            try{content.players().callback(player,actor->{
                ticks.setInt(actor,player.tickCount);
                if(warpDue&&!wuss.getBoolean(null)){
                    warp.invoke(null,actor);
                    player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));
                }
                if(gazeDue)gaze.invoke(null,actor);
            });}
            catch(Exception failure){throw new IllegalStateException("Original TC4 potion/warp tick failed",failure);}
        });
        MinecraftForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Finish event)->{
            if(!(event.getEntity() instanceof ServerPlayer player))return;var content=ModernContentBridge.active();
            if(content==null||!player.hasEffect(content.potions().effect("thaumcraft:unhunger")))return;
            try{
                var used=content.gameplayStack(event.getItem());
                content.players().callback(player,actor->{
                    Object item=services.items().materialize(used);
                    finish.invoke(handler,finishType.getConstructor(playerType,stack,int.class,stack).newInstance(actor,item,event.getDuration(),item));
                });
            }catch(Exception failure){throw new IllegalStateException("Original unnatural hunger cure failed",failure);}
        });
        });
    }
}
