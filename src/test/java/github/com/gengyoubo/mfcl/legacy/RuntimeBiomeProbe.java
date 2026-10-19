package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;

/** Original object identity and exact world-coordinate forwarding, including the TC4 byte-array adapter. */
final class RuntimeBiomeProbe {
    static void verify(LegacyServices services,Map<String,Object> report) throws Exception {
        final class Host implements HostWorldAccess {
            String biome="minecraft:desert";int x,z;boolean client;
            public String dimensionKey(){return "mfcl:biomes";}public int legacyDimensionId(){return 0;}public boolean clientSide(){return client;}
            public long gameTime(){return 0;}public long dayTime(){return 0;}public void setDayTime(long time){}
            public Block block(int x,int y,int z){return new Block("minecraft:air",0);}public boolean setBlock(int x,int y,int z,Block block,int flags){return false;}
            public String biome(int x,int z){this.x=x;this.z=z;return biome;}
            public void setBiome(int x,int z,String key){this.x=x;this.z=z;biome=key;}
        }
        Host host=new Host();Object identity=new Object(),world=services.worlds().bind(identity,host);
        try{
            Object desert=services.biomes().resolve("minecraft:desert");
            Object actual=world.getClass().getMethod("func_72807_a",int.class,int.class).invoke(world,-17,33);
            if(actual!=desert||host.x!=-17||host.z!=33)throw new AssertionError("Biome lookup ignored identity/coordinates");
            var definitions=services.biomes().definitions();
            if(!definitions.isEmpty()){
                if(definitions.size()!=4||definitions.stream().filter(b->b.weight()>0).count()!=2)throw new AssertionError("Missing TC4 biome declarations: "+definitions);
                var loader=services.loader();var method=loader.loadClass("thaumcraft.common.lib.utils.Utils").getMethod("setBiomeAt",loader.loadClass("net.minecraft.world.World"),int.class,int.class,loader.loadClass("net.minecraft.world.biome.BiomeGenBase"));
                for(var definition:definitions){
                    Object biome=services.biomes().resolve(definition.key());
                    method.invoke(null,world,-19,45,biome);
                    if(!host.biome.equals(definition.key())||host.x!=-19||host.z!=45||LegacyWorlds.biome(world,-19,45)!=biome)throw new AssertionError("Original biome mutation was not forwarded: "+definition);
                }
                method.invoke(null,world,0,0,null);
                if(host.x!=-19||host.z!=45)throw new AssertionError("Null biome mutated the host");
                host.client=true;
                try{LegacyWorlds.setBiome(world,0,0,desert);throw new AssertionError("Client biome mutation accepted");}catch(UnsupportedOperationException expected){}
                report.put("originalThaumcraftBiomeMutationsVerified",true);
            }
            try{services.biomes().resolve("mfcl:unknown");throw new AssertionError("Unknown biome silently became plains");}catch(UnsupportedOperationException expected){}
            if(services.biomes().resolve("minecraft:dark_forest")==desert)throw new AssertionError("Distinct vanilla biomes share a fallback");
            report.put("genericBiomeIdentityAndCoordinatesVerified",true);
        }finally{services.worlds().unbind(identity);}
    }
}
