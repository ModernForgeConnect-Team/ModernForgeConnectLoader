package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/**
 * Frozen 1.7.10 OreDictionary entries mapped to host forge: item tags. Host tags match a whole item, while one
 * original item carries all its metadata variants, so an item is tagged only when the entries for that name cover
 * every one of its variants (or use the wildcard); otherwise unrelated variants would join the tag.
 */
public final class LegacyOreDictionary {
    private static final int WILDCARD = 32767;
    /** Ore dictionary prefix to forge tag folder, following the 1.20 forge: naming convention. */
    private static final Map<String, String> PREFIXES = Map.ofEntries(
            Map.entry("nugget", "nuggets"), Map.entry("ingot", "ingots"), Map.entry("ore", "ores"), Map.entry("dust", "dusts"),
            Map.entry("gem", "gems"), Map.entry("block", "storage_blocks"), Map.entry("plate", "plates"), Map.entry("gear", "gears"),
            Map.entry("rod", "rods"), Map.entry("stick", "rods"), Map.entry("wire", "wires"), Map.entry("raw", "raw_materials"));
    private final Map<String, List<LegacyClientData.Stack>> entries = new LinkedHashMap<>();

    public LegacyOreDictionary(LegacyServices services) throws Exception {
        services.items().inLegacy(() -> {
            var dictionary = services.loader().loadClass("net.minecraftforge.oredict.OreDictionary");
            for (String name : (String[]) dictionary.getMethod("getOreNames").invoke(null)) {
                var stacks = new ArrayList<LegacyClientData.Stack>();
                for (Object stack : (List<?>) dictionary.getMethod("getOres", String.class).invoke(null, name)) {
                    if (stack == null) continue;
                    var snapshot = services.items().snapshot(stack);
                    if (snapshot != null && snapshot.tag().isEmpty() && !snapshot.key().startsWith("minecraft:")) stacks.add(snapshot);
                }
                if (!stacks.isEmpty()) entries.put(name, List.copyOf(stacks));
            }
            return null;
        });
    }

    /** nuggetIron -> forge:nuggets/iron; null for names without a known material prefix. */
    public static String forgeTag(String oreName) {
        for (var prefix : PREFIXES.entrySet()) {
            String name = prefix.getKey();
            if (oreName.length() <= name.length() || !oreName.startsWith(name) || !Character.isUpperCase(oreName.charAt(name.length()))) continue;
            String material = oreName.substring(name.length()).replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
            return material.matches("[a-z0-9_]+") ? "forge:" + prefix.getValue() + "/" + material : null;
        }
        return null;
    }

    /** Forge tag to the exact original subtypes listed under it; wildcard entries keep metadata 32767. */
    public Map<String, List<LegacyClientData.Stack>> subtypes() {
        var result = new LinkedHashMap<String, List<LegacyClientData.Stack>>();
        for (var entry : entries.entrySet()) {
            String tag = forgeTag(entry.getKey());
            if (tag != null) result.computeIfAbsent(tag, ignored -> new ArrayList<>()).addAll(entry.getValue());
        }
        return result;
    }

    /** Registers host tags for original items whose declared variants are all inside one ore dictionary name. */
    public void tag(LegacyServices services, Map<String, Set<Integer>> variantsByItem) {
        for (var entry : entries.entrySet()) {
            String tag = forgeTag(entry.getKey());
            if (tag == null) continue;
            var metadataByItem = new HashMap<String, Set<Integer>>();
            for (var stack : entry.getValue()) metadataByItem.computeIfAbsent(stack.key(), ignored -> new HashSet<>()).add(stack.metadata());
            for (var item : metadataByItem.entrySet()) {
                var variants = variantsByItem.get(item.getKey());
                if (variants == null) continue;
                if (item.getValue().contains(WILDCARD) || item.getValue().containsAll(variants)) services.tagItem(tag, item.getKey());
            }
        }
    }
}
