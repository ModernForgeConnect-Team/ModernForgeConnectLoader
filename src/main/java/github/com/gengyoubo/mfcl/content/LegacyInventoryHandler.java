package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import java.util.concurrent.Callable;

/** Native hopper/capability access to the actual old inventory, including its original side and slot rules. */
final class LegacyInventoryHandler implements IItemHandler {
    private final LegacyBlockEntity tile;
    private final Integer side;
    private boolean failureReported;
    LegacyInventoryHandler(LegacyBlockEntity tile, Direction side) { this.tile = tile; this.side = side == null ? null : side.get3DDataValue(); }
    private <T> T call(Callable<T> action) {
        if (tile.isRemoved()) throw new IllegalStateException("Inventory capability belongs to a removed block entity");
        try { return action.call(); } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IllegalStateException("Original inventory operation failed at " + tile.getBlockPos(),error); }
    }
    private <T> T guarded(Callable<T> action, T rejected) {
        try { return call(action); }
        catch (Exception failure) {
            if (!failureReported) {
                failureReported = true;
                com.mojang.logging.LogUtils.getLogger().error("MFCL rejected incompatible inventory operation at {}",tile.getBlockPos(),failure);
            }
            return rejected;
        }
    }
    private int slot(int slot) throws Exception {
        var slots = tile.content().services().inventories().slots(tile.legacy(),side);
        if (slot < 0 || slot >= slots.length) throw new IndexOutOfBoundsException(slot);
        return slots[slot];
    }
    @Override public int getSlots() { return guarded(() -> tile.content().services().inventories().slots(tile.legacy(),side).length,0); }
    @Override public ItemStack getStackInSlot(int slot) {
        return guarded(() -> tile.content().gameplayStack(tile.content().services().inventories().get(tile.legacy(),slot(slot))),ItemStack.EMPTY);
    }
    @Override public int getSlotLimit(int slot) { return guarded(() -> { slot(slot); return tile.content().services().inventories().limit(tile.legacy()); },0); }
    @Override public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return guarded(() -> tile.content().services().inventories().allows(tile.legacy(),slot(slot),tile.content().gameplayStack(stack),side,true),false);
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        return guarded(() -> {
            var content = tile.content(); var access = content.services().inventories(); var old = tile.legacy(); int physical = slot(slot);
            var incoming = content.gameplayStack(stack);
            if (!access.allows(old,physical,incoming,side,true)) return stack;
            var present = content.gameplayStack(access.get(old,physical));
            if (!present.isEmpty() && !ItemStack.isSameItemSameTags(present,stack)) return stack;
            int count = Math.min(stack.getCount(),Math.min(access.limit(old),stack.getMaxStackSize())-present.getCount());
            if (count <= 0) return stack;
            if (!simulate) {
                if (tile.getLevel().isClientSide) throw new IllegalStateException("Client inventory mutation is not authoritative");
                var combined = stack.copy(); combined.setCount(present.getCount()+count);
                content.logicTransaction(tile.getLevel(),tile.getBlockPos(),() -> { access.set(old,physical,content.gameplayStack(combined)); return null; },true);
            }
            var remainder = stack.copy(); remainder.shrink(count); return remainder;
        },stack);
    }
    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;
        return guarded(() -> {
            var content = tile.content(); var access = content.services().inventories(); var old = tile.legacy(); int physical = slot(slot);
            var original = access.get(old,physical);
            if (original == null || !access.allows(old,physical,original,side,false)) return ItemStack.EMPTY;
            var preview = content.gameplayStack(original); preview.setCount(Math.min(amount,preview.getCount()));
            if (simulate) return preview;
            if (tile.getLevel().isClientSide) throw new IllegalStateException("Client inventory mutation is not authoritative");
            return content.logicTransaction(tile.getLevel(),tile.getBlockPos(),() -> {
                var removed = content.gameplayStack(access.remove(old,physical,preview.getCount()));
                if (removed.getCount() > preview.getCount() || !removed.isEmpty() && !ItemStack.isSameItemSameTags(removed,preview))
                    throw new IllegalStateException("Original inventory extraction changed item identity/count");
                return removed;
            },true);
        },ItemStack.EMPTY);
    }
}
