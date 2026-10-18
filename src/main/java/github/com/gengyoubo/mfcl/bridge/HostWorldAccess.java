package github.com.gengyoubo.mfcl.bridge;

/** Synchronous host world contract. The host remains the only owner of world state. */
public interface HostWorldAccess {
    record Block(String legacyKey, int metadata) {}
    String dimensionKey();
    int legacyDimensionId();
    boolean clientSide();
    long gameTime();
    long dayTime();
    void setDayTime(long time);
    Block block(int x, int y, int z);
    boolean setBlock(int x, int y, int z, Block block, int flags);
}
