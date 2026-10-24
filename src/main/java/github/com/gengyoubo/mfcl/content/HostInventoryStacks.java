package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Full native stack serialization, including capabilities, for items with no playable old counterpart. */
final class HostInventoryStacks {
    private static final String PAYLOAD = "mfcl_inventory_stack";
    private final LegacyServices services;

    HostInventoryStacks(LegacyServices services) throws Exception {
        this.services = services;
        // Both sides install one stable identity before loading original inventories or device data.
        LegacyHostStacks.install(services);
    }

    LegacyClientData.Stack toLegacy(ItemStack stack) throws Exception {
        return inHost(() -> encode(stack));
    }

    private static <T> T inHost(java.util.concurrent.Callable<T> work) throws Exception {
        var thread = Thread.currentThread(); var previous = thread.getContextClassLoader();
        try { thread.setContextClassLoader(HostInventoryStacks.class.getClassLoader()); return work.call(); }
        finally { thread.setContextClassLoader(previous); }
    }

    private LegacyClientData.Stack encode(ItemStack stack) throws Exception {
        var tag = new CompoundTag();
        // Count belongs to the outer stack. Normalizing it here lets split stacks merge again.
        var single = stack.copy(); single.setCount(1);
        tag.put(PAYLOAD, single.serializeNBT());
        tag.putInt(LegacyHostStacks.LIMIT, stack.getMaxStackSize());
        var display = new CompoundTag(); display.putString("Name", stack.getHoverName().getString()); tag.put("display", display);
        var bytes = new java.io.ByteArrayOutputStream(); NbtIo.writeCompressed(tag, bytes);
        return services.items().withTag(new LegacyClientData.Stack(LegacyHostStacks.ITEM, 0, stack.getCount(), stack.getHoverName().getString(), ""), bytes.toByteArray());
    }

    ItemStack toHost(LegacyClientData.Stack old) throws Exception {
        return inHost(() -> decode(old));
    }

    private ItemStack decode(LegacyClientData.Stack old) throws Exception {
        var tag = LegacyVanillaStacks.originalTag(services, old);
        if (!tag.contains(PAYLOAD, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Native inventory payload is missing");
        var stack = ItemStack.of(tag.getCompound(PAYLOAD));
        if (stack.isEmpty()) throw new IllegalArgumentException("Native inventory item is missing");
        stack.setCount(old.count());
        return stack;
    }
}
