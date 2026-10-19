package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import github.com.gengyoubo.mfcl.compat.thaumcraft.ThaumcraftBiomeSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real dynamic registries, normal-world biome generation, original mutation, native sync and disk reload. */
final class BiomeHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final Map<String,BlockPos> natural=new LinkedHashMap<>();
    private final BlockPos changed=new BlockPos(-9,80,-9);
    private CompletableFuture<?> pending;
    private int stage,frames;
    private int reportedStage=-1;
    private String world;
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(6);
    private BiomeHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new BiomeHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(reportedStage!=stage){reportedStage=stage;com.mojang.logging.LogUtils.getLogger().info("MFCL_BIOME_STAGE {}",stage);}
            if(pending!=null&&pending.isCompletedExceptionally())pending.join();
            if(System.nanoTime()>deadline)throw new IllegalStateException("Biome smoke timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                world="mfcl-biomes-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("MFCL biome acceptance",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null){
                pending=server(mc,()->{verifyGeneration(mc.getSingleplayerServer().overworld());change(mc,"thaumcraft:tainted_land");return true;});stage=2;
            }else if(stage==2&&pending.isDone()&&clientIs(mc,"thaumcraft:tainted_land")){
                pending.join();verifyClient(mc,"thaumcraft:tainted_land");proof.put("originalMutationAndNativeClientSync",true);
                pending=server(mc,()->{change(mc,"thaumcraft:eerie");return true;});stage=3;
            }else if(stage==3&&pending.isDone()&&clientIs(mc,"thaumcraft:eerie")){
                pending.join();verifyClient(mc,"thaumcraft:eerie");
                pending=server(mc,()->{change(mc,"thaumcraft:magical_forest");return true;});stage=4;
            }else if(stage==4&&pending.isDone()&&clientIs(mc,"thaumcraft:magical_forest")){
                pending.join();verifyClient(mc,"thaumcraft:magical_forest");proof.put("legacyIdentityColorsAndRenderQuery",true);
                pending=server(mc,()->{
                    // This disposable fixture deliberately uses a custom generator. Confirm only its Forge warning.
                    ((net.minecraft.world.level.storage.PrimaryLevelData)mc.getSingleplayerServer().getWorldData()).withConfirmedWarning(true);
                    mc.getSingleplayerServer().saveEverything(false,true,true);return true;
                });stage=5;
            }else if(stage==5&&pending.isDone()){
                pending.join();mc.level.disconnect();mc.clearLevel();mc.setScreen(new TitleScreen());stage=6;
            }else if(stage==6&&mc.getSingleplayerServer()==null){mc.createWorldOpenFlows().loadLevel(mc.screen,world);stage=7;
            }else if(stage==7&&mc.player!=null&&mc.level!=null&&mc.screen==null){
                pending=server(mc,()->{
                    var level=mc.getSingleplayerServer().overworld();
                    if(!HostBiomeQueries.key(level,changed.getX(),changed.getZ()).equals("thaumcraft:magical_forest"))throw new IllegalStateException("Changed biome did not persist");
                    verifyGeneration(level);return true;
                });stage=8;frames=0;
            }else if(stage==8&&pending.isDone()&&++frames>15&&clientIs(mc,"thaumcraft:magical_forest")){
                pending.join();verifyClient(mc,"thaumcraft:magical_forest");proof.put("paletteAndGeneratorSurvivedReload",true);proof.put("status","PASS");finish(mc,null);
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private void verifyGeneration(ServerLevel level) throws Exception {
        var registry=level.registryAccess().registryOrThrow(Registries.BIOME);
        for(var definition:content.services().biomes().definitions()){
            var biome=registry.getOrThrow(ResourceKey.create(Registries.BIOME,ResourceLocation.parse(definition.key())));
            if(biome.getGrassColor(0,0)!=definition.grass()||biome.getFoliageColor()!=definition.foliage()||biome.getWaterColor()!=definition.water())throw new IllegalStateException("Original biome colors were lost: "+definition);
        }
        if(content.services().biomes().definitions().size()!=4)throw new IllegalStateException("Expected four original TC4 biomes");
        proof.put("fourOriginalBiomesRegistered",true);
        for(var entry:registry.entrySet())if(entry.getKey().location().getNamespace().equals("minecraft"))content.services().biomes().resolve(entry.getKey().location().toString());
        proof.put("allNativeBiomeNamesMapped",true);
        var generator=level.getChunkSource().getGenerator();var source=generator.getBiomeSource();
        if(!(source instanceof ThaumcraftBiomeSource))throw new IllegalStateException("Normal preset did not install TC4 biome source: "+source);
        if(source.possibleBiomes().stream().noneMatch(b->b.is(Biomes.OCEAN))||source.possibleBiomes().stream().noneMatch(b->b.is(Biomes.LUSH_CAVES)))throw new IllegalStateException("Wrapper discarded native biomes");
        var sampler=level.getChunkSource().randomState().sampler();
        var plain=registry.getHolderOrThrow(Biomes.PLAINS);
        var disabled=new ThaumcraftBiomeSource(new FixedBiomeSource(plain),registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME,ResourceLocation.parse("thaumcraft:magical_forest"))),registry.getHolderOrThrow(ResourceKey.create(Registries.BIOME,ResourceLocation.parse("thaumcraft:tainted_land"))),0,0);
        if(disabled.possibleBiomes().size()!=1||disabled.getNoiseBiome(40,16,-20,sampler)!=plain)throw new IllegalStateException("Zero weights still generated TC4 biomes");
        var flat=level.registryAccess().registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().overworld().orElseThrow().generator().getBiomeSource();
        if(flat instanceof ThaumcraftBiomeSource)throw new IllegalStateException("Flat preset was changed");
        proof.put("zeroWeightsAndFlatPresetPreserved",true);
        if(natural.isEmpty()){
            var random=new Random(76);
            for(int i=0;i<30000&&natural.size()<2;i++){
                int x=random.nextInt(4096)-2048,z=random.nextInt(4096)-2048;
                var key=source.getNoiseBiome(x,16,z,sampler).unwrapKey().orElseThrow().location().toString();
                if(key.equals("thaumcraft:magical_forest")||key.equals("thaumcraft:tainted_land"))natural.putIfAbsent(key,new BlockPos(x*4,64,z*4));
            }
        }
        if(natural.size()!=2)throw new IllegalStateException("Both naturally generated biomes were not found: "+natural);
        for(var entry:natural.entrySet()){
            var pos=entry.getValue();var chunk=level.getChunk(pos);
            if(!chunk.getNoiseBiome(pos.getX()>>2,16,pos.getZ()>>2).is(ResourceKey.create(Registries.BIOME,ResourceLocation.parse(entry.getKey()))))throw new IllegalStateException("Generated palette/source mismatch at "+pos);
            if(!source.getNoiseBiome(pos.getX()>>2,16,pos.getZ()>>2,sampler).unwrapKey().orElseThrow().location().toString().equals(entry.getKey()))throw new IllegalStateException("Seeded biome source changed after serialization");
        }
        proof.put("naturalBiomeChunks",natural.entrySet().stream().collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,e->e.getValue().toShortString())));
        proof.put("nativeBiomesAndCavesPreserved",true);
    }
    private void change(Minecraft mc,String key) throws Exception {
        var level=mc.getSingleplayerServer().overworld();var player=level.players().get(0);
        player.teleportTo(level,-8.5,Math.max(80,level.getHeight(Heightmap.Types.WORLD_SURFACE,-9,-9)+3),-8.5,0,0);
        String untouched=level.getNoiseBiome(-4,16,-3).unwrapKey().orElseThrow().location().toString();
        var services=content.services();services.items().inLegacy(()->{
            var loader=services.loader();Object world=services.worlds().bound(level);
            loader.loadClass("thaumcraft.common.lib.utils.Utils").getMethod("setBiomeAt",loader.loadClass("net.minecraft.world.World"),int.class,int.class,loader.loadClass("net.minecraft.world.biome.BiomeGenBase"))
                    .invoke(null,world,changed.getX(),changed.getZ(),services.biomes().resolve(key));
            if(LegacyWorlds.biome(world,changed.getX(),changed.getZ())!=services.biomes().resolve(key))throw new IllegalStateException("Legacy biome read lost original identity");
            return null;
        });
        if(!HostBiomeQueries.key(level,changed.getX(),changed.getZ()).equals(key))throw new IllegalStateException("Original mutation did not update the actual server palette");
        if(!level.getNoiseBiome(-4,16,-3).unwrapKey().orElseThrow().location().toString().equals(untouched))throw new IllegalStateException("Column mutation changed a neighboring quart column");
        proof.put("negativeCoordinatesAndAdjacentColumnPreserved",true);
    }
    private boolean clientIs(Minecraft mc,String key){
        return mc.level!=null&&mc.level.hasChunkAt(changed)&&mc.level.getNoiseBiome(changed.getX()>>2,16,changed.getZ()>>2).unwrapKey().orElseThrow().location().toString().equals(key);
    }
    private void verifyClient(Minecraft mc,String key) throws Exception {
        var services=content.services();services.items().inLegacy(()->{
            if(LegacyWorlds.biome(ClientWorldBridge.world(services),changed.getX(),changed.getZ())!=services.biomes().resolve(key))throw new IllegalStateException("Client query returned a fallback biome");
            try{LegacyWorlds.setBiome(ClientWorldBridge.world(services),changed.getX(),changed.getZ(),services.biomes().resolve("thaumcraft:eldritch"));throw new IllegalStateException("Client changed authoritative biome");}
            catch(UnsupportedOperationException expected){}
            return null;
        });
        if(!content.renderView(mc.level,changed).biome(0,0).equals(key))throw new IllegalStateException("Render view did not query the actual biome");
    }
    private <T> CompletableFuture<T> server(Minecraft mc,Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private void finish(Minecraft mc,Throwable failure){
        stage=99;if(failure!=null){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_BIOME_SMOKE_FAIL",failure);}
        try{var path=mc.gameDirectory.toPath().resolve("logs/mfcl/biome-smoke.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_BIOME_SMOKE_RESULT: {}",proof);if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
