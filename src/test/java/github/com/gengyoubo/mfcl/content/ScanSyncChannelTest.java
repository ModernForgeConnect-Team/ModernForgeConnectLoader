package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyPackets;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScanSyncChannelTest {
    @Test void loginSynchronizationKeepsItsClientboundDiscriminator() throws Exception {
        // Plain JUnit does not run Forge's event constructor transformer.
        new NetworkEvent(() -> null).getListenerList();
        new NetworkEvent.GatherLoginPayloadsEvent(new java.util.ArrayList<>(), true).getListenerList();
        ScanSyncChannel.register();
        var field = ScanSyncChannel.class.getDeclaredField("channel");
        field.setAccessible(true);
        var channel = (SimpleChannel) field.get(null);
        var packet = new LegacyPackets.Message("Thaumcraft", 7, new byte[] {1, 2, 3});
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            channel.encodeMessage(packet, buffer);
            assertEquals(1, buffer.readUnsignedByte(), "Login data must use the clientbound handler");
            assertEquals(packet.channel(), buffer.readUtf(128));
            assertEquals(packet.discriminator(), buffer.readVarInt());
            assertArrayEquals(packet.payload(), buffer.readByteArray());
            assertFalse(buffer.isReadable());

            buffer.clear();
            channel.encodeMessage(new ScanSyncChannel.ServerboundMessage(packet), buffer);
            assertEquals(3, buffer.readUnsignedByte(), "Book requests must use the serverbound handler");
            assertEquals(packet.channel(), buffer.readUtf(128));
            assertEquals(packet.discriminator(), buffer.readVarInt());
            assertArrayEquals(packet.payload(), buffer.readByteArray());
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }
}
