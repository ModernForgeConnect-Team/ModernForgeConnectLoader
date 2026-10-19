package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.LegacyItemUse;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Carries a completed client-side legacy scan to the server so the original {@code ScanManager.completeScan}
 * runs where player knowledge is authoritative, replacing the old Forge packet the legacy network cannot route.
 */
public final class ScanSyncChannel {
    public record ScanMessage(byte type, int id, int meta, String phenomena, String prefix) {}
    public record DeviceMessage(int window,github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message packet) {}
    private static final String VERSION = "3";
    private static SimpleChannel channel;
    private ScanSyncChannel() { }
    @SuppressWarnings("removal")
    public static void register() {
        if (channel != null) return;
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("mfcl", "scan"), () -> VERSION, VERSION::equals, VERSION::equals);
        channel.registerMessage(0, ScanMessage.class,
                (message, buffer) -> { buffer.writeByte(message.type()); buffer.writeInt(message.id()); buffer.writeInt(message.meta()); buffer.writeUtf(message.phenomena() == null ? "" : message.phenomena()); buffer.writeUtf(message.prefix() == null ? "@" : message.prefix()); },
                buffer -> new ScanMessage(buffer.readByte(), buffer.readInt(), buffer.readInt(), buffer.readUtf(), buffer.readUtf()),
                (message, context) -> { context.get().enqueueWork(() -> { ServerPlayer sender = context.get().getSender(); if (sender != null) apply(sender, message); }); context.get().setPacketHandled(true); });
        channel.registerMessage(1, github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message.class,
                (message,buffer)->{buffer.writeUtf(message.channel(),128);buffer.writeVarInt(message.discriminator());buffer.writeByteArray(message.payload());},
                buffer->new github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message(buffer.readUtf(128),buffer.readVarInt(),buffer.readByteArray(1024*1024)),
                (message,context)->{context.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->github.com.gengyoubo.mfcl.client.ClientContainerBridge.receive(message)));context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(2,DeviceMessage.class,
                (message,buffer)->{buffer.writeVarInt(message.window());buffer.writeUtf(message.packet().channel(),128);buffer.writeVarInt(message.packet().discriminator());buffer.writeByteArray(message.packet().payload());},
                buffer->new DeviceMessage(buffer.readVarInt(),new github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message(buffer.readUtf(128),buffer.readVarInt(),buffer.readByteArray(1024*1024))),
                (message,context)->{context.get().enqueueWork(()->{var player=context.get().getSender();if(player!=null&&player.containerMenu instanceof LegacyDeviceMenu menu&&menu.containerId==message.window())menu.receivePacket(player,message.packet());});context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }
    public static void sendToServer(LegacyItemUse.Scan scan, String prefix) {
        if (channel != null) channel.sendToServer(new ScanMessage(scan.type(), scan.id(), scan.meta(), scan.phenomena(), prefix));
    }
    public static void sendToPlayer(ServerPlayer player, github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message message) {
        channel.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),message);
    }
    public static void sendDevicePacket(int window,github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message message){channel.sendToServer(new DeviceMessage(window,message));}
    private static void apply(ServerPlayer player, ScanMessage message) {
        var bridge = ModernContentBridge.active();
        if (bridge != null) bridge.applyServerScan(player, message);
    }
}
