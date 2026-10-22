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
    /** Thaumcraft 4 recipe entry point; the Object[] mixes pattern strings, Character keys, ore names and old stacks. */
    @LegacyType("thaumcraft.api.ThaumcraftApi")
    public interface ArcaneRecipes {
        @LegacyMethod(name = "addArcaneCraftingRecipe", isStatic = true,
                descriptor = "(Ljava/lang/String;Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;[Ljava/lang/Object;)Lthaumcraft/api/crafting/ShapedArcaneRecipe;")
        LegacyObject arcane(String research, LegacyObject result, LegacyObject aspects, Object[] recipe);
    }
    @LegacyType("thaumcraft.api.aspects.AspectList")
    public interface AspectLists {
        @LegacyConstructor LegacyObject create();
    }
    /** Needs {@link LegacyFeatures#MLA_OBJECT_ARGUMENTS}; returns the original recipe (removable later with values().remove). */
    public static LegacyObject arcaneRecipe(LegacyRuntime runtime) {
        LegacyObject iron = runtime.stack(new LegacyStack("minecraft:iron_ingot", 0, 1, ""));
        LegacyObject result = runtime.stack(new LegacyStack("minecraft:diamond", 0, 1, ""));
        LegacyObject aspects = runtime.accessor(AspectLists.class).create();
        Object[] recipe = {"G G", " I ", "G G", 'G', "blockGlass", 'I', iron};
        return runtime.accessor(ArcaneRecipes.class).arcane("ASPECTS", result, aspects, recipe);
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
