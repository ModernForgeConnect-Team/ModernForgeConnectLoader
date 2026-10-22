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
    public record ScanMessage(byte type, int id, int meta, int entityId, String phenomena, String prefix) {}
    public record DeviceMessage(int window,github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message packet) {}
    // SimpleChannel selects an encoder by Java class, not by the send direction.
    // Keep this distinct from the clientbound Message used by login/research synchronization.
    public record ServerboundMessage(github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message packet) {}
    private static final String VERSION = "6";
    private static SimpleChannel channel;
    private ScanSyncChannel() { }
    @SuppressWarnings("removal")
    public static void register() {
        if (channel != null) return;
        var guard = github.com.gengyoubo.mfcl.bridge.NetworkRegistrationGuard.host("mfcl:scan");
        channel = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("mfcl", "scan"), () -> VERSION, VERSION::equals, VERSION::equals);
        message(guard, 0, ScanMessage.class,
                (message, buffer) -> { buffer.writeByte(message.type()); buffer.writeInt(message.id()); buffer.writeInt(message.meta()); buffer.writeInt(message.entityId()); buffer.writeUtf(message.phenomena() == null ? "" : message.phenomena()); buffer.writeUtf(message.prefix() == null ? "@" : message.prefix()); },
                buffer -> new ScanMessage(buffer.readByte(), buffer.readInt(), buffer.readInt(), buffer.readInt(), buffer.readUtf(), buffer.readUtf()),
                (message, context) -> { context.get().enqueueWork(() -> { ServerPlayer sender = context.get().getSender(); if (sender != null) apply(sender, message); }); context.get().setPacketHandled(true); },
                java.util.Optional.empty());
        message(guard, 1, github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message.class,
                (message,buffer)->{buffer.writeUtf(message.channel(),128);buffer.writeVarInt(message.discriminator());buffer.writeByteArray(message.payload());},
                buffer->new github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message(buffer.readUtf(128),buffer.readVarInt(),buffer.readByteArray(1024*1024)),
                (message,context)->{context.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->github.com.gengyoubo.mfcl.client.ClientContainerBridge.receive(message)));context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        message(guard, 2,DeviceMessage.class,
                (message,buffer)->{buffer.writeVarInt(message.window());buffer.writeUtf(message.packet().channel(),128);buffer.writeVarInt(message.packet().discriminator());buffer.writeByteArray(message.packet().payload());},
                buffer->new DeviceMessage(buffer.readVarInt(),new github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message(buffer.readUtf(128),buffer.readVarInt(),buffer.readByteArray(1024*1024))),
                (message,context)->{context.get().enqueueWork(()->{var player=context.get().getSender();if(player!=null&&player.containerMenu instanceof LegacyDeviceMenu menu&&menu.containerId==message.window())menu.receivePacket(player,message.packet());});context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        message(guard, 3,ServerboundMessage.class,
                (message,buffer)->{buffer.writeUtf(message.packet().channel(),128);buffer.writeVarInt(message.packet().discriminator());buffer.writeByteArray(message.packet().payload());},
                buffer->new ServerboundMessage(new github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message(buffer.readUtf(128),buffer.readVarInt(),buffer.readByteArray(1024*1024))),
                (message,context)->{context.get().enqueueWork(()->{var player=context.get().getSender();if(player!=null){var bridge=ModernContentBridge.active();if(bridge!=null)bridge.applyServerLegacyMessage(player,message.packet());}});context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
    }
    /** Every index and Java type is checked before Forge's codec can silently replace an earlier registration. */
    private static <M> void message(github.com.gengyoubo.mfcl.bridge.NetworkRegistrationGuard guard, int index, Class<M> type,
            java.util.function.BiConsumer<M, FriendlyByteBuf> encoder, java.util.function.Function<FriendlyByteBuf, M> decoder,
            java.util.function.BiConsumer<M, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context>> handler,
            java.util.Optional<net.minecraftforge.network.NetworkDirection> direction) {
        guard.register(index, type, direction.map(Enum::name).orElse("ANY"));
        channel.registerMessage(index, type, encoder, decoder, handler, direction);
    }
    public static void sendToServer(LegacyItemUse.Scan scan, String prefix) {
        if (channel != null) channel.sendToServer(new ScanMessage(scan.type(), scan.id(), scan.meta(), scan.entityId(), scan.phenomena(), prefix));
    }
    public static void sendToPlayer(ServerPlayer player, github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message message) {
        channel.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),message);
    }
    public static void sendDevicePacket(int window,github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message message){channel.sendToServer(new DeviceMessage(window,message));}
    public static void sendLegacyPacket(github.com.gengyoubo.mfcl.bridge.LegacyPackets.Message message){channel.sendToServer(new ServerboundMessage(message));}
    private static void apply(ServerPlayer player, ScanMessage message) {
        var bridge = ModernContentBridge.active();
        if (bridge != null) bridge.applyServerScan(player, message);
    }
}
