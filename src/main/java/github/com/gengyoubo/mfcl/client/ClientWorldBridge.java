package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** Binds the actual host level to a typed old client world. Client bindings never mutate server authority. */
public final class ClientWorldBridge {
    private final LegacyServices services;
    private final Map<String, Integer> dimensions = new HashMap<>(Map.of("minecraft:overworld", 0, "minecraft:the_nether", -1, "minecraft:the_end", 1));
    private Level current;
    private ClientWorldBridge(LegacyServices services) { this.services = services; }
    public static void install(LegacyServices services) {
        var bridge = new ClientWorldBridge(services); MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Level next = Minecraft.getInstance().level;
        if (next == current) return;
        try {
            if (current != null) services.worlds().unbind(current);
            current = null;
            Object oldWorld = null;
            if (next != null) {
                String dimension = next.dimension().location().toString();
                int oldDimension = dimensions.computeIfAbsent(dimension, key -> dimensions.size() + 2);
                oldWorld = services.worlds().bind(next, new HostWorldAccess() {
                    @Override public String dimensionKey() { return dimension; }
                    @Override public int legacyDimensionId() { return oldDimension; }
                    @Override public boolean clientSide() { return true; }
                    @Override public long gameTime() { return next.getGameTime(); }
                    @Override public long dayTime() { return next.getDayTime(); }
                    @Override public void setDayTime(long time) { throw new UnsupportedOperationException("Client world cannot change authoritative time"); }
                    @Override public Block block(int x, int y, int z) {
                        var state = next.getBlockState(new BlockPos(x, y, z));
                        var content = github.com.gengyoubo.mfcl.content.ModernContentBridge.active();
                        if (content != null) return content.legacyBlock(state);
                        if (!state.equals(state.getBlock().defaultBlockState())) throw new UnsupportedOperationException("Block state requires a legacy metadata mapping: " + state);
                        String key = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                        if (key.equals("minecraft:grass_block")) key = "minecraft:grass";
                        return new Block(key, 0);
                    }
                    @Override public boolean setBlock(int x, int y, int z, Block block, int flags) { throw new UnsupportedOperationException("Client world cannot change authoritative blocks"); }
                });
                current = next;
            }
            var mc = services.loader().loadClass("net.minecraft.client.Minecraft");
            mc.getField("field_71441_e").set(mc.getMethod("func_71410_x").invoke(null), oldWorld);
        } catch (Exception failure) { throw new IllegalStateException("Cannot bind legacy client world", failure); }
    }
}
