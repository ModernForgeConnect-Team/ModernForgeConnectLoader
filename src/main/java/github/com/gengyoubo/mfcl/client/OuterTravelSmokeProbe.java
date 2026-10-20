package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;
import java.util.concurrent.*;

/**
 * A real player walks into an original TC4 eldritch portal: the original TileEldritchPortal tick sends them to the
 * Outer Lands (arriving beside the maze portal that original population built), and its twin there sends them back.
 */
final class OuterTravelSmokeProbe {
    private static final ResourceKey<Level> OUTER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("thaumcraft:outer_lands"));
    private final ModernContentBridge content;
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(6);
    private CompletableFuture<?> pending;
    private int stage, frames;
    private BlockPos overworldPortal, outerPortal;
    private OuterTravelSmokeProbe(ModernContentBridge content) { this.content = content; }
    static void install(ModernContentBridge content) { MinecraftForge.EVENT_BUS.addListener(new OuterTravelSmokeProbe(content)::tick); }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            if (pending != null && pending.isCompletedExceptionally()) pending.join();
            if (System.nanoTime() > deadline) throw new IllegalStateException("Outer Lands travel smoke timed out at stage " + stage);
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow()); mc.options.pauseOnLostFocus = false; mc.options.renderDistance().set(2);
                mc.createWorldOpenFlows().createFreshLevel("mfcl-outer-" + System.currentTimeMillis(),
                        new LevelSettings("MFCL Outer Lands acceptance", GameType.SURVIVAL, false, net.minecraft.world.Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(42, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                stage = 1;
            } else if (stage == 1 && mc.player != null && mc.level != null && mc.screen == null) {
                pending = server(mc, () -> { enterOverworldPortal(mc); return true; }); stage = 2;
            } else if (stage == 2 && pending.isDone() && mc.level != null && mc.level.dimension() == OUTER && ++frames > 40) {
                pending.join(); frames = 0;
                verifyClientResearch(mc);
                pending = server(mc, () -> { arrivedOuter(mc); return true; }); stage = 3;
            } else if (stage == 3 && pending.isDone() && mc.level != null && mc.level.dimension() == Level.OVERWORLD && ++frames > 10) {
                pending.join();
                pending = server(mc, () -> { arrivedOverworld(mc); return true; }); stage = 4;frames=0;
            } else if (stage == 4 && pending.isDone()) {
                pending.join();if(++frames<10)return;verifyClientResearch(mc);
                proof.put("status", "PASS"); finish(mc, null);
            }
        } catch (Throwable failure) { finish(mc, failure); }
    }
    private ServerPlayer player(Minecraft mc) { return mc.getSingleplayerServer().getPlayerList().getPlayers().get(0); }
    private void enterOverworldPortal(Minecraft mc) throws Exception {
        var level = mc.getSingleplayerServer().overworld(); var player = player(mc);
        int x = 200, z = 200; int y = Math.max(64,level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
        level.setBlock(new BlockPos(x,y-1,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        overworldPortal = new BlockPos(x, y, z);
        var services = content.services(); var loader = services.loader();
        services.items().inLegacy(() -> {
            // What TileEldritchAltar.checkForMaze starts on a thread when the altar opens; run inline for a deterministic maze.
            ((Runnable) loader.loadClass("thaumcraft.common.lib.world.dim.MazeThread").getConstructor(int.class, int.class, int.class, int.class, long.class)
                    .newInstance(x >> 4, z >> 4, 15, 15, 99L)).run();
            return null;
        });
        outerPortal = portalCell(services, loader);
        proof.put("overworldPortal", overworldPortal.toShortString()); proof.put("expectedOuterPortal", outerPortal.toShortString());
        level.setBlock(overworldPortal, content.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockPortalEldritch", 0)), 3);
        player.setPortalCooldown(0);
        player.teleportTo(level, x + .5, y, z + .5, 0, 0);
    }
    private BlockPos portalCell(github.com.gengyoubo.mfcl.bridge.LegacyServices services, ClassLoader loader) throws Exception {
        return services.items().inLegacy(() -> {
            var labyrinth = (Map<?, ?>) loader.loadClass("thaumcraft.common.lib.world.dim.MazeHandler").getField("labyrinth").get(null);
            var cellType = loader.loadClass("thaumcraft.common.lib.world.dim.Cell"); var locType = loader.loadClass("thaumcraft.common.lib.world.dim.CellLoc");
            BlockPos nearest = null;
            for (var entry : labyrinth.entrySet()) {
                Object cell = cellType.getConstructor(short.class).newInstance((Short) entry.getValue());
                if (cellType.getField("feature").getByte(cell) != 1) continue;
                var pos = new BlockPos(locType.getField("x").getInt(entry.getKey()) * 16 + 8, 53, locType.getField("z").getInt(entry.getKey()) * 16 + 8);
                if (nearest == null || pos.distSqr(new BlockPos(200, 53, 200)) < nearest.distSqr(new BlockPos(200, 53, 200))) nearest = pos;
            }
            if (nearest == null) throw new IllegalStateException("Maze has no portal cell");
            return nearest;
        });
    }
    private void arrivedOuter(Minecraft mc) throws Exception {
        var player = player(mc); var level = player.serverLevel();
        if (level.dimension() != OUTER) throw new IllegalStateException("Server player is in " + level.dimension().location());
        proof.put("outerArrival", player.position().toString());
        if (Math.abs(Math.abs(player.getX() - (outerPortal.getX() + .5)) - 1) > .01 || Math.abs(Math.abs(player.getZ() - (outerPortal.getZ() + .5)) - 1) > .01 || player.getY() < outerPortal.getY() - 1.01 || player.getY() > outerPortal.getY() + .01)
            throw new IllegalStateException("Arrival is not beside the generated maze portal " + outerPortal + ": " + player.position());
        if (!content.legacyBlock(level.getBlockState(outerPortal)).legacyKey().equals("Thaumcraft:blockPortalEldritch")) throw new IllegalStateException("Original population built no portal at " + outerPortal);
        if (level.getBlockState(player.blockPosition().below()).isAir()) throw new IllegalStateException("Arrival has no maze floor under " + player.blockPosition());
        proof.put("outerPortalGenerated", true);
        verifyEntryResearch(player);
        // The original portal keeps timeUntilPortal at 100 while the player stands in it; step onto it once that is cleared.
        player.setPortalCooldown(0);
        player.teleportTo(level, outerPortal.getX() + .5, outerPortal.getY(), outerPortal.getZ() + .5, 0, 0);
    }
    private void arrivedOverworld(Minecraft mc) throws Exception {
        var player = player(mc);
        if (player.serverLevel().dimension() != Level.OVERWORLD) throw new IllegalStateException("Server player did not return: " + player.serverLevel().dimension().location());
        proof.put("overworldArrival", player.position().toString());
        if (Math.abs(Math.abs(player.getX() - (overworldPortal.getX() + .5)) - 1) > .01 || Math.abs(Math.abs(player.getZ() - (overworldPortal.getZ() + .5)) - 1) > .01)
            throw new IllegalStateException("Return is not beside the overworld portal " + overworldPortal + ": " + player.position());
        proof.put("returnedToOverworld", true);
        verifyEntryResearch(player);
        github.com.gengyoubo.mfcl.content.OuterPlayerHostAcceptance.verifyRollback(content,player,proof);
    }
    private void verifyClientResearch(Minecraft mc) throws Exception {
        content.services().items().inLegacy(()->{
            var manager=content.services().loader().loadClass("thaumcraft.common.lib.research.ResearchManager");
            if(!(boolean)manager.getMethod("isResearchComplete",String.class,String.class).invoke(null,mc.player.getName().getString(),"ENTEROUTER"))throw new IllegalStateException("Entry research packet did not reach the client");
            if((boolean)manager.getMethod("isResearchComplete",String.class,String.class).invoke(null,mc.player.getName().getString(),"RESEARCHER1"))throw new IllegalStateException("Failed tile callback leaked its research packet");
            return null;
        });
        proof.put("entryResearchClientSyncAndFailedPacketIsolation",true);
    }
    private void verifyEntryResearch(ServerPlayer player) throws Exception {
        var services=content.services();var loader=services.loader();
        Object world=services.worlds().bound(player.serverLevel());
        services.items().inLegacy(()->{try(var state=services.playerState().enter(world)){
            Object actor=content.itemUse().clientActor(world,new github.com.gengyoubo.mfcl.bridge.LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),0,0),null);
            byte[] saved=player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
            if(saved.length==0)throw new IllegalStateException("Portal research was not persisted");
            services.playerData().write(actor,saved);
            if(!(boolean)loader.loadClass("thaumcraft.common.lib.research.ResearchManager").getMethod("isResearchComplete",String.class,String.class).invoke(null,player.getName().getString(),"ENTEROUTER"))throw new IllegalStateException("Entry research was lost across dimensions");
            return null;
        }});
        proof.put("entryResearchPersistedAcrossDimensions",true);
    }
    private <T> CompletableFuture<T> server(Minecraft mc, Callable<T> action) { return mc.getSingleplayerServer().submit(() -> { try { return action.call(); } catch (Exception failure) { throw new IllegalStateException(failure); } }); }
    private void finish(Minecraft mc, Throwable failure) {
        stage = 99;
        if (failure != null) { proof.put("status", "FAIL"); proof.put("failure", failure.toString()); com.mojang.logging.LogUtils.getLogger().error("MFCL_OUTER_SMOKE_FAIL", failure); }
        try { var path = mc.gameDirectory.toPath().resolve("logs/mfcl/outer-travel-smoke.json"); java.nio.file.Files.createDirectories(path.getParent()); java.nio.file.Files.writeString(path, new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof)); }
        catch (Exception error) { throw new IllegalStateException(error); }
        com.mojang.logging.LogUtils.getLogger().info("MFCL_OUTER_SMOKE_RESULT: {}", proof);
        if (mc.level != null) { mc.level.disconnect(); mc.clearLevel(); }
        mc.stop();
    }
}
