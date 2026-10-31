package github.com.gengyoubo.mfcl.compat.extrautils;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.HostFlightGrants;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Drives XU's original ring expiry callback; grants still come from its original inventory onUpdate. */
final class ExtraUtilitiesFlight {
    private final HostFlightGrants grants=new HostFlightGrants("mfcl_xu_ring_flight");
    private final LegacyServices services;
    private final Object handler;
    private final java.lang.reflect.Constructor<?> livingEvent;
    private final java.lang.reflect.Method livingTick;
    private final AtomicBoolean reported=new AtomicBoolean();
    private ExtraUtilitiesFlight(LegacyServices services) throws Exception {
        this.services=services;var loader=services.loader();
        var type=loader.loadClass("com.rwtema.extrautils.item.ItemAngelRing$EventHandlerRing");
        handler=type.getConstructor().newInstance();
        var event=loader.loadClass("net.minecraftforge.event.entity.living.LivingEvent$LivingUpdateEvent");
        livingEvent=event.getConstructor(loader.loadClass("net.minecraft.entity.EntityLivingBase"));livingTick=type.getMethod("entTick",event);
    }
    static void install(LegacyServices services) throws Exception {
        if(services.loader().loadClass("com.rwtema.extrautils.ExtraUtils").getField("angelRing").get(null)==null)return;
        var bridge=new ExtraUtilitiesFlight(services);
        MinecraftForge.EVENT_BUS.addListener(bridge::tick);
        MinecraftForge.EVENT_BUS.addListener(bridge::login);
        MinecraftForge.EVENT_BUS.addListener(bridge::logout);
    }
    private ModernContentBridge content(){var content=ModernContentBridge.active();return content!=null&&content.services()==services?content:null;}
    private boolean carriesRing(ModernContentBridge content,ServerPlayer player){
        return player.getInventory().items.stream().anyMatch(stack->content.is(stack,"ExtraUtilities:angelRing"));
    }
    private Object persisted(Object actor) throws Exception {
        var helper=services.loader().loadClass("com.rwtema.extrautils.helper.XUHelper");
        return helper.getMethod("getPersistantNBT",services.loader().loadClass("net.minecraft.entity.Entity")).invoke(null,actor);
    }
    private boolean active(Object data) throws Exception {
        return (boolean)services.loader().loadClass("net.minecraft.nbt.NBTTagCompound").getMethod("func_150297_b",String.class,int.class).invoke(data,"XU|Flying",1);
    }
    private void removePlayer(Object actor) throws Exception {
        var loader=services.loader();
        loader.loadClass("com.rwtema.extrautils.item.ItemAngelRing").getMethod("removePlayer",loader.loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(null,actor);
    }
    private void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||!(event.player instanceof ServerPlayer player))return;
        var content=content();if(content==null)return;
        if(!carriesRing(content,player)&&!grants.owned(player))return;
        try {
            content.withServerActor(player,actor->{
                livingTick.invoke(handler,livingEvent.newInstance(actor));
                if(active(persisted(actor)))grants.claim(player);
                else if(grants.owned(player)){removePlayer(actor);grants.revoke(player);}
            });
        }catch(Exception failure){report(failure);}
    }
    private void login(PlayerEvent.PlayerLoggedInEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;var content=content();if(content==null)return;
        try {
            content.withServerActor(player,actor->{
                var loader=services.loader();
                var notifier=loader.loadClass("com.rwtema.extrautils.network.packets.PacketAngelRingNotifier").getConstructor(String.class,int.class);
                var send=loader.loadClass("com.rwtema.extrautils.network.NetworkHandler").getMethod("sendPacketToPlayer",loader.loadClass("com.rwtema.extrautils.network.XUPacketBase"),loader.loadClass("net.minecraft.entity.player.EntityPlayer"));
                var map=(Map<?,?>)loader.loadClass("com.rwtema.extrautils.item.ItemAngelRing").getField("curFlyingPlayers").get(null);
                for(var entry:new HashMap<>(map).entrySet())send.invoke(null,notifier.newInstance((String)entry.getKey(),((Number)entry.getValue()).intValue()),actor);
                if(grants.owned(player)&&!carriesRing(content,player)){removePlayer(actor);grants.revoke(player);}
            });
        }catch(Exception failure){report(failure);}
    }
    private void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;var content=content();if(content==null)return;
        try {
            content.withServerActor(player,actor->{
                removePlayer(actor);
                Object data=persisted(actor);var remove=services.loader().loadClass("net.minecraft.nbt.NBTTagCompound").getMethod("func_82580_o",String.class);
                remove.invoke(data,"XU|Flying");remove.invoke(data,"XU|FlyingDim");
                if(grants.owned(player))grants.revoke(player);
            });
        }catch(Exception failure){report(failure);}
    }
    private void report(Exception failure){if(reported.compareAndSet(false,true))com.mojang.logging.LogUtils.getLogger().error("MFCL original angel ring lifecycle failed; later failures are not logged",failure);}
}
