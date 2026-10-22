package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real native Mekanism cubes, driven by normal legacy block-entity ticks, followed by the book acceptance. */
final class MekanismEnergyHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final List<BlockPos> sources=new ArrayList<>(),sinks=new ArrayList<>(),nodes=new ArrayList<>();
    private int stage,frames;private long deadline;private CompletableFuture<?> pending;
    private MekanismEnergyHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new MekanismEnergyHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(deadline==0)deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10);
            if(pending!=null&&pending.isCompletedExceptionally())pending.join();
            if(System.nanoTime()>deadline)throw new IllegalStateException("Mekanism acceptance timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-mekanism-energy-"+System.currentTimeMillis(),new net.minecraft.world.level.LevelSettings("MFCL actual Mekanism energy",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.GameRules(),net.minecraft.world.level.WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.screen==null){
                pending=server(()->{setup(mc.getSingleplayerServer().overworld(),mc.getSingleplayerServer().getPlayerList().getPlayers().get(0).blockPosition());return true;});stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&++frames>160){
                pending=server(()->{verify(mc.getSingleplayerServer().overworld());return true;});stage=3;
            }else if(stage==3&&pending.isDone()){pending.join();finish(null);}
        }catch(Throwable failure){finish(failure);}
    }
    private CompletableFuture<Boolean> server(Callable<Boolean> action){return Minecraft.getInstance().getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private void setup(ServerLevel level,BlockPos origin)throws Exception{
        var cube=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism","basic_energy_cube"));
        if(cube==net.minecraft.world.level.block.Blocks.AIR)throw new IllegalStateException("Actual Mekanism cube not installed");
        proof.put("mekanism",net.minecraftforge.fml.ModList.get().getModContainerById("mekanism").orElseThrow().getModInfo().getVersion().toString());
        for(int path=0;path<3;path++){
            BlockPos source=origin.offset(2,0,path*6);sources.add(source);
            level.setBlockAndUpdate(source,content.blocks().get("ExtraUtilities:generator").defaultBlockState());
            var generator=(LegacyBlockEntity)level.getBlockEntity(source);Object old=generator.legacy(),storage=old.getClass().getMethod("getStorage").invoke(old);
            storage.getClass().getMethod("setEnergyStored",int.class).invoke(storage,10000);old.getClass().getField("coolDown").setDouble(old,0);generator.synchronize();
            int distance=path==0?1:path==1?2:4;
            if(path>0){BlockPos node=source.east();nodes.add(node);level.setBlockAndUpdate(node,content.blocks().get("ExtraUtilities:extractor_base").defaultBlockState().setValue(ModernContentBridge.METADATA,12));}
            if(path==2)for(int pipe=2;pipe<4;pipe++)level.setBlockAndUpdate(source.offset(pipe,0,0),content.blocks().get("ExtraUtilities:pipes").defaultBlockState());
            BlockPos sink=source.offset(distance,0,0);sinks.add(sink);level.setBlockAndUpdate(sink,cube.defaultBlockState());
            var energy=level.getBlockEntity(sink).getCapability(ForgeCapabilities.ENERGY,Direction.WEST).orElseThrow(()->new IllegalStateException("Mekanism input exposes no Forge energy"));
            if(!energy.canReceive()||energy.receiveEnergy(100,true)<=0)throw new IllegalStateException("Mekanism cube's west input rejects simulated FE");
        }
    }
    private void verify(ServerLevel level)throws Exception{
        var results=new ArrayList<Map<String,Object>>();
        for(int path=0;path<3;path++){
            var generator=(LegacyBlockEntity)level.getBlockEntity(sources.get(path));var sink=level.getBlockEntity(sinks.get(path));
            int received=sink.getCapability(ForgeCapabilities.ENERGY,Direction.WEST).orElseThrow(IllegalStateException::new).getEnergyStored();
            int remaining=generator.getCapability(ForgeCapabilities.ENERGY,null).orElseThrow(IllegalStateException::new).getEnergyStored(),buffer=0;
            var row=new LinkedHashMap<String,Object>();row.put("path",List.of("generator-cube","generator-node-cube","generator-node-pipes-cube").get(path));row.put("receivedFE",received);row.put("sourceRF",remaining);row.put("generatorPaused",generator.logicSuspended());
            if(path>0){var node=(LegacyBlockEntity)level.getBlockEntity(nodes.get(path-1));buffer=node.getCapability(ForgeCapabilities.ENERGY,null).orElseThrow(IllegalStateException::new).getEnergyStored();row.put("nodeRF",buffer);row.put("nodePaused",node.logicSuspended());}
            results.add(row);proof.put("paths",results);
            if(received<=0||remaining+buffer+received!=10000||generator.logicSuspended()||(path>0&&((LegacyBlockEntity)level.getBlockEntity(nodes.get(path-1))).logicSuspended()))throw new IllegalStateException("Actual tick transfer failed: "+row);
        }
    }
    private void finish(Throwable failure){
        if(stage==99)return;stage=99;var mc=Minecraft.getInstance();proof.put("status",failure==null?"PASS":"FAIL");
        if(failure!=null){proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_MEKANISM_ENERGY_FAIL",failure);}
        try{var path=mc.gameDirectory.toPath().resolve("logs/mfcl/mekanism-energy-smoke.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception error){throw new IllegalStateException(error);}
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.setScreen(new TitleScreen());BookHostSmokeProbe.install(content);
    }
}
