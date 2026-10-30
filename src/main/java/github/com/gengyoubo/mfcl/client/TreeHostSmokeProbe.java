package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Shared height, legacy leaf-loot/decay and material-sound acceptance in a disposable client world. */
final class TreeHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final Set<String> heard=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final List<BlockPos> saplings=new ArrayList<>();
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(6);
    private CompletableFuture<Boolean> pending;
    private BlockPos decay;
    private long started;
    private int stage,frames;
    private TreeHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){
        var probe=new TreeHostSmokeProbe(content);MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.client.event.sound.PlaySoundSourceEvent event)->{
            if(event.getChannel()!=null)probe.heard.add(event.getSound().getLocation().toString());
        });
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private void tick(TickEvent.ClientTickEvent event){
        if(stage==99||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try{
            check(System.nanoTime()<deadline,"Tree probe timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-trees-"+System.currentTimeMillis(),new LevelSettings("MFCL trees",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.screen==null){pending=server(mc,()->{setup(mc);return false;});stage=2;
            }else if(stage==2){
                if(pending!=null){if(!pending.isDone())return;boolean done=pending.join();pending=null;if(done){stage=3;frames=0;return;}}
                if(++frames%10==0)pending=server(mc,()->observe(mc));
            }else if(stage==3&&++frames>40){
                check(heard.contains("minecraft:block.wood.break"),"Wood break never reached an audio channel: "+heard);
                check(heard.contains("minecraft:block.grass.break"),"Leaf break never reached an audio channel: "+heard);
                proof.put("audioChannels",new TreeSet<>(heard));proof.put("status","PASS");finish(mc);
            }
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL tree acceptance failed",failure);try{finish(mc);}catch(Exception e){throw new IllegalStateException(e);}}
    }
    private net.minecraft.world.level.block.state.BlockState state(String key,int meta){return content.modernBlock(new HostWorldAccess.Block(key,meta));}
    private void platform(ServerLevel level,BlockPos base){for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)level.setBlock(base.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),2);}
    private void setup(Minecraft mc)throws Exception {
        var level=mc.getSingleplayerServer().overworld();var player=level.players().get(0);var services=content.services();var loader=services.loader();var world=services.worlds().bound(level);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());level.setDayTime(6000);
        services.items().inLegacy(()->{
            var worldType=loader.loadClass("net.minecraft.world.World");
            check((int)worldType.getMethod("func_72800_K").invoke(world)==level.getMaxBuildHeight(),"Old height getter is still 256");
            check(github.com.gengyoubo.mfcl.bridge.LegacyWorlds.minBuildHeight(world)==level.getMinBuildHeight(),"Old minimum is still zero");
            proof.put("worldBounds",List.of(level.getMinBuildHeight(),level.getMaxBuildHeight()));
            var protocol=new ArrayList<String>();var bytebuf=loader.loadClass("io.netty.buffer.ByteBuf");
            for(String packetName:List.of("thaumcraft.common.lib.network.misc.PacketNote","thaumcraft.common.lib.network.fx.PacketFXVisDrain"))for(int y:new int[]{-64,-1,256,319}){
                Class<?> packet=loader.loadClass(packetName);Object source=packet.getConstructor().newInstance(),decoded=packet.getConstructor().newInstance();
                var coordinate=packet.getDeclaredField("y");coordinate.setAccessible(true);coordinate.setInt(source,y);
                Object buffer=loader.loadClass("io.netty.buffer.Unpooled").getMethod("buffer").invoke(null);
                try{packet.getMethod("toBytes",bytebuf).invoke(source,buffer);packet.getMethod("fromBytes",bytebuf).invoke(decoded,buffer);check(coordinate.getInt(decoded)==y,"Packet truncated Y: "+packetName);}
                finally{bytebuf.getMethod("release").invoke(buffer);}protocol.add(packet.getSimpleName()+":"+y);
            }
            proof.put("originalPacketYRoundTrips",protocol);
            for(int y:new int[]{-40,280}){
                var at=new BlockPos(-8,y,8);level.setBlock(at,state("Thaumcraft:blockAiry",0),2);
                var tile=(github.com.gengyoubo.mfcl.content.LegacyBlockEntity)level.getBlockEntity(at);byte[] saved=services.tiles().save(tile.legacy());
                Object restored=services.tiles().snapshotInstance(null,saved);services.tiles().load(restored,saved);
                check(loader.loadClass("net.minecraft.tileentity.TileEntity").getField("field_145848_d").getInt(restored)==y,"Original tile NBT truncated Y");level.setBlock(at,Blocks.AIR.defaultBlockState(),2);
            }
            proof.put("originalTileYRoundTrips",List.of(-40,280));
            return null;
        });
        var direct=new ArrayList<Map<String,Object>>();proof.put("originalVanillaGenerators",direct);
        int index=0;
        for(String name:List.of("net.minecraft.world.gen.feature.WorldGenTrees","net.minecraft.world.gen.feature.WorldGenTaiga2"))for(int y:new int[]{-32,280}){
            int sample=index++;var at=new BlockPos(64+(sample%2)*24,y,32+(sample/2)*24);platform(level,at);
            boolean generated=content.logicTransaction(level,at,()->services.items().inLegacy(()->{
                Object generator=loader.loadClass(name).getConstructor(boolean.class).newInstance(true);
                return (boolean)generator.getClass().getMethod("func_76484_a",loader.loadClass("net.minecraft.world.World"),Random.class,int.class,int.class,int.class).invoke(generator,world,new Random(42),at.getX(),at.getY(),at.getZ());
            }),true);check(generated,"Original vanilla generator rejected host y="+y+": "+name);direct.add(Map.of("generator",name,"y",y));
        }
        leafLoot(level,player);
        for(int y:new int[]{-40,100,280})for(int type=0;type<2;type++){
            var at=new BlockPos(type*32,y,0);platform(level,at);level.setBlock(at.east(5),Blocks.GLOWSTONE.defaultBlockState(),3);level.setBlock(at,state("Thaumcraft:blockCustomPlant",type),3);saplings.add(at);
        }
        player.setGameMode(GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.connection.teleport(16.5,50,8.5,0,0);
        for(int x=14;x<20;x++)for(int z=6;z<12;z++)level.setBlock(new BlockPos(x,49,z),Blocks.STONE.defaultBlockState(),3);
        check(state("Thaumcraft:blockMagicalLog",1).getSoundType().getBreakSound().getLocation().toString().equals("minecraft:block.wood.break"),"Wood sound property not mirrored");
        check(state("Thaumcraft:blockMagicalLeaves",1).getSoundType().getBreakSound().getLocation().toString().equals("minecraft:block.grass.break"),"Leaf sound property not mirrored");
        // Real block-destruction level events resolve the mirrored state's sound on the receiving client.
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(1024,mc.getSingleplayerServer());started=level.getGameTime();
    }
    private void leafLoot(ServerLevel level,ServerPlayer player)throws Exception {
        player.setGameMode(GameType.SURVIVAL);player.getInventory().clearContent();var at=new BlockPos(-8,-20,0);level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);
        var samples=new ArrayList<Map<String,Object>>();proof.put("leafDrops",samples);
        for(int variant=0;variant<2;variant++){
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(3)))entity.discard();
            int meta=variant;
            for(int i=0;i<4096;i++){
                level.setBlock(at,state("Thaumcraft:blockMagicalLeaves",8|meta),2);
                check(player.gameMode.destroyBlock(at),"Survival player did not destroy legacy leaves");
            }
            int count=0;
            for(var entity:level.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(3))){
                var drop=content.gameplayStack(entity.getItem());check(drop!=null&&drop.key().equals("Thaumcraft:blockCustomPlant")&&drop.metadata()==meta,"Wrong leaf drop: "+drop);count+=drop.count();entity.discard();
            }
            check(count>0,"No saplings from 4096 original leaf drops: meta="+meta);samples.add(Map.of("leafVariant",meta,"broken",4096,"saplings",count));
        }
        for(int i=0;i<100;i++){level.setBlock(at,state("Thaumcraft:blockMagicalLeaves",13),2);player.gameMode.destroyBlock(at);}
        check(level.getEntitiesOfClass(ItemEntity.class,new AABB(at).inflate(3)).isEmpty(),"Player-placed persistent leaves farmed saplings");proof.put("persistentLeavesNoSaplings",true);
        var log=at.south(4);decay=log.east();level.setBlock(log,state("Thaumcraft:blockMagicalLog",1),2);level.setBlock(decay,state("Thaumcraft:blockMagicalLeaves",1),2);
        player.gameMode.destroyBlock(log);check((level.getBlockState(decay).getValue(ModernContentBridge.METADATA)&8)!=0,"Removing wood did not notify old leaves");proof.put("logRemovalMarksLeavesForDecay",true);
    }
    private boolean observe(Minecraft mc){
        var level=mc.getSingleplayerServer().overworld();
        if(!proof.containsKey("soundEventsSent")&&level.getGameTime()-started>40){
            level.levelEvent(null,2001,new BlockPos(16,51,9),net.minecraft.world.level.block.Block.getId(state("Thaumcraft:blockMagicalLog",1)));
            level.levelEvent(null,2001,new BlockPos(17,51,9),net.minecraft.world.level.block.Block.getId(state("Thaumcraft:blockMagicalLeaves",1)));proof.put("soundEventsSent",true);
        }
        proof.put("saplingStatus",saplings.stream().map(at->Map.of("position",at.toShortString(),"block",String.valueOf(content.legacyBlock(level.getBlockState(at))),"light",level.getMaxLocalRawBrightness(at.above()))).toList());
        check(level.getGameTime()-started<3600,"Trees did not grow at all tested heights");
        for(var at:saplings){var block=content.legacyBlock(level.getBlockState(at));if(block==null||!block.legacyKey().equals("Thaumcraft:blockMagicalLog"))return false;}
        if(!level.isEmptyBlock(decay))return false;
        proof.put("naturalSaplingGrowth",saplings.stream().map(BlockPos::toShortString).toList());proof.put("unsupportedLeavesNaturallyDecay",true);proof.put("growthServerTicks",level.getGameTime()-started);
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());return true;
    }
    private CompletableFuture<Boolean> server(Minecraft mc,java.util.concurrent.Callable<Boolean> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception e){throw new IllegalStateException(e);}});}
    private void finish(Minecraft mc)throws Exception {
        stage=99;var file=mc.gameDirectory.toPath().resolve("logs/mfcl/tree-smoke.json");Files.createDirectories(file.getParent());Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        com.mojang.logging.LogUtils.getLogger().info("MFCL_TREE_SMOKE {}",proof);if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
