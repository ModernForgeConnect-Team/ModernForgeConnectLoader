package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Registration provenance for old registries that have no namespaced identity of their own. */
public final class LegacyRegistrations {
    public record Owned(String owner,Object value){}
    public record Spawn(Class<?> entity,int weight,int min,int max,String category,List<Object> biomes){}
    public record Loot(String owner,String category,Object content){}
    private static final Map<ClassLoader,Domain> DOMAINS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class Domain {
        final Map<Object,String> attributes=new IdentityHashMap<>(),statistics=new IdentityHashMap<>();
        final Map<Integer,String> professions=new LinkedHashMap<>();
        final List<Spawn> spawns=new ArrayList<>();final List<Loot> loot=new ArrayList<>(),removedLoot=new ArrayList<>();
    }
    private LegacyRegistrations(){}
    private static Domain domain(ClassLoader loader){return DOMAINS.computeIfAbsent(loader,key->new Domain());}
    private static String owner(ClassLoader loader){
        try{
            var type=loader.loadClass("cpw.mods.fml.common.Loader");Object fml=type.getMethod("instance").invoke(null),mod=type.getMethod("activeModContainer").invoke(fml);
            return mod==null?"minecraft":(String)loader.loadClass("cpw.mods.fml.common.ModContainer").getMethod("getModId").invoke(mod);
        }catch(ReflectiveOperationException|LinkageError unavailable){return "minecraft";}
    }
    public static synchronized void attribute(Object value){domain(value.getClass().getClassLoader()).attributes.putIfAbsent(value,owner(value.getClass().getClassLoader()));}
    public static synchronized void statistic(Object value){domain(value.getClass().getClassLoader()).statistics.putIfAbsent(value,owner(value.getClass().getClassLoader()));}
    public static synchronized void profession(Object registry,int id){domain(registry.getClass().getClassLoader()).professions.putIfAbsent(id,owner(registry.getClass().getClassLoader()));}
    public static synchronized void spawn(Class<?> entity,int weight,int min,int max,Object category,Object[] biomes){
        var values=domain(entity.getClassLoader()).spawns;
        // EntityRegistry.addSpawn replaces an existing entity/category/biome entry; later calls remain authoritative.
        for(Object biome:biomes){values.removeIf(rule->rule.entity()==entity&&rule.category().equals(category.toString())&&rule.biomes().contains(biome));values.add(new Spawn(entity,weight,min,max,category.toString(),List.of(biome)));}
    }
    public static void removeSpawn(Class<?> entity,Object category,Object[] biomes){spawn(entity,0,0,0,category,biomes);}
    public static synchronized void loot(Object hooks,Object content){
        chest(hooks,content,false);
    }
    public static synchronized void removeLoot(Object hooks,Object stack){chest(hooks,stack,true);}
    private static void chest(Object hooks,Object content,boolean removal){
        var loader=hooks.getClass().getClassLoader();
        for(var frame:Thread.currentThread().getStackTrace())if(frame.getClassName().equals("net.minecraftforge.common.ChestGenHooks")&&(frame.getMethodName().equals("init")||frame.getMethodName().equals("<init>")||frame.getMethodName().equals("<clinit>")))return;
        String owner=owner(loader);if(owner.equals("minecraft")||owner.equals("FML")||owner.equals("Forge"))return;
        try{var field=hooks.getClass().getDeclaredField("category");field.setAccessible(true);(removal?domain(loader).removedLoot:domain(loader).loot).add(new Loot(owner,(String)field.get(hooks),content));}
        catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot identify original chest loot category",failure);}
    }
    public static synchronized List<Owned> attributes(ClassLoader loader){return domain(loader).attributes.entrySet().stream().map(entry->new Owned(entry.getValue(),entry.getKey())).toList();}
    public static synchronized List<Owned> statistics(ClassLoader loader){return domain(loader).statistics.entrySet().stream().map(entry->new Owned(entry.getValue(),entry.getKey())).toList();}
    public static synchronized Map<Integer,String> professions(ClassLoader loader){return Map.copyOf(domain(loader).professions);}
    public static synchronized List<Spawn> spawns(ClassLoader loader){return List.copyOf(domain(loader).spawns);}
    public static synchronized List<Loot> loot(ClassLoader loader){return List.copyOf(domain(loader).loot);}
    public static synchronized List<Loot> removedLoot(ClassLoader loader){return List.copyOf(domain(loader).removedLoot);}
}
