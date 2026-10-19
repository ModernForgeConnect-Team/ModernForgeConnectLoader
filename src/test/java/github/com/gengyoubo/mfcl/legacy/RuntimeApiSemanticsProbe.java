package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.mla.*;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;
import java.util.*;

/** Calls actual Forge 1.7.10 members using only public API types and descriptor strings. */
public final class RuntimeApiSemanticsProbe {
    @LegacyType("net.minecraft.item.ItemStack") public interface Stack {
        @LegacyMethod(name="func_77946_l", descriptor="()Lnet/minecraft/item/ItemStack;") LegacyObject copy();
        @LegacyMethod(name="func_77960_j", descriptor="()I") int damage();
        @LegacyField(name="field_77994_a", descriptor="I") int count();
        @LegacyField(name="field_77994_a", descriptor="I") void count(int count);
    }
    @LegacyType("net.minecraft.item.ItemStack") public interface Factory {
        @LegacyConstructor(descriptor="(Lnet/minecraft/item/Item;II)V") LegacyObject create(LegacyObject item, int count, int metadata);
    }
    static void verify(EmbeddedLegacyRuntime embedded, Map<String,Object> report) {
        LegacyRuntime api = embedded.developerApi(Set.of("minecraft", "Forge"));
        if (!api.registryKeys(LegacyRuntime.Registry.ITEM).contains("minecraft:diamond")
                || api.find(LegacyRuntime.Registry.BLOCK, "mfcl:missing").isPresent()
                || api.find(LegacyRuntime.Registry.BLOCK, "minecraft:stone").isEmpty()) throw new AssertionError("API registry lookup differs");
        var diamond = api.find(LegacyRuntime.Registry.ITEM, "minecraft:diamond").orElseThrow();
        var created = api.accessor(Factory.class).create(diamond, 5, 0);
        if (api.accessor(Stack.class, created).count() != 5) throw new AssertionError("MLA constructor failed");
        var original = new LegacyStack("minecraft:diamond", 3, 2, "{mfcl_api:{value:17},list:[{key:aer,amount:2},{key:terra,amount:3}]}");
        var old = api.stack(original); var counter = api.accessor(Stack.class, old); var copy = counter.copy();
        counter.count(7);
        if (api.accessor(Stack.class, copy).count() != 2 || counter.damage() != 3) throw new AssertionError("API stack copy aliases");
        var snapshot = api.snapshot(copy); var roundtrip = api.stack(snapshot);
        if (!api.snapshot(roundtrip).equals(snapshot) || !snapshot.snbt().contains("mfcl_api")) throw new AssertionError("API lost original indexed NBT");
        var before = Thread.currentThread().getContextClassLoader();
        try { api.accessor(Factory.class).create(api.find(LegacyRuntime.Registry.BLOCK, "minecraft:stone").orElseThrow(), 1, 0); throw new AssertionError("Wrong domain type accepted"); }
        catch (LegacyAccessException expected) { if (!expected.code().equals("MLA_INVOKE")) throw expected; }
        if (Thread.currentThread().getContextClassLoader() != before) throw new AssertionError("MLA leaked context loader");
        report.put("genericDeveloperApiVerified", true);
    }
}
