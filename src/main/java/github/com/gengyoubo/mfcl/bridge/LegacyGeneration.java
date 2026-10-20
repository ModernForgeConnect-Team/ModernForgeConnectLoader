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
    public void populate(Object hostIdentity, int chunkX, int chunkZ) throws Exception {
        Object world = services.worlds().bound(hostIdentity);
        var loader = services.loader();
        services.items().inLegacy(() -> {
            var chunkProvider = loader.loadClass("net.minecraft.world.chunk.IChunkProvider");
            Object provider = providers.get(world);
            if (provider == null) {
                Object worldProvider = loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
                providers.put(world, provider = loader.loadClass("net.minecraft.world.WorldProvider").getMethod("func_76555_c").invoke(worldProvider));
            }
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
