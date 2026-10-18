package github.com.gengyoubo.mfcl.bridge;

/** Synchronous host world contract. The host remains the only owner of world state. */
public interface HostWorldAccess {
    record Block(String legacyKey, int metadata) {}
    /** A block hit from the host ray trace, in old World/Item coordinates. */
    record Hit(int x, int y, int z, int side, double hitX, double hitY, double hitZ) {}
    String dimensionKey();
    int legacyDimensionId();
    boolean clientSide();
    long gameTime();
    long dayTime();
    void setDayTime(long time);
    Block block(int x, int y, int z);
    boolean setBlock(int x, int y, int z, Block block, int flags);
    default boolean canPlaceBlock(Block block, int x, int y, int z) { return true; }
    default Hit rayTrace(double startX, double startY, double startZ, double endX, double endY, double endZ) { return null; }
    default Object tileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity lookup is not available"); }
    default void setTileEntity(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity assignment is not available"); }
    default void removeTileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity removal is not available"); }
    default void markTileDirty(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity dirty tracking is not available"); }
    default void blockUpdated(int x, int y, int z) { throw new UnsupportedOperationException("Host block update is not available"); }
}
