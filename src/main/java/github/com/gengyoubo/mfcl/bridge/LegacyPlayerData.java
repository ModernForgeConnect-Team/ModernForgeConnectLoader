package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Bridges an old player's Forge entity data ({@code getEntityData()}, where Thaumcraft keeps research) to a raw
 * byte array, so the host can persist it in the modern player's saved persistent data without re-encoding the
 * whole NBT tree. The exact old binary format is preserved.
 */
public final class LegacyPlayerData {
    /** The host's persisted bytes for a player, or null/empty when there are none. */
    public interface Persistence { byte[] load(UUID player); }
    private final LegacyServices services;
    private volatile Persistence persistence = player -> null;
    // Bytes last exchanged with the host, per actor (identity: old Entity.equals compares entity ids).
    private final Map<Object, byte[]> synced = new com.google.common.collect.MapMaker().weakKeys().makeMap();
    public LegacyPlayerData(LegacyServices services) { this.services = services; }
    public void persistence(Persistence value) { persistence = Objects.requireNonNull(value); }
    public byte[] read(Object player) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Object nbt = loader.loadClass("net.minecraft.entity.Entity").getMethod("getEntityData").invoke(player);
            services.playerState().save(player, nbt);
            Class<?> nbtType = loader.loadClass("net.minecraft.nbt.NBTTagCompound");
            byte[] bytes = (byte[]) loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a", nbtType).invoke(null, nbt);
            synced.put(player, bytes);
            return bytes;
        });
    }
    public void write(Object player, byte[] bytes) throws Exception {
        services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> trackerType = loader.loadClass("net.minecraft.nbt.NBTSizeTracker");
            Object tracker = trackerType.getConstructor(long.class).newInstance(8L * 1024 * 1024);
            Object nbt = loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_152457_a", byte[].class, trackerType).invoke(null, bytes, tracker);
            Field field = loader.loadClass("net.minecraft.entity.Entity").getDeclaredField("customEntityData");
            field.setAccessible(true); field.set(player, nbt);
            services.playerState().load(player, nbt);
            synced.put(player, bytes.clone());
            return null;
        });
    }
    /**
     * Loads the host's persisted bytes unless they are what this actor last exchanged with the host: the persistent
     * actor may then hold newer changes (worn ticks, forwarded events) that the host has not saved yet.
     */
    public void restore(Object player, byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0 || Arrays.equals(bytes, synced.get(player))) return;
        write(player, bytes);
    }
    public void restore(Object player, UUID id) throws Exception { restore(player, persistence.load(id)); }
}
