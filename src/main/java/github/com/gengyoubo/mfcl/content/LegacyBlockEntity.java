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
    private byte[] saved = new byte[0];
    private String savedBlock;
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
                legacy = content.services().tiles().create(key(), getBlockState().getValue(ModernContentBridge.METADATA), world, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ());
                if (legacy != null && saved.length != 0) {
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
        if (legacy != null) invalidateLegacy();
        saved = tag.getByteArray("mfcl_legacy_nbt"); savedBlock = tag.contains("mfcl_legacy_block") ? tag.getString("mfcl_legacy_block") : null;
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
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
