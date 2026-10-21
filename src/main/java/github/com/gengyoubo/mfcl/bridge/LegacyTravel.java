package github.com.gengyoubo.mfcl.bridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Old dimension travel. Adapters rewrite call sites of MinecraftServer/ServerConfigurationManager (absent in the legacy
 * domain) to these statics; the real player is moved by the host, after the current host tick.
 */
public final class LegacyTravel {
    private LegacyTravel() {}
    /** Where a Teleporter puts an arriving player; computed in the destination world, returns {x,y,z} or null to keep the position. */
    public interface Placement { double[] place(Object world, Object teleporter, double x, double y, double z, float yaw); }
    private static final Map<String, Placement> PLACEMENTS = new ConcurrentHashMap<>();
    public static void placement(String teleporterClass, Placement placement) { PLACEMENTS.put(teleporterClass, placement); }
    /** Host-side: the arrival position for a transfer in the bound destination world; unknown teleporters keep the position. */
    public static double[] place(Object world, Object teleporter, double x, double y, double z, float yaw) {
        var placement = teleporter == null ? null : PLACEMENTS.get(teleporter.getClass().getName());
        double[] placed = placement == null ? null : placement.place(world, teleporter, x, y, z, yaw);
        return placed == null ? new double[]{x, y, z} : placed;
    }
    /** FMLCommonHandler.getMinecraftServerInstance(): there is no running legacy server. */
    public static Object legacyServer(Object handler) { return null; }
    /** MinecraftServer.getConfigurationManager(): travel is resolved by {@link #transferPlayer}, which ignores the manager. */
    public static Object configurationManager(Object server) { return null; }
    /** True only for an exact old player actor backed by a current player in its authoritative host world. */
    public static boolean hostPlayer(Object actor) {
        if(actor==null)return false;
        try {
            var loader=actor.getClass().getClassLoader();
            if(actor.getClass()!=loader.loadClass("net.minecraft.entity.player.EntityPlayerMP"))return false;
            var entity=loader.loadClass("net.minecraft.entity.Entity");
            var host=LegacyWorlds.hostOrNull(entity.getField("field_70170_p").get(actor));
            var id=(java.util.UUID)entity.getMethod("func_110124_au").invoke(actor);
            return host!=null&&!host.clientSide()&&host.players().stream().anyMatch(player->player.id().equals(id));
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot identify hosted portal player",failure);}
    }
    /** MinecraftServer.worldServerForDimension(int). */
    public static Object serverWorld(Object server, int dimension) {
        Object world = LegacyWorlds.serverWorld(dimension);
        if (world == null) throw new IllegalStateException("No host level mirrors legacy dimension " + dimension);
        return world;
    }
    /** ServerConfigurationManager.transferPlayerToDimension(EntityPlayerMP, int, Teleporter). */
    public static void transferPlayer(Object manager, Object player, int dimension, Object teleporter) {
        try {
            var loader = player.getClass().getClassLoader();
            var entity = loader.loadClass("net.minecraft.entity.Entity");
            Object world = entity.getField("field_70170_p").get(player);
            var id = (java.util.UUID) entity.getMethod("func_110124_au").invoke(player);
            LegacyWorlds.access(world).transferPlayer(new HostWorldAccess.Transfer(id, dimension,
                    entity.getField("field_70165_t").getDouble(player), entity.getField("field_70163_u").getDouble(player), entity.getField("field_70161_v").getDouble(player),
                    entity.getField("field_70177_z").getFloat(player), entity.getField("field_70125_A").getFloat(player), teleporter));
            entity.getField("field_71093_bK").setInt(player, dimension);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot transfer original player", failure); }
    }
    /** PUTFIELD EntityPlayerMP.timeUntilPortal: mirrored actors are rebuilt, so the cooldown lives on the host player. */
    public static void portalCooldown(Object player, int ticks) {
        try {
            var loader = player.getClass().getClassLoader();
            var entity = loader.loadClass("net.minecraft.entity.Entity");
            loader.loadClass("net.minecraft.entity.player.EntityPlayerMP").getField("field_71088_bW").setInt(player, ticks);
            LegacyWorlds.access(entity.getField("field_70170_p").get(player)).portalCooldown((java.util.UUID) entity.getMethod("func_110124_au").invoke(player), ticks);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Cannot store original portal cooldown", failure); }
    }
    /** Nearest block of an old registry key around a position of a bound world, found in host chunks. */
    public static int[] findBlock(Object world, String legacyKey, int x, int y, int z, int radius) { return LegacyWorlds.access(world).findBlock(legacyKey, x, y, z, radius); }
    public static int[] findBlock(Object world,String legacyKey,int mask,int metadata,int x,int y,int z,int radius) { return LegacyWorlds.access(world).findBlock(legacyKey,mask,metadata,x,y,z,radius); }
}
