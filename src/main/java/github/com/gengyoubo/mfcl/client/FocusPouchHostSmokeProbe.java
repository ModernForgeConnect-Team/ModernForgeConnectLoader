package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Disposable original TC4 pouch GUI and authoritative inventory acceptance. */
final class FocusPouchHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(8);
    private String world;
    private HoverHarnessHostAcceptance harness;
    private CompletableFuture<?> pending;
    private int stage,frames,reportedStage=-1;
    private boolean done;
    private FocusPouchHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(new FocusPouchHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||done)return;var mc=Minecraft.getInstance();
        try {
            if(reportedStage!=stage){reportedStage=stage;com.mojang.logging.LogUtils.getLogger().info("MFCL_POUCH_STAGE {}",stage);}
            if(System.nanoTime()>deadline)throw new IllegalStateException("Pouch acceptance timed out at "+stage);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(++frames<12)return;
            if(stage==0) {
                if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
                world="mfcl-pouch-"+System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(world,new LevelSettings("MFCL pouch acceptance",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            }else if(stage==1) {
                if(mc.player==null||mc.screen!=null)return;
                pending=mc.getSingleplayerServer().submit(()->{
                    var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    player.getInventory().clearContent();player.getInventory().selected=0;
                    player.getInventory().setItem(0,content.stack("Thaumcraft:FocusPouch",0,1,""));
                    player.getInventory().setItem(1,content.stack("Thaumcraft:FocusFire",0,1,""));
                    player.getInventory().setItem(2,new net.minecraft.world.item.ItemStack(Items.STICK));
                    player.inventoryMenu.broadcastChanges();
                });
            }else if(stage==2) {
                mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }else if(stage==3) {
                if(!(mc.screen instanceof LegacyDeviceScreen screen)||!screen.getMenu().receivedContents())return;
                if(!screen.originalGui().getClass().getSimpleName().equals("GuiFocusPouch"))throw new IllegalStateException("Unexpected screen");
                var commands=ClientContainerBridge.active().commands(screen);
                if(commands.stream().noneMatch(command->command instanceof github.com.gengyoubo.mfcl.bridge.LegacyGuiGraphics.Texture texture&&texture.resource().endsWith("gui_focuspouch.png")))throw new IllegalStateException("Original pouch background was not captured");
                proof.put("originalGuiRendered",true);proof.put("commands",commands.size());
                var path=mc.gameDirectory.toPath().resolve("screenshots/focus-pouch.png");java.nio.file.Files.createDirectories(path.getParent());
                try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
                // Hold the disposable server for 600 ms. Both local clicks must finish before it can acknowledge them.
                pending=mc.getSingleplayerServer().submit(()->{try{Thread.sleep(600);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}});
                long start=System.nanoTime();
                click(mc,46,0,ClickType.PICKUP);require(!mc.player.containerMenu.getCarried().isEmpty(),"Pickup did not predict immediately");
                click(mc,0,0,ClickType.PICKUP);require(mc.player.containerMenu.getCarried().isEmpty()&&!mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Insert did not predict immediately");
                require(!pending.isDone(),"Server delay ended before prediction was checked");
                proof.put("simulatedServerStallMs",600);proof.put("localPickupAndInsertMs",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start));
            }else if(stage==4) {
                proof.put("pickupAndInsertBeforeServerAcknowledgement",true);
            }else if(stage==5) {
                server(mc,player->{
                    require(stored(player.getInventory().getItem(0))==1,"Pouch did not persist before close");
                    var menu=(LegacyDeviceMenu)player.containerMenu;
                    Object actor=menu.originalPlayer();var services=content.services();
                    Object inventory=services.loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(actor);
                    Object held=((Object[])inventory.getClass().getField("field_70462_a").get(inventory))[0];
                    for(int index=0;index<10;index++)menu.broadcastChanges();
                    require(held==((Object[])inventory.getClass().getField("field_70462_a").get(inventory))[0],"Idle inventory sync detached pouch");
                    proof.put("committedBeforeCloseAndIdentityRetained",true);
                });
            }else if(stage==6) {
                click(mc,47,0,ClickType.PICKUP);click(mc,1,0,ClickType.PICKUP);
                require(mc.player.containerMenu.getCarried().is(Items.STICK)&&mc.player.containerMenu.getSlot(1).getItem().isEmpty(),"Pouch accepted a non-focus");
                click(mc,47,0,ClickType.PICKUP);proof.put("nonFocusRejected",true);
                click(mc,46,0,ClickType.SWAP);require(content.renderSnapshot(mc.player.containerMenu.getSlot(45).getItem()).key().equals("Thaumcraft:FocusPouch"),"Number key moved the opening pouch");
            }else if(stage==7) {
                click(mc,0,0,ClickType.QUICK_MOVE);require(mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Shift removal did not predict");
            }else if(stage==8) {
                int focus=-1;for(int index=18;index<54;index++){var value=content.renderSnapshot(mc.player.containerMenu.getSlot(index).getItem());if(value!=null&&value.key().equals("Thaumcraft:FocusFire")){require(focus==-1,"Duplicate focus after shift click");focus=index;}}
                require(focus>=0,"Shift removal lost focus");click(mc,focus,0,ClickType.QUICK_MOVE);
                require(!mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Shift insertion did not predict");proof.put("shiftTransferBothDirections",true);
            }else if(stage==9) {
                server(mc,player->{
                    require(stored(player.getInventory().getItem(0))==1,"Server shift result diverged");
                    require(player.getInventory().getItem(1).isEmpty()&&player.getInventory().getItem(2).is(Items.STICK),"Server slot restriction diverged");
                    var opening=player.getInventory().getItem(0);player.getInventory().setItem(3,opening);
                    player.getInventory().setItem(0,content.stack("Thaumcraft:FocusPouch",0,1,""));
                });
            }else if(stage==10) {
                if(mc.screen!=null)return;
                server(mc,player->{
                    require(stored(player.getInventory().getItem(0))==0&&stored(player.getInventory().getItem(3))==1,"Stale menu rewrote replacement pouch");
                    proof.put("replacedPouchClosesWithoutRecreation",true);
                });
            }else if(stage==11) {
                mc.player.getInventory().selected=3;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }else if(stage==12) {
                if(!(mc.screen instanceof LegacyDeviceScreen screen)||!screen.getMenu().receivedContents())return;
                require(!screen.getMenu().getSlot(0).getItem().isEmpty(),"Reopened pouch lost contents");
                ClientContainerBridge.active().commands(screen);proof.put("reopenRetainedContents",true);
                // A selected-slot change may precede a delayed close; contents belong to slot 3 throughout.
                mc.player.getInventory().selected=2;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);mc.screen.onClose();
            }else if(stage==13) {
                server(mc,player->{require(stored(player.getInventory().getItem(3))==1&&player.getInventory().getItem(2).is(Items.STICK),"Close wrote pouch into newly selected slot");proof.put("selectionChangeCloseKeepsOpeningSlot",true);});
            }else if(stage==14) {
                mc.player.getInventory().selected=3;mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
            }else if(stage==15) {
                if(!(mc.screen instanceof LegacyDeviceScreen screen)||!screen.getMenu().receivedContents())return;
                click(mc,0,0,ClickType.PICKUP);mc.screen.onClose();
            }else if(stage==16) {
                server(mc,player->{
                    require(stored(player.getInventory().getItem(3))==0,"Close restored the removed focus");
                    int count=0;for(var value:player.getInventory().items){var snapshot=content.renderSnapshot(value);if(snapshot!=null&&snapshot.key().equals("Thaumcraft:FocusFire"))count+=value.getCount();}
                    require(count==1&&player.containerMenu.getCarried().isEmpty(),"Cursor close duplicated or lost focus: "+count);
                    proof.put("rapidPickupAndCloseReturnsFocusExactlyOnce",true);
                });
            }else if(stage==17) {
                if(harness==null)harness=new HoverHarnessHostAcceptance(content,proof,world);
                if(!harness.tick(mc))return;
                finish(mc,null);return;
            }
            stage++;frames=0;
        }catch(Throwable failure){finish(mc,failure);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void click(Minecraft mc,int slot,int button,ClickType type){mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player);}
    @FunctionalInterface private interface ServerStep{void run(ServerPlayer player)throws Exception;}
    private void server(Minecraft mc,ServerStep action){pending=mc.getSingleplayerServer().submit(()->{try{action.run(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));}catch(Exception failure){throw new IllegalStateException(failure);}});}
    private int stored(net.minecraft.world.item.ItemStack pouch)throws Exception {
        return content.services().items().inLegacy(()->{
            Object stack=content.services().items().materialize(content.gameplayStack(pouch));
            Object item=stack.getClass().getMethod("func_77973_b").invoke(stack);
            Object[] contents=(Object[])item.getClass().getMethod("getInventory",stack.getClass()).invoke(item,stack);
            int count=0;for(Object value:contents)if(value!=null)count+=value.getClass().getField("field_77994_a").getInt(value);return count;
        });
    }
    private void finish(Minecraft mc,Throwable failure) {
        done=true;proof.put("status",failure==null?"PASS":"FAIL");
        if(failure!=null){proof.put("stage",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_POUCH_SMOKE_FAIL",failure);}
        try{var output=mc.gameDirectory.toPath().resolve("logs/mfcl/focus-pouch-smoke.json");java.nio.file.Files.createDirectories(output.getParent());java.nio.file.Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_POUCH_SMOKE_RESULT {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
