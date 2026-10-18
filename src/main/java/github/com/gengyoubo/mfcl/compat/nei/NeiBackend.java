package github.com.gengyoubo.mfcl.compat.nei;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import github.com.gengyoubo.mfcl.bridge.LegacyClientData.*;
import github.com.gengyoubo.mfcl.bridge.LegacyClientData.Stack;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.Pattern;

/** Optional NEI protocol adapter. All game object semantics belong to the shared version backend. */
public final class NeiBackend {
    private final LegacyClientData data;
    private final ClassLoader loader;
    public NeiBackend(LegacyClientData data) { this.data = data; this.loader = data.loader(); }
    public LegacyClientData data() { return data; }
    public Map<String, Object> initialize() throws Exception {
        return data.inLegacy(() -> {
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
            data.replaceCatalog((List<?>) itemList.getField("items").get(null));
            data.onCatalogChanged(values -> {
                try { itemList.getField("items").set(null, new ArrayList<>(values)); }
                catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            });
            if (data.catalog().isEmpty()) throw new IllegalStateException("Original NEI ItemList produced no entries");
            return Map.of("catalogSize", data.catalog().size(), "craftingHandlers", handlers(false).size(), "usageHandlers", handlers(true).size());
        });
    }

    public synchronized List<Stack> search(String expression) throws Exception {
        return data.inLegacy(() -> {
            Pattern pattern = (Pattern) loader.loadClass("codechicken.nei.SearchField").getMethod("getPattern", String.class).invoke(null, expression.toLowerCase(Locale.ROOT));
            var result = new ArrayList<Stack>();
            for (Stack stack : data.catalog()) {
                if (pattern == null || pattern.matcher(stack.name().toLowerCase(Locale.ROOT)).find() || pattern.matcher(stack.key().toLowerCase(Locale.ROOT)).find()) result.add(stack);
            }
            return List.copyOf(result);
        });
    }
    public synchronized List<Recipe> recipes(Stack selected, boolean usages) throws Exception {
        return data.inLegacy(() -> {
            Object query = data.materialize(selected);
            var recipes = new ArrayList<Recipe>();
            for (Object handler : List.copyOf(handlers(usages))) {
                Object derived;
                try {
                    derived = handler.getClass().getMethod(usages ? "getUsageHandler" : "getRecipeHandler", String.class, Object[].class)
                            .invoke(handler, "item", new Object[]{data.copyLegacyStack(query)});
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
    private List<?> handlers(boolean usages) throws Exception {
        return (List<?>) loader.loadClass(usages ? "codechicken.nei.recipe.GuiUsageRecipe" : "codechicken.nei.recipe.GuiCraftingRecipe")
                .getField(usages ? "usagehandlers" : "craftinghandlers").get(null);
    }
    private List<Ingredient> ingredients(List<?> positioned) throws Exception {
        var result = new ArrayList<Ingredient>();
        for (Object p : positioned) {
            var alternatives = new ArrayList<Stack>();
            for (Object item : (Object[]) p.getClass().getField("items").get(p)) alternatives.add(data.snapshot(item));
            result.add(new Ingredient(p.getClass().getField("relx").getInt(p), p.getClass().getField("rely").getInt(p), alternatives));
        }
        return result;
    }
    private Stack positionedResult(Object positioned) throws Exception {
        return positioned == null ? null : data.snapshot(positioned.getClass().getField("item").get(positioned));
    }
}
