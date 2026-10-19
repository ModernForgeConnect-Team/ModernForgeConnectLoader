package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import java.lang.reflect.Field;

/** Validation for TC4's original research-table packets. */
final class ThaumcraftDevicePackets {
    static boolean prepare(Object packet,Object player,LegacyDeviceContainers.Descriptor device) throws Exception {
        String type=packet.getClass().getName();
        if(type.equals("thaumcraft.common.lib.network.playerdata.PacketAspectCombinationToServer")) {
            Object tile=target(packet,player,device);
            var loader=player.getClass().getClassLoader();
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
        if(type.equals("thaumcraft.common.lib.network.playerdata.PacketAspectPlaceToServer")) {
            Object tile=target(packet,player,device);
            validatePlacement(packet,player,tile);
            return true;
        }
        return false;
    }
    private static Object target(Object packet,Object player,LegacyDeviceContainers.Descriptor device) throws Exception {
        if(device==null||!device.mod().equals("Thaumcraft")||device.id()!=10||device.inventory())throw new IllegalArgumentException("Research action requires the open research table");
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
        return tile;
    }
    private static void validatePlacement(Object packet,Object player,Object tile) throws Exception {
        var loader=player.getClass().getClassLoader();
        Object[] contents=(Object[])tile.getClass().getField("contents").get(tile);
        Object note=contents[1];
        var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        Object item=note==null?null:stackType.getMethod("func_77973_b").invoke(note);
        if(item==null||!loader.loadClass("thaumcraft.common.items.ItemResearchNotes").isInstance(item))throw new IllegalArgumentException("Research table has no editable note");
        if((int)stackType.getMethod("func_77960_j").invoke(note)>=64)throw new IllegalArgumentException("Research note is already complete");
        Object data=loader.loadClass("thaumcraft.common.lib.research.ResearchManager").getMethod("getData",stackType).invoke(null,note);
        if(data==null)throw new IllegalArgumentException("Research note has no original data");
        byte q=field(packet,"q").getByte(packet),r=field(packet,"r").getByte(packet);
        Object hex=loader.loadClass("thaumcraft.common.lib.utils.HexUtils$Hex").getConstructor(int.class,int.class).newInstance((int)q,(int)r);
        var entries=(java.util.Map<?,?>)data.getClass().getField("hexEntries").get(data);
        if(!entries.containsKey(hex.toString()))throw new IllegalArgumentException("Research note has no such hex");
    }
    private static Field field(Object value,String name) throws Exception {var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field;}
}
