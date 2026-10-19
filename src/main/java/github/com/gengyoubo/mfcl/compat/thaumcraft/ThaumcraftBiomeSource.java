package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import java.util.Set;
import java.util.stream.Stream;

/** Seeded modern climate selection around the unmodified vanilla source. No old GenLayer runs on worker threads. */
public final class ThaumcraftBiomeSource extends BiomeSource {
    public static final Codec<ThaumcraftBiomeSource> CODEC=RecordCodecBuilder.create(instance->instance.group(
            BiomeSource.CODEC.fieldOf("source").forGetter(value->value.source),
            Biome.CODEC.fieldOf("magical_forest").forGetter(value->value.forest),
            Biome.CODEC.fieldOf("tainted_land").forGetter(value->value.taint),
            Codec.intRange(0,10000).fieldOf("forest_weight").forGetter(value->value.forestWeight),
            Codec.intRange(0,10000).fieldOf("taint_weight").forGetter(value->value.taintWeight)
    ).apply(instance,ThaumcraftBiomeSource::new));
    private static final Set<String> LAND=Set.of("plains","sunflower_plains","forest","flower_forest","birch_forest","old_growth_birch_forest","dark_forest");
    private final BiomeSource source;
    private final Holder<Biome> forest,taint;
    private final int forestWeight,taintWeight;
    public ThaumcraftBiomeSource(BiomeSource source,Holder<Biome> forest,Holder<Biome> taint,int forestWeight,int taintWeight){
        this.source=source;this.forest=forest;this.taint=taint;this.forestWeight=forestWeight;this.taintWeight=taintWeight;
    }
    @Override protected Codec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){
        return Stream.concat(source.possibleBiomes().stream(),Stream.concat(forestWeight>0?Stream.of(forest):Stream.empty(),taintWeight>0?Stream.of(taint):Stream.empty()));
    }
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        var base=source.getNoiseBiome(x,y,z,sampler);
        var id=base.unwrapKey().orElseThrow().location();
        if(!id.getNamespace().equals("minecraft")||!LAND.contains(id.getPath()))return base;
        // Climate noise depends on the world's seed and is continuous across chunk boundaries.
        double noise=Climate.unquantizeCoord(sampler.sample(x,y,z).weirdness());
        double total=50.0+forestWeight+taintWeight;
        if(forestWeight>0&&Math.abs(noise+.45)<forestWeight/total)return forest;
        if(taintWeight>0&&Math.abs(noise-.45)<taintWeight/total)return taint;
        return base;
    }
}
