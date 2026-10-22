package github.com.gengyoubo.mfcl.content;

import github.com.gengyoubo.mfcl.bridge.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** Native synchronization/authority around an original server Container, including its original slot-click logic. */
public final class LegacyDeviceMenu extends AbstractContainerMenu {
    private final ModernContentBridge content;
    private final Inventory inventory;
    private final LegacyDeviceContainers.Open original;
    private final LegacyDeviceContainers.Descriptor descriptor;
    private final SimpleContainer contents;
    private boolean failed;
    private boolean opening=true;
    private final List<LegacyPackets.Message> initialPackets=new ArrayList<>();
    public Object originalPlayer(){return original==null?null:original.player();}
    public LegacyDeviceContainers.Descriptor descriptor(){return descriptor;}
    private BlockPos origin(){return descriptor.item()?inventory.player.blockPosition():new BlockPos(descriptor.x(),descriptor.y(),descriptor.z());}
    public static void open(ServerPlayer player,ModernContentBridge content,LegacyDeviceContainers.Open request) {
        NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inventory,ignored)->new LegacyDeviceMenu(id,inventory,content,request),Component.empty()),buffer->{
            var d=request.descriptor();buffer.writeUtf(d.mod(),128);buffer.writeVarInt(d.id());buffer.writeBlockPos(new BlockPos(d.x(),d.y(),d.z()));buffer.writeBoolean(d.inventory());buffer.writeBoolean(d.item());
            try {
                var slots=LegacyDeviceContainers.slots(request.container());buffer.writeVarInt(slots.size());
                for(Object slot:slots){buffer.writeInt(slot.getClass().getField("field_75223_e").getInt(slot));buffer.writeInt(slot.getClass().getField("field_75221_f").getInt(slot));}
            }catch(Exception failure){throw new IllegalStateException("Cannot describe original slots",failure);}
        });
        if(player.containerMenu instanceof LegacyDeviceMenu menu) {
            menu.opening=false;
            menu.initialPackets.forEach(packet->ScanSyncChannel.sendToPlayer(player,packet));
            menu.initialPackets.clear();
        }
        com.mojang.logging.LogUtils.getLogger().debug("MFCL opened native device menu {} at {}",player.containerMenu.containerId,request.descriptor());
    }
    public static LegacyDeviceMenu fromNetwork(int id,Inventory inventory,FriendlyByteBuf buffer) {
        com.mojang.logging.LogUtils.getLogger().debug("MFCL received native device menu {}",id);
        try {
        String mod=buffer.readUtf(128);int gui=buffer.readVarInt();var pos=buffer.readBlockPos();boolean chest=buffer.readBoolean(),item=buffer.readBoolean();int count=buffer.readVarInt();
        if(count<0||count>512)throw new IllegalArgumentException("Invalid legacy menu size "+count);
        var positions=new ArrayList<int[]>();for(int index=0;index<count;index++)positions.add(new int[]{buffer.readInt(),buffer.readInt()});
        return new LegacyDeviceMenu(id,inventory,ModernContentBridge.active(),null,new LegacyDeviceContainers.Descriptor(mod,gui,pos.getX(),pos.getY(),pos.getZ(),chest,item),positions);
        }catch(RuntimeException failure){com.mojang.logging.LogUtils.getLogger().error("Cannot decode original device menu",failure);throw failure;}
    }
    private LegacyDeviceMenu(int id,Inventory inventory,ModernContentBridge content,LegacyDeviceContainers.Open request) {
        this(id,inventory,content,request,request.descriptor(),positions(request));
        try {
            playerContext(()->{LegacyDeviceContainers.attachViewer(request,id);refresh();return null;});
        }catch(Exception failure){throw new IllegalStateException("Cannot open original container",failure);}
    }
    private static List<int[]> positions(LegacyDeviceContainers.Open request) {
        try {var positions=new ArrayList<int[]>();for(Object slot:LegacyDeviceContainers.slots(request.container()))positions.add(new int[]{slot.getClass().getField("field_75223_e").getInt(slot),slot.getClass().getField("field_75221_f").getInt(slot)});return positions;}
        catch(Exception failure){throw new IllegalStateException(failure);}
    }
    private LegacyDeviceMenu(int id,Inventory inventory,ModernContentBridge content,LegacyDeviceContainers.Open original,LegacyDeviceContainers.Descriptor descriptor,List<int[]> positions) {
        super(content.deviceMenuType(),id);this.content=content;this.inventory=inventory;this.original=original;this.descriptor=descriptor;contents=new SimpleContainer(positions.size());
        for(int index=0;index<positions.size();index++){var p=positions.get(index);addSlot(new Slot(contents,index,p[0],p[1]));}
    }
    private ItemStack modern(Object stack) throws Exception {return stack==null?ItemStack.EMPTY:content.gameplayStack(content.services().items().snapshot(stack));}
    private Object old(ItemStack stack) throws Exception {return stack.isEmpty()?null:content.services().items().materialize(content.gameplayStack(stack));}
    private Object oldInventory() throws Exception {return content.services().loader().loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(original.player());}
    private void refresh() throws Exception {
        var next=new ArrayList<ItemStack>();
        for(Object slot:LegacyDeviceContainers.slots(original.container()))next.add(modern(slot.getClass().getMethod("func_75211_c").invoke(slot)));
        if(next.size()!=slots.size())throw new IllegalStateException("Original device changed its slot layout");
        for(int index=0;index<next.size();index++)contents.setItem(index,next.get(index));
    }
    private void positionActor(Player player) throws Exception {
        var entity=content.services().loader().loadClass("net.minecraft.entity.Entity");
        entity.getField("field_70165_t").setDouble(original.player(),player.getX());entity.getField("field_70163_u").setDouble(original.player(),player.getY());entity.getField("field_70161_v").setDouble(original.player(),player.getZ());
    }
    private void importInventory() throws Exception {
        var stacks=new ArrayList<LegacyClientData.Stack>();for(int index=0;index<36;index++)stacks.add(content.toLegacy(inventory.getItem(index)));
        content.services().playerInventory().write(original.player(),stacks);content.services().playerInventory().select(original.player(),inventory.selected);
    }
    private <T> T originalAction(java.util.concurrent.Callable<T> action) throws Exception {
        return LegacyItemDrops.capture(content,inventory.player,action);
    }
    /** Ordinary slot callbacks consult the same authoritative knowledge as device packets and buttons. */
    private <T> T playerContext(java.util.concurrent.Callable<T> action) throws Exception {
        var services=content.services();var server=(ServerPlayer)inventory.player;
        return services.items().inLegacy(()->{
            Object world=services.worlds().bound(server.serverLevel());
            try(var state=services.playerState().enter(world);var outgoing=LegacyPackets.capture(server.getUUID())) {
                byte[] saved=server.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
                if(saved.length!=0)services.playerData().write(original.player(),saved);
                services.playerState().initialize(original.player());
                byte[] prior=services.playerData().read(original.player());
                try {
                    T result=action.call();
                    server.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,services.playerData().read(original.player()));
                    if(opening)initialPackets.addAll(outgoing.messages());
                    else outgoing.messages().forEach(packet->ScanSyncChannel.sendToPlayer(server,packet));
                    return result;
                } catch(Exception | Error failure){services.playerData().write(original.player(),prior);throw failure;}
            }
        });
    }
    @Override public void broadcastChanges() {
        if(original!=null)try {
            positionActor(inventory.player);
            importInventory();
            playerContext(()->{original.container().getClass().getMethod("func_75142_b").invoke(original.container());refresh();return null;});
        }catch(Exception failure){throw new IllegalStateException("Cannot synchronize original container",failure);}
        super.broadcastChanges();
    }
    @Override public boolean stillValid(Player player) {
        if(original==null)return true;
        if(player.level()!=inventory.player.level()||(!descriptor.item()&&player.distanceToSqr(descriptor.x()+.5,descriptor.y()+.5,descriptor.z()+.5)>64))return false;
        try {positionActor(player);return playerContext(()->(boolean)original.container().getClass().getMethod("func_75145_c",content.services().loader().loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(original.container(),original.player()));}
        catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("MFCL original container validity check failed",failure);return false;}
    }
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(original==null||!(player instanceof ServerPlayer server)||!stillValid(player))return;
        int mode=switch(type){case PICKUP->0;case QUICK_MOVE->1;case SWAP->2;case CLONE->3;case THROW->4;case QUICK_CRAFT->5;case PICKUP_ALL->6;};
        try {
            var services=content.services();var loader=services.loader();var stackType=loader.loadClass("net.minecraft.item.ItemStack");var playerType=loader.loadClass("net.minecraft.entity.player.EntityPlayer");
            // Unmapped host items are hidden from the old container and kept unless the original logic changed their slot.
            var before=new ArrayList<LegacyClientData.Stack>();for(int index=0;index<40;index++)before.add(content.toLegacy(inventory.getItem(index)));
            services.playerInventory().write(original.player(),before);services.playerInventory().select(original.player(),inventory.selected);
            oldInventory().getClass().getMethod("func_70437_b",stackType).invoke(oldInventory(),old(getCarried()));
            var result=playerContext(()->content.logicTransaction(server.serverLevel(),origin(),()->originalAction(()->{
                original.container().getClass().getMethod("func_75144_a",int.class,int.class,int.class,playerType).invoke(original.container(),slot,button,mode,original.player());
                var read=services.playerInventory().read(original.player());var next=new ArrayList<ItemStack>();
                for(int index=0;index<read.size();index++)next.add(index<before.size()&&ModernContentBridge.sameLegacy(before.get(index),read.get(index))?null:content.gameplayStack(read.get(index)));
                ItemStack carried=modern(oldInventory().getClass().getMethod("func_70445_o").invoke(oldInventory()));
                refresh();return new ClickResult(next,carried);
            }),true));
            for(int index=0;index<result.inventory().size();index++)if(result.inventory().get(index)!=null)inventory.setItem(index,result.inventory().get(index));
            inventory.setChanged();setCarried(result.carried());broadcastChanges();
        }catch(Exception failure){failed=true;com.mojang.logging.LogUtils.getLogger().error("MFCL original container click failed",failure);server.closeContainer();}
    }
    private record ClickResult(List<ItemStack> inventory,ItemStack carried) {}
    /** Native menu buttons (e.g. research note duplication) run the original container's enchant/button hook. */
    @Override public boolean clickMenuButton(Player player,int id) {
        if(original==null||!(player instanceof ServerPlayer server)||!stillValid(player))return false;
        var services=content.services();
        try {
            var before=new ArrayList<LegacyClientData.Stack>();for(int index=0;index<36;index++)before.add(content.toLegacy(inventory.getItem(index)));
            var result=services.items().inLegacy(()->{
                Object world=services.worlds().bound(server.serverLevel());
                try(var state=services.playerState().enter(world);var outgoing=LegacyPackets.capture(server.getUUID())) {
                    byte[] persisted=server.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
                    if(persisted.length!=0)services.playerData().write(original.player(),persisted);
                    services.playerState().initialize(original.player());
                    byte[] prior=services.playerData().read(original.player());
                    try {
                        boolean handled=content.logicTransaction(server.serverLevel(),origin(),()->originalAction(()->(boolean)original.container().getClass()
                                .getMethod("func_75140_a",services.loader().loadClass("net.minecraft.entity.player.EntityPlayer"),int.class).invoke(original.container(),original.player(),id)),true);
                        return new ButtonResult(handled,services.playerData().read(original.player()),outgoing.messages());
                    }catch(Exception failure){services.playerData().write(original.player(),prior);throw failure;}
                }
            });
            server.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,result.data());
            result.messages().forEach(message->ScanSyncChannel.sendToPlayer(server,message));
            var stacks=services.playerInventory().read(original.player());
            for(int index=0;index<stacks.size()&&index<36;index++)if(!ModernContentBridge.sameLegacy(before.get(index),stacks.get(index)))
                inventory.setItem(index,stacks.get(index)==null?ItemStack.EMPTY:content.gameplayStack(stacks.get(index)));
            inventory.setChanged();broadcastChanges();
            return result.handled();
        }catch(Exception failure){failed=true;com.mojang.logging.LogUtils.getLogger().error("MFCL original menu button failed",failure);server.closeContainer();return false;}
    }
    private record ButtonResult(boolean handled,byte[] data,List<LegacyPackets.Message> messages) {}
    public void receivePacket(ServerPlayer player,LegacyPackets.Message packet) {
        if(original==null||player!=inventory.player||!stillValid(player))return;
        var services=content.services();
        try {
            var result=services.items().inLegacy(()->{
                Object world=services.worlds().bound(player.serverLevel());
                try(var state=services.playerState().enter(world);var outgoing=LegacyPackets.capture(player.getUUID())) {
                    byte[] persisted=player.getPersistentData().getByteArray(ModernContentBridge.LEGACY_ENTITY_DATA);
                    if(persisted.length!=0)services.playerData().write(original.player(),persisted);
                    services.playerState().initialize(original.player());
                    byte[] prior=services.playerData().read(original.player());
                    try {
                        byte[] saved=content.logicTransaction(player.serverLevel(),origin(),()->originalAction(()->{
                            LegacyPackets.receiveServer(services,packet,original.player(),descriptor);
                            return services.playerData().read(original.player());
                        }),true);
                        return new PacketResult(saved,outgoing.messages());
                    }catch(Exception failure){services.playerData().write(original.player(),prior);throw failure;}
                }
            });
            player.getPersistentData().putByteArray(ModernContentBridge.LEGACY_ENTITY_DATA,result.data());
            result.messages().forEach(message->ScanSyncChannel.sendToPlayer(player,message));broadcastChanges();
        }catch(Exception failure){failed=true;com.mojang.logging.LogUtils.getLogger().error("MFCL original device packet failed",failure);player.closeContainer();}
    }
    private record PacketResult(byte[] data,List<LegacyPackets.Message> messages) {}
    @Override public void removed(Player player) {
        if(original!=null&&!failed)try {
            importInventory();
            var returned=playerContext(()->content.logicTransaction(player.level(),origin(),()->originalAction(()->{
                oldInventory().getClass().getMethod("func_70437_b",content.services().loader().loadClass("net.minecraft.item.ItemStack")).invoke(oldInventory(),new Object[]{null});
                original.container().getClass().getMethod("func_75134_a",content.services().loader().loadClass("net.minecraft.entity.player.EntityPlayer")).invoke(original.container(),original.player());
                return content.services().playerInventory().read(original.player()).stream().map(content::gameplayStack).toList();
            }),true));
            for(int index=0;index<returned.size();index++)inventory.setItem(index,returned.get(index));
        }catch(Exception failure){com.mojang.logging.LogUtils.getLogger().error("MFCL original container close failed",failure);}
        super.removed(player);
    }
}
