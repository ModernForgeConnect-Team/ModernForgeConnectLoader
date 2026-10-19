package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Executes real old tab callbacks, retaining tab identity, membership, order, icon metadata and NBT. */
public final class LegacyCreativeTabs {
    public record Tab(String key, String label, String nameKey, LegacyClientData.Stack icon,
                      List<LegacyClientData.Stack> contents, boolean builtin, boolean showTitle,
                      boolean scroll, boolean searchBar, int searchWidth) {
        public Tab { contents = List.copyOf(contents); }
    }
    private LegacyCreativeTabs() {}
    public static List<Tab> read(LegacyClientData data, Set<String> customItems) throws Exception {
        return data.inLegacy(() -> {
            Class<?> type = data.loader().loadClass("net.minecraft.creativetab.CreativeTabs");
            Set<Object> builtins = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Field field : type.getFields()) if (Modifier.isStatic(field.getModifiers()) && field.getType() == type) builtins.add(field.get(null));
            var sources = new ArrayList<Object>(); Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Object tab : (Object[]) type.getField("field_78032_a").get(null)) if (tab != null && seen.add(tab)) sources.add(tab);
            Class<?> itemType = data.loader().loadClass("net.minecraft.item.Item");
            Object registry = itemType.getField("field_150901_e").get(null);
            for (Object item : (Iterable<?>) registry) for (Object tab : (Object[]) itemType.getMethod("getCreativeTabs").invoke(item))
                if (tab != null && seen.add(tab)) sources.add(tab);
            var result = new ArrayList<Tab>(); Set<String> keys = new HashSet<>();
            for (Object source : sources) {
                boolean builtin = builtins.contains(source);
                String label = (String) type.getMethod("func_78013_b").invoke(source);
                // Search/inventory are special old screens, not categories to duplicate in the modern client.
                if (builtin && (label.equals("search") || label.equals("inventory"))) continue;
                var originals = new ArrayList<Object>(); type.getMethod("func_78018_a", List.class).invoke(source, originals);
                var contents = new ArrayList<LegacyClientData.Stack>();
                for (Object stack : originals) {
                    var snapshot = data.snapshot(stack);
                    if (!builtin || customItems.contains(snapshot.key())) contents.add(snapshot);
                }
                if (builtin && contents.isEmpty()) continue;
                String owner = builtin ? "minecraft" : owner(data, source, contents);
                String key = owner + ":" + label;
                if (keys.contains(key)) {
                    key += "/" + source.getClass().getName();
                    if (keys.contains(key)) key += "/" + type.getMethod("func_78021_a").invoke(source);
                }
                if (!keys.add(key)) throw new IllegalStateException("Ambiguous old creative tab " + label);
                Object icon = type.getMethod("func_151244_d").invoke(source);
                var iconStack = icon == null ? null : data.snapshot(icon);
                result.add(new Tab(key, label, (String) type.getMethod("func_78024_c").invoke(source), iconStack, contents, builtin,
                        (boolean) type.getMethod("func_78019_g").invoke(source), (boolean) type.getMethod("func_78017_i").invoke(source),
                        (boolean) type.getMethod("hasSearchBar").invoke(source), (int) type.getMethod("getSearchbarWidth").invoke(source)));
            }
            return List.copyOf(result);
        });
    }
    private static String owner(LegacyClientData data, Object source, List<LegacyClientData.Stack> contents) throws Exception {
        Set<String> namespaces = new TreeSet<>();
        for (var stack : contents) if (stack.key() != null && stack.key().contains(":") && !stack.key().startsWith("minecraft:")) namespaces.add(stack.key().split(":", 2)[0]);
        if (namespaces.size() == 1) return namespaces.iterator().next();
        var code = source.getClass().getProtectionDomain().getCodeSource();
        if (code != null && code.getLocation() != null && code.getLocation().getProtocol().equals("file")) {
            var file = java.nio.file.Path.of(code.getLocation().toURI()).toFile().getCanonicalFile();
            Class<?> loader = data.loader().loadClass("cpw.mods.fml.common.Loader");
            Class<?> container = data.loader().loadClass("cpw.mods.fml.common.ModContainer");
            for (Object mod : (Iterable<?>) loader.getMethod("getModList").invoke(loader.getMethod("instance").invoke(null))) {
                Object archive = container.getMethod("getSource").invoke(mod);
                if (archive instanceof java.io.File jar && jar.getCanonicalFile().equals(file)) return (String) container.getMethod("getModId").invoke(mod);
            }
        }
        return "mfcl";
    }
}
