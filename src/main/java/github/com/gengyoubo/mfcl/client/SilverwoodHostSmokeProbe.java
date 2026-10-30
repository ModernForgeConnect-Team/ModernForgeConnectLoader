package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Natural engine random ticks, generated node sync and original TESR captures in a disposable client world. */
final class SilverwoodHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final List<BlockPos> saplings=new ArrayList<>();
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<BlockPos> pending;
    private BlockPos node;
    private String expectedNodeType="PURE";
    private long started;
    private int stage,frames;
    private SilverwoodHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new SilverwoodHostSmokeProbe(content)::tick);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;
        var mc=Minecraft.getInstance();
        try {
            check(System.nanoTime()<deadline,"Silverwood probe timed out at stage "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(4);mc.options.hideGui=true;
                mc.createWorldOpenFlows().createFreshLevel("mfcl-silverwood-"+System.currentTimeMillis(),
                        new LevelSettings("MFCL silverwood",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),
                        access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                stage=1;
            }else if(stage==1&&mc.player!=null&&mc.screen==null){
                pending=server(mc,()->{setup(mc);return null;});stage=2;
            }else if(stage==2){
                if(pending!=null){
                    if(!pending.isDone())return;
                    node=pending.join();pending=null;
                    if(node!=null){stage=3;frames=0;return;}
                }
                if(++frames%10==0)pending=server(mc,()->observeGrowth(mc));
            }else if(stage==3&&clientReady(mc,false)&&++frames>=40){
                proof.put("bareNodeRendering",capture(mc));screenshot(mc,"silverwood-node-bare.png");
                pending=server(mc,()->{hold(mc,"Thaumcraft:ItemThaumometer");return node;});stage=4;frames=0;
            }else if(stage==4&&pending.isDone()&&clientReady(mc,false)&&holding(mc,"Thaumcraft:ItemThaumometer")&&++frames>=40){
                pending.join();var drawing=capture(mc);revealed(drawing,"Thaumometer");
                proof.put("thaumometerNodeRendering",drawing);screenshot(mc,"silverwood-node-thaumometer.png");
                pending=server(mc,()->{hold(mc,"minecraft:netherite_sword");return node;});stage=5;frames=0;
            }else if(stage==5&&pending.isDone()&&holding(mc,"minecraft:netherite_sword")&&++frames>=40){
                pending.join();var drawing=capture(mc);
                check(!(boolean)drawing.get("throughBlocks"),"Switching away left the old thaumometer selected");
                proof.put("switchingToModernItemHidesReveal",true);
                pending=server(mc,()->{hold(mc,null);equip(mc,true);return node;});stage=6;frames=0;
            }else if(stage==6&&pending.isDone()&&clientReady(mc,true)&&++frames>=40){
                pending.join();var drawing=capture(mc);
                revealed(drawing,"Revealing goggles");
                proof.put("gogglesNodeRendering",drawing);screenshot(mc,"silverwood-node-goggles.png");
                pending=server(mc,()->placeCreativeNode(mc));stage=7;frames=0;
            }else if(stage==7&&pending.isDone()){
                node=pending.join();stage=8;frames=0;
            }else if(stage==8&&clientReady(mc,true)&&++frames>=40){
                var drawing=capture(mc);revealed(drawing,"Creative node");
                proof.put("creativeNodeRendering",drawing);screenshot(mc,"creative-node-goggles.png");
                proof.put("status","PASS");finish(mc);
            }
        }catch(Throwable failure){
            proof.put("status","FAIL");proof.put("failure",failure.toString());
            com.mojang.logging.LogUtils.getLogger().error("MFCL silverwood acceptance failed",failure);
            try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}
        }
    }
    private void setup(Minecraft mc)throws Exception {
        var level=mc.getSingleplayerServer().overworld();var player=level.players().get(0);
        level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,mc.getSingleplayerServer());
        // Accelerate only the vanilla random-tick scheduler; never invoke growSilverTree/updateTick in this probe.
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(256,mc.getSingleplayerServer());
        var state=content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockCustomPlant",1));
        check(state.isRandomlyTicking(),"Silverwood sapling has no native random ticks");
        int enabled=0,disabled=0;
        for(var declaration:content.declarations().blocks()){
            var host=content.blocks().get(declaration.key()).defaultBlockState();
            check(host.isRandomlyTicking()==declaration.randomTicks(),"Random-tick flag changed for "+declaration.key());
            if(declaration.randomTicks())enabled++;else disabled++;
        }
        check(enabled>0&&disabled>0,"Missing positive/negative random-tick controls");
        proof.put("randomTickDeclarations",Map.of("enabled",enabled,"disabled",disabled));
        for(int i=0;i<8;i++){
            var base=new BlockPos((i%4)*16,100,(i/4)*16);saplings.add(base);
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)level.setBlock(base.offset(x,-1,z),Blocks.DIRT.defaultBlockState(),3);
            level.setBlock(base,state,3);
        }
        player.getInventory().clearContent();player.getAbilities().flying=true;player.onUpdateAbilities();
        player.connection.teleport(24.5,103,-10.5,0,0);player.inventoryMenu.broadcastChanges();started=level.getGameTime();
    }
    private BlockPos observeGrowth(Minecraft mc)throws Exception {
        var level=mc.getSingleplayerServer().overworld();
        check(level.getGameTime()-started<2400,"Natural silverwood growth produced no node within 2400 ticks");
        int trees=0;
        for(var base:saplings){
            var trunk=content.legacyBlock(level.getBlockState(base));
            if(trunk==null||!trunk.legacyKey().equals("Thaumcraft:blockMagicalLog"))continue;
            trees++;
            for(int y=1;y<16;y++){
                var at=base.above(y);var block=content.legacyBlock(level.getBlockState(at));
                if(block==null||!block.legacyKey().equals("Thaumcraft:blockMagicalLog")||block.metadata()!=2)continue;
                check(level.getBlockEntity(at) instanceof LegacyBlockEntity,"Generated node has no native tile");
                var tile=(LegacyBlockEntity)level.getBlockEntity(at);Object original=tile.legacy();
                check(original.getClass().getMethod("getNodeType").invoke(original).toString().equals("PURE"),"Generated node is not pure");
                level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
                proof.put("naturalGrowth",Map.of("node",at.toShortString(),"serverTicks",level.getGameTime()-started,"treesObserved",trees,"originalTile",original.getClass().getName()));
                tile.synchronize();
                var player=level.players().get(0);player.connection.teleport(at.getX()+.5,at.getY()+.5-player.getEyeHeight(),at.getZ()-3.5,0,0);
                equip(mc,false);return at;
            }
        }
        return null;
    }
    private void equip(Minecraft mc,boolean goggles){
        var player=mc.getSingleplayerServer().overworld().players().get(0);
        player.setItemSlot(EquipmentSlot.HEAD,goggles?content.stack("Thaumcraft:ItemGoggles",0,1,""):ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
    }
    private void hold(Minecraft mc,String key){
        var player=mc.getSingleplayerServer().overworld().players().get(0);player.getInventory().selected=0;
        player.getInventory().setItem(0,key==null?ItemStack.EMPTY:content.items().containsKey(key)?content.stack(key,0,1,""):
                new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(key))));player.inventoryMenu.broadcastChanges();
    }
    private boolean holding(Minecraft mc,String key){
        return mc.player!=null&&mc.player.getMainHandItem().is(content.items().containsKey(key)?content.items().get(key):net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(key)));
    }
    private static void revealed(Map<String,Object> drawing,String name){
        check((int)drawing.get("primitives")>1,name+" did not reveal node aspects");
        check((boolean)drawing.get("throughBlocks"),name+" did not reveal the node through blocks");
    }
    private BlockPos placeCreativeNode(Minecraft mc)throws Exception {
        var level=mc.getSingleplayerServer().overworld();var player=level.players().get(0);var at=node.east(10);
        level.setBlockAndUpdate(at.below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        hold(mc,"Thaumcraft:blockAiry");player.connection.teleport(at.getX()+.5,at.getY()+.5-player.getEyeHeight(),at.getZ()-3.5,0,0);
        var result=player.gameMode.useItemOn(player,level,player.getMainHandItem(),net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atBottomCenterOf(at),net.minecraft.core.Direction.UP,at.below(),false));
        check(result.consumesAction()&&level.getBlockEntity(at) instanceof LegacyBlockEntity,"Creative node item placement failed");
        var tile=(LegacyBlockEntity)level.getBlockEntity(at);Object original=tile.legacy();
        Object aspects=original.getClass().getMethod("getAspects").invoke(original);
        int vis=(int)aspects.getClass().getMethod("visSize").invoke(aspects);check(vis>0,"Creative node placement did not initialize aspects");
        expectedNodeType=original.getClass().getMethod("getNodeType").invoke(original).toString();
        proof.put("creativeNodePlacement",Map.of("nativeUseResult",result.toString(),"type",expectedNodeType,"vis",vis));
        tile.synchronize();hold(mc,null);return at;
    }
    private boolean clientReady(Minecraft mc,boolean goggles)throws Exception {
        if(mc.level==null||mc.player==null||!(mc.level.getBlockEntity(node) instanceof LegacyBlockEntity tile))return false;
        if(mc.player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()==goggles)return false;
        if(Math.abs(mc.player.getX()-node.getX()-.5)>.1||Math.abs(mc.player.getZ()-node.getZ()+3.5)>.1)return false;
        Object original=tile.legacy();Object aspects=original.getClass().getMethod("getAspects").invoke(original);
        return original.getClass().getMethod("getNodeType").invoke(original).toString().equals(expectedNodeType)&&(int)aspects.getClass().getMethod("visSize").invoke(aspects)>0;
    }
    private Map<String,Object> capture(Minecraft mc)throws Exception {
        var tile=(LegacyBlockEntity)mc.level.getBlockEntity(node);var renderer=content.blockRendering().tiles();
        var camera=mc.gameRenderer.getMainCamera();var eye=camera.getPosition();
        renderer.view(ClientWorldBridge.world(content.services()),eye.x,eye.y,eye.z,camera.getYRot(),camera.getXRot(),mc.player.tickCount,content.renderSnapshot(mc.player.getMainHandItem()));
        var primitives=renderer.capture(tile.legacy(),0,node.getX()-eye.x,node.getY()-eye.y,node.getZ()-eye.z);
        check(!primitives.isEmpty(),"Original node renderer produced no geometry");
        check(primitives.stream().anyMatch(p->p.texture().contains("nodes.png")),"Node texture missing from original renderer");
        return Map.of("primitives",primitives.size(),"vertices",primitives.stream().mapToInt(p->p.vertices().size()).sum(),
                "throughBlocks",primitives.stream().anyMatch(p->!p.state().depthTest()),"clientOriginalNbtBytes",tile.renderData().length);
    }
    private void screenshot(Minecraft mc,String name)throws Exception {
        var path=mc.gameDirectory.toPath().resolve("screenshots/"+name);Files.createDirectories(path.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
    }
    private CompletableFuture<BlockPos> server(Minecraft mc,java.util.concurrent.Callable<BlockPos> action){
        return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});
    }
    private void finish(Minecraft mc)throws Exception {
        stage=99;var path=mc.gameDirectory.toPath().resolve("logs/mfcl/silverwood-smoke.json");Files.createDirectories(path.getParent());
        Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        com.mojang.logging.LogUtils.getLogger().info("MFCL_SILVERWOOD_SMOKE {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
