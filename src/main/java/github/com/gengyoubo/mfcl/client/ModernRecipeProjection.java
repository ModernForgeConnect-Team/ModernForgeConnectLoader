package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.LegacyClientData;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.common.MinecraftForge;
import com.mojang.logging.LogUtils;
import java.util.*;

/** Projects only fully representable common recipes into the old crafting/furnace data read by NEI. */
public final class ModernRecipeProjection {
    private static volatile Result lastResult;
    public static Result lastResult() { return lastResult; }
    private final LegacyClientData bridge;
    private final Map<String, LegacyClientData.Stack> reverse = new HashMap<>();
    public ModernRecipeProjection(LegacyClientData bridge) throws Exception {
        this.bridge = bridge;
        for (var old : bridge.catalog()) {
            if (!old.tag().isEmpty()) continue;
            ItemStack modern = ModernItemMapping.renderStack(old);
            if (!modern.isEmpty()) reverse.putIfAbsent(BuiltInRegistries.ITEM.getKey(modern.getItem()).toString(), old);
        }
        var missing = new ArrayList<LegacyClientData.HostItem>();
        for (var item : BuiltInRegistries.ITEM) {
            String key = BuiltInRegistries.ITEM.getKey(item).toString(); var stack = new ItemStack(item);
            if (!stack.isEmpty() && !reverse.containsKey(key)) missing.add(new LegacyClientData.HostItem(key, stack.getHoverName().getString(), stack.getMaxStackSize(), stack.getMaxDamage()));
        }
        bridge.importHostItems(missing);
        for (var old : bridge.catalog()) {
            if (!old.tag().isEmpty()) continue;
            ItemStack modern = ModernItemMapping.renderStack(old);
            if (!modern.isEmpty()) reverse.putIfAbsent(BuiltInRegistries.ITEM.getKey(modern.getItem()).toString(), old);
        }
    }
    public static void install(LegacyClientData bridge) {
        MinecraftForge.EVENT_BUS.addListener((RecipesUpdatedEvent event) -> {
            lastResult = null;
            try {
                var projection = new ModernRecipeProjection(bridge);
                var access = net.minecraft.client.Minecraft.getInstance().getConnection();
                if (access != null) projection.update(event.getRecipeManager(), access.registryAccess());
            } catch (Exception error) { LogUtils.getLogger().error("MFCL modern recipe projection failed", error); }
        });
    }
    public record Result(int projected, int unsupported) {}
    public Result update(RecipeManager manager, RegistryAccess access) throws Exception {
        var recipes = new ArrayList<LegacyClientData.HostRecipe>(); int skipped = 0;
        for (Recipe<?> recipe : manager.getRecipes()) {
            String kind;
            if (recipe instanceof ShapedRecipe) kind = "shaped";
            else if (recipe instanceof ShapelessRecipe) kind = "shapeless";
            else if (recipe instanceof SmeltingRecipe) kind = "smelting";
            else { skipped++; continue; }
            var result = convert(recipe.getResultItem(access));
            if (result == null) { skipped++; continue; }
            var ingredients = new ArrayList<List<LegacyClientData.Stack>>(); boolean supported = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                var variants = new ArrayList<LegacyClientData.Stack>();
                for (ItemStack variant : ingredient.getItems()) {
                    var mapped = convert(variant);
                    if (mapped == null) { supported = false; break; }
                    variants.add(mapped);
                }
                ingredients.add(variants);
                if (!supported) break;
            }
            if (!supported) { skipped++; continue; }
            int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 0;
            int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 0;
            recipes.add(new LegacyClientData.HostRecipe(recipe.getId().toString(), kind, width, height, ingredients, result));
        }
        int count = bridge.projectHostRecipes(recipes);
        LogUtils.getLogger().info("MFCL: projected {} modern recipes into legacy runtime; {} recipes not representable yet", count, skipped);
        lastResult = new Result(count, skipped); return lastResult;
    }
    private LegacyClientData.Stack convert(ItemStack modern) {
        if (modern.isEmpty() || modern.hasTag()) return null;
        var value = reverse.get(BuiltInRegistries.ITEM.getKey(modern.getItem()).toString());
        if (value == null) return null;
        return new LegacyClientData.Stack(value.key(), modern.isDamageableItem() ? modern.getDamageValue() : value.metadata(), modern.getCount(), value.name(), value.tag());
    }
}
