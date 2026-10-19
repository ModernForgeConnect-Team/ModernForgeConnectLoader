package github.com.gengyoubo.mfcl.content;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.List;

/** Column semantics of 1.7 biomes expressed through modern 4x4x4 biome palettes and native sync. */
public final class HostBiomeQueries {
    private HostBiomeQueries(){}
    public static String key(LevelReader level,int x,int z){
        int y=Math.max(level.getMinBuildHeight(),Math.min(level.getMaxBuildHeight()-1,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)));
        return key(level,new BlockPos(x,y,z));
    }
    public static String key(LevelReader level,BlockPos pos){
        return level.getBiome(pos).unwrapKey().orElseThrow(()->new IllegalStateException("Unregistered host biome")).location().toString();
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
