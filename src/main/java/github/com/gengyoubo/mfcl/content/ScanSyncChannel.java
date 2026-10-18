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
    private static final String VERSION = "1";
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
    }
    public static void sendToServer(LegacyItemUse.Scan scan, String prefix) {
        if (channel != null) channel.sendToServer(new ScanMessage(scan.type(), scan.id(), scan.meta(), scan.phenomena(), prefix));
    }
    private static void apply(ServerPlayer player, ScanMessage message) {
        var bridge = ModernContentBridge.active();
        if (bridge != null) bridge.applyServerScan(player, message);
    }
}
