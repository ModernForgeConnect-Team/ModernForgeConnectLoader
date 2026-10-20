package github.com.gengyoubo.mfcl.content;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/**
 * Dedicated-server acceptance for TC4's Outer Lands, in two launches on one world. {@code outer-write} checks the host
 * level and its legacy dimension id, builds an original maze (MazeThread), lets original population write it into the
 * host level, places through TeleporterThaumcraft, saves and stops; {@code outer-verify} checks it all survived a restart.
 */
final class OuterLandsSmokeProbe {
    private static final ResourceKey<net.minecraft.world.level.Level> OUTER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("mfcl:thaumcraft_outer"));
    private static final int MAZE_X = 64, MAZE_Z = 64;
    private final ModernContentBridge bridge;
    private final boolean write;
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private long started = -1;
    private int[] portalCell;
    private boolean done;
    private OuterLandsSmokeProbe(ModernContentBridge bridge, boolean write) { this.bridge = bridge; this.write = write; }
    static void install(ModernContentBridge bridge, boolean write) { MinecraftForge.EVENT_BUS.addListener(new OuterLandsSmokeProbe(bridge, write)::tick); }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        MinecraftServer server = event.getServer();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Outer Lands acceptance timed out");
            ServerLevel outer = server.getLevel(OUTER);
            if (outer == null) throw new IllegalStateException("Host level mfcl:thaumcraft_outer is missing");
            if (started < 0) {
                proof.put("phase", write ? "outer-write" : "outer-verify");
                legacyDimension(outer);
                if (write) {
                    outer.getChunk(0, 0); // setblock only accepts loaded positions.
                    int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                            "execute in mfcl:thaumcraft_outer run setblock 0 200 0 minecraft:stone");
                    if (result != 1 || !outer.getBlockState(new BlockPos(0, 200, 0)).is(net.minecraft.world.level.block.Blocks.STONE)) throw new IllegalStateException("execute in mfcl:thaumcraft_outer failed: " + result);
                    outer.removeBlock(new BlockPos(0, 200, 0), false);
                    proof.put("executeIn", true);
                    buildMaze();
                }
                portalCell = portalCell();
                proof.put("portalCell", portalCell);
                // Loading the chunks is all a player arriving would do; population follows on later ticks.
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) outer.getChunk(portalCell[0] + dx, portalCell[1] + dz);
                started = outer.getGameTime();
                return;
            }
            if (outer.getGameTime() - started < 60) return;
            var portal = new BlockPos(portalCell[0] * 16 + 8, 53, portalCell[1] * 16 + 8);
            String portalKey = bridge.legacyBlock(outer.getBlockState(portal)).legacyKey();
            proof.put("portalBlock", portalKey);
            if (!portalKey.equals("Thaumcraft:blockPortalEldritch")) throw new IllegalStateException("No eldritch portal at " + portal + ": " + portalKey);
            int solid = 0;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 40; y < 70; y++)
                if (!outer.getBlockState(new BlockPos(portalCell[0] * 16 + x, y, portalCell[1] * 16 + z)).isAir()) solid++;
            proof.put("portalCellBlocks", solid);
            double[] placed = bridge.services().items().inLegacy(() -> {
                Object world = bridge.services().worlds().bound(outer);
                var loader = bridge.services().loader();
                Object teleporter = loader.loadClass("thaumcraft.common.lib.world.dim.TeleporterThaumcraft").getConstructor(loader.loadClass("net.minecraft.world.WorldServer")).newInstance(world);
                return LegacyTravel.place(world, teleporter, portal.getX() + 20.5, 40, portal.getZ() - 7.5, 0);
            });
            proof.put("arrival", placed);
            if (Math.abs(placed[0] - (portal.getX() + .5)) != 1 || placed[1] != portal.getY() || Math.abs(placed[2] - (portal.getZ() + .5)) != 1)
                throw new IllegalStateException("TeleporterThaumcraft placement did not use the portal: " + Arrays.toString(placed));
            if (write) {
                if (!server.saveEverything(false, true, true)) throw new IllegalStateException("World save reported failure");
                var labyrinth = LegacyDimensionHost.saveDirectory(server.overworld()).toPath().resolve("labyrinth.dat");
                if (!Files.isRegularFile(labyrinth)) throw new IllegalStateException("MazeHandler did not save " + labyrinth);
                proof.put("labyrinthSaved", labyrinth.toString());
            } else proof.put("survivedRestart", true);
            proof.put("status", "PASS");
            finish(server, null);
        } catch (Throwable failure) {
            proof.put("status", "FAIL"); proof.put("failure", failure.toString());
            finish(server, failure);
        }
    }
    private void legacyDimension(ServerLevel outer) throws Exception {
        var services = bridge.services(); var loader = services.loader();
        services.items().inLegacy(() -> {
            Object world = services.worlds().bound(outer);
            Object provider = loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
            int id = loader.loadClass("net.minecraft.world.WorldProvider").getField("field_76574_g").getInt(provider);
            int expected = loader.loadClass("thaumcraft.common.config.Config").getField("dimensionOuterId").getInt(null);
            proof.put("legacyDimension", id); proof.put("provider", provider.getClass().getName());
            if (id != expected || !provider.getClass().getName().equals("thaumcraft.common.lib.world.dim.WorldProviderOuter"))
                throw new IllegalStateException("Outer level is legacy dimension " + id + " / " + provider.getClass().getName() + ", expected " + expected);
            return null;
        });
    }
    private void buildMaze() throws Exception {
        var loader = bridge.services().loader();
        bridge.services().items().inLegacy(() -> {
            // TileEldritchAltar.checkForMaze starts the same Runnable on a thread; run it inline so the result is deterministic.
            ((Runnable) loader.loadClass("thaumcraft.common.lib.world.dim.MazeThread").getConstructor(int.class, int.class, int.class, int.class, long.class)
                    .newInstance(MAZE_X, MAZE_Z, 15, 15, 1234L)).run();
            return null;
        });
    }
    private int[] portalCell() throws Exception {
        var loader = bridge.services().loader();
        return bridge.services().items().inLegacy(() -> {
            var labyrinth = (Map<?, ?>) loader.loadClass("thaumcraft.common.lib.world.dim.MazeHandler").getField("labyrinth").get(null);
            var cellType = loader.loadClass("thaumcraft.common.lib.world.dim.Cell"); var locType = loader.loadClass("thaumcraft.common.lib.world.dim.CellLoc");
            proof.put("mazeCells", labyrinth.size());
            for (var entry : labyrinth.entrySet()) {
                Object cell = cellType.getConstructor(short.class).newInstance((Short) entry.getValue());
                if (cellType.getField("feature").getByte(cell) == 1) return new int[]{locType.getField("x").getInt(entry.getKey()), locType.getField("z").getInt(entry.getKey())};
            }
            throw new IllegalStateException("The maze has no portal cell (" + labyrinth.size() + " cells)");
        });
    }
    private void finish(MinecraftServer server, Throwable failure) {
        done = true;
        try {
            var file = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/server-1.7.10-smoke-" + proof.get("phase") + ".json");
            Files.createDirectories(file.getParent()); Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception ignored) {}
        if (failure != null) LogUtils.getLogger().error("MFCL Outer Lands acceptance failed: {}", proof, failure);
        else LogUtils.getLogger().info("MFCL Outer Lands acceptance passed: {}", proof);
        server.halt(false);
    }
}
