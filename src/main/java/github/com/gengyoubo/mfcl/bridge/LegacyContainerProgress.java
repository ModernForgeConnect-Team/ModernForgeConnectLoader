package github.com.gengyoubo.mfcl.bridge;

import java.nio.ByteBuffer;

/** Original ICrafting properties use the same signed-short values and window guard as old vanilla packets. */
public final class LegacyContainerProgress {
    private static final String CHANNEL = "mfcl:container_progress";
    private LegacyContainerProgress() {}
    public static boolean send(Object player, Object container, int property, int value) throws Exception {
        var type = player.getClass().getClassLoader().loadClass("net.minecraft.inventory.Container");
        int window = type.getField("field_75152_c").getInt(container);
        byte[] payload = ByteBuffer.allocate(12).putInt(window).putInt((short)property).putInt((short)value).array();
        return LegacyPackets.sendClientPayload(player, CHANNEL, payload);
    }
    static boolean receive(LegacyServices services, LegacyPackets.Message message) throws Exception {
        if (!CHANNEL.equals(message.channel())) return false;
        if (message.discriminator() != 0 || message.payload().length != 12)
            throw new IllegalArgumentException("Invalid original container property envelope");
        var buffer = ByteBuffer.wrap(message.payload());
        int window = buffer.getInt(), property = buffer.getInt(), value = buffer.getInt();
        var loader = services.loader();
        var minecraft = loader.loadClass("net.minecraft.client.Minecraft");
        Object client = minecraft.getMethod("func_71410_x").invoke(null);
        Object player = minecraft.getField("field_71439_g").get(client);
        if (player == null) return true;
        Object container = loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71070_bA").get(player);
        var type = loader.loadClass("net.minecraft.inventory.Container");
        if (container != null && type.getField("field_75152_c").getInt(container) == window)
            type.getMethod("func_75137_b", int.class, int.class).invoke(container, property, value);
        return true;
    }
}
