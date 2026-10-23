package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared mechanisms validated without TC4 classes, using original vanilla objects and a separate portable policy. */
final class CompatBridgeHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private boolean done;
    private CompatBridgeHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new CompatBridgeHostSmokeProbe(content)::tick);}
    private void check(boolean value,String name){if(!value)throw new AssertionError(name);proof.put(name,true);}
    @FunctionalInterface private interface Work{void run()throws Exception;}
    private void rejects(Work work,String name)throws Exception {try{work.run();}catch(IllegalArgumentException expected){proof.put(name,true);return;}throw new AssertionError(name);}
    private static final class Packet {
        private int dimension,sender,px=-7,py=64,pz=11;
        private String owner;
    }
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;done=true;var server=event.getServer();var services=content.services();var level=server.overworld();
        var player=FakePlayerFactory.getMinecraft(level);
        try {
            check(services.loader().getResource("thaumcraft/common/Thaumcraft.class")==null,"noThaumcraftDependency");
            player.getAbilities().instabuild=false;player.moveTo(0.5,200,0.5,0,0);level.getChunkAt(player.blockPosition());level.addNewPlayer(player);
            Object world=services.worlds().bound(level),actor=content.itemUse().clientActor(world,ModernContentBridge.placer(player),null);
            services.items().inLegacy(()->{
                ClassLoader prior=Thread.currentThread().getContextClassLoader();
                LegacyModAdapter.withHostContext(()->{
                    check(Thread.currentThread().getContextClassLoader()==LegacyModAdapter.class.getClassLoader(),"nativeContextSelected");
                    LegacyModAdapter.withHostContext(()->{});
                });check(Thread.currentThread().getContextClassLoader()==prior,"nestedContextRestored");
                Exception sentinel=new Exception("context sentinel");
                try{LegacyModAdapter.withHostContext(()->{throw sentinel;});}catch(Exception failure){if(failure!=sentinel)throw failure;}
                check(Thread.currentThread().getContextClassLoader()==prior,"failedContextRestored");
                Packet packet=new Packet();packet.dimension=LegacyObjects.dimension(world);packet.sender=-47;packet.owner=LegacyObjects.playerName(actor);
                LegacyPacketTargets.bindPlayer(packet,actor,"dimension","sender","Fixture");check(packet.sender==LegacyObjects.entityId(actor),"packetSenderRebound");
                LegacyPacketTargets.requirePlayerName(packet,actor,"owner","Fixture");
                packet.dimension++;packet.sender=-47;rejects(()->LegacyPacketTargets.bindPlayer(packet,actor,"dimension","sender","Fixture"),"wrongDimensionRejected");
                check(packet.sender==-47,"rejectedSenderNotRewritten");packet.dimension--;packet.owner="another player";
                rejects(()->LegacyPacketTargets.requirePlayerName(packet,actor,"owner","Fixture"),"spoofedNameRejected");
                var target=new LegacyDeviceContainers.Descriptor("Fixture",21,-7,64,11,false);
                LegacyPacketTargets.requireDevice(packet,target,"Fixture",21,"px","py","pz","Fixture");
                packet.px++;rejects(()->LegacyPacketTargets.requireDevice(packet,target,"Fixture",21,"px","py","pz","Fixture"),"wrongCoordinatesRejected");packet.px--;
                rejects(()->LegacyPacketTargets.requireDevice(packet,null,"Fixture",21,"px","py","pz","Fixture"),"closedDeviceRejected");
                rejects(()->LegacyPacketTargets.requireDevice(packet,target,"Other",21,"px","py","pz","Fixture"),"wrongDeviceRejected");
                Object opening=services.items().materialize(content.gameplayStack(new ItemStack(Items.STICK,1)));
                Object diamond=services.items().materialize(content.gameplayStack(new ItemStack(Items.DIAMOND,1)));
                proof.put("portablePolicy",services.loader().loadClass("github.com.gengyoubo.mfcl.legacy1710.probe.PortableContainerProbe").getMethod("run",Object.class,Object.class,Object.class,Object.class).invoke(null,world,actor,opening,diamond));
                return null;
            });
            var created=new AtomicInteger();
            LegacyCrossbowAmmo.Factory factory=(oldWorld,shooter,ammunition)->{
                created.incrementAndGet();check(ammunition.key().equals("minecraft:arrow"),"genericAmmoDelivered");
                var loader=services.loader();return loader.loadClass("net.minecraft.entity.projectile.EntityArrow").getConstructor(loader.loadClass("net.minecraft.world.World"),loader.loadClass("net.minecraft.entity.EntityLivingBase"),float.class).newInstance(oldWorld,shooter,3.0f);
            };
            services.crossbowAmmo().register("minecraft:arrow",factory);
            rejects(()->services.crossbowAmmo().register("minecraft:arrow",factory),"duplicateAmmoRejected");
            for(int scenario=0;scenario<3;scenario++) {
                for(var arrow:level.getEntitiesOfClass(AbstractArrow.class,player.getBoundingBox().inflate(64)))arrow.discard();
                player.getInventory().clearContent();player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.getInventory().selected=0;
                var hand=scenario==1?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND;var crossbow=new ItemStack(Items.CROSSBOW);
                if(scenario==2)crossbow.enchant(net.minecraft.world.item.enchantment.Enchantments.MULTISHOT,1);
                player.setItemInHand(hand,crossbow);player.getInventory().setItem(1,new ItemStack(Items.ARROW,5));
                crossbow.use(level,player,hand);crossbow.releaseUsing(level,player,crossbow.getUseDuration()-CrossbowItem.getChargeDuration(crossbow)-1);player.stopUsingItem();
                check(CrossbowItem.isCharged(crossbow),"nativeCrossbowCharged"+scenario);int before=created.get();crossbow.use(level,player,hand);
                int expected=scenario==2?3:1;check(created.get()-before==expected,"registeredFactoryUsed"+scenario);
                var arrows=level.getEntitiesOfClass(AbstractArrow.class,player.getBoundingBox().inflate(64));
                check(arrows.size()==expected&&arrows.stream().allMatch(arrow->arrow.getDeltaMovement().lengthSqr()>1),"trajectoryAndMultishotPreserved"+scenario);
                check(player.getInventory().countItem(Items.ARROW)==4,"nativeAmmoConsumedOnce"+scenario);
            }
            proof.put("status","PASS");
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_COMPAT_BRIDGE_SMOKE_FAIL",failure);}
        finally {
            player.discard();
            try{var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/compat-bridge-smoke.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
            catch(Exception failure){throw new IllegalStateException(failure);}
            com.mojang.logging.LogUtils.getLogger().info("MFCL_COMPAT_BRIDGE_SMOKE_DONE: {}",proof);server.halt(false);
        }
    }
}
