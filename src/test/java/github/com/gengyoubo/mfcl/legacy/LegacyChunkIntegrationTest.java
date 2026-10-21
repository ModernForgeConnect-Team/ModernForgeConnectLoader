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
                    verifyManualGeneratorFood(services, host);
                    verifyIc2ExplosionEffect(services, host);
                    verifyGeneratorAndElectricFurnace(services, host,identity);
                    return null;
                });
            } finally { services.worlds().unloaded(identity);services.worlds().unbind(identity); }
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
                for(var command:frame.commands())if(command instanceof LegacyGuiGraphics.Texture texture&&texture.resource().startsWith("ic2:")) {
                    try(var input=loader.getResourceAsStream(LegacyTexturePaths.legacyResource(texture.resource()))) {
                        assertNotNull(input,"Original GUI resource was not resolved: "+texture.resource());
                        assertNotNull(javax.imageio.ImageIO.read(input),texture.resource());
                    }
                }
            }
            x++;
        }
    }

    private static void verifyIc2ExplosionEffect(LegacyServices services,Host host) throws Exception {
        Class<?> network=services.loader().loadClass("ic2.core.network.NetworkManager");
        Object manager=network.getConstructor().newInstance();
        network.getMethod("initiateExplosionEffect",services.loader().loadClass("net.minecraft.world.World"),double.class,double.class,double.class)
                .invoke(manager,host.world,6.25,-60.5,9.75);
        assertEquals(1,host.explosionEffects.size());
        assertArrayEquals(new double[]{6.25,-60.5,9.75},host.explosionEffects.get(0));
    }

    private static void verifyManualGeneratorFood(LegacyServices services,Host host) throws Exception {
        var pos=new Pos(5,-60,7);String key="IC2:blockKineticGenerator";
        host.blocks.put(pos,new HostWorldAccess.Block(key,3));
        Object generator=services.tiles().create(key,3,host.world,pos.x(),pos.y(),pos.z());host.tiles.put(pos,generator);
        var actor=new LegacyTiles.Placer(new UUID(17,29),"ChunkTest",5,-59,7,90,0);
        Class<?> player=services.loader().loadClass("net.minecraft.entity.player.EntityPlayer");
        Object original=services.tiles().actor(host.world,actor,null);
        Object food=player.getMethod("func_71024_bL").invoke(original);
        assertNotNull(food);assertEquals(20,food.getClass().getMethod("func_75116_a").invoke(food));
        var activation=services.logic().activate(key,host.world,5,-60,7,actor,Collections.nCopies(36,null),0,1,.5f,.5f,.5f);
        assertTrue(activation.handled());assertEquals(400,generator.getClass().getField("currentKU").getInt(generator));
        assertEquals(.25f,host.exhaustion);assertNull(activation.device());
        // Fresh callback actors must read current hunger, including the original machine's <=6 refusal rule.
        host.food=new HostWorldAccess.Food(6,0);
        var hungry=services.logic().activate(key,host.world,5,-60,7,actor,Collections.nCopies(36,null),0,1,.5f,.5f,.5f);
        assertFalse(hungry.handled());assertEquals(400,generator.getClass().getField("currentKU").getInt(generator));
        assertEquals(.25f,host.exhaustion);
        assertEquals(6,food.getClass().getMethod("func_75116_a").invoke(food),"Existing FoodStats must refresh host hunger");
        food.getClass().getMethod("func_75122_a",int.class,float.class).invoke(food,4,.5f);
        assertEquals(new HostWorldAccess.Food(10,4),host.food);
        // Client callbacks can read hunger, while exhaustion remains server-authoritative.
        host.client=true;
        try {food.getClass().getMethod("func_75113_a",float.class).invoke(food,1f);assertEquals(.25f,host.exhaustion);}
        finally {host.client=false;host.food=new HostWorldAccess.Food(20,5);}
    }

    private static void verifyGeneratorAndElectricFurnace(LegacyServices services,Host host,Object identity) throws Exception {
        host.networkPlayers=true;
        var generatorPos=new Pos(20,-60,20);var furnacePos=new Pos(21,-60,20);
        host.blocks.put(generatorPos,new HostWorldAccess.Block("IC2:blockGenerator",0));
        host.blocks.put(furnacePos,new HostWorldAccess.Block("IC2:blockMachine",2));
        Object generator=services.tiles().create("IC2:blockGenerator",0,host.world,20,-60,20);
        Object furnace=services.tiles().create("IC2:blockMachine",2,host.world,21,-60,20);
        host.tiles.put(generatorPos,generator);host.tiles.put(furnacePos,furnace);
        var loader=services.loader();var stack=loader.loadClass("net.minecraft.item.ItemStack");var slot=loader.loadClass("ic2.core.block.invslot.InvSlot");
        assertSame(host.world,loader.loadClass("net.minecraftforge.common.DimensionManager").getMethod("getWorld",int.class).invoke(null,0));
        Object fuel=generator.getClass().getField("fuelSlot").get(generator);
        slot.getMethod("put",stack).invoke(fuel,services.items().materialize(new LegacyClientData.Stack("minecraft:coal",0,2,"","")));
        Object input=furnace.getClass().getField("inputSlot").get(furnace);
        slot.getMethod("put",stack).invoke(input,services.items().materialize(new LegacyClientData.Stack("minecraft:iron_ore",0,1,"","")));
        assertEquals(false,generator.getClass().getMethod("canUpdate").invoke(generator),"Exercise IC2's own tick subscriptions");
        var activation=services.logic().activate("IC2:blockGenerator",host.world,20,-60,20,
                new LegacyTiles.Placer(new UUID(17,29),"ChunkTest",20,-59,20,0,0),Collections.nCopies(36,null),0,1,.5f,.5f,.5f);
        assertNotNull(activation.device());LegacyDeviceContainers.attachViewer(activation.device(),7);
        boolean receivedEnergy=false;int ticks=0;
        Object output=null;
        for(;ticks<500&&output==null;ticks++) {
            host.time++;
            services.worlds().tick(identity,true);
            for(Object tile:new ArrayList<>(host.tiles.values()))services.logic().tick(tile);
            services.worlds().tick(identity,false);
            receivedEnergy|=furnace.getClass().getField("energy").getDouble(furnace)>0;
            activation.device().container().getClass().getMethod("func_75142_b").invoke(activation.device().container());
            output=slot.getMethod("get").invoke(furnace.getClass().getField("outputSlot").get(furnace));
        }
        assertTrue(generator.getClass().getField("addedToEnergyNet").getBoolean(generator),"Generator did not complete onLoaded");
        System.out.println("MFCL_IC2_MACHINE_STATE fuel="+generator.getClass().getField("fuel").getInt(generator)+" storage="+generator.getClass().getField("storage").getDouble(generator)+" energy="+furnace.getClass().getField("energy").getDouble(furnace)+" output="+output+" received="+receivedEnergy+" ticks="+ticks);
        assertTrue(generator.getClass().getField("itemFuelTime").getInt(generator)>0,"Coal was not burned");
        Object remaining=slot.getMethod("get").invoke(fuel);assertEquals(1,stack.getField("field_77994_a").getInt(remaining));
        assertTrue(receivedEnergy,"IC2 EU graph did not deliver energy to the adjacent furnace");
        assertNotNull(output,"Electric furnace did not finish its recipe after "+ticks+" ticks");
        assertEquals("minecraft:iron_ingot",services.items().snapshot(output).key());
        assertTrue(host.packets.stream().anyMatch(packet->packet.channel().equals("mfcl:ic2")),"Original machine GUI fields were not sent over host transport");
        Object gateway=loader.loadClass("ic2.core.IC2").getField("network").get(null),network=gateway.getClass().getMethod("get").invoke(gateway);
        network.getClass().getMethod("onTickEnd",loader.loadClass("net.minecraft.world.World")).invoke(network,host.world);
        verifyMachineClientFields(services,host,generator);
        var active=(List<?>)loader.loadClass("net.minecraft.world.World").getField("field_147482_g").get(host.world);
        assertTrue(active.contains(generator));services.tiles().invalidate(generator);host.tiles.remove(generatorPos);
        host.time++;services.worlds().tick(identity,true);services.worlds().tick(identity,false);
        assertFalse(active.contains(generator),"Removed generator retained its tick subscription");
        assertFalse(services.logic().tick(generator));
        System.out.println("MFCL_IC2_MACHINE_CHAIN_PASS ticks="+ticks);
    }

    private static void verifyMachineClientFields(LegacyServices services,Host server,Object generator) throws Exception {
        String thread=Thread.currentThread().getName();Thread.currentThread().setName("Render thread");
        var loader=services.loader();var minecraft=loader.loadClass("net.minecraft.client.Minecraft");
        Object client=minecraft.getMethod("func_71410_x").invoke(null),priorWorld=minecraft.getField("field_71441_e").get(client),priorPlayer=minecraft.getField("field_71439_g").get(client);
        var host=new Host(services);host.client=true;host.blocks.putAll(server.blocks);Object identity=new Object();
        host.world=services.worlds().bind(identity,host);
        try {
            Object tile=services.tiles().create("IC2:blockGenerator",0,host.world,20,-60,20);host.tiles.put(new Pos(20,-60,20),tile);
            Object player=services.tiles().actor(host.world,new LegacyTiles.Placer(new UUID(17,29),"ChunkTest",20,-59,20,0,0),null);
            minecraft.getField("field_71441_e").set(client,host.world);minecraft.getField("field_71439_g").set(client,player);
            for(var packet:server.packets)LegacyPackets.receiveClient(services,packet);
            services.worlds().tick(identity,true);
            // ContainerBaseGenerator synchronizes fuel/storage; itemFuelTime is derived locally by gaugeFuelScaled.
            for(String field:List.of("fuel","storage"))assertEquals(generator.getClass().getField(field).get(generator),tile.getClass().getField(field).get(tile),"Original client decoder did not synchronize "+field);
            assertTrue((int)tile.getClass().getMethod("gaugeFuelScaled",int.class).invoke(tile,14)>0,"Original GUI fuel gauge remained empty");
            assertEquals(generator.getClass().getMethod("getActive").invoke(generator),tile.getClass().getMethod("getActive").invoke(tile),"Machine activity was not synchronized to nearby players");
            services.logic().tick(tile);services.worlds().tick(identity,false);
            assertSame(server.world,loader.loadClass("net.minecraftforge.common.DimensionManager").getMethod("getWorld",int.class).invoke(null,0),"Client binding replaced the authoritative dimension");
        } finally {
            services.worlds().unloaded(identity);services.worlds().unbind(identity);
            minecraft.getField("field_71441_e").set(client,priorWorld);minecraft.getField("field_71439_g").set(client,priorPlayer);
            Thread.currentThread().setName(thread);
        }
    }

    private record Pos(int x, int y, int z) {}
    private static final class Host implements HostWorldAccess {
        final LegacyServices services;
        final Map<Pos, Block> blocks = new HashMap<>();
        final Map<Pos, Object> tiles = new HashMap<>();
        final List<double[]> explosionEffects = new ArrayList<>();
        Food food=new Food(20,5);
        float exhaustion;
        boolean client;
        boolean networkPlayers;
        long time;
        final List<LegacyPackets.Message> packets=new ArrayList<>();
        Object world;
        Host(LegacyServices services) { this.services = services; }
        public String dimensionKey() { return "mfcl:chunk-test"; }
        public int legacyDimensionId() { return 0; }
        public boolean clientSide() { return client; }
        public Food food(UUID id) { return food; }
        public void setFood(UUID id,Food value) { food=value; }
        public void addExhaustion(UUID id,float amount) { exhaustion+=amount; }
        public long gameTime() { return time; }
        public List<HostWorldAccess.Entity> players() {
            return networkPlayers?List.of(new HostWorldAccess.Entity(new UUID(17,29),"net.minecraft.entity.player.EntityPlayerMP","ChunkTest",20,-59,20,0,0,0,0,.6f,1.8f,false,true)):List.of();
        }
        public void sendPacket(UUID recipient,LegacyPackets.Message packet) { packets.add(packet); }
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
        public void explosionEffect(double x,double y,double z) { explosionEffects.add(new double[]{x,y,z}); }
        public int height(int x, int z) { return 320; }
        public boolean canSeeSky(int x, int y, int z) { return y >= 320; }
    }
}
