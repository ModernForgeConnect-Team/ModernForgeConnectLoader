package github.com.gengyoubo.mfcl.compat.extrautils;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.UUID;

/** Original Deep Dark rooms / Last Millennium platforms and destination-specific host portal lookup. */
public final class ExtraUtilitiesTravel {
    private ExtraUtilitiesTravel(){}
    static void install(LegacyServices services,int deepDark,int millennium) {
        install(services,"com.rwtema.extrautils.worldgen.Underdark.TeleporterUnderdark",deepDark,0);
        install(services,"com.rwtema.extrautils.worldgen.endoftime.TeleporterEndOfTime",millennium,2);
    }
    private static void install(LegacyServices services,String teleporterType,int destination,int variant) {
        LegacyTravel.placement(teleporterType,(world,teleporter,x,y,z,yaw)->{
            try {
                // Ignore the locked bit, but never confuse Deep Dark and Last Millennium portals sharing one registry key.
                int[] portal=LegacyTravel.findBlock(world,"ExtraUtilities:dark_portal",14,variant,(int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z),80);
                if(portal!=null)return new double[]{portal[0]+.5,portal[1]+1,portal[2]+.5};
                var loader=services.loader();var entity=loader.loadClass("net.minecraft.entity.Entity");
                int dimension=LegacyObjects.dimension(world);
                if(dimension!=destination)return new double[]{x,(int)loader.loadClass("net.minecraft.world.World").getMethod("func_72825_h",int.class,int.class).invoke(world,(int)Math.floor(x),(int)Math.floor(z)),z};
                if(variant==2){
                    Object provider=loader.loadClass("net.minecraft.world.World").getField("field_73011_w").get(world);
                    Object spawn=loader.loadClass("net.minecraft.world.WorldProvider").getMethod("func_76554_h").invoke(provider);
                    var coordinates=loader.loadClass("net.minecraft.util.ChunkCoordinates");
                    int sx=coordinates.getField("field_71574_a").getInt(spawn),sz=coordinates.getField("field_71573_c").getInt(spawn);
                    portal=LegacyTravel.findBlock(world,"ExtraUtilities:dark_portal",14,variant,sx,64,sz,80);
                    if(portal!=null)return new double[]{portal[0]+.5,portal[1]+1,portal[2]+.5};
                }
                Object actor=services.tiles().actor(world,new LegacyTiles.Placer(new UUID(0,0),"MFCL arrival",x,y,z,yaw,0),null);
                teleporter.getClass().getMethod("func_85188_a",entity).invoke(teleporter,actor);
                return new double[]{entity.getField("field_70165_t").getDouble(actor),entity.getField("field_70163_u").getDouble(actor),entity.getField("field_70161_v").getDouble(actor)};
            }catch(Exception failure){throw new IllegalStateException("Cannot place original Extra Utilities portal arrival",failure);}
        });
    }
}
