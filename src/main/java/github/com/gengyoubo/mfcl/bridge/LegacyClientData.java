package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Forge 1.7.10 Item/ItemStack semantics. Owns identity, copies, variant/NBT snapshots and host recipe publication. */
public final class LegacyClientData {
    public record Stack(String key, int metadata, int count, String name, String tag) {}
    public record Ingredient(int x, int y, List<Stack> alternatives) { public Ingredient { alternatives = List.copyOf(alternatives); } }
    public record Recipe(String handler, String name, List<Ingredient> ingredients, Stack result, List<Ingredient> other) {
        public Recipe { ingredients = List.copyOf(ingredients); other = List.copyOf(other); }
    }
    public record HostRecipe(String id, String kind, int width, int height, List<List<Stack>> ingredients, Stack result) {
        public HostRecipe { ingredients = ingredients.stream().map(List::copyOf).toList(); }
    }
    public record HostItem(String key, String name, int stackLimit, int maxDamage) {}
    private final ClassLoader loader;
    private final Class<?> stackType, itemType;
    private final Object registry;
    private final Method itemOfStack, nameOfItem, itemByName, copyStack;
    private List<Object> catalog = List.of();
    private List<Stack> catalogSnapshots;
    private final Map<Stack, Object> originals = new HashMap<>();
    private final List<String> diagnostics = new ArrayList<>();
    private boolean ready;
    private List<Object> ownCrafting;
    private final Map<Object, HostItem> hostItems = new IdentityHashMap<>();
    private final Map<String, Object> hostItemsByKey = new HashMap<>();
    private Class<?> facadeType;
    private Map<String, float[]> iconLayout = Map.of();
    private boolean contextInitialized;
    private LegacyClientResources.Context resourceContext;

    private final List<Consumer<List<Object>>> catalogListeners = new ArrayList<>();
    public LegacyClientData(ClassLoader loader) throws Exception {
        this.loader = loader;
        stackType = loader.loadClass("net.minecraft.item.ItemStack"); itemType = loader.loadClass("net.minecraft.item.Item");
        registry = itemType.getField("field_150901_e").get(null);
        itemOfStack = stackType.getMethod("func_77973_b"); copyStack = stackType.getMethod("func_77946_l");
        nameOfItem = registry.getClass().getMethod("func_148750_c", Object.class);
        itemByName = registry.getClass().getMethod("func_82594_a", String.class);
    }

    public ClassLoader loader() { return loader; }
    public Object legacyItem(String key) throws Exception { return itemByName.invoke(registry, key); }
    public Map<String, float[]> iconLayout() { return iconLayout; }
    /** The original icon whose synthetic atlas cell contains the given legacy UV, or null. */
    public String iconAt(float u, float v) {
        for (var entry : iconLayout.entrySet()) {
            float[] cell = entry.getValue();
            if (u >= cell[0] - 1.0e-4f && u <= cell[2] + 1.0e-4f && v >= cell[1] - 1.0e-4f && v <= cell[3] + 1.0e-4f) return entry.getKey();
        }
        return null;
    }
    public synchronized void initializeContext(Path gameDir, List<String> languageResources) throws Exception {
        inLegacy(() -> {
            if (contextInitialized) { addLanguages(languageResources); return null; }
            // A typed old client context supplies settings and data; it never starts an old window or game loop.
            Class<?> mcType = loader.loadClass("net.minecraft.client.Minecraft");
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Object client = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, mcType);
            setField(mcType, client, "field_71432_P", client);
            setField(mcType, client, "field_71412_D", gameDir.toFile());
            resourceContext = LegacyClientResources.create(loader);
            setField(mcType, client, "field_110451_am", resourceContext.manager());
            Class<?> fmlClient = loader.loadClass("cpw.mods.fml.client.FMLClientHandler");
            setField(fmlClient, fmlClient.getMethod("instance").invoke(null), "client", client);
            setField(mcType, client, "field_71474_y", loader.loadClass("net.minecraft.client.settings.GameSettings").getConstructor().newInstance());
            setField(mcType, client, "field_71428_T", loader.loadClass("net.minecraft.util.Timer").getConstructor(float.class).newInstance(20f));
            setField(mcType, client, "field_71443_c", 854); setField(mcType, client, "field_71440_d", 480);
            Class<?> fontType = loader.loadClass("net.minecraft.client.gui.FontRenderer");
            Object font = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, fontType);
            int[] widths = new int[256];
            try (var in = loader.getResourceAsStream("assets/minecraft/textures/font/ascii.png")) {
                if (in == null) throw new IllegalStateException("Legacy font texture missing");
                var image = javax.imageio.ImageIO.read(in); int cell = image.getWidth() / 16;
                for (int character = 0; character < 256; character++) {
                    int right = cell - 1;
                    scan: for (; right >= 0; right--) for (int y = 0; y < cell; y++)
                        if ((image.getRGB(character % 16 * cell + right, character / 16 * cell + y) >>> 24) > 16) break scan;
                    widths[character] = (right + 1) * 8 / cell + 1;
                }
                widths[32] = 4;
            }
            setField(fontType, font, "field_78286_d", widths); setField(fontType, font, "field_78288_b", 9);
            try (var in = loader.getResourceAsStream("assets/minecraft/font/glyph_sizes.bin")) {
                setField(fontType, font, "field_78287_e", in == null ? new byte[65536] : in.readAllBytes());
            }
            setField(mcType, client, "field_71466_p", font); setField(mcType, client, "field_71464_q", font);
            Object textureManager = loader.loadClass("net.minecraft.client.renderer.texture.TextureManager")
                    .getConstructor(loader.loadClass("net.minecraft.client.resources.IResourceManager"))
                    .newInstance(mcType.getMethod("func_110442_L").invoke(client));
            setField(mcType, client, "field_71446_o", textureManager);
            Class<?> renderManagerType = loader.loadClass("net.minecraft.client.renderer.entity.RenderManager");
            Object renderManager = renderManagerType.getField("field_78727_a").get(null);
            Object heldRenderer = loader.loadClass("net.minecraft.client.renderer.ItemRenderer").getConstructor(mcType).newInstance(client);
            Class<?> gameRendererType = loader.loadClass("net.minecraft.client.renderer.EntityRenderer");
            Object gameRenderer = unsafe.getClass().getMethod("allocateInstance",Class.class).invoke(unsafe,gameRendererType);
            setField(gameRendererType,gameRenderer,"field_78516_c",heldRenderer);
            setField(mcType,client,"field_71460_t",gameRenderer);
            if (renderManager != null) {
                setField(renderManagerType,renderManager,"field_78721_f",heldRenderer);
                setField(renderManagerType, renderManager, "field_78724_e", textureManager);
                setField(renderManagerType, renderManager, "field_78736_p", font);
                setField(renderManagerType, renderManager, "field_78733_k", mcType.getField("field_71474_y").get(client));
            }
            Class<?> dispatcherType = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
            Object dispatcher = dispatcherType.getField("field_147556_a").get(null);
            setField(dispatcherType, dispatcher, "field_147553_e", textureManager);
            setField(dispatcherType, dispatcher, "field_147557_n", font);
            setField(mcType, client, "field_147127_av", unsafe.getClass().getMethod("allocateInstance", Class.class)
                    .invoke(unsafe, loader.loadClass("net.minecraft.client.audio.SoundHandler")));
            setField(mcType, client, "field_135017_as", loader.loadClass("net.minecraft.client.resources.LanguageManager")
                    .getConstructor(loader.loadClass("net.minecraft.client.resources.data.IMetadataSerializer"), String.class).newInstance(null, "en_US"));
            Class<?> localeType = loader.loadClass("net.minecraft.client.resources.Locale"); Object locale = localeType.getConstructor().newInstance();
            Method loadLanguage = localeType.getDeclaredMethod("func_135021_a", java.io.InputStream.class); loadLanguage.setAccessible(true);
            for (String resource : languageResources) {
                try (var in = loader.getResourceAsStream(resource)) { if (in != null) loadLanguage.invoke(locale, in); }
                try (var in = loader.getResourceAsStream(resource)) { if (in != null) loader.loadClass("net.minecraft.util.StringTranslate").getMethod("inject", java.io.InputStream.class).invoke(null, in); }
            }
            setField(loader.loadClass("net.minecraft.client.resources.I18n"), null, "field_135054_a", locale);
            prepareIconMetadata();
            contextInitialized = true;
            return null;
        });
    }
    private void addLanguages(List<String> resources) throws Exception {
        Class<?> i18n = loader.loadClass("net.minecraft.client.resources.I18n");
        Field field = i18n.getDeclaredField("field_135054_a"); field.setAccessible(true); Object locale = field.get(null);
        Method method = locale.getClass().getDeclaredMethod("func_135021_a", java.io.InputStream.class); method.setAccessible(true);
        for (String resource : resources) {
            try (var in = loader.getResourceAsStream(resource)) { if (in != null) method.invoke(locale, in); }
            try (var in = loader.getResourceAsStream(resource)) { if (in != null) loader.loadClass("net.minecraft.util.StringTranslate").getMethod("inject", java.io.InputStream.class).invoke(null, in); }
        }
    }
    public synchronized void loadRegistryCatalog() throws Exception {
        inLegacy(() -> {
            var values = new ArrayList<Object>();
            Class<?> tab = loader.loadClass("net.minecraft.creativetab.CreativeTabs");
            Method variants = itemType.getMethod("func_150895_a", itemType, tab, List.class);
            for (Object item : (Iterable<?>) registry) {
                var entries = new ArrayList<Object>(); variants.invoke(item, item, null, entries);
                if (entries.isEmpty()) entries.add(stackType.getConstructor(itemType, int.class, int.class).newInstance(item, 1, 0));
                values.addAll(entries);
            }
            replaceCatalog(values); return null;
        });
    }
    public synchronized void replaceCatalog(List<?> values) {
        for (Object value : values) if (!stackType.isInstance(value)) throw new IllegalArgumentException("Stack belongs to a different runtime");
        catalog = new ArrayList<>(values); catalogSnapshots = null; ready = true;
    }
    public synchronized void onCatalogChanged(Consumer<List<Object>> listener) { catalogListeners.add(Objects.requireNonNull(listener)); }
    public synchronized List<Stack> catalog() throws Exception {
        requireReady();
        return inLegacy(() -> {
            if (catalogSnapshots == null) {
                var values = new ArrayList<Stack>(); for (Object value : catalog) values.add(snapshot(value));
                catalogSnapshots = List.copyOf(values);
            }
            return catalogSnapshots;
        });
    }
    public synchronized boolean ready() { return ready; }
    public Map<String, Object> registrySummary() throws Exception {
        return inLegacy(() -> {
            Object blocks = loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            return Map.of("itemsByNamespace", namespaces(registry), "blocksByNamespace", namespaces(blocks), "catalogStacks", catalog.size());
        });
    }
    private Map<String, Integer> namespaces(Object registry) throws Exception {
        var names = new TreeMap<String, Integer>();
        var keyOf = registry.getClass().getMethod("func_148750_c", Object.class);
        for (Object value : (Iterable<?>) registry) {
            String key = (String) keyOf.invoke(registry, value);
            String namespace = key != null && key.contains(":") ? key.substring(0, key.indexOf(':')) : "minecraft";
            names.merge(namespace, 1, Integer::sum);
        }
        return Map.copyOf(names);
    }
    public void close() throws java.io.IOException { if (resourceContext != null) resourceContext.close(); }
    public Object copyLegacyStack(Object value) throws Exception { return copyStack.invoke(value); }
    /** Evaluate the original item's per-pass color on the full stack, including its opaque old NBT. */
    public int itemColor(Stack value, int pass) throws Exception {
        return inLegacy(() -> {
            Object stack = materialize(value);
            return (int)itemType.getMethod("func_82790_a", stackType, int.class).invoke(itemOfStack.invoke(stack), stack, pass);
        });
    }
    private String currentLanguage;
    public synchronized void language(String code) throws Exception {
        if (code.equals(currentLanguage)) return;
        inLegacy(() -> {
            int split = code.indexOf('_');
            String oldCode = split < 0 ? code : code.substring(0, split) + "_" + code.substring(split + 1).toUpperCase(java.util.Locale.ROOT);
            Class<?> localeType = loader.loadClass("net.minecraft.client.resources.Locale");
            Object locale = localeType.getConstructor().newInstance();
            localeType.getMethod("func_135022_a", loader.loadClass("net.minecraft.client.resources.IResourceManager"), List.class)
                    .invoke(locale, resourceContext.manager(), oldCode.equals("en_US") ? List.of("en_US") : List.of("en_US", oldCode));
            setField(loader.loadClass("net.minecraft.client.resources.I18n"), null, "field_135054_a", locale);
            Field translations = localeType.getDeclaredField("field_135032_a"); translations.setAccessible(true);
            loader.loadClass("net.minecraft.util.StringTranslate").getMethod("func_135063_a", Map.class).invoke(null, translations.get(locale));
            currentLanguage = code;
            return null;
        });
    }
    private void prepareIconMetadata() throws Exception {
        Class<?> icon = loader.loadClass("net.minecraft.util.IIcon"), register = loader.loadClass("net.minecraft.client.renderer.texture.IIconRegister");
        Map<String, Object> icons = new HashMap<>();
        Map<String, float[]> layout = new LinkedHashMap<>();
        java.util.concurrent.atomic.AtomicInteger cell = new java.util.concurrent.atomic.AtomicInteger();
        Object registrar = Proxy.newProxyInstance(loader, new Class<?>[]{register}, (p, method, args) -> icons.computeIfAbsent((String) args[0], name -> {
            // A synthetic atlas cell makes captured geometry self-describing: a quad's UV range tells the
            // host which original icon texture it belongs to, without running the old texture stitcher.
            int index = cell.getAndIncrement(), column = index % 16, row = index / 16;
            float minU = column / 16f, maxU = (column + 1) / 16f, minV = row / 16f, maxV = (row + 1) / 16f;
            layout.put(name, new float[]{minU, minV, maxU, maxV});
            return Proxy.newProxyInstance(loader, new Class<?>[]{icon}, (ignored, m, a) -> switch (m.getName()) {
                case "func_94211_a", "func_94216_b" -> 16;
                case "func_94209_e" -> minU;
                case "func_94206_g" -> minV;
                case "func_94212_f" -> maxU;
                case "func_94210_h" -> maxV;
                case "func_94214_a" -> minU + (maxU - minU) * (((Double) a[0]).floatValue() / 16f);
                case "func_94207_b" -> minV + (maxV - minV) * (((Double) a[0]).floatValue() / 16f);
                case "func_94215_i", "toString" -> name;
                case "hashCode" -> name.hashCode();
                case "equals" -> ignored == a[0];
                default -> throw new UnsupportedOperationException("Legacy icon metadata: " + m);
            });
        }));
        Class<?> block = loader.loadClass("net.minecraft.block.Block");
        for (Object b : (Iterable<?>) block.getField("field_149771_c").get(null)) block.getMethod("func_149651_a", register).invoke(b, registrar);
        for (Object i : (Iterable<?>) registry) itemType.getMethod("func_94581_a", register).invoke(i, registrar);
        iconLayout = Map.copyOf(layout);
    }
    public void refreshIconMetadata() throws Exception { inLegacy(() -> { prepareIconMetadata(); return null; }); }

    private void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        loader.loadClass("github.com.gengyoubo.mfcl.legacy1710.ModernEnumSupport")
                .getMethod("setFailsafeFieldValue", Field.class, Object.class, Object.class).invoke(null, owner.getDeclaredField(name), target, value);
    }
    public synchronized int importHostItems(List<HostItem> items) throws Exception {
        requireReady();
        return inLegacy(() -> {
            if (facadeType == null) facadeType = LegacyItemFacades.define(loader);
            var next = new ArrayList<>(catalog); int imported = 0;
            for (HostItem item : items) {
                String key = "mfcl_host:" + item.key().replace(':', '/');
                if (hostItemsByKey.containsKey(key)) continue;
                Object facade = facadeType.getConstructor(String.class, int.class, int.class).newInstance(item.name(), item.stackLimit(), item.maxDamage());
                hostItems.put(facade, new HostItem(key, item.name(), item.stackLimit(), item.maxDamage())); hostItemsByKey.put(key, facade);
                Object stack = stackType.getConstructor(itemType, int.class, int.class).newInstance(facade, 1, 0);
                next.add(stack); imported++;
            }
            catalog = List.copyOf(next);
            catalogSnapshots = null;
            for (var listener : catalogListeners) listener.accept(List.copyOf(catalog));
            return imported;
        });
    }
    public synchronized void preserveCrafting(List<Object> recipes) {ownCrafting=List.copyOf(recipes);}
    public boolean tagsEqual(Stack first,Stack second) throws Exception {return inLegacy(()->(boolean)stackType.getMethod("func_77970_a",stackType,stackType).invoke(null,materialize(first),materialize(second)));}
    /** Build the replacement entirely before publishing it; reloads replace rather than append. */
    @SuppressWarnings("unchecked")
    public synchronized int projectHostRecipes(List<HostRecipe> recipes) throws Exception {
        requireReady();
        return inLegacy(() -> {
            Class<?> craftingType = loader.loadClass("net.minecraft.item.crafting.CraftingManager");
            Object crafting = craftingType.getMethod("func_77594_a").invoke(null);
            List<Object> target = (List<Object>) craftingType.getMethod("func_77592_b").invoke(crafting);
            if (ownCrafting == null) {
                ownCrafting = new ArrayList<>();
                for (Object recipe : target) {
                    Object output = loader.loadClass("net.minecraft.item.crafting.IRecipe").getMethod("func_77571_b").invoke(recipe);
                    if (output != null && !snapshot(output).key().startsWith("minecraft:")) ownCrafting.add(recipe);
                }
            }
            var replacement = new ArrayList<>(ownCrafting);
            var furnace = new LinkedHashMap<Object, Object>();
            for (HostRecipe recipe : recipes) {
                Object output = materialize(recipe.result());
                if (recipe.kind().equals("smelting")) {
                    for (Stack ingredient : recipe.ingredients().get(0)) furnace.put(materialize(ingredient), copyStack.invoke(output));
                } else if (recipe.kind().equals("shaped")) {
                    if (recipe.width() < 1 || recipe.width() > 3 || recipe.height() < 1 || recipe.height() > 3 || recipe.ingredients().size() != recipe.width() * recipe.height())
                        throw new IllegalArgumentException("Invalid crafting grid: " + recipe.id());
                    Class<?> type = loader.loadClass("net.minecraftforge.oredict.ShapedOreRecipe");
                    Object created = type.getConstructor(stackType, Object[].class).newInstance(output, new Object[]{"a", 'a', output});
                    Object[] input = new Object[recipe.ingredients().size()];
                    for (int i = 0; i < input.length; i++) input[i] = alternatives(recipe.ingredients().get(i));
                    setField(type, created, "input", input); setField(type, created, "width", recipe.width()); setField(type, created, "height", recipe.height());
                    replacement.add(created);
                } else if (recipe.kind().equals("shapeless")) {
                    Class<?> type = loader.loadClass("net.minecraftforge.oredict.ShapelessOreRecipe");
                    Object created = type.getConstructor(stackType, Object[].class).newInstance(output, new Object[]{output});
                    var input = new ArrayList<Object>();
                    for (var ingredient : recipe.ingredients()) { Object converted = alternatives(ingredient); if (converted == null) throw new IllegalArgumentException("Empty shapeless ingredient"); input.add(converted); }
                    setField(type, created, "input", input); replacement.add(created);
                } else throw new IllegalArgumentException("Unsupported host recipe: " + recipe.kind());
            }
            Class<?> furnaceType = loader.loadClass("net.minecraft.item.crafting.FurnaceRecipes");
            Object furnaceOwner = furnaceType.getMethod("func_77602_a").invoke(null);
            Map<Object, Object> furnaceTarget = (Map<Object, Object>) furnaceType.getMethod("func_77599_b").invoke(furnaceOwner);
            target.clear(); target.addAll(replacement); furnaceTarget.clear(); furnaceTarget.putAll(furnace);
            return recipes.size();
        });
    }
    private void requireReady() { if (!ready) throw new IllegalStateException("Legacy item catalog is not ready"); }
    private Object alternatives(List<Stack> values) throws Exception {
        if (values.isEmpty()) return null;
        if (values.size() == 1) return materialize(values.get(0));
        var result = new ArrayList<Object>(); for (Stack value : values) result.add(materialize(value)); return result;
    }
    public synchronized Object materialize(Stack value) throws Exception {
        Object existing = originals.get(value);
        if (existing == null) existing = originals.entrySet().stream().filter(e -> e.getKey().key().equals(value.key())
                && e.getKey().metadata() == value.metadata() && e.getKey().tag().equals(value.tag())).map(Map.Entry::getValue).findFirst().orElse(null);
        Object result;
        if (existing != null) result = copyStack.invoke(existing);
        else {
            Object item = hostItemsByKey.get(value.key());
            if (item != null && !value.tag().isEmpty()) throw new UnsupportedOperationException("Host item NBT needs a host-specific mapping: " + value.key());
            if (item == null) item = itemByName.invoke(registry, value.key());
            if (item == null) throw new IllegalArgumentException("Unknown legacy item " + value.key());
            result = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, value.count(), value.metadata());
            if (!value.tag().isEmpty()) {
                Object nbt = loader.loadClass("net.minecraft.nbt.JsonToNBT").getMethod("func_150315_a", String.class).invoke(null, value.tag());
                stackType.getField("field_77990_d").set(result, nbt);
            }
        }
        stackType.getField("field_77994_a").setInt(result, value.count()); return result;
    }
    /** Adds host presentation tags to a copy using the old NBT implementation, preserving old indexed lists. */
    public Stack renderTags(Stack source,Integer repairCost,Integer color,String name) throws Exception {
        return inLegacy(()->{
            Object stack=materialize(source);
            var compound=loader.loadClass("net.minecraft.nbt.NBTTagCompound");
            var field=stackType.getField("field_77990_d");Object tag=field.get(stack);
            if(tag==null){tag=compound.getConstructor().newInstance();field.set(stack,tag);}
            if(repairCost!=null)compound.getMethod("func_74768_a",String.class,int.class).invoke(tag,"RepairCost",repairCost);
            if(color!=null||name!=null) {
                Object display=compound.getMethod("func_74775_l",String.class).invoke(tag,"display");
                if(color!=null)compound.getMethod("func_74768_a",String.class,int.class).invoke(display,"color",color);
                if(name!=null)compound.getMethod("func_74778_a",String.class,String.class).invoke(display,"Name",name);
                compound.getMethod("func_74782_a",String.class,loader.loadClass("net.minecraft.nbt.NBTBase")).invoke(tag,"display",display);
            }
            return snapshot(stack);
        });
    }
    public synchronized Stack snapshot(Object value) throws Exception {
        Object item = itemOfStack.invoke(value);
        Object nbt = stackType.getField("field_77990_d").get(value);
        HostItem host = hostItems.get(item);
        Stack stack = new Stack(host == null ? (String) nameOfItem.invoke(registry, item) : host.key(),
                (Integer) stackType.getMethod("func_77960_j").invoke(value), stackType.getField("field_77994_a").getInt(value),
                host == null ? (String) stackType.getMethod("func_82833_r").invoke(value) : host.name(), nbt == null ? "" : nbt.toString());
        if (!originals.containsKey(stack)) originals.put(stack, copyStack.invoke(value));
        return stack;
    }
    public synchronized <T> T inLegacy(Callable<T> work) throws Exception {
        Thread thread = Thread.currentThread(); ClassLoader before = thread.getContextClassLoader();
        try { thread.setContextClassLoader(loader); return work.call(); }
        finally { thread.setContextClassLoader(before); }
    }
}
