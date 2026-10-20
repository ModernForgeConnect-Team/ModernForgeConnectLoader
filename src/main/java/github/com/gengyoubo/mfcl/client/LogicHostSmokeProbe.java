package github.com.gengyoubo.mfcl.client;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Actual native ticks, hopper capability calls, original TC callbacks and save/reload in a disposable world. */
final class LogicHostSmokeProbe {
    private final ModernContentBridge bridge;
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private int stage;
    private String worldName;
    private CompletableFuture<?> pending;
    private BlockPos furnace, alembic, tube, jar, valve, crucible;
    private long started;
    private boolean wandClientChecked;
    private final long deadline = System.nanoTime()+java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private LogicHostSmokeProbe(ModernContentBridge bridge) { this.bridge = bridge; }
    static void install(ModernContentBridge bridge) { MinecraftForge.EVENT_BUS.addListener(new LogicHostSmokeProbe(bridge)::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Logic acceptance timed out at stage " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                worldName = "mfcl-logic-smoke-"+System.currentTimeMillis(); mc.options.renderDistance().set(2); mc.options.pauseOnLostFocus = false;
                stage = 1;
                mc.createWorldOpenFlows().createFreshLevel(worldName,new LevelSettings("MFCL original logic",GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42,false,false),access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending = server(mc,() -> { setup(mc); return true; }); stage = 2;
                mc.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("MFCL logic acceptance")) {
                    @Override public boolean isPauseScreen() { return false; }
                });
            } else if (stage == 2 && pending.isDone()) {
                if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) mc.setScreen(null);
                pending.join();
                if (!wandClientChecked) {
                    if (!(mc.level.getBlockEntity(crucible) instanceof LegacyBlockEntity)) return;
                    verifyClientWand(mc);
                    wandClientChecked = true;
                }
                pending = server(mc,() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    if (level.getGameTime()-started < 240) return false;
                    verify(level,false); return true;
                }); stage = 3;
            } else if (stage == 3 && pending.isDone()) {
                if (!Boolean.TRUE.equals(pending.join())) { stage = 2; return; }
                stage = 4;
            } else if (stage == 4) {
                if (!(mc.level.getBlockEntity(jar) instanceof LegacyBlockEntity client)) return;
                if (client.legacy().getClass().getField("amount").getInt(client.legacy()) != 3) return;
                proof.put("clientEssentiaAmount",3);
                mc.level.disconnect(); mc.clearLevel(); mc.setScreen(new TitleScreen()); stage = 5;
            } else if (stage == 5 && mc.getSingleplayerServer() == null && mc.screen instanceof TitleScreen) {
                mc.createWorldOpenFlows().loadLevel(mc.screen,worldName); stage = 6;
            } else if (stage == 6 && mc.player != null && mc.level != null && mc.screen == null) {
                pending = server(mc,() -> { verify(mc.getSingleplayerServer().overworld(),true); return true; }); stage = 7;
            } else if (stage == 7 && pending.isDone()) {
                pending.join(); proof.put("status","PASS"); proof.put("world",worldName); write(mc);
                LogUtils.getLogger().info("MFCL_LOGIC_WORLD_SMOKE_PASS: {}",proof);
                mc.level.disconnect(); mc.clearLevel(); stage = 99; mc.stop();
            }
        } catch (Throwable failure) {
            proof.put("status","FAIL"); proof.put("stage",stage); proof.put("failure",failure.toString());
            LogUtils.getLogger().error("MFCL_LOGIC_WORLD_SMOKE_FAIL stage="+stage,failure);
            try { write(mc); } catch (Exception ignored) { }
            stage = 99; mc.stop();
        }
    }
    private void write(Minecraft mc) throws Exception {
        Path path = mc.gameDirectory.toPath().resolve("logs/mfcl/logic-1.7.10-smoke.json"); Files.createDirectories(path.getParent());
        Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
    }
    private <T> CompletableFuture<T> server(Minecraft mc, java.util.concurrent.Callable<T> action) {
        return mc.getSingleplayerServer().submit(() -> {
            try { return action.call(); } catch (Exception failure) { throw new IllegalStateException(failure); }
        });
    }
    private LegacyBlockEntity place(net.minecraft.server.level.ServerLevel level, BlockPos pos, String key, int metadata) throws Exception {
        level.setBlockAndUpdate(pos,bridge.modernBlock(new HostWorldAccess.Block(key,metadata)));
        var tile = (LegacyBlockEntity)level.getBlockEntity(pos); tile.legacy(); return tile;
    }
    private void setup(Minecraft mc) throws Exception {
        var level = mc.getSingleplayerServer().overworld(); var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        player.getInventory().clearContent(); furnace = player.blockPosition().offset(4,0,4);
        alembic = furnace.above(); tube = alembic.east(); jar = tube.below(); valve = furnace.south(3); crucible = furnace.south(5);
        var furnaceTile = place(level,furnace,"Thaumcraft:blockStoneDevice",0);
        place(level,alembic,"Thaumcraft:blockMetalDevice",1); place(level,tube,"Thaumcraft:blockTube",0); place(level,jar,"Thaumcraft:blockJar",0);
        var valveTile = place(level,valve,"Thaumcraft:blockTube",1);
        var unsided = furnaceTile.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        if (!unsided.insertItem(1,new ItemStack(Items.COAL),true).isEmpty() || !unsided.getStackInSlot(1).isEmpty()) throw new IllegalStateException("Simulated insert mutated the original inventory");
        if (!unsided.insertItem(1,new ItemStack(Items.COAL),false).isEmpty()) throw new IllegalStateException("Original furnace rejected native fuel");
        if (!level.getBlockState(furnace).hasAnalogOutputSignal() || level.getBlockState(furnace).getAnalogOutputSignal(level,furnace) <= 0)
            throw new IllegalStateException("Original inventory comparator output was not exposed to native redstone");
        proof.put("originalComparatorOutput",true);
        var denied = furnaceTile.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,Direction.UP).orElseThrow(IllegalStateException::new);
        if (denied.getSlots() != 0) throw new IllegalStateException("Original furnace top-side restriction changed");
        level.setBlockAndUpdate(furnace.west(),Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING,Direction.EAST));
        ((HopperBlockEntity)level.getBlockEntity(furnace.west())).setItem(0,new ItemStack(Items.STICK,3));
        var valveState = level.getBlockState(valve);
        var hit = new BlockHitResult(Vec3.atCenterOf(valve),Direction.UP,valve,false);
        if (!valveState.use(level,player,InteractionHand.MAIN_HAND,hit).consumesAction() || valveTile.legacy().getClass().getField("allowFlow").getBoolean(valveTile.legacy())) throw new IllegalStateException("Original valve activation did not close flow");
        if (!valveState.use(level,player,InteractionHand.MAIN_HAND,hit).consumesAction() || !valveTile.legacy().getClass().getField("allowFlow").getBoolean(valveTile.legacy())) throw new IllegalStateException("Original valve activation did not reopen flow");
        level.setBlockAndUpdate(valve.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(crucible.below(),Blocks.LAVA.defaultBlockState());
        place(level,crucible,"Thaumcraft:blockMetalDevice",0);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        level.getBlockState(crucible).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(crucible),Direction.UP,crucible,false));
        if (!player.getMainHandItem().is(Items.BUCKET) || player.getMainHandItem().getCount() != 1) throw new IllegalStateException("Crucible bucket exchange failed: "+player.getMainHandItem());
        verifyServerWand(level,player);
        var before = bridge.services().tiles().save(furnaceTile.legacy()); var rollbackPos = furnace.north(2); var beforeState = level.getBlockState(rollbackPos);
        var oldWorld = bridge.services().worlds().bound(level);
        var blockRegistry = bridge.services().loader().loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
        var diamondBlock = blockRegistry.getClass().getMethod("func_82594_a",String.class).invoke(blockRegistry,"minecraft:diamond_block");
        try {
            bridge.logicTransaction(level,furnace,() -> {
                LegacyWorlds.setBlock(oldWorld,rollbackPos.getX(),rollbackPos.getY(),rollbackPos.getZ(),diamondBlock,0,3);
                bridge.services().inventories().set(furnaceTile.legacy(),1,null); throw new IllegalStateException("intentional acceptance failure");
            },true);
            throw new AssertionError("Expected callback failure");
        } catch (IllegalStateException expected) {
            if (!expected.getMessage().equals("intentional acceptance failure")) throw expected;
        }
        if (!Arrays.equals(before,bridge.services().tiles().save(furnaceTile.legacy())) || !level.getBlockState(rollbackPos).equals(beforeState)) throw new IllegalStateException("Failed callback lost original inventory/world state");
        proof.put("simulatedInventoryInsert",true); proof.put("originalInventorySideRules",true); proof.put("originalValveActivation",true);
        proof.put("crucibleBucketExchange",true); proof.put("failedCallbackInventoryAndWorldRollback",true); started = level.getGameTime();
    }
    private ItemStack wand() {
        var stack = bridge.declarations().tabs().stream().flatMap(tab -> tab.contents().stream()).filter(value -> value.key().equals("Thaumcraft:WandCasting")).findFirst().orElseThrow();
        return bridge.stack(stack.key(),stack.metadata(),1,stack.tag());
    }
    private int water(Level level) throws Exception {
        Object tile = ((LegacyBlockEntity)level.getBlockEntity(crucible)).legacy();
        Object tank = tile.getClass().getField("tank").get(tile);
        return (int)tank.getClass().getMethod("getFluidAmount").invoke(tank);
    }
    private void verifyServerWand(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer player) throws Exception {
        var target = crucible.east(2);
        player.setItemInHand(InteractionHand.MAIN_HAND,wand());
        level.setBlockAndUpdate(target,Blocks.CAULDRON.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atCenterOf(target),Direction.UP,target,false);
        if (!player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction()
                || !bridge.legacyBlock(level.getBlockState(target)).equals(new HostWorldAccess.Block("Thaumcraft:blockMetalDevice",0)))
            throw new IllegalStateException("Native wand use did not convert cauldron before block activation");
        place(level,target,"Thaumcraft:blockTable",0);
        if (!player.gameMode.useItemOn(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND,hit).consumesAction()
                || !bridge.legacyBlock(level.getBlockState(target)).equals(new HostWorldAccess.Block("Thaumcraft:blockTable",15)))
            throw new IllegalStateException("Native wand use did not convert the wooden table to an arcane workbench");
        if (!player.getMainHandItem().isEmpty() || bridge.services().inventories().get(((LegacyBlockEntity)level.getBlockEntity(target)).legacy(),10) == null)
            throw new IllegalStateException("Converting the wooden table did not transfer the held wand exactly once");
        player.closeContainer();
        level.removeBlock(target,false);
        player.setItemInHand(InteractionHand.MAIN_HAND,wand());
        player.teleportTo(crucible.getX()+.5,crucible.getY(),crucible.getZ()-2);
        player.setYRot(0);player.setXRot(30);player.setShiftKeyDown(false);
        // Warm the cached actor while standing, then change sneak state on the same player.
        player.gameMode.useItem(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND);
        if (water(level) != 1000) throw new IllegalStateException("Standing wand use emptied the crucible");
        player.setShiftKeyDown(true);
        player.gameMode.useItem(player,level,player.getMainHandItem(),InteractionHand.MAIN_HAND);
        if (water(level) != 0) throw new IllegalStateException("Sneaking wand use did not empty the crucible through the cached actor");
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WATER_BUCKET));
        level.getBlockState(crucible).use(level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(crucible),Direction.UP,crucible,false));
        player.setItemInHand(InteractionHand.MAIN_HAND,wand());
        proof.put("nativeWandCauldronAndCraftingTableTriggers",true);
        proof.put("wandSneakStateRefreshAndCrucibleEmptying",true);
    }
    private void verifyClientWand(Minecraft mc) throws Exception {
        mc.player.setItemInHand(InteractionHand.MAIN_HAND,wand());
        mc.player.setPos(crucible.getX()+.5,crucible.getY(),crucible.getZ()-2);
        mc.player.setYRot(0);mc.player.setXRot(30);
        var hit = new BlockHitResult(Vec3.atCenterOf(crucible),Direction.NORTH,crucible,false);
        mc.player.input.shiftKeyDown = true;
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),0,30,true));
        if (mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,hit) != InteractionResult.PASS)
            throw new IllegalStateException("Client item use swallowed the wand's held-use fallback");
        mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
        mc.player.input.shiftKeyDown = false;
        proof.put("clientSneakingWandItemFallthrough",true);
    }
    private void verify(net.minecraft.server.level.ServerLevel level, boolean reloaded) throws Exception {
        var positions = List.of(furnace,alembic,tube,jar,valve,crucible);
        for (var pos : positions) {
            var tile = (LegacyBlockEntity)level.getBlockEntity(pos);
            if (tile == null || tile.logicSuspended()) throw new IllegalStateException("Original tile logic is missing or suspended: "+pos);
            boolean active = (boolean)bridge.services().loader().loadClass("net.minecraft.tileentity.TileEntity").getMethod("canUpdate").invoke(tile.legacy());
            if (!reloaded && active && tile.successfulTicks() < 200 || !active && tile.successfulTicks() != 0) throw new IllegalStateException("Original canUpdate/tick lifecycle was not respected: "+pos);
        }
        var filled = ((LegacyBlockEntity)level.getBlockEntity(jar)).legacy(); int amount = filled.getClass().getField("amount").getInt(filled);
        if (amount != 3) throw new IllegalStateException("Furnace/alembic/tube/jar chain lost or duplicated essentia: " + amount);
        var valveOld = ((LegacyBlockEntity)level.getBlockEntity(valve)).legacy();
        if (valveOld.getClass().getField("allowFlow").getBoolean(valveOld)) throw new IllegalStateException("Native redstone did not close the original valve");
        var heated = ((LegacyBlockEntity)level.getBlockEntity(crucible)).legacy();
        if (heated.getClass().getField("heat").getShort(heated) < 190) throw new IllegalStateException("Original crucible did not heat over native lava");
        if (water(level) != 0) throw new IllegalStateException("Client wand click did not clear the server crucible");
        proof.put("clientWandClickReachedServerCrucible",true);
        if (reloaded) proof.put("logicStateSurvivedReload",true);
        else {
            proof.put("nativeHopperToOriginalFurnace",true); proof.put("originalSmeltingAndEssentiaTransfer",true); proof.put("essentiaConserved",amount);
            proof.put("nativeRedstoneToOriginalValve",true); proof.put("originalCrucibleHeating",true); proof.put("minimumSuccessfulTicks",200);
            level.getServer().saveEverything(false,true,true);
        }
    }
}
