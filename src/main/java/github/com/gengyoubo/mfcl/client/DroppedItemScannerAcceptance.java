package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual held-use packets against native drops, including a target replacement during scanning. */
final class DroppedItemScannerAcceptance {
    private final ModernContentBridge content;
    private final Map<String,Object> proof;
    private CompletableFuture<?> pending;
    private int phase,frames,before,awarded,dropId,replacedId;
    private UUID dropUuid;
    DroppedItemScannerAcceptance(ModernContentBridge content,Map<String,Object> proof){this.content=content;this.proof=proof;}
    boolean tick(Minecraft mc) throws Exception {
        if(pending!=null&&pending.isCompletedExceptionally())pending.join();
        if(phase==0) {
            pending=server(mc,()->{before=pool(mc,true);spawn(mc,new ItemStack(Items.RED_SAND,3));return true;});phase=1;frames=0;
        }else if(phase==1&&pending.isDone()&&++frames>12&&clientDrop(mc)!=null) {
            pending.join();verifyMirror(mc,"minecraft:sand",1,3);hold(mc);phase=2;frames=0;
        }else if(phase==2&&++frames>=35) {
            release(mc);pending=server(mc,()->{
                awarded=pool(mc,true);
                if(awarded<=before||!scanned(mc,true,"minecraft:sand",1))throw new IllegalStateException("Dropped red sand scan did not award/persist its real aspects");
                if(!drop(mc).getItem().is(Items.RED_SAND)||drop(mc).getItem().getCount()!=3)throw new IllegalStateException("Scanning consumed or changed the native drop");
                return true;
            });phase=3;frames=0;
        }else if(phase==3&&pending.isDone()&&++frames>12) {
            pending.join();if(pool(mc,false)!=awarded||!scanned(mc,false,"minecraft:sand",1))throw new IllegalStateException("Dropped item scan did not synchronize to the client");
            var drawing=content.services().dynamicItems().capture(content.renderSnapshot(mc.player.getMainHandItem()),"EQUIPPED_FIRST_PERSON").orElseThrow();
            if(drawing.primitives().stream().noneMatch(p->p.texture().contains("textures/aspects/terra.png")))throw new IllegalStateException("Live lens lost the dropped item's aspect overlay");
            proof.put("droppedItemNativeScanMetadataRewardAndLens",true);hold(mc);phase=4;frames=0;
        }else if(phase==4&&++frames>=35) {
            release(mc);pending=server(mc,()->{if(pool(mc,true)!=awarded)throw new IllegalStateException("Repeated drop scan awarded points twice");remove(mc);spawn(mc,new ItemStack(Items.DIRT,3));return true;});phase=5;frames=0;
        }else if(phase==5&&pending.isDone()&&++frames>12&&clientDrop(mc)!=null) {
            pending.join();verifyMirror(mc,"minecraft:dirt",0,3);hold(mc);phase=6;frames=0;
        }else if(phase==6&&++frames>=5) {
            pending=server(mc,()->{replacedId=dropId;remove(mc);spawn(mc,new ItemStack(Items.DIRT,3));return true;});phase=7;frames=0;
        }else if(phase==7&&pending.isDone()&&++frames>=10) {
            pending.join();release(mc);pending=server(mc,()->{
                if(scanned(mc,true,"minecraft:dirt",0)||pool(mc,true)!=awarded)throw new IllegalStateException("Replacing the target entity did not cancel scanning");
                var player=player(mc);var s=content.services();
                var result=content.itemUse().applyScan(s.worlds().bound(player.serverLevel()),source(player),(byte)2,0,0,replacedId,"","@",player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA));
                if(result.accepted()||pool(mc,true)!=awarded)throw new IllegalStateException("Server accepted a stale entity ID for another drop");
                return true;
            });phase=8;frames=0;
        }else if(phase==8&&pending.isDone()&&++frames>12&&clientDrop(mc)!=null) {
            pending.join();hold(mc);phase=9;frames=0;
        }else if(phase==9&&++frames>=35) {
            release(mc);pending=server(mc,()->{
                if(!scanned(mc,true,"minecraft:dirt",0)||pool(mc,true)<=awarded)throw new IllegalStateException("Fresh scan of the replacement drop failed");
                awarded=pool(mc,true);remove(mc);spawn(mc,new ItemStack(Items.NETHERITE_INGOT,3));return true;
            });phase=10;frames=0;
        }else if(phase==10&&pending.isDone()&&++frames>12&&clientDrop(mc)!=null) {
            pending.join();if(scanClient(mc)!=null)throw new IllegalStateException("Unmapped modern drop became a supported legacy scan target");hold(mc);phase=11;frames=0;
        }else if(phase==11&&++frames>=35) {
            release(mc);pending=server(mc,()->{
                if(pool(mc,true)!=awarded||!drop(mc).getItem().is(Items.NETHERITE_INGOT)||drop(mc).getItem().getCount()!=3)throw new IllegalStateException("Unmapped drop invented rewards or changed its native stack");
                remove(mc);return true;
            });phase=12;
        }else if(phase==12&&pending.isDone()) {
            pending.join();proof.put("dropRepeatNoDuplicateReward",true);proof.put("dropTargetReplacementAndStaleEntityIdRejected",true);proof.put("unmappedDropDoesNotBecomeStone",true);return true;
        }
        return false;
    }
    private ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    private LegacyTiles.Placer source(ServerPlayer player){return new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());}
    private ItemEntity drop(Minecraft mc){return (ItemEntity)player(mc).serverLevel().getEntity(dropUuid);}
    private net.minecraft.world.entity.Entity clientDrop(Minecraft mc){var entity=mc.level.getEntity(dropId);return entity!=null&&entity.getUUID().equals(dropUuid)?entity:null;}
    private void spawn(Minecraft mc,ItemStack stack) {
        var player=player(mc);var entity=new ItemEntity(player.serverLevel(),player.getX(),player.getY()+1.55,player.getZ()+2,stack);
        entity.setNoGravity(true);entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);entity.setNeverPickUp();player.serverLevel().addFreshEntity(entity);dropUuid=entity.getUUID();dropId=entity.getId();
    }
    private void remove(Minecraft mc){var entity=drop(mc);if(entity!=null)entity.discard();}
    private void hold(Minecraft mc) {
        mc.player.setYRot(0);mc.player.setXRot(0);mc.player.yRotO=0;mc.player.xRotO=0;mc.options.keyUse.setDown(true);
        mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        if(!mc.player.isUsingItem())throw new IllegalStateException("Dropped item scanner did not enter held use");
    }
    private void release(Minecraft mc){mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
    private Object scanClient(Minecraft mc) throws Exception {
        var s=content.services();return s.items().inLegacy(()->{
            Object world=s.worlds().bound(mc.level);Object actor=content.itemUse().clientActor(world,new LegacyTiles.Placer(mc.player.getUUID(),mc.player.getName().getString(),mc.player.getX(),mc.player.getY(),mc.player.getZ(),0,0),null);
            Object stack=s.items().materialize(content.gameplayStack(mc.player.getMainHandItem()));Object item=stack.getClass().getMethod("func_77973_b").invoke(stack);
            var scan=item.getClass().getDeclaredMethod("doScan",stack.getClass(),s.loader().loadClass("net.minecraft.world.World"),s.loader().loadClass("net.minecraft.entity.player.EntityPlayer"),int.class);scan.setAccessible(true);
            return scan.invoke(item,stack,world,actor,0);
        });
    }
    private void verifyMirror(Minecraft mc,String key,int metadata,int count) throws Exception {
        Object scan=scanClient(mc);
        if(scan==null||scan.getClass().getField("type").getByte(scan)!=2)throw new IllegalStateException("Original scanner did not recognize the native dropped item");
        Object entity=scan.getClass().getField("entity").get(scan);
        var stack=content.services().items().snapshot(entity.getClass().getMethod("func_92059_d").invoke(entity));
        if((int)entity.getClass().getMethod("func_145782_y").invoke(entity)!=dropId||!stack.key().equals(key)||stack.metadata()!=metadata||stack.count()!=count)throw new IllegalStateException("Dropped item mirror lost its ID or full stack: "+stack);
    }
    private <T> T knowledge(Minecraft mc,boolean server,Callable<T> action) throws Exception {
        if(server&&!mc.getSingleplayerServer().isSameThread())return server(mc,()->knowledge(mc,true,action)).join();
        var s=content.services();return s.items().inLegacy(()->{try(var scope=server?s.playerState().enter(s.worlds().bound(player(mc).serverLevel())):(AutoCloseable)()->{}){return action.call();}});
    }
    private Object knowledge() throws Exception {var l=content.services().loader();Object proxy=l.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);return proxy.getClass().getMethod("getPlayerKnowledge").invoke(proxy);}
    private int pool(Minecraft mc,boolean server) throws Exception {
        return knowledge(mc,server,()->{var aspect=content.services().loader().loadClass("thaumcraft.api.aspects.Aspect");return ((Number)knowledge().getClass().getMethod("getAspectPoolFor",String.class,aspect).invoke(knowledge(),"Dev",aspect.getField("EARTH").get(null))).intValue();});
    }
    private boolean scanned(Minecraft mc,boolean server,String key,int metadata) throws Exception {
        return knowledge(mc,server,()->{
            var l=content.services().loader();Object stack=content.services().items().materialize(new LegacyClientData.Stack(key,metadata,1,"",""));Object item=stack.getClass().getMethod("func_77973_b").invoke(stack);
            int hash=(int)l.loadClass("thaumcraft.common.lib.research.ScanManager").getMethod("generateItemHash",l.loadClass("net.minecraft.item.Item"),int.class).invoke(null,item,metadata);
            var map=(Map<?,?>)knowledge().getClass().getField("objectsScanned").get(knowledge());
            return map.get("Dev") instanceof List<?> list&&list.contains("@"+hash);
        });
    }
    void verifyPersisted(Minecraft mc,boolean server) throws Exception {
        if(!scanned(mc,server,"minecraft:sand",1)||!scanned(mc,server,"minecraft:dirt",0)||pool(mc,server)!=awarded)throw new IllegalStateException("Dropped item rewards/scan records did not survive reload");
    }
    private <T> CompletableFuture<T> server(Minecraft mc,Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
}
