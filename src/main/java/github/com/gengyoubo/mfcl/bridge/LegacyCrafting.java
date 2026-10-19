package github.com.gengyoubo.mfcl.bridge;

import java.util.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Frozen ordinary workbench recipes. Arcane/crucible/infusion registries are never consulted. */
public final class LegacyCrafting {
    public record Definition(String hash,boolean shaped,boolean dynamic,boolean strictNbt,int width,int height,List<List<LegacyClientData.Stack>> inputs,LegacyClientData.Stack output) {}
    private final LegacyServices services;
    private final Map<String,Object> originals=new LinkedHashMap<>();
    private final List<Definition> definitions=new ArrayList<>();
    private final List<String> diagnostics=new ArrayList<>();
    public LegacyCrafting(LegacyServices services) throws Exception {
        this.services=services;
        services.items().inLegacy(()->{
            var loader=services.loader();var manager=loader.loadClass("net.minecraft.item.crafting.CraftingManager");
            var recipeType=loader.loadClass("net.minecraft.item.crafting.IRecipe");
            var retained=new ArrayList<Object>();
            int dynamicIndex=0;
            Class<?> plainShaped=loader.loadClass("net.minecraft.item.crafting.ShapedRecipes"),plainShapeless=loader.loadClass("net.minecraft.item.crafting.ShapelessRecipes"),oreShaped=loader.loadClass("net.minecraftforge.oredict.ShapedOreRecipe"),oreShapeless=loader.loadClass("net.minecraftforge.oredict.ShapelessOreRecipe");
            for(Object recipe:(List<?>)manager.getMethod("func_77592_b").invoke(manager.getMethod("func_77594_a").invoke(null))) {
                String type=recipe.getClass().getName();Object result=recipeType.getMethod("func_77571_b").invoke(recipe);
                var output=result==null?null:services.items().snapshot(result);
                boolean shaped=plainShaped.isInstance(recipe)||oreShaped.isInstance(recipe);
                boolean shapeless=plainShapeless.isInstance(recipe)||oreShapeless.isInstance(recipe);
                if(!shaped&&!shapeless) {
                    if(!type.startsWith("net.minecraft.")&&!type.startsWith("net.minecraftforge.")) {
                        retained.add(recipe);String hash=hash(type+"|dynamic="+dynamicIndex++);originals.put(hash,recipe);
                        definitions.add(new Definition(hash,false,true,false,0,0,List.of(),output));
                    }
                    continue;
                }
                var input=new ArrayList<List<LegacyClientData.Stack>>();int width=0,height=0;
                if(plainShaped.isInstance(recipe)) {
                    width=(int)field(recipe,"field_77576_b");height=(int)field(recipe,"field_77577_c");
                    for(Object entry:(Object[])field(recipe,"field_77574_d"))input.add(alternatives(entry));
                }else if(plainShapeless.isInstance(recipe)) {
                    for(Object entry:(List<?>)field(recipe,"field_77579_b"))input.add(alternatives(entry));
                }else if(shaped) {
                    width=(int)field(recipe,"width");height=(int)field(recipe,"height");
                    for(Object entry:(Object[])recipe.getClass().getMethod("getInput").invoke(recipe))input.add(alternatives(entry));
                }else for(Object entry:(List<?>)recipe.getClass().getMethod("getInput").invoke(recipe))input.add(alternatives(entry));
                boolean mod=output!=null&&!output.key().startsWith("minecraft:")||input.stream().flatMap(List::stream).anyMatch(s->!s.key().startsWith("minecraft:"));
                if(!mod)continue;retained.add(recipe);
                if(output==null||input.isEmpty()||input.size()>9||shaped&&(width>3||height>3)) {diagnostics.add("Invalid ordinary recipe dimensions/output: "+type);continue;}
                String canonical=type+"|"+width+"|"+height+"|"+identity(output)+"|";
                for(var slot:input){for(var stack:slot)canonical+=identity(stack)+";";canonical+="/";}
                if(oreShaped.isInstance(recipe))canonical+="mirror="+field(recipe,"mirrored");
                if(plainShaped.isInstance(recipe))canonical+="copyTag="+field(recipe,"field_92101_f");
                String hash=hash(canonical);
                boolean strictNbt=!(recipe.getClass()==plainShaped||recipe.getClass()==plainShapeless||recipe.getClass()==oreShaped||recipe.getClass()==oreShapeless);
                if(originals.putIfAbsent(hash,recipe)==null)definitions.add(new Definition(hash,shaped,false,strictNbt,width,height,input.stream().map(List::copyOf).toList(),output));
            }
            services.items().preserveCrafting(retained);return null;
        });
    }
    private static String identity(LegacyClientData.Stack stack){return stack.key()+"@"+stack.metadata()+"x"+stack.count()+stack.tag();}
    private List<LegacyClientData.Stack> alternatives(Object value) throws Exception {
        if(value==null)return List.of();var result=new ArrayList<LegacyClientData.Stack>();
        if(value instanceof List<?> list){for(Object stack:list)result.add(services.items().snapshot(stack));}
        else result.add(services.items().snapshot(value));return result;
    }
    private static String hash(String input) throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}
    private static Object field(Object owner,String name) throws Exception {for(Class<?> type=owner.getClass();type!=null;type=type.getSuperclass())try{var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(NoSuchFieldException absent){}throw new NoSuchFieldException(name);}
    public List<Definition> definitions(){return List.copyOf(definitions);}
    public List<String> diagnostics(){return List.copyOf(diagnostics);}
    public Definition definition(String hash){return definitions.stream().filter(d->d.hash().equals(hash)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown original crafting recipe: "+hash));}
    private Object grid(int width,int height,List<LegacyClientData.Stack> input) throws Exception {
        var loader=services.loader();var type=loader.loadClass("net.minecraft.inventory.InventoryCrafting");
        Object grid=type.getConstructor(loader.loadClass("net.minecraft.inventory.Container"),int.class,int.class).newInstance(null,width,height);
        Object contents=Array.newInstance(loader.loadClass("net.minecraft.item.ItemStack"),input.size());
        for(int index=0;index<input.size();index++)if(input.get(index)!=null)Array.set(contents,index,services.items().materialize(input.get(index)));
        var field=type.getDeclaredField("field_70466_a");field.setAccessible(true);field.set(grid,contents);return grid;
    }
    public boolean matches(String hash,int width,int height,List<LegacyClientData.Stack> input) throws Exception {
        return matches(hash,width,height,input,null);
    }
    public boolean matches(String hash,int width,int height,List<LegacyClientData.Stack> input,Object world) throws Exception {return services.items().inLegacy(()->(boolean)services.loader().loadClass("net.minecraft.item.crafting.IRecipe").getMethod("func_77569_a",services.loader().loadClass("net.minecraft.inventory.InventoryCrafting"),services.loader().loadClass("net.minecraft.world.World")).invoke(originals.get(hash),grid(width,height,input),world));}
    public LegacyClientData.Stack assemble(String hash,int width,int height,List<LegacyClientData.Stack> input) throws Exception {
        return services.items().inLegacy(()->{Object value=services.loader().loadClass("net.minecraft.item.crafting.IRecipe").getMethod("func_77572_b",services.loader().loadClass("net.minecraft.inventory.InventoryCrafting")).invoke(originals.get(hash),grid(width,height,input));return value==null?null:services.items().snapshot(value);});
    }
    public LegacyClientData.Stack remainder(LegacyClientData.Stack input) throws Exception {
        if(input==null)return null;
        return services.items().inLegacy(()->{var loader=services.loader();var stack=loader.loadClass("net.minecraft.item.ItemStack");Object value=services.items().materialize(input),item=stack.getMethod("func_77973_b").invoke(value);var type=loader.loadClass("net.minecraft.item.Item");
            if(!(boolean)type.getMethod("hasContainerItem",stack).invoke(item,value))return null;
            Object result=type.getMethod("getContainerItem",stack).invoke(item,value);return result==null?null:services.items().snapshot(result);});
    }
}
