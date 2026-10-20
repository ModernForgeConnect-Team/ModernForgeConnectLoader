package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyDimensions;
import java.io.IOException;
import java.util.*;

/**
 * Host levels for old dimensions, as data pack dimension_type + dimension. The level stays void: original population
 * (LegacyDimensionHost) writes its blocks on the server thread, because old generators cannot run on worldgen workers.
 */
final class LegacyDimensionPack {
    private LegacyDimensionPack() {}
    static void generate(LegacyContentPack pack, LegacyDimensions dimensions) throws IOException {
        for (var dimension : dimensions.definitions()) {
            var id = net.minecraft.resources.ResourceLocation.parse(dimension.hostKey());
            var provider = dimension.provider();
            var type = new LinkedHashMap<String, Object>();
            if (provider.fixedTime() != null) type.put("fixed_time", provider.fixedTime());
            type.put("has_skylight", !provider.noSky());
            type.put("has_ceiling", false);
            type.put("ultrawarm", false);
            type.put("natural", provider.surface());
            type.put("coordinate_scale", 1.0);
            type.put("bed_works", provider.respawn());
            type.put("respawn_anchor_works", false);
            type.put("min_y", 0); type.put("height", 256); type.put("logical_height", 256);
            type.put("infiniburn", "#minecraft:infiniburn_overworld");
            // A 1.7.10 provider without sky renders no sky and fogs every column (WorldProviderHell/Outer), like the nether effects.
            type.put("effects", provider.noSky() ? "minecraft:the_nether" : "minecraft:overworld");
            type.put("ambient_light", 0.0);
            type.put("piglin_safe", false); type.put("has_raids", false);
            type.put("monster_spawn_light_level", 0); type.put("monster_spawn_block_light_limit", 0);
            pack.json("data/" + id.getNamespace() + "/dimension_type/" + id.getPath() + ".json", type);
            var settings = new LinkedHashMap<String, Object>();
            settings.put("biome", ModernContentBridge.id(dimension.biome()).toString());
            settings.put("layers", List.of(Map.of("block", "minecraft:air", "height", 1)));
            settings.put("lakes", false); settings.put("features", false); settings.put("structure_overrides", List.of());
            pack.json("data/" + id.getNamespace() + "/dimension/" + id.getPath() + ".json",
                    Map.of("type", id.toString(), "generator", Map.of("type", "minecraft:flat", "settings", settings)));
        }
    }
}
