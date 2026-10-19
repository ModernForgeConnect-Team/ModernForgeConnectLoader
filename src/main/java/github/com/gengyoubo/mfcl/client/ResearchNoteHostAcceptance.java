package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.concurrent.*;

/** Disposable-world acceptance through the real native use-item packet, with original TC4 data. */
final class ResearchNoteHostAcceptance {
    private final ModernContentBridge content;
    private final Map<String,Object> proof;
    private CompletableFuture<?> pending;
    private int phase,frames;
    private String target;
    ResearchNoteHostAcceptance(ModernContentBridge content,Map<String,Object> proof){this.content=content;this.proof=proof;}
    boolean tick(Minecraft mc) throws Exception {
        if(pending!=null&&pending.isCompletedExceptionally())pending.join();
        if(phase==0){pending=server(mc,()->{seed(mc);return true;});phase=1;frames=0;}
        else if(phase==1&&pending.isDone()&&++frames>12&&mc.screen==null){pending.join();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=2;frames=0;}
        else if(phase==2&&++frames>12){pending=server(mc,()->{verifyRevealed(mc);return true;});phase=3;frames=0;}
        else if(phase==3&&pending.isDone()&&++frames>12){pending.join();mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);phase=4;frames=0;}
        else if(phase==4&&++frames>12){pending=server(mc,()->{verifyLearned(mc);return true;});phase=5;frames=0;}
        else if(phase==5&&pending.isDone()&&++frames>12){pending.join();mc.gameMode.useItem(mc.player,InteractionHand.OFF_HAND);phase=6;frames=0;}
        else if(phase==6&&++frames>12){pending=server(mc,()->{verifyRefund(mc);return true;});phase=7;}
        else if(phase==7&&pending.isDone()){pending.join();return true;}
        return false;
    }
    private ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    private LegacyTiles.Placer source(ServerPlayer player){return new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0);}
    private Class<?> manager() throws Exception{return content.services().loader().loadClass("thaumcraft.common.lib.research.ResearchManager");}
    private Object knowledge() throws Exception {var loader=content.services().loader();return loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null));}
    @SuppressWarnings("unchecked") private void seed(Minecraft mc) throws Exception {
        var player=player(mc);var services=content.services();Object world=services.worlds().bound(player.serverLevel());
        player.closeContainer();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.getInventory().clearContent();player.getInventory().selected=0;
        services.items().inLegacy(()->{try(var scope=services.playerState().enter(world)) {
            var categories=(Map<?,?>)services.loader().loadClass("thaumcraft.api.research.ResearchCategories").getField("researchCategories").get(null);
            var completed=new ArrayList<String>();
            for(Object category:categories.values())for(Object research:((Map<?,?>)category.getClass().getField("research").get(category)).values()) {
                String key=(String)research.getClass().getField("key").get(research);completed.add(key);
                Object tags=research.getClass().getField("tags").get(research);
                if(target==null&&(boolean)research.getClass().getMethod("isHidden").invoke(research)&&tags!=null&&(int)tags.getClass().getMethod("size").invoke(tags)>0
                        &&(research.getClass().getMethod("getItemTriggers").invoke(research)!=null||research.getClass().getMethod("getEntityTriggers").invoke(research)!=null||research.getClass().getMethod("getAspectTriggers").invoke(research)!=null))target=key;
            }
            if(target==null)throw new IllegalStateException("No original hidden research fixture");completed.remove(target);
            ((Map<String,ArrayList<String>>)knowledge().getClass().getField("researchCompleted").get(knowledge())).put(player.getName().getString(),completed);
            Object actor=content.itemUse().clientActor(world,source(player),null);
            player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));
            return null;
        }});
        content.itemUse().sync(world,source(player)).forEach(message->ScanSyncChannel.sendToPlayer(player,message));
        player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemResearchNotes",24,1,""));player.inventoryMenu.broadcastChanges();
    }
    private void verifyRevealed(Minecraft mc) throws Exception {
        var player=player(mc);var services=content.services();
        services.items().inLegacy(()->{
            Object stack=services.items().materialize(content.gameplayStack(player.getMainHandItem()));
            Object data=manager().getMethod("getData",services.loader().loadClass("net.minecraft.item.ItemStack")).invoke(null,stack);
            if(data==null||!target.equals(data.getClass().getField("key").get(data))||(boolean)data.getClass().getMethod("isComplete").invoke(data)||content.metadata(player.getMainHandItem())!=0)throw new IllegalStateException("Native use did not reveal original research NBT: "+player.getMainHandItem());
            proof.put("researchNoteNativeRevealAndNbt",target);
            Object tag=stack.getClass().getField("field_77990_d").get(stack);tag.getClass().getMethod("func_74757_a",String.class,boolean.class).invoke(tag,"complete",true);
            stack.getClass().getMethod("func_77964_b",int.class).invoke(stack,64);
            player.setItemInHand(InteractionHand.MAIN_HAND,content.gameplayStack(services.items().snapshot(stack)));return null;
        });player.inventoryMenu.broadcastChanges();
    }
    private void verifyLearned(Minecraft mc) throws Exception {
        var player=player(mc);var services=content.services();Object world=services.worlds().bound(player.serverLevel());
        if(!player.getMainHandItem().isEmpty())throw new IllegalStateException("Learned note was not consumed by native use");
        services.items().inLegacy(()->{try(var scope=services.playerState().enter(world)) {
            if(!(boolean)manager().getMethod("isResearchComplete",String.class,String.class).invoke(null,player.getName().getString(),target))throw new IllegalStateException("Original server research was not learned");
            // Reload persistence to prove knowledge survives the next actor refresh.
            content.itemUse().loadPersisted(world,source(player),player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA));
            if(!(boolean)manager().getMethod("isResearchComplete",String.class,String.class).invoke(null,player.getName().getString(),target))throw new IllegalStateException("Learned research was not persisted");
            return null;
        }});
        proof.put("researchNoteNativeLearningConsumptionAndPersistence",true);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,3));player.setItemInHand(InteractionHand.OFF_HAND,content.stack("Thaumcraft:ItemResearchNotes",42,1,""));player.inventoryMenu.broadcastChanges();
    }
    private void verifyRefund(Minecraft mc) throws Exception {
        var player=player(mc);if(!player.getOffhandItem().isEmpty()||!player.getMainHandItem().is(Items.DIAMOND)||player.getMainHandItem().getCount()!=3)throw new IllegalStateException("Offhand note use changed the wrong hand");
        int fragments=0;for(var stack:player.getInventory().items)if(fragment(stack))fragments+=stack.getCount();
        for(var entity:player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(8)))if(fragment(entity.getItem()))fragments+=entity.getItem().getCount();
        if(fragments<7||fragments>9)throw new IllegalStateException("Original note refund did not produce 7-9 native fragments: "+fragments);
        proof.put("researchNoteOffhandRefundAndNativeDrop",fragments);
    }
    private boolean fragment(ItemStack stack) throws Exception {return !stack.isEmpty()&&"Thaumcraft:ItemResource".equals(content.gameplayStack(stack).key())&&content.metadata(stack)==9;}
    private <T> CompletableFuture<T> server(Minecraft mc,Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
}
