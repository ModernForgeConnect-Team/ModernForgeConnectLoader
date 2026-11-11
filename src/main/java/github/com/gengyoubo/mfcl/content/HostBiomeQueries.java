package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Column semantics of 1.7 biomes expressed through modern 4x4x4 biome palettes and native sync. */
public final class HostBiomeQueries {
    private HostBiomeQueries(){}
    /** Nearest vanilla counterpart of every non-vanilla biome a query has returned, keyed by its registry name. */
    private static final Map<String,String> COUNTERPARTS=new ConcurrentHashMap<>();
    public static String key(LevelReader level,int x,int z){
        int y=Math.max(level.getMinBuildHeight(),Math.min(level.getMaxBuildHeight()-1,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)));
        return key(level,new BlockPos(x,y,z));
    }
    public static String key(LevelReader level,BlockPos pos){
        // Legacy getBiomeGenForCoords reads the stored column, without modern Voronoi display smoothing.
        var holder=level.getNoiseBiome(QuartPos.fromBlock(pos.getX()),QuartPos.fromBlock(pos.getY()),QuartPos.fromBlock(pos.getZ()));
        String key=holder.unwrapKey().orElseThrow(()->new IllegalStateException("Unregistered host biome")).location().toString();
        // Remember the climate of biomes from other modern mods while the holder is at hand; LegacyBiomes only receives the name.
        if(!key.startsWith("minecraft:"))COUNTERPARTS.computeIfAbsent(key,ignored->counterpart(holder));
        return key;
    }
    /** The vanilla biome a 1.7 mod should see in place of an unmapped host biome, or null when it was never queried. */
    public static String counterpart(String key){return COUNTERPARTS.get(key);}
    private static boolean common(Holder<Biome> biome,String path){
        return biome.is(TagKey.create(Registries.BIOME,ResourceLocation.parse("forge:"+path)))
                ||biome.is(TagKey.create(Registries.BIOME,ResourceLocation.parse("c:"+path)));
    }
    /** Tags first (they carry the modded author's intent), then the 1.7 temperature/rainfall bands. */
    static String counterpart(Holder<Biome> holder){
        if(holder.is(BiomeTags.IS_NETHER))return "minecraft:nether_wastes";
        if(holder.is(BiomeTags.IS_END))return "minecraft:the_end";
        Biome biome=holder.value();
        float temperature=biome.getBaseTemperature(),downfall=biome.getModifiedClimateSettings().downfall();
        boolean cold=temperature<0.15f,rain=biome.hasPrecipitation();
        if(holder.is(BiomeTags.IS_DEEP_OCEAN))return cold?"minecraft:deep_frozen_ocean":"minecraft:deep_ocean";
        if(holder.is(BiomeTags.IS_OCEAN))return cold?"minecraft:frozen_ocean":"minecraft:ocean";
        if(holder.is(BiomeTags.IS_RIVER))return cold?"minecraft:frozen_river":"minecraft:river";
        if(holder.is(BiomeTags.IS_BEACH))return cold?"minecraft:snowy_beach":"minecraft:beach";
        if(common(holder,"is_mushroom"))return "minecraft:mushroom_fields";
        if(holder.is(BiomeTags.IS_BADLANDS))return "minecraft:badlands";
        if(holder.is(BiomeTags.IS_JUNGLE))return "minecraft:jungle";
        if(common(holder,"is_swamp"))return "minecraft:swamp";
        if(holder.is(BiomeTags.IS_SAVANNA))return "minecraft:savanna";
        if(common(holder,"is_desert"))return "minecraft:desert";
        if(holder.is(BiomeTags.IS_TAIGA))return cold?"minecraft:snowy_taiga":"minecraft:taiga";
        if(holder.is(BiomeTags.IS_MOUNTAIN)||holder.is(BiomeTags.IS_HILL))return cold?"minecraft:snowy_slopes":"minecraft:windswept_hills";
        if(cold||common(holder,"is_snowy"))return "minecraft:snowy_plains";
        if(holder.is(BiomeTags.IS_FOREST))return "minecraft:forest";
        if(!rain&&temperature>=1.0f)return temperature>=1.5f?"minecraft:desert":"minecraft:savanna";
        if(temperature>=0.9f&&downfall>=0.85f)return "minecraft:jungle";
        if(downfall>=0.7f)return temperature<0.4f?"minecraft:taiga":"minecraft:forest";
        if(temperature<0.4f)return "minecraft:taiga";
        return "minecraft:plains";
    }
    public static void set(ServerLevel level,int x,int z,String key){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Biome mutation requires the server thread");
        var target=level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(ResourceKey.create(Registries.BIOME,ResourceLocation.parse(key)));
        var chunk=level.getChunk(x>>4,z>>4);int qx=(x&15)>>2,qz=(z&15)>>2;
        // Copy every untouched entry. Do not mutate a palette while its resolver is reading it.
        for(var section:chunk.getSections()){
            var prior=section.getBiomes();
            section.fillBiomesFromNoise((a,b,c,sampler)->(a&3)==qx&&(c&3)==qz?target:prior.get(a&3,b&3,c&3),null,0,0,0);
        }
        chunk.setUnsaved(true);
        level.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
    }
}
