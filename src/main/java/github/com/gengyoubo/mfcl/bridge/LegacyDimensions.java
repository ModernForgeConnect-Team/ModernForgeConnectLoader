package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/**
 * Old dimensions registered through the legacy DimensionManager, mirrored as host levels. The original WorldProvider
 * stays authoritative for legacy code; the host level only stores the blocks its original generation writes.
 */
public final class LegacyDimensions {
    /** Properties read from the original WorldProvider, used for the host dimension_type. */
    public record Provider(boolean noSky, boolean respawn, boolean surface, Integer fixedTime) {}
    public record Definition(String hostKey, int legacyId, String biome, Provider provider) {}
    private final ClassLoader loader;
    private final LegacyWorlds worlds;
    private final Set<String> terrain = new HashSet<>();
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    LegacyDimensions(ClassLoader loader,LegacyWorlds worlds) { this.loader = loader; this.worlds=worlds; }
    /** Opt in to copying provideChunk's base terrain before original population. */
    public synchronized void terrain(String hostKey) {
        if(!definitions.containsKey(hostKey))throw new IllegalArgumentException("Unknown original dimension "+hostKey);
        terrain.add(hostKey);
    }
    public synchronized boolean hasTerrain(String hostKey) { return terrain.contains(hostKey); }
    /** Declares a host level for an old dimension; its terrain is whatever the old chunk provider and world generators produce. */
    public synchronized Definition register(String hostKey, int legacyId, String biome) throws Exception {
        for (var existing : definitions.values()) if (existing.legacyId() == legacyId) throw new IllegalStateException("Legacy dimension " + legacyId + " is already mirrored as " + existing.hostKey());
        var definition = new Definition(hostKey, legacyId, biome, provider(legacyId));
        definitions.put(hostKey, definition); return definition;
    }
    private final Map<String, Integer> assigned = new HashMap<>(Map.of("minecraft:overworld", 0, "minecraft:the_nether", -1, "minecraft:the_end", 1));
    /**
     * Old dimension id of a host level: vanilla ids, the id an old mod registered for a mirrored dimension, otherwise
     * the next id from 2 that no old mod registered, so legacy code never mistakes a host-only level for its own dimension.
     */
    public synchronized int legacyId(String hostKey) {
        var definition = definitions.get(hostKey);
        if (definition != null) return definition.legacyId();
        Integer known = assigned.get(hostKey);
        if (known != null) return known;
        int next = 2;
        while (assigned.containsValue(next) || byLegacyId(next) != null || registered(next)) next++;
        assigned.put(hostKey, next); return next;
    }
    private boolean registered(int legacyId) {
        try { return (boolean) loader.loadClass("net.minecraftforge.common.DimensionManager").getMethod("isDimensionRegistered", int.class).invoke(null, legacyId); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    public synchronized List<Definition> definitions() { return List.copyOf(definitions.values()); }
    public synchronized Definition byHostKey(String hostKey) { return definitions.get(hostKey); }
    public synchronized Definition byLegacyId(int legacyId) {
        for (var definition : definitions.values()) if (definition.legacyId() == legacyId) return definition;
        return null;
    }
    /** A new original provider for the dimension, as DimensionManager.createProviderFor builds it for a WorldServerMulti. */
    public Object createProvider(int legacyId) throws Exception {
        return loader.loadClass("net.minecraftforge.common.DimensionManager").getMethod("createProviderFor", int.class).invoke(null, legacyId);
    }
    private Provider provider(int legacyId) throws Exception {
        // Some original providers construct WorldChunkManager(world); a null world cannot describe them.
        Object identity=new Object();
        Object world=worlds.bind(identity,new HostWorldAccess() {
            public String dimensionKey(){return "mfcl:dimension_preview";}
            public int legacyDimensionId(){return legacyId;}
            public boolean clientSide(){return true;}
            public long gameTime(){return 0;}
            public long dayTime(){return 0;}
            public void setDayTime(long time){throw new UnsupportedOperationException("Read-only dimension preview");}
            public Block block(int x,int y,int z){return new Block("minecraft:air",0);}
            public boolean setBlock(int x,int y,int z,Block block,int flags){throw new UnsupportedOperationException("Read-only dimension preview");}
        });
        try {
        var type = loader.loadClass("net.minecraft.world.WorldProvider");
        Object provider=loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
        boolean noSky = type.getField("field_76576_e").getBoolean(provider);
        boolean respawn = (boolean) type.getMethod("func_76567_e").invoke(provider);
        boolean surface = (boolean) type.getMethod("func_76569_d").invoke(provider);
        var angle = type.getMethod("func_76563_a", long.class, float.class);
        float morning = (float) angle.invoke(provider, 0L, 0f), noon = (float) angle.invoke(provider, 6000L, 0f), night = (float) angle.invoke(provider, 18000L, 0f);
        // A constant celestial angle is a fixed time of day. 1.7.10 and the host share angle = frac(time / 24000 - 0.25).
        Integer fixed = morning == noon && noon == night ? Math.floorMod(Math.round((morning + 0.25f) * 24000), 24000) : null;
        return new Provider(noSky, respawn, surface, fixed);
        }finally{worlds.unbind(identity);}
    }
}
