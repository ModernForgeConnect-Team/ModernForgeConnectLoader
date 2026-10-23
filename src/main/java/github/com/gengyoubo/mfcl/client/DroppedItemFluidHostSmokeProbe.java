package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Real native item aging and original TC4 expiration subscribers in a disposable, enclosed water fixture. */
final class DroppedItemFluidHostSmokeProbe {
    private final ModernContentBridge content;
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
    private CompletableFuture<?> pending;
    private volatile boolean running, done;
    private Throwable serverFailure;
    private int stage;
    private ItemEntity salt, drySalt, stick, stackedSalt;
    private final BlockPos source = new BlockPos(0, 200, 0), dry = source.east(4), control = source.east(8), stacked = source.east(12);
    private DroppedItemFluidHostSmokeProbe(ModernContentBridge content) { this.content = content; }
    static void install(ModernContentBridge content) {
        var probe = new DroppedItemFluidHostSmokeProbe(content);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(probe::clientTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(probe::serverTick);
    }
    private void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Dropped-item fluid acceptance timed out");
            if (pending != null) { if (!pending.isDone()) return; pending.join(); pending = null; }
            if (stage == 0) {
                if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-dropped-items-" + System.currentTimeMillis(),
                        new LevelSettings("MFCL dropped-item fluids", GameType.SURVIVAL, false, net.minecraft.world.Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42, false, false), access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                stage++;
            } else if (stage == 1) {
                if (mc.player == null || mc.screen != null) return;
                pending = mc.getSingleplayerServer().submit(() -> setup(mc.getSingleplayerServer().overworld())); stage++;
            } else if (stage == 2 && !running) finish(mc, serverFailure);
        } catch (Throwable failure) { finish(mc, failure); }
    }
    private void setup(ServerLevel level) {
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        level.setBlockAndUpdate(source.south(4).below(), Blocks.STONE.defaultBlockState());
        player.connection.teleport(.5, 200, 4.5, 0, 0);
        well(level, source, true); well(level, dry, false); well(level, control, true); well(level, stacked, true);
        salt = drop(level, source, content.stack("Thaumcraft:ItemBathSalts", 0, 1, ""));
        drySalt = drop(level, dry, content.stack("Thaumcraft:ItemBathSalts", 0, 1, ""));
        stick = drop(level, control, new ItemStack(Items.STICK));
        stackedSalt = drop(level, stacked, content.stack("Thaumcraft:ItemBathSalts", 0, 3, ""));
        proof.put("bathSaltLifespan", salt.lifespan);
        require(salt.lifespan == 200 && drySalt.lifespan == 200 && stackedSalt.lifespan == 200, "Original bath salt lifespan was not imported");
        require(stick.lifespan == 6000, "Vanilla drop lifetime changed");
        // Expire in flowing water within this tick, before its scheduled flow can change the test block.
        var flowing = source.west(4); well(level, flowing, true);
        level.setBlock(flowing, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 1), 2);
        var flowSalt = drop(level, flowing, content.stack("Thaumcraft:ItemBathSalts", 0, 1, ""));
        var data = flowSalt.saveWithoutId(new net.minecraft.nbt.CompoundTag()); data.putShort("Age", (short)199); flowSalt.load(data);
        flowSalt.tick();
        require(flowSalt.isRemoved() && level.getBlockState(flowing).is(Blocks.WATER), "Flowing water incorrectly converted or salt failed to expire");
        proof.put("flowingWaterRejected", true); running = true;
    }
    private void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !running || done) return;
        try {
            var level = event.getServer().overworld();
            if (!salt.isRemoved()) {
                require(salt.getAge() < 200 && level.getBlockState(source).is(Blocks.WATER), "Salt converted early or failed to expire at 200 ticks");
                if (salt.getAge() >= 180) proof.put("noConversionBeforeExpiration", true);
                return;
            }
            require(Boolean.TRUE.equals(proof.get("noConversionBeforeExpiration")), "Natural item aging was not observed");
            require(level.getBlockState(source).is(content.blocks().get("Thaumcraft:blockFluidPure")), "Expired salt did not convert the water source");
            require(drySalt.isRemoved() && level.getBlockState(dry).isAir(), "Dry salt did not expire normally");
            require(!stick.isRemoved() && stick.getItem().getCount() == 1 && level.getBlockState(control).is(Blocks.WATER), "Ordinary item converted water or disappeared");
            require(stackedSalt.isRemoved() && level.getBlockState(stacked).is(content.blocks().get("Thaumcraft:blockFluidPure")), "Stacked salts diverged from original expiration behavior");
            proof.put("sourceWaterConvertedAt200Ticks", true); proof.put("drySaltExpiresWithoutConversion", true);
            proof.put("ordinaryItemUnaffected", true); proof.put("stackedSaltsMatchOriginalBehavior", true); running = false;
        } catch (Throwable failure) { serverFailure = failure; running = false; }
    }
    private static void well(ServerLevel level, BlockPos pos, boolean water) {
        for (var side : net.minecraft.core.Direction.values()) level.setBlockAndUpdate(pos.relative(side), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
    }
    private static ItemEntity drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        var drop = new ItemEntity(level, pos.getX()+.5, pos.getY()+.25, pos.getZ()+.5, stack);
        drop.setDeltaMovement(0,0,0); drop.setNoGravity(true); drop.setNeverPickUp(); level.addFreshEntity(drop); return drop;
    }
    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    private void finish(Minecraft mc, Throwable failure) {
        done = true; proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) { proof.put("failure", failure.toString()); com.mojang.logging.LogUtils.getLogger().error("MFCL dropped-item fluid acceptance failed", failure); }
        try {
            var output = mc.gameDirectory.toPath().resolve("logs/mfcl/dropped-item-fluid-smoke.json"); java.nio.file.Files.createDirectories(output.getParent());
            java.nio.file.Files.writeString(output, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception error) { throw new IllegalStateException(error); }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_DROPPED_ITEM_FLUID_RESULT {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); } mc.stop();
    }
}
