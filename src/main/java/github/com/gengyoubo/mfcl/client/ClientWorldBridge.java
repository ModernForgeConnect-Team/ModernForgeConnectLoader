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
    private static ClientWorldBridge active;
    private final LegacyServices services;
    private final Map<String, Integer> dimensions = new HashMap<>(Map.of("minecraft:overworld", 0, "minecraft:the_nether", -1, "minecraft:the_end", 1));
    private Level current;
    private ClientWorldBridge(LegacyServices services) { this.services = services; }
    public static void install(LegacyServices services) {
        var bridge = new ClientWorldBridge(services); active=bridge;MinecraftForge.EVENT_BUS.addListener(bridge::tick);
    }
    /** A loaded level can render before the first client tick, especially after reopening a save. */
    static Object world(LegacyServices services) throws Exception {
        if(active==null||active.services!=services)throw new IllegalStateException("Client world bridge is not installed");
        return services.items().inLegacy(()->{active.refresh();return services.worlds().bound(active.current);});
    }
    private void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {services.items().inLegacy(()->{refresh();return null;});}
        catch(Exception failure){throw new IllegalStateException("Cannot refresh legacy client world",failure);}
    }
    private void refresh() {
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
                    @Override public void playSound(Sound sound) {
                        if(sound.excludedPlayer()!=null&&Minecraft.getInstance().player!=null&&sound.excludedPlayer().equals(Minecraft.getInstance().player.getUUID()))return;
                        var id=net.minecraft.resources.ResourceLocation.parse(LegacySounds.modernId(sound.id()));
                        next.playLocalSound(sound.x(),sound.y(),sound.z(),net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.MASTER,sound.volume(),sound.pitch(),sound.distanceDelay());
                    }
                    @Override public long gameTime() { return next.getGameTime(); }
                    @Override public long dayTime() { return next.getDayTime(); }
                    @Override public boolean powered(int x, int y, int z) { return next.hasNeighborSignal(new BlockPos(x,y,z)); }
                    @Override public int power(int x, int y, int z, int side) { return next.getSignal(new BlockPos(x,y,z),net.minecraft.core.Direction.from3DDataValue(side)); }
                    @Override public boolean loaded(int x, int y, int z) { return next.isInWorldBounds(new BlockPos(x,y,z)) && next.hasChunkAt(new BlockPos(x,y,z)); }
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
                    @Override public HostWorldAccess.Hit rayTrace(double sx, double sy, double sz, double ex, double ey, double ez) {
                        var from = new net.minecraft.world.phys.Vec3(sx, sy, sz); var to = new net.minecraft.world.phys.Vec3(ex, ey, ez);
                        var result = next.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
                        if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
                        var pos = result.getBlockPos();
                        return new HostWorldAccess.Hit(pos.getX(), pos.getY(), pos.getZ(), result.getDirection().get3DDataValue(), result.getLocation().x, result.getLocation().y, result.getLocation().z);
                    }
                    @Override public Object tileEntity(int x, int y, int z) {
                        var tile = next.getBlockEntity(new BlockPos(x, y, z));
                        // A host block entity without an old counterpart is simply invisible to legacy code.
                        if (tile instanceof github.com.gengyoubo.mfcl.content.LegacyBlockEntity legacy) return legacy.legacy();
                        return null;
                    }
                    @Override public void markTileDirty(int x, int y, int z, Object old) {
                        // readCustomNBT may mark a client tile dirty while its host envelope is still loading.
                        // This updates the local model only; it must not request server mutation or recurse into legacy().
                        if (next.getBlockEntity(new BlockPos(x,y,z)) instanceof github.com.gengyoubo.mfcl.content.LegacyBlockEntity tile) {
                            tile.setChanged(); tile.requestModelDataUpdate();
                        }
                    }
                    @Override public void blockUpdated(int x, int y, int z) {
                        var pos = new BlockPos(x,y,z); var state = next.getBlockState(pos);
                        next.sendBlockUpdated(pos,state,state,3);
                    }
                });
                current = next;
            }
            var mc = services.loader().loadClass("net.minecraft.client.Minecraft");
            mc.getField("field_71441_e").set(mc.getMethod("func_71410_x").invoke(null), oldWorld);
        } catch (Exception failure) { throw new IllegalStateException("Cannot bind legacy client world", failure); }
    }
}

