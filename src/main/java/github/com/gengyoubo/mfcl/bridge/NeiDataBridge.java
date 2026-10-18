package github.com.gengyoubo.mfcl.bridge;

import java.io.File;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/** Reflective endpoint: all live legacy stacks and handlers stay in their defining domain. */
public final class NeiDataBridge {
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

    public NeiDataBridge(ClassLoader loader) throws Exception {
        this.loader = loader;
        stackType = loader.loadClass("net.minecraft.item.ItemStack"); itemType = loader.loadClass("net.minecraft.item.Item");
        registry = itemType.getField("field_150901_e").get(null);
        itemOfStack = stackType.getMethod("func_77973_b"); copyStack = stackType.getMethod("func_77946_l");
        nameOfItem = registry.getClass().getMethod("func_148750_c", Object.class);
        itemByName = registry.getClass().getMethod("func_82594_a", String.class);
    }

    public synchronized Map<String, Object> initialize(Path gameDir) throws Exception {
        return inLegacy(() -> {
            if (ready) throw new IllegalStateException("NEI bridge already initialized");
            // A typed old client context supplies settings and data; it never starts an old window or game loop.
            Class<?> mcType = loader.loadClass("net.minecraft.client.Minecraft");
            Field unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
            Object unsafe = unsafeField.get(null);
            Object client = unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, mcType);
            setField(mcType, client, "field_71432_P", client);
            setField(mcType, client, "field_71412_D", gameDir.toFile());
            setField(mcType, client, "field_71474_y", loader.loadClass("net.minecraft.client.settings.GameSettings").getConstructor().newInstance());
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
            Class<?> localeType = loader.loadClass("net.minecraft.client.resources.Locale"); Object locale = localeType.getConstructor().newInstance();
            Method loadLanguage = localeType.getDeclaredMethod("func_135021_a", java.io.InputStream.class); loadLanguage.setAccessible(true);
            for (String resource : List.of("assets/minecraft/lang/en_US.lang", "assets/nei/lang/en_US.lang")) {
                try (var in = loader.getResourceAsStream(resource)) { if (in != null) loadLanguage.invoke(locale, in); }
                try (var in = loader.getResourceAsStream(resource)) { if (in != null) loader.loadClass("net.minecraft.util.StringTranslate").getMethod("inject", java.io.InputStream.class).invoke(null, in); }
            }
            setField(loader.loadClass("net.minecraft.client.resources.I18n"), null, "field_135054_a", locale);
            prepareIconMetadata();
            Class<?> config = loader.loadClass("codechicken.nei.NEIClientConfig");
            config.getMethod("loadWorld", String.class).invoke(null, "mfcl");
            Class<?> itemList = loader.loadClass("codechicken.nei.ItemList");
            itemList.getMethod("loadItems").invoke(null);
            Object task = itemList.getField("loadItems").get(null);
            Class<?> taskType = loader.loadClass("codechicken.nei.RestartableTask");
            Field workerField = taskType.getDeclaredField("thread"); workerField.setAccessible(true);
            Thread worker = (Thread) workerField.get(task);
            if (worker != null) {
                worker.join(15000);
                if (worker.isAlive()) { taskType.getMethod("stop").invoke(task); throw new IllegalStateException("NEI item enumeration timed out"); }
            }
            catalog = new ArrayList<>((List<?>) itemList.getField("items").get(null));
            if (catalog.isEmpty()) throw new IllegalStateException("Original NEI ItemList produced no entries");
            ready = true;
            return Map.of("catalogSize", catalog.size(), "craftingHandlers", handlers(false).size(), "usageHandlers", handlers(true).size());
        });
    }

    private void prepareIconMetadata() throws Exception {
        Class<?> icon = loader.loadClass("net.minecraft.util.IIcon"), register = loader.loadClass("net.minecraft.client.renderer.texture.IIconRegister");
        Map<String, Object> icons = new HashMap<>();
        Object registrar = Proxy.newProxyInstance(loader, new Class<?>[]{register}, (p, method, args) -> icons.computeIfAbsent((String) args[0], name ->
                Proxy.newProxyInstance(loader, new Class<?>[]{icon}, (ignored, m, a) -> switch (m.getName()) {
                    case "func_94211_a", "func_94216_b" -> 16;
                    case "func_94209_e", "func_94210_h" -> 0f;
                    case "func_94212_f", "func_94206_g" -> 1f;
                    case "func_94214_a", "func_94207_b" -> ((Double) a[0]).floatValue() / 16f;
                    case "func_94215_i", "toString" -> name;
                    case "hashCode" -> name.hashCode();
                    case "equals" -> ignored == a[0];
                    default -> throw new UnsupportedOperationException("Legacy icon metadata: " + m);
                })));
        Class<?> block = loader.loadClass("net.minecraft.block.Block");
        for (Object b : (Iterable<?>) block.getField("field_149771_c").get(null)) block.getMethod("func_149651_a", register).invoke(b, registrar);
        for (Object i : (Iterable<?>) registry) itemType.getMethod("func_94581_a", register).invoke(i, registrar);
    }

    private void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        loader.loadClass("github.com.gengyoubo.mfcl.legacy1710.ModernEnumSupport")
                .getMethod("setFailsafeFieldValue", Field.class, Object.class, Object.class).invoke(null, owner.getDeclaredField(name), target, value);
    }
    public synchronized List<Stack> search(String expression) throws Exception {
        requireReady();
        return inLegacy(() -> {
            Pattern pattern = (Pattern) loader.loadClass("codechicken.nei.SearchField").getMethod("getPattern", String.class).invoke(null, expression.toLowerCase(Locale.ROOT));
            var result = new ArrayList<Stack>();
            if (catalogSnapshots == null) {
                var snapshots = new ArrayList<Stack>(); for (Object value : catalog) snapshots.add(snapshot(value));
                catalogSnapshots = List.copyOf(snapshots);
            }
            for (Stack stack : catalogSnapshots) {
                if (pattern == null || pattern.matcher(stack.name().toLowerCase(Locale.ROOT)).find() || pattern.matcher(stack.key().toLowerCase(Locale.ROOT)).find()) result.add(stack);
            }
            return List.copyOf(result);
        });
    }
    public synchronized List<Recipe> recipes(Stack selected, boolean usages) throws Exception {
        requireReady();
        return inLegacy(() -> {
            Object query = originals.get(selected);
            if (query == null) {
                Object item = itemByName.invoke(registry, selected.key());
                if (item == null) return List.of();
                query = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, selected.count(), selected.metadata());
            }
            query = copyStack.invoke(query);
            var recipes = new ArrayList<Recipe>();
            for (Object handler : List.copyOf(handlers(usages))) {
                Object derived;
                try {
                    derived = handler.getClass().getMethod(usages ? "getUsageHandler" : "getRecipeHandler", String.class, Object[].class)
                            .invoke(handler, "item", new Object[]{copyStack.invoke(query)});
                } catch (InvocationTargetException error) {
                    throw new IllegalStateException("NEI handler failed: " + handler.getClass().getName(), error.getCause());
                }
                int count = (Integer) derived.getClass().getMethod("numRecipes").invoke(derived);
                if (count > 10000) throw new IllegalStateException("NEI handler produced too many recipes: " + count);
                String name = (String) derived.getClass().getMethod("getRecipeName").invoke(derived);
                for (int i = 0; i < count; i++) recipes.add(new Recipe(derived.getClass().getName(), name,
                        ingredients((List<?>) derived.getClass().getMethod("getIngredientStacks", int.class).invoke(derived, i)),
                        positionedResult(derived.getClass().getMethod("getResultStack", int.class).invoke(derived, i)),
                        ingredients((List<?>) derived.getClass().getMethod("getOtherStacks", int.class).invoke(derived, i))));
            }
            return List.copyOf(recipes);
        });
    }
    public synchronized boolean ready() { return ready; }
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
            loader.loadClass("codechicken.nei.ItemList").getField("items").set(null, new ArrayList<>(catalog));
            return imported;
        });
    }
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
                Object output = legacyStack(recipe.result());
                if (recipe.kind().equals("smelting")) {
                    for (Stack ingredient : recipe.ingredients().get(0)) furnace.put(legacyStack(ingredient), copyStack.invoke(output));
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
    private Object alternatives(List<Stack> values) throws Exception {
        if (values.isEmpty()) return null;
        if (values.size() == 1) return legacyStack(values.get(0));
        var result = new ArrayList<Object>(); for (Stack value : values) result.add(legacyStack(value)); return result;
    }
    private Object legacyStack(Stack value) throws Exception {
        Object existing = originals.get(value);
        if (existing == null) existing = originals.entrySet().stream().filter(e -> e.getKey().key().equals(value.key())
                && e.getKey().metadata() == value.metadata() && e.getKey().tag().equals(value.tag())).map(Map.Entry::getValue).findFirst().orElse(null);
        Object result;
        if (existing != null) result = copyStack.invoke(existing);
        else {
            if (!value.tag().isEmpty()) throw new IllegalArgumentException("Unmapped legacy NBT in host recipe: " + value.key());
            Object item = hostItemsByKey.get(value.key());
            if (item == null) item = itemByName.invoke(registry, value.key());
            if (item == null) throw new IllegalArgumentException("Unknown legacy item " + value.key());
            result = stackType.getConstructor(itemType, int.class, int.class).newInstance(item, value.count(), value.metadata());
        }
        stackType.getField("field_77994_a").setInt(result, value.count()); return result;
    }
    public synchronized List<String> diagnostics() { return List.copyOf(diagnostics); }
    private void requireReady() { if (!ready) throw new IllegalStateException("Original NEI client data is not ready"); }
    private List<?> handlers(boolean usages) throws Exception {
        return (List<?>) loader.loadClass(usages ? "codechicken.nei.recipe.GuiUsageRecipe" : "codechicken.nei.recipe.GuiCraftingRecipe")
                .getField(usages ? "usagehandlers" : "craftinghandlers").get(null);
    }
    private List<Ingredient> ingredients(List<?> positioned) throws Exception {
        var result = new ArrayList<Ingredient>();
        for (Object p : positioned) {
            var alternatives = new ArrayList<Stack>();
            for (Object item : (Object[]) p.getClass().getField("items").get(p)) alternatives.add(snapshot(item));
            result.add(new Ingredient(p.getClass().getField("relx").getInt(p), p.getClass().getField("rely").getInt(p), alternatives));
        }
        return result;
    }
    private Stack positionedResult(Object positioned) throws Exception {
        return positioned == null ? null : snapshot(positioned.getClass().getField("item").get(positioned));
    }
    private Stack snapshot(Object value) throws Exception {
        Object item = itemOfStack.invoke(value);
        Object nbt = stackType.getField("field_77990_d").get(value);
        HostItem host = hostItems.get(item);
        Stack stack = new Stack(host == null ? (String) nameOfItem.invoke(registry, item) : host.key(),
                (Integer) stackType.getMethod("func_77960_j").invoke(value), stackType.getField("field_77994_a").getInt(value),
                host == null ? (String) stackType.getMethod("func_82833_r").invoke(value) : host.name(), nbt == null ? "" : nbt.toString());
        if (!originals.containsKey(stack)) originals.put(stack, copyStack.invoke(value));
        return stack;
    }
    private <T> T inLegacy(Callable<T> work) throws Exception {
        Thread thread = Thread.currentThread(); ClassLoader before = thread.getContextClassLoader();
        try { thread.setContextClassLoader(loader); return work.call(); }
        finally { thread.setContextClassLoader(before); }
    }
}
