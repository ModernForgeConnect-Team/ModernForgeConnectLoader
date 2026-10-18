package github.com.gengyoubo.mfcl.client;

import github.com.gengyoubo.mfcl.bridge.NeiDataBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Explicit flattening cases. Unknown variants stay visible as unmapped instead of silently changing identity. */
public final class ModernItemMapping {
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final Map<String, String> RENAMES = Map.ofEntries(
            Map.entry("mob_spawner", "spawner"), Map.entry("wooden_door", "oak_door"), Map.entry("reeds", "sugar_cane"),
            Map.entry("snow", "snow_block"), Map.entry("melon_block", "melon"), Map.entry("fence", "oak_fence"),
            Map.entry("fence_gate", "oak_fence_gate"), Map.entry("boat", "oak_boat"), Map.entry("sign", "oak_sign"),
            Map.entry("wooden_button", "oak_button"), Map.entry("wooden_pressure_plate", "oak_pressure_plate"), Map.entry("trapdoor", "oak_trapdoor"),
            Map.entry("speckled_melon", "glistering_melon_slice"), Map.entry("melon", "melon_slice"), Map.entry("snow_layer", "snow"),
            Map.entry("brick_block", "bricks"), Map.entry("stonebrick", "stone_bricks"), Map.entry("stone_stairs", "cobblestone_stairs"),
            Map.entry("hardened_clay", "terracotta"), Map.entry("double_plant", "sunflower"), Map.entry("deadbush", "dead_bush"),
            Map.entry("golden_rail", "powered_rail"), Map.entry("slime_ball", "slime_ball"), Map.entry("web", "cobweb"));
    private ModernItemMapping() {}
    public static ItemStack renderStack(NeiDataBridge.Stack old) {
        String key = old.key(); int meta = old.metadata();
        if (key.startsWith("mfcl_host:")) key = key.substring(10).replaceFirst("/", ":");
        else if (key.startsWith("minecraft:")) {
            String path = key.substring(10);
            if ((path.equals("planks") || path.equals("sapling")) && meta >= 0 && meta < WOODS.length) path = WOODS[meta] + (path.equals("planks") ? "_planks" : "_sapling");
            else if ((path.equals("log") || path.equals("leaves")) && meta >= 0 && meta < 4) path = WOODS[meta] + (path.equals("log") ? "_log" : "_leaves");
            else if ((path.equals("log2") || path.equals("leaves2")) && meta >= 0 && meta < 2) path = WOODS[meta + 4] + (path.equals("log2") ? "_log" : "_leaves");
            else if (path.equals("wool") && meta >= 0 && meta < COLORS.length) path = COLORS[meta] + "_wool";
            else if (meta != 0 && !isDamageable(key)) return ItemStack.EMPTY;
            else path = RENAMES.getOrDefault(path, path);
            key = "minecraft:" + path;
        } else if (meta != 0) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(key);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(BuiltInRegistries.ITEM.get(id), Math.max(1, old.count()));
        if (result.isDamageableItem()) result.setDamageValue(meta);
        // Legacy NBT is retained by the bridge; it is not copied blindly into a modern item for display.
        return result;
    }
    private static boolean isDamageable(String key) {
        ResourceLocation id = ResourceLocation.tryParse(key);
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && new ItemStack(BuiltInRegistries.ITEM.get(id)).isDamageableItem();
    }
}
