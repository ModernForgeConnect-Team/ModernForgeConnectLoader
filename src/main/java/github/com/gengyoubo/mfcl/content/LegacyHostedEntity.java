package github.com.gengyoubo.mfcl.content;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyEntityHosting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/**
 * Host envelope of an original entity that has no host translation (Thaumcraft embers, orbs, shards...).
 * The server owns the original and runs its onUpdate; clients rebuild it to run its client update and Render.
 */
public final class LegacyHostedEntity extends Entity implements IEntityAdditionalSpawnData {
    /** The original DataWatcher as a complete snapshot, refreshed whenever the original changes a value. */
    private static final EntityDataAccessor<CompoundTag> WATCHER = SynchedEntityData.defineId(LegacyHostedEntity.class, EntityDataSerializers.COMPOUND_TAG);
    private static final Set<String> FAILURES = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private Object legacy;
    private String legacyType;
    private byte[] saved, spawnData = new byte[0], watcher = new byte[0];
    private float width = 0.25f, height = 0.25f;
    private boolean collidable, failed;
    public LegacyHostedEntity(EntityType<?> type, Level level) { super(type, level); noPhysics = true; }
    /** Wraps an original entity spawned by old server logic; the caller adds it to the level. */
    static LegacyHostedEntity host(ModernContentBridge content, ServerLevel level, Object original) throws Exception {
        var hosted = new LegacyHostedEntity(content.legacyEntityType(), level);
        var hosting = content.services().entityHosting();
        hosted.legacy = original; hosted.legacyType = hosting.type(original);
        hosting.adopt(original, content.services().worlds().bound(level), hosted.getUUID(), hosted.getId());
        hosted.apply(hosting.state(original), true);
        return hosted;
    }
    public Object legacyOrNull() { return legacy; }
    @Override protected void defineSynchedData() { entityData.define(WATCHER, new CompoundTag()); }
    private static LegacyEntityHosting hosting(ModernContentBridge content) { return content.services().entityHosting(); }
    private boolean ensureServerLegacy(ModernContentBridge content, ServerLevel level) throws Exception {
        if (legacy != null) return true;
        if (legacyType == null || saved == null) return false;
        var world = content.services().worlds().bound(level);
        legacy = hosting(content).restore(world, legacyType, saved);
        hosting(content).adopt(legacy, world, getUUID(), getId());
        saved = null; apply(hosting(content).state(legacy), true);
        return true;
    }
    @Override public void tick() {
        var content = ModernContentBridge.active();
        if (content == null || failed) { if (!level().isClientSide) discard(); return; }
        if (level().isClientSide) { clientTick(content); return; }
        var level = (ServerLevel) level();
        try {
            if (!ensureServerLegacy(content, level)) { discard(); return; }
            var world = content.services().worlds().bound(level);
            var state = content.logicTransaction(level, blockPosition(), () -> hosting(content).tick(world, legacy), true);
            apply(state, false);
            byte[] changes = hosting(content).watcher(legacy, false);
            if (changes != null) entityData.set(WATCHER, wrap(changes));
            if (state.dead()) discard();
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    private void clientTick(ModernContentBridge content) {
        try {
            Object world = content.services().worlds().findBound(level());
            if (world == null) return;
            if (legacy == null) {
                if (legacyType == null || saved == null) return;
                legacy = hosting(content).restore(world, legacyType, saved);
                hosting(content).adopt(legacy, world, getUUID(), getId());
                hosting(content).spawnData(legacy, spawnData);
                hosting(content).watcher(legacy, watcher);
                saved = null;
            }
            hosting(content).follow(world, legacy, getX(), getY(), getZ(), getYRot(), getXRot());
        } catch (Exception | LinkageError failure) { fail(failure); }
    }
    private void fail(Throwable failure) {
        failed = true; legacy = null;
        if (FAILURES.add(legacyType + (level().isClientSide ? "@client" : "@server")))
            LogUtils.getLogger().error("MFCL hosted original entity {} failed on the {}; it is removed", legacyType, level().isClientSide ? "client" : "server", failure);
        if (!level().isClientSide) discard();
    }
    private void apply(LegacyEntityHosting.State state, boolean initial) {
        if (initial) { setPos(state.x(), state.y(), state.z()); xo = state.x(); yo = state.y(); zo = state.z(); }
        else setPos(state.x(), state.y(), state.z());
        setDeltaMovement(state.motionX(), state.motionY(), state.motionZ());
        setYRot(state.yaw()); setXRot(state.pitch());
        collidable = state.collidable();
        if (state.width() != width || state.height() != height) { width = state.width(); height = state.height(); refreshDimensions(); }
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key != WATCHER || entityData == null || !level().isClientSide) return;
        byte[] next = entityData.get(WATCHER).getByteArray("d");
        if (next.length == 0) return;
        if (legacy == null) { watcher = next; return; }
        var content = ModernContentBridge.active();
        try { if (content != null) hosting(content).watcher(legacy, next); }
        catch (Exception failure) { fail(failure); }
    }
    private static CompoundTag wrap(byte[] bytes) { var tag = new CompoundTag(); tag.putByteArray("d", bytes); return tag; }
    @Override public EntityDimensions getDimensions(Pose pose) { return EntityDimensions.scalable(width, height); }
    @Override public boolean isPickable() { return collidable && !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    @Override public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || legacy == null || isInvulnerableTo(source)) return false;
        var content = ModernContentBridge.active(); if (content == null) return false;
        try {
            var level = (ServerLevel) level(); var world = content.services().worlds().bound(level);
            Object actor = source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                    ? content.itemUse().clientActor(world, ModernContentBridge.placer(player), null) : null;
            return content.logicTransaction(level, blockPosition(), () -> hosting(content).hurt(legacy, actor, amount), true);
        } catch (Exception failure) { fail(failure); return false; }
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        legacyType = tag.contains("LegacyType") ? tag.getString("LegacyType") : null;
        saved = tag.contains("LegacyNbt") ? tag.getByteArray("LegacyNbt") : null;
        if (tag.contains("LegacyWidth")) { width = tag.getFloat("LegacyWidth"); height = tag.getFloat("LegacyHeight"); refreshDimensions(); }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (legacyType != null) tag.putString("LegacyType", legacyType);
        byte[] data = saved;
        var content = ModernContentBridge.active();
        try { if (legacy != null && content != null) data = hosting(content).save(legacy); }
        catch (Exception failure) { LogUtils.getLogger().error("MFCL cannot save hosted original entity {}", legacyType, failure); }
        if (data != null) tag.putByteArray("LegacyNbt", data);
        tag.putFloat("LegacyWidth", width); tag.putFloat("LegacyHeight", height);
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public void writeSpawnData(FriendlyByteBuf buffer) {
        byte[] nbt = new byte[0], extra = new byte[0], values = new byte[0];
        var content = ModernContentBridge.active();
        try {
            if (content != null && ensureServerLegacy(content, (ServerLevel) level())) {
                nbt = hosting(content).save(legacy); extra = hosting(content).spawnData(legacy); values = hosting(content).watcher(legacy, true);
            }
        } catch (Exception failure) { LogUtils.getLogger().error("MFCL cannot describe hosted original entity {} to clients", legacyType, failure); }
        buffer.writeUtf(legacyType == null ? "" : legacyType);
        buffer.writeByteArray(nbt); buffer.writeByteArray(extra); buffer.writeByteArray(values);
        buffer.writeFloat(width); buffer.writeFloat(height); buffer.writeBoolean(collidable);
    }
    @Override public void readSpawnData(FriendlyByteBuf buffer) {
        String type = buffer.readUtf(); legacyType = type.isEmpty() ? null : type;
        byte[] nbt = buffer.readByteArray(); saved = nbt.length == 0 ? null : nbt;
        spawnData = buffer.readByteArray(); watcher = buffer.readByteArray();
        width = buffer.readFloat(); height = buffer.readFloat(); collidable = buffer.readBoolean(); refreshDimensions();
    }
}
