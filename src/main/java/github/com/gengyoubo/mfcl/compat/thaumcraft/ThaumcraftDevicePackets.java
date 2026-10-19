package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import java.lang.reflect.Field;

/** Validation for TC4's original research-table combination handler. */
final class ThaumcraftDevicePackets {
    static boolean prepare(Object packet,Object player,LegacyDeviceContainers.Descriptor device) throws Exception {
        if(!packet.getClass().getName().equals("thaumcraft.common.lib.network.playerdata.PacketAspectCombinationToServer"))return false;
        if(!device.mod().equals("Thaumcraft")||device.id()!=10||device.inventory())throw new IllegalArgumentException("Research action requires the open research table");
        for(String axis:new String[]{"x","y","z"}) {
            int expected=switch(axis){case "x"->device.x();case "y"->device.y();default->device.z();};
            if(field(packet,axis).getInt(packet)!=expected)throw new IllegalArgumentException("Research action targets another table");
        }
        var loader=player.getClass().getClassLoader();var worldType=loader.loadClass("net.minecraft.world.World");
        Object world=loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p").get(player);
        Object provider=worldType.getField("field_73011_w").get(world);
        if(field(packet,"dim").getInt(packet)!=provider.getClass().getField("field_76574_g").getInt(provider))throw new IllegalArgumentException("Research action targets another dimension");
        field(packet,"playerid").setInt(packet,(int)player.getClass().getMethod("func_145782_y").invoke(player));
        Object tile=worldType.getMethod("func_147438_o",int.class,int.class,int.class).invoke(world,device.x(),device.y(),device.z());
        if(tile==null||!tile.getClass().getName().equals("thaumcraft.common.tiles.TileResearchTable"))throw new IllegalArgumentException("Research table no longer exists");
        Object bonus=tile.getClass().getField("bonusAspects").get(tile);
        for(int index=1;index<=2;index++) {
            Object aspect=field(packet,"aspect"+index).get(packet);
            if(aspect==null)throw new IllegalArgumentException("Unknown research aspect");
            // Bonus availability is authoritative; client booleans cannot grant free points.
            int available=(int)bonus.getClass().getMethod("getAmount",loader.loadClass("thaumcraft.api.aspects.Aspect")).invoke(bonus,aspect);
            field(packet,"ab"+index).setBoolean(packet,available>0);
        }
        return true;
    }
    private static Field field(Object value,String name) throws Exception {var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field;}
}
