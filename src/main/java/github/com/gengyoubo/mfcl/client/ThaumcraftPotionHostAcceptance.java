package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** TC4-only spell costs, warp scheduling/ward, bath and soap checks using native entry points. */
final class ThaumcraftPotionHostAcceptance {
    private final ModernContentBridge content;
    private final LegacyServices services;
    private final ClassLoader loader;
    private final Map<String,Object> proof;
    private record Cost(float modifier,int spent){}
    @FunctionalInterface private interface Action{void run(Object actor)throws Exception;}
    ThaumcraftPotionHostAcceptance(ModernContentBridge content,Map<String,Object> proof){this.content=content;services=content.services();loader=services.loader();this.proof=proof;}
    private void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private MobEffect effect(String name){return Objects.requireNonNull(content.potions().effect("thaumcraft:"+name));}
    private Object knowledge()throws Exception{Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);return proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy);}
    private void session(ServerPlayer player,Action action)throws Exception{
        content.players().callback(player,actor->{action.run(actor);player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));});
    }
    private void warp(ServerPlayer player,int permanent,int sticky,int temporary,int counter)throws Exception{
        session(player,actor->{Object known=knowledge();String name=LegacyObjects.playerName(actor);int index=0;
            for(String field:List.of("WarpPerm","WarpSticky","WarpTemp","WarpCounter"))known.getClass().getMethod("set"+field,String.class,int.class).invoke(known,name,new int[]{permanent,sticky,temporary,counter}[index++]);
        });
    }
    private List<Integer> warp(ServerPlayer player)throws Exception{
        var values=new ArrayList<Integer>();session(player,actor->{Object known=knowledge();String name=LegacyObjects.playerName(actor);
            for(String field:List.of("WarpPerm","WarpSticky","WarpTemp","WarpCounter"))values.add((int)known.getClass().getMethod("get"+field,String.class).invoke(known,name));
        });return values;
    }
    private void seed(ServerPlayer player,long seed)throws Exception{
        session(player,actor->{Object world=services.worlds().bound(player.level());((java.util.Random)loader.loadClass("net.minecraft.world.World").getField("field_73012_v").get(world)).setSeed(seed);});
    }
    private void tick(ServerPlayer player,int ticks)throws Exception{
        int previous=player.tickCount;try{player.tickCount=ticks;MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));}finally{player.tickCount=previous;}
    }
    private Cost cost(ServerPlayer player)throws Exception{
        player.getInventory().selected=0;player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,content.stack("Thaumcraft:WandCasting",0,1,"{rod:\"wood\",cap:\"iron\",aer:10000}"));
        var result=new Cost[1];session(player,actor->{
            Object held=LegacyObjects.heldStack(actor),item=LegacyObjects.item(held);var stack=loader.loadClass("net.minecraft.item.ItemStack");var oldPlayer=loader.loadClass("net.minecraft.entity.player.EntityPlayer");var aspect=loader.loadClass("thaumcraft.api.aspects.Aspect");Object air=aspect.getMethod("getAspect",String.class).invoke(null,"aer");
            float modifier=(float)item.getClass().getMethod("getConsumptionModifier",stack,oldPlayer,aspect,boolean.class).invoke(item,held,actor,air,false);
            check((boolean)item.getClass().getMethod("consumeVis",stack,oldPlayer,aspect,int.class,boolean.class).invoke(item,held,actor,air,1000,false),"Original wand refused a charged spell-cost fixture");
            int left=(int)item.getClass().getMethod("getVis",stack,aspect).invoke(item,held,air);result[0]=new Cost(modifier,10000-left);
        });
        check(result[0].spent()==(int)(1000*result[0].modifier()),"Original spell cost and consumed vis disagree");
        check(content.gameplayStack(player.getMainHandItem()).tag().contains("aer:"+(10000-result[0].spent())),"Original spell cost was not written to the native held wand");return result[0];
    }
    void run(ServerPlayer player)throws Exception{
        player.removeAllEffects();player.getInventory().clearContent();Cost normal=cost(player);
        player.addEffect(new MobEffectInstance(effect("visexhaust"),600,1));Cost exhausted=cost(player);
        player.removeEffect(effect("visexhaust"));player.addEffect(new MobEffectInstance(effect("infvisexhaust"),600,3));Cost infectious=cost(player);
        player.addEffect(new MobEffectInstance(effect("visexhaust"),600,1));Cost combined=cost(player);
        check(Math.abs(exhausted.modifier()-normal.modifier()-.2f)<.0001f,"Vis Exhaust did not add the original 20% cost");
        check(Math.abs(infectious.modifier()-normal.modifier()-.4f)<.0001f&&combined.equals(infectious),"Exhaustion did not use the stronger original potion instead of adding both penalties");
        proof.put("tc4NativeWandSpellCosts",Map.of("normal",normal,"visExhaustII",exhausted,"infectiousIV",infectious,"both",combined));
        player.removeAllEffects();var wuss=loader.loadClass("thaumcraft.common.config.Config").getField("wuss");boolean previous=wuss.getBoolean(null);
        try{
            wuss.setBoolean(null,false);warp(player,30,30,10,10000);long seed=0;
            for(;;seed++){var random=new java.util.Random(seed);random.nextInt(100);if(random.nextInt(100)==13)break;}
            seed(player,seed);tick(player,1990);check(warp(player).equals(List.of(30,30,10,10000)),"Warp scheduler ran before the original 2000-tick interval");
            player.addEffect(new MobEffectInstance(effect("warpward"),600,0));seed(player,seed);tick(player,2000);
            check(warp(player).equals(List.of(30,30,10,10000))&&!player.hasEffect(effect("visexhaust")),"Warp Ward failed to suppress a due warp event");
            player.removeEffect(effect("warpward"));wuss.setBoolean(null,true);seed(player,seed);tick(player,2000);
            check(warp(player).equals(List.of(30,30,10,10000)),"TC4 wuss option did not suppress a due warp event");
            wuss.setBoolean(null,false);seed(player,seed);tick(player,2000);
            check(warp(player).equals(List.of(30,30,9,9800)),"Original warp counter/temporary warp decay did not commit");
            var applied=player.getEffect(effect("visexhaust"));check(applied!=null&&applied.getAmplifier()==3&&applied.getDuration()==5000&&applied.getCurativeItems().isEmpty(),"Scheduled original warp event did not apply the expected native potion");
            proof.put("tc4WarpCadenceWardWussAndOriginalEvent",true);
        }finally{wuss.setBoolean(null,previous);}
        player.removeAllEffects();warp(player,10000,7,9,1234);var level=player.serverLevel();var pos=player.blockPosition();
        var bath=content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockFluidPure",0));level.setBlockAndUpdate(pos,bath);bath.entityInside(level,pos,player);
        check(player.hasEffect(effect("warpward"))&&player.getEffect(effect("warpward")).getDuration()==2000&&level.getBlockState(pos).isAir(),"Purifying bath used the wrong server warp state or failed to consume its source");
        level.setBlockAndUpdate(pos,bath);bath.entityInside(level,pos,player);check(level.getBlockState(pos).getBlock()==bath.getBlock(),"Already-warded player consumed another bath source");
        player.removeEffect(effect("warpward"));var flowing=content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockFluidPure",1));level.setBlockAndUpdate(pos,flowing);flowing.entityInside(level,pos,player);
        check(!player.hasEffect(effect("warpward")),"Flowing purifying fluid granted a source-only ward");level.removeBlock(pos,false);proof.put("tc4PurifyingBathUsesServerWarpAndSourceRules",true);
        player.getInventory().clearContent();player.getInventory().selected=0;var soap=content.stack("Thaumcraft:ItemSanitySoap",0,3,"");player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,soap);
        soap.getItem().releaseUsing(soap,level,player,199);check(player.getMainHandItem().getCount()==3&&warp(player).subList(0,3).equals(List.of(10000,7,9)),"Short soap use changed warp or consumed an item");
        long soapSeed=0;while(new java.util.Random(soapSeed).nextFloat()<.33f||new java.util.Random(soapSeed).nextFloat()>=.58f)soapSeed++;
        seed(player,soapSeed);soap.getItem().releaseUsing(soap,level,player,1);
        check(player.getMainHandItem().getCount()==2&&warp(player).subList(0,3).equals(List.of(10000,7,0)),"Unwarded soap did not clear temporary warp/consume exactly once");
        warp(player,10000,7,9,1234);player.addEffect(new MobEffectInstance(effect("warpward"),600,0));seed(player,soapSeed);soap=player.getMainHandItem();soap.getItem().releaseUsing(soap,level,player,1);
        check(player.getMainHandItem().getCount()==1&&warp(player).subList(0,3).equals(List.of(10000,6,0)),"Warp Ward did not improve the original soap chance or permanent warp changed");proof.put("tc4SoapDurationConsumptionTemporaryStickyAndWardBonus",true);
        player.removeAllEffects();player.getInventory().clearContent();warp(player,12,7,9,1234);
        session(player,actor->{services.playerState().synchronize(actor);
            Object wrapper=loader.loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null),packet=loader.loadClass("thaumcraft.common.lib.network.misc.PacketMiscEvent").getConstructor(short.class).newInstance((short)1);
            wrapper.getClass().getMethod("sendTo",loader.loadClass("cpw.mods.fml.common.network.simpleimpl.IMessage"),loader.loadClass("net.minecraft.entity.player.EntityPlayerMP")).invoke(wrapper,packet,actor);
        });
    }
    void verifyReload(ServerPlayer player)throws Exception{check(warp(player).equals(List.of(12,7,9,1234)),"Original permanent/sticky/temporary warp or counter did not survive world reload");proof.put("tc4AllWarpValuesPersistAcrossWorldReload",true);}
    void verifyClient(){
        content.withApiClientPlayer(actor->{try{
            Object known=knowledge();String name=LegacyObjects.playerName(actor);
            check((int)known.getClass().getMethod("getWarpPerm",String.class).invoke(known,name)==12&&(int)known.getClass().getMethod("getWarpSticky",String.class).invoke(known,name)==7&&(int)known.getClass().getMethod("getWarpTemp",String.class).invoke(known,name)==9,"Original warp packets did not synchronize client knowledge");return null;
        }catch(Exception failure){throw new IllegalStateException(failure);}});
        check(github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftWarpVisuals.vignetteActive()&&github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftWarpVisuals.fogDensity()>0,"Warp packet vignette/fog state was not advanced by the native client");
        proof.put("tc4OriginalWarpPacketsClientKnowledgeFogAndVignette",true);
    }
}
