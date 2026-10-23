package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.*;
import github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftProspecting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real native item callbacks with original TC4 effects, server entities and the prospecting render path. */
final class ElementalToolsHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<?> pending;
    private BlockPos origin,ore;
    private int stage,frames;
    private int heldFromTick;
    private double heldFromY;
    private final java.util.concurrent.atomic.AtomicInteger blockUses=new java.util.concurrent.atomic.AtomicInteger();
    private ElementalToolsHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){var probe=new ElementalToolsHostSmokeProbe(content);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(probe::tick);net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event)->{if(!event.getLevel().isClientSide)probe.blockUses.incrementAndGet();});}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Elemental tools probe timed out at "+stage);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(++frames<15)return;
            if(stage==0){
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-elemental-"+System.currentTimeMillis(),new LevelSettings("MFCL elemental tools",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1){
                if(mc.player==null||mc.screen!=null)return;
                pending=mc.getSingleplayerServer().submit(()->{try{audit(player(mc));prepareScan(player(mc));}catch(Exception failure){throw new IllegalStateException(failure);}});
            }else if(stage==2){
                if(!mc.level.getBlockState(ore).is(Blocks.DIAMOND_ORE)||!mc.player.getMainHandItem().is(content.items().get("Thaumcraft:ItemPickaxeElemental")))return;
                var hit=new BlockHitResult(Vec3.atCenterOf(origin).add(0,0,-.5),Direction.NORTH,origin,false);
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit);
            }else if(stage==3){
                verifyScan(mc);
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=player(mc);int damage=player.getMainHandItem().getDamageValue();proof.put("prospectingServerWear",damage);proof.put("scanPacketDiagnostics",Map.of("serverBlockUses",blockUses.get(),"serverPosition",player.position().toString(),"canReach",player.canReach(origin,1.5),"mayInteract",player.level().mayInteract(player,origin)));check(damage==5,"Prospecting must cost five durability exactly once: damage="+damage);
                });
            }else if(stage==4){
                // The client's own held-use callback must also write its original movement back.
                var player=mc.player;player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:ItemSwordElemental",0,1,""));player.setDeltaMovement(0,0,0);
                player.getMainHandItem().use(mc.level,player,InteractionHand.MAIN_HAND);var stack=player.getMainHandItem();stack.getItem().onUseTick(mc.level,player,stack,71999);
                check(Math.abs(player.getDeltaMovement().y-.08)<.00001,"Client sword lift was discarded: "+player.getDeltaMovement());player.releaseUsingItem();proof.put("swordClientLift",true);
                pending=mc.getSingleplayerServer().submit(()->{var serverPlayer=player(mc);hold(serverPlayer,"Thaumcraft:ItemSwordElemental");serverPlayer.serverLevel().setBlockAndUpdate(new BlockPos(0,5,0),Blocks.STONE.defaultBlockState());serverPlayer.connection.teleport(.5,6,.5,0,-60);serverPlayer.inventoryMenu.broadcastChanges();});
            }else if(stage==5){
                if(!mc.player.getMainHandItem().is(content.items().get("Thaumcraft:ItemSwordElemental"))||Math.abs(mc.player.getY()-6)>.1)return;
                heldFromTick=mc.player.tickCount;heldFromY=mc.player.getY();mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }else if(stage==6){
                if(mc.player.tickCount-heldFromTick<30)return;
                check(mc.player.isUsingItem()&&mc.player.getY()>heldFromY+.2,"Natural held-use ticks did not lift the client: "+mc.player.getY());
                pending=mc.getSingleplayerServer().submit(()->{var player=player(mc);check(player.isUsingItem()&&player.getY()>heldFromY+.2&&player.getMainHandItem().getDamageValue()>0,"Natural held-use ticks did not retain server lift/wear");proof.put("naturalClientAndServerHeldTicks",Map.of("serverY",player.getY(),"startingY",heldFromY,"damage",player.getMainHandItem().getDamageValue()));});
                mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);
            }else if(stage==7){proof.put("status","PASS");finish(mc);return;
            }
            stage++;frames=0;
        }catch(Throwable failure){com.mojang.logging.LogUtils.getLogger().error("MFCL elemental tools acceptance failed",failure);proof.put("status","FAIL");proof.put("failure",failure.toString());try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}}
    }
    private ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);}
    private void hold(ServerPlayer player,String key){player.releaseUsingItem();player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,content.stack(key,0,1,""));player.setDeltaMovement(0,0,0);player.fallDistance=0;}
    private void using(ServerPlayer player,int elapsed){var stack=player.getMainHandItem();stack.getItem().onUseTick(player.level(),player,stack,stack.getUseDuration()-elapsed);}
    private ItemEntity drop(ServerPlayer player,double x,double z){var item=new ItemEntity(player.level(),player.getX()+x,player.getY()+.5,player.getZ()+z,new ItemStack(Items.STICK,3));item.setDeltaMovement(0,0,0);item.setNoGravity(true);item.setPickUpDelay(100);player.serverLevel().addFreshEntity(item);return item;}
    private void audit(ServerPlayer player)throws Exception {
        player.getInventory().clearContent();player.getAbilities().instabuild=false;player.getAbilities().flying=false;player.teleportTo(.5,5,.5);player.setOnGround(true);
        hold(player,"Thaumcraft:ItemSwordElemental");var pushed=drop(player,1.5,0);
        player.gameMode.useItem(player,player.level(),player.getMainHandItem(),InteractionHand.MAIN_HAND);check(player.isUsingItem(),"Sword did not start native use");using(player,1);
        check(Math.abs(player.getDeltaMovement().y-.08)<.00001,"Server sword lift was discarded: "+player.getDeltaMovement());check(pushed.getDeltaMovement().x>0,"Sword did not push the real dropped entity");
        proof.put("swordServerLiftAndEntityRepulsion",Map.of("lift",player.getDeltaMovement().y,"pushX",pushed.getDeltaMovement().x));
        var floating=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.server.network.ServerGamePacketListenerImpl.class,"f_9737_");floating.setInt(player.connection,79);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END,player));check(floating.getInt(player.connection)==0,"Sword reset only the old connection's floating counter");proof.put("swordResetsRealServerFloatingCounter",true);
        player.setDeltaMovement(0,-.24,0);player.fallDistance=6;using(player,2);
        check(Math.abs(player.getDeltaMovement().y-(-.24/1.2000000476837158+.07999999821186066))<.00001&&Math.abs(player.fallDistance-5)<.00001,"Original sword fall reduction missing");
        using(player,20);check(player.getMainHandItem().getDamageValue()>0,"Sword use did not retain durability cost");proof.put("swordFallReductionAndWear",true);pushed.discard();
        hold(player,"Thaumcraft:ItemAxeElemental");var pulled=drop(player,5,0);
        player.gameMode.useItem(player,player.level(),player.getMainHandItem(),InteractionHand.MAIN_HAND);check(player.isUsingItem(),"Axe did not start native use");using(player,1);
        check(pulled.getDeltaMovement().x<0,"Axe attraction did not reach real item entity");check(Math.abs(pulled.getDeltaMovement().x)<=.35001,"Original axe speed cap was lost");
        proof.put("axeAuthoritativeDropAttraction",Map.of("velocityX",pulled.getDeltaMovement().x,"stackCount",pulled.getItem().getCount()));
        player.releaseUsingItem();pulled.setDeltaMovement(0,0,0);player.tick();check(pulled.getDeltaMovement().lengthSqr()==0,"Released axe kept pulling");check(pulled.getItem().getCount()==3,"Attraction changed item count");pulled.discard();proof.put("axeReleaseAndStackConservation",true);
    }
    private void prepareScan(ServerPlayer player){
        hold(player,"Thaumcraft:ItemPickaxeElemental");origin=new BlockPos(0,5,3);ore=origin.east(2);
        player.serverLevel().setBlockAndUpdate(new BlockPos(0,4,0),Blocks.STONE.defaultBlockState());
        player.serverLevel().setBlockAndUpdate(origin,Blocks.STONE.defaultBlockState());player.serverLevel().setBlockAndUpdate(ore,Blocks.DIAMOND_ORE.defaultBlockState());
        player.connection.teleport(.5,5,.5,0,0);player.inventoryMenu.broadcastChanges();
        var hit=new BlockHitResult(Vec3.atCenterOf(origin).add(0,0,-.5),Direction.NORTH,origin,false);
        player.gameMode.useItemOn(player,player.level(),player.getMainHandItem(),InteractionHand.MAIN_HAND,hit);
        int damage=player.getMainHandItem().getDamageValue();proof.put("directNativeProspectingWear",damage);check(damage==5,"Native server prospecting cost mismatch: "+damage);
        player.getMainHandItem().setDamageValue(0);player.inventoryMenu.broadcastChanges();
    }
    private void verifyScan(Minecraft mc)throws Exception {
        var services=content.services();long end=services.items().inLegacy(()->{
            var loader=services.loader();Object tc=loader.loadClass("thaumcraft.common.Thaumcraft").getField("instance").get(null),renderer=tc.getClass().getField("renderEventHandler").get(tc);
            long expiry=renderer.getClass().getField("scanCount").getLong(renderer);check(expiry>System.currentTimeMillis(),"Pickaxe did not start original client scan");
            var field=renderer.getClass().getDeclaredField("scannedBlocks");field.setAccessible(true);int[][][] result=(int[][][])field.get(renderer);
            check(result[ore.getX()-origin.getX()+8][8][8]>=0,"Original scan failed to identify diamond ore");return expiry;
        });
        var geometry=ThaumcraftProspecting.capture(services,0,end-2500);check(!geometry.isEmpty(),"Prospecting overlay generated no geometry");
        check(geometry.stream().allMatch(p->!p.state().depthTest()&&!p.state().depthWrite()),"Ore markers would be hidden by surrounding stone");
        check(ThaumcraftProspecting.capture(services,0,end+1).isEmpty(),"Expired prospecting overlay remained visible");
        proof.put("originalProspectingOreGeometryThroughWallsAndExpiry",Map.of("primitives",geometry.size(),"ore",ore.toShortString()));
    }
    private void finish(Minecraft mc)throws Exception{
        mc.options.keyUse.setDown(false);var path=mc.gameDirectory.toPath().resolve("logs/mfcl/elemental-tools-smoke.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));com.mojang.logging.LogUtils.getLogger().info("MFCL_ELEMENTAL_TOOLS_SMOKE {}",proof);stage=99;if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
