package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Frozen declarations from the real old registries. No Minecraft types cross the runtime boundary. */
public final class LegacyContent {
    public record Layer(String icon, int color) {}
    public record Variant(int metadata, String nameKey, String icon, int color, List<Layer> layers) {
        public Variant { layers = List.copyOf(layers); }
    }
    public record Item(String key, String blockKey, int stackLimit, int maxDamage, boolean creative, List<Variant> variants) {
        public Item { variants = List.copyOf(variants); }
    }
    public record Block(String key, float hardness, float resistance, int light, boolean opaque,
                        boolean tileEntity, int renderType, boolean inventory3D, List<List<String>> faces, List<Integer> tileMetas) {
        public Block {
            faces = faces.stream().map(List::copyOf).toList();
            tileMetas = List.copyOf(tileMetas);
        }
        public boolean simple() { return renderType == 0 && !tileEntity; }
        public boolean hasTile(int metadata) { return tileMetas.contains(metadata); }
        /** Whether the original inventory model is a block cube instead of a flat sprite. */
        public boolean blockModel() { return inventory3D; }
    }
    private final LegacyClientData data;
    private final List<Item> items;
    private final List<Block> blocks;
    private final List<LegacyCreativeTabs.Tab> tabs;
    private final Map<String, Object> oldBlocks = new HashMap<>();
    private final Map<String, List<LegacyVertexStream.Primitive>> geometry = new LinkedHashMap<>();
    /** IItemRenderer geometry per {@code ItemRenderType}, so held/entity models can differ from the inventory snapshot. */
    private final Map<String, List<LegacyVertexStream.Primitive>> contextGeometry = new LinkedHashMap<>();
    private final Set<String> customItems = new LinkedHashSet<>();
    private final List<String> captureDiagnostics = new ArrayList<>();

    public LegacyContent(LegacyClientData data) throws Exception {
        this.data = data;
        var declarations = data.inLegacy(() -> read());
        items = declarations.items; blocks = declarations.blocks; tabs = declarations.tabs;
        captureStaticGeometry();
        captureItemRenderers();
    }

    /** Executes the real old Forge IItemRenderer (wands, banners, ...) and keeps its captured geometry. */
    private void captureItemRenderers() {
        try {
            ClassLoader loader = data.loader();
            Class<?> forgeClient = loader.loadClass("net.minecraftforge.client.MinecraftForgeClient");
            Class<?> rendererType = loader.loadClass("net.minecraftforge.client.IItemRenderer");
            Class<?> renderType = loader.loadClass("net.minecraftforge.client.IItemRenderer$ItemRenderType");
            Class<?> stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Method getRenderer = forgeClient.getMethod("getItemRenderer", stackType, renderType);
            var pipeline = new LegacyItemRendering(loader);
            var renderTypes = new ArrayList<Object>();
            for (String name : List.of("INVENTORY", "EQUIPPED", "EQUIPPED_FIRST_PERSON", "ENTITY")) renderTypes.add(renderType.getField(name).get(null));
            Object inventory = renderType.getField("INVENTORY").get(null);
            var stacks = new ArrayList<LegacyClientData.Stack>(data.catalog());
            for (var tab : tabs) for (var entry : tab.contents()) if (stacks.stream().noneMatch(s -> s.key().equals(entry.key()) && s.metadata() == entry.metadata() && s.tag().equals(entry.tag()))) stacks.add(entry);
            for (var snapshot : stacks) {
                if (snapshot.key().startsWith("minecraft:")) continue;
                Object stack = data.materialize(snapshot); // keeps the original NBT, e.g. a wand's rod and cap
                Object inventoryRenderer = getRenderer.invoke(null, stack, inventory);
                if (inventoryRenderer == null) { if (snapshot.key().contains("Wand")) captureDiagnostics.add("no item renderer for " + snapshot.key() + "@" + snapshot.metadata()); continue; }
                for (Object renderTypeValue : renderTypes) {
                    String typeName = ((Enum<?>)renderTypeValue).name();
                    Object renderer = getRenderer.invoke(null, stack, renderTypeValue);
                    if (renderer == null) continue;
                    LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
                    boolean complete = false;
                    try { data.inLegacy(() -> { pipeline.render(renderTypeValue, renderer, stack); return null; }); complete = true; }
                    catch (Throwable failure) {
                        Throwable cause = failure;
                        while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
                        captureDiagnostics.add(typeName + " item renderer failed " + snapshot.key() + "@" + snapshot.metadata() + ": " + cause);
                    }
                    List<LegacyVertexStream.Primitive> captured;
                    try { captured = frame.primitives(); }
                    finally {
                        try { frame.close(); }
                        catch (IllegalStateException failure) { complete = false; captureDiagnostics.add("item capture failed to close " + snapshot.key() + "@" + snapshot.metadata() + ": " + failure); }
                    }
                    if (!complete) continue; // An exception may leave only half of a model in the frame.
                    captured = resolveIcons(captured);
                    if (typeName.equals("INVENTORY") && snapshot.key().contains("Wand")) captureDiagnostics.add("wand renderer " + snapshot.key() + "@" + snapshot.metadata() + " -> " + captured.size() + " primitives");
                    if (captured.isEmpty()) continue;
                    String key = geometryKey(snapshot.key(), snapshot.metadata(), snapshot.tag());
                    contextGeometry.put(typeName + "|" + key, captured);
                    if (typeName.equals("INVENTORY")) {
                        geometry.put(key, captured);
                        captureDiagnostics.add("captured item renderer " + snapshot.key() + "@" + snapshot.metadata() + ": " + captured.size() + " primitives, "
                                + captured.stream().mapToInt(p -> p.vertices().size()).sum() + " vertices");
                        customItems.add(snapshot.key());
                    } else captureDiagnostics.add("captured " + typeName + " " + snapshot.key() + "@" + snapshot.metadata() + ": " + captured.size() + " primitives, bounds " + bounds(captured));
                }
            }
        } catch (Throwable unavailable) { captureDiagnostics.add("item renderer bridge unavailable: " + unavailable); }
    }

    /** Executes the real old inventory renderers once and keeps their geometry; texture stays the icon name. */
    private void captureStaticGeometry() {
        Object renderer;
        Method render;
        try {
            Class<?> type = data.loader().loadClass("github.com.gengyoubo.mfcl.legacy1710.LegacyBlockRenderer");
            renderer = type.getConstructor().newInstance();
            render = type.getMethod("renderInventory", String.class, int.class, int.class);
        } catch (Exception unavailable) { return; }
        for (var block : blocks) {
            if (block.renderType() == 0 || block.renderType() < 0) continue;
            for (var item : items) {
                if (!block.key().equals(item.blockKey())) continue;
                boolean rendered = false;
                for (var variant : item.variants()) {
                    LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
                    boolean drawn = false;
                    try { drawn = Boolean.TRUE.equals(data.inLegacy(() -> render.invoke(renderer, block.key(), variant.metadata(), 0))); }
                    catch (Throwable failure) {
                        Throwable cause = failure;
                        while (cause.getCause() != null && cause != cause.getCause()) cause = cause.getCause();
                        captureDiagnostics.add("capture failed " + block.key() + "@" + variant.metadata() + " renderType=" + block.renderType() + ": " + cause);
                    }
                    List<LegacyVertexStream.Primitive> captured;
                    try { captured = frame.primitives(); }
                    finally {
                        try { frame.close(); }
                        catch (IllegalStateException failure) { drawn = false; captureDiagnostics.add("block capture failed to close " + block.key() + "@" + variant.metadata() + ": " + failure); }
                    }
                    if (!drawn || captured.isEmpty()) continue;
                    var resolved = resolveIcons(captured);
                    if (resolved.isEmpty()) { captureDiagnostics.add("unresolved icons " + block.key() + "@" + variant.metadata() + " (" + captured.size() + " primitives)"); continue; }
                    geometry.put(item.key() + "@" + variant.metadata(), resolved);
                    captureDiagnostics.add("captured " + item.key() + "@" + variant.metadata() + ": " + resolved.size() + " primitives, "
                            + resolved.stream().mapToInt(p -> p.vertices().size()).sum() + " vertices, bounds " + bounds(resolved));
                    rendered = true;
                }
                if (rendered) customItems.add(item.key());
            }
        }
    }
    private List<LegacyVertexStream.Primitive> resolveIcons(List<LegacyVertexStream.Primitive> primitives) {
        return LegacyIconResolver.resolve(primitives, data.iconLayout());
    }
    public Map<String, List<LegacyVertexStream.Primitive>> geometry() { return geometry; }
    public Map<String, List<LegacyVertexStream.Primitive>> contextGeometry() { return contextGeometry; }
    public static String geometryKey(String key, int metadata, String tag) {
        return key + "@" + metadata + (tag == null || tag.isEmpty() ? "" : "#" + tag);
    }
    public Set<String> customItems() { return customItems; }
    public List<String> captureDiagnostics() { return List.copyOf(captureDiagnostics); }
    private static String bounds(List<LegacyVertexStream.Primitive> primitives) {
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (var primitive : primitives) for (var vertex : primitive.vertices()) {
            box[0] = Math.min(box[0], vertex.x()); box[1] = Math.min(box[1], vertex.y()); box[2] = Math.min(box[2], vertex.z());
            box[3] = Math.max(box[3], vertex.x()); box[4] = Math.max(box[4], vertex.y()); box[5] = Math.max(box[5], vertex.z());
        }
        return String.format(java.util.Locale.ROOT, "[%.2f,%.2f,%.2f]-[%.2f,%.2f,%.2f]", box[0], box[1], box[2], box[3], box[4], box[5]);
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
                    !tileMetas.isEmpty(), (int) blockType.getMethod("func_149645_b").invoke(block),
                    (boolean)data.loader().loadClass("net.minecraft.client.renderer.RenderBlocks").getMethod("func_147739_a", int.class)
                            .invoke(null, (int)blockType.getMethod("func_149645_b").invoke(block)), faces, tileMetas));
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
                    // ItemBlock's icon methods ignore metadata, so block items resolve the real block icon for
                    // this metadata; ordinary items keep their stack-specific icon and only fall back to damage.
                    Object block = linkedBlock == null ? null : oldBlocks.get(linkedBlock);
                    Object icon;
                    if (block != null) icon = blockType.getMethod("func_149691_a", int.class, int.class).invoke(block, 2, meta);
                    else {
                        icon = itemType.getMethod("getIcon", stackType, int.class).invoke(item, stack, 0);
                        if (icon == null) icon = itemType.getMethod("func_77617_a", int.class).invoke(item, meta);
                    }
                    texture = icon == null ? "" : (String) iconType.getMethod("func_94215_i").invoke(icon);
                    color = (int) itemType.getMethod("func_82790_a", stackType, int.class).invoke(item, stack, 0);
                    limit = Math.min(limit, (int) itemType.getMethod("getItemStackLimit", stackType).invoke(item, stack));
                    damage = Math.max(damage, (int) itemType.getMethod("getMaxDamage", stackType).invoke(item, stack));
                } catch (ReflectiveOperationException undeclared) {
                    name = (String) itemType.getMethod("func_77658_a").invoke(item);
                    texture = ""; color = 0xFFFFFF;
                }
                var layers = new ArrayList<Layer>();
                try {
                    int passes = linkedBlock == null ? (int)itemType.getMethod("getRenderPasses", int.class).invoke(item, meta) : 1;
                    for (int pass = 0; pass < passes; pass++) {
                        Object icon = linkedBlock == null ? itemType.getMethod("getIcon", stackType, int.class).invoke(item, stack, pass) : null;
                        String layerIcon = icon == null ? texture : (String)iconType.getMethod("func_94215_i").invoke(icon);
                        int layerColor = linkedBlock == null ? (int)itemType.getMethod("func_82790_a", stackType, int.class).invoke(item, stack, pass) : color;
                        layers.add(new Layer(layerIcon, layerColor));
                    }
                } catch (ReflectiveOperationException undeclared) { layers.clear(); layers.add(new Layer(texture, color)); }
                variants.add(new Variant(meta, name + ".name", texture, color, layers));
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
    public Set<String> registeredIcons() { return Set.copyOf(data.iconLayout().keySet()); }
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
