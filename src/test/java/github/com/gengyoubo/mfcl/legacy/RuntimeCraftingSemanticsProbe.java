package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.*;
import java.util.*;
import java.lang.reflect.Array;

final class RuntimeCraftingSemanticsProbe {
    static void verify(LegacyServices services,Map<String,Object> report) throws Exception {
        var loader=services.loader();Class<?> itemType=loader.loadClass("net.minecraft.item.Item"),stackType=loader.loadClass("net.minecraft.item.ItemStack");
        if (!services.items().catalog("mfcl_host:mfcl/crafting_fixture").isEmpty()) throw new AssertionError("Unexpected fixture before import");
        services.items().importHostItems(List.of(new LegacyClientData.HostItem("mfcl:crafting_fixture","Crafting fixture",64,0)));
        if (services.items().catalog("mfcl_host:mfcl/crafting_fixture").size() != 1) throw new AssertionError("Catalog index was not refreshed after import");
        var exact=new LegacyClientData.Stack("mfcl_host:mfcl/crafting_fixture",2,1,"","");
        var output=tagged(services,new LegacyClientData.Stack(exact.key(),7,3,"","{result_payload:23}"));
        var diamond=new LegacyClientData.Stack("minecraft:diamond",0,1,"","");var emerald=new LegacyClientData.Stack("minecraft:emerald",0,1,"","");
        var managerType=loader.loadClass("net.minecraft.item.crafting.CraftingManager");Object manager=managerType.getMethod("func_77594_a").invoke(null);
        var recipes=(List<Object>)managerType.getMethod("func_77592_b").invoke(manager);
        var ore=loader.loadClass("net.minecraftforge.oredict.ShapedOreRecipe");
        Object shaped=ore.getConstructor(stackType,Object[].class).newInstance(services.items().materialize(output),new Object[]{"AB",'A',services.items().materialize(exact),'B',services.items().materialize(diamond)});
        ore.getMethod("setMirrored",boolean.class).invoke(shaped,false);recipes.add(shaped);
        var dict=loader.loadClass("net.minecraftforge.oredict.OreDictionary");
        for(var gem:List.of(diamond,emerald))dict.getMethod("registerOre",String.class,stackType).invoke(null,"mfclTestCraftingGem",services.items().materialize(gem));
        var wildcard=new LegacyClientData.Stack(exact.key(),32767,1,"","");
        Object shapeless=loader.loadClass("net.minecraftforge.oredict.ShapelessOreRecipe").getConstructor(stackType,Object[].class).newInstance(services.items().materialize(new LegacyClientData.Stack(exact.key(),9,1,"","")),new Object[]{services.items().materialize(wildcard),"mfclTestCraftingGem"});recipes.add(shapeless);
        Object ingredients=Array.newInstance(stackType,1);Array.set(ingredients,0,services.items().materialize(exact));
        var plainType=loader.loadClass("net.minecraft.item.crafting.ShapedRecipes");
        Object copying=plainType.getConstructor(int.class,int.class,ingredients.getClass(),stackType).newInstance(1,1,ingredients,services.items().materialize(new LegacyClientData.Stack(exact.key(),11,1,"","")));
        plainType.getMethod("func_92100_c").invoke(copying);recipes.add(copying);
        var bridge=new LegacyCrafting(services);
        for (var definition : bridge.definitions()) if (bridge.definition(definition.hash()) != definition)
            throw new AssertionError("Recipe index changed definition identity");
        for (var definition : bridge.smelting()) if (bridge.smeltingDefinition(definition.hash()) != definition)
            throw new AssertionError("Smelting index changed definition identity");
        try { bridge.definition("absent"); throw new AssertionError("Missing recipe accepted"); }
        catch (IllegalArgumentException expected) { }
        var shape=bridge.definitions().stream().filter(d->d.output().metadata()==7&&d.output().key().equals(exact.key())).findFirst().orElseThrow();
        var shapelessDefinition=bridge.definitions().stream().filter(d->d.output().metadata()==9&&d.output().key().equals(exact.key())).findFirst().orElseThrow();
        var copy=bridge.definitions().stream().filter(d->d.output().metadata()==11&&d.output().key().equals(exact.key())).findFirst().orElseThrow();
        var grid=new ArrayList<LegacyClientData.Stack>(Collections.nCopies(9,null));grid.set(4,exact);grid.set(5,diamond);
        if(!bridge.matches(shape.hash(),3,3,grid)||!bridge.assemble(shape.hash(),3,3,grid).tag().contains("result_payload:23"))throw new AssertionError("Original shaped placement/output NBT lost");
        grid.set(4,diamond);grid.set(5,exact);if(bridge.matches(shape.hash(),3,3,grid))throw new AssertionError("Non-mirrored recipe was mirrored");
        grid.set(4,new LegacyClientData.Stack(exact.key(),3,1,"",""));grid.set(5,diamond);if(bridge.matches(shape.hash(),3,3,grid))throw new AssertionError("Wrong metadata matched ordinary recipe");
        grid=new ArrayList<>(Collections.nCopies(9,null));grid.set(8,new LegacyClientData.Stack(exact.key(),83,1,"",""));grid.set(0,emerald);
        if(!bridge.matches(shapelessDefinition.hash(),3,3,grid))throw new AssertionError("Wildcard metadata or ore alternative failed");
        grid.set(1,diamond);if(bridge.matches(shapelessDefinition.hash(),3,3,grid))throw new AssertionError("Extra ingredient was accepted");
        grid=new ArrayList<>(Collections.nCopies(4,null));grid.set(3,tagged(services,new LegacyClientData.Stack(exact.key(),2,1,"","{copied_payload:19}")));
        if(!bridge.assemble(copy.hash(),2,2,grid).tag().contains("copied_payload:19"))throw new AssertionError("Original copy-NBT crafting behavior lost");
        if(!bridge.remainder(new LegacyClientData.Stack("minecraft:milk_bucket",0,1,"","")).key().equals("minecraft:bucket"))throw new AssertionError("Container item remainder lost");
        services.items().projectHostRecipes(List.of());
        if(!bridge.matches(copy.hash(),2,2,grid)||!recipes.contains(shaped)||!recipes.contains(shapeless))throw new AssertionError("Host recipe projection replaced frozen mod recipes");
        report.put("genericOriginalWorkbenchRecipesVerified",true);
    }
    private static LegacyClientData.Stack tagged(LegacyServices services,LegacyClientData.Stack value) throws Exception {
        Object stack=services.items().materialize(new LegacyClientData.Stack(value.key(),value.metadata(),value.count(),value.name(),""));
        Object tag=services.loader().loadClass("net.minecraft.nbt.JsonToNBT").getMethod("func_150315_a",String.class).invoke(null,value.tag());
        stack.getClass().getField("field_77990_d").set(stack,tag);return services.items().snapshot(stack);
    }
}
