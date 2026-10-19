package github.com.gengyoubo.mfcl.content;

import com.google.gson.*;
import github.com.gengyoubo.mfcl.bridge.LegacyBiomes;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Emits dynamic registry data before WorldLoader freezes the modern biome registry. */
final class LegacyBiomePack {
    private LegacyBiomePack(){}
    static void generate(LegacyContentPack pack,LegacyBiomes biomes) throws IOException {
        var definitions=biomes.definitions();if(definitions.isEmpty())return;
        for(var biome:definitions){
            boolean forest=biome.key().equals("thaumcraft:magical_forest");
            var json=vanilla("worldgen/biome/"+(forest?"forest":"plains")+".json");
            json.addProperty("temperature",biome.temperature());json.addProperty("downfall",biome.downfall());json.addProperty("has_precipitation",biome.precipitation());
            var effects=json.getAsJsonObject("effects");
            effects.addProperty("grass_color",biome.grass());effects.addProperty("foliage_color",biome.foliage());
            effects.addProperty("water_color",biome.water());effects.addProperty("sky_color",biome.sky());
            // Custom legacy mobs are not registered as host EntityTypes yet. Do not invent replacements.
            if(!forest){json.add("spawners",new JsonObject());json.add("spawn_costs",new JsonObject());}
            var id=ModernContentBridge.id(biome.key());
            pack.json("data/"+id.getNamespace()+"/worldgen/biome/"+id.getPath()+".json",json);
            pack.json("data/minecraft/tags/worldgen/biome/is_overworld.json",Map.of("replace",false,"values",definitions.stream().filter(b->!b.key().equals("thaumcraft:eldritch")).map(LegacyBiomes.Definition::key).toList()));
        }
        var forest=definitions.stream().filter(b->b.key().equals("thaumcraft:magical_forest")).findFirst();
        var taint=definitions.stream().filter(b->b.key().equals("thaumcraft:tainted_land")).findFirst();
        if(forest.isPresent()&&taint.isPresent()&&(forest.get().weight()>0||taint.get().weight()>0)){
            // Existing worlds retain their serialized generator. Only new normal/large/amplified presets change.
            for(String preset:List.of("normal","large_biomes","amplified")){
                var json=vanilla("worldgen/world_preset/"+preset+".json");
                var generator=json.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
                var wrapper=new JsonObject();wrapper.addProperty("type","mfcl:thaumcraft_biomes");wrapper.add("source",generator.get("biome_source"));
                wrapper.addProperty("magical_forest",forest.get().key());wrapper.addProperty("tainted_land",taint.get().key());
                wrapper.addProperty("forest_weight",forest.get().weight());wrapper.addProperty("taint_weight",taint.get().weight());
                generator.add("biome_source",wrapper);pack.json("data/minecraft/worldgen/world_preset/"+preset+".json",json);
            }
        }
        pack.json("mfcl-biomes.json",definitions);
    }
    private static JsonObject vanilla(String path) throws IOException {
        try(var in=LegacyBiomePack.class.getResourceAsStream("/data/minecraft/"+path)){
            if(in==null)throw new FileNotFoundException("Vanilla worldgen template: "+path);
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
