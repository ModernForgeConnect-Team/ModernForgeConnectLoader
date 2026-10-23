package github.com.gengyoubo.mfcl.bridge;

/** Reusable sender and open-device checks; adapters declare field names and gameplay rules. */
public final class LegacyPacketTargets {
    private LegacyPacketTargets() {}
    public static void requireDimension(Object packet,Object player,String dimensionField,String action)throws Exception {
        if(LegacyObjects.declaredField(packet,dimensionField).getInt(packet)!=LegacyObjects.dimension(LegacyObjects.world(player)))
            throw new IllegalArgumentException(action+" targets another dimension");
    }
    public static void bindPlayer(Object packet,Object player,String dimensionField,String playerIdField,String action)throws Exception {
        requireDimension(packet,player,dimensionField,action);
        LegacyObjects.declaredField(packet,playerIdField).setInt(packet,LegacyObjects.entityId(player));
    }
    public static void requirePlayerName(Object packet,Object player,String nameField,String action)throws Exception {
        if(!LegacyObjects.playerName(player).equals(LegacyObjects.declared(packet,nameField)))throw new IllegalArgumentException(action+" targets another player");
    }
    public static void requireDevice(Object packet,LegacyDeviceContainers.Descriptor device,String mod,int gui,String xField,String yField,String zField,String action)throws Exception {
        if(device==null||!device.mod().equals(mod)||device.id()!=gui||device.inventory()||device.item())throw new IllegalArgumentException(action+" requires the open device");
        if(LegacyObjects.declaredField(packet,xField).getInt(packet)!=device.x()||LegacyObjects.declaredField(packet,yField).getInt(packet)!=device.y()||LegacyObjects.declaredField(packet,zField).getInt(packet)!=device.z())
            throw new IllegalArgumentException(action+" targets another device");
    }
}
