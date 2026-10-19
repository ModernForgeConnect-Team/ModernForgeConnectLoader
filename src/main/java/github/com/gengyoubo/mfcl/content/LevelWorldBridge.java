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
            @Override public String biome(int x,int z) {check();return HostBiomeQueries.key(level,x,z);}
            @Override public void setBiome(int x,int z,String key) {check();content.afterCommit(level,()->HostBiomeQueries.set(level,x,z,key));}
            @Override public void playSound(Sound sound) {
                check();var id=net.minecraft.resources.ResourceLocation.parse(github.com.gengyoubo.mfcl.bridge.LegacySounds.modernId(sound.id()));
                var excluded=sound.excludedPlayer()==null?null:level.getServer().getPlayerList().getPlayer(sound.excludedPlayer());
                content.afterCommit(level,()->level.playSound(excluded,sound.x(),sound.y(),sound.z(),net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id),net.minecraft.sounds.SoundSource.MASTER,sound.volume(),sound.pitch()));
            }
            @Override public long gameTime() { check(); return level.getGameTime(); }
            @Override public java.util.List<HostWorldAccess.Entity> entities(double a,double b,double c,double d,double e,double f) {check();return HostEntityQueries.query(level,a,b,c,d,e,f);}
            @Override public void applyMotion(HostWorldAccess.Motion motion) {check();content.afterCommit(level,()->HostEntityQueries.apply(level,motion));}
            @Override public Object existingEntity(java.util.UUID id) {
                check();var entity=level.getEntity(id);
                if(entity instanceof net.minecraft.server.level.ServerPlayer player) {
                    if(player.containerMenu instanceof LegacyDeviceMenu menu)return menu.originalPlayer();
                    try {return content.itemUse().clientActor(content.services().worlds().bound(level),new github.com.gengyoubo.mfcl.bridge.LegacyTiles.Placer(player.getUUID(),player.getName().getString(),player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot(),player.getAbilities().instabuild,player.isShiftKeyDown()),null);}
                    catch(Exception failure){throw new IllegalStateException("Cannot resolve original player for entity query",failure);}
                }
                return null;
            }
            @Override public void spawnProjectile(Projectile projectile) {
                check();
                var arrow = new net.minecraft.world.entity.projectile.Arrow(level, projectile.x(), projectile.y(), projectile.z());
                if (projectile.owner() != null) {
                    var owner = level.getServer().getPlayerList().getPlayer(projectile.owner());
                    if (owner != null) arrow.setOwner(owner);
                }
                arrow.setDeltaMovement(projectile.motionX(), projectile.motionY(), projectile.motionZ());
                if (projectile.damage() > 0) arrow.setBaseDamage(projectile.damage());
                if (projectile.burning()) arrow.setSecondsOnFire(100);
                content.afterCommit(level, () -> level.addFreshEntity(arrow));
            }
            @Override public long dayTime() { check(); return level.getDayTime(); }
            @Override public void setDayTime(long time) { check(); level.setDayTime(time); }
            @Override public boolean powered(int x, int y, int z) { check(); return level.hasNeighborSignal(new BlockPos(x,y,z)); }
            @Override public int power(int x, int y, int z, int side) { check(); return level.getSignal(new BlockPos(x,y,z),net.minecraft.core.Direction.from3DDataValue(side)); }
            @Override public boolean loaded(int x, int y, int z) { check(); return level.isInWorldBounds(new BlockPos(x,y,z)) && level.hasChunkAt(new BlockPos(x,y,z)); }
            @Override public void updateComparators(int x, int y, int z) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                content.afterCommit(level,() -> level.updateNeighbourForOutputSignal(pos,block));
            }
            @Override public void blockEvent(int x, int y, int z, String key, int event, int data) {
                check(); var pos = new BlockPos(x,y,z); var block = level.getBlockState(pos).getBlock();
                if (!content.legacyBlock(level.getBlockState(pos)).legacyKey().equals(key)) return;
                content.afterCommit(level,() -> level.blockEvent(pos,block,event,data));
            }
            @Override public void schedule(int x, int y, int z, String key, int delay) {
                check(); var pos = new BlockPos(x,y,z);
                if (content.legacyBlock(level.getBlockState(pos)).legacyKey().equals(key)) {
                    var block = level.getBlockState(pos).getBlock();
                    content.afterCommit(level,() -> level.scheduleTick(pos,block,Math.max(1,delay)));
                }
            }
            @Override public Block block(int x, int y, int z) { check(); return content.legacyBlock(level.getBlockState(new BlockPos(x, y, z))); }
            @Override public boolean setBlock(int x, int y, int z, Block block, int flags) {
                check(); var pos = new BlockPos(x,y,z); content.beforeMutation(level,pos);
                return level.setBlock(pos, content.modernBlock(block), flags);
            }
            @Override public boolean canPlaceBlock(Block block, int x, int y, int z) {
                check(); var candidate = content.blocks().get(block.legacyKey());
                var state = candidate == null ? content.modernBlock(block) : candidate.defaultBlockState();
                return level.isInWorldBounds(new BlockPos(x,y,z)) && level.isUnobstructed(state,new BlockPos(x,y,z),net.minecraft.world.phys.shapes.CollisionContext.empty());
            }
            @Override public HostWorldAccess.Hit rayTrace(double sx, double sy, double sz, double ex, double ey, double ez) {
                check();
                var from = new net.minecraft.world.phys.Vec3(sx, sy, sz); var to = new net.minecraft.world.phys.Vec3(ex, ey, ez);
                var result = level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, null));
                if (result.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return null;
                var pos = result.getBlockPos();
                return new HostWorldAccess.Hit(pos.getX(), pos.getY(), pos.getZ(), result.getDirection().get3DDataValue(), result.getLocation().x, result.getLocation().y, result.getLocation().z);
            }
            @Override public Object tileEntity(int x, int y, int z) {
                check(); content.beforeMutation(level,new BlockPos(x,y,z)); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (tile == null) return null;
                if (tile instanceof LegacyBlockEntity legacy) return legacy.legacy();
                return null; // An unsupported native tile is not an instance of any legacy machine/interface.
            }
            @Override public void setTileEntity(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (!(tile instanceof LegacyBlockEntity legacy)) throw new UnsupportedOperationException("No legacy tile envelope at requested position");
                legacy.replace(old); legacy.synchronize();
            }
            @Override public void removeTileEntity(int x, int y, int z) { check(); level.removeBlockEntity(new BlockPos(x, y, z)); }
            @Override public void markTileDirty(int x, int y, int z, Object old) {
                check(); var tile = level.getBlockEntity(new BlockPos(x, y, z));
                if (!(tile instanceof LegacyBlockEntity legacy) || !legacy.owns(old)) throw new IllegalStateException("Legacy tile identity mismatch");
                legacy.markLegacyDirty();
            }
            @Override public void blockUpdated(int x, int y, int z) {
                check(); BlockPos pos = new BlockPos(x, y, z); var state = level.getBlockState(pos);
                if (level.getBlockEntity(pos) instanceof LegacyBlockEntity tile) tile.markLegacyDirty();
                else level.sendBlockUpdated(pos, state, state, 3);
            }
        };
    }
}

