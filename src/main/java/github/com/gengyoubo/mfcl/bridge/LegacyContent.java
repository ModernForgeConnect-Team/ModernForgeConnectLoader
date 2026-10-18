package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Frozen declarations from the real old registries. No Minecraft types cross the runtime boundary. */
public final class LegacyContent {
    public record Variant(int metadata, String nameKey, String icon, int color) {}
    public record Item(String key, String blockKey, int stackLimit, int maxDamage, boolean creative, List<Variant> variants) {
        public Item { variants = List.copyOf(variants); }
    }
    public record Block(String key, float hardness, float resistance, int light, boolean opaque,
                        boolean tileEntity, int renderType, List<List<String>> faces, List<Integer> tileMetas) {
        public Block {
            faces = faces.stream().map(List::copyOf).toList();
            tileMetas = List.copyOf(tileMetas);
        }
        public boolean simple() { return renderType == 0 && !tileEntity; }
        public boolean hasTile(int metadata) { return tileMetas.contains(metadata); }
    }
    private final LegacyClientData data;
    private final List<Item> items;
    private final List<Block> blocks;
    private final List<LegacyCreativeTabs.Tab> tabs;
    private final Map<String, Object> oldBlocks = new HashMap<>();

    public LegacyContent(LegacyClientData data) throws Exception {
        this.data = data;
        var declarations = data.inLegacy(() -> read());
        items = declarations.items; blocks = declarations.blocks; tabs = declarations.tabs;
    }
    private record Declarations(List<Item> items, List<Block> blocks, List<LegacyCreativeTabs.Tab> tabs) {}
    private Declarations read() throws Exception {
        ClassLoader loader = data.loader();
        Class<?> blockType = loader.loadClass("net.minecraft.block.Block"), itemType = loader.loadClass("net.minecraft.item.Item");
        Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack"), itemBlock = loader.loadClass("net.minecraft.item.ItemBlock");
        Class<?> iconType = loader.loadClass("net.minecraft.util.IIcon");
        Object blockRegistry = blockType.getField("field_149771_c").get(null), itemRegistry = itemType.getField("field_150901_e").get(null);
        Method blockKey = blockRegistry.getClass().getMethod("func_148750_c", Object.class), itemKey = itemRegistry.getClass().getMethod("func_148750_c", Object.class);
        Set<String> customItems = new HashSet<>();
        Map<String, Object> itemsByKey = new HashMap<>();
        for (Object item : (Iterable<?>) itemRegistry) {
            String key = (String) itemKey.invoke(itemRegistry, item);
            if (key == null || key.startsWith("minecraft:")) continue;
            customItems.add(key); itemsByKey.put(key, item);
        }
        var tabValues = LegacyCreativeTabs.read(data, customItems);
        // Some legacy blocks index an icon array directly by metadata and throw for undeclared values,
        // so only probe the metadata their ItemBlock actually exposes.
        Map<String, Set<Integer>> declaredBlockMetas = new HashMap<>();
        for (var stack : data.catalog()) declaredBlockMeta(declaredBlockMetas, stack, itemsByKey, itemBlock, blockRegistry, blockKey);
        for (var tab : tabValues) for (var stack : tab.contents()) declaredBlockMeta(declaredBlockMetas, stack, itemsByKey, itemBlock, blockRegistry, blockKey);
        var blockValues = new ArrayList<Block>();
        for (Object block : (Iterable<?>) blockRegistry) {
            String key = (String) blockKey.invoke(blockRegistry, block);
            if (key == null || key.startsWith("minecraft:")) continue;
            Set<Integer> declaredMetas = declaredBlockMetas.get(key);
            var faces = new ArrayList<List<String>>();
            for (int meta = 0; meta < 16; meta++) {
                var sides = new ArrayList<String>();
                boolean probe = meta == 0 || declaredMetas == null || declaredMetas.contains(meta);
                for (int side = 0; side < 6; side++) sides.add(probe ? icon(blockType, block, side, meta, iconType) : "");
                faces.add(sides);
            }
            var tileMetas = new ArrayList<Integer>();
            for (int meta = 0; meta < 16; meta++) if ((boolean) blockType.getMethod("hasTileEntity", int.class).invoke(block, meta)) tileMetas.add(meta);
            blockValues.add(new Block(key, number(blockType, block, "field_149782_v"),
                    ((Number) blockType.getMethod("func_149638_a", loader.loadClass("net.minecraft.entity.Entity")).invoke(block, new Object[]{null})).floatValue(),
                    (int) blockType.getMethod("func_149750_m").invoke(block), (boolean) blockType.getMethod("func_149662_c").invoke(block),
                    !tileMetas.isEmpty(), (int) blockType.getMethod("func_149645_b").invoke(block), faces, tileMetas));
            oldBlocks.put(key, block);
        }
        var itemValues = new ArrayList<Item>();
        for (Object item : (Iterable<?>) itemRegistry) {
            String key = (String) itemKey.invoke(itemRegistry, item);
            if (key == null || key.startsWith("minecraft:")) continue;
            String linkedBlock = null;
            if (itemBlock.isInstance(item)) linkedBlock = (String) blockKey.invoke(blockRegistry, itemBlock.getField("field_150939_a").get(item));
            var variants = new ArrayList<Variant>();
            var metas = new TreeSet<Integer>();
            for (var stack : data.catalog()) if (stack.key().equals(key)) metas.add(stack.metadata());
            for (var tab : tabValues) for (var stack : tab.contents()) if (stack.key().equals(key)) metas.add(stack.metadata());
            if (metas.isEmpty()) metas.add(0);
            int limit = 64, damage = 0;
            for (int meta : metas) {
                Object stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, meta);
                String name; String texture; int color;
                try {
                    name = (String) itemType.getMethod("func_77667_c", stackType).invoke(item, stack);
                    Object icon = itemType.getMethod("getIcon", stackType, int.class).invoke(item, stack, 0);
                    texture = icon == null ? "" : (String) iconType.getMethod("func_94215_i").invoke(icon);
                    color = (int) itemType.getMethod("func_82790_a", stackType, int.class).invoke(item, stack, 0);
                    limit = Math.min(limit, (int) itemType.getMethod("getItemStackLimit", stackType).invoke(item, stack));
                    damage = Math.max(damage, (int) itemType.getMethod("getMaxDamage", stackType).invoke(item, stack));
                } catch (ReflectiveOperationException undeclared) {
                    name = (String) itemType.getMethod("func_77658_a").invoke(item);
                    texture = ""; color = 0xFFFFFF;
                }
                variants.add(new Variant(meta, name + ".name", texture, color));
            }
            boolean creative = Arrays.stream((Object[]) itemType.getMethod("getCreativeTabs").invoke(item)).anyMatch(Objects::nonNull);
            itemValues.add(new Item(key, linkedBlock, Math.max(1, Math.min(64, limit)), damage, creative, variants));
        }
        blockValues.sort(Comparator.comparing(Block::key)); itemValues.sort(Comparator.comparing(Item::key));
        return new Declarations(List.copyOf(itemValues), List.copyOf(blockValues), tabValues);
    }
    private static void declaredBlockMeta(Map<String, Set<Integer>> target, LegacyClientData.Stack stack, Map<String, Object> itemsByKey,
                                          Class<?> itemBlock, Object blockRegistry, Method blockKey) throws Exception {
        if (stack.key().startsWith("minecraft:")) return;
        Object item = itemsByKey.get(stack.key());
        if (!itemBlock.isInstance(item)) return;
        String key = (String) blockKey.invoke(blockRegistry, itemBlock.getField("field_150939_a").get(item));
        if (key == null || key.startsWith("minecraft:")) return;
        target.computeIfAbsent(key, ignored -> new TreeSet<>()).add(stack.metadata());
    }
    private static String icon(Class<?> blockType, Object block, int side, int meta, Class<?> iconType) throws Exception {
        try {
            Object icon = blockType.getMethod("func_149691_a", int.class, int.class).invoke(block, side, meta);
            return icon == null ? "" : (String) iconType.getMethod("func_94215_i").invoke(icon);
        } catch (ReflectiveOperationException undeclared) {
            return "";
        }
    }
    private static float number(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.getFloat(target);
    }
    public List<Item> items() { return items; }
    public List<Block> blocks() { return blocks; }
    public List<LegacyCreativeTabs.Tab> tabs() { return tabs; }
    public ClassLoader loader() { return data.loader(); }
    /** Execute the original vanilla-style drop methods, preserving custom ore drop identity and metadata. */
    public List<LegacyClientData.Stack> drops(String key, int metadata, long seed) throws Exception {
        return data.inLegacy(() -> {
            Object block = Objects.requireNonNull(oldBlocks.get(key)); Class<?> type = data.loader().loadClass("net.minecraft.block.Block");
            Random random = new Random(seed);
            int count = (int) type.getMethod("func_149679_a", int.class, Random.class).invoke(block, 0, random);
            int damage = (int) type.getMethod("func_149692_a", int.class).invoke(block, metadata);
            Class<?> itemType = data.loader().loadClass("net.minecraft.item.Item"), stackType = data.loader().loadClass("net.minecraft.item.ItemStack");
            var result = new ArrayList<LegacyClientData.Stack>();
            for (int i = 0; i < count; i++) {
                Object item = type.getMethod("func_149650_a", int.class, Random.class, int.class).invoke(block, metadata, random, 0);
                if (item != null) result.add(data.snapshot(stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, damage)));
            }
            return List.copyOf(result);
        });
    }
}
