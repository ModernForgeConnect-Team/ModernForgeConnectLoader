package github.com.gengyoubo.mfcl.content;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import java.nio.file.Files;
import java.util.*;

/** Exercises the old structure-generator biome manager against a real dedicated-server level. */
final class BiomeManagerHostSmokeProbe {
    private final ModernContentBridge content;
    private boolean done;
    private BiomeManagerHostSmokeProbe(ModernContentBridge content){this.content=content;}
    static void install(ModernContentBridge content){MinecraftForge.EVENT_BUS.addListener(new BiomeManagerHostSmokeProbe(content)::tick);}
    private void tick(TickEvent.ServerTickEvent event){
        if(done||event.phase!=TickEvent.Phase.END)return;done=true;
        var proof=new LinkedHashMap<String,Object>();
        try {
            var level=event.getServer().overworld();var services=content.services();var loader=services.loader();
            services.items().inLegacy(()->{
                Object world=services.worlds().bound(level);Class<?> oldWorld=loader.loadClass("net.minecraft.world.World");
                Object manager=oldWorld.getMethod("func_72959_q").invoke(world);
                if(manager==null)throw new IllegalStateException("Host biome manager is null");
                var type=loader.loadClass("net.minecraft.world.biome.WorldChunkManager");
                var query=type.getMethod("func_76935_a",int.class,int.class);
                int x=-9,z=-13;Object expected=services.biomes().resolve(HostBiomeQueries.key(level,x,z));
                if(query.invoke(manager,x,z)!=expected)throw new IllegalStateException("Manager did not return the host biome");
                var biome=loader.loadClass("net.minecraft.world.biome.BiomeGenBase");Class<?> array=java.lang.reflect.Array.newInstance(biome,0).getClass();
                Object[] grid=(Object[])type.getMethod("func_76933_b",array,int.class,int.class,int.class,int.class).invoke(manager,null,x,z,2,2);
                if(grid[0]!=expected||grid[3]!=query.invoke(manager,x+1,z+1))throw new IllegalStateException("Biome grid differs from the host");
                Object[] quarter=(Object[])type.getMethod("func_76937_a",array,int.class,int.class,int.class,int.class).invoke(manager,null,-2,-3,2,2);
                if(quarter[0]!=query.invoke(manager,-8,-12)||quarter[3]!=query.invoke(manager,-4,-8))throw new IllegalStateException("Quarter-scale biome grid differs from the host");
                var one=List.of(query.invoke(manager,0,0));
                if(!(boolean)type.getMethod("func_76940_a",int.class,int.class,int.class,List.class).invoke(manager,0,0,0,one))throw new IllegalStateException("Allowed biome rejected");
                if((boolean)type.getMethod("func_76940_a",int.class,int.class,int.class,List.class).invoke(manager,0,0,0,List.of()))throw new IllegalStateException("Disallowed biome accepted");
                if(type.getMethod("func_150795_a",int.class,int.class,int.class,List.class,Random.class).invoke(manager,0,0,0,one,new Random(1))==null)throw new IllegalStateException("Allowed biome position not found");
                float[] rain=(float[])type.getMethod("func_76936_a",float[].class,int.class,int.class,int.class,int.class).invoke(manager,null,x,z,1,1);
                float expectedRain=Math.min(1f,biome.getField("field_76751_G").getFloat(expected));
                if(Math.abs(rain[0]-expectedRain)>0.0001f)throw new IllegalStateException("Biome rainfall mismatch");
                // Choose the exact scattered-feature candidate in region 0,0 so the original method reaches getWorldChunkManager().
                Random random=(Random)oldWorld.getMethod("func_72843_D",int.class,int.class,int.class).invoke(world,0,0,14357617);
                int cx=random.nextInt(24),cz=random.nextInt(24);
                var generator=loader.loadClass("net.minecraft.world.gen.structure.MapGenScatteredFeature");Object scattered=generator.getConstructor().newInstance();
                var worldField=loader.loadClass("net.minecraft.world.gen.MapGenBase").getDeclaredField("field_75039_c");worldField.setAccessible(true);worldField.set(scattered,world);
                var candidate=generator.getDeclaredMethod("func_75047_a",int.class,int.class);candidate.setAccessible(true);candidate.invoke(scattered,cx,cz);
                proof.put("structureCandidate",List.of(cx,cz));proof.put("hostBiomeManager",true);proof.put("gridsAndRainfall",true);proof.put("viabilityAndSearch",true);proof.put("originalScatteredFeature",true);
                return null;
            });
            proof.put("status","PASS");
        }catch(Throwable failure){proof.put("status","FAIL");proof.put("failure",failure.toString());com.mojang.logging.LogUtils.getLogger().error("MFCL biome manager smoke failed",failure);}
        try {var path=FMLPaths.GAMEDIR.get().resolve("logs/mfcl/biome-manager-smoke.json");Files.createDirectories(path.getParent());Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(proof));}
        catch(Exception failure){throw new IllegalStateException(failure);}
        event.getServer().halt(false);
    }
}
