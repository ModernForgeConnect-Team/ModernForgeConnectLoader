package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;

/**
 * Bridges an old player's Forge entity data ({@code getEntityData()}, where Thaumcraft keeps research) to a raw
 * byte array, so the host can persist it in the modern player's saved persistent data without re-encoding the
 * whole NBT tree. The exact old binary format is preserved.
 */
public final class LegacyPlayerData {
    private final LegacyServices services;
    public LegacyPlayerData(LegacyServices services) { this.services = services; }
    public byte[] read(Object player) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Object nbt = loader.loadClass("net.minecraft.entity.Entity").getMethod("getEntityData").invoke(player);
            services.playerState().save(player, nbt);
            Class<?> nbtType = loader.loadClass("net.minecraft.nbt.NBTTagCompound");
            return (byte[]) loader.loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a", nbtType).invoke(null, nbt);
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
            return null;
        });
    }
}
