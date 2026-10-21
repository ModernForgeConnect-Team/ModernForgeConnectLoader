package github.com.gengyoubo.mfcl.compat.ic2;

import github.com.gengyoubo.mfcl.bridge.*;

/** IC2's original client decoder, transported without the absent 1.7.10 Netty connection. */
final class Ic2Packets {
    private static final String CHANNEL="mfcl:ic2";
    private Ic2Packets() {}
    static void install(LegacyServices services) throws Exception {
        var loader=services.loader();var client=loader.loadClass("net.minecraft.client.Minecraft");
        var network=loader.loadClass("ic2.core.network.NetworkManagerClient");
        var decode=network.getDeclaredMethod("onPacketData",java.io.InputStream.class,loader.loadClass("net.minecraft.entity.player.EntityPlayer"));decode.setAccessible(true);
        LegacyPackets.clientReceiver(loader,CHANNEL,payload->{
            Object minecraft=client.getMethod("func_71410_x").invoke(null);
            Object player=client.getField("field_71439_g").get(minecraft);if(player==null)return;
            Object gateway=loader.loadClass("ic2.core.IC2").getField("network").get(null);
            Object manager=gateway.getClass().getMethod("get").invoke(gateway);
            decode.invoke(manager,new java.io.ByteArrayInputStream(payload),player);
        });
    }
}
