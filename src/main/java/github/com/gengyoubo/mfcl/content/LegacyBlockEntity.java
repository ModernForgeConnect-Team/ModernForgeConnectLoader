package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Host persistence and synchronization envelope around one real old TileEntity per side and position. */
public final class LegacyBlockEntity extends BlockEntity {
    private final ModernContentBridge content;
    private Object legacy;
    private volatile byte[] saved = new byte[0];
    public byte[] renderData() { return saved.clone(); }
    private String savedBlock;
    private boolean updatePending, logicSuspended;
    private long successfulTicks;
    private final java.util.Map<net.minecraft.core.Direction,net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler>> inventories = new java.util.HashMap<>();
    public ModernContentBridge content() { return content; }
    public boolean owns(Object original) { return legacy == original; }
    public boolean logicSuspended() { return logicSuspended; }
    public long successfulTicks() { return successfulTicks; }
    public LegacyBlockEntity(ModernContentBridge content, BlockPos position, BlockState state) {
        super(content.tileType(), position, state); this.content = content;
    }
    private String key() { return content.legacyBlock(getBlockState()).legacyKey(); }
    public Object legacy() {
        if (level == null || isRemoved()) throw new IllegalStateException("Legacy tile has no live host level");
        try {
            Object world = content.services().worlds().bound(level);
            if (legacy == null) {
                if (savedBlock != null && !savedBlock.equals(key())) throw new IllegalStateException("Saved legacy tile belongs to " + savedBlock + ", not " + key());
                legacy = saved.length == 0 ? content.services().tiles().create(key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ())
                        : content.services().tiles().snapshotInstance(null, saved);
                if (legacy != null && saved.length != 0) {
                    content.services().tiles().attach(legacy, key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
                    content.services().tiles().load(legacy, saved);
                    content.services().tiles().attach(legacy, key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
                }
            }
            return legacy;
        } catch (Exception failure) { legacy = null; throw new IllegalStateException("Cannot create/load original TileEntity " + key() + " at " + worldPosition, failure); }
    }
    public Object legacyOrNull() {
        var entry = content.declarations().blocks().stream().filter(block -> block.key().equals(key())).findFirst().orElse(null);
        if (entry == null || !entry.hasTile(getBlockState().getValue(ModernContentBridge.METADATA))) return null;
        return legacy();
    }
    public void replace(Object value) {
        try {
            Object world = content.services().worlds().bound(level);
            content.services().tiles().attach(value, key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
            legacy = value; saved = new byte[0]; setChanged();
        } catch (Exception error) { throw new IllegalStateException("Cannot replace old TileEntity", error); }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        saved = tag.getByteArray("mfcl_legacy_nbt"); savedBlock = tag.contains("mfcl_legacy_block") ? tag.getString("mfcl_legacy_block") : null;
        if(legacy!=null) {
            if(level!=null&&level.isClientSide&&!isRemoved()&&saved.length!=0&&(savedBlock==null||savedBlock.equals(key())))try {
                // Retain open GUI references for same-type updates, but honor authoritative subtype changes.
                Object updated = content.services().tiles().snapshotInstance(legacy,saved);
                if(updated!=legacy) { invalidateLegacy(); legacy=updated; }
                content.services().tiles().attach(legacy,key(),getBlockState().getValue(ModernContentBridge.METADATA),content.services().worlds().bound(level),worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
                content.services().tiles().load(legacy,saved);
                content.services().tiles().attach(legacy,key(),getBlockState().getValue(ModernContentBridge.METADATA),content.services().worlds().bound(level),worldPosition.getX(),worldPosition.getY(),worldPosition.getZ());
            }catch(Exception failure){throw new IllegalStateException("Cannot update original client TileEntity",failure);}
            else invalidateLegacy();
        }
        if (level != null && level.isClientSide) {
            requestModelDataUpdate(); level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        try { if (legacy != null) saved = content.services().tiles().save(legacy); }
        catch (Exception error) { throw new IllegalStateException("Cannot save original TileEntity " + key(), error); }
        tag.putString("mfcl_legacy_block", key()); tag.putByteArray("mfcl_legacy_nbt", saved);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    public void synchronize() {
        captureChanges(true);
    }
    public void captureChanges(boolean immediate) {
        try {
            if (legacy != null) {
                byte[] next = content.services().tiles().save(legacy);
                if (!java.util.Arrays.equals(next,saved)) { saved = next; updatePending = true; setChanged(); }
            }
        }
        catch (Exception error) { throw new IllegalStateException("Cannot snapshot legacy tile for rendering", error); }
        if (updatePending && level != null && !level.isClientSide && (immediate || level.getGameTime()%5 == 0)) {
            updatePending = false; level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    public void markLegacyDirty() { setChanged(); updatePending = true; }
    public void tickServer() {
        if (logicSuspended || isRemoved() || level == null || level.isClientSide) return;
        try {
            boolean ticked = content.logicTransaction(level,worldPosition,() -> content.services().logic().tick(legacyOrNull()),false);
            if (ticked) successfulTicks++;
        } catch (Exception | LinkageError failure) {
            logicSuspended = true;
            if (level.getBlockEntity(worldPosition) instanceof LegacyBlockEntity current) current.logicSuspended = true;
            com.mojang.logging.LogUtils.getLogger().error("MFCL paused original tile logic for {} at {} after rollback",key(),worldPosition,failure);
        }
    }
    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
        if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER && level != null && !isRemoved()) {
            try {
                if (content.services().inventories().supports(legacyOrNull())) return inventories.computeIfAbsent(side,key ->
                        net.minecraftforge.common.util.LazyOptional.of(() -> new LegacyInventoryHandler(this,key))).cast();
            } catch (Exception failure) { throw new IllegalStateException("Cannot expose original inventory",failure); }
        }
        return super.getCapability(capability,side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); inventories.values().forEach(net.minecraftforge.common.util.LazyOptional::invalidate); inventories.clear();
    }
    @Override public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (legacy != null) try { content.services().tiles().metadata(legacy, state.getValue(ModernContentBridge.METADATA)); }
        catch (Exception error) { throw new IllegalStateException("Cannot synchronize old tile metadata", error); }
    }
    private void invalidateLegacy() {
        try { content.services().tiles().invalidate(legacy); }
        catch (Exception error) { throw new IllegalStateException("Cannot invalidate original TileEntity " + key(), error); }
        finally { legacy = null; }
    }
    @Override public void setRemoved() {
        if (legacy != null) {
            try { saved = content.services().tiles().save(legacy); savedBlock = key(); }
            catch (Exception error) { throw new IllegalStateException("Cannot retain old tile data during removal", error); }
            invalidateLegacy();
        }
        super.setRemoved();
    }
}
