package example;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;

/** Invoke initialize() in the modern addon constructor. No old Minecraft compile dependency is needed. */
public final class LegacyAddon {
    @LegacyType("net.minecraft.item.ItemStack")
    public interface OriginalStack {
        @LegacyMethod(name="func_77946_l", descriptor="()Lnet/minecraft/item/ItemStack;") LegacyObject copy();
        @LegacyField(name="field_77994_a", descriptor="I") int count();
    }
    public static AutoCloseable initialize() {
        return MfclApi.whenReady(runtime -> {
            if (!runtime.registryKeys(LegacyRuntime.Registry.ITEM).contains("minecraft:diamond")) return;
            LegacyObject original = runtime.stack(new LegacyStack("minecraft:diamond", 0, 2, ""));
            OriginalStack accessor = runtime.accessor(OriginalStack.class, original);
            LegacyObject copy = accessor.copy();
            if (runtime.snapshot(copy).count() != accessor.count()) throw new IllegalStateException("Copy differs");
        });
    }
}
