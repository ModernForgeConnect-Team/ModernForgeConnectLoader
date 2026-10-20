package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Hosted original entities end to end: a real held fire-focus wand spawns original embers on the server, they hit a
 * host pig through the mirror damage hooks, and the client rebuilds and draws them with the original renderer.
 */
final class FireFocusHostProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> report=new LinkedHashMap<>();
    private int stage,frames,serverHosted,clientHosted,clientRestored,primitives;
    private UUID pig;private float pigHealth;private boolean sawBurning;private final List<String> pigSamples=new ArrayList<>();
    private CompletableFuture<?> pending;
    private final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(4);
    private FireFocusHostProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new FireFocusHostProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try{
            if(System.nanoTime()>deadline)throw new IllegalStateException("Fire focus probe timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);stage=1;
                mc.createWorldOpenFlows().createFreshLevel("mfcl-fire-focus-"+System.currentTimeMillis(),new LevelSettings("Fire focus",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null&&++frames>20){
                pending=server(mc,()->{prepare(mc);return true;});stage=2;frames=0;
            }else if(stage==2&&pending.isDone()){pending.join();pending=server(mc,()->{use(mc);return true;});stage=3;frames=0;
            }else if(stage==3&&pending.isDone()){
                pending.join();observeClient(mc);
                if(frames%5==0)server(mc,()->{var level=mc.getSingleplayerServer().overworld();serverHosted=Math.max(serverHosted,hosted(level));
                    if(level.getEntity(pig) instanceof net.minecraft.world.entity.LivingEntity living){sawBurning|=living.isOnFire();pigSamples.add(living.getHealth()+(living.isOnFire()?"F":""));}
                    else pigSamples.add("gone");return true;}).join();
                if(++frames>80){pending=server(mc,()->{verify(mc);return true;});stage=4;}
            }else if(stage==4&&pending.isDone()){pending.join();report.put("status","PASS");finish(mc);}
        }catch(Throwable failure){report.put("status","FAIL");report.put("failure",String.valueOf(failure));report.put("serverHosted",serverHosted);report.put("clientHosted",clientHosted);
            report.put("clientRestored",clientRestored);report.put("renderPrimitives",primitives);
            com.mojang.logging.LogUtils.getLogger().error("MFCL fire focus probe failed",failure);try{finish(mc);}catch(Exception write){throw new IllegalStateException(write);}}
    }
    private <T> CompletableFuture<T> server(Minecraft mc,java.util.concurrent.Callable<T> action){return mc.getSingleplayerServer().submit(()->{try{return action.call();}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static int hosted(Level level){
        int count=0;for(var entity:level instanceof net.minecraft.server.level.ServerLevel server?server.getAllEntities():((net.minecraft.client.multiplayer.ClientLevel)level).entitiesForRendering())if(entity instanceof LegacyHostedEntity)count++;return count;
    }
    private void prepare(Minecraft mc)throws Exception{
        var server=mc.getSingleplayerServer();var level=server.overworld();var player=server.getPlayerList().getPlayers().get(0);
        player.getInventory().clearContent();
        var services=content.services();var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        // A wood/iron wand holding the original fire focus and full primal vis, built through the wand's own API.
        var wand=services.items().inLegacy(()->{
            Object focus=null;
            for(var candidate:services.items().catalog()){
                Object old=services.items().materialize(candidate),item=stackType.getMethod("func_77973_b").invoke(old);
                if(loader.loadClass("thaumcraft.common.items.wands.foci.ItemFocusFire").isInstance(item)){focus=old;break;}
            }
            check(focus!=null,"No fire focus in the original catalog");
            Object old=services.items().materialize(content.gameplayStack(content.stack("Thaumcraft:WandCasting",0,1,"{rod:\"wood\",cap:\"iron\"}")));
            Object item=stackType.getMethod("func_77973_b").invoke(old);
            item.getClass().getMethod("setFocus",stackType,stackType).invoke(item,old,focus);
            var aspect=loader.loadClass("thaumcraft.api.aspects.Aspect");var list=loader.loadClass("thaumcraft.api.aspects.AspectList");Object vis=list.getConstructor().newInstance();
            int max=(int)item.getClass().getMethod("getMaxVis",stackType).invoke(item,old);
            for(Object primal:(List<?>)aspect.getMethod("getPrimalAspects").invoke(null))list.getMethod("add",aspect,int.class).invoke(vis,primal,max);
            item.getClass().getMethod("storeAllVis",stackType,list).invoke(item,old,vis);
            return services.items().snapshot(old);
        });
        player.getInventory().selected=0;player.setItemInHand(InteractionHand.MAIN_HAND,content.stack(wand.key(),wand.metadata(),1,wand.tag()));player.inventoryMenu.broadcastChanges();
        int ground=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,0,0);
        // Facing +Z (yaw 0) and looking down at a pig three blocks ahead, so the ember stream lands on it.
        player.connection.teleport(0.5,ground,0.5,0,20);
        var target=EntityType.PIG.create(level);target.moveTo(0.5,ground,3.5,180,0);target.setNoAi(true);level.addFreshEntity(target);
        pig=target.getUUID();pigHealth=target.getHealth();
        report.put("wand",wand.key()+" "+wand.tag());
    }
    private void use(Minecraft mc){
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        var result=player.getMainHandItem().use(player.level(),player,InteractionHand.MAIN_HAND);
        check(player.isUsingItem(),"The wand did not start its held use: "+result.getResult());
        report.put("heldUse",true);
    }
    private void observeClient(Minecraft mc)throws Exception{
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof LegacyHostedEntity hosted){
            clientHosted=Math.max(clientHosted,hosted(mc.level));
            Object original=hosted.legacyOrNull();if(original==null)continue;clientRestored++;
            if(primitives==0){
                var world=content.services().worlds().findBound(mc.level);var eye=mc.gameRenderer.getMainCamera().getPosition();
                content.blockRendering().tiles().view(world,eye.x,eye.y,eye.z,0,20,mc.player.tickCount);
                primitives=content.services().entityHosting().render(original,hosted.getX()-eye.x,hosted.getY()-eye.y,hosted.getZ()-eye.z,hosted.getYRot(),0).size();
                report.put("renderedType",original.getClass().getName());
            }
        }
    }
    private void verify(Minecraft mc){
        var server=mc.getSingleplayerServer();var player=server.getPlayerList().getPlayers().get(0);player.stopUsingItem();
        var target=server.overworld().getEntity(pig);
        boolean hurt=target==null||!target.isAlive()||((net.minecraft.world.entity.LivingEntity)target).getHealth()<pigHealth;
        boolean burning=sawBurning;report.put("pigSamples",pigSamples);
        report.put("serverHosted",serverHosted);report.put("clientHosted",clientHosted);report.put("clientRestored",clientRestored);report.put("renderPrimitives",primitives);
        report.put("pigHurt",hurt);report.put("pigBurning",burning);
        check(serverHosted>0,"No original ember was hosted on the server");
        check(clientRestored>0,"No hosted ember was rebuilt on the client");
        check(primitives>0,"The original ember renderer drew nothing");
        check(hurt,"Embers never damaged the host pig");
        check(burning,"Ember hits never set the host pig on fire");
    }
    private void finish(Minecraft mc)throws Exception{
        Path output=mc.gameDirectory.toPath().resolve("logs/mfcl/fire-focus-host-probe.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        com.mojang.logging.LogUtils.getLogger().info("MFCL_FIRE_FOCUS_PROBE: {}",report);stage=99;
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
