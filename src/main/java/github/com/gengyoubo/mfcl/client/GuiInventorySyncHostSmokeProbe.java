package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real Extra Utilities transfer-node GUI, native click packets and delayed close reconciliation. */
final class GuiInventorySyncHostSmokeProbe {
    private interface Step { boolean run(Minecraft mc) throws Exception; }
    private final ModernContentBridge content;
    private final Map<String,Object> proof=new LinkedHashMap<>();
    private final List<Step> steps=new ArrayList<>();
    private final long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<?> pending;
    private List<CompoundTag> expected;
    private BlockPos node;
    private int stage,frames;
    private boolean done;
    private GuiInventorySyncHostSmokeProbe(ModernContentBridge content) { this.content=content;define(); }
    static void install(ModernContentBridge content) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(new GuiInventorySyncHostSmokeProbe(content)::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if(done||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        try {
            if(System.nanoTime()>deadline)throw new IllegalStateException("GUI sync acceptance timed out at "+stage);
            if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
            if(++frames<10)return;
            if(steps.get(stage).run(mc)){stage++;frames=0;}
            if(stage==steps.size()){proof.put("status","PASS");finish(mc,null);}
        } catch(Throwable failure){finish(mc,failure);}
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> action) {
        pending=mc.getSingleplayerServer().submit(()->action.accept(mc.getSingleplayerServer().getPlayerList().getPlayers().get(0)));
    }
    private static List<CompoundTag> snapshot(net.minecraft.world.entity.player.Player player) {
        return player.getInventory().items.stream().map(value->value.save(new CompoundTag())).toList();
    }
    private static int inventorySlot(Minecraft mc,int slot) { return mc.player.containerMenu.slots.size()-9+(slot<9?slot:slot-36); }
    private static void click(Minecraft mc,int slot,int button,ClickType type) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,button,type,mc.player); }
    private boolean opened(Minecraft mc) {
        if(!(mc.screen instanceof LegacyDeviceScreen screen)||!screen.getMenu().receivedContents())return false;
        require(screen.originalGui().getClass().getSimpleName().equals("GuiTransferNode"),"Wrong original GUI");return true;
    }
    private void resetAndOpen(boolean sentinel) {
        steps.add(mc->{
            require(mc.screen==null,"Previous GUI still open");
            server(mc,player->{
                player.getInventory().clearContent();player.getInventory().selected=0;
                player.getInventory().setItem(1,new ItemStack(Items.STICK,17));player.getInventory().setItem(2,new ItemStack(Items.DIAMOND,3));
                if(sentinel){var stack=new ItemStack(Items.NETHERITE_INGOT,7);stack.getOrCreateTag().putString("GuiSyncSentinel","preserve");player.getInventory().setItem(12,stack);}
                player.inventoryMenu.broadcastChanges();
            });return true;
        });
        steps.add(mc->{mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(node),Direction.UP,node,false));return true;});
        steps.add(this::opened);
    }
    private void verifyClosed(String name) {
        steps.add(mc->{
            require(mc.screen==null,"GUI did not close");
            server(mc,player->{
                require(player.containerMenu==player.inventoryMenu&&player.containerMenu.getCarried().isEmpty(),name+": server cursor not returned");
                require(player.getInventory().countItem(Items.STICK)==17&&player.getInventory().countItem(Items.DIAMOND)==3,name+": server lost/duplicated items");
                expected=snapshot(player);
            });return true;
        });
        steps.add(mc->{
            require(mc.player.containerMenu==mc.player.inventoryMenu&&mc.player.containerMenu.getCarried().isEmpty(),name+": stale cursor survived close");
            require(snapshot(mc.player).equals(expected),name+": client inventory differs from server after close");proof.put(name,true);return true;
        });
    }
    private void define() {
        steps.add(mc->{
            if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null)return false;
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(2);
            mc.createWorldOpenFlows().createFreshLevel("mfcl-gui-sync-"+System.currentTimeMillis(),new LevelSettings("MFCL GUI sync acceptance",GameType.SURVIVAL,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                    new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access->access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET).getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());return true;
        });
        steps.add(mc->{
            if(mc.player==null||mc.screen!=null)return false;
            server(mc,player->{node=player.blockPosition().offset(2,0,0);player.serverLevel().setBlockAndUpdate(node,content.blocks().get("ExtraUtilities:extractor_base").defaultBlockState().setValue(ModernContentBridge.METADATA,0));});return true;
        });
        resetAndOpen(false);
        steps.add(mc->{click(mc,inventorySlot(mc,1),0,ClickType.PICKUP);return true;});
        steps.add(mc->{
            require(mc.player.containerMenu.getCarried().is(Items.STICK),"Node GUI pickup not acknowledged");
            require(mc.player.getInventory().getItem(1).isEmpty(),"Acknowledged node GUI pickup left stale native inventory slot");
            proof.put("acknowledgedPickupUpdatesNativeInventory",true);click(mc,inventorySlot(mc,4),0,ClickType.PICKUP);return true;
        });
        steps.add(mc->{require(mc.player.getInventory().getItem(4).getCount()==17,"Node GUI placement not reflected in native inventory");mc.screen.onClose();return true;});
        verifyClosed("acknowledgedMoveAndClose");
        resetAndOpen(false);
        steps.add(mc->{click(mc,inventorySlot(mc,1),0,ClickType.PICKUP);return true;});
        steps.add(mc->{click(mc,0,0,ClickType.PICKUP);return true;});
        steps.add(mc->{
            require(mc.player.containerMenu.getSlot(0).getItem().getCount()==17&&mc.player.containerMenu.getCarried().isEmpty(),"Node buffer insert did not synchronize");
            click(mc,0,0,ClickType.QUICK_MOVE);return true;
        });
        steps.add(mc->{require(mc.player.containerMenu.getSlot(0).getItem().isEmpty(),"Node buffer shift removal did not synchronize");mc.screen.onClose();return true;});
        verifyClosed("nodeBufferInsertAndShiftRemoval");
        for(int scenario=0;scenario<5;scenario++) {
            final int scenarioId=scenario;resetAndOpen(false);
            steps.add(mc->{
                // All click and close packets reach the server after its 600 ms stall, in wire order.
                pending=mc.getSingleplayerServer().submit(()->{try{Thread.sleep(600);}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}});
                if(scenarioId==2)click(mc,inventorySlot(mc,1),4,ClickType.SWAP);
                else {
                    click(mc,inventorySlot(mc,1),scenarioId==3?1:0,ClickType.PICKUP);
                    if(scenarioId==1)click(mc,inventorySlot(mc,4),0,ClickType.PICKUP);
                    if(scenarioId==4){click(mc,-999,0,ClickType.QUICK_CRAFT);click(mc,inventorySlot(mc,4),1,ClickType.QUICK_CRAFT);click(mc,inventorySlot(mc,5),1,ClickType.QUICK_CRAFT);click(mc,-999,2,ClickType.QUICK_CRAFT);}
                }
                mc.screen.onClose();require(!pending.isDone(),"Server stall ended before close");return true;
            });
            verifyClosed("delayedCloseScenario"+scenario);
        }
        resetAndOpen(true);
        steps.add(mc->{mc.screen.onClose();return true;});
        verifyClosed("unmappedInventoryClose");
        steps.add(mc->{
            require(mc.player.getInventory().getItem(12).is(Items.NETHERITE_INGOT)&&mc.player.getInventory().getItem(12).getCount()==7&&"preserve".equals(mc.player.getInventory().getItem(12).getTag().getString("GuiSyncSentinel")),"Close erased native-only inventory stack");
            proof.put("nativeOnlyStackPreserved",true);return true;
        });
    }
    private void finish(Minecraft mc,Throwable failure) {
        done=true;
        if(failure!=null){proof.put("status","FAIL");proof.put("stage",stage);proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL_GUI_SYNC_SMOKE_FAIL",failure);}
        try {var path=mc.gameDirectory.toPath().resolve("logs/mfcl/gui-sync-smoke.json");java.nio.file.Files.createDirectories(path.getParent());java.nio.file.Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception error){throw new IllegalStateException(error);}
        com.mojang.logging.LogUtils.getLogger().info("MFCL_GUI_SYNC_SMOKE_DONE: {}",proof);
        if(mc.level!=null){mc.level.disconnect();mc.clearLevel();}mc.stop();
    }
}
