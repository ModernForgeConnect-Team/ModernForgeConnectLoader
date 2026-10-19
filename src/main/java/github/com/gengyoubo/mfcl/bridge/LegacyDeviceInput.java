package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Original custom GUI input with native ownership of inventory slot clicks. */
public final class LegacyDeviceInput implements AutoCloseable {
    private static final ThreadLocal<LegacyDeviceInput> CURRENT=new ThreadLocal<>();
    private final List<LegacyPackets.Message> packets=new ArrayList<>();
    private final LegacyDeviceInput previous;
    public LegacyDeviceInput(){previous=CURRENT.get();CURRENT.set(this);}
    public static boolean active(){return CURRENT.get()!=null;}
    public static boolean send(Object wrapper,Object packet) throws Exception {
        var scope=CURRENT.get();if(scope==null)return false;
        scope.packets.add(LegacyPackets.encode(wrapper,packet,"SERVER"));return true;
    }
    public List<LegacyPackets.Message> packets(){return List.copyOf(packets);}
    @Override public void close(){if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
}
