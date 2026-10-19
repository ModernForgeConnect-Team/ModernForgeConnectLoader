package github.com.gengyoubo.mfcl.bridge;

/** Synchronous host world contract. The host remains the only owner of world state. */
public interface HostWorldAccess {
    record Block(String legacyKey, int metadata) {}
    /** A block hit from the host ray trace, in old World/Item coordinates. */
    record Hit(int x, int y, int z, int side, double hitX, double hitY, double hitZ) {}
    record Sound(double x,double y,double z,String id,float volume,float pitch,boolean distanceDelay,java.util.UUID excludedPlayer) {}
    String dimensionKey();
    int legacyDimensionId();
    boolean clientSide();
    long gameTime();
    long dayTime();
    void setDayTime(long time);
    Block block(int x, int y, int z);
    boolean setBlock(int x, int y, int z, Block block, int flags);
    default boolean canPlaceBlock(Block block, int x, int y, int z) { return true; }
    default boolean powered(int x, int y, int z) { return false; }
    default int power(int x, int y, int z, int side) { return 0; }
    default boolean loaded(int x, int y, int z) { return true; }
    default void playSound(Sound sound) { }
    default void updateComparators(int x, int y, int z) { }
    default void blockEvent(int x, int y, int z, String block, int event, int data) { throw new UnsupportedOperationException("Host block events are not available"); }
    default void schedule(int x, int y, int z, String block, int delay) { throw new UnsupportedOperationException("Host block scheduling is not available"); }
    default Hit rayTrace(double startX, double startY, double startZ, double endX, double endY, double endZ) { return null; }
    default Object tileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity lookup is not available"); }
    default void setTileEntity(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity assignment is not available"); }
    default void removeTileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity removal is not available"); }
    default void markTileDirty(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity dirty tracking is not available"); }
    default void blockUpdated(int x, int y, int z) { throw new UnsupportedOperationException("Host block update is not available"); }
}
