package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Proxy;

/** An original IChunkProvider over host chunks, without generating detached 1.7.10 terrain. */
public final class LegacyChunkProvider {
    private LegacyChunkProvider(){}
    public static Object create(ClassLoader loader,Object world)throws Exception {
        var type=loader.loadClass("net.minecraft.world.chunk.IChunkProvider");
        return Proxy.newProxyInstance(loader,new Class<?>[]{type},(proxy,method,args)->switch(method.getName()){
            case "func_73154_d","func_73158_c"->LegacyWorlds.chunk(world,(int)args[0],(int)args[1]);
            case "func_73149_a"->LegacyWorlds.chunkLoaded(world,(int)args[0],(int)args[1]);
            case "func_73153_a","func_104112_b","func_82695_e"->null;
            case "func_73151_a","func_73157_c"->true;
            case "func_73156_b"->false;
            case "func_73152_e"->0;
            case "func_73148_d","toString"->"MFCL host chunk provider";
            case "func_147416_a"->null;
            case "func_73155_a"->{Object biome=loader.loadClass("net.minecraft.world.World").getMethod("func_72807_a",int.class,int.class).invoke(world,args[1],args[3]);yield biome.getClass().getMethod("func_76747_a",loader.loadClass("net.minecraft.entity.EnumCreatureType")).invoke(biome,args[0]);}
            case "hashCode"->System.identityHashCode(proxy);
            case "equals"->proxy==args[0];
            default->throw new UnsupportedOperationException("Unmapped original chunk-provider method: "+method);
        });
    }
}
