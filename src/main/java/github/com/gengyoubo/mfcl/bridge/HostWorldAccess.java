package github.com.gengyoubo.mfcl.bridge;

/** Synchronous host world contract. The host remains the only owner of world state. */
public interface HostWorldAccess {
    record Block(String legacyKey, int metadata) {}
    /** A block hit from the host ray trace, in old World/Item coordinates. */
    record Hit(int x, int y, int z, int side, double hitX, double hitY, double hitZ) {}
    record Sound(double x,double y,double z,String id,float volume,float pitch,boolean distanceDelay,java.util.UUID excludedPlayer) {}
    /** A legacy projectile reduced to host-neutral data so the modern side can spawn an equivalent entity. */
    record Projectile(double x,double y,double z,double motionX,double motionY,double motionZ,double damage,boolean burning,java.util.UUID owner) {}
    record Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item,int age,int love) {
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable,int entityId,LegacyClientData.Stack item) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,entityId,item,0,0);
        }
        public Entity(java.util.UUID id,String legacyClass,String name,double x,double y,double z,double motionX,double motionY,double motionZ,float fallDistance,float width,float height,boolean sneaking,boolean pushable) {
            this(id,legacyClass,name,x,y,z,motionX,motionY,motionZ,fallDistance,width,height,sneaking,pushable,0,null);
        }
    }
    /** {@code love} is the animal breeding timer (EntityAnimal.inLove), 0 for anything else. */
    record Motion(java.util.UUID id,double x,double y,double z,float fallDistance,int love) {}
    String dimensionKey();
    int legacyDimensionId();
    boolean clientSide();
    long gameTime();
    long dayTime();
    void setDayTime(long time);
    default String biome(int x,int z) { return "minecraft:plains"; }
    default void setBiome(int x,int z,String key) { throw new UnsupportedOperationException("Host biome mutation is not available"); }
    Block block(int x, int y, int z);
    boolean setBlock(int x, int y, int z, Block block, int flags);
    default boolean canPlaceBlock(Block block, int x, int y, int z) { return true; }
    default boolean powered(int x, int y, int z) { return false; }
    default int power(int x, int y, int z, int side) { return 0; }
    default boolean loaded(int x, int y, int z) { return true; }
    default void playSound(Sound sound) { }
    default void spawnProjectile(Projectile projectile) { }
    default java.util.List<Entity> entities(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) { return java.util.List.of(); }
    default Object existingEntity(java.util.UUID id) { return null; }
    /** Every player in this world (old World.playerEntities). */
    default java.util.List<Entity> players() { return java.util.List.of(); }
    /** Old World.newExplosion; the host explosion damages real entities and breaks blocks with host rules. */
    default void explode(double x, double y, double z, float power, boolean fire, boolean breakBlocks) { }
    default void applyMotion(Motion motion) { }
    default void updateComparators(int x, int y, int z) { }
    default void blockEvent(int x, int y, int z, String block, int event, int data) { throw new UnsupportedOperationException("Host block events are not available"); }
    default void schedule(int x, int y, int z, String block, int delay) { throw new UnsupportedOperationException("Host block scheduling is not available"); }
    default Hit rayTrace(double startX, double startY, double startZ, double endX, double endY, double endZ) { return null; }
    default Object tileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity lookup is not available"); }
    default void setTileEntity(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity assignment is not available"); }
    default void removeTileEntity(int x, int y, int z) { throw new UnsupportedOperationException("Host TileEntity removal is not available"); }
    default void markTileDirty(int x, int y, int z, Object tile) { throw new UnsupportedOperationException("Host TileEntity dirty tracking is not available"); }
    /** Combined sky/block light as 1.7.10 getBlockLightValue saw it (sky already darkened by time of day). */
    default int light(int x, int y, int z) { return 15; }
    /** First free y above the motion-blocking surface (1.7.10 getHeightValue). */
    default int height(int x, int z) { return 64; }
    default boolean canSeeSky(int x, int y, int z) { return true; }
    /** Notifies host blocks around the position; original blocks there are notified by the legacy side. */
    default void neighborsChanged(int x, int y, int z) { }
    default void blockUpdated(int x, int y, int z) { throw new UnsupportedOperationException("Host block update is not available"); }
    /** World seed (old World.getSeed); worldgen and Teleporter randoms derive from it. */
    default long seed() { return 0; }
    /** Directory standing in for the old world save folder (ISaveHandler.getWorldDirectory), or null without saves. */
    default java.io.File legacySaveDirectory() { return null; }
    /** Old transferPlayerToDimension, resolved: the player goes to the host level of {@code legacyDimension}. */
    record Transfer(java.util.UUID player, int legacyDimension, double x, double y, double z, float yaw, float pitch, Object teleporter) {}
    default void transferPlayer(Transfer transfer) { throw new UnsupportedOperationException("Host dimension travel is not available"); }
    /** Old EntityPlayerMP.timeUntilPortal, kept on the host player between mirrored actors. */
    default void portalCooldown(java.util.UUID player, int ticks) { }
    default int portalCooldown(java.util.UUID player) { return 0; }
    /** Nearest block of {@code legacyKey} in host chunks within {@code radius} columns, as {x,y,z}, or null. */
    default int[] findBlock(String legacyKey, int x, int y, int z, int radius) { return null; }
}
