package github.com.gengyoubo.mfcl.bridge;

/** Original allowFlying/isFlying fields and sendPlayerAbilities, under host server authority. */
public final class LegacyPlayerFlight {
    private LegacyPlayerFlight(){}
    public static void refresh(Object player) throws Exception {
        var loader=player.getClass().getClassLoader();var entity=loader.loadClass("net.minecraft.entity.Entity");
        var host=LegacyWorlds.hostOrNull(entity.getField("field_70170_p").get(player));if(host==null||host.clientSide())return;
        var flight=host.flight((java.util.UUID)entity.getMethod("func_110124_au").invoke(player));if(flight==null)return;
        Object abilities=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71075_bZ").get(player);
        var type=loader.loadClass("net.minecraft.entity.player.PlayerCapabilities");
        type.getField("field_75101_c").setBoolean(abilities,flight.allowed());type.getField("field_75100_b").setBoolean(abilities,flight.flying());
    }
    public static void send(Object player) throws Exception {
        var loader=player.getClass().getClassLoader();var entity=loader.loadClass("net.minecraft.entity.Entity");
        var host=LegacyWorlds.hostOrNull(entity.getField("field_70170_p").get(player));if(host==null||host.clientSide())return;
        Object abilities=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71075_bZ").get(player);
        var type=loader.loadClass("net.minecraft.entity.player.PlayerCapabilities");
        host.flight((java.util.UUID)entity.getMethod("func_110124_au").invoke(player),new HostWorldAccess.Flight(type.getField("field_75101_c").getBoolean(abilities),type.getField("field_75100_b").getBoolean(abilities)));
    }
}
