package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.HostWorldAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import java.util.*;

/** Server levels own all authoritative mutations; the old World façade keeps its owner-thread checks. */
public final class LevelWorldBridge {
    private final ModernContentBridge content;
    private final Map<String, Integer> dimensions = new HashMap<>(Map.of("minecraft:overworld", 0, "minecraft:the_nether", -1, "minecraft:the_end", 1));
    private LevelWorldBridge(ModernContentBridge content) { this.content = content; }
    public static void install(ModernContentBridge content) {
        var bridge = new LevelWorldBridge(content);
        MinecraftForge.EVENT_BUS.addListener(bridge::load); MinecraftForge.EVENT_BUS.addListener(bridge::unload);
    }
    private void load(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String key = level.dimension().location().toString();
        int dimension = dimensions.computeIfAbsent(key, ignored -> dimensions.size() + 2);
        try { content.services().worlds().bind(level, access(level, dimension)); }
        catch (Exception error) { throw new IllegalStateException("Cannot bind legacy server world " + key, error); }
    }
    private void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) content.services().worlds().unbind(level);
    }
    private HostWorldAccess access(ServerLevel level, int dimension) {
        return new HostWorldAccess() {
            private void check() {
                if (!level.getServer().isSameThread()) throw new IllegalStateException("Legacy server world requires the server thread");
            }
            @Override public String dimensionKey() { return level.dimension().location().toString(); }
            @Override public int legacyDimensionId() { return dimension; }
            @Override public boolean clientSide() { return false; }
            @Override public long gameTime() { check(); return level.getGameTime(); }
            @Override public long dayTime() { check(); return level.getDayTime(); }
            @Override public void setDayTime(long time) { check(); level.setDayTime(time); }
            @Override public Block block(int x, int y, int z) { check(); return content.legacyBlock(level.getBlockState(new BlockPos(x, y, z))); }
            @Override public boolean setBlock(int x, int y, int z, Block block, int flags) {
                check(); return level.setBlock(new BlockPos(x, y, z), content.modernBlock(block), flags);
            }
            @Override public Object tileEntity(int x, int y, int z) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (tile == null) return null;
                if (tile instanceof LegacyBlockEntity legacy) return legacy.legacy();
                throw new UnsupportedOperationException("Modern block entity has no old representation: " + tile.getType());
            }
            @Override public void setTileEntity(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (!(tile instanceof LegacyBlockEntity legacy)) throw new UnsupportedOperationException("No legacy tile envelope at requested position");
                legacy.replace(old); legacy.synchronize();
            }
            @Override public void removeTileEntity(int x, int y, int z) { check(); level.removeBlockEntity(new BlockPos(x, y, z)); }
            @Override public void markTileDirty(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (!(tile instanceof LegacyBlockEntity legacy) || legacy.legacy() != old) throw new IllegalStateException("Legacy tile identity mismatch");
                legacy.synchronize();
            }
            @Override public void blockUpdated(int x, int y, int z) {
                check(); BlockPos pos = new BlockPos(x, y, z); var state = level.getBlockState(pos);
                level.sendBlockUpdated(pos, state, state, 3);
            }
        };
    }
}
