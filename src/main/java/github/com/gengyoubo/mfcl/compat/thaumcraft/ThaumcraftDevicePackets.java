package github.com.gengyoubo.mfcl.compat.thaumcraft;

import github.com.gengyoubo.mfcl.bridge.*;
import java.lang.reflect.Field;
import java.util.Map;

/** Validation for TC4's original research-table handlers. */
final class ThaumcraftDevicePackets {
    static boolean prepare(Object packet,Object player,LegacyDeviceContainers.Descriptor device) throws Exception {
        String name=packet.getClass().getName();
        boolean placement=name.equals("thaumcraft.common.lib.network.playerdata.PacketAspectPlaceToServer");
        if(!placement&&!name.equals("thaumcraft.common.lib.network.playerdata.PacketAspectCombinationToServer"))return false;
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
        if(placement) {
            preparePlacement(packet,player,tile,loader);
            return true;
        }
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
    private static void preparePlacement(Object packet,Object player,Object tile,ClassLoader loader) throws Exception {
        // Read the current stack: the cached grid may belong to the previous note in this table.
        tile.getClass().getMethod("gatherResults").invoke(tile);
        Object data=tile.getClass().getField("data").get(tile);
        Object note=tile.getClass().getMethod("func_70301_a",int.class).invoke(tile,1);
        if(data==null||note==null||(int)note.getClass().getMethod("func_77960_j").invoke(note)>=64
                ||(boolean)data.getClass().getMethod("isComplete").invoke(data))throw new IllegalArgumentException("Research action requires an unfinished note");
        int q=field(packet,"q").getByte(packet),r=field(packet,"r").getByte(packet);
        Object hex=loader.loadClass("thaumcraft.common.lib.utils.HexUtils$Hex").getConstructor(int.class,int.class).newInstance(q,r);
        String key=hex.toString();
        var hexes=(Map<?,?>)data.getClass().getField("hexes").get(data);
        var entries=(Map<?,?>)data.getClass().getField("hexEntries").get(data);
        Object entry=entries.get(key),aspect=field(packet,"aspect").get(packet);
        if(!hexes.containsKey(key)||entry==null)throw new IllegalArgumentException("Research hex is outside the note");
        int type=entry.getClass().getField("type").getInt(entry);
        if(type!=(aspect==null?2:0))throw new IllegalArgumentException("Research hex cannot be changed");
        if(aspect==null)return; // A null aspect is the original erase operation.
        Object proxy=loader.loadClass("thaumcraft.common.Thaumcraft").getField("proxy").get(null);
        Object knowledge=loader.loadClass("thaumcraft.common.CommonProxy").getField("playerKnowledge").get(proxy);
        Class<?> aspectType=loader.loadClass("thaumcraft.api.aspects.Aspect");
        String username=(String)player.getClass().getMethod("func_70005_c_").invoke(player);
        Object discovered=knowledge.getClass().getMethod("getAspectsDiscovered",String.class).invoke(knowledge,username);
        if(discovered==null||(int)discovered.getClass().getMethod("getAmount",aspectType).invoke(discovered,aspect)<=0)
            throw new IllegalArgumentException("Research aspect has not been discovered");
        int pool=((Number)knowledge.getClass().getMethod("getAspectPoolFor",String.class,aspectType).invoke(knowledge,username,aspect)).intValue();
        Object bonus=tile.getClass().getField("bonusAspects").get(tile);
        if(pool<=0&&(int)bonus.getClass().getMethod("getAmount",aspectType).invoke(bonus,aspect)<=0)
            throw new IllegalArgumentException("Research aspect has no available points");
    }
    private static Field field(Object value,String name) throws Exception {var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field;}
}
