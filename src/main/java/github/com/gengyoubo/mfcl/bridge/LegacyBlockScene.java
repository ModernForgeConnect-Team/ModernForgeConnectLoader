package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Short-lived read-only old world owned by the calling render/shape thread. */
public final class LegacyBlockScene implements AutoCloseable {
    private final LegacyServices services;
    private final Object identity = new Object();
    private final Object world;
    private final Map<List<Integer>, Object> tiles = new HashMap<>();
    public LegacyBlockScene(LegacyServices services, LegacyBlockRendering.View view) throws Exception {
        this.services = services;
        world = services.worlds().bind(identity, new HostWorldAccess() {
            public String dimensionKey() { return "mfcl:block_preview"; }
            public int legacyDimensionId() { return 0; }
            public boolean clientSide() { return true; }
            public long gameTime() { return view.time(); }
            public long dayTime() { return view.time(); }
            public Block block(int x, int y, int z) { return view.block(x,y,z); }
            public Object tileEntity(int x, int y, int z) { return tile(view, x,y,z); }
            public void setDayTime(long time) { throw new UnsupportedOperationException("Read-only block scene"); }
            public boolean setBlock(int x, int y, int z, Block block, int flags) { throw new UnsupportedOperationException("Read-only block scene"); }
            public void markTileDirty(int x, int y, int z, Object tile) { }
            public void blockUpdated(int x, int y, int z) { }
        });
    }
    private Object tile(LegacyBlockRendering.View view, int x, int y, int z) {
        var position = List.of(x,y,z);
        if (tiles.containsKey(position)) return tiles.get(position);
        try {
            var cell = view.block(x,y,z);
            Object tile = services.tiles().create(cell.legacyKey(), cell.metadata(), world, x,y,z);
            if (tile != null) {
                byte[] bytes = view.tileData(x,y,z);
                if (bytes != null && bytes.length != 0) {
                    services.tiles().load(tile, bytes);
                    services.tiles().attach(tile, cell.legacyKey(), cell.metadata(), world, x,y,z);
                }
            }
            tiles.put(position, tile); return tile;
        } catch (Exception error) { throw new IllegalStateException("Cannot construct preview tile at " + position, error); }
    }
    public Object world() { return world; }
    @Override public void close() { services.worlds().unbind(identity); }
}
