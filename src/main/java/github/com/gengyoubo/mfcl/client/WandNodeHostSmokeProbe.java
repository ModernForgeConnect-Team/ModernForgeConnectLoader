package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import github.com.gengyoubo.mfcl.content.LegacyBlockEntity;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Actual client use-on/held packets and natural use ticks, including nodes concealed by silverwood. */
final class WandNodeHostSmokeProbe {
    private static final BlockPos NODE=new BlockPos(0,101,4);
    private static final String[] CASES={"coveredSilverwood","exposedSilverwood","floatingNode"};
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final List<Map<String,Object>> results=new ArrayList<>();
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
    private CompletableFuture<?> pending;
    private int stage,frames,fixture,holdTick;
    private Object aspects,air;
    private Map<String,Object> row;
    private WandNodeHostSmokeProbe(ModernContentBridge content){this.content=content;proof.put("cases",results);}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new WandNodeHostSmokeProbe(content)::tick);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            check(System.nanoTime()<deadline,"Wand node probe timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-wand-node-"+System.currentTimeMillis(),
                        new LevelSettings("MFCL wand nodes",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),
                        access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.screen==null){pending=server(mc,()->{setup(mc);return null;});stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&ready(mc)&&++frames>=30){
                pending.join();press(mc);holdTick=mc.player.tickCount;stage=3;
            }else if(stage==3&&mc.player.tickCount-holdTick>=70){
                row.put("clientUsingBeforeRelease",mc.player.isUsingItem());
                screenshot(mc,"wand-node-"+CASES[fixture]+".png");
                mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);stage=4;frames=0;
            }else if(stage==4&&++frames>=12){pending=server(mc,()->{observe(mc);return null;});stage=5;
            }else if(stage==5&&pending.isDone()){
                pending.join();
                if(++fixture<CASES.length){pending=server(mc,()->{setup(mc);return null;});stage=2;frames=0;}
                else {
                    check((int)results.get(0).get("wandAir")==0,"Wand charged through solid covering wood");
                    for(int i=1;i<results.size();i++){
                        var result=results.get(i);int vis=(int)result.get("wandAir"),left=(int)result.get("nodeAir");
                        check(vis>0,"No natural client charge for "+CASES[i]+": "+result);
                        check(vis+left*100==2000,"Vis not conserved for "+CASES[i]);
                        check((boolean)result.get("released"),"Release retained the node target for "+CASES[i]);
                    }
                    proof.put("status","PASS");finish(mc);
                }
            }
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL wand node acceptance failed",failure);try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}}
    }
    private void setup(Minecraft mc)throws Exception {
        var level=mc.getSingleplayerServer().overworld();var player=level.players().get(0);
        level.setDayTime(6000);level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,mc.getSingleplayerServer());
        for(int x=-2;x<=2;x++)for(int z=-1;z<=6;z++){
            level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());
            for(int y=100;y<=104;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(NODE,content.modernBlock(new HostWorldAccess.Block(fixture==2?"Thaumcraft:blockAiry":"Thaumcraft:blockMagicalLog",fixture==2?0:2)));
        if(fixture==0)level.setBlockAndUpdate(NODE.north(),content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockMagicalLog",1)));
        var services=content.services();var loader=services.loader();
        services.items().inLegacy(()->{
            var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");air=aspectType.getMethod("getAspect",String.class).invoke(null,"aer");
            aspects=loader.loadClass("thaumcraft.api.aspects.AspectList").getConstructor().newInstance();aspects.getClass().getMethod("add",aspectType,int.class).invoke(aspects,air,20);
            var tile=(LegacyBlockEntity)level.getBlockEntity(NODE);Object original=tile.legacy();original.getClass().getMethod("setAspects",aspects.getClass()).invoke(original,aspects);
            var nodeType=loader.loadClass("thaumcraft.api.nodes.NodeType");original.getClass().getMethod("setNodeType",nodeType).invoke(original,nodeType.getField(fixture==2?"NORMAL":"PURE").get(null));
            tile.synchronize();return null;
        });
        player.getInventory().clearContent();player.getInventory().selected=0;player.releaseUsingItem();
        player.setItemInHand(InteractionHand.MAIN_HAND,content.stack("Thaumcraft:WandCasting",0,1,"{rod:\"wood\",cap:\"iron\"}"));
        player.setItemSlot(EquipmentSlot.HEAD,content.stack("Thaumcraft:ItemGoggles",0,1,""));
        player.connection.teleport(.5,100,.5,0,(float)Math.toDegrees(Math.atan2(.12,4)));player.inventoryMenu.broadcastChanges();
        row=new LinkedHashMap<>();row.put("fixture",CASES[fixture]);results.add(row);
    }
    private boolean ready(Minecraft mc){
        if(mc.level==null||mc.player==null||mc.screen!=null||mc.player.getY()<99.9||mc.player.getY()>100.1)return false;
        if(!mc.player.getMainHandItem().is(content.items().get("Thaumcraft:WandCasting")))return false;
        var block=content.legacyBlock(mc.level.getBlockState(NODE));
        if(block==null||!block.legacyKey().equals(fixture==2?"Thaumcraft:blockAiry":"Thaumcraft:blockMagicalLog"))return false;
        var front=content.legacyBlock(mc.level.getBlockState(NODE.north()));
        return fixture==0?front!=null&&front.legacyKey().equals("Thaumcraft:blockMagicalLog"):mc.level.isEmptyBlock(NODE.north());
    }
    private void press(Minecraft mc){
        var hit=mc.hitResult;row.put("nativeHit",hit==null?"null":hit.getType().toString());
        if(hit instanceof BlockHitResult block)row.put("nativeHitPosition",block.getBlockPos().toShortString());
        InteractionResult result=InteractionResult.PASS;
        if(hit instanceof BlockHitResult block&&hit.getType()==HitResult.Type.BLOCK)result=mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,block);
        row.put("nativeBlockUse",result.toString());
        if(!result.consumesAction())row.put("nativeHeldUse",mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND).toString());
        mc.options.keyUse.setDown(true);
    }
    private void observe(Minecraft mc)throws Exception {
        var level=mc.getSingleplayerServer().overworld();var player=level.players().get(0);var services=content.services();var loader=services.loader();
        services.items().inLegacy(()->{
            var stackType=loader.loadClass("net.minecraft.item.ItemStack");var aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
            var snapshot=content.gameplayStack(player.getMainHandItem());Object old=services.items().materialize(snapshot),wand=stackType.getMethod("func_77973_b").invoke(old);
            row.put("wandAir",wand.getClass().getMethod("getVis",stackType,aspectType).invoke(wand,old,air));
            row.put("nodeAir",aspects.getClass().getMethod("getAmount",aspectType).invoke(aspects,air));row.put("released",!player.isUsingItem()&&!snapshot.tag().contains("IIUX"));return null;
        });
    }
    private void screenshot(Minecraft mc,String name)throws Exception {
        var file=mc.gameDirectory.toPath().resolve("screenshots/"+name);Files.createDirectories(file.getParent());try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(file);}
    }
    private CompletableFuture<?> server(Minecraft mc,java.util.concurrent.Callable<?> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private void finish(Minecraft mc)throws Exception {
        stage=99;mc.options.keyUse.setDown(false);var file=mc.gameDirectory.toPath().resolve("logs/mfcl/wand-node-smoke.json");Files.createDirectories(file.getParent());
        Files.writeString(file,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));com.mojang.logging.LogUtils.getLogger().info("MFCL_WAND_NODE_SMOKE {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
