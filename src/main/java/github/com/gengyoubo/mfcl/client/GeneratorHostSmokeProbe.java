package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Disposable real client/server acceptance for original Extra Utilities generator GUI, fuel and sound. */
final class GeneratorHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(4);
    private CompletableFuture<?> pending;
    private int stage,frames;
    private BlockPos position;
    private GeneratorHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){EnergyHostAcceptance.install();net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(new GeneratorHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||stage==99)return;var mc=Minecraft.getInstance();
        try {
            if(pending!=null&&pending.isCompletedExceptionally())pending.join();
            if(System.nanoTime()>deadline)throw new IllegalStateException("Generator acceptance timed out at "+stage);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-generator-"+System.currentTimeMillis(),new LevelSettings("MFCL generator acceptance",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());stage=1;
            }else if(stage==1&&mc.player!=null&&mc.level!=null&&mc.screen==null) {
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();
                    position=player.blockPosition().offset(2,0,0);
                    level.setBlock(position,content.blocks().get("ExtraUtilities:generator").defaultBlockState().setValue(ModernContentBridge.METADATA,0),3);
                    player.getInventory().setItem(0,new ItemStack(Items.COAL,3));return true;
                });stage=2;frames=0;
            }else if(stage==2&&pending.isDone()&&++frames>20) {
                if(!(mc.level.getBlockEntity(position) instanceof LegacyBlockEntity tile)||tile.logicSuspended())throw new IllegalStateException("Client generator tick was paused");
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();
                    if(!level.getBlockState(position).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(position),Direction.UP,position,false)).consumesAction()||!(player.containerMenu instanceof LegacyDeviceMenu))throw new IllegalStateException("Generator did not open original menu");
                    return true;
                });stage=3;frames=0;
            }else if(stage==3&&pending.isDone()&&mc.screen instanceof LegacyDeviceScreen screen&&++frames>15) {
                Object container=screen.originalGui().getClass().getField("field_147002_h").get(screen.originalGui());
                if(widget(container,"curMax")<=0||widget(container,"curEnergy")!=0)throw new IllegalStateException("Initial generator widget description was lost");
                proof.put("initialWidgetSync",true);
                var slot=screen.getMenu().slots.stream().filter(value->value.getItem().is(Items.COAL)).findFirst().orElseThrow();
                mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId,slot.index,0,ClickType.PICKUP,mc.player);stage=4;frames=0;
            }else if(stage==4&&++frames>8) {
                if(!mc.player.containerMenu.getCarried().is(Items.COAL))throw new IllegalStateException("Original inventory pickup failed");
                mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,0,0,ClickType.PICKUP,mc.player);stage=5;frames=0;
            }else if(stage==5&&++frames>45) {
                if(!(mc.screen instanceof LegacyDeviceScreen screen))throw new IllegalStateException("Generator menu closed unexpectedly");
                Object container=screen.originalGui().getClass().getField("field_147002_h").get(screen.originalGui());int energy=widget(container,"curEnergy");
                if(energy<=0)throw new IllegalStateException("Live generated energy did not reach GUI");
                proof.put("clientEnergy",energy);proof.put("fuelSlotClicks",true);proof.put("liveWidgetSync",true);
                if(((LegacyBlockEntity)mc.level.getBlockEntity(position)).logicSuspended())throw new IllegalStateException("Client generator logic was paused");
                var clientEnergy=mc.level.getBlockEntity(position).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY,null).orElseThrow(IllegalStateException::new);
                int before=clientEnergy.getEnergyStored();
                if(clientEnergy.extractEnergy(1,false)!=0||clientEnergy.receiveEnergy(1,false)!=0||clientEnergy.getEnergyStored()!=before)throw new IllegalStateException("Client mutated authoritative RF energy");
                proof.put("clientEnergyReadOnly",true);
                pending=mc.getSingleplayerServer().submit(()->{
                    var tile=(LegacyBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(position);
                    if(tile.logicSuspended()||tile.successfulTicks()==0)throw new IllegalStateException("Server generator logic was paused");
                    proof.put("serverTicks",tile.successfulTicks());
                    try{proof.put("energyBridge",EnergyHostAcceptance.verify(content,mc.getSingleplayerServer().overworld(),position));}
                    catch(Exception failure){throw new IllegalStateException("Energy bridge acceptance failed",failure);}
                    return true;
                });stage=6;
            }else if(stage==6&&pending.isDone()) {
                pending.join();proof.put("clientAndServerTicks",true);mc.player.closeContainer();mc.level.disconnect();mc.clearLevel();stage=7;frames=0;
            }else if(stage==7&&++frames>8) {
                var loader=content.services().loader();var oldMc=loader.loadClass("net.minecraft.client.Minecraft");Object old=oldMc.getMethod("func_71410_x").invoke(null);
                Object handler=oldMc.getMethod("func_147118_V").invoke(old);var f=handler.getClass().getDeclaredField("field_147694_f");f.setAccessible(true);Object manager=f.get(handler);
                var map=manager.getClass().getDeclaredField("field_148629_h");map.setAccessible(true);
                if(!((Map<?,?>)map.get(manager)).isEmpty())throw new IllegalStateException("Generator sounds survived world unload");
                proof.put("soundCleanupOnUnload",true);proof.put("status","PASS");finish(mc,null);
            }
        }catch(Throwable failure){finish(mc,failure);}
    }
    private int widget(Object container,String name) throws Exception {
        for(Object widget:(List<?>)container.getClass().getField("widgets").get(container))if(widget.getClass().getName().endsWith(".WidgetEnergy")) {
            var field=widget.getClass().getDeclaredField(name);field.setAccessible(true);return field.getInt(widget);
        }
        throw new IllegalStateException("Missing original generator energy widget");
    }
    private void finish(Minecraft mc,Throwable failure) {
        if(failure!=null){proof.put("status","FAIL");proof.put("stage",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_GENERATOR_SMOKE_FAIL",failure);}
        stage=99;
        try{var output=mc.gameDirectory.toPath().resolve("logs/mfcl/generator-smoke.json");java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_GENERATOR_SMOKE_RESULT {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
