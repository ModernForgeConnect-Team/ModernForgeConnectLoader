package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import java.lang.reflect.*;

/** Original TC4 repair and movement routines, without forwarding unrelated warp/flight/living event handlers. */
final class ThaumcraftEnchantments {
    private final LegacyServices services;
    private final Method repair,speed,item,damage;
    private final Field inventory,main,armor,forward,airborne,water;
    private final Class<?> repairable,harness;
    private final Object handler;
    private final java.util.Set<String> failures=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private ThaumcraftEnchantments(LegacyServices services)throws Exception {
        this.services=services;var loader=services.loader();var player=loader.loadClass("net.minecraft.entity.player.EntityPlayer");var stack=loader.loadClass("net.minecraft.item.ItemStack");
        var events=loader.loadClass("thaumcraft.common.lib.events.EventHandlerEntity");handler=events.getConstructor().newInstance();
        repair=events.getMethod("doRepair",stack,player);speed=events.getDeclaredMethod("updateSpeed",player);speed.setAccessible(true);
        item=stack.getMethod("func_77973_b");damage=stack.getMethod("func_77960_j");inventory=player.getField("field_71071_by");
        main=loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getField("field_70462_a");armor=loader.loadClass("net.minecraft.entity.player.InventoryPlayer").getField("field_70460_b");
        repairable=loader.loadClass("thaumcraft.api.IRepairable");harness=loader.loadClass("thaumcraft.common.items.armor.ItemHoverHarness");
        forward=loader.loadClass("net.minecraft.entity.EntityLivingBase").getField("field_70701_bs");
        var entity=loader.loadClass("net.minecraft.entity.Entity");airborne=entity.getField("field_70160_al");water=entity.getDeclaredField("field_70171_ac");water.setAccessible(true);
    }
    static void install(LegacyServices services)throws Exception {
        var bridge=new ThaumcraftEnchantments(services);
        // Forge transforms modern event parents through the context loader; adapter initialization uses the old loader.
        var thread=Thread.currentThread();var previous=thread.getContextClassLoader();
        try{thread.setContextClassLoader(ThaumcraftEnchantments.class.getClassLoader());net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(bridge::tick);}
        finally{thread.setContextClassLoader(previous);}
    }
    private void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;var player=event.player;var content=ModernContentBridge.active();if(content==null||content.services()!=services||!content.registered())return;
        // An addon forwarding the original living event already runs both original routines.
        if(content.players().forwarding(github.com.gengyoubo.mfcl.api.LegacyEvents.Kind.LIVING_UPDATE))return;
        try {
            if(player.tickCount%40==0)for(int slot=0;slot<player.getInventory().getContainerSize();slot++)content.enchantments().migrate(player.getInventory().getItem(slot));
            if(player instanceof ServerPlayer&&!player.getAbilities().instabuild&&player.tickCount%40==0&&needsRepair(content,player))
                content.players().callback(player,actor->{
                    Object inv=inventory.get(actor);
                    for(Object stack:(Object[])main.get(inv))repair(stack,actor,false);
                    for(Object stack:(Object[])armor.get(inv))repair(stack,actor,true);
                });
            if(player.level().isClientSide&&player.isLocalPlayer()&&player.zza>0&&!player.getAbilities().flying&&level(content,player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),"thaumcraft:haste")>0)
                content.players().callback(player,actor->{
                    forward.setFloat(actor,player.zza);airborne.setBoolean(actor,!player.onGround());water.setBoolean(actor,player.isInWater());speed.invoke(handler,actor);
                });
        }catch(Exception failure){if(failures.add(player.level().isClientSide?"client":"server"))com.mojang.logging.LogUtils.getLogger().error("MFCL TC4 enchantment effect failed",failure);}
    }
    private int level(ModernContentBridge content,ItemStack stack,String key){var enchantment=content.enchantments().entries().get(key);return enchantment==null||stack.isEmpty()?0:stack.getEnchantmentLevel(enchantment);}
    private boolean needsRepair(ModernContentBridge content,Player player){
        for(var stack:player.getInventory().items)if(stack.isDamaged()&&level(content,stack,"thaumcraft:repair")>0)return true;
        for(var stack:player.getArmorSlots())if(stack.isDamaged()&&level(content,stack,"thaumcraft:repair")>0)return true;return false;
    }
    private void repair(Object stack,Object actor,boolean worn)throws Exception {
        if(stack==null||(int)damage.invoke(stack)<=0)return;Object value=item.invoke(stack);
        if(repairable.isInstance(value)&&(worn||!harness.isInstance(value)))repair.invoke(null,stack,actor);
    }
}
