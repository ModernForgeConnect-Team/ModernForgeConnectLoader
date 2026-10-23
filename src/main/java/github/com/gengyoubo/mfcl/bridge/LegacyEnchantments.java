package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Explicit stable names for original numeric enchantment IDs; original predicates and costs remain authoritative. */
public final class LegacyEnchantments {
    public static final Map<Integer,String> VANILLA=Map.ofEntries(
            Map.entry(0,"protection"),Map.entry(1,"fire_protection"),Map.entry(2,"feather_falling"),Map.entry(3,"blast_protection"),
            Map.entry(4,"projectile_protection"),Map.entry(5,"respiration"),Map.entry(6,"aqua_affinity"),Map.entry(7,"thorns"),
            Map.entry(16,"sharpness"),Map.entry(17,"smite"),Map.entry(18,"bane_of_arthropods"),Map.entry(19,"knockback"),Map.entry(20,"fire_aspect"),Map.entry(21,"looting"),
            Map.entry(32,"efficiency"),Map.entry(33,"silk_touch"),Map.entry(34,"unbreaking"),Map.entry(35,"fortune"),
            Map.entry(48,"power"),Map.entry(49,"punch"),Map.entry(50,"flame"),Map.entry(51,"infinity"),Map.entry(61,"luck_of_the_sea"),Map.entry(62,"lure"));
    public record Entry(String key,int id,String name,int weight,int min,int max,boolean books,List<Integer> minCosts,List<Integer> maxCosts) {}
    private final LegacyServices services;
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private final Map<Integer,String> names=new HashMap<>();
    private final Map<String,Integer> ids=new HashMap<>();
    public LegacyEnchantments(LegacyServices services){this.services=services;VANILLA.forEach((id,name)->{names.put(id,"minecraft:"+name);ids.put("minecraft:"+name,id);});}
    public synchronized void register(String key,Object enchantment)throws Exception {
        if(enchantment==null)return;
        services.items().inLegacy(()->{
            var type=services.loader().loadClass("net.minecraft.enchantment.Enchantment");
            if(!type.isInstance(enchantment))throw new IllegalArgumentException("Foreign enchantment");
            int id=type.getField("field_77352_x").getInt(enchantment);
            if(entries.containsKey(key)||names.containsKey(id)||ids.containsKey(key))throw new IllegalArgumentException("Duplicate enchantment mapping: "+key+" / "+id);
            int min=(int)type.getMethod("func_77319_d").invoke(enchantment),max=(int)type.getMethod("func_77325_b").invoke(enchantment);
            if(min<1||max<min||max>255)throw new IllegalArgumentException("Invalid original enchantment levels");
            var lower=new ArrayList<Integer>();var upper=new ArrayList<Integer>();
            for(int level=0;level<=max;level++){lower.add((int)type.getMethod("func_77321_a",int.class).invoke(enchantment,level));upper.add((int)type.getMethod("func_77317_b",int.class).invoke(enchantment,level));}
            entries.put(key,new Entry(key,id,(String)type.getMethod("func_77320_a").invoke(enchantment),(int)type.getMethod("func_77324_c").invoke(enchantment),min,max,
                    (boolean)type.getMethod("isAllowedOnBooks").invoke(enchantment),List.copyOf(lower),List.copyOf(upper)));
            names.put(id,key);ids.put(key,id);return null;
        });
    }
    public synchronized List<Entry> entries(){return List.copyOf(entries.values());}
    public synchronized String name(int id){return names.get(id);}
    public synchronized Integer id(String name){return ids.get(name);}
    private Object original(int id)throws Exception{return ((Object[])services.loader().loadClass("net.minecraft.enchantment.Enchantment").getField("field_77331_b").get(null))[id];}
    public boolean applies(Entry entry,LegacyClientData.Stack stack,boolean table)throws Exception {
        if(stack==null)return false;
        return services.items().inLegacy(()->(boolean)services.loader().loadClass("net.minecraft.enchantment.Enchantment")
                .getMethod(table?"canApplyAtEnchantingTable":"func_92089_a",services.loader().loadClass("net.minecraft.item.ItemStack"))
                .invoke(original(entry.id()),services.items().materialize(stack)));
    }
    public boolean compatible(Entry entry,Integer other)throws Exception {
        if(other==null)return true;
        return services.items().inLegacy(()->(boolean)services.loader().loadClass("net.minecraft.enchantment.Enchantment")
                .getMethod("func_77326_a",services.loader().loadClass("net.minecraft.enchantment.Enchantment")).invoke(original(entry.id()),original(other)));
    }
}
