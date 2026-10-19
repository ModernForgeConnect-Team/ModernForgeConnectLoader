package example;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;

/** Invoke initialize() in the modern addon constructor. No old Minecraft compile dependency is needed. */
public final class LegacyAddon {
    /** Query from the modern common setup event or later; no gameplay API readiness is needed. */
    public static String thaumcraftVersion() {
        return MfclApi.mod("Thaumcraft").map(LegacyModInfo::version).orElse("");
    }
    @LegacyType("net.minecraft.item.ItemStack")
    public interface OriginalStack {
        @LegacyMethod(name="func_77946_l") OriginalStack copy();
        @LegacyField(name="field_77994_a") int count();
    }
    public static AutoCloseable initialize() {
        return MfclApi.whenReady(runtime -> {
            if (!runtime.registryKeys(LegacyRuntime.Registry.ITEM).contains("minecraft:diamond")) return;
            LegacyObject original = runtime.stack(new LegacyStack("minecraft:diamond", 0, 2, ""));
            OriginalStack accessor = runtime.accessor(OriginalStack.class, original);
            OriginalStack copy = accessor.copy();
            if (copy.count() != accessor.count()) throw new IllegalStateException("Copy differs");
        });
    }
}
