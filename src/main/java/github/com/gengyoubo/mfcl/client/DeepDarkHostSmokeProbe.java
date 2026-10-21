package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Disposable native client/server acceptance; both portal clicks cross the actual host connection. */
final class DeepDarkHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(8);
    private CompletableFuture<?> pending;
    private int stage,frames;
    private BlockPos entry,exit,particles;
    private DeepDarkHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(new DeepDarkHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(pending!=null&&pending.isCompletedExceptionally())pending.join();
            if(System.nanoTime()>deadline)throw new IllegalStateException("Deep Dark acceptance timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-deep-dark-"+System.currentTimeMillis(),new LevelSettings("MFCL Deep Dark acceptance",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null){
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();entry=player.blockPosition().offset(2,0,0);
                    level.setBlockAndUpdate(entry,content.modernBlock(new HostWorldAccess.Block("ExtraUtilities:dark_portal",0)));
                    return true;
                });stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&++frames>25){click(mc,entry);stage=3;frames=0;
            }else if(stage==3&&mc.level!=null&&mc.level.dimension().location().toString().equals("extrautils:deep_dark")&&mc.screen==null&&++frames>25){
                proof.put("nativeRightClickEntered",true);
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();exit=player.blockPosition().below();
                    if(!content.legacyBlock(level.getBlockState(exit)).legacyKey().equals("ExtraUtilities:dark_portal"))throw new IllegalStateException("Arrival has no original return portal: "+player.position());
                    if(level.getBlockState(exit.below()).isAir()||!level.getBlockState(new BlockPos(exit.getX(),0,exit.getZ())).is(net.minecraft.world.level.block.Blocks.BEDROCK))throw new IllegalStateException("Arrival room or original base terrain absent");
                    proof.put("originalTerrainAndArrivalRoom",true);proof.put("arrival",player.position().toString());
                    particles=exit.offset(1,1,0);level.setBlockAndUpdate(particles,content.modernBlock(new HostWorldAccess.Block("ExtraUtilities:dark_portal",2)));
                    return true;
                });stage=4;frames=0;
            }else if(stage==4&&pending.isDone()&&++frames>20){
                content.services().items().inLegacy(()->{
                    var loader=content.services().loader();var oldMc=loader.loadClass("net.minecraft.client.Minecraft");Object client=oldMc.getMethod("func_71410_x").invoke(null);
                    Object renderer=oldMc.getField("field_71452_i").get(client);
                    int before=Integer.parseInt((String)renderer.getClass().getMethod("func_78869_b").invoke(renderer));
                    content.services().logic().displayTick("ExtraUtilities:dark_portal",content.services().worlds().bound(mc.level),particles.getX(),particles.getY(),particles.getZ(),42);
                    int after=Integer.parseInt((String)renderer.getClass().getMethod("func_78869_b").invoke(renderer));
                    if(after!=before+1)throw new IllegalStateException("Original portal particle did not enter current renderer");
                    return null;
                });proof.put("portalParticlesInCurrentRenderer",true);click(mc,exit);stage=5;frames=0;
            }else if(stage==5&&mc.level!=null&&mc.level.dimension()==Level.OVERWORLD&&mc.screen==null&&++frames>20){
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    if(Math.abs(player.getX()-entry.getX()-.5)>.01||Math.abs(player.getZ()-entry.getZ()-.5)>.01||Math.abs(player.getY()-entry.getY()-1)>1.01)throw new IllegalStateException("Did not return to original overworld portal");
                    proof.put("nativeRightClickReturned",true);return true;
                });stage=6;
            }else if(stage==6&&pending.isDone()){pending.join();proof.put("status","PASS");finish(mc,null);}
        }catch(Throwable failure){finish(mc,failure);}
    }
    private void click(Minecraft mc,BlockPos pos){
        if(!mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false)).consumesAction())throw new IllegalStateException("Native portal interaction was not consumed");
    }
    private void finish(Minecraft mc,Throwable failure){
        if(failure!=null){proof.put("status","FAIL");proof.put("stage",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_DEEP_DARK_SMOKE_FAIL",failure);}
        stage=99;
        try{var output=mc.gameDirectory.toPath().resolve("logs/mfcl/deep-dark-smoke.json");java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_DEEP_DARK_SMOKE_RESULT {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
