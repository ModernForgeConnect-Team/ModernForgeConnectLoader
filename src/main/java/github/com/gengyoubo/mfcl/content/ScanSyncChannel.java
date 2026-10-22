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
    /** Remote trading ({@link RemoteMerchants}): the villagers a device reaches, and the player's choice among them. */
    public record MerchantEntry(int entityId, net.minecraft.network.chat.Component name, java.util.List<net.minecraft.world.item.ItemStack> results) {}
    public record MerchantList(net.minecraft.core.BlockPos origin, java.util.List<MerchantEntry> merchants) {}
    public record MerchantPick(net.minecraft.core.BlockPos origin, int entityId) {}
    /** Original device slot counts beyond the one-byte vanilla menu protocol; zero means the vanilla count is exact. */
    public record SlotCounts(int window, int[] counts) {}
    private static final String VERSION = "8";
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
        message(guard, 4, MerchantList.class,
                (message,buffer)->{buffer.writeBlockPos(message.origin());buffer.writeCollection(message.merchants(),(out,entry)->{out.writeVarInt(entry.entityId());out.writeComponent(entry.name());out.writeCollection(entry.results(),FriendlyByteBuf::writeItem);});},
                buffer->new MerchantList(buffer.readBlockPos(),buffer.readList(in->new MerchantEntry(in.readVarInt(),in.readComponent(),in.readList(FriendlyByteBuf::readItem)))),
                (message,context)->{context.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->github.com.gengyoubo.mfcl.client.RemoteMerchantScreen.open(message)));context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        message(guard, 5, MerchantPick.class,
                (message,buffer)->{buffer.writeBlockPos(message.origin());buffer.writeVarInt(message.entityId());},
                buffer->new MerchantPick(buffer.readBlockPos(),buffer.readVarInt()),
                (message,context)->{context.get().enqueueWork(()->{var player=context.get().getSender();if(player!=null)RemoteMerchants.pick(player,message);});context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        message(guard, 6, SlotCounts.class,
                (message,buffer)->{
                    int[] counts=message.counts();buffer.writeVarInt(message.window());buffer.writeVarInt(counts.length);
                    buffer.writeVarInt((int)java.util.Arrays.stream(counts).filter(count->count!=0).count());
                    for(int index=0;index<counts.length;index++)if(counts[index]!=0){buffer.writeVarInt(index);buffer.writeVarInt(counts[index]);}
                },
                buffer->{
                    int window=buffer.readVarInt(),size=buffer.readVarInt();
                    if(size<0||size>LegacyDeviceMenu.MAX_SLOTS)throw new IllegalArgumentException("Invalid legacy menu size "+size);
                    int[] counts=new int[size];int entries=buffer.readVarInt();
                    if(entries<0||entries>size)throw new IllegalArgumentException("Invalid legacy slot count list "+entries);
                    for(int entry=0;entry<entries;entry++){int index=buffer.readVarInt();if(index<0||index>=size)throw new IllegalArgumentException("Invalid legacy slot "+index);counts[index]=buffer.readVarInt();}
                    return new SlotCounts(window,counts);
                },
                (message,context)->{context.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->github.com.gengyoubo.mfcl.client.ClientContainerBridge.receiveCounts(message.window(),message.counts())));context.get().setPacketHandled(true);},
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
    }
    static void sendSlotCounts(ServerPlayer player,int window,int[] counts){channel.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),new SlotCounts(window,counts));}
    static void sendMerchants(ServerPlayer player, MerchantList list){channel.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->player),list);}
    public static void pickMerchant(MerchantPick pick){channel.sendToServer(pick);}
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
