package github.com.gengyoubo.mfcl.api;

import java.util.Optional;
import java.util.function.Function;

/**
 * A private old world holding a few original blocks, drawn with the original block renderers and tile entity special
 * renderers. Typical use: place a structure's blocks, let the original code form it in {@link #withWorld}, then
 * {@link #render} the result every frame. Every member must run on the client thread; a closed scene throws
 * {@link IllegalStateException}.
 */
public interface LegacyScene extends AutoCloseable {
    /** Inclusive cell coordinates enclosing every non-air block. */
    record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int sizeX() { return maxX - minX + 1; }
        public int sizeY() { return maxY - minY + 1; }
        public int sizeZ() { return maxZ - minZ + 1; }
    }

    /**
     * Places an original block exactly, without placement callbacks or neighbour updates, and gives it a fresh original
     * tile entity when its block has one at that metadata. {@code "minecraft:air"} clears the cell.
     *
     * @param legacyKey an old block registry key, for example {@code "Thaumcraft:blockStoneDevice"} or {@code "minecraft:obsidian"}
     * @throws IllegalArgumentException when the key is not an old block or the metadata is outside 0..15
     */
    void setBlock(int x, int y, int z, String legacyKey, int metadata);

    /** The old block key at a cell, {@code "minecraft:air"} when empty. */
    String blockKey(int x, int y, int z);

    int metadata(int x, int y, int z);

    /**
     * Runs original code against this scene's old {@code World}, for example a multiblock's formation method. The world
     * reports itself as the logical server ({@code isRemote} false) for the duration, so server-only original code
     * applies; sounds, particles and packets are dropped. Blocks it sets and tile entities it changes stay in the scene.
     * The reference and references derived from it expire when the callback returns. Return snapshots only.
     */
    <T> T withWorld(Function<LegacyObject, T> operation);

    /** Empty when the scene holds no block. */
    Optional<Bounds> bounds();

    /**
     * Draws every block and tile entity at the current pose: one unit per block, cell {@code (x, y, z)} spanning
     * {@code [x, x + 1)} on each axis. Blocks are fully lit. Host blocks (the {@code minecraft} namespace) are drawn by
     * their host model; fluids are not drawn. A block or tile whose original renderer fails is skipped and recorded in
     * {@link LegacyRuntime#diagnostics()} once until the scene changes.
     *
     * @param poseStack   the host {@code PoseStack}
     * @param buffers     the host {@code MultiBufferSource}; a buffer source is flushed batch by batch
     * @param partialTick the frame's partial tick, for animated tile renderers
     * @param yaw         the viewing direction in host degrees, for original effects that face the camera
     * @param pitch       the viewing pitch in host degrees
     */
    void render(Object poseStack, Object buffers, float partialTick, float yaw, float pitch);

    /** Releases the old world and its tile entities. Idempotent. */
    @Override void close();
}
