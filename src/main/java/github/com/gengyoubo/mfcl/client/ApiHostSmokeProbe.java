package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/** Acceptance through the exact API-only addon entry point inside a real Forge client/integrated server. */
final class ApiHostSmokeProbe {
    @LegacyType("net.minecraft.world.World") public interface World {
        @LegacyMethod(name="func_72820_D", descriptor="()J") long dayTime();
    }
    private final Map<String,Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(4);
    private int stage, readyCalls;
    private LegacyObject clientWorld;
    private CompletableFuture<LegacyObject> pending;
    static void install() {
        var probe = new ApiHostSmokeProbe();
        MfclApi.whenReady(runtime -> probe.readyCalls++);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(probe::tick);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("API acceptance timed out at " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2);
                if (readyCalls != 1) throw new IllegalStateException("API ready callback did not fire exactly once");
                var api = MfclApi.runtime().orElseThrow();
                if (!api.loadedMods().contains("Thaumcraft")) throw new IllegalStateException("Actual loaded mod ids missing");
                proof.put("readyCallbackAndPublicServiceLoader", true);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-api-smoke-" + System.currentTimeMillis(),
                        new LevelSettings("MFCL API", net.minecraft.world.level.GameType.CREATIVE, false,
                                net.minecraft.world.Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42, false, false),
                        access -> access.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.level != null && mc.player != null && mc.screen == null) {
                var api = MfclApi.runtime().orElseThrow();
                var input = new ItemStack(Items.OAK_PLANKS, 3);
                var old = api.mappings().toLegacy(LegacyMappings.ITEM_STACK, input).orElseThrow();
                var snapshot = api.snapshot(old);
                if (snapshot.count() != 3 || !snapshot.key().equals("minecraft:planks")) throw new IllegalStateException("Vanilla stack mapping differs");
                var output = api.mappings().toHost(LegacyMappings.ITEM_STACK, old, ItemStack.class).orElseThrow();
                output.setCount(1);
                if (input.getCount() != 3 || api.snapshot(old).count() != 3) throw new IllegalStateException("Stack ownership leaked");
                var nbt = new LegacyStack("Thaumcraft:ItemEssence", 1, 2, "{Aspects:[{key:aer,amount:3},{key:terra,amount:2}]}");
                var tagged = api.stack(nbt);
                var nativeTagged = api.mappings().toHost(LegacyMappings.ITEM_STACK, tagged, ItemStack.class).orElseThrow();
                var recovered = api.mappings().toLegacy(LegacyMappings.ITEM_STACK, nativeTagged).orElseThrow();
                if (!api.snapshot(tagged).equals(api.snapshot(recovered))) throw new IllegalStateException("Native envelope lost old NBT");
                var unsupported = new ItemStack(Items.DIAMOND); unsupported.getOrCreateTag().putString("host_only", "reject");
                try { api.mappings().toLegacy(LegacyMappings.ITEM_STACK, unsupported); throw new IllegalStateException("Lossy NBT mapping accepted"); }
                catch (LegacyAccessException expected) { /* explicit rejection, no silent tag loss */ }
                proof.put("nativeItemCopiesMetadataAndOriginalNbt", true);
                clientWorld = api.mappings().toLegacy(LegacyMappings.WORLD, mc.level).orElseThrow();
                if (api.accessor(World.class, clientWorld).dayTime() != mc.level.getDayTime()) throw new IllegalStateException("Client world mapping differs");
                if (api.mappings().toHost(LegacyMappings.WORLD, clientWorld, Level.class).orElseThrow() != mc.level) throw new IllegalStateException("Client world identity differs");
                pending = mc.getSingleplayerServer().submit(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    if (mc.getSingleplayerServer().getCommands().performPrefixedCommand(mc.getSingleplayerServer().createCommandSourceStack(), "mfclapi") != 1)
                        throw new IllegalStateException("API operator diagnostic command failed");
                    var world = api.mappings().toLegacy(LegacyMappings.WORLD, level).orElseThrow();
                    if (api.accessor(World.class, world).dayTime() != level.getDayTime()) throw new IllegalStateException("Server world mapping differs");
                    return world;
                }); stage = 2;
            } else if (stage == 2 && pending.isDone()) {
                var world = pending.join(); var api = MfclApi.runtime().orElseThrow();
                try { api.accessor(World.class, world); throw new IllegalStateException("Server world accepted on render thread"); }
                catch (LegacyAccessException expected) { /* thread guard */ }
                proof.put("clientServerWorldIdentityAndThreadGuards", true);
                proof.put("operatorDiagnosticCommand", true);
                mc.level.disconnect(); mc.clearLevel(); stage = 3;
            } else if (stage == 3 && mc.level == null) {
                var api = MfclApi.runtime().orElseThrow();
                try { api.accessor(World.class, clientWorld); throw new IllegalStateException("Unloaded world reference accepted"); }
                catch (LegacyAccessException expected) { /* lifetime guard */ }
                proof.put("unloadedWorldReferenceRejected", true);
                proof.put("diagnosticsRecorded", api.diagnostics().size());
                finish(mc, null);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }
    private void finish(Minecraft mc, Throwable failure) {
        stage = 99; proof.put("status", failure == null ? "PASS" : "FAIL");
        if (failure != null) { proof.put("failure", failure.toString()); com.mojang.logging.LogUtils.getLogger().error("MFCL_API_SMOKE_FAIL", failure); }
        try {
            var file = mc.gameDirectory.toPath().resolve("logs/mfcl/api-smoke.json"); java.nio.file.Files.createDirectories(file.getParent());
            java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception error) { throw new IllegalStateException(error); }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_API_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); } mc.stop();
    }
}
