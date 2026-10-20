package github.com.gengyoubo.mfcl.compat.thaumcraft;

import java.util.*;

/** Original sorting keys, including foci inside inventory pouches; the server never accepts client item data. */
public final class ThaumcraftWandFocus {
    private ThaumcraftWandFocus() {}
    public static Map<String,Object> available(ClassLoader loader,Object player) throws Exception {
        Object inventory=loader.loadClass("net.minecraft.entity.player.EntityPlayer").getField("field_71071_by").get(player);
        Object[] slots=(Object[])inventory.getClass().getField("field_70462_a").get(inventory);
        var result=new TreeMap<String,Object>();for(Object stack:slots)add(loader,stack,result);return result;
    }
    public static void add(ClassLoader loader,Object stack,Map<String,Object> result) throws Exception {
        if(stack==null)return;var stackType=loader.loadClass("net.minecraft.item.ItemStack");
        if(stackType.getField("field_77994_a").getInt(stack)<=0)return;
        Object item=stackType.getMethod("func_77973_b").invoke(stack);
        if(loader.loadClass("thaumcraft.api.wands.ItemFocusBasic").isInstance(item))
            result.put((String)item.getClass().getMethod("getSortingHelper",stackType).invoke(item,stack),stack);
        else if(loader.loadClass("thaumcraft.common.items.wands.ItemFocusPouch").isInstance(item))
            for(Object focus:(Object[])item.getClass().getMethod("getInventory",stackType).invoke(item,stack))
                if(focus!=null&&loader.loadClass("thaumcraft.api.wands.ItemFocusBasic").isInstance(stackType.getMethod("func_77973_b").invoke(focus)))add(loader,focus,result);
    }
}
