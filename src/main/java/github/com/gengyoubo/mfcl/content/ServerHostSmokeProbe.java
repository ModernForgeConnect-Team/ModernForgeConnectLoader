package github.com.gengyoubo.mfcl.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless dedicated-server acceptance, run as two separate server launches: {@code write} places original tiles and
 * inventories, lets them tick, saves and stops; {@code verify} restarts on the same world and checks the reloaded state.
 * It references no client classes, so a successful run also shows that this path is safe without a graphical environment.
 */
public final class ServerHostSmokeProbe {
    private static final String ESSENCE_TAG = "{Aspects:[{key:aer,amount:3}]}";
    private record State(int x, int y, int z) {}
    private final ModernContentBridge bridge;
    private final boolean write;
    private final Map<String, Object> proof = new LinkedHashMap<>();
    private final long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.MINUTES.toNanos(5);
    private BlockPos chest;
    private long started = -1;
    private boolean done;

    private ServerHostSmokeProbe(ModernContentBridge bridge, boolean write) { this.bridge = bridge; this.write = write; }

    public static void install(ModernContentBridge bridge, String phase) {
        if (phase.equals("outer-write") || phase.equals("outer-verify")) { OuterLandsSmokeProbe.install(bridge, phase.equals("outer-write")); return; }
        if (!phase.equals("write") && !phase.equals("verify")) throw new IllegalArgumentException("mfcl.test.server must be write or verify: " + phase);
        MinecraftForge.EVENT_BUS.addListener(new ServerHostSmokeProbe(bridge, phase.equals("write"))::tick);
    }

    private void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        MinecraftServer server = event.getServer();
        try {
            if (System.nanoTime() > deadline) throw new IllegalStateException("Server acceptance timed out");
            ServerLevel level = server.overworld();
            if (started < 0) {
                proof.put("dedicated", server.isDedicatedServer());
                proof.put("phase", write ? "write" : "verify");
                if (write) setup(level); else chest = read(server);
                started = level.getGameTime();
                return;
            }
            if (level.getGameTime() - started < 100) return;
            verify(level);
            if (write) {
                if (!server.saveEverything(false, true, true)) throw new IllegalStateException("World save reported failure");
                proof.put("saved", true);
                Files.writeString(state(server), new Gson().toJson(new State(chest.getX(), chest.getY(), chest.getZ())));
            } else proof.put("stateSurvivedRestart", true);
            proof.put("status", "PASS");
            finish(server, null);
        } catch (Throwable failure) {
            proof.put("status", "FAIL");
            proof.put("failure", failure.toString());
            finish(server, failure);
        }
    }

    private void setup(ServerLevel level) throws Exception {
        var spawn = level.getSharedSpawnPos();
        chest = new BlockPos(spawn.getX(), level.getMaxBuildHeight() - 20, spawn.getZ());
        level.setBlockAndUpdate(chest, bridge.modernBlock(new HostWorldAccess.Block("Thaumcraft:blockChestHungry", 0)));
        var tile = (LegacyBlockEntity) level.getBlockEntity(chest);
        tile.legacy();
        var handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        if (!handler.insertItem(0, new ItemStack(Items.STICK, 5), false).isEmpty()) throw new IllegalStateException("Original inventory rejected a native item");
        if (!handler.insertItem(1, essence(), false).isEmpty()) throw new IllegalStateException("Original inventory rejected an original item");
        level.setBlockAndUpdate(chest.east(), Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) level.getBlockEntity(chest.east())).setItem(0, essence());
    }

    private void verify(ServerLevel level) throws Exception {
        if (!(level.getBlockEntity(chest) instanceof LegacyBlockEntity tile)) throw new IllegalStateException("Original tile missing at " + chest);
        if (tile.logicSuspended()) throw new IllegalStateException("Original tile logic suspended on the dedicated server");
        var handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        var sticks = handler.getStackInSlot(0);
        if (!sticks.is(Items.STICK) || sticks.getCount() != 5) throw new IllegalStateException("Native item in original inventory changed: " + sticks);
        expectEssence(handler.getStackInSlot(1), "original inventory");
        if (!(level.getBlockEntity(chest.east()) instanceof ChestBlockEntity host)) throw new IllegalStateException("Host chest missing");
        expectEssence(host.getItem(0), "host chest");
        proof.put("originalTile", tile.legacy().getClass().getName());
        proof.put("originalInventory", true);
        proof.put("originalItemNbtInHostInventory", true);
    }

    private ItemStack essence() {
        var stack = bridge.stack("Thaumcraft:ItemEssence", 1, 2, ESSENCE_TAG);
        if (stack.isEmpty()) throw new IllegalStateException("Thaumcraft:ItemEssence is not registered");
        return stack;
    }

    private void expectEssence(ItemStack stack, String where) throws Exception {
        var old = bridge.gameplayStack(stack);
        if (old == null || !old.key().equals("Thaumcraft:ItemEssence") || old.metadata() != 1 || old.count() != 2 || !old.tag().contains("aer"))
            throw new IllegalStateException("Original item changed in " + where + ": " + old);
    }

    private BlockPos read(MinecraftServer server) throws Exception {
        var path = state(server);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Run the write phase first; missing " + path);
        var saved = new Gson().fromJson(Files.readString(path), State.class);
        return new BlockPos(saved.x(), saved.y(), saved.z());
    }

    private static Path state(MinecraftServer server) throws Exception {
        var path = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/server-smoke-state.json");
        Files.createDirectories(path.getParent());
        return path;
    }

    private void finish(MinecraftServer server, Throwable failure) {
        done = true;
        try {
            var subscriptions = github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.subscriptions();
            var diagnostics = new LinkedHashMap<String, Object>();
            diagnostics.put("eventTracking", github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.eventTracking());
            diagnostics.put("eventHandlers", subscriptions.size());
            diagnostics.put("undeliveredEventHandlers", subscriptions.stream().filter(s -> !s.delivered()).count());
            diagnostics.put("missingMappings", github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.findings(github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.Kind.MISSING_MAPPING).size());
            diagnostics.put("legacyFailures", github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.findings(github.com.gengyoubo.mfcl.bridge.LegacyDiagnostics.Kind.LEGACY_EXCEPTION).size());
            proof.put("diagnostics", diagnostics);
        } catch (RuntimeException unavailable) { proof.put("diagnostics", unavailable.toString()); }
        try {
            var path = FMLPaths.GAMEDIR.get().resolve("logs/mfcl/server-1.7.10-smoke-" + (write ? "write" : "verify") + ".json");
            Files.createDirectories(path.getParent());
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(proof));
        } catch (Exception ignored) { }
        if (failure == null) LogUtils.getLogger().info("MFCL_SERVER_SMOKE_PASS: {}", proof);
        else LogUtils.getLogger().error("MFCL_SERVER_SMOKE_FAIL: " + proof, failure);
        server.halt(false);
    }
}
