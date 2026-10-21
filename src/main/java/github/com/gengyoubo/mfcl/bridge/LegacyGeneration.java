package github.com.gengyoubo.mfcl.bridge;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Original chunk population for mirrored old dimensions, run on the host owner thread once a host chunk exists:
 * IChunkProvider.populate followed by GameRegistry.generateWorld, as ChunkProviderServer.populate did in 1.7.10.
 */
public final class LegacyGeneration {
    private final LegacyServices services;
    private final Map<Object, Object> providers = new IdentityHashMap<>();
    public LegacyGeneration(LegacyServices services) { this.services = services; }
    private Object provider(Object world) throws Exception {
        Object provider=providers.get(world);
        if(provider==null){
            var loader=services.loader();
            Object worldProvider=loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
            providers.put(world,provider=loader.loadClass("net.minecraft.world.WorldProvider").getMethod("func_76555_c").invoke(worldProvider));
        }
        return provider;
    }
    @FunctionalInterface public interface TerrainSink { void block(int x,int y,int z,HostWorldAccess.Block block); }
    /** Original provideChunk runs on the world owner; only its detached block palette crosses to the host. */
    public void terrain(Object hostIdentity,int chunkX,int chunkZ,TerrainSink sink) throws Exception {
        Object world=services.worlds().bound(hostIdentity);var loader=services.loader();
        services.items().inLegacy(()->{
            Object chunk=loader.loadClass("net.minecraft.world.chunk.IChunkProvider").getMethod("func_73154_d",int.class,int.class).invoke(provider(world),chunkX,chunkZ);
            Object[] sections=(Object[])loader.loadClass("net.minecraft.world.chunk.Chunk").getMethod("func_76587_i").invoke(chunk);
            var sectionType=loader.loadClass("net.minecraft.world.chunk.storage.ExtendedBlockStorage");
            var lowIds=sectionType.getMethod("func_76658_g");
            var highIds=sectionType.getMethod("func_76660_i");
            var metadata=sectionType.getMethod("func_76669_j");
            var nibbleData=loader.loadClass("net.minecraft.world.chunk.NibbleArray").getField("field_76585_a");
            var base=sectionType.getMethod("func_76662_d");
            Object registry=loader.loadClass("net.minecraft.block.Block").getField("field_149771_c").get(null);
            var name=registry.getClass().getMethod("func_148750_c",Object.class);
            var blockById=loader.loadClass("net.minecraft.block.Block").getMethod("func_149729_e",int.class);
            var palette=new HostWorldAccess.Block[65536];
            for(Object section:sections)if(section!=null){
                int sy=(int)base.invoke(section);
                byte[] low=(byte[])lowIds.invoke(section);Object high=highIds.invoke(section);
                byte[] upper=high==null?null:(byte[])nibbleData.get(high),meta=(byte[])nibbleData.get(metadata.invoke(section));
                for(int index=0;index<4096;index++){
                    int id=(low[index]&255)|(nibble(upper,index)<<8);if(id==0)continue;
                    int variant=nibble(meta,index),packed=(id<<4)|variant;
                    HostWorldAccess.Block block=palette[packed];
                    if(block==null){
                        String key=(String)name.invoke(registry,blockById.invoke(null,id));
                        if(key==null)throw new IllegalStateException("Unregistered original terrain block "+id);
                        palette[packed]=block=new HostWorldAccess.Block(key,variant);
                    }
                    sink.block((chunkX<<4)+(index&15),sy+(index>>8),(chunkZ<<4)+((index>>4)&15),block);
                }
            }
            return null;
        });
    }
    private static int nibble(byte[] bytes,int index){return bytes==null?0:(bytes[index>>1]>>((index&1)*4))&15;}
    public void populate(Object hostIdentity, int chunkX, int chunkZ) throws Exception {
        Object world = services.worlds().bound(hostIdentity);
        var loader = services.loader();
        services.items().inLegacy(() -> {
            var chunkProvider = loader.loadClass("net.minecraft.world.chunk.IChunkProvider");
            Object provider = provider(world);
            chunkProvider.getMethod("func_73153_a", chunkProvider, int.class, int.class).invoke(provider, provider, chunkX, chunkZ);
            loader.loadClass("cpw.mods.fml.common.registry.GameRegistry").getMethod("generateWorld", int.class, int.class, loader.loadClass("net.minecraft.world.World"), chunkProvider, chunkProvider)
                    .invoke(null, chunkX, chunkZ, world, provider, provider);
            return null;
        });
    }
    public void forget(Object hostIdentity) {
        Object world = services.worlds().findBound(hostIdentity);
        if (world != null) providers.remove(world);
    }
}
