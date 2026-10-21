package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Original IC2 placement and direct Chunk access, including modern heights outside 0..255. */
class LegacyChunkIntegrationTest {
    @TempDir Path temporary;

    @Test void forwardsChunkAccessAndPlacesIc2MachinesBelowZero() throws Exception {
        String support = System.getProperty("mfcl.test.legacySupportDir", "");
        String ic2 = System.getProperty("mfcl.test.ic2Jar", "");
        assumeTrue(!support.isBlank() && !ic2.isBlank(), "Supply -PlegacySupportDir and -Pic2Jar");
        Path mods = Files.createDirectories(temporary.resolve("mods/1.7.10"));
        Files.copy(Path.of(ic2), mods.resolve(Path.of(ic2).getFileName()));
        Path runtime = Path.of(support);
        if (Files.isDirectory(runtime.resolve("runtime"))) runtime = runtime.resolve("runtime");
        Path log = temporary.resolve("chunks.log");
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("mfcl.test.runtimeClasspath"),
                getClass().getName(), temporary.toString(), runtime.toAbsolutePath().toString())
                .directory(temporary.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(180, TimeUnit.SECONDS), ()->"Chunk/IC2 placement probe timed out: "+readLog(log));
            String output = Files.readString(log);
            assertEquals(0, process.exitValue(), output);
            assertTrue(output.contains("MFCL_CHUNK_PLACEMENT_PASS"), output);
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(10, TimeUnit.SECONDS); }
        }
    }

    private static String readLog(Path log) { try{return Files.readString(log);}catch(Exception failure){return failure.toString();} }

    public static void main(String[] args) throws Exception {
        var service = new LegacyRuntimeService();
        var report = service.start(Path.of(args[0]), Path.of(args[1]));
        try (var runtime = service.runtime()) {
            assertTrue(report.legacyFmlStarted(), report.toString());
            var services = runtime.services();
            // Legacy FML identifies the logical server by this thread name.
            Thread.currentThread().setName("Server thread");
            var host = new Host(services);
            Object identity = new Object();
            host.world = services.worlds().bind(identity, host);
            try {
                services.items().inLegacy(() -> {
                    verifyChunkAccess(services, host);
                    verifyIc2Placement(services, host);
                    return null;
                });
            } finally { services.worlds().unbind(identity); }
            System.out.println("MFCL_CHUNK_PLACEMENT_PASS");
        }
    }

    private static void verifyChunkAccess(LegacyServices services, Host host) throws Exception {
        var loader = services.loader();
        Class<?> chunk = loader.loadClass("net.minecraft.world.chunk.Chunk");
        Class<?> block = loader.loadClass("net.minecraft.block.Block");
        Class<?> tile = loader.loadClass("net.minecraft.tileentity.TileEntity");
        var registry = block.getField("field_149771_c").get(null);
        Object stone = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, "minecraft:stone");
        // Negative chunk coordinates exercise local-to-world conversion, independently of vertical indexing.
        int x = -17, z = 31;
        Object view = LegacyWorlds.chunk(host.world, x >> 4, z >> 4);
        assertTrue(chunk.isInstance(view));
        assertSame(host.world, chunk.getField("field_76637_e").get(view));
        for (int y : new int[]{-64, -61, -1, 0, 255, 256, 319}) {
            var pos = new Pos(x, y, z);
            host.blocks.put(pos, new HostWorldAccess.Block("minecraft:stone", 3));
            assertSame(stone, chunk.getMethod("func_150810_a", int.class, int.class, int.class).invoke(view, x & 15, y, z & 15));
            assertEquals(3, chunk.getMethod("func_76628_c", int.class, int.class, int.class).invoke(view, x & 15, y, z & 15));
            assertEquals(true, chunk.getMethod("func_76589_b", int.class, int.class, int.class, int.class).invoke(view, x & 15, y, z & 15, 7));
            assertEquals(7, host.blocks.get(pos).metadata());
            assertEquals(true, chunk.getMethod("func_150807_a", int.class, int.class, int.class, block, int.class).invoke(view, x & 15, y, z & 15, stone, 2));
            assertEquals(new HostWorldAccess.Block("minecraft:stone", 2), host.blocks.get(pos));
            Object chest = loader.loadClass("net.minecraft.tileentity.TileEntityChest").getConstructor().newInstance();
            chunk.getMethod("func_150812_a", int.class, int.class, int.class, tile).invoke(view, x & 15, y, z & 15, chest);
            assertSame(chest, host.tiles.get(pos));
            for (String getter : List.of("func_150806_e", "getTileEntityUnsafe"))
                assertSame(chest, chunk.getMethod(getter, int.class, int.class, int.class).invoke(view, x & 15, y, z & 15));
            chunk.getMethod("func_150805_f", int.class, int.class, int.class).invoke(view, x & 15, y, z & 15);
            assertFalse(host.tiles.containsKey(pos));
            // A facade obtained before a host write must see the new block immediately.
            host.blocks.put(pos, new HostWorldAccess.Block("minecraft:dirt", 0));
            assertNotSame(stone, chunk.getMethod("func_150810_a", int.class, int.class, int.class).invoke(view, x & 15, y, z & 15));
        }
        assertEquals(320, chunk.getMethod("func_76611_b", int.class, int.class).invoke(view, x & 15, z & 15));
        assertEquals(true, chunk.getMethod("func_76619_d", int.class, int.class, int.class).invoke(view, x & 15, 320, z & 15));
    }

    private static void verifyIc2Placement(LegacyServices services, Host host) throws Exception {
        var loader = services.loader();
        var block = loader.loadClass("net.minecraft.block.Block");
        var registry = block.getField("field_149771_c").get(null);
        var ownTile = loader.loadClass("ic2.core.block.BlockMultiID").getMethod("getOwnTe",
                loader.loadClass("net.minecraft.world.IBlockAccess"), int.class, int.class, int.class);
        int x = 6;
        for (String key : List.of("IC2:blockGenerator", "IC2:blockKineticGenerator")) {
            var ground = new Pos(x, -62, 9);
            var target = new Pos(x, -61, 9);
            host.blocks.put(ground, new HostWorldAccess.Block("minecraft:stone", 0));
            var stack = new LegacyClientData.Stack(key, 0, 2, "", "");
            var result = new LegacyItemPlacement(services).use(host.world,
                    new LegacyTiles.Placer(new UUID(17, 29), "ChunkTest", x, -60, 9, 90, 0), stack,
                    Collections.singletonList(stack), 0, x, -62, 9, 1, .5f, 1, .5f);
            assertTrue(result.used(), key);
            assertEquals(key, host.blocks.get(target).legacyKey());
            assertEquals(1, result.stack().count());
            Object machine = registry.getClass().getMethod("func_82594_a", String.class).invoke(registry, key);
            Object expected = host.tiles.get(target);
            assertNotNull(expected, key);
            assertSame(expected, ownTile.invoke(machine, host.world, x, -61, 9));
            assertEquals((short) 5, loader.loadClass("ic2.api.tile.IWrenchable").getMethod("getFacing").invoke(expected));
            var activation=services.logic().activate(key,host.world,x,-61,9,
                    new LegacyTiles.Placer(new UUID(17,29),"ChunkTest",x,-60,9,90,0),
                    Collections.nCopies(36,null),0,1,.5f,.5f,.5f);
            assertTrue(activation.handled());assertNotNull(activation.device(),"IC2 GUI was not captured");
            var opened=activation.device();
            assertEquals("mfcl:provider/ic2",opened.descriptor().mod());
            assertTrue(LegacyDeviceContainers.slots(opened.container()).size()>=36);
            services.containers().preparePlayer();
            Object gui=LegacyDeviceContainers.clientGui(services,opened.descriptor(),opened.player(),host.world);
            assertTrue(loader.loadClass("net.minecraft.client.gui.inventory.GuiContainer").isInstance(gui));
            try(var frame=LegacyGuiGraphics.begin(320,240,0,0,Set.of());var screen=services.screens().open(gui,320,240)) {
                screen.draw(0,0,0);
                assertTrue(frame.commands().stream().anyMatch(command->command instanceof LegacyGuiGraphics.Texture texture&&texture.resource().startsWith("ic2:")),"IC2 GUI background was not drawn");
            }
            x++;
        }
    }

    private record Pos(int x, int y, int z) {}
    private static final class Host implements HostWorldAccess {
        final LegacyServices services;
        final Map<Pos, Block> blocks = new HashMap<>();
        final Map<Pos, Object> tiles = new HashMap<>();
        Object world;
        Host(LegacyServices services) { this.services = services; }
        public String dimensionKey() { return "mfcl:chunk-test"; }
        public int legacyDimensionId() { return 0; }
        public boolean clientSide() { return false; }
        public long gameTime() { return 0; }
        public long dayTime() { return 0; }
        public void setDayTime(long time) {}
        public Block block(int x, int y, int z) { return blocks.getOrDefault(new Pos(x, y, z), new Block("minecraft:air", 0)); }
        public boolean setBlock(int x, int y, int z, Block value, int flags) {
            var pos = new Pos(x, y, z);
            blocks.put(pos, value);
            if (!tiles.containsKey(pos)) {
                try {
                    Object tile = services.tiles().create(value.legacyKey(), value.metadata(), world, x, y, z);
                    if (tile != null) tiles.put(pos, tile);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }
            return true;
        }
        public Object tileEntity(int x, int y, int z) { return tiles.get(new Pos(x, y, z)); }
        public void setTileEntity(int x, int y, int z, Object tile) { tiles.put(new Pos(x, y, z), tile); }
        public void removeTileEntity(int x, int y, int z) { tiles.remove(new Pos(x, y, z)); }
        public void markTileDirty(int x, int y, int z, Object tile) {}
        public void blockUpdated(int x, int y, int z) {}
        public int height(int x, int z) { return 320; }
        public boolean canSeeSky(int x, int y, int z) { return y >= 320; }
    }
}
