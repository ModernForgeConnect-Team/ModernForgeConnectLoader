package github.com.gengyoubo.mfcl.bridge;

import com.mojang.logging.LogUtils;
import java.lang.reflect.*;
import java.util.*;

/**
 * Original entities living inside a host entity. The server runs their real onUpdate as World.updateEntity did;
 * the client rebuilds them from their NBT, spawn data and DataWatcher and draws them with their original Render.
 */
public final class LegacyEntityHosting {
    /** What the host entity mirrors after an original update. */
    public record State(double x, double y, double z, double motionX, double motionY, double motionZ, float yaw, float pitch,
                        float width, float height, boolean dead, boolean collidable) {}
    private final LegacyServices services;
    private final Class<?> entity, world, nbt, packet, spawnData;
    private final Field posX, posY, posZ, lastX, lastY, lastZ, prevX, prevY, prevZ, motionX, motionY, motionZ, yaw, pitch, prevYaw, prevPitch,
            width, height, dead, ticks, worldField, addedToChunk, uniqueId;
    private final Method update, setPosition, collidable, writeNbt, readNbt, setId, watcher;
    private final Set<Class<?>> clientUpdateFailures = Collections.newSetFromMap(new WeakHashMap<>());
    private boolean renderersLoaded;
    LegacyEntityHosting(LegacyServices services) throws Exception {
        this.services = services; var loader = services.loader();
        entity = loader.loadClass("net.minecraft.entity.Entity"); world = loader.loadClass("net.minecraft.world.World");
        nbt = loader.loadClass("net.minecraft.nbt.NBTTagCompound"); packet = loader.loadClass("net.minecraft.network.PacketBuffer");
        spawnData = loader.loadClass("cpw.mods.fml.common.registry.IEntityAdditionalSpawnData");
        posX = entity.getField("field_70165_t"); posY = entity.getField("field_70163_u"); posZ = entity.getField("field_70161_v");
        lastX = entity.getField("field_70142_S"); lastY = entity.getField("field_70137_T"); lastZ = entity.getField("field_70136_U");
        prevX = entity.getField("field_70169_q"); prevY = entity.getField("field_70167_r"); prevZ = entity.getField("field_70166_s");
        motionX = entity.getField("field_70159_w"); motionY = entity.getField("field_70181_x"); motionZ = entity.getField("field_70179_y");
        yaw = entity.getField("field_70177_z"); pitch = entity.getField("field_70125_A"); prevYaw = entity.getField("field_70126_B"); prevPitch = entity.getField("field_70127_C");
        width = entity.getField("field_70130_N"); height = entity.getField("field_70131_O"); dead = entity.getField("field_70128_L");
        ticks = entity.getField("field_70173_aa"); worldField = entity.getField("field_70170_p"); addedToChunk = entity.getField("field_70175_ag");
        uniqueId = entity.getDeclaredField("field_96093_i"); uniqueId.setAccessible(true);
        update = entity.getMethod("func_70071_h_"); setPosition = entity.getMethod("func_70107_b", double.class, double.class, double.class);
        collidable = entity.getMethod("func_70067_L"); writeNbt = entity.getMethod("func_70109_d", nbt); readNbt = entity.getMethod("func_70020_e", nbt);
        setId = entity.getMethod("func_145769_d", int.class); watcher = entity.getMethod("func_70096_w");
    }
    public boolean isEntity(Object value) { return entity.isInstance(value); }
    public String type(Object value) { return value.getClass().getName(); }
    /** The host entity's identity becomes the original's, so old queries and damage sources resolve to the host entity. */
    public void adopt(Object value, Object boundWorld, UUID id, int entityId) throws Exception {
        services.items().inLegacy(() -> {
            uniqueId.set(value, id); setId.invoke(value, entityId); worldField.set(value, boundWorld); addedToChunk.setBoolean(value, true); return null;
        });
    }
    public State state(Object value) throws Exception {
        return services.items().inLegacy(() -> new State(posX.getDouble(value), posY.getDouble(value), posZ.getDouble(value),
                motionX.getDouble(value), motionY.getDouble(value), motionZ.getDouble(value), yaw.getFloat(value), pitch.getFloat(value),
                width.getFloat(value), height.getFloat(value), dead.getBoolean(value), (boolean) collidable.invoke(value)));
    }
    /** World.updateEntityWithOptionalForce: last-tick position and rotation, ticksExisted, then the original onUpdate. */
    public State tick(Object boundWorld, Object value) throws Exception {
        return services.items().inLegacy(() -> {
            worldField.set(value, boundWorld); remember(value);
            ticks.setInt(value, ticks.getInt(value) + 1);
            update.invoke(value);
            return state(value);
        });
    }
    private void remember(Object value) throws IllegalAccessException {
        lastX.setDouble(value, posX.getDouble(value)); lastY.setDouble(value, posY.getDouble(value)); lastZ.setDouble(value, posZ.getDouble(value));
        prevYaw.setFloat(value, yaw.getFloat(value)); prevPitch.setFloat(value, pitch.getFloat(value));
    }
    /**
     * Client copy: the server position is authoritative. The original client onUpdate still runs for its own effects
     * (animation counters, sounds); a class whose client update fails is only positioned from then on.
     */
    public void follow(Object boundWorld, Object value, double x, double y, double z, float yawValue, float pitchValue) throws Exception {
        services.items().inLegacy(() -> {
            worldField.set(value, boundWorld); remember(value);
            prevX.setDouble(value, posX.getDouble(value)); prevY.setDouble(value, posY.getDouble(value)); prevZ.setDouble(value, posZ.getDouble(value));
            ticks.setInt(value, ticks.getInt(value) + 1);
            if (!clientUpdateFailures.contains(value.getClass())) {
                try { update.invoke(value); }
                catch (InvocationTargetException failure) {
                    clientUpdateFailures.add(value.getClass());
                    LogUtils.getLogger().warn("MFCL original client update of {} failed; it is only positioned from now on", value.getClass().getName(), failure.getCause());
                }
            }
            setPosition.invoke(value, x, y, z); yaw.setFloat(value, yawValue); pitch.setFloat(value, pitchValue);
            dead.setBoolean(value, false);
            return null;
        });
    }
    public byte[] save(Object value) throws Exception {
        return services.items().inLegacy(() -> {
            Object tag = nbt.getConstructor().newInstance(); writeNbt.invoke(value, tag);
            return (byte[]) services.loader().loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_74798_a", nbt).invoke(null, tag);
        });
    }
    /** A saved or synchronized original: its World constructor (entityInit included), then readFromNBT. */
    public Object restore(Object boundWorld, String type, byte[] data) throws Exception {
        return services.items().inLegacy(() -> {
            Class<?> original = services.loader().loadClass(type);
            if (!entity.isAssignableFrom(original)) throw new IllegalArgumentException("Not an original entity: " + type);
            Object value = original.getConstructor(world).newInstance(boundWorld);
            var tracker = services.loader().loadClass("net.minecraft.nbt.NBTSizeTracker");
            Object tag = services.loader().loadClass("net.minecraft.nbt.CompressedStreamTools").getMethod("func_152457_a", byte[].class, tracker)
                    .invoke(null, data, tracker.getConstructor(long.class).newInstance(8L * 1024 * 1024));
            readNbt.invoke(value, tag);
            return value;
        });
    }
    /** FML IEntityAdditionalSpawnData, as the old entity spawn packet carried it; empty for other entities. */
    public byte[] spawnData(Object value) throws Exception {
        return services.items().inLegacy(() -> {
            if (!spawnData.isInstance(value)) return new byte[0];
            Object buffer = buffer(null); spawnData.getMethod("writeSpawnData", buffer.getClass().getClassLoader().loadClass("io.netty.buffer.ByteBuf")).invoke(value, buffer);
            return bytes(buffer);
        });
    }
    public void spawnData(Object value, byte[] data) throws Exception {
        services.items().inLegacy(() -> {
            if (!spawnData.isInstance(value) || data.length == 0) return null;
            Object buffer = buffer(data); spawnData.getMethod("readSpawnData", buffer.getClass().getClassLoader().loadClass("io.netty.buffer.ByteBuf")).invoke(value, buffer);
            return null;
        });
    }
    /** Every DataWatcher value once any changed (null otherwise), so the latest synchronized snapshot is always complete. */
    public byte[] watcher(Object value, boolean force) throws Exception {
        return services.items().inLegacy(() -> {
            Object data = watcher.invoke(value); var type = data.getClass();
            boolean changed = (boolean) type.getMethod("func_75684_a").invoke(data);
            if (changed) type.getMethod("func_75688_b").invoke(data);
            if (!changed && !force) return null;
            Object buffer = packet.getConstructor(services.loader().loadClass("io.netty.buffer.ByteBuf")).newInstance(buffer(null));
            type.getMethod("func_151507_a", List.class, packet).invoke(null, type.getMethod("func_75685_c").invoke(data), buffer);
            return bytes(buffer);
        });
    }
    public void watcher(Object value, byte[] bytes) throws Exception {
        services.items().inLegacy(() -> {
            if (bytes.length == 0) return null;
            Object data = watcher.invoke(value); var type = data.getClass();
            Object buffer = packet.getConstructor(services.loader().loadClass("io.netty.buffer.ByteBuf")).newInstance(buffer(bytes));
            Object values = type.getMethod("func_151508_b", packet).invoke(null, buffer);
            if (values != null) type.getMethod("func_75687_a", List.class).invoke(data, values);
            return null;
        });
    }
    /** A host hit on the hosted original: player damage from the attacker's original actor, generic damage otherwise. */
    public boolean hurt(Object value, Object playerActor, float amount) throws Exception {
        return services.items().inLegacy(() -> {
            var source = services.loader().loadClass("net.minecraft.util.DamageSource");
            Object damage = playerActor == null ? source.getField("field_76377_j").get(null)
                    : source.getMethod("func_76365_a", services.loader().loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(null, playerActor);
            return (boolean) entity.getMethod("func_70097_a", source, float.class).invoke(value, damage, amount);
        });
    }
    /** The original Render's drawing at the camera-relative position, captured into replayable primitives. */
    public List<LegacyVertexStream.Primitive> render(Object value, double x, double y, double z, float yawValue, float partialTick) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            var managerType = loader.loadClass("net.minecraft.client.renderer.entity.RenderManager"); Object manager = managerType.getField("field_78727_a").get(null);
            if (!renderersLoaded) {
                // FMLClientHandler.finishMinecraftLoading installs mod renderers into the manager after mod initialization.
                Object registry = loader.loadClass("cpw.mods.fml.client.registry.RenderingRegistry").getMethod("instance").invoke(null);
                registry.getClass().getMethod("loadEntityRenderers", Map.class).invoke(registry, managerType.getField("field_78729_o").get(manager));
                renderersLoaded = true;
            }
            var mc = loader.loadClass("net.minecraft.client.Minecraft"); Object client = mc.getMethod("func_71410_x").invoke(null);
            if (managerType.getField("field_78724_e").get(manager) == null) managerType.getField("field_78724_e").set(manager, mc.getField("field_71446_o").get(client));
            if (managerType.getField("field_78733_k").get(manager) == null) managerType.getField("field_78733_k").set(manager, mc.getField("field_71474_y").get(client));
            Object render = managerType.getMethod("func_78713_a", entity).invoke(manager, value);
            if (render == null) return List.of();
            try (var frame = LegacyWorldGraphics.begin()) {
                LegacyWorldGraphics.translate(-x, -y, -z);
                loader.loadClass("net.minecraft.client.renderer.entity.Render").getMethod("func_76986_a", entity, double.class, double.class, double.class, float.class, float.class)
                        .invoke(render, value, x, y, z, yawValue, partialTick);
                return LegacyIconResolver.resolve(frame.primitives(), services.items().iconLayout());
            }
        });
    }
    private Object buffer(byte[] data) throws Exception {
        var unpooled = services.loader().loadClass("io.netty.buffer.Unpooled");
        return data == null ? unpooled.getMethod("buffer").invoke(null) : unpooled.getMethod("wrappedBuffer", byte[].class).invoke(null, (Object) data);
    }
    private static byte[] bytes(Object buffer) throws Exception {
        Object raw = buffer;
        // PacketBuffer delegates to the ByteBuf it wraps; read through the public ByteBuf API either way.
        var type = raw.getClass().getClassLoader().loadClass("io.netty.buffer.ByteBuf");
        byte[] result = new byte[(int) type.getMethod("readableBytes").invoke(raw)];
        type.getMethod("readBytes", byte[].class).invoke(raw, (Object) result);
        return result;
    }
}
