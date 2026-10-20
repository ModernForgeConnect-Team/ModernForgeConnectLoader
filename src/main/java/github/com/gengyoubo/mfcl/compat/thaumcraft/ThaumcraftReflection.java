package github.com.gengyoubo.mfcl.compat.thaumcraft;

import com.mojang.logging.LogUtils;
import github.com.gengyoubo.mfcl.bridge.LegacyObjects;
import java.util.concurrent.atomic.AtomicBoolean;

/** TC4 lookups shared by this package. */
final class ThaumcraftReflection {
    private ThaumcraftReflection() {}
    static boolean present(ClassLoader loader) { return loader.getResource("thaumcraft/common/Thaumcraft.class") != null; }
    /** Thaumcraft.proxy (CommonProxy/ClientProxy), which owns the per-player knowledge maps. */
    static Object proxy(ClassLoader loader) throws Exception { return loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null); }
    /** TC4's SimpleNetworkWrapper. */
    static Object packetHandler(ClassLoader loader) throws Exception {
        return loader.loadClass("thaumcraft.common.lib.network.PacketHandler").getField("INSTANCE").get(null);
    }
    /** The packet's {@code dim} must be the sender's own dimension. */
    static void requireSameDimension(Object packet, Object player, String action) throws Exception {
        if (LegacyObjects.declaredField(packet, "dim").getInt(packet) != LegacyObjects.dimension(LegacyObjects.world(player)))
            throw new IllegalArgumentException(action + " targets another dimension");
    }
    /** Same dimension, and {@code playerid} rewritten to the authenticated sender. */
    static void bindPlayer(Object packet, Object player, String action) throws Exception {
        requireSameDimension(packet, player, action);
        LegacyObjects.declaredField(packet, "playerid").setInt(packet, LegacyObjects.entityId(player));
    }
    static void reportOnce(AtomicBoolean reported, String message, Throwable failure) {
        if (reported.compareAndSet(false, true)) LogUtils.getLogger().error(message, failure);
    }
}
