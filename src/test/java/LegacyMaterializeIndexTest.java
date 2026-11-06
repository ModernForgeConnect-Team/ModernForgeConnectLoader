package github.com.gengyoubo.mfcl.bridge;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real lookup against minimal 1.7.10-shaped classes, so it needs neither the legacy runtime nor a game. */
class LegacyMaterializeIndexTest {
    private static final String MODE = "mfcl.debug.materializeIndex";
    private static final String ITEM = """
            package net.minecraft.item;
            public class Item {
                public static final Registry field_150901_e = new Registry();
                public final String name;
                public Item(String name) { this.name = name; field_150901_e.items.put(name, this); }
                public static final class Registry {
                    final java.util.Map<String, Item> items = new java.util.HashMap<>();
                    public String func_148750_c(Object item) { return ((Item) item).name; }
                    public Object func_82594_a(String name) { return items.get(name); }
                }
            }""";
    /** The tag prints only its text, like old NBT that omits array contents, but compares its payload too. */
    private static final String TAG = """
            package net.minecraft.item;
            public record FakeTag(String text, String payload) {
                @Override public String toString() { return text; }
            }""";
    private static final String STACK = """
            package net.minecraft.item;
            public class ItemStack {
                public final Item item; public int field_77994_a; public final int damage; public Object field_77990_d; public String displayName = "";
                public ItemStack(Item item, int count, int damage) { this.item = item; field_77994_a = count; this.damage = damage; }
                public Item func_77973_b() { return item; }
                public int func_77960_j() { return damage; }
                public String func_82833_r() { return displayName; }
                public ItemStack func_77946_l() {
                    var copy = new ItemStack(item, field_77994_a, damage);
                    copy.field_77990_d = field_77990_d; copy.displayName = displayName;
                    return copy;
                }
                public static boolean func_77970_a(ItemStack a, ItemStack b) { return java.util.Objects.equals(a.field_77990_d, b.field_77990_d); }
            }""";

    @TempDir Path classes;
    private URLClassLoader loader;
    private Class<?> stackType, itemType, tagType;

    @BeforeEach void compileLegacyShapes() throws Exception {
        var package_ = Files.createDirectories(classes.resolve("net/minecraft/item"));
        var arguments = new ArrayList<>(List.of("-d", classes.toString()));
        for (var source : List.of(new String[]{"Item", ITEM}, new String[]{"FakeTag", TAG}, new String[]{"ItemStack", STACK})) {
            var file = package_.resolve(source[0] + ".java");
            Files.writeString(file, source[1]);
            arguments.add(file.toString());
        }
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, arguments.toArray(String[]::new)));
        loader = new URLClassLoader(new URL[]{classes.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        stackType = loader.loadClass("net.minecraft.item.ItemStack");
        itemType = loader.loadClass("net.minecraft.item.Item");
        tagType = loader.loadClass("net.minecraft.item.FakeTag");
        itemType.getConstructor(String.class).newInstance("mod:apple");
    }

    @AfterEach void cleanUp() throws Exception {
        System.clearProperty(MODE);
        loader.close();
    }

    private LegacyClientData data(String mode) throws Exception {
        if (mode != null) System.setProperty(MODE, mode);
        return new LegacyClientData(loader);
    }

    private Object stack(int count, String name, String tagText, String payload) throws Exception {
        Object item = itemType.getField("field_150901_e").get(null).getClass().getMethod("func_82594_a", String.class)
                .invoke(itemType.getField("field_150901_e").get(null), "mod:apple");
        Object stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, count, 3);
        stackType.getField("displayName").set(stack, name);
        if (tagText != null) stackType.getField("field_77990_d").set(stack, tagType.getConstructors()[0].newInstance(tagText, payload));
        return stack;
    }

    private Object tag(Object stack) throws Exception { return stackType.getField("field_77990_d").get(stack); }
    private int count(Object stack) throws Exception { return stackType.getField("field_77994_a").getInt(stack); }
    private static LegacyClientData.Stack request(int count, String name, String tag) {
        return new LegacyClientData.Stack("mod:apple", 3, count, name, tag);
    }

    @Test void findsVariantWhenCountAndNameDiffer() throws Exception {
        var data = data(null);
        Object stored = stack(5, "Apple", "{a}", "one");
        data.snapshot(stored);
        Object result = data.materialize(request(2, "", "{a}"));
        assertNotSame(stored, result);
        assertEquals(2, count(result));
        assertEquals(tag(stored), tag(result));
        assertEquals("Apple", stackType.getField("displayName").get(result));
    }

    @Test void returnedCopyDoesNotChangeTheIndexedOriginal() throws Exception {
        var data = data(null);
        data.snapshot(stack(5, "Apple", "{a}", "one"));
        var request = request(2, "", "{a}");
        stackType.getField("field_77994_a").setInt(data.materialize(request), 40);
        assertEquals(7, count(data.materialize(new LegacyClientData.Stack("mod:apple", 3, 7, "", "{a}"))));
        assertEquals(2, count(data.materialize(request)));
    }

    @Test void exactStackWinsOverFirstVariant() throws Exception {
        var data = data(null);
        data.snapshot(stack(1, "First", "{a}", "one"));
        var second = data.snapshot(stack(1, "Second", "{a}", "two"));
        assertEquals(tag(stack(1, "", "{a}", "two")), tag(data.materialize(second)));
    }

    @Test void firstWrittenOriginalRepresentsAVariant() throws Exception {
        var data = data(null);
        data.snapshot(stack(1, "First", "{a}", "one"));
        for (int i = 0; i < 200; i++) data.snapshot(stack(1, "Later" + i, "{a}", "later"));
        assertEquals(tag(stack(1, "", "{a}", "one")), tag(data.materialize(request(1, "", "{a}"))));
    }

    @Test void otherTagOrMetadataIsNotAVariantHit() throws Exception {
        var data = data(null);
        data.snapshot(stack(1, "Apple", "{a}", "one"));
        Object result = data.materialize(request(4, "", ""));
        assertNull(tag(result));
        assertEquals(4, count(result));
        Object otherDamage = data.materialize(new LegacyClientData.Stack("mod:apple", 9, 1, "", ""));
        assertEquals(9, stackType.getField("damage").getInt(otherDamage));
    }

    @Test void unknownItemIsStillRejected() throws Exception {
        var data = data(null);
        assertThrows(IllegalArgumentException.class, () -> data.materialize(new LegacyClientData.Stack("mod:missing", 0, 1, "", "")));
    }

    @Test void scanModeFindsTheSameVariants() throws Exception {
        var data = data("scan");
        data.snapshot(stack(5, "Apple", "{a}", "one"));
        assertEquals(tag(stack(1, "", "{a}", "one")), tag(data.materialize(request(2, "", "{a}"))));
        assertNull(tag(data.materialize(request(2, "", ""))));
    }

    @Test void verifyModeCountsLookupsWithoutMismatches() throws Exception {
        var data = data("verify");
        var exact = data.snapshot(stack(5, "Apple", "{a}", "one"));
        data.snapshot(stack(6, "Apple", "{a}", "one"));
        data.materialize(exact);
        data.materialize(request(1, "", "{a}"));
        data.materialize(request(1, "", ""));
        var stats = data.materializeIndexStats();
        assertEquals(1, stats.exactHits());
        assertEquals(1, stats.variantHits());
        assertEquals(1, stats.misses());
        assertEquals(1, stats.ambiguousVariants());
        assertEquals(4, stats.entriesVisited());
        assertEquals(0, stats.mismatches());
        assertEquals(0, stats.failures());
        assertEquals(2, stats.originals());
        assertEquals(1, stats.variants());
    }

    @Test void verifyModeReportsOriginalsHiddenByTheirTagText() throws Exception {
        var data = data("verify");
        data.snapshot(stack(1, "First", "{a}", "one"));
        data.snapshot(stack(1, "Second", "{a}", "two"));
        data.materialize(request(1, "", "{a}"));
        assertEquals(1, data.materializeIndexStats().mismatches());
    }

    @Test void statsStayEmptyOutsideVerifyMode() throws Exception {
        var data = data(null);
        var exact = data.snapshot(stack(5, "Apple", "{a}", "one"));
        data.materialize(exact);
        data.materialize(request(1, "", "{a}"));
        var stats = data.materializeIndexStats();
        assertEquals(0, stats.exactHits() + stats.variantHits() + stats.misses() + stats.entriesVisited());
    }
}
