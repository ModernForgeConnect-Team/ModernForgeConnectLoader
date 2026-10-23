package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.bridge.*;
import github.com.gengyoubo.mfcl.content.ModernContentBridge;
import github.com.gengyoubo.mfcl.internal.api.LegacyApiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import java.util.*;
import java.util.function.Function;

/**
 * {@link LegacyScenes} on the physical client: each scene binds its own client façade world over an in-memory cell map,
 * so original formation code and tile renderers see a normal old World while the host world is never touched.
 */
public final class ApiScenes implements LegacyScenes {
    private static final HostWorldAccess.Block AIR = new HostWorldAccess.Block("minecraft:air", 0);
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Original tile renderers see the camera this far from the scene centre, along the requested view direction. */
    private static final double EYE_DISTANCE = 8;
    private final LegacyApiRuntime runtime;
    private final ModernContentBridge content;
    private LegacyBlockEntityRenderer replay;

    public ApiScenes(LegacyApiRuntime runtime, ModernContentBridge content) { this.runtime = runtime; this.content = content; }

    @Override public LegacyScene create() {
        return runtime.call("SCENE", "create", () -> { requireClientThread(); return new Scene(); });
    }

    private static void requireClientThread() {
        if (!LocalPlayerAccess.onClientThread()) throw new IllegalStateException("Legacy scenes belong to the client thread");
    }

    private LegacyBlockEntityRenderer replay() {
        if (replay == null) replay = new LegacyBlockEntityRenderer(content);
        return replay;
    }

    private final class Scene implements LegacyScene {
        private record Cell(int x, int y, int z) { }
        /** What a cell draws until the scene changes: a host model, original block geometry, or nothing. */
        private record Static(Cell cell, BlockState host, List<LegacyVertexStream.Primitive> geometry) { }
        private final Object identity = new Object();
        private final Map<Cell, HostWorldAccess.Block> cells = new HashMap<>();
        private final Map<Cell, Object> tiles = new HashMap<>();
        private final Set<Cell> failedTiles = new HashSet<>();
        private final Object world;
        private final java.lang.reflect.Field remote;
        private boolean closed;
        private int version, built = -1;
        private List<Static> statics = List.of();

        Scene() throws Exception {
            var services = content.services();
            remote = services.loader().loadClass("net.minecraft.world.World").getField("field_72995_K");
            world = services.items().inLegacy(() -> services.worlds().bind(identity, new Host()));
        }

        /** The writable world contract: cells and tiles live here, every host side effect is dropped. */
        private final class Host implements HostWorldAccess {
            public String dimensionKey() { return "mfcl:scene"; }
            public int legacyDimensionId() { return 0; }
            public boolean clientSide() { return true; }
            public long gameTime() { var level = Minecraft.getInstance().level; return level == null ? 0 : level.getGameTime(); }
            public long dayTime() { var level = Minecraft.getInstance().level; return level == null ? 6000 : level.getDayTime(); }
            public void setDayTime(long time) { }
            public Block block(int x, int y, int z) { return cells.getOrDefault(new Cell(x, y, z), AIR); }
            public boolean setBlock(int x, int y, int z, Block block, int flags) { place(new Cell(x, y, z), block); return true; }
            public Object tileEntity(int x, int y, int z) { return tile(new Cell(x, y, z)); }
            public void setTileEntity(int x, int y, int z, Object tile) {
                var cell = new Cell(x, y, z); var block = block(x, y, z);
                try { content.services().tiles().attach(tile, block.legacyKey(), block.metadata(), world, x, y, z); }
                catch (Exception failure) { throw new IllegalStateException("Cannot attach scene tile at " + cell, failure); }
                tiles.put(cell, tile); version++;
            }
            public void removeTileEntity(int x, int y, int z) { drop(new Cell(x, y, z)); version++; }
            public void markTileDirty(int x, int y, int z, Object tile) { version++; }
            public java.util.List<int[]> legacyTiles(int chunkX, int chunkZ) {
                var result = new ArrayList<int[]>();
                for (var cell : tiles.keySet()) if (cell.x() >> 4 == chunkX && cell.z() >> 4 == chunkZ) result.add(new int[]{cell.x(), cell.y(), cell.z()});
                return result;
            }
            public void blockUpdated(int x, int y, int z) { }
            public void blockEvent(int x, int y, int z, String block, int event, int data) { }
            public void schedule(int x, int y, int z, String block, int delay) { }
            public void sendPacket(java.util.UUID recipient, LegacyPackets.Message packet) { }
            public void broadcastPacket(LegacyPackets.Message packet) { }
        }

        private void place(Cell cell, HostWorldAccess.Block block) {
            var previous = cells.getOrDefault(cell, AIR);
            if (block.legacyKey().equals(AIR.legacyKey())) cells.remove(cell); else cells.put(cell, block);
            var tile = tiles.get(cell);
            try {
                if (tile != null && previous.legacyKey().equals(block.legacyKey()))
                    content.services().tiles().attach(tile, block.legacyKey(), block.metadata(), world, cell.x(), cell.y(), cell.z());
                else if (tile != null) drop(cell);
                if (!tiles.containsKey(cell) && cells.containsKey(cell)) {
                    Object created = content.services().tiles().create(block.legacyKey(), block.metadata(), world, cell.x(), cell.y(), cell.z());
                    if (created != null) tiles.put(cell, created);
                }
            } catch (Exception failure) { throw new IllegalStateException("Cannot place scene tile at " + cell, failure); }
            version++;
        }

        private Object tile(Cell cell) {
            if (tiles.containsKey(cell) || !cells.containsKey(cell)) return tiles.get(cell);
            var block = cells.get(cell);
            try {
                Object created = content.services().tiles().create(block.legacyKey(), block.metadata(), world, cell.x(), cell.y(), cell.z());
                if (created != null) tiles.put(cell, created);
                return created;
            } catch (Exception failure) { throw new IllegalStateException("Cannot create scene tile at " + cell, failure); }
        }

        private void drop(Cell cell) {
            Object tile = tiles.remove(cell);
            if (tile == null) return;
            try { tile.getClass().getMethod("func_145843_s").invoke(tile); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot invalidate scene tile at " + cell, failure); }
        }

        private void check() {
            requireClientThread();
            if (closed) throw new IllegalStateException("Legacy scene is closed");
        }

        @Override public void setBlock(int x, int y, int z, String legacyKey, int metadata) {
            Objects.requireNonNull(legacyKey);
            runtime.call("SCENE", "setBlock " + legacyKey + "@" + metadata, () -> {
                check();
                if (metadata < 0 || metadata > 15) throw new IllegalArgumentException("Metadata outside 0..15: " + metadata);
                if (!legacyKey.equals(AIR.legacyKey()) && !runtime.registryKeys(LegacyRuntime.Registry.BLOCK).contains(legacyKey))
                    throw new IllegalArgumentException("Unknown old block " + legacyKey);
                return content.services().items().inLegacy(() -> { place(new Cell(x, y, z), new HostWorldAccess.Block(legacyKey, metadata)); return null; });
            });
        }

        @Override public String blockKey(int x, int y, int z) {
            return runtime.call("SCENE", "blockKey", () -> { check(); return cells.getOrDefault(new Cell(x, y, z), AIR).legacyKey(); });
        }

        @Override public int metadata(int x, int y, int z) {
            return runtime.call("SCENE", "metadata", () -> { check(); return cells.getOrDefault(new Cell(x, y, z), AIR).metadata(); });
        }

        @Override public <T> T withWorld(Function<LegacyObject, T> operation) {
            Objects.requireNonNull(operation);
            return runtime.call("SCENE", "withWorld", () -> {
                check();
                var alive = new java.util.concurrent.atomic.AtomicBoolean(true);
                try {
                    return content.services().items().inLegacy(() -> {
                        remote.setBoolean(world, false);
                        try { return operation.apply(runtime.borrowed(world, alive::get)); }
                        finally { remote.setBoolean(world, true); version++; }
                    });
                } finally { alive.set(false); }
            });
        }

        @Override public Optional<Bounds> bounds() {
            return runtime.call("SCENE", "bounds", () -> {
                check();
                if (cells.isEmpty()) return Optional.<Bounds>empty();
                int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                for (var cell : cells.keySet()) {
                    minX = Math.min(minX, cell.x()); minY = Math.min(minY, cell.y()); minZ = Math.min(minZ, cell.z());
                    maxX = Math.max(maxX, cell.x()); maxY = Math.max(maxY, cell.y()); maxZ = Math.max(maxZ, cell.z());
                }
                return Optional.of(new Bounds(minX, minY, minZ, maxX, maxY, maxZ));
            });
        }

        @Override public void render(Object poseStack, Object buffers, float partialTick, float yaw, float pitch) {
            var pose = (PoseStack) Objects.requireNonNull(poseStack);
            var sink = (MultiBufferSource) Objects.requireNonNull(buffers);
            runtime.call("SCENE_RENDER", "render", () -> {
                check();
                if (built != version) rebuild();
                var blocks = Minecraft.getInstance().getBlockRenderer();
                for (var entry : statics) {
                    pose.pushPose();
                    pose.translate(entry.cell().x(), entry.cell().y(), entry.cell().z());
                    if (entry.host() != null) blocks.renderSingleBlock(entry.host(), pose, sink, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                    else replay().render(entry.geometry(), pose, sink, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
                renderTiles(pose, sink, partialTick, yaw, pitch);
                return null;
            });
        }

        /** Block geometry only changes with the scene; tile renderers animate and are captured every frame. */
        private void rebuild() {
            var rendering = content.blockRendering();
            if (rendering == null) throw new IllegalStateException("Original block rendering is not ready");
            var result = new ArrayList<Static>();
            for (var entry : cells.entrySet()) {
                var cell = entry.getKey(); var block = entry.getValue();
                try {
                    if (block.legacyKey().startsWith("minecraft:")) {
                        var state = content.modernBlock(block);
                        if (state.getFluidState().isEmpty() && state.getRenderShape() == RenderShape.MODEL) result.add(new Static(cell, state, null));
                        continue;
                    }
                    var geometry = new ArrayList<LegacyVertexStream.Primitive>();
                    // Neighbours come from the scene, so connected and context-dependent original models match the world.
                    for (var layer : rendering.capture(block.legacyKey(), block.metadata(),
                            (x, y, z) -> cells.getOrDefault(new Cell(cell.x() + x, cell.y() + y, cell.z() + z), AIR)))
                        geometry.addAll(layer.geometry());
                    if (!geometry.isEmpty()) result.add(new Static(cell, null, List.copyOf(geometry)));
                } catch (Exception | LinkageError failure) {
                    runtime.note("SCENE_RENDER", block.legacyKey() + "@" + block.metadata() + " at " + cell, failure);
                }
            }
            statics = List.copyOf(result);
            failedTiles.clear();
            built = version;
        }

        private void renderTiles(PoseStack pose, MultiBufferSource sink, float partialTick, float yaw, float pitch) throws Exception {
            if (tiles.isEmpty()) return;
            var rendering = content.blockRendering().tiles();
            var mc = Minecraft.getInstance();
            var box = bounds().orElse(new Bounds(0, 0, 0, 0, 0, 0));
            double radiansYaw = Math.toRadians(yaw), radiansPitch = Math.toRadians(pitch);
            // Host look vector for this yaw/pitch; the eye sits behind the scene centre.
            double lookX = -Math.sin(radiansYaw) * Math.cos(radiansPitch), lookY = -Math.sin(radiansPitch), lookZ = Math.cos(radiansYaw) * Math.cos(radiansPitch);
            double eyeX = (box.minX() + box.maxX() + 1) / 2.0 - lookX * EYE_DISTANCE;
            double eyeY = (box.minY() + box.maxY() + 1) / 2.0 - lookY * EYE_DISTANCE;
            double eyeZ = (box.minZ() + box.maxZ() + 1) / 2.0 - lookZ * EYE_DISTANCE;
            int ticks = mc.player == null ? 0 : mc.player.tickCount;
            try {
                rendering.view(world, eyeX, eyeY, eyeZ, yaw, pitch, ticks);
                for (var entry : List.copyOf(tiles.entrySet())) {
                    var cell = entry.getKey(); var tile = entry.getValue();
                    if (failedTiles.contains(cell)) continue;
                    try {
                        if (!rendering.hasRenderer(tile)) continue;
                        var primitives = rendering.capture(tile, partialTick, cell.x() - eyeX, cell.y() - eyeY, cell.z() - eyeZ);
                        pose.pushPose();
                        pose.translate(cell.x(), cell.y(), cell.z());
                        replay().render(primitives, pose, sink, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                        pose.popPose();
                    } catch (Exception | LinkageError failure) {
                        failedTiles.add(cell);
                        runtime.note("SCENE_RENDER", "tile " + cells.getOrDefault(cell, AIR) + " at " + cell, failure);
                    }
                }
            } finally { restoreView(rendering, mc); }
        }

        /**
         * Old renderers keep the view entity and its world in globals; hand them back to the live world at once, so
         * nothing that runs before the next placed tile renders sees this scene.
         */
        private void restoreView(LegacyTileRendering rendering, Minecraft mc) throws Exception {
            if (mc.level == null) return;
            Object live = content.services().worlds().findBound(mc.level);
            if (live == null) return;
            var camera = mc.gameRenderer.getMainCamera(); var eye = camera.getPosition();
            rendering.view(live, eye.x, eye.y, eye.z, camera.getYRot(), camera.getXRot(), mc.player == null ? 0 : mc.player.tickCount);
        }

        @Override public void close() {
            if (closed) return;
            runtime.call("SCENE", "close", () -> {
                requireClientThread();
                closed = true;
                content.services().items().inLegacy(() -> {
                    try { for (var cell : List.copyOf(tiles.keySet())) drop(cell); }
                    finally { content.services().worlds().unbind(identity); }
                    return null;
                });
                return null;
            });
        }
    }
}
