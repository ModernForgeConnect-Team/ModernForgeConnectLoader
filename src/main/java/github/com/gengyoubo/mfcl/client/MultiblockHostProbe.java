package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Builds each TC4 multiblock and forms it through the native use-on path, as a player holding the tool would. */
final class MultiblockHostProbe {
    private static final String WAND="{rod:\"silverwood\",cap:\"thaumium\",aer:9000,ignis:9000,aqua:9000,terra:9000,ordo:9000,perditio:9000}";
    private record Check(BlockPos pos, String key, int metadata) {}
    private final ModernContentBridge content;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private final Map<String,BlockPos> anchors=new LinkedHashMap<>();
    private int stage, frames;
    private CompletableFuture<?> pending;
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
    private MultiblockHostProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new MultiblockHostProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Multiblock probe timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);stage=1;
                mc.createWorldOpenFlows().createFreshLevel("mfcl-multiblock-"+System.currentTimeMillis(),new LevelSettings("Multiblock probe",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null){
                pending=mc.getSingleplayerServer().submit(()->{try{form(mc);}catch(Exception failure){throw new IllegalStateException(failure);}return true;});stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&++frames>40){
                pending.join();clientBounds(mc);report.putIfAbsent("status","DONE");finish(mc);
            }
        }catch(Throwable failure){report.put("status","FAIL");report.put("failure",failure.toString());try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}}
    }
    private BlockState legacy(String key,int metadata){return content.modernBlock(new HostWorldAccess.Block(key,metadata));}
    private void form(Minecraft mc)throws Exception{
        var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        grantResearch(level,player,"INFUSION","INFERNALFURNACE","NODEJAR","THAUMATORIUM","ADVALCHEMYFURNACE");
        var origin=player.blockPosition().above(3);
        attempt(level,player,"researchTable",origin,this::researchTable);
        attempt(level,player,"infusionAltar",origin.east(8),this::infusionAltar);
        attempt(level,player,"infernalFurnace",origin.east(16),this::infernalFurnace);
        attempt(level,player,"thaumatorium",origin.east(24),this::thaumatorium);
        attempt(level,player,"advancedAlchemyFurnace",origin.east(32),this::advancedFurnace);
        attempt(level,player,"nodeJar",origin.east(40),this::nodeJar);
    }
    @FunctionalInterface private interface Builder { List<Check> build(ServerLevel level, ServerPlayer player, BlockPos at) throws Exception; }
    private void attempt(ServerLevel level,ServerPlayer player,String name,BlockPos at,Builder builder){
        var entry=new LinkedHashMap<String,Object>();report.put(name,entry);anchors.put(name,at);
        try{
            var checks=builder.build(level,player,at);
            var wrong=new ArrayList<String>();
            for(var check:checks){var found=content.legacyBlock(level.getBlockState(check.pos()));
                if(!found.legacyKey().equals(check.key())||found.metadata()!=check.metadata())wrong.add(check.pos()+": expected "+check.key()+"@"+check.metadata()+", found "+found.legacyKey()+"@"+found.metadata());}
            entry.put("formed",wrong.isEmpty());if(!wrong.isEmpty())entry.put("mismatches",wrong);
        }catch(Throwable failure){entry.put("formed",false);entry.put("failure",failure.toString());
            com.mojang.logging.LogUtils.getLogger().error("MFCL multiblock probe {} failed",name,failure);}
    }
    private void click(ServerLevel level,ServerPlayer player,BlockPos pos,Direction face,net.minecraft.world.item.ItemStack held,Map<String,Object> entry){
        player.teleportTo(pos.getX()+.5,pos.getY()+1,pos.getZ()-2.5);player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,held);
        var result=player.gameMode.useItemOn(player,level,held,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos).relative(face,.5),face,pos,false));
        entry.put("useResult",result.toString());
    }
    @SuppressWarnings("unchecked") private Map<String,Object> entry(String name){return (Map<String,Object>)report.get(name);}
    private net.minecraft.world.item.ItemStack wand(){return content.stack("Thaumcraft:WandCasting",0,1,WAND);}
    private List<Check> researchTable(ServerLevel level,ServerPlayer player,BlockPos at){
        level.setBlockAndUpdate(at,legacy("Thaumcraft:blockTable",0));level.setBlockAndUpdate(at.east(),legacy("Thaumcraft:blockTable",0));
        click(level,player,at,Direction.UP,content.stack("Thaumcraft:ItemInkwell",0,1,null),entry("researchTable"));
        // ForgeDirection 5 (east) is the neighbour; the anchor takes that ordinal and the other half its opposite + 4.
        return List.of(new Check(at,"Thaumcraft:blockTable",5),new Check(at.east(),"Thaumcraft:blockTable",8));
    }
    private List<Check> infusionAltar(ServerLevel level,ServerPlayer player,BlockPos at){
        level.setBlockAndUpdate(at,legacy("Thaumcraft:blockStoneDevice",1));
        var corners=List.of(at.offset(-1,0,-1),at.offset(1,0,-1),at.offset(-1,0,1),at.offset(1,0,1));
        for(var corner:corners){level.setBlockAndUpdate(corner,legacy("Thaumcraft:blockCosmeticSolid",7));level.setBlockAndUpdate(corner.above(),legacy("Thaumcraft:blockCosmeticSolid",6));}
        var matrix=at.above(2);level.setBlockAndUpdate(matrix,legacy("Thaumcraft:blockStoneDevice",2));
        click(level,player,matrix,Direction.NORTH,wand(),entry("infusionAltar"));
        var checks=new ArrayList<Check>();
        for(var corner:corners){checks.add(new Check(corner,"Thaumcraft:blockStoneDevice",3));checks.add(new Check(corner.above(),"Thaumcraft:blockStoneDevice",4));}
        return checks;
    }
    private List<Check> infernalFurnace(ServerLevel level,ServerPlayer player,BlockPos at){
        for(int y=0;y<3;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var pos=at.offset(x,y,z);boolean corner=x!=0&&z!=0,center=x==0&&z==0;
            BlockState state=corner?Blocks.NETHER_BRICKS.defaultBlockState():Blocks.OBSIDIAN.defaultBlockState();
            if(center&&y==1)state=Blocks.LAVA.defaultBlockState();
            if(center&&y==2)state=Blocks.AIR.defaultBlockState();
            if(y==1&&x==0&&z==-1)state=Blocks.IRON_BARS.defaultBlockState();
            level.setBlock(pos,state,2);
        }
        click(level,player,at.offset(-1,1,-1),Direction.NORTH,wand(),entry("infernalFurnace"));
        return List.of(new Check(at.above(),"Thaumcraft:blockArcaneFurnace",0));
    }
    private List<Check> thaumatorium(ServerLevel level,ServerPlayer player,BlockPos at){
        level.setBlockAndUpdate(at,legacy("Thaumcraft:blockMetalDevice",0));
        level.setBlockAndUpdate(at.above(),legacy("Thaumcraft:blockMetalDevice",9));level.setBlockAndUpdate(at.above(2),legacy("Thaumcraft:blockMetalDevice",9));
        click(level,player,at.above(),Direction.NORTH,wand(),entry("thaumatorium"));
        return List.of(new Check(at,"Thaumcraft:blockMetalDevice",0),new Check(at.above(),"Thaumcraft:blockMetalDevice",10),new Check(at.above(2),"Thaumcraft:blockMetalDevice",11));
    }
    private List<Check> advancedFurnace(ServerLevel level,ServerPlayer player,BlockPos at){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            boolean corner=x!=0&&z!=0,center=x==0&&z==0;
            level.setBlockAndUpdate(at.offset(x,0,z),center?legacy("Thaumcraft:blockStoneDevice",0):legacy("Thaumcraft:blockMetalDevice",3));
            if(!center)level.setBlockAndUpdate(at.offset(x,1,z),legacy("Thaumcraft:blockMetalDevice",corner?1:9));
        }
        click(level,player,at.offset(0,0,-1),Direction.NORTH,wand(),entry("advancedAlchemyFurnace"));
        return List.of(new Check(at,"Thaumcraft:blockAlchemyFurnace",0),new Check(at.offset(-1,0,0),"Thaumcraft:blockAlchemyFurnace",1),new Check(at.offset(-1,1,-1),"Thaumcraft:blockAlchemyFurnace",2));
    }
    private List<Check> nodeJar(ServerLevel level,ServerPlayer player,BlockPos at)throws Exception{
        for(int y=0;y<4;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var pos=at.offset(x,y,z);
            if(y==1&&x==0&&z==0)continue;
            level.setBlockAndUpdate(pos,y==3?Blocks.OAK_SLAB.defaultBlockState():Blocks.GLASS.defaultBlockState());
        }
        var node=at.above();level.setBlockAndUpdate(node,legacy("Thaumcraft:blockAiry",0));
        var services=content.services();var loader=services.loader();Object tile=((LegacyBlockEntity)level.getBlockEntity(node)).legacy();
        var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");Object air=aspectType.getMethod("getAspect",String.class).invoke(null,"aer");
        Object aspects=loader.loadClass("thaumcraft.api.aspects.AspectList").getConstructor().newInstance();aspects.getClass().getMethod("add",aspectType,int.class).invoke(aspects,air,20);
        tile.getClass().getMethod("setAspects",aspects.getClass()).invoke(tile,aspects);
        // createNodeJar searches 0..3 cells below the clicked block for the slab lid; the bottom glass layer is out of reach.
        click(level,player,at.offset(0,2,-1),Direction.NORTH,wand(),entry("nodeJar"));
        return List.of(new Check(node,"Thaumcraft:blockJar",2));
    }
    private void grantResearch(ServerLevel level,ServerPlayer player,String... keys)throws Exception{
        var services=content.services();var loader=services.loader();Object world=services.worlds().bound(level);
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){
            Object actor=content.itemUse().clientActor(world,new LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0),null);
            Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null),knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
            @SuppressWarnings("unchecked") var completed=(Map<String,ArrayList<String>>)knowledge.getClass().getField("researchCompleted").get(knowledge);
            var learned=completed.computeIfAbsent(player.getName().getString(),ignored->new ArrayList<>());
            for(String key:keys)if(!learned.contains(key))learned.add(key);
            player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(actor));return null;
        }});
    }
    /** The client copy must declare the original multi-cell render box, or the model disappears with its anchor cell. */
    private void clientBounds(Minecraft mc){
        var bounds=new LinkedHashMap<String,Object>();
        for(var entry:Map.of("researchTable",BlockPos.ZERO,"infusionAltar",new BlockPos(-1,0,-1),"thaumatorium",new BlockPos(0,1,0),"advancedAlchemyFurnace",BlockPos.ZERO).entrySet()){
            var anchor=anchors.get(entry.getKey());if(anchor==null)continue;var pos=anchor.offset(entry.getValue());
            if(mc.level.getBlockEntity(pos) instanceof LegacyBlockEntity tile){var box=tile.getRenderBoundingBox();
                bounds.put(entry.getKey(),String.format(Locale.ROOT,"%.1f x %.1f x %.1f",box.getXsize(),box.getYsize(),box.getZsize()));}
            else bounds.put(entry.getKey(),"no client tile at "+pos);
        }
        report.put("clientRenderBounds",bounds);
    }
    private void finish(Minecraft mc)throws Exception{
        Path output=mc.gameDirectory.toPath().resolve("logs/mfcl/multiblock-host-probe.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        com.mojang.logging.LogUtils.getLogger().info("MFCL_MULTIBLOCK_PROBE: {}",report);stage=99;
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
