package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Original custom GUI input with native ownership of inventory slot clicks. */
public final class LegacyDeviceInput implements AutoCloseable {
    private static final ThreadLocal<LegacyDeviceInput> CURRENT=new ThreadLocal<>();
    private final List<LegacyPackets.Message> packets=new ArrayList<>();
    private final LegacyDeviceInput previous;
    public LegacyDeviceInput(){previous=CURRENT.get();CURRENT.set(this);}
    public static boolean active(){return CURRENT.get()!=null;}
    /** A connection-less old client closes the host menu through its native protocol. */
    public static boolean closeContainer() {
        if(!active())return false;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.player==null||!(mc.player.containerMenu instanceof github.com.gengyoubo.mfcl.content.LegacyDeviceMenu))return false;
        var menu=mc.player.containerMenu;
        mc.tell(()->{if(mc.player!=null&&mc.player.containerMenu==menu)mc.player.closeContainer();});
        return true;
    }
    public static boolean send(Object wrapper,Object packet) throws Exception {
        var scope=CURRENT.get();if(scope==null)return false;
        scope.packets.add(LegacyPackets.encode(wrapper,packet,"SERVER"));return true;
    }
    /** Original container-button packets (e.g. research note duplication) become native menu buttons. */
    public static boolean sendButton(int windowId,int button) {
        if(CURRENT.get()==null)return false;
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.getConnection()==null||mc.player==null||mc.player.containerMenu==null||mc.player.containerMenu.containerId!=windowId)return false;
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(windowId,button));
        return true;
    }
    public List<LegacyPackets.Message> packets(){return List.copyOf(packets);}
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
