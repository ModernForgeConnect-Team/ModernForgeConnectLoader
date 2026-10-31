package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.api.LegacyStack;
import github.com.gengyoubo.mfcl.api.spi.LegacyItemProvider;
import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import github.com.gengyoubo.mfcl.bridge.LegacyContent;
import github.com.gengyoubo.mfcl.bridge.LegacyServices;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/**
 * Host items split from one original item by {@link LegacyItemProvider}s. Each split item is the original item to old
 * code; the stored metadata and SNBT stay complete, so only the old-to-host direction selects among them.
 */
final class LegacyItemSplits {
    private static final java.util.regex.Pattern VARIANT = java.util.regex.Pattern.compile("[a-z0-9_.-]+");
    private record Split(LegacyItemProvider.ItemSplit rule, Map<String, Item> items) {}
    private record Selection(String key, int metadata, String snbt) {}
    private final LegacyServices services;
    private final Map<String, Split> splits = new HashMap<>();
    private final Map<Item, String> variants = new IdentityHashMap<>();
    private final Map<Selection, String> selected = Collections.synchronizedMap(new LinkedHashMap<>(256, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Selection, String> eldest) { return size() > 4096; }
    });
    private List<LegacyItemProvider> providers;

    LegacyItemSplits(LegacyServices services) { this.services = services; }

    /** Asks every provider once, in the item registration window. Null when the original item stays whole. */
    LegacyItemProvider.ItemSplit plan(LegacyContent.Item entry) {
        if (providers == null) {
            var providerMap = new LinkedHashMap<String, LegacyItemProvider>();
            for (var provider : github.com.gengyoubo.mfcl.api.LegacyItemProviders.providers()) providerMap.put(provider.getClass().getName(), provider);
            for (var provider : ServiceLoader.load(LegacyItemProvider.class, github.com.gengyoubo.mfcl.api.MfclApi.class.getClassLoader()))
                providerMap.putIfAbsent(provider.getClass().getName(), provider);
            providers = List.copyOf(providerMap.values());
        }
        if (providers.isEmpty()) return null;
        var declaration = declaration(entry);
        LegacyItemProvider.ItemSplit result = null;
        for (var provider : providers) {
            var split = provider.split(entry.key(), declaration);
            if (split == null) continue;
            if (result != null) throw new IllegalStateException("Duplicate host item split: " + entry.key());
            var names = split.variants();
            if (names == null || names.isEmpty()) throw new IllegalStateException("Host item split declares no variants: " + entry.key());
            if (new HashSet<>(names).size() != names.size()) throw new IllegalStateException("Duplicate host item split variant: " + entry.key());
            for (String name : names)
                if (name == null || !VARIANT.matcher(name).matches()) throw new IllegalStateException("Invalid host item split variant " + name + ": " + entry.key());
            result = split;
        }
        return result;
    }
    static ResourceLocation id(String key, String variant) {
        var base = ModernContentBridge.id(key);
        return ResourceLocation.fromNamespaceAndPath(base.getNamespace(), base.getPath() + "/" + variant);
    }
    void register(String key, LegacyItemProvider.ItemSplit rule, Map<String, Item> items) {
        splits.put(key, new Split(rule, Map.copyOf(items)));
        items.forEach((variant, item) -> variants.put(item, variant));
    }
    boolean any() { return !splits.isEmpty(); }
    boolean split(String key) { return splits.containsKey(key); }
    /** The split variant of a host item, or null for the original host item and unrelated items. */
    String variant(Item item) { return variants.get(item); }
    Collection<Item> items(String key) { var split = splits.get(key); return split == null ? List.of() : split.items().values(); }

    /** The split host item for an original stack, or null when it belongs on the original host item. */
    Item select(String key, int metadata, String snbt) {
        var split = splits.get(key); if (split == null) return null;
        String tag = snbt == null ? "" : snbt;
        var selection = new Selection(key, metadata, tag);
        String variant = selected.get(selection);
        if (variant == null && !selected.containsKey(selection)) {
            variant = split.rule().select(stack(key, metadata, tag));
            if (variant != null && !split.items().containsKey(variant))
                throw new IllegalStateException("Host item split selected an undeclared variant " + variant + ": " + key);
            selected.put(selection, variant);
        }
        return variant == null ? null : split.items().get(variant);
    }
    /** A saved stack moved to the item the current rules select; the same instance when it is already there. */
    ItemStack migrate(ItemStack stack, Item target) {
        if (stack.isEmpty() || target == null || stack.getItem() == target) return stack;
        CompoundTag saved = stack.serializeNBT();
        saved.putString("id", BuiltInRegistries.ITEM.getKey(target).toString());
        return ItemStack.of(saved);
    }

    private LegacyItemProvider.Stack stack(String key, int metadata, String snbt) {
        return new LegacyItemProvider.Stack() {
            private CompoundTag tag; private boolean converted;
            @Override public int metadata() { return metadata; }
            @Override public String snbt() { return snbt; }
            @Override public Object hostTag() {
                if (!converted) {
                    try {
                        byte[] bytes = services.items().tagBytes(new LegacyClientData.Stack(key, metadata, 1, "", snbt));
                        tag = bytes == null ? null : net.minecraft.nbt.NbtIo.readCompressed(new java.io.ByteArrayInputStream(bytes));
                    } catch (Exception failure) { throw new IllegalStateException("Cannot read original NBT of " + key, failure); }
                    converted = true;
                }
                return tag == null ? null : tag.copy();
            }
        };
    }
    private LegacyItemProvider.ItemDeclaration declaration(LegacyContent.Item entry) {
        var metadata = entry.variants().stream().map(LegacyContent.Variant::metadata).distinct().sorted().toList();
        return new LegacyItemProvider.ItemDeclaration() {
            private List<LegacyStack> catalog;
            @Override public List<Integer> metadata() { return metadata; }
            @Override public int maxDamage() { return entry.maxDamage(); }
            @Override public List<LegacyStack> catalog() {
                if (catalog == null) try {
                    catalog = services.items().catalog(entry.key()).stream().filter(stack -> stack.metadata() >= 0)
                            .map(stack -> new LegacyStack(stack.key(), stack.metadata(), Math.max(1, Math.min(127, stack.count())), stack.tag())).toList();
                } catch (Exception failure) { throw new IllegalStateException("Cannot list original stacks of " + entry.key(), failure); }
                return catalog;
            }
        };
    }
}
