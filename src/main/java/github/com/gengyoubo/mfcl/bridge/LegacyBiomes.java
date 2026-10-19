package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Stable modern names for original biome objects; legacy numeric IDs never enter host chunk palettes. */
public final class LegacyBiomes {
    public record Definition(String key,int legacyId,String name,float temperature,float downfall,
                             boolean precipitation,int grass,int foliage,int water,int sky,int weight) {}
    private final Class<?> type;
    private final Map<String,Object> byKey=new LinkedHashMap<>();
    private final Map<Object,String> keys=new IdentityHashMap<>();
    private final List<Definition> definitions=new ArrayList<>();
    public LegacyBiomes(ClassLoader loader) throws Exception {
        type=loader.loadClass("net.minecraft.world.biome.BiomeGenBase");
        // Renames and nearest 1.7 counterparts are explicit; TC4 identities are registered separately.
        vanilla(0,"ocean","warm_ocean","lukewarm_ocean","cold_ocean");
        vanilla(1,"plains","sunflower_plains","meadow");vanilla(2,"desert");vanilla(3,"windswept_hills","stony_peaks");
        vanilla(4,"forest","flower_forest");vanilla(5,"taiga");vanilla(6,"swamp","mangrove_swamp");
        vanilla(7,"river");vanilla(8,"nether_wastes","crimson_forest","warped_forest","soul_sand_valley","basalt_deltas");
        vanilla(9,"the_end","end_highlands","end_midlands","small_end_islands","end_barrens","the_void");
        vanilla(10,"frozen_ocean","deep_frozen_ocean");vanilla(11,"frozen_river");
        vanilla(12,"snowy_plains","ice_spikes","snowy_slopes","frozen_peaks","jagged_peaks","grove");
        vanilla(14,"mushroom_fields");vanilla(16,"beach");vanilla(21,"jungle","bamboo_jungle");
        vanilla(23,"sparse_jungle");vanilla(24,"deep_ocean","deep_lukewarm_ocean","deep_cold_ocean");
        vanilla(25,"stony_shore");vanilla(26,"snowy_beach");vanilla(27,"birch_forest","old_growth_birch_forest","cherry_grove");
        vanilla(29,"dark_forest");vanilla(30,"snowy_taiga");vanilla(32,"old_growth_pine_taiga","old_growth_spruce_taiga");
        vanilla(34,"windswept_forest","windswept_gravelly_hills");vanilla(35,"savanna","windswept_savanna");
        vanilla(36,"savanna_plateau");vanilla(37,"badlands","eroded_badlands");vanilla(38,"wooded_badlands");
        vanilla(3,"dripstone_caves","deep_dark");vanilla(4,"lush_caves");
    }
    private void vanilla(int id,String... names) throws Exception {
        Object value=type.getMethod("func_150568_d",int.class).invoke(null,id);
        for(String name:names){byKey.put("minecraft:"+name,value);keys.putIfAbsent(value,"minecraft:"+name);}
    }
    public void register(String key,Object biome,int weight) throws Exception {
        if(biome==null||!type.isInstance(biome))throw new IllegalArgumentException("Missing original biome "+key);
        if(byKey.putIfAbsent(key,biome)!=null)throw new IllegalArgumentException("Duplicate biome "+key);
        keys.put(biome,key);
        definitions.add(new Definition(key,type.getField("field_76756_M").getInt(biome),
                (String)type.getField("field_76791_y").get(biome),type.getField("field_76750_F").getFloat(biome),
                type.getField("field_76751_G").getFloat(biome),(boolean)type.getMethod("func_76738_d").invoke(biome),
                (int)type.getMethod("func_150558_b",int.class,int.class,int.class).invoke(biome,0,64,0),
                (int)type.getMethod("func_150571_c",int.class,int.class,int.class).invoke(biome,0,64,0),
                (int)type.getMethod("getWaterColorMultiplier").invoke(biome),
                (int)type.getMethod("func_76731_a",float.class).invoke(biome,0f),Math.max(0,weight)));
    }
    public Object resolve(String key) {
        Object value=byKey.get(key);
        if(value==null)throw new UnsupportedOperationException("No legacy biome mapping: "+key);
        return value;
    }
    public String key(Object biome) {
        String key=keys.get(biome);if(key==null)throw new UnsupportedOperationException("Unregistered legacy biome: "+biome);return key;
    }
    public List<Definition> definitions(){return List.copyOf(definitions);}
}
